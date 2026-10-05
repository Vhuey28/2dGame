package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Settlement;
import world.military.BattleReport;
import world.military.Regiment;

class MilitarySimulationTest {

    @Test
    void generatedArmiesContainUniquePersistentPeople() {
        CampaignSession session = new CampaignSession(12345L);
        WorldState world = session.getWorld();

        assertEquals(2, world.armies.size());
        assertEquals(2, world.regiments.size());
        Set<Long> soldiers = new HashSet<>();
        for (Regiment regiment : world.regiments.values()) {
            assertEquals(12, regiment.livingStrength(world));
            for (Long personId : regiment.soldierPersonIds) {
                assertTrue(soldiers.add(personId), "A person cannot serve in two regiments");
                Person person = world.people.get(personId);
                assertNotNull(person);
                assertTrue(person.alive);
                assertEquals(regiment.id, person.regimentId);
                assertEquals(Person.PersonType.SOLDIER, person.type);
                assertNull(person.employerId);
            }
        }
        assertEquals(24, soldiers.size());
        assertTrue(soldiers.size() <= world.people.values().stream().filter(person -> person.alive).count());
    }

    @Test
    void campaigningConsumesCarriedPhysicalSupplies() {
        CampaignSession session = new CampaignSession(44L);
        Army army = session.getWorld().armies.values().iterator().next();
        int before = army.supplies.getQuantity(GoodType.GRAIN);
        assertTrue(before > 0);
        army.state = Army.ArmyState.TRAVELING;

        session.getSimulation().getMilitarySystem().processDay(WorldConfig.MINUTES_PER_DAY);

        assertTrue(army.supplies.getQuantity(GoodType.GRAIN) < before);
        assertEquals(0, army.daysWithoutFood);
    }

    @Test
    void recruitmentNeverCreatesPeopleOrExceedsEligibleResidents() {
        CampaignSession session = new CampaignSession(55L);
        WorldState world = session.getWorld();
        Realm realm = world.realms.values().stream().min(Comparator.comparingLong(candidate -> candidate.id)).orElseThrow();
        Settlement settlement = world.geography.getSettlements().values().stream()
                .filter(candidate -> candidate.controllerRealmId != null
                        && candidate.controllerRealmId == realm.id
                        && candidate.id != realm.capitalSettlementId)
                .findFirst().orElseThrow();
        int populationBefore = world.people.size();
        long eligibleBefore = world.people.values().stream().filter(person -> person.alive
                && person.currentSettlementId != null && person.currentSettlementId == settlement.id
                && person.regimentId == null && person.travelingPartyId == null
                && !person.isChild(0L) && !person.isElderly(0L)
                && person.type != Person.PersonType.NOBLE
                && person.type != Person.PersonType.MERCHANT).count();
        settlement.publicStockpile.add(GoodType.WEAPONS, 1000);
        realm.treasury.add(100_000L);
        int regimentsBefore = world.regiments.size();

        CommandResult result = session.getSimulation().getMilitarySystem().recruitRegiment(
                realm.id, settlement.id, 1000, Regiment.RegimentType.SPEAR, 0L);

        assertTrue(result.accepted);
        assertEquals(populationBefore, world.people.size());
        Regiment created = world.regiments.values().stream()
                .filter(regiment -> regiment.id > world.regiments.values().stream()
                        .mapToLong(other -> other.id).min().orElse(0L))
                .max(Comparator.comparingLong(regiment -> regiment.id)).orElseThrow();
        assertEquals(regimentsBefore + 1, world.regiments.size());
        assertTrue(created.soldierPersonIds.size() <= eligibleBefore);
    }

    @Test
    void autoResolutionKillsExactPeopleAndAffectsHouseholds() {
        CampaignSession session = new CampaignSession(66L);
        WorldState world = session.getWorld();
        ArrayList<Army> armies = new ArrayList<>(world.armies.values());
        armies.sort(Comparator.comparingLong(army -> army.id));
        Army first = armies.get(0);
        Army second = armies.get(1);
        long livingBefore = world.people.values().stream().filter(person -> person.alive).count();
        int householdMembersBefore = world.households.values().stream().mapToInt(Household::getMemberCount).sum();

        BattleReport report = session.getSimulation().getMilitarySystem()
                .autoResolveEncounter(first.id, second.id, WorldConfig.MINUTES_PER_DAY);

        assertNotNull(report);
        int losses = report.firstCasualties + report.secondCasualties;
        assertTrue(losses > 0);
        long livingAfter = world.people.values().stream().filter(person -> person.alive).count();
        int householdMembersAfter = world.households.values().stream().mapToInt(Household::getMemberCount).sum();
        assertEquals(losses, livingBefore - livingAfter);
        assertEquals(losses, householdMembersBefore - householdMembersAfter);
        assertEquals(1, world.battleReports.size());
    }

    @Test
    void demobilizationReturnsSurvivorsToCivilianLife() {
        CampaignSession session = new CampaignSession(77L);
        WorldState world = session.getWorld();
        Army army = world.armies.values().iterator().next();
        ArrayList<Long> soldiers = new ArrayList<>();
        for (Long regimentId : army.regimentIds) {
            soldiers.addAll(world.regiments.get(regimentId).soldierPersonIds);
        }

        CommandResult result = session.getSimulation().getMilitarySystem().demobilize(army.id, 0L);

        assertTrue(result.accepted);
        assertEquals(Army.ArmyState.DISBANDED, army.state);
        for (Long personId : soldiers) {
            Person person = world.people.get(personId);
            assertNull(person.regimentId);
            assertNull(person.travelingPartyId);
            assertEquals(Person.PersonType.CITIZEN, person.type);
            assertEquals(army.homeSettlementId, person.currentSettlementId);
        }
    }
}
