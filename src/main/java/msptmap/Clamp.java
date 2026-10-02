package msptmap;

/**
 * 数值夹取。
 *
 * {@code Math.clamp} 是 Java 21 才有的 API，而本模组的 1.20 – 1.20.4 以 Java 17 为目标；
 * 统一走这里，调用点不必按版本分叉。
 */
public final class Clamp {
	private Clamp() {
	}

	public static int of(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public static float of(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public static double of(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
