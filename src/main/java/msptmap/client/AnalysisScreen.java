package msptmap.client;

//? if >=26.2 {
import msptmap.MsptMapMod;
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
import msptmap.vendor.owo.ui.core.PositionedRectangle;
import msptmap.vendor.owo.ui.core.Sizing;
import msptmap.vendor.owo.ui.core.Surface;
import msptmap.vendor.owo.ui.core.UIComponent;
import msptmap.vendor.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
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
	private static final int[] COLUMN_WEIGHTS = {100, 52, 30, 50, 50};
	private static final int COLUMN_GAP = 3;
	private static final int SCROLLBAR_WIDTH = 8;
	/** 数据行左右内边距之和（见 {@link RowComponent} 的 padding）。 */
	private static final int ROW_PADDING_HORIZONTAL = 4;
	/** 维度方块图标的边长。 */
	private static final int ICON_SIZE = 8;
	/** 坐标列的最小宽度：图标 + 间隙 + 约 9 个字符的 [x,z]。 */
	private static final int COORD_MIN_WIDTH = 64;
	/** 表头排序三角：7×4，纹理上段为升序、中段为降序、下段透明供未排序列占位。 */
	private static final int SORT_ARROW_WIDTH = 7;
	private static final int SORT_ARROW_HEIGHT = 4;
	/** 右键菜单：两项固定，每项高 13，四周留 4 像素。 */
	private static final int MENU_ITEM_HEIGHT = 13;
	private static final int MENU_PADDING = 4;
	private static final int MENU_ITEMS = 2;

	private static final DateTimeFormatter SCAN_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");

	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int BORDER = 0x66AFAFAF;

	private static final List<String> DIMENSIONS = List.of(
			"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

	private final Screen parent;
	/** 从地图右键进入时的目标区块；无目标时维度为 null。 */
	private final @Nullable String targetDimension;
	private final int targetX;
	private final int targetZ;
	private final List<Row> allRows = new ArrayList<>();
	private final List<Row> visibleRows = new ArrayList<>();
	/** 各列的像素宽；build 时按窗口大小算好，表头与数据行共用。 */
	private final int[] columns = new int[COLUMN_WEIGHTS.length];

	private FlowLayout list;
	private IncrementalScroll listScroll;
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
	/** 首帧布局完成后需要滚动到可见处的目标行。 */
	private @Nullable Row pendingScroll;
	/** 界面上次见到的扫描时间；扫描完成后据此刷新列表。 */
	private long scannedAt;
	/** 右键菜单的目标行与菜单左上角；未打开时为 null。 */
	private @Nullable Row menuRow;
	private int menuX;
	private int menuY;
	/** 重命名输入框与目标行；编辑中非 null。 */
	private @Nullable EditBox renameBox;
	private @Nullable Row renameRow;

	public AnalysisScreen(@Nullable Screen parent) {
		this(parent, null, 0, 0);
	}

	/** @param dimension 目标区块所在维度；null 表示按坐标在全部维度中查找 */
	public AnalysisScreen(@Nullable Screen parent, @Nullable String dimension, int chunkX, int chunkZ) {
		super(Component.translatable("msptmap.analysis.title"));
		this.parent = parent;
		this.targetDimension = dimension;
		this.targetX = chunkX;
		this.targetZ = chunkZ;
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
		// 从地图右键进入时先切到目标维度，标签高亮与列表筛选据此保持一致
		if (targetDimension != null && DIMENSIONS.contains(targetDimension)) {
			dimension = targetDimension;
		}

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
		if (targetDimension == null) {
			showHint();
		} else {
			showTarget();
		}
	}

	/** 窗口尺寸变化会重建控件树：输入框与菜单的坐标随之失效，直接丢弃。 */
	@Override
	protected void init() {
		menuRow = null;
		renameBox = null;
		renameRow = null;
		super.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (invalid) {
			return;
		}
		refreshAfterScan();
		scrollToTarget();
		drawMenu(graphics, mouseX, mouseY);
	}

	/** 扫描结果整体替换，旧的选中行与滚动位置不再有意义，回到顶部。 */
	private void refreshAfterScan() {
		long scanned = ClientSnapshot.scannedAtMillis();
		if (scanned == scannedAt) {
			return;
		}
		scannedAt = scanned;
		selected = null;
		pendingScroll = null;
		menuRow = null;
		finishRename(false);
		showHint();
		reloadRows();
		resetList();
	}

	/** 右键功能列表：重命名、在地图上定位（双击定位仍然保留）。 */
	private static Component[] menuItems() {
		return new Component[]{
				Component.translatable("msptmap.analysis.menu.rename"),
				Component.translatable("msptmap.analysis.menu.locate")};
	}

	private int menuWidth() {
		int width = 0;
		for (Component item : menuItems()) {
			width = Math.max(width, Minecraft.getInstance().font.width(item));
		}
		return width + MENU_PADDING * 2;
	}

	private int menuHeight() {
		return MENU_ITEMS * MENU_ITEM_HEIGHT + MENU_PADDING * 2;
	}

	/** 在点击处打开菜单；贴边时向内收，菜单不越出屏幕。 */
	private void openMenu(Row row, int x, int y) {
		menuRow = row;
		menuX = Math.max(1, Math.min(x, this.width - menuWidth() - 1));
		menuY = Math.max(1, Math.min(y, this.height - menuHeight() - 1));
	}

	private void drawMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (menuRow == null) {
			return;
		}
		int width = menuWidth();
		int height = menuHeight();
		graphics.fill(menuX - 1, menuY - 1, menuX + width + 1, menuY + height + 1, BORDER);
		graphics.fill(menuX, menuY, menuX + width, menuY + height, 0xF0101010);
		Component[] items = menuItems();
		for (int i = 0; i < items.length; i++) {
			int top = menuY + MENU_PADDING + i * MENU_ITEM_HEIGHT;
			if (mouseX >= menuX && mouseX < menuX + width && mouseY >= top && mouseY < top + MENU_ITEM_HEIGHT) {
				graphics.fill(menuX + 1, top, menuX + width - 1, top + MENU_ITEM_HEIGHT, 0x40FFFFFF);
			}
			graphics.text(Minecraft.getInstance().font, items[i], menuX + MENU_PADDING,
					top + (MENU_ITEM_HEIGHT - 8) / 2, TEXT);
		}
	}

	/** 菜单打开时吞掉本次点击：命中项执行动作，点在菜单外只关闭。 */
	private boolean handleMenuClick(MouseButtonEvent click) {
		Row row = menuRow;
		menuRow = null;
		if (row == null) {
			return true;
		}
		int width = menuWidth();
		if (click.x() >= menuX && click.x() < menuX + width) {
			for (int i = 0; i < MENU_ITEMS; i++) {
				int top = menuY + MENU_PADDING + i * MENU_ITEM_HEIGHT;
				if (click.y() >= top && click.y() < top + MENU_ITEM_HEIGHT) {
					if (i == 0) {
						beginRename(row);
					} else {
						locate(row);
					}
					return true;
				}
			}
		}
		return true;
	}

	/** 在该行上叠一个原版输入框：Enter 保存，Esc 或点击别处取消。 */
	private void beginRename(Row row) {
		RowComponent component = rowComponent(row);
		if (component == null) {
			return;
		}
		renameRow = row;
		renameBox = new EditBox(Minecraft.getInstance().font, component.x() + 2, component.y() - 1,
				Math.max(80, columns[0] - 4), 13, Component.translatable("msptmap.analysis.rename.narration"));
		renameBox.setMaxLength(24);
		String current = ClientConfig.chunkName(row.dimension(), row.x(), row.z());
		renameBox.setValue(current == null ? "" : current);
		renameBox.setHint(Component.translatable("msptmap.analysis.rename.hint"));
		renameBox.setFocused(true);
		addRenderableWidget(renameBox);
	}

	private @Nullable RowComponent rowComponent(Row row) {
		for (UIComponent child : list.children()) {
			if (child instanceof RowComponent component && component.row == row) {
				return component;
			}
		}
		return null;
	}

	private void finishRename(boolean save) {
		EditBox box = renameBox;
		Row row = renameRow;
		renameBox = null;
		renameRow = null;
		if (box == null) {
			return;
		}
		removeWidget(box);
		if (save && row != null) {
			ClientConfig.setChunkName(row.dimension(), row.x(), row.z(), box.getValue().trim());
			rebuild();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (menuRow != null) {
			return handleMenuClick(click);
		}
		if (renameBox != null) {
			if (renameBox.mouseClicked(click, doubled)) {
				renameBox.setFocused(true);
				return true;
			}
			finishRename(false);
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (renameBox != null) {
			if (input.isEscape()) {
				finishRename(false);
				return true;
			}
			if (input.isConfirmation()) {
				finishRename(true);
				return true;
			}
			return renameBox.keyPressed(input);
		}
		if (menuRow != null && input.isEscape()) {
			menuRow = null;
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (renameBox != null) {
			return renameBox.charTyped(input);
		}
		return super.charTyped(input);
	}

	/** 菜单或输入框打开时不平移列表，避免两者位置错开。 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (menuRow != null || renameBox != null) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	/** 选中从地图带来的目标区块；本次快照没有该区块时在详情区说明。 */
	private void showTarget() {
		for (Row row : visibleRows) {
			if (row.x() == targetX && row.z() == targetZ && row.dimension().equals(targetDimension)) {
				revealRow(row);
				return;
			}
		}
		detail.clearChildren();
		detail.child(UIComponents.label(Component.translatable("msptmap.analysis.detail.missing",
				targetX, targetZ)).color(Color.ofArgb(TEXT_DIM)).shadow(false));
	}

	/** 加载到目标行、选中并记下待滚动行（布局完成后由 scrollToTarget 执行）。 */
	private void revealRow(Row row) {
		int index = visibleRows.indexOf(row);
		if (index >= loaded) {
			loaded = Math.min(visibleRows.size(), (index / LOAD_STEP + 1) * LOAD_STEP);
			rebuild();
		}
		selected = row;
		for (UIComponent child : list.children()) {
			if (child instanceof RowComponent component) {
				component.setSelected(component.row == row);
			}
		}
		showDetail(row);
		pendingScroll = row;
	}

	/** 首帧布局完成后把目标行滚到可见处。 */
	private void scrollToTarget() {
		Row target = pendingScroll;
		if (target == null) {
			return;
		}
		pendingScroll = null;
		for (UIComponent child : list.children()) {
			if (child instanceof RowComponent component && component.row == target) {
				listScroll.scrollTo(component);
				return;
			}
		}
	}

	private FlowLayout listHeader() {
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14)).gap(COLUMN_GAP);
		header.padding(Insets.both(2, 0));
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(new HeaderButton("msptmap.analysis.column.x", Sort.X, columns[0], HorizontalAlignment.LEFT));
		header.child(new HeaderButton("msptmap.analysis.column.mspt", Sort.MSPT, columns[1]));
		header.child(new HeaderButton("msptmap.analysis.column.entities_short", Sort.ENTITIES, columns[2]));
		header.child(new HeaderButton("msptmap.analysis.column.load_level", Sort.LEVEL, columns[3]));
		header.child(new HeaderButton("msptmap.analysis.column.compute_level", Sort.COMPUTE_LEVEL, columns[4]));
		return header;
	}

	/**
	 * 按列表实际可用宽度等比分配五列：可用宽 = 列表列宽 - 滚动条 - 行内边距 - 列间隙。
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
		// clearChildren 会把列表高度瞬时算成 0，滚动容器随之把偏移钳到顶部；重建后按原偏移恢复
		if (listScroll != null) {
			listScroll.keepScroll();
		}
		list.clearChildren();
		for (int i = 0; i < loaded; i++) {
			list.child(new RowComponent(visibleRows.get(i), i));
		}
		if (visibleRows.isEmpty()) {
			list.child(UIComponents.label(Component.translatable("msptmap.analysis.empty"))
					.color(Color.ofArgb(TEXT_DIM)).shadow(false));
		}
		if (listScroll != null) {
			listScroll.restoreScroll();
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
		// 只换行底色：重建列表会触发滚动容器的偏移钳制，表现为点击后弹回顶部
		for (UIComponent child : list.children()) {
			if (child instanceof RowComponent component) {
				component.setSelected(component.row == row);
			}
		}
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
		COMPUTE_LEVEL
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

		/** 清空重建前记下偏移；restoreScroll 在重建后恢复。 */
		private double savedScroll = -1;

		void keepScroll() {
			savedScroll = this.scrollOffset;
		}

		/**
		 * 重建行后恢复原有偏移：clearChildren 会让子项高度瞬时归零，
		 * ScrollContainer.layout 随即把 scrollOffset 钳到顶部。
		 */
		void restoreScroll() {
			double offset = savedScroll;
			savedScroll = -1;
			if (offset <= 0 || this.maxScroll <= 0) {
				return;
			}
			this.scrollOffset = Math.min(offset, this.maxScroll);
			this.currentScrollPosition = this.scrollOffset;
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

	/**
	 * 表头列：点击任意一列按该列排序，再点同一列切换升降序。
	 * 列名与排序三角作为一组按 alignment 摆放；三角采样贴图的上/中/透明段，未排序列占位宽度不变。
	 */
	private final class HeaderButton extends FlowLayout {
		private final Sort column;
		private final LabelComponent label;
		private final TextureComponent arrow;
		private @Nullable Boolean styledActive;
		private boolean styledAscending;

		private HeaderButton(String key, Sort column, int width) {
			this(key, column, width, HorizontalAlignment.CENTER);
		}

		private HeaderButton(String key, Sort column, int width, HorizontalAlignment alignment) {
			super(Sizing.fixed(width), Sizing.fill(100), Algorithm.HORIZONTAL);
			this.column = column;
			this.horizontalAlignment(alignment);
			this.verticalAlignment(VerticalAlignment.CENTER);
			this.gap(2);
			this.padding(Insets.both(2, 0));
			this.cursorStyle(CursorStyle.HAND);
			if (alignment == HorizontalAlignment.CENTER) {
				// 左侧占位与右侧三角等宽，使列名中心落在列中线上
				this.child(UIContainers.horizontalFlow(Sizing.fixed(SORT_ARROW_WIDTH), Sizing.fixed(1)));
			}
			this.label = UIComponents.label(Component.translatable(key)).shadow(false);
			this.label.verticalTextAlignment(VerticalAlignment.CENTER);
			this.child(this.label);
			this.arrow = UIComponents.texture(Ids.of(MsptMapMod.MOD_ID, "textures/gui/sort.png"),
					0, 0, SORT_ARROW_WIDTH, SORT_ARROW_HEIGHT,
					SORT_ARROW_WIDTH, SORT_ARROW_HEIGHT * 3);
			this.child(this.arrow);
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
			this.label.color(Color.ofArgb(active ? TEXT : TEXT_DIM));
			this.arrow.visibleArea(PositionedRectangle.of(0,
					active ? (asc ? 0 : SORT_ARROW_HEIGHT) : SORT_ARROW_HEIGHT * 2,
					SORT_ARROW_WIDTH, SORT_ARROW_HEIGHT));
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
		private final int index;

		private RowComponent(Row row, int index) {
			super(Sizing.fill(100), Sizing.fixed(13), Algorithm.HORIZONTAL);
			this.row = row;
			this.index = index;
			this.gap(COLUMN_GAP);
			this.padding(Insets.both(2, 0));
			this.verticalAlignment(VerticalAlignment.CENTER);
			this.setSelected(row == selected);
			child(coordCell(row));
			// mspt 列与地图热力、总览 TOP 行同源着色（相对模式 / 红色阈值一并生效）
			String msptText = Decimals.format3(row.mspt());
			child(cell(ClientConfig.summaryColored
					? TickText.colored(msptText, ScanSummary.chunkColor(row.mspt(), row.dimension()))
					: Component.literal(msptText), columns[1], TEXT));
			child(cell(Component.translatable("msptmap.analysis.column.entities", row.entities()).getString(), columns[2], TEXT_DIM));
			child(cell(Integer.toString(row.loadLevel()), columns[3], TEXT_DIM));
			child(cell(Integer.toString(row.computeLevel()), columns[4], TEXT_DIM));
		}

		/** 选中行浅灰底，其余恢复斑马纹。 */
		private void setSelected(boolean selected) {
			if (selected) {
				this.surface(Surface.flat(0x40FFFFFF));
			} else if (this.index % 2 == 1) {
				this.surface(Surface.flat(0x10FFFFFF));
			} else {
				this.surface(Surface.flat(0x00000000));
			}
		}

		/** 坐标列：图标紧贴 [x,z]，名称接在坐标右侧（放不下时截断）；整列靠左，图标不参与排序。 */
		private FlowLayout coordCell(Row row) {
			FlowLayout rowLayout = UIContainers.horizontalFlow(Sizing.fixed(columns[0]), Sizing.content());
			rowLayout.horizontalAlignment(HorizontalAlignment.LEFT);
			rowLayout.verticalAlignment(VerticalAlignment.CENTER);
			FlowLayout content = UIContainers.horizontalFlow(Sizing.content(), Sizing.content()).gap(2);
			content.verticalAlignment(VerticalAlignment.CENTER);
			content.child(dimensionIcon(row.dimension()));
			String coords = "[" + row.x() + "," + row.z() + "]";
			LabelComponent coord = UIComponents.label(Component.literal(coords))
					.shadow(false).color(Color.ofArgb(TEXT));
			coord.verticalTextAlignment(VerticalAlignment.CENTER);
			content.child(coord);
			String name = ClientConfig.chunkName(row.dimension(), row.x(), row.z());
			if (name != null) {
				Font font = Minecraft.getInstance().font;
				// 减去图标、坐标与两处 gap，余下宽度留给名称
				String shown = font.plainSubstrByWidth(name, Math.max(0, columns[0] - ICON_SIZE - 4 - font.width(coords)));
				if (!shown.isEmpty()) {
					LabelComponent label = UIComponents.label(Component.literal(shown))
							.shadow(false).color(Color.ofArgb(TEXT_DIM));
					label.verticalTextAlignment(VerticalAlignment.CENTER);
					content.child(label);
				}
			}
			rowLayout.child(content);
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
			label.horizontalTextAlignment(HorizontalAlignment.CENTER);
			label.verticalTextAlignment(VerticalAlignment.CENTER);
			return label;
		}

		@Override
		public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
			if (click.button() == 1) {
				select(this.row);
				// owo 传入的是相对本组件的坐标，菜单按屏幕坐标定位
				openMenu(this.row, (int) (x() + click.x()), (int) (y() + click.y()));
				return true;
			}
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
