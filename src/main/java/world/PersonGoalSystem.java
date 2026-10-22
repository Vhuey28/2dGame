package world;

import java.util.Map;

import world.geography.Settlement;

/**
 * Gives every living person a stable, inspectable medium-term goal. Goals are
 * refreshed monthly (and immediately after major interactions) from health,
 * household food, employment, local pressure, family, skills and personality.
 */
public final class PersonGoalSystem {
    private final SimulationContext context;
    private final WorldState world;

    public PersonGoalSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void processMonth(long minute) {
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        for (Person person : world.people.values()) {
            SettlementPressure pressure = person.currentSettlementId == null
                    ? null : pressures.get(person.currentSettlementId);
            refresh(person, pressure, minute);
        }
    }

    public void refresh(Person person, long minute) {
        if (person == null) return;
        Settlement settlement = person.currentSettlementId == null
                ? null : world.geography.getSettlement(person.currentSettlementId);
        SettlementPressure pressure = settlement == null
                ? null : SettlementPressure.evaluate(world, settlement, minute);
        refresh(person, pressure, minute);
    }

    public void refresh(Person person, SettlementPressure pressure, long minute) {
        if (person == null) return;
        if (!person.alive) {
            setGoal(person, Person.Goal.WORK, "Deceased", 0, minute);
            return;
        }

        Household household = person.householdId == null ? null : world.households.get(person.householdId);
        double householdFood = household == null ? SettlementPressure.clamp01(person.needs.foodSecurity)
                : SettlementPressure.clamp01(household.foodSecurity);
        double personalFood = Math.min(householdFood, SettlementPressure.clamp01(person.needs.foodSecurity));

        if (person.isChild(minute) || person.type == Person.PersonType.CHILD) {
            if (personalFood < 0.32) {
                setGoal(person, Person.Goal.FIND_FOOD,
                        "Their household is only " + percent(personalFood) + " food-secure", 92, minute);
            } else {
                setGoal(person, Person.Goal.GROW_UP,
                        "Still dependent on their household", 22, minute);
            }
            return;
        }

        if (person.health != null && (person.health.healthLevel <= 38.0 || person.health.wounded
                || person.health.injured || person.health.sick)) {
            setGoal(person, Person.Goal.RECOVER,
                    "Health is " + Math.max(1, Math.round(person.health.healthLevel))
                            + "% and needs rest", 94, minute);
            return;
        }

        if (person.regimentId != null || person.type == Person.PersonType.SOLDIER) {
            setGoal(person, Person.Goal.SERVE_REALM,
                    "Serving with a regiment; martial skill " + person.skills.martial, 78, minute);
            return;
        }

        if (personalFood < 0.38 || pressure != null && pressure.foodStress >= 0.78
                && householdFood < 0.55) {
            setGoal(person, Person.Goal.FIND_FOOD,
                    "Household food security is " + percent(personalFood)
                            + (pressure == null ? "" : "; local reserves last "
                                    + oneDecimal(pressure.foodReserveDays) + " days"),
                    personalFood < 0.22 ? 98 : 88, minute);
            return;
        }

        boolean recentDanger = person.lastLifeEventMinute != Long.MIN_VALUE
                && minute - person.lastLifeEventMinute <= 90L * WorldConfig.MINUTES_PER_DAY
                && (person.lastLifeEvent.contains("robbed") || person.lastLifeEvent.contains("attacked")
                        || person.lastLifeEvent.contains("besieged"));
        if (pressure != null && (pressure.migrationPressure >= 0.68
                || pressure.warStress >= 0.72 || recentDanger && pressure.crimeStress >= 0.45)
                && person.officeId == null && person.type != Person.PersonType.NOBLE) {
            setGoal(person, Person.Goal.SEEK_SAFER_HOME,
                    "Local danger is driving migration pressure to "
                            + percent(pressure.migrationPressure) + ": " + pressure.summary,
                    84, minute);
            return;
        }

        boolean workingAge = !person.isElderly(minute);
        boolean hasWork = person.employerId != null || person.officeId != null;
        if (workingAge && !hasWork && person.travelingPartyId == null) {
            int urgency = 58 + (int) Math.round((1.0 - person.needs.wealthSecurity) * 22.0)
                    + (person.personality.ambition >= 65.0 ? 7 : 0);
            setGoal(person, Person.Goal.FIND_WORK,
                    "No employer; strongest work skills are farming " + person.skills.farming
                            + ", crafting " + person.skills.crafting + ", trade " + person.skills.trade,
                    Math.min(92, urgency), minute);
            return;
        }

        boolean ruler = world.realms.values().stream().anyMatch(realm ->
                realm.rulerPersonId != null && realm.rulerPersonId.longValue() == person.id);
        if (ruler || person.officeId != null || person.type == Person.PersonType.NOBLE
                && person.personality.ambition >= 55.0) {
            setGoal(person, Person.Goal.SEEK_OFFICE,
                    ruler ? "Holds a crown and must keep the realm governable"
                            : "Office, rank and influence matter to them; ambition "
                                    + Math.round(person.personality.ambition),
                    ruler ? 82 : 64, minute);
            return;
        }

        int householdMembers = household == null ? 1 : household.getMemberCount();
        if (person.spouseId != null || householdMembers >= 3 || person.personality.compassion >= 72.0) {
            setGoal(person, Person.Goal.SUPPORT_FAMILY,
                    "Supports a household of " + householdMembers + " at "
                            + percent(householdFood) + " food security", 58, minute);
            return;
        }

        if (person.type == Person.PersonType.MERCHANT || person.personality.greed >= 68.0
                || person.needs.wealthSecurity < 0.42) {
            setGoal(person, Person.Goal.BUILD_WEALTH,
                    "Wealth security is " + percent(person.needs.wealthSecurity)
                            + "; trade skill " + person.skills.trade, 56, minute);
            return;
        }

        if (person.needs.socialBelonging < 0.38 || person.personality.sociability >= 78.0) {
            setGoal(person, Person.Goal.SOCIALIZE,
                    "Wants company and standing in the community", 38, minute);
            return;
        }

        setGoal(person, Person.Goal.WORK,
                hasWork ? "Settled in regular work" : "Keeping up ordinary household duties",
                hasWork ? 34 : 28, minute);
    }

    private static void setGoal(Person person, Person.Goal goal, String reason, int urgency, long minute) {
        person.currentGoal = goal;
        person.goalReason = reason == null || reason.isBlank() ? "No reason recorded" : reason;
        person.goalUrgency = Math.max(0, Math.min(100, urgency));
        person.goalUpdatedMinute = minute;
    }

    private static String percent(double value) {
        return Math.round(SettlementPressure.clamp01(value) * 100.0) + "%";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", Double.isFinite(value) ? value : 0.0);
    }
}
