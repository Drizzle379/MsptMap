package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import msptmap.util.Clamp;
import msptmap.util.Decimals;
import msptmap.util.Ids;
import msptmap.MsptMapMod;
import msptmap.monitor.MsptAlert;
import msptmap.net.ScanResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
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

	/** 未指定秒数时的默认值。 */
	public static final int DEFAULT_SECONDS = 2;

	/** 正在采样。热路径只读这一个字段。 */
	private static boolean sampling;

	/** 每秒刻数，窗口长度与客户端进度圈（ScanProgress）共用此口径。 */
	public static final int TICKS_PER_SECOND = 20;

	/** 进度包发送间隔：2 刻 = 0.1 秒。 */
	private static final int PROGRESS_EVERY_TICKS = 2;

	/** 窗口内已过的服务端 tick 数：mspt 的分母，数够「秒数 × TICKS_PER_SECOND」即收尾。 */
	private static int windowTicks;

	/** 窗口内各 tick 耗时之和（纳秒）：总览「区块合计占整 tick」百分比的分母。 */
	private static long windowTickNanos;

	/** 本次窗口的秒数（已夹取），随「开始」包发回客户端。 */
	private static int seconds;

	/** 本次扫描的发起人：客户端请求为玩家，服务端命令 / 控制台为 null。 */
	private static ServerPlayer requester;

	/** 本次结果是否广播给在线 OP（由 {@link msptmap.monitor.MsptMonitor} 触发的自动扫描）。 */
	private static boolean broadcast;

	/** 广播时的服务端句柄：出结果时从这里取在线玩家列表；非广播时为 null。 */
	private static MinecraftServer broadcastServer;

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

	/** 本 tick 的起始时刻（START_SERVER_TICK 记），与本 tick 收尾时刻之差即整 tick 耗时；0 = 尚未记过。 */
	private static long tickStartNanos;

	/**
	 * 上一 tick 收尾、本 tick 才发的完成包（见 {@link #finish()}）；没有待发时为 null。
	 *
	 * <p>压后一 tick 是为了让客户端有整整一 tick 把进度圈画满：窗口最后一刻直接发送的话，圈会停在
	 * 差两刻未满处直接消失，观感如同未转满即中断。
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
		return startInternal(seconds, requester, false, null);
	}

	/**
	 * 开一个 N 秒的窗口，结果广播给在线 OP：由 {@link msptmap.monitor.MsptMonitor} 在 MSPT 持续
	 * 超标时发起。没有发起人，也没有进度包（无人在等，逐个 OP 推包纯属浪费）。
	 */
	public static StartResult startAuto(int seconds, MinecraftServer server) {
		return startInternal(seconds, null, true, server);
	}

	/** 开窗本体。发起人、广播标记与服务端句柄三选一（发起人与广播互斥）。 */
	private static StartResult startInternal(int seconds, ServerPlayer requester, boolean broadcast,
			MinecraftServer server) {
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
		// 开窗等待——单人档在地图上点按钮时世界本就暂停，关掉地图即会开始数刻
		if (stalled && requester == null) {
			return StartResult.STALLED;
		}
		MsptSampler.requester = requester;
		MsptSampler.broadcast = broadcast;
		MsptSampler.broadcastServer = server;
		MsptSampler.seconds = clampSeconds(seconds);
		clearTimings();
		windowTicks = 0;
		windowTickNanos = 0L;
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
		broadcast = false;
		broadcastServer = null;
		windowTicks = 0;
		windowTickNanos = 0L;
		pendingPlayer = null;
		pendingDone = null;
		clearTimings();
		lastEndNanos = 0L;
	}

	/** 每个服务端 tick 开始时调用一次：记下起始时刻，供 {@link #onServerTick} 算出整 tick 耗时。 */
	public static void onTickStart() {
		tickStartNanos = System.nanoTime();
	}

	/**
	 * 每个服务端 tick 结束时调用一次：数够窗口刻数即收尾，收尾前每 0.1 秒推一次进度包
	 * （最后一刻改在 {@link #finish()} 中推满格，完成包再压后一 tick）。
	 *
	 * <p>时间戳与是否采样无关：未采样时也要记录，停滞判定（{@link #stalled}）依赖它。
	 */
	public static void onServerTick() {
		long now = System.nanoTime();
		lastTickNanos = now;
		if (pendingDone != null) {
			sendPendingDone();
		}
		if (!sampling) {
			return;
		}
		// 本 tick 的耗时（起止之差，不含刻与刻之间的等待，与常态监控同口径）；未记过起始时刻则跳过
		if (tickStartNanos != 0L) {
			windowTickNanos += now - tickStartNanos;
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

	/**
	 * 收尾：结果打包给发起人；自动扫描的结果广播给在线 OP；发不回去（控制台发起 / 中途掉线）则打到
	 * 服务端控制台。
	 */
	private static void finish() {
		sampling = false;
		long finishStartNanos = System.nanoTime();
		// 窗口到此结束：冷却时间从这里起算
		lastEndNanos = finishStartNanos;
		ServerPlayer player = requester;
		requester = null;
		MinecraftServer server = broadcastServer;
		boolean toOperators = broadcast;
		broadcastServer = null;
		broadcast = false;
		try {
			if (player != null && ServerPlayNetworking.canSend(player, ScanResultPayload.TYPE)) {
				// 先补一格满格进度，完成包压到下一 tick（见 pendingDone）：圈画满整整一 tick 再消失
				ServerPlayNetworking.send(player, ScanResultPayload.progress(seconds, windowTicks));
				pendingDone = ScanResultPayload.done(seconds, windowTicks, windowTickNanos, SnapshotBuilder.build(timings));
				pendingPlayer = player;
			} else if (toOperators && server != null) {
				broadcastToOperators(server);
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
	 * 自动扫描收尾：TOP 区块走聊天告警发给受众，热力数据同步给其中装了本模组（能收包）的。
	 *
	 * <p>须在 {@link #clearTimings()} 之前执行（快照与排行都读采样表）。
	 */
	private static void broadcastToOperators(MinecraftServer server) {
		List<ServerPlayer> targets = MsptAlert.targets(server);
		if (targets.isEmpty()) {
			// 扫描期间受众全部下线了：结果别彻底消失，留一份到控制台
			logTopChunks();
			return;
		}
		List<ServerPlayer> receivers = new ArrayList<>(targets.size());
		for (ServerPlayer target : targets) {
			if (ServerPlayNetworking.canSend(target, ScanResultPayload.TYPE)) {
				receivers.add(target);
			}
		}
		// 一个能收包的都没有就不构建快照（实体遍历 + 逐块编码，成本不低），只发聊天告警
		if (!receivers.isEmpty()) {
			ScanResultPayload done = ScanResultPayload.done(seconds, windowTicks, windowTickNanos, SnapshotBuilder.build(timings));
			for (ServerPlayer receiver : receivers) {
				ServerPlayNetworking.send(receiver, done);
			}
		}
		MsptAlert.send(targets, topRows(MsptAlert.TOP_ROWS), windowTicks);
		MsptMapMod.LOGGER.info("自动扫描结束：告警发给 {} 名受众，热力数据发给其中 {} 名",
				targets.size(), receivers.size());
	}

	/**
	 * 窗口内最重的 n 个区块，跨维度降序；一个区块都没测到时为空列表。
	 *
	 * <p>须在 {@link #clearTimings()} 之前调用。
	 */
	public static List<Heavy> topRows(int n) {
		List<Row> rows = sortedRows();
		int count = Math.min(n, rows.size());
		List<Heavy> heaviest = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Row row = rows.get(i);
			heaviest.add(new Heavy(Ids.id(row.level.dimension()), ChunkPos.getX(row.key),
					ChunkPos.getZ(row.key), row.total));
		}
		return heaviest;
	}

	/** 窗口内全部区块按重量降序；重量一次算好，不在比较器里反复求和。 */
	private static List<Row> sortedRows() {
		List<Row> rows = new ArrayList<>();
		timings.forEach((level, chunks) -> chunks.forEach(
				(key, timing) -> rows.add(new Row(level, key, timing, timing.totalNanos()))));
		rows.sort((a, b) -> Long.compare(b.total, a.total));
		return rows;
	}

	/** 一个卡顿区块：维度 ID、区块坐标与窗口内总耗时（纳秒）。 */
	public record Heavy(String dimension, int chunkX, int chunkZ, long totalNanos) {
	}

	/** 结果发不回客户端时，把最重的 5 个区块打到服务端控制台。 */
	private static void logTopChunks() {
		List<Row> rows = sortedRows();
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
}
