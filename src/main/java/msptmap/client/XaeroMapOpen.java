package msptmap.client;

import net.minecraft.client.Minecraft;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * 打开 Xaero 的世界地图。本类是普通代码里唯一引用 Xaero 的类（mixin 另在客户端配置中），只在
 * {@code xaeroworldmap} 已加载时才被调用，故未安装时不会加载它。
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
		// 当前屏幕：26.2 起挪进了 Gui（同 MsptMapClient.chat），26.1 及以前是 Minecraft 自己的字段
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
