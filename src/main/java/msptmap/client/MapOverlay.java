package msptmap.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
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
	/** 达到该加载等级即弱加载（26.2 中 ChunkLevel.isBlockTicking 的界线为 32）。 */
	private static final int WEAK_LOAD_LEVEL = 32;

	/** 弱加载且无耗时的区块铺的淡灰。 */
	private static final float IDLE_GRAY = 0.7f;
	private static final float IDLE_ALPHA = 0.3f;

	/**
	 * 黄点在红点上的位置（1/3）：颜色只留红点一个旋钮，黄点由它推出，相对模式下同样跟随本次最重的
	 * 区块。默认红阈值 1.5 时黄点为 0.50。
	 */
	private static final float YELLOW_KNEE = 1.0f / 3.0f;

	private MapOverlay() {
	}

	/**
	 * 绘制一个维度的热力图。参数均为 Xaero 那一帧的现成对象（见 GuiMapMixin 的注入点）。
	 *
	 * @param dimension 地图当前显示的维度，非玩家所在维度 —— 切到地狱地图即绘制地狱的数据
	 */
	public static void draw(Matrix4f matrix, VertexConsumer buffer, int flooredCameraX, int flooredCameraZ,
			Identifier dimension) {
		ClientSnapshot.Chunk[] chunks = ClientSnapshot.get(dimension);
		if (chunks == null) {
			return;
		}

		// 红点整帧只算一次（相对模式需遍历该维度的全部区块）
		float redPoint = redPoint(chunks);
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
	 * 这一帧的红点：耗时达到它即为最高等级的红。
	 *
	 * 固定模式（默认）用设置中的红色阈值，一次定死、跨次可比；相对模式用当前显示的这一个维度、本次
	 * 快照中最重的区块（地图一次只画一个维度，其他维度不参与）。一个耗时都没测到（最重为 0）时退回
	 * 固定阈值：它恒大于 0，可作分母。
	 */
	static float redPoint(ClientSnapshot.Chunk[] chunks) {
		if (!ClientConfig.relativeColor) {
			return (float) ClientConfig.redAt;
		}
		float heaviest = 0.0f;
		for (ClientSnapshot.Chunk chunk : chunks) {
			heaviest = Math.max(heaviest, chunk.mspt());
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
