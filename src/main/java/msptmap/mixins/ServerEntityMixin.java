package msptmap.mixins;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import msptmap.util.ChunkKeys;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体状态同步计时：每 tick 向追踪该实体的玩家发送位置、旋转、骑乘与元数据变更。
 *
 * <p>并入实体类别：该方法的调用点在 {@code ChunkMap.tick}，与 {@code ServerLevel.tickNonPassenger}
 * 分处 tick 的不同阶段，不嵌套，两者耗时可直接相加。
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
	/** 被同步的实体：其位置即记账所属的区块。 */
	@Shadow
	@Final
	private Entity entity;

	/** 实体所在维度。 */
	@Shadow
	@Final
	private ServerLevel level;

	/** 本次同步的开始时刻（0 = 未采样）。 */
	@Unique
	private long msptmapStart;

	@Inject(method = "sendChanges", at = @At("HEAD"))
	private void msptmapBegin(CallbackInfo ci) {
		this.msptmapStart = MsptSampler.begin();
	}

	@Inject(method = "sendChanges", at = @At("RETURN"))
	private void msptmapEnd(CallbackInfo ci) {
		if (this.msptmapStart == 0L) {
			return;
		}
		MsptSampler.end(TickCategory.ENTITY, this.level, ChunkKeys.pack(this.entity), this.msptmapStart);
	}
}
