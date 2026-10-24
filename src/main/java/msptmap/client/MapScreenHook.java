package msptmap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import msptmap.MsptMapMod;
import msptmap.util.Ids;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.GuiTexturedButton;
import xaero.map.world.MapDimension;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Xaero 世界地图上的挂点：四个按钮（扫描 / 清屏 / 设置 / 总览折叠）、每帧一次的热力图、
 * 悬停详情、扫描进度圈、扫描总览与右侧完整源列表。
 *
 * <p>不直接 Mixin {@code GuiMap}：被 Mixin 重写时 ASM 要重算该类全部方法的栈帧，其超大方法在
 * Loader 0.20.0（ASM 9.11）下触发单类操作数上限，启动即崩。改由 XaeroLib 的 {@code ScreenBase}
 * 与 {@code MapRenderHelper} 的 mixin 调用本类；本类只在确认屏幕是 GuiMap 后才被加载，未装世界
 * 地图时不会加载。
 */
public final class MapScreenHook implements ChunkFocus.Host {
	/**
	 * 扫描按钮的框：进度圈贴该框绘制，按钮与进度圈共用这组常量。
	 *
	 * <p>与 Xaero 自身按钮同规格：框 20×20、图标 16×16。{@code GuiTexturedButton} 将图标在框内
	 * 居中，四周各留 2 px：恰好容纳进度圈的 1 px，并使图标距屏幕左缘 4 px（贴图单元内自 (2,2)
	 * 起）。框贴屏幕左缘，列宽即 {@link #SCAN_BUTTON_SIZE}。
	 */
	private static final int SCAN_BUTTON_X = 0;
	private static final int SCAN_BUTTON_Y = 60;
	private static final int SCAN_BUTTON_SIZE = 20;

	/** 贴图内自 (0,0) 取的图标边长；小于按钮框，两者之差即居中留白。 */
	private static final int BUTTON_ICON_SIZE = 16;

	/**
	 * 清屏 / 设置按钮的上沿：间隔取按钮边长，框与框紧邻，同 Xaero 的按钮列（20×20、相隔 20），
	 * 两钮之间没有归属不明的空隙。
	 */
	private static final int CLEAR_BUTTON_Y = SCAN_BUTTON_Y + SCAN_BUTTON_SIZE;
	private static final int CONFIG_BUTTON_Y = CLEAR_BUTTON_Y + SCAN_BUTTON_SIZE;

	/**
	 * 总览折叠钮：面积为扫描按钮的 1/4，位于列上方空位的右下角，右缘与列右缘对齐。钮底与扫描
	 * 按钮上沿留 4 px（单侧留白的 2 倍），使折叠钮图标与扫描图标之间的视觉间距（含投影）与
	 * 三个主按钮图标之间的一致（8 px）。
	 */
	private static final int FOLD_BUTTON_SIZE = SCAN_BUTTON_SIZE / 2;
	private static final int FOLD_BUTTON_X = SCAN_BUTTON_X + SCAN_BUTTON_SIZE - FOLD_BUTTON_SIZE;
	private static final int FOLD_BUTTON_Y = SCAN_BUTTON_Y - FOLD_BUTTON_SIZE - 4;

	/** 扫描总览的左上角与扫描按钮右缘的距离。 */
	private static final int SUMMARY_GAP = 2;

	/**
	 * 扫描总览左上角：右贴按钮列（隔 {@link #SUMMARY_GAP}）；上沿与折叠钮图标齐平。图标悬停时
	 * 上浮 1 px（见 GuiTexturedButton），故取钮上沿 -1。绘制与点击命中共用这组坐标。
	 */
	private static final int SUMMARY_X = SCAN_BUTTON_X + SCAN_BUTTON_SIZE + SUMMARY_GAP;
	private static final int SUMMARY_Y = FOLD_BUTTON_Y - 1;

	/** 每张地图屏一个挂点：按钮、定位状态机与 GuiMap 的私有状态都经由它访问。 */
	private static final Map<GuiMap, MapScreenHook> HOOKS = new WeakHashMap<>();

	/**
	 * 上次复位面板状态时所在的地图屏实例：Xaero 切维度会在同一屏上重建控件、再走一次 {@code init}
	 * （见 {@link #onInit}），据实例区分「重新打开地图」与「同一次地图内切维度」。
	 */
	private static GuiMap resetScreen;

	/*
	 * GuiMap 的私有状态：无法用 @Shadow（本类不是它的 mixin），改由反射访问。字段名在 Xaero
	 * 1.44–1.47 之间一致；取不到时对应功能降级，不影响地图与按钮。
	 */
	private static final Field CAMERA_DESTINATION = field("cameraDestination");
	private static final Field ATTACHED_CAMERA = field("attachedCamera");
	private static final Field MOUSE_BLOCK_POS_X = field("mouseBlockPosX");
	private static final Field MOUSE_BLOCK_POS_Z = field("mouseBlockPosZ");
	private static final Field MAP_TILE_SELECTION = field("mapTileSelection");

	private final WeakReference<GuiMap> mapRef;

	/**
	 * 区块定位的状态机：将点击总览行与外部请求的目标作用于相机，时序与理由见 {@link ChunkFocus}。
	 * 本类作为宿主，提供地图处理器与相机、跟随状态的读写。
	 */
	private final ChunkFocus focus = new ChunkFocus(this);

	/**
	 * 折叠钮的两个控件：展开态显示 ▾、收起态显示 ▸，同一位置只留一个可见。
	 *
	 * <p>用两个控件的原因：{@code GuiTexturedButton} 的贴图区域构造时确定、之后只读，运行中无法修改
	 * （其字段为 protected，本包不可访问），而折叠钮需要在界面开启期间更换图标。
	 */
	private GuiTexturedButton foldExpanded;
	private GuiTexturedButton foldCollapsed;

	private MapScreenHook(GuiMap map) {
		this.mapRef = new WeakReference<>(map);
	}

	/* ---------- mixin 入口 ---------- */

	/** 地图屏初始化完成后重建本模组的按钮（见 {@link #addButtons}）。 */
	public static void onInit(Screen screen) {
		MapScreenHook hook = of(screen);
		hook.addButtons();
		GuiMap map = hook.map();
		// 重新打开地图时复位面板状态：右侧完整列表收起、总览与它都回到顶部（不落盘，见
		// resetSourcesPanel）。按屏实例判断，切维度虽会重建控件并再走一次 init，展开态须照旧
		if (map != null && map != resetScreen) {
			resetScreen = map;
			ScanSummary.resetSourcesPanel();
		}
	}

	/** 每帧绘制总览、进度圈，并推进待执行的定位。 */
	//? if >=26.1 {
	public static void onRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*public static void onRender(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		MapScreenHook hook = of(screen);
		hook.drawSummary(graphics, mouseX, mouseY);
		hook.drawScanRing(graphics);
		hook.focus.tick();
	}

	/**
	 * 悬停详情：挂在 {@code renderTooltips} 末尾——该处 Xaero 的区块提示已绘制完，本模组的详情
	 * 才能压在其上（两者都在鼠标旁）。拖图或下拉菜单悬停时 Xaero 不调用该方法，详情随之中断，
	 * 与 Xaero 自身的提示行为一致。
	 */
	//? if >=26.1 {
	public static void onRenderTooltips(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*public static void onRenderTooltips(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		of(screen).drawHoverTooltip(graphics, mouseX, mouseY);
	}

	/** 总览与右侧列表的点击：返回 true 表示已消费（否则 Xaero 会把它当作拖图起点）。 */
	public static boolean onClick(Screen screen, int mouseX, int mouseY, boolean leftButton) {
		return of(screen).summaryClick(leftButton, mouseX, mouseY);
	}

	/** 总览与右侧列表的滚轮：返回 true 表示已消费（否则地图会同时缩放）。 */
	public static boolean onScroll(Screen screen, double mouseX, double mouseY, double amount) {
		return of(screen).panelScroll(mouseX, mouseY, amount);
	}

	/**
	 * 热力图：挂在 {@code MapRenderHelper.renderDynamicHighlight} 上，借用其当前帧的矩阵、缓冲区
	 * 与相机坐标。GuiMap 对该方法有两处调用，第二处（选区高亮）只在有选区时出现；有选区时整体
	 * 跳过，避免重复叠色。
	 */
	public static void onOverlay(Screen screen, PoseStack matrixStack, VertexConsumer buffer,
			int flooredCameraX, int flooredCameraZ) {
		MapScreenHook hook = of(screen);
		if (hook.tileSelection() != null) {
			return;
		}
		MapDimension dimension = hook.currentDimension();
		if (dimension == null) {
			return;
		}
		MapOverlay.draw(matrixStack.last().pose(), buffer, flooredCameraX, flooredCameraZ,
				ChunkFocus.dimensionId(dimension));
	}

	/* ---------- ChunkFocus.Host ---------- */

	@Override
	public MapProcessor processor() {
		GuiMap map = map();
		return map == null ? null : map.getMapProcessor();
	}

	@Override
	public void detachCamera() {
		GuiMap map = map();
		if (map == null || !getStaticBoolean(ATTACHED_CAMERA)) {
			return;
		}
		setStaticBoolean(ATTACHED_CAMERA, false);
		map.shouldReinit = true;
	}

	@Override
	public void setCameraDestination(int[] destination) {
		GuiMap map = map();
		if (map == null || CAMERA_DESTINATION == null) {
			return;
		}
		try {
			CAMERA_DESTINATION.set(map, destination);
		} catch (IllegalAccessException ignored) {
		}
	}

	/* ---------- 按钮 ---------- */

	private void addButtons() {
		GuiMap map = map();
		if (map == null) {
			return;
		}
		// 左侧空置的一列：齿轮位于 (0,0)、30×30，Xaero 自身的按钮都在右边一列与底边。
		// 使用 Xaero 的 GuiTexturedButton：无底框、仅图标，悬停时图标上浮 1 px 并在其区域覆盖一层
		// 半透明白，无需自行绘制。按「隐藏界面」键时 Xaero 跳过整个控件绘制，按钮随之隐藏；提示由其
		// ScreenBase 扫描控件绘制，为 Supplier，悬停时每帧调用一次，故用 lambda。
		// GuiTexturedButton 构造器两代不同：1.21.1 及以前为 11 参，贴图边长由 Xaero 固定为 256
		// （内部经 GuiGraphics.blit 的 7 参重载绘制，该重载固定按 256×256 采样，传入 16×16 的贴图
		// 只会采到左上 1/16 区域、图标不可见）；1.21.3 起尾部多两个参数，边长由调用方给出。故贴图
		// 统一为 256×256（图标绘制在左上角：三个主按钮取 16×16，折叠钮取 10×10），两代采样同一区域。
		//? if >=1.21.3 {
		map.addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.translatable(MsptMapClient.scanButtonHint())), 256, 256));
		// 清屏：位于扫描按钮下一格，同宽同高。仅清除客户端持有的结果，服务端不知情。
		map.addButton(new GuiTexturedButton(SCAN_BUTTON_X, CLEAR_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.translatable("msptmap.button.clear")), 256, 256));
		// 设置：位于清屏按钮下一格。parent 为地图屏幕，关闭设置后返回地图。
		map.addButton(new GuiTexturedButton(SCAN_BUTTON_X, CONFIG_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/config.png"),
				button -> MsptMapClient.onConfigPress(map),
				new Tooltip(Component.translatable("msptmap.button.config")), 256, 256));
		//?} else {
		/*map.addButton(new GuiTexturedButton(SCAN_BUTTON_X, SCAN_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/scan.png"),
				button -> MsptMapClient.onButtonPress(),
				() -> new Tooltip(Component.translatable(MsptMapClient.scanButtonHint()))));
		// 清屏：位于扫描按钮下一格，同宽同高。仅清除客户端持有的结果，服务端不知情。
		map.addButton(new GuiTexturedButton(SCAN_BUTTON_X, CLEAR_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/close.png"),
				button -> MsptMapClient.onClearPress(),
				new Tooltip(Component.translatable("msptmap.button.clear"))));
		// 设置：位于清屏按钮下一格。parent 为地图屏幕，关闭设置后返回地图。
		map.addButton(new GuiTexturedButton(SCAN_BUTTON_X, CONFIG_BUTTON_Y,
				SCAN_BUTTON_SIZE, SCAN_BUTTON_SIZE, 0, 0, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/config.png"),
				button -> MsptMapClient.onConfigPress(map),
				new Tooltip(Component.translatable("msptmap.button.config"))));
		*///?}
		addFoldButtons(map);
	}

	/**
	 * 创建折叠钮的两个控件：两者位置相同（{@link #FOLD_BUTTON_X}），靠 {@link #syncFoldButtons()}
	 * 二选一可见，故从不重叠。
	 *
	 * <p>贴图边长的两个参数只在 1.21.3 起有，两种形态各写一遍（同上面三个按钮）。
	 */
	private void addFoldButtons(GuiMap map) {
		//? if >=1.21.3 {
		map.addButton(foldExpanded = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/collapse.png"),
				button -> toggleSummary(),
				() -> foldTooltip(), 256, 256));
		map.addButton(foldCollapsed = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/expand.png"),
				button -> toggleSummary(),
				() -> foldTooltip(), 256, 256));
		//?} else {
		/*map.addButton(foldExpanded = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/collapse.png"),
				button -> toggleSummary(),
				() -> foldTooltip()));
		map.addButton(foldCollapsed = new GuiTexturedButton(FOLD_BUTTON_X, FOLD_BUTTON_Y,
				FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE, 0, 0, FOLD_BUTTON_SIZE, FOLD_BUTTON_SIZE,
				Ids.of(MsptMapMod.MOD_ID, "textures/gui/expand.png"),
				button -> toggleSummary(),
				() -> foldTooltip()));
		*///?}
		syncFoldButtons();
	}

	/** 折叠钮的点击：翻转开关，并把两个控件的可见性对调。 */
	private void toggleSummary() {
		MsptMapClient.onSummaryToggle();
		syncFoldButtons();
	}

	/**
	 * 按当前开关选定折叠钮显示哪一个控件（另一个不可见：既不绘制，也不接收鼠标事件，见
	 * {@code isMouseOver}）；无扫描数据时两个都不可见——没有可开合的框，钮与提示一并藏起（见
	 * {@link ScanSummary#hasData}）。
	 */
	private void syncFoldButtons() {
		boolean hasData = ScanSummary.hasData();
		if (foldExpanded != null) {
			foldExpanded.visible = hasData && ClientConfig.summaryExpanded;
		}
		if (foldCollapsed != null) {
			foldCollapsed.visible = hasData && !ClientConfig.summaryExpanded;
		}
	}

	/**
	 * 折叠钮的悬停提示：描述下一次点击的行为；无扫描数据（钮整体隐藏）时返回 null。
	 *
	 * <p>两个控件共用同一个 Supplier：Xaero 扫描提示时只比对坐标矩形、不看 {@code visible}（见
	 * {@code xaero.lib.client.gui.ScreenBase#renderTooltips}），隐藏的控件同样会被命中，故文案须与
	 * 命中的是哪一个无关；返回 null 是该处唯一能让提示随钮一并消失的手段。
	 */
	private static Tooltip foldTooltip() {
		if (!ScanSummary.hasData()) {
			return null;
		}
		return new Tooltip(Component.translatable(ClientConfig.summaryExpanded
				? "msptmap.hint.summary_collapse" : "msptmap.hint.summary_expand"));
	}

	/* ---------- 绘制 ---------- */

	/**
	 * 扫描总览：画在 Xaero 控件之后、其区块提示之前——压住地图与按钮、又让按钮的悬停提示画在
	 * 自己上面（提示被挡住就看不见了）。收起或无数据时 {@link ScanSummary#draw} 跳过不画。
	 */
	//? if >=26.1 {
	private void drawSummary(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*private void drawSummary(GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		// 折叠钮的显隐随数据有无而变（扫描完成、清屏都发生在帧外），每帧同步一次
		syncFoldButtons();
		// 按「隐藏界面」键时随按钮一起隐藏（与悬停详情一致）；收起由折叠钮决定，与隐藏界面无关
		if (GuiMap.hiddenUI || !ClientConfig.summaryExpanded) {
			return;
		}
		// 鼠标坐标用于给悬停的可点击行绘制高亮底色（见 ScanSummary.draw 与 SourceListPanel.draw）
		ScanSummary.draw(graphics, SUMMARY_X, SUMMARY_Y, mouseX, mouseY);
		if (ScanSummary.sourcesPanelOpen()) {
			SourceListPanel.draw(graphics, sourcesPanelX(), sourcesPanelY(), mouseX, mouseY);
		}
	}

	/** 右侧完整列表的左缘：紧贴总览右缘（隔 {@link #SUMMARY_GAP}）。 */
	private static int sourcesPanelX() {
		return SUMMARY_X + ScanSummary.panelWidth() + SUMMARY_GAP;
	}

	/**
	 * 右侧完整列表的上沿：与总览的折叠行并排（见 {@link ScanSummary#foldRowTop}），而非与总览顶对齐
	 * ——列表正是那一行展开出来的。
	 */
	private static int sourcesPanelY() {
		return ScanSummary.foldRowTop(SUMMARY_Y);
	}

	/**
	 * 悬停详情：此处地图已绘制完毕、Xaero 的缩放平移也已结束，经 graphics 绘制的文字必在最上层，
	 * 坐标为普通屏幕坐标。不与热力图共用注入点：后者位于地图自身的矩阵内，在那里绘制的文字会随
	 * 地图缩放。
	 *
	 * <p>读字段而非局部变量：{@code mouseBlockPosX/Z} 本帧最后一次写入在方法很靠前处，至末尾时已
	 * 确定；Xaero 自身高亮区块用的就是同一个值。压在控件、总览框或右侧完整列表上时不画（见
	 * {@link ChunkTooltip#overWidget}、{@link ScanSummary#overPanel}、{@link SourceListPanel#overPanel}）。
	 */
	//? if >=26.1 {
	private void drawHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	//?} else {
	/*private void drawHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
	*///?}
		// 按 Xaero「隐藏界面」键时详情随控件一并隐藏（热力图不隐藏，其属于地图内容）
		if (GuiMap.hiddenUI) {
			return;
		}
		GuiMap map = map();
		MapProcessor processor = processor();
		if (map == null || processor == null || !processor.isMapWorldUsable()) {
			return;
		}
		// 鼠标位于控件上时不绘制（判据见 ChunkTooltip.overWidget）
		if (ChunkTooltip.overWidget(mouseX, mouseY, map.children())) {
			return;
		}
		// 鼠标落在总览框或右侧完整列表上时同样让位：详情绘制于其后，重叠处会盖住面板
		if (ScanSummary.overPanel(mouseX, mouseY, SUMMARY_X, SUMMARY_Y)
				|| SourceListPanel.overPanel(mouseX, mouseY, sourcesPanelX(), sourcesPanelY())) {
			return;
		}
		MapDimension dimension = processor.getMapWorld().getCurrentDimension();
		if (dimension == null) {
			return;
		}
		String dimId = ChunkFocus.dimensionId(dimension);
		// 该维度从未扫描则不显示：无热力图之处不应出现「未采样」
		if (ClientSnapshot.get(dimId) == null) {
			return;
		}
		int chunkX = mouseBlockPosX() >> 4;
		int chunkZ = mouseBlockPosZ() >> 4;
		ChunkTooltip.draw(graphics, mouseX, mouseY,
				ChunkTooltip.lines(ClientSnapshot.find(dimId, chunkX, chunkZ), chunkX, chunkZ,
						ClientSnapshot.windowTicks()));
	}

	/**
	 * 扫描进度圈：同样画在控件之后，坐标为普通屏幕坐标。画在扫描按钮自身的边框上（理由见
	 * {@link ScanRing}）。
	 *
	 * <p>不自行计算任何数值：进度全部来自服务端每 0.1 秒推送的包（见 {@link ScanProgress}），故
	 * 单人档地图开着（世界暂停、服务端发不出包）时进度圈不移动。
	 */
	//? if >=26.1 {
	private void drawScanRing(GuiGraphicsExtractor graphics) {
	//?} else {
	/*private void drawScanRing(GuiGraphics graphics) {
	*///?}
		// 按「隐藏界面」键时随按钮一起隐藏（与悬停详情一致）
		if (GuiMap.hiddenUI || !ScanProgress.active()) {
			return;
		}
		ScanRing.draw(graphics, ScanProgress.fraction(), SCAN_BUTTON_X, SCAN_BUTTON_Y, SCAN_BUTTON_SIZE);
	}

	/* ---------- 交互 ---------- */

	/**
	 * 点击的命中判定与定位：命中（左键、总览可见、正指着某可定位行）则把地图定位到该区块并返回
	 * true；折叠行只开合右侧列表。坐标由调用处从事件取（GUI 缩放坐标，勿用 Xaero 的 {@code Misc}）。
	 */
	private boolean summaryClick(boolean leftButton, int mouseX, int mouseY) {
		GuiMap map = map();
		if (!leftButton || map == null || GuiMap.hiddenUI || !ClientConfig.summaryExpanded) {
			return false;
		}
		MapProcessor processor = processor();
		if (processor == null || !processor.isMapWorldUsable()) {
			return false;
		}
		// 鼠标位于控件上时让位：与悬停详情的判据一致，无法绘制详情的位置也不应响应点击
		if (ChunkTooltip.overWidget(mouseX, mouseY, map.children())) {
			return false;
		}
		// 折叠行：只开合右侧完整列表，不定位
		if (ScanSummary.hitFold(mouseX, mouseY, SUMMARY_X, SUMMARY_Y)) {
			ScanSummary.toggleSourcesPanel();
			return true;
		}
		// 先右框后总览：两框位置不重叠，先后不影响结果
		ChunkRef target = SourceListPanel.hitTarget(mouseX, mouseY, sourcesPanelX(), sourcesPanelY());
		if (target == null) {
			target = ScanSummary.hitTarget(mouseX, mouseY, SUMMARY_X, SUMMARY_Y);
		}
		if (target == null) {
			return false;
		}
		focus.focus(target);
		return true;
	}

	/**
	 * 滚轮的判定：鼠标在右侧完整列表或总览框上则翻动并返回 true，都不在则返回 false（交回地图
	 * 缩放）。滚轮在 {@code Screen.mouseScrolled} 之前被 MouseHandler 的 mixin 截住——GuiMap 先
	 * 改动缩放、再调用父类，到父类时已无法阻止地图缩放。
	 */
	private boolean panelScroll(double mouseX, double mouseY, double amount) {
		if (GuiMap.hiddenUI || !ClientConfig.summaryExpanded) {
			return false;
		}
		int direction = (int) Math.signum(amount);
		if (ScanSummary.sourcesPanelOpen()
				&& SourceListPanel.scroll((int) mouseX, (int) mouseY, sourcesPanelX(), sourcesPanelY(), direction)) {
			return true;
		}
		return ScanSummary.scroll((int) mouseX, (int) mouseY, SUMMARY_X, SUMMARY_Y, direction);
	}

	/* ---------- 反射与实例 ---------- */

	private GuiMap map() {
		return mapRef.get();
	}

	private static MapScreenHook of(Screen screen) {
		return HOOKS.computeIfAbsent((GuiMap) screen, MapScreenHook::new);
	}

	/** 当前显示维度；地图世界不可用时为 null。 */
	private MapDimension currentDimension() {
		MapProcessor processor = processor();
		if (processor == null || processor.getMapWorld() == null) {
			return null;
		}
		return processor.getMapWorld().getCurrentDimension();
	}

	/** 导出/选区高亮对应的选择区域；无选区时为 null（GuiMap 的第二处高亮调用只在有选区时出现）。 */
	private Object tileSelection() {
		GuiMap map = map();
		if (map == null || MAP_TILE_SELECTION == null) {
			return null;
		}
		try {
			return MAP_TILE_SELECTION.get(map);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private int mouseBlockPosX() {
		return getInt(MOUSE_BLOCK_POS_X);
	}

	private int mouseBlockPosZ() {
		return getInt(MOUSE_BLOCK_POS_Z);
	}

	private int getInt(Field field) {
		GuiMap map = map();
		if (map == null || field == null) {
			return 0;
		}
		try {
			return field.getInt(map);
		} catch (IllegalAccessException e) {
			return 0;
		}
	}

	private static Field field(String name) {
		try {
			Field field = GuiMap.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	private static boolean getStaticBoolean(Field field) {
		if (field == null) {
			return false;
		}
		try {
			return field.getBoolean(null);
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	private static void setStaticBoolean(Field field, boolean value) {
		if (field == null) {
			return;
		}
		try {
			field.setBoolean(null, value);
		} catch (IllegalAccessException ignored) {
		}
	}
}
