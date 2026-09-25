package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import msptmap.MsptMapMod;
import msptmap.mixins.ChunkMapAccessor;
import msptmap.net.ScanResultPayload;
import msptmap.net.SnapshotCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采样核心：每个区块的耗时记在一张表里，开一个 N 秒的窗口，窗口结束出结果。
 *
 * 只有一个开关 + 一张表，没有后台线程：未采样时注入点只读一个 boolean，
 * 采样时每个事件只记两笔（纳秒、次数），不做除法、不分配对象。
 *
 * 窗口按**服务端 tick 数**收尾（挂在 Fabric API 的 END_SERVER_TICK 上），不看现实时间。
 */
public final class MsptSampler {
	/** 采样秒数上限。 */
	public static final int MAX_SECONDS = 60;

	/**
	 * 一次扫描**所有维度合计**的字节预算。原版自定义包上限 1MB，这里留一倍余量；
	 * 一个区块编码后 14~28 字节。
	 */
	public static final int MAX_SNAPSHOT_BYTES = 700 * 1024;

	/** 正在采样 = true。热路径只读这一个字段。 */
	private static boolean sampling;

	/** 一秒多少刻。窗口长度与客户端进度圈（ScanProgress）的分母共用这一个口径。 */
	public static final int TICKS_PER_SECOND = 20;

	/** 进度包每这么多刻发一次：2 刻 = 0.1 秒。 */
	private static final int PROGRESS_EVERY_TICKS = 2;

	/** 窗口内已过的服务端 tick 数：换算 mspt 的分母，数够 {@code 秒数 × TICKS_PER_SECOND} 即收尾。 */
	private static int windowTicks;

	/** 本次窗口的秒数（已夹取），随「开始」包发回客户端。 */
	private static int seconds;

	/** 本次扫描的发起人：客户端请求为玩家，服务端命令 / 控制台为 null。 */
	private static ServerPlayer requester;

	/** 每个维度一张表：区块坐标 → 账本。外层按对象身份比，内层用 fastutil 的 long 键表（免装箱）。 */
	private static final Map<ServerLevel, Long2ObjectOpenHashMap<ChunkTiming>> timings = new IdentityHashMap<>();

	private MsptSampler() {
	}

	/**
	 * 把请求的秒数夹进合法区间。调用方须用它的返回值：回给客户端的「开始」包要报夹取后的秒数，
	 * 否则进度圈的分母与真实窗口不符。
	 */
	public static int clampSeconds(int seconds) {
		return Math.clamp(seconds, 1, MAX_SECONDS);
	}

	/**
	 * 开一个 N 秒的窗口；已在采样中则返回 false，由调用方决定提示。
	 *
	 * @param requester 本次扫描的发起人：客户端请求为玩家（结果回给他），服务端命令 / 控制台为 null（结果打控制台）
	 */
	public static boolean start(int seconds, ServerPlayer requester) {
		if (sampling) {
			return false;
		}
		MsptSampler.requester = requester;
		MsptSampler.seconds = clampSeconds(seconds);
		timings.clear();
		windowTicks = 0;
		sampling = true;
		return true;
	}

	/** 每个服务端 tick 结束时调用一次：数够窗口刻数即收尾，收尾前每 0.1 秒推一次进度包（最后一刻不推）。 */
	public static void onServerTick() {
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

	/**
	 * 把「窗口已过的刻数」推给发起人：客户端只画服务端报过的数。
	 * 控制台 / 命令方块发起的扫描没有发起人，不发。
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

	/** 计时结束并入账。 */
	public static void end(TickCategory category, ServerLevel level, long chunkKey, long startNanos) {
		if (startNanos == 0L) {
			return;
		}
		timings.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(chunkKey, key -> new ChunkTiming())
				.add(category, System.nanoTime() - startNanos);
	}

	/** 收尾：结果打包给发起人；发不回去（控制台发起 / 中途掉线）则打到服务端控制台。 */
	private static void finish() {
		sampling = false;
		ServerPlayer player = requester;
		requester = null;
		if (player != null && ServerPlayNetworking.canSend(player, ScanResultPayload.TYPE)) {
			ServerPlayNetworking.send(player, ScanResultPayload.done(seconds, windowTicks, snapshot()));
		} else {
			logTopChunks();
		}
	}

	/**
	 * 把采样表变成可发送的快照，同时取两个等级 —— 取的是**出快照这一刻**的值（它随时在变，
	 * 每问一次是一次哈希查找，不适合放在采样热路径上）。
	 *
	 * 字节预算按维度平分，避免外层表的遍历顺序决定谁先吃光预算。
	 */
	private static List<SnapshotCodec.DimensionData> snapshot() {
		List<SnapshotCodec.DimensionData> dimensions = new ArrayList<>(timings.size());
		int share = MAX_SNAPSHOT_BYTES / Math.max(1, timings.size());
		timings.forEach((level, chunks) -> dimensions.add(snapshotDimension(level, chunks, share)));
		return dimensions;
	}

	private static SnapshotCodec.DimensionData snapshotDimension(ServerLevel level, Long2ObjectOpenHashMap<ChunkTiming> chunks,
			int budget) {
		DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
		LongOpenHashSet measured = new LongOpenHashSet(chunks.size());
		List<SnapshotCodec.ChunkData> out = new ArrayList<>(chunks.size());
		chunks.forEach((key, timing) -> {
			measured.add(key);
			out.add(new SnapshotCodec.ChunkData(
					ChunkPos.getX(key),
					ChunkPos.getZ(key),
					timing.nanosArray(),
					timing.countsArray(),
					// simulate=false 取加载等级，true 取计算等级（和 /chunkloadinfo 同一个方法）
					distanceManager.getChunkLevel(key, false),
					distanceManager.getChunkLevel(key, true)));
		});
		// 按重量降序：装不下时移除的必然是尾部最轻的
		out.sort(Comparator.comparingLong(SnapshotCodec.ChunkData::totalNanos).reversed());
		int used = SnapshotCodec.fitToBudget(out, budget);
		if (out.size() < chunks.size()) {
			MsptMapMod.LOGGER.info("维度 {} 超出字节预算：{} 个干过活的区块没带上（最轻的那些）",
					level.dimension().identifier(), chunks.size() - out.size());
		}
		// 有计时的收完，再补「加载着、窗口内无计时」的那些
		addLoadedChunks(level, distanceManager, measured, out, budget - used);
		return new SnapshotCodec.DimensionData(level.dimension().identifier(), out);
	}

	/**
	 * 把「加载着但窗口内无计时」的区块补进快照，耗时与次数全填 0 —— 客户端据此铺淡灰。
	 *
	 * 只收加载等级 ≤ 32 的（31 实体刻 / 32 方块刻）；33 及以上完全不 tick。等级直接读
	 * {@code ChunkHolder.getTicketLevel()}，与 {@code getChunkLevel(key, false)} 同源。
	 *
	 * 这批区块没有轻重可挑，装不下即中止，并留一行日志。
	 */
	private static void addLoadedChunks(ServerLevel level, DistanceManager distanceManager, LongOpenHashSet measured,
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
			SnapshotCodec.ChunkData chunk = new SnapshotCodec.ChunkData(ChunkPos.getX(key), ChunkPos.getZ(key),
					new long[TickCategory.values().length], new int[TickCategory.values().length],
					loadLevel, distanceManager.getChunkLevel(key, true));
			int size = SnapshotCodec.encodedSize(chunk);
			if (used + size > budget) {
				full = true;
				break;
			}
			used += size;
			out.add(chunk);
		}
		if (full) {
			MsptMapMod.LOGGER.info("维度 {} 超出字节预算：一部分只加载着的区块没带上", level.dimension().identifier());
		}
	}

	/** 结果发不回客户端时，把最重的 5 个区块打到服务端控制台。 */
	private static void logTopChunks() {
		List<Row> rows = new ArrayList<>();
		timings.forEach((level, chunks) -> chunks.forEach((key, timing) -> rows.add(new Row(level, key, timing))));
		rows.sort((a, b) -> Long.compare(b.timing.totalNanos(), a.timing.totalNanos()));

		MsptMapMod.LOGGER.info("扫描结束：{} 个维度 / {} 个区块，窗口 {} 秒 / {} tick",
				timings.size(), rows.size(), seconds, windowTicks);

		for (int i = 0; i < Math.min(5, rows.size()); i++) {
			Row row = rows.get(i);
			MsptMapMod.LOGGER.info("  #{} {} ({}, {})  {} mspt [随机刻 {} 计划刻 {} 方块实体 {} 实体 {} 刷怪 {}]",
					i + 1,
					row.level.dimension().identifier(),
					ChunkPos.getX(row.key),
					ChunkPos.getZ(row.key),
					ms(row.timing.totalNanos()),
					ms(row.timing.nanos(TickCategory.RANDOM_TICK)),
					ms(row.timing.nanos(TickCategory.SCHEDULED)),
					ms(row.timing.nanos(TickCategory.BLOCK_ENTITY)),
					ms(row.timing.nanos(TickCategory.ENTITY)),
					ms(row.timing.nanos(TickCategory.SPAWN)));
		}
	}

	/** 纳秒 → mspt 文本（三位小数），仅供打印。 */
	private static String ms(long nanos) {
		return String.format("%.3f", nanos / 1_000_000.0 / windowTicks);
	}

	/** 打印用的临时行，最多 5 行。 */
	private record Row(ServerLevel level, long key, ChunkTiming timing) {
	}
}
