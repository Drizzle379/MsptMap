package msptmap.vendor.owo.ui.event;

//? if >=26.3 {
/*import com.mojang.blaze3d.platform.InputConstants;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}

/** 与版本无关的键盘事件数据。 */
public record KeyInput(int key, int modifiers) {

    public boolean isCycleFocus() {
        //? if >=26.3 {
        /*return this.key == InputConstants.KEY_TAB;
        *///?} else {
        return this.key == GLFW.GLFW_KEY_TAB;
        //?}
    }

    public boolean isLeft() {
        //? if >=26.3 {
        /*return this.key == InputConstants.KEY_LEFT;
        *///?} else {
        return this.key == GLFW.GLFW_KEY_LEFT;
        //?}
    }

    public boolean isRight() {
        //? if >=26.3 {
        /*return this.key == InputConstants.KEY_RIGHT;
        *///?} else {
        return this.key == GLFW.GLFW_KEY_RIGHT;
        //?}
    }

    public boolean isUp() {
        //? if >=26.3 {
        /*return this.key == InputConstants.KEY_UP;
        *///?} else {
        return this.key == GLFW.GLFW_KEY_UP;
        //?}
    }

    public boolean isDown() {
        //? if >=26.3 {
        /*return this.key == InputConstants.KEY_DOWN;
        *///?} else {
        return this.key == GLFW.GLFW_KEY_DOWN;
        //?}
    }

    public boolean hasShiftDown() {
        //? if >=26.3 {
        /*// SDL_KMOD_LSHIFT | SDL_KMOD_RSHIFT
        return (this.modifiers & 3) != 0;
        *///?} else {
        return (this.modifiers & 1) != 0;
        //?}
    }

    public boolean hasAltDown() {
        //? if >=26.3 {
        /*// SDL_KMOD_LALT | SDL_KMOD_RALT
        return (this.modifiers & 768) != 0;
        *///?} else {
        return (this.modifiers & 4) != 0;
        //?}
    }
}