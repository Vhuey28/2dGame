package entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-unit threat table: tracks how much "threat" each potential target has
 * generated (damage dealt, proximity, taunts, healer bonus), and returns the
 * highest-threat target for aggro decisions. This replaces "always chase the
 * player" / "always attack nearest enemy" with something that reacts to who's
 * actually been fighting this unit.
 *
 * Usage:
 *   threatTable.addThreat(attacker, damageAmount);   // call wherever damage is applied
 *   threatTable.addProximityThreat(target, distance); // optional, call periodically
 *   Object target = threatTable.getHighestThreatTarget();
 *   threatTable.decay(); // call once per tick so old threat fades
 */
public class ThreatTable {

    private final Map<Object, Float> threat = new HashMap<>();
    private float decayRatePerTick = 0.15f;   // threat lost per tick, flat
    private float decayMultiplier = 0.999f;   // slow proportional decay on top
    private static final float HEALER_THREAT_MULTIPLIER = 1.5f; // healers draw more aggro once damaged

    public void addThreat(Object source, float amount) {
        if (source == null) return;
        threat.merge(source, amount, Float::sum);
    }

    /** Call this when a target is identified as a healer/support unit — makes it stickier once it has any threat. */
    public void addHealerThreat(Object healerSource, float amount) {
        addThreat(healerSource, amount * HEALER_THREAT_MULTIPLIER);
    }

    /** Small passive threat gain for being near the unit — keeps nearby targets relevant even without dealing damage. */
    public void addProximityThreat(Object source, float distance, float maxRelevantDistance) {
        if (source == null || distance >= maxRelevantDistance) return;
        float proximityFactor = 1f - (distance / maxRelevantDistance);
        addThreat(source, proximityFactor * 0.5f);
    }

    public Object getHighestThreatTarget() {
        Object best = null;
        float bestVal = -1f;
        for (Map.Entry<Object, Float> e : threat.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public float getThreat(Object source) {
        return threat.getOrDefault(source, 0f);
    }

    /** Call once per game tick. Removes stale entries and fades old threat so targeting can shift over time. */
    public void decay() {
        threat.replaceAll((k, v) -> Math.max(0f, v * decayMultiplier - decayRatePerTick));
        threat.values().removeIf(v -> v <= 0.01f);
    }

    public void clearTarget(Object source) {
        threat.remove(source);
    }

    public void clearAll() {
        threat.clear();
    }

    public boolean isEmpty() {
        return threat.isEmpty();
    }
}