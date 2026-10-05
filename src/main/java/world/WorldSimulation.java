package world;

import world.event.ScheduledEvent;
import world.event.ScheduledEventQueue;
import world.event.WorldEvent;
import world.economy.MarketSystem;
import world.economy.ProductionSystem;
import world.economy.HouseholdConsumptionSystem;
import world.economy.TaxSystem;
import world.economy.FamineSystem;
import world.economy.Inventory;
import world.economy.MoneyAccount;

/**
 * The simulation orchestrator. Advances time, processes scheduled events,
 * and publishes events. Does not depend on Swing, sprites, or audio —
 * it is the headless core that GamePanel can call but not control.
 */
public class WorldSimulation {
    private final WorldClock clock;
    private final WorldState world;
    private final SimulationContext context;
    private final ScheduledEventQueue scheduledEvents = new ScheduledEventQueue();
    private double accumulatedWorldMinutes = 0.0;

    // New systems for Phase 2: Food Economy
    private final MarketSystem marketSystem;
    private final ProductionSystem productionSystem;
    private final HouseholdConsumptionSystem householdConsumptionSystem;
    private final TaxSystem taxSystem;
    private final FamineSystem famineSystem;
    private final DemographicSystem demographicSystem; // Also integrate demographicSystem here

    public WorldSimulation(WorldClock clock, WorldState world) {
        this.clock = clock;
        this.world = world;
        this.context = new SimulationContext(this);

        // Initialize new systems
        this.marketSystem = new MarketSystem(world.geography.getSettlements());
        this.productionSystem = new ProductionSystem(this.marketSystem);
        this.householdConsumptionSystem = new HouseholdConsumptionSystem(world.households);
        this.taxSystem = new TaxSystem();
        this.famineSystem = new FamineSystem(world.households, world.people, world.geography, context);
        this.demographicSystem = new DemographicSystem(context); // Initialize demographicSystem
    }

    public WorldClock getClock() {
        return clock;
    }

    public WorldState getWorld() {
        return world;
    }

    public SimulationContext getContext() {
        return context;
    }

    public ScheduledEventQueue getScheduledEvents() {
        return scheduledEvents;
    }

    /** Called each render frame to accumulate elapsed real time and advance the world. */
    public void update(double realSeconds) {
        if (clock.isPaused()) return;

        accumulatedWorldMinutes += realSeconds * WorldConfig.WORLD_MINUTES_PER_REAL_SECOND * clock.getSpeed();

        int budget = WorldConfig.MAX_STEPS_PER_FRAME;
        while (accumulatedWorldMinutes >= WorldConfig.STEP_MINUTES && budget-- > 0) {
            advanceMinutes(WorldConfig.STEP_MINUTES);
            accumulatedWorldMinutes -= WorldConfig.STEP_MINUTES;
        }
    }

    /** Advance the clock by the specified minutes and process due events. */
    public void advanceMinutes(long minutes) {
        long previousMinute = clock.getWorldMinute();
        clock.advance(minutes);
        long current = clock.getWorldMinute();

        // Process scheduled events due at this minute
        for (ScheduledEvent e : scheduledEvents.getDueEvents(current)) {
            onScheduledEvent(e, current);
        }

        // Check if we've crossed a day boundary (1440 minutes per day)
        long prevDay = previousMinute / WorldConfig.MINUTES_PER_DAY;
        long currDay = current / WorldConfig.MINUTES_PER_DAY;
        if (currDay > prevDay) {
            processDaySystems(currDay * WorldConfig.MINUTES_PER_DAY);
        }

        // Check if we've crossed a month boundary (43200 minutes per month)
        long prevMonth = previousMinute / WorldConfig.MINUTES_PER_MONTH;
        long currMonth = current / WorldConfig.MINUTES_PER_MONTH;
        if (currMonth > prevMonth) {
            processMonthSystems(currMonth * WorldConfig.MINUTES_PER_MONTH);
        }
    }

    /** Process all daily systems (production, consumption, market clearing). */
    private void processDaySystems(long currentMinute) {
        productionSystem.processDay(world.geography.getSettlements(), world.people, world.households);
        householdConsumptionSystem.processDay(currentMinute);

        // Clear markets at end of day using current inventories and accounts
        java.util.Map<Long, Inventory> inventories = new java.util.HashMap<>();
        java.util.Map<Long, MoneyAccount> accounts = new java.util.HashMap<>();
        for (world.geography.Settlement s : world.geography.getSettlements().values()) {
            inventories.put(s.id, s.publicStockpile);
            accounts.put(s.id, s.treasury);
        }
        marketSystem.clearMarket(currentMinute, inventories, accounts);
    }

    /** Process all monthly systems (taxes, famine, demographics). */
    private void processMonthSystems(long currentMinute) {
        taxSystem.processMonth(world.geography.getSettlements(), world.households, world.people, currentMinute);
        famineSystem.processMonth(currentMinute);
        demographicSystem.processMonth(); // Use world time for births/aging/deaths
    }

    private void onScheduledEvent(ScheduledEvent event, long currentMinute) {
        switch (event.type) {
            case "DEFAULT":
                break;
            default:
                break;
        }
    }

    /** Publish an event to all subscribers. */
    public void publish(WorldEvent event) {
        context.eventBus.publish(event);
    }
}