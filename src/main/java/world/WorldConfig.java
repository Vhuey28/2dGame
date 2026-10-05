package world;

/**
 * Tunable world configuration constants. Keeps simulation behavior
 * in one place so it can be changed without touching simulation code.
 */
public final class WorldConfig {
    public static final int MINUTES_PER_HOUR = 60;
    public static final int HOURS_PER_DAY = 24;
    public static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY;
    public static final int DAYS_PER_MONTH = 30;
    public static final int MONTHS_PER_YEAR = 12;
    public static final int MINUTES_PER_MONTH = MINUTES_PER_DAY * DAYS_PER_MONTH;
    public static final int MINUTES_PER_YEAR = MINUTES_PER_MONTH * MONTHS_PER_YEAR;

    public static final int MAX_STEPS_PER_FRAME = 2000; // prevent unlimited catch-up
    public static final long STEP_MINUTES = 1L;         // advance in 1-minute steps
    public static final double WORLD_MINUTES_PER_REAL_SECOND = 1.0; // 1 world minute per real second

    public static final long DEFAULT_SEED = 12345L;
    public static final int DEFAULT_POPULATION = 300;

    private WorldConfig() {}
}