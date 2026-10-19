package msptmap.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import msptmap.sampler.TicketCode;
import msptmap.util.HeatColors;
import org.joml.Matrix4f;
import xaero.hud.render.util.RenderBufferUtil;

/**
 * 往 Xaero 小地图的 FBO 上叠加热力图。仅依赖 {@link ClientSnapshot} 与绘制参数，不涉及网络。
 *
 * <p>FBO 中 1 方块 = 1 纹理像素，玩家位于纹理原点；区块矩形由世界坐标减去玩家 floor 坐标得到，
 * 不做缩放。旋转与圆形遮罩由 Xaero 在其后贴图时完成，故此处无须处理。
 *
 * <p>填色规则与世界地图一致；加载源中心的蓝框宽 1 方块，小地图缩小时随之变细。
 */
public final class MinimapOverlay {
	/** 达到该加载等级即弱加载（与 {@code ChunkLevel.isBlockTicking} 的分界一致）。 */
	private static final int WEAK_LOAD_LEVEL = 32;

	/** 弱加载区块的填充色与透明度。 */
	private static final float IDLE_GRAY = 0.7f;
	private static final float IDLE_ALPHA = 0.3f;

	/** 加载源中心蓝框的颜色（不透明，与绿黄红热力色区分）。 */
	private static final float CENTER_RED = 0.2f;
	private static final float CENTER_GREEN = 0.5f;
	private static final float CENTER_BLUE = 1.0f;

	/** 蓝框宽度（方块）：小地图缩放时随之变细。 */
	private static final int CENTER_BORDER = 1;

	/** 视野粗筛半径（方块）：以玩家为中心，覆盖小地图的最小缩放。 */
	private static final int RANGE = 1024;

	private MinimapOverlay() {
	}

	/**
	 * 绘制当前维度在玩家附近的热力图。
	 *
	 * @param playerX 玩家所在方块的 x（floor）
	 * @param playerZ 玩家所在方块的 z（floor）
	 * @param dimension 小地图当前显示的维度
	 */
	public static void draw(Matrix4f matrix, VertexConsumer buffer, int playerX, int playerZ, String dimension) {
		if (!ClientConfig.minimapHeatmap) {
			return;
		}
		ClientSnapshot.Chunk[] chunks = ClientSnapshot.get(dimension);
		if (chunks == null) {
			return;
		}

		// 红点整帧只算一次
		float redPoint = redPoint(ClientSnapshot.heaviestMspt(dimension));
		for (ClientSnapshot.Chunk chunk : chunks) {
			int x1 = chunk.x1() - playerX;
			int z1 = chunk.z1() - playerZ;
			int x2 = chunk.x2() - playerX;
			int z2 = chunk.z2() - playerZ;
			if (outsideRange(x1, z1, x2, z2)) {
				continue;
			}

			if (ClientConfig.showWeakGray && chunk.loadLevel() >= WEAK_LOAD_LEVEL
					&& !(chunk.timed() && ClientConfig.weakTimedColor)) {
				fill(matrix, buffer, x1, z1, x2, z2, IDLE_GRAY, IDLE_GRAY, IDLE_GRAY, IDLE_ALPHA);
				continue;
			}
			float mspt = chunk.mspt();
			fill(matrix, buffer, x1, z1, x2, z2,
					HeatColors.red(mspt, redPoint), HeatColors.green(mspt, redPoint), 0.0f,
					(float) ClientConfig.fillAlpha);
		}

		// 加载源中心画蓝框：与世界地图同源（见 MapOverlay），每条边宽 1 方块
		for (ClientSnapshot.Chunk chunk : chunks) {
			if (!TicketCode.isCenter(chunk.loadTicket()) && !TicketCode.isCenter(chunk.simTicket())) {
				continue;
			}
			int x1 = chunk.x1() - playerX;
			int z1 = chunk.z1() - playerZ;
			int x2 = chunk.x2() - playerX;
			int z2 = chunk.z2() - playerZ;
			if (outsideRange(x1, z1, x2, z2)) {
				continue;
			}
			centerBorder(matrix, buffer, x1, z1, x2, z2);
		}
	}

	/** 矩形完全落在粗筛范围外时跳过，避免把远处区块的顶点写进缓冲。 */
	private static boolean outsideRange(int x1, int z1, int x2, int z2) {
		return x2 < -RANGE || x1 > RANGE || z2 < -RANGE || z1 > RANGE;
	}

	/** 沿区块内沿画一圈蓝框：上下两条横边跨满，左右两条竖边接在两横边之间。 */
	private static void centerBorder(Matrix4f matrix, VertexConsumer buffer, int x1, int z1, int x2, int z2) {
		fill(matrix, buffer, x1, z1, x2, z1 + CENTER_BORDER,
				CENTER_RED, CENTER_GREEN, CENTER_BLUE, 1.0f);
		fill(matrix, buffer, x1, z2 - CENTER_BORDER, x2, z2,
				CENTER_RED, CENTER_GREEN, CENTER_BLUE, 1.0f);
		fill(matrix, buffer, x1, z1 + CENTER_BORDER, x1 + CENTER_BORDER, z2 - CENTER_BORDER,
				CENTER_RED, CENTER_GREEN, CENTER_BLUE, 1.0f);
		fill(matrix, buffer, x2 - CENTER_BORDER, z1 + CENTER_BORDER, x2, z2 - CENTER_BORDER,
				CENTER_RED, CENTER_GREEN, CENTER_BLUE, 1.0f);
	}

	/** 一个带顶点色的矩形（世界方块坐标已转为相对坐标）。 */
	private static void fill(Matrix4f matrix, VertexConsumer buffer, int x1, int z1, int x2, int z2,
			float red, float green, float blue, float alpha) {
		RenderBufferUtil.addColoredRect(matrix, buffer, x1, z1, x2 - x1, z2 - z1, red, green, blue, alpha);
	}

	/** 当前帧的红点：与世界地图同一条规则（见 {@link HeatColors#redPoint}）。 */
	private static float redPoint(float heaviest) {
		return HeatColors.redPoint(ClientConfig.relativeColor, (float) ClientConfig.redAt, heaviest);
	}
}
