package msptmap.vendor.owo.ui.core;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

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