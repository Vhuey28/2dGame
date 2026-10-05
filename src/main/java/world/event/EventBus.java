package world.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple publish/subscribe event bus. Systems publish events; other systems
 * may subscribe to types of interest.
 */
public class EventBus {
    private final java.util.Map<String, List<Consumer<WorldEvent>>> subscribers = new java.util.HashMap<>();
    private final EventHistory history;

    public EventBus() {
        this.history = new EventHistory();
    }

    public EventBus(EventHistory history) {
        this.history = history;
    }

    /** Get the associated event history. */
    public EventHistory getHistory() {
        return history;
    }

    /** Subscribe a handler for a specific event type. */
    public void subscribe(String type, Consumer<WorldEvent> handler) {
        subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
    }

    /** Publish an event to all subscribers of its type. */
    public void publish(WorldEvent event) {
        if (event == null) return;
        history.record(event);
        List<Consumer<WorldEvent>> handlers = subscribers.get(event.type);
        if (handlers != null) {
            for (Consumer<WorldEvent> h : handlers) {
                h.accept(event);
            }
        }
    }
}