package world;

import world.event.ScheduledEvent;
import world.event.ScheduledEventQueue;
import world.event.WorldEvent;
import world.economy.MarketSystem;
import world.economy.ProductionSystem;
import world.economy.HouseholdConsumptionSystem;
import world.economy.TaxSystem;
import world.economy.FamineSystem;
import world.economy.EmploymentSystem;
import world.economy.HouseholdTradeSystem;
import world.economy.Inventory;
import world.economy.MoneyAccount;
import world.trade.TradeSystem;
import world.politics.PoliticsSystem;
import world.diplomacy.DiplomacySystem;
import world.military.MilitarySystem;
import world.military.WarSystem;

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
    private final DemographicSystem demographicSystem;
    private final EmploymentSystem employmentSystem;
    private final HouseholdTradeSystem householdTradeSystem;
    private final TradeSystem tradeSystem;
    private final PoliticsSystem politicsSystem;
    private final DiplomacySystem diplomacySystem;
    private final MilitarySystem militarySystem;
    private final WarSystem warSystem;

    public WorldSimulation(WorldClock clock, WorldState world) {
        this.clock = clock;
        this.world = world;
        this.context = new SimulationContext(this);

        // Initialize new systems
        this.marketSystem = new MarketSystem(world.geography.getSettlements());
        this.productionSystem = new ProductionSystem(this.marketSystem);
        this.householdConsumptionSystem = new HouseholdConsumptionSystem(world.households, world.people);
        this.taxSystem = new TaxSystem(context);
        this.famineSystem = new FamineSystem(world.households, world.people, world.geography, context);
        this.demographicSystem = new DemographicSystem(context);
        this.employmentSystem = new EmploymentSystem(context);
        this.householdTradeSystem = new HouseholdTradeSystem(context);
        this.tradeSystem = new TradeSystem(context);
        this.politicsSystem = new PoliticsSystem(context);
        this.diplomacySystem = new DiplomacySystem(context);
        this.militarySystem = new MilitarySystem(context);
        this.warSystem = new WarSystem(context, militarySystem);
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

    public DiplomacySystem getDiplomacySystem() {
        return diplomacySystem;
    }

    public MilitarySystem getMilitarySystem() {
        return militarySystem;
    }

    public world.military.BattleBridge getBattleBridge() {
        return militarySystem.getBattleBridge();
    }

    public WarSystem getWarSystem() {
        return warSystem;
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

        long prevHour = previousMinute / WorldConfig.MINUTES_PER_HOUR;
        long currHour = current / WorldConfig.MINUTES_PER_HOUR;
        for (long hour = prevHour + 1; hour <= currHour; hour++) {
            long hourMinute = hour * WorldConfig.MINUTES_PER_HOUR;
            tradeSystem.processHour(hourMinute);
            militarySystem.processHour(hourMinute);
        }

        // Process every crossed boundary. This is important for campaign debug
        // controls, loading catch-up, and future fast-forward commands that may
        // advance more than one day at a time.
        long prevDay = previousMinute / WorldConfig.MINUTES_PER_DAY;
        long currDay = current / WorldConfig.MINUTES_PER_DAY;
        for (long day = prevDay + 1; day <= currDay; day++) {
            processDaySystems(day * WorldConfig.MINUTES_PER_DAY);
        }

        long prevMonth = previousMinute / WorldConfig.MINUTES_PER_MONTH;
        long currMonth = current / WorldConfig.MINUTES_PER_MONTH;
        for (long month = prevMonth + 1; month <= currMonth; month++) {
            processMonthSystems(month * WorldConfig.MINUTES_PER_MONTH);
        }

        context.eventHistory.trim(current);
    }

    /** Prepare systems after a generated or loaded world has populated registries. */
    public void initializeGeneratedWorld() {
        long minute = clock.getWorldMinute();
        employmentSystem.processWeek(minute);
        tradeSystem.processWeek(minute);
    }

    /** Process all daily systems (production, trade, consumption, market clearing). */
    private void processDaySystems(long currentMinute) {
        productionSystem.processDay(world.geography.getSettlements(), world.people, world.households);
        employmentSystem.processDay();
        householdTradeSystem.processDay();
        householdConsumptionSystem.processDay(currentMinute);
        tradeSystem.processDay(currentMinute);
        militarySystem.processDay(currentMinute);
        warSystem.processDay(currentMinute);
        long day = currentMinute / WorldConfig.MINUTES_PER_DAY;
        if (day % 7 == 0) {
            employmentSystem.processWeek(currentMinute);
            tradeSystem.processWeek(currentMinute);
        }

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
        militarySystem.processMonth(currentMinute);
        taxSystem.processMonth(world.geography.getSettlements(), world.households, world.people, currentMinute);
        famineSystem.processMonth(currentMinute);
        demographicSystem.processMonth();
        politicsSystem.processMonth(currentMinute);
        diplomacySystem.processMonth(currentMinute);
        warSystem.processMonth(currentMinute);
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