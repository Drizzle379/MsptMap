package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.base.BaseUIComponent;
import msptmap.vendor.owo.ui.core.*;
import msptmap.vendor.owo.util.Observable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class LabelComponent extends BaseUIComponent {

    protected final Font textRenderer = Minecraft.getInstance().font;

    protected Component text;
    protected List<FormattedCharSequence> wrappedText;

    protected VerticalAlignment verticalTextAlignment = VerticalAlignment.TOP;
    protected HorizontalAlignment horizontalTextAlignment = HorizontalAlignment.LEFT;

    protected final AnimatableProperty<Color> color = AnimatableProperty.of(Color.WHITE);
    protected final Observable<Integer> lineHeight = Observable.of(this.textRenderer.lineHeight);
    protected final Observable<Integer> lineSpacing = Observable.of(2);
    protected boolean shadow;
    protected int maxWidth;

    protected LabelComponent(Component text) {
        this.text = text;
        this.wrappedText = new ArrayList<>();

        this.shadow = false;
        this.maxWidth = Integer.MAX_VALUE;

        Observable.observeAll(this::notifyParentIfMounted, this.lineHeight, this.lineSpacing);
    }

    public LabelComponent text(Component text) {
        this.text = text;
        this.notifyParentIfMounted();
        return this;
    }

    public Component text() {
        return this.text;
    }

    public LabelComponent maxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        this.notifyParentIfMounted();
        return this;
    }

    public int maxWidth() {
        return this.maxWidth;
    }

    public LabelComponent shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public boolean shadow() {
        return this.shadow;
    }

    public LabelComponent color(Color color) {
        this.color.set(color);
        return this;
    }

    public AnimatableProperty<Color> color() {
        return this.color;
    }

    public LabelComponent verticalTextAlignment(VerticalAlignment verticalAlignment) {
        this.verticalTextAlignment = verticalAlignment;
        return this;
    }

    public VerticalAlignment verticalTextAlignment() {
        return this.verticalTextAlignment;
    }

    public LabelComponent horizontalTextAlignment(HorizontalAlignment horizontalAlignment) {
        this.horizontalTextAlignment = horizontalAlignment;
        return this;
    }

    public HorizontalAlignment horizontalTextAlignment() {
        return this.horizontalTextAlignment;
    }

    public LabelComponent lineHeight(int lineHeight) {
        this.lineHeight.set(lineHeight);
        return this;
    }

    public int lineHeight() {
        return this.lineHeight.get();
    }

    public LabelComponent lineSpacing(int lineSpacing) {
        this.lineSpacing.set(lineSpacing);
        return this;
    }

    public int lineSpacing() {
        return this.lineSpacing.get();
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        int widestText = 0;
        for (var line : this.wrappedText) {
            int width = this.textRenderer.width(line);
            if (width > widestText) widestText = width;
        }

        if (widestText > this.maxWidth) {
            this.wrapLines();
            return this.determineHorizontalContentSize(sizing);
        } else {
            return widestText;
        }
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        this.wrapLines();
        return this.textHeight();
    }

    @Override
    public void inflate(Size space) {
        this.wrapLines();
        super.inflate(space);
    }

    private void wrapLines() {
        this.wrappedText = this.textRenderer.split(this.text, this.horizontalSizing.get().isContent() ? this.maxWidth : this.width);
    }

    protected int textHeight() {
        return (this.wrappedText.size() * (this.lineHeight() + this.lineSpacing())) - this.lineSpacing();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics
            .pushMatrix()
            .translateMatrix(0, 1f / Minecraft.getInstance().getWindow().getGuiScale());

        this.drawText((renderX, renderY, text, shadow, color) -> {
            graphics.text(
                Minecraft.getInstance().font,
                text,
                renderX,
                renderY,
                color.argb(),
                shadow
            );
        });

        graphics.popMatrix();
    }

    protected void drawText(LabelDrawFunction goodFunction) {
        int x = this.x;
        int y = this.y;

        if (this.horizontalSizing.get().isContent()) {
            x += this.horizontalSizing.get().value;
        }
        if (this.verticalSizing.get().isContent()) {
            y += this.verticalSizing.get().value;
        }

        switch (this.verticalTextAlignment) {
            case CENTER -> y += (this.height - (this.textHeight())) / 2;
            case BOTTOM -> y += this.height - (this.textHeight());
        }

        final int lambdaX = x;
        final int lambdaY = y;

        for (int i = 0; i < this.wrappedText.size(); i++) {
            var renderText = this.wrappedText.get(i);
            int renderX = lambdaX;

            switch (this.horizontalTextAlignment) {
                case CENTER -> renderX += (this.width - this.textRenderer.width(renderText)) / 2;
                case RIGHT -> renderX += this.width - this.textRenderer.width(renderText);
            }

            int renderY = lambdaY + i * (this.lineHeight() + this.lineSpacing());
            renderY += this.lineHeight() - this.textRenderer.lineHeight;

            goodFunction.draw(renderX, renderY, renderText, this.shadow, this.color.get());
        }
    }

    @FunctionalInterface
    protected interface LabelDrawFunction {
        void draw(int renderX, int renderY, FormattedCharSequence text, boolean shadow, Color color);
    }
}
