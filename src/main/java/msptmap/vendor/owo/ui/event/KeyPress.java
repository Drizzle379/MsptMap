package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;

public interface KeyPress {
    boolean onKeyPress(KeyInput input);

    static EventStream<KeyPress> newStream() {
        return new EventStream<>(subscribers -> (input) -> {
            var anyTriggered = false;
            for (var subscriber : subscribers) {
                anyTriggered |= subscriber.onKeyPress(input);
            }
            return anyTriggered;
        });
    }
}
