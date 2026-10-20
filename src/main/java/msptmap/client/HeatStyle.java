package msptmap.client;

import msptmap.util.HeatColors;

/**
 * 世界地图与小地图共用的热力配色与弱加载判定。
 *
 * <p>两份绘制入口（{@link MapOverlay} / {@link MinimapOverlay}）只保留各自的坐标变换与填充 API，
 * 颜色、阈值与“哪些区块铺淡灰”的规则集中在这里，避免两处漂移。
 */
final class HeatStyle {
	/** 达到该加载等级即弱加载（与 {@code ChunkLevel.isBlockTicking} 的分界一致）。 */
	static final int WEAK_LOAD_LEVEL = 32;

	/** 弱加载区块的填充色与透明度。 */
	static final float IDLE_GRAY = 0.7f;
	static final float IDLE_ALPHA = 0.3f;

	/** 加载源中心蓝框的颜色（不透明，与绿黄红热力色区分）。 */
	static final float CENTER_RED = 0.2f;
	static final float CENTER_GREEN = 0.5f;
	static final float CENTER_BLUE = 1.0f;

	/** 蓝框宽度（区块内沿一圈，压住最外圈的热力色）。 */
	static final int CENTER_BORDER = 1;

	private HeatStyle() {
	}

	/** 弱加载且未勾选「计时时染色」时铺淡灰；其余区块按热力色绘制。 */
	static boolean weakGray(ClientSnapshot.Chunk chunk) {
		return ClientConfig.showWeakGray && chunk.loadLevel() >= WEAK_LOAD_LEVEL
				&& !(chunk.timed() && ClientConfig.weakTimedColor);
	}

	/** 当前帧红点：相对模式取本维度最重值，否则取红色阈值（规则见 {@link HeatColors#redPoint}）。 */
	static float redPoint(float heaviest) {
		return HeatColors.redPoint(ClientConfig.relativeColor, (float) ClientConfig.redAt, heaviest);
	}
}
