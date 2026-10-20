package world.military;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persistent siege consuming real settlement supplies and tracking occupation progress. */
public final class Siege {
    public long id;
    public long warId;
    public long besiegingArmyId;
    /** Additional armies committed to the same siege lines, including armies still marching to join. */
    public final Set<Long> supportingArmyIds = new LinkedHashSet<>();
    public long settlementId;
    public long startMinute;
    public SiegeState state = SiegeState.ACTIVE;
    public double breachProgress;
    public double defenderResolve = 100.0;
    public int foodConsumed;
    public int civilianDeaths;
    public Long endMinute;

    public Siege(long id, long warId, long besiegingArmyId, long settlementId, long startMinute) {
        this.id = id;
        this.warId = warId;
        this.besiegingArmyId = besiegingArmyId;
        this.settlementId = settlementId;
        this.startMinute = startMinute;
    }

    /** Every army committed to these siege lines, with the original/lead army first. */
    public List<Long> besiegingArmyIds() {
        List<Long> armies = new ArrayList<>();
        armies.add(besiegingArmyId);
        for (Long armyId : supportingArmyIds) {
            if (armyId != null && armyId.longValue() != besiegingArmyId) armies.add(armyId);
        }
        return armies;
    }

    public boolean isBesiegingArmy(long armyId) {
        return besiegingArmyId == armyId || supportingArmyIds.contains(armyId);
    }

    /** Registers another army in the siege. Returns true when this is a new participant. */
    public boolean addSupportingArmy(long armyId) {
        if (besiegingArmyId == armyId) return false;
        return supportingArmyIds.add(armyId);
    }

    public enum SiegeState { ACTIVE, CAPTURED, RELIEVED, ABANDONED }
}
