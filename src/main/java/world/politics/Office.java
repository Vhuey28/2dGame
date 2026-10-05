package world.politics;

/** Realm office occupied by a person and used as a source of political power. */
public final class Office {
    public long id;
    public long realmId;
    public OfficeType type;
    public Long holderPersonId;
    public long appointedMinute;

    public Office(long id, long realmId, OfficeType type, Long holderPersonId, long appointedMinute) {
        this.id = id;
        this.realmId = realmId;
        this.type = type;
        this.holderPersonId = holderPersonId;
        this.appointedMinute = appointedMinute;
    }

    public enum OfficeType { RULER, STEWARD, MARSHAL, CHANCELLOR, SPYMASTER }
}
