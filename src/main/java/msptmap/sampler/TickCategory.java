package msptmap.sampler;

/**
 * 计入耗时的七类工作。
 *
 * <p>顺序不可变更：{@link ChunkTiming} 的数组按下标（ordinal）存储，快照也按此顺序上线；新增类别
 * 只能追加在末尾。界面上的显示顺序另有一套，见 {@code msptmap.client.ChunkTooltip#ORDER}。
 */
public enum TickCategory {
	/** 随机刻：{@code ServerLevel.tickChunk} 整个方法的耗时（其中绝大部分是随机刻）。 */
	RANDOM_TICK,
	/** 计划刻：方块与流体（{@code ServerLevel.tickBlock} / {@code tickFluid}）。 */
	SCHEDULED,
	/** 方块实体：{@code LevelChunk$BoundTickingBlockEntity.tick()}（见 BoundTickingBlockEntityMixin）。 */
	BLOCK_ENTITY,
	/** 实体：{@code ServerLevel.tickNonPassenger}。 */
	ENTITY,
	/** 刷怪：{@code NaturalSpawner.spawnForChunk}。 */
	SPAWN,
	/** 方块更新：{@code ServerLevel.updateNeighborsAt}（通知六个邻居，红石连锁的耗时落在这里）。 */
	NEIGHBOR_UPDATE,
	/** 方块事件：{@code ServerLevel.doBlockEvent}（活塞、箱子、音符盒一类的排队方块动作）。 */
	BLOCK_EVENT;

	/**
	 * 类别数。以它代替 {@code values().length}：后者每次调用都克隆一份数组，逐区块的编码与建表
	 * 路径上会积成数万次克隆。
	 */
	public static final int COUNT = values().length;
}
