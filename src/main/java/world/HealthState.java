package world;

/**
 * Health state for a person (world.md section 9.1).
 * Represents overall health status from injury, sickness, age.
 */
public final class HealthState {
    public double healthLevel = 100.0; // 0.0 - 100.0
    public boolean injured = false;
    public boolean sick = false;
    public boolean wounded = false;
    public double injurySeverity = 0.0; // 0.0 - 100.0

    /** Clamp healthLevel to [0.0, 100.0]. */
    public void clamp() {
        healthLevel = Math.max(0.0, Math.min(100.0, healthLevel));
    }

    @Override
    public String toString() {
        return String.format("Health[%.1f, injured=%b, sick=%b, wounded=%b]",
            healthLevel, injured, sick, wounded);
    }
}