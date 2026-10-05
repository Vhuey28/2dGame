package world.politics;

import java.util.EnumMap;
import java.util.Map;

/** Constitutional rules and laws governing a realm. */
public final class Government {
    public long id;
    public long realmId;
    public GovernmentType type;
    public SuccessionLaw successionLaw;
    public final Map<LawType, Integer> laws = new EnumMap<>(LawType.class);

    public Government(long id, long realmId, GovernmentType type, SuccessionLaw successionLaw) {
        this.id = id;
        this.realmId = realmId;
        this.type = type;
        this.successionLaw = successionLaw;
        for (LawType law : LawType.values()) laws.put(law, 1);
    }

    public int getLawLevel(LawType type) {
        return laws.getOrDefault(type, 1);
    }

    public void setLawLevel(LawType type, int level) {
        laws.put(type, Math.max(0, Math.min(3, level)));
    }

    public enum GovernmentType { FEUDAL_MONARCHY, ELECTIVE_MONARCHY, REPUBLIC }
    public enum SuccessionLaw { HEREDITARY, ELECTIVE }
    public enum LawType { TAXATION, CONSCRIPTION, TARIFFS, RELIGIOUS_TOLERANCE, LOCAL_AUTONOMY }
}
