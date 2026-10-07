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
 * Xaero 世界地图上的挂点：四个按钮（扫描 / 清屏 / 设置 / 总览折叠）、每帧一次的热力图、悬停详情、
 * 扫描进度圈与扫描总览。
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
	 * 清屏 / 设置按钮的上沿：间隔取按钮边长，框与框严丝合缝 —— 同 Xaero 的按钮列（20×20、相隔 20），
	 * 两钮之间没有既不算上也不算下的死区。
	 */
	@Unique
	private static final int CLEAR_BUTTON_Y = SCAN_BUTTON_Y + SCAN_BUTTON_SIZE;
	@Unique
	private static final int CONFIG_BUTTON_Y = CLEAR_BUTTON_Y + SCAN_BUTTON_SIZE;

	/**
	 * 总览折叠钮：面积为扫描按钮的 1/4，贴在列上方空位的右下角 —— 右缘接列右缘。钮底不接扫描
	 * 按钮上沿，而是留 4 px（主按钮图标在钮内单侧的留白），图标与扫描图标的含投影视觉间距由
	 * 此同三个主按钮图标之间的一致（8 px）。
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
	 * 维度 ID 字符串的按对象缓存：同一帧中热力图与悬停详情各要取一次 ID，而两次拿到的是同一个维度
	 * 对象，第二次直接复用。维度切换（换世界、走传送门）后对象随之更换，引用比较自然失效，无需清理。
	 */
	@Unique
	private static MapDimension msptmapLastDimension;
	@Unique
	private static String msptmapLastDimensionId;

	/**
	 * 折叠钮的两个控件：展开态显示 ▾、收起态显示 ▸，同一位置上只留一个可见。
	 *
	 * <p>之所以是两个控件：{@code GuiTexturedButton} 的贴图区域在构造时定死、之后只读，运行中改不了
	 * （其字段为 protected，本包也够不着），而折叠钮要在界面开着的时候换图标。
	 */
	@Unique
	private GuiTexturedButton msptmapFoldExpanded;
	@Unique
	private GuiTexturedButton msptmapFoldCollapsed;

	/**
	 * 折叠钮的悬停提示：说的是下一击会做什么。
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
		// 用 Xaero 的 GuiTexturedButton：无底框、只有图标，悬停时图标上浮 1 px 并在其区域盖一层半透
		// 明白，无需自行绘制。按「隐藏界面」键时 Xaero 会跳过整个控件绘制，按钮随之隐藏；提示由其
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
		msptmap$addFoldButtons();
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
		msptmap$addFoldButtons();
		*///?}
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
	 * 扫描总览：同挂在 TAIL（坐标为普通屏幕坐标），左上角贴着折叠钮右上角（上沿与折叠钮图标齐平，
	 * 见下方调用处注释）。收起时整个不画。无数据时 {@link ScanSummary#draw} 拿到空行，自然也不会画。
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
		// 按「隐藏界面」键时随按钮一起隐藏（和悬停详情一个规矩）；收起由折叠钮决定，与隐藏界面无关
		if (GuiMap.hiddenUI || !ClientConfig.summaryExpanded) {
			return;
		}
		// 上沿与折叠钮图标的上沿齐平：图标悬停时会上浮 1 px（见 GuiTexturedButton），故比钮上沿高
		// 1 px；点击开合后鼠标停在钮上，看到的即是齐平状态。
		ScanSummary.draw(graphics, SCAN_BUTTON_X + SCAN_BUTTON_SIZE + SUMMARY_GAP, FOLD_BUTTON_Y - 1);
	}
}
