package msptmap.client;

import msptmap.sampler.TickCategory;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「悬停详情」子页：区块详情面板的内容开关，按三列分组——
 * 单行信息（5）| 显示方式与合计（3）| 类别明细（7）。
 *
 * <p>15 个开关原排两列、最高 10 行，把整页撑到 282px；三列后最高 7 行（147px），
 * 加标题与底部按钮也低于常见 GUI 高度下限 240px。
 */
class TooltipConfigScreen extends ConfigScreenBase {
	TooltipConfigScreen(Screen parent) {
		super(parent, Component.translatable("msptmap.config.group.tooltip"));
	}

	/** 一行开关：标签 + 当前值 + 写回动作。 */
	private record Entry(Component label, boolean selected, Consumer<Boolean> apply) {
	}

	@Override
	protected List<Column> planColumns() {
		return List.of(checkboxColumn(infoEntries()), checkboxColumn(displayEntries()),
				checkboxColumn(categoryEntries()));
	}

	/** 列1：单行信息的开关。 */
	private List<Entry> infoEntries() {
		return List.of(
				new Entry(Component.translatable("msptmap.config.coords"), ClientConfig.tooltipCoords,
						value -> ClientConfig.tooltipCoords = value),
				new Entry(Component.translatable("msptmap.config.levels"), ClientConfig.tooltipLevels,
						value -> ClientConfig.tooltipLevels = value),
				// 两个票开关紧跟在「等级」之后：它们是等级行的一部分，不是独立行
				new Entry(Component.translatable("msptmap.config.ticket_load"), ClientConfig.tooltipTicketLoad,
						value -> ClientConfig.tooltipTicketLoad = value),
				new Entry(Component.translatable("msptmap.config.ticket_sim"), ClientConfig.tooltipTicketSim,
						value -> ClientConfig.tooltipTicketSim = value),
				new Entry(Component.translatable("msptmap.config.entities"), ClientConfig.tooltipEntities,
						value -> ClientConfig.tooltipEntities = value));
	}

	/** 列2：整列数值的显示方式与合计。 */
	private List<Entry> displayEntries() {
		return List.of(
				// 「显示为缩写」置于「合计」之前：它改变的是整列类别名的显示方式
				new Entry(Component.translatable("msptmap.config.abbreviate"), ClientConfig.tooltipAbbreviate,
						value -> {
							ClientConfig.tooltipAbbreviate = value;
							// 各类勾选框的标签随显示方式更换，重建控件使其立即生效
							rebuildWidgets();
						}),
				new Entry(Component.translatable("msptmap.config.unit"), ClientConfig.tooltipMsptUnit,
						value -> ClientConfig.tooltipMsptUnit = value),
				new Entry(Component.translatable("msptmap.label.total"), ClientConfig.tooltipTotal,
						value -> ClientConfig.tooltipTotal = value));
	}

	/** 列3：七类明细的开关；类别与标签都随 {@link ChunkTooltip#ORDER} 走。 */
	private List<Entry> categoryEntries() {
		List<Entry> entries = new ArrayList<>();
		for (TickCategory category : ChunkTooltip.ORDER) {
			entries.add(new Entry(ChunkTooltip.label(category), ClientConfig.tooltipCategory(category),
					value -> ClientConfig.tooltipCategories[category.ordinal()] = value));
		}
		return entries;
	}

	/** 一列开关：宽度按列内最长的标签测量。 */
	private Column checkboxColumn(List<Entry> entries) {
		int widest = 0;
		List<Row> rows = new ArrayList<>();
		for (Entry entry : entries) {
			widest = Math.max(widest, font.width(entry.label()));
			rows.add(new Row(ROW, (x, y) -> addCheckbox(x, y, entry.label(), entry.selected(), entry.apply())));
		}
		return new Column(checkboxWidth(widest), rows);
	}

	/** 勾选框整宽 = 盒子 + 4 + 文字；1.20.4 及以前没有公开的盒子尺寸接口，盒子 + 间距按 24 估计。 */
	private int checkboxWidth(int widest) {
		//? if >=1.20.5 {
		int width = Checkbox.getBoxSize(font) + 4 + widest;
		//?} else {
		/*int width = 24 + widest;
		*///?}
		return width;
	}

}
