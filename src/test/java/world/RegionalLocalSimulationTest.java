package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.local.LocalActor;

class RegionalLocalSimulationTest {
    @Test
    void everyResidentIsProjectedAndMovesOnItsCanonicalSchedule() {
        CampaignSession session = new CampaignSession(1201L);
        long settlementId = session.getPlayerState().currentSettlementId;
        long residentCount = session.getWorld().people.values().stream()
                .filter(person -> person.alive && person.currentSettlementId != null
                        && person.currentSettlementId == settlementId
                        && person.id != session.getPlayerState().personId)
                .count();

        session.enterLocalScene(1200, 900);
        List<LocalActor> before = session.getLocalActors();
        assertEquals(residentCount, before.stream()
                .filter(actor -> actor.kind == LocalActor.ActorKind.PERSON).count());
        LocalActor actor = before.stream().filter(value -> value.kind == LocalActor.ActorKind.PERSON)
                .findFirst().orElseThrow();
        double x = actor.x;
        double y = actor.y;
        session.updateLocalScene(5.0, 1200, 900);
        assertTrue(actor.x != x || actor.y != y);
        assertEquals(session.getWorld().people.get(actor.sourceId).currentActivity, actor.activity);
    }

    @Test
    void schedulesChangeByHourForWorkersChildrenAndSoldiers() {
        WorldState world = new WorldState();
        Person worker = new Person(1L, "Work", "Er", Person.Sex.FEMALE, -30L * WorldConfig.MINUTES_PER_YEAR);
        worker.employerId = 9L;
        Person child = new Person(2L, "Young", "One", Person.Sex.MALE, -8L * WorldConfig.MINUTES_PER_YEAR);
        child.type = Person.PersonType.CHILD;
        Person soldier = new Person(3L, "Guard", "One", Person.Sex.MALE, -25L * WorldConfig.MINUTES_PER_YEAR);
        soldier.type = Person.PersonType.SOLDIER;
        world.people.put(1L, worker);
        world.people.put(2L, child);
        world.people.put(3L, soldier);
        ScheduleSystem schedules = new ScheduleSystem(world);

        schedules.processHour(10L * WorldConfig.MINUTES_PER_HOUR);
        assertEquals(PersonActivity.WORKING, worker.currentActivity);
        assertEquals(PersonActivity.SOCIALIZING, child.currentActivity);
        assertEquals(PersonActivity.GUARDING, soldier.currentActivity);
        schedules.processHour(23L * WorldConfig.MINUTES_PER_HOUR);
        assertEquals(PersonActivity.SLEEPING, worker.currentActivity);
    }

    @Test
    void crimesPersistAndApplyLocalSecurityConsequences() {
        CampaignSession session = new CampaignSession(1202L);
        double securityBefore = session.getWorld().geography.getSettlements().values().stream()
                .mapToDouble(settlement -> settlement.security).sum();
        session.advanceOneDayForTesting();

        assertFalse(session.getWorld().crimeIncidents.isEmpty());
        double securityAfter = session.getWorld().geography.getSettlements().values().stream()
                .mapToDouble(settlement -> settlement.security).sum();
        assertTrue(securityAfter < securityBefore);
        assertFalse(session.getSnapshot().crimes.isEmpty());
        assertTrue(new WorldInvariantValidator().validate(session.getWorld()).isValid());
    }
}
