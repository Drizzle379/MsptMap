package msptmap.mixins;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import msptmap.util.ChunkKeys;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 实体追踪计时：玩家进出实体的可见范围时更新追踪关系，并收发相应的生成与移除包。
 *
 * <p>并入实体类别：与 {@code ServerEntity.sendChanges} 同属 ChunkMap.tick 下的实体处理，两者
 * 相互独立、不嵌套。
 *
 * <p>目标为 {@code ChunkMap} 的包私有内部类，源码中无法书写其 class 字面量，只能用 targets 字符串
 * （写法同 {@link BoundTickingBlockEntityMixin}）。
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class ChunkMapTrackedEntityMixin {
	/** 被追踪的实体：其位置即记账所属的区块，维度也由它取得。 */
	@Shadow
	@Final
	private Entity entity;

	/** 本次更新的开始时刻（0 = 未采样）。 */
	@Unique
	private long msptmapStart;

	@Inject(method = "updatePlayers", at = @At("HEAD"))
	private void msptmapBegin(List<ServerPlayer> players, CallbackInfo ci) {
		this.msptmapStart = MsptSampler.begin();
	}

	@Inject(method = "updatePlayers", at = @At("RETURN"))
	private void msptmapEnd(List<ServerPlayer> players, CallbackInfo ci) {
		if (this.msptmapStart == 0L) {
			return;
		}
		if (((EntityLevelAccessor) this.entity).msptmap$level() instanceof ServerLevel level) {
			MsptSampler.end(TickCategory.ENTITY, level, ChunkKeys.pack(this.entity.chunkPosition()),
					this.msptmapStart);
		}
	}
}
