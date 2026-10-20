package msptmap.client;

import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * 地图上可滚动面板的滚动逻辑：可见行数、位置夹取、把手位置与滚动条绘制。扫描总览（{@link ScanSummary}）
 * 与右侧完整加载源列表（{@link SourceListPanel}）共用，两处的滚轮步长与滚动条外观随之统一。
 *
 * <p>面板本身由 {@link MapPanel} 绘制，本类只管"画哪一段"与右缘那条滚动条。
 */
final class PanelScroll {
	/** 滚轮一格翻动的行数。 */
	static final int STEP = 3;
	/** 滚动条宽度：贴面板右缘，恰在 3 px 内边距内，不遮挡文字。 */
	private static final int SCROLLBAR_WIDTH = 3;
	/** 滚动条把手的最小高度：行数远多于可见行时按比例算出的把手会细到看不见。 */
	private static final int MIN_THUMB = 4;
	/** 滚动条槽与把手的颜色（槽同 MapPanel 的行高亮底，把手亮白）。 */
	private static final int TRACK_COLOR = 0x30FFFFFF;
	private static final int THUMB_COLOR = 0xC0FFFFFF;

	private PanelScroll() {
	}

	/**
	 * 面板自 {@code panelY} 起能画下的行数：总行数、{@code maxRows} 与屏幕容得下的行数三者取小
	 * （自面板上沿量到屏幕底，上下各去 3 px 内边距，同 {@link MapPanel}），至少 1。
	 *
	 * <p>调用方须先确认行列表非空：空列表下返回值仍为 1，直接 {@code subList(0, 1)} 会越界。
	 */
	static int shownRows(int rows, int panelY, int maxRows) {
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int fits = (screenHeight - panelY - MapPanel.PADDING * 2) / MapPanel.lineHeight();
		return Math.max(1, Math.min(Math.min(rows, maxRows), fits));
	}

	/**
	 * 滚动位置夹回 [0, 总行数 - 窗口行数]；总行数不超过窗口时恒 0。纯函数（不碰字体与屏幕），
	 * 绘制、命中与滚轮三处共用。
	 */
	static int clampScroll(int rows, int shown, int scroll) {
		return Math.max(0, Math.min(scroll, rows - shown));
	}

	/**
	 * 滚动条把手的位置与高度 {@code {y, h}}：高按可见比例、位置按滚动进度；轨道为
	 * ({@code trackY}, 高 {@code trackH})。只在确有滚动（总行数 &gt; 窗口行数）时调用。
	 */
	static int[] thumb(int rows, int shown, int scroll, int trackY, int trackH) {
		// 防御：不需滚动时把手占满整条轨道（当前只由 draw 在 rows > shown 时调用）
		if (rows <= shown) {
			return new int[]{trackY, trackH};
		}
		int height = Math.max(MIN_THUMB, (int) ((long) trackH * shown / rows));
		int y = trackY + (int) ((long) (trackH - height) * scroll / (rows - shown));
		return new int[]{y, height};
	}

	/** 确有滚动时在面板右缘画一条滚动条（槽 + 把手）；不用滚动时什么都不画。 */
	//? if >=26.1 {
	static void draw(GuiGraphicsExtractor graphics, int panelX, int panelY, int[] size, int rows, int shown,
			int scroll) {
	//?} else {
	/*static void draw(GuiGraphics graphics, int panelX, int panelY, int[] size, int rows, int shown,
			int scroll) {
	*///?}
		if (rows <= shown) {
			return;
		}
		int trackX = panelX + size[0] - SCROLLBAR_WIDTH;
		graphics.fill(trackX, panelY, trackX + SCROLLBAR_WIDTH, panelY + size[1], TRACK_COLOR);
		int[] thumb = thumb(rows, shown, scroll, panelY, size[1]);
		graphics.fill(trackX, thumb[0], trackX + SCROLLBAR_WIDTH, thumb[0] + thumb[1], THUMB_COLOR);
	}
}
