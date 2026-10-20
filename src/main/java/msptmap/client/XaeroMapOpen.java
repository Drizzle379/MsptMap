package msptmap.client;

import net.minecraft.client.Minecraft;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * 打开 Xaero 的世界地图。本类是普通代码中唯一由命令入口直接调用 Xaero 的类；其余引用 Xaero 的
 * 绘制类只在 required:false 的 mixin 调用链上加载，未安装时不会加载它们。
 */
public final class XaeroMapOpen {
	private XaeroMapOpen() {
	}

	/**
	 * 地图未打开则打开；已在地图界面内则不作处理，帧循环自会取出待定位目标。
	 *
	 * <p>只有命令这条路径会走到这里，切屏故走 {@link Screens#showLater}：当即切换会被原版关闭
	 * 聊天栏那一步覆盖（原因见该方法）。
	 */
	static void openIfClosed() {
		Minecraft minecraft = Minecraft.getInstance();
		// 当前屏幕：26.2 起挪进了 Gui（同 Chat.chat()），26.1 及以前是 Minecraft 自己的字段
		//? if >=26.2 {
		if (minecraft.gui.screen() instanceof GuiMap) {
		//?} else {
		/*if (minecraft.screen instanceof GuiMap) {
		*///?}
			return;
		}
		WorldMapSession session = WorldMapSession.getCurrentSession();
		if (session == null || !session.isUsable()) {
			return;
		}
		// 两个 null 表示返回上一级界面（Xaero 自己的按键处理同样传 null）：关闭地图后直接回到游戏
		Screens.showLater(new GuiMap(null, null, session.getMapProcessor(), minecraft.getCameraEntity()));
	}
}
