package msptmap.mixins;

import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 刷怪的计时。
 *
 * 与其他几处不同：spawnForChunk 是静态方法，开始时刻只能存静态字段（它在主线程上按区块顺序调用，
 * 不会嵌套）；方法签名已含 level 与 chunk，无需自行计算坐标。
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	@Unique
	private static long msptmapSpawnStart;

	@Inject(method = "spawnForChunk", at = @At("HEAD"))
	private static void msptmapSpawnBegin(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
			List<MobCategory> spawningCategories, CallbackInfo ci) {
		msptmapSpawnStart = MsptSampler.begin();
	}

	@Inject(method = "spawnForChunk", at = @At("RETURN"))
	private static void msptmapSpawnEnd(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
			List<MobCategory> spawningCategories, CallbackInfo ci) {
		MsptSampler.end(TickCategory.SPAWN, level, chunk.getPos().pack(), msptmapSpawnStart);
	}
}
