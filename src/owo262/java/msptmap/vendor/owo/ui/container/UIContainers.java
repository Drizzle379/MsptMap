package msptmap.vendor.owo.ui.container;

import msptmap.vendor.owo.ui.core.Sizing;
import msptmap.vendor.owo.ui.core.UIComponent;

public final class UIContainers {

    private UIContainers() {}

    public static FlowLayout verticalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.VERTICAL);
    }

    public static FlowLayout horizontalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.HORIZONTAL);
    }

    public static StackLayout stack(Sizing horizontalSizing, Sizing verticalSizing) {
        return new StackLayout(horizontalSizing, verticalSizing);
    }

    public static <C extends UIComponent> ScrollContainer<C> horizontalScroll(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        return new ScrollContainer<>(ScrollContainer.ScrollDirection.HORIZONTAL, horizontalSizing, verticalSizing, child);
    }
}