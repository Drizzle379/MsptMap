package msptmap.mixins;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块实体的计时。
 *
 * 挂在真正执行 tick 的那个 ticker 上，不 redirect 循环中的 TickingBlockEntity.tick()：后者是方块实体
 * 循环里唯一的计时位置，同类模组都会抢占，而 @Redirect 互斥 —— 后到的被静默跳过并在启动时崩溃。
 *
 * 全游戏仅 3 个类实现 TickingBlockEntity：LevelChunk$1.tick() 为空方法（占位）、
 * RebindableTickingBlockEntityWrapper 仅转发给被包装者，真正 tick 方块实体的只有这一个。
 *
 * 目标类为包私有，源码中无法书写 LevelChunk.BoundTickingBlockEntity.class，只能用 targets 字符串。
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {
	/** 该 ticker 包装的方块实体：位置与维度均由它取得。 */
	@Shadow
	@Final
	private BlockEntity blockEntity;

	/** 本次 tick 的开始时刻（0 = 未采样），HEAD 每 tick 重写。 */
	@Unique
	private long msptmapStart;

	@Inject(method = "tick", at = @At("HEAD"))
	private void msptmapStartTick(CallbackInfo ci) {
		msptmapStart = MsptSampler.begin();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void msptmapEndTick(CallbackInfo ci) {
		// start 为 0 时 MsptSampler.end 自行忽略，无需重复判断
		// 客户端也有同一套 ticker（ClientLevel），采样仅在服务端发生
		if (blockEntity.getLevel() instanceof ServerLevel level) {
			MsptSampler.end(TickCategory.BLOCK_ENTITY, level, ChunkPos.pack(blockEntity.getBlockPos()), msptmapStart);
		}
	}
}
