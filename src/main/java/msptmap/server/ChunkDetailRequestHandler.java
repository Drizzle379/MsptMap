package msptmap.server;

import msptmap.MsptMapMod;
import msptmap.Permissions;
import msptmap.mixins.EntityLevelAccessor;
import msptmap.net.ChunkDetailRequestPayload;
import msptmap.net.ChunkDetailResultPayload;
import msptmap.sampler.ChunkDetailBuilder;
import msptmap.util.Ids;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 区块详情请求的处理：校魔数、查权限与维度，再交给 {@link ChunkDetailBuilder} 构建回包。
 *
 * <p>独立于模组入口（同 {@link ScanRequestHandler}）：入口只挂注册。
 */
public final class ChunkDetailRequestHandler {
	private ChunkDetailRequestHandler() {
	}

	public static void handle(ChunkDetailRequestPayload payload, ServerPlayer player) {
		if (payload.protocol() == ChunkDetailRequestPayload.MISMATCH) {
			// 包体无法读取：无从判断对面请求的是哪个区块，故不回复
			MsptMapMod.LOGGER.warn("玩家 {} 的 MsptMap 区块详情请求解析不了，已忽略", playerName(player));
			return;
		}
		if (payload.protocol() != MsptMapMod.PROTOCOL) {
			// 对面版本不同：仅记日志，照常执行（同 ScanRequestHandler）
			MsptMapMod.LOGGER.warn("玩家 {} 的 MsptMap 版本与本端不一致（对面 {}，本端 {}），仍按其请求执行",
					playerName(player), payload.protocol(), MsptMapMod.PROTOCOL);
		}
		if (!Permissions.canUse(player.createCommandSourceStack())) {
			ServerPlayNetworking.send(player, ChunkDetailResultPayload.empty(
					payload.dimension(), payload.chunkX(), payload.chunkZ()));
			return;
		}
		// ServerPlayer 自身没有公开的取 server 的入口，经 ServerLevel 获取；理由同 CommandRelayHandler
		MinecraftServer server = ((EntityLevelAccessor) player).msptmap$level().getServer();
		ServerLevel level = findLevel(server, payload.dimension());
		if (level == null) {
			ServerPlayNetworking.send(player, ChunkDetailResultPayload.empty(
					payload.dimension(), payload.chunkX(), payload.chunkZ()));
			return;
		}
		ServerPlayNetworking.send(player, ChunkDetailBuilder.build(
				level, payload.dimension(), payload.chunkX(), payload.chunkZ()));
	}

	/** 按维度 ID 查已加载的维度；不存在时返回 null。 */
	private static @Nullable ServerLevel findLevel(@Nullable MinecraftServer server, String dimension) {
		if (server == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (Ids.id(level.dimension()).equals(dimension)) {
				return level;
			}
		}
		return null;
	}

	/** 玩家名（供日志）。同 {@link ScanRequestHandler}：{@code getScoreboardName()} 全版本稳定。 */
	private static String playerName(ServerPlayer player) {
		return player.getScoreboardName();
	}
}
