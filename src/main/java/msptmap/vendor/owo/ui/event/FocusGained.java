package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.ui.core.UIComponent;
import msptmap.vendor.owo.util.EventStream;

public interface FocusGained {
    void onFocusGained(UIComponent.FocusSource source);

    static EventStream<FocusGained> newStream() {
        return new EventStream<>(subscribers -> source -> {
            for (var subscriber : subscribers) {
                subscriber.onFocusGained(source);
            }
        });
    }
}
