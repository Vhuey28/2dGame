package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import world.economy.GoodType;
import world.geography.Settlement;

class CheatAndContractClarityTest {
    @Test
    void contractSnapshotExplainsObjectiveRouteProgressDeadlineAndConsequences() {
        CampaignSession session = new CampaignSession(1401L);
        CampaignSnapshot.ContractView offer = session.getSnapshot().contracts.stream()
                .filter(contract -> "OPEN".equals(contract.status)).findFirst().orElseThrow();

        assertFalse(offer.objective.isBlank());
        assertFalse(offer.issuerName.isBlank());
        assertFalse(offer.destinationName.isBlank());
        assertNotEquals(offer.issuerSettlementId, offer.destinationSettlementId);
        assertTrue(offer.rewardCoins > 0);
        assertTrue(offer.penaltyCoins >= 0);
        assertTrue(offer.remainingDays() > 0);
        assertFalse(offer.progress.isBlank());

        session.travelPlayerTo(offer.issuerSettlementId);
        assertTrue(session.acceptContract(offer.id).accepted);
        CampaignSnapshot.ContractView active = session.getSnapshot().contracts.stream()
                .filter(contract -> contract.id == offer.id).findFirst().orElseThrow();
        assertEquals("ACTIVE", active.status);
        assertTrue(active.objective.contains(active.destinationName));
    }

    @Test
    void cheatsMutateCampaignSystemsThroughExplicitService() {
        CampaignSession session = new CampaignSession(1402L);
        WorldState world = session.getWorld();
        CheatService cheats = session.getCheats();
        Settlement settlement = world.geography.getSettlement(session.getPlayerState().currentSettlementId);
        ArrayList<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        Realm targetRealm = realms.get(1);
        Person targetPerson = world.people.values().stream()
                .filter(person -> person.alive && person.currentSettlementId != null
                        && person.currentSettlementId == settlement.id
                        && person.id != session.getPlayerState().personId)
                .findFirst().orElseThrow();

        int grain = settlement.publicStockpile.getQuantity(GoodType.GRAIN);
        long gold = world.parties.get(session.getPlayerState().partyId).money.copperCoins;
        int armies = world.armies.size();
        int caravans = world.caravans.size();
        int titles = world.titles.size();

        assertTrue(cheats.addFood(settlement.id, 500).accepted);
        assertEquals(grain + 500, settlement.publicStockpile.getQuantity(GoodType.GRAIN));
        assertTrue(cheats.addPlayerGold(10_000).accepted);
        assertEquals(gold + 10_000, world.parties.get(session.getPlayerState().partyId).money.copperCoins);
        assertTrue(cheats.addPlayerCargo(5).accepted);
        assertEquals(5, session.getPlayerState().cargo.getQuantity(GoodType.MEDICINE));
        assertTrue(cheats.spawnArmy(settlement.id, 10).accepted);
        assertTrue(world.armies.size() > armies);
        assertTrue(cheats.addTroops(settlement.id, 5).accepted);
        assertTrue(cheats.spawnCaravan(settlement.id).accepted);
        assertTrue(world.caravans.size() > caravans);

        assertTrue(cheats.makePlayerKing(targetRealm.id).accepted);
        assertEquals(session.getPlayerState().personId, targetRealm.rulerPersonId);
        assertTrue(cheats.setKing(targetRealm.id, targetPerson.id).accepted);
        assertEquals(targetPerson.id, targetRealm.rulerPersonId);
        assertTrue(cheats.changeSettlementOwner(settlement.id, targetRealm.id).accepted);
        assertEquals(targetRealm.id, settlement.controllerRealmId);
        assertTrue(cheats.appointLord(settlement.id, targetPerson.id).accepted);
        assertTrue(world.titles.size() > titles);
        assertTrue(cheats.triggerCrime(settlement.id).accepted);
        assertFalse(world.crimeIncidents.isEmpty());
        assertTrue(cheats.toggleWar(realms.get(0).id, realms.get(1).id).accepted);
        assertTrue(new WorldInvariantValidator().validate(world).isValid());
    }

    @Test
    void expandedCheatsActOnExplicitlySelectedArmyRealmAndPlayer() {
        CampaignSession session = new CampaignSession(1403L);
        WorldState world = session.getWorld();
        CheatService cheats = session.getCheats();
        PlayerCampaignState player = session.getPlayerState();
        Settlement here = world.geography.getSettlement(player.currentSettlementId);
        ArrayList<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        Realm selectedRealm = realms.stream()
                .filter(realm -> here.controllerRealmId == null || realm.id != here.controllerRealmId.longValue())
                .findFirst().orElseThrow();
        Settlement elsewhere = world.geography.getSettlements().values().stream()
                .filter(settlement -> settlement.id != here.id)
                .sorted(Comparator.comparingLong(settlement -> settlement.id))
                .findFirst().orElseThrow();

        Set<Long> before = new HashSet<>(world.armies.keySet());
        assertTrue(cheats.spawnArmyForRealm(here.id, selectedRealm.id, 5).accepted);
        Army selectedArmy = newestArmy(world, before);
        assertEquals(selectedRealm.id, selectedArmy.realmId,
                "spawn must use the explicitly selected realm, not only the settlement owner");
        int strength = session.getSimulation().getMilitarySystem().strength(selectedArmy);
        assertTrue(cheats.addTroopsToArmy(selectedArmy.id, here.id, 3).accepted);
        assertTrue(session.getSimulation().getMilitarySystem().strength(selectedArmy) > strength);

        assertTrue(cheats.orderArmyToSettlement(selectedArmy.id, elsewhere.id).accepted);
        assertEquals(Army.ArmyOrder.MOVE, selectedArmy.order);
        assertEquals(elsewhere.id, selectedArmy.destinationSettlementId.longValue());
        assertTrue(cheats.teleportArmyToPlayer(selectedArmy.id).accepted);
        WorldParty party = world.parties.get(player.partyId);
        assertEquals(6.0, Math.hypot(selectedArmy.position.x - party.position.x,
                selectedArmy.position.y - party.position.y), 0.001);
        int grain = selectedArmy.supplies.getQuantity(GoodType.GRAIN);
        assertTrue(cheats.resupplyArmy(selectedArmy.id).accepted);
        assertTrue(selectedArmy.supplies.getQuantity(GoodType.GRAIN) > grain);

        assertTrue(cheats.givePlayerCommand(selectedArmy.id).accepted);
        assertEquals(selectedArmy.id, player.commandedArmyId.longValue());
        assertTrue(selectedArmy.playerCommanded);
        assertEquals(player.personId, selectedArmy.commanderPersonId);

        assertTrue(cheats.woundPlayer().accepted);
        assertEquals(25.0, world.people.get(player.personId).health.healthLevel, 0.001);
        assertTrue(session.playerCanFight().accepted);

        assertTrue(cheats.teleportPlayerToSettlement(elsewhere.id).accepted);
        assertEquals(elsewhere.id, player.currentSettlementId);
        assertEquals(elsewhere.position.x, party.position.x, 0.001);
        assertEquals(elsewhere.position.y, party.position.y, 0.001);
        assertEquals(elsewhere.id, selectedArmy.currentSettlementId,
                "an accompanying commanded army teleports with the party");
        assertEquals(elsewhere.position.x, selectedArmy.position.x, 0.001);
        assertEquals(elsewhere.position.y, selectedArmy.position.y, 0.001);

        before = new HashSet<>(world.armies.keySet());
        assertTrue(cheats.spawnHostileArmyNearPlayer(5).accepted);
        Army hostile = newestArmy(world, before);
        assertNotEquals(selectedRealm.id, hostile.realmId);
        assertEquals(6.0, Math.hypot(hostile.position.x - party.position.x,
                hostile.position.y - party.position.y), 0.001);
        assertTrue(cheats.musterAllRealms().accepted);
        assertTrue(new WorldInvariantValidator().validate(world).isValid());
    }

    private static Army newestArmy(WorldState world, Set<Long> beforeIds) {
        return world.armies.values().stream()
                .filter(army -> !beforeIds.contains(army.id))
                .max(Comparator.comparingLong(army -> army.id)).orElseThrow();
    }
}
