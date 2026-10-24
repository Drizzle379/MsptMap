package msptmap.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * 世界地图屏幕的识别：只比较类名，不引用世界地图的类型，未装世界地图时本类也能安全加载。
 *
 * <p>世界地图的 mixin 挂在 XaeroLib 的 {@code ScreenBase} 上（它随小地图也会安装），
 * 是否执行本模组的逻辑由本类判定。
 */
public final class XaeroCompat {
	private XaeroCompat() {
	}

	public static boolean isGuiMap(Screen screen) {
		return screen != null && "xaero.map.gui.GuiMap".equals(screen.getClass().getName());
	}
}
