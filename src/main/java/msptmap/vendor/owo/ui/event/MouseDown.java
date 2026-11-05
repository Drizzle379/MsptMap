package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;

public interface MouseDown {
    boolean onMouseDown(MouseClick click, boolean doubled);

    static EventStream<MouseDown> newStream() {
        return new EventStream<>(subscribers -> (click, doubled) -> {
            var anyTriggered = false;
            for (var subscriber : subscribers) {
                anyTriggered |= subscriber.onMouseDown(click, doubled);
            }
            return anyTriggered;
        });
    }
}
