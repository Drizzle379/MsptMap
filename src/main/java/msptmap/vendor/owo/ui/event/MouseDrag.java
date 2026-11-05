package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;


public interface MouseDrag {
    boolean onMouseDrag(MouseClick click, double deltaX, double deltaY);

    static EventStream<MouseDrag> newStream() {
        return new EventStream<>(subscribers -> (click, deltaX, deltaY) -> {
            var anyTriggered = false;
            for (var subscriber : subscribers) {
                anyTriggered |= subscriber.onMouseDrag(click, deltaX, deltaY);
            }
            return anyTriggered;
        });
    }
}
