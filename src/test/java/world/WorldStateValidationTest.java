package world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import world.event.*;

class WorldStateValidationTest {

    @Test
    void testIdGeneratorProducesSequentialIds() {
        IdGenerator idGen = new IdGenerator();
        long id1 = idGen.next();
        long id2 = idGen.next();
        long id3 = idGen.next();

        assertEquals(1L, id1);
        assertEquals(2L, id2);
        assertEquals(3L, id3);
        assertEquals(4L, idGen.getNextId());
    }

    @Test
    void testIdGeneratorRestore() {
        IdGenerator idGen = new IdGenerator();
        idGen.next(); // 1
        idGen.next(); // 2

        idGen.restoreNextId(100L);
        assertEquals(100L, idGen.next());
        assertEquals(101L, idGen.next());
    }

    @Test
    void testWorldStateInitialization() {
        WorldState world = new WorldState();

        assertNotNull(world.people);
        assertNotNull(world.households);
        assertNotNull(world.settlements);
        assertNotNull(world.realms);
        assertNotNull(world.armies);
        assertNotNull(world.caravans);
        assertNotNull(world.wars);
        assertNotNull(world.idGenerator);

        assertTrue(world.people.isEmpty());
        assertTrue(world.households.isEmpty());
        assertTrue(world.settlements.isEmpty());
        assertTrue(world.realms.isEmpty());
        assertTrue(world.armies.isEmpty());
        assertTrue(world.caravans.isEmpty());
        assertTrue(world.wars.isEmpty());
    }

    @Test
    void testWorldClockAdvance() {
        WorldClock clock = new WorldClock(0L);
        assertEquals(0L, clock.getWorldMinute());

        clock.advance(60L);
        assertEquals(60L, clock.getWorldMinute());

        clock.setPaused(true);
        clock.advance(30L);
        assertEquals(60L, clock.getWorldMinute()); // Should not change when paused

        clock.setPaused(false);
        clock.advance(30L);
        assertEquals(90L, clock.getWorldMinute());
    }

    @Test
    void testWorldClockTimeComponents() {
        WorldClock clock = new WorldClock(0L);

        // 61 minutes = 1 hour, 1 minute into the day
        clock.setWorldMinute(61L);
        assertEquals(1, clock.getHourOfDay());
        assertEquals(0, clock.getDayOfMonth()); // still day 0
        assertEquals(0, clock.getMonthOfYear());
        assertEquals(0, clock.getYear());
    }

    @Test
    void testWorldCalendarConversions() {
        long minute = WorldCalendar.toMinute(2024, 4, 15, 10, 30);
        assertEquals(2024, WorldCalendar.getYear(minute));
        assertEquals(4, WorldCalendar.getMonth(minute)); // 0-indexed
        assertEquals(15, WorldCalendar.getDay(minute));
        assertEquals(10, WorldCalendar.getHour(minute));
        assertEquals(30, WorldCalendar.getMinuteOfHour(minute));
    }

    @Test
    void testEventBusPublishAndSubscribe() {
        EventBus bus = new EventBus();
        final boolean[] received = {false};
        final String[] typeReceived = {null};

        bus.subscribe("TEST_EVENT", event -> {
            received[0] = true;
            typeReceived[0] = event.type;
        });

        WorldEvent event = new WorldEvent(1L, 100L, "TEST_EVENT");
        bus.publish(event);

        assertTrue(received[0]);
        assertEquals("TEST_EVENT", typeReceived[0]);
        assertEquals(1, bus.getHistory().getRecent().size());
    }

    @Test
    void testScheduledEventQueueOrdering() {
        ScheduledEventQueue queue = new ScheduledEventQueue();

        queue.enqueue(new ScheduledEvent(1L, 100L, "LATE"));
        queue.enqueue(new ScheduledEvent(2L, 50L, "EARLY"));
        queue.enqueue(new ScheduledEvent(3L, 75L, "MIDDLE"));

        assertEquals(3, queue.size());

        ScheduledEvent first = queue.poll();
        assertEquals(50L, first.dueMinute);
        assertEquals("EARLY", first.type);

        ScheduledEvent second = queue.poll();
        assertEquals(75L, second.dueMinute);
        assertEquals("MIDDLE", second.type);

        ScheduledEvent third = queue.poll();
        assertEquals(100L, third.dueMinute);
        assertEquals("LATE", third.type);

        assertTrue(queue.isEmpty());
    }

    @Test
    void testSeededRandomDeterministic() {
        SeededRandom rand1 = new SeededRandom(12345L);
        SeededRandom rand2 = new SeededRandom(12345L);

        int[] vals1 = new int[5];
        int[] vals2 = new int[5];
        for (int i = 0; i < 5; i++) {
            vals1[i] = rand1.nextInt(100);
            vals2[i] = rand2.nextInt(100);
        }

        assertArrayEquals(vals1, vals2);

        // Different seeds should produce different sequences
        SeededRandom rand3 = new SeededRandom(54321L);
        int[] vals3 = new int[5];
        for (int i = 0; i < 5; i++) {
            vals3[i] = rand3.nextInt(100);
        }

        assertNotEquals(vals1[0], vals3[0]); // Very unlikely to be equal
    }

    @Test
    void testSimulationContextRandomStreams() {
        WorldClock clock = new WorldClock();
        WorldState world = new WorldState();
        WorldSimulation sim = new WorldSimulation(clock, world);
        SimulationContext ctx = sim.getContext();

        SeededRandom defaultRand = ctx.getRandom("DEFAULT");
        SeededRandom econRand = ctx.getRandom("ECONOMY");
        SeededRandom unknownRand = ctx.getRandom("UNKNOWN_STREAM");

        assertNotNull(defaultRand);
        assertNotNull(econRand);
        assertNotNull(unknownRand); // Should fall back to DEFAULT

        // UNKNOWN_STREAM should return the same instance as DEFAULT
        assertSame(defaultRand, unknownRand);

        // Calling nextInt on defaultRand and then on unknownRand (which is the same object)
        // should produce consecutive, different values from the same random stream.
        int val1 = defaultRand.nextInt(1000);
        int val2 = unknownRand.nextInt(1000);
        assertNotEquals(val1, val2);
    }
}