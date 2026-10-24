package msptmap.vendor.owo.ui.event;

import msptmap.vendor.owo.util.EventStream;
import net.minecraft.client.input.MouseButtonEvent;

public interface MouseDown {
    boolean onMouseDown(MouseButtonEvent click, boolean doubled);

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
