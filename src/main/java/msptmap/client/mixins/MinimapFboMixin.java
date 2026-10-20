package msptmap.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import msptmap.client.ClientConfig;
import msptmap.client.MinimapOverlay;
import msptmap.util.Ids;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.graphics.CustomRenderTypes;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.common.misc.OptimizedMath;
import xaero.hud.minimap.module.MinimapSession;
//? if >=1.21.11 {
import xaero.lib.XaeroLib;
import xaero.lib.client.graphics.XaeroBufferProvider;
//?} else {
/*import xaero.common.graphics.CustomVertexConsumers;
*///?}
//? if <1.21.8 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Xaero 小地图 FBO 上的热力图挂点：在区块纹理（或世界地图纹理）之后、区块网格之前叠加热力色。
 *
 * <p>注入 {@code CHUNK_GRID} 的读取处而非某次绘制调用之后：区块纹理与世界地图纹理两条路径在此
 * 汇合，后者会跳过区块纹理的绘制，注入此处才能两者都覆盖。目标类全在 Xaero 中，故该 mixin 与
 * 世界地图的挂点同放客户端配置（required:false）：未装小地图时不应用。
 */
@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MinimapFboMixin {
	/**
	 * 目标方法签名按 Xaero 的三代分档：
	 * <ul>
	 *   <li>1.21.11 起：第二参数 {@code PoseStack}、buffer 为 {@code XaeroBufferProvider}；</li>
	 *   <li>1.21.8–1.21.10：第二参数 {@code PoseStack}、buffer 为 {@code CustomVertexConsumers}；</li>
	 *   <li>1.21.4 及以前：第二参数 {@code GuiGraphics}（其 {@code pose()} 是 PoseStack）、
	 *       buffer 为 {@code CustomVertexConsumers}。</li>
	 * </ul>
	 * 玩家坐标的取整与 Xaero 自身一致（{@link OptimizedMath#myFloor}），保证与区块纹理对齐。
	 */
	//? if >=1.21.11 {
	@Inject(method = "renderChunksToFBO",
			at = @At(value = "FIELD",
					target = "Lxaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions;CHUNK_GRID:Lxaero/lib/common/config/option/RangeConfigOption;",
					opcode = Opcodes.GETSTATIC, shift = At.Shift.BEFORE),
			remap = false)
	private void msptmap$drawHeatmap(MinimapSession session, PoseStack poseStack, MinimapProcessor processor,
			Vec3 renderPos, ResourceKey<Level> dimension, double arg1, int arg2, float arg3, int arg4,
			boolean arg5, boolean arg6, int arg7, double arg8, double arg9, boolean arg10,
			XaeroBufferProvider consumers, CallbackInfo ci) {
		if (!ClientConfig.minimapHeatmap) {
			return;
		}
		MinimapOverlay.draw(poseStack.last().pose(),
				XaeroLib.INSTANCE.getClient().getBufferProvider().getBuffer(CustomRenderTypes.MAP_CHUNK_OVERLAY),
				OptimizedMath.myFloor(renderPos.x), OptimizedMath.myFloor(renderPos.z), Ids.id(dimension));
	}
	//?} else if >=1.21.8 {
	/*@Inject(method = "renderChunksToFBO",
			at = @At(value = "FIELD",
					target = "Lxaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions;CHUNK_GRID:Lxaero/lib/common/config/option/RangeConfigOption;",
					opcode = Opcodes.GETSTATIC, shift = At.Shift.BEFORE),
			remap = false)
	private void msptmap$drawHeatmap(MinimapSession session, PoseStack poseStack, MinimapProcessor processor,
			Vec3 renderPos, ResourceKey<Level> dimension, double arg1, int arg2, float arg3, int arg4,
			boolean arg5, boolean arg6, int arg7, double arg8, double arg9, boolean arg10,
			CustomVertexConsumers consumers, CallbackInfo ci) {
		if (!ClientConfig.minimapHeatmap) {
			return;
		}
		MinimapOverlay.draw(poseStack.last().pose(),
				consumers.getBetterPVPRenderTypeBuffers().getBuffer(CustomRenderTypes.MAP_CHUNK_OVERLAY),
				OptimizedMath.myFloor(renderPos.x), OptimizedMath.myFloor(renderPos.z), Ids.id(dimension));
	}
	*///?} else {
	/*@Inject(method = "renderChunksToFBO",
			at = @At(value = "FIELD",
					target = "Lxaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions;CHUNK_GRID:Lxaero/lib/common/config/option/RangeConfigOption;",
					opcode = Opcodes.GETSTATIC, shift = At.Shift.BEFORE),
			remap = false)
	private void msptmap$drawHeatmap(MinimapSession session, GuiGraphics graphics, MinimapProcessor processor,
			Vec3 renderPos, ResourceKey<Level> dimension, double arg1, int arg2, float arg3, int arg4,
			boolean arg5, boolean arg6, int arg7, double arg8, double arg9, boolean arg10,
			CustomVertexConsumers consumers, CallbackInfo ci) {
		if (!ClientConfig.minimapHeatmap) {
			return;
		}
		MinimapOverlay.draw(graphics.pose().last().pose(),
				consumers.getBetterPVPRenderTypeBuffers().getBuffer(CustomRenderTypes.MAP_CHUNK_OVERLAY),
				OptimizedMath.myFloor(renderPos.x), OptimizedMath.myFloor(renderPos.z), Ids.id(dimension));
	}
	*///?}
}
