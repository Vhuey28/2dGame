package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.BattleContext;
import world.military.BattleResult;
import world.military.MilitarySystem;
import world.military.Regiment;
import world.politics.PoliticsSystem;
import world.politics.Title;

class SoloBattleArmyGenerationAndWarTest {

    @Test
    void playerCanFightAloneAndTemporarySoloArmyDisbandsAfterReconciliation() {
        CampaignSession session = new CampaignSession(20261026L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        Army enemy = sortedArmies(world).stream()
                .filter(army -> military.battleReadyStrength(army) > 0)
                .findFirst().orElseThrow();

        assertNull(player.commandedArmyId);
        placeArmy(enemy, home, Army.ArmyOrder.DEFEND);
        setPlayerHealth(session, 37.0);

        CommandResult cleared = session.canEngageArmy(enemy.id);
        assertTrue(cleared.accepted, cleared.message);
        assertEquals("ENGAGE_SOLO", cleared.reasonCode);

        CommandResult sent = session.autoResolveEngagement(enemy.id);
        assertFalse(sent.accepted);
        assertEquals("NO_TROOPS_TO_SEND", sent.reasonCode);
        assertNotEquals(Army.ArmyState.ENGAGED, enemy.state);

        long minute = session.getSimulation().getClock().getWorldMinute();
        BattleContext battle = session.createPlayerBattleContext(enemy.id, List.of(), minute);
        Army solo = world.armies.get(battle.attacker.armyId);
        assertNotNull(solo);
        assertTrue(solo.temporaryBattleArmy);
        assertTrue(battle.attacker.combatants.isEmpty(),
                "the player sprite is the solo combatant; no fake regiment is needed");
        assertFalse(battle.defender.combatants.isEmpty());
        assertNull(player.commandedArmyId, "fighting solo must not invent a permanent command");
        assertEquals(Army.ArmyState.ENGAGED, solo.state);
        assertEquals(Army.ArmyState.ENGAGED, enemy.state);

        BattleResult result = session.getSimulation().getBattleBridge().resultFromTactical(
                battle.battleId, BattleResult.WinningSide.ATTACKER, Map.of(), 60L);
        assertTrue(session.getSimulation().getBattleBridge().reconcile(result).accepted);
        assertEquals(Army.ArmyState.DISBANDED, solo.state);
        assertNull(player.commandedArmyId);
        assertTrue(world.battleReports.containsKey(battle.battleId));
    }

    @Test
    void commandedArmyOutOfReachDoesNotBlockSoloFight() {
        CampaignSession session = new CampaignSession(20261027L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        List<Realm> realms = sortedRealms(world);
        Army mine = armyForRealm(world, military, realms.get(0).id);
        Army enemy = armyForRealm(world, military, realms.get(1).id);
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        Settlement farAway = sortedSettlements(world).stream()
                .filter(settlement -> settlement.id != home.id)
                .max(Comparator.comparingDouble(settlement -> Math.hypot(
                        settlement.position.x - home.position.x,
                        settlement.position.y - home.position.y)))
                .orElseThrow();

        mine.playerCommanded = true;
        mine.commanderPersonId = player.personId;
        player.commandedArmyId = mine.id;
        placeArmy(mine, farAway, Army.ArmyOrder.DEFEND);
        placeArmy(enemy, home, Army.ArmyOrder.RAID);
        setPlayerHealth(session, 80.0);

        CommandResult cleared = session.canEngageArmy(enemy.id);
        assertTrue(cleared.accepted, cleared.message);
        assertEquals("ENGAGE_SOLO", cleared.reasonCode);
        assertEquals("NO_TROOPS_TO_SEND", session.autoResolveEngagement(enemy.id).reasonCode);

        BattleContext battle = session.createPlayerBattleContext(enemy.id, List.of(),
                session.getSimulation().getClock().getWorldMinute());
        assertNotEquals(mine.id, battle.attacker.armyId);
        assertNotEquals(Army.ArmyState.ENGAGED, mine.state);
        Army solo = world.armies.get(battle.attacker.armyId);
        BattleResult result = session.getSimulation().getBattleBridge().resultFromTactical(
                battle.battleId, BattleResult.WinningSide.DEFENDER, Map.of(), 60L);
        assertTrue(session.getSimulation().getBattleBridge().reconcile(result).accepted);
        assertEquals(Army.ArmyState.DISBANDED, solo.state);
        assertEquals(mine.id, player.commandedArmyId.longValue());
        assertNotEquals(Army.ArmyState.DISBANDED, mine.state);
    }

    @Test
    void soloDefeatDoesNotDamageAnUninvolvedCommandedArmy() {
        CampaignSession session = new CampaignSession(20261102L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        List<Realm> realms = sortedRealms(world);
        Army mine = armyForRealm(world, military, realms.get(0).id);
        Army enemy = armyForRealm(world, military, realms.get(1).id);
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        Settlement farAway = sortedSettlements(world).stream()
                .filter(settlement -> settlement.id != home.id)
                .max(Comparator.comparingDouble(settlement -> Math.hypot(
                        settlement.position.x - home.position.x,
                        settlement.position.y - home.position.y)))
                .orElseThrow();

        mine.playerCommanded = true;
        mine.commanderPersonId = player.personId;
        player.commandedArmyId = mine.id;
        placeArmy(mine, farAway, Army.ArmyOrder.DEFEND);
        mine.morale = 75.0;
        mine.fatigue = 10.0;
        placeArmy(enemy, home, Army.ArmyOrder.RAID);
        setPlayerHealth(session, 80.0);

        BattleContext battle = session.createPlayerBattleContext(enemy.id, List.of(),
                session.getSimulation().getClock().getWorldMinute());
        assertNotEquals(mine.id, battle.attacker.armyId);
        BattleResult result = session.getSimulation().getBattleBridge().resultFromTactical(
                battle.battleId, BattleResult.WinningSide.DEFENDER, Map.of(), 60L);
        assertTrue(session.getSimulation().getBattleBridge().reconcile(result).accepted);

        CommandResult defeat = session.resolvePlayerDefeat(enemy.realmId, 1.0, false);
        assertTrue(defeat.accepted, defeat.message);
        assertEquals(Long.valueOf(mine.id), player.commandedArmyId);
        assertEquals(Army.ArmyState.MUSTERED, mine.state);
        assertEquals(75.0, mine.morale, 0.001);
        assertEquals(10.0, mine.fatigue, 0.001);
    }

    @Test
    void armyWithNoBattleReadySoldiersCannotStartAnEmptyBattle() {
        CampaignSession session = new CampaignSession(20261028L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        List<Realm> realms = sortedRealms(world);
        Army attacker = armyForRealm(world, military, realms.get(0).id);
        Army defender = armyForRealm(world, military, realms.get(1).id);
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        placeArmy(defender, home, Army.ArmyOrder.DEFEND);

        for (Long regimentId : defender.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            for (Long personId : regiment.soldierPersonIds) {
                Person soldier = world.people.get(personId);
                if (soldier != null) soldier.capturedByRealmId = realms.get(0).id;
            }
        }
        assertTrue(military.strength(defender) > 0, "captured soldiers are still alive");
        assertEquals(0, military.battleReadyStrength(defender));

        CommandResult cleared = session.canEngageArmy(defender.id);
        assertFalse(cleared.accepted);
        assertEquals("NO_TROOPS", cleared.reasonCode);

        attacker.state = Army.ArmyState.MUSTERED;
        defender.state = Army.ArmyState.MUSTERED;
        int contexts = world.battleContexts.size();
        assertThrows(IllegalStateException.class, () -> session.getSimulation().getBattleBridge()
                .createContext(attacker.id, defender.id, false, List.of(), 0L));
        assertEquals(contexts, world.battleContexts.size());
        assertNotEquals(Army.ArmyState.ENGAGED, attacker.state);
        assertNotEquals(Army.ArmyState.ENGAGED, defender.state);
    }

    @Test
    void rulerCanDeclareWarWithoutAnyFieldArmyAndCrownSurvivesSuccessionCheck() {
        CampaignSession session = new CampaignSession(20261029L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        List<Realm> realms = sortedRealms(world);
        Realm mine = realms.get(0);
        Realm enemy = realms.get(1);
        resetWars(world);
        for (Army army : world.armies.values()) army.state = Army.ArmyState.DISBANDED;

        assertTrue(session.joinKingdom(mine.id, PlayerCampaignState.KingdomRole.LORD).accepted);
        assertTrue(session.appointPlayerAsKing(mine.id).accepted);
        Title crown = world.titles.get(mine.rulerTitleId);
        assertNotNull(crown);
        assertEquals(player.personId, crown.holderPersonId.longValue());
        assertEquals(List.of(crown.id), player.titleIds);
        new PoliticsSystem(session.getSimulation().getContext())
                .checkSuccession(mine, WorldConfig.MINUTES_PER_MONTH);
        assertEquals(player.personId, mine.rulerPersonId.longValue(),
                "monthly succession must not restore the previous crown holder");

        assertTrue(session.declarableWarTargets().contains(enemy.id));
        CommandResult declared = session.declareWarAsRuler(enemy.id,
                War.WarGoal.CONQUER_PROVINCE, null, null);
        assertTrue(declared.accepted, declared.message);
        assertTrue(world.wars.values().stream().anyMatch(war -> war.state == War.WarState.ACTIVE
                && war.attackerRealmIds.contains(mine.id) && war.defenderRealmIds.contains(enemy.id)));
    }

    @Test
    void boundedMusteringGeneratesArmiesForBothRealms() {
        CampaignSession session = new CampaignSession(20261030L);
        WorldState world = session.getWorld();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        long minute = session.getSimulation().getClock().getWorldMinute();
        resetWars(world);
        for (Army army : new ArrayList<>(world.armies.values())) {
            if (army.state != Army.ArmyState.DISBANDED) military.demobilize(army.id, minute);
        }
        for (Realm realm : world.realms.values()) realm.treasury.add(20_000L);
        for (Settlement settlement : world.geography.getSettlements().values()) {
            settlement.publicStockpile.add(GoodType.WEAPONS, 100);
        }
        for (Realm realm : world.realms.values()) assertEquals(0, independentArmyCount(world, military, realm.id));

        assertEquals(2, military.musterRealmArmies(minute));
        for (Realm realm : world.realms.values()) assertEquals(1, independentArmyCount(world, military, realm.id));
        assertEquals(2, military.musterRealmArmies(minute));
        for (Realm realm : world.realms.values()) assertEquals(2, independentArmyCount(world, military, realm.id));
        assertEquals(0, military.musterRealmArmies(minute), "peacetime mustering is capped at two per realm");
    }

    @Test
    void independentArmyPatrolsButPlayerCommandedArmyKeepsPlayerOrders() {
        CampaignSession session = new CampaignSession(20261031L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        List<Realm> realms = sortedRealms(world);
        resetWars(world);
        Army independent = armyForRealm(world, military, realms.get(0).id);
        Army playerOwned = armyForRealm(world, military, realms.get(1).id);
        for (Army army : world.armies.values()) {
            if (army.id != independent.id && army.id != playerOwned.id) army.state = Army.ArmyState.DISBANDED;
        }
        Settlement independentPost = settlementControlledBy(world, realms.get(0).id);
        Settlement playerPost = settlementControlledBy(world, realms.get(1).id);
        prepareIdleArmy(independent, independentPost);
        prepareIdleArmy(playerOwned, playerPost);
        playerOwned.playerCommanded = true;
        playerOwned.commanderPersonId = player.personId;
        player.commandedArmyId = playerOwned.id;

        military.issueIndependentArmyOrders(session.getSimulation().getClock().getWorldMinute());

        assertEquals(Army.ArmyOrder.PATROL, independent.order);
        assertEquals(Army.ArmyState.TRAVELING, independent.state);
        assertNotNull(independent.destinationSettlementId);
        assertNotEquals(independentPost.id, independent.destinationSettlementId.longValue());
        assertFalse(independent.routeRoadIds.isEmpty());
        assertEquals(Army.ArmyOrder.MUSTER, playerOwned.order);
        assertEquals(Army.ArmyState.MUSTERED, playerOwned.state);
        assertEquals(playerPost.id, playerOwned.destinationSettlementId.longValue());
        assertTrue(playerOwned.routeRoadIds.isEmpty());
    }

    @Test
    void independentOrdersRespectAnArmyAlreadyInSiegePosition() {
        CampaignSession session = new CampaignSession(20261103L);
        WorldState world = session.getWorld();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        List<Realm> realms = sortedRealms(world);
        resetWars(world);
        Army besieger = armyForRealm(world, military, realms.get(0).id);
        for (Army army : world.armies.values()) {
            if (army.id != besieger.id) army.state = Army.ArmyState.DISBANDED;
        }
        Settlement target = settlementControlledBy(world, realms.get(1).id);
        prepareIdleArmy(besieger, target);
        besieger.order = Army.ArmyOrder.BESIEGE;
        besieger.destinationSettlementId = target.id;

        military.issueIndependentArmyOrders(session.getSimulation().getClock().getWorldMinute());

        assertEquals(Army.ArmyState.MUSTERED, besieger.state);
        assertEquals(Army.ArmyOrder.BESIEGE, besieger.order);
        assertEquals(target.id, besieger.currentSettlementId);
        assertEquals(target.id, besieger.destinationSettlementId.longValue());
        assertTrue(besieger.routeRoadIds.isEmpty());
    }

    @Test
    void strongestIndependentArmyAdvancesToBesiegeDuringWarAndRoutedArmyReturnsToSafety() {
        CampaignSession session = new CampaignSession(20261101L);
        WorldState world = session.getWorld();
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        List<Realm> realms = sortedRealms(world);
        resetWars(world);
        Army attacker = armyForRealm(world, military, realms.get(0).id);
        for (Army army : world.armies.values()) {
            if (army.id != attacker.id) army.state = Army.ArmyState.DISBANDED;
        }
        Settlement staging = settlementControlledBy(world, realms.get(0).id);
        prepareIdleArmy(attacker, staging);
        long minute = session.getSimulation().getClock().getWorldMinute();
        War war = new War(world.idGenerator.next(), realms.get(0).id, realms.get(1).id,
                War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);

        military.issueIndependentArmyOrders(minute);
        assertEquals(Army.ArmyOrder.BESIEGE, attacker.order);
        assertEquals(Army.ArmyState.TRAVELING, attacker.state);
        Settlement objective = world.geography.getSettlement(attacker.destinationSettlementId);
        assertNotNull(objective);
        assertEquals(realms.get(1).id, objective.controllerRealmId.longValue());

        // The same autonomous pass sends a routed survivor toward friendly safety.
        attacker.state = Army.ArmyState.ROUTED;
        attacker.order = Army.ArmyOrder.RETURN_HOME;
        attacker.routeRoadIds.clear();
        attacker.currentSettlementId = objective.id;
        attacker.position = new WorldPosition(objective.position.x, objective.position.y);
        military.issueIndependentArmyOrders(minute + WorldConfig.MINUTES_PER_DAY);
        assertEquals(Army.ArmyOrder.RETURN_HOME, attacker.order);
        assertEquals(Army.ArmyState.TRAVELING, attacker.state);
        Settlement refuge = world.geography.getSettlement(attacker.destinationSettlementId);
        assertNotNull(refuge);
        assertEquals(realms.get(0).id, refuge.controllerRealmId.longValue());
    }

    private static void prepareIdleArmy(Army army, Settlement settlement) {
        placeArmy(army, settlement, Army.ArmyOrder.MUSTER);
        army.supplies.add(GoodType.GRAIN, 1_000);
        army.daysWithoutFood = 0;
        army.morale = 80.0;
        army.fatigue = 0.0;
    }

    private static int independentArmyCount(WorldState world, MilitarySystem military, long realmId) {
        int count = 0;
        for (Army army : world.armies.values()) {
            if (army.realmId == realmId && !army.playerCommanded && !army.playerFunded
                    && !army.temporaryBattleArmy && army.state != Army.ArmyState.DISBANDED
                    && military.strength(army) > 0) count++;
        }
        return count;
    }

    private static Army armyForRealm(WorldState world, MilitarySystem military, long realmId) {
        return sortedArmies(world).stream()
                .filter(army -> army.realmId == realmId)
                .filter(army -> army.state != Army.ArmyState.DISBANDED)
                .filter(army -> military.battleReadyStrength(army) > 0)
                .findFirst().orElseThrow(() -> new AssertionError("no generated army for realm " + realmId));
    }

    private static Settlement settlementControlledBy(WorldState world, long realmId) {
        return sortedSettlements(world).stream()
                .filter(settlement -> settlement.controllerRealmId != null
                        && settlement.controllerRealmId.longValue() == realmId)
                .findFirst().orElseThrow();
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
        army.targetArmyId = null;
    }

    private static void setPlayerHealth(CampaignSession session, double health) {
        Person person = session.getWorld().people.get(session.getPlayerState().personId);
        assertNotNull(person);
        person.alive = true;
        person.health.healthLevel = health;
        person.health.wounded = health < 70.0;
        person.health.injured = health < 70.0;
        person.health.clamp();
        session.getPlayerState().captiveUntilMinute = null;
    }

    private static void resetWars(WorldState world) {
        for (War war : world.wars.values()) war.state = War.WarState.ENDED;
        world.sieges.clear();
    }

    private static List<Realm> sortedRealms(WorldState world) {
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        return realms;
    }

    private static List<Army> sortedArmies(WorldState world) {
        List<Army> armies = new ArrayList<>(world.armies.values());
        armies.sort(Comparator.comparingLong(army -> army.id));
        return armies;
    }

    private static List<Settlement> sortedSettlements(WorldState world) {
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        return settlements;
    }
}
