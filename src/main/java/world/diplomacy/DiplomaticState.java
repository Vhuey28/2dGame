package world.diplomacy;

import java.util.HashSet;
import java.util.Set;

/** Persistent bilateral relationship. Realm IDs are stored in canonical order. */
public final class DiplomaticState {
    public long id;
    public final long firstRealmId;
    public final long secondRealmId;
    public int opinion;
    public int trust;
    public int fear;
    public int rivalry;
    public int borderTension;
    public int tradeDependence;
    public final Set<Long> treatyIds = new HashSet<>();
    public final Set<Long> grievanceIds = new HashSet<>();
    public String lastAiExplanation = "No proposal evaluated";

    public DiplomaticState(long id, long realmA, long realmB) {
        if (realmA == realmB) throw new IllegalArgumentException("Diplomacy requires two realms");
        this.id = id;
        this.firstRealmId = Math.min(realmA, realmB);
        this.secondRealmId = Math.max(realmA, realmB);
    }

    public boolean connects(long realmA, long realmB) {
        return firstRealmId == Math.min(realmA, realmB)
                && secondRealmId == Math.max(realmA, realmB);
    }

    public int relationshipScore() {
        return clamp(opinion + trust / 2 + tradeDependence / 3
                - rivalry / 2 - borderTension / 2);
    }

    public static int clamp(int value) {
        return Math.max(-100, Math.min(100, value));
    }
}
