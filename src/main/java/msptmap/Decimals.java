package msptmap;

import java.util.Locale;

/**
 * 小数显示的唯一出口：固定 {@link Locale#ROOT}，界面与日志共用同一套精度。
 *
 * 不收口的话有两处会漂移：一是默认 Locale，在德语、法语等区域用逗号作小数点，「1.500」会变成
 * 「1,500」；二是精度，同一份口径散在悬停详情、设置界面、控制台打印三处，改一处必漏另两处。
 */
public final class Decimals {
	private Decimals() {
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
