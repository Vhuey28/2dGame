package world;

/**
 * Monotonically increasing world time measured in minutes.
 * Advanced by WorldSimulation according to real-time accumulation
 * and configured game speed.
 */
public final class WorldClock {
    private long worldMinute;
    private int speed = 1;      // 1 = real-time, 2 = double-speed, etc.
    private boolean paused;

    public WorldClock() {
        this.worldMinute = 0L;
        this.speed = 1;
        this.paused = false;
    }

    public WorldClock(long initialMinute) {
        this.worldMinute = initialMinute;
        this.speed = 1;
        this.paused = false;
    }

    public long getWorldMinute() {
        return worldMinute;
    }

    public void setWorldMinute(long minute) {
        if (minute < 0L) throw new IllegalArgumentException("Minute cannot be negative");
        this.worldMinute = minute;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        if (speed <= 0) throw new IllegalArgumentException("Speed must be positive");
        this.speed = speed;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Advance the clock by specified minutes (not paused) */
    public void advance(long minutes) {
        if (paused) return;
        if (minutes < 0L) throw new IllegalArgumentException("Cannot advance by negative minutes");
        this.worldMinute += minutes;
    }

    /** Derived time components for convenience */
    public int getHourOfDay() {
        return (int) ((worldMinute / 60) % 24);
    }

    public int getDayOfMonth() {
        return (int) ((worldMinute / (60 * 24)) % 30); // Approximate month length
    }

    public int getMonthOfYear() {
        return (int) ((worldMinute / (60 * 24 * 30)) % 12);
    }

    public int getYear() {
        return (int) (worldMinute / (60L * 24 * 30 * 12));
    }

    @Override
    public String toString() {
        return String.format("WorldClock[minute=%d, speed=%dx, paused=%b]",
            worldMinute, speed, paused);
    }
}