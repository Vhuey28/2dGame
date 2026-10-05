package world.military;

import java.util.ArrayList;
import java.util.List;

import world.Person;
import world.WorldState;

/** Strategic organization retaining the persistent IDs of every soldier. */
public final class Regiment {
    public long id;
    public long realmId;
    public RegimentType type;
    public final List<Long> soldierPersonIds = new ArrayList<>();
    public Long officerPersonId;
    public long homeSettlementId;
    public int equipmentQuality;
    public double cohesion = 50.0;
    public double experience;
    public long dailyPayPerSoldier = 2L;
    public boolean active = true;

    public Regiment(long id, long realmId, RegimentType type, long homeSettlementId) {
        this.id = id;
        this.realmId = realmId;
        this.type = type;
        this.homeSettlementId = homeSettlementId;
    }

    public int livingStrength(WorldState world) {
        int living = 0;
        for (Long personId : soldierPersonIds) {
            Person person = world.people.get(personId);
            if (person != null && person.alive) living++;
        }
        return living;
    }

    public enum RegimentType { LEVY_INFANTRY, SPEAR, ARCHER, CAVALRY, GUARD }
}
