package world;

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
        return new Result(years, totalDays, System.nanoTime() - start, report, simulation.getProfile());
    }

    public static final class Result {
        public final int years;
        public final int days;
        public final long elapsedNanos;
        public final WorldInvariantValidator.Report invariantReport;
        public final SimulationProfile profile;

        Result(int years, int days, long elapsedNanos, WorldInvariantValidator.Report invariantReport,
               SimulationProfile profile) {
            this.years = years;
            this.days = days;
            this.elapsedNanos = elapsedNanos;
            this.invariantReport = invariantReport;
            this.profile = profile;
        }

        public double getElapsedSeconds() { return elapsedNanos / 1_000_000_000.0; }
    }
}
