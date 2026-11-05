package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;

public interface CharTyped {
    boolean onCharTyped(CharInput input);

    static EventStream<CharTyped> newStream() {
        return new EventStream<>(subscribers -> (input) -> {
            var anyTriggered = false;
            for (var subscriber : subscribers) {
                anyTriggered |= subscriber.onCharTyped(input);
            }
            return anyTriggered;
        });
    }
}
