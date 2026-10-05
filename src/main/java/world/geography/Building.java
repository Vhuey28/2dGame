package world.geography;

/**
 * Stub placeholder for Building. Full implementation in later phases.
 * Buildings can be houses, farms, workshops, markets, warehouses, barracks, walls, keeps, temples, taverns, hospitals, schools, ports.
 */
public final class Building {
    public long id;
    public long settlementId;
    public BuildingType type;
    public Long districtId; // Optional district this building belongs to
    public Long ownedByHouseholdId; // If owned by a household
    public Long operatedByWorkplaceId; // If operated by a workplace (future phase)
    public int level; // Construction level/quality

    public Building(long id, long settlementId, BuildingType type) {
        this.id = id;
        this.settlementId = settlementId;
        this.type = type;
        this.districtId = null;
        this.ownedByHouseholdId = null;
        this.operatedByWorkplaceId = null;
        this.level = 1;
    }

    public enum BuildingType { HOUSE, FARM, WORKSHOP, MARKET, WAREHOUSE, BARRACKS, WALL, KEEP, TEMPLE, TAVERN, HOSPITAL, SCHOOL, PORT }

    @Override
    public String toString() {
        return String.format("Building[id=%d, type=%s, settlementId=%d]", id, type, settlementId);
    }
}