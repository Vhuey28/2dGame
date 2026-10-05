package world;

/** Persistent local offense whose consequences feed settlement security and unrest. */
public final class CrimeIncident {
    public final long id;
    public final long settlementId;
    public final long offenderPersonId;
    public final Long victimPersonId;
    public final CrimeType type;
    public final long minute;
    public final boolean discovered;
    public final int severity;

    public CrimeIncident(long id, long settlementId, long offenderPersonId, Long victimPersonId,
            CrimeType type, long minute, boolean discovered, int severity) {
        this.id = id;
        this.settlementId = settlementId;
        this.offenderPersonId = offenderPersonId;
        this.victimPersonId = victimPersonId;
        this.type = type;
        this.minute = minute;
        this.discovered = discovered;
        this.severity = severity;
    }

    public enum CrimeType { THEFT, ASSAULT, BANDITRY, SMUGGLING }
}
