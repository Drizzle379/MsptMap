package msptmap.vendor.owo.ui.event;

/** 与版本无关的字符输入事件数据。 */
public record CharInput(char codePoint, int modifiers) {
}