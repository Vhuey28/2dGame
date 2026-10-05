package world.economy;

import world.Household;
import world.Person;
import world.WorldConfig;

import java.util.Map;

/**
 * Tax system: handles wages, rent, and taxes.
 * Called monthly by the simulation.
 */
public final class TaxSystem {
    private final double taxRate = 0.10; // 10% tax on household income
    private final double rentRate = 0.05; // 5% of household wealth as rent
    private final double wagePerWorkerPerDay = 2.0; // base daily wages in copper coins

    /**
     * Process monthly taxes, wages, and rent for all households.
     */
    public void processMonth(Map<Long, world.geography.Settlement> settlements,
                             Map<Long, Household> households,
                             Map<Long, Person> people,
                             long currentMinute) {
        // 1. Pay wages from workplaces to workers
        payWages(settlements, people, households, currentMinute);

        // 2. Collect rent from households (to settlement treasury)
        collectRent(settlements, households, currentMinute);

        // 3. Collect taxes from households (to settlement treasury as realm proxy)
        collectTaxes(settlements, households, currentMinute);
    }

    /**
     * Pay wages to workers from workplace output value.
     * Wages are paid from the workplace owner's account to workers.
     */
    private void payWages(Map<Long, world.geography.Settlement> settlements,
                          Map<Long, Person> people,
                          Map<Long, Household> households,
                          long currentMinute) {
        for (world.geography.Settlement settlement : settlements.values()) {
            for (Workplace workplace : settlement.workplaces) {
                if (workplace.ownerHouseholdId == null) continue;

                Household ownerHousehold = households.get(workplace.ownerHouseholdId);
                if (ownerHousehold == null) continue;

                int workerCount = workplace.getWorkerCount();
                if (workerCount == 0) continue;

                // Calculate total wage bill (daily wage * days in month)
                int daysInMonth = WorldConfig.DAYS_PER_MONTH;
                long totalWages = (long) (wagePerWorkerPerDay * workerCount * daysInMonth);

                // Check if owner can pay
                if (ownerHousehold.account.subtract(totalWages)) {

                    // Distribute wages to workers
                    long wagePerWorker = totalWages / workerCount;
                    for (Long workerId : workplace.workerIds) {
                        Person worker = people.get(workerId);
                        if (worker != null && worker.alive) {
                            // Add wage to worker's household
                            Household workerHousehold = households.get(worker.householdId);
                            if (workerHousehold != null) {
                                workerHousehold.account.add(wagePerWorker);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Collect rent from households to their settlement's treasury.
     * Rent is a percentage of household wealth (money + inventory value).
     */
    private void collectRent(Map<Long, world.geography.Settlement> settlements,
                             Map<Long, Household> households,
                             long currentMinute) {
        for (Household household : households.values()) {
            // Skip households without a settlement
            world.geography.Settlement settlement = settlements.get(household.homeSettlementId);
            if (settlement == null) continue;

            // Calculate rent based on household wealth
            long wealth = household.account.copperCoins;
            // Add inventory value
            for (GoodType good : GoodType.values()) {
                wealth += (long) (household.inventory.getQuantity(good) * GoodsCatalog.getBasePrice(good));
            }

            long rent = (long) (wealth * rentRate);
            if (rent > 0 && household.account.subtract(rent)) {
                settlement.treasury.add(rent);
            }
        }
    }

    /**
     * Collect taxes from households to their realm's treasury.
     * Tax is a percentage of household wealth as a proxy for income.
     */
    private void collectTaxes(Map<Long, world.geography.Settlement> settlements,
                              Map<Long, Household> households,
                              long currentMinute) {
        for (Household household : households.values()) {
            // Skip households without a settlement (no valid settlement ID found in map)
            world.geography.Settlement settlement = settlements.get(household.homeSettlementId);
            if (settlement == null || settlement.controllerRealmId == null) continue;

            // Tax based on household wealth as proxy for income
            long wealth = household.account.copperCoins;
            for (GoodType good : GoodType.values()) {
                wealth += (long) (household.inventory.getQuantity(good) * GoodsCatalog.getBasePrice(good));
            }

            long tax = (long) (wealth * taxRate);
            if (tax > 0 && household.account.subtract(tax)) {
                settlement.treasury.add(tax);
            }
        }
    }
}