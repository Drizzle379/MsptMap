package msptmap.client.mixins;

import msptmap.client.MapScreenHook;
import msptmap.client.XaeroCompat;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * XaeroLib 屏幕基类上的挂点：把世界地图屏的初始化、渲染与点击转给 {@link MapScreenHook}。
 *
 * <p>不挂在 {@code GuiMap} 上：Mixin 重写该类时 ASM 要重算其全部方法的栈帧，超大方法在
 * Loader 0.20.0（ASM 9.11）下触发单类操作数上限而崩。挂在父类上对 GuiMap 的字节码零改动；
 * 其余 Xaero 屏幕也走这里，由 {@link XaeroCompat#isGuiMap} 过滤。
 */
@Mixin(value = xaero.lib.client.gui.ScreenBase.class, remap = false)
public abstract class ScreenBaseMixin {
	/** 初始化完成：世界地图在此重建本模组的按钮（见 MapScreenHook.onInit）。 */
	//? if >=26.1 {
	@Inject(method = "init", at = @At("TAIL"), remap = false)
	private void msptmap$init(CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onInit(screen);
		}
	}
	//?} else {
	/*@Inject(method = "method_25426", at = @At("TAIL"), remap = false)
	private void msptmap$init(CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onInit(screen);
		}
	}
	*///?}

	/** 渲染末尾：绘制总览与进度圈，并推进定位（见 MapScreenHook.onRender）。 */
	//? if >=26.1 {
	@Inject(method = "extractRenderState", at = @At("TAIL"), remap = false)
	private void msptmap$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onRender(screen, graphics, mouseX, mouseY);
		}
	}
	//?} else {
	/*@Inject(method = "method_25394", at = @At("TAIL"), remap = false)
	private void msptmap$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onRender(screen, graphics, mouseX, mouseY);
		}
	}
	*///?}

	/** 控件提示绘制末尾：在其上绘制悬停详情（见 MapScreenHook.onRenderTooltips）。 */
	//? if >=26.1 {
	@Inject(method = "renderTooltips", at = @At("TAIL"), remap = false)
	private void msptmap$renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfoReturnable<Boolean> cir) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onRenderTooltips(screen, graphics, mouseX, mouseY);
		}
	}
	//?} else {
	/*@Inject(method = "renderTooltips", at = @At("TAIL"), remap = false)
	private void msptmap$renderTooltips(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfoReturnable<Boolean> cir) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)) {
			MapScreenHook.onRenderTooltips(screen, graphics, mouseX, mouseY);
		}
	}
	*///?}

	/**
	 * 点击最前：总览与右侧列表命中即消费。GuiMap 在调用父类后据返回值决定是否开始拖图，
	 * 故在这里返回 true 足以拦下。
	 */
	//? if >=26.1 {
	@Inject(method = "mouseClicked", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		Screen screen = (Screen) (Object) this;
		if (!XaeroCompat.isGuiMap(screen)) {
			return;
		}
		//? if >=26.3 {
		/*boolean left = event.button() == 1;
		*///?} else {
		boolean left = event.button() == 0;
		//?}
		if (MapScreenHook.onClick(screen, (int) event.x(), (int) event.y(), left)) {
			cir.setReturnValue(true);
		}
	}
	//?} else if >=1.21.9 {
	/*@Inject(method = "method_25402", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen)
				&& MapScreenHook.onClick(screen, (int) event.x(), (int) event.y(), event.button() == 0)) {
			cir.setReturnValue(true);
		}
	}
	*///?} else {
	/*@Inject(method = "method_25402", at = @At("HEAD"), remap = false, cancellable = true)
	private void msptmap$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		Screen screen = (Screen) (Object) this;
		if (XaeroCompat.isGuiMap(screen) && MapScreenHook.onClick(screen, (int) mouseX, (int) mouseY, button == 0)) {
			cir.setReturnValue(true);
		}
	}
	*///?}
}
