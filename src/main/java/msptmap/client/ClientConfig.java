package msptmap.client;

import msptmap.util.Clamp;
import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import msptmap.util.PropertiesFile;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 客户端的可调值：扫描秒数、颜色阈值、悬停详情显示内容。
 *
 * <p>不并入 {@code msptmap.ServerConfig}：后者是本机作服务端时的运维设置，这里全是客户端
 * 偏好，存 config/msptmap-client.properties，两个文件互不覆盖。
 *
 * <p>值直接存静态字段：绘制与拼接悬停文字的热路径直接读取；设置界面改完立即生效，落盘交给
 * {@link #save()}。读盘与存盘共用 {@link #clamp()} 夹取区间：文件可手改，外部输入一律不信任。
 */
public final class ClientConfig {
	/** 秒数的合法区间（数字输入框与服务端的夹取是同一个口径）。 */
	public static final int MIN_SECONDS = 1;
	public static final int MAX_SECONDS = MsptSampler.MAX_SECONDS;

	/** 红阈值与透明度的合法区间；滑块与 {@link #clamp()} 都按它夹取。 */
	public static final double MIN_RED_AT = 0.05;
	public static final double MAX_RED_AT = 5.0;
	public static final double MIN_FILL_ALPHA = 0.05;

	/** 文件名。-client 后缀用于与服务端那份区分。 */
	private static final String FILE_NAME = "msptmap-client.properties";

	/** 每次请求的扫描秒数，由客户端决定。 */
	public static int scanSeconds;

	/** 红阈值：区块耗时到这里就是最高等级的红（ms/tick）。黄点由它推出，见 {@link MapOverlay}。 */
	public static double redAt;

	/**
	 * 颜色是否按相对大小判定（设置界面里的「采用相对模式」）。
	 *
	 * 开：红点 = 本次快照中最重的区块；关（默认）：红点 = {@link #redAt}，跨次可比。
	 */
	public static boolean relativeColor;

	/** 热力填色的透明度。 */
	public static double fillAlpha;

	/** 弱加载（加载等级 ≥32）且整段窗口无耗时的区块是否铺淡灰。 */
	public static boolean showWeakGray;

	/** 地图上那框扫描总览是否展开；由折叠钮翻转，跨次记忆（见 {@link ScanSummary}）。 */
	public static boolean summaryExpanded;

	/** 扫描总览的数值行是否按热力梯度着色：明细红点固定 20 mspt、合计 40 mspt、TOP5 跟随地图配色。 */
	public static boolean summaryColored;

	/** 悬停详情中坐标、等级与合计三行各自的显示开关；实体单列（见下）。 */
	public static boolean tooltipCoords;
	public static boolean tooltipLevels;
	public static boolean tooltipTotal;
	/** 「实体数 N」是否显示。与耗时无关，故不占明细那一组开关。 */
	public static boolean tooltipEntities;

	/**
	 * 「加载等级」「计算等级」两行各自是否附带加载票来源（形如「 · 玩家加载中心」「 · 玩家加载 @12,-34」）。
	 *
	 * 两者不是独立行，故不参与 {@link #anyTooltipLine()}：等级行不显示时它们自然也没了。
	 */
	public static boolean tooltipTicketLoad;
	public static boolean tooltipTicketSim;

	/** 各类名称是否显示为二字母缩写（详情与设置界面共用；两个语言下相同）。 */
	public static boolean tooltipAbbreviate;

	/** 各类明细行的 mspt 单位是否显示；合计行的单位固定显示，不受此开关影响。 */
	public static boolean tooltipMsptUnit;

	/** 各类明细各自的显示开关。下标 = {@link TickCategory#ordinal()}，顺序不可变更。 */
	public static final boolean[] tooltipCategories = new boolean[TickCategory.COUNT];

	static {
		// 默认值只在 resetToDefaults() 中写一次：首次启动与「恢复默认」共用同一份
		resetToDefaults();
	}

	private ClientConfig() {
	}

	/**
	 * 全部配置项。读盘、写盘与夹取都遍历这一份清单，加一项只改 {@link #options()}。
	 *
	 * <p>lambda 只捕获字段引用、不读值，故静态初始化的先后无碍。
	 */
	static final List<Option> OPTIONS = options();

	/** 配置项清单：键名与配置文件里的一一对应，顺序即写盘顺序。 */
	private static List<Option> options() {
		List<Option> options = new ArrayList<>();
		options.add(new IntValue("scan.seconds", MIN_SECONDS, MAX_SECONDS,
				() -> scanSeconds, value -> scanSeconds = value));
		options.add(new DecimalValue("color.redAt", MIN_RED_AT, MAX_RED_AT,
				() -> redAt, value -> redAt = value));
		options.add(new Flag("color.relative", () -> relativeColor, value -> relativeColor = value));
		options.add(new DecimalValue("color.fillAlpha", MIN_FILL_ALPHA, 1.0,
				() -> fillAlpha, value -> fillAlpha = value));
		options.add(new Flag("color.showWeakGray", () -> showWeakGray, value -> showWeakGray = value));
		options.add(new Flag("summary.expanded", () -> summaryExpanded, value -> summaryExpanded = value));
		options.add(new Flag("summary.colored", () -> summaryColored, value -> summaryColored = value));
		options.add(new Flag("tooltip.coords", () -> tooltipCoords, value -> tooltipCoords = value));
		options.add(new Flag("tooltip.levels", () -> tooltipLevels, value -> tooltipLevels = value));
		options.add(new Flag("tooltip.total", () -> tooltipTotal, value -> tooltipTotal = value));
		options.add(new Flag("tooltip.entities", () -> tooltipEntities, value -> tooltipEntities = value));
		options.add(new Flag("tooltip.ticketLoad", () -> tooltipTicketLoad, value -> tooltipTicketLoad = value));
		options.add(new Flag("tooltip.ticketSim", () -> tooltipTicketSim, value -> tooltipTicketSim = value));
		options.add(new Flag("tooltip.abbreviate", () -> tooltipAbbreviate, value -> tooltipAbbreviate = value));
		options.add(new Flag("tooltip.unit", () -> tooltipMsptUnit, value -> tooltipMsptUnit = value));
		for (TickCategory category : TickCategory.values()) {
			options.add(new Flag("tooltip.category." + category.name(),
					() -> tooltipCategories[category.ordinal()],
					value -> tooltipCategories[category.ordinal()] = value));
		}
		return options;
	}

	/** 恢复全部出厂默认（设置界面的「恢复默认」按钮同样走这里）。 */
	public static void resetToDefaults() {
		scanSeconds = 2;
		redAt = 1.5;
		relativeColor = false;
		fillAlpha = 0.35;
		showWeakGray = true;
		summaryExpanded = true;
		summaryColored = true;
		tooltipCoords = true;
		tooltipLevels = true;
		tooltipTotal = true;
		tooltipEntities = true;
		tooltipTicketLoad = true;
		tooltipTicketSim = true;
		tooltipAbbreviate = false;
		tooltipMsptUnit = false;
		Arrays.fill(tooltipCategories, true);
	}

	/** 从 config/msptmap-client.properties 读取；文件缺失或损坏则用默认值，绝不因设置崩游戏。 */
	public static void load() {
		load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** 写盘失败只记一行日志：设置存不下不应妨碍游戏。 */
	public static void save() {
		save(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** 实际读盘。带参数是为了离线测试能喂入临时文件。 */
	static void load(Path file) {
		// 先回默认值再覆盖：缺失的键用默认值，坏值也不会残留部分旧状态
		resetToDefaults();
		Properties properties = PropertiesFile.read(file, "设置");
		if (properties == null) {
			return;
		}
		for (Option option : OPTIONS) {
			option.read(properties);
		}
		clamp();
	}

	/** 实际写盘。手写这几行而不用 Properties.store：后者键序不稳定、还会写入时间戳注释。 */
	static void save(Path file) {
		clamp();
		StringBuilder text = new StringBuilder();
		text.append("# MsptMap client settings. Edit in game: Mod Menu -> MsptMap -> Settings, "
				+ "or the settings button on the world map.\n");
		text.append("# This file stores client-side preferences only; the server decides its own default "
				+ "seconds (a scan without seconds runs 2 seconds).\n");
		text.append("# Values out of range are clamped automatically; malformed values do not crash the game.\n\n");
		for (Option option : OPTIONS) {
			option.write(text);
		}
		PropertiesFile.write(file, text.toString(), "设置");
	}

	/** 悬停详情中该类的明细是否显示。 */
	public static boolean tooltipCategory(TickCategory category) {
		return tooltipCategories[category.ordinal()];
	}

	/** 悬停详情是否一行都不显示——全关时整个面板不画，不留空框。 */
	public static boolean anyTooltipLine() {
		if (tooltipCoords || tooltipLevels || tooltipTotal || tooltipEntities) {
			return true;
		}
		for (boolean on : tooltipCategories) {
			if (on) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 数字输入框中的文本 → 秒数。不是 {@link #MIN_SECONDS}~{@link #MAX_SECONDS} 的整数则返回 -1，
	 * 调用方据此不改动任何值。
	 *
	 * <p>与滑块换算一样置于此处而非设置界面：这是「界面文本 ↔ 配置数值」的规则，需能离线断言。
	 */
	public static int parseSeconds(String text) {
		int seconds;
		try {
			seconds = Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
		return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS ? seconds : -1;
	}

	/**
	 * 滑块位置（0~1）→ 区间内的值，保留两位小数。
	 *
	 * 两位小数是刻意的：文件中的数要能一眼看懂，也避开浮点尾巴。
	 */
	public static double fromSlider(double position, double min, double max) {
		return round2(min + position * (max - min));
	}

	/** 区间内的值 → 滑块位置（0~1）。 */
	public static double toSlider(double value, double min, double max) {
		return (value - min) / (max - min);
	}

	/** 保留两位小数。 */
	public static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	/** 各值夹回合法区间。 */
	private static void clamp() {
		for (Option option : OPTIONS) {
			option.clamp();
		}
	}

	/** 一项配置的描述符：键名、类型与区间，以及对该静态字段的读写。 */
	abstract static class Option {
		private final String key;

		Option(String key) {
			this.key = key;
		}

		/** 配置文件里的键。 */
		String key() {
			return key;
		}

		/** 从文件覆盖该字段；缺失或坏值保持现值。 */
		abstract void read(Properties properties);

		/** 追加 {@code key=value} 一行。 */
		abstract void write(StringBuilder text);

		/** 夹回合法区间。 */
		abstract void clamp();
	}

	/** 布尔项：只认 true / false 两个词。 */
	static final class Flag extends Option {
		final BooleanSupplier reader;
		final Consumer<Boolean> writer;

		Flag(String key, BooleanSupplier reader, Consumer<Boolean> writer) {
			super(key);
			this.reader = reader;
			this.writer = writer;
		}

		@Override
		void read(Properties properties) {
			writer.accept(PropertiesFile.readBoolean(properties, key(), reader.getAsBoolean()));
		}

		@Override
		void write(StringBuilder text) {
			text.append(key()).append('=').append(reader.getAsBoolean()).append('\n');
		}

		@Override
		void clamp() {
			// 布尔值没有越界一说
		}
	}

	/** 整数项：区间供 {@link Clamp} 夹取读盘值。 */
	static final class IntValue extends Option {
		final int min;
		final int max;
		final IntSupplier reader;
		final IntConsumer writer;

		IntValue(String key, int min, int max, IntSupplier reader, IntConsumer writer) {
			super(key);
			this.min = min;
			this.max = max;
			this.reader = reader;
			this.writer = writer;
		}

		@Override
		void read(Properties properties) {
			writer.accept(PropertiesFile.readInt(properties, key(), reader.getAsInt()));
		}

		@Override
		void write(StringBuilder text) {
			text.append(key()).append('=').append(reader.getAsInt()).append('\n');
		}

		@Override
		void clamp() {
			writer.accept(Clamp.of(reader.getAsInt(), min, max));
		}
	}

	/** 小数项：区间供 {@link Clamp} 夹取读盘值，值一律两位小数。 */
	static final class DecimalValue extends Option {
		final double min;
		final double max;
		final DoubleSupplier reader;
		final DoubleConsumer writer;

		DecimalValue(String key, double min, double max, DoubleSupplier reader, DoubleConsumer writer) {
			super(key);
			this.min = min;
			this.max = max;
			this.reader = reader;
			this.writer = writer;
		}

		@Override
		void read(Properties properties) {
			writer.accept(PropertiesFile.readDouble(properties, key(), reader.getAsDouble()));
		}

		@Override
		void write(StringBuilder text) {
			text.append(key()).append('=').append(reader.getAsDouble()).append('\n');
		}

		@Override
		void clamp() {
			writer.accept(PropertiesFile.clamp(reader.getAsDouble(), min, max, 2));
		}
	}
}
