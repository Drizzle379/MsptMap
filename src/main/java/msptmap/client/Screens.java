package msptmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 切屏工具：把各版本的原版界面切换 API 差异集中一处。 */
final class Screens {
	/** 命令路径排队的切屏目标，由客户端 tick 钩子取出，见 {@link #applyPending}。 */
	private static Screen pending;

	private Screens() {
	}

	/**
	 * 切到目标界面。26.2 起为 {@code Gui.setScreen}，此前为 {@code Minecraft.setScreen}；
	 * 各版本的原版界面均走这条路径。
	 *
	 * <p>不用 {@code Minecraft.setScreenAndShow}：它在设屏后额外强制渲染一帧（本意是退出世界后
	 * 立即重画避免残影，仅 {@code clearClientLevel} 等场景调用），常规按钮切屏使用它会在正常帧之间
	 * 插入一帧非周期渲染，画面会概率性闪烁一帧。
	 */
	static void show(Minecraft minecraft, Screen target) {
		//? if >=26.2 {
		minecraft.gui.setScreen(target);
		//?} else {
		/*minecraft.setScreen(target);
		*///?}
	}

	/**
	 * 从命令里切屏：只记下目标，等下一次客户端 tick 再切（见 {@link #applyPending}）。
	 *
	 * <p>客户端命令在 ChatScreen 提交那一刻同步执行完，原版随后还会把屏幕置为 null
	 * （{@code ChatScreen.keyPressed}：{@code handleChatInput} 之后紧跟 {@code setScreen(null)}），
	 * 当即切换会被这一步覆盖，表现为命令不报错、界面却不出现。按钮回调不在此列，直接走 {@link #show}。
	 */
	static void showLater(Screen target) {
		pending = target;
	}

	/** 客户端每 tick 末尾调用：把命令里排队的界面切过去。此刻聊天栏已关，屏幕稳定。 */
	static void applyPending(Minecraft minecraft) {
		if (pending == null) {
			return;
		}
		Screen target = pending;
		pending = null;
		show(minecraft, target);
	}
}
