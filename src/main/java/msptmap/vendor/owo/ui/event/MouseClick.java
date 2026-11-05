package msptmap.vendor.owo.ui.event;

/** 与版本无关的鼠标事件数据：1.21.9 前后的原版类型不同，统一在这里收口。 */
public record MouseClick(double x, double y, int button) {
}
