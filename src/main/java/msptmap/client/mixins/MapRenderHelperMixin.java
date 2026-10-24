package msptmap.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import msptmap.client.MapScreenHook;
import msptmap.client.XaeroCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 热力图的挂点：借 Xaero 绘制动态高亮时的矩阵、缓冲区与相机坐标，把本模组的填色画进同一批。
 *
 * <p>挂在 {@code MapRenderHelper} 上而非 GuiMap（理由见 {@link ScreenBaseMixin}）。GuiMap 每帧
 * 在未隐藏界面时调用该方法一次；导出界面的选区高亮是另一处调用，由 MapScreenHook 按选区状态过滤。
 */
@Mixin(value = xaero.map.graphics.MapRenderHelper.class, remap = false)
public abstract class MapRenderHelperMixin {
	@Inject(method = "renderDynamicHighlight", at = @At("HEAD"), remap = false)
	private static void msptmap$drawHeatmap(PoseStack matrixStack, VertexConsumer buffer,
			int flooredCameraX, int flooredCameraZ, int x1, int y1, int x2, int y2,
			float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2, CallbackInfo ci) {
		Screen screen = currentScreen();
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onOverlay(screen, matrixStack, buffer, flooredCameraX, flooredCameraZ);
		}
	}

	private static Screen currentScreen() {
		Minecraft minecraft = Minecraft.getInstance();
		//? if >=26.2 {
		return minecraft.gui.screen();
		//?} else {
		/*return minecraft.screen;
		*///?}
	}
}
