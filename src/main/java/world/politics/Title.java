package world.politics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Legal title with a living holder, claims, and persistent holder history. */
public final class Title {
    public long id;
    public String name;
    public Rank rank;
    public long realmId;
    public Long deJureProvinceId;
    public Long holderPersonId;
    public final Set<Long> claimIds = new HashSet<>();
    public final List<HolderRecord> holderHistory = new ArrayList<>();

    public Title(long id, String name, Rank rank, long realmId) {
        this.id = id;
        this.name = name;
        this.rank = rank;
        this.realmId = realmId;
    }

    public void installHolder(long personId, long minute, String reason) {
        if (holderPersonId != null && !holderHistory.isEmpty()) {
            holderHistory.get(holderHistory.size() - 1).endMinute = minute;
        }
        holderPersonId = personId;
        holderHistory.add(new HolderRecord(personId, minute, reason));
    }

    public enum Rank { BARONY, COUNTY, DUCHY, KINGDOM, EMPIRE }

    public static final class HolderRecord {
        public final long personId;
        public final long startMinute;
        public Long endMinute;
        public final String reason;

        HolderRecord(long personId, long startMinute, String reason) {
            this.personId = personId;
            this.startMinute = startMinute;
            this.reason = reason;
        }
    }
}
