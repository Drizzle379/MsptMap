package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.base.BaseUIComponent;
import msptmap.vendor.owo.ui.core.*;
import net.minecraft.client.renderer.RenderPipelines;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

public class TextureComponent extends BaseUIComponent {

    //? if >=1.21.11 {
    protected final Identifier texture;
    //?} else {
    /*protected final ResourceLocation texture;
    *///?}
    protected final int u, v;
    protected final int regionWidth, regionHeight;
    protected final int textureWidth, textureHeight;

    protected final AnimatableProperty<PositionedRectangle> visibleArea;

    //? if >=1.21.11 {
    protected TextureComponent(Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
    //?} else {
    /*protected TextureComponent(ResourceLocation texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
    *///?}
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;

        this.visibleArea = AnimatableProperty.of(PositionedRectangle.of(0, 0, this.regionWidth, this.regionHeight));
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return this.regionWidth;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.regionHeight;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(this.width / (float) this.regionWidth, this.height / (float) this.regionHeight);

        var visibleArea = this.visibleArea.get();

        int bottomEdge = Math.min(visibleArea.y() + visibleArea.height(), regionHeight);
        int rightEdge = Math.min(visibleArea.x() + visibleArea.width(), regionWidth);

        graphics.blit(RenderPipelines.GUI_TEXTURED,
            this.texture,
            visibleArea.x(),
            visibleArea.y(),
            this.u + visibleArea.x(),
            this.v + visibleArea.y(),
            rightEdge - visibleArea.x(),
            bottomEdge - visibleArea.y(),
            rightEdge - visibleArea.x(),
            bottomEdge - visibleArea.y(),
            this.textureWidth, this.textureHeight
        );

        matrices.popMatrix();
    }

    public TextureComponent visibleArea(PositionedRectangle visibleArea) {
        this.visibleArea.set(visibleArea);
        return this;
    }

    public TextureComponent resetVisibleArea() {
        this.visibleArea(PositionedRectangle.of(0, 0, this.regionWidth, this.regionHeight));
        return this;
    }

    public AnimatableProperty<PositionedRectangle> visibleArea() {
        return this.visibleArea;
    }
}