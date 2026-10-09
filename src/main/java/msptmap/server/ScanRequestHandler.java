package msptmap.server;

import msptmap.MsptMapMod;
import msptmap.Permissions;
import msptmap.net.ScanRequestPayload;
import msptmap.net.ScanResultPayload;
import msptmap.sampler.MsptSampler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 扫描请求的处理：校魔数、查权限、开窗口。结果由结果包回发，不在这里。
 *
 * <p>独立于模组入口（{@link MsptMapMod}）：入口只挂注册，收发两端各一处处理逻辑。
 */
public final class ScanRequestHandler {
	private ScanRequestHandler() {
	}

	public static void handle(ScanRequestPayload payload, ServerPlayer player) {
		if (payload.protocol() == ScanRequestPayload.MISMATCH) {
			// 包体读不出来：无从知道对面要什么，故不回复
			MsptMapMod.LOGGER.warn("玩家 {} 的 MsptMap 请求包解析不了，已忽略", playerName(player));
			return;
		}
		if (payload.protocol() != MsptMapMod.PROTOCOL) {
			// 对面版本不同：仅记日志，照常执行。请求包字段各版本一致，结果包格式由对面自行判定
			MsptMapMod.LOGGER.warn("玩家 {} 的 MsptMap 版本与本端不一致（对面 {}，本端 {}），仍按其请求执行",
					playerName(player), payload.protocol(), MsptMapMod.PROTOCOL);
		}
		// 权限闸门，同 MsptMapCommand
		if (!Permissions.canUse(player.createCommandSourceStack())) {
			ServerPlayNetworking.send(player, ScanResultPayload.denied());
			return;
		}
		// 非 0 为客户端指定的秒数，0 表示用服务端默认值
		int requested = payload.seconds() > 0 ? payload.seconds() : MsptSampler.DEFAULT_SECONDS;
		int seconds = MsptSampler.clampSeconds(requested);

		switch (MsptSampler.start(seconds, player)) {
			// 先回 START：秒数以服务端为准，客户端据此计算进度圈
			case STARTED -> ServerPlayNetworking.send(player, ScanResultPayload.start(seconds));
			case BUSY -> ServerPlayNetworking.send(player, ScanResultPayload.busy());
			case COOLDOWN -> {
				MsptMapMod.LOGGER.info("玩家 {} 的扫描请求距上次结束不足 {} 秒，已忽略", playerName(player),
						MsptSampler.COOLDOWN_SECONDS);
				ServerPlayNetworking.send(player, ScanResultPayload.cooldown());
			}
			// 停滞判定只拒绝无发起人的请求，玩家请求到不了这里（见 MsptSampler.start）；兜底按忙碌回
			case STALLED -> ServerPlayNetworking.send(player, ScanResultPayload.busy());
		}
	}

	/**
	 * 玩家名（供日志）。用 {@code getScoreboardName()} 而非 GameProfile：后者在 1.21 系列内两度更名
	 * （getName/name），前者全版本稳定，语义也更准。
	 */
	private static String playerName(ServerPlayer player) {
		return player.getScoreboardName();
	}
}
