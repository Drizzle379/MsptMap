package msptmap.client;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 设置界面的公共骨架：主界面（{@link MsptMapConfigScreen}）与三个子页
 * （{@link ScanConfigScreen} / {@link ColorConfigScreen} / {@link TooltipConfigScreen}）共用。
 *
 * <p>三块职责：①版本适配集中一处（背景绘制、文字绘制、切屏 API 在版本间的差异）；
 * ②「先量后摆」的列布局——子类给出各列的宽度与行计划，本类求和、居中、逐行摆放；
 * ③通用构件（整行勾选框）。
 *
 * <p>绘制顺序：框架为 extractBackground → extractRenderState → 绘制控件，故文字在 super 之后画，
 * 会压在控件底层——标签只写在控件旁的空白处，不重叠。
 */
abstract class ConfigScreenBase extends Screen {
	/** 滑块与按钮的标准高度。 */
	protected static final int WIDGET_HEIGHT = 20;
	/** 一行的高度（控件 20 + 1 像素间隔）。 */
	protected static final int ROW = 21;
	/** 顶部标题的高度。 */
	private static final int TITLE_HEIGHT = 22;
	/** 最后一排控件与底部按钮的间隔。 */
	private static final int BUTTON_GAP = 12;
	/** 列与列之间的间隔。 */
	private static final int COLUMN_GAP = 24;
	/** 面板与屏幕边缘的最小距离。 */
	protected static final int MARGIN = 20;
	/** 底部按钮的标准宽度。 */
	protected static final int BUTTON_WIDTH = 100;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	/** 上一级界面；null = 无上一级，关闭后直接回游戏（仅主界面会出现）。 */
	protected final Screen parent;

	/** 待绘制的文字（位置在 init 中计算）。 */
	private final List<Label> labels = new ArrayList<>();

	/** 标题的 y。 */
	private int titleY;

	protected ConfigScreenBase(Screen parent, Component title) {
		super(title);
		this.parent = parent;
	}

	/** 一列的排布计划：列宽（子类先行测量）+ 行计划列表。 */
	protected record Column(int width, List<Row> rows) {
	}

	/** 一行：高度 + 「在列起点 x、行顶 y 上摆放」的动作。 */
	protected record Row(int height, Place place) {
	}

	/** 摆放动作，坐标为列起点与行顶。 */
	protected interface Place {
		void at(int x, int y);
	}

	/** 一行在控件旁绘制的文字。 */
	protected record Label(Component text, int x, int y) {
	}

	/** 子类给出本页的列；宽度须先行测量，摆放时由本类决定整体居中的位置。 */
	protected abstract List<Column> planColumns();

	/** 摆放底部按钮：centerX 为屏幕水平中心，y 为按钮顶。默认为居中的「返回」钮。 */
	protected void placeBottomButtons(int centerX, int y) {
		addRenderableWidget(Button.builder(Component.translatable("msptmap.config.back"), button -> onClose())
				.bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, WIDGET_HEIGHT)
				.build());
	}

	@Override
	protected void init() {
		labels.clear();

		List<Column> columns = planColumns();
		int panelWidth = 0;
		int bodyHeight = 0;
		for (Column column : columns) {
			panelWidth += column.width();
			bodyHeight = Math.max(bodyHeight, planHeight(column.rows()));
		}
		panelWidth += COLUMN_GAP * (columns.size() - 1);
		int panelX = Math.max(MARGIN, (width - panelWidth) / 2);

		int totalHeight = TITLE_HEIGHT + bodyHeight + BUTTON_GAP + WIDGET_HEIGHT;
		// 矮窗口（GUI 高度常见下限为 240）从 18 起，宁可面板探出屏幕底部，也不把标题顶出屏幕
		titleY = Math.max(18, (height - totalHeight) / 2);
		int bodyTop = titleY + TITLE_HEIGHT;

		int columnX = panelX;
		for (Column column : columns) {
			applyPlan(column.rows(), columnX, bodyTop);
			columnX += column.width() + COLUMN_GAP;
		}
		placeBottomButtons(width / 2, bodyTop + bodyHeight + BUTTON_GAP);
	}

	/** 增加一条行标签：y 为行顶，文字下沉 6 px，与同排控件内的文字对齐。 */
	protected void addRowLabel(Component text, int x, int y) {
		labels.add(new Label(text, x, y + 6));
	}

	/** 计划占的总高度。 */
	private static int planHeight(List<Row> rows) {
		int height = 0;
		for (Row row : rows) {
			height += row.height();
		}
		return height;
	}

	private static void applyPlan(List<Row> rows, int x, int top) {
		int y = top;
		for (Row row : rows) {
			row.place().at(x, y);
			y += row.height();
		}
	}

	@Override
	//? if >=26.1 {
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		//?} else {
	/*public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 1.20.1 及以前：框架不会调 renderBackground，背景由各屏自行绘制（原版与 Mod Menu 同此约定）
		//? if <1.20.2 {
		renderDirtBackground(graphics);
		//?}
		super.render(graphics, mouseX, mouseY, partialTick);
	*///?}
		//? if >=26.1 {
		graphics.centeredText(font, title, width / 2, titleY, TEXT_COLOR);
		//?} else {
		/*graphics.drawCenteredString(font, title, width / 2, titleY, TEXT_COLOR);
		*///?}
		for (Label label : labels) {
			//? if >=26.1 {
			graphics.text(font, label.text(), label.x(), label.y(), TEXT_COLOR);
			//?} else {
			/*graphics.drawString(font, label.text(), label.x(), label.y(), TEXT_COLOR);
			*///?}
		}
	}

	/**
	 * 1.20.4 及以前的原版没有菜单模糊：世界内打开时，默认的半透明背景会把下层界面清晰透出（且旧内容
	 * 不被覆盖，会留下残影）。改为画泥土（与原版无世界场景一致），两层界面不再互相干扰；1.20.5 起原版
	 * 自带模糊，不覆写。
	 *
	 * <p>绘制方式随版本：1.20.2–1.20.4 的框架会调 renderBackground，覆写即可；1.20.1 及以前不调
	 * （各屏自行绘制背景，Mod Menu 亦如此），那个版本段改在 render 覆写里画。
	 */
	//? if >=1.20.2 && <1.20.5 {
	/*@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderDirtBackground(graphics);
	}
	*///?}

	/**
	 * 切到目标界面。26.2 起为 {@code Gui.setScreen}，此前为 {@code Minecraft.setScreen}——
	 * 各版本的原版界面均走这条路径。
	 *
	 * <p>不用 {@code Minecraft.setScreenAndShow}：它在设屏后额外强制渲染一帧（本意是退出世界后
	 * 立即重画避免残影，仅 {@code clearClientLevel} 等场景调用），常规按钮切屏用它会在正常帧之间
	 * 插入一帧非周期渲染，画面概率性闪一帧。
	 */
	static void showScreen(Minecraft minecraft, Screen target) {
		//? if >=26.2 {
		minecraft.gui.setScreen(target);
		//?} else {
		/*minecraft.setScreen(target);
		*///?}
	}

	/** 切到目标界面（本屏的 {@code minecraft} 实例）。 */
	protected void showScreen(Screen target) {
		showScreen(minecraft, target);
	}

	/** 返回上一级。 */
	protected void goBack() {
		showScreen(parent);
	}

	/** 「返回」与 Esc 同效：回上一级（不保存；保存集中在主界面退出时）。 */
	@Override
	public void onClose() {
		goBack();
	}

	/** 不带悬停说明的勾选框（子页的开关：勾的是什么看标签即可）。 */
	protected void addCheckbox(int x, int y, Component label, boolean selected, Consumer<Boolean> apply) {
		addCheckbox(x, y, label, selected, null, apply);
	}

	/** 勾选框（含 1.20.2 及以前无 Builder 的兼容）。 */
	protected void addCheckbox(int x, int y, Component label, boolean selected, Component tooltip,
			Consumer<Boolean> apply) {
		//? if >=1.20.3 {
		Checkbox checkbox = Checkbox.builder(label, font)
				.pos(x, y)
				.selected(selected)
				.onValueChange((control, value) -> apply.accept(value))
				.build();
		//?} else {
		/*// 1.20.2 及以前没有 Builder：构造器直接收位置与宽度，值变化由 MsptCheckbox 上报。
		// 宽度 = 盒子 20 + 间距 4 + 文字
		Checkbox checkbox = new MsptCheckbox(x, y, font.width(label) + 24, label, selected, apply);
		*///?}
		// 提示需自行挂载：Builder 的 setTooltip 仅在标签长到要折三行以上时（overflowsRowLimit）
		// 才生效，而这些标签都是一行，走 Builder 会被丢弃。
		if (tooltip != null) {
			checkbox.setTooltip(Tooltip.create(tooltip));
		}
		addRenderableWidget(checkbox);
	}

	//? if <1.20.3 {
	/*// 1.20.2 及以前：Checkbox 没有值变化回调，覆写 onPress 上报新值——由 super 先翻转选择，
	// 再读 selected()（此时已是新值）
	private static class MsptCheckbox extends Checkbox {
		private final Consumer<Boolean> apply;

		MsptCheckbox(int x, int y, int width, Component message, boolean selected, Consumer<Boolean> apply) {
			super(x, y, width, WIDGET_HEIGHT, message, selected);
			this.apply = apply;
		}

		@Override
		public void onPress() {
			super.onPress();
			apply.accept(selected());
		}
	}
	*///?}
}
