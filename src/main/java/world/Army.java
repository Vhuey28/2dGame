package world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import world.economy.Inventory;
import world.geography.WorldPosition;

/** Persistent strategic army party composed only of recruited people. */
public final class Army {
    public long id;
    public long realmId;
    public long commanderPersonId;
    public final List<Long> regimentIds = new ArrayList<>();
    public WorldPosition position;
    public ArmyOrder order = ArmyOrder.MUSTER;
    public ArmyState state = ArmyState.MUSTERED;
    public Inventory supplies = new Inventory();
    public double morale = 70.0;
    public double fatigue;
    public double movementProgress;
    public long currentSettlementId;
    public Long homeSettlementId;
    public Long destinationSettlementId;
    public Long targetArmyId;
    public final List<Long> routeRoadIds = new ArrayList<>();
    public int routeIndex;
    public double roadProgress;
    public double speedPerHour = 8.0;
    public int daysWithoutFood;
    public final Set<Long> detectedArmyIds = new HashSet<>();
    public final Set<Long> prisonerPersonIds = new HashSet<>();
    /**
     * True when the adventurer commands this army: realm AI must not issue orders to it or
     * pick it as a realm's field army. Independent of funding, so a king can command a
     * realm-funded army and a landless adventurer can command a self-funded company.
     */
    public boolean playerCommanded;
    /** True when upkeep is paid from the adventurer's own purse instead of a realm treasury. */
    public boolean playerFunded;
    /**
     * True only for the short-lived projection of the adventurer's own party
     * used when they fight without commanding a regimented army. It must be
     * disbanded by battle reconciliation and never receive autonomous orders.
     */
    public boolean temporaryBattleArmy;

    public Army(long id, long realmId, long commanderPersonId,
            long settlementId, WorldPosition position) {
        this.id = id;
        this.realmId = realmId;
        this.commanderPersonId = commanderPersonId;
        this.currentSettlementId = settlementId;
        this.homeSettlementId = settlementId;
        this.position = position;
    }

    public enum ArmyOrder {
        MUSTER, MOVE, PATROL, ESCORT, DEFEND, PURSUE, AVOID,
        RAID, BESIEGE, RESUPPLY, RETURN_HOME
    }

    public enum ArmyState { MUSTERED, TRAVELING, ENGAGED, ROUTED, DISBANDED }
}
