package world;

import world.command.CommandResult;
import world.diplomacy.Grievance;
import world.economy.GoodType;
import world.geography.Road;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.BattleContext;
import world.military.Siege;
import world.politics.Claim;
import world.politics.Government;
import world.politics.Office;
import world.politics.Title;
import world.save.CampaignSaveCodec;

import java.util.ArrayList;

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
        advancePlayerTravel(currentMinute);
        processCaptivity(currentMinute);
        processHoldingIncome(currentMinute);
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
        CommandResult blocked = freeActionBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
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
        CommandResult blocked = freeActionBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
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

    /**
     * Legacy instant travel retained for tests and scripted settlement changes. The
     * campaign map uses {@link #beginTravelTo(long)}, which moves the party token
     * along roads while the simulation clock runs.
     */
    public CommandResult travelPlayerTo(long settlementId) {
        if (settlementId == playerState.currentSettlementId && !playerState.isTraveling()) {
            return CommandResult.accepted();
        }
        CommandResult blocked = captivityBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        Settlement destination = world.geography.getSettlement(settlementId);
        if (destination == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (settlementId == playerState.currentSettlementId) {
            // Instant travel back to the journey origin cancels the road journey.
            arrivePlayerAtSettlement(settlementId);
            refreshSnapshot();
            return CommandResult.accepted();
        }
        java.util.List<Road> route = world.geography.getRouteGraph()
                .findShortestRoute(playerState.currentSettlementId, settlementId);
        if (route.isEmpty()) return CommandResult.rejected("NO_ROUTE", "No open road reaches that settlement");
        double distance = route.stream().mapToDouble(Road::getEffectiveCostFactor).sum();
        long travelMinutes = Math.max(60L, Math.round(distance / 10.0 * 60.0));
        playerState.clearTravel();
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
        arrivePlayerAtSettlement(settlementId);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    /**
     * Starts (or retargets) a Bannerlord-style road journey. The party remains in
     * the world while time passes: armies and caravans keep moving, contracts keep
     * ageing, and the player can reverse or choose a new destination at the next
     * road decision point. If a commanded army is travelling with the party it is
     * ordered to the same destination.
     */
    public CommandResult beginTravelTo(long settlementId) {
        return beginTravelTo(settlementId, Army.ArmyOrder.MOVE);
    }

    /**
     * Travel implementation shared by ordinary map clicks and army orders.
     * When the commanded army is with the party, installing the route keeps
     * that army in the same party token and records the order it will carry
     * out on arrival (for example BESIEGE instead of a plain MOVE).
     */
    private CommandResult beginTravelTo(long settlementId, Army.ArmyOrder accompanyingOrder) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        Settlement destination = world.geography.getSettlement(settlementId);
        if (destination == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");

        if (!playerState.isTraveling()) {
            if (settlementId == playerState.currentSettlementId) return CommandResult.accepted();
            java.util.List<Long> route = findRouteSettlementIds(
                    playerState.currentSettlementId, settlementId);
            if (route.size() < 2) {
                return CommandResult.rejected("NO_ROUTE", "No open road reaches that settlement");
            }
            installTravelRoute(route, 1, 0.0, settlementId, minute, null, accompanyingOrder);
        } else {
            Long previousDestination = playerState.travelDestinationSettlementId;
            if (previousDestination != null && previousDestination.longValue() == settlementId) {
                if (accompanyingOrder != null && accompanyingOrder != Army.ArmyOrder.MOVE) {
                    orderAccompanyingArmy(settlementId, minute, previousDestination, accompanyingOrder);
                    refreshSnapshot();
                }
                return CommandResult.accepted();
            }
            int index = playerState.travelRouteIndex;
            long behind = playerState.travelRouteSettlementIds.get(index - 1);
            long ahead = playerState.travelRouteSettlementIds.get(index);
            double progress = playerState.travelSegmentProgress;
            if (settlementId == ahead) {
                java.util.List<Long> shortened = new ArrayList<>(
                        playerState.travelRouteSettlementIds.subList(0, index + 1));
                installTravelRoute(shortened, index, progress, settlementId, minute,
                        previousDestination, accompanyingOrder);
            } else {
                java.util.List<Long> fromBehind = settlementId == behind
                        ? java.util.List.of(behind) : findRouteSettlementIds(behind, settlementId);
                java.util.List<Long> fromAhead = findRouteSettlementIds(ahead, settlementId);
                if (fromBehind.isEmpty() && fromAhead.size() < 2) {
                    return CommandResult.rejected("NO_ROUTE", "No open road reaches that settlement");
                }
                double segmentLength = settlementDistance(behind, ahead);
                double backCost = fromBehind.isEmpty() ? Double.POSITIVE_INFINITY
                        : progress * segmentLength + routeDistance(fromBehind);
                double forwardCost = fromAhead.size() < 2 ? Double.POSITIVE_INFINITY
                        : (1.0 - progress) * segmentLength + routeDistance(fromAhead);
                if (backCost <= forwardCost) {
                    java.util.List<Long> route = new ArrayList<>();
                    route.add(ahead);
                    route.addAll(fromBehind);
                    installTravelRoute(route, 1, 1.0 - progress, settlementId, minute,
                            previousDestination, accompanyingOrder);
                } else {
                    java.util.List<Long> route = new ArrayList<>();
                    route.add(behind);
                    route.addAll(fromAhead);
                    installTravelRoute(route, 1, progress, settlementId, minute,
                            previousDestination, accompanyingOrder);
                }
            }
        }
        refreshSnapshot();
        return new CommandResult(true, "TRAVEL_STARTED", "Riding to " + destination.name + ".");
    }

    /** Turns the party back toward the last settlement behind it on the current road. */
    public CommandResult cancelTravel() {
        if (!playerState.isTraveling()) return CommandResult.accepted();
        long behind = playerState.travelRouteSettlementIds.get(playerState.travelRouteIndex - 1);
        return beginTravelTo(behind);
    }

    /** Advances the player party along its stored road route by elapsed world time. */
    private void advancePlayerTravel(long currentMinute) {
        if (!playerState.isTraveling()) {
            playerState.travelLastMinute = currentMinute;
            return;
        }
        if (playerState.travelLastMinute == Long.MIN_VALUE) {
            playerState.travelLastMinute = currentMinute;
            updateTravelPosition();
            return;
        }
        long elapsedMinutes = currentMinute - playerState.travelLastMinute;
        playerState.travelLastMinute = currentMinute;
        if (elapsedMinutes <= 0) {
            updateTravelPosition();
            return;
        }
        double remaining = Math.max(1.0, playerState.travelSpeedPerHour) * elapsedMinutes / 60.0;
        while (remaining > 0.0 && playerState.isTraveling()) {
            int index = playerState.travelRouteIndex;
            long fromId = playerState.travelRouteSettlementIds.get(index - 1);
            long toId = playerState.travelRouteSettlementIds.get(index);
            Settlement from = world.geography.getSettlement(fromId);
            Settlement to = world.geography.getSettlement(toId);
            if (from == null || to == null || from.position == null || to.position == null) {
                arrivePlayerAtSettlement(playerState.currentSettlementId);
                break;
            }
            double segmentLength = Math.max(1.0, Math.hypot(
                    to.position.x - from.position.x, to.position.y - from.position.y));
            double distanceToEnd = (1.0 - playerState.travelSegmentProgress) * segmentLength;
            if (remaining + 1.0e-9 < distanceToEnd) {
                playerState.travelSegmentProgress += remaining / segmentLength;
                remaining = 0.0;
            } else {
                remaining -= distanceToEnd;
                if (index >= playerState.travelRouteSettlementIds.size() - 1) {
                    arrivePlayerAtSettlement(toId);
                    break;
                }
                playerState.travelRouteIndex++;
                playerState.travelSegmentProgress = 0.0;
            }
        }
        if (playerState.isTraveling()) updateTravelPosition();
    }

    private void installTravelRoute(java.util.List<Long> route, int index, double progress,
            long destinationId, long minute, Long previousDestination,
            Army.ArmyOrder accompanyingOrder) {
        playerState.traveling = true;
        playerState.travelDestinationSettlementId = destinationId;
        playerState.travelRouteSettlementIds.clear();
        playerState.travelRouteSettlementIds.addAll(route);
        playerState.travelRouteIndex = Math.max(1, Math.min(index, route.size() - 1));
        playerState.travelSegmentProgress = Math.max(0.0, Math.min(1.0, progress));
        playerState.travelSpeedPerHour = currentPartySpeedPerHour(previousDestination);
        playerState.travelLastMinute = minute;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) {
            party.state = WorldParty.PartyState.TRAVELING;
            party.currentSettlementId = null;
            party.destinationSettlementId = destinationId;
            if (party.position == null && !route.isEmpty()) {
                Settlement start = world.geography.getSettlement(route.get(0));
                if (start != null && start.position != null) {
                    party.position = new WorldPosition(start.position.x, start.position.y);
                }
            }
        }
        updateTravelPosition();
        orderAccompanyingArmy(destinationId, minute, previousDestination, accompanyingOrder);
    }

    private void updateTravelPosition() {
        if (!playerState.isTraveling()) return;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return;
        int index = playerState.travelRouteIndex;
        Settlement from = world.geography.getSettlement(
                playerState.travelRouteSettlementIds.get(index - 1));
        Settlement to = world.geography.getSettlement(
                playerState.travelRouteSettlementIds.get(index));
        if (from == null || to == null || from.position == null || to.position == null) return;
        double progress = Math.max(0.0, Math.min(1.0, playerState.travelSegmentProgress));
        party.position = new WorldPosition(
                from.position.x + (to.position.x - from.position.x) * progress,
                from.position.y + (to.position.y - from.position.y) * progress);
        Army army = commandedArmy();
        if (isCommandedArmyWithPlayer(army, null)) {
            syncCommandedArmyWithPlayerParty(army);
        }
    }

    private java.util.List<Long> findRouteSettlementIds(long fromId, long toId) {
        if (fromId == toId) return java.util.List.of(fromId);
        java.util.List<Road> roads = world.geography.getRouteGraph().findShortestRoute(fromId, toId);
        if (roads.isEmpty()) return java.util.List.of();
        java.util.List<Long> route = new ArrayList<>();
        route.add(fromId);
        long current = fromId;
        for (Road road : roads) {
            long next;
            if (road.fromSettlementId == current) next = road.toSettlementId;
            else if (road.toSettlementId == current) next = road.fromSettlementId;
            else return java.util.List.of();
            route.add(next);
            current = next;
        }
        return current == toId ? route : java.util.List.of();
    }

    private double routeDistance(java.util.List<Long> route) {
        double total = 0.0;
        for (int i = 1; i < route.size(); i++) total += settlementDistance(route.get(i - 1), route.get(i));
        return total;
    }

    private double settlementDistance(long firstId, long secondId) {
        Settlement first = world.geography.getSettlement(firstId);
        Settlement second = world.geography.getSettlement(secondId);
        if (first == null || second == null || first.position == null || second.position == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(second.position.x - first.position.x,
                second.position.y - first.position.y);
    }

    private double currentPartySpeedPerHour(Long previousDestination) {
        WorldParty party = world.parties.get(playerState.partyId);
        int partySize = party == null ? 1 : Math.max(1, party.memberPersonIds.size());
        double loadRatio = playerState.cargoCapacity <= 0 ? 0.0
                : Math.max(0.0, Math.min(1.0,
                        playerState.cargo.totalQuantity() / (double) playerState.cargoCapacity));
        double speed = 10.0 * (1.0 - 0.15 * loadRatio)
                / (1.0 + Math.max(0, partySize - 5) * 0.02);
        Army army = commandedArmy();
        if (isCommandedArmyWithPlayer(army, previousDestination)) {
            speed = Math.min(speed, army.speedPerHour);
        }
        return Math.max(4.0, Math.min(12.0, speed));
    }

    private void orderAccompanyingArmy(long destinationId, long minute, Long previousDestination,
            Army.ArmyOrder accompanyingOrder) {
        Army army = commandedArmy();
        if (!isCommandedArmyWithPlayer(army, previousDestination)) return;
        army.order = accompanyingOrder == null ? Army.ArmyOrder.MOVE : accompanyingOrder;
        army.destinationSettlementId = destinationId;
        // The army does not receive a second, independently simulated road
        // route any more: it is part of the player party token and is moved
        // by syncCommandedArmyWithPlayerParty below.
        syncCommandedArmyWithPlayerParty(army);
    }

    /** The army currently commanded by the adventurer, clearing a stale reference if needed. */
    private Army commandedArmy() {
        if (playerState.commandedArmyId == null) return null;
        Army army = world.armies.get(playerState.commandedArmyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            if (army == null) playerState.commandedArmyId = null;
            return army;
        }
        return army;
    }

    /**
     * Whether the commanded army is travelling as part of the adventurer's
     * party rather than as a detached/remote command. A remote army with its
     * own road route only joins the party once it is actually co-located.
     */
    private boolean isCommandedArmyWithPlayer(Army army, Long previousDestination) {
        if (army == null || army.state == Army.ArmyState.DISBANDED
                || army.state == Army.ArmyState.ENGAGED) return false;
        WorldParty party = world.parties.get(playerState.partyId);
        boolean coLocated = party != null && party.position != null && army.position != null
                && Math.hypot(party.position.x - army.position.x,
                        party.position.y - army.position.y) <= 0.001;
        if (coLocated) return true;

        // An army marching on its own road route is detached even while its
        // last completed settlement (currentSettlementId) or destination
        // happens to match the player's. Treating those stale ids as proof of
        // attachment would teleport a remote/siege-joining army into the party
        // when the player retargets travel.
        if (!army.routeRoadIds.isEmpty()) return false;
        if (army.currentSettlementId == playerState.currentSettlementId) return true;
        if (previousDestination != null
                && previousDestination.equals(army.destinationSettlementId)) return true;
        Long playerDestination = playerState.isTraveling()
                ? playerState.travelDestinationSettlementId : playerState.currentSettlementId;
        return playerDestination != null
                && playerDestination.equals(army.destinationSettlementId);
    }

    /**
     * Keeps an accompanying commanded army in the player party: same map
     * position, same current/destination settlement, and no separate road
     * route for MilitarySystem to advance at a different speed.
     */
    private void syncCommandedArmyWithPlayerParty(Army army) {
        if (army == null || army.state == Army.ArmyState.DISBANDED
                || army.state == Army.ArmyState.ENGAGED) return;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null && party.position != null) {
            army.position = new WorldPosition(party.position.x, party.position.y);
        }
        army.routeRoadIds.clear();
        army.routeIndex = 0;
        army.roadProgress = 0.0;
        army.currentSettlementId = playerState.currentSettlementId;
        if (playerState.isTraveling()) {
            army.destinationSettlementId = playerState.travelDestinationSettlementId;
            if (army.state != Army.ArmyState.ROUTED) army.state = Army.ArmyState.TRAVELING;
            syncCommandedArmySoldierLocations(army, null);
        } else {
            army.destinationSettlementId = playerState.currentSettlementId;
            if (army.state != Army.ArmyState.ROUTED) army.state = Army.ArmyState.MUSTERED;
            syncCommandedArmySoldierLocations(army, playerState.currentSettlementId);
        }
    }

    private void syncCommandedArmySoldierLocations(Army army, Long settlementId) {
        for (Long regimentId : army.regimentIds) {
            world.military.Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person soldier = world.people.get(personId);
                if (soldier == null || !soldier.alive) continue;
                soldier.travelingPartyId = army.id;
                soldier.currentSettlementId = settlementId;
            }
        }
    }

    /** Snaps a lagging accompanying army back into the party before range/siege checks. */
    private void repairAccompanyingArmyPosition(Army army) {
        if (isCommandedArmyWithPlayer(army, null)) {
            syncCommandedArmyWithPlayerParty(army);
        }
    }

    /**
     * A self-funded company follows the adventurer's allegiance. Without this,
     * a company raised under one realm and then pledged to its enemy was still
     * compared against the old realm in battle/siege checks.
     */
    private void alignPlayerFundedCompanyWithPlayerRealm() {
        if (playerState == null) return;
        Realm realm = effectivePlayerRealm();
        Army army = commandedArmy();
        if (realm == null || army == null || !army.playerFunded) return;
        army.realmId = realm.id;
        for (Long regimentId : army.regimentIds) {
            world.military.Regiment regiment = world.regiments.get(regimentId);
            if (regiment != null) regiment.realmId = realm.id;
        }
    }

    /** Shared final-arrival bookkeeping for instant and continuous travel. */
    private void arrivePlayerAtSettlement(long settlementId) {
        Settlement destination = world.geography.getSettlement(settlementId);
        WorldParty party = world.parties.get(playerState.partyId);
        Army accompanyingArmy = commandedArmy();
        boolean armyWithPlayer = isCommandedArmyWithPlayer(accompanyingArmy, null);
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
            if (destination != null && destination.position != null) {
                party.position = new WorldPosition(destination.position.x, destination.position.y);
            }
        }
        playerState.clearTravel();
        if (armyWithPlayer) {
            syncCommandedArmyWithPlayerParty(accompanyingArmy);
            if (accompanyingArmy.order == Army.ArmyOrder.RETURN_HOME
                    && accompanyingArmy.homeSettlementId != null
                    && accompanyingArmy.homeSettlementId.longValue() == settlementId) {
                simulation.getMilitarySystem().demobilize(
                        accompanyingArmy.id, clock.getWorldMinute());
                playerState.commandedArmyId = null;
            }
        }
        contractSystem.onPlayerArrived(playerState, clock.getWorldMinute());
    }

    /** Settlement actions are unavailable while the party is between settlements. */
    private CommandResult travelBlock() {
        if (!playerState.isTraveling()) return null;
        Settlement destination = playerState.travelDestinationSettlementId == null ? null
                : world.geography.getSettlement(playerState.travelDestinationSettlementId);
        return CommandResult.rejected("TRAVELING", "You are on the road to "
                + (destination == null ? "your destination" : destination.name) + ".");
    }

    public CommandResult recruitCompanion(long personId) {
        Person companion = world.people.get(personId);
        WorldParty party = world.parties.get(playerState.partyId);
        if (companion == null || party == null) return CommandResult.rejected("INVALID_PERSON", "Person not found");
        CommandResult blocked = freeActionBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        if (!companion.alive || companion.currentSettlementId == null
                || companion.currentSettlementId != playerState.currentSettlementId) {
            return CommandResult.rejected("NOT_PRESENT", "Companion must be alive and present");
        }
        if (party.memberPersonIds.contains(personId)) {
            return CommandResult.rejected("ALREADY_RECRUITED", "This person is already in your party");
        }
        long minute = clock.getWorldMinute();
        if (companion.isChild(minute) || companion.type == Person.PersonType.CHILD) {
            return CommandResult.rejected("CHILD", "Children cannot be recruited into an adventuring party");
        }
        if (companion.regimentId != null || companion.type == Person.PersonType.SOLDIER) {
            return CommandResult.rejected("MILITARY_DUTY", "This person is serving in an army");
        }
        if (companion.travelingPartyId != null && companion.travelingPartyId != party.id) {
            return CommandResult.rejected("OTHER_PARTY", "This person already belongs to another traveling party");
        }
        if (companion.type == Person.PersonType.NOBLE) {
            return CommandResult.rejected("NOBLE_DUTY", "This noble will not abandon their obligations");
        }
        if (companion.employerId != null) {
            world.economy.Workplace workplace = world.workplaces.get(companion.employerId);
            if (workplace != null) workplace.removeWorker(companion.id);
            companion.employerId = null;
        }
        party.memberPersonIds.add(personId);
        companion.travelingPartyId = party.id;
        companion.currentActivity = PersonActivity.IDLE;
        world.indexes.rebuild(world, clock.getWorldMinute());
        refreshSnapshot();
        return new CommandResult(true, "RECRUITED",
                companion.givenName + " has joined your party");
    }

    /** Returns dialogue derived from the person's current schedule, needs, and personality. */
    public CommandResult talkToPerson(long personId) {
        Person person = world.people.get(personId);
        if (!isPersonPresent(person)) {
            return CommandResult.rejected("NOT_PRESENT", "That person is no longer nearby");
        }
        String concern = mostPressingConcern(person);
        String manner = person.personality.sociability >= 65.0 ? "warmly"
                : person.personality.patience < 35.0 ? "briefly" : "carefully";
        String activity = person.currentActivity.toString().toLowerCase().replace('_', ' ');
        person.needs.socialBelonging = Math.min(1.0, person.needs.socialBelonging + 0.01);
        refreshSnapshot();
        return new CommandResult(true, "CONVERSATION",
                person.givenName + " speaks " + manner + " about " + concern
                        + " before returning to " + activity + ".");
    }

    /** Transfers one unit of player grain to a nearby person's household. */
    public CommandResult giveFoodToPerson(long personId) {
        Person person = world.people.get(personId);
        if (!isPersonPresent(person)) {
            return CommandResult.rejected("NOT_PRESENT", "That person is no longer nearby");
        }
        if (!playerState.cargo.remove(GoodType.GRAIN, 1)) {
            return CommandResult.rejected("NO_GRAIN", "You have no grain in your cargo");
        }
        Household household = person.householdId == null ? null : world.households.get(person.householdId);
        if (household != null) household.inventory.add(GoodType.GRAIN, 1);
        person.needs.foodSecurity = Math.min(1.0, person.needs.foodSecurity + 0.15);
        person.needs.socialBelonging = Math.min(1.0, person.needs.socialBelonging + 0.05);
        playerState.changeSettlementReputation(playerState.currentSettlementId, 1);
        refreshSnapshot();
        return new CommandResult(true, "GIFT_ACCEPTED",
                person.givenName + " accepts the food. Local reputation increased.");
    }

    private boolean isPersonPresent(Person person) {
        return person != null && person.alive && person.currentSettlementId != null
                && person.currentSettlementId == playerState.currentSettlementId;
    }

    private String mostPressingConcern(Person person) {
        double lowest = person.needs.foodSecurity;
        String concern = "the price of food";
        if (person.needs.safety < lowest) { lowest = person.needs.safety; concern = "safety on the roads"; }
        if (person.needs.health < lowest) { lowest = person.needs.health; concern = "their health"; }
        if (person.needs.wealthSecurity < lowest) { lowest = person.needs.wealthSecurity; concern = "finding steady work"; }
        if (person.needs.politicalSatisfaction < lowest) concern = "the local rulers";
        return concern;
    }

    /** Gives supplies to an army only when it is physically at the player's settlement. */
    public CommandResult supplyArmy(long armyId, int grain) {
        Army army = world.armies.get(armyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "That army is no longer active");
        }
        if (army.currentSettlementId != playerState.currentSettlementId
                || army.state == Army.ArmyState.TRAVELING) {
            return CommandResult.rejected("NOT_PRESENT", "The army must be at your current settlement");
        }
        if (grain <= 0 || playerState.cargo.getQuantity(GoodType.GRAIN) < grain) {
            return CommandResult.rejected("NO_GRAIN", "You do not have enough grain in your cargo");
        }
        playerState.cargo.remove(GoodType.GRAIN, grain);
        army.supplies.add(GoodType.GRAIN, grain);
        if (!army.playerCommanded) playerState.realmReputation.merge(army.realmId, 1, Integer::sum);
        refreshSnapshot();
        return new CommandResult(true, "ARMY_SUPPLIED", army.playerCommanded
                ? "Your quartermaster takes " + grain + " grain. The company is fed."
                : "The quartermaster accepts " + grain + " grain. Realm reputation increased.");
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
        alignPlayerFundedCompanyWithPlayerRealm();
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
        long minute = clock.getWorldMinute();
        Title crown = world.titles.get(realm.rulerTitleId);

        // The crown is the player's one title. Vacating any prior title and
        // installing the crown holder are both essential: PoliticsSystem
        // treats the living crown holder as the realm's ruler every month,
        // so changing Realm.rulerPersonId alone would silently revert.
        for (Title held : new ArrayList<>(world.titles.values())) {
            if (held.holderPersonId == null || held.holderPersonId.longValue() != player.id) continue;
            if (crown != null && held.id == crown.id) continue;
            held.vacateHolder(minute);
            playerState.titleIds.remove(Long.valueOf(held.id));
            for (Realm other : world.realms.values()) {
                if (other.id != realm.id && other.rulerTitleId == held.id
                        && other.rulerPersonId != null && other.rulerPersonId == player.id) {
                    other.rulerPersonId = null;
                }
            }
        }
        if (crown != null) {
            if (crown.holderPersonId == null || crown.holderPersonId.longValue() != player.id) {
                crown.installHolder(player.id, minute, "APPOINTED_RULER");
            }
            playerState.titleIds.clear();
            playerState.titleIds.add(crown.id);
        } else {
            playerState.titleIds.clear();
        }
        for (Office office : world.offices.values()) {
            boolean targetRulerOffice = office.realmId == realm.id
                    && office.type == Office.OfficeType.RULER;
            if (!targetRulerOffice && office.holderPersonId != null
                    && office.holderPersonId.longValue() == player.id) {
                office.holderPersonId = null;
            }
        }
        player.officeId = null;
        for (Office office : world.offices.values()) {
            if (office.realmId != realm.id || office.type != Office.OfficeType.RULER) continue;
            Person previous = office.holderPersonId == null ? null : world.people.get(office.holderPersonId);
            if (previous != null && previous.id != player.id && previous.officeId != null
                    && previous.officeId.longValue() == office.id) previous.officeId = null;
            office.holderPersonId = player.id;
            office.appointedMinute = minute;
            player.officeId = office.id;
        }

        realm.rulerPersonId = player.id;
        player.type = Person.PersonType.NOBLE;
        playerState.affiliatedRealmId = realmId;
        playerState.kingdomRole = PlayerCampaignState.KingdomRole.KING;
        alignPlayerFundedCompanyWithPlayerRealm();
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

    /** How badly a lost campaign battle hit the adventurer. Rolled from the margin of defeat. */
    public enum DefeatSeverity { WOUNDED, CAPTURED, ROUTED }

    /** Severity of the defeat that was just applied, so the UI can describe it. */
    private DefeatSeverity lastDefeatSeverity = DefeatSeverity.WOUNDED;

    public DefeatSeverity getLastDefeatSeverity() {
        return lastDefeatSeverity;
    }

    /**
     * Applies the consequences of losing a campaign battle.
     *
     * <p>{@code defeatMargin} runs from 0 (you nearly held the field) to 1 (total loss) and
     * shifts a single seeded roll across three outcomes: a wounding that costs coin and cargo,
     * capture for ransom, or a rout that destroys a commanded army and can kill companions.
     */
    public CommandResult resolvePlayerDefeat(Long captorRealmId, double defeatMargin) {
        return resolvePlayerDefeat(captorRealmId, defeatMargin, true);
    }

    /**
     * Personal defeat consequences. When the player fought solo while a
     * commanded army was elsewhere, that uninvolved army is not routed or
     * destroyed merely because the adventurer lost a separate fight.
     */
    public CommandResult resolvePlayerDefeat(Long captorRealmId, double defeatMargin,
            boolean commandedArmyInvolved) {
        long minute = clock.getWorldMinute();
        double margin = Math.max(0.0, Math.min(1.0, defeatMargin));
        SeededRandom random = simulation.getContext().getRandom("MILITARY");
        double routedChance = 0.10 + margin * 0.40;
        double capturedChance = 0.20 + margin * 0.35;
        double roll = random.nextDouble();
        DefeatSeverity severity = roll < routedChance ? DefeatSeverity.ROUTED
                : roll < routedChance + capturedChance ? DefeatSeverity.CAPTURED
                        : DefeatSeverity.WOUNDED;
        lastDefeatSeverity = severity;

        Person player = world.people.get(playerState.personId);
        WorldParty party = world.parties.get(playerState.partyId);
        if (player == null || party == null) {
            return CommandResult.rejected("INVALID_STATE", "The adventurer cannot be resolved");
        }
        if (playerState.isTraveling()) {
            // A defeat ends the journey at the last settled refuge rather than
            // leaving a wounded or captive party marching on its own.
            playerState.clearTravel();
            Settlement refuge = world.geography.getSettlement(playerState.currentSettlementId);
            party.state = WorldParty.PartyState.AT_SETTLEMENT;
            party.currentSettlementId = playerState.currentSettlementId;
            party.destinationSettlementId = null;
            if (refuge != null && refuge.position != null) {
                party.position = new WorldPosition(refuge.position.x, refuge.position.y);
            }
        }

        StringBuilder message = new StringBuilder();
        long lostCoins = takeCoins(party, severity == DefeatSeverity.WOUNDED ? 0.15
                : severity == DefeatSeverity.CAPTURED ? 0.40 : 0.60);
        int lostGoods = takeCargo(party, severity == DefeatSeverity.WOUNDED ? 0.10
                : severity == DefeatSeverity.CAPTURED ? 0.30 : 0.50);
        message.append("Defeat: you lose ").append(lostCoins).append("c");
        if (lostGoods > 0) message.append(" and ").append(lostGoods).append(" cargo");
        message.append(". ");

        woundPlayer(player, severity == DefeatSeverity.ROUTED ? 25.0 : 45.0);
        boolean hadArmy = commandedArmyInvolved && playerState.commandedArmyId != null;
        if (commandedArmyInvolved) damageCommandedArmy(minute, severity);

        if (severity == DefeatSeverity.ROUTED) {
            int deaths = scatterCompanions(party, minute, 0.25 + margin * 0.35);
            if (deaths > 0) message.append(deaths).append(deaths == 1 ? " companion falls" : " companions fall").append(". ");
            if (hadArmy) message.append("Your army is destroyed. ");
        }

        if (severity == DefeatSeverity.CAPTURED) {
            int days = 4 + random.nextInt(10);
            long ransom = 150L + 75L * Math.max(0, party.memberPersonIds.size() - 1)
                    + Math.max(0L, party.money.copperCoins / 8L);
            playerState.captiveUntilMinute = minute + days * WorldConfig.MINUTES_PER_DAY;
            playerState.captorRealmId = captorRealmId;
            playerState.ransomCoins = ransom;
            player.capturedByRealmId = captorRealmId;
            Realm captor = captorRealmId == null ? null : world.realms.get(captorRealmId);
            message.append("You are taken prisoner by ")
                    .append(captor == null ? "the enemy" : captor.name).append(" for ")
                    .append(days).append(" days unless you pay ").append(ransom).append("c.");
            publish("PLAYER_CAPTURED", minute);
        } else {
            message.append(severity == DefeatSeverity.ROUTED
                    ? "You escape the field wounded and alone."
                    : "You are left wounded on the field but keep your freedom.");
        }
        refreshSnapshot();
        return new CommandResult(true, "DEFEAT_" + severity.name(), message.toString());
    }

    /** Pays the ransom and ends captivity immediately. */
    public CommandResult payRansom() {
        long minute = clock.getWorldMinute();
        if (!playerState.isCaptiveAt(minute)) {
            return CommandResult.rejected("NOT_CAPTIVE", "You are not a prisoner");
        }
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return CommandResult.rejected("INVALID_STATE", "Your party is unavailable");
        if (party.money.copperCoins < playerState.ransomCoins) {
            return CommandResult.rejected("INSUFFICIENT_FUNDS",
                    "The ransom is " + playerState.ransomCoins + "c and you have "
                            + party.money.copperCoins + "c");
        }
        party.money.subtract(playerState.ransomCoins);
        Settlement settlement = world.geography.getSettlement(playerState.currentSettlementId);
        if (settlement != null) settlement.treasury.add(playerState.ransomCoins);
        releaseFromCaptivity(minute);
        refreshSnapshot();
        return new CommandResult(true, "RANSOM_PAID", "You pay the ransom and walk free.");
    }

    /** Releases the prisoner once the captivity term expires. Called every update. */
    private void processCaptivity(long minute) {
        if (playerState.captiveUntilMinute == null) return;
        if (minute < playerState.captiveUntilMinute) return;
        releaseFromCaptivity(minute);
        publish("PLAYER_RELEASED", minute);
    }

    private void releaseFromCaptivity(long minute) {
        playerState.captiveUntilMinute = null;
        playerState.captorRealmId = null;
        playerState.ransomCoins = 0L;
        Person player = world.people.get(playerState.personId);
        if (player != null) player.capturedByRealmId = null;
    }

    /** Rejection used to gate every free-action verb while the adventurer is a prisoner. */
    private CommandResult captivityBlock(long minute) {
        if (!playerState.isCaptiveAt(minute)) return null;
        return CommandResult.rejected("CAPTIVE", "You are a prisoner for another "
                + playerState.captiveDaysRemaining(minute) + " day(s) — pay the ransom of "
                + playerState.ransomCoins + "c to go free");
    }

    /** Captivity takes precedence; otherwise ordinary verbs are unavailable on the road. */
    private CommandResult freeActionBlock(long minute) {
        CommandResult blocked = captivityBlock(minute);
        return blocked != null ? blocked : travelBlock();
    }

    /** Rests at the settlement inn: costs coin, advances the clock, and heals wounds. */
    public CommandResult restAtInn(int hours) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        Person player = world.people.get(playerState.personId);
        Household household = world.households.get(playerState.householdId);
        if (player == null || household == null) {
            return CommandResult.rejected("INVALID_STATE", "There is nowhere for you to rest");
        }
        if (world.geography.getSettlement(playerState.currentSettlementId) == null) {
            return CommandResult.rejected("NOT_PRESENT", "You must be inside a settlement to rest");
        }
        int nights = Math.max(1, hours / 8);
        long cost = 5L * nights;
        if (household.account.copperCoins < cost) {
            return CommandResult.rejected("CANNOT_AFFORD", "A room costs " + cost + "c and you have "
                    + household.account.copperCoins + "c");
        }
        household.account.subtract(cost);
        Settlement settlement = world.geography.getSettlement(playerState.currentSettlementId);
        if (settlement != null) settlement.treasury.add(cost);

        boolean paused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes((long) nights * 8L * 60L);
        clock.setPaused(paused);

        player.health.healthLevel = Math.min(100.0, player.health.healthLevel + 30.0 * nights);
        player.health.clamp();
        if (player.health.healthLevel >= 70.0) {
            player.health.wounded = false;
            player.health.injured = false;
            player.health.injurySeverity = Math.min(player.health.injurySeverity, 10.0);
        }
        refreshSnapshot();
        return new CommandResult(true, "RESTED", "You rest " + (nights * 8) + " hours for " + cost
                + "c. Health " + Math.round(player.health.healthLevel) + "%.");
    }

    /**
     * Presses the adventurer's claim to the throne of a realm they serve as a lord.
     * Requires either a legal claim on the crown or a realm in crisis that trusts them.
     */
    public CommandResult seizeThrone(long realmId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        Realm realm = world.realms.get(realmId);
        Person player = world.people.get(playerState.personId);
        if (realm == null || player == null) {
            return CommandResult.rejected("INVALID_REALM", "Kingdom or adventurer not found");
        }
        if (realm.rulerPersonId != null && realm.rulerPersonId == playerState.personId) {
            return CommandResult.rejected("ALREADY_RULER", "You already rule this kingdom");
        }
        if (playerState.affiliatedRealmId == null || playerState.affiliatedRealmId != realmId) {
            return CommandResult.rejected("NOT_A_MEMBER", "Pledge allegiance to this kingdom first");
        }
        if (playerState.kingdomRole != PlayerCampaignState.KingdomRole.LORD) {
            return CommandResult.rejected("NOT_A_LORD",
                    "Only a lord of the realm can press a claim to its throne");
        }
        world.politics.Title crown = world.titles.get(realm.rulerTitleId);
        boolean hasClaim = crown != null && world.claims.values().stream()
                .anyMatch(claim -> claim.titleId == crown.id
                        && claim.claimantPersonId == playerState.personId
                        && (claim.expiryMinute == null || claim.expiryMinute > minute));
        Person currentRuler = realm.rulerPersonId == null ? null : world.people.get(realm.rulerPersonId);
        boolean realmInCrisis = currentRuler == null || !currentRuler.alive || realm.stability < 25.0;
        int reputation = playerState.realmReputation.getOrDefault(realmId, 0);
        if (!hasClaim && !(realmInCrisis && reputation >= 25)) {
            return CommandResult.rejected("NO_BASIS", hasClaim ? ""
                    : "You need a claim on the crown, or a kingdom in crisis that trusts you"
                            + " (stability below 25 and 25+ realm reputation; you have " + reputation + ")");
        }

        Long deposedId = realm.rulerPersonId;
        CommandResult installed = appointPlayerAsKing(realmId);
        if (!installed.accepted) return installed;

        realm.stability = Math.max(0.0, realm.stability - 20.0);
        realm.legitimacy = Math.max(0.0, realm.legitimacy - (hasClaim ? 5.0 : 20.0));
        // A deposed ruler keeps a claim, so succession and factions can contest the new reign.
        if (deposedId != null && deposedId != playerState.personId && crown != null) {
            Person deposed = world.people.get(deposedId);
            if (deposed != null && deposed.alive) {
                world.politics.Claim counter = new world.politics.Claim(world.idGenerator.next(),
                        deposedId, crown.id, hasClaim ? 45.0 : 70.0,
                        Claim.ClaimSource.DISPUTED_SUCCESSION, minute);
                world.claims.put(counter.id, counter);
                crown.claimIds.add(counter.id);
            }
        }
        publish(hasClaim ? "THRONE_CLAIMED" : "THRONE_USURPED", minute);
        refreshSnapshot();
        return new CommandResult(true, "THRONE_TAKEN", player.givenName + " takes the throne of "
                + realm.name + (hasClaim ? " by right of claim." : " by force. Legitimacy suffers."));
    }

    private void woundPlayer(Person player, double resultingHealth) {
        player.health.healthLevel = Math.min(player.health.healthLevel, resultingHealth);
        player.health.clamp();
        player.health.wounded = true;
        player.health.injured = true;
        player.health.injurySeverity = Math.max(player.health.injurySeverity,
                100.0 - player.health.healthLevel);
    }

    private long takeCoins(WorldParty party, double fraction) {
        long available = Math.max(0L, party.money.copperCoins);
        long lost = Math.min(available, Math.round(available * fraction));
        if (lost > 0) party.money.subtract(lost);
        return lost;
    }

    private int takeCargo(WorldParty party, double fraction) {
        int total = 0;
        for (GoodType good : GoodType.values()) {
            int held = party.cargo.getQuantity(good);
            int lost = Math.min(held, (int) Math.round(held * fraction));
            if (lost <= 0) continue;
            if (party.cargo.remove(good, lost)) total += lost;
        }
        return total;
    }

    private void damageCommandedArmy(long minute, DefeatSeverity severity) {
        if (playerState.commandedArmyId == null) return;
        Army army = world.armies.get(playerState.commandedArmyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return;
        }
        if (severity == DefeatSeverity.ROUTED) {
            simulation.getMilitarySystem().demobilize(army.id, minute);
            playerState.commandedArmyId = null;
            return;
        }
        army.morale = Math.max(0.0, army.morale - (severity == DefeatSeverity.CAPTURED ? 45.0 : 20.0));
        army.fatigue = Math.min(100.0, army.fatigue + (severity == DefeatSeverity.CAPTURED ? 40.0 : 25.0));
        boolean home = army.homeSettlementId == null
                || army.homeSettlementId.longValue() == army.currentSettlementId;
        if (isCommandedArmyWithPlayer(army, null)) {
            // resolvePlayerDefeat has already snapped a travelling party back
            // to its refuge. The surviving troops who were riding in that party
            // retreat to the same refuge instead of being left at the battle
            // position or becoming a second token marching home on its own.
            if (home) army.state = Army.ArmyState.MUSTERED;
            army.order = Army.ArmyOrder.DEFEND;
            syncCommandedArmyWithPlayerParty(army);
            return;
        }
        if (home) {
            army.state = Army.ArmyState.MUSTERED;
            army.order = Army.ArmyOrder.DEFEND;
            return;
        }
        // Limp home; MilitarySystem.arrive() demobilizes a RETURN_HOME army that reaches home.
        simulation.getMilitarySystem().issueMoveOrder(army.id, army.homeSettlementId,
                Army.ArmyOrder.RETURN_HOME, minute);
    }

    /** Kills a random share of companions after a rout and returns how many died. */
    private int scatterCompanions(WorldParty party, long minute, double deathChance) {
        SeededRandom random = simulation.getContext().getRandom("MILITARY");
        int deaths = 0;
        for (Long memberId : new ArrayList<>(party.memberPersonIds)) {
            if (memberId == playerState.personId) continue;
            Person companion = world.people.get(memberId);
            if (companion == null || !companion.alive) continue;
            if (random.nextDouble() >= deathChance) continue;
            companion.die(minute, Person.DeathCause.WAR);
            companion.regimentId = null;
            companion.travelingPartyId = null;
            party.memberPersonIds.remove(memberId);
            deaths++;
            publish("COMPANION_KILLED", minute);
        }
        return deaths;
    }

    private void publish(String type, long minute) {
        simulation.getContext().eventBus.publish(
                new world.event.WorldEvent(world.idGenerator.next(), minute, type));
    }

    // ===== Army command ==================================================

    /** Realm name lookup for player-facing messages. */
    private String realmNameOf(Long realmId) {
        if (realmId == null) return "no realm";
        Realm realm = world.realms.get(realmId);
        return realm == null ? "an unknown realm" : realm.name;
    }

    /**
     * Raises a company of your own, or adds to the one you already command. Paid from your
     * own purse at 35c a head plus one weapon each, so it needs no conscription law and no
     * realm treasury.
     */
    public CommandResult raiseCompany(int count, world.military.Regiment.RegimentType type) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return CommandResult.rejected("INVALID_STATE", "Your party is unavailable");
        Long settlementId = playerState.currentSettlementId;
        if (settlementId == null) return CommandResult.rejected("NOT_PRESENT", "Enter a settlement first");
        Army previous = playerState.commandedArmyId == null
                ? null : world.armies.get(playerState.commandedArmyId);
        CommandResult raised = simulation.getMilitarySystem().raisePersonalCompany(
                playerState.personId, settlementId, count, type, party.money, minute);
        if (!raised.accepted) return raised;
        Army army = simulation.getMilitarySystem().armyCommandedBy(playerState.personId);
        if (army == null) return CommandResult.rejected("INVALID_STATE", "The company could not be formed");
        if (previous != null && previous.id != army.id && previous.state != Army.ArmyState.DISBANDED) {
            // Fold the old company in so the player always commands exactly one army.
            for (Long regimentId : new ArrayList<>(previous.regimentIds)) {
                previous.regimentIds.remove(regimentId);
                army.regimentIds.add(regimentId);
            }
            for (GoodType good : GoodType.values()) {
                int quantity = previous.supplies.getQuantity(good);
                if (quantity > 0 && previous.supplies.remove(good, quantity)) army.supplies.add(good, quantity);
            }
            previous.playerCommanded = false;
            previous.playerFunded = false;
            previous.state = Army.ArmyState.DISBANDED;
        }
        playerState.commandedArmyId = army.id;
        alignPlayerFundedCompanyWithPlayerRealm();
        syncCommandedArmyWithPlayerParty(army);
        refreshSnapshot();
        return new CommandResult(true, raised.reasonCode, raised.message
                + " Your company: " + simulation.getMilitarySystem().strength(army) + " troops, "
                + Math.round(army.supplies.getQuantity(GoodType.GRAIN)) + " grain.");
    }

    /** A ruler may take personal command of one of their own realm's armies. */
    public CommandResult takeCommandOfArmy(long armyId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        Army army = world.armies.get(armyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "That army is not active");
        }
        Realm realm = effectivePlayerRealm();
        boolean isRuler = realm != null && realm.rulerPersonId != null
                && realm.rulerPersonId == playerState.personId;
        if (!isRuler) {
            return CommandResult.rejected("NOT_RULER",
                    "Only a ruler takes personal command of a realm army. Raise your own company instead.");
        }
        if (army.realmId != realm.id) {
            return CommandResult.rejected("FOREIGN_ARMY", "That army serves " + realmNameOf(army.realmId));
        }
        if (playerState.commandedArmyId != null && playerState.commandedArmyId != armyId) {
            Army current = world.armies.get(playerState.commandedArmyId);
            if (current != null && current.state != Army.ArmyState.DISBANDED) {
                return CommandResult.rejected("ALREADY_COMMANDING",
                        current.playerFunded
                                ? "Release or disband your own company before taking command of a realm army"
                                : "You already command another army");
            }
        }
        army.commanderPersonId = playerState.personId;
        army.playerCommanded = true;
        // A realm army stays on the realm's payroll; only a private company costs you.
        army.playerFunded = false;
        playerState.commandedArmyId = army.id;
        repairAccompanyingArmyPosition(army);
        publish("PLAYER_TOOK_COMMAND", minute);
        refreshSnapshot();
        return new CommandResult(true, "COMMAND_TAKEN", "You take command of " + realm.name
                + " army #" + army.id + " (" + simulation.getMilitarySystem().strength(army) + " troops).");
    }

    /** Gives up command: a private company is paid off, a realm army returns to its officers. */
    public CommandResult relinquishCommand() {
        long minute = clock.getWorldMinute();
        if (playerState.commandedArmyId == null) {
            return CommandResult.rejected("NO_ARMY", "You command no army");
        }
        Army army = world.armies.get(playerState.commandedArmyId);
        playerState.commandedArmyId = null;
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            refreshSnapshot();
            return new CommandResult(true, "COMMAND_RELEASED", "You no longer command an army.");
        }
        boolean wasFunded = army.playerFunded;
        army.playerCommanded = false;
        army.playerFunded = false;
        if (wasFunded) {
            simulation.getMilitarySystem().demobilize(army.id, minute);
            refreshSnapshot();
            return new CommandResult(true, "COMPANY_DISBANDED",
                    "Your company is paid off and the men go home.");
        }
        if (!army.regimentIds.isEmpty()) {
            world.military.Regiment regiment = world.regiments.get(army.regimentIds.get(0));
            if (regiment != null && regiment.officerPersonId != null) {
                army.commanderPersonId = regiment.officerPersonId;
            }
        }
        refreshSnapshot();
        return new CommandResult(true, "COMMAND_RELEASED",
                "Command returns to the army's own officers.");
    }

    /**
     * Whether the adventurer personally has health left to fight. Being
     * wounded, injured, or sick does not block battle: the only health cutoff
     * is zero (or death). Captivity is reported separately because a prisoner
     * cannot reach the field even while technically alive.
     */
    public CommandResult playerCanFight() {
        long minute = clock.getWorldMinute();
        Person player = world.people.get(playerState.personId);
        if (player == null || !player.alive || player.health == null
                || !(player.health.healthLevel > 0.0)) {
            return CommandResult.rejected("PLAYER_INCAPACITATED",
                    "You have no health left to fight. Recover before leading another attack or siege.");
        }
        CommandResult captive = captivityBlock(minute);
        return captive == null ? CommandResult.accepted() : captive;
    }

    /** Health-only check: captivity can still prevent reaching the field. */
    public boolean playerHasHealthToFight() {
        Person player = world.people.get(playerState.personId);
        return player != null && player.alive && player.health != null
                && player.health.healthLevel > 0.0;
    }

    /** Current strategic health as a whole percentage, for battle/siege UI. */
    public int playerHealthPercent() {
        Person player = world.people.get(playerState.personId);
        if (player == null || !player.alive || player.health == null
                || !(player.health.healthLevel > 0.0)) return 0;
        // Any positive fractional health is still enough to fight, so never
        // round a living, fight-capable player down to a misleading 0%.
        return Math.max(1, Math.min(100, (int) Math.round(player.health.healthLevel)));
    }

    /** Orders the army you command to a settlement. BESIEGE and RAID are validated first. */
    public CommandResult issueArmyOrder(long destinationSettlementId, Army.ArmyOrder order) {
        if (order == Army.ArmyOrder.BESIEGE) return startOrJoinSiege(destinationSettlementId);
        long minute = clock.getWorldMinute();
        if (order == Army.ArmyOrder.RAID) {
            CommandResult health = playerCanFight();
            if (!health.accepted) return health;
        }
        if (playerState.commandedArmyId == null) {
            return CommandResult.rejected("NO_ARMY", "You command no army. Raise a company first.");
        }
        Army army = world.armies.get(playerState.commandedArmyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return CommandResult.rejected("INVALID_ARMY", "Your army no longer exists");
        }
        Settlement destination = world.geography.getSettlement(destinationSettlementId);
        if (destination == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        alignPlayerFundedCompanyWithPlayerRealm();
        repairAccompanyingArmyPosition(army);
        if (order == Army.ArmyOrder.RAID && destination.controllerRealmId != null
                && destination.controllerRealmId == army.realmId) {
            return CommandResult.rejected("FRIENDLY_RAID", "You cannot raid your own realm's settlement");
        }
        if (isCommandedArmyWithPlayer(army, null)) {
            if (!playerState.isTraveling()
                    && destinationSettlementId == playerState.currentSettlementId) {
                army.order = order;
                army.destinationSettlementId = destinationSettlementId;
                syncCommandedArmyWithPlayerParty(army);
                if (order == Army.ArmyOrder.RETURN_HOME && army.homeSettlementId != null
                        && army.homeSettlementId.longValue() == destinationSettlementId) {
                    simulation.getMilitarySystem().demobilize(army.id, minute);
                    playerState.commandedArmyId = null;
                }
            } else {
                CommandResult travel = beginTravelTo(destinationSettlementId, order);
                if (!travel.accepted) return travel;
            }
            refreshSnapshot();
            return new CommandResult(true, "ORDER_ISSUED", describeOrder(order, destination));
        }
        CommandResult issued = simulation.getMilitarySystem()
                .issueMoveOrder(army.id, destinationSettlementId, order, minute);
        if (!issued.accepted) return issued;
        refreshSnapshot();
        return new CommandResult(true, "ORDER_ISSUED", describeOrder(order, destination));
    }

    /**
     * Starts a siege with the army you command, or joins the active siege at
     * that settlement when your army's realm is on the besieging side of an
     * active war. If the army is elsewhere it receives a BESIEGE order and is
     * registered as joining immediately; it contributes once it arrives.
     */
    public CommandResult startOrJoinSiege(long settlementId) {
        return startOrJoinSiege(settlementId, null);
    }

    /** Joins one specific active siege, as selected from the WAR tab. */
    public CommandResult joinSiege(long siegeId) {
        world.military.Siege siege = world.sieges.get(siegeId);
        if (siege == null || siege.state != Siege.SiegeState.ACTIVE) {
            return CommandResult.rejected("INVALID_SIEGE", "That siege is no longer active");
        }
        return startOrJoinSiege(siege.settlementId, siege);
    }

    private CommandResult startOrJoinSiege(long settlementId, world.military.Siege preferredSiege) {
        long minute = clock.getWorldMinute();
        CommandResult health = playerCanFight();
        if (!health.accepted) return health;
        if (playerState.commandedArmyId == null) {
            return CommandResult.rejected("NO_ARMY",
                    "You command no army. Raise a company first, then start or join the siege with it.");
        }
        Army army = world.armies.get(playerState.commandedArmyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return CommandResult.rejected("INVALID_ARMY", "Your army no longer exists");
        }
        if (army.state == Army.ArmyState.ENGAGED) {
            return CommandResult.rejected("ALREADY_ENGAGED", "Your army is already fighting a battle");
        }
        if (army.state == Army.ArmyState.ROUTED) {
            return CommandResult.rejected("ARMY_ROUTED", "Your routed army must regroup before it can besiege");
        }
        if (simulation.getMilitarySystem().strength(army) <= 0) {
            return CommandResult.rejected("NO_TROOPS", "Your army has no troops left to besiege with");
        }
        alignPlayerFundedCompanyWithPlayerRealm();
        repairAccompanyingArmyPosition(army);
        Settlement target = world.geography.getSettlement(settlementId);
        if (target == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (target.controllerRealmId == null) {
            return CommandResult.rejected("NO_SIEGE",
                    "That settlement answers to no realm, so there is nothing to besiege");
        }

        War war = preferredSiege == null ? findActiveWarAgainst(army, target)
                : world.wars.get(preferredSiege.warId);
        world.military.Siege existing = preferredSiege;
        if (existing == null && war != null) {
            existing = simulation.getWarSystem().findActiveSiege(war.id, settlementId);
        }
        if (existing == null) existing = activeSiegeAt(settlementId);
        if (existing != null && existing.settlementId != settlementId) existing = null;
        if (existing != null && war == null) war = world.wars.get(existing.warId);
        if (existing != null) {
            War existingWar = world.wars.get(existing.warId);
            if (existingWar != null && existingWar.state == War.WarState.ACTIVE
                    && existingWar.opposing(army.realmId, target.controllerRealmId.longValue())) {
                // Join the siege that is actually on the ground, even if another
                // overlapping war has a lower id.
                war = existingWar;
            } else if (preferredSiege == null && war != null && existingWar != null
                    && war.id != existingWar.id) {
                // A stale/unrelated siege in another war should not swallow this
                // army; it can still start its own legally validated siege below.
                existing = null;
            }
        }

        boolean alreadyParticipant = existing != null && existing.isBesiegingArmy(army.id);
        if (existing != null) {
            if (war == null || war.state != War.WarState.ACTIVE) {
                return CommandResult.rejected("INVALID_SIEGE", "That siege's war is no longer active");
            }
            if (!war.opposing(army.realmId, target.controllerRealmId.longValue())) {
                if (war.includes(army.realmId)) {
                    return CommandResult.rejected("WRONG_SIDE",
                            "Your side holds " + target.name + ". You cannot join the siege against it; "
                                    + "attack a besieging army to relieve it instead.");
                }
                return CommandResult.rejected("NOT_AT_WAR",
                        "Your army's realm is not a belligerent in the war over " + target.name + ".");
            }
        } else {
            String obstacle = siegeObstacle(army, target);
            if (obstacle != null) return CommandResult.rejected("NO_SIEGE", obstacle);
            if (war == null) war = findActiveWarAgainst(army, target);
            if (war == null) {
                return CommandResult.rejected("NOT_AT_WAR",
                        "Your army's realm is not at war with " + realmNameOf(target.controllerRealmId) + ".");
            }
        }

        boolean withPlayer = isCommandedArmyWithPlayer(army, null);
        boolean inPosition = army.currentSettlementId == settlementId
                && army.state != Army.ArmyState.TRAVELING
                && (!withPlayer || !playerState.isTraveling());
        CommandResult issued;
        if (withPlayer && inPosition) {
            army.order = Army.ArmyOrder.BESIEGE;
            army.destinationSettlementId = settlementId;
            syncCommandedArmyWithPlayerParty(army);
            issued = CommandResult.accepted();
        } else if (withPlayer) {
            // The adventurer rides with the besieging army instead of sending
            // it away as a second token that can lag behind on another route.
            issued = beginTravelTo(settlementId, Army.ArmyOrder.BESIEGE);
        } else {
            issued = simulation.getMilitarySystem().issueMoveOrder(
                    army.id, settlementId, Army.ArmyOrder.BESIEGE, minute);
            inPosition = army.currentSettlementId == settlementId
                    && army.state != Army.ArmyState.TRAVELING;
        }
        if (!issued.accepted) return issued;

        if (existing != null) {
            world.military.Siege joined = simulation.getWarSystem()
                    .beginOrJoinSiege(army, target, war, minute);
            refreshSnapshot();
            int participants = joined == null ? 0 : joined.besiegingArmyIds().size();
            if (alreadyParticipant) {
                return new CommandResult(true, inPosition ? "ALREADY_IN_SIEGE" : "ALREADY_JOINING_SIEGE",
                        inPosition
                                ? "Your army is already in the siege lines at " + target.name + "."
                                : "Your army is already marching to join the siege of " + target.name + ".");
            }
            return new CommandResult(true, inPosition ? "JOINED_SIEGE" : "JOINING_SIEGE",
                    inPosition
                            ? "Your army joins the siege of " + target.name + " (" + participants
                                    + " besieging armies). Its strength now counts toward the breach."
                            : "Your army marches to join the siege of " + target.name
                                    + ". It will count toward the breach when it arrives.");
        }
        if (inPosition) {
            simulation.getWarSystem().beginOrJoinSiege(army, target, war, minute);
            publish("PLAYER_SIEGE_STARTED", minute);
            refreshSnapshot();
            return new CommandResult(true, "SIEGE_STARTED",
                    "Your army lays siege to " + target.name + ". Hold the lines while the breach grows, "
                            + "or storm the walls from the army/WAR menus.");
        }
        refreshSnapshot();
        return new CommandResult(true, "SIEGE_ORDERED", withPlayer
                ? "You march with your army to lay siege to " + target.name
                        + ". The siege begins when you arrive."
                : "Your army marches to lay siege to " + target.name
                        + ". The siege begins when it arrives.");
    }

    private String describeOrder(Army.ArmyOrder order, Settlement destination) {
        switch (order) {
            case BESIEGE:
                return "Your army marches to besiege " + destination.name
                        + ". The siege starts when it arrives.";
            case RAID:
                return "Your army marches to raid " + destination.name
                        + ". Raiding provokes any army it meets.";
            case DEFEND:
                return "Your army marches to defend " + destination.name + ".";
            case PATROL:
                return "Your army patrols toward " + destination.name + ".";
            case RESUPPLY:
                return "Your army marches to resupply at " + destination.name + ".";
            case RETURN_HOME:
                return "Your army marches home. It disbands when it arrives.";
            default:
                return "Your army marches to " + destination.name + " (" + order + ").";
        }
    }

    /** The active war in which this army's realm opposes the target's controller, if any. */
    private War findActiveWarAgainst(Army army, Settlement target) {
        if (army == null || target == null || target.controllerRealmId == null || army.realmId < 0L) {
            return null;
        }
        War best = null;
        for (War war : world.wars.values()) {
            if (war.state != War.WarState.ACTIVE
                    || !war.opposing(army.realmId, target.controllerRealmId.longValue())) continue;
            if (best == null || war.id < best.id) best = war;
        }
        return best;
    }

    private world.military.Siege activeSiegeAt(long settlementId) {
        world.military.Siege best = null;
        for (world.military.Siege siege : world.sieges.values()) {
            if (siege.settlementId != settlementId
                    || siege.state != Siege.SiegeState.ACTIVE) continue;
            if (best == null || siege.id < best.id) best = siege;
        }
        return best;
    }

    /** Why this army cannot start a siege at that settlement, or null when it can. */
    private String siegeObstacle(Army army, Settlement target) {
        if (target.controllerRealmId == null) {
            return "That settlement answers to no realm, so there is nothing to besiege";
        }
        if (target.controllerRealmId.longValue() == army.realmId) {
            if (target.occupyingRealmId != null
                    && target.occupyingRealmId.longValue() != army.realmId) {
                return target.name + " is legally controlled by " + realmNameOf(army.realmId)
                        + " but occupied by " + realmNameOf(target.occupyingRealmId)
                        + ". It is not an enemy-controlled siege target.";
            }
            return target.name + " is controlled by your army's realm ("
                    + realmNameOf(army.realmId) + "), so it cannot be besieged.";
        }
        if (army.realmId < 0L) {
            return "A stateless company cannot besiege. Pledge to a realm that is at war, or found your own.";
        }
        War war = findActiveWarAgainst(army, target);
        if (war == null) {
            return "Your realm is not at war with " + realmNameOf(target.controllerRealmId)
                    + ". A ruler can declare war from the WAR tab.";
        }
        if (target.occupyingRealmId != null
                && !war.opposing(army.realmId, target.occupyingRealmId.longValue())) {
            return target.name + " is already occupied by "
                    + realmNameOf(target.occupyingRealmId)
                    + ". Claim it or settle the war instead of besieging it again.";
        }
        return null;
    }

    /**
     * Checks whether the adventurer can attack a target army, either with a
     * commanded army in reach or personally with their travelling party.
     * GamePanel then asks {@link #createPlayerBattleContext} to build the
     * correct tactical projection. Commanding an army is therefore useful,
     * but no longer a prerequisite for fighting.
     */
    public CommandResult canEngageArmy(long targetArmyId) {
        CommandResult health = playerCanFight();
        if (!health.accepted) return health;

        alignPlayerFundedCompanyWithPlayerRealm();
        Army mine = playerState.commandedArmyId == null
                ? null : world.armies.get(playerState.commandedArmyId);
        if (mine == null || mine.state == Army.ArmyState.DISBANDED) {
            if (playerState.commandedArmyId != null) playerState.commandedArmyId = null;
            mine = null;
        } else {
            repairAccompanyingArmyPosition(mine);
        }

        Army target = world.armies.get(targetArmyId);
        if (target == null || target.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "That army is no longer active");
        }
        if (mine != null && target.id == mine.id) {
            return CommandResult.rejected("INVALID_ARMY", "That is your own army");
        }
        if (target.state == Army.ArmyState.ENGAGED) {
            return CommandResult.rejected("ALREADY_ENGAGED", "That army is already fighting");
        }
        if (simulation.getMilitarySystem().battleReadyStrength(target) <= 0) {
            return CommandResult.rejected("NO_TROOPS",
                    "That army has no living, uncaptured soldiers able to take the field");
        }
        if (target.position == null) {
            return CommandResult.rejected("OUT_OF_RANGE", "Positions are not resolved yet");
        }

        if (commandedArmyInEngagementRange(mine, target)) {
            return new CommandResult(true, "ENGAGE_WITH_ARMY", "");
        }

        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null || party.position == null) {
            return CommandResult.rejected("OUT_OF_RANGE", "Positions are not resolved yet");
        }
        long soloRealmId = soloBattleRealmId(mine, target);
        if (soloRealmId == target.realmId) {
            return CommandResult.rejected("NOT_HOSTILE", "That army serves the same realm as you");
        }
        double partyDistance = Math.hypot(party.position.x - target.position.x,
                party.position.y - target.position.y);
        if (partyDistance <= 12.0) {
            return new CommandResult(true, "ENGAGE_SOLO", "");
        }
        if (mine != null && mine.state == Army.ArmyState.ENGAGED) {
            return CommandResult.rejected("ALREADY_ENGAGED", "Your commanded army is already fighting");
        }
        double nearest = partyDistance;
        if (mine != null && mine.position != null
                && simulation.getMilitarySystem().battleReadyStrength(mine) > 0) {
            nearest = Math.min(nearest, Math.hypot(mine.position.x - target.position.x,
                    mine.position.y - target.position.y));
        }
        return CommandResult.rejected("OUT_OF_RANGE", "That army is " + Math.round(nearest)
                + " away; your party or commanded army can engage within 12. March closer first.");
    }

    private boolean commandedArmyInEngagementRange(Army mine, Army target) {
        return mine != null && target != null && mine.id != target.id
                && mine.state != Army.ArmyState.ENGAGED
                && mine.realmId != target.realmId
                && mine.position != null && target.position != null
                && simulation.getMilitarySystem().battleReadyStrength(mine) > 0
                && Math.hypot(mine.position.x - target.position.x,
                        mine.position.y - target.position.y) <= 12.0;
    }

    private Army commandedArmyForEngagement(Army target) {
        if (target == null || playerState.commandedArmyId == null) return null;
        alignPlayerFundedCompanyWithPlayerRealm();
        Army mine = world.armies.get(playerState.commandedArmyId);
        if (mine == null || mine.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return null;
        }
        repairAccompanyingArmyPosition(mine);
        return commandedArmyInEngagementRange(mine, target) ? mine : null;
    }

    private long soloBattleRealmId(Army mine, Army target) {
        Realm effective = effectivePlayerRealm();
        long realmId = effective != null ? effective.id : mine != null ? mine.realmId : -1L;
        if (realmId == target.realmId && effective == null && mine == null) realmId = -2L;
        return realmId;
    }

    /**
     * Builds the tactical battle for an engagement {@link #canEngageArmy}
     * accepted. A usable commanded army projects normally. Otherwise a
     * temporary, regiment-less party army carries the player (represented by
     * the tactical player sprite) and companions through the existing
     * reconciliation path, then disbands in BattleBridge.
     */
    public BattleContext createPlayerBattleContext(long targetArmyId,
            java.util.List<Long> companionPersonIds, long strategicMinute) {
        CommandResult cleared = canEngageArmy(targetArmyId);
        if (!cleared.accepted) throw new IllegalStateException(cleared.message);
        Army target = world.armies.get(targetArmyId);
        if (target == null) throw new IllegalStateException("That army is no longer active");

        Army commanded = commandedArmyForEngagement(target);
        if (commanded != null) {
            return simulation.getBattleBridge().createContext(commanded.id, target.id, true,
                    companionPersonIds, strategicMinute);
        }

        WorldParty party = world.parties.get(playerState.partyId);
        Person player = world.people.get(playerState.personId);
        if (party == null || party.position == null || player == null || !player.alive) {
            throw new IllegalStateException("Your party cannot take the field");
        }
        Army rawCommanded = playerState.commandedArmyId == null
                ? null : world.armies.get(playerState.commandedArmyId);
        Army solo = new Army(world.idGenerator.next(), soloBattleRealmId(rawCommanded, target),
                playerState.personId, playerState.currentSettlementId,
                new WorldPosition(party.position.x, party.position.y));
        solo.destinationSettlementId = playerState.currentSettlementId;
        solo.order = Army.ArmyOrder.PURSUE;
        solo.targetArmyId = target.id;
        solo.playerCommanded = true;
        solo.temporaryBattleArmy = true;
        solo.morale = Math.max(35.0, Math.min(85.0, 45.0 + player.health.healthLevel * 0.35));
        solo.fatigue = 0.0;
        world.armies.put(solo.id, solo);
        try {
            return simulation.getBattleBridge().createContext(solo.id, target.id, true,
                    companionPersonIds, strategicMinute);
        } catch (RuntimeException failure) {
            solo.state = Army.ArmyState.DISBANDED;
            throw failure;
        }
    }

    /** Resolves an engagement through the same automatic battle path used by AI armies. */
    public CommandResult autoResolveEngagement(long targetArmyId) {
        CommandResult cleared = canEngageArmy(targetArmyId);
        if (!cleared.accepted) return cleared;
        Army commanded = commandedArmyForEngagement(world.armies.get(targetArmyId));
        if (commanded == null) {
            return CommandResult.rejected("NO_TROOPS_TO_SEND",
                    "You have no commanded troops in reach to send. Choose Attack to fight personally with your party.");
        }
        long mineId = commanded.id;
        long minute = clock.getWorldMinute();
        world.military.BattleReport report = simulation.getMilitarySystem()
                .autoResolveEncounter(mineId, targetArmyId, minute);
        if (report == null) {
            return CommandResult.rejected("INVALID_ARMY", "That army is no longer able to fight");
        }
        boolean heldField = report.winnerArmyId == mineId;
        int yourCasualties = report.firstArmyId == mineId
                ? report.firstCasualties : report.secondCasualties;
        int enemyCasualties = report.firstArmyId == mineId
                ? report.secondCasualties : report.firstCasualties;
        notePlayerBattleResult(heldField);
        refreshSnapshot();
        return new CommandResult(true, heldField ? "AUTO_RESOLVE_VICTORY" : "AUTO_RESOLVE_DEFEAT",
                (heldField ? "Your troops hold the field. " : "Your troops are driven off. ")
                        + "Losses: yours " + yourCasualties + ", theirs " + enemyCasualties + ".");
    }

    /** Storms the walls of an active siege your commanded army has joined and reached. */
    public CommandResult assaultSiege(long siegeId) {
        long minute = clock.getWorldMinute();
        CommandResult health = playerCanFight();
        if (!health.accepted) return health;
        world.military.Siege siege = world.sieges.get(siegeId);
        if (siege == null || siege.state != Siege.SiegeState.ACTIVE) {
            return CommandResult.rejected("INVALID_SIEGE", "There is no active siege to assault");
        }
        Army mine = playerState.commandedArmyId == null
                ? null : world.armies.get(playerState.commandedArmyId);
        if (mine == null || mine.state == Army.ArmyState.DISBANDED) {
            if (playerState.commandedArmyId != null) playerState.commandedArmyId = null;
            return CommandResult.rejected("NO_ARMY", "Raise or take command of an army before storming a siege");
        }
        alignPlayerFundedCompanyWithPlayerRealm();
        repairAccompanyingArmyPosition(mine);
        War war = world.wars.get(siege.warId);
        Settlement target = world.geography.getSettlement(siege.settlementId);
        if (war == null || war.state != War.WarState.ACTIVE || target == null
                || target.controllerRealmId == null) {
            return CommandResult.rejected("INVALID_SIEGE", "That siege has collapsed");
        }
        if (!war.opposing(mine.realmId, target.controllerRealmId.longValue())) {
            if (war.includes(mine.realmId)) {
                return CommandResult.rejected("WRONG_SIDE",
                        "Your side holds " + target.name + ". Storming would help the besiegers; "
                                + "attack a besieging army to relieve the settlement instead.");
            }
            return CommandResult.rejected("NOT_AT_WAR",
                    "Your army's realm is not a belligerent in the war over " + target.name + ".");
        }

        boolean registered = siege.isBesiegingArmy(mine.id);
        boolean physicallyReady = mine.currentSettlementId == siege.settlementId
                && mine.state != Army.ArmyState.TRAVELING
                && mine.state != Army.ArmyState.ENGAGED
                && mine.order == Army.ArmyOrder.BESIEGE;
        if (!registered && physicallyReady) {
            // An allied army that marched in with BESIEGE orders joins formally
            // when its commander calls the assault.
            simulation.getWarSystem().beginOrJoinSiege(mine, target, war, minute);
            registered = true;
        }
        boolean participating = false;
        for (Army besieger : simulation.getWarSystem().activeBesiegingArmies(siege, minute)) {
            if (besieger.id == mine.id) { participating = true; break; }
        }
        if (!participating) {
            return CommandResult.rejected(registered ? "NOT_AT_SIEGE" : "NOT_BESIEGING",
                    registered
                            ? "Your army has joined the siege but is not in the siege lines at "
                                    + target.name + " yet."
                            : "Join the siege at " + target.name + " and arrive there before storming its walls.");
        }
        CommandResult result = simulation.getWarSystem().assaultSiege(siegeId, true, minute);
        if (result.accepted && "SIEGE_ASSAULT_CAPTURED".equals(result.reasonCode)) {
            // You personally took part in the storm, whether as lead or support.
            playerState.conquestContribution += 35.0;
        }
        refreshSnapshot();
        return result;
    }

    /** Realms a ruling player could legally declare war on right now. */
    public java.util.List<Long> declarableWarTargets() {
        java.util.List<Long> targets = new java.util.ArrayList<>();
        Realm realm = effectivePlayerRealm();
        if (realm == null || realm.rulerPersonId == null || realm.rulerPersonId != playerState.personId) {
            return targets;
        }
        for (Realm other : world.realms.values()) {
            if (other.id == realm.id) continue;
            boolean atWar = false;
            for (War war : world.wars.values()) {
                if (war.state != War.WarState.ACTIVE) continue;
                if ((war.attackerRealmIds.contains(realm.id) && war.defenderRealmIds.contains(other.id))
                        || (war.attackerRealmIds.contains(other.id) && war.defenderRealmIds.contains(realm.id))) {
                    atWar = true;
                    break;
                }
            }
            if (atWar) continue;
            for (world.geography.Province province : world.geography.getProvinces().values()) {
                if (province.controllerRealmId != null && province.controllerRealmId == other.id) {
                    targets.add(other.id);
                    break;
                }
            }
        }
        targets.sort(Long::compareTo);
        return targets;
    }

    /**
     * A ruler declares war for their realm, using the same legal goals as the AI. A conquest
     * war without a named province takes the enemy's first held province as its goal.
     */
    public CommandResult declareWarAsRuler(long defenderRealmId, War.WarGoal goal,
            Long targetProvinceId, Long targetTitleId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        Realm realm = effectivePlayerRealm();
        if (realm == null || realm.rulerPersonId == null || realm.rulerPersonId != playerState.personId) {
            return CommandResult.rejected("NOT_RULER", "Only a ruler can declare war");
        }
        if (goal == War.WarGoal.CONQUER_PROVINCE && targetProvinceId == null) {
            for (world.geography.Province province : world.geography.getProvinces().values()) {
                if (province.controllerRealmId != null && province.controllerRealmId == defenderRealmId) {
                    targetProvinceId = province.id;
                    break;
                }
            }
            if (targetProvinceId == null) {
                return CommandResult.rejected("NO_TARGET", realmNameOf(defenderRealmId) + " holds no province to conquer");
            }
        }
        CommandResult declared = simulation.getWarSystem().declareWar(
                realm.id, defenderRealmId, goal, targetProvinceId, targetTitleId, minute);
        refreshSnapshot();
        return declared;
    }

    // ===== Fiefs by conquest ==============================================

    /** What a title pays its holder each day, out of the settlement's own treasury. */
    public static long dailyRent(world.politics.Title title, Settlement settlement) {
        if (title == null || settlement == null) return 0L;
        long base = title.rank == Title.Rank.BARONY ? 5L : 12L;
        double prosperity = 1.0 + Math.max(0.0, Math.min(1.5, settlement.prosperity));
        double calm = 1.0 - Math.min(0.8, Math.max(0.0, settlement.unrest));
        return Math.max(1L, Math.round(base * prosperity * calm));
    }

    private long lastHoldingIncomeMinute = Long.MIN_VALUE / 4;

    /** Land pays. Every title you hold draws a daily rent share from its settlement. */
    private void processHoldingIncome(long minute) {
        if (playerState.titleIds.isEmpty()) return;
        if (minute - lastHoldingIncomeMinute < WorldConfig.MINUTES_PER_DAY) return;
        lastHoldingIncomeMinute = minute;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return;
        int lost = 0;
        for (Long titleId : new ArrayList<>(playerState.titleIds)) {
            world.politics.Title title = world.titles.get(titleId);
            if (title == null || title.holderPersonId == null || title.holderPersonId != playerState.personId) {
                // Taken from you, inherited away, or revoked: stop counting it.
                playerState.titleIds.remove(titleId);
                lost++;
                continue;
            }
            Settlement settlement = title.settlementId == null
                    ? null : world.geography.getSettlement(title.settlementId);
            if (settlement == null) continue;
            long rent = dailyRent(title, settlement);
            long paid = Math.min(rent, settlement.treasury.copperCoins);
            // Rent is quiet income; the FIEFS tab shows the rate so it is never a mystery.
            if (paid > 0 && settlement.treasury.subtract(paid)) party.money.add(paid);
        }
        if (lost > 0) publish("HOLDING_LOST", minute);
    }

    /** Records that you fought a strategic battle, so conquest can be earned from it. */
    public void notePlayerBattleResult(boolean playerHeldField) {
        recordPlayerBattleOutcome(playerHeldField, false, 0, 0, 0, 0,
                false, false, false, null);
    }

    /**
     * Applies the personal consequences of a personally fought campaign battle.
     * The strategic armies, casualties, prisoners, and supply loot are reconciled
     * by BattleBridge; this method adds what belongs to the adventurer: conquest
     * credit, battlefield salvage, military experience, and reputation. Pursuing a
     * broken enemy pays more salvage, while showing mercy pays more reputation.
     *
     * @return a short player-readable reward summary, or an empty string for defeat
     */
    public String recordPlayerBattleOutcome(boolean playerHeldField, boolean draw,
            int personalKills, int enemyKilled, int prisonersTaken, int routedEnemies,
            boolean enemyCommanderDown, boolean pursuedRout, boolean showedMercy,
            Long enemyRealmId) {
        if (!playerHeldField && !draw) return "";

        int kills = Math.max(0, personalKills);
        int lossesInflicted = Math.max(0, enemyKilled);
        int prisoners = Math.max(0, prisonersTaken);
        int routed = Math.max(0, routedEnemies);

        double contribution = playerHeldField
                ? 25.0 + Math.min(20.0, kills * 1.5) + prisoners * 2.0 + routed * 0.5
                        + (enemyCommanderDown ? 10.0 : 0.0)
                : 8.0 + Math.min(5.0, kills * 0.5);
        playerState.conquestContribution += contribution;

        long salvageCoins = playerHeldField
                ? 20L + lossesInflicted * 4L + kills * 2L + prisoners * 15L
                        + (pursuedRout ? routed * 2L : 0L)
                        + (enemyCommanderDown ? 50L : 0L)
                : Math.min(30L, lossesInflicted * 2L);
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null && salvageCoins > 0L) party.money.add(salvageCoins);

        Person adventurer = world.people.get(playerState.personId);
        if (adventurer != null) {
            double experience = (playerHeldField ? 10.0 : 4.0) + kills * 1.25
                    + prisoners * 0.75 + (enemyCommanderDown ? 8.0 : 0.0);
            adventurer.militaryExperience = Math.min(100.0,
                    adventurer.militaryExperience + experience);
            if (playerHeldField && kills >= 5) {
                adventurer.skills.martial = Math.min(100, adventurer.skills.martial + 1);
            }
            if (playerHeldField && (prisoners > 0 || enemyCommanderDown)) {
                adventurer.skills.leadership = Math.min(100,
                        adventurer.skills.leadership + 1);
            }
        }

        int localReputation = playerHeldField
                ? 2 + kills / 5 + (showedMercy ? 3 : 0)
                : 1;
        playerState.changeSettlementReputation(playerState.currentSettlementId, localReputation);
        Realm friendlyRealm = effectivePlayerRealm();
        if (friendlyRealm != null) {
            int realmReputation = playerHeldField
                    ? 2 + (enemyCommanderDown ? 2 : 0) + prisoners / 3 : 1;
            playerState.realmReputation.merge(friendlyRealm.id, realmReputation, Integer::sum);
        }
        if (enemyRealmId != null) {
            int enemyReputation = showedMercy ? 1 : pursuedRout ? -3 : -2;
            playerState.realmReputation.merge(enemyRealmId,
                    playerHeldField ? enemyReputation : 0, Integer::sum);
        }

        publish(playerHeldField ? "PLAYER_BATTLE_VICTORY" : "PLAYER_BATTLE_DRAW",
                clock.getWorldMinute());
        refreshSnapshot();
        StringBuilder summary = new StringBuilder();
        summary.append("Personal spoils: ").append(Math.round(contribution))
                .append(" conquest credit");
        if (salvageCoins > 0L) summary.append(", ").append(salvageCoins).append("c salvage");
        if (prisoners > 0) summary.append(", ").append(prisoners).append(" prisoners taken by your army");
        if (kills > 0) summary.append(", ").append(kills).append(" personal kills");
        summary.append('.');
        return summary.toString();
    }

    /**
     * The adventurer was knocked down, but their remaining soldiers held or
     * safely left the field. This is a serious wound, not captivity or a rout.
     */
    public CommandResult recordPlayerKnockedOutArmyHeldField(boolean victory) {
        long minute = clock.getWorldMinute();
        Person adventurer = world.people.get(playerState.personId);
        if (adventurer == null) {
            return CommandResult.rejected("INVALID_STATE", "The adventurer cannot be resolved");
        }
        woundPlayer(adventurer, victory ? 65.0 : 55.0);
        adventurer.militaryExperience = Math.min(100.0, adventurer.militaryExperience + 5.0);
        publish("PLAYER_KNOCKED_OUT", minute);
        refreshSnapshot();
        return new CommandResult(true, victory ? "KNOCKED_OUT_VICTORY" : "KNOCKED_OUT_DRAW",
                victory
                        ? "You are knocked senseless, but your soldiers carry the field and drag you clear."
                        : "You are knocked senseless; your soldiers pull you back as both sides disengage.");
    }

    /** The one title currently held by the adventurer, if any (lowest id wins for old saves). */
    private Title playerHeldTitle() {
        Title held = null;
        for (Title title : world.titles.values()) {
            if (title.holderPersonId == null
                    || title.holderPersonId.longValue() != playerState.personId) continue;
            if (held == null || title.id < held.id) held = title;
        }
        return held;
    }

    /**
     * Gives up a landed title so another one can be claimed later. Crowns are
     * not surrendered through the fief screen; succession has to settle those.
     */
    public CommandResult relinquishTitle(long titleId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        Title title = world.titles.get(titleId);
        if (title == null || title.holderPersonId == null
                || title.holderPersonId.longValue() != playerState.personId) {
            return CommandResult.rejected("NOT_HELD", "You do not hold that title.");
        }
        for (Realm realm : world.realms.values()) {
            if (realm.rulerTitleId == titleId) {
                return CommandResult.rejected("CROWN_TITLE",
                        "A crown cannot be relinquished from the FIEFS tab.");
            }
        }
        title.vacateHolder(minute);
        playerState.titleIds.remove(Long.valueOf(titleId));
        publish("TITLE_RELINQUISHED", minute);
        refreshSnapshot();
        return new CommandResult(true, "TITLE_RELINQUISHED",
                "You relinquish " + title.name + ". You may now claim a different title.");
    }

    /**
     * Claims a settlement your realm occupies as your personal holding. Occupation is not
     * ownership: this is the step that turns it into a title, and it costs you - the realm
     * you dispossessed gets a grievance and its ruler a claim on your new land, so they can
     * legally come after it. Requires that you won the place yourself.
     */
    public CommandResult claimHolding(long settlementId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = freeActionBlock(minute);
        if (blocked != null) return blocked;
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        Realm realm = effectivePlayerRealm();
        if (realm == null) {
            return CommandResult.rejected("NO_REALM",
                    "Land is held under a realm. Pledge to one first: right-click its settlement.");
        }
        if (settlement.occupyingRealmId == null || settlement.occupyingRealmId != realm.id) {
            return CommandResult.rejected("NOT_OCCUPIED", settlement.name + " is not occupied by " + realm.name
                    + ". Win the siege for it first - and claim it before peace gives it back.");
        }
        Title alreadyHeld = playerHeldTitle();
        if (alreadyHeld != null) {
            return CommandResult.rejected("ALREADY_HOLDS_TITLE",
                    "You already hold " + alreadyHeld.name
                            + ". You may hold only one title at a time; relinquish it first.");
        }
        boolean county = settlement.type == Settlement.SettlementType.CITY
                || settlement.type == Settlement.SettlementType.FORT
                || settlement.type == Settlement.SettlementType.CAPITAL;
        double needed = county ? 40.0 : 25.0;
        if (playerState.conquestContribution < needed) {
            return CommandResult.rejected("NOT_YOUR_CONQUEST", "You did not win " + settlement.name
                    + " yourself: " + Math.round(playerState.conquestContribution) + " of "
                    + Math.round(needed) + " contribution. Storm its siege (+35) or win its battle (+25).");
        }
        Long dispossessed = settlement.controllerRealmId;
        world.politics.Title title = new world.politics.Title(world.idGenerator.next(),
                (county ? "County of " : "Barony of ") + settlement.name,
                county ? Title.Rank.COUNTY : Title.Rank.BARONY, realm.id);
        title.deJureProvinceId = settlement.provinceId;
        title.settlementId = settlement.id;
        title.installHolder(playerState.personId, minute, "CONQUEST");
        world.titles.put(title.id, title);
        playerState.titleIds.add(title.id);
        playerState.conquestContribution -= needed;

        Person adventurer = world.people.get(playerState.personId);
        if (adventurer != null) adventurer.type = Person.PersonType.NOBLE;
        if (playerState.kingdomRole == PlayerCampaignState.KingdomRole.NONE
                || playerState.kingdomRole == PlayerCampaignState.KingdomRole.MERCENARY) {
            playerState.kingdomRole = PlayerCampaignState.KingdomRole.LORD;
        }

        settlement.controllerRealmId = realm.id;
        settlement.occupyingRealmId = null;
        world.geography.Province province = world.geography.getProvince(settlement.provinceId);
        if (province != null) {
            boolean wholeProvince = true;
            for (Long otherId : province.settlementIds) {
                Settlement other = world.geography.getSettlement(otherId);
                if (other == null) continue;
                if (other.controllerRealmId == null || other.controllerRealmId != realm.id) {
                    wholeProvince = false;
                    break;
                }
            }
            if (wholeProvince) {
                province.controllerRealmId = realm.id;
                province.occupyingRealmId = null;
            }
        }

        // The diplomatic cost of keeping conquered land.
        if (dispossessed != null && dispossessed != realm.id && world.realms.containsKey(dispossessed)) {
            world.diplomacy.Grievance grievance = new world.diplomacy.Grievance(world.idGenerator.next(),
                    dispossessed, realm.id, Grievance.GrievanceType.BORDER_DISPUTE,
                    county ? 70 : 45, minute, minute + 360L * WorldConfig.MINUTES_PER_DAY);
            world.grievances.put(grievance.id, grievance);
            Realm loser = world.realms.get(dispossessed);
            if (loser != null && loser.rulerPersonId != null) {
                world.politics.Claim counter = new world.politics.Claim(world.idGenerator.next(),
                        loser.rulerPersonId, title.id, county ? 70.0 : 55.0,
                        Claim.ClaimSource.DISPUTED_SUCCESSION, minute);
                world.claims.put(counter.id, counter);
                title.claimIds.add(counter.id);
            }
        }
        simulation.getContext().eventBus.publish(new world.event.WorldEvent(world.idGenerator.next(), minute, "HOLDING_CLAIMED",
                (adventurer == null ? "The adventurer" : adventurer.givenName + " " + adventurer.familyName)
                        + " is granted " + title.name + " by conquest under " + realm.name));
        refreshSnapshot();
        return new CommandResult(true, "HOLDING_CLAIMED", title.name + " is yours, held under " + realm.name
                + ". It pays about " + dailyRent(title, settlement) + "c a day"
                + (dispossessed != null && dispossessed != realm.id
                        ? ", and " + realmNameOf(dispossessed) + " now has a grievance and a claim against you."
                        : "."));
    }

    private Realm effectivePlayerRealm() {
        for (Realm realm : world.realms.values()) {
            if (realm.rulerPersonId != null && realm.rulerPersonId == playerState.personId) return realm;
        }
        return playerState.affiliatedRealmId == null ? null : world.realms.get(playerState.affiliatedRealmId);
    }

    public CommandResult acceptContract(long contractId) {
        CommandResult blocked = freeActionBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
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
        return new LocalPlayerState(person == null ? 100 : playerHealthPercent(),
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
        // Repair the personal company's allegiance before the UI snapshot is
        // taken, including for campaigns saved before this rule existed.
        alignPlayerFundedCompanyWithPlayerRealm();
        snapshot = CampaignSnapshot.capture(simulation);
        lastSnapshotMinute = clock.getWorldMinute();
    }
}
