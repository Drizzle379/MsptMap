package msptmap.vendor.owo.ui.event;

/** 与版本无关的字符输入事件数据；code point 用 int，避免截断 BMP 外字符。 */
public record CharInput(int codePoint) {
}
