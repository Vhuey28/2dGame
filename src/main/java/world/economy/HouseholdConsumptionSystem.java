package world.economy;

import world.Household;

import java.util.Map;

/**
 * Household consumption system: processes daily food consumption for all households.
 * Each person consumes a daily food requirement (grain + vegetables + meat).
 * If a household lacks food, food security drops and people may starve.
 */
public final class HouseholdConsumptionSystem {
    private static final int GRAIN_DAILY_REQUIREMENT = 1; // 1 unit of grain per person per day
    private static final int VEGETABLE_DAILY_REQUIREMENT = 1;

    private final Map<Long, Household> households;

    public HouseholdConsumptionSystem(Map<Long, Household> households) {
        this.households = households;
    }

    /**
     * Process one day of household consumption.
     * Called by the simulation at daily intervals.
     */
    public void processDay(long currentMinute) {
        for (Household household : households.values()) {
            consumeForHousehold(household, currentMinute);
        }
    }

    /**
     * Process food consumption for one household.
     */
    private void consumeForHousehold(Household household, long currentMinute) {
        int memberCount = household.getMemberCount();
        if (memberCount == 0) return;

        // Calculate daily food requirements
        int grainNeeded = memberCount * GRAIN_DAILY_REQUIREMENT;
        int vegNeeded = memberCount * VEGETABLE_DAILY_REQUIREMENT;

        // Check if household has enough food
        int grainAvailable = household.inventory.getQuantity(GoodType.GRAIN);
        int vegAvailable = household.inventory.getQuantity(GoodType.VEGETABLES);

        boolean hasEnoughGrain = grainAvailable >= grainNeeded;
        boolean hasEnoughVeg = vegAvailable >= vegNeeded;

        if (hasEnoughGrain && hasEnoughVeg) {
            // Normal consumption
            household.inventory.remove(GoodType.GRAIN, grainNeeded);
            household.inventory.remove(GoodType.VEGETABLES, vegNeeded);
            household.foodSecurity = Math.min(1.0, household.foodSecurity + 0.03);
        } else if (hasEnoughGrain) {
            // Only grain available - consume grain, but note vegetables shortage
            household.inventory.remove(GoodType.GRAIN, grainNeeded);
            household.foodSecurity *= 0.9; // slight drop in food security
        } else if (grainAvailable > 0) {
            // Partial grain - consume what's available
            household.inventory.remove(GoodType.GRAIN, grainAvailable);
            household.foodSecurity *= 0.8; // significant drop
        } else {
            // No food at all - severe shortage
            household.foodSecurity *= 0.5; // major drop
        }

        // Clamp food security between 0 and 1
        if (household.foodSecurity < 0.0) household.foodSecurity = 0.0;
        if (household.foodSecurity > 1.0) household.foodSecurity = 1.0;
    }

    /**
     * Get the food security level for a household.
     * 1.0 = fully secure, 0.0 = no food.
     */
    public double getFoodSecurity(long householdId) {
        Household household = households.get(householdId);
        if (household == null) return 0.0;
        return household.foodSecurity;
    }
}