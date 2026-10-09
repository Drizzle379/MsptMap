package msptmap;

import java.util.Locale;

/**
 * 小数显示的唯一出口：固定 {@link Locale#ROOT}，界面与日志共用同一套精度。
 *
 * <p>若不收口，默认 Locale（德语、法语等以逗号作小数点）与散落在悬停详情、总览、设置界面与
 * 控制台的精度口径都会各自漂移，改一处必漏其余。
 */
public final class Decimals {
	private Decimals() {
	}

	/** 一位小数（服务端监控的 mspt 阈值与均值）。 */
	public static String format1(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** 两位小数（设置界面的滑块读数与阈值）。 */
	public static String format2(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** 三位小数（mspt，界面与日志同此精度）。 */
	public static String format3(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}
}
