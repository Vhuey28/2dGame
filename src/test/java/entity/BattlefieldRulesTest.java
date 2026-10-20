package entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class BattlefieldRulesTest {

    @Test
    void livingPlayerCanAlwaysBasicAttackAfterRecovery() {
        assertTrue(BattlefieldRules.canBasicAttack(1, 0));
        assertTrue(BattlefieldRules.canBasicAttack(100, -1));
        assertFalse(BattlefieldRules.canBasicAttack(0, 0));
        assertFalse(BattlefieldRules.canBasicAttack(50, 1));
    }

    @Test
    void exhaustionSlowsButNeverDisablesTheNextSwing() {
        assertEquals(18, BattlefieldRules.attackCooldownTicks(20, 90));
        assertEquals(20, BattlefieldRules.attackCooldownTicks(20, 20));
        assertEquals(29, BattlefieldRules.attackCooldownTicks(20, 5));
        assertTrue(BattlefieldRules.attackCooldownTicks(2, 0) >= 6);
    }

    @Test
    void cleaveKeepsPrimaryHitStrongAndNeverDropsToZero() {
        assertEquals(20, BattlefieldRules.cleaveDamage(20, 0, 100));
        assertEquals(16, BattlefieldRules.cleaveDamage(20, 1, 100));
        assertEquals(12, BattlefieldRules.cleaveDamage(20, 5, 100));
        assertEquals(14, BattlefieldRules.cleaveDamage(20, 0, 0));
        assertEquals(1, BattlefieldRules.cleaveDamage(1, 12, 0));
    }

    @Test
    void freshEnemyDoesNotBreakFromOneMinorLoss() {
        assertFalse(BattlefieldRules.shouldEnemyBreak(
                20, 19, 70.0, false, 0, 5));
        assertFalse(BattlefieldRules.shouldEnemyBreak(
                20, 0, 20.0, true, 20, 20));
    }

    @Test
    void commanderLossAndHeavyCasualtiesBreakTheLine() {
        assertTrue(BattlefieldRules.shouldEnemyBreak(
                20, 16, 80.0, true, 1, 6));
        assertTrue(BattlefieldRules.shouldEnemyBreak(
                20, 8, 50.0, false, 5, 10));
        assertTrue(BattlefieldRules.shouldEnemyBreak(
                20, 5, 100.0, false, 0, 10));
    }

    @Test
    void surrenderNeedsAHopelessRemnant() {
        assertTrue(BattlefieldRules.surrenderAccepted(
                20, 10, 50.0, false, 5, 15));
        assertTrue(BattlefieldRules.surrenderAccepted(
                20, 11, 80.0, true, 0, 11));
        assertFalse(BattlefieldRules.surrenderAccepted(
                20, 15, 50.0, true, 10, 20));
        assertFalse(BattlefieldRules.surrenderAccepted(
                20, 0, 0.0, true, 20, 20));
    }

    @Test
    void projectedBattleSuppressesDuplicateAndUnrecruitedHeroes() {
        Set<Long> projected = Set.of(42L, 99L);

        assertTrue(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                true, false, true, 42L, projected));
        assertTrue(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                true, false, false, null, projected),
                "ambient recruitable heroes are not part of either projected army");
        assertFalse(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                true, true, true, 42L, projected), "the player-controlled hero remains active");
        assertFalse(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                true, false, true, 7L, projected), "an unrelated recruited hero is not a duplicate");
        assertFalse(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                false, false, true, 42L, projected), "heroes act normally outside projected battles");
        assertFalse(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                false, false, false, null, projected), "ambient heroes remain in local scenes");
        assertFalse(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                true, false, true, null, projected));
        assertFalse(BattlefieldRules.shouldSuppressHeroInProjectedBattle(
                true, false, true, 42L, null));
    }

    @Test
    void remainingForceResultSupportsEitherSideFinishingTheBattle() {
        assertEquals(1, BattlefieldRules.remainingForceResult(10.0, 8.0));
        assertEquals(-1, BattlefieldRules.remainingForceResult(3.0, 5.0));
        assertEquals(0, BattlefieldRules.remainingForceResult(10.0, 9.5));
        assertEquals(0, BattlefieldRules.remainingForceResult(0.0, 0.0));
    }
}
