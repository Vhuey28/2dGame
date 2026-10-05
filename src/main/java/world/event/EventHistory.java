package world.event;

import java.util.ArrayList;
import java.util.List;

/**
 * Retains events for UI and debugging. Uses three retention levels:
 * - Ephemeral: routine market matches, discarded after processing.
 * - Recent: retained for a fixed period for UI/debugging.
 * - Historical: births of important people, deaths, wars, titles, battles, treaties, settlement ownership.
 */
public class EventHistory {
    private final List<WorldEvent> recent = new ArrayList<>();
    private final List<WorldEvent> historical = new ArrayList<>();
    private static final int RECENT_RETENTION_MINUTES = 60 * 24 * 7; // 1 week

    /** Record an event. */
    public void record(WorldEvent event) {
        if (event == null) return;
        recent.add(event);
        historical.add(event);
    }

    /** Trim events older than the retention window from the recent list. */
    public void trim(long currentMinute) {
        recent.removeIf(e -> (currentMinute - e.worldMinute) > RECENT_RETENTION_MINUTES);
    }

    public List<WorldEvent> getRecent() {
        return recent;
    }

    public List<WorldEvent> getHistorical() {
        return historical;
    }

    public void clear() {
        recent.clear();
        historical.clear();
    }
}