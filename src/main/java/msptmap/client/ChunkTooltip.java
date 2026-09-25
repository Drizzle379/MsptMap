package msptmap.client;

import msptmap.sampler.TickCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬停详情：鼠标所指区块的账本。
 *
 * 分两半：{@link #lines} 只拼字符串（不认游戏，可离线断言），{@link #draw} 只负责绘制。
 *
 * 所指区块由 Xaero 的高亮决定（{@code mouseBlockPosX >> 4}，依据见
 * {@link msptmap.client.mixins.GuiMapMixin}），与地图显示的必然是同一个区块。
 */
public final class ChunkTooltip {
	private static final int PADDING = 3;
	/** 面板与鼠标的距离。 */
	private static final int OFFSET = 8;
	/** 面板与屏幕边缘的最小距离。 */
	private static final int MARGIN = 2;
	/** 深色半透明底：地图颜色杂乱，需垫底才看得清字。 */
	private static final int BACKGROUND = 0xC0000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private ChunkTooltip() {
	}

	/**
	 * 详情要显示的每一行。显示哪几行由 {@link ClientConfig} 的勾选决定，全关时返回空列表
	 * （连「未采样」也不给）；调用方见到空列表即不画面板。明细右侧的「×1200」是另一条开关
	 * （{@link ClientConfig#tooltipCounts}），不单独成行。
	 *
	 * @param chunk       鼠标所指区块；快照中没有（本次未扫到）时传 null —— 坐标行照给，另加一行
	 *                    「未采样」，以便区分「无数据」与「未显示」
	 * @param windowTicks 窗口内经过的 tick 数，五类纳秒换算 mspt 时的分母
	 */
	public static List<String> lines(ClientSnapshot.Chunk chunk, int chunkX, int chunkZ, int windowTicks) {
		List<String> lines = new ArrayList<>();
		if (!ClientConfig.anyTooltipLine()) {
			return lines;
		}
		if (ClientConfig.tooltipCoords) {
			lines.add("区块 " + chunkX + ", " + chunkZ);
		}
		if (chunk == null) {
			lines.add("未采样");
			return lines;
		}
		if (ClientConfig.tooltipLevels) {
			lines.add("加载等级 " + chunk.loadLevel() + " · 计算等级 " + chunk.computeLevel());
		}
		if (ClientConfig.tooltipTotal) {
			lines.add("合计 " + format(chunk.mspt()) + " mspt");
		}
		for (TickCategory category : TickCategory.values()) {
			if (ClientConfig.tooltipCategory(category)) {
				lines.add(label(category) + " " + ms(chunk.nanos()[category.ordinal()], windowTicks) + " mspt"
						+ (ClientConfig.tooltipCounts ? " ×" + chunk.counts()[category.ordinal()] : ""));
			}
		}
		return lines;
	}

	/**
	 * 面板位置：默认在鼠标右下角，右 / 下放不下则翻到另一侧，再收回屏幕内。返回 {x, y}。
	 *
	 * 单独拆出以便离线断言（贴边翻面是 {@link #draw} 里唯一会算错的地方）。
	 */
	public static int[] position(int mouseX, int mouseY, int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
		int x = mouseX + OFFSET;
		int y = mouseY + OFFSET;
		if (x + boxWidth > screenWidth - MARGIN) {
			x = mouseX - OFFSET - boxWidth;
		}
		if (y + boxHeight > screenHeight - MARGIN) {
			y = mouseY - OFFSET - boxHeight;
		}
		return new int[]{Math.max(MARGIN, x), Math.max(MARGIN, y)};
	}

	/**
	 * 鼠标是否正压在一个控件（按钮 / 输入框 / 下拉列表…）上 —— 是则这一帧不画详情。
	 *
	 * Xaero 在 {@code GuiMap.extractRenderState} 末尾也绘制自己的提示框，位置同在鼠标处，而注入点
	 * 是同一方法的 TAIL（最后绘制），两个框会重叠；指向按钮时本就不在看地图，让位即可。
	 * 判定只看是否为控件且鼠标落在其矩形内（左闭右开，同 {@code fill}），不看显示与禁用状态，
	 * 与 Xaero 的口径一致；只认 {@link AbstractWidget}。
	 *
	 * 只用原版类型，不涉及 Xaero，可离线断言。
	 */
	public static boolean overWidget(int mouseX, int mouseY, List<? extends GuiEventListener> children) {
		for (GuiEventListener child : children) {
			if (!(child instanceof AbstractWidget widget)) {
				continue;
			}
			if (mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
					&& mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight()) {
				return true;
			}
		}
		return false;
	}

	/** 在鼠标右下方绘制小面板。无行可画时直接返回，不留空框。 */
	public static void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<String> lines) {
		if (lines.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int lineHeight = font.lineHeight + 1;
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int boxWidth = textWidth + PADDING * 2;
		int boxHeight = lines.size() * lineHeight + PADDING * 2;
		int[] at = position(mouseX, mouseY, boxWidth, boxHeight,
				Minecraft.getInstance().getWindow().getGuiScaledWidth(),
				Minecraft.getInstance().getWindow().getGuiScaledHeight());

		graphics.fill(at[0], at[1], at[0] + boxWidth, at[1] + boxHeight, BACKGROUND);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), at[0] + PADDING, at[1] + PADDING + i * lineHeight, TEXT_COLOR);
		}
	}

	/**
	 * 五类的名称。用 switch 而非数组：枚举顺序变更会在编译期报错，不会静默错位。
	 *
	 * 包内可见：设置界面那五个勾选框也用它 —— 详情显示什么，设置里就写什么。
	 */
	static String label(TickCategory category) {
		return switch (category) {
			case RANDOM_TICK -> "随机刻";
			case SCHEDULED -> "计划刻";
			case BLOCK_ENTITY -> "方块实体";
			case ENTITY -> "实体";
			case SPAWN -> "刷怪";
		};
	}

	/** 纳秒 → 毫秒/tick 文本（三位小数），与 MsptSampler 打印用的格式一致。 */
	private static String ms(long nanos, int windowTicks) {
		return format(nanos / 1_000_000.0 / Math.max(1, windowTicks));
	}

	/** 快照里已算好的 ms/tick 走这里。 */
	private static String format(double mspt) {
		return String.format("%.3f", mspt);
	}
}
