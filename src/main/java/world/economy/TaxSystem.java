package world.economy;

import world.Household;
import world.Person;
import world.Realm;
import world.SimulationContext;
import world.WorldState;
import world.politics.Government;

import java.util.Map;

/**
 * Tax system: handles wages, rent, and taxes.
 * Called monthly by the simulation.
 */
public final class TaxSystem {
    private final double rentRate = 0.05;
    private final WorldState world;

    public TaxSystem(SimulationContext context) {
        this.world = context.getWorld();
    }

    /**
     * Process monthly taxes, wages, and rent for all households.
     */
    public void processMonth(Map<Long, world.geography.Settlement> settlements,
                             Map<Long, Household> households,
                             Map<Long, Person> people,
                             long currentMinute) {
        // EmploymentSystem pays daily wages. This monthly pass handles rent
        // and law-driven taxation without paying workers a second time.
        collectRent(settlements, households, currentMinute);
        collectTaxes(settlements, households, currentMinute);
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

            Realm realm = world.realms.get(settlement.controllerRealmId);
            Government government = realm == null ? null : world.governments.get(realm.governmentId);
            int lawLevel = government == null ? 1
                    : government.getLawLevel(Government.LawType.TAXATION);
            double taxRate = switch (lawLevel) {
                case 0 -> 0.03;
                case 1 -> 0.07;
                case 2 -> 0.12;
                default -> 0.18;
            };
            long assessedTax = (long) (wealth * taxRate);
            long tax = Math.min(assessedTax, household.account.copperCoins);
            if (tax > 0 && household.account.subtract(tax)) {
                long localShare = tax * 40 / 100;
                settlement.treasury.add(localShare);
                if (realm != null) realm.treasury.add(tax - localShare);
                else settlement.treasury.add(tax - localShare);
            }
        }
    }
}