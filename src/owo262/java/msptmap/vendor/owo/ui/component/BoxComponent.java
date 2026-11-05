package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.base.BaseUIComponent;
import msptmap.vendor.owo.ui.core.AnimatableProperty;
import msptmap.vendor.owo.ui.core.Color;
import msptmap.vendor.owo.ui.core.OwoUIGraphics;
import msptmap.vendor.owo.ui.core.Sizing;

/**
 * 单色矩形，可选择填充或描边。
 */
public class BoxComponent extends BaseUIComponent {

    protected boolean fill = false;
    protected final AnimatableProperty<Color> color = AnimatableProperty.of(Color.BLACK);

    public BoxComponent(Sizing horizontalSizing, Sizing verticalSizing) {
        this.sizing(horizontalSizing, verticalSizing);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        final int color = this.color.get().argb();

        if (this.fill) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, color);
        } else {
            graphics.drawRectOutline(this.x, this.y, this.width, this.height, color);
        }
    }

    /**
     * 设置填充或描边
     */
    public BoxComponent fill(boolean fill) {
        this.fill = fill;
        return this;
    }

    public boolean fill() {
        return this.fill;
    }

    public BoxComponent color(Color color) {
        this.color.set(color);
        return this;
    }

    public AnimatableProperty<Color> color() {
        return this.color;
    }
}