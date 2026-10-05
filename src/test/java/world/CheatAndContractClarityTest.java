package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;

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
}
