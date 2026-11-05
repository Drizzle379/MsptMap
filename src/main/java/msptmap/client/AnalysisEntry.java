package msptmap.client;

//? if >=1.21.6 {
import msptmap.MsptMapMod;
import msptmap.util.Ids;
//? if >=26.1.2 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else if >=1.21.6 {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

/**
 * 综合分析界面的入口：快捷键（默认不绑定，玩家可在控制设置中自设）与地图按钮共用 {@link #open}。
 */
public final class AnalysisEntry {
	private static @Nullable KeyMapping key;

	private AnalysisEntry() {
	}

	/** 客户端初始化时注册键位；默认键为 UNKNOWN，即未绑定。 */
	public static void register() {
		int defaultKey =
				//? if >=26.3 {
				InputConstants.UNKNOWN.getValue();
				//?} else {
				/*GLFW.GLFW_KEY_UNKNOWN;
				*///?}
		//? if >=26.1.2 {
		KeyMapping.Category category = KeyMapping.Category.register(Ids.of(MsptMapMod.MOD_ID, "analysis"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.msptmap.analysis", defaultKey, category));
		//?} else if >=1.21.9 {
		/*KeyMapping.Category category = KeyMapping.Category.register(Ids.of(MsptMapMod.MOD_ID, "analysis"));
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.msptmap.analysis", defaultKey, category));
		*///?} else {
		/*key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.msptmap.analysis", defaultKey, "key.categories.msptmap.analysis"));
		*///?}
	}

	/** 每个客户端 tick 检查一次；只在没有其他界面时响应，避免在聊天栏或设置里误开。 */
	public static void tick() {
		if (key == null) {
			return;
		}
		while (key.consumeClick()) {
			//? if >=26.2 {
			if (Minecraft.getInstance().gui.screen() == null) {
			//?} else {
			/*if (Minecraft.getInstance().screen == null) {
			*///?}
				open(null);
			}
		}
	}

	public static void open(@Nullable Screen parent) {
		Screens.show(Minecraft.getInstance(), new AnalysisScreen(parent));
	}

	/** 从地图右键进入：以指定维度与区块为初始目标（dimension 为 null 时按坐标查找）。 */
	public static void open(@Nullable Screen parent, @Nullable String dimension, int chunkX, int chunkZ) {
		Screens.show(Minecraft.getInstance(), new AnalysisScreen(parent, dimension, chunkX, chunkZ));
	}
}
//?}