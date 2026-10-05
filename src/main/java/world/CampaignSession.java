package world;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Road;
import world.geography.Settlement;

/**
 * Owns one playable campaign simulation and publishes immutable UI snapshots.
 * This is the integration boundary between the Swing game and the headless world.
 */
public final class CampaignSession {
    private final long seed;
    private final WorldState world;
    private final WorldClock clock;
    private final WorldSimulation simulation;
    private final PlayerCampaignState playerState;
    private volatile CampaignSnapshot snapshot;
    private long lastSnapshotMinute = Long.MIN_VALUE;

    public CampaignSession(long seed) {
        this.seed = seed;
        this.world = new WorldState();
        this.clock = new WorldClock(0L);
        this.simulation = new WorldSimulation(clock, world);
        configureRandomStreams(seed);

        WorldGenerator generator = new WorldGenerator(simulation.getContext());
        generator.generateVerticalSlice();
        this.playerState = createPlayerAdventurer();
        simulation.initializeGeneratedWorld();

        // Campaign testing starts at one world hour per real second. Players can
        // select 1x, 60x, or 1440x from the campaign map.
        clock.setSpeed(60);
        refreshSnapshot();
    }

    private PlayerCampaignState createPlayerAdventurer() {
        java.util.List<world.geography.Settlement> settlements = new java.util.ArrayList<>(
                world.geography.getSettlements().values());
        settlements.sort(java.util.Comparator.comparingLong(s -> s.id));
        if (settlements.isEmpty()) throw new IllegalStateException("Campaign requires a settlement");
        long settlementId = settlements.get(0).id;
        long personId = world.idGenerator.next();
        Person person = new Person(personId, "The", "Adventurer", Person.Sex.MALE,
                -25L * WorldConfig.MINUTES_PER_YEAR);
        person.currentSettlementId = settlementId;
        person.homeSettlementId = settlementId;
        world.people.put(personId, person);
        long householdId = world.idGenerator.next();
        Household household = new Household(householdId, settlementId, personId);
        household.account.add(250L);
        household.inventory.add(GoodType.GRAIN, 5);
        household.inventory.add(GoodType.VEGETABLES, 5);
        world.households.put(householdId, household);
        person.householdId = householdId;
        PlayerCampaignState state = new PlayerCampaignState(personId, householdId, settlementId);
        world.player = state;
        return state;
    }

    private void configureRandomStreams(long seed) {
        SimulationContext context = simulation.getContext();
        context.randomStreams.clear();
        context.randomStreams.put("DEFAULT", new SeededRandom(seed));
        context.randomStreams.put("DEMOGRAPHICS", new SeededRandom(seed + 1));
        context.randomStreams.put("ECONOMY", new SeededRandom(seed + 2));
        context.randomStreams.put("POLITICS", new SeededRandom(seed + 3));
        context.randomStreams.put("MILITARY", new SeededRandom(seed + 4));
        context.randomStreams.put("GENERATION", new SeededRandom(seed + 5));
        context.randomStreams.put("DIPLOMACY", new SeededRandom(seed + 6));
    }

    public void update(double realSeconds) {
        simulation.update(realSeconds);
        long currentMinute = clock.getWorldMinute();
        if (currentMinute != lastSnapshotMinute) {
            refreshSnapshot();
        }
    }

    public CommandResult buyFromSettlement(long settlementId, GoodType good, int quantity) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Household household = world.households.get(playerState.householdId);
        if (settlement == null || household == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (playerState.currentSettlementId != settlementId) return CommandResult.rejected("NOT_PRESENT", "Travel to the settlement first");
        quantity = Math.min(quantity, playerState.remainingCapacity());
        quantity = Math.min(quantity, settlement.publicStockpile.getQuantity(good));
        long price = Math.max(1L, settlement.market.getLastPrice(good));
        quantity = (int) Math.min(quantity, household.account.copperCoins / price);
        if (quantity <= 0 || !household.account.subtract(quantity * price)) {
            return CommandResult.rejected("CANNOT_BUY", "No stock, money, or cargo space");
        }
        settlement.treasury.add(quantity * price);
        settlement.publicStockpile.remove(good, quantity);
        playerState.cargo.add(good, quantity);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult sellToSettlement(long settlementId, GoodType good, int quantity) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Household household = world.households.get(playerState.householdId);
        if (settlement == null || household == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (playerState.currentSettlementId != settlementId) return CommandResult.rejected("NOT_PRESENT", "Travel to the settlement first");
        quantity = Math.min(quantity, playerState.cargo.getQuantity(good));
        long price = Math.max(1L, settlement.market.getLastPrice(good));
        quantity = (int) Math.min(quantity, settlement.treasury.copperCoins / price);
        if (quantity <= 0 || !settlement.treasury.subtract(quantity * price)) {
            return CommandResult.rejected("CANNOT_SELL", "No cargo or settlement funds");
        }
        playerState.cargo.remove(good, quantity);
        settlement.publicStockpile.add(good, quantity);
        household.account.add(quantity * price);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult travelPlayerTo(long settlementId) {
        if (settlementId == playerState.currentSettlementId) return CommandResult.accepted();
        Settlement destination = world.geography.getSettlement(settlementId);
        if (destination == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        java.util.List<Road> route = world.geography.getRouteGraph()
                .findShortestRoute(playerState.currentSettlementId, settlementId);
        if (route.isEmpty()) return CommandResult.rejected("NO_ROUTE", "No open road reaches that settlement");
        double distance = route.stream().mapToDouble(Road::getEffectiveCostFactor).sum();
        long travelMinutes = Math.max(60L, Math.round(distance / 10.0 * 60.0));
        boolean paused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes(travelMinutes);
        clock.setPaused(paused);
        playerState.currentSettlementId = settlementId;
        Person player = world.people.get(playerState.personId);
        Household household = world.households.get(playerState.householdId);
        if (player != null) {
            player.currentSettlementId = settlementId;
            player.homeSettlementId = settlementId;
        }
        if (household != null) household.homeSettlementId = settlementId;
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public PlayerCampaignState getPlayerState() {
        return playerState;
    }

    public void setSpeed(int speed) {
        clock.setSpeed(speed);
        refreshSnapshot();
    }

    public void toggleWorldPaused() {
        clock.setPaused(!clock.isPaused());
        refreshSnapshot();
    }

    public void setWorldPaused(boolean paused) {
        clock.setPaused(paused);
        refreshSnapshot();
    }

    public void advanceOneDayForTesting() {
        boolean wasPaused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes(WorldConfig.MINUTES_PER_DAY);
        clock.setPaused(wasPaused);
        refreshSnapshot();
    }

    public CampaignSnapshot getSnapshot() {
        return snapshot;
    }

    public WorldSimulation getSimulation() {
        return simulation;
    }

    public WorldState getWorld() {
        return world;
    }

    public long getSeed() {
        return seed;
    }

    private void refreshSnapshot() {
        snapshot = CampaignSnapshot.capture(simulation);
        lastSnapshotMinute = clock.getWorldMinute();
    }
}
