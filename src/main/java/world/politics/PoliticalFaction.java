package world.politics;

import java.util.HashSet;
import java.util.Set;

/** Organized internal pressure group within a realm. */
public final class PoliticalFaction {
    public long id;
    public long realmId;
    public FactionGoal goal;
    public Long leaderPersonId;
    public final Set<Long> memberPersonIds = new HashSet<>();
    public double support;
    public double organization = 20.0;
    public double militancy;
    public boolean active = true;

    public PoliticalFaction(long id, long realmId, FactionGoal goal) {
        this.id = id;
        this.realmId = realmId;
        this.goal = goal;
    }

    public enum FactionGoal {
        LOWER_TAXES, MERCHANT_PRIVILEGES, NOBLE_PRIVILEGES,
        REPLACE_RULER, LOCAL_AUTONOMY, INDEPENDENCE, END_WAR, CONTINUE_WAR
    }
}
