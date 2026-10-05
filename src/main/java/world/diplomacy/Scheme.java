package world.diplomacy;

import java.util.HashSet;
import java.util.Set;

/** Persistent intrigue operation progressed in explicit monthly phases. */
public final class Scheme {
    public long id;
    public SchemeType type;
    public long sponsorRealmId;
    public long schemerPersonId;
    public long targetRealmId;
    public Long targetPersonId;
    public SchemePhase phase = SchemePhase.RECRUIT_AGENTS;
    public final Set<Long> agentPersonIds = new HashSet<>();
    public final Set<Long> discoveredByRealmIds = new HashSet<>();
    public long monetaryCost;
    public double preparation;
    public double secrecy = 70.0;
    public boolean active = true;
    public boolean successful;
    public String outcome = "IN_PROGRESS";
    public long startedMinute;
    public Long resolvedMinute;

    public Scheme(long id, SchemeType type, long sponsorRealmId, long schemerPersonId,
            long targetRealmId, Long targetPersonId, long monetaryCost, long startedMinute) {
        this.id = id;
        this.type = type;
        this.sponsorRealmId = sponsorRealmId;
        this.schemerPersonId = schemerPersonId;
        this.targetRealmId = targetRealmId;
        this.targetPersonId = targetPersonId;
        this.monetaryCost = monetaryCost;
        this.startedMinute = startedMinute;
    }

    public boolean isVisibleTo(long realmId) {
        return sponsorRealmId == realmId || discoveredByRealmIds.contains(realmId) || !active;
    }

    public enum SchemeType { FABRICATE_CLAIM, ASSASSINATE, BUILD_SPY_NETWORK }
    public enum SchemePhase { RECRUIT_AGENTS, PAY_COSTS, PREPARATION, ATTEMPT, RESOLVED, ABORTED }
}
