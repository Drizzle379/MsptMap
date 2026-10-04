package msptmap.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import msptmap.ChunkKeys;
import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 随机刻 / 计划刻 / 方块更新 / 方块事件 / 实体五类工作的计时。
 *
 * 每个点都是 HEAD 记开始、RETURN 记结束，差值即该方法自身的耗时。开始时刻存 @Unique 字段而不用
 * ThreadLocal：这几个方法只在服务端主线程调用，而 ThreadLocal 每次读写都要装箱一个 Long。
 *
 * 各 RETURN 注入点先判「开始时刻为 0（未在采样）」即提前返回：否则每次收尾都要白算一遍区块坐标
 * 打包与维度转换（{@link MsptSampler#end} 本就忽略 0，只是参数已经求了值）。
 *
 * 乘客不会重复计时，其耗时记在**载具所在的那个区块**上。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Unique
	private long msptmapRandomTickStart;

	@Unique
	private long msptmapBlockStart;

	@Unique
	private long msptmapFluidStart;

	@Unique
	private long msptmapEntityStart;

	@Unique
	private long msptmapNeighborStart;

	/** 方块更新的嵌套层数：只有最外层那次计时。 */
	@Unique
	private int msptmapNeighborDepth;

	@Unique
	private long msptmapBlockEventStart;

	/** 随机刻。 */
	@Inject(method = "tickChunk", at = @At("HEAD"))
	private void msptmapRandomTickBegin(LevelChunk chunk, int tickSpeed, CallbackInfo ci) {
		this.msptmapRandomTickStart = MsptSampler.begin();
	}

	@Inject(method = "tickChunk", at = @At("RETURN"))
	private void msptmapRandomTickEnd(LevelChunk chunk, int tickSpeed, CallbackInfo ci) {
		if (this.msptmapRandomTickStart == 0L) {
			return;
		}
		MsptSampler.end(TickCategory.RANDOM_TICK, this.msptmapLevel(), ChunkKeys.pack(chunk.getPos()), this.msptmapRandomTickStart);
	}

	/** 计划刻：方块。 */
	@Inject(method = "tickBlock", at = @At("HEAD"))
	private void msptmapBlockBegin(BlockPos pos, Block block, CallbackInfo ci) {
		this.msptmapBlockStart = MsptSampler.begin();
	}

	@Inject(method = "tickBlock", at = @At("RETURN"))
	private void msptmapBlockEnd(BlockPos pos, Block block, CallbackInfo ci) {
		if (this.msptmapBlockStart == 0L) {
			return;
		}
		MsptSampler.end(TickCategory.SCHEDULED, this.msptmapLevel(), ChunkKeys.pack(pos), this.msptmapBlockStart);
	}

	/** 计划刻：流体。开始时刻与方块分开存储，两者嵌套时互不覆盖。 */
	@Inject(method = "tickFluid", at = @At("HEAD"))
	private void msptmapFluidBegin(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		this.msptmapFluidStart = MsptSampler.begin();
	}

	@Inject(method = "tickFluid", at = @At("RETURN"))
	private void msptmapFluidEnd(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		if (this.msptmapFluidStart == 0L) {
			return;
		}
		MsptSampler.end(TickCategory.SCHEDULED, this.msptmapLevel(), ChunkKeys.pack(pos), this.msptmapFluidStart);
	}

	/** 实体。 */
	@Inject(method = "tickNonPassenger", at = @At("HEAD"))
	private void msptmapEntityBegin(Entity entity, CallbackInfo ci) {
		this.msptmapEntityStart = MsptSampler.begin();
	}

	@Inject(method = "tickNonPassenger", at = @At("RETURN"))
	private void msptmapEntityEnd(Entity entity, CallbackInfo ci) {
		if (this.msptmapEntityStart == 0L) {
			return;
		}
		MsptSampler.end(TickCategory.ENTITY, this.msptmapLevel(), ChunkKeys.pack(entity.chunkPosition()), this.msptmapEntityStart);
	}

	/**
	 * 方块更新：通知某坐标的六个邻居。红石连锁在派发过程中会再绕回本方法，故只记最外层那一次
	 * （嵌套调用的耗时已含在外层里，逐个记账会重复累加）；区块算最外层那次的位置。
	 *
	 * 用 @WrapMethod 而非 HEAD / RETURN 两次 @Inject：深度计数须在 try-finally 里还原 —— 目标方法
	 * 抛出异常时 RETURN 注入不会执行，计数将永久失衡，此后所有红石耗时都不再记录。
	 */
	// 1.21.2 起目标方法多了 Orientation 参数；两个形态只差它，注入方法随之分叉。
	//? if >=1.21.2 {
	@WrapMethod(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
			+ "Lnet/minecraft/world/level/redstone/Orientation;)V")
	private void msptmapNeighbor(BlockPos pos, Block sourceBlock, Orientation orientation, Operation<Void> original) {
		if (this.msptmapNeighborDepth++ == 0) {
			this.msptmapNeighborStart = MsptSampler.begin();
		}
		try {
			original.call(pos, sourceBlock, orientation);
		} finally {
			if (--this.msptmapNeighborDepth == 0 && this.msptmapNeighborStart != 0L) {
				MsptSampler.end(TickCategory.NEIGHBOR_UPDATE, this.msptmapLevel(), ChunkKeys.pack(pos),
						this.msptmapNeighborStart);
			}
		}
	}
	//?} else {
	/*@WrapMethod(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V")
	private void msptmapNeighbor(BlockPos pos, Block sourceBlock, Operation<Void> original) {
		if (this.msptmapNeighborDepth++ == 0) {
			this.msptmapNeighborStart = MsptSampler.begin();
		}
		try {
			original.call(pos, sourceBlock);
		} finally {
			if (--this.msptmapNeighborDepth == 0 && this.msptmapNeighborStart != 0L) {
				MsptSampler.end(TickCategory.NEIGHBOR_UPDATE, this.msptmapLevel(), ChunkKeys.pack(pos),
						this.msptmapNeighborStart);
			}
		}
	}
	*///?}

	/**
	 * 方块事件：由 {@code ServerLevel.tick} 里的 runBlockEvents 调起，不在 tickChunk 内，与本类其余各点不重叠。
	 *
	 * 这个方法返回 boolean（事件有没有被处理掉），所以回调必须是 {@link CallbackInfoReturnable}：
	 * 目标有返回值而回调写成 CallbackInfo，Mixin 会在 Bootstrap 阶段直接报 InvalidInjectionException。
	 * 我们只要时间，不碰那个返回值（没写 cancellable，改不了它）。
	 */
	@Inject(method = "doBlockEvent", at = @At("HEAD"))
	private void msptmapBlockEventBegin(BlockEventData eventData, CallbackInfoReturnable<Boolean> cir) {
		this.msptmapBlockEventStart = MsptSampler.begin();
	}

	@Inject(method = "doBlockEvent", at = @At("RETURN"))
	private void msptmapBlockEventEnd(BlockEventData eventData, CallbackInfoReturnable<Boolean> cir) {
		if (this.msptmapBlockEventStart == 0L) {
			return;
		}
		MsptSampler.end(TickCategory.BLOCK_EVENT, this.msptmapLevel(), ChunkKeys.pack(eventData.pos()),
				this.msptmapBlockEventStart);
	}

	/** Mixin 内 this 不具备 ServerLevel 静态类型，须转换后才能作为参数传递。 */
	@Unique
	private ServerLevel msptmapLevel() {
		return (ServerLevel) (Object) this;
	}
}
