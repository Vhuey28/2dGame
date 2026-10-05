package world.economy;

import java.util.ArrayList;
import java.util.Comparator;

import world.Household;
import world.Person;
import world.SimulationContext;
import world.WorldState;
import world.geography.Settlement;

/** Assigns working-age residents to local vacancies and pays daily wages. */
public final class EmploymentSystem {
    private static final long DAILY_WAGE = 2L;
    private final WorldState world;

    public EmploymentSystem(SimulationContext context) {
        this.world = context.getWorld();
    }

    public void processWeek(long currentMinute) {
        for (Settlement settlement : world.geography.getSettlements().values()) {
            ArrayList<Person> candidates = new ArrayList<>();
            for (Person person : world.people.values()) {
                if (person.alive && person.currentSettlementId != null
                        && person.currentSettlementId == settlement.id
                        && person.employerId == null
                        && !person.isChild(currentMinute)
                        && !person.isElderly(currentMinute)) {
                    candidates.add(person);
                }
            }
            candidates.sort(Comparator.comparingLong(person -> person.id));
            int candidateIndex = 0;
            for (Workplace workplace : settlement.workplaces) {
                while (workplace.getWorkerCount() < workplace.maxWorkers
                        && candidateIndex < candidates.size()) {
                    Person worker = candidates.get(candidateIndex++);
                    if (workplace.addWorker(worker.id)) worker.employerId = workplace.id;
                }
            }
        }
    }

    public void processDay() {
        for (Workplace workplace : world.workplaces.values()) {
            Household owner = workplace.ownerHouseholdId == null
                    ? null : world.households.get(workplace.ownerHouseholdId);
            for (Long workerId : new ArrayList<>(workplace.workerIds)) {
                Person worker = world.people.get(workerId);
                if (worker == null || !worker.alive || worker.householdId == null) {
                    workplace.removeWorker(workerId);
                    continue;
                }
                Household workerHousehold = world.households.get(worker.householdId);
                if (workerHousehold == null || owner == null || owner.id == workerHousehold.id) continue;
                if (owner.account.subtract(DAILY_WAGE)) workerHousehold.account.add(DAILY_WAGE);
            }
        }
    }
}
