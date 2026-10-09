package msptmap.server;

import msptmap.MsptMapMod;
import msptmap.net.CommandRelayPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 转发命令的处理：校魔数、卡前缀，再把命令以发起者的身份交给服务端命令树。
 *
 * <p>权限不在这里判：{@code performPrefixedCommand} 用的是玩家自己的来源，access 与 monitor 各自的
 * requires 照常生效，无权限者拿到的就是原版那句「未知或不完整的命令」。回执也不必管——命令自己
 * {@code sendSuccess} 给发起者，直接落在他的聊天栏里。
 *
 * <p>独立于模组入口（同 {@link ScanRequestHandler}）：入口只挂注册。
 */
public final class CommandRelayHandler {
	/** 允许转发的前缀：只放行本模组自己的服务端命令，这条通道不当任意命令的旁路。 */
	private static final String PREFIX = "msptmap ";

	private CommandRelayHandler() {
	}

	public static void handle(CommandRelayPayload payload, ServerPlayer player) {
		if (payload.protocol() == CommandRelayPayload.MISMATCH) {
			MsptMapMod.LOGGER.warn("玩家 {} 的 MsptMap 转发包解析不了，已忽略", playerName(player));
			return;
		}
		String command = payload.command();
		if (!command.startsWith(PREFIX)) {
			// 客户端只会拼自己的子命令；走到这里说明对面在手工构造包
			MsptMapMod.LOGGER.warn("玩家 {} 转发的命令不在允许范围内，已忽略：{}", playerName(player), command);
			return;
		}
		if (payload.protocol() != MsptMapMod.PROTOCOL) {
			// 对面版本不同：仅记日志，照常执行（同 ScanRequestHandler）
			MsptMapMod.LOGGER.warn("玩家 {} 的 MsptMap 版本与本端不一致（对面 {}，本端 {}），仍按其请求执行",
					playerName(player), payload.protocol(), MsptMapMod.PROTOCOL);
		}
		// ServerPlayer 自身没有公开的取 server 的口子（字段是 private，Entity 也没有 getServer），
		// 经 ServerLevel 拿；这里必然非空
		MinecraftServer server = player.level().getServer();
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	/** 玩家名（供日志）。同 {@link ScanRequestHandler}：{@code getScoreboardName()} 全版本稳定。 */
	private static String playerName(ServerPlayer player) {
		return player.getScoreboardName();
	}
}
