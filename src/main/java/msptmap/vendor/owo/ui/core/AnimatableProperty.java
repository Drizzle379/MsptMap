package msptmap.vendor.owo.ui.core;

import msptmap.vendor.owo.util.Observable;

/**
 * 可观察的组件属性容器。
 *
 * @param <A> 属性值类型
 */
public class AnimatableProperty<A> extends Observable<A> {

    protected AnimatableProperty(A initial) {
        super(initial);
    }

    /**
     * 创建以给定初始值填充的属性容器
     */
    public static <A> AnimatableProperty<A> of(A initial) {
        return new AnimatableProperty<>(initial);
    }
}