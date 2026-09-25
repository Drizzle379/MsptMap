package msptmap.client;

import msptmap.MsptMapMod;
import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**
 * 客户端的可调值：扫描秒数、颜色阈值、悬停详情显示什么。
 *
 * 不并入 {@link msptmap.MsptMapSettings}：那个是服务端门面（地毯规则改写它），这里全是客户端偏好，
 * 存 config/msptmap-client.properties，两个文件互不覆盖。
 *
 * 值直接存静态字段：绘制与拼悬停文字的热路径直接读；设置界面改完立即生效，落盘交给 {@link #save()}。
 * 读盘与存盘共用 {@link #clamp()} 夹取区间：文件可手改，外部输入一律不信任。
 */
public final class ClientConfig {
	/** 秒数的合法区间（数字输入框与服务端的夹取是同一个口径）。 */
	public static final int MIN_SECONDS = 1;
	public static final int MAX_SECONDS = MsptSampler.MAX_SECONDS;

	/** 红阈值与透明度的合法区间。滑块与 setProperty 都按它走。 */
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

	/** 悬停详情中四条单行信息各自的显示开关。 */
	public static boolean tooltipCoords;
	public static boolean tooltipLevels;
	public static boolean tooltipTotal;
	/** 「实体数 N」是否显示。与耗时无关，故不占明细那一组开关。 */
	public static boolean tooltipEntities;

	/** 各类明细各自的显示开关。下标 = {@link TickCategory#ordinal()}，顺序不可变更。 */
	public static final boolean[] tooltipCategories = new boolean[TickCategory.values().length];

	static {
		// 默认值只在 resetToDefaults() 中写一次：首次启动与「恢复默认」共用同一份
		resetToDefaults();
	}

	private ClientConfig() {
	}

	/** 恢复全部出厂默认（设置界面的「恢复默认」按钮同样走这里）。 */
	public static void resetToDefaults() {
		scanSeconds = 5;
		redAt = 1.5;
		relativeColor = false;
		fillAlpha = 0.35;
		showWeakGray = true;
		tooltipCoords = true;
		tooltipLevels = true;
		tooltipTotal = true;
		tooltipEntities = true;
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
		// 先回默认值再覆盖：缺失的键用默认值，坏值也不会残留半个旧状态
		resetToDefaults();
		if (!Files.exists(file)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("设置读不出来，先用默认值：{}", file, e);
			return;
		}
		scanSeconds = readInt(properties, "scan.seconds", scanSeconds);
		redAt = readDouble(properties, "color.redAt", redAt);
		relativeColor = readBoolean(properties, "color.relative", relativeColor);
		fillAlpha = readDouble(properties, "color.fillAlpha", fillAlpha);
		showWeakGray = readBoolean(properties, "color.showWeakGray", showWeakGray);
		tooltipCoords = readBoolean(properties, "tooltip.coords", tooltipCoords);
		tooltipLevels = readBoolean(properties, "tooltip.levels", tooltipLevels);
		tooltipTotal = readBoolean(properties, "tooltip.total", tooltipTotal);
		tooltipEntities = readBoolean(properties, "tooltip.entities", tooltipEntities);
		for (TickCategory category : TickCategory.values()) {
			tooltipCategories[category.ordinal()] =
					readBoolean(properties, "tooltip.category." + category.name(), tooltipCategories[category.ordinal()]);
		}
		clamp();
	}

	/** 实际写盘。手写这几行而不用 Properties.store：后者会把中文注释转义成 unicode 码点。 */
	static void save(Path file) {
		clamp();
		StringBuilder text = new StringBuilder();
		text.append("# MsptMap 客户端设置。游戏里改：模组菜单 → MsptMap → 设置（备用入口 /msptmap config）。\n");
		text.append("# 服务端的默认秒数不在这里，在 config/msptmap.properties。\n");
		text.append("# 值越界会被自动夹回来，手改坏了也不会崩游戏。\n\n");
		text.append("scan.seconds=").append(scanSeconds).append('\n');
		text.append("color.redAt=").append(redAt).append('\n');
		text.append("color.relative=").append(relativeColor).append('\n');
		text.append("color.fillAlpha=").append(fillAlpha).append('\n');
		text.append("color.showWeakGray=").append(showWeakGray).append('\n');
		text.append("tooltip.coords=").append(tooltipCoords).append('\n');
		text.append("tooltip.levels=").append(tooltipLevels).append('\n');
		text.append("tooltip.total=").append(tooltipTotal).append('\n');
		text.append("tooltip.entities=").append(tooltipEntities).append('\n');
		for (TickCategory category : TickCategory.values()) {
			text.append("tooltip.category.").append(category.name()).append('=')
					.append(tooltipCategories[category.ordinal()]).append('\n');
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("设置写不进去：{}", file, e);
		}
	}

	/** 悬停详情中该类的明细是否显示。 */
	public static boolean tooltipCategory(TickCategory category) {
		return tooltipCategories[category.ordinal()];
	}

	/** 悬停详情是否一行都不显示 —— 全关时整个面板不画，不留空框。 */
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
	 * 数字输入框中的文本 → 秒数。不是 {@link #MIN_SECONDS} ~ {@link #MAX_SECONDS} 的整数则返回 -1，
	 * 调用方据此不改动任何值。
	 *
	 * 与滑块换算一样置于此处而非设置界面：这是「界面文本 ↔ 配置数值」的规则，需能离线断言。
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
		scanSeconds = Math.clamp(scanSeconds, MIN_SECONDS, MAX_SECONDS);
		redAt = clampRange(redAt, MIN_RED_AT, MAX_RED_AT);
		fillAlpha = clampRange(fillAlpha, MIN_FILL_ALPHA, 1.0);
	}

	private static double clampRange(double value, double min, double max) {
		return round2(Math.clamp(value, min, max));
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		// 只认这两个词：Boolean.parseBoolean 会把任意文本当作 false，等于静默改写设置
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		return fallback;
	}

	private static int readInt(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double readDouble(Properties properties, String key, double fallback) {
		try {
			double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
			// NaN 与 Infinity 能被 parse 出来，但夹取对其无效（NaN 夹取后仍为 NaN），用于算颜色会出问题
			return Double.isFinite(value) ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
