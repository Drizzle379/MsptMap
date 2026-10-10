package msptmap.mixins;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
//? if >=1.21.5 {
import net.minecraft.world.level.TicketStorage;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取该维度当前加载的区块、各自的等级，以及加载票表。
 *
 * <p>开放访问器是因为这两张表在 ChunkMap 中均为 private，而公开的两条遍历
 * （{@code forEachReadyToSendChunk} / {@code forEachBlockTickingChunk}）分别要求「已就绪待发送」
 * 与「31 级以内」，均会漏掉远离玩家的加载点（珍珠加载器即此类）。等级写在
 * {@link ChunkHolder#getTicketLevel()} 上，与 {@code getChunkLevel(key, false)} 读到的是同一个值。
 *
 * <p>加载票表用于反查区块的加载来源：{@code TicketStorage.getTickets(long)} 给出注册在该区块上的
 * 票，而票只落在锚点区块上，周围区块靠等级传播覆盖。
 *
 * <p>只读字段，不改动原版逻辑；{@code @Accessor} 之间也不冲突。
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("visibleChunkMap")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> getVisibleChunks();

	// 全量区块表：含视距外正在 tick 的（远程 forceload、传送门加载区）。票的锚点须在这张表上查找；
	// 只用 visibleChunks 会漏掉远离玩家的加载点，那片热力图即无中心。
	@Accessor("updatingChunkMap")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> getUpdatingChunks();

	// 加载票表：1.21.5 起才有 TicketStorage 字段（1.21.4 及以前的票表在 DistanceManager 中，
	// 体系不同，本模组暂不支持，见 TicketSources）。
	//? if >=1.21.5 {
	@Accessor("ticketStorage")
	TicketStorage getTicketStorage();
	//?}
}
