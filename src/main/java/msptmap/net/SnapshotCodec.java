package msptmap.net;

import io.netty.buffer.Unpooled;
import msptmap.sampler.TickCategory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 快照的数据形状与字节格式。两者同处一个文件：字段变更必然连带编解码。
 *
 * 一次扫描的结果按维度分组，每个区块含坐标、七类耗时与次数、实体数、加载等级与计算等级。
 * 字节上统一使用 varint/varlong：耗时为纳秒，多数在数千至数百万之间，比定长 long 省约一半流量。
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
	 */
	public record ChunkData(int x, int z, long[] nanos, int[] counts, int entities, int loadLevel, int computeLevel) {
		/** 各类耗时之和。除以窗口 tick 数即 ms/tick，故不单独传输。 */
		public long totalNanos() {
			long total = 0L;
			for (long value : nanos) {
				total += value;
			}
			return total;
		}
	}

	/** 一个维度的一批区块。 */
	public record DimensionData(Identifier dimension, List<ChunkData> chunks) {
	}

	public static final StreamCodec<FriendlyByteBuf, ChunkData> CHUNK = StreamCodec.of(
			(buf, chunk) -> {
				buf.writeVarInt(chunk.x());
				buf.writeVarInt(chunk.z());
				for (int i = 0; i < TickCategory.values().length; i++) {
					buf.writeVarLong(chunk.nanos()[i]);
					buf.writeVarInt(chunk.counts()[i]);
				}
				buf.writeVarInt(chunk.entities());
				buf.writeVarInt(chunk.loadLevel());
				buf.writeVarInt(chunk.computeLevel());
			},
			buf -> {
				int x = buf.readVarInt();
				int z = buf.readVarInt();
				long[] nanos = new long[TickCategory.values().length];
				int[] counts = new int[TickCategory.values().length];
				for (int i = 0; i < nanos.length; i++) {
					nanos[i] = buf.readVarLong();
					counts[i] = buf.readVarInt();
				}
				int entities = buf.readVarInt();
				int loadLevel = buf.readVarInt();
				int computeLevel = buf.readVarInt();
				return new ChunkData(x, z, nanos, counts, entities, loadLevel, computeLevel);
			});

	/**
	 * 一串区块。{@code ByteBufCodecs.list()} 先写入元素个数、再逐个写入元素，无需手写循环；
	 * 尖括号内的类型必须显式写出，否则编译失败。
	 */
	private static final StreamCodec<FriendlyByteBuf, List<ChunkData>> CHUNKS =
			ByteBufCodecs.<FriendlyByteBuf, ChunkData>list().apply(CHUNK);

	public static final StreamCodec<FriendlyByteBuf, DimensionData> DIMENSION = StreamCodec.of(
			(buf, dimension) -> {
				Identifier.STREAM_CODEC.encode(buf, dimension.dimension());
				CHUNKS.encode(buf, dimension.chunks());
			},
			buf -> {
				Identifier dimension = Identifier.STREAM_CODEC.decode(buf);
				return new DimensionData(dimension, CHUNKS.decode(buf));
			});

	/** 一次扫描的全部维度。字节上限由采样器控制（见 MsptSampler.MAX_SNAPSHOT_BYTES）。 */
	public static final StreamCodec<FriendlyByteBuf, List<DimensionData>> DIMENSIONS =
			ByteBufCodecs.<FriendlyByteBuf, DimensionData>list().apply(DIMENSION);

	/** {@link #encodedSize} 复用的缓冲。仅服务端主线程使用。 */
	private static final FriendlyByteBuf SCRATCH = new FriendlyByteBuf(Unpooled.buffer(64));

	/**
	 * 计算单个区块编码后的字节数。
	 *
	 * 用真实编解码器写入临时缓冲后取长度，不自行计算 varint 位数：后者重复实现字节格式，
	 * 字段变更时不会同步。
	 */
	public static int encodedSize(ChunkData chunk) {
		SCRATCH.clear();
		CHUNK.encode(SCRATCH, chunk);
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
