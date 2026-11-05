package msptmap.vendor.owo.ui.core;

//? if >=26.1.2 {
import msptmap.vendor.owo.mixin.ui.access.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
//?} else if >=1.21.11 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
*///?} else if >=1.21.6 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
*///?}

//? if >=26.1.2 {
public class OwoUIGraphics extends GuiGraphicsExtractor {

    protected OwoUIGraphics(Minecraft client, GuiRenderState renderState, int mouseX, int mouseY) {
        super(client, renderState, mouseX, mouseY);
    }

    public static OwoUIGraphics of(GuiGraphicsExtractor graphics) {
        var owoContext = new OwoUIGraphics(
            Minecraft.getInstance(),
            graphics.guiRenderState,
            ((GuiGraphicsExtractorAccessor) graphics).owo$getMouseY(),
            ((GuiGraphicsExtractorAccessor) graphics).owo$getMouseX()
        );

        ((GuiGraphicsExtractorAccessor) owoContext).owo$setScissorStack(((GuiGraphicsExtractorAccessor) graphics).owo$getScissorStack());
        ((GuiGraphicsExtractorAccessor) owoContext).owo$setPose(((GuiGraphicsExtractorAccessor) graphics).owo$getPose());

        return owoContext;
    }

    /** 推导矩阵栈的便捷入口：不依赖接口注入，避免与其他内置 UI 库的同名接口冲突。 */
    public OwoUIGraphics pushMatrix() {
        this.pose().pushMatrix();
        return this;
    }

    public OwoUIGraphics popMatrix() {
        this.pose().popMatrix();
        return this;
    }

    public OwoUIGraphics translateMatrix(float x, float y) {
        this.pose().translate(x, y);
        return this;
    }

    public boolean intersectsScissor(PositionedRectangle other) {
        other = other.transform(new Matrix3x2f(this.pose()));

        var rect = this.scissorStack.peek();

        if (rect == null) return true;

        var pos = rect.position();

        return other.x() < pos.x() + rect.width()
            && other.x() + other.width() >= pos.x()
            && other.y() < pos.y() + rect.height()
            && other.y() + other.height() >= pos.y();
    }

    public void drawRectOutline(int x, int y, int width, int height, int color) {
        this.fill(RenderPipelines.GUI, x, y, x + width, y + 1, color);
        this.fill(RenderPipelines.GUI, x, y + height - 1, x + width, y + height, color);

        this.fill(RenderPipelines.GUI, x, y + 1, x + 1, y + height - 1, color);
        this.fill(RenderPipelines.GUI, x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
//?} else if >=1.21.11 {
/*public class OwoUIGraphics {

    protected final GuiGraphics graphics;

    protected OwoUIGraphics(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public static OwoUIGraphics of(GuiGraphics graphics) {
        return new OwoUIGraphics(graphics);
    }

    public Matrix3x2fStack pose() {
        return this.graphics.pose();
    }

    public OwoUIGraphics pushMatrix() {
        this.pose().pushMatrix();
        return this;
    }

    public OwoUIGraphics popMatrix() {
        this.pose().popMatrix();
        return this;
    }

    public OwoUIGraphics translateMatrix(float x, float y) {
        this.pose().translate(x, y);
        return this;
    }

    // 裁剪由原版 GuiGraphics 维护，这里不做额外剔除。
    public boolean intersectsScissor(PositionedRectangle other) {
        return true;
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        this.graphics.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        this.graphics.disableScissor();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        this.graphics.fill(x1, y1, x2, y2, color);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        this.graphics.drawString(font, text, x, y, color, shadow);
    }

    public void blit(RenderPipeline pipeline, Identifier texture,
                     int x, int y, float u, float v, int width, int height,
                     int uWidth, int vHeight, int textureWidth, int textureHeight) {
        this.graphics.blit(pipeline, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight);
    }

    public void drawRectOutline(int x, int y, int width, int height, int color) {
        this.fill(x, y, x + width, y + 1, color);
        this.fill(x, y + height - 1, x + width, y + height, color);
        this.fill(x, y + 1, x + 1, y + height - 1, color);
        this.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
*///?} else if >=1.21.6 {
/*public class OwoUIGraphics {

    protected final GuiGraphics graphics;

    protected OwoUIGraphics(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public static OwoUIGraphics of(GuiGraphics graphics) {
        return new OwoUIGraphics(graphics);
    }

    public Matrix3x2fStack pose() {
        return this.graphics.pose();
    }

    public OwoUIGraphics pushMatrix() {
        this.pose().pushMatrix();
        return this;
    }

    public OwoUIGraphics popMatrix() {
        this.pose().popMatrix();
        return this;
    }

    public OwoUIGraphics translateMatrix(float x, float y) {
        this.pose().translate(x, y);
        return this;
    }

    // 裁剪由原版 GuiGraphics 维护，这里不做额外剔除。
    public boolean intersectsScissor(PositionedRectangle other) {
        return true;
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        this.graphics.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        this.graphics.disableScissor();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        this.graphics.fill(x1, y1, x2, y2, color);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        this.graphics.drawString(font, text, x, y, color, shadow);
    }

    public void blit(RenderPipeline pipeline, ResourceLocation texture,
                     int x, int y, float u, float v, int width, int height,
                     int uWidth, int vHeight, int textureWidth, int textureHeight) {
        this.graphics.blit(pipeline, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight);
    }

    public void drawRectOutline(int x, int y, int width, int height, int color) {
        this.fill(x, y, x + width, y + 1, color);
        this.fill(x, y + height - 1, x + width, y + height, color);
        this.fill(x, y + 1, x + 1, y + height - 1, color);
        this.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
*///?}