package msptmap.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import msptmap.Ids;
import msptmap.MsptMapMod;
import msptmap.client.ChunkTooltip;
import msptmap.client.ClientSnapshot;
import msptmap.client.MapOverlay;
import msptmap.client.MsptMapClient;
import msptmap.client.ScanProgress;
import msptmap.client.ScanRing;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.GuiTexturedButton;
import xaero.map.world.MapDimension;

/**
 * Xaero 世界地图上的挂点：两个按钮（扫描 / 清屏）、每帧一次的热力图、悬停详情、扫描进度圈。
 *
 * 目标类及其引用的类型全在 Xaero 中，故该 mixin 单独放在客户端配置（msptmap.client.mixins.json，
 * required:false）：未装世界地图时不至于起不来，最多是没有热力图。
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
	/** 扫描按钮的框：进度圈贴着它画，故按钮与圈共用这组常量。 */
	@Unique
	private static final int SCAN_BUTTON_X = 0;
	@Unique
	private static final int SCAN_BUTTON_Y = 40;
	@Unique
	private static final int SCAN_BUTTON_SIZE = 20;

	/** 地图显示的维度、地图世界是否可用，均由它取得。 */
	@Shadow
	private MapProcessor mapProcessor;

	/** Xaero 算好的鼠标所在方块坐标：悬停详情指向哪一块由它俩决定。 */
	@Shadow
	private int mouseBlockPosX;

	@Shadow
	private int mouseBlockPosZ;

	// 方法名同 render 那两处：1.21.11 及以前是 Screen.init 的 intermediary 名 method_25426
	//? if >=26.1 {
	@Inject(method = "init", at = @At("TAIL"), remap = false)
	//?} else {
	/*@Inject(method = "method_25426", at = @At("TAIL"), remap = false)
	*///?}
	private void msptmap$addButton(CallbackInfo ci) {
		// 左侧那一列：齿轮在 (0,0) 的 30×30，Xaero 自己的按钮都在右边那列和底边，这一段是空的。
		// 用 Xaero 自己的 GuiTexturedButton：无底框、只有图标，悬停时图标上浮并变亮，无需自行绘制
		// 尺寸照左下角那一列（20×20 的按钮内画 16×16 图标，白块 11×11 居中、右下 1 px 灰影）。
		// 按「隐藏界面」键时 Xaero 会跳过整个控件绘制，按钮随之隐藏；提示也是它自己的 ScreenBase
		// 扫控件画的。提示为 Supplier，悬停时每帧调用一次，故用 lambda。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.literal(MsptMapClient.scanButtonHint())), 16, 16));
		// 清屏：位于扫描按钮下一格，同宽同高。只清客户端手上那份结果，服务端不知情。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(0, 62, 20, 20, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.literal("清空Mspt地图")), 16, 16));
	}

	/**
	 * 绘制挂在 GuiMap.prevLoadingLeaves 字段写完的那一刻：恰在 Xaero 打开叠加层缓冲之后、收缓冲之前，
	 * 故画进去的方块必被这一帧画出，又不会盖住之后绘制的路标与文字。
	 *
	 * 局部变量按名字取（Xaero 的类带有局部变量表），不写死槽位号：槽位号随版本变化，名字不会。
	 * 注入点的方法名随 MC 版本：1.21.11 及以前是 Screen.render 的 intermediary 名 method_25394
	 * （Xaero 发布时被重映射成这个），26.1 起 Xaero 随原版改名 extractRenderState。
	 * 两个形态只差方法名与屏幕类型的写法，绘制体共用 {@link #msptmap$heatmap}。
	 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState",
			at = @At(value = "FIELD", target = "Lxaero/map/gui/GuiMap;prevLoadingLeaves:Z",
					opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
			remap = false)
	private void msptmap$drawHeatmap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci,
			@Local(name = "overlayBuffer") VertexConsumer overlayBuffer,
			@Local(name = "matrixStack") PoseStack matrixStack,
			@Local(name = "flooredCameraX") int flooredCameraX,
			@Local(name = "flooredCameraZ") int flooredCameraZ,
			@Local(name = "currentDim") MapDimension currentDim) {
		msptmap$heatmap(overlayBuffer, matrixStack, flooredCameraX, flooredCameraZ, currentDim);
	}
	//?} else {
	/*@Inject(method = "method_25394",
			at = @At(value = "FIELD", target = "Lxaero/map/gui/GuiMap;prevLoadingLeaves:Z",
					opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
			remap = false)
	private void msptmap$drawHeatmap(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci,
			@Local(name = "overlayBuffer") VertexConsumer overlayBuffer,
			@Local(name = "matrixStack") PoseStack matrixStack,
			@Local(name = "flooredCameraX") int flooredCameraX,
			@Local(name = "flooredCameraZ") int flooredCameraZ,
			@Local(name = "currentDim") MapDimension currentDim) {
		msptmap$heatmap(overlayBuffer, matrixStack, flooredCameraX, flooredCameraZ, currentDim);
	}
	*///?}

	/** 热力图的绘制体（与注入点的方法名 / 屏幕类型无关，两种形态共用）。 */
	@Unique
	private void msptmap$heatmap(VertexConsumer overlayBuffer, PoseStack matrixStack, int flooredCameraX,
			int flooredCameraZ, MapDimension currentDim) {
		// 维度未定时无可绘制内容（也免得下面取 getDimId() 空指针）
		if (currentDim == null) {
			return;
		}
		MapOverlay.draw(matrixStack.last().pose(), overlayBuffer, flooredCameraX, flooredCameraZ,
				Ids.id(currentDim.getDimId()));
	}

	/**
	 * 悬停详情挂在方法最末尾（TAIL）：此处地图已绘制完毕、Xaero 的缩放平移也已收干净，用 guiGraphics
	 * 绘制的字必在最上层，坐标为普通屏幕坐标。不与热力图共用注入点：那个点位于地图自身的矩阵内，
	 * 在那里绘制的字会随地图缩放。
	 *
	 * 读字段而非局部变量：{@code mouseBlockPosX/Z} 本帧最后一次写入在方法很靠前处，到 TAIL 早已定下；
	 * Xaero 自己高亮区块用的就是同一个值。鼠标压在控件上时整个不画，判定见
	 * {@link ChunkTooltip#overWidget}。
	 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
	private void msptmap$drawHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$hoverTooltip(graphics, mouseX, mouseY);
	}
	//?} else {
	/*@Inject(method = "method_25394", at = @At("TAIL"), remap = false)
	private void msptmap$drawHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$hoverTooltip(graphics, mouseX, mouseY);
	}
	*///?}

	/** 悬停详情的绘制体（与注入点的方法名 / 屏幕类型无关，两种形态共用）。 */
	@Unique
	//? if >=26.1 {
	private void msptmap$hoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*private void msptmap$hoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		// 按 Xaero「隐藏界面」键时详情随控件一起隐藏（热力图不隐藏，它属于地图内容）
		if (GuiMap.hiddenUI || mapProcessor == null || !mapProcessor.isMapWorldUsable()) {
			return;
		}
		// 鼠标压在控件上时不画：Xaero 自己的提示框同在鼠标处绘制，会重叠
		// （两侧按钮、底边控件、搜索框、展开的下拉列表都算）
		if (ChunkTooltip.overWidget(mouseX, mouseY, ((GuiMap) (Object) this).children())) {
			return;
		}
		MapDimension dimension = mapProcessor.getMapWorld().getCurrentDimension();
		if (dimension == null) {
			return;
		}
		String dimId = Ids.id(dimension.getDimId());
		// 该维度从未扫描则不显示：没有热力图的地方不应出现「未采样」
		if (ClientSnapshot.get(dimId) == null) {
			return;
		}
		int chunkX = mouseBlockPosX >> 4;
		int chunkZ = mouseBlockPosZ >> 4;
		ChunkTooltip.draw(graphics, mouseX, mouseY,
				ChunkTooltip.lines(ClientSnapshot.find(dimId, chunkX, chunkZ), chunkX, chunkZ,
						ClientSnapshot.windowTicks()));
	}

	/**
	 * 扫描进度圈：同样挂在 TAIL（此处 Xaero 控件已绘制完，坐标为普通屏幕坐标）。画在扫描按钮自身的
	 * 边框上（理由见 {@link ScanRing}）。
	 *
	 * 不自行计算任何数值：进度全部来自服务端每 0.1 秒推送的包（见 {@link ScanProgress}），故单人档
	 * 地图开着（世界暂停、服务端发不出包）时它不动。
	 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
	private void msptmap$drawScanRing(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$scanRing(graphics);
	}
	//?} else {
	/*@Inject(method = "method_25394", at = @At("TAIL"), remap = false)
	private void msptmap$drawScanRing(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$scanRing(graphics);
	}
	*///?}

	/** 进度圈的绘制体（与注入点的方法名 / 屏幕类型无关，两种形态共用）。 */
	@Unique
	//? if >=26.1 {
	private void msptmap$scanRing(GuiGraphicsExtractor graphics) {
	//?} else {
	/*private void msptmap$scanRing(GuiGraphics graphics) {
	*///?}
		// 按「隐藏界面」键时随按钮一起隐藏（和悬停详情一个规矩）
		if (GuiMap.hiddenUI || !ScanProgress.active()) {
			return;
		}
		ScanRing.draw(graphics, ScanProgress.fraction(), SCAN_BUTTON_X, SCAN_BUTTON_Y, SCAN_BUTTON_SIZE);
	}
}
