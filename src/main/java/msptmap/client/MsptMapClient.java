package msptmap.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import msptmap.MsptMapMod;
import msptmap.net.ScanRequestPayload;
import msptmap.net.ScanResultPayload;
import msptmap.sampler.MsptSampler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
// 1.21.11 及以前叫 ClientCommandManager，26.1 起更名为 ClientCommands。literal / argument 两个
// 方法各自静态导入，调用点便无需按版本区分。
//? if >=26.1 {
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
//?} else {
/*import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
*///?}
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 客户端入口：结果包转发、掉线清理、本地命令（scan / config / status / top / locate）与地图按钮回调。
 *
 * <p>聊天栏只输出状态提示（见 {@link Chat}：参数为语言键，按客户端语言解析；走客户端本地消息，
 * 不发往服务端），其余只写日志。
 *
 * <p>命令为本地执行：Fabric 在 ClientPacketListener.sendCommand 处拦截，命令能在客户端命令树上
 * 跑通即不发往服务端，故与服务端那条同名命令不冲突。
 */
public class MsptMapClient implements ClientModInitializer {
	/** 点击后包确实发出时在聊天栏显示（服务端是否答应随后另说）。 */
	static final String STARTING_KEY = "msptmap.message.starting";
	/** 包发不出去：服务端未装本模组，或装的是协议不同的另一版本（Fabric 只告诉「对面不认识这个包 ID」）。 */
	static final String NO_MOD_KEY = "msptmap.message.no_mod";
	/** 上一次还没出结果（进度圈还在转）时又发起：命令用它报错，按钮路径直接忽略。 */
	static final String WAITING_KEY = "msptmap.message.waiting";
	/** 告警里的区块行点了要开地图，但客户端没装 Xaero 世界地图：无从定位。 */
	static final String NO_XAERO_KEY = "msptmap.message.no_xaero";

	@Override
	public void onInitializeClient() {
		// 设置需在画第一帧前读入：地图上色与悬停详情读的就是这些静态字段
		ClientConfig.load();

		// 退出游戏时补一次落盘：设置界面只在 onClose 里存，用窗口关闭按钮退出或崩溃时改动会丢
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientConfig.save());

		// 退出世界 / 掉线：丢弃上一局的结果与未画完的进度圈。快照按维度名存储（minecraft:overworld），
		// 不清则进入同一维度的另一世界仍显示旧颜色，扫描中的进度圈也会一直挂在按钮上。
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientSnapshot.clear();
			ScanProgress.stop();
			MapFocus.clear();
		});

		//? if >=1.20.5 {
		ClientPlayNetworking.registerGlobalReceiver(ScanResultPayload.TYPE,
				(payload, context) -> ScanResultHandler.handle(payload));
		//?} else {
		/*// 1.20.4 及以前：handler 为三参数（包、客户端、回包器）
		ClientPlayNetworking.registerGlobalReceiver(ScanResultPayload.TYPE,
				(payload, client, sender) -> ScanResultHandler.handle(payload));
		*///?}

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("msptmap")
						.then(literal("scan")
								.executes(context -> requestScan(context.getSource(), ClientConfig.scanSeconds))
								.then(argument("seconds", IntegerArgumentType.integer(1, MsptSampler.MAX_SECONDS))
										.executes(context -> requestScan(context.getSource(),
												IntegerArgumentType.getInteger(context, "seconds")))))
						// 设置：无参打开设置界面，键名读写单项，reset 恢复默认
						.then(ClientCommands.config())
						// 本次扫描的运行状态与最重区块
						.then(ClientCommands.status())
						.then(ClientCommands.top())
						// 聊天告警里点击区块行走这里（命令由服务端的组件携带，本地执行）
						.then(literal("locate")
								.then(argument("dimension", StringArgumentType.string())
										.then(argument("chunkX", IntegerArgumentType.integer())
												.then(argument("chunkZ", IntegerArgumentType.integer())
														.executes(context -> locate(context.getSource(),
																StringArgumentType.getString(context, "dimension"),
																IntegerArgumentType.getInteger(context, "chunkX"),
																IntegerArgumentType.getInteger(context, "chunkZ")))))))));
	}

	/** 地图上扫描按钮的入口。包发不出去（服务端未装本模组）时在聊天栏说明原因。 */
	public static void onButtonPress() {
		if (ScanProgress.active()) {
			// 上一次还没出结果：再发包只会被服务端回「被其他玩家占用」，而占用者其实是自己；
			// 圈还在转，玩家看得见状态，这里静默忽略即可
			MsptMapMod.LOGGER.info("上一次分析还没结束，本次点击忽略");
			return;
		}
		if (!send(ClientConfig.scanSeconds)) {
			MsptMapMod.LOGGER.warn("服务端没有装 MsptMap，扫不了。");
			Chat.say(NO_MOD_KEY);
			return;
		}
		// 包已发出，先说「分析中…」；服务端是否答应（拒绝 / 被占用）随后另说
		Chat.say(STARTING_KEY);
		if (worldPausesWithMapOpen()) {
			// 请求已发出，但世界正处于暂停（见 worldPausesWithMapOpen）：玩家观感是「点了没反应」，
			// 日志留一句以便排查
			MsptMapMod.LOGGER.info("单人档：地图开着时世界是暂停的，采样要等关掉地图才会开始。");
		}
	}

	/** 扫描按钮当前的悬停提示。悬停时每帧现算，故单人档与服务器之间切换无需重进地图。 */
	public static String scanButtonHint() {
		return scanButtonHint(worldPausesWithMapOpen());
	}

	/**
	 * 提示措辞的语言键：单人档与服务端上要做的事不同（单人档地图开着时世界暂停）。
	 *
	 * 纯函数，便于离线断言。
	 */
	static String scanButtonHint(boolean worldPauses) {
		return worldPauses ? "msptmap.hint.singleplayer" : "msptmap.hint.server";
	}

	/**
	 * 地图开着时该世界是否暂停，即此刻按扫描按钮是否会一直无反应。暂停期间服务端不走 tick，采样
	 * 窗口一刻也数不动，必须关掉地图才会开始。
	 *
	 * <p>原式 {@code hasSingleplayerServer() && gui.isPausing() && !isPublished()} 中省略了
	 * {@code gui.isPausing()}：本就在地图界面内，它必然为真。开启局域网（isPublished）则不暂停。
	 */
	private static boolean worldPausesWithMapOpen() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
	}

	/**
	 * 地图上的 ✕ 按钮：丢弃当前结果，地图立即恢复干净状态（纯客户端操作，不发包）。
	 *
	 * 地图上立即可见，聊天栏不必再说；按了没反应（本来就是空的）也能在日志里查到。
	 */
	public static void onClearPress() {
		MsptMapMod.LOGGER.info("清屏：丢掉 {} 个维度的热力图", ClientSnapshot.clear());
	}

	/**
	 * 地图上的折叠钮：展开 / 收起扫描总览。
	 *
	 * 立即落盘：这是玩家一次显式选择，不必等到退出游戏（见 {@link ClientConfig#save}）。只翻开关，
	 * 不写聊天栏——总览的显隐本身就是回执。
	 */
	public static void onSummaryToggle() {
		ClientConfig.summaryExpanded = !ClientConfig.summaryExpanded;
		ClientConfig.save();
	}

	/**
	 * 地图上的设置按钮：打开设置界面。parent 传地图屏幕，关闭设置后回地图，而非退到游戏。
	 *
	 * 只开界面：不在聊天栏发消息。
	 */
	public static void onConfigPress(Screen parent) {
		Screens.show(Minecraft.getInstance(), new MsptMapConfigScreen(parent));
	}

	/**
	 * 定位命令：记下目标并确保地图已打开，随后由地图的帧循环完成定位（见 {@link MapFocus}）。
	 *
	 * <p>参数由聊天告警的行拼出（维度 ID 为字符串参数，故命令里带引号）；客户端本地执行，不发往服务端。
	 */
	private static int locate(FabricClientCommandSource source, String dimension, int chunkX, int chunkZ) {
		if (!FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
			// 未装世界地图：没有可定位的画布。引用的类在此守卫之后才加载
			source.sendError(Component.translatable(NO_XAERO_KEY));
			return 0;
		}
		MapFocus.request(dimension, chunkX, chunkZ);
		XaeroMapOpen.openIfClosed();
		return 1;
	}

	/** 命令那条路：发不出去由命令自行报错（命令反馈不算刷屏），发得出去则与按钮一致。 */
	private static int requestScan(FabricClientCommandSource source, int seconds) {
		if (ScanProgress.active()) {
			// 同 onButtonPress：等结果的途中再发没有意义，这里是显式输入，说清楚而不是静默
			source.sendError(Component.translatable(WAITING_KEY));
			return 0;
		}
		if (!send(seconds)) {
			source.sendError(Component.translatable(NO_MOD_KEY));
			return 0;
		}
		Chat.say(STARTING_KEY);
		return 1;
	}

	/**
	 * 实际发送请求；false = 未发出。
	 *
	 * 先查 canSend：服务端未装本模组时不认识该包，发送会导致玩家被踢下线。
	 */
	private static boolean send(int seconds) {
		if (!ClientPlayNetworking.canSend(ScanRequestPayload.TYPE)) {
			return false;
		}
		ClientPlayNetworking.send(ScanRequestPayload.scan(seconds));
		return true;
	}
}
