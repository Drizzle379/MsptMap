package msptmap.client;

/**
 * 待定位目标的暂存：聊天告警里点击区块行走客户端命令写入，世界地图的帧循环取出后交给地图的定位逻辑
 * （与点击总览里的行同一套）。
 *
 * <p>之所以要中转：写入方是命令（地图可能尚未打开），取用方是地图界面的帧循环，两条路径不共享实例。
 */
public final class MapFocus {
	/** 目标形状与总览的可定位行共用（维度 ID + 区块坐标）。 */
	private static ScanSummary.Target pending;

	private MapFocus() {
	}

	/** 记下一个待定位目标，覆盖上一个（后点的为准）。 */
	public static void request(String dimension, int chunkX, int chunkZ) {
		pending = new ScanSummary.Target(dimension, chunkX, chunkZ);
	}

	/** 是否有待定位目标（只看不动）。 */
	public static boolean awaiting() {
		return pending != null;
	}

	/** 取出待定位目标并清除；没有则为 null。 */
	public static ScanSummary.Target consume() {
		ScanSummary.Target target = pending;
		pending = null;
		return target;
	}

	/** 丢弃待定位目标（退出世界：目标属于上一局的地图）。 */
	public static void clear() {
		pending = null;
	}
}
