package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.diplomacy.DiplomaticState;
import world.diplomacy.Grievance;
import world.diplomacy.Scheme;
import world.diplomacy.Treaty;

class DiplomacySimulationTest {

    @Test
    void campaignGeneratesPersistentDiplomacy() {
        CampaignSession session = new CampaignSession(12345L);
        WorldState world = session.getWorld();

        assertEquals(1, world.diplomaticStates.size());
        assertEquals(1, world.treaties.size());
        assertEquals(1, world.grievances.size());
        assertEquals(1, world.spyNetworks.size());
        assertEquals(1, world.schemes.size());
        DiplomaticState state = world.diplomaticStates.values().iterator().next();
        assertFalse(state.lastAiExplanation.isBlank());
        assertEquals(1, session.getSnapshot().activeTreatyCount);
    }

    @Test
    void treatyViolationCreatesConsequencesAndExplanation() {
        CampaignSession session = new CampaignSession(42L);
        WorldState world = session.getWorld();
        Treaty treaty = world.treaties.values().iterator().next();
        long violator = treaty.participantRealmIds.iterator().next();
        DiplomaticState state = world.diplomaticStates.values().iterator().next();
        int oldTrust = state.trust;

        CommandResult result = session.getSimulation().getDiplomacySystem()
                .violateTreaty(treaty.id, violator, 0L);

        assertTrue(result.accepted);
        assertTrue(treaty.violated);
        assertTrue(state.trust < oldTrust);
        assertTrue(world.grievances.values().stream().anyMatch(grievance ->
                grievance.type == Grievance.GrievanceType.TREATY_VIOLATION));
        String explanation = session.getSimulation().getDiplomacySystem().explainProposal(
                state.firstRealmId, state.secondRealmId, Treaty.TreatyType.DEFENSIVE_ALLIANCE);
        assertTrue(explanation.contains("relations"));
        assertTrue(explanation.contains("tension"));
    }

    @Test
    void treatiesExpireDuringMonthlySimulation() {
        CampaignSession session = new CampaignSession(99L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        Treaty treaty = new Treaty(world.idGenerator.next(), Treaty.TreatyType.MILITARY_ACCESS,
                realms.get(0).id, realms.get(1).id, 0L, (long) WorldConfig.MINUTES_PER_MONTH);
        world.treaties.put(treaty.id, treaty);
        world.diplomaticStates.values().iterator().next().treatyIds.add(treaty.id);

        session.getSimulation().advanceMinutes(WorldConfig.MINUTES_PER_MONTH);

        assertFalse(treaty.active);
        assertTrue(session.getSimulation().getContext().eventHistory.getHistorical().stream()
                .anyMatch(event -> "TREATY_EXPIRED".equals(event.type)));
    }

    @Test
    void undiscoveredSchemeDoesNotLeakAndProgressesThroughPhases() {
        CampaignSession session = new CampaignSession(7L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        Realm sponsor = realms.get(0);
        Realm target = realms.get(1);
        Person schemer = world.people.get(sponsor.rulerPersonId);
        schemer.skills.intrigue = 100;
        Scheme scheme = new Scheme(world.idGenerator.next(), Scheme.SchemeType.FABRICATE_CLAIM,
                sponsor.id, schemer.id, target.id, target.rulerPersonId, 10L, 0L);

        CommandResult started = session.getSimulation().getDiplomacySystem().startScheme(scheme);
        assertTrue(started.accepted);
        assertTrue(scheme.isVisibleTo(sponsor.id));
        assertFalse(scheme.isVisibleTo(target.id));

        for (int i = 0; i < 6 && scheme.active; i++) {
            session.getSimulation().advanceMinutes(WorldConfig.MINUTES_PER_MONTH);
        }

        assertFalse(scheme.active);
        assertNotEquals("IN_PROGRESS", scheme.outcome);
    }

    @Test
    void commandsRejectImpossibleDiplomacy() {
        CampaignSession session = new CampaignSession(5L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        Realm proposer = realms.get(0);
        Realm recipient = realms.get(1);
        proposer.treasury.copperCoins = 0L;

        CommandResult result = session.getSimulation().getDiplomacySystem().proposeTreaty(
                proposer.id, recipient.id, Treaty.TreatyType.TRIBUTE,
                WorldConfig.MINUTES_PER_MONTH, 0L);

        assertFalse(result.accepted);
        assertEquals("INSUFFICIENT_TREASURY", result.reasonCode);
    }
}
