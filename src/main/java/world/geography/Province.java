package world.geography;

import java.util.Set;

/**
 * A strategic map province. Contains settlements, terrain, resources,
 * and has legal/military ownership.
 */
public final class Province {
    public long id;
    public String name;
    public String regionIdentifier; // Polygon or region identifier
    public TerrainType terrain;
    public ClimateType climate;
    public Set<Long> resourceDepositIds; // Links to resource deposits (future phase)
    public Set<Long> settlementIds;
    public Long legalTitleId;           // Links to a Title (future phase)
    public Long controllerRealmId;      // Current controlling realm (military/administrative)
    public Long occupyingRealmId;       // Occupying realm if different from controller
    public Set<Long> neighborProvinceIds;
    public Set<Long> roadConnectionIds; // Links to Road edges (future phase)

    public Province(long id, String name, String regionIdentifier, TerrainType terrain, ClimateType climate) {
        this.id = id;
        this.name = name;
        this.regionIdentifier = regionIdentifier;
        this.terrain = terrain;
        this.climate = climate;
        this.resourceDepositIds = new java.util.HashSet<>();
        this.settlementIds = new java.util.HashSet<>();
        this.neighborProvinceIds = new java.util.HashSet<>();
        this.roadConnectionIds = new java.util.HashSet<>();
    }

    // Placeholder enums for now
    public enum TerrainType { PLAINS, FORESTS, MOUNTAINS, DESERT, HILLS, SWAMP }
    public enum ClimateType { TEMPERATE, ARID, TROPICAL, COLD }

    @Override
    public String toString() {
        return String.format("Province[id=%d, name=%s, terrain=%s, climate=%s]",
            id, name, terrain, climate);
    }
}