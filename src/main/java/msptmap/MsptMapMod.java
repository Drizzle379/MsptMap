package msptmap;

import msptmap.command.MsptMapCommand;
import msptmap.monitor.MsptMonitor;
import msptmap.net.ScanRequestPayload;
import msptmap.net.ScanResultPayload;
import msptmap.sampler.MsptSampler;
import msptmap.server.ScanRequestHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
// 1.20.5 起才有这个类；1.20.4 及以前的 FabricPacket 体系由 registerGlobalReceiver 隐式注册，不需要它
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主入口，服务端与客户端都会执行。
 *
 * <p>注册：服务端 tick 的起止（采样器与常态监控）、服务器起止时的读存与复位、{@code /msptmap}
 * 命令、网络包与扫描请求接收器。
 */
public class MsptMapMod implements ModInitializer {
	public static final String MOD_ID = "msptmap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 本端可读的字节格式版本（魔数，"MSP4" 的十六进制），两个包均以它打头。
	 *
	 * <p>改动包的字节格式（字段增删、顺序调整、类别增删）时递增。两端都将其视为标记而非闸门：
	 * 值不同仅提示版本可能不一致，仍照常解析，确实读不出来才判本次失败。
	 */
	public static final int PROTOCOL = 0x4D535034;

	@Override
	public void onInitialize() {
		// 监控与采样都要量整 tick 的耗时，故自己配一对 START / END 钩子：END 那头同时挂着采样器（保持
		// 无参签名，离线测试直接调它），这里才拿得到 MinecraftServer
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			MsptSampler.onTickStart();
			MsptMonitor.onTickStart();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MsptSampler.onServerTick();
			MsptMonitor.onTickEnd(server);
		});
		// 服务端配置在服务器起来后读一次（命令改完即存，见 MsptMapCommand 的 monitor 子树）
		ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerConfig.load());
		// 服务器停止：丢弃采样与监控状态。静态字段跨世界存活，不复位则重进后旧窗口继续数刻、新请求
		// 被误判为「忙」，监控也会带着上一个世界的冷却与均值
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MsptSampler.reset();
			MsptMonitor.reset();
		});
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> MsptMapCommand.register(dispatcher));

		// 请求包 客户端 → 服务端，结果包 服务端 → 客户端。包类型注册：1.20.5 起走
		// PayloadTypeRegistry（1.21.11 及以前为 playC2S/playS2C，26.1 起更名为
		// serverboundPlay/clientboundPlay）；1.20.4 及以前的 FabricPacket 体系无需此步。
		//? if >=26.1 {
		PayloadTypeRegistry.serverboundPlay().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScanResultPayload.TYPE, ScanResultPayload.CODEC);
		//?} else if >=1.20.5 {
		/*PayloadTypeRegistry.playC2S().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ScanResultPayload.TYPE, ScanResultPayload.CODEC);
		*///?}

		//? if >=1.20.5 {
		ServerPlayNetworking.registerGlobalReceiver(ScanRequestPayload.TYPE,
				(payload, context) -> ScanRequestHandler.handle(payload, context.player()));
		//?} else {
		/*// 1.20.4 及以前：handler 为三参数（包、玩家、回包器）
		ServerPlayNetworking.registerGlobalReceiver(ScanRequestPayload.TYPE,
				(payload, player, sender) -> ScanRequestHandler.handle(payload, player));
		*///?}

		LOGGER.info("msptmap loaded");
	}
}
