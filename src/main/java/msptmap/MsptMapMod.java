package msptmap;

import msptmap.carpet.CarpetCompat;
import msptmap.command.MsptMapCommand;
import msptmap.net.ScanRequestPayload;
import msptmap.net.ScanResultPayload;
import msptmap.sampler.MsptSampler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主入口。服务端与客户端都会执行（fabric.mod.json 的 main 两侧都要跑）。
 *
 * 挂四件事：每个服务端 tick 结束时调用一次采样器（走 Fabric API 事件，省掉一个 mixin）、
 * 注册 /msptmap 命令、注册网络包并在服务端接收扫描请求、装了地毯时把权限交给地毯规则。
 */
public class MsptMapMod implements ModInitializer {
	public static final String MOD_ID = "msptmap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 协议版本（魔数，"MSP2" 的十六进制）。两个包都以它打头。
	 *
	 * 改动包的字节格式（字段增删、顺序调整、类别增删）时必须同时 +1 并改
	 * {@link #CHANNEL_SUFFIX}：包 ID 不同，两端协议不同的包根本不会互相送达；
	 * 魔数则是第二道闸，防的是包 ID 相同而格式不同（漏改后缀）的情况。
	 */
	public static final int PROTOCOL = 0x4D535032;

	/** 两个包 ID 共同的协议后缀，与 {@link #PROTOCOL} 同进同退。 */
	public static final String CHANNEL_SUFFIX = "_v2";

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> MsptSampler.onServerTick());
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> MsptMapCommand.register(dispatcher));

		// 仅装了地毯才加载 CarpetCompat：守卫不通过时 JVM 不会解析它引用的那些地毯类
		if (FabricLoader.getInstance().isModLoaded("carpet")) {
			CarpetCompat.register();
		}

		// 请求包 客户端 → 服务端，结果包 服务端 → 客户端。
		PayloadTypeRegistry.serverboundPlay().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScanResultPayload.TYPE, ScanResultPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ScanRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (payload.protocol() != PROTOCOL) {
				// 包名对得上而魔数对不上：对面是别版本的客户端。什么都不回（它可能读不懂本端的状态码）
				LOGGER.warn("玩家 {} 的 MsptMap 协议不一致（收到 {}，本端 {}），已忽略其请求",
						player.getGameProfile().name(), payload.protocol(), PROTOCOL);
				return;
			}
			// 权限闸门，同 MsptMapCommand
			if (!MsptMapSettings.canUse.test(player.createCommandSourceStack())) {
				ServerPlayNetworking.send(player, ScanResultPayload.denied());
				return;
			}
			// 非 0 为客户端指定的秒数，0 表示用服务端默认值
			int requested = payload.seconds() > 0 ? payload.seconds() : MsptMapSettings.seconds.getAsInt();
			int seconds = MsptSampler.clampSeconds(requested);

			if (MsptSampler.start(seconds, player)) {
				// 先回 START：秒数以服务端为准，客户端据此计算进度圈
				ServerPlayNetworking.send(player, ScanResultPayload.start(seconds));
			} else {
				ServerPlayNetworking.send(player, ScanResultPayload.busy());
			}
		});

		LOGGER.info("msptmap loaded");
	}
}
