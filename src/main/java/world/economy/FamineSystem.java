package world.economy;

import world.Household;
import world.Person;
import world.Person.DeathCause; // Corrected import
import world.SettlementPressure;
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
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(
                context.getWorld(), currentMinute);
        // Migration can create a new household, so traverse a stable snapshot.
        for (Household household : new java.util.ArrayList<>(households.values())) {
            checkHouseholdFamine(household, pressures, currentMinute);
        }
    }

    /**
     * Check a single household for famine conditions.
     */
    private void checkHouseholdFamine(Household household,
            Map<Long, SettlementPressure> pressures, long currentMinute) {
        double foodSecurity = household.foodSecurity;
        int memberCount = household.getMemberCount();

        if (memberCount == 0) return; // Skip empty households

        if (foodSecurity <= 0.0) {
            // No food security - immediate starvation risk
            processStarvation(household, currentMinute, DeathCause.STARVATION);
        } else if (foodSecurity < FOOD_SECURITY_CRITICAL
                && context.getRandom("ECONOMY").nextDouble() < MONTHLY_STARVATION_CHANCE) {
            processStarvation(household, currentMinute, DeathCause.STARVATION);
        }

        SettlementPressure sourcePressure = pressures.get(household.homeSettlementId);
        boolean memberWantsToLeave = household.memberIds.stream().anyMatch(personId -> {
            Person person = people.get(personId);
            return person != null && person.alive && person.goalUrgency >= 72
                    && (person.currentGoal == Person.Goal.FIND_FOOD
                            || person.currentGoal == Person.Goal.SEEK_SAFER_HOME);
        });
        if (foodSecurity < FOOD_SECURITY_LOW
                && (memberWantsToLeave || sourcePressure != null
                        && sourcePressure.migrationPressure >= 0.34)) {
            considerMigration(household, pressures, currentMinute);
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
            // Active soldiers consume army supplies and face military attrition instead.
            if (person == null || !person.alive || person.regimentId != null) continue;

            // Children and elderly are more vulnerable
            boolean isVulnerable = person.isChild(currentMinute) || person.isElderly(currentMinute);
            double deathChance = isVulnerable ? 0.1 : 0.02; // 10% for vulnerable, 2% for others

            if (context.getRandom("DEMOGRAPHICS").nextDouble() < deathChance) {
                person.die(currentMinute, cause);
                household.removeMember(personId); // Remove from household after death
                context.eventBus.publish(new WorldEvent(
                        context.getWorld().idGenerator.next(), currentMinute, "PERSON_DIED",
                        ((person.givenName == null ? "" : person.givenName) + " "
                                + (person.familyName == null ? "" : person.familyName)).trim()
                                + " died of starvation; household food security was "
                                + Math.round(SettlementPressure.clamp01(household.foodSecurity) * 100.0) + "%"));

                // If this was the last person, break
                if (household.getMemberCount() == 0) break;
            }
        }
    }

    /** Move a small number of adults only to a materially better supplied and safer settlement. */
    private void considerMigration(Household household,
            Map<Long, SettlementPressure> pressures, long currentMinute) {
        if (household.getMemberCount() <= 1) return;
        SettlementPressure sourcePressure = pressures.get(household.homeSettlementId);
        double sourceScore = sourcePressure == null ? 0.0 : sourcePressure.destinationScore();
        world.geography.Settlement destination = null;
        SettlementPressure destinationPressure = null;
        double bestScore = sourceScore + 8.0;
        for (world.geography.Settlement candidate : geography.getSettlements().values()) {
            if (candidate.id == household.homeSettlementId) continue;
            SettlementPressure candidatePressure = pressures.get(candidate.id);
            if (candidatePressure == null || candidatePressure.foodReserveDays < 1.0
                    || candidatePressure.besieged) continue;
            double score = candidatePressure.destinationScore();
            if (score > bestScore) {
                bestScore = score;
                destination = candidate;
                destinationPressure = candidatePressure;
            }
        }
        if (destination == null || destinationPressure == null) return;

        java.util.List<Long> members = new java.util.ArrayList<>(household.memberIds);
        members.removeIf(personId -> {
            Person person = people.get(personId);
            return person == null || !person.alive || person.regimentId != null
                    || person.travelingPartyId != null || person.officeId != null
                    || context.getWorld().player != null && context.getWorld().player.personId == personId;
        });
        if (members.isEmpty()) return;
        members.sort((id1, id2) -> {
            Person p1 = people.get(id1);
            Person p2 = people.get(id2);
            int goal1 = migrationPriority(p1);
            int goal2 = migrationPriority(p2);
            if (goal1 != goal2) return Integer.compare(goal2, goal1);
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
        migrantHousehold.foodSecurity = Math.max(0.35,
                Math.min(0.65, destinationPressure.averageFoodSecurity));
        context.getWorld().households.put(migrantHousehold.id, migrantHousehold);
        world.geography.Settlement source = geography.getSettlement(household.homeSettlementId);
        for (int i = 0; i < count; i++) {
            Long personId = members.get(i);
            Person person = people.get(personId);
            if (person == null || !person.alive) continue;
            if (person.employerId != null) {
                Workplace workplace = context.getWorld().workplaces.get(person.employerId);
                if (workplace != null) workplace.removeWorker(person.id);
                person.employerId = null;
            }
            household.removeMember(personId);
            migrantHousehold.addMember(personId);
            if (migrantHousehold.headPersonId == null) migrantHousehold.headPersonId = personId;
            person.householdId = migrantHousehold.id;
            person.homeSettlementId = destination.id;
            person.currentSettlementId = destination.id;
            person.currentGoal = Person.Goal.FIND_WORK;
            person.goalReason = "Migrated to " + destination.name + " for food and safety";
            person.goalUrgency = 72;
            person.goalUpdatedMinute = currentMinute;
            person.rememberLifeEvent("Migrated from "
                    + (source == null ? "a hungry settlement" : source.name)
                    + " to " + destination.name, currentMinute);
            if (destination.publicStockpile.remove(GoodType.GRAIN, 1)) {
                migrantHousehold.inventory.add(GoodType.GRAIN, 1);
            } else if (destination.publicStockpile.remove(GoodType.VEGETABLES, 1)) {
                migrantHousehold.inventory.add(GoodType.VEGETABLES, 1);
            }
        }
        if (migrantHousehold.getMemberCount() == 0) {
            context.getWorld().households.remove(migrantHousehold.id);
            return;
        }
        context.eventBus.publish(new WorldEvent(context.getWorld().idGenerator.next(),
                currentMinute, "HOUSEHOLD_MIGRATED", migrantHousehold.getMemberCount()
                        + " people moved from " + (source == null ? "a settlement" : source.name)
                        + " to " + destination.name + " because source food security was "
                        + Math.round(SettlementPressure.clamp01(household.foodSecurity) * 100.0)
                        + "% while the destination had " + String.format(java.util.Locale.ROOT, "%.1f",
                                destinationPressure.foodReserveDays) + " food-days and loyalty "
                        + Math.round(destinationPressure.loyalty * 100.0) + "%"));
    }

    private static int migrationPriority(Person person) {
        if (person == null) return 0;
        int bonus = switch (person.currentGoal) {
            case SEEK_SAFER_HOME -> 45;
            case FIND_FOOD -> 38;
            case FIND_WORK -> 22;
            default -> 0;
        };
        return bonus + person.goalUrgency;
    }
}