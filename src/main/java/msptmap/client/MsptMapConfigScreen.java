package msptmap.client;

import msptmap.Clamp;
import msptmap.Decimals;
import msptmap.sampler.TickCategory;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;

/**
 * 设置界面。两个入口：模组菜单（{@link ModMenuIntegration}）与命令 {@code /msptmap config}。
 *
 * <p>布局为两列（左：扫描 + 颜色；右：悬停详情），窗口过窄则压窄滑块并整块居中。控件位置在
 * {@link #init()} 中算好，文字在 {@link #extractRenderState} 中绘制——框架顺序是 extractBackground →
 * extractRenderState → 绘制控件，故 super 之后再画会压在控件底层（文字只写在控件旁的空白处，不重叠）。
 *
 * <p>落盘只有一处：{@link #onClose()}。滑块拖动、勾选框点击、秒数框输入都立刻写入内存字段（地图下一帧
 * 即按新值绘制），「完成」与 Esc 均为保存退出——没有取消按钮。
 */
public class MsptMapConfigScreen extends Screen {
	/** 滑块与按钮的标准高度。 */
	private static final int WIDGET_HEIGHT = 20;
	/** 一行的高度（控件 20 + 1 像素间隔）。 */
	private static final int ROW = 21;
	/** 分组标题的高度。 */
	private static final int HEADER = 18;
	/** 顶部标题的高度。 */
	private static final int TITLE_HEIGHT = 22;
	/** 分组之间的额外间隔。 */
	private static final int GROUP_GAP = 6;
	/** 左列控件左侧文字的宽度。 */
	private static final int LABEL_WIDTH = 84;
	/** 左列滑块的最大宽度（窗口过窄时压窄）。 */
	private static final int CONTROL_WIDTH = 150;
	private static final int MIN_CONTROL_WIDTH = 60;
	/** 秒数输入框的宽度：仅一两位数字。 */
	private static final int SECONDS_BOX_WIDTH = 50;
	/** 左右两列之间的间隔。 */
	private static final int COLUMN_GAP = 24;
	/** 右列两个子列之间的间隔。 */
	private static final int SUB_GAP = 16;
	/** 面板与屏幕边缘的最小距离。 */
	private static final int MARGIN = 20;
	/** 最后一排控件与底部按钮的间隔。 */
	private static final int BUTTON_GAP = 12;
	private static final int BUTTON_WIDTH = 100;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	/** 分组标题的暖色，与控件文字区分。 */
	private static final int HEADER_COLOR = 0xFFFFD060;

	/** 上一级界面（模组菜单传入）；null = 无上一级，关闭后直接回游戏。 */
	private final Screen parent;

	/** 待绘制的文字（位置在 init 中计算）。 */
	private final List<Label> labels = new ArrayList<>();

	/** 标题的 y。 */
	private int titleY;

	/** 「红色阈值」滑块。在 {@link #init()} 中赋值：勾选「采用相对模式」时需关闭其 active。 */
	private ConfigSlider redSlider;

	public MsptMapConfigScreen(Screen parent) {
		super(Component.literal("MsptMap 设置"));
		this.parent = parent;
	}

	/** 一行在控件旁绘制的文字。 */
	private record Label(String text, int x, int y, boolean header) {
	}

	/**
	 * 布局计划里的一行：高度 + 「在给定 y 上摆放控件」的动作。
	 *
	 * <p>列高由计划本身求和而来（见 {@link #init()}），增删一行只动计划，不再有第二处行数要手工同步。
	 */
	private record Row(int height, IntConsumer place) {
	}

	@Override
	protected void init() {
		labels.clear();

		int rightWidth = rightColumnWidth();
		// 窗口不够宽时压窄滑块，避免把右列挤出屏幕
		int control = Clamp.of(width - MARGIN * 2 - LABEL_WIDTH - COLUMN_GAP - rightWidth,
				MIN_CONTROL_WIDTH, CONTROL_WIDTH);
		int leftWidth = LABEL_WIDTH + control;
		int panelWidth = leftWidth + COLUMN_GAP + rightWidth;
		int panelX = Math.max(MARGIN, (width - panelWidth) / 2);
		int rightX = panelX + leftWidth + COLUMN_GAP;

		// 先组三份排布计划，再由计划求和出列高、居中、逐行摆放（「先量后摆」）
		List<Row> leftPlan = leftColumnPlan(panelX, control);
		List<Row> infoPlan = rightInfoPlan(rightX);
		int subWidth = (rightWidth - SUB_GAP) / 2;
		List<Row> categoryPlan = rightCategoryPlan(rightX + subWidth + SUB_GAP);
		int bodyHeight = Math.max(planHeight(leftPlan), rightColumnHeight(infoPlan, categoryPlan));

		int totalHeight = TITLE_HEIGHT + bodyHeight + BUTTON_GAP + WIDGET_HEIGHT;
		// 矮窗口（GUI 高度常见下限为 240）从 18 起，宁可下溢也不把标题顶出屏幕
		titleY = Math.max(18, (height - totalHeight) / 2);
		int bodyTop = titleY + TITLE_HEIGHT;

		// 摆控件：左列（扫描 + 颜色），右列（悬停详情，两个子列并排）
		applyPlan(leftPlan, bodyTop);
		labels.add(new Label("悬停详情", rightX, bodyTop + 4, true));
		applyPlan(infoPlan, bodyTop + HEADER);
		applyPlan(categoryPlan, bodyTop + HEADER);

		// 底部两个按钮：置于两列下方（bodyHeight 已取两列中的较大者）
		int buttonY = bodyTop + bodyHeight + BUTTON_GAP;
		addRenderableWidget(Button.builder(Component.literal("恢复默认"), button -> {
					ClientConfig.resetToDefaults();
					// 值已回到默认，控件随之重建（滑块位置、勾选框、秒数框文本）
					rebuildWidgets();
				})
				.bounds(width / 2 - BUTTON_WIDTH - 4, buttonY, BUTTON_WIDTH, WIDGET_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("将所有设置恢复为默认值。")))
				.build());
		addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
				.bounds(width / 2 + 4, buttonY, BUTTON_WIDTH, WIDGET_HEIGHT)
				.build());
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
			graphics.text(font, label.text(), label.x(), label.y(), label.header() ? HEADER_COLOR : TEXT_COLOR);
			//?} else {
			/*graphics.drawString(font, label.text(), label.x(), label.y(), label.header() ? HEADER_COLOR : TEXT_COLOR);
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
	 * 窗口改大小走这条路径：{@code init(int, int)} 只在首次调用 {@link #init()}，之后都调这里。
	 * 不接则窗口拉大后控件留在原处、旁边的文字却按新布局走，整块就散了。
	 */
	@Override
	protected void repositionElements() {
		rebuildWidgets();
	}

	/** 「完成」与 Esc 均为保存退出；无上一级（{@code /msptmap config}）时直接回游戏。 */
	@Override
	public void onClose() {
		ClientConfig.save();
		if (parent == null) {
			super.onClose();
		} else {
			// 1.21.11 及以前叫 setScreen，26.1 起更名为 setScreenAndShow
			//? if >=26.1 {
			minecraft.setScreenAndShow(parent);
			//?} else {
			/*minecraft.setScreen(parent);
			*///?}
		}
	}

	/** 左列（扫描 + 颜色）的排布计划：自上而下每一行的高度与摆放动作。 */
	private List<Row> leftColumnPlan(int panelX, int control) {
		return List.of(
				new Row(HEADER, y -> labels.add(new Label("扫描", panelX, y + 4, true))),
				new Row(ROW, y -> {
					labels.add(new Label("秒数", panelX, y + 6, false));
					addSecondsBox(panelX + LABEL_WIDTH, y);
				}),
				// 纯间隔行：只占高，不摆控件
				new Row(GROUP_GAP, y -> {
				}),
				new Row(HEADER, y -> labels.add(new Label("颜色", panelX, y + 4, true))),
				new Row(ROW, y -> addCheckbox(panelX, y, "采用相对模式", ClientConfig.relativeColor,
						"勾选后颜色按本次扫描的相对大小判定：最重的区块显示为红色，其余区块与它比较 —— "
								+ "整体卡顿较低时，更容易发现相对卡顿的区块。取消勾选则一律按下方固定的红色阈值判定。",
						value -> {
							ClientConfig.relativeColor = value;
							redSlider.active = !value;
						})),
				new Row(ROW, y -> {
					labels.add(new Label("红色阈值", panelX, y + 6, false));
					redSlider = addSlider(panelX + LABEL_WIDTH, y, control,
							ClientConfig.MIN_RED_AT, ClientConfig.MAX_RED_AT, ClientConfig.redAt,
							value -> Decimals.format2(value) + " mspt",
							value -> ClientConfig.redAt = value,
							"区块耗时达到此值即显示为最高等级红色；低于此值的区块，颜色由绿经黄连续过渡到红。"
									+ "勾选「采用相对模式」时此项不参与判定，不可调。");
					// 勾选相对模式时红点由数据决定，该滑块不参与，直接禁用
					redSlider.active = !ClientConfig.relativeColor;
				}),
				new Row(ROW, y -> {
					labels.add(new Label("不透明度", panelX, y + 6, false));
					addSlider(panelX + LABEL_WIDTH, y, control, ClientConfig.MIN_FILL_ALPHA, 1.0,
							ClientConfig.fillAlpha, Decimals::format2,
							value -> ClientConfig.fillAlpha = value,
							"热力色填充的不透明度。取值范围 0.05 ~ 1.00，数值越小，地图底图越清晰。");
				}),
				new Row(ROW, y -> addCheckbox(panelX, y, "显示弱加载区块", ClientConfig.showWeakGray,
						"显示加载等级为32的区块。",
						value -> ClientConfig.showWeakGray = value)));
	}

	/** 右列左子列（单行信息的开关）的排布计划。 */
	private List<Row> rightInfoPlan(int x) {
		return List.of(
				new Row(ROW, y -> addCheckbox(x, y, "坐标", ClientConfig.tooltipCoords,
						value -> ClientConfig.tooltipCoords = value)),
				new Row(ROW, y -> addCheckbox(x, y, "等级", ClientConfig.tooltipLevels,
						value -> ClientConfig.tooltipLevels = value)),
				// 两个票开关紧跟在「等级」之后：它们是等级行的一部分，不是独立行
				new Row(ROW, y -> addCheckbox(x, y, "加载票", ClientConfig.tooltipTicketLoad,
						value -> ClientConfig.tooltipTicketLoad = value)),
				new Row(ROW, y -> addCheckbox(x, y, "计算票", ClientConfig.tooltipTicketSim,
						value -> ClientConfig.tooltipTicketSim = value)),
				new Row(ROW, y -> addCheckbox(x, y, "实体数", ClientConfig.tooltipEntities,
						value -> ClientConfig.tooltipEntities = value)));
	}

	/** 右列右子列（合计 + 各类明细）的排布计划：标签与开关都取自同一份显示定义。 */
	private List<Row> rightCategoryPlan(int x) {
		List<Row> plan = new ArrayList<>();
		plan.add(new Row(ROW, y -> addCheckbox(x, y, "合计", ClientConfig.tooltipTotal,
				value -> ClientConfig.tooltipTotal = value)));
		for (TickCategory category : ChunkTooltip.ORDER) {
			plan.add(new Row(ROW, y -> addCheckbox(x, y, ChunkTooltip.label(category),
					ClientConfig.tooltipCategory(category),
					value -> ClientConfig.tooltipCategories[category.ordinal()] = value)));
		}
		return plan;
	}

	/** 计划占的总高度。 */
	private static int planHeight(List<Row> plan) {
		int height = 0;
		for (Row row : plan) {
			height += row.height();
		}
		return height;
	}

	/** 按计划逐行摆放：每行在累计到的 y 上执行摆放动作，再累加它的高度。 */
	private static void applyPlan(List<Row> plan, int top) {
		int y = top;
		for (Row row : plan) {
			row.place().accept(y);
			y += row.height();
		}
	}

	/** 右列总高度：一个分组标题 + 较高的那个子列（左子列五条单行信息、右子列是合计 + 各类明细）。 */
	private static int rightColumnHeight(List<Row> infoPlan, List<Row> categoryPlan) {
		return HEADER + Math.max(planHeight(infoPlan), planHeight(categoryPlan));
	}

	/** 右列宽度：两个子列，各按最长标签计算（宽度为 Checkbox 的框 + 4 + 文字）。 */
	private int rightColumnWidth() {
		int widest = 0;
		for (TickCategory category : ChunkTooltip.ORDER) {
			widest = Math.max(widest, font.width(ChunkTooltip.label(category)));
		}
		// 1.20.4 及以前没有公开的盒子尺寸接口（getBoxSize 1.20.5 起才有），盒子 + 间距按 24 估计
		//? if >=1.20.5 {
		int subWidth = Checkbox.getBoxSize(font) + 4 + widest;
		//?} else {
		/*int subWidth = 24 + widest;
		*///?}
		return subWidth * 2 + SUB_GAP;
	}

	/**
	 * 秒数输入框。
	 *
	 * <p>EditBox 没有 setFilter，合法范围自行把关：输入非法则把框内文本改回当前生效值（改回的文本必然
	 * 合法，故 responder 不会递归多层），使框内显示与将要发送的始终一致。清空时先不处理——需允许擦掉
	 * 旧值重输，此时的值仍是上一个合法值。
	 */
	private void addSecondsBox(int x, int y) {
		EditBox box = new EditBox(font, x, y, SECONDS_BOX_WIDTH, WIDGET_HEIGHT, Component.literal("扫描秒数"));
		box.setMaxLength(2);
		box.setValue(Integer.toString(ClientConfig.scanSeconds));
		box.setResponder(text -> {
			if (text.isEmpty()) {
				return;
			}
			int seconds = ClientConfig.parseSeconds(text);
			if (seconds < 0) {
				box.setValue(Integer.toString(ClientConfig.scanSeconds));
				return;
			}
			ClientConfig.scanSeconds = seconds;
		});
		box.setTooltip(Tooltip.create(Component.literal("采样秒数（按游戏刻换算）。")));
		addRenderableWidget(box);
	}

	/** 不带悬停说明的勾选框（右列「悬停详情」栏：勾的是什么看标签即可）。 */
	private void addCheckbox(int x, int y, String label, boolean selected, Consumer<Boolean> apply) {
		addCheckbox(x, y, label, selected, null, apply);
	}

	private void addCheckbox(int x, int y, String label, boolean selected, String tooltip,
			Consumer<Boolean> apply) {
		//? if >=1.20.3 {
		Checkbox checkbox = Checkbox.builder(Component.literal(label), font)
				.pos(x, y)
				.selected(selected)
				.onValueChange((control, value) -> apply.accept(value))
				.build();
		//?} else {
		/*// 1.20.2 及以前没有 Builder：构造器直接收位置与宽度，值变化由 MsptCheckbox 上报。
		// 宽度 = 盒子 20 + 间距 4 + 文字
		Checkbox checkbox = new MsptCheckbox(x, y, font.width(label) + 24,
				Component.literal(label), selected, apply);
		*///?}
		// 提示需自行挂载：Builder 的 setTooltip 仅在标签长到要折三行以上时（overflowsRowLimit）
		// 才生效，而这些标签都是一行，走 Builder 会被丢弃。
		if (tooltip != null) {
			checkbox.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		addRenderableWidget(checkbox);
	}

	private ConfigSlider addSlider(int x, int y, int sliderWidth, double min, double max, double value,
			DoubleFunction<String> format, DoubleConsumer apply, String tooltip) {
		ConfigSlider slider = new ConfigSlider(x, y, sliderWidth, min, max, value, format, apply);
		slider.setTooltip(Tooltip.create(Component.literal(tooltip)));
		addRenderableWidget(slider);
		return slider;
	}

	/**
	 * 0~1 的滑块：位置 → 区间 [min, max] 内的值（两位小数）。
	 *
	 * 显示文本与写回的值都走 {@link #current()}，两者不会不一致。
	 */
	private static class ConfigSlider extends AbstractSliderButton {
		private final double min;
		private final double max;
		private final DoubleFunction<String> format;
		private final DoubleConsumer apply;

		ConfigSlider(int x, int y, int sliderWidth, double min, double max, double value,
				DoubleFunction<String> format, DoubleConsumer apply) {
			super(x, y, sliderWidth, WIDGET_HEIGHT, Component.empty(), ClientConfig.toSlider(value, min, max));
			this.min = min;
			this.max = max;
			this.format = format;
			this.apply = apply;
			// 父类构造器只存位置、不调 updateMessage，故文字需自行填一次
			updateMessage();
		}

		/** 滑块当前代表的值。 */
		private double current() {
			return ClientConfig.fromSlider(value, min, max);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(format.apply(current())));
		}

		@Override
		protected void applyValue() {
			apply.accept(current());
		}
	}

	//? if <1.20.3 {
	/*// 1.20.2 及以前：Checkbox 没有值变化回调，覆写 onPress 上报新值 —— 由 super 先翻转选择，
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
