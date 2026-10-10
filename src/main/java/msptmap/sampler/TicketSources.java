package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import msptmap.util.ChunkKeys;
import msptmap.util.Ids;
import msptmap.MsptMapMod;
import msptmap.mixins.ChunkMapAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
//? if >=1.21.5 {
import net.minecraft.world.level.TicketStorage;
//?}

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;

/**
 * 反查每个区块的加载来源：即该区块被哪张加载票覆盖。
 *
 * <p>票的分布有两种：forced / portal / ender_pearl 等为稀疏票，只落在一个区块上；player_loading
 * 则逐区块铺设，视距内每格一张。稀疏票靠等级传播覆盖周围：从持票区块向外传播值逐格 +1（传播值 =
 * 票自身等级 + 到锚点的距离），某邻居的实际等级恰等于本票的传播值时，本票即为覆盖它的（并列）最强
 * 来源，继续往外传播。逐区块铺设的票每格自身即为持票区块，扩散退化为原地不动，结果仍正确。
 *
 * <p>扩散判据必须拿「本票传播值」与实际等级比较，不能拿实际等级自身逐格 +1：实际等级是全区所有票
 * 取最小后的结果，若本票被更强的票压住（例如传送门落在玩家模拟区内），实际等级中已看不出本票，照
 * 它 +1 会沿强票的等级梯度一路扩散，将强票的半片区域误认成本票的覆盖区。本类不自算等级，一律读取
 * 传播器给出的实际等级比对。
 *
 * <p>加载链与模拟链是两条独立的传播链，须各推一遍（玩家票自 1.21.8 起拆为 player_loading 与
 * player_simulation，同一区块上两条链的源头可能不同）。
 *
 * <p>结果按 {@link TicketCode} 的线格式编码：源头偏移、类型与存疑位打包进一个 int。
 */
public final class TicketSources {
	private TicketSources() {
	}

	/**
	 * 算出该维度、该条链上每个区块的来源。
	 *
	 * @param simulation false 走加载链，true 走模拟链
	 * @return 区块坐标 → 编码后的来源；该链一条票都没覆盖到的区块不在表里，由调用方判为存疑
	 */
	public static Long2IntOpenHashMap resolve(ServerLevel level, boolean simulation) {
		//? if <1.21.5 {
		/*// 1.21.4 及以前的票体系与 1.21.5+ 不同（票表在 DistanceManager，无 doesLoad/doesSimulate，
		// 类型集合也不同），加载票来源暂不支持：返回空表，客户端只显示等级、不显示来源。
		return new Long2IntOpenHashMap();
		*///?} else {
		ChunkMap chunkMap = level.getChunkSource().chunkMap;
		ChunkMapAccessor accessor = (ChunkMapAccessor) (Object) chunkMap;
		TicketStorage storage = accessor.getTicketStorage();
		DistanceManager distanceManager = chunkMap.getDistanceManager();
		return resolve(storage::getTickets, key -> distanceManager.getChunkLevel(key, simulation),
				accessor.getUpdatingChunks().keySet(), simulation, Ids.id(level.dimension()));
		//?}
	}

	//? if >=1.21.5 {
	/**
	 * BFS 本体：数据源由调用方给出，采集与算法分离，离线测试方可在无游戏环境的 JVM 中直连
	 * （真票表与真 {@code SimulationChunkTracker}，见 tickettest）。
	 *
	 * @param ticketsAt  区块 → 挂在该区块上的全部票
	 * @param levelAt    区块 → 该链上传播后的实际等级
	 * @param keys       可能要认作持票区块的区块集合（生产中即 chunkMap 的更新中区块表）
	 * @param simulation false 走加载链，true 走模拟链
	 * @param dimension  日志用的维度名
	 */
	static Long2IntOpenHashMap resolve(LongFunction<List<Ticket>> ticketsAt, LongToIntFunction levelAt,
			LongSet keys, boolean simulation, String dimension) {
		Long2IntOpenHashMap sources = new Long2IntOpenHashMap();
		// BFS 队列用四个并行原始数组（免去每个元素一个 long[]），传播值随元素携带，出队后无需再查
		// 实际等级。BFS 逐层扩展，首次到达某区块的锚点必是覆盖它的（并列）最近的一个。
		// 容量按全量区块数取，扩散阶段不够再翻倍。
		int capacity = Math.max(16, keys.size());
		long[] queueKeys = new long[capacity];
		int[] queueSpread = new int[capacity];
		int[] queueAnchorXs = new int[capacity];
		int[] queueAnchorZs = new int[capacity];
		int queueHead = 0;
		int queueTail = 0;

		// 一、持票区块：持有本链的票，且等级落在快照范围内。等级更高的票扩散后只会更高，
		//     没有区块会采信，直接跳过。遍历全量区块表而非可见表：视距外的加载点（远程 forceload、
		//     传送门）也需认出来，否则那片热力图找不到中心。
		int anchors = 0;
		StringBuilder sample = new StringBuilder();
		for (LongIterator it = keys.iterator(); it.hasNext(); ) {
			long key = it.nextLong();
			int[] anchor = anchorAt(ticketsAt.apply(key), simulation);
			if (anchor == null || !ChunkLevel.isBlockTicking(anchor[1])) {
				continue;
			}
			int x = ChunkPos.getX(key);
			int z = ChunkPos.getZ(key);
			sources.put(key, TicketCode.encode(anchor[0], 0, 0, false));
			// 传播值从票自身的等级起算（而非该区块的实际等级）：被更强票压住的锚点，实际等级中已
			// 看不出本票，往外第一步就会与邻居的实际等级对不上而停下（见类注释）
			queueKeys[queueTail] = key;
			queueSpread[queueTail] = anchor[1];
			queueAnchorXs[queueTail] = x;
			queueAnchorZs[queueTail] = z;
			queueTail++;
			if (anchors < 5) {
				sample.append(sample.isEmpty() ? "" : "、").append('(').append(x).append(',').append(z)
						.append(") 票").append(anchor[0]).append(" 等级").append(anchor[1]);
			}
			anchors++;
		}

		// 二、按 8 邻扩散（与 ChunkTracker 的方向一致），只往实际等级恰好等于本票传播值的邻居走：
		//     等级小于传播值说明被更强的票压住，本票不再覆盖它；实际等级必 ≤ 本票传播值，
		//     不会出现大于的情形。
		while (queueHead < queueTail) {
			long key = queueKeys[queueHead];
			int spread = queueSpread[queueHead];
			int anchorX = queueAnchorXs[queueHead];
			int anchorZ = queueAnchorZs[queueHead];
			queueHead++;
			int type = TicketCode.type(sources.get(key));
			int x = ChunkPos.getX(key);
			int z = ChunkPos.getZ(key);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					long neighbor = ChunkKeys.pack(x + dx, z + dz);
					if (sources.containsKey(neighbor)) {
						continue;
					}
					int neighborLevel = levelAt.applyAsInt(neighbor);
					if (neighborLevel != spread + 1 || !ChunkLevel.isBlockTicking(neighborLevel)) {
						continue;
					}
					// 偏移记录的是「源头相对本区块」，客户端以「本区块 + 偏移」还原为源头坐标
					sources.put(neighbor, TicketCode.encode(type, anchorX - (x + dx), anchorZ - (z + dz), false));
					// 扩容只在此处（锚点阶段的入队数不超过初始容量）：四个数组一同翻倍
					if (queueTail == queueKeys.length) {
						int grown = queueTail * 2;
						queueKeys = Arrays.copyOf(queueKeys, grown);
						queueSpread = Arrays.copyOf(queueSpread, grown);
						queueAnchorXs = Arrays.copyOf(queueAnchorXs, grown);
						queueAnchorZs = Arrays.copyOf(queueAnchorZs, grown);
					}
					queueKeys[queueTail] = neighbor;
					queueSpread[queueTail] = spread + 1;
					queueAnchorXs[queueTail] = anchorX;
					queueAnchorZs[queueTail] = anchorZ;
					queueTail++;
				}
			}
		}

		// 诊断：持票区块数应为「玩家数 + forceload + 传送门/珍珠」之和，覆盖数应接近快照中的区块数
		// （淡灰的弱加载区块也在覆盖内）。两者对不上即说明正推有误。默认级别不打印（每次扫描每维度
		// 一条，生产服上过于吵闹），排查时开到 debug。
		MsptMapMod.LOGGER.debug("维度 {} 加载票（{}）：持票区块 {} 个、覆盖 {} 个区块。前几个持票区块：{}",
				dimension, simulation ? "模拟链" : "加载链", anchors, sources.size(),
				sample.isEmpty() ? "（无）" : sample);
		return sources;
	}
	//?}


	//? if >=1.21.5 {
	/**
	 * 该区块在本链上的持票信息：{类型序号, 票等级}，无票返回 null。
	 *
	 * 一个区块可能同时挂着多张票：先比等级，取最低的那张（与
	 * {@code TicketStorage.getTicketLevelAt} 的取法一致，由其决定该区块的状态）；等级相同时按
	 * {@link TicketCode#priority} 取舍（等级相同的票无强弱之分，但显示哪一张对查看者更有用）。
	 */
	private static int[] anchorAt(List<Ticket> tickets, boolean simulation) {
		if (tickets == null) {
			return null;
		}
		int bestLevel = Integer.MAX_VALUE;
		int bestType = TicketCode.NONE;
		for (Ticket ticket : tickets) {
			if (!belongsTo(ticket.getType(), simulation)) {
				continue;
			}
			int level = ticket.getTicketLevel();
			int type = indexOf(ticket.getType());
			boolean better = level < bestLevel
					|| (level == bestLevel && TicketCode.priority(type) < TicketCode.priority(bestType));
			if (better) {
				bestLevel = level;
				bestType = type;
			}
		}
		return bestType == TicketCode.NONE ? null : new int[] {bestType, bestLevel};
	}
	//?}

	//? if >=1.21.5 {
	/** 这张票参不参与本条链：加载链看 doesLoad，模拟链看 doesSimulate。 */
	private static boolean belongsTo(TicketType type, boolean simulation) {
		return simulation ? type.doesSimulate() : type.doesLoad();
	}

	/**
	 * 票类型身份 → 协议序号的缓存：{@link #indexOf} 需走注册表反查加字符串匹配，而票类型是单例
	 * （枚举 / 注册表对象），同一张票只应计算一次。用身份比较即可；算出的序号不随注册表变化，跨次
	 * 扫描也有效。采样在服务端主线程上收尾，为单线程访问。
	 */
	private static final Map<TicketType, Integer> TYPE_CACHE = new IdentityHashMap<>();

	/** 票类型 → 协议序号（先查 {@link #TYPE_CACHE}，未命中才算）。 */
	private static int indexOf(TicketType type) {
		Integer cached = TYPE_CACHE.get(type);
		if (cached != null) {
			return cached;
		}
		int index = computeIndexOf(type);
		TYPE_CACHE.put(type, index);
		return index;
	}

	/**
	 * 票类型 → 协议序号。
	 *
	 * 识别依据为注册表中的名字，而非 {@code equals}：{@code TicketType} 是 record，相等性只看字段值，
	 * 而不同票的字段值可能完全相同（如 1.21.8 的 {@code start} 与 {@code dragon}），用 equals 会把
	 * 它们认成同一张票。名字才是唯一的。
	 */
	private static int computeIndexOf(TicketType type) {
		String path = Ids.path(BuiltInRegistries.TICKET_TYPE, type);
		if (path == null) {
			return TicketCode.UNRECOGNIZED;
		}
		return switch (path) {
			case "player_loading" -> TicketCode.PLAYER_LOADING;
			case "player_simulation" -> TicketCode.PLAYER_SIMULATION;
			case "forced" -> TicketCode.FORCED;
			case "portal" -> TicketCode.PORTAL;
			case "ender_pearl" -> TicketCode.ENDER_PEARL;
			// 出生点票：1.21.10 及以前叫 start，1.21.11 拆成 player_spawn + spawn_search
			case "start" -> TicketCode.PLAYER_SPAWN;
			case "player_spawn" -> TicketCode.PLAYER_SPAWN;
			case "spawn_search" -> TicketCode.SPAWN_SEARCH;
			case "dragon" -> TicketCode.DRAGON;
			case "unknown" -> TicketCode.UNKNOWN;
			default -> TicketCode.UNRECOGNIZED;
		};
	}
	//?}
}
