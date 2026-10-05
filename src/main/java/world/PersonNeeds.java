package world;

/**
 * Needs of a person, represented as 0.0-1.0 (or 0-100) satisfaction levels.
 * Updated daily. Do not update all needs every hour if daily processing is sufficient.
 */
public final class PersonNeeds {
    // Initial seven needs as per world.md section 10.1
    public double foodSecurity = 1.0;
    public double shelter = 1.0;
    public double health = 1.0;
    public double safety = 1.0;
    public double wealthSecurity = 1.0;
    public double socialBelonging = 1.0;
    public double politicalSatisfaction = 1.0;

    /** Clamp all values to [0.0, 1.0]. */
    public void clamp() {
        foodSecurity = clampValue(foodSecurity);
        shelter = clampValue(shelter);
        health = clampValue(health);
        safety = clampValue(safety);
        wealthSecurity = clampValue(wealthSecurity);
        socialBelonging = clampValue(socialBelonging);
        politicalSatisfaction = clampValue(politicalSatisfaction);
    }

    private double clampValue(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Override
    public String toString() {
        return String.format("Needs[Food:%.2f Shelter:%.2f Health:%.2f Safety:%.2f Wealth:%.2f Social:%.2f Pol:%.2f]",
            foodSecurity, shelter, health, safety, wealthSecurity, socialBelonging, politicalSatisfaction);
    }
}