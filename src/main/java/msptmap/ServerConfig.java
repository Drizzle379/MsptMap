package msptmap;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * 服务端的可调值：扫描权限，以及常态 MSPT 监控的开关、阈值、去抖、冷却与接收范围。
 *
 * <p>存 config/msptmap-server.properties，与客户端那份（msptmap-client.properties）互不覆盖。
 *
 * <p>值直接存静态字段：监控热路径每 tick 读取；命令改完立即生效，落盘交给 {@link #save()}。
 * 读盘与存盘共用 {@link #clamp()} 夹取区间：文件可手改，外部输入一律不信任。
 */
public final class ServerConfig {
	/** 触发阈值的合法区间（mspt）；一位小数。 */
	public static final double MIN_THRESHOLD = 1.0;
	public static final double MAX_THRESHOLD = 1000.0;

	/** 去抖次数：连续这么多次评估超标才算数，避免一两秒的抖动就告警。 */
	public static final int MIN_CONSECUTIVE = 1;
	public static final int MAX_CONSECUTIVE = 60;

	/** 一次告警后的冷却时长（分钟）。 */
	public static final int MIN_COOLDOWN_MINUTES = 1;
	public static final int MAX_COOLDOWN_MINUTES = 1440;

	/** 文件名。与客户端那份区分。 */
	private static final String FILE_NAME = "msptmap-server.properties";

	/** 谁能发起扫描。 */
	public enum Access {
		/** 仅原版 OP（管理等级 2）。默认。 */
		OPS,
		/** 所有玩家，由服主用 {@code /msptmap access all} 放开。 */
		ALL
	}

	/** 自动扫描结果的接收范围。 */
	public enum Audience {
		/** 只发给在线 OP：默认。 */
		OP,
		/** 所有在线玩家（不限管理员）——未装本模组的只能收到英文回退文案。 */
		ALL
	}

	/** 扫描权限。 */
	public static Access access;

	/** 总开关。默认关：装上后需服主显式 {@code /msptmap monitor on}。 */
	public static boolean monitorEnabled;

	/** 触发阈值（mspt）：平滑均值高于它即计一次超标。 */
	public static double threshold;

	/** 去抖次数。 */
	public static int consecutive;

	/** 触发后的冷却（分钟）。 */
	public static int cooldownMinutes;

	/** 接收范围。 */
	public static Audience audience;

	static {
		// 默认值只在 resetToDefaults() 中写一次：首次启动与「改坏了回默认」共用同一份
		resetToDefaults();
	}

	private ServerConfig() {
	}

	/** 恢复全部出厂默认。 */
	public static void resetToDefaults() {
		access = Access.OPS;
		monitorEnabled = false;
		threshold = 40.0;
		consecutive = 3;
		cooldownMinutes = 5;
		audience = Audience.OP;
	}

	/** 从 config/msptmap-server.properties 读取；文件缺失或损坏则用默认值，绝不因设置崩服务器。 */
	public static void load() {
		load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** 写盘失败只记一行日志：设置存不下不应妨碍服务器。 */
	public static void save() {
		save(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** 实际读盘。带参数是为了离线测试能喂入临时文件。 */
	static void load(Path file) {
		// 先回默认值再覆盖：缺失的键用默认值，坏值也不会残留部分旧状态
		resetToDefaults();
		if (!Files.exists(file)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("服务端设置读不出来，先用默认值：{}", file, e);
			return;
		}
		access = readAccess(properties, "access", access);
		monitorEnabled = readBoolean(properties, "monitor.enabled", monitorEnabled);
		threshold = readDouble(properties, "monitor.threshold", threshold);
		consecutive = readInt(properties, "monitor.consecutive", consecutive);
		cooldownMinutes = readInt(properties, "monitor.cooldownMinutes", cooldownMinutes);
		audience = readAudience(properties, "monitor.audience", audience);
		clamp();
	}

	/** 实际写盘。手写这几行而不用 Properties.store：后者键序不稳定、还会写入时间戳注释。 */
	static void save(Path file) {
		clamp();
		StringBuilder text = new StringBuilder();
		text.append("# MsptMap server settings. Edit in game: /msptmap <access|monitor> <name> <value>.\n");
		text.append("# access: who may start a scan, ops (default) or all.\n");
		text.append("# monitor.*: monitors server tick time and, when it stays above the threshold,\n");
		text.append("# runs one scan and alerts the audience with the heaviest chunks.\n");
		text.append("# monitor.audience: op (online operators only, default) or all (every player).\n");
		text.append("# Values out of range are clamped automatically; malformed values do not crash the server.\n\n");
		text.append("access=").append(access.name().toLowerCase(Locale.ROOT)).append('\n');
		text.append("monitor.enabled=").append(monitorEnabled).append('\n');
		text.append("monitor.threshold=").append(threshold).append('\n');
		text.append("monitor.consecutive=").append(consecutive).append('\n');
		text.append("monitor.cooldownMinutes=").append(cooldownMinutes).append('\n');
		text.append("monitor.audience=").append(audience.name().toLowerCase(Locale.ROOT)).append('\n');
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("服务端设置写不进去：{}", file, e);
		}
	}

	/** 各值夹回合法区间。 */
	private static void clamp() {
		threshold = clampRange(threshold, MIN_THRESHOLD, MAX_THRESHOLD);
		consecutive = Clamp.of(consecutive, MIN_CONSECUTIVE, MAX_CONSECUTIVE);
		cooldownMinutes = Clamp.of(cooldownMinutes, MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES);
	}

	/** 夹取并保留一位小数：阈值存进文件后要能一眼看懂。 */
	private static double clampRange(double value, double min, double max) {
		return Math.round(Clamp.of(value, min, max) * 10.0) / 10.0;
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		// 只认这两个词：Boolean.parseBoolean 会把任意文本当作 false，相当于静默改写设置
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
			// NaN 与 Infinity 能被 parse 出来，但夹取对其无效（NaN 夹取后仍为 NaN），比较会永远为假
			return Double.isFinite(value) ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static Access readAccess(Properties properties, String key, Access fallback) {
		String value = properties.getProperty(key);
		if ("ops".equalsIgnoreCase(value)) {
			return Access.OPS;
		}
		if ("all".equalsIgnoreCase(value)) {
			return Access.ALL;
		}
		return fallback;
	}

	private static Audience readAudience(Properties properties, String key, Audience fallback) {
		String value = properties.getProperty(key);
		if ("op".equalsIgnoreCase(value)) {
			return Audience.OP;
		}
		if ("all".equalsIgnoreCase(value)) {
			return Audience.ALL;
		}
		return fallback;
	}
}
