package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.LongIterator;
import msptmap.MsptMapMod;
import msptmap.mixins.ChunkMapAccessor;
import msptmap.net.ChunkDetailResultPayload;
import msptmap.util.ChunkKeys;
import msptmap.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
//? if >=1.21.5 {
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 区块详情构建：覆盖本区块的加载票与当前实体组成。
 *
 * <p>列出所有传播到本区块后仍处于 block ticking 范围（等级 ≤ 32）的票：
 * <ul>
 *   <li>本区块自己持有的票：传播值即票面等级，不标锚点；</li>
 *   <li>附近锚点传播过来的票：传播值 = 票等级 + 切比雪夫距离，带上锚点坐标。</li>
 * </ul>
 * 多张票可并列覆盖同一区块，其中传播值最小者决定实际等级，其余票只维持同一区块的 tick，
 * 故逐张列出并标出各自等级，而不是只列决定等级的那一张。等级超过 32 的票只加载不 tick，不列。
 * 玩家加载票由玩家 tracker 给每个区块本地挂一张（锚点即自身），只在该区块自身列出，不按邻近
 * 锚点传播标注来源。
 *
 * <p>票体系自 1.21.5 起为 {@code TicketStorage}；更早版本结构不同，票列表返回空。
 */
public final class ChunkDetailBuilder {
	private ChunkDetailBuilder() {
	}

	public static ChunkDetailResultPayload build(ServerLevel level, String dimension, int chunkX, int chunkZ) {
		long key = ChunkKeys.pack(chunkX, chunkZ);
		return new ChunkDetailResultPayload(MsptMapMod.PROTOCOL, dimension, chunkX, chunkZ,
				tickets(level, key, chunkX, chunkZ), entities(level, key));
	}

	/** 覆盖本区块的票，按最低等级升序（同等级按类型与锚点）；超过上限时截掉尾部。 */
	private static List<ChunkDetailResultPayload.TicketInfo> tickets(ServerLevel level, long key,
			int chunkX, int chunkZ) {
		//? if >=1.21.5 {
		ChunkMap chunkMap = level.getChunkSource().chunkMap;
		ChunkMapAccessor accessor = (ChunkMapAccessor) (Object) chunkMap;
		TicketStorage storage = accessor.getTicketStorage();
		List<ChunkDetailResultPayload.TicketInfo> out = new ArrayList<>();

		// 本区块自己的票：传播值即票面等级
		addOwnTickets(out, storage.getTickets(key), chunkX, chunkZ);
		// 附近锚点：票只挂在锚点上，逐锚点取票，按传播值判断是否覆盖本区块。距离超过最大
		// 加载等级（33）的锚点，传播值必然超过 32，不可能让本区块 tick，直接跳过
		for (LongIterator it = accessor.getUpdatingChunks().keySet().iterator(); it.hasNext(); ) {
			long anchor = it.nextLong();
			if (anchor == key) {
				continue;
			}
			int distance = Math.max(Math.abs(ChunkPos.getX(anchor) - chunkX),
					Math.abs(ChunkPos.getZ(anchor) - chunkZ));
			if (distance > ChunkLevel.MAX_LEVEL) {
				continue;
			}
			addNearbyTickets(out, storage.getTickets(anchor), anchor, chunkX, chunkZ);
		}
		out.sort(Comparator
				.comparingInt(ChunkDetailBuilder::minLevel)
				.thenComparing(ChunkDetailResultPayload.TicketInfo::type)
				.thenComparingInt(ChunkDetailResultPayload.TicketInfo::anchorX)
				.thenComparingInt(ChunkDetailResultPayload.TicketInfo::anchorZ));
		if (out.size() > ChunkDetailResultPayload.MAX_TICKETS) {
			out.subList(ChunkDetailResultPayload.MAX_TICKETS, out.size()).clear();
		}
		return out;
		//?} else {
		/*return List.of();
		*///?}
	}

	//? if >=1.21.5 {
	/** 排序键：两条链中传播等级的最低值（-1 表示该链不参与）。 */
	private static int minLevel(ChunkDetailResultPayload.TicketInfo ticket) {
		int load = ticket.loadLevel() < 0 ? Integer.MAX_VALUE : ticket.loadLevel();
		int sim = ticket.simLevel() < 0 ? Integer.MAX_VALUE : ticket.simLevel();
		return Math.min(load, sim);
	}

	/** 本区块持有的票：传播值即票面等级，只留能让本区块 tick 的；类型不支持某链时该链写 -1。 */
	private static void addOwnTickets(List<ChunkDetailResultPayload.TicketInfo> out, List<Ticket> tickets,
			int chunkX, int chunkZ) {
		if (tickets == null) {
			return;
		}
		for (Ticket ticket : tickets) {
			TicketType type = ticket.getType();
			int level = ticket.getTicketLevel();
			boolean load = type.doesLoad();
			boolean sim = type.doesSimulate();
			if ((!load && !sim) || !ChunkLevel.isBlockTicking(level)) {
				continue;
			}
			out.add(new ChunkDetailResultPayload.TicketInfo(typeName(type), chunkX, chunkZ,
					load ? level : -1, sim ? level : -1));
		}
	}

	/**
	 * 附近锚点的票：按「票等级 + 切比雪夫距离」得到传播值，仍能让本区块 tick（≤ 32）才列；
	 * 传播值更大、只加载不 tick 的票不列。
	 */
	private static void addNearbyTickets(List<ChunkDetailResultPayload.TicketInfo> out, List<Ticket> tickets,
			long anchor, int chunkX, int chunkZ) {
		if (tickets == null) {
			return;
		}
		int anchorX = ChunkPos.getX(anchor);
		int anchorZ = ChunkPos.getZ(anchor);
		int distance = Math.max(Math.abs(anchorX - chunkX), Math.abs(anchorZ - chunkZ));
		for (Ticket ticket : tickets) {
			TicketType type = ticket.getType();
			// 玩家加载票是每个区块本地一张，不随邻近锚点传播，也不标来源
			if (type == TicketType.PLAYER_LOADING) {
				continue;
			}
			int spread = ticket.getTicketLevel() + distance;
			if (!ChunkLevel.isBlockTicking(spread)) {
				continue;
			}
			boolean load = type.doesLoad();
			boolean sim = type.doesSimulate();
			if (!load && !sim) {
				continue;
			}
			out.add(new ChunkDetailResultPayload.TicketInfo(typeName(type), anchorX, anchorZ,
					load ? spread : -1, sim ? spread : -1));
		}
	}

	/** 票类型的注册表路径（英文原代码）；未注册时给 unknown。 */
	private static String typeName(TicketType type) {
		String path = Ids.path(BuiltInRegistries.TICKET_TYPE, type);
		return path == null ? "unknown" : path;
	}
	//?}

	/** 本区块的实体组成：按类型完整 ID 分组计数，数量降序、同数量按 ID 排序。 */
	private static List<ChunkDetailResultPayload.EntityInfo> entities(ServerLevel level, long key) {
		Map<String, Integer> counts = new HashMap<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity.isRemoved() || ChunkKeys.pack(entity) != key) {
				continue;
			}
			var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
			counts.merge(id == null ? "unknown" : id.toString(), 1, Integer::sum);
		}
		List<ChunkDetailResultPayload.EntityInfo> out = new ArrayList<>(counts.size());
		counts.forEach((id, count) -> out.add(new ChunkDetailResultPayload.EntityInfo(id, count)));
		out.sort(Comparator.comparingInt(ChunkDetailResultPayload.EntityInfo::count).reversed()
				.thenComparing(ChunkDetailResultPayload.EntityInfo::id));
		if (out.size() > ChunkDetailResultPayload.MAX_ENTITIES) {
			out.subList(ChunkDetailResultPayload.MAX_ENTITIES, out.size()).clear();
		}
		return out;
	}
}
