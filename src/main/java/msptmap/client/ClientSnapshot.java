package msptmap.client;

import msptmap.net.SnapshotCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端手上最后一份扫描结果：按维度分好，且已换成绘制用的形状。
 *
 * 该类不涉及任何 Xaero 代码（坐标只到世界方块坐标）：将来若要同步显示到小地图，只需另写绘制入口
 * 写入只发生在收到 DONE 包时，且为客户端主线程（Fabric 把客户端 play 包派发到
 * packetProcessor 线程，即主线程），故渲染时读取无需加锁。
 */
public final class ClientSnapshot {
	/**
	 * 一个区块在屏幕上的矩形。坐标为世界方块坐标（chunkX << 4 到 +16）：不减相机（相机每帧才确定，
	 * 提前算进去等于固定），也不做维度缩放（依据见 {@link MapOverlay}）。
	 *
	 * {@code timed} = 本段窗口内测到过耗时：false 表示只被服务端加载、整段窗口无计时，铺淡灰 ——
	 * 「未测到」与「测到 0」不同。
	 */
	public record Chunk(int x1, int z1, int x2, int z2, float mspt, int loadLevel, int computeLevel, boolean timed,
		long[] nanos, int[] counts) {
	}

	private static final Map<Identifier, Chunk[]> byDimension = new HashMap<>();

	/** 本次窗口实际经过的 tick 数：五类纳秒换算 ms/tick 的分母。 */
	private static int windowTicks;

	private ClientSnapshot() {
	}

	/**
	 * 收下一份结果，整体替换上一次的。空结果同样替换：上一次的颜色不能留在屏幕上。
	 *
	 * @param windowTicks 窗口实际经过的 tick 数，纳秒换算 ms/tick 的分母
	 */
	public static void accept(int windowTicks, List<SnapshotCodec.DimensionData> dimensions) {
		ClientSnapshot.windowTicks = Math.max(1, windowTicks);
		byDimension.clear();
		for (SnapshotCodec.DimensionData dimension : dimensions) {
			List<SnapshotCodec.ChunkData> chunks = dimension.chunks();
			Chunk[] converted = new Chunk[chunks.size()];
			for (int i = 0; i < converted.length; i++) {
				SnapshotCodec.ChunkData chunk = chunks.get(i);
				converted[i] = new Chunk(
						chunk.x() << 4,
						chunk.z() << 4,
						(chunk.x() + 1) << 4,
						(chunk.z() + 1) << 4,
						mspt(chunk.totalNanos(), windowTicks),
						chunk.loadLevel(),
						chunk.computeLevel(),
						timed(chunk.counts()),
						chunk.nanos(),
						chunk.counts());
			}
			byDimension.put(dimension.dimension(), converted);
		}
	}

	/**
	 * 清屏：丢弃当前结果，地图立即恢复未上色状态（地图上那个 ✕ 按钮即此操作）。
	 *
	 * 悬停详情无需另行通知：它先查 {@link #get}，无该维度数据即不显示。窗口 tick 数一并归零。
	 *
	 * @return 丢弃的维度数（供调用方区分「按了没反应」与「本来就是空的」）
	 */
	public static int clear() {
		int dropped = byDimension.size();
		byDimension.clear();
		windowTicks = 0;
		return dropped;
	}

	/** 该维度的数据；本次未扫到（或从未扫描）时为 null。 */
	public static Chunk[] get(Identifier dimension) {
		return byDimension.get(dimension);
	}

	/** 本次窗口经过的 tick 数。悬停详情用它把五类纳秒换算成 ms/tick。 */
	public static int windowTicks() {
		return windowTicks;
	}

	/**
	 * 鼠标所指区块；未扫到（或无该维度数据）时为 null。
	 *
	 * 线性查找：一个维度最多几千个（上限由服务端字节预算决定），比热力图每帧绘制一遍更便宜。
	 */
	public static Chunk find(Identifier dimension, int chunkX, int chunkZ) {
		Chunk[] chunks = byDimension.get(dimension);
		if (chunks == null) {
			return null;
		}
		for (Chunk chunk : chunks) {
			if ((chunk.x1() >> 4) == chunkX && (chunk.z1() >> 4) == chunkZ) {
				return chunk;
			}
		}
		return null;
	}

	/** 五类次数只要有一项非 0，即该区块在本段窗口内被计时过。 */
	private static boolean timed(int[] counts) {
		for (int count : counts) {
			if (count > 0) {
				return true;
			}
		}
		return false;
	}

	/** 纳秒 → ms/tick。分母兜底，避免窗口 tick 为 0 时除零。 */
	private static float mspt(long totalNanos, int windowTicks) {
		return (float) (totalNanos / 1_000_000.0 / Math.max(1, windowTicks));
	}
}
