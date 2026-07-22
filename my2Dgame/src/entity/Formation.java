package entity;

import java.awt.Point;

/**
 * Computes a world-space slot position for a given member index in a
 * formation, centered on a leader position and oriented by a facing angle
 * (radians, 0 = facing along +X / right). Use the returned Point as a unit's
 * movement target instead of a hardcoded offset from the leader.
 *
 * Usage:
 *   Formation formation = new Formation(Formation.Type.WEDGE, tileSize * 1.5f);
 *   Point slot = formation.getSlotPosition(memberIndex, leaderX, leaderY, facingAngleRadians);
 */
public class Formation {

    public enum Type { LINE, WEDGE, CIRCLE, SCATTERED }

    public Type type;
    public float spacing; // pixel distance between adjacent slots

    public Formation(Type type, float spacing) {
        this.type = type;
        this.spacing = spacing;
    }

    public Point getSlotPosition(int memberIndex, float leaderX, float leaderY, float facingAngleRad) {
        float localX, localY; // offset in formation-local space (before rotation), leader faces +X

        switch (type) {
            case LINE: {
                // Single row behind the leader, alternating left/right as index increases
                int row = (memberIndex / 2) + 1;
                int side = (memberIndex % 2 == 0) ? -1 : 1;
                localX = -row * spacing;
                localY = side * (row * spacing * 0.5f);
                break;
            }
            case WEDGE: {
                // V-shape trailing behind the leader — classic "commander at the tip" formation
                int row = (memberIndex / 2) + 1;
                int side = (memberIndex % 2 == 0) ? -1 : 1;
                localX = -row * spacing * 0.8f;
                localY = side * row * spacing * 0.6f;
                break;
            }
            case CIRCLE: {
                // Evenly distributed ring around the leader — good for "defend healers" duty
                int totalSlotsEstimate = Math.max(6, memberIndex + 1); // caller should pass consistent total via squad size ideally
                double angle = (2 * Math.PI / totalSlotsEstimate) * memberIndex;
                localX = (float) (Math.cos(angle) * spacing);
                localY = (float) (Math.sin(angle) * spacing);
                break;
            }
            case SCATTERED:
            default: {
                // Deterministic pseudo-random spread based on index, so it's stable across ticks but not rigid
                float pseudoAngle = (memberIndex * 137.5f) % 360f; // golden-angle spread, avoids clumping
                double rad = Math.toRadians(pseudoAngle);
                float dist = spacing * (0.6f + 0.4f * ((memberIndex * 53) % 10) / 10f);
                localX = (float) (Math.cos(rad) * dist);
                localY = (float) (Math.sin(rad) * dist);
                break;
            }
        }

        // Rotate local offset by facing angle, then translate to world position
        float rotatedX = (float) (localX * Math.cos(facingAngleRad) - localY * Math.sin(facingAngleRad));
        float rotatedY = (float) (localX * Math.sin(facingAngleRad) + localY * Math.cos(facingAngleRad));

        return new Point(Math.round(leaderX + rotatedX), Math.round(leaderY + rotatedY));
    }

    /** Convenience overload assuming the leader faces along +X (angle 0) — fine for top-down movement without facing tracking. */
    public Point getSlotPosition(int memberIndex, float leaderX, float leaderY) {
        return getSlotPosition(memberIndex, leaderX, leaderY, 0f);
    }
}
