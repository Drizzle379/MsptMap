package msptmap.vendor.owo.mixin.ui.access;

import msptmap.vendor.owo.ui.base.BaseOwoContainerScreen;
import msptmap.vendor.owo.ui.core.OwoUIAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BaseOwoContainerScreen.class, remap = false)
public interface BaseOwoHandledScreenAccessor {
    @Accessor("uiAdapter")
    OwoUIAdapter<?> owo$getUIAdapter();
}
