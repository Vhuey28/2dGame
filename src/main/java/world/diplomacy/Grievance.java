package world.diplomacy;

/** A remembered diplomatic offense that decays or expires over time. */
public final class Grievance {
    public long id;
    public long offendedRealmId;
    public long offenderRealmId;
    public GrievanceType type;
    public int severity;
    public long createdMinute;
    public Long expiryMinute;
    public boolean resolved;

    public Grievance(long id, long offendedRealmId, long offenderRealmId,
            GrievanceType type, int severity, long createdMinute, Long expiryMinute) {
        this.id = id;
        this.offendedRealmId = offendedRealmId;
        this.offenderRealmId = offenderRealmId;
        this.type = type;
        this.severity = Math.max(1, Math.min(100, severity));
        this.createdMinute = createdMinute;
        this.expiryMinute = expiryMinute;
    }

    public enum GrievanceType { TREATY_VIOLATION, EXPOSED_SCHEME, UNPAID_TRIBUTE, BORDER_DISPUTE }
}
