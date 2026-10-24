package msptmap.vendor.owo.util.pond;

import msptmap.vendor.owo.ui.core.PositionedRectangle;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

public interface OwoSlotExtension {

    void owo$setDisabledOverride(boolean disabled);

    boolean owo$getDisabledOverride();

    void owo$setScissorArea(@Nullable ScreenRectangle scissor);

    @Nullable ScreenRectangle owo$getScissorArea();
}
