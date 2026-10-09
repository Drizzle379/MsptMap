package msptmap.client;

import msptmap.util.Clamp;
import msptmap.util.Decimals;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * 「颜色」子页：热力配色的四个选项（相对模式、红色阈值、不透明度、弱加载变灰）。
 */
class ColorConfigScreen extends ConfigScreenBase {
	/** 行标签的最小宽度；英文标签更长时按实际宽度扩展。 */
	private static final int MIN_LABEL_WIDTH = 84;
	/** 行标签的语言键；最长者的宽度决定控件起始 x。 */
	private static final List<String> ROW_LABELS = List.of(
			"msptmap.config.red_at", "msptmap.config.opacity");
	/** 滑块的最大宽度（窗口过窄时压窄）。 */
	private static final int CONTROL_WIDTH = 150;
	private static final int MIN_CONTROL_WIDTH = 60;

	/** 「红色阈值」滑块。在 {@link #init()} 中赋值：勾选「采用相对模式」时需关闭其 active。 */
	private ConfigSlider redSlider;

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
					addSlider(x + labelWidth, y, control, ClientConfig.MIN_FILL_ALPHA, 1.0,
							ClientConfig.fillAlpha, Decimals::format2,
							value -> ClientConfig.fillAlpha = value,
							Component.translatable("msptmap.config.opacity_tooltip"));
				}),
				new Row(ROW, (x, y) -> addCheckbox(x, y, Component.translatable("msptmap.config.weak"),
						ClientConfig.showWeakGray,
						Component.translatable("msptmap.config.weak_tooltip"),
						value -> ClientConfig.showWeakGray = value)))));
	}

	/** 行标签宽度：取当前语言中最长者（英文标签通常更宽）。 */
	private int rowLabelWidth() {
		int widest = MIN_LABEL_WIDTH;
		for (String key : ROW_LABELS) {
			widest = Math.max(widest, font.width(Component.translatable(key)) + 8);
		}
		return widest;
	}

	private ConfigSlider addSlider(int x, int y, int sliderWidth, double min, double max, double value,
			DoubleFunction<String> format, DoubleConsumer apply, Component tooltip) {
		ConfigSlider slider = new ConfigSlider(x, y, sliderWidth, min, max, value, format, apply);
		slider.setTooltip(Tooltip.create(tooltip));
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
}
