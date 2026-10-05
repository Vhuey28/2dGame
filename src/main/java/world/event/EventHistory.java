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
    private static final int MAX_HISTORICAL_EVENTS = 10_000;

    /** Record an event. Routine events remain recent; durable events are capped for long campaigns. */
    public void record(WorldEvent event) {
        if (event == null) return;
        recent.add(event);
        if (isHistorical(event.type)) {
            historical.add(event);
            if (historical.size() > MAX_HISTORICAL_EVENTS) {
                historical.subList(0, historical.size() - MAX_HISTORICAL_EVENTS).clear();
            }
        }
    }

    private boolean isHistorical(String type) {
        String value = type == null ? "" : type;
        return value.contains("WAR") || value.contains("BATTLE") || value.contains("SIEGE")
                || value.contains("PEACE") || value.contains("TREATY") || value.contains("TITLE")
                || value.contains("SUCCESSION") || value.contains("BIRTH") || value.contains("DEATH")
                || value.contains("SETTLEMENT_CAPTURED") || value.contains("REALM");
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