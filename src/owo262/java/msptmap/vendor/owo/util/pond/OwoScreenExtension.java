package msptmap.vendor.owo.util.pond;

import msptmap.vendor.owo.ui.core.ParentUIComponent;
import msptmap.vendor.owo.ui.layers.Layer;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * 注入到原版 Screen 上的图层扩展。braid 相关的状态已随裁剪移除。
 */
public interface OwoScreenExtension {
    List<Layer<?, ?>.Instance> owo$getInstancesView();

    <S extends Screen, R extends ParentUIComponent> Layer<S, R>.Instance owo$getInstance(Layer<S, R> layer);

    void owo$updateLayers();
}
