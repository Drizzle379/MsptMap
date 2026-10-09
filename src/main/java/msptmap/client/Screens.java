package msptmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 切屏工具：把各版本的原版界面切换 API 差异集中一处。 */
final class Screens {
	private Screens() {
	}

	/**
	 * 切到目标界面。26.2 起为 {@code Gui.setScreen}，此前为 {@code Minecraft.setScreen}——
	 * 各版本的原版界面均走这条路径。
	 *
	 * <p>不用 {@code Minecraft.setScreenAndShow}：它在设屏后额外强制渲染一帧（本意是退出世界后
	 * 立即重画避免残影，仅 {@code clearClientLevel} 等场景调用），常规按钮切屏用它会在正常帧之间
	 * 插入一帧非周期渲染，画面概率性闪一帧。
	 */
	static void show(Minecraft minecraft, Screen target) {
		//? if >=26.2 {
		minecraft.gui.setScreen(target);
		//?} else {
		/*minecraft.setScreen(target);
		*///?}
	}
}
