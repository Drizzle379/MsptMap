package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import msptmap.ChunkKeys;
import msptmap.Clamp;
import msptmap.Decimals;
import msptmap.Ids;
import msptmap.MsptMapMod;
import msptmap.mixins.ChunkMapAccessor;
import msptmap.net.ScanResultPayload;
import msptmap.net.SnapshotCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采样核心：逐区块累计耗时，开一个 N 秒的窗口，窗口结束出结果。
 *
 * <p>无后台线程，只有开关与数据表：未采样时注入点只读一个 boolean；采样时每个事件仅记两笔
 * （纳秒、次数），不做除法、不分配对象。
 *
 * <p>窗口按服务端 tick 数收尾（挂在 Fabric API 的 END_SERVER_TICK 上），不看现实时间。两次窗口
 * 之间隔 {@link #COOLDOWN_SECONDS} 秒冷却；服务器停止时由 {@link #reset()} 清空状态。
 */
public final class MsptSampler {
	/** 采样秒数上限。 */
	public static final int MAX_SECONDS = 60;

	/**
	 * 一次扫描所有维度合计的字节预算：原版自定义包上限 1MB，此处留一倍余量（单区块编码后约
	 * 14~28 字节）。
	 */
	public static final int MAX_SNAPSHOT_BYTES = 700 * 1024;

	/** 正在采样。热路径只读这一个字段。 */
	private static boolean sampling;

	/** 每秒刻数，窗口长度与客户端进度圈（ScanProgress）共用此口径。 */
	public static final int TICKS_PER_SECOND = 20;

	/** 进度包发送间隔：2 刻 = 0.1 秒。 */
	private static final int PROGRESS_EVERY_TICKS = 2;

	/** 窗口内已过的服务端 tick 数：mspt 的分母，数够「秒数 × TICKS_PER_SECOND」即收尾。 */
	private static int windowTicks;

	/** 本次窗口的秒数（已夹取），随「开始」包发回客户端。 */
	private static int seconds;

	/** 本次扫描的发起人：客户端请求为玩家，服务端命令 / 控制台为 null。 */
	private static ServerPlayer requester;

	/** 每个维度一张表：区块坐标 → 账本。外层按对象身份比，内层用 fastutil 的 long 键表（免装箱）。 */
	private static final Map<ServerLevel, Long2ObjectOpenHashMap<ChunkTiming>> timings = new IdentityHashMap<>();

	/** 上一次扫描结束后至少间隔的现实秒数。收尾（快照构建）有成本，且未装地毯时谁都能发起。 */
	public static final int COOLDOWN_SECONDS = 2;

	private static final long COOLDOWN_NANOS = COOLDOWN_SECONDS * 1_000_000_000L;

	/** 上次扫描结束的时刻，用于冷却；0 = 还没扫过。服务器停止时复位。 */
	private static long lastEndNanos;

	/** 上一条记账的来源缓存：事件按区块聚集，命中时免掉两次哈希查找。换维度或清表时作废。 */
	private static ServerLevel lastLevel;
	private static Long2ObjectOpenHashMap<ChunkTiming> lastChunks;
	private static long lastKey;
	private static ChunkTiming lastTiming;

	/**
	 * 超过这么多现实秒没有服务端 tick 即视为服务端未在运行（空载自动暂停 / 世界暂停 / 长时间卡顿）。
	 * 正常一刻 50 毫秒，5 秒有 100 倍余量，普通卡顿不会误判。
	 */
	private static final int STALL_SECONDS = 5;

	private static final long STALL_NANOS = STALL_SECONDS * 1_000_000_000L;

	/** 上一次服务端 tick 的时刻（与是否采样无关，每 tick 更新）；0 = 服务器还没有 tick 过。 */
	private static long lastTickNanos;

	/**
	 * 上一 tick 收尾、本 tick 才发的完成包（见 {@link #finish()}）；没有待发时为 null。
	 *
	 * <p>压后一 tick 是为了让客户端有整整一 tick 把进度圈画满：窗口最后一刻直接发送的话，圈会从
	 * 90% 跳到消失，观感如同未转满即中断。
	 */
	private static ServerPlayer pendingPlayer;
	private static ScanResultPayload pendingDone;

	private MsptSampler() {
	}

	/** 服务端是不是没有在运行：距上次 tick 超过 {@link #STALL_SECONDS} 秒。 */
	private static boolean stalled(long now) {
		return lastTickNanos != 0L && now - lastTickNanos > STALL_NANOS;
	}

	/**
	 * 把请求秒数夹进合法区间。调用方须使用返回值：回给客户端的「开始」包要报夹取后的秒数，
	 * 否则进度圈分母与真实窗口不符。
	 */
	public static int clampSeconds(int seconds) {
		return Clamp.of(seconds, 1, MAX_SECONDS);
	}

	/** 一次「开始」请求的结果，由调用方决定怎么提示。 */
	public enum StartResult {
		/** 窗口已开。 */
		STARTED,
		/** 已有扫描进行中。 */
		BUSY,
		/** 距上次扫描结束不足 {@link #COOLDOWN_SECONDS} 秒。 */
		COOLDOWN,
		/** 服务端当前没有在运行（见 {@link #stalled}）：窗口数不到刻，控制台请求直接拒绝。 */
		STALLED
	}

	/**
	 * 开一个 N 秒的窗口；已在采样中或冷却未过则返回对应结果，由调用方决定提示。
	 *
	 * @param requester 本次扫描的发起人：客户端请求为玩家（结果回给他），服务端命令 / 控制台为 null（结果打控制台）
	 */
	public static StartResult start(int seconds, ServerPlayer requester) {
		long now = System.nanoTime();
		boolean stalled = stalled(now);
		if (sampling) {
			if (!stalled) {
				return StartResult.BUSY;
			}
			// 服务端已很久没有 tick：旧窗口僵死（发起人掉线、世界暂停后未恢复）。作废重开，
			// 否则它将一直占用，后续请求均被判为「忙」
			MsptMapMod.LOGGER.info("上一个采样窗口已停滞（服务端 {} 秒没有 tick），已作废",
					(now - lastTickNanos) / 1_000_000_000L);
			reset();
		}
		// 冷却从上次结束算起；若从开始算，间隔 ≤ 窗口长度时冷却会在窗口内提前过期，等于没有冷却
		if (lastEndNanos != 0L && now - lastEndNanos < COOLDOWN_NANOS) {
			return StartResult.COOLDOWN;
		}
		// 服务端当前未运行：窗口数不到刻，控制台（无发起人）拿不到结果，直接拒绝；玩家请求照旧
		// 开窗等待 —— 单人档在地图上点按钮时世界本就暂停，关掉地图即会开始数刻
		if (stalled && requester == null) {
			return StartResult.STALLED;
		}
		MsptSampler.requester = requester;
		MsptSampler.seconds = clampSeconds(seconds);
		clearTimings();
		windowTicks = 0;
		sampling = true;
		return StartResult.STARTED;
	}

	/**
	 * 服务器停止：丢弃采样状态与表、复位冷却。
	 *
	 * <p>静态字段跨世界存活（单人档退回主菜单再进是同一个 JVM），不复位则旧窗口继续数刻，
	 * 重进后的扫描请求会被误判为「忙」，而服务器中并无他人扫描。
	 */
	public static void reset() {
		sampling = false;
		requester = null;
		windowTicks = 0;
		pendingPlayer = null;
		pendingDone = null;
		clearTimings();
		lastEndNanos = 0L;
	}

	/**
	 * 每个服务端 tick 结束时调用一次：数够窗口刻数即收尾，收尾前每 0.1 秒推一次进度包
	 * （最后一刻改在 {@link #finish()} 中推满格，完成包再压后一 tick）。
	 *
	 * <p>时间戳与是否采样无关：未采样时也要记录，停滞判定（{@link #stalled}）依赖它。
	 */
	public static void onServerTick() {
		lastTickNanos = System.nanoTime();
		if (pendingDone != null) {
			sendPendingDone();
		}
		if (!sampling) {
			return;
		}
		windowTicks++;
		if (windowTicks >= seconds * TICKS_PER_SECOND) {
			finish();
			return;
		}
		sendProgress();
	}

	/** 发上一 tick 收尾时压住的完成包；这段间隔里玩家若已掉线则丢弃。 */
	private static void sendPendingDone() {
		ServerPlayer player = pendingPlayer;
		ScanResultPayload payload = pendingDone;
		pendingPlayer = null;
		pendingDone = null;
		if (ServerPlayNetworking.canSend(player, ScanResultPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		} else {
			MsptMapMod.LOGGER.info("扫描结果没送到：{} 已不在线（窗口 {} tick）",
					player.getScoreboardName(), payload.windowTicks());
		}
	}

	/**
	 * 把窗口已过的刻数推给发起人：客户端只画服务端报过的数。控制台 / 命令方块发起的扫描没有
	 * 发起人，不发。
	 */
	private static void sendProgress() {
		if (windowTicks % PROGRESS_EVERY_TICKS != 0) {
			return;
		}
		ServerPlayer player = requester;
		if (player != null && ServerPlayNetworking.canSend(player, ScanResultPayload.TYPE)) {
			ServerPlayNetworking.send(player, ScanResultPayload.progress(seconds, windowTicks));
		}
	}

	/** 计时开始。返回 0 表示未在采样：{@link #end} 会忽略 0，注入点无需自查。 */
	public static long begin() {
		return sampling ? System.nanoTime() : 0L;
	}

	/** 计时结束并入账。热路径：先走 {@link #lastTiming} 缓存，未命中才查表。 */
	public static void end(TickCategory category, ServerLevel level, long chunkKey, long startNanos) {
		if (startNanos == 0L) {
			return;
		}
		if (level != lastLevel) {
			// 换维度：外层表查一次，并作废上一次的区块缓存（表都换了）
			lastChunks = timings.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>());
			lastLevel = level;
			lastTiming = null;
		}
		ChunkTiming timing;
		if (chunkKey == lastKey && lastTiming != null) {
			timing = lastTiming;
		} else {
			timing = lastChunks.get(chunkKey);
			if (timing == null) {
				timing = new ChunkTiming();
				lastChunks.put(chunkKey, timing);
			}
			lastKey = chunkKey;
			lastTiming = timing;
		}
		timing.add(category, System.nanoTime() - startNanos);
	}

	/**
	 * 清表并作废来源缓存。缓存指向的表一旦被换掉，再写入就会写进已废弃的表；三个清表入口
	 * （start / finish / reset）一律走这里，不直接调用 {@code timings.clear()}。
	 */
	private static void clearTimings() {
		timings.clear();
		lastLevel = null;
		lastChunks = null;
		lastTiming = null;
	}

	/** 收尾：结果打包给发起人；发不回去（控制台发起 / 中途掉线）则打到服务端控制台。 */
	private static void finish() {
		sampling = false;
		long finishStartNanos = System.nanoTime();
		// 窗口到此结束：冷却时间从这里起算
		lastEndNanos = finishStartNanos;
		ServerPlayer player = requester;
		requester = null;
		try {
			if (player != null && ServerPlayNetworking.canSend(player, ScanResultPayload.TYPE)) {
				// 先补一格满格进度，完成包压到下一 tick（见 pendingDone）：圈画满整整一 tick 再消失
				ServerPlayNetworking.send(player, ScanResultPayload.progress(seconds, windowTicks));
				pendingDone = ScanResultPayload.done(seconds, windowTicks, snapshot());
				pendingPlayer = player;
			} else {
				logTopChunks();
			}
		} finally {
			// 须在 snapshot()/logTopChunks() 之后执行，且发送路径抛异常时也要执行：外层表的键是
			// ServerLevel，不清则多世界服务器卸载某世界后它仍被强引用，无法回收。
			clearTimings();
		}
		// 收尾（实体遍历、两条链 BFS、排序、逐块编码）全部同步在本 tick 上，此处计量其成本；
		// 编码与实际的发送在网络线程，不在计量范围内
		MsptMapMod.LOGGER.info("收尾耗时 {} ms", (System.nanoTime() - finishStartNanos) / 1_000_000L);
	}

	/**
	 * 把采样表转换为可发送的快照，同时取两个等级；取的是出快照这一刻的值（随时在变，每次查询
	 * 都是一次哈希查找，不宜放入采样热路径）。
	 *
	 * <p>字节预算按维度平分，避免外层表的遍历顺序决定谁先吃光预算。
	 */
	private static List<SnapshotCodec.DimensionData> snapshot() {
		List<SnapshotCodec.DimensionData> dimensions = new ArrayList<>(timings.size());
		int share = MAX_SNAPSHOT_BYTES / Math.max(1, timings.size());
		timings.forEach((level, chunks) -> dimensions.add(snapshotDimension(level, chunks, share)));
		return dimensions;
	}

	private static SnapshotCodec.DimensionData snapshotDimension(ServerLevel level, Long2ObjectOpenHashMap<ChunkTiming> chunks,
			int budget) {
		ChunkMap chunkMap = level.getChunkSource().chunkMap;
		DistanceManager distanceManager = chunkMap.getDistanceManager();
		Long2IntOpenHashMap entityCounts = entityCounts(level);
		// 两条链各推一遍：同一个区块上，加载票与模拟票可能不是同一张
		Long2IntOpenHashMap loadTickets = TicketSources.resolve(level, false);
		Long2IntOpenHashMap simTickets = TicketSources.resolve(level, true);
		LongOpenHashSet measured = new LongOpenHashSet(chunks.size());
		List<SnapshotCodec.ChunkData> out = new ArrayList<>(chunks.size());
		chunks.forEach((key, timing) -> {
			measured.add(key);
			//? if >=1.21.5 {
			// simulate=false 取加载等级，true 取计算等级（和 /chunkloadinfo 同一个方法）
			int loadLevel = distanceManager.getChunkLevel(key, false);
			int computeLevel = distanceManager.getChunkLevel(key, true);
			//?} else {
			/*// 1.21.4 及以前：DistanceManager 没有按坐标取等级的接口，改用 ChunkHolder 的 ticketLevel
			// （加载）与 queueLevel（计算）。33 = 完整生成但不刻，与快照上限一致。ChunkMap 的
			// getVisibleChunkIfPresent 在 1.21.4 为 protected，故走自己的 accessor。
			ChunkHolder holder = ((ChunkMapAccessor) (Object) chunkMap).getVisibleChunks().get(key);
			int loadLevel = holder != null ? holder.getTicketLevel() : 33;
			int computeLevel = holder != null ? holder.getQueueLevel() : 33;
			*///?}
			out.add(new SnapshotCodec.ChunkData(
					ChunkPos.getX(key),
					ChunkPos.getZ(key),
					timing.nanosArray(),
					timing.countsArray(),
					// 显式转 long 走原始版 get(long)（缺省值 0）；装箱版 get(Object) 对不存在的键
					// 在部分 fastutil 版本上返回 null，拆箱即 NPE（1.21.8 实机曾触发）
					entityCounts.get((long) key),
					loadLevel,
					computeLevel,
					//? if >=1.21.5 {
					ticketCode(loadTickets, key, ChunkLevel.isBlockTicking(loadLevel)),
					ticketCode(simTickets, key, ChunkLevel.isBlockTicking(computeLevel))));
					//?} else {
					/*TicketSources.NONE,
					TicketSources.NONE));
					*///?}
		});
		// 按重量降序：装不下时移除的必然是尾部最轻的。重量先一次算好再排；若在比较器中现算
		// totalNanos()，每次比较都要重加那七个数，数万区块时即为数十万次重复求和
		List<Weighted> weighted = new ArrayList<>(out.size());
		for (SnapshotCodec.ChunkData chunk : out) {
			weighted.add(new Weighted(chunk, chunk.totalNanos()));
		}
		weighted.sort(Comparator.comparingLong(Weighted::total).reversed());
		for (int i = 0; i < out.size(); i++) {
			out.set(i, weighted.get(i).chunk());
		}
		int used = SnapshotCodec.fitToBudget(out, budget);
		if (out.size() < chunks.size()) {
			MsptMapMod.LOGGER.info("维度 {} 超出字节预算：{} 个干过活的区块没带上（最轻的那些）",
					Ids.id(level.dimension()), chunks.size() - out.size());
		}
		// 有计时的收完，再补「加载着、窗口内无计时」的那些
		addLoadedChunks(level, distanceManager, measured, entityCounts, loadTickets, simTickets, out, budget - used);
		return new SnapshotCodec.DimensionData(Ids.id(level.dimension()), out);
	}

	/**
	 * 该区块在某条链上的加载来源。表里没有它时，以「该链是否本该覆盖本区块」区分：本该覆盖却无源，
	 * 说明正推漏了锚点，标为存疑（客户端显示星号）；本就不覆盖（模拟链超出模拟距离）时无源属正常，
	 * 不应报为异常，否则视距内、模拟距离外的一圈会星号满屏。
	 *
	 * @param expected 该链是否本该覆盖本区块
	 */
	private static int ticketCode(Long2IntOpenHashMap sources, long key, boolean expected) {
		int code = sources.get(key);
		// 表里不存 NONE（0），所以取出 0 即代表没有
		return code == 0 ? TicketSources.encode(TicketSources.NONE, 0, 0, expected) : code;
	}

	/**
	 * 出快照那一刻每个区块的实体数（含乘客）。与耗时不同，这是瞬时值而非窗口内的累计，故不放入
	 * 热路径：每维度遍历一遍全部实体，只在收尾时进行一次。
	 */
	private static Long2IntOpenHashMap entityCounts(ServerLevel level) {
		Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
		for (Entity entity : level.getAllEntities()) {
			if (!entity.isRemoved()) {
				counts.addTo(ChunkKeys.pack(entity.chunkPosition()), 1);
			}
		}
		return counts;
	}

	/**
	 * 把「加载着但窗口内无计时」的区块补进快照，耗时与次数全填 0，客户端据此铺淡灰。
	 *
	 * <p>只收加载等级 ≤ 32 的（31 实体刻 / 32 方块刻）；33 及以上完全不 tick。等级直接读
	 * {@code ChunkHolder.getTicketLevel()}，与 {@code getChunkLevel(key, false)} 同源。这批区块
	 * 没有轻重可挑，装不下即中止，并留一行日志。
	 */
	private static void addLoadedChunks(ServerLevel level, DistanceManager distanceManager, LongOpenHashSet measured,
			Long2IntOpenHashMap entityCounts, Long2IntOpenHashMap loadTickets, Long2IntOpenHashMap simTickets,
			List<SnapshotCodec.ChunkData> out, int budget) {
		int used = 0;
		boolean full = false;
		for (Long2ObjectMap.Entry<ChunkHolder> entry : ((ChunkMapAccessor) (Object) level.getChunkSource().chunkMap)
				.getVisibleChunks().long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			if (measured.contains(key)) {
				continue;
			}
			int loadLevel = entry.getValue().getTicketLevel();
			if (!ChunkLevel.isBlockTicking(loadLevel)) {
				continue;
			}
			//? if >=1.21.5 {
			int computeLevel = distanceManager.getChunkLevel(key, true);
			//?} else {
			/*int computeLevel = entry.getValue().getQueueLevel();
			*///?}
			SnapshotCodec.ChunkData chunk = new SnapshotCodec.ChunkData(ChunkPos.getX(key), ChunkPos.getZ(key),
					SnapshotCodec.ZERO_NANOS, SnapshotCodec.ZERO_COUNTS,
					entityCounts.get(key), loadLevel, computeLevel,
					//? if >=1.21.5 {
					ticketCode(loadTickets, key, ChunkLevel.isBlockTicking(loadLevel)),
					ticketCode(simTickets, key, ChunkLevel.isBlockTicking(computeLevel)));
					//?} else {
					/*TicketSources.NONE,
					TicketSources.NONE);
					*///?}
			int size = SnapshotCodec.encodedSize(chunk);
			if (used + size > budget) {
				full = true;
				break;
			}
			used += size;
			out.add(chunk);
		}
		if (full) {
			MsptMapMod.LOGGER.info("维度 {} 超出字节预算：一部分只加载着的区块没带上", Ids.id(level.dimension()));
		}
	}

	/** 结果发不回客户端时，把最重的 5 个区块打到服务端控制台。 */
	private static void logTopChunks() {
		// 重量同 snapshotDimension：排序前一次算好，不在比较器里反复求和
		List<Row> rows = new ArrayList<>();
		timings.forEach((level, chunks) -> chunks.forEach(
				(key, timing) -> rows.add(new Row(level, key, timing, timing.totalNanos()))));
		rows.sort((a, b) -> Long.compare(b.total, a.total));

		MsptMapMod.LOGGER.info("扫描结束：{} 个维度 / {} 个区块，窗口 {} 秒 / {} tick",
				timings.size(), rows.size(), seconds, windowTicks);

		for (int i = 0; i < Math.min(5, rows.size()); i++) {
			Row row = rows.get(i);
			MsptMapMod.LOGGER.info("  #{} {} ({}, {})  {} mspt [随机刻 {} 计划刻 {} 方块更新 {} 方块事件 {} "
							+ "方块实体 {} 实体 {} 刷怪 {}]",
					i + 1,
					Ids.id(row.level.dimension()),
					ChunkPos.getX(row.key),
					ChunkPos.getZ(row.key),
					ms(row.timing.totalNanos()),
					ms(row.timing.nanos(TickCategory.RANDOM_TICK)),
					ms(row.timing.nanos(TickCategory.SCHEDULED)),
					ms(row.timing.nanos(TickCategory.NEIGHBOR_UPDATE)),
					ms(row.timing.nanos(TickCategory.BLOCK_EVENT)),
					ms(row.timing.nanos(TickCategory.BLOCK_ENTITY)),
					ms(row.timing.nanos(TickCategory.ENTITY)),
					ms(row.timing.nanos(TickCategory.SPAWN)));
		}
	}

	/** 纳秒 → mspt 文本，仅供打印（精度与 Locale 见 {@link Decimals}）。 */
	private static String ms(long nanos) {
		return Decimals.format3(nanos / 1_000_000.0 / windowTicks);
	}

	/** 打印用的临时行，最多 5 行；total 是排序前一次算好的重量。 */
	private record Row(ServerLevel level, long key, ChunkTiming timing, long total) {
	}

	/** 排序用的临时行：区块连同它的重量。 */
	private record Weighted(SnapshotCodec.ChunkData chunk, long total) {
	}
}
