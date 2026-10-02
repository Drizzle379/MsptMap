package msptmap.client;

import msptmap.sampler.TickCategory;
import msptmap.sampler.TicketSources;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
	/** 附注文字色：正文是纯白，附注降一档。 */
	private static final int NOTE_COLOR = 0xFFB0B0B0;

	/**
	 * 存疑说明行。{@link #lines} 只在真有区块对不上时把它追加在末尾，{@link #draw} 认这一行，
	 * 用斜体画。
	 */
	static final String NOTE_DOUBTFUL = "* 该行数据未能核实";

	/**
	 * 各类耗时在详情与设置界面里的显示顺序。
	 *
	 * 与枚举顺序不同：枚举只许在末尾追加（协议按 ordinal 上线），而这里按「原版 tick 的先后」排，
	 * 与 {@code ServerLevel.tick} 里各阶段的次序一致。新增类别时这里也要加一项，否则设置界面与
	 * 详情都不会显示它（顺序自定）。
	 */
	static final List<TickCategory> ORDER = List.of(
			TickCategory.RANDOM_TICK,
			TickCategory.SCHEDULED,
			TickCategory.NEIGHBOR_UPDATE,
			TickCategory.BLOCK_EVENT,
			TickCategory.BLOCK_ENTITY,
			TickCategory.ENTITY,
			TickCategory.SPAWN);

	private ChunkTooltip() {
	}

	/**
	 * 详情要显示的每一行。显示哪几行由 {@link ClientConfig} 的勾选决定，全关时返回空列表
	 * （连「未采样」也不给）；调用方见到空列表即不画面板。
	 *
	 * @param chunk       鼠标所指区块；快照中没有（本次未扫到）时传 null —— 坐标行照给，另加一行
	 *                    「未采样」，以便区分「无数据」与「未显示」
	 * @param windowTicks 窗口内经过的 tick 数，各类纳秒换算 mspt 时的分母
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
		boolean doubtful = false;
		if (ClientConfig.tooltipLevels) {
			// 两条链各配自己的票：加载票与模拟票在同一区块上可能不是同一张
			lines.add("加载等级 " + chunk.loadLevel()
					+ ticket(chunk.loadTicket(), ClientConfig.tooltipTicketLoad, chunkX, chunkZ));
			lines.add("计算等级 " + chunk.computeLevel()
					+ ticket(chunk.simTicket(), ClientConfig.tooltipTicketSim, chunkX, chunkZ));
			doubtful = ticketDoubtful(chunk.loadTicket(), ClientConfig.tooltipTicketLoad)
					|| ticketDoubtful(chunk.simTicket(), ClientConfig.tooltipTicketSim);
		}
		if (ClientConfig.tooltipEntities) {
			// 与耗时无关的瞬时值：方块实体 / 实体 / 刷怪那几类耗时的成因多半在这里
			lines.add("实体数 " + chunk.entities());
		}
		if (ClientConfig.tooltipTotal) {
			lines.add("合计 " + format(chunk.mspt()) + " mspt");
		}
		for (TickCategory category : ORDER) {
			if (ClientConfig.tooltipCategory(category)) {
				lines.add(label(category) + " " + ms(chunk.nanos()[category.ordinal()], windowTicks) + " mspt");
			}
		}
		if (doubtful) {
			lines.add(NOTE_DOUBTFUL);
		}
		return lines;
	}

	/**
	 * 等级行后半段：「 · 票名中心」或「 · 票名 @x,z」；存疑时是「 · 未知 *」。
	 * 没勾选该开关、或（理论上不该发生的）该链没有来源时返回空串。
	 */
	private static String ticket(int code, boolean enabled, int chunkX, int chunkZ) {
		if (!enabled) {
			return "";
		}
		if (TicketSources.doubtful(code)) {
			return " · 未知 *";
		}
		int type = TicketSources.type(code);
		if (type == TicketSources.NONE) {
			return "";
		}
		String name = ticketName(type);
		if (type == TicketSources.PLAYER_LOADING) {
			// 26.2 的 player_loading 是**逐区块铺**的：视距内每格一张、等级还都一样，所以这一链上
			// 根本不存在「中心」与距离可言（每格算出来都是自己）。只写票名，不编造一个恒为 0 的距离。
			// 其余票种（forced / portal / ender_pearl…）是稀疏的，中心与距离才有意义。
			return " · " + name;
		}
		int offsetX = TicketSources.offsetX(code);
		int offsetZ = TicketSources.offsetZ(code);
		if (offsetX == 0 && offsetZ == 0) {
			// 票就在本区块上：源头坐标即本区块坐标，不必重复写
			return " · " + name + "中心";
		}
		return " · " + name + " @" + (chunkX + offsetX) + "," + (chunkZ + offsetZ);
	}

	/** 该链存疑、且这一栏确实要显示。 */
	private static boolean ticketDoubtful(int code, boolean enabled) {
		return enabled && TicketSources.doubtful(code);
	}

	/** 加载票的名称。原版没有官方译名，这里是本模组的定名，改动即协议变更。 */
	private static String ticketName(int type) {
		return switch (type) {
			case TicketSources.PLAYER_LOADING -> "玩家加载";
			case TicketSources.PLAYER_SIMULATION -> "玩家模拟";
			case TicketSources.FORCED -> "强制加载";
			case TicketSources.PORTAL -> "传送门";
			case TicketSources.ENDER_PEARL -> "末影珍珠";
			case TicketSources.PLAYER_SPAWN -> "出生点";
			case TicketSources.SPAWN_SEARCH -> "出生点搜索";
			case TicketSources.DRAGON -> "末影龙";
			default -> "未知";
		};
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
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<String> lines) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int mouseX, int mouseY, List<String> lines) {
	*///?}
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
			String line = lines.get(i);
			int y = at[1] + PADDING + i * lineHeight;
			if (line.equals(NOTE_DOUBTFUL)) {
				// 附注：斜体、不加粗、比正文暗一档
				//? if >=26.1 {
				graphics.text(font, Component.literal(line).withStyle(ChatFormatting.ITALIC),
						at[0] + PADDING, y, NOTE_COLOR);
				//?} else {
				/*graphics.drawString(font, Component.literal(line).withStyle(ChatFormatting.ITALIC),
						at[0] + PADDING, y, NOTE_COLOR);
				*///?}
			} else {
				//? if >=26.1 {
				graphics.text(font, line, at[0] + PADDING, y, TEXT_COLOR);
				//?} else {
				/*graphics.drawString(font, line, at[0] + PADDING, y, TEXT_COLOR);
				*///?}
			}
		}
	}

	/**
	 * 各类的名称。用 switch 而非数组：枚举新增类别时会在编译期报错，不会静默错位。
	 * 名称都是四个字（对齐后每行等宽），新增时沿用。
	 *
	 * 包内可见：设置界面那些勾选框也用它 —— 详情显示什么，设置里就写什么。
	 */
	static String label(TickCategory category) {
		return switch (category) {
			case RANDOM_TICK -> "随机刻";
			case SCHEDULED -> "计划刻";
			case NEIGHBOR_UPDATE -> "方块更新";
			case BLOCK_EVENT -> "方块事件";
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
