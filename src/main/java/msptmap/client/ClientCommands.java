package msptmap.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import msptmap.ServerConfig;
import msptmap.net.CommandRelayPayload;
// 1.21.11 及以前叫 ClientCommandManager，26.1 起更名为 ClientCommands（同 MsptMapClient）
//? if >=26.1 {
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
//?} else {
/*import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
*///?}
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * 服务端 access 与 monitor 两条子命令的客户端转发子树。
 *
 * <p>命令在客户端本地解析：解析得动就本地执行、不发往服务端，同一位置的服务端同名命令因此被遮蔽。
 * 这两棵按服务端同样的形状注册，叶子节点把命令转发上去执行（见 {@link CommandRelayPayload}）。
 */
final class ClientCommands {
	private ClientCommands() {
	}

	/**
	 * {@code /msptmap access ...}：服务端那条命令被本地同名的根遮蔽，故按同样的形状在这里再注册
	 * 一棵，叶子节点把命令转发上去（见 {@link CommandRelayPayload}）。
	 */
	static LiteralArgumentBuilder<FabricClientCommandSource> access() {
		return literal("access")
				.executes(context -> relay(context.getSource(), "access"))
				.then(literal("ops").executes(context -> relay(context.getSource(), "access ops")))
				.then(literal("all").executes(context -> relay(context.getSource(), "access all")));
	}

	/**
	 * {@code /msptmap monitor ...}：同上。数值区间与服务端同源（{@link ServerConfig} 的常量），
	 * 同一串输入在两端的判定一致。
	 */
	static LiteralArgumentBuilder<FabricClientCommandSource> monitor() {
		return literal("monitor")
				.executes(context -> relay(context.getSource(), "monitor"))
				.then(literal("on").executes(context -> relay(context.getSource(), "monitor on")))
				.then(literal("off").executes(context -> relay(context.getSource(), "monitor off")))
				.then(literal("threshold")
						.then(argument("mspt", DoubleArgumentType.doubleArg(
										ServerConfig.MIN_THRESHOLD, ServerConfig.MAX_THRESHOLD))
								.executes(context -> relay(context.getSource(),
										"monitor threshold " + DoubleArgumentType.getDouble(context, "mspt")))))
				.then(literal("cooldown")
						.then(argument("minutes", IntegerArgumentType.integer(
										ServerConfig.MIN_COOLDOWN_MINUTES, ServerConfig.MAX_COOLDOWN_MINUTES))
								.executes(context -> relay(context.getSource(),
										"monitor cooldown " + IntegerArgumentType.getInteger(context, "minutes")))))
				.then(literal("audience")
						.then(literal("op").executes(context -> relay(context.getSource(), "monitor audience op")))
						.then(literal("all")
								.executes(context -> relay(context.getSource(), "monitor audience all"))));
	}

	/**
	 * 把一条服务端命令转发上去执行。
	 *
	 * <p>权限交给服务端判（那里才有权威的 OP 等级），客户端不预筛：非 OP 收到的正是原版那句
	 * 「未知或不完整的命令」，与未装本模组的客户端一致。
	 */
	private static int relay(FabricClientCommandSource source, String command) {
		if (!ClientPlayNetworking.canSend(CommandRelayPayload.TYPE)) {
			// 服务端未装本模组时不认识这个包，发了会被踢下线（同 MsptMapClient.send）
			source.sendError(Component.translatable(MsptMapClient.NO_MOD_KEY));
			return 0;
		}
		ClientPlayNetworking.send(CommandRelayPayload.relay("msptmap " + command));
		return 1;
	}
}
