package world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import world.economy.Inventory;

/** Persistent strategic identity for the adventurer; money and cargo are canonical on the world party. */
public final class PlayerCampaignState {
    public final long personId;
    public final long householdId;
    public final long partyId;
    public long currentSettlementId;
    public final Inventory cargo;
    public int cargoCapacity = 40;
    /** Compatibility summary; scoped reputation maps are canonical. */
    public int reputation;
    public final Map<Long, Integer> settlementReputation = new HashMap<>();
    public final Map<Long, Integer> realmReputation = new HashMap<>();
    public final List<Long> acceptedContractIds = new ArrayList<>();

    public PlayerCampaignState(long personId, long householdId, long partyId,
            long currentSettlementId, Inventory partyCargo) {
        this.personId = personId;
        this.householdId = householdId;
        this.partyId = partyId;
        this.currentSettlementId = currentSettlementId;
        this.cargo = partyCargo;
    }

    public int remainingCapacity() {
        return Math.max(0, cargoCapacity - cargo.totalQuantity());
    }

    public int reputationAtSettlement(long settlementId) {
        return settlementReputation.getOrDefault(settlementId, 0);
    }

    public void changeSettlementReputation(long settlementId, int amount) {
        settlementReputation.merge(settlementId, amount, Integer::sum);
        reputation = settlementReputation.values().stream().mapToInt(Integer::intValue).sum();
    }
}
