package msptmap.sampler;

/**
 * 计入耗时的七类工作。
 *
 * 顺序不可变更：ChunkTiming 的数组按下标（ordinal）存储，快照也按此顺序上线。新增类别只能追加在末尾；
 * 界面上的显示顺序另有一套，见 {@link msptmap.client.ChunkTooltip#ORDER}。
 */
public enum TickCategory {
	/** 随机刻：ServerLevel.tickChunk 整个方法的耗时（其中绝大部分是随机刻）。 */
	RANDOM_TICK,
	/** 计划刻：方块与流体（ServerLevel.tickBlock / tickFluid）。 */
	SCHEDULED,
	/** 方块实体：LevelChunk$BoundTickingBlockEntity.tick()（见 BoundTickingBlockEntityMixin）。 */
	BLOCK_ENTITY,
	/** 实体：ServerLevel.tickNonPassenger。 */
	ENTITY,
	/** 刷怪：NaturalSpawner.spawnForChunk。 */
	SPAWN,
	/** 方块更新：ServerLevel.updateNeighborsAt（通知六个邻居，红石连锁的耗时落在这里）。 */
	NEIGHBOR_UPDATE,
	/** 方块事件：ServerLevel.doBlockEvent（活塞、箱子、音符盒那类排队的方块动作）。 */
	BLOCK_EVENT
}
