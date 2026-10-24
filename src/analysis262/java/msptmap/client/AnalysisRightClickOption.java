package msptmap.client;

import msptmap.util.Ids;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.world.MapDimension;

import java.lang.reflect.Field;

/**
 * 世界地图右键菜单里的「在区块分析界面中显示」：以右键处的区块为初始目标打开分析界面。
 *
 * <p>右键坐标是 {@code GuiMap} 的私有字段，用反射读取（字段名在 Xaero 1.44–1.47 之间一致，
 * 与 {@link MapScreenHook} 的取法相同）；字段缺失时 {@link #available()} 为 false，菜单项不出现。
 */
public final class AnalysisRightClickOption extends RightClickOption {
	private static final Field RIGHT_CLICK_X = field("rightClickX");
	private static final Field RIGHT_CLICK_Z = field("rightClickZ");
	private static final Field RIGHT_CLICK_DIM = field("rightClickDim");

	private final GuiMap map;

	public AnalysisRightClickOption(GuiMap map, int index) {
		super("msptmap.analysis.menu.show", index, map);
		this.map = map;
	}

	/** 右键坐标可读时才提供菜单项。 */
	public static boolean available() {
		return RIGHT_CLICK_X != null && RIGHT_CLICK_Z != null;
	}

	@Override
	public void onAction(Screen screen) {
		AnalysisEntry.open(map, dimension(), block(RIGHT_CLICK_X) >> 4, block(RIGHT_CLICK_Z) >> 4);
	}

	/** 右键处的维度；Xaero 没记下时退回地图当前维度。 */
	private @Nullable String dimension() {
		Object key = value(RIGHT_CLICK_DIM);
		if (key instanceof ResourceKey<?> resourceKey) {
			@SuppressWarnings("unchecked")
			ResourceKey<Level> levelKey = (ResourceKey<Level>) resourceKey;
			return Ids.id(levelKey);
		}
		MapProcessor processor = map.getMapProcessor();
		if (processor == null || processor.getMapWorld() == null) {
			return null;
		}
		MapDimension current = processor.getMapWorld().getCurrentDimension();
		return current == null ? null : ChunkFocus.dimensionId(current);
	}

	private int block(Field field) {
		if (field == null) {
			return 0;
		}
		try {
			return field.getInt(map);
		} catch (IllegalAccessException e) {
			return 0;
		}
	}

	private Object value(Field field) {
		if (field == null) {
			return null;
		}
		try {
			return field.get(map);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static Field field(String name) {
		try {
			Field field = GuiMap.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}
}
