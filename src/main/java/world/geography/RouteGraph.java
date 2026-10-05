package world.geography;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A graph of route edges connecting settlements. Used for Dijkstra/A* routing
 * to calculate strategic travel paths between locations on the continent map.
 */
public final class RouteGraph {
    /** Map from source settlement id to list of outgoing route edges. */
    private final Map<Long, List<Road>> adjacency = new HashMap<>();

    /** Map from route edge id to the road itself. */
    private final Map<Long, Road> roadMap = new HashMap<>();

    /** Add a road to the graph. Roads are directed; add both directions for two-way travel. */
    public void addRoad(Road road) {
        adjacency.computeIfAbsent(road.fromSettlementId, k -> new ArrayList<>()).add(road);
        roadMap.put(road.id, road);

        // Also add reverse road for two-way travel
        Road reverse = new Road(road.id + 10_000L, road.toSettlementId, road.fromSettlementId,
                road.length, road.terrainCost, road.roadQuality, road.dangerLevel);
        reverse.blocked = road.blocked;
        reverse.tollAuthorityRealmId = road.tollAuthorityRealmId;
        adjacency.computeIfAbsent(road.toSettlementId, k -> new ArrayList<>()).add(reverse);
        roadMap.put(reverse.id, reverse);
    }

    /** Get all outgoing roads from a settlement. */
    public List<Road> getOutgoingRoads(long settlementId) {
        return adjacency.getOrDefault(settlementId, new ArrayList<>());
    }

    /** Get a road by its ID. */
    public Road getRoad(long roadId) {
        return roadMap.get(roadId);
    }

    /** Check if a route exists between two settlements. */
    public boolean hasRoute(long fromSettlementId, long toSettlementId) {
        for (Road road : adjacency.getOrDefault(fromSettlementId, new ArrayList<>())) {
            if (road.toSettlementId == toSettlementId) return true;
        }
        return false;
    }

    /** Get all roads in the graph. */
    public java.util.List<Road> getAllRoads() {
        java.util.List<Road> all = new java.util.ArrayList<>();
        for (List<Road> roads : adjacency.values()) {
            all.addAll(roads);
        }
        return all;
    }

    @Override
    public String toString() {
        return String.format("RouteGraph[roads=%d, settlements=%d]", roadMap.size(), adjacency.size());
    }
}