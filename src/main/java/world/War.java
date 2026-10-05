package world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Persistent war with coalitions, legal goal, score, contributions, and exhaustion. */
public final class War {
    public long id;
    public final Set<Long> attackerRealmIds = new HashSet<>();
    public final Set<Long> defenderRealmIds = new HashSet<>();
    public WarGoal goal;
    public Long targetProvinceId;
    public Long targetTitleId;
    public Long claimantPersonId;
    public long startMinute;
    public Long endMinute;
    public WarState state = WarState.ACTIVE;
    public final Map<Long, Double> contributionByRealm = new HashMap<>();
    public final Set<Long> processedBattleReportIds = new HashSet<>();
    public double attackerScore;
    public double defenderScore;
    public double attackerExhaustion;
    public double defenderExhaustion;
    public String declarationReason;
    public Long enforcedPeaceOfferId;

    public War(long id, long attackerRealmId, long defenderRealmId,
            WarGoal goal, long startMinute) {
        this.id = id;
        this.attackerRealmIds.add(attackerRealmId);
        this.defenderRealmIds.add(defenderRealmId);
        this.goal = goal;
        this.startMinute = startMinute;
        contributionByRealm.put(attackerRealmId, 0.0);
        contributionByRealm.put(defenderRealmId, 0.0);
    }

    public boolean includes(long realmId) {
        return attackerRealmIds.contains(realmId) || defenderRealmIds.contains(realmId);
    }

    public boolean opposing(long firstRealmId, long secondRealmId) {
        return attackerRealmIds.contains(firstRealmId) && defenderRealmIds.contains(secondRealmId)
                || attackerRealmIds.contains(secondRealmId) && defenderRealmIds.contains(firstRealmId);
    }

    public enum WarGoal { CLAIM_TITLE, CONQUER_PROVINCE, INDEPENDENCE, VASSALIZE, PUNITIVE }
    public enum WarState { ACTIVE, NEGOTIATING, ENDED }
}
