package world.event;

/**
 * A future event scheduled by world time. Used for
 * pregnancy completion, contract deadlines, treaty expiration,
 * truce expiration, caravan arrival estimates, construction
 * completion, scheme phase completion, etc.
 */
public class ScheduledEvent implements Comparable<ScheduledEvent> {
    public final long dueMinute;
    public final String type;
    public final long id;

    public ScheduledEvent(long id, long dueMinute, String type) {
        this.id = id;
        this.dueMinute = dueMinute;
        this.type = type;
    }

    @Override
    public int compareTo(ScheduledEvent other) {
        return Long.compare(this.dueMinute, other.dueMinute);
    }
}