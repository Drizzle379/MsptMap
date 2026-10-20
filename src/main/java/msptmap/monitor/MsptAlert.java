package msptmap.monitor;

import msptmap.util.Decimals;
import msptmap.util.Dimensions;
import msptmap.util.HeatColors;
import msptmap.Permissions;
import msptmap.ServerConfig;
import msptmap.sampler.MsptSampler;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动扫描的聊天告警：一条标题加若干可点击的区块行。
 *
 * <p>区块行与扫描总览的卡顿区块行同构（同用 {@code msptmap.summary.line} 语言键）：二级条目的缩进与
 * 附注灰、{@code 耗时 @ 维度 x,z} 的排布，耗时按热力色的同一条斜坡着色，红点取单区块的默认红阈值。
 * 两处因此可直接对照。
 *
 * <p>文案一律 {@code translatableWithFallback}：接收范围设为「所有玩家」时，未装本模组的玩家没有
 * msptmap.* 的翻译，回退文案（英文）兜底，否则他们只会看到裸键名。行里嵌的维度名同理：装了的客户端
 * 走 {@link Dimensions#key} 的短名，未装的按 {@link Dimensions#fallbackName} 回退英文短名；原版语言
 * 文件无 {@code dimension.*} 键，仅用原版键会显示裸键名。
 *
 * <p>整行带 {@code runCommand} 点击事件与悬停提示（不加下划线：整行皆为可点区域，逐字下划线反而杂乱）：
 * 装了本模组的玩家点击后即由客户端打开世界地图并定位过去（受众可为非 OP）。
 */
public final class MsptAlert {
	/** 告警里列出的区块行数。 */
	public static final int TOP_ROWS = 5;

	/** 二级条目的缩进与附注灰，与总览的 {@code TickText.secondary} 同一口径。 */
	private static final String INDENT = "  ";
	private static final int NOTE_COLOR = 0xB0B0B0;

	private static final String TITLE_KEY = "msptmap.alert.title";
	private static final String TITLE_FALLBACK =
			"[MsptMap] Server MSPT stayed above %1$s mspt; scanned lagging chunks:";
	/** 行格式与总览的数据行共用同一个语言键，两处排版不会分家。 */
	private static final String ROW_KEY = "msptmap.summary.line";
	private static final String ROW_FALLBACK = "%1$s @ %2$s %3$s,%4$s";
	private static final String HOVER_KEY = "msptmap.alert.hover";
	private static final String HOVER_FALLBACK = "Click to locate this chunk on the world map";

	private MsptAlert() {
	}

	/**
	 * 本次自动扫描该把告警发给谁：默认只发在线 OP；接收范围设为 {@link ServerConfig.Audience#ALL}
	 * 时发给所有在线玩家（不限管理员）。
	 */
	public static List<ServerPlayer> targets(MinecraftServer server) {
		List<ServerPlayer> targets = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerConfig.audience == ServerConfig.Audience.OP
					&& !Permissions.isOperator(player.createCommandSourceStack())) {
				continue;
			}
			targets.add(player);
		}
		return targets;
	}

	/** 把告警发给每个目标：标题一条、每个区块一条。{@code windowTicks} 是本次窗口的实际刻数（mspt 的分母）。 */
	public static void send(List<ServerPlayer> targets, List<MsptSampler.Heavy> heaviest, int windowTicks) {
		List<Component> lines = new ArrayList<>(heaviest.size() + 1);
		lines.add(title());
		for (MsptSampler.Heavy heavy : heaviest) {
			lines.add(row(heavy, windowTicks));
		}
		for (ServerPlayer target : targets) {
			for (Component line : lines) {
				target.sendSystemMessage(line);
			}
		}
	}

	/** 标题行：加粗，与总览的标题同为「一眼可辨」的分区入口。 */
	private static Component title() {
		return Component.translatableWithFallback(TITLE_KEY, TITLE_FALLBACK,
				Decimals.format1(ServerConfig.threshold))
				.withStyle(style -> style.withBold(true));
	}

	/** 一条区块行：耗时 @ 维度 x,z；整行附注灰，耗时另着色，与总览的卡顿区块行一致。 */
	private static Component row(MsptSampler.Heavy heavy, int windowTicks) {
		// 维度名走本模组短名键（仅装了本模组的客户端显示中文短名）；未装的按原版短名回退，
		// 不能只给原版 dimension.* 键：官方语言文件中并无此键，只会显示裸键名
		MutableComponent text = Component.literal(INDENT)
				.append(Component.translatableWithFallback(ROW_KEY, ROW_FALLBACK,
						value(heavy.totalNanos(), windowTicks),
						Component.translatableWithFallback(Dimensions.key(heavy.dimension()),
								Dimensions.fallbackName(heavy.dimension())),
						heavy.chunkX(), heavy.chunkZ()))
				.withStyle(style -> style.withColor(TextColor.fromRgb(NOTE_COLOR)));
		// 维度 ID 含冒号，命令里走字符串参数，故加引号
		String command = "/msptmap locate \"" + heavy.dimension() + "\" "
				+ heavy.chunkX() + " " + heavy.chunkZ();
		return clickable(text, command);
	}

	/**
	 * 耗时文本：与悬停详情、总览同口径（{@link Decimals#format3}）；颜色与地图热力图同源，红点取单区块
	 * 的默认红阈值（与总览 TOP5 行一致）。告警在服务端拼装，读不到各玩家的客户端阈值，故用默认值。
	 */
	private static Component value(long nanos, int windowTicks) {
		float mspt = mspt(nanos, windowTicks);
		return Component.literal(Decimals.format3(mspt)).withStyle(style -> style.withColor(
				TextColor.fromRgb(HeatColors.soften(HeatColors.rgb(mspt, HeatColors.DEFAULT_RED_POINT)))));
	}

	/** 纳秒 → ms/tick，与 {@link Decimals#format3} 的字符串同口径。 */
	private static float mspt(long nanos, int windowTicks) {
		return (float) Decimals.mspt(nanos, windowTicks);
	}

	/**
	 * 给组件挂上点击与悬停提示。1.21.6 起 ClickEvent / HoverEvent 改为 record 实现，此前为「动作 + 值」
	 * 的构造器。
	 *
	 * <p>命令文本带前导斜杠：旧版由客户端剥离（{@code startsWith("/")} 不成立即报错），新版由
	 * {@code Commands.trimOptionalPrefix} 剥离，两端一致。
	 */
	private static Component clickable(MutableComponent text, String command) {
		Component hover = Component.translatableWithFallback(HOVER_KEY, HOVER_FALLBACK);
		//? if >=1.21.6 {
		return text.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
		//?} else {
		/*return text.withStyle(style -> style.withClickEvent(
						new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
		*///?}
	}
}
