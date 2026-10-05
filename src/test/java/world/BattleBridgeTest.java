package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.military.BattleBridge;
import world.military.BattleContext;
import world.military.BattleResult;

class BattleBridgeTest {

    @Test
    void contextProjectsEveryStrategicSoldierWithSourceIdentity() {
        CampaignSession session = new CampaignSession(201L);
        WorldState world = session.getWorld();
        ArrayList<Army> armies = sortedArmies(world);
        Army attacker = armies.get(0);
        Army defender = armies.get(1);

        BattleContext context = session.getSimulation().getBattleBridge().createContext(
                attacker.id, defender.id, true, 100L);

        assertTrue(context.playerInvolved);
        assertEquals(session.getSimulation().getMilitarySystem().strength(attacker),
                context.attacker.combatants.size());
        assertEquals(session.getSimulation().getMilitarySystem().strength(defender),
                context.defender.combatants.size());
        for (BattleContext.TacticalCombatant combatant : context.attacker.combatants) {
            Person person = world.people.get(combatant.sourcePersonId);
            assertNotNull(person);
            assertEquals(combatant.sourceRegimentId, person.regimentId);
            assertEquals(attacker.id, combatant.sourceArmyId);
            assertTrue(combatant.attackModifier > 0.0);
            assertTrue(combatant.defenseModifier > 0.0);
        }
        assertSame(context, world.battleContexts.get(context.battleId));
        assertEquals(Army.ArmyState.ENGAGED, attacker.state);
        assertThrows(UnsupportedOperationException.class,
                () -> context.attacker.combatants.clear());
    }

    @Test
    void tacticalResultUpdatesExactPeopleAndCanOnlyApplyOnce() {
        CampaignSession session = new CampaignSession(202L);
        WorldState world = session.getWorld();
        ArrayList<Army> armies = sortedArmies(world);
        BattleBridge bridge = session.getSimulation().getBattleBridge();
        BattleContext context = bridge.createContext(armies.get(0).id, armies.get(1).id,
                true, 500L);
        BattleContext.TacticalCombatant killed = context.attacker.combatants.get(0);
        BattleContext.TacticalCombatant wounded = context.defender.combatants.get(0);
        Map<Long, BattleResult.CasualtyOutcome> outcomes = new HashMap<>();
        outcomes.put(killed.sourcePersonId, BattleResult.CasualtyOutcome.KILLED);
        outcomes.put(wounded.sourcePersonId, BattleResult.CasualtyOutcome.SEVERELY_WOUNDED);
        BattleResult result = bridge.resultFromTactical(context.battleId,
                BattleResult.WinningSide.DEFENDER, outcomes, 73L);
        long livingBefore = world.people.values().stream().filter(person -> person.alive).count();

        CommandResult first = bridge.reconcile(result);
        long minuteAfterFirst = session.getSimulation().getClock().getWorldMinute();
        CommandResult duplicate = bridge.reconcile(result);

        assertTrue(first.accepted);
        assertFalse(duplicate.accepted);
        assertEquals("BATTLE_ALREADY_APPLIED", duplicate.reasonCode);
        assertEquals(minuteAfterFirst, session.getSimulation().getClock().getWorldMinute());
        assertFalse(world.people.get(killed.sourcePersonId).alive);
        assertTrue(world.people.get(wounded.sourcePersonId).alive);
        assertTrue(world.people.get(wounded.sourcePersonId).health.wounded);
        assertTrue(world.people.get(wounded.sourcePersonId).health.healthLevel < 100.0);
        assertEquals(livingBefore - 1,
                world.people.values().stream().filter(person -> person.alive).count());
        assertSame(result, world.battleResults.get(context.battleId));
        assertTrue(world.reconciledBattleIds.contains(context.battleId));
        assertEquals(573L, minuteAfterFirst);
    }

    @Test
    void invalidTacticalParticipantIsRejectedBeforeMutation() {
        CampaignSession session = new CampaignSession(203L);
        WorldState world = session.getWorld();
        ArrayList<Army> armies = sortedArmies(world);
        BattleBridge bridge = session.getSimulation().getBattleBridge();
        BattleContext context = bridge.createContext(armies.get(0).id, armies.get(1).id,
                true, 0L);
        Person outsider = world.people.values().stream()
                .filter(person -> !context.containsPerson(person.id)).findFirst().orElseThrow();
        BattleResult.BattleCasualty falseCasualty = new BattleResult.BattleCasualty(
                outsider.id, 999_999L, armies.get(0).id,
                BattleResult.CasualtyOutcome.KILLED, 100.0);
        BattleResult invalid = new BattleResult(context.battleId, BattleResult.WinningSide.ATTACKER,
                java.util.List.of(falseCasualty), java.util.List.of(), Map.of(), Map.of(),
                BattleResult.RetreatOutcome.DEFENDER_WITHDREW, 30L);

        CommandResult result = bridge.reconcile(invalid);

        assertFalse(result.accepted);
        assertTrue(outsider.alive);
        assertFalse(world.reconciledBattleIds.contains(context.battleId));
        assertEquals(0L, session.getSimulation().getClock().getWorldMinute());
    }

    @Test
    void aiResolutionUsesSameContextAndResultPipeline() {
        CampaignSession session = new CampaignSession(204L);
        WorldState world = session.getWorld();
        ArrayList<Army> armies = sortedArmies(world);

        world.military.BattleReport report = session.getSimulation().getMilitarySystem()
                .autoResolveEncounter(armies.get(0).id, armies.get(1).id, 1_000L);

        assertNotNull(report);
        assertTrue(world.battleContexts.containsKey(report.id));
        assertTrue(world.battleResults.containsKey(report.id));
        assertTrue(world.reconciledBattleIds.contains(report.id));
        assertTrue(session.getSimulation().getContext().eventHistory.getHistorical().stream()
                .anyMatch(event -> "AUTOSAVE_SAFE_POINT".equals(event.type)));
    }

    private ArrayList<Army> sortedArmies(WorldState world) {
        ArrayList<Army> armies = new ArrayList<>(world.armies.values());
        armies.sort(Comparator.comparingLong(army -> army.id));
        return armies;
    }
}
