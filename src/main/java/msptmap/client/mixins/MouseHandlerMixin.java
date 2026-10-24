package msptmap.client.mixins;

import com.mojang.blaze3d.platform.Window;
import msptmap.client.MapScreenHook;
import msptmap.client.XaeroCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 滚轮的最前置拦截：GuiMap 的 {@code mouseScrolled} 先改动地图缩放、再调用父类，只有在这里
 * 拦下才能让总览与右侧列表独占滚轮（见 MapScreenHook.onScroll）。
 */
@Mixin(net.minecraft.client.MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void msptmap$scrollPanels(long window, double xOffset, double yOffset, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = currentScreen(minecraft);
		if (!XaeroCompat.isGuiMap(screen)) {
			return;
		}
		Window guiWindow = minecraft.getWindow();
		int width = guiWindow.getWidth();
		int height = guiWindow.getHeight();
		if (width <= 0 || height <= 0) {
			return;
		}
		// 与 MouseHandler 内部相同的换算：窗口坐标 → GUI 缩放坐标
		double mouseX = minecraft.mouseHandler.xpos() * guiWindow.getGuiScaledWidth() / width;
		double mouseY = minecraft.mouseHandler.ypos() * guiWindow.getGuiScaledHeight() / height;
		if (MapScreenHook.onScroll(screen, mouseX, mouseY, yOffset)) {
			ci.cancel();
		}
	}

	private static Screen currentScreen(Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gui.screen();
		//?} else {
		/*return minecraft.screen;
		*///?}
	}
}
