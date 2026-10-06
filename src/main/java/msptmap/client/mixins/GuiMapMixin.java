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
import msptmap.client.ScanSummary;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
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
 * Xaero 世界地图上的挂点：三个按钮（扫描 / 清屏 / 设置）、每帧一次的热力图、悬停详情、扫描进度圈
 * 与扫描总览。
 *
 * <p>目标类及其引用的类型全在 Xaero 中，故该 mixin 单独放在客户端配置
 * （msptmap.client.mixins.json，required:false）：未装世界地图时不至于起不来，最多是没有热力图。
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
	/**
	 * 扫描按钮的框：进度圈贴着它画，故按钮与圈共用这组常量。
	 *
	 * <p>x 取 2 而非 0：使图标墨迹（贴图内自 (2,2) 起）距屏幕左缘 4 px，与 Xaero 左下角按钮的
	 * 图标对齐（其按钮 20×20，图标在按钮内居中偏 2、墨迹在贴图单元内再偏 2，屏幕最左同为 4）。
	 */
	@Unique
	private static final int SCAN_BUTTON_X = 2;
	@Unique
	private static final int SCAN_BUTTON_Y = 60;
	@Unique
	private static final int SCAN_BUTTON_SIZE = 16;

	/** 扫描总览的左上角与扫描按钮右缘的距离（上边与按钮齐平）。 */
	@Unique
	private static final int SUMMARY_GAP = 4;

	/**
	 * 维度 ID 字符串的按对象缓存：同一帧中热力图与悬停详情各要取一次 ID，而两次拿到的是同一个维度
	 * 对象，第二次直接复用。维度切换（换世界、走传送门）后对象随之更换，引用比较自然失效，无需清理。
	 */
	@Unique
	private static MapDimension msptmapLastDimension;
	@Unique
	private static String msptmapLastDimensionId;

	/** 维度 ID（如 {@code minecraft:overworld}）。注册表反查 + 字符串构造不便宜，同一个对象只算一次。 */
	@Unique
	private static String msptmapDimensionId(MapDimension dimension) {
		if (dimension != msptmapLastDimension) {
			msptmapLastDimension = dimension;
			msptmapLastDimensionId = Ids.id(dimension.getDimId());
		}
		return msptmapLastDimensionId;
	}

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
		// 左侧空置的一列：齿轮在 (0,0) 的 30×30，Xaero 自己的按钮都在右边一列和底边。
		// 用 Xaero 的 GuiTexturedButton：无底框、只有图标，悬停时图标上浮并变亮，无需自行绘制。
		// 按「隐藏界面」键时 Xaero 会跳过整个控件绘制，按钮随之隐藏；提示由其 ScreenBase 扫描控件
		// 绘制，为 Supplier，悬停时每帧调用一次，故用 lambda。
		// GuiTexturedButton 构造器两代不同：1.21.1 及以前为 11 参，贴图边长由 Xaero 写死为 256
		// （内部经 GuiGraphics.blit 的 7 参重载绘制，该重载固定按 256×256 采样，给 16×16 的贴图
		// 只会采到左上 1/16 区域、图标不可见）；1.21.3 起尾部多两个参数，边长由调用方给出。故贴图
		// 统一为 256×256（16×16 的图标画在左上角），两代采样同一区域。
		//? if >=1.21.3 {
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.translatable(MsptMapClient.scanButtonHint())), 256, 256));
		// 清屏：位于扫描按钮下一格，同宽同高。只清客户端手上那份结果，服务端不知情。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(2, 78, 16, 16, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.translatable("msptmap.button.clear")), 256, 256));
		// 设置：位于清屏按钮下一格。parent 传地图屏幕，关闭设置后回地图。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(2, 96, 16, 16, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/config.png"),
				button -> MsptMapClient.onConfigPress((Screen) (Object) this),
				new Tooltip(Component.translatable("msptmap.button.config")), 256, 256));
		//?} else {
		/*((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.translatable(MsptMapClient.scanButtonHint()))));
		// 清屏：位于扫描按钮下一格，同宽同高。只清客户端手上那份结果，服务端不知情。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(2, 78, 16, 16, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.translatable("msptmap.button.clear"))));
		// 设置：位于清屏按钮下一格。parent 传地图屏幕，关闭设置后回地图。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(2, 96, 16, 16, 0, 0, 16, 16,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/config.png"),
				button -> MsptMapClient.onConfigPress((Screen) (Object) this),
				new Tooltip(Component.translatable("msptmap.button.config"))));
		*///?}
	}

	/**
	 * 绘制挂在 GuiMap.prevLoadingLeaves 字段写完的那一刻：恰在 Xaero 打开叠加层缓冲之后、收缓冲之前，
	 * 故画进去的方块必被这一帧画出，又不会盖住之后绘制的路标与文字。
	 *
	 * <p>局部变量按名字取（Xaero 的类带有局部变量表），不写死槽位号：槽位号随版本变化，名字不会。
	 * 注入点的方法名随 MC 版本：1.21.11 及以前是 Screen.render 的 intermediary 名 method_25394
	 * （Xaero 发布时被重映射成它），26.1 起 Xaero 随原版改名 extractRenderState。两个形态只差方法名
	 * 与屏幕类型的写法，绘制体共用 {@link #msptmap$heatmap}。
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
				msptmapDimensionId(currentDim));
	}

	/**
	 * 悬停详情挂在方法最末尾（TAIL）：此处地图已绘制完毕、Xaero 的缩放平移也已收干净，用 guiGraphics
	 * 绘制的文字必在最上层，坐标为普通屏幕坐标。不与热力图共用注入点：后者位于地图自身的矩阵内，
	 * 在那里绘制的文字会随地图缩放。
	 *
	 * <p>读字段而非局部变量：{@code mouseBlockPosX/Z} 本帧最后一次写入在方法很靠前处，到 TAIL 早已
	 * 定下；Xaero 自身高亮区块用的就是同一个值。鼠标压在控件上时整个不画，判定见
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
		String dimId = msptmapDimensionId(dimension);
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
	 * <p>不自行计算任何数值：进度全部来自服务端每 0.1 秒推送的包（见 {@link ScanProgress}），故
	 * 单人档地图开着（世界暂停、服务端发不出包）时它不动。
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

	/**
	 * 扫描总览：同挂在 TAIL（坐标为普通屏幕坐标），位置固定在扫描按钮右上角。无数据时
	 * {@link ScanSummary#draw} 拿到空行，自然不会画。
	 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
	private void msptmap$drawSummary(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$summary(graphics);
	}
	//?} else {
	/*@Inject(method = "method_25394", at = @At("TAIL"), remap = false)
	private void msptmap$drawSummary(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$summary(graphics);
	}
	*///?}

	/** 总览的绘制体（与注入点的方法名 / 屏幕类型无关，两种形态共用）。 */
	@Unique
	//? if >=26.1 {
	private void msptmap$summary(GuiGraphicsExtractor graphics) {
	//?} else {
	/*private void msptmap$summary(GuiGraphics graphics) {
	*///?}
		// 按「隐藏界面」键时随按钮一起隐藏（和悬停详情一个规矩）
		if (GuiMap.hiddenUI) {
			return;
		}
		ScanSummary.draw(graphics, SCAN_BUTTON_X + SCAN_BUTTON_SIZE + SUMMARY_GAP, SCAN_BUTTON_Y);
	}
}
