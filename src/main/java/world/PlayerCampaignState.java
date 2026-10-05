package world;

import world.economy.Inventory;

/** Persistent strategic identity and carried cargo for the adventurer. */
public final class PlayerCampaignState {
    public final long personId;
    public final long householdId;
    public long currentSettlementId;
    public final Inventory cargo = new Inventory();
    public int cargoCapacity = 40;
    public int reputation;

    public PlayerCampaignState(long personId, long householdId, long currentSettlementId) {
        this.personId = personId;
        this.householdId = householdId;
        this.currentSettlementId = currentSettlementId;
    }

    public int remainingCapacity() {
        return Math.max(0, cargoCapacity - cargo.totalQuantity());
    }
}
