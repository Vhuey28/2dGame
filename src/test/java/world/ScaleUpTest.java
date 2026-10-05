package world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.event.EventHistory;
import world.event.WorldEvent;
import world.geography.Road;
import world.geography.RouteGraph;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.geography.WorldSpatialIndex;

class ScaleUpTest {
    @Test
    void routeCacheReturnsCopiesAndRejectsNewlyBlockedRoads() {
        RouteGraph graph = new RouteGraph();
        Road direct = new Road(1L, 10L, 20L, 5.0, 1.0, 1.0, 0.0);
        Road alternateA = new Road(2L, 10L, 30L, 4.0, 1.0, 1.0, 0.0);
        Road alternateB = new Road(3L, 30L, 20L, 4.0, 1.0, 1.0, 0.0);
        graph.addRoad(direct);
        graph.addRoad(alternateA);
        graph.addRoad(alternateB);

        List<Road> first = graph.findShortestRoute(10L, 20L);
        assertEquals(List.of(direct), first);
        first.clear();
        assertEquals(List.of(direct), graph.findShortestRoute(10L, 20L));
        assertEquals(1L, graph.getCacheHits());

        direct.blocked = true; // cache validation also protects callers that mutate the legacy field directly
        assertEquals(List.of(alternateA, alternateB), graph.findShortestRoute(10L, 20L));
        assertEquals(2L, graph.getCacheMisses());
    }

    @Test
    void spatialAndPopulationIndexesAreRebuildableDerivedState() {
        WorldState world = new WorldState();
        Settlement settlement = new Settlement(1L, "Index Town", 10L,
                new WorldPosition(10.0, 10.0), Settlement.SettlementType.TOWN);
        world.geography.addSettlement(settlement);
        Person person = new Person(2L, "Ada", "Index", Person.Sex.FEMALE, 0L);
        person.homeSettlementId = 1L;
        person.currentSettlementId = 1L;
        world.people.put(person.id, person);
        Household household = new Household(3L, 1L, person.id);
        person.householdId = household.id;
        world.households.put(household.id, household);

        world.indexes.rebuild(world, 7L);
        assertEquals(List.of(2L), world.indexes.livingPeopleAt(1L));
        assertEquals(List.of(3L), world.indexes.householdsAt(1L));

        WorldSpatialIndex spatial = world.geography.getSpatialIndex();
        assertEquals(List.of(1L), spatial.querySettlementIds(10.0, 10.0, 2.0));
        assertTrue(spatial.querySettlementIds(500.0, 500.0, 2.0).isEmpty());
    }

    @Test
    void historyRetentionIsBoundedAndRoutineEventsAreNotPermanent() {
        EventHistory history = new EventHistory();
        history.record(new WorldEvent(1L, 0L, "MARKET_CLEARED"));
        for (int i = 0; i < 10_050; i++) history.record(new WorldEvent(i + 2L, i, "BATTLE_ENDED"));
        assertEquals(10_000, history.getHistorical().size());
        assertTrue(history.getHistorical().stream().noneMatch(event -> "MARKET_CLEARED".equals(event.type)));
    }

    @Test
    void configurableScaleGenerationLeavesDefaultCampaignOptional() {
        WorldState world = new WorldState();
        WorldSimulation simulation = new WorldSimulation(new WorldClock(), world);
        new WorldGenerator(simulation.getContext()).generateScale(new WorldGenerationScale(3, 12, 120));
        assertEquals(3, world.realms.size());
        assertEquals(12, world.settlements.size());
        assertEquals(120, world.people.size());
        assertTrue(new WorldInvariantValidator().validate(world).isValid());
    }

    @Test
    void oneHundredYearHeadlessSoakCompletesWithoutInvariantViolations() {
        WorldSimulation simulation = new WorldSimulation(new WorldClock(), new WorldState());
        SoakRunner.Result result = new SoakRunner().run(simulation, 100, 365);
        assertEquals(100, result.years);
        assertTrue(result.invariantReport.isValid());
        assertEquals(100L * WorldConfig.MINUTES_PER_YEAR, simulation.getClock().getWorldMinute());
        assertTrue(result.profile.getTicks() > 0);
    }
}
