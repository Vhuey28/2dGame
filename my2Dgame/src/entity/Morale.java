package entity;

/**
 * Per-unit morale. Morale drops when allies die nearby or the unit takes
 * damage, and slowly regenerates when things are calm. Low morale shifts a
 * unit into WAVERING (reduced effectiveness — hook this up to your damage/
 * accuracy calculations if desired) and eventually ROUTED (should flee).
 *
 * Usage:
 *   morale.onDamageTaken(amount);
 *   morale.onAllyDied();          // call for nearby squadmates when one dies
 *   morale.onAllyDiedNearby(dist, maxRelevantDist); // scales impact by distance
 *   morale.update();              // once per tick, for regen
 *   if (morale.getState() == Morale.State.ROUTED) { ...flee logic... }
 */
public class Morale {

    public enum State { STEADY, WAVERING, ROUTED }

    private float current = 100f;
    private float max = 100f;

    // Tuning knobs — adjust to taste
    private float regenPerTick = 0.05f;
    private float damageMoralePenaltyRatio = 0.4f; // morale lost per point of damage taken
    private float allyDeathPenalty = 25f;
    private float wavThreshold = 40f; // below this: WAVERING
    private float routThreshold = 15f; // below this: ROUTED

    private int routedRecoveryCooldown = 0; // ticks before a routed unit can start recovering
    private static final int ROUTED_RECOVERY_DELAY = 180; // ~3 seconds at 60fps before morale can climb back out of ROUTED

    public void onDamageTaken(float damageAmount) {
        current -= damageAmount * damageMoralePenaltyRatio;
        clamp();
        if (getState() == State.ROUTED) routedRecoveryCooldown = ROUTED_RECOVERY_DELAY;
    }

    public void onAllyDied() {
        current -= allyDeathPenalty;
        clamp();
        routedRecoveryCooldown = ROUTED_RECOVERY_DELAY;
    }

    /** Scales the death penalty by distance — a squadmate dying next to you hits harder than one dying across the map. */
    public void onAllyDiedNearby(float distance, float maxRelevantDistance) {
        if (distance >= maxRelevantDistance) return;
        float factor = 1f - (distance / maxRelevantDistance);
        current -= allyDeathPenalty * factor;
        clamp();
        routedRecoveryCooldown = ROUTED_RECOVERY_DELAY;
    }

    /** Boosts morale — call this for a nearby commander/boss presence, or a "rally" order. */
    public void boost(float amount) {
        current += amount;
        clamp();
    }

    public void update() {
        if (routedRecoveryCooldown > 0) {
            routedRecoveryCooldown--;
            return; // no passive regen while recently hit/routed
        }
        current += regenPerTick;
        clamp();
    }

    public State getState() {
        if (current <= routThreshold) return State.ROUTED;
        if (current <= wavThreshold) return State.WAVERING;
        return State.STEADY;
    }

    public float getCurrent() { return current; }
    public float getMax() { return max; }
    public float getRatio() { return current / max; }

    private void clamp() {
        if (current < 0f) current = 0f;
        if (current > max) current = max;
    }
}
