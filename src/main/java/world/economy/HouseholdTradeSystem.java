package world.economy;

import world.Household;
import world.SimulationContext;
import world.WorldState;
import world.geography.Settlement;

/**
 * Connects household inventories to settlement stockpiles. Producers sell
 * surplus food and households purchase shortfalls before daily consumption.
 */
public final class HouseholdTradeSystem {
    private final WorldState world;

    public HouseholdTradeSystem(SimulationContext context) {
        this.world = context.getWorld();
    }

    public void processDay() {
        for (Household household : world.households.values()) {
            Settlement settlement = world.geography.getSettlement(household.homeSettlementId);
            if (settlement == null || household.getMemberCount() == 0) continue;
            sellSurplus(household, settlement, GoodType.GRAIN, household.getMemberCount() * 7);
            sellSurplus(household, settlement, GoodType.VEGETABLES, household.getMemberCount() * 4);
        }
        for (Household household : world.households.values()) {
            Settlement settlement = world.geography.getSettlement(household.homeSettlementId);
            if (settlement == null || household.getMemberCount() == 0) continue;
            buyTowardTarget(household, settlement, GoodType.GRAIN, household.getMemberCount() * 3);
            buyTowardTarget(household, settlement, GoodType.VEGETABLES, household.getMemberCount() * 2);
        }
    }

    private void sellSurplus(Household household, Settlement settlement,
            GoodType good, int reserve) {
        int surplus = Math.min(60, Math.max(0, household.inventory.getQuantity(good) - reserve));
        long price = Math.max(1L, settlement.market.getLastPrice(good));
        int affordable = (int) Math.min(surplus, settlement.treasury.copperCoins / price);
        if (affordable <= 0 || !household.inventory.remove(good, affordable)) return;
        settlement.treasury.subtract(affordable * price);
        household.account.add(affordable * price);
        settlement.publicStockpile.add(good, affordable);
    }

    private void buyTowardTarget(Household household, Settlement settlement,
            GoodType good, int target) {
        int wanted = Math.max(0, target - household.inventory.getQuantity(good));
        long scarcityPremium = settlement.publicStockpile.getQuantity(good) < target ? 2L : 0L;
        long price = Math.max(1L, settlement.market.getLastPrice(good) + scarcityPremium);
        int quantity = Math.min(wanted, settlement.publicStockpile.getQuantity(good));
        quantity = (int) Math.min(quantity, household.account.copperCoins / price);
        if (quantity <= 0 || !household.account.subtract(quantity * price)) return;
        settlement.treasury.add(quantity * price);
        settlement.publicStockpile.remove(good, quantity);
        household.inventory.add(good, quantity);
    }
}
