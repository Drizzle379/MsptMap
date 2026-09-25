package msptmap.sampler;

/**
 * 一个区块的账本：五类工作各累计的纳秒数与调用次数。
 *
 * 以 long 存纳秒而非 double 存毫秒：热路径只做加法与自增，换算成 mspt 留到窗口结束时做一次
 * （少几千次除法，也不丢精度）。
 */
public final class ChunkTiming {
	private static final int CATEGORIES = TickCategory.values().length;

	private final long[] nanos = new long[CATEGORIES];
	private final int[] counts = new int[CATEGORIES];

	/** 记一笔。热路径，禁止分配对象与除法。 */
	public void add(TickCategory category, long durationNanos) {
		int index = category.ordinal();
		this.nanos[index] += durationNanos;
		this.counts[index]++;
	}

	public long nanos(TickCategory category) {
		return this.nanos[category.ordinal()];
	}

	/** 内部数组直接给出（下标 = {@link TickCategory#ordinal()}）。仅供快照使用：编码一次即弃，不复制。 */
	public long[] nanosArray() {
		return this.nanos;
	}

	public int[] countsArray() {
		return this.counts;
	}

	public long totalNanos() {
		long total = 0L;
		for (long value : this.nanos) {
			total += value;
		}
		return total;
	}
}
