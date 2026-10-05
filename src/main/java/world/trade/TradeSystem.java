package world.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.Caravan;
import world.Household;
import world.Person;
import world.SimulationContext;
import world.WorldState;
import world.economy.GoodType;
import world.economy.GoodsCatalog;
import world.event.WorldEvent;
import world.geography.Road;
import world.geography.Settlement;
import world.geography.WorldPosition;

/** Plans merchant cargo, moves caravans, resolves sales, and applies route risk. */
public final class TradeSystem {
    private final SimulationContext context;
    private final WorldState world;

    public TradeSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    /** Re-plan idle merchants. Called weekly and after arrivals. */
    public void processWeek(long currentMinute) {
        List<Caravan> caravans = new ArrayList<>(world.caravans.values());
        caravans.sort(Comparator.comparingLong(c -> c.id));
        for (Caravan caravan : caravans) {
            if (caravan.state == Caravan.CaravanState.DESTROYED) continue;
            if (caravan.state == Caravan.CaravanState.SELLING) sellAtDestination(caravan, currentMinute);
            if (caravan.state == Caravan.CaravanState.IDLE) planTrip(caravan, currentMinute);
        }
    }

    /** Move all traveling caravans by one strategic hour. */
    public void processHour(long currentMinute) {
        for (Caravan caravan : new ArrayList<>(world.caravans.values())) {
            if (caravan.state != Caravan.CaravanState.TRAVELING) continue;
            moveOneHour(caravan, currentMinute);
        }
    }

    /** Resolve low-frequency bandit losses on the road. */
    public void processDay(long currentMinute) {
        for (Caravan caravan : new ArrayList<>(world.caravans.values())) {
            if (caravan.state != Caravan.CaravanState.TRAVELING
                    || caravan.routeIndex >= caravan.routeRoadIds.size()) continue;
            Road road = world.geography.getRouteGraph().getRoad(caravan.routeRoadIds.get(caravan.routeIndex));
            if (road == null) continue;
            double guardProtection = 1.0 + caravan.guardPersonIds.size() * 0.75;
            double attackChance = road.dangerLevel * 0.08 / guardProtection;
            if (context.getRandom("ECONOMY").nextDouble() < attackChance) {
                int percentLost = 20 + context.getRandom("ECONOMY").nextInt(31);
                for (GoodType good : GoodType.values()) {
                    int quantity = caravan.cargo.getQuantity(good);
                    int lost = quantity * percentLost / 100;
                    if (lost > 0) caravan.cargo.remove(good, lost);
                }
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute,
                        "CARAVAN_ATTACKED"));
            }
        }
    }

    private void planTrip(Caravan caravan, long currentMinute) {
        Settlement source = world.geography.getSettlement(caravan.currentSettlementId);
        if (source == null) return;

        TradeChoice best = null;
        for (GoodType good : new GoodType[]{GoodType.GRAIN, GoodType.VEGETABLES,
                GoodType.TIMBER, GoodType.IRON_ORE, GoodType.TOOLS}) {
            int sourceStock = source.publicStockpile.getQuantity(good);
            if (sourceStock < 20) continue;
            for (Settlement destination : world.geography.getSettlements().values()) {
                if (destination.id == source.id) continue;
                int destinationStock = destination.publicStockpile.getQuantity(good);
                int shortage = sourceStock - destinationStock;
                List<Road> route = world.geography.getRouteGraph()
                        .findShortestRoute(source.id, destination.id);
                if (shortage <= 10 || route.isEmpty()) continue;
                double routeCost = route.stream().mapToDouble(Road::getEffectiveCostFactor).sum();
                double score = shortage * GoodsCatalog.getBasePrice(good) - routeCost * 0.08;
                if (best == null || score > best.score) {
                    best = new TradeChoice(good, destination.id, route, score);
                }
            }
        }
        if (best == null) return;

        long purchasePrice = Math.max(1L, source.market.getLastPrice(best.good));
        int affordable = (int) Math.min(Integer.MAX_VALUE, caravan.cash.copperCoins / purchasePrice);
        int reserve = 10;
        int available = Math.max(0, source.publicStockpile.getQuantity(best.good) - reserve);
        int quantity = Math.min(caravan.remainingCapacity(), Math.min(available, affordable));
        if (quantity <= 0) return;

        long cost = quantity * purchasePrice;
        if (!caravan.cash.subtract(cost)) return;
        if (!source.publicStockpile.remove(best.good, quantity)) {
            caravan.cash.add(cost);
            return;
        }
        source.treasury.add(cost);
        caravan.cargo.add(best.good, quantity);
        caravan.cargoCostBasis += cost;
        caravan.destinationSettlementId = best.destinationId;
        caravan.routeRoadIds.clear();
        for (Road road : best.route) caravan.routeRoadIds.add(road.id);
        caravan.routeIndex = 0;
        caravan.roadProgress = 0.0;
        caravan.state = Caravan.CaravanState.TRAVELING;

        Person leader = world.people.get(caravan.leaderPersonId);
        if (leader != null) {
            leader.currentSettlementId = null;
            leader.travelingPartyId = caravan.id;
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute,
                "CARAVAN_DEPARTED"));
    }

    private void moveOneHour(Caravan caravan, long currentMinute) {
        double distanceRemaining = caravan.speedPerHour;
        while (distanceRemaining > 0 && caravan.routeIndex < caravan.routeRoadIds.size()) {
            Road road = world.geography.getRouteGraph().getRoad(caravan.routeRoadIds.get(caravan.routeIndex));
            if (road == null || road.blocked) {
                caravan.state = Caravan.CaravanState.IDLE;
                caravan.routeRoadIds.clear();
                return;
            }
            double effectiveLength = Math.max(1.0, road.getEffectiveCostFactor());
            double remainingOnRoad = effectiveLength * (1.0 - caravan.roadProgress);
            double traveled = Math.min(distanceRemaining, remainingOnRoad);
            caravan.roadProgress += traveled / effectiveLength;
            distanceRemaining -= traveled;
            updatePosition(caravan, road);

            if (caravan.roadProgress >= 0.999999) {
                caravan.currentSettlementId = road.toSettlementId;
                caravan.routeIndex++;
                caravan.roadProgress = 0.0;
            }
        }

        if (caravan.routeIndex >= caravan.routeRoadIds.size()) arrive(caravan, currentMinute);
    }

    private void updatePosition(Caravan caravan, Road road) {
        Settlement from = world.geography.getSettlement(road.fromSettlementId);
        Settlement to = world.geography.getSettlement(road.toSettlementId);
        if (from == null || to == null) return;
        double x = from.position.x + (to.position.x - from.position.x) * caravan.roadProgress;
        double y = from.position.y + (to.position.y - from.position.y) * caravan.roadProgress;
        caravan.position = new WorldPosition(x, y);
        caravan.position.routeEdgeId = road.id;
        caravan.position.routeProgress = caravan.roadProgress;
    }

    private void arrive(Caravan caravan, long currentMinute) {
        if (caravan.destinationSettlementId != null) {
            caravan.currentSettlementId = caravan.destinationSettlementId;
        }
        Settlement destination = world.geography.getSettlement(caravan.currentSettlementId);
        if (destination != null) {
            caravan.position = new WorldPosition(destination.position.x, destination.position.y);
        }
        caravan.routeRoadIds.clear();
        caravan.routeIndex = 0;
        caravan.state = Caravan.CaravanState.SELLING;
        Person leader = world.people.get(caravan.leaderPersonId);
        if (leader != null) {
            leader.currentSettlementId = caravan.currentSettlementId;
            leader.travelingPartyId = null;
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute,
                "CARAVAN_ARRIVED"));
        sellAtDestination(caravan, currentMinute);
    }

    private void sellAtDestination(Caravan caravan, long currentMinute) {
        Settlement destination = world.geography.getSettlement(caravan.currentSettlementId);
        if (destination == null) return;
        boolean soldAnything = false;
        for (GoodType good : GoodType.values()) {
            int quantity = caravan.cargo.getQuantity(good);
            if (quantity <= 0) continue;
            long unitPrice = Math.max(1L, Math.round(GoodsCatalog.getBasePrice(good) * 1.25));
            int affordable = (int) Math.min(quantity, destination.treasury.copperCoins / unitPrice);
            if (affordable <= 0) continue;
            long revenue = affordable * unitPrice;
            int cargoBeforeSale = Math.max(1, caravan.cargoQuantity());
            long allocatedCost = caravan.cargoCostBasis * affordable / cargoBeforeSale;
            destination.treasury.subtract(revenue);
            caravan.cash.add(revenue);
            caravan.lifetimeProfit += revenue - allocatedCost;
            caravan.cargoCostBasis = Math.max(0L, caravan.cargoCostBasis - allocatedCost);
            caravan.cargo.remove(good, affordable);
            destination.publicStockpile.add(good, affordable);
            soldAnything = true;
        }
        if (caravan.cargoQuantity() == 0) {
            caravan.completedTrips++;
            caravan.destinationSettlementId = null;
            caravan.state = Caravan.CaravanState.IDLE;
            if (soldAnything) {
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute,
                        "CARAVAN_SOLD_CARGO"));
            }
        }
    }

    private static final class TradeChoice {
        final GoodType good;
        final long destinationId;
        final List<Road> route;
        final double score;

        TradeChoice(GoodType good, long destinationId, List<Road> route, double score) {
            this.good = good;
            this.destinationId = destinationId;
            this.route = route;
            this.score = score;
        }
    }
}
