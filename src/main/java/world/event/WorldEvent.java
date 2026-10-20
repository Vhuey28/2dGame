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
    /**
     * Optional player-readable detail ("Kern declared war on Aldenmyr to conquer Wold").
     * Null when the publishing system has not been given a description yet; the UI then
     * falls back to a readable rendering of the type alone.
     */
    public final String detail;

    public WorldEvent(long eventId, long worldMinute, String type) {
        this(eventId, worldMinute, type, null);
    }

    public WorldEvent(long eventId, long worldMinute, String type, String detail) {
        this.eventId = eventId;
        this.worldMinute = worldMinute;
        this.type = type;
        this.detail = detail;
    }

    @Override
    public String toString() {
        return String.format("WorldEvent[id=%d, minute=%d, type=%s]", eventId, worldMinute, type);
    }
}