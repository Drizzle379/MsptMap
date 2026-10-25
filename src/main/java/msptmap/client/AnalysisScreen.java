package msptmap.client;

//? if >=26.2 {
import msptmap.net.ChunkDetailResultPayload;
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
 * <p>布局自左向右为区块列表、详情、加载票与实体。列表分批加载：先显示 50 行，滚到底部再追加
 * 50 行，滚动条随内容变长而缩短。界面只在 26.2 及以上编译。
 */
public final class AnalysisScreen extends BaseOwoScreen<FlowLayout> {
	/** 设计尺寸；窗口过窄时各栏内容横向滚动。 */
	private static final int DESIGN_WIDTH = 420;
	private static final int DESIGN_HEIGHT = 240;
	private static final double SCALE_MIN = 0.7;
	private static final double SCALE_MAX = 1.2;

	/** 每次追加的行数。 */
	private static final int LOAD_STEP = 50;
	/** 右栏加载票默认显示的条目数，超过即折叠。 */
	private static final int TICKET_LIST_ROWS = 4;

	/** 各列宽度的权重：实际列宽在 {@link #computeColumnWidths} 里按列表可用宽度等比分配。 */
	private static final int[] COLUMN_WEIGHTS = {70, 52, 30, 92};
	private static final int COLUMN_GAP = 2;
	private static final int SCROLLBAR_WIDTH = 8;
	/** 详情/信息栏内容左右内边距之和（见两栏的 Insets.of(3)）。 */
	private static final int PANE_PADDING = 6;
	/** 窗口宽与内容区宽之差（outline 与 content 的左右 padding）。 */
	private static final int WINDOW_INSET = 8;
	/** 数据行左右内边距之和（见 {@link RowComponent} 的 padding）。 */
	private static final int ROW_PADDING_HORIZONTAL = 4;
	/** 维度方块图标的边长。 */
	private static final int ICON_SIZE = 8;
	/** 坐标列的最小宽度：图标 + 间隙 + 约 9 个字符的 [x,z]。 */
	private static final int COORD_MIN_WIDTH = 64;
	/** 右键菜单：两项固定，每项高 13、项间 1 像素分割线；上下无额外留白，文字左侧缩进 4 像素。 */
	private static final int MENU_ITEM_HEIGHT = 13;
	private static final int MENU_SEPARATOR = 1;
	private static final int MENU_TEXT_INSET = 4;
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
	private PaneScroll detailScroll;
	/** 详情右侧：加载票与实体组成。 */
	private FlowLayout info;
	private PaneScroll infoScroll;
	/** 票列表是否展开；换行选择时复位。 */
	private boolean ticketsExpanded;
	/** 界面上次见到的详情版本，用于判断是否需要重绘。 */
	private long detailVersion;
	private LabelComponent overview;
	private LabelComponent scanTime;
	/** 状态栏可用宽度，用于判断右侧的时间是否放得下。 */
	private int contentWidth;
	/** 内容过宽时按需拉宽的窗口宽度（GUI 像素）；只增不减，避免来回跳动。 */
	private int requiredWindowWidth;
	/** 本次 build 实际采用的窗口宽度。 */
	private int builtWindowWidth;
	/** 已安排下一帧重建界面。 */
	private boolean relayoutPending;

	private String dimension = "*";
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
		// 内容放不下时把窗口拉宽到所需宽度；高度仍按设计尺寸缩放
		int windowWidth = Math.max((int) (DESIGN_WIDTH * scale), requiredWindowWidth);
		int windowHeight = (int) (DESIGN_HEIGHT * scale);
		builtWindowWidth = windowWidth;
		// 内容区宽 = 窗口宽 - outline 左右 padding(2) - content 左右 padding(6)
		contentWidth = windowWidth - WINDOW_INSET;
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

		// 主体：左列表 + 中详情 + 右票与实体
		FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand()).gap(3);

		FlowLayout listColumn = UIContainers.verticalFlow(Sizing.fill(41), Sizing.fill(100)).gap(1);
		listColumn.child(listHeader());
		list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		// expand 而非 fill(100)：垂直 flow 中 fill 是整高，会把表头那 15px 挤到容器外
		listScroll = new IncrementalScroll(Sizing.fill(100), Sizing.expand(), list, this::loadMore);
		listScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x66FFFFFF)));
		listScroll.scrollbarThiccness(SCROLLBAR_WIDTH);
		listScroll.surface(Surface.outline(0x550F0F0F));
		listColumn.child(listScroll);
		body.child(listColumn);

		detail = UIContainers.verticalFlow(Sizing.content(), Sizing.content()).gap(2);
		detail.padding(Insets.of(3));
		FlowLayout detailColumn = UIContainers.verticalFlow(Sizing.fill(26), Sizing.fill(100)).gap(1);
		detailScroll = new PaneScroll(Sizing.fill(100), Sizing.fill(100), detail);
		detailScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x66FFFFFF)));
		detailScroll.scrollbarThiccness(SCROLLBAR_WIDTH);
		detailColumn.child(detailScroll);
		body.child(detailColumn);

		info = UIContainers.verticalFlow(Sizing.content(), Sizing.content()).gap(2);
		info.padding(Insets.of(3));
		FlowLayout infoColumn = UIContainers.verticalFlow(Sizing.fill(31), Sizing.fill(100)).gap(1);
		infoScroll = new PaneScroll(Sizing.fill(100), Sizing.fill(100), info);
		infoScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x66FFFFFF)));
		infoScroll.scrollbarThiccness(SCROLLBAR_WIDTH);
		infoColumn.child(infoScroll);
		if (!MsptMapClient.canRequestDetail()) {
			// 旧服务端没有详情包：提示固定在信息栏底部右侧，不随内容滚动
			infoColumn.child(oldServerHint());
		}
		body.child(infoColumn);
		content.child(body);

		scannedAt = ClientSnapshot.scannedAtMillis();
		reloadRows();
		rebuild();
		if (targetDimension == null) {
			// 拉宽重建后恢复原来的选中行；首次打开则显示提示
			if (selected != null && visibleRows.contains(selected)) {
				revealRow(selected);
			} else {
				showHint();
			}
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
		if (relayoutPending) {
			// 丢弃旧控件树，让父类按新的窗口宽度重新 build
			relayoutPending = false;
			this.uiAdapter = null;
		}
		super.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (invalid) {
			return;
		}
		refreshAfterScan();
		syncDetail();
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
		ClientChunkDetail.clear();
		ticketsExpanded = false;
		detailVersion = ClientChunkDetail.version();
		showHint();
		reloadRows();
		resetList();
	}

	/** 收到新的区块详情时重绘右栏；结果与当前选中行不匹配则等下一份。 */
	private void syncDetail() {
		long version = ClientChunkDetail.version();
		if (version == detailVersion) {
			return;
		}
		detailVersion = version;
		Row row = selected;
		if (row != null && ClientChunkDetail.dataFor(row.dimension(), row.x(), row.z()) != null) {
			showDetail(row);
			showInfo(row);
		}
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
		return width + MENU_TEXT_INSET * 2;
	}

	private int menuHeight() {
		return MENU_ITEMS * MENU_ITEM_HEIGHT + (MENU_ITEMS - 1) * MENU_SEPARATOR;
	}

	/** 第 index 项的顶部（屏幕坐标）。 */
	private int menuItemTop(int index) {
		return menuY + index * (MENU_ITEM_HEIGHT + MENU_SEPARATOR);
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
			int top = menuItemTop(i);
			if (mouseX >= menuX && mouseX < menuX + width && mouseY >= top && mouseY < top + MENU_ITEM_HEIGHT) {
				graphics.fill(menuX, top, menuX + width, top + MENU_ITEM_HEIGHT, 0x40FFFFFF);
			}
			graphics.text(Minecraft.getInstance().font, items[i], menuX + MENU_TEXT_INSET,
					top + (MENU_ITEM_HEIGHT - 8) / 2, TEXT);
			if (i < items.length - 1) {
				int lineY = top + MENU_ITEM_HEIGHT;
				graphics.fill(menuX, lineY, menuX + width, lineY + MENU_SEPARATOR, 0x30FFFFFF);
			}
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
				int top = menuItemTop(i);
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
		present(row);
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
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(13)).gap(COLUMN_GAP);
		header.padding(Insets.both(2, 0));
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(headerCell("msptmap.analysis.column.x", columns[0], HorizontalAlignment.LEFT));
		header.child(headerCell("msptmap.analysis.column.mspt", columns[1], HorizontalAlignment.CENTER));
		header.child(headerCell("msptmap.analysis.column.entities_short", columns[2], HorizontalAlignment.CENTER));
		header.child(headerCell("msptmap.analysis.column.level", columns[3], HorizontalAlignment.CENTER));
		return header;
	}

	/** 表头单元格：只显示列名，不参与排序。 */
	private LabelComponent headerCell(String key, int width, HorizontalAlignment alignment) {
		LabelComponent label = UIComponents.label(Component.translatable(key)).shadow(false)
				.color(Color.ofArgb(TEXT_DIM));
		label.horizontalSizing(Sizing.fixed(width));
		label.horizontalTextAlignment(alignment);
		label.verticalTextAlignment(VerticalAlignment.CENTER);
		return label;
	}

	/**
	 * 按列表实际可用宽度等比分配四列：可用宽 = 列表列宽 - 滚动条 - 行内边距 - 列间隙。
	 * 最后一列吸收取整误差，列宽总和不会超出可用宽。
	 */
	private void computeColumnWidths(int windowWidth) {
		int usable = Math.round(0.41f * (windowWidth - 8)) - SCROLLBAR_WIDTH
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
						chunk.nanos(), chunk.counts()));
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
		// 固定按 mspt 从大到小，最重的区块总在顶部
		visibleRows.sort(Comparator.comparingDouble(Row::mspt).reversed());

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

	private void select(Row row) {
		selected = row;
		// 只换行底色：重建列表会触发滚动容器的偏移钳制，表现为点击后弹回顶部
		for (UIComponent child : list.children()) {
			if (child instanceof RowComponent component) {
				component.setSelected(component.row == row);
			}
		}
		present(row);
	}

	/** 展示某行的详情并请求服务端补充数据；两张列表回到折叠态，两栏滚到顶部。 */
	private void present(Row row) {
		ticketsExpanded = false;
		if (detailScroll != null) {
			detailScroll.scrollTo(0);
		}
		if (infoScroll != null) {
			infoScroll.scrollTo(0);
		}
		showDetail(row);
		showInfo(row);
		MsptMapClient.requestDetail(row.dimension(), row.x(), row.z());
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
		info.clearChildren();
	}

	/** 详情首行与列表坐标列共用的内容：维度方块图标、[x,z]、区块名称（放不下时截断）。 */
	private static FlowLayout coordContent(Row row, int maxWidth) {
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
			String shown = font.plainSubstrByWidth(name, Math.max(0, maxWidth - ICON_SIZE - 4 - font.width(coords)));
			if (!shown.isEmpty()) {
				LabelComponent label = UIComponents.label(Component.literal(shown))
						.shadow(false).color(Color.ofArgb(TEXT_DIM));
				label.verticalTextAlignment(VerticalAlignment.CENTER);
				content.child(label);
			}
		}
		return content;
	}

	/** 维度对应的方块侧面纹理（草方块 / 下界岩 / 末地石）。 */
	private static TextureComponent dimensionIcon(String dimension) {
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

	/**
	 * 按右栏文字所需宽度反推窗口宽度；超出本次 build 的窗口时，下一帧重建界面并整体拉宽。
	 *
	 * @param share 该栏占内容区宽度的比例（详情栏 0.26、信息栏 0.31）
	 */
	private void requirePaneWidth(int textWidth, float share) {
		int needed = (int) Math.ceil((textWidth + PANE_PADDING + SCROLLBAR_WIDTH) / share) + WINDOW_INSET;
		if (needed > requiredWindowWidth) {
			requiredWindowWidth = needed;
		}
		if (requiredWindowWidth > builtWindowWidth && !relayoutPending) {
			relayoutPending = true;
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> {
				if (minecraft.gui.screen() == this) {
					this.rebuildWidgets();
				}
			});
		}
	}

	/** 详情栏内容可用宽：栏宽减去滚动条与左右内边距。 */
	private int detailContentWidth() {
		return Math.max(1, Math.round(0.26f * contentWidth) - SCROLLBAR_WIDTH - PANE_PADDING);
	}

	private void showDetail(Row row) {
		if (detailScroll != null) {
			detailScroll.keepScroll();
		}
		detail.clearChildren();
		Font font = Minecraft.getInstance().font;
		int widest = coordContentWidth(row);
		// 详情栏内容按自然宽度布局；区块名称放不下时截断
		detail.child(coordContent(row, detailContentWidth()));
		detail.child(divider());
		widest = Math.max(widest, addDetailLine("msptmap.analysis.detail.mspt",
				Decimals.format3(row.mspt()) + (ClientConfig.tooltipMsptUnit ? " mspt" : "")));
		widest = Math.max(widest, addDetailLine("msptmap.analysis.detail.entities",
				Integer.toString(row.entities())));
		widest = Math.max(widest, addDetailLine("msptmap.analysis.detail.level",
				row.loadLevel() + "/" + row.computeLevel()));
		widest = Math.max(widest, addDetailLine("msptmap.analysis.detail.ticket_load",
				ticketName(row.loadTicket())));
		widest = Math.max(widest, addDetailLine("msptmap.analysis.detail.ticket_sim",
				ticketName(row.simTicket())));
		detail.child(sectionLabel("msptmap.analysis.detail.categories"));
		widest = Math.max(widest, font.width(Component.translatable("msptmap.analysis.detail.categories")));
		for (TickCategory category : TickText.ORDER) {
			Component line = categoryText(row, category);
			detail.child(UIComponents.label(line).shadow(false));
			widest = Math.max(widest, font.width(line));
		}
		requirePaneWidth(widest, 0.26f);
		if (detailScroll != null) {
			detailScroll.restoreScroll();
		}
	}

	/** 添加一行详情（标签灰、值白）并返回该行的自然宽。 */
	private int addDetailLine(String key, String value) {
		detail.child(detailLine(key, value));
		return Minecraft.getInstance().font.width(detailText(key, value));
	}

	/** 各类耗时行：耗时后附本窗口内的触发次数。 */
	private static Component categoryText(Row row, TickCategory category) {
		Component line = TickText.secondary(TickText.categoryLine(
				row.nanos()[category.ordinal()], ClientSnapshot.windowTicks(), category));
		// 次数紧跟在耗时后：与列表里的实体数同口径，为本窗口内的触发次数
		return line.copy().append(Component.literal("  ×" + row.counts()[category.ordinal()]));
	}

	/** 详情首行的自然宽：图标、坐标与完整区块名称。 */
	private static int coordContentWidth(Row row) {
		Font font = Minecraft.getInstance().font;
		String coords = "[" + row.x() + "," + row.z() + "]";
		int width = ICON_SIZE + 4 + font.width(coords);
		String name = ClientConfig.chunkName(row.dimension(), row.x(), row.z());
		return name == null ? width : width + font.width(name);
	}

	/** 右栏：加载票与实体组成，数据由服务端在收到详情请求后补充。 */
	private void showInfo(Row row) {
		if (infoScroll != null) {
			infoScroll.keepScroll();
		}
		info.clearChildren();
		Font font = Minecraft.getInstance().font;
		int widest = 0;
		// 旧服务端没有这个包，右下角另有固定说明
		if (MsptMapClient.canRequestDetail()) {
			Component ticketsTitle = Component.translatable("msptmap.analysis.detail.tickets");
			info.child(sectionLabel("msptmap.analysis.detail.tickets"));
			widest = Math.max(widest, font.width(ticketsTitle));
			ClientChunkDetail.Data data = ClientChunkDetail.dataFor(row.dimension(), row.x(), row.z());
			if (data == null) {
				info.child(noteLabel("msptmap.analysis.detail.loading"));
				widest = Math.max(widest,
						font.width(Component.translatable("msptmap.analysis.detail.loading")));
			} else {
				widest = Math.max(widest, ticketList(row, data.tickets()));
				info.child(divider());
				Component entitiesTitle = Component.translatable("msptmap.analysis.detail.entity_list");
				info.child(sectionLabel("msptmap.analysis.detail.entity_list"));
				widest = Math.max(widest, font.width(entitiesTitle));
				widest = Math.max(widest, entityList(data.entities()));
			}
		}
		requirePaneWidth(widest, 0.31f);
		if (infoScroll != null) {
			infoScroll.restoreScroll();
		}
	}

	/** 加载票列表：每张票两行（首行类型，次行来源与加载/模拟等级），超过四条折叠。返回最宽行。 */
	private int ticketList(Row row, List<ChunkDetailResultPayload.TicketInfo> tickets) {
		Font font = Minecraft.getInstance().font;
		if (tickets.isEmpty()) {
			info.child(noteLabel("msptmap.analysis.detail.none"));
			return font.width(Component.translatable("msptmap.analysis.detail.none"));
		}
		// 来源与等级各占固定列，票与票之间纵向对齐；展开后才可见的票也要算进宽度
		int sourceWidth = 0;
		int levelWidth = 0;
		int widest = 0;
		for (ChunkDetailResultPayload.TicketInfo ticket : tickets) {
			sourceWidth = Math.max(sourceWidth, font.width(Component.literal(ticketSource(row, ticket))));
			levelWidth = Math.max(levelWidth, font.width(Component.literal(ticketLevel(ticket))));
			widest = Math.max(widest, font.width(Component.literal(ticket.type())));
		}
		sourceWidth += 4;
		widest = Math.max(widest, sourceWidth + levelWidth);
		int shown = ticketsExpanded ? tickets.size() : Math.min(TICKET_LIST_ROWS, tickets.size());
		for (int i = 0; i < shown; i++) {
			ChunkDetailResultPayload.TicketInfo ticket = tickets.get(i);
			FlowLayout entry = UIContainers.verticalFlow(Sizing.content(), Sizing.content()).gap(0);
			entry.child(UIComponents.label(Component.literal(ticket.type()))
					.shadow(false).color(Color.ofArgb(TEXT)));
			FlowLayout detail = UIContainers.horizontalFlow(Sizing.content(), Sizing.content()).gap(0);
			LabelComponent source = UIComponents.label(Component.literal(ticketSource(row, ticket)))
					.shadow(false).color(Color.ofArgb(TEXT_DIM));
			source.horizontalSizing(Sizing.fixed(sourceWidth));
			LabelComponent levelLabel = UIComponents.label(Component.literal(ticketLevel(ticket)))
					.shadow(false).color(Color.ofArgb(TEXT_DIM));
			levelLabel.horizontalSizing(Sizing.fixed(levelWidth));
			levelLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
			detail.child(source);
			detail.child(levelLabel);
			entry.child(detail);
			info.child(entry);
		}
		return Math.max(widest, addToggle(tickets.size(), shown, ticketsExpanded, expanded -> {
			ticketsExpanded = expanded;
			showInfo(row);
		}));
	}

	/** 票的来源：本区块持票写 center，其余写 @锚点；两列固定宽，等级右对齐。 */
	private static String ticketSource(Row row, ChunkDetailResultPayload.TicketInfo ticket) {
		if (ticket.anchorX() == row.x() && ticket.anchorZ() == row.z()) {
			return "center";
		}
		return "@" + ticket.anchorX() + "," + ticket.anchorZ();
	}

	/** 票的等级：加载/模拟，某链不参与写 -。 */
	private static String ticketLevel(ChunkDetailResultPayload.TicketInfo ticket) {
		return level(ticket.loadLevel()) + "/" + level(ticket.simLevel());
	}

	private static String level(int level) {
		return level < 0 ? "-" : Integer.toString(level);
	}

	/** 实体列表：名称 × 数量，全部显示。返回最宽行。 */
	private int entityList(List<ChunkDetailResultPayload.EntityInfo> entities) {
		Font font = Minecraft.getInstance().font;
		if (entities.isEmpty()) {
			info.child(noteLabel("msptmap.analysis.detail.none"));
			return font.width(Component.translatable("msptmap.analysis.detail.none"));
		}
		int widest = 0;
		for (ChunkDetailResultPayload.EntityInfo entity : entities) {
			Component line = Component.literal(entityName(entity.id()) + " ×" + entity.count());
			info.child(UIComponents.label(line).shadow(false).color(Color.ofArgb(TEXT)));
			widest = Math.max(widest, font.width(line));
		}
		return widest;
	}

	/**
	 * 实体名称：原版译名键 {@code entity.<命名空间>.<路径>}；语言表里没有时退回完整 ID。
	 * 模组实体的译名键同样是这个形状。
	 */
	private static String entityName(String id) {
		String key = "entity." + id.replace(':', '.');
		String name = Component.translatable(key).getString();
		return name.equals(key) ? id : name;
	}

	/** 票列表超过四张时的折叠按钮：显示剩余条数，点击就地展开 / 收起。返回按钮宽度。 */
	private int addToggle(int total, int shown, boolean expanded,
			java.util.function.Consumer<Boolean> toggle) {
		if (total <= TICKET_LIST_ROWS) {
			return 0;
		}
		Component text = expanded
				? Component.translatable("msptmap.summary.sources_less")
				: Component.translatable("msptmap.summary.sources_more", total - shown);
		LabelComponent label = UIComponents.label(TickText.secondary(text)).shadow(false);
		label.cursorStyle(CursorStyle.HAND);
		label.mouseDown().subscribe((click, doubled) -> {
			if (click.button() != 0) {
				return false;
			}
			playClick();
			toggle.accept(!expanded);
			return true;
		});
		info.child(label);
		return Minecraft.getInstance().font.width(text);
	}

	/** 列表标题：灰色，与「各类耗时」同款。 */
	private static LabelComponent sectionLabel(String key) {
		return UIComponents.label(Component.translatable(key))
				.color(Color.ofArgb(TEXT_DIM)).shadow(false);
	}

	/** 灰字提示（加载中、无数据）。 */
	private static LabelComponent noteLabel(String key) {
		return UIComponents.label(Component.translatable(key))
				.color(Color.ofArgb(TEXT_DIM)).shadow(false);
	}

	/** 旧服务端提示：固定在详情区底部，右对齐两行。 */
	private static FlowLayout oldServerHint() {
		FlowLayout hint = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(0);
		hint.child(rightLabel("msptmap.analysis.detail.old_server_1"));
		hint.child(rightLabel("msptmap.analysis.detail.old_server_2"));
		return hint;
	}

	private static LabelComponent rightLabel(String key) {
		LabelComponent label = UIComponents.label(Component.translatable(key))
				.color(Color.ofArgb(TEXT_DIM)).shadow(false);
		label.horizontalSizing(Sizing.fill(100));
		label.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		return label;
	}

	/** 详情行：标签灰、值白，中间空两格。 */
	private LabelComponent detailLine(String key, String value) {
		return UIComponents.label(detailText(key, value)).shadow(false);
	}

	private static Component detailText(String key, String value) {
		return Component.translatable(key)
				.withStyle(style -> style.withColor(TextColor.fromRgb(TEXT_DIM & 0xFFFFFF)))
				.append(Component.literal("  " + value)
						.withStyle(style -> style.withColor(TextColor.fromRgb(TEXT & 0xFFFFFF))));
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

	private record Row(String dimension, int x, int z, float mspt, int entities, int loadLevel, int computeLevel,
			int loadTicket, int simTicket, long[] nanos, int[] counts) {
	}

	/** 重建内容时保持滚动偏移的垂直滚动容器。 */
	private abstract static class KeepingScroll extends ScrollContainer<FlowLayout> {
		/** 清空重建前记下偏移；restoreScroll 在重建后恢复。 */
		private double savedScroll = -1;

		private KeepingScroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
			super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
		}

		final void keepScroll() {
			savedScroll = this.scrollOffset;
		}

		/**
		 * 重建行后恢复原有偏移：clearChildren 会让子项高度瞬时归零，
		 * ScrollContainer.layout 随即把 scrollOffset 钳到顶部。
		 */
		final void restoreScroll() {
			double offset = savedScroll;
			savedScroll = -1;
			if (offset <= 0 || this.maxScroll <= 0) {
				return;
			}
			this.scrollOffset = Math.min(offset, this.maxScroll);
			this.currentScrollPosition = this.scrollOffset;
		}
	}

	/**
	 * 滚到最底部时追加下一批行的滚动容器。{@code maxScroll} 与 {@code scrollOffset} 在 owo 的
	 * ScrollContainer 中是 protected，子类可以直接判断是否触底。
	 */
	private final class IncrementalScroll extends KeepingScroll {
		private final Runnable onLoadMore;

		private IncrementalScroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child,
				Runnable onLoadMore) {
			super(horizontalSizing, verticalSizing, child);
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

	/** 详情与信息栏的滚动容器：重建内容时就地保持偏移。 */
	private final class PaneScroll extends KeepingScroll {
		private PaneScroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
			super(horizontalSizing, verticalSizing, child);
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
			super(Sizing.fill(100), Sizing.fixed(12), Algorithm.HORIZONTAL);
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
			child(cell(row.loadLevel() + "/" + row.computeLevel(), columns[3], TEXT_DIM));
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
			rowLayout.child(coordContent(row, columns[0]));
			return rowLayout;
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
