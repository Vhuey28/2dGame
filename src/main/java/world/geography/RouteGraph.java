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

    /** Cached route IDs. Copies are returned so callers cannot mutate cache state. */
    private final Map<RouteKey, List<Long>> routeCache = new HashMap<>();
    private long cacheHits;
    private long cacheMisses;

    /** Add a road to the graph. Roads are directed; add both directions for two-way travel. */
    public void addRoad(Road road) {
        clearRouteCache();
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
        RouteKey key = new RouteKey(fromSettlementId, toSettlementId);
        List<Long> cached = routeCache.get(key);
        if (cached != null) {
            List<Road> route = materializeUsableRoute(cached);
            if (route != null) {
                cacheHits++;
                return route;
            }
            routeCache.remove(key);
        }
        cacheMisses++;

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
        List<Long> routeIds = new ArrayList<>();
        for (Road road : route) routeIds.add(road.id);
        routeCache.put(key, routeIds);
        return new ArrayList<>(route);
    }

    private List<Road> materializeUsableRoute(List<Long> roadIds) {
        List<Road> roads = new ArrayList<>(roadIds.size());
        for (Long roadId : roadIds) {
            Road road = roadMap.get(roadId);
            if (road == null || road.blocked) return null;
            roads.add(road);
        }
        return roads;
    }

    public void setRoadBlocked(long roadId, boolean blocked) {
        Road road = roadMap.get(roadId);
        if (road == null) return;
        boolean changed = road.blocked != blocked;
        road.blocked = blocked;
        for (Road candidate : adjacency.getOrDefault(road.toSettlementId, List.of())) {
            if (candidate.toSettlementId == road.fromSettlementId) {
                changed |= candidate.blocked != blocked;
                candidate.blocked = blocked;
            }
        }
        if (changed) clearRouteCache();
    }

    public void clearRouteCache() { routeCache.clear(); }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public int getCachedRouteCount() { return routeCache.size(); }

    private static final class RouteKey {
        final long from;
        final long to;
        RouteKey(long from, long to) { this.from = from; this.to = to; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RouteKey)) return false;
            RouteKey key = (RouteKey) other;
            return from == key.from && to == key.to;
        }
        @Override public int hashCode() {
            return 31 * Long.hashCode(from) + Long.hashCode(to);
        }
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