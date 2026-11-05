package msptmap.vendor.owo.ui.util;

import com.mojang.blaze3d.platform.Window;
import msptmap.vendor.owo.ui.core.CursorStyle;
import net.minecraft.client.Minecraft;

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