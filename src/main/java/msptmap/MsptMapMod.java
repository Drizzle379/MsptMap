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
	 * 本端能读的字节格式版本（魔数，"MSP3" 的十六进制）。两个包都以它打头。
	 *
	 * 改动包的字节格式（字段增删、顺序调整、类别增删）时 +1。两端都把它当**标记**而非闸门：
	 * 收到不同的值只提示版本可能不一致，仍照常尝试解析（见两个 Payload 的解码器），
	 * 真读不出来才判本次失败。包 ID 不带版本号，两端只要能互相送达就允许一试。
	 */
	public static final int PROTOCOL = 0x4D535033;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> MsptSampler.onServerTick());
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> MsptMapCommand.register(dispatcher));

		// 仅装了地毯才加载 CarpetCompat：守卫不通过时 JVM 不会解析它引用的那些地毯类。
		// 再包一层 LinkageError：地毯若移除了 CarpetCompat 依赖的类，这里降级即可，
		// 不能让整个服务端起不来（地毯已把 carpet.settings 标为 forRemoval）。
		if (FabricLoader.getInstance().isModLoaded("carpet")) {
			try {
				CarpetCompat.register();
			} catch (LinkageError e) {
				LOGGER.warn("地毯版本与本模组的地毯兼容层不匹配，权限规则未注册，MsptMap 对所有人开放", e);
			}
		}

		// 请求包 客户端 → 服务端，结果包 服务端 → 客户端。
		// 1.21.11 及以前叫 playC2S / playS2C，26.1 起更名为 serverboundPlay / clientboundPlay。
		//? if >=26.1 {
		PayloadTypeRegistry.serverboundPlay().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScanResultPayload.TYPE, ScanResultPayload.CODEC);
		//?} else {
		/*PayloadTypeRegistry.playC2S().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ScanResultPayload.TYPE, ScanResultPayload.CODEC);
		*///?}

		ServerPlayNetworking.registerGlobalReceiver(ScanRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (payload.protocol() == ScanRequestPayload.MISMATCH) {
				// 包体读不出来：无从知道对面要什么，只能不作声
				LOGGER.warn("玩家 {} 的 MsptMap 请求包解析不了，已忽略", playerName(player));
				return;
			}
			if (payload.protocol() != PROTOCOL) {
				// 对面版本不同：只记一笔，照常往下走。请求包的字段各版本一致，扫描本身跑得起来；
				// 结果包格式对不对由对面自己判（它的解码器会兜住不成形的包）
				LOGGER.warn("玩家 {} 的 MsptMap 版本与本端不一致（对面 {}，本端 {}），仍按其请求执行",
						playerName(player), payload.protocol(), PROTOCOL);
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

	/** 玩家名（供日志）。走 Scoreboard 名而非 GameProfile：后者在 1.21 系列内两度更名（getName/name），
	 * 而 getScoreboardName() 全版本稳定，语义也更准（纯玩家名，不含显示名装饰）。 */
	private static String playerName(ServerPlayer player) {
		return player.getScoreboardName();
	}
}
