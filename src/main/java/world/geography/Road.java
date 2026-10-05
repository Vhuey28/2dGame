package world.geography;

/**
 * A road/route edge connecting two settlements or map locations.
 * Provides length, terrain cost, road quality, seasonal modifiers,
 * river crossing cost, danger level, owner/toll authority, and blocked state.
 */
public final class Road {
    public long id;
    public long fromSettlementId;
    public long toSettlementId;
    public double length; // distance units
    public double terrainCost; // movement cost factor based on terrain
    public double roadQuality; // 0.0 (path) to 1.0 (paved imperial road)
    public double seasonalModifier; // multiplies movement time in bad seasons
    public double riverCrossingCost; // extra cost if there's a river crossing
    public double dangerLevel; // bandit/wildlife danger 0.0 to 1.0
    public Long tollAuthorityRealmId; // realm that collects tolls, if any
    public boolean blocked;

    public Road(long id, long fromSettlementId, long toSettlementId, double length,
                double terrainCost, double roadQuality, double dangerLevel) {
        this.id = id;
        this.fromSettlementId = fromSettlementId;
        this.toSettlementId = toSettlementId;
        this.length = length;
        this.terrainCost = terrainCost;
        this.roadQuality = roadQuality;
        this.seasonalModifier = 1.0;
        this.riverCrossingCost = 0.0;
        this.dangerLevel = dangerLevel;
        this.tollAuthorityRealmId = null;
        this.blocked = false;
    }

    /** Effective travel cost factor combining terrain, road quality, river crossing. */
    public double getEffectiveCostFactor() {
        double qualityFactor = 2.0 - roadQuality; // 1.0 at full quality, 2.0 at zero quality
        double base = length * terrainCost * qualityFactor;
        return base + riverCrossingCost;
    }

    @Override
    public String toString() {
        return String.format("Road[id=%d, %d<->%d, length=%.1f, danger=%.2f, blocked=%b]",
            id, fromSettlementId, toSettlementId, length, dangerLevel, blocked);
    }
}