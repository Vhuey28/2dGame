package world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import world.economy.Workplace;

/** Rebuildable secondary indexes; canonical ownership remains in WorldState registries. */
public final class WorldIndexes {
    private final Map<Long, List<Long>> livingPeopleByCurrentSettlement = new HashMap<>();
    private final Map<Long, List<Long>> peopleByHomeSettlement = new HashMap<>();
    private final Map<Long, List<Long>> householdsBySettlement = new HashMap<>();
    private final Map<Long, List<Long>> workplacesBySettlement = new HashMap<>();
    private long revision;
    private long lastWorldMinute = Long.MIN_VALUE;

    public void rebuild(WorldState world, long worldMinute) {
        livingPeopleByCurrentSettlement.clear();
        peopleByHomeSettlement.clear();
        householdsBySettlement.clear();
        workplacesBySettlement.clear();
        for (Person person : world.people.values()) {
            if (person.homeSettlementId != null) add(peopleByHomeSettlement, person.homeSettlementId, person.id);
            if (person.alive && person.currentSettlementId != null) {
                add(livingPeopleByCurrentSettlement, person.currentSettlementId, person.id);
            }
        }
        for (Household household : world.households.values()) {
            add(householdsBySettlement, household.homeSettlementId, household.id);
        }
        for (Workplace workplace : world.workplaces.values()) {
            add(workplacesBySettlement, workplace.settlementId, workplace.id);
        }
        revision++;
        lastWorldMinute = worldMinute;
    }

    public void ensureBuilt(WorldState world, long worldMinute) {
        if (revision == 0L || lastWorldMinute != worldMinute) rebuild(world, worldMinute);
    }

    public List<Long> livingPeopleAt(long settlementId) {
        return view(livingPeopleByCurrentSettlement, settlementId);
    }

    public List<Long> peopleWithHomeAt(long settlementId) {
        return view(peopleByHomeSettlement, settlementId);
    }

    public List<Long> householdsAt(long settlementId) {
        return view(householdsBySettlement, settlementId);
    }

    public List<Long> workplacesAt(long settlementId) {
        return view(workplacesBySettlement, settlementId);
    }

    public long getRevision() { return revision; }
    public long getLastWorldMinute() { return lastWorldMinute; }

    private void add(Map<Long, List<Long>> index, long key, long id) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(id);
    }

    private List<Long> view(Map<Long, List<Long>> index, long key) {
        List<Long> values = index.get(key);
        return values == null ? List.of() : Collections.unmodifiableList(values);
    }
}
