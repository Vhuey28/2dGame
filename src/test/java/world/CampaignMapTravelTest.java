package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.Regiment;
import world.save.CampaignSaveCodec;

/**
 * Continuous Bannerlord-style travel on the campaign map: the party is a token
 * on the road network while simulated time runs, rather than a teleport.
 */
class CampaignMapTravelTest {

    private static List<Settlement> sortedSettlements(WorldState world) {
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        return settlements;
    }

    private static Settlement reachableDestination(CampaignSession session, long originId,
            long... excludedIds) {
        WorldState world = session.getWorld();
        for (Settlement candidate : sortedSettlements(world)) {
            if (candidate.id == originId) continue;
            boolean excluded = false;
            for (long excludedId : excludedIds) if (candidate.id == excludedId) excluded = true;
            if (excluded) continue;
            if (!world.geography.getRouteGraph().findShortestRoute(originId, candidate.id).isEmpty()) {
                return candidate;
            }
        }
        throw new AssertionError("no reachable settlement from " + originId);
    }

    /** Advances the simulation clock, then lets the session move the road token. */
    private static void advanceWorldMinutes(CampaignSession session, long minutes) {
        session.getSimulation().advanceMinutes(minutes);
        session.update(0.0);
    }

    private static double distanceFrom(WorldParty party, Settlement settlement) {
        assertNotNull(party.position);
        assertNotNull(settlement.position);
        return Math.hypot(party.position.x - settlement.position.x,
                party.position.y - settlement.position.y);
    }

    @Test
    void partyRidesAlongRoadsInsteadOfTeleporting() {
        CampaignSession session = new CampaignSession(12345L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        long originId = player.currentSettlementId;
        Settlement origin = world.geography.getSettlement(originId);
        Settlement destination = reachableDestination(session, originId);
        WorldParty party = world.parties.get(player.partyId);
        assertNotNull(origin);
        assertNotNull(party);

        CommandResult started = session.beginTravelTo(destination.id);
        assertTrue(started.accepted, started.message);
        assertEquals("TRAVEL_STARTED", started.reasonCode);
        assertTrue(player.isTraveling());
        assertEquals(originId, player.currentSettlementId,
                "the last settled location stays canonical until final arrival");
        assertEquals(WorldParty.PartyState.TRAVELING, party.state);
        assertEquals(destination.id, party.destinationSettlementId.longValue());

        CampaignSnapshot enRoute = session.getSnapshot();
        assertTrue(enRoute.player.traveling);
        assertEquals(destination.id, enRoute.player.travelDestinationSettlementId.longValue());
        assertEquals(destination.name, enRoute.player.travelDestinationName);
        assertTrue(enRoute.player.travelEtaMinutes > 0L);
        assertTrue(enRoute.player.travelSpeedPerHour > 0.0);

        CommandResult shopping = session.buyFromSettlement(originId, GoodType.GRAIN, 1);
        assertFalse(shopping.accepted);
        assertEquals("TRAVELING", shopping.reasonCode);

        advanceWorldMinutes(session, WorldConfig.MINUTES_PER_HOUR);
        assertTrue(player.isTraveling(), "one hour should not complete an inter-settlement road");
        assertTrue(distanceFrom(party, origin) > 1.0,
                "the token should have left the origin settlement position");

        advanceWorldMinutes(session, 15L * WorldConfig.MINUTES_PER_DAY);
        assertFalse(player.isTraveling());
        assertEquals(destination.id, player.currentSettlementId);
        assertEquals(WorldParty.PartyState.AT_SETTLEMENT, party.state);
        assertEquals(destination.id, party.currentSettlementId.longValue());
        assertTrue(distanceFrom(party, destination) < 0.001);
        assertEquals(destination.id, session.getSnapshot().player.settlementId);
        assertEquals(destination.position.x, session.getSnapshot().player.worldX, 0.001);
        assertEquals(destination.position.y, session.getSnapshot().player.worldY, 0.001);
        for (Long memberId : party.memberPersonIds) {
            assertEquals(destination.id, world.people.get(memberId).currentSettlementId.longValue());
        }
    }

    @Test
    void journeyCanTurnBackAndBeRetargetedMidRoad() {
        CampaignSession session = new CampaignSession(777L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        long originId = player.currentSettlementId;
        Settlement first = reachableDestination(session, originId);
        Settlement second = reachableDestination(session, originId, first.id);

        assertTrue(session.beginTravelTo(first.id).accepted);
        advanceWorldMinutes(session, 2L * WorldConfig.MINUTES_PER_HOUR);
        assertTrue(player.isTraveling());

        CommandResult cancelled = session.cancelTravel();
        assertTrue(cancelled.accepted, cancelled.message);
        assertEquals(originId, player.travelDestinationSettlementId.longValue());
        advanceWorldMinutes(session, 4L * WorldConfig.MINUTES_PER_HOUR);
        assertFalse(player.isTraveling());
        assertEquals(originId, player.currentSettlementId);

        assertTrue(session.beginTravelTo(first.id).accepted);
        advanceWorldMinutes(session, WorldConfig.MINUTES_PER_HOUR);
        CommandResult retargeted = session.beginTravelTo(second.id);
        assertTrue(retargeted.accepted, retargeted.message);
        assertEquals(second.id, player.travelDestinationSettlementId.longValue());

        advanceWorldMinutes(session, 15L * WorldConfig.MINUTES_PER_DAY);
        assertFalse(player.isTraveling());
        assertEquals(second.id, player.currentSettlementId);
    }

    @Test
    void midJourneyTravelSurvivesSaveLoad(@TempDir Path directory) throws Exception {
        CampaignSession session = new CampaignSession(13579L);
        PlayerCampaignState player = session.getPlayerState();
        Settlement destination = reachableDestination(session, player.currentSettlementId);
        assertTrue(session.beginTravelTo(destination.id).accepted);
        advanceWorldMinutes(session, WorldConfig.MINUTES_PER_HOUR);
        assertTrue(player.isTraveling());
        int routeIndex = player.travelRouteIndex;
        double segmentProgress = player.travelSegmentProgress;
        WorldPosition enRoute = session.getWorld().parties.get(player.partyId).position;

        Path save = directory.resolve("campaign.ccq4");
        CampaignSaveCodec.save(session, save);
        CampaignSession loaded = CampaignSaveCodec.load(save);
        PlayerCampaignState loadedPlayer = loaded.getPlayerState();
        WorldParty loadedParty = loaded.getWorld().parties.get(loadedPlayer.partyId);

        assertTrue(loadedPlayer.isTraveling());
        assertEquals(destination.id, loadedPlayer.travelDestinationSettlementId.longValue());
        assertEquals(routeIndex, loadedPlayer.travelRouteIndex);
        assertEquals(segmentProgress, loadedPlayer.travelSegmentProgress, 0.000001);
        assertEquals(WorldParty.PartyState.TRAVELING, loadedParty.state);
        assertEquals(destination.id, loadedParty.destinationSettlementId.longValue());
        assertEquals(enRoute.x, loadedParty.position.x, 0.000001);
        assertEquals(enRoute.y, loadedParty.position.y, 0.000001);

        advanceWorldMinutes(loaded, 15L * WorldConfig.MINUTES_PER_DAY);
        assertFalse(loadedPlayer.isTraveling());
        assertEquals(destination.id, loadedPlayer.currentSettlementId);
    }

    @Test
    void snapshotExposesBannerlordStyleMapPresentationFields() {
        CampaignSession session = new CampaignSession(2468L);
        WorldState world = session.getWorld();
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        assertTrue(realms.size() >= 2);
        Realm playerRealm = realms.get(0);
        Realm enemyRealm = realms.get(1);
        assertTrue(session.joinKingdom(
                playerRealm.id, PlayerCampaignState.KingdomRole.MERCENARY).accepted);

        // Guarantee an enemy token for the hostility calculation even if world
        // generation changes which faction starts with a field army.
        Settlement anySettlement = sortedSettlements(world).get(0);
        Person anyPerson = world.people.values().stream()
                .filter(person -> person.alive).findFirst().orElseThrow();
        Army enemy = new Army(world.idGenerator.next(), enemyRealm.id, anyPerson.id,
                anySettlement.id, new WorldPosition(anySettlement.position.x, anySettlement.position.y));
        world.armies.put(enemy.id, enemy);
        War war = new War(world.idGenerator.next(), playerRealm.id, enemyRealm.id,
                War.WarGoal.PUNITIVE, session.getSnapshot().worldMinute);
        world.wars.put(war.id, war);
        advanceWorldMinutes(session, 1L);

        CampaignSnapshot snapshot = session.getSnapshot();
        assertEquals(2L, snapshot.settlements.stream().filter(settlement -> settlement.capital).count());
        for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
            assertNotNull(settlement.type);
            assertNotNull(settlement.provinceName);
            assertNotNull(settlement.terrain);
        }
        CampaignSnapshot.ArmyView enemyView = snapshot.findArmy(enemy.id);
        assertNotNull(enemyView);
        assertNotNull(enemyView.commanderName);
        assertTrue(enemyView.hostileToPlayer, "an active war should mark the enemy token hostile");
        for (CampaignSnapshot.CaravanView caravan : snapshot.caravans) {
            assertNotNull(caravan.leaderName);
            assertNotNull(caravan.currentSettlementId);
        }
        Settlement start = world.geography.getSettlement(session.getPlayerState().currentSettlementId);
        assertEquals(start.position.x, snapshot.player.worldX, 0.001);
        assertEquals(start.position.y, snapshot.player.worldY, 0.001);
        assertFalse(snapshot.player.traveling);
    }

    @Test
    void sendTroopsAutoResolveUsesCommandedArmy() {
        CampaignSession session = new CampaignSession(4242L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        assertNotNull(home);
        home.publicStockpile.add(GoodType.WEAPONS, 20);
        world.parties.get(player.partyId).money.add(5000L);
        assertTrue(session.raiseCompany(5, Regiment.RegimentType.LEVY_INFANTRY).accepted);
        Army mine = world.armies.get(player.commandedArmyId);
        assertNotNull(mine);

        Army enemy = world.armies.values().stream()
                .filter(army -> army.id != mine.id)
                .filter(army -> army.realmId != mine.realmId)
                .filter(army -> army.state != Army.ArmyState.DISBANDED)
                .filter(army -> army.state != Army.ArmyState.ENGAGED)
                .filter(army -> session.getSimulation().getMilitarySystem().strength(army) > 0)
                .findFirst().orElseThrow();
        enemy.position = new WorldPosition(mine.position.x, mine.position.y);
        enemy.currentSettlementId = mine.currentSettlementId;

        CommandResult resolved = session.autoResolveEngagement(enemy.id);
        assertTrue(resolved.accepted, resolved.message);
        assertTrue(resolved.reasonCode.startsWith("AUTO_RESOLVE_"), resolved.reasonCode);
    }
}
