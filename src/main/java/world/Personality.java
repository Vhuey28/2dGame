package world;

/**
 * Personality axes for decision-making (world.md section 9.3).
 * Named traits shown to the player can be derived from thresholds.
 */
public final class Personality {
    public double ambition = 50.0;   // 0.0 - 100.0
    public double bravery = 50.0;
    public double compassion = 50.0;
    public double greed = 50.0;
    public double honor = 50.0;
    public double loyalty = 50.0;
    public double sociability = 50.0;
    public double zeal = 50.0;
    public double patience = 50.0;

    /** Get a personality axis by name. Returns 0.0 if not found. */
    public double get(String axisName) {
        return switch (axisName.toLowerCase()) {
            case "ambition" -> ambition;
            case "bravery" -> bravery;
            case "compassion" -> compassion;
            case "greed" -> greed;
            case "honor" -> honor;
            case "loyalty" -> loyalty;
            case "sociability" -> sociability;
            case "zeal" -> zeal;
            case "patience" -> patience;
            default -> 0.0;
        };
    }

    /** Clamp all values to [0.0, 100.0]. */
    public void clamp() {
        ambition = clamp(ambition);
        bravery = clamp(bravery);
        compassion = clamp(compassion);
        greed = clamp(greed);
        honor = clamp(honor);
        loyalty = clamp(loyalty);
        sociability = clamp(sociability);
        zeal = clamp(zeal);
        patience = clamp(patience);
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }
}