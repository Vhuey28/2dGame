package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.diplomacy.Treaty;
import world.economy.GoodType;
import world.geography.Province;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.PeaceOffer;
import world.military.Siege;

class WarSimulationTest {

    @Test
    void validWarCreatesPhysicalOrdersAndPersistentCoalitions() {
        CampaignSession session = new CampaignSession(100L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        Province target = provinceControlledBy(world, defender.id);

        CommandResult result = session.getSimulation().getWarSystem().declareWar(attacker.id,
                defender.id, War.WarGoal.CONQUER_PROVINCE, target.id, null, 0L);

        assertTrue(result.accepted);
        assertEquals(1, world.wars.size());
        War war = world.wars.values().iterator().next();
        assertTrue(war.attackerRealmIds.contains(attacker.id));
        assertTrue(war.defenderRealmIds.contains(defender.id));
        Army fieldArmy = world.armies.values().stream().filter(army -> army.realmId == attacker.id)
                .findFirst().orElseThrow();
        assertEquals(Army.ArmyOrder.BESIEGE, fieldArmy.order);
        assertNotNull(fieldArmy.destinationSettlementId);
    }

    @Test
    void siegeConsumesRealFoodCausesCivilianLossAndOccupation() {
        CampaignSession session = new CampaignSession(101L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        Province targetProvince = provinceControlledBy(world, defender.id);
        assertTrue(session.getSimulation().getWarSystem().declareWar(attacker.id, defender.id,
                War.WarGoal.CONQUER_PROVINCE, targetProvince.id, null, 0L).accepted);
        War war = world.wars.values().iterator().next();
        Army army = world.armies.values().stream().filter(candidate -> candidate.realmId == attacker.id)
                .findFirst().orElseThrow();
        world.armies.values().stream().filter(candidate -> candidate.realmId == defender.id)
                .forEach(candidate -> candidate.state = Army.ArmyState.DISBANDED);
        Settlement target = world.geography.getSettlement(army.destinationSettlementId);
        army.currentSettlementId = target.id;
        army.position = new WorldPosition(target.position.x, target.position.y);
        army.state = Army.ArmyState.MUSTERED;
        army.order = Army.ArmyOrder.BESIEGE;
        target.publicStockpile.remove(GoodType.GRAIN, target.publicStockpile.getQuantity(GoodType.GRAIN));
        target.publicStockpile.remove(GoodType.VEGETABLES, target.publicStockpile.getQuantity(GoodType.VEGETABLES));
        long livingBefore = world.people.values().stream().filter(person -> person.alive).count();

        for (int day = 1; day <= 4; day++) {
            session.getSimulation().getWarSystem().processDay(day * WorldConfig.MINUTES_PER_DAY);
        }

        Siege siege = world.sieges.values().iterator().next();
        assertTrue(siege.civilianDeaths >= 1);
        assertTrue(world.people.values().stream().filter(person -> person.alive).count() < livingBefore);
        siege.breachProgress = 99.9;
        session.getSimulation().getWarSystem().processDay(5L * WorldConfig.MINUTES_PER_DAY);
        assertEquals(Siege.SiegeState.CAPTURED, siege.state);
        assertEquals(attacker.id, target.occupyingRealmId);
        assertTrue(war.attackerScore > 0.0);
    }

    @Test
    void peaceTermsTransferTerritoryAtomicallyAndCreateTruce() {
        CampaignSession session = new CampaignSession(102L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        Province target = provinceControlledBy(world, defender.id);
        assertTrue(session.getSimulation().getWarSystem().declareWar(attacker.id, defender.id,
                War.WarGoal.CONQUER_PROVINCE, target.id, null, 0L).accepted);
        War war = world.wars.values().iterator().next();
        PeaceOffer offer = session.getSimulation().getWarSystem().createPeaceOffer(war.id,
                attacker.id, defender.id, WorldConfig.MINUTES_PER_MONTH);
        offer.terms.add(new PeaceOffer.PeaceTerm(PeaceOffer.TermType.TRANSFER_PROVINCE,
                target.id, defender.id, attacker.id, 0L));

        CommandResult result = session.getSimulation().getWarSystem().submitPeaceOffer(offer,
                WorldConfig.MINUTES_PER_MONTH, true);

        assertTrue(result.accepted);
        assertEquals(PeaceOffer.OfferState.ACCEPTED, offer.state);
        assertEquals(War.WarState.ENDED, war.state);
        assertEquals(attacker.id, target.controllerRealmId);
        assertTrue(attacker.controlledProvinceIds.contains(target.id));
        assertFalse(defender.controlledProvinceIds.contains(target.id));
        assertTrue(world.treaties.values().stream().anyMatch(treaty -> treaty.type == Treaty.TreatyType.TRUCE
                && treaty.includes(attacker.id) && treaty.includes(defender.id)));
    }

    @Test
    void invalidWarGoalIsRejected() {
        CampaignSession session = new CampaignSession(103L);
        WorldState world = session.getWorld();
        ArrayList<Realm> realms = sortedRealms(world);
        Province ownProvince = provinceControlledBy(world, realms.get(0).id);

        CommandResult result = session.getSimulation().getWarSystem().declareWar(realms.get(0).id,
                realms.get(1).id, War.WarGoal.CONQUER_PROVINCE, ownProvince.id, null, 0L);

        assertFalse(result.accepted);
        assertEquals("INVALID_WAR_GOAL", result.reasonCode);
        assertTrue(world.wars.isEmpty());
    }

    @Test
    void generatedSettlementsHaveReadableStrategicSpacing() {
        CampaignSession session = new CampaignSession(104L);
        ArrayList<Settlement> settlements = new ArrayList<>(session.getWorld().geography.getSettlements().values());
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 0; i < settlements.size(); i++) {
            for (int j = i + 1; j < settlements.size(); j++) {
                double dx = settlements.get(i).position.x - settlements.get(j).position.x;
                double dy = settlements.get(i).position.y - settlements.get(j).position.y;
                minimum = Math.min(minimum, Math.sqrt(dx * dx + dy * dy));
            }
        }
        assertTrue(minimum >= 250.0, "Strategic settlements should not overlap visually");
    }

    private ArrayList<Realm> sortedRealms(WorldState world) {
        ArrayList<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        return realms;
    }

    private Province provinceControlledBy(WorldState world, long realmId) {
        return world.geography.getProvinces().values().stream()
                .filter(province -> province.controllerRealmId != null && province.controllerRealmId == realmId)
                .findFirst().orElseThrow();
    }
}
