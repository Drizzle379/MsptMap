package msptmap.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import msptmap.MsptMapMod;
import msptmap.net.ScanRequestPayload;
import msptmap.net.ScanResultPayload;
import msptmap.net.SnapshotCodec;
import msptmap.sampler.MsptSampler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 客户端入口：结果包接收器、掉线清理、两条本地命令（scan / config）、地图按钮回调。
 *
 * 聊天栏只说三句状态话（{@link #say}：走客户端本地消息，不发往服务器），其余只写日志。
 *
 * 命令为本地执行：Fabric 在 ClientPacketListener.sendCommand 处拦截，命令能在客户端命令树上跑通就
 * 不发给服务端，故与服务端那条同名命令不冲突。
 */
public class MsptMapClient implements ClientModInitializer {
	/** 点击后包确实发出时在聊天栏显示（服务端是否答应随后另说）。 */
	static final String STARTING_MESSAGE = "分析中…";
	/** 服务端未装本模组：包发不出去。 */
	static final String NO_MOD_MESSAGE = "分析失败，服务端未安装 MsptMap";

	@Override
	public void onInitializeClient() {
		// 设置需在画第一帧前读入：地图上色与悬停详情读的就是这些静态字段
		ClientConfig.load();

		// 退出世界 / 掉线：丢弃上一局的结果与未画完的进度圈。快照按维度名存储（minecraft:overworld），
		// 不清则进入同一维度的另一世界仍显示旧颜色，扫描中的圈也会一直挂在按钮上。
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientSnapshot.clear();
			ScanProgress.stop();
		});

		ClientPlayNetworking.registerGlobalReceiver(ScanResultPayload.TYPE, (payload, context) -> {
			switch (payload.status()) {
				case START -> {
					MsptMapMod.LOGGER.info("收到 开始：服务端要扫 {} 秒", payload.seconds());
					// 圈的总刻数用服务端报的秒数（请求里的可能被夹取），进度取决于后续推送的包
					ScanProgress.start(payload.seconds());
				}
				// 每 0.1 秒一个，仅用于画圈，不写日志
				case PROGRESS -> ScanProgress.update(payload.windowTicks());
				case DONE -> {
					ScanProgress.stop();
					ClientSnapshot.accept(payload.windowTicks(), payload.dimensions());
					MsptMapMod.LOGGER.info("收到 完成：窗口 {} tick、{} 个维度",
							payload.windowTicks(), payload.dimensions().size());
					for (SnapshotCodec.DimensionData dimension : payload.dimensions()) {
						MsptMapMod.LOGGER.info("收到 {} 个区块、维度 {}",
								dimension.chunks().size(), dimension.dimension());
					}
				}
				case DENIED -> {
					ScanProgress.stop();
					MsptMapMod.LOGGER.info("收到 拒绝：服务端没给权限");
				}
				case BUSY -> {
					ScanProgress.stop();
					MsptMapMod.LOGGER.info("收到 忙碌：服务端正在扫另一次");
				}
			}
			// 收尾的三种状态在聊天栏说一句，START 与 PROGRESS 不说话
			say(statusMessage(payload.status()));
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("msptmap")
						.then(ClientCommands.literal("scan")
								.executes(context -> requestScan(context.getSource(), ClientConfig.scanSeconds))
								.then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, MsptSampler.MAX_SECONDS))
										.executes(context -> requestScan(context.getSource(),
												IntegerArgumentType.getInteger(context, "seconds")))))
						// 设置界面的备用入口：未装 Mod Menu 时使用
						.then(ClientCommands.literal("config").executes(context -> openConfig()))));
	}

	/** 在聊天栏说一句。走客户端本地消息，仅自己可见，不发往服务器。传 null 则不说。 */
	private static void say(String text) {
		if (text != null) {
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(text));
		}
	}

	/**
	 * 某种状态对应的那句话；null = 不说（START / PROGRESS 不说话，「分析中…」在点按钮时说）。
	 *
	 * 纯函数，便于离线断言各说各的。
	 */
	static String statusMessage(ScanResultPayload.Status status) {
		return switch (status) {
			case DONE -> "分析成功，请打开地图查看";
			case DENIED -> "分析失败，权限不足";
			case BUSY -> "分析失败，分析器被其他玩家占用中";
			case START, PROGRESS -> null;
		};
	}

	/** 地图上扫描按钮的入口。包发不出去（服务端未装本模组）时在聊天栏说明原因。 */
	public static void onButtonPress() {
		if (!send(ClientConfig.scanSeconds)) {
			MsptMapMod.LOGGER.warn("服务端没有装 MsptMap，扫不了。");
			say(NO_MOD_MESSAGE);
			return;
		}
		// 包已发出，先说「分析中…」；服务端是否答应（拒绝 / 被占用）随后另说
		say(STARTING_MESSAGE);
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
	 * 提示措辞：单人档与服务器上要做的事不同（单人档地图开着时世界暂停）。
	 *
	 * 纯函数，便于离线断言两句各说各的。
	 */
	static String scanButtonHint(boolean worldPauses) {
		return worldPauses
				? "绘制Mspt地图（单人档要关闭地图等待一段时间，否则无法生成）"
				: "绘制Mspt地图（向服务端请求）";
	}

	/**
	 * 地图开着时该世界是否暂停 = 此刻按扫描按钮是否会一直无反应。暂停期间服务端不走 tick，采样窗口
	 * 一刻也数不动，必须关掉地图才开始。
	 *
	 * 原式 {@code hasSingleplayerServer() && gui.isPausing() && !isPublished()} 里省掉了
	 * {@code gui.isPausing()}：本就在地图界面内，它必然为真。开启局域网（isPublished）则不暂停。
	 */
	private static boolean worldPausesWithMapOpen() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
	}

	/**
	 * 地图上的 ✕ 按钮：丢弃当前结果，地图立即恢复干净状态（纯客户端操作，不发包）。
	 *
	 * 只写日志：聊天栏只留状态话，按了没反应（本来就是空的）也能在日志里查出来。
	 */
	public static void onClearPress() {
		MsptMapMod.LOGGER.info("清屏：丢掉 {} 个维度的热力图", ClientSnapshot.clear());
	}

	/** 未装 Mod Menu 时打开设置界面的命令。parent 为 null：关闭后直接回游戏。只开界面，不往聊天栏发东西。 */
	private static int openConfig() {
		Minecraft.getInstance().setScreenAndShow(new MsptMapConfigScreen(null));
		return 1;
	}

	/** 命令那条路：发不出去由命令自行报错（命令反馈不算刷屏），发得出去则与按钮一致。 */
	private static int requestScan(FabricClientCommandSource source, int seconds) {
		if (!send(seconds)) {
			source.sendError(Component.literal(NO_MOD_MESSAGE));
			return 0;
		}
		say(STARTING_MESSAGE);
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
		ClientPlayNetworking.send(new ScanRequestPayload(seconds));
		return true;
	}
}
