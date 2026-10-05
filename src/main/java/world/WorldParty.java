package world;

import java.util.ArrayList;
import java.util.List;

import world.economy.Inventory;
import world.economy.MoneyAccount;
import world.geography.WorldPosition;

/** Persistent strategic party record. Local sprites are only presentation adapters for this state. */
public final class WorldParty {
    public final long id;
    public final List<Long> memberPersonIds = new ArrayList<>();
    public final Inventory cargo = new Inventory();
    public final MoneyAccount money = new MoneyAccount();
    public int cargoCapacity = 40;
    public Long currentSettlementId;
    public Long destinationSettlementId;
    public WorldPosition position;
    public PartyState state = PartyState.AT_SETTLEMENT;

    public WorldParty(long id, long leaderPersonId, long settlementId) {
        this.id = id;
        this.memberPersonIds.add(leaderPersonId);
        this.currentSettlementId = settlementId;
    }

    public int remainingCapacity() {
        return Math.max(0, cargoCapacity - cargo.totalQuantity());
    }

    public enum PartyState { AT_SETTLEMENT, TRAVELING, LOCAL_SCENE }
}
