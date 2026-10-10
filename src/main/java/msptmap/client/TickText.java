package msptmap.client;

import msptmap.util.Decimals;
import msptmap.sampler.TickCategory;
import msptmap.sampler.TicketCode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.List;

/**
 * tick 数据的文本构件：类别显示顺序与名称、各类耗时行、加载票名，以及附注样式。
 *
 * <p>只产出文本组件、不持有状态，构造时不查语言表（可离线断言）。悬停详情与扫描总览都从这里取行，
 * 两者的显示口径因此总是一致。
 */
public final class TickText {
	/** 附注文字色（RGB）：正文为纯白，附注色暗一档。 */
	static final int NOTE_COLOR = 0xB0B0B0;
	/** 二级条目（明细、加载源、TOP5 数据行）的缩进。 */
	private static final String INDENT = "  ";

	/**
	 * 存疑说明行：斜体与附注色内嵌在样式里。悬停详情只在确有区块对不上时把它追加在末尾，绘制由调用方统一进行。
	 */
	static final Component DOUBTFUL_NOTE = Component.translatable("msptmap.tooltip.doubtful")
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

	private TickText() {
	}

	/** 「合计」行：单位固定显示，不受「显示单位」开关影响。 */
	static Component msptLine(Component label, String mspt) {
		return Component.translatable("msptmap.tooltip.mspt_line", label, mspt);
	}

	/**
	 * 各类明细行：单位是否显示由「显示单位」开关决定；数值可传 String（无色）或带样式的组件。返回
	 * 可变类型：方块更新行的斜体要补在返回值上（1.20.1 的 Component 接口没有 withStyle 变体）。
	 */
	private static MutableComponent valueLine(Component label, Object mspt) {
		return Component.translatable(ClientConfig.tooltipMsptUnit
				? "msptmap.tooltip.mspt_line" : "msptmap.tooltip.plain_line", label, mspt);
	}

	/** 数值组件的着色包装：扫描总览把梯度色只挂在数值上，名称、单位等其余部分不受影响。 */
	static Component colored(String value, int rgb) {
		return Component.literal(value).withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
	}

	/**
	 * 某类的明细行。方块更新行以斜体与行尾星号标示合计不计它；行色由调用方给——悬停详情经
	 * {@link #secondary} 得附注灰，扫描总览的着色版只给数值上梯度色。
	 *
	 * <p>包内可见，接收纳秒而非区块坐标：扫描总览的明细行取自跨维度合计的同类数组。
	 */
	static Component categoryLine(long nanos, int windowTicks, TickCategory category) {
		return categoryLine(category, ms(nanos, windowTicks));
	}

	/** 数值着色版（扫描总览）：梯度色只挂在数值上，名称、星号等保持外层样式。 */
	static Component categoryLine(long nanos, int windowTicks, TickCategory category, int msptRgb) {
		return categoryLine(category, colored(ms(nanos, windowTicks), msptRgb));
	}

	/** 拼行本体：value 为 String（无色）或带样式的组件。 */
	private static Component categoryLine(TickCategory category, Object value) {
		if (category != TickCategory.NEIGHBOR_UPDATE) {
			return valueLine(label(category), value);
		}
		return valueLine(label(category).copy().append("*"), value)
				.withStyle(style -> style.withItalic(true));
	}

	/**
	 * 二级条目：缩进两格、附注灰。悬停详情的七类明细与扫描总览的明细、加载源、TOP5 行共用
	 * （包内可见）。
	 */
	static Component secondary(Component content) {
		return Component.literal(INDENT).append(content)
				.withStyle(style -> style.withColor(TextColor.fromRgb(NOTE_COLOR)));
	}

	/** 等级行后半段（票名…）：附注灰，与前半段的白色正文相区分。 */
	private static Component note(Component content) {
		return content.copy().withStyle(style -> style.withColor(TextColor.fromRgb(NOTE_COLOR)));
	}

	/**
	 * 等级行后半段：票名——「玩家加载」「强制加载 @x,z」「玩家模拟中心」；存疑时是「未知 *」。
	 * 未勾选该开关、或该链没有来源（如无模拟票的弱加载区块）时返回空组件。
	 */
	static Component ticket(int code, boolean enabled, int chunkX, int chunkZ) {
		if (!enabled) {
			return Component.empty();
		}
		if (TicketCode.doubtful(code)) {
			return note(Component.translatable("msptmap.tooltip.ticket_doubtful"));
		}
		int type = TicketCode.type(code);
		if (type == TicketCode.NONE) {
			return Component.empty();
		}
		Component name = Component.translatable(ticketName(type));
		if (type == TicketCode.PLAYER_LOADING) {
			// player_loading 逐区块铺设：视距内每格一张、等级相同，故此链上不存在「中心」
			// 与距离（每格算出来都是自己）。只写票名，不构造恒为 0 的距离。其余票种（forced /
			// portal / ender_pearl 等）为稀疏票，中心与距离才有意义。
			return note(Component.translatable("msptmap.tooltip.ticket_name", name));
		}
		// 中心判据与蓝框同源（TicketCode.isCenter），两处显示不会不一致
		if (TicketCode.isCenter(code)) {
			// 票就在本区块上：源头坐标即本区块坐标，不必重复写
			return note(Component.translatable("msptmap.tooltip.ticket_center", name));
		}
		return note(Component.translatable("msptmap.tooltip.ticket_at", name,
				chunkX + TicketCode.offsetX(code), chunkZ + TicketCode.offsetZ(code)));
	}

	/** 该链存疑、且这一栏确实要显示。 */
	static boolean ticketDoubtful(int code, boolean enabled) {
		return enabled && TicketCode.doubtful(code);
	}

	/** 加载票名称的语言键，与类型一一对应；新增票种时此处与语言文件需同步更新。包内可见：扫描总览的加载源行也用它。 */
	static String ticketName(int type) {
		return switch (type) {
			case TicketCode.PLAYER_LOADING -> "msptmap.ticket.player_loading";
			case TicketCode.PLAYER_SIMULATION -> "msptmap.ticket.player_simulation";
			case TicketCode.FORCED -> "msptmap.ticket.forced";
			case TicketCode.PORTAL -> "msptmap.ticket.portal";
			case TicketCode.ENDER_PEARL -> "msptmap.ticket.ender_pearl";
			// 出生点票：1.21.10 及以前名为 start，1.21.11 起拆分为 player_spawn 与 spawn_search
			//? if >=1.21.11 {
			case TicketCode.PLAYER_SPAWN -> "msptmap.ticket.player_spawn";
			//?} else {
			/*case TicketCode.PLAYER_SPAWN -> "msptmap.ticket.start";
			*///?}
			case TicketCode.SPAWN_SEARCH -> "msptmap.ticket.spawn_search";
			case TicketCode.DRAGON -> "msptmap.ticket.dragon";
			default -> "msptmap.ticket.unknown";
		};
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
}
