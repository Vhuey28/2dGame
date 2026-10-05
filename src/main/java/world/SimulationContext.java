package world;

import world.event.EventBus;
import world.event.EventHistory;

/**
 * Context provided to all simulation systems. Gives access to
 * world state, clock, random streams, event bus, and event history.
 */
public final class SimulationContext {
    private final WorldSimulation simulation;
    public final EventHistory eventHistory = new EventHistory();
    public final EventBus eventBus = new EventBus(eventHistory);
    public final java.util.Map<String, SeededRandom> randomStreams = new java.util.HashMap<>();

    public SimulationContext(WorldSimulation simulation) {
        this.simulation = simulation;
        // Initialize default random streams
        randomStreams.put("DEFAULT", new SeededRandom(WorldConfig.DEFAULT_SEED));
        randomStreams.put("DEMOGRAPHICS", new SeededRandom(WorldConfig.DEFAULT_SEED + 1));
        randomStreams.put("ECONOMY", new SeededRandom(WorldConfig.DEFAULT_SEED + 2));
        randomStreams.put("POLITICS", new SeededRandom(WorldConfig.DEFAULT_SEED + 3));
        randomStreams.put("MILITARY", new SeededRandom(WorldConfig.DEFAULT_SEED + 4));
        randomStreams.put("GENERATION", new SeededRandom(WorldConfig.DEFAULT_SEED + 5));
    }

    public WorldClock getClock() {
        return simulation.getClock();
    }

    public WorldState getWorld() {
        return simulation.getWorld();
    }

    public WorldSimulation getSimulation() {
        return simulation;
    }

    /** Access the geography system (provinces, settlements, roads, etc.). */
    public world.geography.GeographySystem getGeography() {
        return simulation.getWorld().geography;
    }

    public SeededRandom getRandom(String streamName) {
        // Return the specific stream if it exists, otherwise return the DEFAULT stream.
        // This ensures unknown streams use the default sequence for reproducibility.
        return randomStreams.getOrDefault(streamName, randomStreams.get("DEFAULT"));
    }
}