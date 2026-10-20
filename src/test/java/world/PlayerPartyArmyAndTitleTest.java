package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import entity.Enemy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.MilitarySystem;
import world.military.Regiment;
import world.politics.Title;

/**
 * Regression coverage for the player-party army, full-health enemy contacts,
 * enemy health bars, realm alignment for sieges, and the one-title rule.
 */
class PlayerPartyArmyAndTitleTest {

    @Test
    void untouchedEnemiesHideHealthBarsUntilHitOrWounded() {
        assertFalse(Enemy.shouldShowHealthBar(0, 40, 40),
                "a full-health enemy that has not been hit should not show a bar");
        assertTrue(Enemy.shouldShowHealthBar(1, 40, 40),
                "being hit reveals the bar even if the hit dealt no damage");
        assertTrue(Enemy.shouldShowHealthBar(0, 39, 40),
                "a wounded enemy keeps showing its bar");
        assertFalse(Enemy.shouldShowHealthBar(0, 60, 60),
                "an untouched commander also starts with no visible bar");
    }

    @Test
    void raisedCompanyTravelsInsidePlayerPartyInsteadOfTrailingBehind() {
        CampaignSession session = new CampaignSession(20261020L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session);
        Army company = raiseCompany(session);
        company.supplies.add(GoodType.GRAIN, 500);
        WorldParty party = world.parties.get(player.partyId);
        Settlement destination = reachableDestination(session, home.id);

        assertTrue(session.beginTravelTo(destination.id).accepted);
        assertEquals(Army.ArmyState.TRAVELING, company.state);
        assertTrue(company.routeRoadIds.isEmpty(),
                "the accompanying army must not simulate a second road route");
        assertEquals(destination.id, company.destinationSettlementId.longValue());
        assertPositionEquals(party.position, company.position);

        advanceWorldMinutes(session, WorldConfig.MINUTES_PER_HOUR);
        assertPositionEquals(party.position, company.position);
        CampaignSnapshot snapshot = session.getSnapshot();
        CampaignSnapshot.ArmyView armyView = snapshot.findArmy(company.id);
        assertNotNull(armyView);
        assertEquals(snapshot.player.worldX, armyView.worldX, 0.0001);
        assertEquals(snapshot.player.worldY, armyView.worldY, 0.0001);

        advanceWorldMinutes(session, 15L * WorldConfig.MINUTES_PER_DAY);
        assertFalse(player.isTraveling());
        assertEquals(destination.id, player.currentSettlementId);
        assertEquals(destination.id, company.currentSettlementId);
        assertEquals(Army.ArmyState.MUSTERED, company.state);
        assertPositionEquals(destination.position, company.position);
        for (Long regimentId : company.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            for (Long soldierId : regiment.soldierPersonIds) {
                Person soldier = world.people.get(soldierId);
                if (soldier == null || !soldier.alive) continue;
                assertEquals(destination.id, soldier.currentSettlementId.longValue());
                assertEquals(company.id, soldier.travelingPartyId.longValue());
            }
        }
    }

    @Test
    void detachedArmyWithOwnRouteIsNotTeleportedIntoPlayerParty() {
        CampaignSession session = new CampaignSession(20261024L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session);
        Army company = raiseCompany(session);
        Settlement armyDestination = reachableDestination(session, home.id);
        Settlement playerDestination = reachableDestination(session, home.id, armyDestination.id);
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        long minute = session.getSimulation().getClock().getWorldMinute();

        // A detached command (for example a realm army or a siege joiner) can
        // still have the player's origin as its last completed settlement and
        // share the player's former destination. Those ids alone must not be
        // mistaken for the army physically riding in the party.
        CommandResult detached = military.issueMoveOrder(
                company.id, armyDestination.id, Army.ArmyOrder.BESIEGE, minute);
        assertTrue(detached.accepted, detached.message);
        assertFalse(company.routeRoadIds.isEmpty());
        WorldPosition detachedPosition = new WorldPosition(
                home.position.x + 25.0, home.position.y + 25.0);
        company.position = new WorldPosition(detachedPosition.x, detachedPosition.y);
        int routeSize = company.routeRoadIds.size();

        assertTrue(session.beginTravelTo(armyDestination.id).accepted);
        assertPositionEquals(detachedPosition, company.position);
        assertEquals(routeSize, company.routeRoadIds.size());
        assertEquals(armyDestination.id, company.destinationSettlementId.longValue());

        assertTrue(session.beginTravelTo(playerDestination.id).accepted);
        assertEquals(playerDestination.id, player.travelDestinationSettlementId.longValue());
        assertPositionEquals(detachedPosition, company.position);
        assertEquals(routeSize, company.routeRoadIds.size());
        assertEquals(armyDestination.id, company.destinationSettlementId.longValue());
        assertEquals(Army.ArmyOrder.BESIEGE, company.order);
    }

    @Test
    void survivingArmyRetreatsToRefugeWithPartyAfterRoadDefeat() {
        CampaignSession session = new CampaignSession(20261025L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session);
        Army company = raiseCompany(session);
        company.supplies.add(GoodType.GRAIN, 100);
        WorldParty party = world.parties.get(player.partyId);
        Settlement destination = reachableDestination(session, home.id);

        assertTrue(session.beginTravelTo(destination.id).accepted);
        advanceWorldMinutes(session, WorldConfig.MINUTES_PER_HOUR);
        assertTrue(player.isTraveling());
        assertPositionEquals(party.position, company.position);

        // Java's Random(42) first double is above the routed+captured
        // thresholds at a zero defeat margin, producing the WOUNDED outcome.
        session.getSimulation().getContext().getRandom("MILITARY").setSeed(42L);
        CommandResult defeat = session.resolvePlayerDefeat(null, 0.0);
        assertTrue(defeat.accepted, defeat.message);
        assertEquals(CampaignSession.DefeatSeverity.WOUNDED,
                session.getLastDefeatSeverity());
        assertFalse(player.isTraveling());
        assertEquals(home.id, player.currentSettlementId);
        assertEquals(company.id, player.commandedArmyId.longValue());
        assertPositionEquals(home.position, party.position);
        assertPositionEquals(home.position, company.position);
        assertEquals(home.id, company.currentSettlementId);
        assertEquals(home.id, company.destinationSettlementId.longValue());
        assertTrue(company.routeRoadIds.isEmpty());
        assertEquals(Army.ArmyState.MUSTERED, company.state);
    }

    @Test
    void fullHealthPlayerCanAnswerEnemyContactAndItIsNotSilentlyAutoResolved() {
        CampaignSession session = new CampaignSession(20261021L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session);
        Army company = raiseCompany(session);
        WorldParty party = world.parties.get(player.partyId);
        MilitarySystem military = session.getSimulation().getMilitarySystem();

        Army enemy = world.armies.values().stream()
                .sorted(Comparator.comparingLong(army -> army.id))
                .filter(army -> army.id != company.id)
                .filter(army -> army.realmId != company.realmId)
                .filter(army -> military.strength(army) > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no enemy army generated"));
        placeArmy(enemy, home, Army.ArmyOrder.RAID);

        // Reproduce the old visual/ownership bug: the commanded army had
        // fallen behind even though it belonged to the player at this place.
        company.state = Army.ArmyState.TRAVELING;
        company.currentSettlementId = home.id;
        company.destinationSettlementId = home.id;
        company.routeRoadIds.clear();
        company.position = new WorldPosition(home.position.x + 100.0, home.position.y + 100.0);
        setPlayerHealth(session, 100.0);
        player.captiveUntilMinute = null;

        CommandResult engagement = session.canEngageArmy(enemy.id);
        assertTrue(engagement.accepted, engagement.message);
        assertPositionEquals(party.position, company.position);

        for (Army army : world.armies.values()) {
            if (army.id != company.id && army.id != enemy.id) {
                army.state = Army.ArmyState.DISBANDED;
            }
        }
        int companyStrength = military.strength(company);
        int enemyStrength = military.strength(enemy);
        int reports = world.battleReports.size();
        long minute = session.getSimulation().getClock().getWorldMinute();
        military.processHour(minute + WorldConfig.MINUTES_PER_HOUR);

        assertEquals(reports, world.battleReports.size());
        assertEquals(companyStrength, military.strength(company));
        assertEquals(enemyStrength, military.strength(enemy));
        assertFalse(company.state == Army.ArmyState.ENGAGED);
        assertFalse(enemy.state == Army.ArmyState.ENGAGED);
    }

    @Test
    void personalCompanyAdoptsAffiliationSoEnemyControlledTargetCanBeBesieged() {
        CampaignSession session = new CampaignSession(20261022L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session);
        assertNotNull(home.controllerRealmId);
        Army company = raiseCompany(session);
        Realm homeRealm = world.realms.get(home.controllerRealmId);
        Realm otherRealm = sortedRealms(world).stream()
                .filter(realm -> realm.id != homeRealm.id)
                .findFirst()
                .orElseThrow();
        assertEquals(homeRealm.id, company.realmId,
                "an unaffiliated company begins with its recruiting realm");

        resetWars(world);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), otherRealm.id, homeRealm.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);

        assertTrue(session.joinKingdom(otherRealm.id,
                PlayerCampaignState.KingdomRole.MERCENARY).accepted);
        assertEquals(otherRealm.id, company.realmId);
        for (Long regimentId : company.regimentIds) {
            assertEquals(otherRealm.id, world.regiments.get(regimentId).realmId);
        }

        Settlement target = settlementsControlledBy(world, homeRealm.id).stream()
                .filter(settlement -> settlement.id != home.id)
                .filter(settlement -> !world.geography.getRouteGraph()
                        .findShortestRoute(home.id, settlement.id).isEmpty())
                .findFirst()
                .orElseThrow();
        target.occupyingRealmId = null;
        setPlayerHealth(session, 100.0);
        player.captiveUntilMinute = null;

        CommandResult ordered = session.startOrJoinSiege(target.id);
        assertTrue(ordered.accepted, ordered.message);
        assertEquals("SIEGE_ORDERED", ordered.reasonCode);
        assertTrue(player.isTraveling());
        assertEquals(target.id, player.travelDestinationSettlementId.longValue());
        assertEquals(Army.ArmyOrder.BESIEGE, company.order);
        assertEquals(target.id, company.destinationSettlementId.longValue());
        assertTrue(company.routeRoadIds.isEmpty());
        WorldParty party = world.parties.get(player.partyId);
        assertPositionEquals(party.position, company.position);

        advanceWorldMinutes(session, WorldConfig.MINUTES_PER_HOUR);
        assertPositionEquals(party.position, company.position);
    }

    @Test
    void playerMayHoldOnlyOneTitleUntilRelinquishingIt() {
        CampaignSession session = new CampaignSession(20261023L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        List<Realm> realms = sortedRealms(world);
        Realm mine = realms.get(0);
        Realm enemy = realms.get(1);
        List<Settlement> targets = settlementsControlledBy(world, enemy.id);
        assertTrue(targets.size() >= 2);
        Settlement first = targets.get(0);
        Settlement second = targets.get(1);
        first.occupyingRealmId = mine.id;
        second.occupyingRealmId = mine.id;

        assertTrue(session.joinKingdom(mine.id,
                PlayerCampaignState.KingdomRole.MERCENARY).accepted);
        player.conquestContribution = 100.0;
        int titlesBefore = world.titles.size();

        assertTrue(session.claimHolding(first.id).accepted);
        assertEquals(1, player.titleIds.size());
        assertEquals(1, session.getSnapshot().playerTitles().size());
        Title firstTitle = world.titles.get(player.titleIds.get(0));
        double contributionAfterFirst = player.conquestContribution;

        CommandResult secondClaim = session.claimHolding(second.id);
        assertFalse(secondClaim.accepted);
        assertEquals("ALREADY_HOLDS_TITLE", secondClaim.reasonCode);
        assertEquals(titlesBefore + 1, world.titles.size());
        assertEquals(contributionAfterFirst, player.conquestContribution, 0.0001);
        assertEquals(1, session.getSnapshot().playerTitles().size());

        CommandResult relinquished = session.relinquishTitle(firstTitle.id);
        assertTrue(relinquished.accepted, relinquished.message);
        assertNull(firstTitle.holderPersonId);
        assertTrue(player.titleIds.isEmpty());
        assertTrue(session.getSnapshot().playerTitles().isEmpty());

        assertTrue(session.claimHolding(second.id).accepted);
        assertEquals(1, session.getSnapshot().playerTitles().size());
        Title secondTitle = world.titles.get(player.titleIds.get(0));
        assertEquals(second.id, secondTitle.settlementId.longValue());
    }

    /** The settlement the adventurer starts in, with weapons and coin enough to recruit. */
    private static Settlement preparedHome(CampaignSession session) {
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        if (world.geography.getSettlement(player.currentSettlementId) == null) {
            session.travelPlayerTo(sortedSettlements(world).get(0).id);
        }
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        assertNotNull(home, "the adventurer must start in a settlement");
        home.publicStockpile.add(GoodType.WEAPONS, 30);
        world.parties.get(player.partyId).money.add(10_000L);
        return home;
    }

    private static Army raiseCompany(CampaignSession session) {
        CommandResult raised = session.raiseCompany(5, Regiment.RegimentType.LEVY_INFANTRY);
        assertTrue(raised.accepted, raised.message);
        Army company = session.getWorld().armies.get(session.getPlayerState().commandedArmyId);
        assertNotNull(company);
        assertTrue(session.getSimulation().getMilitarySystem().strength(company) > 0);
        return company;
    }

    private static Settlement reachableDestination(CampaignSession session, long originId,
            long... excludedIds) {
        WorldState world = session.getWorld();
        for (Settlement candidate : sortedSettlements(world)) {
            if (candidate.id == originId) continue;
            boolean excluded = false;
            for (long excludedId : excludedIds) {
                if (candidate.id == excludedId) excluded = true;
            }
            if (excluded) continue;
            if (!world.geography.getRouteGraph()
                    .findShortestRoute(originId, candidate.id).isEmpty()) {
                return candidate;
            }
        }
        throw new AssertionError("no reachable settlement from " + originId);
    }

    private static List<Settlement> settlementsControlledBy(WorldState world, long realmId) {
        List<Settlement> controlled = new ArrayList<>();
        for (Settlement settlement : sortedSettlements(world)) {
            if (settlement.controllerRealmId != null
                    && settlement.controllerRealmId.longValue() == realmId) {
                controlled.add(settlement);
            }
        }
        return controlled;
    }

    private static List<Settlement> sortedSettlements(WorldState world) {
        List<Settlement> settlements = new ArrayList<>(
                world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        return settlements;
    }

    private static List<Realm> sortedRealms(WorldState world) {
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        return realms;
    }

    private static void placeArmy(Army army, Settlement settlement, Army.ArmyOrder order) {
        army.currentSettlementId = settlement.id;
        army.destinationSettlementId = settlement.id;
        army.position = new WorldPosition(settlement.position.x, settlement.position.y);
        army.routeRoadIds.clear();
        army.routeIndex = 0;
        army.roadProgress = 0.0;
        army.state = Army.ArmyState.MUSTERED;
        army.order = order;
    }

    private static void resetWars(WorldState world) {
        for (War war : world.wars.values()) war.state = War.WarState.ENDED;
        world.sieges.clear();
    }

    private static void setPlayerHealth(CampaignSession session, double health) {
        Person player = session.getWorld().people.get(session.getPlayerState().personId);
        assertNotNull(player);
        player.alive = true;
        player.health.healthLevel = health;
        player.health.wounded = health < 70.0;
        player.health.injured = health < 70.0;
        player.health.clamp();
    }

    private static void advanceWorldMinutes(CampaignSession session, long minutes) {
        session.getSimulation().advanceMinutes(minutes);
        session.update(0.0);
    }

    private static void assertPositionEquals(WorldPosition expected, WorldPosition actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.x, actual.x, 0.0001);
        assertEquals(expected.y, actual.y, 0.0001);
    }
}
