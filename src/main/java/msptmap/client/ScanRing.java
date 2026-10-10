package msptmap.client;

import msptmap.util.Clamp;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描按钮四周的进度圈：自左上角起顺时针，转满一圈即本次扫描结束。
 *
 * <p>圈画在按钮自身的边框上，不向外扩：按钮贴屏幕左边缘（x=0），外扩会使左边那条被裁掉；按钮内
 * 16×16 的图标居中，四周各有 2 像素空当，正好容纳这一像素宽的圈。
 *
 * <p>与 {@link ChunkTooltip} 同理：{@link #segments} 只算矩形（可离线断言），{@link #draw} 才接触屏幕。
 */
public final class ScanRing {
	/** 未走到的部分：半透明黑轨道。 */
	private static final int TRACK_COLOR = 0x80000000;
	/** 已走过的部分：白色，与 Xaero 图标同色。 */
	private static final int PROGRESS_COLOR = 0xFFFFFFFF;

	private ScanRing() {
	}

	/**
	 * 顺时针走过 {@code fraction} 圈需要填充的矩形。
	 *
	 * <p>一圈分四条边（上 → 右 → 下 → 左），每边 {@code size} 步；每个 {@code int[]} 为
	 * {@code {x1, y1, x2, y2}}，可直接传给 {@code graphics.fill}（左闭右开，宽度恰为 1 像素）。
	 * 最多四个矩形，不做逐像素碎块。四角各被两条边重复计入，颜色相同，不可见。
	 */
	public static List<int[]> segments(float fraction, int x, int y, int size) {
		List<int[]> out = new ArrayList<>(4);
		int done = doneSteps(fraction, size);
		for (int side = 0; side < 4; side++) {
			int steps = sideSteps(done, side, size);
			if (steps == 0) {
				continue;
			}
			int[] rect = new int[4];
			sideRect(side, steps, x, y, size, rect);
			out.add(rect);
		}
		return out;
	}

	/** 已走过的总步数（整圈 = {@code size} 的 4 倍）。 */
	private static int doneSteps(float fraction, int size) {
		return Math.round(Clamp.of(fraction, 0f, 1f) * size * 4);
	}

	/** 该条边已走过多少步（0 ~ {@code size}）。 */
	private static int sideSteps(int done, int side, int size) {
		return Clamp.of(done - side * size, 0, size);
	}

	/** 把某条边上已走过的一段写成矩形（{@code out} = {x1, y1, x2, y2}）。四条的几何算式只此一处。 */
	private static void sideRect(int side, int steps, int x, int y, int size, int[] out) {
		switch (side) {
			case 0 -> { out[0] = x; out[1] = y; out[2] = x + steps; out[3] = y + 1; }                          // 上：左 → 右
			case 1 -> { out[0] = x + size - 1; out[1] = y; out[2] = x + size; out[3] = y + steps; }            // 右：上 → 下
			case 2 -> { out[0] = x + size - steps; out[1] = y + size - 1; out[2] = x + size; out[3] = y + size; } // 下：右 → 左
			default -> { out[0] = x; out[1] = y + size - steps; out[2] = x + 1; out[3] = y + size; }           // 左：下 → 上
		}
	}

	/** 绘制复用的矩形缓冲：只在客户端渲染线程用（单独一条线程），故静态共享无并发问题。 */
	private static final int[] RECT = new int[4];

	/** 把圈画在按钮边框上：先铺整圈暗轨道，再覆盖已走过的部分。 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, float fraction, int x, int y, int size) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, float fraction, int x, int y, int size) {
	*///?}
		fillRing(graphics, 1f, x, y, size, TRACK_COLOR);
		fillRing(graphics, fraction, x, y, size, PROGRESS_COLOR);
	}

	/**
	 * 逐条边填充；不走 {@link #segments}（避免每帧分配列表与 int[]），几何算式与之共用。
	 *
	 * <p>声明行分叉（{@code GuiGraphics} 在 26.1 更名为 {@code GuiGraphicsExtractor}），方法体共用；
	 * else 段的注释里不能再放以星号斜杠收尾的注释（javadoc 也算），那会提前关上包装注释。
	 */
	//? if >=26.1 {
	private static void fillRing(GuiGraphicsExtractor graphics, float fraction, int x, int y, int size, int color) {
	//?} else {
	/*private static void fillRing(GuiGraphics graphics, float fraction, int x, int y, int size, int color) {
	*///?}
		int done = doneSteps(fraction, size);
		for (int side = 0; side < 4; side++) {
			int steps = sideSteps(done, side, size);
			if (steps == 0) {
				continue;
			}
			sideRect(side, steps, x, y, size, RECT);
			graphics.fill(RECT[0], RECT[1], RECT[2], RECT[3], color);
		}
	}
}
