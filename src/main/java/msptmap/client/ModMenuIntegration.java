package msptmap.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mod Menu 入口：使「模组列表 → MsptMap → 设置」能打开 {@link MsptMapConfigScreen}。
 *
 * <p>本类是唯一接触 Mod Menu 的代码，且仅在 {@code modmenu} entrypoint 中被加载；未安装 Mod Menu
 * 的客户端不会加载它，故 modmenu 依赖为 compileOnly。未安装时可点地图上的设置按钮。
 */
public class ModMenuIntegration implements ModMenuApi {
	/** parent 为模组列表那一屏，「完成」需退回该屏。 */
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return (Screen parent) -> new MsptMapConfigScreen(parent);
	}
}
