package msptmap.client;

import msptmap.sampler.MsptSampler;

/**
 * 扫描进度：只记录服务端报过的数，客户端不自行计时。
 *
 * 服务端每 0.1 秒（2 刻）推一个进度包（{@code Status.PROGRESS}），此处原样记录，交给 {@link ScanRing}
 * 画成按钮四周那个顺时针转满的圈。不做本地倒计时：服务端卡顿时本地刻数会多于服务端（圈提前转满），
 * 且单人档地图一开世界整体暂停、客户端也无刻可数。
 */
public final class ScanProgress {
	/** 本次是否已发起且仍在等待结果。 */
	private static boolean active;
	/** 窗口总刻数（开始包中服务端夹取后的秒数 × 20）。 */
	private static int totalTicks;
	/** 服务端报的窗口已过刻数。 */
	private static int elapsedTicks;

	private ScanProgress() {
	}

	/**
	 * 收到 START：窗口总刻数 = 服务端报的秒数 × 20。用服务端那份秒数（请求里的数可能被它夹取），
	 * 否则圈的分母与真实窗口不符。
	 */
	public static void start(int seconds) {
		active = true;
		totalTicks = Math.max(1, seconds * MsptSampler.TICKS_PER_SECOND);
		elapsedTicks = 0;
	}

	/** 收到 PROGRESS：以服务端报的刻数为准。未在等待（未收到开始包）则忽略。 */
	public static void update(int windowTicks) {
		if (active) {
			elapsedTicks = windowTicks;
		}
	}

	/** 收到 DONE / DENIED / BUSY：本次等待结束，圈收掉。 */
	public static void stop() {
		active = false;
		totalTicks = 0;
		elapsedTicks = 0;
	}

	/** 是否仍在等待结果（= 圈是否该画）。 */
	public static boolean active() {
		return active;
	}

	/** 圈已转过的比例（0 ~ 1）。未在等待时为 0。 */
	public static float fraction() {
		if (!active) {
			return 0f;
		}
		return Math.clamp((float) elapsedTicks / totalTicks, 0f, 1f);
	}
}
