package msptmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 地图上的小面板：深色半透明底、白字、四周内边距 3、行距 lineHeight + 1。
 *
 * <p>悬停详情（{@link ChunkTooltip}）与扫描总览（{@link ScanSummary}）共用同一套外观与度量，
 * 两处不再各画各的。
 */
public final class MapPanel {
	/** 面板四周的内边距。 */
	private static final int PADDING = 3;
	/** 深色半透明底：地图颜色杂乱，需垫底才看得清字。 */
	private static final int BACKGROUND = 0xC0000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private MapPanel() {
	}

	/**
	 * 面板尺寸 {@code {宽, 高}}：宽 = 最长行 + 两侧内边距，高 = 行数 × 行距 + 上下内边距。
	 *
	 * <p>单独拆出：调用方先算位置再绘制，两处都要尺寸。
	 */
	public static int[] size(List<Component> lines) {
		Font font = Minecraft.getInstance().font;
		int textWidth = 0;
		for (Component line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		return new int[]{textWidth + PADDING * 2, lines.size() * (font.lineHeight + 1) + PADDING * 2};
	}

	/** 以 ({@code x}, {@code y}) 为左上角画面板；{@code size} 由 {@link #size} 预先算出。 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int[] size, List<Component> lines) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int x, int y, int[] size, List<Component> lines) {
	*///?}
		graphics.fill(x, y, x + size[0], y + size[1], BACKGROUND);
		Font font = Minecraft.getInstance().font;
		int lineHeight = font.lineHeight + 1;
		// 行的样式（颜色、斜体等）已内嵌在组件里，此处不再区分
		for (int i = 0; i < lines.size(); i++) {
			int lineY = y + PADDING + i * lineHeight;
			//? if >=26.1 {
			graphics.text(font, lines.get(i), x + PADDING, lineY, TEXT_COLOR);
			//?} else {
			/*graphics.drawString(font, lines.get(i), x + PADDING, lineY, TEXT_COLOR);
			*///?}
		}
	}
}
