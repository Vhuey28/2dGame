package world.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.Household;
import world.Person;
import world.SettlementPressure;
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
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        for (Settlement settlement : settlements) {
            ArrayList<Person> candidates = new ArrayList<>();
            for (Person person : world.people.values()) {
                if (person.alive && person.currentSettlementId != null
                        && person.currentSettlementId == settlement.id
                        && person.employerId == null
                        && person.officeId == null
                        && person.regimentId == null
                        && person.travelingPartyId == null
                        && person.type != Person.PersonType.NOBLE
                        && person.health.healthLevel > 20.0
                        && !person.isChild(currentMinute)
                        && !person.isElderly(currentMinute)) {
                    candidates.add(person);
                }
            }
            candidates.sort(Comparator.comparingLong(person -> person.id));
            List<Workplace> workplaces = new ArrayList<>(settlement.workplaces);
            workplaces.sort(Comparator
                    .comparingInt((Workplace workplace) -> producesFood(workplace) ? 0 : 1)
                    .thenComparingLong(workplace -> workplace.id));
            for (Workplace workplace : workplaces) {
                while (workplace.getWorkerCount() < workplace.maxWorkers && !candidates.isEmpty()) {
                    Person worker = bestCandidate(workplace, candidates);
                    if (worker == null) break;
                    candidates.remove(worker);
                    if (!workplace.addWorker(worker.id)) continue;
                    worker.employerId = workplace.id;
                    worker.currentGoal = Person.Goal.WORK;
                    worker.goalReason = "Employed at " + workplace.name;
                    worker.goalUrgency = 35;
                    worker.goalUpdatedMinute = currentMinute;
                    worker.rememberLifeEvent("Took work at " + workplace.name, currentMinute);
                    worker.needs.wealthSecurity = Math.min(1.0, worker.needs.wealthSecurity + 0.03);
                }
            }
        }
    }

    private Person bestCandidate(Workplace workplace, List<Person> candidates) {
        Person best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Person candidate : candidates) {
            double score = candidateScore(workplace, candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private double candidateScore(Workplace workplace, Person candidate) {
        double skill = bestRelevantSkill(workplace, candidate);
        double goal = switch (candidate.currentGoal) {
            case FIND_WORK -> candidate.goalUrgency * 0.42;
            case FIND_FOOD -> candidate.goalUrgency * (producesFood(workplace) ? 0.44 : 0.34);
            case BUILD_WEALTH, SUPPORT_FAMILY -> candidate.goalUrgency * 0.27;
            case RECOVER -> -35.0;
            case SEEK_SAFER_HOME -> -10.0;
            default -> candidate.goalUrgency * 0.12;
        };
        Household household = candidate.householdId == null ? null : world.households.get(candidate.householdId);
        double hunger = household == null ? 1.0 - candidate.needs.foodSecurity
                : 1.0 - SettlementPressure.clamp01(household.foodSecurity);
        return skill * 1.25 + goal + hunger * 24.0
                + (1.0 - candidate.needs.wealthSecurity) * 10.0
                + Math.min(8.0, Math.max(0.0, candidate.health.healthLevel - 50.0) * 0.08);
    }

    private static int bestRelevantSkill(Workplace workplace, Person candidate) {
        int best = Math.max(candidate.skills.farming,
                Math.max(candidate.skills.crafting, candidate.skills.trade));
        if (workplace.recipes.isEmpty()) return best;
        int recipeBest = 0;
        for (ProductionRecipe recipe : workplace.recipes) {
            String skill = recipe.skillRequired == null ? "" : recipe.skillRequired.toLowerCase();
            int value = skill.contains("farm") ? candidate.skills.farming
                    : skill.contains("trade") || skill.contains("merchant") ? candidate.skills.trade
                    : skill.contains("craft") || skill.contains("smith") || skill.contains("wood")
                            ? candidate.skills.crafting : best;
            recipeBest = Math.max(recipeBest, value);
        }
        return recipeBest;
    }

    private static boolean producesFood(Workplace workplace) {
        for (ProductionRecipe recipe : workplace.recipes) {
            if (recipe.outputs.containsKey(GoodType.GRAIN)
                    || recipe.outputs.containsKey(GoodType.VEGETABLES)) return true;
        }
        return false;
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
