package world.event;

/**
 * Base class for all world events. Events state that something happened.
 * They serve three purposes:
 * 1. Other systems can react without tight coupling.
 * 2. The UI can display news.
 * 3. Tests and historical records can inspect causal sequences.
 */
public class WorldEvent {
    public final long eventId;
    public final long worldMinute;
    public final String type;

    public WorldEvent(long eventId, long worldMinute, String type) {
        this.eventId = eventId;
        this.worldMinute = worldMinute;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("WorldEvent[id=%d, minute=%d, type=%s]", eventId, worldMinute, type);
    }
}