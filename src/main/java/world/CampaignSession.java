package world;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Road;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.save.CampaignSaveCodec;
import world.politics.Government;

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
    private final ContractSystem contractSystem;
    private final CheatService cheatService;
    private world.local.LocalSceneSimulation localScene;
    private volatile CampaignSnapshot snapshot;
    private long lastSnapshotMinute = Long.MIN_VALUE;

    public CampaignSession(long seed) {
        this.seed = seed;
        this.world = new WorldState();
        this.clock = new WorldClock(0L);
        this.simulation = new WorldSimulation(clock, world);
        this.contractSystem = new ContractSystem(simulation.getContext());
        this.cheatService = new CheatService(simulation);
        configureRandomStreams(seed);

        WorldGenerator generator = new WorldGenerator(simulation.getContext());
        generator.generateVerticalSlice();
        this.playerState = createPlayerAdventurer();
        simulation.initializeGeneratedWorld();
        contractSystem.generateInitialOffers(clock.getWorldMinute());

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
        household.inventory.add(GoodType.GRAIN, 5);
        household.inventory.add(GoodType.VEGETABLES, 5);
        world.households.put(householdId, household);
        person.householdId = householdId;

        long partyId = world.idGenerator.next();
        WorldParty party = new WorldParty(partyId, personId, settlementId);
        party.money.add(250L);
        household.account = party.money; // one canonical purse shared by strategic household/party views
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement != null) party.position = new WorldPosition(
                settlement.position.x, settlement.position.y);
        world.parties.put(partyId, party);
        person.travelingPartyId = partyId;

        PlayerCampaignState state = new PlayerCampaignState(personId, householdId, partyId,
                settlementId, party.cargo);
        state.cargoCapacity = party.cargoCapacity;
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
        context.randomStreams.put("CRIME", new SeededRandom(seed + 7));
    }

    public void update(double realSeconds) {
        simulation.update(realSeconds);
        long currentMinute = clock.getWorldMinute();
        contractSystem.processDeadlines(playerState, currentMinute);
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
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) {
            party.state = WorldParty.PartyState.TRAVELING;
            party.currentSettlementId = null;
            party.destinationSettlementId = settlementId;
        }
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
        if (party != null) {
            for (Long memberId : party.memberPersonIds) {
                Person member = world.people.get(memberId);
                if (member != null) member.currentSettlementId = settlementId;
            }
        }
        if (household != null) household.homeSettlementId = settlementId;
        if (party != null) {
            party.state = WorldParty.PartyState.AT_SETTLEMENT;
            party.currentSettlementId = settlementId;
            party.destinationSettlementId = null;
            party.position = new WorldPosition(destination.position.x, destination.position.y);
        }
        contractSystem.onPlayerArrived(playerState, clock.getWorldMinute());
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult recruitCompanion(long personId) {
        Person companion = world.people.get(personId);
        WorldParty party = world.parties.get(playerState.partyId);
        if (companion == null || party == null) return CommandResult.rejected("INVALID_PERSON", "Person not found");
        if (!companion.alive || companion.currentSettlementId == null
                || companion.currentSettlementId != playerState.currentSettlementId) {
            return CommandResult.rejected("NOT_PRESENT", "Companion must be alive and present");
        }
        if (!party.memberPersonIds.contains(personId)) party.memberPersonIds.add(personId);
        companion.travelingPartyId = party.id;
        return CommandResult.accepted();
    }

    public CommandResult joinKingdom(long realmId, PlayerCampaignState.KingdomRole role) {
        Realm realm = world.realms.get(realmId);
        if (realm == null) return CommandResult.rejected("INVALID_REALM", "Kingdom not found");
        if (role != PlayerCampaignState.KingdomRole.MERCENARY
                && role != PlayerCampaignState.KingdomRole.LORD) {
            return CommandResult.rejected("INVALID_ROLE", "Join as a mercenary or lord");
        }
        playerState.affiliatedRealmId = realmId;
        playerState.kingdomRole = role;
        Person player = world.people.get(playerState.personId);
        if (player != null && role == PlayerCampaignState.KingdomRole.LORD) {
            player.type = Person.PersonType.NOBLE;
        }
        refreshSnapshot();
        return CommandResult.accepted();
    }

    /** Installs the player as ruler; succession/events and test tools can call the same hook. */
    public CommandResult appointPlayerAsKing(long realmId) {
        Realm realm = world.realms.get(realmId);
        Person player = world.people.get(playerState.personId);
        if (realm == null || player == null) {
            return CommandResult.rejected("INVALID_REALM", "Kingdom or player not found");
        }
        realm.rulerPersonId = player.id;
        player.type = Person.PersonType.NOBLE;
        playerState.affiliatedRealmId = realmId;
        playerState.kingdomRole = PlayerCampaignState.KingdomRole.KING;
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult adjustKingdomLaw(Government.LawType law, int delta) {
        Realm realm = effectivePlayerRealm();
        if (realm == null || realm.rulerPersonId == null || realm.rulerPersonId != playerState.personId) {
            return CommandResult.rejected("NOT_RULER", "Only the kingdom's ruler can change laws");
        }
        Government government = world.governments.get(realm.governmentId);
        if (government == null) return CommandResult.rejected("NO_GOVERNMENT", "Kingdom government not found");
        government.setLawLevel(law, government.getLawLevel(law) + delta);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    private Realm effectivePlayerRealm() {
        for (Realm realm : world.realms.values()) {
            if (realm.rulerPersonId != null && realm.rulerPersonId == playerState.personId) return realm;
        }
        return playerState.affiliatedRealmId == null ? null : world.realms.get(playerState.affiliatedRealmId);
    }

    public CommandResult acceptContract(long contractId) {
        CommandResult result = contractSystem.accept(contractId, playerState, clock.getWorldMinute());
        refreshSnapshot();
        return result;
    }

    public java.util.List<Contract> getAvailableContractsAt(long settlementId) {
        java.util.List<Contract> result = new java.util.ArrayList<>();
        for (Contract contract : world.contracts.values()) {
            if (contract.issuerSettlementId == settlementId
                    && contract.status == Contract.ContractStatus.OPEN) result.add(contract);
        }
        result.sort(java.util.Comparator.comparingLong(contract -> contract.id));
        return result;
    }

    public LocalPlayerState enterLocalScene() {
        return enterLocalScene(1600, 1200);
    }

    public LocalPlayerState enterLocalScene(int localWidth, int localHeight) {
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) party.state = WorldParty.PartyState.LOCAL_SCENE;
        localScene = new world.local.LocalSceneSimulation(world, playerState.currentSettlementId,
                localWidth, localHeight);
        Person person = world.people.get(playerState.personId);
        return new LocalPlayerState(person == null ? 100 : (int) Math.round(person.health.healthLevel),
                playerState.currentSettlementId);
    }

    public void updateLocalScene(double seconds, int localWidth, int localHeight) {
        if (localScene != null) localScene.update(seconds, clock.getWorldMinute(), localWidth, localHeight);
    }

    public java.util.List<world.local.LocalActor> getLocalActors() {
        return localScene == null ? java.util.List.of() : localScene.getActors();
    }

    public void leaveLocalScene(LocalPlayerState local) {
        Person person = world.people.get(playerState.personId);
        if (person != null && local != null) {
            person.health.healthLevel = local.health;
            person.health.clamp();
            if (person.health.healthLevel <= 0.0 && person.alive) person.die(clock.getWorldMinute(), Person.DeathCause.WAR);
        }
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) party.state = WorldParty.PartyState.AT_SETTLEMENT;
        localScene = null;
        refreshSnapshot();
    }

    public PlayerCampaignState getPlayerState() {
        return playerState;
    }

    public CheatService getCheats() {
        return cheatService;
    }

    public void refreshAfterCheat() {
        world.indexes.rebuild(world, clock.getWorldMinute());
        world.geography.getSpatialIndex().rebuildDynamic(world);
        refreshSnapshot();
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
        advanceDaysForTesting(1);
    }

    public void advanceDaysForTesting(int days) {
        if (days <= 0) return;
        boolean wasPaused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes((long) days * WorldConfig.MINUTES_PER_DAY);
        contractSystem.processDeadlines(playerState, clock.getWorldMinute());
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

    public void save(java.nio.file.Path path) throws java.io.IOException {
        CampaignSaveCodec.save(this, path);
    }

    public static CampaignSession load(java.nio.file.Path path) throws java.io.IOException {
        return CampaignSaveCodec.load(path);
    }

    /** Called by the versioned loader after canonical registries have been restored. */
    public void rebuildDerivedStateAfterLoad() {
        world.indexes.rebuild(world, clock.getWorldMinute());
        world.geography.getSpatialIndex().rebuildSettlements(world.geography.getSettlements().values());
        world.geography.getSpatialIndex().rebuildDynamic(world);
        new WorldInvariantValidator().validate(world).throwIfInvalid();
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        snapshot = CampaignSnapshot.capture(simulation);
        lastSnapshotMinute = clock.getWorldMinute();
    }
}
