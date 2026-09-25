package msptmap.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描按钮四周的进度圈：自左上角起顺时针，转满一圈 = 本次扫描结束。
 *
 * 圈画在按钮自身的边框上，不向外扩：按钮贴屏幕左边缘（x=0），外扩会使左边那条被裁掉；按钮内
 * 16×16 的图标居中，四周各有 2 像素空当，正好容纳这一像素宽的圈。
 *
 * 与 {@link ChunkTooltip} 同理：{@link #segments} 只算矩形（可离线断言），{@link #draw} 才接触屏幕。
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
	 * 一圈分四条边（上 → 右 → 下 → 左），每边 {@code size} 步；每个 {@code int[]} 为
	 * {@code {x1, y1, x2, y2}}，可直接传给 {@code graphics.fill}（左闭右开，宽度恰为 1 像素）。
	 * 最多四个矩形，不做逐像素碎块。四角各被两条边重复计入，颜色相同，不可见。
	 */
	public static List<int[]> segments(float fraction, int x, int y, int size) {
		List<int[]> out = new ArrayList<>(4);
		int done = Math.round(Math.clamp(fraction, 0f, 1f) * size * 4);
		for (int side = 0; side < 4; side++) {
			int steps = Math.clamp(done - side * size, 0, size);
			if (steps == 0) {
				continue;
			}
			out.add(switch (side) {
				case 0 -> new int[]{x, y, x + steps, y + 1};                                  // 上：左 → 右
				case 1 -> new int[]{x + size - 1, y, x + size, y + steps};                    // 右：上 → 下
				case 2 -> new int[]{x + size - steps, y + size - 1, x + size, y + size};      // 下：右 → 左
				default -> new int[]{x, y + size - steps, x + 1, y + size};                   // 左：下 → 上
			});
		}
		return out;
	}

	/** 把圈画在按钮边框上：先铺整圈暗轨道，再覆盖已走过的部分。 */
	public static void draw(GuiGraphicsExtractor graphics, float fraction, int x, int y, int size) {
		for (int[] side : segments(1f, x, y, size)) {
			graphics.fill(side[0], side[1], side[2], side[3], TRACK_COLOR);
		}
		for (int[] step : segments(fraction, x, y, size)) {
			graphics.fill(step[0], step[1], step[2], step[3], PROGRESS_COLOR);
		}
	}
}
