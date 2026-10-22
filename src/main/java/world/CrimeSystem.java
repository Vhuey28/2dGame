package world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import world.economy.GoodType;
import world.event.WorldEvent;
import world.geography.Settlement;

/** Daily individual crime simulation with persistent, local economic and security consequences. */
public final class CrimeSystem {
    private final SimulationContext context;
    private final WorldState world;

    public CrimeSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void processDay(long minute) {
        SeededRandom random = context.getRandom("CRIME");
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        for (Settlement settlement : settlements) {
            List<Person> residents = new ArrayList<>();
            for (Long personId : world.indexes.livingPeopleAt(settlement.id)) {
                Person person = world.people.get(personId);
                if (person != null && person.alive) residents.add(person);
            }
            if (residents.size() < 2) continue;
            SettlementPressure pressure = pressures.get(settlement.id);
            double risk = 0.025 + SettlementPressure.clamp01(settlement.unrest) * 0.22
                    + (1.0 - SettlementPressure.clamp01(settlement.security)) * 0.10
                    + (pressure == null ? 0.0 : pressure.foodStress * 0.13
                            + pressure.jobStress * 0.085 + pressure.taxStress * 0.04
                            + pressure.warStress * 0.05);
            double expected = residents.size() * SettlementPressure.clamp01(risk) / 25.0;
            int attempts = (int) Math.floor(expected);
            if (random.nextDouble() < expected - attempts) attempts++;
            attempts = Math.min(4, attempts);
            for (int i = 0; i < attempts; i++) {
                Person offender = chooseOffender(residents, minute, random);
                if (offender == null) break;
                Person victim = chooseVictim(residents, offender, random);
                if (victim == null) break;
                createIncident(settlement, pressure, offender, victim, minute, random);
            }
        }
        trim(minute);
    }

    private void createIncident(Settlement settlement, SettlementPressure pressure,
            Person offender, Person victim, long minute, SeededRandom random) {
        boolean hungry = offender.currentGoal == Person.Goal.FIND_FOOD;
        CrimeIncident.CrimeType type = !hungry
                && offender.personality.bravery >= 62.0 && offender.personality.compassion <= 42.0
                && random.nextDouble() < 0.38
                ? CrimeIncident.CrimeType.ASSAULT : CrimeIncident.CrimeType.THEFT;
        int severity = 1 + random.nextInt(5)
                + (offender.personality.greed >= 80.0 || offender.personality.bravery >= 85.0 ? 1 : 0);
        severity = Math.min(6, severity);
        double discoveryChance = Math.max(0.08, Math.min(0.90,
                SettlementPressure.clamp01(settlement.security)
                        + (pressure != null && pressure.friendlyArmyStrength > 0 ? 0.06 : 0.0)));
        boolean discovered = random.nextDouble() < discoveryChance;
        CrimeIncident incident = new CrimeIncident(world.idGenerator.next(), settlement.id,
                offender.id, victim.id, type, minute, discovered, severity);
        world.crimeIncidents.put(incident.id, incident);

        GoodType stolenFood = null;
        long stolenCoins = 0L;
        if (type == CrimeIncident.CrimeType.THEFT) {
            Household victimHousehold = household(victim);
            Household offenderHousehold = household(offender);
            if (hungry && victimHousehold != null && offenderHousehold != null) {
                if (victimHousehold.inventory.remove(GoodType.GRAIN, 1)) stolenFood = GoodType.GRAIN;
                else if (victimHousehold.inventory.remove(GoodType.VEGETABLES, 1)) stolenFood = GoodType.VEGETABLES;
                if (stolenFood != null) offenderHousehold.inventory.add(stolenFood, 1);
            }
            long desired = severity * 2L;
            if (victimHousehold != null && offenderHousehold != null
                    && victimHousehold.id != offenderHousehold.id) {
                stolenCoins = Math.min(desired, Math.max(0L, victimHousehold.account.copperCoins));
                if (stolenCoins > 0 && victimHousehold.account.subtract(stolenCoins)) {
                    offenderHousehold.account.add(stolenCoins);
                } else stolenCoins = 0L;
            }
            if (stolenCoins == 0L) {
                double moved = Math.min(desired, Math.max(0.0, victim.wealth.netWorth));
                victim.wealth.netWorth -= moved;
                offender.wealth.netWorth += moved;
            }
            victim.needs.wealthSecurity = SettlementPressure.clamp01(
                    victim.needs.wealthSecurity - severity * 0.025);
        } else {
            victim.health.healthLevel = Math.max(1.0, victim.health.healthLevel - severity * 2.0);
            if (severity >= 4) victim.health.wounded = true;
        }

        victim.needs.safety = SettlementPressure.clamp01(victim.needs.safety - severity * 0.035);
        victim.rememberLifeEvent((type == CrimeIncident.CrimeType.THEFT ? "Was robbed" : "Was attacked")
                + " in " + settlement.name, minute);
        if (victim.needs.safety < 0.34) {
            victim.currentGoal = Person.Goal.SEEK_SAFER_HOME;
            victim.goalReason = "Recent crime in " + settlement.name + " left them feeling unsafe";
            victim.goalUrgency = 72;
            victim.goalUpdatedMinute = minute;
        }
        offender.rememberLifeEvent(discovered ? "Was caught committing a crime in " + settlement.name
                : "Committed an undiscovered crime in " + settlement.name, minute);

        if (discovered) {
            Household victimHousehold = household(victim);
            Household offenderHousehold = household(offender);
            if (stolenFood != null && victimHousehold != null && offenderHousehold != null
                    && offenderHousehold.inventory.remove(stolenFood, 1)) {
                victimHousehold.inventory.add(stolenFood, 1);
            }
            if (stolenCoins > 0 && victimHousehold != null && offenderHousehold != null
                    && offenderHousehold.account.subtract(Math.min(stolenCoins,
                            Math.max(0L, offenderHousehold.account.copperCoins)))) {
                victimHousehold.account.add(stolenCoins);
            }
            long fine = severity * 3L;
            if (offenderHousehold != null) {
                long paid = Math.min(fine, Math.max(0L, offenderHousehold.account.copperCoins));
                if (paid > 0 && offenderHousehold.account.subtract(paid)) settlement.treasury.add(paid);
            }
            offender.needs.politicalSatisfaction = SettlementPressure.clamp01(
                    offender.needs.politicalSatisfaction - 0.06);
            context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CRIME_DISCOVERED",
                    name(offender) + " was caught for " + type.name().toLowerCase()
                            + " against " + name(victim) + " in " + settlement.name
                            + "; pressure: " + (pressure == null ? "unknown" : pressure.summary)));
        } else if (severity >= 5) {
            context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CRIME_REPORTED",
                    name(victim) + " reported a serious " + type.name().toLowerCase()
                            + " in " + settlement.name + "; no offender was caught"));
        }

        settlement.unrest = SettlementPressure.clamp01(settlement.unrest + severity * 0.004);
        settlement.security = SettlementPressure.clamp01(settlement.security - severity * 0.0025);
    }

    private Person chooseOffender(List<Person> residents, long minute, SeededRandom random) {
        List<Person> eligible = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;
        for (Person person : residents) {
            if (!isPlayerParty(person) && person.regimentId == null && person.travelingPartyId == null
                    && !person.isChild(minute) && !person.isElderly(minute)
                    && person.type != Person.PersonType.CHILD && person.health.healthLevel > 18.0) {
                double weight = offenderWeight(person);
                eligible.add(person);
                weights.add(weight);
                total += weight;
            }
        }
        return weightedChoice(eligible, weights, total, random);
    }

    private Person chooseVictim(List<Person> residents, Person offender, SeededRandom random) {
        List<Person> eligible = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;
        for (Person person : residents) {
            if (person.id == offender.id || isPlayerParty(person)) continue;
            if (person.householdId != null && person.householdId.equals(offender.householdId)) continue;
            Household household = household(person);
            double weight = 1.0 + Math.max(0.0, person.wealth.netWorth) / 500.0
                    + (household == null ? 0.0 : Math.max(0L, household.account.copperCoins) / 250.0)
                    + (person.type == Person.PersonType.MERCHANT || person.type == Person.PersonType.NOBLE ? 0.7 : 0.0);
            eligible.add(person);
            weights.add(weight);
            total += weight;
        }
        return weightedChoice(eligible, weights, total, random);
    }

    private double offenderWeight(Person person) {
        Household household = household(person);
        double hunger = household == null ? 1.0 - person.needs.foodSecurity
                : 1.0 - SettlementPressure.clamp01(household.foodSecurity);
        double weight = 0.65 + person.personality.greed / 28.0
                + (100.0 - person.personality.honor) / 38.0
                + (100.0 - person.personality.compassion) / 85.0
                + person.personality.bravery / 95.0 + hunger * 1.9;
        if (person.employerId == null && person.officeId == null) weight += 1.45;
        weight += switch (person.currentGoal) {
            case FIND_FOOD -> person.goalUrgency / 24.0;
            case FIND_WORK -> person.goalUrgency / 34.0;
            case BUILD_WEALTH -> person.goalUrgency / 46.0;
            case RECOVER, GROW_UP -> -0.55;
            default -> 0.0;
        };
        if (person.officeId != null || person.type == Person.PersonType.NOBLE) weight *= 0.45;
        return Math.max(0.05, weight);
    }

    private Person weightedChoice(List<Person> people, List<Double> weights, double total, SeededRandom random) {
        if (people.isEmpty() || total <= 0.0) return null;
        double roll = random.nextDouble() * total;
        for (int i = 0; i < people.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0.0) return people.get(i);
        }
        return people.get(people.size() - 1);
    }

    private Household household(Person person) {
        return person == null || person.householdId == null ? null : world.households.get(person.householdId);
    }

    private boolean isPlayerParty(Person person) {
        if (world.player == null) return false;
        if (person.id == world.player.personId) return true;
        WorldParty party = world.parties.get(world.player.partyId);
        return party != null && party.memberPersonIds.contains(person.id);
    }

    private static String name(Person person) {
        return ((person.givenName == null ? "" : person.givenName) + " "
                + (person.familyName == null ? "" : person.familyName)).trim();
    }

    private void trim(long minute) {
        long cutoff = minute - 5L * WorldConfig.MINUTES_PER_YEAR;
        world.crimeIncidents.values().removeIf(incident -> incident.minute < cutoff);
    }
}
