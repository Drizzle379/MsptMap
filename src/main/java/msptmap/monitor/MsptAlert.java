package msptmap.monitor;

import msptmap.util.Decimals;
import msptmap.util.Dimensions;
import msptmap.Permissions;
import msptmap.ServerConfig;
import msptmap.sampler.MsptSampler;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动扫描的聊天告警：一条标题加若干可点击的区块行。
 *
 * <p>文案一律 {@code translatableWithFallback}：接收范围设为「所有玩家」时，未装本模组的玩家没有
 * msptmap.* 的翻译，回退文案（英文）兜底，否则他们只会看到裸键名。行里嵌的维度名同理走
 * {@link Dimensions#vanillaKey}（原版键人人都有），而不是本模组的短名键。
 *
 * <p>整行带 {@code runCommand} 点击事件：装了本模组的 OP 点了即由客户端打开世界地图并定位过去。
 */
public final class MsptAlert {
	/** 告警里列出的区块行数。 */
	public static final int TOP_ROWS = 5;

	private static final String TITLE_KEY = "msptmap.alert.title";
	private static final String TITLE_FALLBACK =
			"[MsptMap] Server MSPT stayed above %1$s mspt; scanned %2$s s - heaviest chunks:";
	private static final String ROW_KEY = "msptmap.alert.row";
	private static final String ROW_FALLBACK = "%1$d) %2$s - chunk (%3$d, %4$d) - %5$s mspt";
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
		lines.add(title(windowTicks));
		for (int i = 0; i < heaviest.size(); i++) {
			lines.add(row(i + 1, heaviest.get(i), windowTicks));
		}
		for (ServerPlayer target : targets) {
			for (Component line : lines) {
				target.sendSystemMessage(line);
			}
		}
	}

	private static Component title(int windowTicks) {
		return Component.translatableWithFallback(TITLE_KEY, TITLE_FALLBACK,
				Decimals.format1(ServerConfig.threshold), windowTicks / MsptSampler.TICKS_PER_SECOND);
	}

	/** 一条区块行：序号、维度、区块坐标、耗时，整行可点。 */
	private static Component row(int index, MsptSampler.Heavy heavy, int windowTicks) {
		MutableComponent text = Component.translatableWithFallback(ROW_KEY, ROW_FALLBACK,
				index,
				Component.translatable(Dimensions.vanillaKey(heavy.dimension())),
				heavy.chunkX(), heavy.chunkZ(),
				msptText(heavy.totalNanos(), windowTicks));
		// 维度 ID 含冒号，命令里走字符串参数，故加引号
		String command = "/msptmap locate \"" + heavy.dimension() + "\" "
				+ heavy.chunkX() + " " + heavy.chunkZ();
		return clickable(text, command);
	}

	/**
	 * 给组件挂上点击与悬停。1.21.6 起 ClickEvent / HoverEvent 改为 record 实现，此前是「动作 + 值」
	 * 的构造器。
	 *
	 * <p>命令文本带前导斜杠：旧版由客户端剥（{@code startsWith("/")} 不成立直接报错），新版由
	 * {@code Commands.trimOptionalPrefix} 剥，两端一致。
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

	/** 区块耗时 → 告警里的 mspt 文本，与悬停详情同口径。 */
	private static String msptText(long nanos, int windowTicks) {
		return Decimals.format2(nanos / 1_000_000.0 / Math.max(1, windowTicks));
	}
}
