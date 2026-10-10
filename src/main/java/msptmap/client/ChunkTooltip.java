package msptmap.client;

import msptmap.util.Decimals;
import msptmap.sampler.TickCategory;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬停详情：鼠标所指区块的账本。
 *
 * <p>分为两部分：{@link #lines} 只组装文本组件（构造时不查语言表，可离线断言），{@link #draw} 只
 * 负责绘制。所指区块由 Xaero 的高亮决定（{@code mouseBlockPosX >> 4}，依据见
 * {@link msptmap.client.mixins.GuiMapMixin}），与地图显示的必然是同一个区块。
 *
 * <p>文本一律经 {@link Component#translatable} 引用语言文件里的键；翻译在绘制时才按当前语言解析，
 * 故切换语言无需作废 {@link #lines} 的缓存。行构件（类别顺序与名称、各类耗时行、票名、附注样式）
 * 见 {@link TickText}。
 */
public final class ChunkTooltip {
	/** 面板与鼠标的距离。 */
	private static final int OFFSET = 8;
	/** 面板与屏幕边缘的最小距离。 */
	private static final int MARGIN = 2;

	/**
	 * 上次拼行结果与其输入：区块（引用即快照代次——每收一包快照都会重建全部区块对象）、坐标、
	 * 窗口刻数、配置签名。五者全同则直接复用。
	 */
	private static ClientSnapshot.Chunk cachedChunk;
	private static int cachedChunkX;
	private static int cachedChunkZ;
	private static int cachedWindowTicks;
	/** 初值 -1：与任何真实签名（位掩码，≥ 0）都不同，首帧不会误命中。 */
	private static int cachedSignature = -1;
	private static List<Component> cachedLines = List.of();

	private ChunkTooltip() {
	}

	/**
	 * 详情要显示的每一行。显示哪几行由 {@link ClientConfig} 的勾选决定，全关时返回空列表（连
	 * 「未采样」也不给），调用方见到空列表即不画面板。
	 *
	 * <p>悬停时每帧都会调用到这里，而输入（区块、窗口、配置）在两次绘制之间通常不变，故按输入
	 * 缓存：命中即省去约八次 {@code format} 与整串拼接。
	 *
	 * @param chunk       鼠标所指区块；快照中没有（本次未扫到）时为 null——坐标行照给，另加一行
	 *                    「未采样」，以便区分「无数据」与「未显示」
	 * @param windowTicks 窗口内经过的 tick 数，各类纳秒换算 mspt 时的分母
	 */
	public static List<Component> lines(ClientSnapshot.Chunk chunk, int chunkX, int chunkZ, int windowTicks) {
		int signature = signature();
		if (chunk == cachedChunk && chunkX == cachedChunkX && chunkZ == cachedChunkZ
				&& windowTicks == cachedWindowTicks && signature == cachedSignature) {
			return cachedLines;
		}
		List<Component> lines = buildLines(chunk, chunkX, chunkZ, windowTicks);
		cachedChunk = chunk;
		cachedChunkX = chunkX;
		cachedChunkZ = chunkZ;
		cachedWindowTicks = windowTicks;
		cachedSignature = signature;
		cachedLines = lines;
		return lines;
	}

	/** 拼行本体（不含缓存）。 */
	private static List<Component> buildLines(ClientSnapshot.Chunk chunk, int chunkX, int chunkZ, int windowTicks) {
		List<Component> lines = new ArrayList<>();
		if (!ClientConfig.anyTooltipLine()) {
			return lines;
		}
		if (ClientConfig.tooltipCoords) {
			lines.add(Component.translatable("msptmap.tooltip.coords", chunkX, chunkZ));
		}
		if (chunk == null) {
			lines.add(Component.translatable("msptmap.tooltip.unsampled"));
			return lines;
		}
		boolean doubtful = false;
		if (ClientConfig.tooltipLevels) {
			// 两条链各配自己的票：加载票与模拟票在同一区块上可能不是同一张
			lines.add(Component.translatable("msptmap.tooltip.load_level", chunk.loadLevel())
					.append(TickText.ticket(chunk.loadTicket(), ClientConfig.tooltipTicketLoad, chunkX, chunkZ)));
			lines.add(Component.translatable("msptmap.tooltip.compute_level", chunk.computeLevel())
					.append(TickText.ticket(chunk.simTicket(), ClientConfig.tooltipTicketSim, chunkX, chunkZ)));
			doubtful = TickText.ticketDoubtful(chunk.loadTicket(), ClientConfig.tooltipTicketLoad)
					|| TickText.ticketDoubtful(chunk.simTicket(), ClientConfig.tooltipTicketSim);
		}
		if (ClientConfig.tooltipEntities) {
			// 与耗时无关的瞬时值：方块实体 / 实体 / 刷怪这几类耗时的成因多在于此
			lines.add(Component.translatable("msptmap.tooltip.entities", chunk.entities()));
		}
		if (ClientConfig.tooltipTotal) {
			lines.add(TickText.msptLine(Component.translatable("msptmap.label.total"), Decimals.format3(chunk.mspt())));
		}
		for (TickCategory category : TickText.ORDER) {
			if (ClientConfig.tooltipCategory(category)) {
				lines.add(TickText.secondary(TickText.categoryLine(
						chunk.nanos()[category.ordinal()], windowTicks, category)));
			}
		}
		if (doubtful) {
			lines.add(TickText.DOUBTFUL_NOTE);
		}
		return lines;
	}

	/**
	 * 面板位置：默认在鼠标右下角，右 / 下放不下则翻到另一侧，再收回屏幕内。返回 {@code {x, y}}。
	 *
	 * <p>单独拆出以便离线断言（贴边翻面是 {@link #draw} 中唯一易算错之处）。
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
	 * 鼠标是否正压在一个控件（按钮 / 输入框 / 下拉列表…）上；是则这一帧不画详情。
	 *
	 * <p>Xaero 在 {@code GuiMap.extractRenderState} 末尾也绘制自己的提示框，位置同在鼠标处，而注入点
	 * 是同一方法的 TAIL（最后绘制），两个框会重叠；指向按钮时本就不在看地图，让位即可。
	 *
	 * <p>以 {@code isMouseOver} 为准，不自行比对矩形：其他模组会往地图上加全屏的透明叠加层，靠覆写
	 * 它返回 false 声明自己不参与悬停（如 Xaero Head Tracker 的玩家头像层）。自行比对矩形会让这类
	 * 控件的矩形覆盖整屏，把悬停永久挡掉。
	 *
	 * <p>只用原版类型，不涉及 Xaero，可离线断言。
	 */
	public static boolean overWidget(int mouseX, int mouseY, List<? extends GuiEventListener> children) {
		for (GuiEventListener child : children) {
			if (child instanceof AbstractWidget widget && widget.isMouseOver(mouseX, mouseY)) {
				return true;
			}
		}
		return false;
	}

	/** 在鼠标右下方绘制小面板（外观与度量见 {@link MapPanel}）。无行可画时直接返回，不留空框。 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<Component> lines) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int mouseX, int mouseY, List<Component> lines) {
	*///?}
		if (lines.isEmpty()) {
			return;
		}
		int[] size = MapPanel.size(lines);
		int[] at = position(mouseX, mouseY, size[0], size[1],
				Minecraft.getInstance().getWindow().getGuiScaledWidth(),
				Minecraft.getInstance().getWindow().getGuiScaledHeight());
		MapPanel.draw(graphics, at[0], at[1], size, lines, -1);
	}

	/**
	 * 参与拼行的全部配置项压成一个位掩码：任一开关变动则签名改变，缓存随之作废。
	 *
	 * <p>逐项列出（不依赖任何通用机制），配置项增删时此处会跟着改，不会静默漏进签名。
	 */
	private static int signature() {
		int bits = 0;
		if (ClientConfig.tooltipCoords) {
			bits |= 1 << 0;
		}
		if (ClientConfig.tooltipLevels) {
			bits |= 1 << 1;
		}
		if (ClientConfig.tooltipTotal) {
			bits |= 1 << 2;
		}
		if (ClientConfig.tooltipEntities) {
			bits |= 1 << 3;
		}
		if (ClientConfig.tooltipTicketLoad) {
			bits |= 1 << 4;
		}
		if (ClientConfig.tooltipTicketSim) {
			bits |= 1 << 5;
		}
		// 各类明细各占一位，位序按 ordinal（签名只需唯一，与显示顺序无关）
		for (TickCategory category : TickText.ORDER) {
			if (ClientConfig.tooltipCategory(category)) {
				bits |= 1 << (6 + category.ordinal());
			}
		}
		// 类别名缩写紧随类别段之后占一位
		if (ClientConfig.tooltipAbbreviate) {
			bits |= 1 << 13;
		}
		// 明细行的 mspt 单位占一位（行内容随之变，须入签名）
		if (ClientConfig.tooltipMsptUnit) {
			bits |= 1 << 14;
		}
		return bits;
	}
}
