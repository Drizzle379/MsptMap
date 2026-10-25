package msptmap.client;

import msptmap.util.Clamp;
import msptmap.util.Decimals;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 「颜色」子页：热力配色的六个选项（相对模式、红色阈值、不透明度、弱加载变灰、
 * 弱加载计时时染色、小地图同步）。
 */
class ColorConfigScreen extends ConfigScreenBase {
	/** 行标签的最小宽度；英文标签更长时按实际宽度扩展。 */
	private static final int MIN_LABEL_WIDTH = 84;
	/** 行标签的语言键；最长者的宽度决定控件起始 x。 */
	private static final List<String> ROW_LABELS = List.of("msptmap.config.red_at",
			"msptmap.config.opacity");
	/** 滑块的最大宽度（窗口过窄时压窄）。 */
	private static final int CONTROL_WIDTH = 150;
	private static final int MIN_CONTROL_WIDTH = 60;

	/** 「红色阈值」滑块。在 {@link #init()} 中赋值：勾选「采用相对模式」时需关闭其 active。 */
	private ConfigSlider redSlider;

	/** 「弱加载区块计时时染色」勾选框。在 {@link #init()} 中赋值：未显示弱加载区块时需关闭其 active。 */
	private Checkbox weakTimedBox;

	ColorConfigScreen(Screen parent) {
		super(parent, Component.translatable("msptmap.config.group.colors"));
	}

	@Override
	protected List<Column> planColumns() {
		int labelWidth = rowLabelWidth();
		// 窗口不够宽时压窄滑块
		int control = Clamp.of(width - MARGIN * 2 - labelWidth, MIN_CONTROL_WIDTH, CONTROL_WIDTH);
		return List.of(new Column(labelWidth + control, List.of(
				new Row(ROW, (x, y) -> addCheckbox(x, y, Component.translatable("msptmap.config.relative"),
						ClientConfig.relativeColor,
						Component.translatable("msptmap.config.relative_tooltip"),
						value -> {
							ClientConfig.relativeColor = value;
							redSlider.active = !value;
						})),
				new Row(ROW, (x, y) -> {
					addRowLabel(Component.translatable("msptmap.config.red_at"), x, y);
					redSlider = addSlider(x + labelWidth, y, control,
							ClientConfig.MIN_RED_AT, ClientConfig.MAX_RED_AT, ClientConfig.redAt,
							value -> Decimals.format2(value) + " mspt",
							value -> ClientConfig.redAt = value,
							Component.translatable("msptmap.config.red_at_tooltip"));
					// 勾选相对模式时红点由数据决定，该滑块不参与，直接禁用
					redSlider.active = !ClientConfig.relativeColor;
				}),
				new Row(ROW, (x, y) -> {
					addRowLabel(Component.translatable("msptmap.config.opacity"), x, y);
					addSlider(x + labelWidth, y, control,
							ClientConfig.MIN_FILL_ALPHA, ClientConfig.MAX_FILL_ALPHA, ClientConfig.fillAlpha,
							Decimals::format2,
							value -> ClientConfig.fillAlpha = value,
							Component.translatable("msptmap.config.opacity_tooltip"));
				}),
				new Row(ROW, (x, y) -> addCheckbox(x, y, Component.translatable("msptmap.config.weak"),
						ClientConfig.showWeakGray,
						Component.translatable("msptmap.config.weak_tooltip"),
						value -> {
							ClientConfig.showWeakGray = value;
							// 关掉后弱加载区块不再上色，这一开关随之失效
							weakTimedBox.active = value;
						})),
				new Row(ROW, (x, y) -> {
					weakTimedBox = addCheckbox(x, y, Component.translatable("msptmap.config.weak_timed"),
							ClientConfig.weakTimedColor,
							Component.translatable("msptmap.config.weak_timed_tooltip"),
							value -> ClientConfig.weakTimedColor = value);
					weakTimedBox.active = ClientConfig.showWeakGray;
				}),
				new Row(ROW, (x, y) -> addCheckbox(x, y, Component.translatable("msptmap.config.minimap"),
						ClientConfig.minimapHeatmap,
						Component.translatable("msptmap.config.minimap_tooltip"),
						value -> ClientConfig.minimapHeatmap = value)))));
	}

	/** 行标签宽度：取当前语言中最长者（英文标签通常更宽）。 */
	private int rowLabelWidth() {
		int widest = MIN_LABEL_WIDTH;
		for (String key : ROW_LABELS) {
			widest = Math.max(widest, font.width(Component.translatable(key)) + 8);
		}
		return widest;
	}
}
