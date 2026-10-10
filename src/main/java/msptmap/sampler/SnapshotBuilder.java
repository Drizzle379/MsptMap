package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import msptmap.util.ChunkKeys;
import msptmap.util.Ids;
import msptmap.MsptMapMod;
import msptmap.mixins.ChunkMapAccessor;
import msptmap.net.SnapshotCodec;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 快照构建：把采样表转换成可发送的 {@link SnapshotCodec.DimensionData} 列表。
 *
 * <p>等级、加载来源与实体数均为取样时刻的瞬时值（每次查询都是一次哈希查找，不宜放进采样热路径），
 * 故整段只在窗口收尾时同步执行一次。
 */
public final class SnapshotBuilder {
	/**
	 * 一次扫描所有维度合计的字节预算：原版自定义包上限 1MB，此处留约三成余量（单区块编码后约
	 * 14~28 字节）。
	 */
	public static final int MAX_SNAPSHOT_BYTES = 700 * 1024;

	private SnapshotBuilder() {
	}

	/**
	 * 将采样表转换为可发送的快照，同时取两个等级，取值状态为出快照这一刻。
	 *
	 * <p>字节预算按维度平分，避免外层表的遍历顺序决定谁先吃光预算。
	 *
	 * @param timings 每个维度一张采样表（外层按对象身份比，内层按区块坐标免装箱）
	 */
	public static List<SnapshotCodec.DimensionData> build(
			Map<ServerLevel, Long2ObjectOpenHashMap<ChunkTiming>> timings) {
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
			// simulate=false 取加载等级，true 取计算等级（与 /chunkloadinfo 同一个方法）
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
			out.add(chunkData(key, timing.nanosArray(), timing.countsArray(), loadLevel, computeLevel,
					entityCounts, loadTickets, simTickets));
		});
		// 按重量降序：装不下时移除的必为尾部最轻的。重量先一次算好再排序；若在比较器中现算
		// totalNanos()，每次比较都要重加那六个数，数万区块时即为数十万次重复求和
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
	 * 不应报为异常，否则视距内、模拟距离外的一圈会布满星号。
	 *
	 * @param expected 该链是否本该覆盖本区块
	 */
	private static int ticketCode(Long2IntOpenHashMap sources, long key, boolean expected) {
		int code = sources.get(key);
		// 表里不存 NONE（0），所以取出 0 即代表没有
		return code == 0 ? TicketCode.encode(TicketCode.NONE, 0, 0, expected) : code;
	}

	/**
	 * 生成快照时每个区块的实体数（含乘客）。与耗时不同，此为瞬时值而非窗口内累计，故不放入
	 * 热路径：每维度遍历一遍全部实体，仅在收尾时进行一次。
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
	 * 把「加载着但窗口内无计时」的区块补进快照，耗时与次数全填 0，客户端据此绘制淡灰色。
	 *
	 * <p>只收加载等级 ≤ 32 的（31 实体刻 / 32 方块刻）；33 及以上完全不 tick。等级直接读
	 * {@code ChunkHolder.getTicketLevel()}，与 {@code getChunkLevel(key, false)} 同源。这批区块
	 * 没有轻重可挑，装不下即中止，并留一行日志。
	 *
	 * <p>中心区块（见 {@link TicketSources#isCenter}）不在此列：先于可见区块补上、不计预算。中心
	 * 不在视距内（远程 forceload、传送门）或没有耗时记录时，只有先补才能保证客户端绘制出中心蓝框。
	 */
	private static void addLoadedChunks(ServerLevel level, DistanceManager distanceManager, LongOpenHashSet measured,
			Long2IntOpenHashMap entityCounts, Long2IntOpenHashMap loadTickets, Long2IntOpenHashMap simTickets,
			List<SnapshotCodec.ChunkData> out, int budget) {
		// out 中已有的键：实测留下的与下面补的中心；实测但被预算裁掉的不在其中
		LongOpenHashSet included = new LongOpenHashSet(Math.max(16, out.size()));
		for (SnapshotCodec.ChunkData chunk : out) {
			included.add(ChunkKeys.pack(chunk.x(), chunk.z()));
		}
		//? if >=1.21.5 {
		// 一、先补两条链的中心：每张票一个、数量少，不计预算（1.21.4 及以前来源降级，表为空）
		for (Long2IntOpenHashMap tickets : new Long2IntOpenHashMap[] {loadTickets, simTickets}) {
			for (Long2IntMap.Entry entry : tickets.long2IntEntrySet()) {
				long key = entry.getLongKey();
				// add 返回 false 表示快照中已有（同一区块在两条链上均为中心时也只补一次）
				if (TicketCode.isCenter(entry.getIntValue()) && included.add(key)) {
					out.add(loadedChunk(key, distanceManager.getChunkLevel(key, false),
							distanceManager.getChunkLevel(key, true), entityCounts, loadTickets, simTickets));
				}
			}
		}
		//?}
		// 二、其余「加载着、无计时」的可见区块
		int used = 0;
		boolean full = false;
		for (Long2ObjectMap.Entry<ChunkHolder> entry : ((ChunkMapAccessor) (Object) level.getChunkSource().chunkMap)
				.getVisibleChunks().long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			if (measured.contains(key) || included.contains(key)) {
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
			SnapshotCodec.ChunkData chunk = loadedChunk(key, loadLevel, computeLevel,
					entityCounts, loadTickets, simTickets);
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

	/** 「仅加载、无计时」形态的区块数据（耗时与次数全填 0，客户端据此绘制淡灰色）。 */
	private static SnapshotCodec.ChunkData loadedChunk(long key, int loadLevel, int computeLevel,
			Long2IntOpenHashMap entityCounts, Long2IntOpenHashMap loadTickets, Long2IntOpenHashMap simTickets) {
		return chunkData(key, SnapshotCodec.ZERO_NANOS, SnapshotCodec.ZERO_COUNTS,
				loadLevel, computeLevel, entityCounts, loadTickets, simTickets);
	}

	/**
	 * 组装一个区块的数据；实测区块与仅加载区块共用（前者给 {@code timing} 的两组数组，后者传
	 * {@link SnapshotCodec#ZERO_NANOS}）。
	 */
	private static SnapshotCodec.ChunkData chunkData(long key, long[] nanos, int[] counts,
			int loadLevel, int computeLevel, Long2IntOpenHashMap entityCounts,
			Long2IntOpenHashMap loadTickets, Long2IntOpenHashMap simTickets) {
		return new SnapshotCodec.ChunkData(ChunkPos.getX(key), ChunkPos.getZ(key), nanos, counts,
				// key 为原始 long，走原始版 get(long)（缺省值 0）；装箱版 get(Object) 对不存在的键
				// 在部分 fastutil 版本中返回 null，拆箱即 NPE（1.21.8 实机曾触发）
				entityCounts.get(key), loadLevel, computeLevel,
				//? if >=1.21.5 {
				ticketCode(loadTickets, key, ChunkLevel.isBlockTicking(loadLevel)),
				ticketCode(simTickets, key, ChunkLevel.isBlockTicking(computeLevel)));
				//?} else {
				/*TicketCode.NONE,
				TicketCode.NONE);
				*///?}
	}

	/** 排序用的临时行：区块连同它的重量。 */
	private record Weighted(SnapshotCodec.ChunkData chunk, long total) {
	}
}
