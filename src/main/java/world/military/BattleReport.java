package world.military;

/** Immutable strategic auto-resolution record used by later tactical reconciliation. */
public final class BattleReport {
    public final long id;
    public final long firstArmyId;
    public final long secondArmyId;
    public final long winnerArmyId;
    public final int firstCasualties;
    public final int secondCasualties;
    public final long worldMinute;

    public BattleReport(long id, long firstArmyId, long secondArmyId, long winnerArmyId,
            int firstCasualties, int secondCasualties, long worldMinute) {
        this.id = id;
        this.firstArmyId = firstArmyId;
        this.secondArmyId = secondArmyId;
        this.winnerArmyId = winnerArmyId;
        this.firstCasualties = firstCasualties;
        this.secondCasualties = secondCasualties;
        this.worldMinute = worldMinute;
    }
}
