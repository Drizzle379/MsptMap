package msptmap.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import msptmap.util.ChunkKeys;
import msptmap.util.NeighborTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 形状更新（PP）的计时。
 *
 * <p>{@code neighborShapeChanged} 是 PP 的唯一入口：方块放置、破坏或状态变化后，邻居检查自身形状
 * （火把掉落、栅栏与铁轨的连接、红石线的附着等）的耗时都落在这里。
 *
 * <p>耗时刻意并入「方块更新」一类（{@link TickCategory#NEIGHBOR_UPDATE}）而非单列：PP 与 NC 互为
 * 嵌套，共用 {@link NeighborTiming} 的嵌套层数才不重复计时，两者相加即整类事件的总耗时。
 *
 * <p>用 {@code @WrapMethod} 而非 HEAD / RETURN 两次注入：嵌套层数须在 try-finally 中还原，目标方法
 * 抛异常时 RETURN 注入不会执行，计数将永久失衡（同 {@link ServerLevelMixin}）。
 */
@Mixin(Level.class)
public abstract class LevelMixin implements NeighborTiming {
	/** 最外层方块更新的开始时刻；0 = 未在计时。 */
	@Unique
	private long msptmapUpdateStartNanos;

	/** 当前嵌套层数：0 表示不在任何方块更新入口内。 */
	@Unique
	private int msptmapUpdateNestingCount;

	@Override
	public void msptmapUpdateBegin() {
		if (this.msptmapUpdateNestingCount == 0) {
			// 客户端也走本入口：只在服务端读取采样状态，避免渲染线程读服务端主线程的字段
			this.msptmapUpdateStartNanos = (Object) this instanceof ServerLevel ? MsptSampler.begin() : 0L;
		}
		this.msptmapUpdateNestingCount++;
	}

	@Override
	public void msptmapUpdateEnd(BlockPos pos) {
		this.msptmapUpdateNestingCount--;
		if (this.msptmapUpdateNestingCount != 0) {
			return;
		}
		long start = this.msptmapUpdateStartNanos;
		this.msptmapUpdateStartNanos = 0L;
		// 客户端也走这个入口，但它不是 ServerLevel，不记账（未采样时 begin 本就返回 0）。
		// 先转 Object：mixin 里 this 的静态类型是 LevelMixin，与 ServerLevel 无继承关系，直接匹配不过编译
		if (start != 0L && (Object) this instanceof ServerLevel level) {
			MsptSampler.end(TickCategory.NEIGHBOR_UPDATE, level, ChunkKeys.pack(pos), start);
		}
	}

	// 被更新形状的方块：1.21.2 起是第二个参数，此前是第三个（顺序与 NeighborUpdater.shapeUpdate 对齐）。
	//? if >=1.21.2 {
	@WrapMethod(method = "neighborShapeChanged(Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)V")
	private void msptmapShapeUpdate(Direction direction, BlockPos pos, BlockPos sourcePos, BlockState state,
			int flags, int recursionLeft, Operation<Void> original) {
		this.msptmapUpdateBegin();
		try {
			original.call(direction, pos, sourcePos, state, flags, recursionLeft);
		} finally {
			this.msptmapUpdateEnd(pos);
		}
	}
	//?} else {
	/*@WrapMethod(method = "neighborShapeChanged(Lnet/minecraft/core/Direction;"
			+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/core/BlockPos;II)V")
	private void msptmapShapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos sourcePos,
			int flags, int recursionLeft, Operation<Void> original) {
		this.msptmapUpdateBegin();
		try {
			original.call(direction, state, pos, sourcePos, flags, recursionLeft);
		} finally {
			this.msptmapUpdateEnd(pos);
		}
	}
	*///?}
}
