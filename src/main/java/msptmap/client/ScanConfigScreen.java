package msptmap.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 「扫描」子页：一次扫描的时长（秒）。
 */
class ScanConfigScreen extends ConfigScreenBase {
	/** 秒数输入框的宽度：仅一两位数字。 */
	private static final int SECONDS_BOX_WIDTH = 50;

	ScanConfigScreen(Screen parent) {
		super(parent, Component.translatable("msptmap.config.group.scan"));
	}

	@Override
	protected List<Column> planColumns() {
		Component label = Component.translatable("msptmap.config.seconds");
		int labelWidth = font.width(label) + 8;
		return List.of(new Column(labelWidth + SECONDS_BOX_WIDTH, List.of(
				new Row(ROW, (x, y) -> {
					addRowLabel(label, x, y);
					addSecondsBox(x + labelWidth, y);
				}))));
	}

	/**
	 * 秒数输入框。
	 *
	 * <p>EditBox 没有 setFilter，合法范围自行校验：输入非法则将框内文本改回当前生效值（改回的文本必然
	 * 合法，故 responder 不会递归），使框内显示与将要发送的值始终一致。清空时先不处理：需要允许擦除
	 * 旧值重输，此时的值仍为上一个合法值。
	 */
	private void addSecondsBox(int x, int y) {
		EditBox box = new EditBox(font, x, y, SECONDS_BOX_WIDTH, WIDGET_HEIGHT,
				Component.translatable("msptmap.config.seconds_narration"));
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
		box.setTooltip(Tooltip.create(Component.translatable("msptmap.config.seconds_tooltip")));
		addRenderableWidget(box);
	}
}
