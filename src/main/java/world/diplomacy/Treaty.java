package world.diplomacy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Bilateral treaty with explicit duration, obligations, and violation state. */
public final class Treaty {
    public long id;
    public TreatyType type;
    public final Set<Long> participantRealmIds;
    public long startMinute;
    public Long endMinute;
    public long tributePerMonth;
    public Long tributePayerRealmId;
    public boolean active = true;
    public boolean violated;
    public Long violatedByRealmId;
    public Long violationMinute;

    public Treaty(long id, TreatyType type, long firstRealmId, long secondRealmId,
            long startMinute, Long endMinute) {
        this.id = id;
        this.type = type;
        Set<Long> participants = new HashSet<>();
        participants.add(firstRealmId);
        participants.add(secondRealmId);
        if (participants.size() != 2) throw new IllegalArgumentException("Treaty requires two realms");
        this.participantRealmIds = Collections.unmodifiableSet(participants);
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public boolean includes(long realmId) { return participantRealmIds.contains(realmId); }
    public boolean isActiveAt(long minute) {
        return active && !violated && (endMinute == null || minute < endMinute);
    }
    public long otherParticipant(long realmId) {
        if (!includes(realmId)) throw new IllegalArgumentException("Realm is not a participant");
        return participantRealmIds.stream().filter(id -> id != realmId).findFirst().orElseThrow();
    }

    public enum TreatyType {
        TRUCE, NON_AGGRESSION, TRADE_AGREEMENT, DEFENSIVE_ALLIANCE,
        MILITARY_ACCESS, VASSALAGE, TRIBUTE
    }
}
