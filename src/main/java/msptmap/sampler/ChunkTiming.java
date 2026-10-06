package msptmap.sampler;

/**
 * 单个区块的采样账本：按类别累计耗时（纳秒）与调用次数。
 *
 * <p>以 long 存纳秒而非 double 存毫秒，使热路径只需加法与自增，单位换算留到窗口结束时统一进行。
 */
public final class ChunkTiming {
	private final long[] nanos = new long[TickCategory.COUNT];
	private final int[] counts = new int[TickCategory.COUNT];

	/** 记一笔。热路径，不得分配对象或做除法。 */
	public void add(TickCategory category, long durationNanos) {
		int index = category.ordinal();
		this.nanos[index] += durationNanos;
		this.counts[index]++;
	}

	public long nanos(TickCategory category) {
		return this.nanos[category.ordinal()];
	}

	/** 内部数组，下标为 {@link TickCategory#ordinal()}；仅供编码快照时只读使用，不复制。 */
	public long[] nanosArray() {
		return this.nanos;
	}

	public int[] countsArray() {
		return this.counts;
	}

	/** 合计：各类耗时之和（纳秒），不计方块更新——其耗时已含在触发它的那一类里，计入会重复。 */
	public long totalNanos() {
		long total = 0L;
		for (int i = 0; i < TickCategory.COUNT; i++) {
			if (i != TickCategory.NEIGHBOR_UPDATE.ordinal()) {
				total += this.nanos[i];
			}
		}
		return total;
	}
}
