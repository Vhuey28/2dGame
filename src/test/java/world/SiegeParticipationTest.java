package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import world.military.Siege;
import world.military.WarSystem;

class SiegeParticipationTest {

    private static List<Realm> sortedRealms(WorldState world) {
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        return realms;
    }

    private static Settlement settlementControlledBy(WorldState world, long realmId) {
        return world.geography.getSettlements().values().stream()
                .filter(settlement -> settlement.controllerRealmId != null
                        && settlement.controllerRealmId.longValue() == realmId)
                .min(Comparator.comparingLong(settlement -> settlement.id))
                .orElseThrow(() -> new IllegalStateException("realm holds no settlement"));
    }

    private static void resetWarsAndSieges(WorldState world) {
        for (War war : world.wars.values()) war.state = War.WarState.ENDED;
        world.sieges.clear();
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

    private static void commandArmy(CampaignSession session, Army army) {
        PlayerCampaignState player = session.getPlayerState();
        Army previous = player.commandedArmyId == null ? null
                : session.getWorld().armies.get(player.commandedArmyId);
        if (previous != null && previous.id != army.id) previous.playerCommanded = false;
        player.commandedArmyId = army.id;
        army.playerCommanded = true;
        army.commanderPersonId = player.personId;
    }

    /** Recruits one additional physical army through the normal military path. */
    private static Army recruitExtraArmy(CampaignSession session, Realm realm, int requested) {
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();
        Settlement recruitingGround = world.geography.getSettlements().values().stream()
                .filter(settlement -> settlement.controllerRealmId != null
                        && settlement.controllerRealmId.longValue() == realm.id)
                .max(Comparator.comparingLong(settlement -> world.people.values().stream()
                        .filter(person -> person.alive && person.currentSettlementId != null
                                && person.currentSettlementId.longValue() == settlement.id
                                && person.regimentId == null && person.travelingPartyId == null
                                && !person.isChild(minute) && !person.isElderly(minute)
                                && person.type != Person.PersonType.NOBLE
                                && person.type != Person.PersonType.MERCHANT)
                        .count()))
                .orElseThrow(() -> new IllegalStateException("realm has no recruiting settlement"));
        long newestArmyId = world.armies.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
        recruitingGround.publicStockpile.add(GoodType.WEAPONS, 100);
        realm.treasury.add(100_000L);
        CommandResult recruited = session.getSimulation().getMilitarySystem().recruitRegiment(
                realm.id, recruitingGround.id, requested, Regiment.RegimentType.LEVY_INFANTRY, minute);
        assertTrue(recruited.accepted, recruited.message);
        return world.armies.values().stream()
                .filter(army -> army.realmId == realm.id && army.id > newestArmyId)
                .max(Comparator.comparingLong(army -> army.id))
                .orElseThrow(() -> new IllegalStateException("recruitment created no army"));
    }

    private static void setPlayerHealth(CampaignSession session, double health) {
        Person player = session.getWorld().people.get(session.getPlayerState().personId);
        player.alive = true;
        player.health.healthLevel = health;
        player.health.wounded = health < 70.0;
        player.health.injured = health < 70.0;
        player.health.clamp();
    }

    @Test
    void woundedPlayerWithOneHealthCanFightButZeroHealthCannot() {
        CampaignSession session = new CampaignSession(9101L);
        PlayerCampaignState player = session.getPlayerState();

        setPlayerHealth(session, 0.4);
        assertTrue(session.playerCanFight().accepted,
                "any positive fractional health is still health to fight with");
        assertEquals(1, session.playerHealthPercent());
        LocalPlayerState local = session.enterLocalScene();
        assertEquals(1, local.health);
        session.leaveLocalScene(local);

        setPlayerHealth(session, 1.0);
        assertTrue(session.playerCanFight().accepted);
        assertTrue(session.playerHasHealthToFight());
        CampaignSnapshot.PlayerView view = CampaignSnapshot.capture(session.getSimulation()).player;
        assertNotNull(view);
        assertEquals(1, view.healthPercent);
        assertTrue(view.wounded);
        assertTrue(view.canFight());

        long minute = session.getSimulation().getClock().getWorldMinute();
        player.captiveUntilMinute = minute + WorldConfig.MINUTES_PER_DAY;
        assertTrue(session.playerHasHealthToFight(), "captivity is separate from having health");
        assertEquals("CAPTIVE", session.playerCanFight().reasonCode);
        player.captiveUntilMinute = null;

        setPlayerHealth(session, 0.0);
        CommandResult incapacitated = session.playerCanFight();
        assertFalse(incapacitated.accepted);
        assertEquals("PLAYER_INCAPACITATED", incapacitated.reasonCode);
    }

    @Test
    void woundedPlayerCanStartSiegeImmediatelyWhenArmyIsInPosition() {
        CampaignSession session = new CampaignSession(9102L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        Army army = wars.strongestArmy(attacker.id);
        assertNotNull(army);
        Settlement target = settlementControlledBy(world, defender.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        placeArmy(army, target, Army.ArmyOrder.MUSTER);
        commandArmy(session, army);
        setPlayerHealth(session, 1.0);

        target.occupyingRealmId = attacker.id;
        CommandResult alreadyOccupied = session.startOrJoinSiege(target.id);
        assertFalse(alreadyOccupied.accepted);
        assertEquals("NO_SIEGE", alreadyOccupied.reasonCode);
        assertTrue(world.sieges.isEmpty());
        target.occupyingRealmId = null;

        CommandResult started = session.startOrJoinSiege(target.id);

        assertTrue(started.accepted, started.message);
        assertEquals("SIEGE_STARTED", started.reasonCode);
        assertEquals(Army.ArmyOrder.BESIEGE, army.order);
        assertEquals(1, world.sieges.size());
        Siege siege = world.sieges.values().iterator().next();
        assertEquals(army.id, siege.besiegingArmyId);
        CampaignSnapshot.SiegeView view = session.getSnapshot().findSiege(siege.id);
        assertNotNull(view);
        assertTrue(view.playerArmyJoined);
        assertTrue(view.playerArmyAtSiege);

        CommandResult again = session.issueArmyOrder(target.id, Army.ArmyOrder.BESIEGE);
        assertTrue(again.accepted, again.message);
        assertEquals("ALREADY_IN_SIEGE", again.reasonCode);
    }

    @Test
    void woundedPlayerCanEngageNearbyEnemyArmyUntilHealthRunsOut() {
        CampaignSession session = new CampaignSession(9107L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        WarSystem wars = session.getSimulation().getWarSystem();
        Army mine = wars.strongestArmy(realms.get(0).id);
        Army enemy = wars.strongestArmy(realms.get(1).id);
        assertNotNull(mine);
        assertNotNull(enemy);
        Settlement field = settlementControlledBy(world, realms.get(0).id);
        placeArmy(mine, field, Army.ArmyOrder.MUSTER);
        placeArmy(enemy, field, Army.ArmyOrder.DEFEND);
        commandArmy(session, mine);

        setPlayerHealth(session, 1.0);
        assertTrue(session.canEngageArmy(enemy.id).accepted,
                "one point of health is still enough to lead an attack");

        setPlayerHealth(session, 0.0);
        CommandResult incapacitated = session.canEngageArmy(enemy.id);
        assertFalse(incapacitated.accepted);
        assertEquals("PLAYER_INCAPACITATED", incapacitated.reasonCode);
    }

    @Test
    void woundedPlayerCanOrderSiegeFromElsewhereAndItStartsOnArrival() {
        CampaignSession session = new CampaignSession(9108L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        Army army = wars.strongestArmy(attacker.id);
        Army defenderArmy = wars.strongestArmy(defender.id);
        assertNotNull(army);
        assertNotNull(defenderArmy);
        defenderArmy.state = Army.ArmyState.DISBANDED;
        Settlement staging = settlementControlledBy(world, attacker.id);
        Settlement target = settlementControlledBy(world, defender.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        placeArmy(army, staging, Army.ArmyOrder.MUSTER);
        commandArmy(session, army);
        setPlayerHealth(session, 2.0);

        CommandResult ordered = session.startOrJoinSiege(target.id);

        assertTrue(ordered.accepted, ordered.message);
        assertEquals("SIEGE_ORDERED", ordered.reasonCode);
        assertTrue(world.sieges.isEmpty(), "no siege exists until the army reaches the walls");
        assertEquals(Army.ArmyOrder.BESIEGE, army.order);
        assertEquals(target.id, army.destinationSettlementId.longValue());
        assertEquals(Army.ArmyState.TRAVELING, army.state);

        placeArmy(army, target, Army.ArmyOrder.BESIEGE);
        wars.processDay(minute + WorldConfig.MINUTES_PER_DAY);

        Siege siege = wars.findActiveSiege(war.id, target.id);
        assertNotNull(siege);
        assertEquals(army.id, siege.besiegingArmyId);
        CampaignSnapshot.SiegeView view =
                CampaignSnapshot.capture(session.getSimulation()).findSiege(siege.id);
        assertNotNull(view);
        assertTrue(view.playerArmyJoined);
        assertTrue(view.playerArmyAtSiege);
        assertTrue(view.playerArmyCanJoin);
    }

    @Test
    void playerCanJoinOngoingSiegeShareAssaultAndTakeOverIfLeadLeaves() {
        CampaignSession session = new CampaignSession(9103L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        Army primary = wars.strongestArmy(attacker.id);
        Army support = wars.strongestArmy(defender.id);
        assertNotNull(primary);
        assertNotNull(support);
        support.realmId = attacker.id;
        Settlement target = settlementControlledBy(world, defender.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        placeArmy(primary, target, Army.ArmyOrder.BESIEGE);
        placeArmy(support, target, Army.ArmyOrder.MUSTER);
        commandArmy(session, support);
        setPlayerHealth(session, 3.0);
        Siege siege = wars.beginOrJoinSiege(primary, target, war, minute);
        assertNotNull(siege);

        CommandResult joined = session.joinSiege(siege.id);

        assertTrue(joined.accepted, joined.message);
        assertEquals("JOINED_SIEGE", joined.reasonCode);
        assertTrue(siege.isBesiegingArmy(support.id));
        assertEquals(Army.ArmyOrder.BESIEGE, support.order);
        assertEquals(2, wars.activeBesiegingArmies(siege, minute).size());
        CampaignSnapshot.SiegeView view = session.getSnapshot().findSiege(siege.id);
        assertNotNull(view);
        assertEquals(2, view.besiegerCount);
        assertTrue(view.playerArmyAtSiege);
        assertTrue(view.playerArmyCanJoin);
        assertEquals(military.strength(primary) + military.strength(support), view.besiegerStrength);

        int totalBefore = military.strength(primary) + military.strength(support);
        CommandResult stormed = session.assaultSiege(siege.id);
        assertTrue(stormed.accepted, stormed.message);
        assertTrue(siege.breachProgress > 0.0);
        assertTrue(military.strength(primary) + military.strength(support) < totalBefore,
                "a joint assault must cost the combined besieging force");

        primary.order = Army.ArmyOrder.MOVE;
        wars.processDay(minute + WorldConfig.MINUTES_PER_DAY);
        assertEquals(Siege.SiegeState.ACTIVE, siege.state);
        assertEquals(support.id, siege.besiegingArmyId,
                "a supporting army still in position takes over instead of abandoning the siege");
    }

    @Test
    void settlementAnchorAloneDoesNotPutDistantArmyInSiegeLines() {
        CampaignSession session = new CampaignSession(9107L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        Army besieger = wars.strongestArmy(attacker.id);
        assertNotNull(besieger);
        Settlement target = settlementControlledBy(world, defender.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        placeArmy(besieger, target, Army.ArmyOrder.BESIEGE);
        Siege siege = wars.beginOrJoinSiege(besieger, target, war, minute);
        assertEquals(1, wars.activeBesiegingArmies(siege, minute).size());

        besieger.position = new WorldPosition(target.position.x + 100.0,
                target.position.y + 100.0);
        assertEquals(target.id, besieger.currentSettlementId);
        assertTrue(wars.activeBesiegingArmies(siege, minute).isEmpty(),
                "an army token far from the walls must not contribute merely because "
                        + "its last settlement id names the target");
        CommandResult storm = wars.assaultSiege(siege.id, false, minute);
        assertFalse(storm.accepted);
        assertEquals("SIEGE_NOT_READY", storm.reasonCode);
    }

    @Test
    void reliefVictoryOverLeadDoesNotBreakSiegeWhileSupportHoldsLines() {
        CampaignSession session = new CampaignSession(9106L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        Army firstBesieger = wars.strongestArmy(attacker.id);
        Army relief = wars.strongestArmy(defender.id);
        Army support = recruitExtraArmy(session, attacker, 5);
        assertNotNull(firstBesieger);
        assertNotNull(relief);
        assertNotNull(support);
        Settlement target = settlementControlledBy(world, defender.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        placeArmy(firstBesieger, target, Army.ArmyOrder.BESIEGE);
        placeArmy(support, target, Army.ArmyOrder.BESIEGE);
        placeArmy(relief, target, Army.ArmyOrder.DEFEND);
        Siege siege = wars.beginOrJoinSiege(firstBesieger, target, war, minute);
        wars.beginOrJoinSiege(support, target, war, minute);
        List<Army> besiegers = wars.activeBesiegingArmies(siege, minute);
        assertEquals(2, besiegers.size());
        Army doomedLead = besiegers.stream()
                .max(Comparator.comparingInt(military::strength)).orElseThrow();
        Army holdingSupport = besiegers.get(0).id == doomedLead.id ? besiegers.get(1) : besiegers.get(0);
        doomedLead.morale = 0.0;
        doomedLead.fatigue = 100.0;
        holdingSupport.morale = 100.0;
        holdingSupport.fatigue = 0.0;
        relief.morale = 100.0;
        relief.fatigue = 0.0;

        wars.processDay(minute + WorldConfig.MINUTES_PER_DAY);

        assertEquals(Army.ArmyOrder.RETURN_HOME, doomedLead.order,
                "the relief force should win its field battle against the exhausted lead");
        assertEquals(Siege.SiegeState.ACTIVE, siege.state,
                "one defeated besieger does not relieve a settlement still held by another");
        assertEquals(holdingSupport.id, siege.besiegingArmyId);
    }

    @Test
    void joiningFromElsewhereRegistersArmyButStormWaitsForArrivalAndWrongSideIsRejected() {
        CampaignSession session = new CampaignSession(9104L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        Army primary = wars.strongestArmy(attacker.id);
        Army arriving = wars.strongestArmy(defender.id);
        assertNotNull(primary);
        assertNotNull(arriving);
        Settlement target = settlementControlledBy(world, defender.id);
        Settlement staging = settlementControlledBy(world, attacker.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        placeArmy(primary, target, Army.ArmyOrder.BESIEGE);
        Siege siege = wars.beginOrJoinSiege(primary, target, war, minute);
        placeArmy(arriving, staging, Army.ArmyOrder.MUSTER);
        commandArmy(session, arriving);
        setPlayerHealth(session, 25.0);

        CommandResult wrongSide = session.joinSiege(siege.id);
        assertFalse(wrongSide.accepted);
        assertEquals("WRONG_SIDE", wrongSide.reasonCode);
        assertFalse(siege.isBesiegingArmy(arriving.id));
        CampaignSnapshot.SiegeView wrongSideView =
                CampaignSnapshot.capture(session.getSimulation()).findSiege(siege.id);
        assertNotNull(wrongSideView);
        assertFalse(wrongSideView.playerArmyCanJoin,
                "the UI should not offer Join for a siege against your own side");

        arriving.realmId = attacker.id;
        CampaignSnapshot.SiegeView joinableView =
                CampaignSnapshot.capture(session.getSimulation()).findSiege(siege.id);
        assertNotNull(joinableView);
        assertTrue(joinableView.playerArmyCanJoin);
        CommandResult joining = session.joinSiege(siege.id);
        assertTrue(joining.accepted, joining.message);
        assertEquals("JOINING_SIEGE", joining.reasonCode);
        assertTrue(siege.isBesiegingArmy(arriving.id));
        assertEquals(Army.ArmyOrder.BESIEGE, arriving.order);
        assertEquals(target.id, arriving.destinationSettlementId.longValue());
        assertEquals(Army.ArmyState.TRAVELING, arriving.state);
        CampaignSnapshot.SiegeView marchingView = session.getSnapshot().findSiege(siege.id);
        assertNotNull(marchingView);
        assertEquals(2, marchingView.besiegerCount, "both armies are committed to the siege");
        assertTrue(marchingView.playerArmyJoined);
        assertFalse(marchingView.playerArmyAtSiege);
        assertEquals(military.strength(primary), marchingView.besiegerStrength,
                "a marching joiner is committed but must not add strength before arrival");

        CommandResult tooSoon = session.assaultSiege(siege.id);
        assertFalse(tooSoon.accepted);
        assertEquals("NOT_AT_SIEGE", tooSoon.reasonCode);
    }

    @Test
    void defendingSideCanCounterSiegeAndCaptureScoresForDefenders() {
        CampaignSession session = new CampaignSession(9105L);
        WorldState world = session.getWorld();
        resetWarsAndSieges(world);
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        WarSystem wars = session.getSimulation().getWarSystem();
        Army counterBesieger = wars.strongestArmy(defender.id);
        assertNotNull(counterBesieger);
        Settlement target = settlementControlledBy(world, attacker.id);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), attacker.id, defender.id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        for (Army army : world.armies.values()) {
            if (army.realmId == attacker.id) army.state = Army.ArmyState.DISBANDED;
        }
        placeArmy(counterBesieger, target, Army.ArmyOrder.MUSTER);
        commandArmy(session, counterBesieger);
        setPlayerHealth(session, 15.0);

        CommandResult started = session.startOrJoinSiege(target.id);
        assertTrue(started.accepted, started.message);
        Siege siege = wars.findActiveSiege(war.id, target.id);
        assertNotNull(siege);
        siege.breachProgress = 99.9;

        wars.processDay(minute + WorldConfig.MINUTES_PER_DAY);

        assertEquals(Siege.SiegeState.CAPTURED, siege.state);
        assertEquals(defender.id, target.occupyingRealmId.longValue());
        assertEquals(35.0, war.defenderScore, 0.001);
        assertEquals(0.0, war.attackerScore, 0.001);
    }
}
