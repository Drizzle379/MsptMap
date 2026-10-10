package msptmap.monitor;

import msptmap.ServerConfig;
import msptmap.sampler.MsptSampler;
import net.minecraft.server.MinecraftServer;

/**
 * 常态 MSPT 监控：每 tick 量一次服务端 tick 的实际执行耗时，平滑后持续超标即触发一次自动扫描。
 *
 * <p>与 {@link MsptSampler} 分开：那个只在扫描窗口内累计「区块归属」的耗时，窗口关着就什么都不测，
 * 拿不到整 tick 的耗时，无从判断服务器是否变卡。这里测的是 START/END 钩子之间的墙钟差，不含
 * tick 之间的等待，即真正的 mspt。
 *
 * <p>决策为纯逻辑（{@link #observe} 只接收一个纳秒数与一个时刻），仅在触发时才访问服务端对象，
 * 故去抖、平滑与冷却均可离线断言。
 */
public final class MsptMonitor {
	/** 当前状态，供 {@code /msptmap monitor} 显示。 */
	public enum State {
		/** 未启用。 */
		OFF,
		/** 已启用，均值未超标。 */
		OK,
		/** 已启用，正在累计超标次数（去抖中）。 */
		WATCHING,
		/** 已启用，距上次触发不足冷却时长。 */
		COOLDOWN
	}

	/** 评估间隔（刻）：每 1 秒判定一次，无需逐 tick 决策。 */
	private static final int EVALUATE_TICKS = MsptSampler.TICKS_PER_SECOND;

	/** 平滑窗口（秒）：固定值，不对外可调。 */
	static final int WINDOW_SECONDS = 5;

	/** 自动扫描的时长（秒）：固定值，不对外可调。 */
	static final int AUTO_SCAN_SECONDS = 5;

	/** 去抖次数：连续该次数的评估超标才有效；固定值，不对外可调。 */
	static final int CONSECUTIVE_CHECKS = 5;

	/** 环形缓冲容量 = 窗口刻数：窗口固定，一次配足后不再分配。 */
	private static final int CAPACITY = WINDOW_SECONDS * MsptSampler.TICKS_PER_SECOND;

	/** 最近若干 tick 的耗时（纳秒）。满后覆盖最旧的，{@link #ringSum} 同步增减，求均值无需遍历。 */
	private static final long[] ring = new long[CAPACITY];
	private static int ringIndex;
	private static int ringCount;
	private static long ringSum;

	/** 距上次评估过了几刻。 */
	private static int sinceEvaluate;

	/** 连续超标次数；任一次评估未超标即清零。 */
	private static int consecutiveChecks;

	/** 冷却截止时刻（纳秒）；0 = 从未触发。 */
	private static long cooldownUntilNanos;

	/** 本 tick 开始的时刻（START 钩子写，END 钩子读）；0 表示无起点。 */
	private static long tickStartNanos;

	private MsptMonitor() {
	}

	/** 服务器停止：丢弃全部状态。静态字段跨世界存活，不复位则旧冷却带进下一个世界。 */
	public static void reset() {
		ringIndex = 0;
		ringCount = 0;
		ringSum = 0L;
		sinceEvaluate = 0;
		consecutiveChecks = 0;
		cooldownUntilNanos = 0L;
		tickStartNanos = 0L;
	}

	/** 每个服务端 tick 开始时调用：记下起点时刻。 */
	public static void onTickStart() {
		tickStartNanos = System.nanoTime();
	}

	/**
	 * 每个服务端 tick 结束时调用：量本 tick 耗时并做一次决策，该触发就发起自动扫描。
	 *
	 * <p>未启用时不作任何测量（关闭即零开销），并清空上次留下的窗口。
	 */
	public static void onTickEnd(MinecraftServer server) {
		long now = System.nanoTime();
		long start = tickStartNanos;
		tickStartNanos = 0L;
		if (start == 0L) {
			// 没有起点：服务器刚起或刚复位，本 tick 的耗时无从算起
			return;
		}
		if (!ServerConfig.monitorEnabled) {
			if (ringCount != 0) {
				reset();
			}
			return;
		}
		if (!observe(now - start, now)) {
			return;
		}
		// 无接收者时扫描没有意义，且会占用采样窗口，妨碍玩家的手动扫描
		if (MsptAlert.targets(server).isEmpty()) {
			return;
		}
		switch (MsptSampler.startAuto(AUTO_SCAN_SECONDS, server)) {
			case STARTED -> markTriggered(now);
			// 忙 / 冷却中 / 停滞：留在等待态，下次评估再试；不消耗冷却，也不清零去抖计数
			default -> {
			}
		}
	}

	/**
	 * 喂一个 tick 的实际耗时（纳秒）并决策；true = 本次应当发起一次自动扫描。
	 *
	 * <p>每 {@link #EVALUATE_TICKS} 刻才评估一次：均值高于阈值则累加去抖计数，低于阈值立刻清零
	 * （回摆即重新计数）。连续达标且冷却已过才返回 true。
	 */
	static boolean observe(long nanos, long now) {
		push(nanos);
		if (++sinceEvaluate < EVALUATE_TICKS) {
			return false;
		}
		sinceEvaluate = 0;
		// 窗口未填满时不判定：样本过少，均值不具代表性（监控刚启用）
		if (ringCount < CAPACITY) {
			consecutiveChecks = 0;
			return false;
		}
		if (meanMspt() <= ServerConfig.threshold) {
			consecutiveChecks = 0;
			return false;
		}
		consecutiveChecks++;
		return consecutiveChecks >= CONSECUTIVE_CHECKS && now >= cooldownUntilNanos;
	}

	/** 本次扫描确实发起了：进冷却、清零去抖计数。 */
	static void markTriggered(long now) {
		cooldownUntilNanos = now + ServerConfig.cooldownMinutes * 60_000_000_000L;
		consecutiveChecks = 0;
	}

	/** 把本 tick 的耗时放进环形缓冲。 */
	private static void push(long nanos) {
		if (ringCount == CAPACITY) {
			ringSum -= ring[ringIndex];
		} else {
			ringCount++;
		}
		ring[ringIndex] = nanos;
		ringSum += nanos;
		ringIndex = (ringIndex + 1) % CAPACITY;
	}

	/** 已收样本的平均 mspt；还没有样本时为 0。 */
	public static double meanMspt() {
		return ringCount == 0 ? 0.0 : ringSum / 1_000_000.0 / ringCount;
	}

	/** 当前状态。 */
	public static State state() {
		if (!ServerConfig.monitorEnabled) {
			return State.OFF;
		}
		if (System.nanoTime() < cooldownUntilNanos) {
			return State.COOLDOWN;
		}
		return consecutiveChecks > 0 ? State.WATCHING : State.OK;
	}

	/** 已连续超标的次数（去抖进度）。 */
	public static int consecutiveChecks() {
		return consecutiveChecks;
	}

	/** 冷却剩余秒数（向上取整）；不在冷却中时为 0。 */
	public static long cooldownRemainingSeconds() {
		long remaining = cooldownUntilNanos - System.nanoTime();
		return remaining <= 0 ? 0L : (remaining + 999_999_999L) / 1_000_000_000L;
	}
}
