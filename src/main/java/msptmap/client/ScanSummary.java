package msptmap.client;

import msptmap.Decimals;
import msptmap.sampler.MsptSampler;
import msptmap.sampler.TickCategory;
import msptmap.sampler.TicketSources;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描总览：挂在扫描按钮右侧的数据框，扫描成功（收到 DONE 包）后出现，清屏后消失；展开与否则由地图
 * 上的折叠钮决定，跨次记忆（见 {@link ClientConfig#summaryExpanded}）。
 *
 * <p>数据是三个维度的合计（见 {@link ClientSnapshot.Totals}）：Xaero 地图一次只显示一个维度，
 * 总览给的是全局视角。组装与绘制分离（同 {@link ChunkTooltip}）：{@link #lines} 只拼文本组件
 * （可离线断言），{@link #draw} 只负责绘制。文本一律经 {@link Component#translatable} 引用语言
 * 文件里的键，翻译在绘制时才按当前语言解析。
 */
public final class ScanSummary {
	/**
	 * 细分行的显示顺序（票类型序号），越具体的来源越靠前，同 {@link TicketSources#priority}。
	 *
	 * <p>player_loading 不在表内：它逐区块铺、没有中心（见 {@link TicketSources#isCenter}），
	 * 不会计入加载源。
	 */
	private static final int[] SOURCE_ORDER = {
			TicketSources.FORCED,
			TicketSources.ENDER_PEARL,
			TicketSources.PORTAL,
			TicketSources.DRAGON,
			TicketSources.PLAYER_SPAWN,
			TicketSources.SPAWN_SEARCH,
			TicketSources.PLAYER_SIMULATION,
			TicketSources.UNKNOWN,
			TicketSources.UNRECOGNIZED,
	};

	/** 上次拼行结果与其输入：合计（引用即快照代次）与配置签名，两者全同则复用。 */
	private static ClientSnapshot.Totals cachedTotals;
	/** 初值 -1：与任何真实签名（位掩码，≥ 0）都不同，首帧不会误命中。 */
	private static int cachedSignature = -1;
	private static Built cached = new Built(List.of(), List.of(), -1);

	/**
	 * 拼行结果：{@code lines} 为全部行，{@code heaviest} 逐项对应 TOP3 数据行，{@code topFirstRow}
	 * 为首条 TOP3 行行号（-1 无分区）。悬停高亮与点击定位由后两者推得鼠标下是哪一行。
	 */
	private record Built(List<Component> lines, List<ClientSnapshot.Heavy> heaviest, int topFirstRow) {
	}

	private ScanSummary() {
	}

	/**
	 * 总览要显示的每一行；未扫描、已清屏（合计取不到）时返回空列表，调用方见到空列表即不画面板。
	 *
	 * <p>每帧都会被调用到这里，而输入（合计、两个开关）在两次绘制之间通常不变，故按输入缓存。
	 */
	public static List<Component> lines() {
		ClientSnapshot.Totals totals = ClientSnapshot.totals();
		if (totals == null) {
			return List.of();
		}
		int signature = signature();
		if (totals == cachedTotals && signature == cachedSignature) {
			return cached.lines();
		}
		cachedTotals = totals;
		cachedSignature = signature;
		cached = buildLines(totals);
		return cached.lines();
	}

	/** 拼行本体（不含缓存）。 */
	private static Built buildLines(ClientSnapshot.Totals totals) {
		int windowTicks = totals.windowTicks();
		List<Component> lines = new ArrayList<>();
		lines.add(title("msptmap.summary.title"));
		// 方括号内为本段窗口测到过耗时的区块数，· 后为窗口实际秒数
		lines.add(Component.translatable("msptmap.summary.chunks", totals.chunks(), totals.timed(),
				windowTicks / MsptSampler.TICKS_PER_SECOND));
		lines.add(Component.translatable("msptmap.summary.entities", totals.entities()));
		lines.add(Component.translatable("msptmap.summary.sources", totals.sources()));
		// 细分行：与「加载源」同源（见 ClientSnapshot.accept），各项之和恒等于其数；为 0 的票种不列
		for (int type : SOURCE_ORDER) {
			int count = totals.sourcesByType()[type];
			if (count > 0) {
				lines.add(ChunkTooltip.secondary(Component.translatable("msptmap.summary.source_item",
						Component.translatable(ChunkTooltip.ticketName(type)), count)));
			}
		}
		// 总卡顿：单位固定显示（同悬停详情的「合计」行）
		lines.add(ChunkTooltip.msptLine(Component.translatable("msptmap.summary.total"),
				ChunkTooltip.ms(totals.totalNanos(), windowTicks)));
		// 七类明细：整体缩进；方块更新的附注样式（灰、斜体、带星）内嵌在行组件里
		for (TickCategory category : ChunkTooltip.ORDER) {
			lines.add(ChunkTooltip.secondary(ChunkTooltip.categoryLine(
					totals.categoryNanos()[category.ordinal()], windowTicks, category)));
		}
		// 卡顿区块TOP3：分区标题白字不加粗；一个都没有时连分区标题都不出现
		List<ClientSnapshot.Heavy> heaviest = totals.heaviest();
		int topFirstRow = -1;
		if (!heaviest.isEmpty()) {
			lines.add(Component.translatable("msptmap.summary.top_title"));
			topFirstRow = lines.size();
			for (ClientSnapshot.Heavy heavy : heaviest) {
				lines.add(ChunkTooltip.secondary(Component.translatable("msptmap.summary.top_line",
						Decimals.format3(heavy.totalNanos() / 1_000_000.0 / windowTicks),
						Component.translatable(dimensionKey(heavy.dimension())),
						heavy.chunkX(), heavy.chunkZ())));
			}
		}
		return new Built(lines, heaviest, topFirstRow);
	}

	/** 标题行：加粗白字，不缩进。 */
	private static Component title(String key) {
		return Component.translatable(key).withStyle(style -> style.withBold(true));
	}

	/**
	 * TOP3 行维度名的语言键：三个原版维度用本模组的短名（中文「主世界 / 下界 / 末地」；原版英文
	 * 「The Nether / The End」是两个词，这里改用一词的 Nether / End）；其余维度回退原版键
	 * {@code dimension.<命名空间>.<路径>}（点号形式，同 Util.makeDescriptionId）。
	 */
	private static String dimensionKey(String dimId) {
		return switch (dimId) {
			case "minecraft:overworld" -> "msptmap.dimension.overworld";
			case "minecraft:the_nether" -> "msptmap.dimension.nether";
			case "minecraft:the_end" -> "msptmap.dimension.end";
			default -> "dimension." + dimId.replace(':', '.');
		};
	}

	/**
	 * 参与拼行的配置项压成位掩码：与悬停详情共用的两个开关（类别名缩写、明细单位）各占一位，
	 * 任一开关变动则签名改变，缓存随之作废。
	 *
	 * <p>类别勾选不进签名：它只决定悬停详情显示哪几行，总览的七类明细恒全列。
	 */
	private static int signature() {
		int bits = 0;
		if (ClientConfig.tooltipAbbreviate) {
			bits |= 1 << 0;
		}
		if (ClientConfig.tooltipMsptUnit) {
			bits |= 1 << 1;
		}
		return bits;
	}

	/**
	 * 以 ({@code x}, {@code y}) 为左上角绘制总览。无行可画时直接返回，不留空框；鼠标下的 TOP3
	 * 数据行垫一层淡白底，提示可点击定位。
	 */
	//? if >=26.1 {
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
	//?} else {
	/*public static void draw(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
	*///?}
		List<Component> lines = lines();
		if (lines.isEmpty()) {
			return;
		}
		int[] size = MapPanel.size(lines);
		MapPanel.draw(graphics, x, y, size, lines, summaryRowAt(mouseX, mouseY, x, y, size));
	}

	/**
	 * 鼠标是否落在总览框上（收起、无数据时恒 false）。悬停详情绘制在总览之上，重叠处让其不画。
	 * {@code panelX}/{@code panelY} 是总览左上角，与 {@link #draw} 的调用处同一组坐标。
	 */
	public static boolean overPanel(int mouseX, int mouseY, int panelX, int panelY) {
		if (!ClientConfig.summaryExpanded) {
			return false;
		}
		List<Component> lines = lines();
		if (lines.isEmpty()) {
			return false;
		}
		int[] size = MapPanel.size(lines);
		return mouseX >= panelX && mouseX < panelX + size[0] && mouseY >= panelY && mouseY < panelY + size[1];
	}

	/**
	 * 鼠标下 TOP3 行指向的区块；未指向任何 TOP3 行（含没有数据的空面板）时返回 null。
	 * {@code panelX}/{@code panelY} 是总览左上角，与 {@link #draw} 的调用处同一组坐标。
	 */
	public static ClientSnapshot.Heavy hitTarget(int mouseX, int mouseY, int panelX, int panelY) {
		if (ClientSnapshot.totals() == null) {
			return null;
		}
		int[] size = MapPanel.size(lines());
		int row = summaryRowAt(mouseX, mouseY, panelX, panelY, size);
		return row < 0 ? null : cached.heaviest().get(row - cached.topFirstRow());
	}

	/** 鼠标下的 TOP3 数据行行号；未命中返回 -1。{@code size} 由 {@link MapPanel#size} 算出。 */
	private static int summaryRowAt(int mouseX, int mouseY, int panelX, int panelY, int[] size) {
		if (cached.topFirstRow() < 0) {
			return -1;
		}
		int row = MapPanel.rowAt(mouseX, mouseY, panelX, panelY, size);
		if (row < cached.topFirstRow() || row >= cached.topFirstRow() + cached.heaviest().size()) {
			return -1;
		}
		return row;
	}
}
