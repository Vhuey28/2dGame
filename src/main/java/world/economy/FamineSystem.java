package world.economy;

import world.Household;
import world.Person;
import world.Person.DeathCause; // Corrected import
import world.SimulationContext; // Added import
import world.event.WorldEvent;

import java.util.Map;

/**
 * Famine system: detects severe food shortages and applies consequences.
 * When household food security drops below threshold, people may die from starvation
 * or migrate to other settlements seeking food.
 */
public final class FamineSystem {
    private static final double FOOD_SECURITY_CRITICAL = 0.2; // Below this, starvation risk
    private static final double FOOD_SECURITY_LOW = 0.5; // Below this, migration risk
    private static final double MONTHLY_STARVATION_CHANCE = 0.05; // 5% chance per month when critical

    private final Map<Long, Household> households;
    private final Map<Long, Person> people;
    private final world.geography.GeographySystem geography;
    private final SimulationContext context; // Added context field

    public FamineSystem(Map<Long, Household> households, Map<Long, Person> people,
                        world.geography.GeographySystem geography, SimulationContext context) { // Added context parameter
        this.households = households;
        this.people = people;
        this.geography = geography;
        this.context = context; // Initialized context field
    }

    /**
     * Process monthly famine checks.
     * Called by the simulation at monthly intervals.
     */
    public void processMonth(long currentMinute) {
        for (Household household : households.values()) {
            checkHouseholdFamine(household, currentMinute);
        }
    }

    /**
     * Check a single household for famine conditions.
     */
    private void checkHouseholdFamine(Household household, long currentMinute) {
        double foodSecurity = household.foodSecurity;
        int memberCount = household.getMemberCount();

        if (memberCount == 0) return; // Skip empty households

        if (foodSecurity <= 0.0) {
            // No food security - immediate starvation risk
            processStarvation(household, currentMinute, DeathCause.STARVATION);
        } else if (foodSecurity < FOOD_SECURITY_CRITICAL) {
            // Critical food shortage
            if (context.getRandom("ECONOMY").nextDouble() < MONTHLY_STARVATION_CHANCE) {
                processStarvation(household, currentMinute, DeathCause.STARVATION);
            }
            // Also consider migration
            if (foodSecurity < FOOD_SECURITY_LOW) {
                considerMigration(household, currentMinute);
            }
        }
    }

    /**
     * Process starvation deaths in a household.
     */
    private void processStarvation(Household household, long currentMinute, DeathCause cause) {
        // Starvation affects the most vulnerable first (children, elderly)
        // Create a copy of memberIds to avoid ConcurrentModificationException
        java.util.List<Long> currentMembers = new java.util.ArrayList<>(household.memberIds);

        for (Long personId : currentMembers) {
            Person person = people.get(personId);
            if (person == null || !person.alive) continue;

            // Children and elderly are more vulnerable
            boolean isVulnerable = person.isChild(currentMinute) || person.isElderly(currentMinute);
            double deathChance = isVulnerable ? 0.1 : 0.02; // 10% for vulnerable, 2% for others

            if (context.getRandom("DEMOGRAPHICS").nextDouble() < deathChance) {
                person.die(currentMinute, cause);
                household.removeMember(personId); // Remove from household after death
                context.eventBus.publish(new WorldEvent(
                        context.getWorld().idGenerator.next(), currentMinute, "PERSON_DIED"));

                // If this was the last person, break
                if (household.getMemberCount() == 0) break;
            }
        }
    }

    /** Move a small number of adults to the best supplied reachable settlement. */
    private void considerMigration(Household household, long currentMinute) {
        if (household.getMemberCount() <= 1) return;
        world.geography.Settlement destination = null;
        int bestFood = -1;
        for (world.geography.Settlement candidate : geography.getSettlements().values()) {
            if (candidate.id == household.homeSettlementId) continue;
            int food = candidate.publicStockpile.getQuantity(GoodType.GRAIN)
                    + candidate.publicStockpile.getQuantity(GoodType.VEGETABLES);
            if (food > bestFood) {
                bestFood = food;
                destination = candidate;
            }
        }
        if (destination == null || bestFood <= 0) return;

        java.util.List<Long> members = new java.util.ArrayList<>(household.memberIds);
        members.sort((id1, id2) -> {
            Person p1 = people.get(id1);
            Person p2 = people.get(id2);
            int age1 = p1 != null ? p1.getAge(currentMinute) : 0;
            int age2 = p2 != null ? p2.getAge(currentMinute) : 0;
            boolean adult1 = age1 >= 18 && age1 < 65;
            boolean adult2 = age2 >= 18 && age2 < 65;
            if (adult1 != adult2) return adult1 ? -1 : 1;
            return Integer.compare(age2, age1);
        });

        int count = Math.min(Math.max(1, household.getMemberCount() / 10), members.size());
        Household migrantHousehold = new Household(context.getWorld().idGenerator.next(),
                destination.id, null);
        migrantHousehold.foodSecurity = 0.5;
        context.getWorld().households.put(migrantHousehold.id, migrantHousehold);
        for (int i = 0; i < count; i++) {
            Long personId = members.get(i);
            Person person = people.get(personId);
            if (person == null || !person.alive) continue;
            household.removeMember(personId);
            migrantHousehold.addMember(personId);
            if (migrantHousehold.headPersonId == null) migrantHousehold.headPersonId = personId;
            person.householdId = migrantHousehold.id;
            person.homeSettlementId = destination.id;
            person.currentSettlementId = destination.id;
        }
        context.eventBus.publish(new WorldEvent(context.getWorld().idGenerator.next(),
                currentMinute, "HOUSEHOLD_MIGRATED"));
    }
}