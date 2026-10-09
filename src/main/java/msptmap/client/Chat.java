package msptmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/**
 * 客户端聊天栏输出：参数为语言键，按客户端语言解析；仅自己可见，不发往服务端。
 *
 * <p>地图按钮（{@link MsptMapClient}）与结果包处理（{@link ScanResultHandler}）共用。
 */
final class Chat {
	private Chat() {
	}

	/** 在聊天栏显示一条消息（key 为语言键）；key 为 null 时不显示。 */
	static void say(String key) {
		if (key != null) {
			// 1.21.11 及以前名为 addMessage，26.1 起更名为 addClientSystemMessage
			//? if >=26.1 {
			chat().addClientSystemMessage(Component.translatable(key));
			//?} else {
			/*chat().addMessage(Component.translatable(key));
			*///?}
		}
	}

	/** 聊天组件：26.2 起挪进了新引入的 Gui.hud，26.1 及以前 Gui 自己就有 getChat()。 */
	private static ChatComponent chat() {
		//? if >=26.2 {
		return Minecraft.getInstance().gui.hud.getChat();
		//?} else {
		/*return Minecraft.getInstance().gui.getChat();
		*///?}
	}
}
