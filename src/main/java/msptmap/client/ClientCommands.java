package msptmap.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import msptmap.util.Decimals;
import msptmap.util.Dimensions;
import msptmap.sampler.MsptSampler;
// 1.21.11 及以前叫 ClientCommandManager，26.1 起更名为 ClientCommands（同 MsptMapClient）
//? if >=26.1 {
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
//?} else {
/*import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
*///?}
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 客户端的 config、status 与 top 三条子命令。
 *
 * <p>设置项一律按名字读写 {@link ClientConfig}，名字与配置文件里的键一一对应（文件可手改，这份命令
 * 提供同一套键的在游戏内入口）；数值区间写进参数类型，越界输入在解析期就被拒。
 *
 * <p>回执走 {@code source.sendFeedback}：本地执行，输出只在本地聊天栏，不发往服务端。
 */
final class ClientCommands {
	/** {@code top} 不带参数时列出的行数（与扫描总览的 TOP5 一致）。 */
	private static final int DEFAULT_TOP = 5;
	/** {@code top} 最多列出的行数：再多不如直接看地图。 */
	private static final int MAX_TOP = 50;

	/** 回执文案：与服务端那条命令共用同一对键（客户端装了本模组，翻译必然有）。 */
	private static final String SET_KEY = "msptmap.set";
	private static final String SET_FALLBACK = "MsptMap: %1$s = %2$s";

	private static final String STATUS_SCANNING_KEY = "msptmap.status.scanning";
	private static final String STATUS_SCANNING_FALLBACK = "MsptMap: scanning the server...";
	private static final String STATUS_RESULT_KEY = "msptmap.status.result";
	private static final String STATUS_RESULT_FALLBACK =
			"MsptMap: %1$s s window, %2$s chunks, %3$s entities, total %4$s mspt";

	private static final String NO_RESULT_KEY = "msptmap.no_result";
	private static final String NO_RESULT_FALLBACK = "MsptMap: no scan result yet";

	private static final String TOP_TITLE_KEY = "msptmap.top.title";
	private static final String TOP_TITLE_FALLBACK = "MsptMap: heaviest chunks (top %1$s)";
	private static final String TOP_ROW_KEY = "msptmap.top.row";
	private static final String TOP_ROW_FALLBACK = "%1$d) %2$s - chunk (%3$d, %4$d) - %5$s mspt";

	private static final String RESET_KEY = "msptmap.config.reset_done";
	private static final String RESET_FALLBACK = "MsptMap: settings restored to defaults";

	private ClientCommands() {
	}

	/** {@code /msptmap config} 的整棵子树：无参打开设置界面，键名读写单项，reset 恢复默认。 */
	static LiteralArgumentBuilder<FabricClientCommandSource> config() {
		LiteralArgumentBuilder<FabricClientCommandSource> config = literal("config")
				// 设置界面的备用入口：未装 Mod Menu 时使用
				.executes(context -> openConfig())
				.then(literal("reset").executes(context -> reset(context.getSource())));
		for (LiteralArgumentBuilder<FabricClientCommandSource> key : keys()) {
			config.then(key);
		}
		return config;
	}

	/** {@code /msptmap status}：本次扫描的运行状态与最近一份结果。 */
	static LiteralArgumentBuilder<FabricClientCommandSource> status() {
		return literal("status").executes(context -> status(context.getSource()));
	}

	/** {@code /msptmap top [个数]}：本次扫描最重的若干区块。 */
	static LiteralArgumentBuilder<FabricClientCommandSource> top() {
		return literal("top")
				.executes(context -> top(context.getSource(), DEFAULT_TOP))
				.then(argument("count", IntegerArgumentType.integer(1, MAX_TOP))
						.executes(context -> top(context.getSource(),
								IntegerArgumentType.getInteger(context, "count"))));
	}

	/** 设置项分支：由 {@link ClientConfig} 的配置项清单生成，名字与配置文件里的键一一对应。 */
	private static List<LiteralArgumentBuilder<FabricClientCommandSource>> keys() {
		List<LiteralArgumentBuilder<FabricClientCommandSource>> keys = new ArrayList<>();
		for (ClientConfig.Option option : ClientConfig.OPTIONS) {
			keys.add(key(option));
		}
		return keys;
	}

	/** 按描述符的类型生成对应分支：区间写进参数类型，越界输入在解析期就被拒。 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> key(ClientConfig.Option option) {
		if (option instanceof ClientConfig.Flag flag) {
			return flag(flag.key(), flag.reader, flag.writer);
		}
		if (option instanceof ClientConfig.IntValue integer) {
			return intKey(integer.key(), integer.min, integer.max, integer.reader, integer.writer);
		}
		ClientConfig.DecimalValue decimal = (ClientConfig.DecimalValue) option;
		return doubleKey(decimal.key(), decimal.min, decimal.max, decimal.reader, decimal.writer);
	}

	/** 布尔设置项：不带参数显示现值，{@code on} / {@code off} 设置。 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> flag(String name, BooleanSupplier read,
			Consumer<Boolean> write) {
		return literal(name)
				.executes(context -> print(context.getSource(), name, onOff(read.getAsBoolean())))
				.then(literal("on").executes(context -> setFlag(context.getSource(), name, true, write)))
				.then(literal("off").executes(context -> setFlag(context.getSource(), name, false, write)));
	}

	/** 整数设置项：区间写进参数类型，越界输入在解析期被拒。 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> intKey(String name, int min, int max,
			IntSupplier read, IntConsumer write) {
		return literal(name)
				.executes(context -> print(context.getSource(), name, Integer.toString(read.getAsInt())))
				.then(argument("value", IntegerArgumentType.integer(min, max))
						.executes(context -> setInt(context.getSource(), name,
								IntegerArgumentType.getInteger(context, "value"), write)));
	}

	/** 小数设置项：读与写都按两位小数（同设置界面的滑块读数）。 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> doubleKey(String name, double min, double max,
			DoubleSupplier read, DoubleConsumer write) {
		return literal(name)
				.executes(context -> print(context.getSource(), name, Decimals.format2(read.getAsDouble())))
				.then(argument("value", DoubleArgumentType.doubleArg(min, max))
						.executes(context -> setDouble(context.getSource(), name,
								DoubleArgumentType.getDouble(context, "value"), write)));
	}

	private static int setFlag(FabricClientCommandSource source, String name, boolean value,
			Consumer<Boolean> write) {
		write.accept(value);
		return saved(source, name, onOff(value));
	}

	private static int setInt(FabricClientCommandSource source, String name, int value, IntConsumer write) {
		write.accept(value);
		return saved(source, name, Integer.toString(value));
	}

	private static int setDouble(FabricClientCommandSource source, String name, double value,
			DoubleConsumer write) {
		write.accept(value);
		return saved(source, name, Decimals.format2(value));
	}

	/** 显示一项当前值。 */
	private static int print(FabricClientCommandSource source, String name, String value) {
		source.sendFeedback(Component.translatableWithFallback(SET_KEY, SET_FALLBACK, name, value));
		return 1;
	}

	/** 改完即落盘，并把生效值报回聊天栏（{@link ClientConfig#save()} 会顺带夹取越界值）。 */
	private static int saved(FabricClientCommandSource source, String name, String value) {
		ClientConfig.save();
		source.sendFeedback(Component.translatableWithFallback(SET_KEY, SET_FALLBACK, name, value));
		return 1;
	}

	/** 恢复全部默认值并落盘（同设置界面的「恢复默认」按钮）。 */
	private static int reset(FabricClientCommandSource source) {
		ClientConfig.resetToDefaults();
		ClientConfig.save();
		source.sendFeedback(Component.translatableWithFallback(RESET_KEY, RESET_FALLBACK));
		return 1;
	}

	/** 设置界面的备用入口：未装 Mod Menu 时使用。parent 为 null：关闭后直接回游戏。 */
	private static int openConfig() {
		Screens.show(Minecraft.getInstance(), new MsptMapConfigScreen(null));
		return 1;
	}

	private static int status(FabricClientCommandSource source) {
		if (ScanProgress.active()) {
			source.sendFeedback(Component.translatableWithFallback(STATUS_SCANNING_KEY, STATUS_SCANNING_FALLBACK));
			return 1;
		}
		ClientSnapshot.Totals totals = ClientSnapshot.totals();
		if (totals == null) {
			source.sendFeedback(Component.translatableWithFallback(NO_RESULT_KEY, NO_RESULT_FALLBACK));
			return 1;
		}
		source.sendFeedback(Component.translatableWithFallback(STATUS_RESULT_KEY, STATUS_RESULT_FALLBACK,
				totals.windowTicks() / MsptSampler.TICKS_PER_SECOND,
				totals.chunks(), totals.entities(),
				Decimals.format2(totals.totalNanos() / 1_000_000.0 / totals.windowTicks())));
		return 1;
	}

	private static int top(FabricClientCommandSource source, int count) {
		List<ClientSnapshot.Top> heaviest = ClientSnapshot.heaviest(count);
		if (heaviest.isEmpty()) {
			source.sendError(Component.translatableWithFallback(NO_RESULT_KEY, NO_RESULT_FALLBACK));
			return 0;
		}
		source.sendFeedback(Component.translatableWithFallback(TOP_TITLE_KEY, TOP_TITLE_FALLBACK, heaviest.size()));
		for (int i = 0; i < heaviest.size(); i++) {
			ClientSnapshot.Top chunk = heaviest.get(i);
			source.sendFeedback(Component.translatableWithFallback(TOP_ROW_KEY, TOP_ROW_FALLBACK,
					i + 1,
					Component.translatable(Dimensions.key(chunk.dimension())),
					chunk.chunkX(), chunk.chunkZ(),
					Decimals.format2(chunk.mspt())));
		}
		return 1;
	}

	/** 布尔值的命令写法：与配置文件里的 true / false 区分开，聊天栏读起来更直观。 */
	private static String onOff(boolean value) {
		return value ? "on" : "off";
	}
}
