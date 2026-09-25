package msptmap.sampler;

/**
 * 计入耗时的五类工作。
 *
 * 顺序不可变更：ChunkTiming 的数组按下标（ordinal）存储。
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
	SPAWN
}
