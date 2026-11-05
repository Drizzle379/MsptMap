package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;

public interface MouseUp {
    boolean onMouseUp(MouseClick click);

    static EventStream<MouseUp> newStream() {
        return new EventStream<>(subscribers -> (click) -> {
            var anyTriggered = false;
            for (var subscriber : subscribers) {
                anyTriggered |= subscriber.onMouseUp(click);
            }
            return anyTriggered;
        });
    }
}
