package world;

import java.util.Map;

/** Headless deterministic long-run harness used by tests and scale diagnostics. */
public final class SoakRunner {
    private final WorldInvariantValidator validator = new WorldInvariantValidator();

    public Result run(WorldSimulation simulation, int years, int validationIntervalDays) {
        if (years < 0 || validationIntervalDays <= 0) throw new IllegalArgumentException("Invalid soak duration");
        long start = System.nanoTime();
        int totalDays = years * WorldConfig.MONTHS_PER_YEAR * WorldConfig.DAYS_PER_MONTH;
        WorldInvariantValidator.Report report = validator.validate(simulation.getWorld());
        report.throwIfInvalid();
        for (int day = 1; day <= totalDays; day++) {
            simulation.advanceMinutes(WorldConfig.MINUTES_PER_DAY);
            if (day % validationIntervalDays == 0 || day == totalDays) {
                report = validator.validate(simulation.getWorld());
                report.throwIfInvalid();
            }
        }
        return new Result(years, totalDays, System.nanoTime() - start, report,
                simulation.getProfile(), Metrics.capture(simulation));
    }

    /** Compact outcome metrics for comparing seeds and spotting silent world collapse. */
    public static final class Metrics {
        public final int livingPopulation;
        public final int occupiedHouseholds;
        public final double averageFoodSecurity;
        public final double averageSettlementPressure;
        public final double worstSettlementPressure;
        public final double averageLoyalty;
        public final int crisisSettlements;
        public final int totalWars;
        public final int activeWars;
        public final int retainedCrimeIncidents;
        public final int openContracts;
        public final int activeContracts;
        public final int completedContracts;
        public final int historicalEvents;
        public final int activeArmies;

        private Metrics(int livingPopulation, int occupiedHouseholds, double averageFoodSecurity,
                double averageSettlementPressure, double worstSettlementPressure, double averageLoyalty,
                int crisisSettlements, int totalWars, int activeWars, int retainedCrimeIncidents,
                int openContracts, int activeContracts, int completedContracts, int historicalEvents,
                int activeArmies) {
            this.livingPopulation = livingPopulation;
            this.occupiedHouseholds = occupiedHouseholds;
            this.averageFoodSecurity = averageFoodSecurity;
            this.averageSettlementPressure = averageSettlementPressure;
            this.worstSettlementPressure = worstSettlementPressure;
            this.averageLoyalty = averageLoyalty;
            this.crisisSettlements = crisisSettlements;
            this.totalWars = totalWars;
            this.activeWars = activeWars;
            this.retainedCrimeIncidents = retainedCrimeIncidents;
            this.openContracts = openContracts;
            this.activeContracts = activeContracts;
            this.completedContracts = completedContracts;
            this.historicalEvents = historicalEvents;
            this.activeArmies = activeArmies;
        }

        public static Metrics capture(WorldSimulation simulation) {
            WorldState world = simulation.getWorld();
            long minute = simulation.getClock().getWorldMinute();
            int living = 0;
            for (Person person : world.people.values()) if (person.alive) living++;
            int occupied = 0;
            double food = 0.0;
            for (Household household : world.households.values()) {
                if (household.getMemberCount() == 0) continue;
                occupied++;
                food += SettlementPressure.clamp01(household.foodSecurity);
            }
            Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
            double pressureTotal = 0.0;
            double loyaltyTotal = 0.0;
            double worst = 0.0;
            int crisis = 0;
            for (SettlementPressure pressure : pressures.values()) {
                pressureTotal += pressure.overallPressure;
                loyaltyTotal += pressure.loyalty;
                worst = Math.max(worst, pressure.overallPressure);
                if (pressure.outlook == SettlementPressure.Outlook.CRISIS) crisis++;
            }
            int pressureCount = Math.max(1, pressures.size());
            int activeWars = 0;
            for (War war : world.wars.values()) if (war.state == War.WarState.ACTIVE) activeWars++;
            int open = 0, active = 0, completed = 0;
            for (Contract contract : world.contracts.values()) {
                if (contract.status == Contract.ContractStatus.OPEN) open++;
                else if (contract.status == Contract.ContractStatus.ACTIVE) active++;
                else if (contract.status == Contract.ContractStatus.COMPLETED) completed++;
            }
            int armies = 0;
            for (Army army : world.armies.values()) {
                if (army.state != Army.ArmyState.DISBANDED && !army.temporaryBattleArmy) armies++;
            }
            return new Metrics(living, occupied, occupied == 0 ? 0.0 : food / occupied,
                    pressureTotal / pressureCount, worst, loyaltyTotal / pressureCount, crisis,
                    world.wars.size(), activeWars, world.crimeIncidents.size(), open, active,
                    completed, simulation.getContext().eventHistory.getHistorical().size(), armies);
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT,
                    "pop=%d, households=%d, food=%.2f, pressure avg/worst=%.2f/%.2f, loyalty=%.2f, "
                            + "crisis=%d, wars=%d/%d, crimes=%d, contracts open/active/done=%d/%d/%d, "
                            + "history=%d, armies=%d",
                    livingPopulation, occupiedHouseholds, averageFoodSecurity,
                    averageSettlementPressure, worstSettlementPressure, averageLoyalty,
                    crisisSettlements, activeWars, totalWars, retainedCrimeIncidents,
                    openContracts, activeContracts, completedContracts, historicalEvents, activeArmies);
        }
    }

    public static final class Result {
        public final int years;
        public final int days;
        public final long elapsedNanos;
        public final WorldInvariantValidator.Report invariantReport;
        public final SimulationProfile profile;
        public final Metrics metrics;

        Result(int years, int days, long elapsedNanos, WorldInvariantValidator.Report invariantReport,
               SimulationProfile profile, Metrics metrics) {
            this.years = years;
            this.days = days;
            this.elapsedNanos = elapsedNanos;
            this.invariantReport = invariantReport;
            this.profile = profile;
            this.metrics = metrics;
        }

        public double getElapsedSeconds() { return elapsedNanos / 1_000_000_000.0; }
    }
}
