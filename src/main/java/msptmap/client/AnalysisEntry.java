package msptmap.client;

//? if >=26.2 {
import msptmap.MsptMapMod;
import msptmap.util.Ids;
import msptmap.vendor.owo.ui.core.OwoUIPipelines;
import msptmap.vendor.owo.ui.util.NinePatchTexture;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 综合分析界面的入口：快捷键（默认不绑定，玩家可在控制设置中自设）与地图按钮共用 {@link #open}。
 */
public final class AnalysisEntry {
	private static @Nullable KeyMapping key;

	private AnalysisEntry() {
	}

	/** 客户端初始化时注册键位；默认键为 GLFW_KEY_UNKNOWN，即未绑定。 */
	public static void register() {
		// 内置 owo 的渲染管线与九宫格主题（原 OwoClient 的初始化里属于 UI 的部分）
		OwoUIPipelines.register();
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
				.registerReloadListener(new NinePatchTexture.MetadataLoader());

		KeyMapping.Category category = KeyMapping.Category.register(Ids.of(MsptMapMod.MOD_ID, "analysis"));
		key = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.msptmap.analysis", GLFW.GLFW_KEY_UNKNOWN, category));
	}

	/** 每个客户端 tick 检查一次；只在没有其他界面时响应，避免在聊天栏或设置里误开。 */
	public static void tick() {
		if (key == null) {
			return;
		}
		while (key.consumeClick()) {
			if (Minecraft.getInstance().gui.screen() == null) {
				open(null);
			}
		}
	}

	public static void open(@Nullable Screen parent) {
		Screens.show(Minecraft.getInstance(), new AnalysisScreen(parent));
	}
}
//?}
