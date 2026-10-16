package msptmap.client;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 右侧完整加载源列表：总览里折起的那部分在此全列。行格式与可点击定位（{@link ChunkRef}）
 * 都与总览内的源行相同，开合由总览的折叠行决定（状态在 {@link ScanSummary}）。
 *
 * <p>面板紧贴总览右缘并与之顶对齐（位置由调用处算出）。行数超过 {@link #MAX_ROWS} 或屏幕容不下时
 * 只画一个窗口，右缘出滚动条，滚轮按 {@link PanelScroll#STEP} 行翻动（不做拖动把手）。滚动位置是静态
 * 字段、不落盘：打开地图时随 {@link ScanSummary#resetSourcesPanel()} 一并复位。
 */
public final class SourceListPanel {
	/** 窗口行数上限，再多则出滚动条。 */
	public static final int MAX_ROWS = 30;

	/** 窗口首行序号（见 {@link PanelScroll#clampScroll}）；不落盘。 */
	private static int scroll;

	private SourceListPanel() {
	}

	/** 面板尺寸 {宽, 高}：宽按全部行（滚动时不变），高按窗口行数。 */
	static int[] size(int panelY) {
		List<Component> rows = ScanSummary.sourceRows();
		int shown = PanelScroll.shownRows(rows.size(), panelY, MAX_ROWS);
		int[] full = MapPanel.size(rows);
		int[] window = MapPanel.size(rows.subList(0, shown));
		return new int[]{full[0], window[1]};
	}

	/**
	 * 以 ({@code x}, {@code y}) 为左上角绘制窗口；调用方先确认 {@link ScanSummary#sourcesPanelOpen()}。
	 * 鼠标下的行垫高亮底（窗口里每一行都是可点击的源行）；确有滚动时右缘画一条滚动条。
	 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
	*///?}
		List<Component> rows = ScanSummary.sourceRows();
		if (rows.isEmpty()) {
			return;
		}
		int shown = PanelScroll.shownRows(rows.size(), y, MAX_ROWS);
		scroll = PanelScroll.clampScroll(rows.size(), shown, scroll);
		int[] size = size(y);
		int row = MapPanel.rowAt(mouseX, mouseY, x, y, size);
		MapPanel.draw(graphics, x, y, size, rows.subList(scroll, scroll + shown), row, -1);
		PanelScroll.draw(graphics, x, y, size, rows.size(), shown, scroll);
	}

	/** 鼠标是否落在面板上（未展开、无数据时恒 false）。悬停详情绘制在其上，重叠处使其不绘制。 */
	public static boolean overPanel(int mouseX, int mouseY, int panelX, int panelY) {
		if (!ScanSummary.sourcesPanelOpen()) {
			return false;
		}
		List<Component> rows = ScanSummary.sourceRows();
		if (rows.isEmpty()) {
			return false;
		}
		int[] size = size(panelY);
		return MapPanel.contains(mouseX, mouseY, panelX, panelY, size);
	}

	/**
	 * 鼠标下的源行指向的区块；不在面板上时返回 null。按当前滚动位置取行，点击的必是眼下可见的那条。
	 */
	public static ChunkRef hitTarget(int mouseX, int mouseY, int panelX, int panelY) {
		if (!ScanSummary.sourcesPanelOpen()) {
			return null;
		}
		List<ClientSnapshot.Source> sources = ScanSummary.sources();
		if (sources.isEmpty()) {
			return null;
		}
		int shown = PanelScroll.shownRows(sources.size(), panelY, MAX_ROWS);
		int row = MapPanel.rowAt(mouseX, mouseY, panelX, panelY, size(panelY));
		if (row < 0) {
			return null;
		}
		ClientSnapshot.Source source = sources.get(PanelScroll.clampScroll(sources.size(), shown, scroll) + row);
		return new ChunkRef(source.dimension(), source.chunkX(), source.chunkZ());
	}

	/**
	 * 滚轮落在面板上：按 {@link PanelScroll#STEP} 行翻动（{@code direction} 正为上滚、看更前面的源），
	 * 并返回 true 让调用方消费事件（避免地图随之缩放）；没落在面板上返回 false。
	 */
	public static boolean scroll(int mouseX, int mouseY, int panelX, int panelY, int direction) {
		if (!overPanel(mouseX, mouseY, panelX, panelY)) {
			return false;
		}
		int rows = ScanSummary.sourceRows().size();
		int shown = PanelScroll.shownRows(rows, panelY, MAX_ROWS);
		scroll = PanelScroll.clampScroll(rows, shown, scroll - direction * PanelScroll.STEP);
		return true;
	}

	/** 打开地图时复位：滚动回到顶部（不落盘）。 */
	public static void reset() {
		scroll = 0;
	}
}
