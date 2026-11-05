package msptmap.vendor.owo.ui.core;

//? if >=1.21.9 {
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

//? if >=1.21.9 {
public enum CursorStyle {
    /**
     * The default cursor style defined by
     * the operating system
     */
    NONE(CursorType.DEFAULT),
    /**
     * The default arrow-style pointing cursor
     */
    POINTER(CursorTypes.ARROW),

    /**
     * The hand cursor which signals clickable areas
     */
    HAND(CursorTypes.POINTING_HAND);

    public final CursorType cursor;

    CursorStyle(CursorType cursor) {this.cursor = cursor;}
}
//?} else {
/*public enum CursorStyle {
    NONE(0),
    POINTER(GLFW.GLFW_ARROW_CURSOR),
    HAND(GLFW.GLFW_HAND_CURSOR);

    public final int glfw;

    CursorStyle(int glfw) {this.glfw = glfw;}
}
*///?}
