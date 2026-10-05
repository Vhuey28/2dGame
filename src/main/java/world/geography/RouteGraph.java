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

    /** Check if a direct route exists between two settlements. */
    public boolean hasRoute(long fromSettlementId, long toSettlementId) {
        for (Road road : adjacency.getOrDefault(fromSettlementId, new ArrayList<>())) {
            if (!road.blocked && road.toSettlementId == toSettlementId) return true;
        }
        return false;
    }

    /**
     * Find the least-cost route between two settlements using Dijkstra's
     * algorithm. Blocked roads are excluded and danger adds a small planning
     * premium so merchants prefer safer routes when distances are similar.
     */
    public List<Road> findShortestRoute(long fromSettlementId, long toSettlementId) {
        if (fromSettlementId == toSettlementId) return new ArrayList<>();

        Map<Long, Double> distance = new HashMap<>();
        Map<Long, Road> previousRoad = new HashMap<>();
        java.util.PriorityQueue<RouteNode> open = new java.util.PriorityQueue<>();
        distance.put(fromSettlementId, 0.0);
        open.add(new RouteNode(fromSettlementId, 0.0));

        while (!open.isEmpty()) {
            RouteNode node = open.poll();
            if (node.cost > distance.getOrDefault(node.settlementId, Double.POSITIVE_INFINITY)) continue;
            if (node.settlementId == toSettlementId) break;

            for (Road road : adjacency.getOrDefault(node.settlementId, new ArrayList<>())) {
                if (road.blocked) continue;
                double nextCost = node.cost + road.getEffectiveCostFactor() * (1.0 + road.dangerLevel * 0.35);
                if (nextCost < distance.getOrDefault(road.toSettlementId, Double.POSITIVE_INFINITY)) {
                    distance.put(road.toSettlementId, nextCost);
                    previousRoad.put(road.toSettlementId, road);
                    open.add(new RouteNode(road.toSettlementId, nextCost));
                }
            }
        }

        if (!previousRoad.containsKey(toSettlementId)) return new ArrayList<>();
        java.util.LinkedList<Road> route = new java.util.LinkedList<>();
        long cursor = toSettlementId;
        while (cursor != fromSettlementId) {
            Road road = previousRoad.get(cursor);
            if (road == null) return new ArrayList<>();
            route.addFirst(road);
            cursor = road.fromSettlementId;
        }
        return route;
    }

    private static final class RouteNode implements Comparable<RouteNode> {
        final long settlementId;
        final double cost;

        RouteNode(long settlementId, double cost) {
            this.settlementId = settlementId;
            this.cost = cost;
        }

        @Override
        public int compareTo(RouteNode other) {
            return Double.compare(cost, other.cost);
        }
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