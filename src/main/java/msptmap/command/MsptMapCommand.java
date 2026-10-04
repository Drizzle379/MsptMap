package msptmap.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import msptmap.MsptMapMod;
import msptmap.MsptMapSettings;
import msptmap.sampler.MsptSampler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * 服务端的 /msptmap 命令：只有 scan 一条子命令，不向来源回话，结果打到服务端控制台。
 * 玩家看地图热力图用的是客户端那条同名的 /msptmap scan（本地执行，走不到这里）。
 *
 * 权限用 Brigadier 的 requires：装了地毯按 commandMsptMap 规则判，没装地毯谁都能用。
 */
public final class MsptMapCommand {
	private MsptMapCommand() {
	}

	/** 挂到命令树上。 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("msptmap")
				// 无权限者看不到这条命令
				.requires(source -> MsptMapSettings.canUse.test(source))
				.then(Commands.literal("scan")
						// 不带秒数则用服务端默认值（与网络包的 seconds == 0 同源）
						.executes(context -> scan(context.getSource(), MsptMapSettings.seconds.getAsInt()))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, MsptSampler.MAX_SECONDS))
								.executes(context -> scan(context.getSource(),
										IntegerArgumentType.getInteger(context, "seconds"))))));
	}

	private static int scan(CommandSourceStack source, int seconds) {
		// requester 为 null：结果只打控制台，不发包
		switch (MsptSampler.start(seconds, null)) {
			case STARTED -> MsptMapMod.LOGGER.info("开始采样 {} 秒（请求来自 {}）", seconds, source.getTextName());
			case BUSY -> MsptMapMod.LOGGER.info("已经在采样了，这次请求忽略（请求来自 {}）", source.getTextName());
			case COOLDOWN -> MsptMapMod.LOGGER.info("距上次扫描结束不足 {} 秒，这次请求忽略（请求来自 {}）",
					MsptSampler.COOLDOWN_SECONDS, source.getTextName());
			case STALLED -> MsptMapMod.LOGGER.info("服务端当前没有在运行（空载暂停或长时间卡顿），"
					+ "采样窗口数不到刻，这次请求忽略（请求来自 {}）", source.getTextName());
		}
		return 1;
	}
}
