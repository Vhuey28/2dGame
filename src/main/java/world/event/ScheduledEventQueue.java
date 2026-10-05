package world.event;

import java.util.PriorityQueue;

/**
 * Priority queue of scheduled events ordered by due world time.
 * Avoids checking every person and agreement every frame.
 */
public class ScheduledEventQueue {
    private final PriorityQueue<ScheduledEvent> queue = new PriorityQueue<>();

    /** Add a new scheduled event. */
    public void enqueue(ScheduledEvent event) {
        if (event == null) return;
        queue.add(event);
    }

    /** Remove and return the next event, or null if queue is empty. */
    public ScheduledEvent poll() {
        return queue.poll();
    }

    /** Peek at the next event without removing it. */
    public ScheduledEvent peek() {
        return queue.peek();
    }

    /** Get all events due at or before the given minute. */
    public java.util.List<ScheduledEvent> getDueEvents(long currentMinute) {
        java.util.List<ScheduledEvent> due = new java.util.ArrayList<>();
        while (!queue.isEmpty() && queue.peek().dueMinute <= currentMinute) {
            due.add(queue.poll());
        }
        return due;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}