package msptmap.net;

import io.netty.buffer.Unpooled;
import msptmap.sampler.TickCategory;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 快照的数据形状与字节格式。两者同处一个文件：字段变更必然连带编解码。
 *
 * 一次扫描的结果按维度分组，每个区块含坐标、七类耗时与次数、实体数、加载等级与计算等级。
 * 字节上统一使用 varint/varlong：耗时为纳秒，多数在数千至数百万之间，比定长 long 省约一半流量。
 *
 * 只提供静态的 write/read 方法，不用原版的 StreamCodec（1.20.5 才引入）；1.20.4 及以前的
 * FabricPacket 体系直接手写缓冲区，两代共用这一套方法，字节格式也因此天然一致。
 */
public final class SnapshotCodec {
	private SnapshotCodec() {
	}

	/**
	 * 单个区块的全部数据。数组下标 = {@link TickCategory#ordinal()}，顺序不可变更；数组直接引用采样器
	 * 实例，不复制。
	 *
	 * {@code entities} 是出快照那一刻该区块的实体数，与窗口内是否计时无关。
	 * 耗时与次数全为 0 表示该区块仅被加载（见 MsptSampler.addLoadedChunks），客户端据此铺淡灰。
	 *
	 * {@code loadTicket} / {@code simTicket} 是两条链各自的加载来源，由
	 * {@link msptmap.sampler.TicketSources} 编码（类型序号 + 距离 + 存疑位）。两条链独立，源头可能不同。
	 */
	public record ChunkData(int x, int z, long[] nanos, int[] counts, int entities, int loadLevel, int computeLevel,
			int loadTicket, int simTicket) {
		/** 各类耗时之和。除以窗口 tick 数即 ms/tick，故不单独传输。 */
		public long totalNanos() {
			long total = 0L;
			for (long value : nanos) {
				total += value;
			}
			return total;
		}
	}

	/** 一个维度的一批区块。维度用全名字符串（如 {@code minecraft:overworld}）标识，与资源位置的更名无关。 */
	public record DimensionData(String dimension, List<ChunkData> chunks) {
	}

	/**
	 * 「仅加载」区块共用的全零数组：只有这里持有，且只读地交给 ChunkData。一次扫描里这类区块
	 * 可达数万个，逐个分配两个数组是纯浪费。
	 */
	public static final long[] ZERO_NANOS = new long[TickCategory.COUNT];

	public static final int[] ZERO_COUNTS = new int[TickCategory.COUNT];

	/** 单个区块编码后的最小字节数（坐标为小 varint、七组计数为 0 时约 21 字节，取 20 留余量）。 */
	private static final int MIN_CHUNK_BYTES = 20;

	/** 单个维度编码后的最小字节数（名称长度前缀 1 + 名称 1 + 区块个数 1）。 */
	private static final int MIN_DIMENSION_BYTES = 3;

	/** 写一个区块。 */
	public static void writeChunk(FriendlyByteBuf buf, ChunkData chunk) {
		buf.writeVarInt(chunk.x());
		buf.writeVarInt(chunk.z());
		for (int i = 0; i < TickCategory.COUNT; i++) {
			buf.writeVarLong(chunk.nanos()[i]);
			buf.writeVarInt(chunk.counts()[i]);
		}
		buf.writeVarInt(chunk.entities());
		buf.writeVarInt(chunk.loadLevel());
		buf.writeVarInt(chunk.computeLevel());
		buf.writeVarInt(chunk.loadTicket());
		buf.writeVarInt(chunk.simTicket());
	}

	/** 读一个区块。 */
	public static ChunkData readChunk(FriendlyByteBuf buf) {
		int x = buf.readVarInt();
		int z = buf.readVarInt();
		long[] nanos = new long[TickCategory.COUNT];
		int[] counts = new int[TickCategory.COUNT];
		for (int i = 0; i < nanos.length; i++) {
			nanos[i] = buf.readVarLong();
			counts[i] = buf.readVarInt();
		}
		int entities = buf.readVarInt();
		int loadLevel = buf.readVarInt();
		int computeLevel = buf.readVarInt();
		int loadTicket = buf.readVarInt();
		int simTicket = buf.readVarInt();
		return new ChunkData(x, z, nanos, counts, entities, loadLevel, computeLevel, loadTicket, simTicket);
	}

	/** 一串区块：先个数、再逐个。 */
	public static void writeChunks(FriendlyByteBuf buf, List<ChunkData> chunks) {
		buf.writeVarInt(chunks.size());
		for (ChunkData chunk : chunks) {
			writeChunk(buf, chunk);
		}
	}

	public static List<ChunkData> readChunks(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		// 先按剩余字节数把关再分配：对端声明几亿个区块时 new ArrayList<>(size) 抛的是 OOM（Error），
		// ScanResultPayload.decode 的 catch (Exception) 兜不住、会直接崩；换成运行时异常走 MISMATCH 降级路
		if (size < 0 || size > buf.readableBytes() / MIN_CHUNK_BYTES) {
			throw new IllegalArgumentException("区块个数 " + size + " 与剩余 " + buf.readableBytes() + " 字节不符");
		}
		List<ChunkData> chunks = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			chunks.add(readChunk(buf));
		}
		return chunks;
	}

	public static void writeDimension(FriendlyByteBuf buf, DimensionData dimension) {
		buf.writeUtf(dimension.dimension());
		writeChunks(buf, dimension.chunks());
	}

	public static DimensionData readDimension(FriendlyByteBuf buf) {
		return new DimensionData(buf.readUtf(), readChunks(buf));
	}

	/** 一次扫描的全部维度。字节上限由采样器控制（见 MsptSampler.MAX_SNAPSHOT_BYTES）。 */
	public static void writeDimensions(FriendlyByteBuf buf, List<DimensionData> dimensions) {
		buf.writeVarInt(dimensions.size());
		for (DimensionData dimension : dimensions) {
			writeDimension(buf, dimension);
		}
	}

	public static List<DimensionData> readDimensions(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		// 同 readChunks：按对端声明的个数直接分配会被伪造/错位的大数打成 OOM
		if (size < 0 || size > buf.readableBytes() / MIN_DIMENSION_BYTES) {
			throw new IllegalArgumentException("维度个数 " + size + " 与剩余 " + buf.readableBytes() + " 字节不符");
		}
		List<DimensionData> dimensions = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			dimensions.add(readDimension(buf));
		}
		return dimensions;
	}

	/** {@link #encodedSize} 复用的缓冲。仅服务端主线程使用。 */
	private static final FriendlyByteBuf SCRATCH = new FriendlyByteBuf(Unpooled.buffer(64));

	/**
	 * 计算单个区块编码后的字节数。
	 *
	 * 用真实写入方法写进临时缓冲后取长度，不自行计算 varint 位数：后者重复实现字节格式，
	 * 字段变更时不会同步。
	 */
	public static int encodedSize(ChunkData chunk) {
		SCRATCH.clear();
		writeChunk(SCRATCH, chunk);
		return SCRATCH.readableBytes();
	}

	/**
	 * 按字节预算裁剪区块列表（就地移除尾部放不下的部分），返回保留部分占用的字节数。
	 *
	 * 调用方保证列表已按重量降序排列，被移除的必然是尾部最轻的区块。返回值作为下一批
	 * （仅加载、无计时的区块）的剩余预算。
	 */
	public static int fitToBudget(List<ChunkData> chunks, int budgetBytes) {
		int used = 0;
		for (int i = 0; i < chunks.size(); i++) {
			int size = encodedSize(chunks.get(i));
			if (used + size > budgetBytes) {
				chunks.subList(i, chunks.size()).clear();
				return used;
			}
			used += size;
		}
		return used;
	}
}
