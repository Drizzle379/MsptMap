package msptmap.client;

/**
 * 一个区块的引用：维度 ID + 区块坐标。
 *
 * <p>定位的载体：总览的 TOP 行 / 加载源行、右侧完整列表的源行，以及聊天告警里的区块行都指向它。
 */
public record ChunkRef(String dimension, int chunkX, int chunkZ) {
}
