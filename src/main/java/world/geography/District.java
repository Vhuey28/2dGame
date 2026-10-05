package world.geography;

/**
 * Stub placeholder for District. Full implementation in later phases.
 */
public final class District {
    public long id;
    public long settlementId;
    public String name;

    public District(long id, long settlementId, String name) {
        this.id = id;
        this.settlementId = settlementId;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("District[id=%d, name=%s, settlementId=%d]", id, name, settlementId);
    }
}