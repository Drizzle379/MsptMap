package msptmap.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import msptmap.Decimals;
import msptmap.MsptMapMod;
import msptmap.MsptMapSettings;
import msptmap.ServerConfig;
import msptmap.monitor.MsptMonitor;
import msptmap.sampler.MsptSampler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 服务端的 {@code /msptmap} 命令：scan 与 monitor 两条子命令。
 *
 * <p>{@code scan} 不向来源回话，结果打到服务端控制台；{@code monitor} 设置常态 MSPT 监控，改完
 * 立即生效并落盘，回执报给来源。玩家看地图热力图用的是客户端那条同名的 {@code /msptmap scan}
 * （本地执行，走不到这里）。
 *
 * <p>权限经 Brigadier 的 requires 判定：装了地毯按 commandMsptMap 规则，未装则所有人可用；
 * monitor 子树另加一层原版 OP 等级判定。
 */
public final class MsptMapCommand {
	/** 状态行 / 参数行 / 冷却行 / 设置回执的语言键与英文回退（未装本模组的 OP 靠回退文案）。 */
	private static final String STATUS_KEY = "msptmap.monitor.status";
	private static final String STATUS_FALLBACK = "MsptMap monitor: %1$s (average %2$s mspt)";
	private static final String SETTINGS_KEY = "msptmap.monitor.settings";
	private static final String SETTINGS_FALLBACK =
			"threshold %1$s mspt; window %2$s s; consecutive %3$s; cooldown %4$s min; scan %5$s s; audience %6$s";
	private static final String COOLDOWN_KEY = "msptmap.monitor.cooldown_left";
	private static final String COOLDOWN_FALLBACK = "cooldown: %1$s s left";
	private static final String SET_KEY = "msptmap.monitor.set";
	private static final String SET_FALLBACK = "MsptMap monitor: %1$s = %2$s";

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
										IntegerArgumentType.getInteger(context, "seconds")))))
				.then(monitor()));
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

	/**
	 * 常态 MSPT 监控的子树：开关、阈值、去抖、冷却、扫描时长、接收范围。
	 *
	 * <p>数值区间写进参数类型：越界的输入在解析期就被拒，不必在回执里解释。
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> monitor() {
		return Commands.literal("monitor")
				// 服主的管理项：判原版 OP 等级（见 MsptMapSettings.isOperator），不随地毯规则放宽
				.requires(source -> MsptMapSettings.isOperator.test(source))
				.executes(context -> monitorStatus(context.getSource()))
				.then(Commands.literal("on").executes(context -> setEnabled(context.getSource(), true)))
				.then(Commands.literal("off").executes(context -> setEnabled(context.getSource(), false)))
				.then(Commands.literal("threshold")
						.then(Commands.argument("mspt", DoubleArgumentType.doubleArg(
										ServerConfig.MIN_THRESHOLD, ServerConfig.MAX_THRESHOLD))
								.executes(context -> setThreshold(context.getSource(),
										DoubleArgumentType.getDouble(context, "mspt")))))
				.then(Commands.literal("window")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(
										ServerConfig.MIN_WINDOW_SECONDS, ServerConfig.MAX_WINDOW_SECONDS))
								.executes(context -> setWindow(context.getSource(),
										IntegerArgumentType.getInteger(context, "seconds")))))
				.then(Commands.literal("consecutive")
						.then(Commands.argument("times", IntegerArgumentType.integer(
										ServerConfig.MIN_CONSECUTIVE, ServerConfig.MAX_CONSECUTIVE))
								.executes(context -> setConsecutive(context.getSource(),
										IntegerArgumentType.getInteger(context, "times")))))
				.then(Commands.literal("cooldown")
						.then(Commands.argument("minutes", IntegerArgumentType.integer(
										ServerConfig.MIN_COOLDOWN_MINUTES, ServerConfig.MAX_COOLDOWN_MINUTES))
								.executes(context -> setCooldown(context.getSource(),
										IntegerArgumentType.getInteger(context, "minutes")))))
				.then(Commands.literal("scan")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(
										ServerConfig.MIN_SCAN_SECONDS, ServerConfig.MAX_SCAN_SECONDS))
								.executes(context -> setScanSeconds(context.getSource(),
										IntegerArgumentType.getInteger(context, "seconds")))))
				.then(Commands.literal("audience")
						.then(Commands.literal("modded").executes(context -> setAudience(context.getSource(),
								ServerConfig.Audience.MODDED)))
						.then(Commands.literal("all").executes(context -> setAudience(context.getSource(),
								ServerConfig.Audience.ALL))));
	}

	/** 打印当前设置与运行状态：状态（含均值）一行、各参数一行，冷却中另加一行剩余时长。 */
	private static int monitorStatus(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatableWithFallback(STATUS_KEY, STATUS_FALLBACK,
				stateName(MsptMonitor.state()), Decimals.format1(MsptMonitor.meanMspt())), false);
		source.sendSuccess(() -> Component.translatableWithFallback(SETTINGS_KEY, SETTINGS_FALLBACK,
				Decimals.format1(ServerConfig.threshold), ServerConfig.windowSeconds, ServerConfig.consecutive,
				ServerConfig.cooldownMinutes, ServerConfig.scanSeconds, audienceValue(ServerConfig.audience)), false);
		long cooldown = MsptMonitor.cooldownRemainingSeconds();
		if (cooldown > 0) {
			source.sendSuccess(() -> Component.translatableWithFallback(COOLDOWN_KEY, COOLDOWN_FALLBACK, cooldown),
					false);
		}
		return 1;
	}

	private static int setEnabled(CommandSourceStack source, boolean enabled) {
		ServerConfig.monitorEnabled = enabled;
		if (!enabled) {
			// 关掉即丢弃窗口与冷却：再开时从头数起，不带上一轮的均值
			MsptMonitor.reset();
		}
		return saved(source, "enabled", Boolean.toString(enabled));
	}

	private static int setThreshold(CommandSourceStack source, double mspt) {
		ServerConfig.threshold = mspt;
		return saved(source, "threshold", Decimals.format1(ServerConfig.threshold));
	}

	private static int setWindow(CommandSourceStack source, int seconds) {
		ServerConfig.windowSeconds = seconds;
		return saved(source, "window", Integer.toString(ServerConfig.windowSeconds));
	}

	private static int setConsecutive(CommandSourceStack source, int times) {
		ServerConfig.consecutive = times;
		return saved(source, "consecutive", Integer.toString(ServerConfig.consecutive));
	}

	private static int setCooldown(CommandSourceStack source, int minutes) {
		ServerConfig.cooldownMinutes = minutes;
		return saved(source, "cooldown", Integer.toString(ServerConfig.cooldownMinutes));
	}

	private static int setScanSeconds(CommandSourceStack source, int seconds) {
		ServerConfig.scanSeconds = seconds;
		return saved(source, "scan", Integer.toString(ServerConfig.scanSeconds));
	}

	private static int setAudience(CommandSourceStack source, ServerConfig.Audience audience) {
		ServerConfig.audience = audience;
		return saved(source, "audience", audienceValue(audience));
	}

	/** 改完即落盘，并把生效值报回来源（回执进服务器日志，便于事后追查是谁改的）。 */
	private static int saved(CommandSourceStack source, String name, String value) {
		ServerConfig.save();
		source.sendSuccess(() -> Component.translatableWithFallback(SET_KEY, SET_FALLBACK, name, value), true);
		return 1;
	}

	/** 状态名：键由枚举名拼出，各带英文回退（未装本模组的 OP 也读得懂）。 */
	private static Component stateName(MsptMonitor.State state) {
		String fallback = switch (state) {
			case OFF -> "off";
			case OK -> "normal";
			case WATCHING -> "watching";
			case COOLDOWN -> "cooldown";
		};
		return Component.translatableWithFallback(
				"msptmap.monitor.state." + state.name().toLowerCase(Locale.ROOT), fallback);
	}

	/** 接收范围在配置文件与命令里的字面量（与 {@link ServerConfig} 写盘用的一致）。 */
	private static String audienceValue(ServerConfig.Audience audience) {
		return audience.name().toLowerCase(Locale.ROOT);
	}
}
