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
	/** 可点击行悬停时的行底：在深底上叠一层淡白，提示可点。 */
	private static final int HIGHLIGHT = 0x30FFFFFF;
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

	/**
	 * 鼠标落在第几行（自 0 起）；不在面板上时返回 -1。行区与 {@link #draw} 的排布同源：自
	 * y + PADDING 起、每行 lineHeight 高。
	 */
	public static int rowAt(int mouseX, int mouseY, int x, int y, int[] size) {
		int lineHeight = Minecraft.getInstance().font.lineHeight + 1;
		if (mouseX < x || mouseX >= x + size[0] || mouseY < y + PADDING || mouseY >= y + size[1] - PADDING) {
			return -1;
		}
		return (mouseY - y - PADDING) / lineHeight;
	}

	/** 点是否落在 ({@code x}, {@code y}) 起、{@code size} 尺寸的框内（悬停让位面板的判据）。 */
	public static boolean contains(int mouseX, int mouseY, int x, int y, int[] size) {
		return mouseX >= x && mouseX < x + size[0] && mouseY >= y && mouseY < y + size[1];
	}

	/**
	 * 以 ({@code x}, {@code y}) 为左上角画面板；{@code size} 由 {@link #size} 预先算出。
	 * {@code highlightRow} 为要高亮底的行号（{@link #rowAt} 的结果），-1 表示不高亮任何行。
	 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int[] size, List<Component> lines,
			int highlightRow) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int x, int y, int[] size, List<Component> lines,
			int highlightRow) {
	*///?}
		graphics.fill(x, y, x + size[0], y + size[1], BACKGROUND);
		Font font = Minecraft.getInstance().font;
		int lineHeight = font.lineHeight + 1;
		// 行的样式（颜色、斜体等）已内嵌在组件里，此处不再区分
		for (int i = 0; i < lines.size(); i++) {
			int lineY = y + PADDING + i * lineHeight;
			if (i == highlightRow) {
				// 高亮底上移 1px：块在字形上下各余 1px
				graphics.fill(x, lineY - 1, x + size[0], lineY + lineHeight - 1, HIGHLIGHT);
			}
			//? if >=26.1 {
			graphics.text(font, lines.get(i), x + PADDING, lineY, TEXT_COLOR);
			//?} else {
			/*graphics.drawString(font, lines.get(i), x + PADDING, lineY, TEXT_COLOR);
			*///?}
		}
	}
}
