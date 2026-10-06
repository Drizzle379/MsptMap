package msptmap.client;

import msptmap.Decimals;
import msptmap.sampler.TickCategory;
import msptmap.sampler.TicketSources;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

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
 * 故切换语言无需作废 {@link #lines} 的缓存。
 */
public final class ChunkTooltip {
	/** 面板与鼠标的距离。 */
	private static final int OFFSET = 8;
	/** 面板与屏幕边缘的最小距离。 */
	private static final int MARGIN = 2;
	/** 附注文字色（RGB）：正文为纯白，附注色暗一档。 */
	private static final int NOTE_COLOR = 0xB0B0B0;

	/**
	 * 存疑说明行。{@link #lines} 只在确有区块对不上时把它追加在末尾；斜体与附注色内嵌在样式里，
	 * {@link #draw} 统一绘制。
	 */
	static final Component NOTE_DOUBTFUL = Component.translatable("msptmap.tooltip.doubtful")
			.withStyle(style -> style.withItalic(true).withColor(TextColor.fromRgb(NOTE_COLOR)));

	/**
	 * 各类耗时在详情与设置界面里的显示顺序。
	 *
	 * <p>与枚举顺序不同：枚举只许在末尾追加（协议按 ordinal 上线），这里按原版 tick 的实际先后排
	 * （{@code ServerLevel.tick}：计划刻 → 随机刻、刷怪 → 方块事件 → 实体 → 方块实体）。方块更新例外，
	 * 排在最后：其耗时已含在触发它的类别里（合计不计它），单列末尾并以附注样式（灰、斜体、带星）区别于
	 * 计入合计的正式行。
	 *
	 * <p>新增类别时此处也要加一项，否则设置界面与详情都不会显示它（顺序自定）。
	 */
	static final List<TickCategory> ORDER = List.of(
			TickCategory.SCHEDULED,
			TickCategory.RANDOM_TICK,
			TickCategory.SPAWN,
			TickCategory.BLOCK_EVENT,
			TickCategory.ENTITY,
			TickCategory.BLOCK_ENTITY,
			TickCategory.NEIGHBOR_UPDATE);

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
	 * 缓存：命中即省下约八次 {@code format} 与整串拼接。
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
					.append(ticket(chunk.loadTicket(), ClientConfig.tooltipTicketLoad, chunkX, chunkZ)));
			lines.add(Component.translatable("msptmap.tooltip.compute_level", chunk.computeLevel())
					.append(ticket(chunk.simTicket(), ClientConfig.tooltipTicketSim, chunkX, chunkZ)));
			doubtful = ticketDoubtful(chunk.loadTicket(), ClientConfig.tooltipTicketLoad)
					|| ticketDoubtful(chunk.simTicket(), ClientConfig.tooltipTicketSim);
		}
		if (ClientConfig.tooltipEntities) {
			// 与耗时无关的瞬时值：方块实体 / 实体 / 刷怪那几类耗时的成因多半在这里
			lines.add(Component.translatable("msptmap.tooltip.entities", chunk.entities()));
		}
		if (ClientConfig.tooltipTotal) {
			lines.add(msptLine(Component.translatable("msptmap.label.total"), Decimals.format3(chunk.mspt())));
		}
		for (TickCategory category : ORDER) {
			if (ClientConfig.tooltipCategory(category)) {
				lines.add(categoryLine(chunk.nanos()[category.ordinal()], windowTicks, category));
			}
		}
		if (doubtful) {
			lines.add(NOTE_DOUBTFUL);
		}
		return lines;
	}

	/** 「合计」行：单位固定显示，不受「显示单位」开关管。包内可见：扫描总览的「总卡顿」行也用它。 */
	static Component msptLine(Component label, String mspt) {
		return Component.translatable("msptmap.tooltip.mspt_line", label, mspt);
	}

	/** 各类明细行：单位是否显示由「显示单位」开关决定。包内可见：扫描总览的明细行也用它。 */
	static MutableComponent valueLine(Component label, String mspt) {
		return Component.translatable(ClientConfig.tooltipMsptUnit
				? "msptmap.tooltip.mspt_line" : "msptmap.tooltip.plain_line", label, mspt);
	}

	/**
	 * 某类的明细行。方块更新行附加注样式：合计不计它，以灰、斜体、带星区别于计入合计的其余行。
	 *
	 * <p>包内可见，吃纳秒而非区块：扫描总览的明细行取自跨维度合计的同类数组。
	 */
	static Component categoryLine(long nanos, int windowTicks, TickCategory category) {
		String value = ms(nanos, windowTicks);
		if (category != TickCategory.NEIGHBOR_UPDATE) {
			return valueLine(label(category), value);
		}
		return valueLine(label(category).copy().append("*"), value)
				.withStyle(style -> style.withItalic(true).withColor(TextColor.fromRgb(NOTE_COLOR)));
	}

	/**
	 * 等级行后半段：「 · 票名中心」或「 · 票名 @x,z」；存疑时是「 · 未知 *」。
	 * 未勾选该开关、或该链没有来源（理论上不应发生）时返回空组件。
	 */
	private static Component ticket(int code, boolean enabled, int chunkX, int chunkZ) {
		if (!enabled) {
			return Component.empty();
		}
		if (TicketSources.doubtful(code)) {
			return Component.translatable("msptmap.tooltip.ticket_doubtful");
		}
		int type = TicketSources.type(code);
		if (type == TicketSources.NONE) {
			return Component.empty();
		}
		Component name = Component.translatable(ticketName(type));
		if (type == TicketSources.PLAYER_LOADING) {
			// 26.2 的 player_loading 是逐区块铺的：视距内每格一张、等级相同，故此链上不存在「中心」
			// 与距离（每格算出来都是自己）。只写票名，不编造一个恒为 0 的距离。其余票种（forced /
			// portal / ender_pearl 等）是稀疏的，中心与距离才有意义。
			return Component.translatable("msptmap.tooltip.ticket_name", name);
		}
		// 中心判据与蓝框同源（TicketSources.isCenter），两处显示不会不一致
		if (TicketSources.isCenter(code)) {
			// 票就在本区块上：源头坐标即本区块坐标，不必重复写
			return Component.translatable("msptmap.tooltip.ticket_center", name);
		}
		return Component.translatable("msptmap.tooltip.ticket_at", name,
				chunkX + TicketSources.offsetX(code), chunkZ + TicketSources.offsetZ(code));
	}

	/** 该链存疑、且这一栏确实要显示。 */
	private static boolean ticketDoubtful(int code, boolean enabled) {
		return enabled && TicketSources.doubtful(code);
	}

	/** 加载票名称的语言键，与类型一一对应；新增票种时此处与语言文件需同步更新。包内可见：扫描总览的细分行也用它。 */
	static String ticketName(int type) {
		return switch (type) {
			case TicketSources.PLAYER_LOADING -> "msptmap.ticket.player_loading";
			case TicketSources.PLAYER_SIMULATION -> "msptmap.ticket.player_simulation";
			case TicketSources.FORCED -> "msptmap.ticket.forced";
			case TicketSources.PORTAL -> "msptmap.ticket.portal";
			case TicketSources.ENDER_PEARL -> "msptmap.ticket.ender_pearl";
			// 出生点票：1.21.10 及以前名为 start，1.21.11 起拆分为 player_spawn 与 spawn_search
			//? if >=1.21.11 {
			case TicketSources.PLAYER_SPAWN -> "msptmap.ticket.player_spawn";
			//?} else {
			/*case TicketSources.PLAYER_SPAWN -> "msptmap.ticket.start";
			*///?}
			case TicketSources.SPAWN_SEARCH -> "msptmap.ticket.spawn_search";
			case TicketSources.DRAGON -> "msptmap.ticket.dragon";
			default -> "msptmap.ticket.unknown";
		};
	}

	/**
	 * 面板位置：默认在鼠标右下角，右 / 下放不下则翻到另一侧，再收回屏幕内。返回 {@code {x, y}}。
	 *
	 * <p>单独拆出以便离线断言（贴边翻面是 {@link #draw} 中唯一容易算错之处）。
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
		MapPanel.draw(graphics, at[0], at[1], size, lines);
	}

	/**
	 * 各类的名称；「显示为缩写」开启时为其二字母缩写。两个键表都用 switch 而非数组：枚举新增类别时
	 * 会在编译期报错，不会静默错位。
	 *
	 * <p>包内可见：设置界面的勾选框也使用它，使详情与设置中的名称一致。
	 */
	static Component label(TickCategory category) {
		return Component.translatable(ClientConfig.tooltipAbbreviate ? abbrKey(category) : nameKey(category));
	}

	/** 类别名称的语言键。 */
	private static String nameKey(TickCategory category) {
		return switch (category) {
			case RANDOM_TICK -> "msptmap.category.random_tick";
			case SCHEDULED -> "msptmap.category.scheduled";
			case NEIGHBOR_UPDATE -> "msptmap.category.neighbor_update";
			case BLOCK_EVENT -> "msptmap.category.block_event";
			case BLOCK_ENTITY -> "msptmap.category.block_entity";
			case ENTITY -> "msptmap.category.entity";
			case SPAWN -> "msptmap.category.spawn";
		};
	}

	/** 类别二字母缩写的语言键；缩写为英文，两语言同值。 */
	private static String abbrKey(TickCategory category) {
		return switch (category) {
			case RANDOM_TICK -> "msptmap.category.abbr.random_tick";
			case SCHEDULED -> "msptmap.category.abbr.scheduled";
			case NEIGHBOR_UPDATE -> "msptmap.category.abbr.neighbor_update";
			case BLOCK_EVENT -> "msptmap.category.abbr.block_event";
			case BLOCK_ENTITY -> "msptmap.category.abbr.block_entity";
			case ENTITY -> "msptmap.category.abbr.entity";
			case SPAWN -> "msptmap.category.abbr.spawn";
		};
	}

	/** 纳秒 → 毫秒/tick 文本，与 MsptSampler 打印共用 {@link Decimals} 的口径。 */
	static String ms(long nanos, int windowTicks) {
		return Decimals.format3(nanos / 1_000_000.0 / Math.max(1, windowTicks));
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
		for (TickCategory category : ORDER) {
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
