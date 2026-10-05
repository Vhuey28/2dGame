package world.military;

/** Persistent siege consuming real settlement supplies and tracking occupation progress. */
public final class Siege {
    public long id;
    public long warId;
    public long besiegingArmyId;
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

    public enum SiegeState { ACTIVE, CAPTURED, RELIEVED, ABANDONED }
}
