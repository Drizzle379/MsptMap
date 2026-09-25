package msptmap.mixins;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.Orientation;
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
		MsptSampler.end(TickCategory.RANDOM_TICK, this.msptmapLevel(), chunk.getPos().pack(), this.msptmapRandomTickStart);
	}

	/** 计划刻：方块。 */
	@Inject(method = "tickBlock", at = @At("HEAD"))
	private void msptmapBlockBegin(BlockPos pos, Block block, CallbackInfo ci) {
		this.msptmapBlockStart = MsptSampler.begin();
	}

	@Inject(method = "tickBlock", at = @At("RETURN"))
	private void msptmapBlockEnd(BlockPos pos, Block block, CallbackInfo ci) {
		MsptSampler.end(TickCategory.SCHEDULED, this.msptmapLevel(), ChunkPos.pack(pos), this.msptmapBlockStart);
	}

	/** 计划刻：流体。开始时刻与方块分开存储，两者嵌套时互不覆盖。 */
	@Inject(method = "tickFluid", at = @At("HEAD"))
	private void msptmapFluidBegin(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		this.msptmapFluidStart = MsptSampler.begin();
	}

	@Inject(method = "tickFluid", at = @At("RETURN"))
	private void msptmapFluidEnd(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		MsptSampler.end(TickCategory.SCHEDULED, this.msptmapLevel(), ChunkPos.pack(pos), this.msptmapFluidStart);
	}

	/** 实体。 */
	@Inject(method = "tickNonPassenger", at = @At("HEAD"))
	private void msptmapEntityBegin(Entity entity, CallbackInfo ci) {
		this.msptmapEntityStart = MsptSampler.begin();
	}

	@Inject(method = "tickNonPassenger", at = @At("RETURN"))
	private void msptmapEntityEnd(Entity entity, CallbackInfo ci) {
		MsptSampler.end(TickCategory.ENTITY, this.msptmapLevel(), entity.chunkPosition().pack(), this.msptmapEntityStart);
	}

	/**
	 * 方块更新：通知某坐标的六个邻居。红石连锁在派发过程中会再绕回本方法，故只记最外层那一次
	 * （嵌套调用的耗时已含在外层里，逐个记账会重复累加）；区块算最外层那次的位置。
	 */
	@Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
			+ "Lnet/minecraft/world/level/redstone/Orientation;)V", at = @At("HEAD"))
	private void msptmapNeighborBegin(BlockPos pos, Block sourceBlock, Orientation orientation, CallbackInfo ci) {
		if (this.msptmapNeighborDepth++ == 0) {
			this.msptmapNeighborStart = MsptSampler.begin();
		}
	}

	@Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
			+ "Lnet/minecraft/world/level/redstone/Orientation;)V", at = @At("RETURN"))
	private void msptmapNeighborEnd(BlockPos pos, Block sourceBlock, Orientation orientation, CallbackInfo ci) {
		if (--this.msptmapNeighborDepth == 0) {
			MsptSampler.end(TickCategory.NEIGHBOR_UPDATE, this.msptmapLevel(), ChunkPos.pack(pos),
					this.msptmapNeighborStart);
		}
	}

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
		MsptSampler.end(TickCategory.BLOCK_EVENT, this.msptmapLevel(), ChunkPos.pack(eventData.pos()),
				this.msptmapBlockEventStart);
	}

	/** Mixin 内 this 不具备 ServerLevel 静态类型，须转换后才能作为参数传递。 */
	@Unique
	private ServerLevel msptmapLevel() {
		return (ServerLevel) (Object) this;
	}
}
