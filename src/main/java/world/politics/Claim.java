package world.politics;

/** A person's legal or political claim to a title. */
public final class Claim {
    public long id;
    public long claimantPersonId;
    public long titleId;
    public double strength;
    public ClaimSource source;
    public long createdMinute;
    public Long expiryMinute;

    public Claim(long id, long claimantPersonId, long titleId, double strength,
            ClaimSource source, long createdMinute) {
        this.id = id;
        this.claimantPersonId = claimantPersonId;
        this.titleId = titleId;
        this.strength = Math.max(0.0, Math.min(100.0, strength));
        this.source = source;
        this.createdMinute = createdMinute;
    }

    public enum ClaimSource { INHERITANCE, DISPUTED_SUCCESSION, GRANT, FABRICATION }
}
