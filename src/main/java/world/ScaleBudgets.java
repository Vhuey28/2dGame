package world;

/** Explicit Phase 10 budgets for the opt-in target-continent benchmark. */
public final class ScaleBudgets {
    public static final int TARGET_REALMS = 24;
    public static final int TARGET_SETTLEMENTS = 240;
    public static final int TARGET_PEOPLE = 24_000;
    public static final double MAX_AVERAGE_DAY_TICK_MILLIS = 50.0;
    public static final long MAX_HEAP_BYTES = 512L * 1024L * 1024L;
    public static final long MAX_SAVE_BYTES = 64L * 1024L * 1024L;
    public static final double MIN_RENDER_FRAME_RATE = 60.0;

    private ScaleBudgets() {}
}
