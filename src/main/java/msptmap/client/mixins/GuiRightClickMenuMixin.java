package msptmap.client.mixins;

import msptmap.client.AnalysisRightClickOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/**
 * 世界地图右键菜单：追加「在区块分析界面中显示」。
 *
 * <p>不 Mixin {@code GuiMap}（其超大方法在 Loader 0.20.0 下会触发 ASM 操作数上限，见
 * {@code ScreenBaseMixin}），改为重定向 {@code GuiRightClickMenu.getMenu} 里取选项列表的调用：
 * 只重写这个小组件类，同时能拿到右键目标与列表。
 */
@Mixin(value = GuiRightClickMenu.class, remap = false)
public class GuiRightClickMenuMixin {
	@Redirect(method = "getMenu", at = @At(value = "INVOKE",
			target = "Lxaero/map/gui/IRightClickableElement;getRightClickOptions()Ljava/util/ArrayList;"),
			remap = false)
	private static ArrayList<RightClickOption> msptmap$addAnalysisOption(IRightClickableElement target) {
		ArrayList<RightClickOption> options = target.getRightClickOptions();
		if (target instanceof GuiMap map && AnalysisRightClickOption.available()) {
			// index 是列表下标，选项必须追加在末尾
			options.add(new AnalysisRightClickOption(map, options.size()));
		}
		return options;
	}
}
