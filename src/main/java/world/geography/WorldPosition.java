package world.geography;

/**
 * Represents a position in the strategic world map. Can be a fixed coordinate
 * or a point along a route edge.
 */
public final class WorldPosition {
    public double x;
    public double y;
    public Long routeEdgeId;
    public double routeProgress;

    public WorldPosition(double x, double y) {
        this.x = x;
        this.y = y;
        this.routeEdgeId = null;
        this.routeProgress = 0.0;
    }

    public WorldPosition(long routeEdgeId, double routeProgress) {
        this.x = 0.0; // Not used for route-based positions
        this.y = 0.0; // Not used for route-based positions
        this.routeEdgeId = routeEdgeId;
        this.routeProgress = routeProgress;
    }

    public boolean isOnRoute() {
        return routeEdgeId != null;
    }

    @Override
    public String toString() {
        if (isOnRoute()) {
            return String.format("WorldPosition[routeEdgeId=%d, progress=%.2f]", routeEdgeId, routeProgress);
        } else {
            return String.format("WorldPosition[x=%.2f, y=%.2f]", x, y);
        }
    }
}