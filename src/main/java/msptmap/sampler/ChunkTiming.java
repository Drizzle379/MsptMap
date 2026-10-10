package msptmap.sampler;

/**
 * 单个区块的采样账本：按类别累计耗时（纳秒）与调用次数。
 *
 * <p>耗时以 long 存纳秒而非 double 存毫秒，使热路径只需加法与自增，单位换算留待窗口结束时统一进行。
 */
public final class ChunkTiming {
	private final long[] nanos = new long[TickCategory.COUNT];
	private final int[] counts = new int[TickCategory.COUNT];

	/** 记一笔。热路径，不得分配对象或执行除法。 */
	public void add(TickCategory category, long durationNanos) {
		int index = category.ordinal();
		this.nanos[index] += durationNanos;
		this.counts[index]++;
	}

	public long nanos(TickCategory category) {
		return this.nanos[category.ordinal()];
	}

	/** 内部数组，下标为 {@link TickCategory#ordinal()}；仅供编码快照时只读使用，不作复制。 */
	public long[] nanosArray() {
		return this.nanos;
	}

	public int[] countsArray() {
		return this.counts;
	}

	/** 合计（规则见 {@link TickCategory#totalNanos(long[])}）。 */
	public long totalNanos() {
		return TickCategory.totalNanos(this.nanos);
	}
}
