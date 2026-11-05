package msptmap.vendor.owo.ui.component;

import msptmap.vendor.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * 内置裁剪版的组件工厂，只保留综合分析界面实际用到的组件。
 */
public final class UIComponents {

    private UIComponents() {}

    public static LabelComponent label(Component text) {
        return new LabelComponent(text);
    }

    //? if >=1.21.11 {
    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, 256, 256);
    }
    //?} else {
    /*public static TextureComponent texture(ResourceLocation texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public static TextureComponent texture(ResourceLocation texture, int u, int v, int regionWidth, int regionHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, 256, 256);
    }
    *///?}

    public static BoxComponent box(Sizing horizontalSizing, Sizing verticalSizing) {
        return new BoxComponent(horizontalSizing, verticalSizing);
    }

    public static SpacerComponent spacer(int percent) {
        return new SpacerComponent(percent);
    }

    public static SpacerComponent spacer() {
        return spacer(100);
    }
}