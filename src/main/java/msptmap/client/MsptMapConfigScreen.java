package msptmap.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 设置主界面。两个入口：模组菜单（{@link ModMenuIntegration}）与命令 {@code /msptmap config}。
 *
 * <p>照 Xaero 世界地图的分层做法：本界面只放三个板块的入口按钮（如设置条目般整行），点击进入各自
 * 子页（{@link ScanConfigScreen} / {@link ColorConfigScreen} / {@link TooltipConfigScreen}），
 * 子页由「返回」或 Esc 回到本界面。此前所有选项挤在一页，面板高 282px，常见 GUI 缩放下底部按钮
 * 会出屏；拆开后每页最坏 219px，低于 GUI 高度下限 240。
 *
 * <p>落盘只有一处：{@link #onClose()}。子页里的改动都立刻写入内存字段（地图下一帧即按新值绘制），
 * 回到本界面再「完成」或 Esc 时统一保存——没有取消按钮。
 */
public class MsptMapConfigScreen extends ConfigScreenBase {
	/** 入口按钮的宽度（整行式）。 */
	private static final int ENTRY_WIDTH = 200;
	/** 入口按钮的行距（按钮 20 + 4 像素间隔）。 */
	private static final int ENTRY_ROW = 24;

	public MsptMapConfigScreen(Screen parent) {
		super(parent, Component.translatable("msptmap.config.title"));
	}

	@Override
	protected List<Column> planColumns() {
		return List.of(new Column(ENTRY_WIDTH, List.of(
				new Row(ENTRY_ROW, (x, y) -> addEntry(x, y, "msptmap.config.group.scan",
						() -> showScreen(new ScanConfigScreen(this)))),
				new Row(ENTRY_ROW, (x, y) -> addEntry(x, y, "msptmap.config.group.colors",
						() -> showScreen(new ColorConfigScreen(this)))),
				new Row(ENTRY_ROW, (x, y) -> addEntry(x, y, "msptmap.config.group.tooltip",
						() -> showScreen(new TooltipConfigScreen(this)))))));
	}

	/** 一个板块的入口按钮：点击进入对应子页。 */
	private void addEntry(int x, int y, String key, Runnable onPress) {
		addRenderableWidget(Button.builder(Component.translatable(key), button -> onPress.run())
				.bounds(x, y, ENTRY_WIDTH, WIDGET_HEIGHT)
				.build());
	}

	@Override
	protected void placeBottomButtons(int centerX, int y) {
		addRenderableWidget(Button.builder(Component.translatable("msptmap.config.reset"), button -> {
					ClientConfig.resetToDefaults();
					// 值已回到默认，控件随之重建（滑块位置、勾选框、秒数框文本）
					rebuildWidgets();
				})
				.bounds(centerX - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, WIDGET_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("msptmap.config.reset_tooltip")))
				.build());
		addRenderableWidget(Button.builder(Component.translatable("msptmap.config.done"), button -> onClose())
				.bounds(centerX + 4, y, BUTTON_WIDTH, WIDGET_HEIGHT)
				.build());
	}

	/** 「完成」与 Esc 均为保存退出；无上一级（{@code /msptmap config}）时直接回游戏。 */
	@Override
	public void onClose() {
		ClientConfig.save();
		goBack();
	}
}
