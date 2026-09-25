package msptmap.mixins;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 随机刻 / 计划刻 / 实体三类工作的计时。
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

	/** Mixin 内 this 不具备 ServerLevel 静态类型，须转换后才能作为参数传递。 */
	@Unique
	private ServerLevel msptmapLevel() {
		return (ServerLevel) (Object) this;
	}
}
