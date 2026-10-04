package msptmap;

/**
 * 数值夹取。{@code Math.clamp} 仅 Java 21 起可用，而本模组的 1.20–1.20.4 以 Java 17 为目标，
 * 故统一由本类提供，调用点无需按版本分叉。
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
