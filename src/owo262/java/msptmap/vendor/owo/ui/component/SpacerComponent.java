package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.base.BaseUIComponent;
import msptmap.vendor.owo.ui.core.OwoUIGraphics;
import msptmap.vendor.owo.ui.core.Sizing;
import msptmap.vendor.owo.ui.parsing.UIParsing;
import org.w3c.dom.Element;

public class SpacerComponent extends BaseUIComponent {

    protected SpacerComponent(int percent) {
        this.sizing(Sizing.expand(percent));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {}

    public static SpacerComponent parse(Element element) {
        if (!element.hasAttribute("percent")) return UIComponents.spacer();
        return UIComponents.spacer(UIParsing.parseUnsignedInt(element.getAttributeNode("percent")));
    }
}
