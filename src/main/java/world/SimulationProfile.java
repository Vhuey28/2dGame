package world;

/** Runtime diagnostics only; measurements never participate in simulation decisions. */
public final class SimulationProfile {
    private long ticks;
    private long totalTickNanos;
    private long slowestTickNanos;
    private long heapUsedBytes;
    private long heapCommittedBytes;

    void recordTick(long elapsedNanos) {
        ticks++;
        totalTickNanos += elapsedNanos;
        slowestTickNanos = Math.max(slowestTickNanos, elapsedNanos);
        Runtime runtime = Runtime.getRuntime();
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        heapCommittedBytes = runtime.totalMemory();
    }

    public long getTicks() { return ticks; }
    public double getAverageTickMillis() { return ticks == 0 ? 0.0 : totalTickNanos / ticks / 1_000_000.0; }
    public double getSlowestTickMillis() { return slowestTickNanos / 1_000_000.0; }
    public long getHeapUsedBytes() { return heapUsedBytes; }
    public long getHeapCommittedBytes() { return heapCommittedBytes; }
}
