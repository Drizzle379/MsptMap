package msptmap.vendor.owo.ui.util;

import com.mojang.blaze3d.platform.Window;
import msptmap.vendor.owo.ui.core.CursorStyle;
import net.minecraft.client.Minecraft;
//? if <1.21.9 {
/*import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
*///?}

//? if >=1.21.9 {
public class CursorAdapter {

    protected final Window window;

    protected CursorStyle lastCursorStyle = CursorStyle.POINTER;
    protected boolean disposed = false;

    protected CursorAdapter(Window window) {
        this.window = window;
    }

    public static CursorAdapter ofClientWindow() {
        return new CursorAdapter(Minecraft.getInstance().getWindow());
    }

    public static CursorAdapter ofWindow(Window window) {
        return new CursorAdapter(window);
    }

    public void applyStyle(CursorStyle style) {
        if (this.disposed || this.lastCursorStyle == style) return;

        this.window.selectCursor(style.cursor);
        this.lastCursorStyle = style;
    }

    public void dispose() {
        this.disposed = true;
    }

}
//?} else {
/*public class CursorAdapter {

    protected static final CursorStyle[] ACTIVE_STYLES = {CursorStyle.POINTER, CursorStyle.TEXT, CursorStyle.HAND, CursorStyle.CROSSHAIR, CursorStyle.MOVE, CursorStyle.HORIZONTAL_RESIZE, CursorStyle.VERTICAL_RESIZE, CursorStyle.NWSE_RESIZE, CursorStyle.NESW_RESIZE, CursorStyle.NOT_ALLOWED};

    protected final EnumMap<CursorStyle, Long> cursors = new EnumMap<>(CursorStyle.class);
    protected final long windowHandle;

    protected CursorStyle lastCursorStyle = CursorStyle.POINTER;
    protected boolean disposed = false;

    protected CursorAdapter(Window window) {
        this.windowHandle = window.getWindow();
        for (var style : ACTIVE_STYLES) {
            var pointer = GLFW.glfwCreateStandardCursor(style.glfw);
            if (pointer == 0) continue;

            this.cursors.put(style, pointer);
        }
    }

    public static CursorAdapter ofClientWindow() {
        return new CursorAdapter(Minecraft.getInstance().getWindow());
    }

    public static CursorAdapter ofWindow(Window window) {
        return new CursorAdapter(window);
    }

    public void applyStyle(CursorStyle style) {
        if (this.disposed || this.lastCursorStyle == style) return;

        if (style == CursorStyle.NONE) {
            GLFW.glfwSetCursor(this.windowHandle, 0);
        } else {
            GLFW.glfwSetCursor(this.windowHandle, this.cursors.getOrDefault(style, 0L));
        }
        this.lastCursorStyle = style;
    }

    public void dispose() {
        if (this.disposed) return;

        this.cursors.values().forEach(GLFW::glfwDestroyCursor);
        this.disposed = true;
    }

}
*///?}