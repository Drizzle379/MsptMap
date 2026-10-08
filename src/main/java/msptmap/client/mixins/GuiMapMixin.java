package msptmap.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import msptmap.Ids;
import msptmap.MsptMapMod;
import msptmap.client.ChunkTooltip;
import msptmap.client.ClientConfig;
import msptmap.client.ClientSnapshot;
import msptmap.client.MapOverlay;
import msptmap.client.MsptMapClient;
import msptmap.client.ScanProgress;
import msptmap.client.ScanRing;
import msptmap.client.ScanSummary;
import msptmap.client.SourceListPanel;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.GuiTexturedButton;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/**
 * Xaero 世界地图上的挂点：四个按钮（扫描 / 清屏 / 设置 / 总览折叠）、每帧一次的热力图、悬停详情、
 * 扫描进度圈、扫描总览与右侧完整源列表。
 *
 * <p>目标类及其引用的类型全在 Xaero 中，故该 mixin 单独放在客户端配置
 * （msptmap.client.mixins.json，required:false）：未装世界地图时不至于起不来，最多是没有热力图。
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
	/**
	 * 扫描按钮的框：进度圈贴着它画，故按钮与圈共用这组常量。
	 *
	 * <p>与 Xaero 自己的按钮同规格：框 20×20、图标 16×16。{@code GuiTexturedButton} 把图标在框内
	 * 居中，故四周各留 2 px：恰容下进度圈的 1 px，也使图标墨迹距屏幕左缘 4 px（贴图单元内自 (2,2)
	 * 起）。框贴屏幕左缘，列宽即 {@link #SCAN_BUTTON_SIZE}。
	 */
	@Unique
	private static final int SCAN_BUTTON_X = 0;
	@Unique
	private static final int SCAN_BUTTON_Y = 60;
	@Unique
	private static final int SCAN_BUTTON_SIZE = 20;

	/** 贴图内自 (0,0) 取的图标边长；小于按钮框，两者之差就是居中留下的空当。 */
	@Unique
	private static final int BUTTON_ICON_SIZE = 16;

	/**
	 * 清屏 / 设置按钮的上沿：间隔取按钮边长，框与框严丝合缝——同 Xaero 的按钮列（20×20、相隔 20），
	 * 两钮之间没有既不算上也不算下的死区。
	 */
	@Unique
	private static final int CLEAR_BUTTON_Y = SCAN_BUTTON_Y + SCAN_BUTTON_SIZE;
	@Unique
	private static final int CONFIG_BUTTON_Y = CLEAR_BUTTON_Y + SCAN_BUTTON_SIZE;

	/**
	 * 总览折叠钮：面积为扫描按钮的 1/4，贴在列上方空位的右下角，右缘接列右缘。钮底不接扫描
	 * 按钮上沿，留 4 px（单侧留白的 2 倍），折叠钮图标与扫描图标之间的含投影视觉间距由此同
	 * 三个主按钮图标之间的一致（8 px）。
	 */
	@Unique
	private static final int FOLD_BUTTON_SIZE = SCAN_BUTTON_SIZE / 2;
	@Unique
	private static final int FOLD_BUTTON_X = SCAN_BUTTON_X + SCAN_BUTTON_SIZE - FOLD_BUTTON_SIZE;
	@Unique
	private static final int FOLD_BUTTON_Y = SCAN_BUTTON_Y - FOLD_BUTTON_SIZE - 4;

	/** 扫描总览的左上角与扫描按钮右缘的距离。 */
	@Unique
	private static final int SUMMARY_GAP = 2;

	/**
	 * 扫描总览左上角：右贴按钮列（隔 {@link #SUMMARY_GAP}）；上沿与折叠钮图标齐平——图标悬停时
	 * 上浮 1 px（见 GuiTexturedButton），故取钮上沿 -1。绘制与点击命中共用这组坐标。
	 */
	@Unique
	private static final int SUMMARY_X = SCAN_BUTTON_X + SCAN_BUTTON_SIZE + SUMMARY_GAP;
	@Unique
	private static final int SUMMARY_Y = FOLD_BUTTON_Y - 1;

	/**
	 * 维度 ID 字符串的按对象缓存：同一帧中热力图与悬停详情各要取一次 ID，而两次拿到的是同一个维度
	 * 对象，第二次直接复用。维度切换（换世界、走传送门）后对象随之更换，引用比较自然失效，无需清理。
	 */
	@Unique
	private static MapDimension msptmapLastDimension;
	@Unique
	private static String msptmapLastDimensionId;

	/**
	 * 待执行的定位：维度 + 区块中心方块坐标，记录在点击当帧，实际设置相机目标推迟到维度切换落地
	 * 之后（见 {@link #msptmap$pendingFocus()}）。{@code msptmapPendingDimension} 为 null 表示玩家
	 * 所在维度（同「跟随」）。
	 *
	 * <p>不可在点击当帧就设：Xaero 的维度切换在后台线程落地，而相机坐标空间随显示维度变化，落地
	 * 那一帧 Xaero 会清空相机目标与动画（见 GuiMap 的维度比例变化分支）——提前设的目标会在旧维度
	 * 先滑起来，随后被清掉、停在中途。
	 */
	@Unique
	private ResourceKey<Level> msptmapPendingDimension;
	@Unique
	private int[] msptmapPendingFocus;

	/**
	 * 折叠钮的两个控件：展开态显示 ▾、收起态显示 ▸，同一位置上只留一个可见。
	 *
	 * <p>之所以是两个控件：{@code GuiTexturedButton} 的贴图区域在构造时定死、之后只读，运行中改不了
	 * （其字段为 protected，本包访问不到），而折叠钮要在界面开着的时候换图标。
	 */
	@Unique
	private GuiTexturedButton msptmapFoldExpanded;
	@Unique
	private GuiTexturedButton msptmapFoldCollapsed;

	/**
	 * 折叠钮的悬停提示：说的是下一次点击会做什么。
	 *
	 * <p>两个控件共用同一个 Supplier：Xaero 扫描提示时只比坐标矩形、不看 {@code visible}（见
	 * {@code xaero.lib.client.gui.ScreenBase#renderTooltips}），隐藏的那个照样会被命中，故文案必须
	 * 与命中的是哪一个无关。
	 */
	@Unique
	private static Tooltip msptmapFoldTooltip() {
		return new Tooltip(Component.translatable(ClientConfig.summaryExpanded
				? "msptmap.hint.summary_collapse" : "msptmap.hint.summary_expand"));
	}

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

	/** 相机的动画目标（方块坐标）：非 null 时自下一帧起向它滑动，到达后置 null。定位区块即设它。 */
	@Shadow
	private int[] cameraDestination;

	/** 相机是否跟随玩家：跟随中相机每帧被拉回玩家处，定位区块前须先行脱离（同 Xaero 拖图时）。 */
	@Shadow
	private static boolean attachedCamera;

	/** 请求下一帧重建控件：脱离跟随后同步跟随按钮的外观（同 Xaero 拖图时）。 */
	@Shadow
	public boolean shouldReinit;

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
		// 用 Xaero 的 GuiTexturedButton：无底框、只有图标，悬停时图标上浮 1 px 并在其区域盖一层
		// 半透明白，无需自行绘制。按「隐藏界面」键时 Xaero 会跳过整个控件绘制，按钮随之隐藏；提示由其
		// ScreenBase 扫描控件绘制，为 Supplier，悬停时每帧调用一次，故用 lambda。
		// GuiTexturedButton 构造器两代不同：1.21.1 及以前为 11 参，贴图边长由 Xaero 写死为 256
		// （内部经 GuiGraphics.blit 的 7 参重载绘制，该重载固定按 256×256 采样，给 16×16 的贴图
		// 只会采到左上 1/16 区域、图标不可见）；1.21.3 起尾部多两个参数，边长由调用方给出。故贴图
		// 统一为 256×256（图标画在左上角：三个主按钮取 16×16，折叠钮取 10×10），两代采样同一区域。
		//? if >=1.21.3 {
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.translatable(MsptMapClient.scanButtonHint())), 256, 256));
		// 清屏：位于扫描按钮下一格，同宽同高。只清客户端手上那份结果，服务端不知情。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, CLEAR_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.translatable("msptmap.button.clear")), 256, 256));
		// 设置：位于清屏按钮下一格。parent 传地图屏幕，关闭设置后回地图。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, CONFIG_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/config.png"),
				button -> MsptMapClient.onConfigPress((Screen) (Object) this),
				new Tooltip(Component.translatable("msptmap.button.config")), 256, 256));
		//?} else {
		/*((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.translatable(MsptMapClient.scanButtonHint()))));
		// 清屏：位于扫描按钮下一格，同宽同高。只清客户端手上那份结果，服务端不知情。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, CLEAR_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.translatable("msptmap.button.clear"))));
		// 设置：位于清屏按钮下一格。parent 传地图屏幕，关闭设置后回地图。
		((GuiMap) (Object) this).addButton(new GuiTexturedButton(SCAN_BUTTON_X, CONFIG_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/config.png"),
				button -> MsptMapClient.onConfigPress((Screen) (Object) this),
				new Tooltip(Component.translatable("msptmap.button.config"))));
		*///?}
		msptmap$addFoldButtons();
		// 打开地图时复位右侧完整列表（状态不落盘，见 ScanSummary.resetSourcesPanel）
		ScanSummary.resetSourcesPanel();
	}

	/**
	 * 建折叠钮的两个控件：两者位置相同（{@link #FOLD_BUTTON_X}），靠
	 * {@link #msptmap$syncFoldButtons()} 二选一可见，故从不重叠。
	 *
	 * <p>贴图边长的两个参数只在 1.21.3 起有，两种形态各写一遍（同上面三个按钮）。
	 */
	@Unique
	private void msptmap$addFoldButtons() {
		//? if >=1.21.3 {
		((GuiMap) (Object) this).addButton(msptmapFoldExpanded = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/collapse.png"),
				button -> msptmap$toggleSummary(),
				() -> msptmapFoldTooltip(), 256, 256));
		((GuiMap) (Object) this).addButton(msptmapFoldCollapsed = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/expand.png"),
				button -> msptmap$toggleSummary(),
				() -> msptmapFoldTooltip(), 256, 256));
		//?} else {
		/*((GuiMap) (Object) this).addButton(msptmapFoldExpanded = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/collapse.png"),
				button -> msptmap$toggleSummary(),
				() -> msptmapFoldTooltip()));
		((GuiMap) (Object) this).addButton(msptmapFoldCollapsed = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/expand.png"),
				button -> msptmap$toggleSummary(),
				() -> msptmapFoldTooltip()));
		*///?}
		msptmap$syncFoldButtons();
	}

	/** 折叠钮的点击：翻转开关，并把两个控件的可见性对调。 */
	@Unique
	private void msptmap$toggleSummary() {
		MsptMapClient.onSummaryToggle();
		msptmap$syncFoldButtons();
	}

	/** 按当前开关选定折叠钮显示哪一个控件（另一个不可见：既画不出，也接不到鼠标，见 {@code isMouseOver}）。 */
	@Unique
	private void msptmap$syncFoldButtons() {
		msptmapFoldExpanded.visible = ClientConfig.summaryExpanded;
		msptmapFoldCollapsed.visible = !ClientConfig.summaryExpanded;
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
		// 维度未定时无可绘制内容（也避免下面取 getDimId() 空指针）
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
	 * 定下；Xaero 自身高亮区块用的就是同一个值。压在控件、总览框或右侧完整列表上时不画（见
	 * {@link ChunkTooltip#overWidget}、{@link ScanSummary#overPanel}、{@link SourceListPanel#overPanel}）。
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
		// 鼠标压在控件上时不画（判据见 ChunkTooltip.overWidget）
		if (ChunkTooltip.overWidget(mouseX, mouseY, ((GuiMap) (Object) this).children())) {
			return;
		}
		// 鼠标落在总览框或右侧完整列表上时也让位：详情绘制在它们之后，重叠处会盖住面板
		if (ScanSummary.overPanel(mouseX, mouseY, SUMMARY_X, SUMMARY_Y)
				|| SourceListPanel.overPanel(mouseX, mouseY, msptmap$sourcesPanelX(), SUMMARY_Y)) {
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
		// 按「隐藏界面」键时随按钮一起隐藏（与悬停详情一致）
		if (GuiMap.hiddenUI || !ScanProgress.active()) {
			return;
		}
		ScanRing.draw(graphics, ScanProgress.fraction(), SCAN_BUTTON_X, SCAN_BUTTON_Y, SCAN_BUTTON_SIZE);
	}

	/**
	 * 扫描总览：挂在 Xaero 绘制控件提示（{@code renderTooltips}）之前——压住地图与按钮、又让按钮
	 * 的悬停提示画在自己上面（提示被挡住就看不见了）。收起或无数据时 {@link ScanSummary#draw} 跳过不画。
	 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lxaero/map/gui/GuiMap;renderTooltips(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)Z",
					shift = At.Shift.BEFORE),
			remap = false)
	private void msptmap$drawSummary(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$summary(graphics, mouseX, mouseY);
	}
	//?} else {
	/*@Inject(method = "method_25394",
			at = @At(value = "INVOKE",
					target = "Lxaero/map/gui/GuiMap;renderTooltips(Lnet/minecraft/class_332;IIF)Z",
					shift = At.Shift.BEFORE),
			remap = false)
	private void msptmap$drawSummary(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$summary(graphics, mouseX, mouseY);
	}
	*///?}

	/** 总览的绘制体（与注入点的方法名 / 屏幕类型无关，两种形态共用）。 */
	@Unique
	//? if >=26.1 {
	private void msptmap$summary(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*private void msptmap$summary(GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		// 按「隐藏界面」键时随按钮一起隐藏（与悬停详情一致）；收起由折叠钮决定，与隐藏界面无关
		if (GuiMap.hiddenUI || !ClientConfig.summaryExpanded) {
			return;
		}
		// 鼠标坐标用于给悬停的可点击行垫高亮底（见 ScanSummary.draw 与 SourceListPanel.draw）
		ScanSummary.draw(graphics, SUMMARY_X, SUMMARY_Y, mouseX, mouseY);
		if (ScanSummary.sourcesPanelOpen()) {
			SourceListPanel.draw(graphics, msptmap$sourcesPanelX(), SUMMARY_Y, mouseX, mouseY);
		}
	}

	/** 右侧完整列表的横坐标：紧贴总览右缘（隔 {@link #SUMMARY_GAP}）；纵坐标与总览同用 {@link #SUMMARY_Y}。 */
	@Unique
	private static int msptmap$sourcesPanelX() {
		return SUMMARY_X + ScanSummary.panelSize()[0] + SUMMARY_GAP;
	}

	/**
	 * 总览可点击行（TOP、源行、折叠行）与右侧完整列表的点击：挂在 mouseClicked 最前，命中即消费
	 * （否则 Xaero 会当作拖图起点）；左键编号：26.3 起为 1、其余（含 26.1.2、26.2）为 0。
	 */
	//? if >=26.1 {
	@Inject(method = "mouseClicked", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$clickSummary(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		//? if >=26.3 {
		/*boolean left = event.button() == 1;
		*///?} else {
		boolean left = event.button() == 0;
		//?}
		if (msptmap$summaryClick(left, (int) event.x(), (int) event.y())) {
			cir.setReturnValue(true);
		}
	}
	//?} else if >=1.21.11 {
	/*@Inject(method = "method_25402", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$clickSummary(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> cir) {
		if (msptmap$summaryClick(event.button() == 0, (int) event.x(), (int) event.y())) {
			cir.setReturnValue(true);
		}
	}
	*///?} else {
	/*@Inject(method = "method_25402", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$clickSummary(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (msptmap$summaryClick(button == 0, (int) mouseX, (int) mouseY)) {
			cir.setReturnValue(true);
		}
	}
	*///?}

	/**
	 * 点击的命中判定与定位：命中（左键、总览可见、正指着某可定位行）则把地图定位到该区块并返回
	 * true；折叠行只开合右侧列表。坐标由调用处从事件取（GUI 缩放坐标，勿用 Xaero 的 {@code Misc}）。
	 */
	@Unique
	private boolean msptmap$summaryClick(boolean leftButton, int mouseX, int mouseY) {
		if (!leftButton || GuiMap.hiddenUI || !ClientConfig.summaryExpanded) {
			return false;
		}
		if (mapProcessor == null || !mapProcessor.isMapWorldUsable()) {
			return false;
		}
		// 鼠标压在控件上时让位：与悬停详情的判据一致，画不出详情的地方也不该响应点击
		if (ChunkTooltip.overWidget(mouseX, mouseY, ((GuiMap) (Object) this).children())) {
			return false;
		}
		// 折叠行：只开合右侧完整列表，不定位
		if (ScanSummary.hitFold(mouseX, mouseY, SUMMARY_X, SUMMARY_Y)) {
			ScanSummary.toggleSourcesPanel();
			return true;
		}
		// 先右框后总览：两框位置不重叠，先后不影响结果
		ScanSummary.Target target = SourceListPanel.hitTarget(mouseX, mouseY, msptmap$sourcesPanelX(), SUMMARY_Y);
		if (target == null) {
			target = ScanSummary.hitTarget(mouseX, mouseY, SUMMARY_X, SUMMARY_Y);
		}
		if (target == null) {
			return false;
		}
		msptmap$focusChunk(target);
		return true;
	}

	/**
	 * 把地图定位到某区块：必要时先切维度（同 Xaero 的维度按钮：目标即玩家所在维度时回「跟随玩家」），
	 * 并脱离跟随相机；相机的动画目标（区块中心）延后到切换落地再设（见
	 * {@link #msptmap$pendingFocus()}）。
	 */
	@Unique
	private void msptmap$focusChunk(ScanSummary.Target target) {
		MapWorld mapWorld = mapProcessor.getMapWorld();
		MapDimension found = msptmap$findDimension(mapWorld, target.dimension());
		if (found == null) {
			// 该维度的地图从未加载过（玩家没去过）：切不过去，这次点击不响应
			MsptMapMod.LOGGER.info("定位 {} 区块 ({}, {})：该维度地图未加载，忽略",
					target.dimension(), target.chunkX(), target.chunkZ());
			return;
		}
		ResourceKey<Level> key = found.getDimId();
		if (key == Minecraft.getInstance().level.dimension()) {
			key = null;
		}
		MsptMapMod.LOGGER.info("定位 {} 区块 ({}, {})：目标维度 {}", target.dimension(),
				target.chunkX(), target.chunkZ(), key == null ? "当前（跟随）" : Ids.id(key));
		mapWorld.setCustomDimensionId(key);
		mapProcessor.checkForWorldUpdate();
		if (attachedCamera) {
			attachedCamera = false;
			shouldReinit = true;
		}
		// 区块中心：Xaero 的相机目标就是方块坐标；先记下，切换落地后才设（见字段注释）
		msptmapPendingDimension = key;
		msptmapPendingFocus = new int[]{target.chunkX() * 16 + 8, target.chunkZ() * 16 + 8};
	}

	/**
	 * 待执行定位的落地：挂在每帧渲染末尾（TAIL）——该处晚于 Xaero 的相机复位（维度比例变化、拉回
	 * 玩家等清空相机目标与动画的分支），此时设的目标不会被清掉。切换未完成时继续等。
	 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
	private void msptmap$applyPendingFocus(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$pendingFocus();
	}
	//?} else {
	/*@Inject(method = "method_25394", at = @At("TAIL"), remap = false)
	private void msptmap$applyPendingFocus(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		msptmap$pendingFocus();
	}
	*///?}

	/** 应用待执行的定位：维度已切到目标、地图可用且不再等待世界更新时，把区块中心设为相机目标。 */
	@Unique
	private void msptmap$pendingFocus() {
		if (msptmapPendingFocus == null || mapProcessor == null) {
			return;
		}
		// 切换中或地图世界重建中：此时设了也会被 Xaero 清掉，继续等
		if (mapProcessor.isWaitingForWorldUpdate() || !mapProcessor.isMapWorldUsable()) {
			return;
		}
		ResourceKey<Level> expected = msptmapPendingDimension != null ? msptmapPendingDimension
				: Minecraft.getInstance().level.dimension();
		// 当前显示维度尚未切到目标：地图空间不同，坐标不可用，继续等
		if (!expected.equals(mapProcessor.getMapWorld().getCurrentDimensionId())) {
			return;
		}
		cameraDestination = msptmapPendingFocus;
		msptmapPendingFocus = null;
	}

	/**
	 * 右侧完整列表的滚轮翻动：同样挂在 mouseScrolled 最前，鼠标在面板上即消费（否则被当作地图
	 * 缩放）；参数按版本三档（1.20.1 三参、1.20.2 起四参、26.x 起 named），都只取纵向量。
	 */
	//? if >=26.1 {
	@Inject(method = "mouseScrolled", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$scrollSources(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (msptmap$sourcesScroll(mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}
	//?} else if >=1.20.2 {
	/*@Inject(method = "method_25401", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$scrollSources(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (msptmap$sourcesScroll(mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}
	*///?} else {
	/*@Inject(method = "method_25401", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$scrollSources(double mouseX, double mouseY, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (msptmap$sourcesScroll(mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}
	*///?}

	/** 滚轮的判定体（与注入点的方法名 / 参数个数无关，三档共用）：在面板上则翻动并返回 true。 */
	@Unique
	private static boolean msptmap$sourcesScroll(double mouseX, double mouseY, double amount) {
		if (GuiMap.hiddenUI || !ScanSummary.sourcesPanelOpen()) {
			return false;
		}
		return SourceListPanel.scroll((int) mouseX, (int) mouseY, msptmap$sourcesPanelX(), SUMMARY_Y,
				(int) Math.signum(amount));
	}

	/** 从地图已加载的维度里按 ID 找目标；没有则 null。按 ID 字符串反查，避开各版本构造维度键的差异。 */
	@Unique
	private static MapDimension msptmap$findDimension(MapWorld mapWorld, String dimId) {
		for (MapDimension dimension : mapWorld.getDimensionsList()) {
			if (dimId.equals(msptmapDimensionId(dimension))) {
				return dimension;
			}
		}
		return null;
	}
}
