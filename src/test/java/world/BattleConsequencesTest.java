package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;

class BattleConsequencesTest {

    @Test
    void personalVictoryPaysSalvageExperienceSkillsAndConquestCredit() {
        CampaignSession session = new CampaignSession(778L);
        PlayerCampaignState player = session.getPlayerState();
        WorldParty party = session.getWorld().parties.get(player.partyId);
        Person adventurer = session.getWorld().people.get(player.personId);
        Realm enemyRealm = session.getWorld().realms.values().stream()
                .findFirst().orElseThrow();

        double contributionBefore = player.conquestContribution;
        long coinsBefore = party.money.copperCoins;
        double experienceBefore = adventurer.militaryExperience;
        int martialBefore = adventurer.skills.martial;
        int leadershipBefore = adventurer.skills.leadership;

        String summary = session.recordPlayerBattleOutcome(
                true, false, 6, 8, 3, 4,
                true, false, false, enemyRealm.id);

        assertTrue(summary.contains("Personal spoils"));
        assertTrue(player.conquestContribution > contributionBefore + 40.0);
        assertTrue(party.money.copperCoins > coinsBefore + 100L);
        assertTrue(adventurer.militaryExperience > experienceBefore + 20.0);
        assertEquals(martialBefore + 1, adventurer.skills.martial);
        assertEquals(leadershipBefore + 1, adventurer.skills.leadership);
        assertEquals(-2, player.realmReputation.getOrDefault(enemyRealm.id, 0).intValue());
    }

    @Test
    void mercyImprovesReputationInsteadOfPunishingTheEnemyRealm() {
        CampaignSession session = new CampaignSession(779L);
        PlayerCampaignState player = session.getPlayerState();
        Realm enemyRealm = session.getWorld().realms.values().stream()
                .findFirst().orElseThrow();
        long settlementId = player.currentSettlementId;
        int localBefore = player.reputationAtSettlement(settlementId);

        session.recordPlayerBattleOutcome(
                true, false, 1, 3, 0, 6,
                false, false, true, enemyRealm.id);

        assertTrue(player.reputationAtSettlement(settlementId) >= localBefore + 5);
        assertEquals(1, player.realmReputation.getOrDefault(enemyRealm.id, 0).intValue());
    }

    @Test
    void gettingKnockedOutWhileYourArmyWinsWoundsWithoutCaptivityOrRansom() {
        CampaignSession session = new CampaignSession(780L);
        PlayerCampaignState player = session.getPlayerState();
        Person adventurer = session.getWorld().people.get(player.personId);
        adventurer.health.healthLevel = 100.0;
        adventurer.health.wounded = false;
        player.captiveUntilMinute = null;
        player.ransomCoins = 0L;

        CommandResult result = session.recordPlayerKnockedOutArmyHeldField(true);

        assertTrue(result.accepted);
        assertEquals("KNOCKED_OUT_VICTORY", result.reasonCode);
        assertTrue(adventurer.health.wounded);
        assertEquals(65.0, adventurer.health.healthLevel, 0.001);
        assertNull(player.captiveUntilMinute);
        assertEquals(0L, player.ransomCoins);
    }

    @Test
    void defeatRecordsNoPersonalVictoryRewards() {
        CampaignSession session = new CampaignSession(781L);
        PlayerCampaignState player = session.getPlayerState();
        double contributionBefore = player.conquestContribution;

        String summary = session.recordPlayerBattleOutcome(
                false, false, 12, 20, 5, 10,
                true, true, false, null);

        assertEquals("", summary);
        assertEquals(contributionBefore, player.conquestContribution, 0.001);
    }
}
