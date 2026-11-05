package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.base.BaseUIComponent;
import msptmap.vendor.owo.ui.core.OwoUIGraphics;
import msptmap.vendor.owo.ui.core.Sizing;

public class SpacerComponent extends BaseUIComponent {

    protected SpacerComponent(int percent) {
        this.sizing(Sizing.expand(percent));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {}
}