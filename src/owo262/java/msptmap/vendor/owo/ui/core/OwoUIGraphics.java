package msptmap.vendor.owo.ui.core;

import msptmap.vendor.owo.mixin.ui.access.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;

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