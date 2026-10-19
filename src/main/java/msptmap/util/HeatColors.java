package msptmap.util;

/**
 * 热力色的纯计算：地图填色（{@code MapOverlay}）、扫描总览与聊天告警的数值着色共用这一套斜坡，
 * 三处的颜色因此总是一致。
 *
 * <p>斜坡自绿经黄至红，黄点在红点的 {@link #YELLOW_KNEE} 处；红点由调用方给：地图按本次扫描
 * 的最重区块或红色阈值，告警按单区块的默认红阈值。不依赖游戏类，服务端同样可用。
 */
public final class HeatColors {
	/** 黄点 = 红点 × 该系数。 */
	private static final float YELLOW_KNEE = 1.0f / 3.0f;

	/** 单区块红点的默认值（ms/tick）：客户端的地图、总览与服务端的聊天告警共用，三处默认配色一致。 */
	public static final float DEFAULT_RED_POINT = 1.5f;

	private HeatColors() {
	}

	/**
	 * 当前红点：固定模式取 {@code redAt}，相对模式取本次最重值；无耗时测得时退回 {@code redAt}。
	 * 地图与小地图共用这一条规则。
	 */
	public static float redPoint(boolean relative, float redAt, float heaviest) {
		return relative && heaviest > 0.0f ? heaviest : redAt;
	}

	/** 黄点：红点 × {@link #YELLOW_KNEE}；红点恒大于 0，可作分母。 */
	public static float yellowAt(float redPoint) {
		return redPoint * YELLOW_KNEE;
	}

	/** 红色分量（0 ~ 1）：黄点以下为绿→黄斜坡，以上为黄→红斜坡。 */
	public static float red(float mspt, float redPoint) {
		float knee = yellowAt(redPoint);
		return mspt <= knee ? mspt / knee : 1.0f;
	}

	/** 绿色分量（0 ~ 1）：与 {@link #red} 配成同一条斜坡（0 → 绿，黄点 → 黄，≥红点 → 红）。 */
	public static float green(float mspt, float redPoint) {
		float knee = yellowAt(redPoint);
		return mspt <= knee ? 1.0f : 1.0f - Math.min(1.0f, (mspt - knee) / (redPoint - knee));
	}

	/** 同一条斜坡的 0xRRGGBB（文字着色用），蓝分量恒为 0。 */
	public static int rgb(float mspt, float redPoint) {
		return Math.round(red(mspt, redPoint) * 255.0f) << 16
				| Math.round(green(mspt, redPoint) * 255.0f) << 8;
	}

	/** 文字档柔化：每通道 ×0xD0/0xFF（比灰字档 0xB0 亮约 18%），色相不变；方块填色不用。 */
	public static int soften(int rgb) {
		return ((rgb >> 16 & 0xFF) * 0xD0 / 0xFF) << 16 | ((rgb >> 8 & 0xFF) * 0xD0 / 0xFF) << 8;
	}
}
