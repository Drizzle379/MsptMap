package msptmap.sampler;

/**
 * 加载票来源的线格式：类型序号、源头偏移与存疑位打包进一个 int，附带同级票的取舍顺序。
 *
 * <p>位布局：0~3 位类型，4~10 位与 11~17 位是源头相对本区块的偏移（偏置见下），18 位存疑。偏移记录
 * 的是「源头相对本区块」而非距离：客户端需显示 {@code @x,z}，且偏移量很小（加载范围 33 格以内）。
 *
 * <p>产出方为 {@link TicketSources} 的 BFS 反查，消费方是快照编码与客户端解包。
 */
public final class TicketCode {
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

	private TicketCode() {
	}

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

	/**
	 * 该编码是否指向本区块上的票，即本区块是某张票的中心。
	 *
	 * <p>编码只有两处产出：持票区块（偏移恒为 0）与扩散覆盖区（偏移恒非 0），故「有来源且偏移为 0」
	 * 即票就在本区块上。player_loading 除外：它逐区块铺设、视距内每格偏移均为 0，不存在中心可言——
	 * 玩家位置由模拟链上的 player_simulation 代表。无来源与存疑的编码不会命中。
	 *
	 * <p>客户端绘制中心蓝框、显示「…中心」，以及服务端将中心补进快照，均以此为准。
	 */
	public static boolean isCenter(int code) {
		int type = type(code);
		return type != NONE && type != PLAYER_LOADING
				&& offsetX(code) == 0 && offsetZ(code) == 0;
	}

	/**
	 * 等级相同时的取舍顺序：数值越小越优先。
	 *
	 * <p>将两张玩家票排在最后是有意为之：{@code player_loading} 覆盖视距内每一格，几乎总与别的票同时在场
	 * （例如脚下的 forceload 区块），若让它优先，其余来源将永远无法显示。其余按「越具体越优先」排列：
	 * 主动标记（forceload）＞一次性成因（珍珠、传送门）＞世界结构（末地主岛、出生点）。
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
}
