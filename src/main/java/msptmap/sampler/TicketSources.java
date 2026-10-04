package msptmap.sampler;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import msptmap.ChunkKeys;
import msptmap.Ids;
import msptmap.MsptMapMod;
import msptmap.mixins.ChunkMapAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ChunkHolder;
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

/**
 * 反查每个区块的加载来源：即该区块被哪张加载票覆盖。
 *
 * <p>票的分布有两种：forced / portal / ender_pearl 等是稀疏的，只落在一个区块上；26.2 的
 * player_loading 则是逐区块铺的，视距内每格一张。稀疏票靠等级传播覆盖周围——{@code ChunkTracker}
 * 保证邻居等级 = 本区块等级 + 1，故从持票区块沿「等级恰好 +1」的邻居遍历即可认回整片覆盖区。
 * 本类不自算等级，一律读 {@code getChunkLevel}，与游戏实际结果必然一致。逐区块铺的票则每格自身
 * 即为持票区块，扩散退化为原地不动，结果仍正确。
 *
 * <p>加载链与模拟链是两条独立的传播链，须各推一遍（玩家票在 26.2 拆为 player_loading 与
 * player_simulation，同一区块上两条链的源头可能不同）。
 *
 * <p>结果记录源头坐标相对本区块的偏移而非距离：客户端要显示 {@code @x,z}，且偏移量很小
 * （加载范围 33 格以内），可与类型、存疑位一同编入一个 int。
 */
public final class TicketSources {
	/** 该链没有来源。 */
	public static final int NONE = 0;

	public static final int PLAYER_LOADING = 1;
	public static final int PLAYER_SIMULATION = 2;
	public static final int FORCED = 3;
	public static final int PORTAL = 4;
	public static final int ENDER_PEARL = 5;
	public static final int PLAYER_SPAWN = 6;
	public static final int SPAWN_SEARCH = 7;
	public static final int DRAGON = 8;
	/** 原版的 unknown 票。 */
	public static final int UNKNOWN = 9;
	/** 本模组不认识的类型（原版新增票种时兜底，不解析崩溃）。 */
	public static final int UNRECOGNIZED = 10;

	private static final int TYPE_MASK = 0xF;
	/** 两个偏移各占 7 位（-64 ~ 63），足以覆盖 33 格的加载半径。 */
	private static final int OFFSET_BIAS = 64;
	private static final int OFFSET_MASK = 0x7F;
	private static final int DX_SHIFT = 4;
	private static final int DZ_SHIFT = 11;
	private static final int DOUBTFUL_BIT = 1 << 18;

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

		Long2IntOpenHashMap sources = new Long2IntOpenHashMap();
		// BFS 队列用四个并行原始数组（免去每元素一个 long[]），等级随元素携带，出队后无需再查
		// getChunkLevel。BFS 逐层访问，故首次到达某区块时的源头必是覆盖它的锚点中最近的一个。
		// 容量按可见区块数取，扩散阶段不够再翻倍。
		int capacity = Math.max(16, accessor.getVisibleChunks().size());
		long[] queueKeys = new long[capacity];
		int[] queueLevels = new int[capacity];
		int[] queueAnchorXs = new int[capacity];
		int[] queueAnchorZs = new int[capacity];
		int queueHead = 0;
		int queueTail = 0;

		// 一、持票区块：持有本链的票、且等级落在快照范围内的。等级更高的票扩散后只会更高，
		//     没有区块会采信，直接跳过。
		int anchors = 0;
		StringBuilder sample = new StringBuilder();
		for (Long2ObjectMap.Entry<ChunkHolder> entry : accessor.getVisibleChunks().long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			int[] anchor = anchorAt(storage.getTickets(key), simulation);
			if (anchor == null || !ChunkLevel.isBlockTicking(anchor[1])) {
				continue;
			}
			int x = ChunkPos.getX(key);
			int z = ChunkPos.getZ(key);
			sources.put(key, encode(anchor[0], 0, 0, false));
			// 等级取 getChunkLevel 而非票自身的等级：邻近若有等级更低的票，此处的实际等级会被它压低，
			// 扩散判定必须用实际等级。入队时查与出队时查等价（同一趟里票表不变）
			queueKeys[queueTail] = key;
			queueLevels[queueTail] = distanceManager.getChunkLevel(key, simulation);
			queueAnchorXs[queueTail] = x;
			queueAnchorZs[queueTail] = z;
			queueTail++;
			if (anchors < 5) {
				sample.append(sample.isEmpty() ? "" : "、").append('(').append(x).append(',').append(z)
						.append(") 票").append(anchor[0]).append(" 等级").append(anchor[1]);
			}
			anchors++;
		}

		// 二、按 8 邻扩散（与 ChunkTracker 的方向一致），只往等级恰好 +1 的邻居走。
		while (queueHead < queueTail) {
			long key = queueKeys[queueHead];
			int hereLevel = queueLevels[queueHead];
			int anchorX = queueAnchorXs[queueHead];
			int anchorZ = queueAnchorZs[queueHead];
			queueHead++;
			int type = type(sources.get(key));
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
					int neighborLevel = distanceManager.getChunkLevel(neighbor, simulation);
					if (neighborLevel != hereLevel + 1 || !ChunkLevel.isBlockTicking(neighborLevel)) {
						continue;
					}
					// 偏移记的是「源头相对本区块」，客户端用「本区块 + 偏移」还原为源头坐标
					sources.put(neighbor, encode(type, anchorX - (x + dx), anchorZ - (z + dz), false));
					// 扩容只在此处（锚点阶段的入队数不超过初始容量）：四个数组一同翻倍
					if (queueTail == queueKeys.length) {
						int grown = queueTail * 2;
						queueKeys = Arrays.copyOf(queueKeys, grown);
						queueLevels = Arrays.copyOf(queueLevels, grown);
						queueAnchorXs = Arrays.copyOf(queueAnchorXs, grown);
						queueAnchorZs = Arrays.copyOf(queueAnchorZs, grown);
					}
					queueKeys[queueTail] = neighbor;
					queueLevels[queueTail] = neighborLevel;
					queueAnchorXs[queueTail] = anchorX;
					queueAnchorZs[queueTail] = anchorZ;
					queueTail++;
				}
			}
		}

		// 诊断：持票区块数应为「玩家数 + forceload + 传送门/珍珠」之和，覆盖数应接近快照中的区块数
		// （淡灰的弱加载区块也在覆盖内）。两者对不上即说明正推有误。默认级别不打印（每次扫描每维度
		// 一条，生产服上过吵），排查时开到 debug。
		MsptMapMod.LOGGER.debug("维度 {} 加载票（{}）：持票区块 {} 个、覆盖 {} 个区块。前几个持票区块：{}",
				Ids.id(level.dimension()), simulation ? "模拟链" : "加载链", anchors, sources.size(),
				sample.isEmpty() ? "（无）" : sample);
		return sources;
		//?}
	}

	//? if >=1.21.5 {
	/**
	 * 该区块在本链上的持票信息：{类型序号, 票等级}，无票返回 null。
	 *
	 * 一个区块可能同时挂着多张票：先比等级，取最低的那张（与
	 * {@code TicketStorage.getTicketLevelAt} 的取法一致，它才决定该区块的状态）；等级相同时按
	 * {@link #priority} 取舍（等级相同的票无强弱之分，但显示哪一张对查看者更有用）。
	 */
	private static int[] anchorAt(List<Ticket> tickets, boolean simulation) {
		if (tickets == null) {
			return null;
		}
		int bestLevel = Integer.MAX_VALUE;
		int bestType = NONE;
		for (Ticket ticket : tickets) {
			if (!belongsTo(ticket.getType(), simulation)) {
				continue;
			}
			int level = ticket.getTicketLevel();
			int type = indexOf(ticket.getType());
			boolean better = level < bestLevel
					|| (level == bestLevel && priority(type) < priority(bestType));
			if (better) {
				bestLevel = level;
				bestType = type;
			}
		}
		return bestType == NONE ? null : new int[] {bestType, bestLevel};
	}
	//?}

	/**
	 * 等级相同时的取舍顺序：数值越小越优先。
	 *
	 * 把两张玩家票排在最后是有意的：{@code player_loading} 覆盖视距内每一格，几乎总与别的票同时在场
	 * （比如脚下的 forceload 区块），若让它优先，其余来源将永远无法显示。其余按「越具体越优先」排：
	 * 主动标记的（forceload）＞一次性成因（珍珠、传送门）＞世界结构（末地主岛、出生点）。
	 */
	public static int priority(int type) {
		return switch (type) {
			case FORCED -> 0;
			case ENDER_PEARL -> 1;
			case PORTAL -> 2;
			case DRAGON -> 3;
			case PLAYER_SPAWN -> 4;
			case SPAWN_SEARCH -> 5;
			case PLAYER_SIMULATION -> 6;
			case PLAYER_LOADING -> 7;
			default -> 8;
		};
	}

	//? if >=1.21.5 {
	/** 这张票参不参与本条链：加载链看 doesLoad，模拟链看 doesSimulate。 */
	private static boolean belongsTo(TicketType type, boolean simulation) {
		return simulation ? type.doesSimulate() : type.doesLoad();
	}

	/**
	 * 票类型身份 → 协议序号的缓存：{@link #indexOf} 需走注册表反查加字符串匹配，而票类型是单例
	 * （枚举 / 注册表对象），同一张票只该算一次。用身份比较即可；算出的序号不随注册表变化，跨次
	 * 扫描也有效。采样在服务端主线程上收尾，单线程访问。
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
	 * 认的是注册表里的名字，而非 {@code equals}：{@code TicketType} 是 record，相等性只看
	 * (timeout, flags) 两个字段，而 {@code spawn_search} 与 {@code player_loading} 这两项取值完全相同
	 * （都是 0 / 2），用 equals 会把前者认成后者。名字才是唯一的。
	 */
	private static int computeIndexOf(TicketType type) {
		String path = Ids.path(BuiltInRegistries.TICKET_TYPE, type);
		if (path == null) {
			return UNRECOGNIZED;
		}
		return switch (path) {
			case "player_loading" -> PLAYER_LOADING;
			// 1.21.4 及以前玩家票未拆分（该区间票来源已降级，此处仅保底）
			case "player" -> PLAYER_LOADING;
			case "player_simulation" -> PLAYER_SIMULATION;
			case "forced" -> FORCED;
			case "portal" -> PORTAL;
			case "ender_pearl" -> ENDER_PEARL;
			// 出生点票：1.21.10 及以前叫 start，1.21.11 拆成 player_spawn + spawn_search
			case "start" -> PLAYER_SPAWN;
			case "player_spawn" -> PLAYER_SPAWN;
			case "spawn_search" -> SPAWN_SEARCH;
			case "dragon" -> DRAGON;
			case "unknown" -> UNKNOWN;
			default -> UNRECOGNIZED;
		};
	}
	//?}

	/** 编码：0~3 位类型，4~10 位与 11~17 位是源头相对本区块的偏移，18 位存疑。 */
	public static int encode(int type, int offsetX, int offsetZ, boolean doubtful) {
		return (type & TYPE_MASK)
				| (((offsetX + OFFSET_BIAS) & OFFSET_MASK) << DX_SHIFT)
				| (((offsetZ + OFFSET_BIAS) & OFFSET_MASK) << DZ_SHIFT)
				| (doubtful ? DOUBTFUL_BIT : 0);
	}

	public static int type(int code) {
		return code & TYPE_MASK;
	}

	/** 源头相对本区块的 X 偏移（源头坐标 - 本区块坐标）；0 表示票就在本区块上。 */
	public static int offsetX(int code) {
		return ((code >> DX_SHIFT) & OFFSET_MASK) - OFFSET_BIAS;
	}

	public static int offsetZ(int code) {
		return ((code >> DZ_SHIFT) & OFFSET_MASK) - OFFSET_BIAS;
	}

	public static boolean doubtful(int code) {
		return (code & DOUBTFUL_BIT) != 0;
	}
}
