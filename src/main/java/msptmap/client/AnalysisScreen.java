package msptmap.client;

//? if >=26.2 {
import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import msptmap.sampler.TicketCode;
import msptmap.util.Decimals;
import msptmap.util.Dimensions;
import msptmap.util.Ids;
import msptmap.vendor.owo.ui.base.BaseOwoScreen;
import msptmap.vendor.owo.ui.component.LabelComponent;
import msptmap.vendor.owo.ui.component.TextureComponent;
import msptmap.vendor.owo.ui.component.UIComponents;
import msptmap.vendor.owo.ui.container.FlowLayout;
import msptmap.vendor.owo.ui.container.ScrollContainer;
import msptmap.vendor.owo.ui.container.UIContainers;
import msptmap.vendor.owo.ui.core.Color;
import msptmap.vendor.owo.ui.core.CursorStyle;
import msptmap.vendor.owo.ui.core.HorizontalAlignment;
import msptmap.vendor.owo.ui.core.Insets;
import msptmap.vendor.owo.ui.core.OwoUIAdapter;
import msptmap.vendor.owo.ui.core.OwoUIGraphics;
import msptmap.vendor.owo.ui.core.Sizing;
import msptmap.vendor.owo.ui.core.Surface;
import msptmap.vendor.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 综合分析界面。窗口沿用 CarpetGUI 的视觉语言：深色半透明内容区、灰色细边框、扁平按钮与顶部标签栏。
 *
 * <p>列表分批加载：先显示 50 行，滚到底部再追加 50 行，滚动条随内容变长而缩短。
 * 点击表头任意一列按该列排序，再点同一列切换升降序。界面只在 26.2 及以上编译。
 */
public final class AnalysisScreen extends BaseOwoScreen<FlowLayout> {
	/** 设计尺寸；比 CarpetGUI 的 420×200 略高，但保持紧凑。 */
	private static final int DESIGN_WIDTH = 420;
	private static final int DESIGN_HEIGHT = 240;
	private static final double SCALE_MIN = 0.7;
	private static final double SCALE_MAX = 1.2;

	/** 每次追加的行数。 */
	private static final int LOAD_STEP = 50;

	/** 各列宽度的权重：实际列宽在 {@link #computeColumnWidths} 里按列表可用宽度等比分配。 */
	private static final int[] COLUMN_WEIGHTS = {75, 52, 30, 50, 50, 50};
	private static final int COLUMN_GAP = 3;
	private static final int SCROLLBAR_WIDTH = 8;
	/** 数据行左右内边距之和（见 {@link RowComponent} 的 padding）。 */
	private static final int ROW_PADDING_HORIZONTAL = 4;
	/** 维度方块图标的边长。 */
	private static final int ICON_SIZE = 8;
	/** 坐标列的最小宽度：图标 + 间隙 + 约 9 个字符的 [x,z]。 */
	private static final int COORD_MIN_WIDTH = 64;

	private static final DateTimeFormatter SCAN_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");

	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int BORDER = 0x66AFAFAF;

	private static final List<String> DIMENSIONS = List.of(
			"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

	private final Screen parent;
	private final List<Row> allRows = new ArrayList<>();
	private final List<Row> visibleRows = new ArrayList<>();
	/** 六列的像素宽；build 时按窗口大小算好，表头与数据行共用。 */
	private final int[] columns = new int[COLUMN_WEIGHTS.length];

	private FlowLayout list;
	private ScrollContainer<FlowLayout> listScroll;
	private FlowLayout detail;
	private LabelComponent overview;
	private LabelComponent scanTime;
	/** 状态栏可用宽度，用于判断右侧的时间是否放得下。 */
	private int contentWidth;

	private String dimension = "*";
	private Sort sort = Sort.MSPT;
	private boolean ascending = false;
	private int loaded = LOAD_STEP;
	private @Nullable Row selected;
	/** 界面上次见到的扫描时间；扫描完成后据此刷新列表。 */
	private long scannedAt;

	public AnalysisScreen(@Nullable Screen parent) {
		super(Component.translatable("msptmap.analysis.title"));
		this.parent = parent;
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	protected void build(FlowLayout root) {
		int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		double scale = Math.min((double) (screenWidth - 4) / DESIGN_WIDTH, (double) (screenHeight - 4) / DESIGN_HEIGHT);
		scale = Math.max(SCALE_MIN, Math.min(scale, SCALE_MAX));
		int windowWidth = (int) (DESIGN_WIDTH * scale);
		int windowHeight = (int) (DESIGN_HEIGHT * scale);
		// 内容区宽 = 窗口宽 - outline 左右 padding(2) - content 左右 padding(6)
		contentWidth = windowWidth - 8;
		computeColumnWidths(windowWidth);

		root.padding(Insets.of(2));
		root.horizontalAlignment(HorizontalAlignment.CENTER);
		root.verticalAlignment(VerticalAlignment.CENTER);
		// 不用 Surface.blur：每帧全屏模糊开销很大，深色遮罩视觉效果接近且不卡
		root.surface(Surface.flat(0x80000000));

		FlowLayout outline = UIContainers.verticalFlow(Sizing.fixed(windowWidth), Sizing.fixed(windowHeight));
		outline.surface(Surface.outline(BORDER));
		outline.padding(Insets.of(1));
		root.child(outline);

		FlowLayout content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
		content.surface(Surface.flat(0xE0000000));
		content.padding(Insets.of(0, 3, 3, 3));
		content.gap(1);
		outline.child(content);

		// 顶部状态栏：左侧为本次扫描汇总，右侧为扫描的现实时间
		FlowLayout statusBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(15)).gap(3);
		statusBar.verticalAlignment(VerticalAlignment.CENTER);
		overview = UIComponents.label(Component.empty()).shadow(false).color(Color.ofArgb(TEXT_DIM));
		statusBar.child(overview);
		statusBar.child(UIComponents.spacer());
		scanTime = UIComponents.label(Component.empty()).shadow(false).color(Color.ofArgb(TEXT_DIM));
		statusBar.child(scanTime);
		content.child(statusBar);

		// 维度标签栏
		FlowLayout tabBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14)).gap(2);
		tabBar.verticalAlignment(VerticalAlignment.CENTER);
		tabBar.padding(Insets.both(2, 0));
		tabBar.surface(Surface.flat(0x661A1A1A));
		tabBar.child(new TabButton(Component.translatable("msptmap.analysis.dimension.all"), "*"));
		for (String dim : DIMENSIONS) {
			tabBar.child(new TabButton(Component.translatable(Dimensions.key(dim)), dim));
		}
		// 扫描按钮靠右：与维度标签同一行，发起一次全区扫描
		tabBar.child(UIComponents.spacer());
		tabBar.child(new ActionButton(Component.translatable("msptmap.analysis.scan"), MsptMapClient::onButtonPress));
		content.child(tabBar);

		// 主体：左侧列表 + 右侧详情
		FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand()).gap(3);

		FlowLayout listColumn = UIContainers.verticalFlow(Sizing.fill(68), Sizing.fill(100)).gap(1);
		listColumn.child(listHeader());
		list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		// expand 而非 fill(100)：垂直 flow 中 fill 是整高，会把表头那 15px 挤到容器外
		listScroll = new IncrementalScroll(Sizing.fill(100), Sizing.expand(), list, this::loadMore);
		listScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x66FFFFFF)));
		listScroll.scrollbarThiccness(SCROLLBAR_WIDTH);
		listScroll.surface(Surface.outline(0x550F0F0F));
		listColumn.child(listScroll);
		body.child(listColumn);

		detail = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
		detail.padding(Insets.of(3));
		// 68% + 31% + gap 3px 不会超过 100%，详情框右缘不再被 body 裁掉
		ScrollContainer<FlowLayout> detailScroll = UIContainers.verticalScroll(
				Sizing.fill(31), Sizing.fill(100), detail);
		detailScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x66FFFFFF)));
		detailScroll.scrollbarThiccness(SCROLLBAR_WIDTH);
		detailScroll.surface(Surface.flat(0x33000000).and(Surface.outline(0x550F0F0F)));
		body.child(detailScroll);
		content.child(body);

		scannedAt = ClientSnapshot.scannedAtMillis();
		reloadRows();
		rebuild();
		showHint();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (!invalid) {
			refreshAfterScan();
		}
	}

	/** 扫描结果整体替换，旧的选中行与滚动位置不再有意义，回到顶部。 */
	private void refreshAfterScan() {
		long scanned = ClientSnapshot.scannedAtMillis();
		if (scanned == scannedAt) {
			return;
		}
		scannedAt = scanned;
		selected = null;
		showHint();
		reloadRows();
		resetList();
	}

	private FlowLayout listHeader() {
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14)).gap(COLUMN_GAP);
		header.padding(Insets.both(2, 0));
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(new HeaderButton("msptmap.analysis.column.x", Sort.X, columns[0]));
		header.child(new HeaderButton("msptmap.analysis.column.mspt", Sort.MSPT, columns[1]));
		header.child(new HeaderButton("msptmap.analysis.column.entities_short", Sort.ENTITIES, columns[2]));
		header.child(new HeaderButton("msptmap.analysis.column.load_level", Sort.LEVEL, columns[3]));
		header.child(new HeaderButton("msptmap.analysis.column.compute_level", Sort.COMPUTE_LEVEL, columns[4]));
		header.child(new HeaderButton("msptmap.analysis.column.dimension", Sort.DIMENSION, columns[5]));
		return header;
	}

	/**
	 * 按列表实际可用宽度等比分配六列：可用宽 = 列表列宽 - 滚动条 - 行内边距 - 列间隙。
	 * 最后一列吸收取整误差，列宽总和不会超出可用宽。
	 */
	private void computeColumnWidths(int windowWidth) {
		int usable = Math.round(0.68f * (windowWidth - 8)) - SCROLLBAR_WIDTH
				- ROW_PADDING_HORIZONTAL - COLUMN_GAP * (COLUMN_WEIGHTS.length - 1);
		usable = Math.max(usable, COLUMN_WEIGHTS.length);
		int totalWeight = 0;
		for (int weight : COLUMN_WEIGHTS) {
			totalWeight += weight;
		}
		int used = 0;
		for (int i = 0; i < COLUMN_WEIGHTS.length - 1; i++) {
			int rest = COLUMN_WEIGHTS.length - 1 - i;
			int width = Math.round((float) usable * COLUMN_WEIGHTS[i] / totalWeight);
			columns[i] = Math.max(1, Math.min(width, usable - used - rest));
			used += columns[i];
		}
		columns[COLUMN_WEIGHTS.length - 1] = Math.max(1, usable - used);
		// 坐标列至少容下维度图标与 [x,z]；不足的宽度从右往左各列扣，总和保持不变
		int minCoord = Math.min(COORD_MIN_WIDTH, Math.max(1, usable - (columns.length - 1)));
		int deficit = minCoord - columns[0];
		if (deficit > 0) {
			columns[0] = minCoord;
			for (int i = columns.length - 1; i > 0 && deficit > 0; i--) {
				int take = Math.min(deficit, Math.max(0, columns[i] - 1));
				columns[i] -= take;
				deficit -= take;
			}
		}
	}

	private void reloadRows() {
		allRows.clear();
		for (String dim : DIMENSIONS) {
			ClientSnapshot.Chunk[] chunks = ClientSnapshot.get(dim);
			if (chunks == null) {
				continue;
			}
			for (ClientSnapshot.Chunk chunk : chunks) {
				int chunkX = chunk.x1() >> 4;
				int chunkZ = chunk.z1() >> 4;
				allRows.add(new Row(dim, chunkX, chunkZ, chunk.mspt(), chunk.entities(),
						chunk.loadLevel(), chunk.computeLevel(), chunk.loadTicket(), chunk.simTicket(),
						chunk.nanos()));
			}
		}
	}

	private void updateOverview() {
		if (overview == null) {
			return;
		}
		var totals = ClientSnapshot.totals();
		if (totals == null) {
			overview.text(Component.translatable("msptmap.analysis.overview.empty"));
			if (scanTime != null) {
				scanTime.text(Component.empty());
			}
			return;
		}
		// 两个 mspt 与总览同口径，直接复用 ScanSummary 的着色（含「总览着色」开关）
		Component chunkTotal = ScanSummary.totalValue(totals.totalNanos(), totals.windowTicks());
		Component serverTotal = ScanSummary.totalValue(totals.tickNanos(), totals.windowTicks());
		String seconds = Decimals.format1(totals.windowTicks() / (double) MsptSampler.TICKS_PER_SECOND);
		// 服务端没量到整 tick（tickNanos 为 0）时退化为只显示区块合计，与总览一致
		Component line = totals.tickNanos() > 0L
				? Component.translatable("msptmap.analysis.overview", chunkTotal, serverTotal,
						totals.chunks(), totals.timed(), totals.sourceList().size(), seconds)
				: Component.translatable("msptmap.analysis.overview_single", chunkTotal,
						totals.chunks(), totals.timed(), totals.sourceList().size(), seconds);
		overview.text(line);

		if (scanTime != null) {
			long scannedAt = ClientSnapshot.scannedAtMillis();
			Component time = scannedAt == 0L ? Component.empty()
					: Component.literal(SCAN_TIME.format(
							Instant.ofEpochMilli(scannedAt).atZone(ZoneId.systemDefault())));
			// 窄窗口下时间放不下就留空，避免与左侧文字重叠或被右缘裁掉
			if (!time.getString().isEmpty()
					&& Minecraft.getInstance().font.width(line) + 3
							+ Minecraft.getInstance().font.width(time) > contentWidth) {
				time = Component.empty();
			}
			scanTime.text(time);
		}
	}

	/** 筛选、排序或切换维度后回到顶部并只加载第一批。 */
	private void resetList() {
		loaded = LOAD_STEP;
		if (listScroll != null) {
			listScroll.scrollTo(0);
		}
		rebuild();
	}

	private void loadMore() {
		if (loaded >= visibleRows.size()) {
			return;
		}
		loaded = Math.min(visibleRows.size(), loaded + LOAD_STEP);
		rebuild();
	}

	private void rebuild() {
		if (list == null) {
			return;
		}
		visibleRows.clear();
		for (Row row : allRows) {
			if (!"*".equals(dimension) && !row.dimension().equals(dimension)) {
				continue;
			}
			visibleRows.add(row);
		}
		visibleRows.sort(comparator());

		loaded = Math.min(loaded, Math.max(0, visibleRows.size()));
		if (loaded == 0 && !visibleRows.isEmpty()) {
			loaded = Math.min(LOAD_STEP, visibleRows.size());
		}
		list.clearChildren();
		for (int i = 0; i < loaded; i++) {
			list.child(new RowComponent(visibleRows.get(i), i));
		}
		if (visibleRows.isEmpty()) {
			list.child(UIComponents.label(Component.translatable("msptmap.analysis.empty"))
					.color(Color.ofArgb(TEXT_DIM)).shadow(false));
		}
		updateOverview();
	}

	private Comparator<Row> comparator() {
		Comparator<Row> by = switch (sort) {
			case MSPT -> Comparator.comparingDouble(Row::mspt);
			case ENTITIES -> Comparator.comparingInt(Row::entities);
			case X -> Comparator.comparingInt(Row::x).thenComparingInt(Row::z);
			case LEVEL -> Comparator.comparingInt(Row::loadLevel);
			case COMPUTE_LEVEL -> Comparator.comparingInt(Row::computeLevel);
			case DIMENSION -> Comparator.comparing(Row::dimension).thenComparingInt(Row::x);
		};
		return ascending ? by : by.reversed();
	}

	private void setSort(Sort next) {
		if (this.sort == next) {
			ascending = !ascending;
		} else {
			sort = next;
			ascending = false;
		}
		resetList();
	}

	private void select(Row row) {
		selected = row;
		rebuild();
		showDetail(row);
	}

	/** 双击行：关闭本界面并在世界地图上定位该区块（未装世界地图时只提示）。 */
	private void locate(Row row) {
		if (!MsptMapClient.canLocate()) {
			Chat.say(MsptMapClient.NO_XAERO_KEY);
			return;
		}
		// 先回到 parent：parent 本身就是地图时，openIfClosed 才不会另开一份
		onClose();
		MsptMapClient.locate(row.dimension(), row.x(), row.z());
	}

	private void showHint() {
		detail.clearChildren();
		detail.child(UIComponents.label(Component.translatable("msptmap.analysis.detail.hint"))
				.color(Color.ofArgb(TEXT_DIM)).shadow(false));
	}

	private void showDetail(Row row) {
		detail.clearChildren();
		detail.child(UIComponents.label(Component.translatable("msptmap.analysis.detail.title",
				Component.translatable(Dimensions.key(row.dimension())), row.x(), row.z())).shadow(false));
		detail.child(divider());
		detail.child(detailLine("msptmap.analysis.detail.mspt",
				Decimals.format3(row.mspt()) + (ClientConfig.tooltipMsptUnit ? " mspt" : "")));
		detail.child(detailLine("msptmap.analysis.detail.entities", Integer.toString(row.entities())));
		detail.child(detailLine("msptmap.analysis.detail.load_level", Integer.toString(row.loadLevel())));
		detail.child(detailLine("msptmap.analysis.detail.compute_level", Integer.toString(row.computeLevel())));
		detail.child(detailLine("msptmap.analysis.detail.ticket_load", ticketName(row.loadTicket())));
		detail.child(detailLine("msptmap.analysis.detail.ticket_sim", ticketName(row.simTicket())));
		detail.child(UIComponents.label(Component.translatable("msptmap.analysis.detail.categories"))
				.color(Color.ofArgb(TEXT_DIM)).shadow(false));
		for (TickCategory category : TickText.ORDER) {
			detail.child(UIComponents.label(TickText.secondary(TickText.categoryLine(
					row.nanos()[category.ordinal()], ClientSnapshot.windowTicks(), category))).shadow(false));
		}
	}

	/** 详情行：标签灰、值白，中间空两格。 */
	private LabelComponent detailLine(String key, String value) {
		Component line = Component.translatable(key)
				.withStyle(style -> style.withColor(TextColor.fromRgb(TEXT_DIM & 0xFFFFFF)))
				.append(Component.literal("  " + value)
						.withStyle(style -> style.withColor(TextColor.fromRgb(TEXT & 0xFFFFFF))));
		return UIComponents.label(line).shadow(false);
	}

	/** 标题与正文之间的细分隔线。 */
	private static FlowLayout divider() {
		FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
		line.surface(Surface.flat(0x33FFFFFF));
		return line;
	}

	private static String ticketName(int code) {
		return Component.translatable(TickText.ticketName(TicketCode.type(code))).getString();
	}

	@Override
	public void onClose() {
		Screens.show(Minecraft.getInstance(), parent);
	}

	private enum Sort {
		MSPT,
		ENTITIES,
		X,
		LEVEL,
		COMPUTE_LEVEL,
		DIMENSION
	}

	private record Row(String dimension, int x, int z, float mspt, int entities, int loadLevel, int computeLevel,
			int loadTicket, int simTicket, long[] nanos) {
	}

	/**
	 * 滚到最底部时追加下一批行的滚动容器。{@code maxScroll} 与 {@code scrollOffset} 在 owo 的
	 * ScrollContainer 中是 protected，子类可以直接判断是否触底。
	 */
	private final class IncrementalScroll extends ScrollContainer<FlowLayout> {
		private final Runnable onLoadMore;

		private IncrementalScroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child,
				Runnable onLoadMore) {
			super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
			this.onLoadMore = onLoadMore;
		}

		@Override
		public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
			boolean handled = super.onMouseScroll(mouseX, mouseY, amount);
			if (amount < 0 && this.maxScroll - this.scrollOffset <= 2) {
				this.onLoadMore.run();
			}
			return handled;
		}
	}

	/** 表头列：点击任意一列按该列排序，再点同一列切换升降序；箭头显示当前方向。 */
	private final class HeaderButton extends FlowLayout {
		private final Component base;
		private final Sort column;
		private final LabelComponent label;
		private @Nullable Boolean styledActive;
		private boolean styledAscending;

		private HeaderButton(String key, Sort column, int width) {
			super(Sizing.fixed(width), Sizing.fill(100), Algorithm.HORIZONTAL);
			this.base = Component.translatable(key);
			this.column = column;
			this.verticalAlignment(VerticalAlignment.CENTER);
			this.padding(Insets.both(2, 0));
			this.cursorStyle(CursorStyle.HAND);
			this.label = UIComponents.label(this.base).shadow(false);
			this.label.verticalTextAlignment(VerticalAlignment.CENTER);
			this.child(this.label);
			this.mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) {
					return false;
				}
				playClick();
				setSort(this.column);
				return true;
			});
			updateStyle();
		}

		private void updateStyle() {
			boolean active = AnalysisScreen.this.sort == this.column;
			boolean asc = AnalysisScreen.this.ascending;
			if (this.styledActive != null && this.styledActive == active && this.styledAscending == asc) {
				return;
			}
			this.styledActive = active;
			this.styledAscending = asc;
			Component text = active ? Component.empty().append(this.base).append(asc ? " ▲" : " ▼") : this.base;
			this.label.text(text);
			this.label.color(Color.ofArgb(active ? TEXT : TEXT_DIM));
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			updateStyle();
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
		}
	}

	/** CarpetGUI 风格标签：活动项浅灰填充，非活动透明，始终保持灰色细边框。 */
	private final class TabButton extends FlowLayout {
		private final String value;
		private final LabelComponent label;
		private @Nullable Boolean styledActive;

		private TabButton(Component title, String value) {
			super(Sizing.content(), Sizing.fill(100), Algorithm.HORIZONTAL);
			this.value = value;
			this.verticalAlignment(VerticalAlignment.CENTER);
			this.horizontalAlignment(HorizontalAlignment.CENTER);
			this.padding(Insets.both(8, 0));
			this.cursorStyle(CursorStyle.HAND);
			this.label = UIComponents.label(title).shadow(false);
			this.label.verticalTextAlignment(VerticalAlignment.CENTER);
			this.child(this.label);
			this.mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) {
					return false;
				}
				// 换维度后旧详情已不在当前列表里，一并清掉
				if (!dimension.equals(value)) {
					dimension = value;
					selected = null;
					showHint();
				}
				playClick();
				resetList();
				return true;
			});
			updateStyle();
		}

		private void updateStyle() {
			boolean active = dimension.equals(value);
			if (this.styledActive != null && this.styledActive == active) {
				return;
			}
			this.styledActive = active;
			this.surface(Surface.flat(active ? 0x66CFCFCF : 0x00000000).and(Surface.outline(BORDER)));
			this.label.color(Color.ofArgb(active ? TEXT : TEXT_DIM));
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			updateStyle();
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
		}
	}

	/** 标签栏右侧的动作按钮：外观与维度标签一致，无选中态，点击后执行动作。 */
	private final class ActionButton extends FlowLayout {
		private ActionButton(Component title, Runnable action) {
			super(Sizing.content(), Sizing.fill(100), Algorithm.HORIZONTAL);
			this.verticalAlignment(VerticalAlignment.CENTER);
			this.horizontalAlignment(HorizontalAlignment.CENTER);
			this.padding(Insets.both(8, 0));
			this.cursorStyle(CursorStyle.HAND);
			this.surface(Surface.outline(BORDER));
			LabelComponent label = UIComponents.label(title).shadow(false).color(Color.ofArgb(TEXT_DIM));
			label.verticalTextAlignment(VerticalAlignment.CENTER);
			this.child(label);
			this.mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) {
					return false;
				}
				playClick();
				action.run();
				return true;
			});
		}
	}

	private final class RowComponent extends FlowLayout {
		private final Row row;

		private RowComponent(Row row, int index) {
			super(Sizing.fill(100), Sizing.fixed(13), Algorithm.HORIZONTAL);
			this.row = row;
			this.gap(COLUMN_GAP);
			this.padding(Insets.both(2, 0));
			this.verticalAlignment(VerticalAlignment.CENTER);
			if (row == selected) {
				this.surface(Surface.flat(0x40FFFFFF));
			} else if (index % 2 == 1) {
				this.surface(Surface.flat(0x10FFFFFF));
			}
			child(coordCell(row));
			// mspt 列与地图热力、总览 TOP 行同源着色（相对模式 / 红色阈值一并生效）
			String msptText = Decimals.format3(row.mspt());
			child(cell(ClientConfig.summaryColored
					? TickText.colored(msptText, ScanSummary.chunkColor(row.mspt(), row.dimension()))
					: Component.literal(msptText), columns[1], TEXT));
			child(cell(Component.translatable("msptmap.analysis.column.entities", row.entities()).getString(), columns[2], TEXT_DIM));
			child(cell(Integer.toString(row.loadLevel()), columns[3], TEXT_DIM));
			child(cell(Integer.toString(row.computeLevel()), columns[4], TEXT_DIM));
			child(cell(Component.translatable(Dimensions.key(row.dimension())), columns[5], TEXT_DIM));
		}

		/** 坐标列：维度方块图标在左、[x,z] 在右；图标不参与排序。 */
		private FlowLayout coordCell(Row row) {
			FlowLayout rowLayout = UIContainers.horizontalFlow(Sizing.fixed(columns[0]), Sizing.content()).gap(2);
			rowLayout.verticalAlignment(VerticalAlignment.CENTER);
			rowLayout.child(dimensionIcon(row.dimension()));
			rowLayout.child(cell("[" + row.x() + "," + row.z() + "]",
					Math.max(1, columns[0] - ICON_SIZE - 2), TEXT));
			return rowLayout;
		}

		/** 维度对应的方块侧面纹理（草方块 / 下界岩 / 末地石）。 */
		private TextureComponent dimensionIcon(String dimension) {
			String block = switch (dimension) {
				case "minecraft:the_nether" -> "netherrack";
				case "minecraft:the_end" -> "end_stone";
				default -> "grass_block_side";
			};
			TextureComponent icon = UIComponents.texture(
					Ids.of("minecraft", "textures/block/" + block + ".png"), 0, 0, 16, 16, 16, 16);
			icon.horizontalSizing(Sizing.fixed(ICON_SIZE));
			icon.verticalSizing(Sizing.fixed(ICON_SIZE));
			return icon;
		}

		private LabelComponent cell(String text, int width, int color) {
			return cell(Component.literal(text), width, color);
		}

		private LabelComponent cell(Component text, int width, int color) {
			LabelComponent label = UIComponents.label(text).shadow(false)
					.color(Color.ofArgb(color));
			label.horizontalSizing(Sizing.fixed(width));
			label.horizontalTextAlignment(HorizontalAlignment.LEFT);
			label.verticalTextAlignment(VerticalAlignment.CENTER);
			return label;
		}

		@Override
		public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
			if (click.button() != 0) {
				return false;
			}
			select(this.row);
			if (doubled) {
				locate(this.row);
			}
			return true;
		}
	}

	private static void playClick() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
	}
}
//?}
