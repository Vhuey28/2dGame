package world;

import java.util.ArrayList;
import java.util.List;

import world.economy.GoodType;
import world.economy.Inventory;
import world.economy.MoneyAccount;
import world.geography.WorldPosition;

/** A persistent merchant party moving real cargo through the route graph. */
public final class Caravan {
    public long id;
    public long ownerHouseholdId;
    public long leaderPersonId;
    public List<Long> guardPersonIds = new ArrayList<>();
    public Inventory cargo = new Inventory();
    public MoneyAccount cash = new MoneyAccount();
    public WorldPosition position;
    public long currentSettlementId;
    public Long destinationSettlementId;
    public List<Long> routeRoadIds = new ArrayList<>();
    public int routeIndex;
    public double roadProgress;
    public double speedPerHour = 12.0;
    public int carryingCapacity = 120;
    public long cargoCostBasis;
    public long lifetimeProfit;
    public int completedTrips;
    public CaravanState state = CaravanState.IDLE;

    public Caravan(long id, long ownerHouseholdId, long leaderPersonId,
            long currentSettlementId, WorldPosition position) {
        this.id = id;
        this.ownerHouseholdId = ownerHouseholdId;
        this.leaderPersonId = leaderPersonId;
        this.currentSettlementId = currentSettlementId;
        this.position = position;
    }

    public int cargoQuantity() {
        return cargo.totalQuantity();
    }

    public int remainingCapacity() {
        return Math.max(0, carryingCapacity - cargoQuantity());
    }

    public GoodType primaryCargo() {
        GoodType best = null;
        int quantity = 0;
        for (GoodType type : GoodType.values()) {
            int candidate = cargo.getQuantity(type);
            if (candidate > quantity) {
                quantity = candidate;
                best = type;
            }
        }
        return best;
    }

    public enum CaravanState { IDLE, BUYING, TRAVELING, SELLING, DESTROYED }
}
