package msptmap.util;

import msptmap.MsptMapMod;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * .properties 配置文件的读写骨架：客户端与服务端两份配置共用的读盘、写盘与取值规则。
 *
 * <p>取值失败一律退回调用方给定的默认值，读写失败仅记一行日志。配置文件可手工修改，视为不可信
 * 输入，任何取值异常都不得中断游戏或服务器。
 */
public final class PropertiesFile {
	private PropertiesFile() {
	}

	/** 读取配置文件；文件不存在或读取失败时返回 null（已记日志），调用方据此沿用默认值。 */
	public static Properties read(Path file, String description) {
		if (!Files.exists(file)) {
			return null;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("{}读不出来，先用默认值：{}", description, file, e);
			return null;
		}
		return properties;
	}

	/** 写入配置文件，上级目录不存在时自动创建；失败仅记一行日志。 */
	public static void write(Path file, String text, String description) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("{}写不进去：{}", description, file, e);
		}
	}

	/** 读取布尔值：仅接受 true / false，其余文本（含空值）退回 fallback。 */
	public static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		// Boolean.parseBoolean 将任意文本视为 false，会静默改写设置
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		return fallback;
	}

	/** 读取整数：解析失败时退回 fallback。 */
	public static int readInt(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** 读取小数：NaN 与 Infinity 视为解析失败——夹取对二者无效（NaN 夹取后仍为 NaN），参与比较恒为假。 */
	public static double readDouble(Properties properties, String key, double fallback) {
		try {
			double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
			return Double.isFinite(value) ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** 夹取到区间内并保留指定小数位，使写入文件的值便于阅读。 */
	public static double clamp(double value, double min, double max, int decimals) {
		double scale = Math.pow(10, decimals);
		return Math.round(Clamp.of(value, min, max) * scale) / scale;
	}
}
