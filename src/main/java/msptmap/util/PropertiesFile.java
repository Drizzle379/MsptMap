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
 * <p>读不懂的值一律退回调用方给的现值，读写失败只记一行日志：配置文件可手改，外部输入不信任，
 * 也不因设置问题中断游戏或服务器。
 */
public final class PropertiesFile {
	private PropertiesFile() {
	}

	/** 读一个文件；不存在或读失败返回 null（已记日志），调用方据此保持默认值。 */
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

	/** 写一个文件（上级目录不存在则建）；失败只记一行日志。 */
	public static void write(Path file, String text, String description) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			MsptMapMod.LOGGER.warn("{}写不进去：{}", description, file, e);
		}
	}

	/** 布尔值：只认 true / false 两个词，别的（含空值）退回 fallback。 */
	public static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		// Boolean.parseBoolean 会把任意文本当作 false，相当于静默改写设置
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		return fallback;
	}

	/** 整数：读不懂退回 fallback。 */
	public static int readInt(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** 小数：NaN 与 Infinity 也算读不懂——夹取对它们无效（NaN 夹取后仍是 NaN），用于比较会永远为假。 */
	public static double readDouble(Properties properties, String key, double fallback) {
		try {
			double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
			return Double.isFinite(value) ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** 夹回区间并保留指定小数位：存进文件的值要能一眼看懂。 */
	public static double clamp(double value, double min, double max, int decimals) {
		double scale = Math.pow(10, decimals);
		return Math.round(Clamp.of(value, min, max) * scale) / scale;
	}
}
