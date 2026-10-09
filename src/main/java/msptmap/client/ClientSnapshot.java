package msptmap.client;

import msptmap.net.SnapshotCodec;
import msptmap.sampler.TickCategory;
import msptmap.sampler.TicketCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端持有的最后一份扫描结果：按维度分组，且已转换为绘制用的形状。
 *
 * <p>本类不涉及任何 Xaero 代码（坐标只到世界方块坐标），将来若要同步显示到小地图，只需另写绘制
 * 入口。写入只发生在收到 DONE 包时，且在客户端主线程（Fabric 将客户端 play 包派发到
 * packetProcessor 线程，即主线程），故渲染时读取无需加锁。
 */
public final class ClientSnapshot {
	/**
	 * 一个区块在屏幕上的矩形。坐标为世界方块坐标（chunkX << 4 到 +16）：不减相机（相机每帧才确定，
	 * 提前计入等于固定），也不做维度缩放（依据见 {@link MapOverlay}）。
	 *
	 * <p>{@code timed} = 本段窗口内测到过耗时；false 表示仅被服务端加载、整段窗口无计时，铺淡灰——
	 * 「未测到」与「测到 0」不同。
	 *
	 * <p>{@code entities} 是服务端出快照那一刻该区块的实体数（含乘客），不是窗口内的平均值。
	 *
	 * <p>{@code loadTicket}/{@code simTicket} 是两条链各自的加载来源（编码见
	 * {@link msptmap.sampler.TicketCode}），供悬停详情写出「 · 玩家加载中心」一类的后缀。
	 */
	public record Chunk(int x1, int z1, int x2, int z2, float mspt, int loadLevel, int computeLevel, int entities,
		int loadTicket, int simTicket, boolean timed, long[] nanos) {
	}

	/** 最重榜的容量（「卡顿区块 TOP5」的 5）。 */
	private static final int TOP_COUNT = 5;

	/** 最重榜上的一格：区块（区块坐标）及其总耗时。跨维度排名，故记住所属维度。 */
	public record Heavy(long totalNanos, int chunkX, int chunkZ, String dimension) {
	}

	/**
	 * 一个加载源：票种序号 + 中心区块坐标 + 所属维度；与蓝框、「加载源」计数同源（见 {@link #accept}），
	 * 每区块至多一个（两条链都命中时取加载链），坐标即中心区块自身（{@link TicketCode#isCenter}）。
	 */
	public record Source(int type, int chunkX, int chunkZ, String dimension) {
	}

	/**
	 * 扫描总览用的合计：三个维度一并累加。
	 *
	 * <p>{@code sourceList} 是全部加载源（票种 + 维度 + 坐标），与蓝框同源（见
	 * {@link TicketCode#isCenter}），已按显示顺序排好（见 {@link #SOURCE_ORDER}）；
	 * {@code heaviest} 按总耗时降序，至多 {@link #TOP_COUNT} 个。
	 *
	 * <p>{@code categoryNanos} 与 {@code totalNanos} 的口径同
	 * {@link SnapshotCodec.ChunkData#totalNanos}：七个类别各自累加，合计不计方块更新；
	 * {@code tickNanos} 为窗口内各 tick 耗时之和（服务端所量），是「区块合计占整 tick」百分比的分母。
	 */
	public record Totals(int chunks, int timed, int entities, List<Source> sourceList,
			List<Heavy> heaviest, long totalNanos, long[] categoryNanos, int windowTicks, long tickNanos) {
	}

	/**
	 * 加载源的显示顺序：票种按 {@link TicketCode#priority}（越具体越前），同票种按维度
	 * （主世界 / 下界 / 末地在前，其余按 ID）、区块 x、z；收快照时一次排定（遍历顺序不稳定）。
	 */
	private static final Comparator<Source> SOURCE_ORDER =
			Comparator.comparingInt((Source source) -> TicketCode.priority(source.type()))
					.thenComparingInt(Source::type)
					.thenComparingInt(source -> dimensionRank(source.dimension()))
					.thenComparing(Source::dimension)
					.thenComparingInt(Source::chunkX)
					.thenComparingInt(Source::chunkZ);

	/** 维度排序档次：三个原版维度按主世界 / 下界 / 末地的习惯序排在前，其余维度按 ID 字典序排在其后。 */
	private static int dimensionRank(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> 0;
			case "minecraft:the_nether" -> 1;
			case "minecraft:the_end" -> 2;
			default -> 3;
		};
	}

	private static final Map<String, Chunk[]> byDimension = new HashMap<>();

	/**
	 * 每个维度本次快照里最重的区块耗时（ms/tick）。相对模式下红点取它——绘制每帧都要读取，故收
	 * 快照时随转换循环一并算好，不从 {@link #byDimension} 中现扫。
	 */
	private static final Map<String, Float> heaviestByDimension = new HashMap<>();

	/** 本次窗口实际经过的 tick 数：各类纳秒换算 ms/tick 的分母。 */
	private static int windowTicks;

	/** 总览的合计（三个维度一并累加）；未扫描、已清屏时为 null。 */
	private static Totals totals;

	private ClientSnapshot() {
	}

	/**
	 * 收下一份结果，整体替换上一次的。空结果同样替换：上一次的颜色不能留在屏幕上。
	 *
	 * <p>三个维度一并累加出总览的合计（{@link Totals}）：Xaero 地图一次只显示一个维度，
	 * 总览则是全局视角。
	 *
	 * @param windowTicks 窗口实际经过的 tick 数，纳秒换算 ms/tick 的分母
	 * @param tickNanos   窗口内各 tick 耗时之和（服务端所量），总览的「区块合计占整 tick」用；0 = 未量到
	 */
	public static void accept(int windowTicks, long tickNanos, List<SnapshotCodec.DimensionData> dimensions) {
		int window = Math.max(1, windowTicks);
		ClientSnapshot.windowTicks = window;
		byDimension.clear();
		heaviestByDimension.clear();
		int chunkCount = 0;
		int timedCount = 0;
		int entityCount = 0;
		List<Source> sourceList = new ArrayList<>();
		long totalNanos = 0L;
		long[] categoryNanos = new long[TickCategory.COUNT];
		List<Heavy> heaviest = new ArrayList<>(TOP_COUNT);
		for (SnapshotCodec.DimensionData dimension : dimensions) {
			List<SnapshotCodec.ChunkData> chunksOf = dimension.chunks();
			String dimensionId = dimension.dimension();
			Chunk[] converted = new Chunk[chunksOf.size()];
			// 一并取出最重的一个（见 heaviestByDimension）；否则需再遍历一遍
			float heaviestHere = 0.0f;
			for (int i = 0; i < converted.length; i++) {
				SnapshotCodec.ChunkData chunk = chunksOf.get(i);
				long total = chunk.totalNanos();
				float value = mspt(total, window);
				heaviestHere = Math.max(heaviestHere, value);
				boolean timedHere = timed(chunk.counts());
				converted[i] = new Chunk(
						chunk.x() << 4,
						chunk.z() << 4,
						(chunk.x() + 1) << 4,
						(chunk.z() + 1) << 4,
						value,
						chunk.loadLevel(),
						chunk.computeLevel(),
						chunk.entities(),
						chunk.loadTicket(),
						chunk.simTicket(),
						timedHere,
						chunk.nanos());
				chunkCount++;
				if (timedHere) {
					timedCount++;
				}
				entityCount += chunk.entities();
				totalNanos += total;
				long[] nanos = chunk.nanos();
				for (int c = 0; c < TickCategory.COUNT; c++) {
					categoryNanos[c] += nanos[c];
				}
				// 加载源与蓝框同源（见 TicketCode.isCenter）：每区块最多记一个，两条链都
				// 命中时加载链优先
				boolean loadCenter = TicketCode.isCenter(chunk.loadTicket());
				if (loadCenter || TicketCode.isCenter(chunk.simTicket())) {
					int code = loadCenter ? chunk.loadTicket() : chunk.simTicket();
					sourceList.add(new Source(TicketCode.type(code), chunk.x(), chunk.z(), dimensionId));
				}
				insertHeaviest(heaviest, total, chunk.x(), chunk.z(), dimensionId);
			}
			byDimension.put(dimensionId, converted);
			heaviestByDimension.put(dimensionId, heaviestHere);
		}
		sourceList.sort(SOURCE_ORDER);
		totals = new Totals(chunkCount, timedCount, entityCount, sourceList, heaviest,
				totalNanos, categoryNanos, window, tickNanos);
	}

	/**
	 * 清屏：丢弃当前结果，地图立即恢复未上色状态（地图上的 ✕ 按钮即此操作）。
	 *
	 * <p>悬停详情与扫描总览无需另行通知：前者先查 {@link #get}、后者先查 {@link #totals}，
	 * 取不到即不显示。窗口 tick 数一并归零。
	 *
	 * @return 丢弃的维度数（供调用方区分「按了没反应」与「本来就是空的」）
	 */
	public static int clear() {
		int dropped = byDimension.size();
		byDimension.clear();
		heaviestByDimension.clear();
		totals = null;
		windowTicks = 0;
		return dropped;
	}

	/** 该维度的数据；本次未扫到（或从未扫描）时为 null。 */
	public static Chunk[] get(String dimension) {
		return byDimension.get(dimension);
	}

	/** 总览的合计；未扫描、已清屏时为 null。 */
	public static Totals totals() {
		return totals;
	}

	/**
	 * 把区块插入最重榜（按总耗时降序，至多 {@link #TOP_COUNT} 个）：找到插入位置后多出的从尾部裁掉。
	 *
	 * <p>只收有耗时的（totalNanos &gt; 0）：仅加载的区块不该出现在最重榜里。
	 */
	private static void insertHeaviest(List<Heavy> top, long totalNanos, int chunkX, int chunkZ, String dimension) {
		if (totalNanos <= 0L) {
			return;
		}
		int at = top.size();
		while (at > 0 && top.get(at - 1).totalNanos() < totalNanos) {
			at--;
		}
		if (at == TOP_COUNT) {
			return;
		}
		top.add(at, new Heavy(totalNanos, chunkX, chunkZ, dimension));
		if (top.size() > TOP_COUNT) {
			top.remove(TOP_COUNT);
		}
	}

	/** 本次窗口经过的 tick 数。悬停详情用它把各类纳秒换算成 ms/tick。 */
	public static int windowTicks() {
		return windowTicks;
	}

	/**
	 * 该维度本次快照里最重的区块耗时（ms/tick）；没有数据时为 0。
	 *
	 * 相对模式下红点取它（见 {@link MapOverlay#draw}），收快照时已一并算好。
	 */
	public static float heaviestMspt(String dimension) {
		Float heaviest = heaviestByDimension.get(dimension);
		return heaviest == null ? 0.0f : heaviest;
	}

	/**
	 * 鼠标所指区块；未扫到（或无该维度数据）时为 null。
	 *
	 * 线性查找：一个维度最多几千个（上限由服务端字节预算决定），比热力图每帧绘制一遍更便宜。
	 */
	public static Chunk find(String dimension, int chunkX, int chunkZ) {
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

	/** 各类次数只要有一项非 0，即该区块在本段窗口内被计时过。 */
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
