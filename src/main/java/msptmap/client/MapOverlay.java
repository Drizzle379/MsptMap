package msptmap.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import xaero.map.graphics.MapRenderHelper;

/**
 * 往 Xaero 地图上绘制热力图。仅依赖 {@link ClientSnapshot} 与绘制参数，不涉及网络。
 *
 * <p>地图空间即当前维度的方块坐标（1 单位 = 1 格）：区块矩形由快照中的世界坐标减去相机偏移得到，
 * 不做缩放。矩阵为 Xaero 当前帧已带好缩放平移的 PoseStack。
 *
 * <p>填色规则：测到过耗时 → 按 ms/tick 铺绿黄红；弱加载（加载等级 ≥32）且整段窗口无耗时 → 淡灰；
 * 弱加载但测到过耗时 → 照常铺热力色。阈值与透明度每帧从 {@link ClientConfig} 现读。
 */
public final class MapOverlay {
	/** 达到该加载等级即弱加载（与 {@code ChunkLevel.isBlockTicking} 的分界一致）。 */
	private static final int WEAK_LOAD_LEVEL = 32;

	/** 弱加载且无耗时区块的填充色与透明度。 */
	private static final float IDLE_GRAY = 0.7f;
	private static final float IDLE_ALPHA = 0.3f;

	/**
	 * 黄点在红点上的位置（1/3）：颜色只保留红点一个可调参数，黄点由它推出，相对模式下同样跟随本次
	 * 最重的区块。默认红阈值 1.5 时黄点为 0.50。
	 */
	private static final float YELLOW_KNEE = 1.0f / 3.0f;

	private MapOverlay() {
	}

	/**
	 * 绘制一个维度的热力图。参数均为 Xaero 当前帧的现成对象（见 {@code GuiMapMixin} 的注入点）。
	 *
	 * @param dimension 地图当前显示的维度，非玩家所在维度 —— 切到地狱地图即绘制地狱的数据
	 */
	public static void draw(Matrix4f matrix, VertexConsumer buffer, int flooredCameraX, int flooredCameraZ,
			String dimension) {
		ClientSnapshot.Chunk[] chunks = ClientSnapshot.get(dimension);
		if (chunks == null) {
			return;
		}

		// 红点整帧只算一次
		float redPoint = redPoint(ClientSnapshot.heaviestMspt(dimension));
		for (ClientSnapshot.Chunk chunk : chunks) {
			int x1 = chunk.x1() - flooredCameraX;
			int z1 = chunk.z1() - flooredCameraZ;
			int x2 = chunk.x2() - flooredCameraX;
			int z2 = chunk.z2() - flooredCameraZ;

			if (ClientConfig.showWeakGray && chunk.loadLevel() >= WEAK_LOAD_LEVEL && !chunk.timed()) {
				MapRenderHelper.fillIntoExistingBuffer(matrix, buffer, x1, z1, x2, z2,
						IDLE_GRAY, IDLE_GRAY, IDLE_GRAY, IDLE_ALPHA);
				continue;
			}
			float mspt = chunk.mspt();
			MapRenderHelper.fillIntoExistingBuffer(matrix, buffer, x1, z1, x2, z2,
					red(mspt, redPoint), green(mspt, redPoint), 0.0f, (float) ClientConfig.fillAlpha);
		}
	}

	/**
	 * 当前帧的红点：耗时达到它即为最高等级的红。
	 *
	 * <p>固定模式（默认）取设置中的红色阈值，跨次可比；相对模式取当前显示维度、本次快照中最重的区块
	 * （最重值由 {@link ClientSnapshot#accept} 预计算，见 {@link ClientSnapshot#heaviestMspt}）。
	 * 无任何耗时测得时退回固定阈值，保证结果恒大于 0，可作分母。
	 */
	static float redPoint(float heaviest) {
		if (!ClientConfig.relativeColor) {
			return (float) ClientConfig.redAt;
		}
		return heaviest > 0.0f ? heaviest : (float) ClientConfig.redAt;
	}

	/** 黄点 = 红点 × {@link #YELLOW_KNEE}；红点恒大于 0，可作分母。 */
	static float yellowAt(float redPoint) {
		return redPoint * YELLOW_KNEE;
	}

	/** 红色分量：黄点以下为绿→黄斜坡，以上为黄→红斜坡。 */
	static float red(float mspt, float redPoint) {
		float knee = yellowAt(redPoint);
		return mspt <= knee ? mspt / knee : 1.0f;
	}

	/** 绿色分量：与 {@link #red} 配成同一条斜坡（0 → 绿，黄点 → 黄，≥红点 → 红）。 */
	static float green(float mspt, float redPoint) {
		float knee = yellowAt(redPoint);
		return mspt <= knee ? 1.0f : 1.0f - Math.min(1.0f, (mspt - knee) / (redPoint - knee));
	}
}
