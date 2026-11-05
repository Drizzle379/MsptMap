package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;

public interface MouseLeave {
    void onMouseLeave();

    static EventStream<MouseLeave> newStream() {
        return new EventStream<>(subscribers -> () -> {
            for (var subscriber : subscribers) {
                subscriber.onMouseLeave();
            }
        });
    }
}
