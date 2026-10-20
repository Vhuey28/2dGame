package entity;

import java.util.Set;

/**
 * Pure, UI-free rules shared by the local combat entities and the campaign
 * battle projection. Keeping the thresholds here makes the "hero keeps
 * fighting while alive" and rout rules directly testable without starting
 * the Swing game.
 */
public final class BattlefieldRules {
    private BattlefieldRules() { }

    /** A basic attack is gated by being alive and by swing recovery only. */
    public static boolean canBasicAttack(int health, int attackCooldown) {
        return health > 0 && attackCooldown <= 0;
    }

    /**
     * Low stamina slows the next swing but never prevents it. A rested player
     * attacks a little faster than the old base cadence; an exhausted player
     * remains dangerous but can be overwhelmed.
     */
    public static int attackCooldownTicks(int baseCooldown, int stamina) {
        int base = Math.max(4, baseCooldown);
        if (stamina < 10) return Math.max(6, (int) Math.round(base * 1.45));
        if (stamina < 30) return base;
        return Math.max(6, (int) Math.round(base * 0.88));
    }

    /** First target takes full damage; cleaved targets and exhausted swings are softened. */
    public static int cleaveDamage(int baseDamage, int targetIndex, int stamina) {
        double damage = Math.max(1, baseDamage);
        if (targetIndex > 0) damage *= Math.max(0.55, 0.85 - targetIndex * 0.05);
        if (stamina < 10) damage *= 0.72;
        return Math.max(1, (int) Math.round(damage));
    }

    /**
     * Enemy break test. Commander loss matters immediately, but normally the
     * side must also have suffered meaningful losses so a fresh army cannot be
     * routed by one lucky hit.
     */
    public static boolean shouldEnemyBreak(int initialEnemies, int standingEnemies,
            double startingMorale, boolean commanderDown, int playerKills,
            int opposingStanding) {
        if (initialEnemies <= 0 || standingEnemies <= 0) return false;
        double lossFraction = 1.0 - standingEnemies / (double) initialEnemies;
        double pressure = lossFraction * 72.0
                + Math.max(0.0, 62.0 - startingMorale) * 0.32
                + (commanderDown ? 30.0 : 0.0)
                + Math.min(20.0, Math.max(0, playerKills) * 2.0);
        if (commanderDown && lossFraction >= 0.18) return true;
        if (lossFraction >= 0.35 && pressure >= 55.0) return true;
        return standingEnemies <= Math.max(1, initialEnemies / 4)
                && opposingStanding >= Math.max(2, standingEnemies * 2);
    }

    /** Deterministic surrender response used after the enemy has already broken. */
    public static boolean surrenderAccepted(int initialEnemies, int remainingEnemies,
            double startingMorale, boolean commanderDown, int playerKills,
            int opposingStanding) {
        if (initialEnemies <= 0 || remainingEnemies <= 0) return false;
        double remainingFraction = remainingEnemies / (double) initialEnemies;
        double hopelessness = (1.0 - remainingFraction) * 55.0
                + Math.max(0.0, 65.0 - startingMorale) * 0.35
                + (commanderDown ? 25.0 : 0.0)
                + Math.min(15.0, Math.max(0, playerKills) * 1.5)
                + Math.max(0, opposingStanding - remainingEnemies) * 2.0;
        return remainingFraction <= 0.55 && hopelessness >= 48.0;
    }

    /**
     * Whether a local Hero must sit out a projected campaign battle. A Hero
     * whose world person is already a projected troop would give that person
     * a second body, health pool, and unreconciled abilities; an unrecruited
     * map hero is not part of either projected army at all. The troop is the
     * reconciled representation, and the player-controlled active hero is
     * never suppressed.
     */
    public static boolean shouldSuppressHeroInProjectedBattle(boolean projectedBattleActive,
            boolean activePlayerHero, boolean recruited, Long sourcePersonId,
            Set<Long> projectedPersonIds) {
        if (!projectedBattleActive || activePlayerHero) return false;
        if (!recruited) return true;
        return sourcePersonId != null && projectedPersonIds != null
                && projectedPersonIds.contains(sourcePersonId);
    }

    /** Returns 1 when the first force should prevail, -1 for the second, or 0 for a draw. */
    public static int remainingForceResult(double firstPower, double secondPower) {
        double strongest = Math.max(firstPower, secondPower);
        if (strongest <= 0.0) return 0;
        if (Math.abs(firstPower - secondPower) / strongest < 0.08) return 0;
        return firstPower > secondPower ? 1 : -1;
    }
}
