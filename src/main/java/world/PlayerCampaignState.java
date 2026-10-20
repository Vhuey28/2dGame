package world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import world.economy.Inventory;

/** Persistent strategic identity for the adventurer; money and cargo are canonical on the world party. */
public final class PlayerCampaignState {
    public enum KingdomRole { NONE, MERCENARY, LORD, KING }

    /**
     * How much of the current conquest the adventurer personally won: battles fought and
     * sieges stormed. Spent when a captured settlement is claimed as a personal holding, so
     * land goes to the person who took it rather than to whoever happens to be nearby.
     */
    public double conquestContribution;

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
    /** Realm service is explicit so mercenary/lord status survives travel and saves. */
    public Long affiliatedRealmId;
    public KingdomRole kingdomRole = KingdomRole.NONE;

    /** Army the adventurer personally commands; null while unattached. */
    public Long commandedArmyId;
    /** Titles the adventurer personally holds: conquered fiefs and granted lordships. */
    public final List<Long> titleIds = new ArrayList<>();
    /** Minute the adventurer is released from captivity; null while free. */
    public Long captiveUntilMinute;
    public Long captorRealmId;
    public long ransomCoins;

    /** True while the adventurer is a prisoner of war at the given world minute. */
    public boolean isCaptiveAt(long minute) {
        return captiveUntilMinute != null && minute < captiveUntilMinute;
    }

    /** Days of captivity remaining, rounded up. Zero when free. */
    public int captiveDaysRemaining(long minute) {
        if (!isCaptiveAt(minute)) return 0;
        long remaining = captiveUntilMinute - minute;
        return (int) ((remaining + WorldConfig.MINUTES_PER_DAY - 1) / WorldConfig.MINUTES_PER_DAY);
    }

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
