package world;

import world.event.WorldEvent;
import world.economy.Workplace;

import java.util.ArrayList;

/**
 * Demographic system for births, aging, deaths, and household membership.
 * Runs at scheduled intervals (daily/monthly) rather than every frame.
 */
public final class DemographicSystem {
    private final WorldState world;
    private final SimulationContext context;

    public DemographicSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    /**
     * Process a day of demographic events. This is called by the simulation
     * at daily intervals, not every frame.
     */
    public void processDay() {
        long currentMinute = context.getClock().getWorldMinute();
        // Process deaths
        processDeaths(currentMinute);
        // Process aging
        processAging(currentMinute);
        // Process births
        processBirths(currentMinute);
    }

    /**
     * Process monthly demographic events. Called by the simulation at monthly intervals.
     */
    public void processMonth() {
        long currentMinute = context.getClock().getWorldMinute();
        processDeaths(currentMinute);
        processAging(currentMinute);
        processBirths(currentMinute);
    }

    /**
     * Process deaths for all people. A person dies if they are elderly and a death roll succeeds.
     */
    private void processDeaths(long currentMinute) {
        for (Person person : new ArrayList<>(world.people.values())) {
            if (!person.alive) continue;
            if (person.isElderly(currentMinute)) {
                // Simple death check: elderly people have a small chance of dying each month
                double deathChance = 0.001; // 0.1% per month for elderly
                if (context.getRandom("DEMOGRAPHICS").nextDouble() < deathChance) {
                    diePerson(person, currentMinute, Person.DeathCause.OLD_AGE);
                }
            }
        }
    }

    /**
     * Process aging. Currently a no-op since age is derived from birthMinute.
     * Future phases may add aging-related events.
     */
    private void processAging(long currentMinute) {
        // Age is derived from birthMinute, so no explicit aging needed
    }

    /**
     * Process births. For now, this is a placeholder.
     * Future phases will add fertility, marriage, and birth events.
     */
    private void processBirths(long currentMinute) {
        // Placeholder for future birth logic
    }

    /**
     * Mark a person as dead and emit a PersonDied event.
     */
    public void diePerson(Person person, long deathMinute, Person.DeathCause cause) {
        if (person == null) throw new IllegalArgumentException("Person cannot be null");
        if (person.deathMinute != null) return; // Already dead

        person.alive = false;
        person.deathMinute = deathMinute;
        person.deathCause = cause;

        // Remove from employment and offices.
        if (person.employerId != null) {
            Workplace workplace = world.workplaces.get(person.employerId);
            if (workplace != null) workplace.removeWorker(person.id);
            person.employerId = null;
        }
        if (person.officeId != null) {
            person.officeId = null;
        }
        if (person.regimentId != null) {
            world.military.Regiment regiment = world.regiments.get(person.regimentId);
            if (regiment != null) regiment.soldierPersonIds.remove(Long.valueOf(person.id));
            person.regimentId = null;
            person.travelingPartyId = null;
        }

        // Dead people remain in world.people for history and family links, but
        // they must not continue consuming household food.
        if (person.householdId != null) {
            Household household = world.households.get(person.householdId);
            if (household != null) {
                household.removeMember(person.id);
                if (household.headPersonId != null && household.headPersonId == person.id) {
                    household.headPersonId = null;
                    for (Long memberId : household.memberIds) {
                        Person member = world.people.get(memberId);
                        if (member != null && member.alive) {
                            household.headPersonId = memberId;
                            break;
                        }
                    }
                }
            }
        }

        // Emit event
        context.eventBus.publish(new WorldEvent(context.getWorld().idGenerator.next(), deathMinute, "PERSON_DIED"));
    }

    /**
     * Create a new person with the given parameters.
     */
    public Person createPerson(long id, String givenName, String familyName, Person.Sex sex, long birthMinute,
                               long cultureId, long settlementId) {
        Person person = new Person(id, givenName, familyName, sex, birthMinute);
        person.cultureId = cultureId;
        person.currentSettlementId = settlementId;
        person.homeSettlementId = settlementId;
        person.alive = true;
        world.people.put(id, person);
        return person;
    }

    /**
     * Get the total population count.
     */
    public int getPopulationCount() {
        int count = 0;
        for (Person person : world.people.values()) {
            if (person.alive) count++;
        }
        return count;
    }

    /**
     * Get the population count for a settlement.
     */
    public int getSettlementPopulation(long settlementId) {
        int count = 0;
        for (Person person : world.people.values()) {
            if (person.alive && person.currentSettlementId != null
                    && person.currentSettlementId.equals(settlementId)) {
                count++;
            }
        }
        return count;
    }
}