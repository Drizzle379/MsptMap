package msptmap.client;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描总览：挂在扫描按钮右侧的数据框，扫描成功（收到 DONE 包）后出现，清屏后消失；展开与否则由地图
 * 上的折叠钮决定，跨次记忆（见 {@link ClientConfig#summaryExpanded}）。
 *
 * <p>数据是三个维度的合计（见 {@link ClientSnapshot.Totals}）：Xaero 地图一次只显示一个维度，
 * 总览给的是全局视角。加载源逐条列出（票名 @ 维度 坐标，与蓝框同源），超过 {@link #SOURCE_ROWS}
 * 条的折到右侧完整列表（{@link SourceListPanel}），由折叠行开合。组装与绘制分离（同
 * {@link ChunkTooltip}）：{@link #lines} 只拼文本组件（可离线断言），{@link #draw} 只负责绘制。
 * 文本一律经 {@link Component#translatable} 引用语言文件里的键，翻译在绘制时才按当前语言解析。
 */
public final class ScanSummary {
	/** 总览内逐条列出的加载源行数上限；超出的折到右侧完整列表。 */
	private static final int SOURCE_ROWS = 8;

	/** 七类明细数值的着色区间（mspt）：绿点 3、红点 20，均匀过渡（黄在 11.5）；低于绿点也取起点绿。 */
	private static final float CATEGORY_GREEN_MS = 3.0f;
	private static final float CATEGORY_RED_MS = 20.0f;

	/** 「总卡顿」数值的着色区间（mspt）：绿点 10、红点 40，取明细区间两倍；低于绿点同样取起点绿。 */
	private static final float TOTAL_GREEN_MS = 10.0f;
	private static final float TOTAL_RED_MS = 40.0f;

	/** 空结果：清屏（合计取不到）时缓存的形状，也是 {@link #cached} 的初值。 */
	private static final Built EMPTY = new Built(List.of(), List.of(), -1, List.of(), List.of(), 0, 0, -1);

	/** 上次拼行结果与其输入：合计（引用即快照代次）与配置签名，两者全同则复用。 */
	private static ClientSnapshot.Totals cachedTotals;
	/** 初值 -1：与任何真实签名（位掩码，≥ 0）都不同，首帧不会误命中。 */
	private static int cachedSignature = -1;
	private static Built cached = EMPTY;

	/** 右侧完整源列表是否展开：折叠行翻转，打开地图时复位（不落盘）。 */
	private static boolean sourcesExpanded;

	/**
	 * 拼行结果：{@code lines} 为总览全部行；{@code heaviest} 为 TOP 数据行、{@code topFirstRow} 为其
	 * 首行行号；{@code sourceRows} 为源行（一一对应 {@code sources}，总览列前 {@code sourceShown} 条）、
	 * {@code foldRow} 为折叠行行号；行号 -1 均表示无此分区。
	 */
	private record Built(List<Component> lines, List<ClientSnapshot.Heavy> heaviest, int topFirstRow,
			List<ClientSnapshot.Source> sources, List<Component> sourceRows, int sourceFirstRow,
			int sourceShown, int foldRow) {
	}

	/** 点击定位的目标：维度 + 区块坐标；TOP 数据行与加载源行共用。 */
	public record Target(String dimension, int chunkX, int chunkZ) {
		static Target of(ClientSnapshot.Heavy heavy) {
			return new Target(heavy.dimension(), heavy.chunkX(), heavy.chunkZ());
		}

		static Target of(ClientSnapshot.Source source) {
			return new Target(source.dimension(), source.chunkX(), source.chunkZ());
		}
	}

	private ScanSummary() {
	}

	/**
	 * 总览要显示的每一行；未扫描、已清屏（合计取不到）时返回空列表，调用方见到空列表即不画面板。
	 *
	 * <p>每帧都会被调用到这里，而输入（合计、三个开关）在两次绘制之间通常不变，故按输入缓存。
	 */
	public static List<Component> lines() {
		ClientSnapshot.Totals totals = ClientSnapshot.totals();
		if (totals == null) {
			// 清屏时必须连缓存一并作废：残留的旧 foldRow 会让右框在无数据时仍被判为展开
			cachedTotals = null;
			cachedSignature = -1;
			cached = EMPTY;
			return List.of();
		}
		int signature = signature();
		if (totals == cachedTotals && signature == cachedSignature) {
			return cached.lines();
		}
		cachedTotals = totals;
		cachedSignature = signature;
		cached = buildLines(totals);
		return cached.lines();
	}

	/** 拼行本体（不含缓存）。 */
	private static Built buildLines(ClientSnapshot.Totals totals) {
		int windowTicks = totals.windowTicks();
		List<Component> lines = new ArrayList<>();
		lines.add(title("msptmap.summary.title"));
		// 方括号内为本段窗口测到过耗时的区块数，· 后为窗口实际秒数
		lines.add(Component.translatable("msptmap.summary.chunks", totals.chunks(), totals.timed(),
				windowTicks / MsptSampler.TICKS_PER_SECOND));
		lines.add(Component.translatable("msptmap.summary.entities", totals.entities()));
		lines.add(Component.translatable("msptmap.summary.sources", totals.sourceList().size()));
		// 加载源逐条列出（与「加载源」同源，见 ClientSnapshot.accept）：一行一个源，票名 @ 维度 坐标
		List<ClientSnapshot.Source> sources = totals.sourceList();
		List<Component> sourceRows = new ArrayList<>(sources.size());
		for (ClientSnapshot.Source source : sources) {
			sourceRows.add(sourceLine(source));
		}
		int sourceFirstRow = lines.size();
		int sourceShown = Math.min(SOURCE_ROWS, sourceRows.size());
		lines.addAll(sourceRows.subList(0, sourceShown));
		int foldRow = -1;
		if (sourceRows.size() > SOURCE_ROWS) {
			foldRow = lines.size();
			lines.add(foldLine(sourceRows.size() - SOURCE_ROWS));
		}
		// 总卡顿：单位固定显示（同悬停详情的「合计」行）；着色开启时按固定区间给数值铺色、低于绿点取
		// 起点绿，名称与单位保持白字（一级行不压暗）
		float totalMspt = mspt(totals.totalNanos(), windowTicks);
		String totalValue = ChunkTooltip.ms(totals.totalNanos(), windowTicks);
		lines.add(ClientConfig.summaryColored
				? ChunkTooltip.msptLine(Component.translatable("msptmap.summary.total"), totalValue,
						rangeColor(totalMspt, TOTAL_GREEN_MS, TOTAL_RED_MS))
				: ChunkTooltip.msptLine(Component.translatable("msptmap.summary.total"), totalValue));
		// 七类明细：整体缩进、类别名附注灰；着色开启时梯度色只给数值（压暗一档），星号与斜体照旧
		for (TickCategory category : ChunkTooltip.ORDER) {
			long nanos = totals.categoryNanos()[category.ordinal()];
			float categoryMspt = mspt(nanos, windowTicks);
			lines.add(ChunkTooltip.secondary(ClientConfig.summaryColored
					? ChunkTooltip.categoryLine(nanos, windowTicks, category,
							soften(rangeColor(categoryMspt, CATEGORY_GREEN_MS, CATEGORY_RED_MS)))
					: ChunkTooltip.categoryLine(nanos, windowTicks, category)));
		}
		// 卡顿区块 TOP5：分区标题白字不加粗；一个都没有时连分区标题都不出现。着色时数值色与地图同源
		// （红点：固定模式读红色阈值、相对模式读该维度最重的区块），再压暗一档
		List<ClientSnapshot.Heavy> heaviest = totals.heaviest();
		int topFirstRow = -1;
		if (!heaviest.isEmpty()) {
			lines.add(Component.translatable("msptmap.summary.top_title"));
			topFirstRow = lines.size();
			for (ClientSnapshot.Heavy heavy : heaviest) {
				String value = ChunkTooltip.ms(heavy.totalNanos(), windowTicks);
				Component shown = ClientConfig.summaryColored
						? ChunkTooltip.colored(value, soften(heatColor(mspt(heavy.totalNanos(), windowTicks),
								MapOverlay.redPoint(ClientSnapshot.heaviestMspt(heavy.dimension())))))
						: Component.literal(value);
				lines.add(ChunkTooltip.secondary(Component.translatable("msptmap.summary.line", shown,
						Component.translatable(dimensionKey(heavy.dimension())),
						heavy.chunkX(), heavy.chunkZ())));
			}
		}
		return new Built(lines, heaviest, topFirstRow, sources, sourceRows, sourceFirstRow, sourceShown, foldRow);
	}

	/** 按热力图同一公式（{@link MapOverlay#red} / {@link MapOverlay#green}）把耗时换算成 0xRRGGBB 文字色；TOP5 行用它。 */
	private static int heatColor(float mspt, float redPoint) {
		int r = Math.round(MapOverlay.red(mspt, redPoint) * 255.0f);
		int g = Math.round(MapOverlay.green(mspt, redPoint) * 255.0f);
		return r << 16 | g << 8;
	}

	/**
	 * 绿点与红点之间均匀渐变（黄在正中间）的 0xRRGGBB，低于绿点取起点绿；明细与合计的固定区间
	 * 用它——热力图那条公式的黄点在红点 1/3 处，低段颜色变化过快。
	 */
	private static int rangeColor(float mspt, float greenMspt, float redMspt) {
		float t = Math.min(1.0f, Math.max(0.0f, (mspt - greenMspt) / (redMspt - greenMspt)));
		float twoT = t * 2.0f;
		int red = Math.round(Math.min(1.0f, twoT) * 255.0f);
		int green = Math.round(Math.min(1.0f, 2.0f - twoT) * 255.0f);
		return red << 16 | green << 8;
	}

	/** 明细与 TOP5 行的数值柔化：每通道 ×0xD0/0xFF（比灰字档 0xB0 亮约 18%），色相不变。 */
	private static int soften(int rgb) {
		return ((rgb >> 16 & 0xFF) * 0xD0 / 0xFF) << 16 | ((rgb >> 8 & 0xFF) * 0xD0 / 0xFF) << 8;
	}

	/** 纳秒 → ms/tick，与 {@link ChunkTooltip#ms} 的字符串同口径。 */
	private static float mspt(long nanos, int windowTicks) {
		return (float) (nanos / 1_000_000.0 / Math.max(1, windowTicks));
	}

	/** 标题行：加粗白字，不缩进。 */
	private static Component title(String key) {
		return Component.translatable(key).withStyle(style -> style.withBold(true));
	}

	/** 一条加载源行：票名 + 维度 + 中心区块坐标，与 TOP 数据行同构（同为二级条目）。 */
	private static Component sourceLine(ClientSnapshot.Source source) {
		return ChunkTooltip.secondary(Component.translatable("msptmap.summary.line",
				Component.translatable(ChunkTooltip.ticketName(source.type())),
				Component.translatable(dimensionKey(source.dimension())),
				source.chunkX(), source.chunkZ()));
	}

	/** 折叠行：右侧完整列表展开与否两态；收起时带被折起的源数。 */
	private static Component foldLine(int hidden) {
		return ChunkTooltip.secondary(sourcesExpanded
				? Component.translatable("msptmap.summary.sources_less")
				: Component.translatable("msptmap.summary.sources_more", hidden));
	}

	/**
	 * 维度名的语言键：三个原版维度用本模组的短名（中文「主世界 / 下界 / 末地」；原版英文
	 * 「The Nether / The End」是两个词，这里改用一词的 Nether / End）；其余维度回退原版键
	 * {@code dimension.<命名空间>.<路径>}（点号形式，同 Util.makeDescriptionId）。
	 */
	private static String dimensionKey(String dimId) {
		return switch (dimId) {
			case "minecraft:overworld" -> "msptmap.dimension.overworld";
			case "minecraft:the_nether" -> "msptmap.dimension.nether";
			case "minecraft:the_end" -> "msptmap.dimension.end";
			default -> "dimension." + dimId.replace(':', '.');
		};
	}

	/**
	 * 参与拼行的配置项压成位掩码：与悬停详情共用的两个开关（类别名缩写、明细单位）各占一位，
	 * 右侧列表的开关也占一位（折叠行文案随之变），着色开关与它的两个输入（相对模式、红色阈值——
	 * TOP5 行的颜色跟随地图红点）同样计入，任一变动则签名改变，缓存随之作废。
	 *
	 * <p>类别勾选不进签名：它只决定悬停详情显示哪几行，总览的七类明细恒全列。
	 */
	private static int signature() {
		int bits = 0;
		if (ClientConfig.tooltipAbbreviate) {
			bits |= 1 << 0;
		}
		if (ClientConfig.tooltipMsptUnit) {
			bits |= 1 << 1;
		}
		if (sourcesExpanded) {
			bits |= 1 << 2;
		}
		if (ClientConfig.summaryColored) {
			bits |= 1 << 3;
		}
		if (ClientConfig.relativeColor) {
			bits |= 1 << 4;
		}
		// 红色阈值量化成两位整数（5~500），占 bit 5..13
		bits |= (int) Math.round(ClientConfig.redAt * 100.0) << 5;
		return bits;
	}

	/** 总览框尺寸（宽, 高）；调用方据此把右侧完整列表摆在紧邻的右边。 */
	public static int[] panelSize() {
		return MapPanel.size(lines());
	}

	/**
	 * 以 ({@code x}, {@code y}) 为左上角绘制总览。无行可画时直接返回，不留空框；鼠标下的可点击行
	 * （TOP 数据行、加载源行、折叠行）垫一层淡白底，提示可点击。
	 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
	*///?}
		List<Component> lines = lines();
		if (lines.isEmpty()) {
			return;
		}
		int[] size = MapPanel.size(lines);
		int row = MapPanel.rowAt(mouseX, mouseY, x, y, size);
		MapPanel.draw(graphics, x, y, size, lines, clickableRow(row) ? row : -1);
	}

	/**
	 * 鼠标是否落在总览框上（收起、无数据时恒 false）。悬停详情绘制在总览之上，重叠处让其不画。
	 * {@code panelX}/{@code panelY} 是总览左上角，与 {@link #draw} 的调用处同一组坐标。
	 */
	public static boolean overPanel(int mouseX, int mouseY, int panelX, int panelY) {
		if (!ClientConfig.summaryExpanded) {
			return false;
		}
		List<Component> lines = lines();
		if (lines.isEmpty()) {
			return false;
		}
		int[] size = MapPanel.size(lines);
		return MapPanel.contains(mouseX, mouseY, panelX, panelY, size);
	}

	/**
	 * 鼠标下可定位行指向的区块；未指向任何可定位行（含没有数据的空面板、折叠行）时返回 null。
	 * {@code panelX}/{@code panelY} 是总览左上角，与 {@link #draw} 的调用处同一组坐标。
	 */
	public static Target hitTarget(int mouseX, int mouseY, int panelX, int panelY) {
		if (ClientSnapshot.totals() == null) {
			return null;
		}
		int[] size = MapPanel.size(lines());
		int row = MapPanel.rowAt(mouseX, mouseY, panelX, panelY, size);
		return row < 0 ? null : targetAt(row);
	}

	/** 该行指向的区块：TOP 数据行与加载源行；其余行（标题、明细、折叠行等）返回 null。 */
	private static Target targetAt(int row) {
		if (cached.topFirstRow() >= 0 && row >= cached.topFirstRow()
				&& row < cached.topFirstRow() + cached.heaviest().size()) {
			return Target.of(cached.heaviest().get(row - cached.topFirstRow()));
		}
		if (row >= cached.sourceFirstRow() && row < cached.sourceFirstRow() + cached.sourceShown()) {
			return Target.of(cached.sources().get(row - cached.sourceFirstRow()));
		}
		return null;
	}

	/** 鼠标是否落在折叠行上（收起、无数据、未超上限时恒 false）。坐标口径同 {@link #hitTarget}。 */
	public static boolean hitFold(int mouseX, int mouseY, int panelX, int panelY) {
		if (ClientSnapshot.totals() == null) {
			return false;
		}
		int[] size = MapPanel.size(lines());
		int row = MapPanel.rowAt(mouseX, mouseY, panelX, panelY, size);
		return row >= 0 && row == cached.foldRow();
	}

	/** 该行是否可点击（TOP 数据行、加载源行或折叠行）——悬停高亮与命中共用同一判据。 */
	private static boolean clickableRow(int row) {
		return row >= 0 && (row == cached.foldRow() || targetAt(row) != null);
	}

	/** 全部加载源行（与总览内所列同格式）；无数据时为空列表。 */
	public static List<Component> sourceRows() {
		lines();
		return cached.sourceRows();
	}

	/** 全部加载源（与 {@link #sourceRows()} 一一对应）。 */
	public static List<ClientSnapshot.Source> sources() {
		lines();
		return cached.sources();
	}

	/**
	 * 右侧完整列表是否应显示：总览展开、开关已开、确有数据、且源多于总览内列出的上限（不多则无需
	 * 另开框）。绘制、点击、滚轮与悬停让位共用这一个判据，收起总览时右框一并藏起。
	 */
	public static boolean sourcesPanelOpen() {
		lines();
		return ClientConfig.summaryExpanded && sourcesExpanded && cached.foldRow() >= 0;
	}

	/** 折叠行的点击：开合右侧完整列表。 */
	public static void toggleSourcesPanel() {
		sourcesExpanded = !sourcesExpanded;
	}

	/** 打开地图时复位：右侧完整列表回到收起态、滚动回到顶部（状态不落盘）。 */
	public static void resetSourcesPanel() {
		sourcesExpanded = false;
		SourceListPanel.reset();
	}
}
