package msptmap.mixins;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取该维度当前加载的区块、各自的等级，以及加载票表。
 *
 * 开访问器是因为这两张表在 ChunkMap 中都是 private，而公开的两条遍历（{@code forEachReadyToSendChunk} /
 * {@code forEachBlockTickingChunk}）分别要求「已就绪待发送」与「31 级以内」，都会漏掉远离玩家的加载点
 * —— 珍珠加载器即此类。等级写在 {@link ChunkHolder#getTicketLevel()} 上，与
 * {@code getChunkLevel(key, false)} 读到的是同一个值。
 *
 * 加载票表用于反查区块的加载来源：{@code TicketStorage.getTickets(long)} 给出注册在该区块上的票，
 * 而票只落在锚点区块上，周围的区块靠等级传播覆盖（见 PLAN 坑 25）。
 *
 * 只读字段，不改动原版逻辑；{@code @Accessor} 之间也不冲突。
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("visibleChunkMap")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> getVisibleChunks();

	@Accessor("ticketStorage")
	TicketStorage getTicketStorage();
}
