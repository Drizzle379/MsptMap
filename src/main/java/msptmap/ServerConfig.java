package msptmap;

import msptmap.util.Clamp;
import msptmap.util.PropertiesFile;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * 服务端的可调值：扫描权限，以及常态 MSPT 监控的开关、阈值、冷却与接收范围。
 *
 * <p>存于 config/msptmap-server.properties，与客户端配置（msptmap-client.properties）互不覆盖。
 *
 * <p>值直接存于静态字段：监控热路径逐 tick 读取，命令修改后立即生效，落盘交由 {@link #save()}。
 * 读盘与存盘共用 {@link #clamp()} 夹取区间；文件可手工修改，外部输入一律不信任。
 */
public final class ServerConfig {
	/** 触发阈值的合法区间（mspt）；一位小数。 */
	public static final double MIN_THRESHOLD = 1.0;
	public static final double MAX_THRESHOLD = 1000.0;

	/** 一次告警后的冷却时长（分钟）。 */
	public static final int MIN_COOLDOWN_MINUTES = 1;
	public static final int MAX_COOLDOWN_MINUTES = 1440;

	/** 文件名，与客户端配置区分。 */
	private static final String FILE_NAME = "msptmap-server.properties";

	/** 谁能发起扫描。 */
	public enum Access {
		/** 仅原版 OP（管理等级 2）。默认值。 */
		OPS,
		/** 所有玩家，由服主用 {@code /msptmap access all} 放开。 */
		ALL
	}

	/** 自动扫描结果的接收范围。 */
	public enum Audience {
		/** 仅发给在线 OP。默认值。 */
		OP,
		/** 所有在线玩家（不限管理员）；未装本模组的玩家只能收到英文回退文案。 */
		ALL
	}

	/** 扫描权限。 */
	public static Access access;

	/** 总开关。默认关闭，安装后需服主显式执行 {@code /msptmap monitor on}。 */
	public static boolean monitorEnabled;

	/** 触发阈值（mspt）：平滑均值高于该值即计一次超标。 */
	public static double threshold;

	/** 触发后的冷却（分钟）。 */
	public static int cooldownMinutes;

	/** 接收范围。 */
	public static Audience audience;

	static {
		// 默认值仅在 resetToDefaults() 中定义：首次启动与恢复出厂共用同一份
		resetToDefaults();
	}

	private ServerConfig() {
	}

	/** 恢复全部出厂默认值。 */
	public static void resetToDefaults() {
		access = Access.OPS;
		monitorEnabled = false;
		threshold = 40.0;
		cooldownMinutes = 5;
		audience = Audience.OP;
	}

	/** 从 config/msptmap-server.properties 读取；文件缺失或损坏时使用默认值，任何设置问题均不致使服务器崩溃。 */
	public static void load() {
		load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** 写盘失败仅记一行日志；设置无法保存不得妨碍服务器运行。 */
	public static void save() {
		save(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
	}

	/** 实际读盘；带参数以便离线测试传入临时文件。 */
	static void load(Path file) {
		// 先恢复默认值再覆盖：缺失的键沿用默认值，坏值也不会残留部分旧状态
		resetToDefaults();
		Properties properties = PropertiesFile.read(file, "服务端设置");
		if (properties == null) {
			return;
		}
		access = readAccess(properties, "access", access);
		monitorEnabled = PropertiesFile.readBoolean(properties, "monitor.enabled", monitorEnabled);
		threshold = PropertiesFile.readDouble(properties, "monitor.threshold", threshold);
		cooldownMinutes = PropertiesFile.readInt(properties, "monitor.cooldownMinutes", cooldownMinutes);
		audience = readAudience(properties, "monitor.audience", audience);
		clamp();
	}

	/** 实际写盘。此处手写而不用 {@code Properties.store}：后者键序不稳定，且会写入时间戳注释。 */
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
		text.append("monitor.cooldownMinutes=").append(cooldownMinutes).append('\n');
		text.append("monitor.audience=").append(audience.name().toLowerCase(Locale.ROOT)).append('\n');
		PropertiesFile.write(file, text.toString(), "服务端设置");
	}

	/** 将各值夹取到合法区间。 */
	private static void clamp() {
		threshold = PropertiesFile.clamp(threshold, MIN_THRESHOLD, MAX_THRESHOLD, 1);
		cooldownMinutes = Clamp.of(cooldownMinutes, MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES);
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
