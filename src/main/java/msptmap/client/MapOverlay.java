package msptmap.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import xaero.map.graphics.MapRenderHelper;

/**
 * 往 Xaero 地图上绘制热力图。只依赖 {@link ClientSnapshot} 与几个绘制参数，不涉及网络。
 *
 * 坐标：地图空间即当前显示的维度的方块坐标（1 单位 = 1 格），区块矩形直接用快照中的世界坐标减去
 * 相机偏移，不做任何缩放：Xaero 那个只作用于玩家标记的系数不能用于换算地图内容。
 * 矩阵是 Xaero 那一帧已带好缩放平移的 PoseStack。
 *
 * 一个区块一块填色，无边框：
 * - 测到过耗时 → 按 ms/tick 铺绿黄红（红点由 {@link #redPoint} 定，黄点为它的三分之一）；
 * - 弱加载（加载等级 ≥32）且整段窗口无耗时 → 淡灰；
 * - 弱加载但测到过耗时 → 照常铺热力色。
 *
 * 阈值、透明度、灰底开关均来自 {@link ClientConfig}，每帧现读（静态字段，读取即一次访存）。
 */
public final class MapOverlay {
	/** 达到该加载等级即弱加载（ChunkLevel.isBlockTicking 的界线为 32）。 */
	private static final int WEAK_LOAD_LEVEL = 32;

	/** 弱加载且无耗时的区块铺的淡灰。 */
	private static final float IDLE_GRAY = 0.7f;
	private static final float IDLE_ALPHA = 0.3f;

	/**
	 * 黄点在红点上的位置（1/3）：颜色只留红点一个旋钮，黄点由它推出，相对模式下同样跟随本次最重的
	 * 区块。默认红阈值 1.5 时黄点为 0.50。
	 */
	private static final float YELLOW_KNEE = 1.0f / 3.0f;

	/** 剔除时可见范围外扩的格数：给矩阵取整与地图缩放动画留余量。 */
	private static final int VIEW_MARGIN = 32;

	private MapOverlay() {
	}

	/**
	 * 绘制一个维度的热力图。参数均为 Xaero 那一帧的现成对象（见 GuiMapMixin 的注入点）。
	 *
	 * @param dimension    地图当前显示的维度，非玩家所在维度 —— 切到地狱地图即绘制地狱的数据
	 * @param screenWidth  屏幕（GUI 缩放后）的宽；≤0 表示拿不到屏幕尺寸，此时不剔除
	 * @param screenHeight 屏幕（GUI 缩放后）的高；≤0 同上
	 */
	public static void draw(Matrix4f matrix, VertexConsumer buffer, int flooredCameraX, int flooredCameraZ,
			String dimension, int screenWidth, int screenHeight) {
		ClientSnapshot.Chunk[] chunks = ClientSnapshot.get(dimension);
		if (chunks == null) {
			return;
		}

		// 红点整帧只算一次（相对模式取的是收快照时预计算好的本维度最重区块，这里不再遍历）
		float redPoint = redPoint(ClientSnapshot.heaviestMspt(dimension));
		// 屏幕外的区块不提交顶点：视距大的服务器上屏幕只显示几百个，其余照写是每帧最可省的一笔开销
		float[] view = viewBounds(matrix, flooredCameraX, flooredCameraZ, screenWidth, screenHeight);
		for (ClientSnapshot.Chunk chunk : chunks) {
			if (view != null && (chunk.x2() < view[0] || chunk.x1() > view[1]
					|| chunk.z2() < view[2] || chunk.z1() > view[3])) {
				continue;
			}
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
	 * 当前帧可见的世界坐标范围 {minX, maxX, minZ, maxZ}（已按 {@link #VIEW_MARGIN} 外扩）；
	 * 屏幕宽高非正（离线测试）或矩阵不可逆时返回 null，调用方据此跳过剔除。
	 *
	 * 做法：屏幕四角经矩阵的逆变换回「世界坐标 − 相机」空间，再平移回世界坐标取外接矩形 ——
	 * 地图若旋转，外接矩形仍是保守覆盖。纯函数，便于离线断言。
	 */
	static float[] viewBounds(Matrix4f matrix, int flooredCameraX, int flooredCameraZ, int screenWidth,
			int screenHeight) {
		if (screenWidth <= 0 || screenHeight <= 0) {
			return null;
		}
		Matrix4f inverse = new Matrix4f(matrix).invert();
		if (inverse == null) {
			return null;
		}
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (int corner = 0; corner < 4; corner++) {
			float screenX = (corner & 1) == 0 ? 0f : screenWidth;
			float screenY = (corner & 2) == 0 ? 0f : screenHeight;
			// 逆矩阵作用在 (screenX, screenY, 0, 1) 上；GUI 矩阵一般是正交（w = 1），除法只为稳妥
			float x = inverse.m00() * screenX + inverse.m01() * screenY + inverse.m03();
			float y = inverse.m10() * screenX + inverse.m11() * screenY + inverse.m13();
			float w = inverse.m30() * screenX + inverse.m31() * screenY + inverse.m33();
			if (w == 0f) {
				return null;
			}
			float worldX = x / w + flooredCameraX;
			float worldZ = y / w + flooredCameraZ;
			minX = Math.min(minX, worldX);
			maxX = Math.max(maxX, worldX);
			minZ = Math.min(minZ, worldZ);
			maxZ = Math.max(maxZ, worldZ);
		}
		if (!Float.isFinite(minX) || !Float.isFinite(maxX) || !Float.isFinite(minZ) || !Float.isFinite(maxZ)) {
			return null;
		}
		return new float[] {minX - VIEW_MARGIN, maxX + VIEW_MARGIN, minZ - VIEW_MARGIN, maxZ + VIEW_MARGIN};
	}

	/**
	 * 这一帧的红点：耗时达到它即为最高等级的红。
	 *
	 * 固定模式（默认）用设置中的红色阈值，一次定死、跨次可比；相对模式用当前显示的这一个维度、本次
	 * 快照中最重的区块（地图一次只画一个维度，其他维度不参与；最重值由 {@link ClientSnapshot#accept}
	 * 预计算，见 {@link ClientSnapshot#heaviestMspt}）。一个耗时都没测到（最重为 0）时退回固定阈值：
	 * 它恒大于 0，可作分母。
	 */
	static float redPoint(float heaviest) {
		if (!ClientConfig.relativeColor) {
			return (float) ClientConfig.redAt;
		}
		return heaviest > 0.0f ? heaviest : (float) ClientConfig.redAt;
	}

	/** 黄点 = 红点 × {@link #YELLOW_KNEE}。红点恒大于 0（下面拿它当分母）。 */
	static float yellowAt(float redPoint) {
		return redPoint * YELLOW_KNEE;
	}

	/** 红色分量：黄点以下是绿→黄那段斜坡，往上是黄→红那段。 */
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
