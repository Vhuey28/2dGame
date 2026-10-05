package world.geography;

import world.economy.Inventory;
import world.economy.Market;
import world.economy.MoneyAccount;
import world.economy.Workplace;

import java.util.List;

/**
 * Represents a settlement (village, town, city) on the strategic map.
 * Population is derived from resident people.
 */
public final class Settlement {
    public long id;
    public String name;
    public long provinceId;
    public Long controllerRealmId;
    public Long occupyingRealmId;
    public WorldPosition position;
    public SettlementType type;

    public List<Long> districtIds;   // Links to Districts (future phase)
    public List<Long> buildingIds;   // Links to Buildings (future phase)
    public Market market;
    public Inventory publicStockpile;
    public MoneyAccount treasury;
    public java.util.List<Workplace> workplaces = new java.util.ArrayList<>();

    public double security;
    public double sanitation;
    public double prosperity;
    public double unrest;

    public Settlement(long id, String name, long provinceId, WorldPosition position, SettlementType type) {
        this.id = id;
        this.name = name;
        this.provinceId = provinceId;
        this.position = position;
        this.type = type;
        this.districtIds = new java.util.ArrayList<>();
        this.buildingIds = new java.util.ArrayList<>();
        this.market = new Market(id); // Market ID is same as settlement for simplicity for now
        this.publicStockpile = new Inventory();
        this.treasury = new MoneyAccount();
        this.security = 0.65;
        this.sanitation = 0.55;
        this.prosperity = 0.50;
        this.unrest = 0.10;
    }

    /** Add a workplace to this settlement. */
    public void addWorkplace(Workplace workplace) {
        if (workplace == null) throw new IllegalArgumentException("Workplace cannot be null");
        workplaces.add(workplace);
    }

    /** Get total number of workplaces in this settlement. */
    public int getWorkplaceCount() {
        return workplaces.size();
    }

    public enum SettlementType { VILLAGE, TOWN, CITY, FORT, CAPITAL }

    @Override
    public String toString() {
        return String.format("Settlement[id=%d, name=%s, type=%s, pos=%s]",
            id, name, type, position);
    }
}
