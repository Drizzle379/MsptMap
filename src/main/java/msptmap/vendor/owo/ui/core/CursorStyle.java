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
     * The text selection, usually I-beam, cursor
     */
    TEXT(CursorTypes.IBEAM),

    /**
     * The hand cursor which signals clickable areas
     */
    HAND(CursorTypes.POINTING_HAND),

    /**
     * the Crosshair cursor
     */
    CROSSHAIR(CursorTypes.CROSSHAIR),

    /**
     * The cross-shaped cursor which signals
     * draggable/movable areas
     */
    MOVE(CursorTypes.RESIZE_ALL),

    /**
     * The horizontal resize cursor
     * @see #VERTICAL_RESIZE
     */
    HORIZONTAL_RESIZE(CursorTypes.RESIZE_EW),

    /**
     * The vertical resize cursor
     * @see #HORIZONTAL_RESIZE
     */
    VERTICAL_RESIZE(CursorTypes.RESIZE_NS),

    /**
     * The NorthWest-SouthEast resize cursor
     * @see #NESW_RESIZE
     *
     * @implNote 原版没有对角线标准光标，统一用四向缩放光标
     */
    NWSE_RESIZE(CursorTypes.RESIZE_ALL),

    /**
     * The NorthEast-SouthWest resize cursor
     * @see #NWSE_RESIZE
     *
     * @implNote 原版没有对角线标准光标，统一用四向缩放光标
     */
    NESW_RESIZE(CursorTypes.RESIZE_ALL),


    /**
     * The Not-Allowed cursor style
     *
     * @implNote This cursor style is not necessarily supported by all cursor themes
     */
    NOT_ALLOWED(CursorTypes.NOT_ALLOWED);


    public final CursorType cursor;

    CursorStyle(CursorType cursor) {this.cursor = cursor;}
}
//?} else {
/*public enum CursorStyle {
    NONE(0),
    POINTER(GLFW.GLFW_ARROW_CURSOR),
    TEXT(GLFW.GLFW_IBEAM_CURSOR),
    HAND(GLFW.GLFW_HAND_CURSOR),
    CROSSHAIR(GLFW.GLFW_CROSSHAIR_CURSOR),
    MOVE(GLFW.GLFW_RESIZE_ALL_CURSOR),
    HORIZONTAL_RESIZE(GLFW.GLFW_HRESIZE_CURSOR),
    VERTICAL_RESIZE(GLFW.GLFW_VRESIZE_CURSOR),
    NWSE_RESIZE(GLFW.GLFW_RESIZE_NWSE_CURSOR),
    NESW_RESIZE(GLFW.GLFW_RESIZE_NESW_CURSOR),
    NOT_ALLOWED(GLFW.GLFW_NOT_ALLOWED_CURSOR);

    public final int glfw;

    CursorStyle(int glfw) {this.glfw = glfw;}
}
*///?}