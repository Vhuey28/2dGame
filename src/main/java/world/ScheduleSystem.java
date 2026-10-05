package world;

/** Assigns every living person a coarse daily activity without requiring a loaded map. */
public final class ScheduleSystem {
    private final WorldState world;

    public ScheduleSystem(WorldState world) { this.world = world; }

    public void processHour(long minute) {
        int hour = (int) ((minute / WorldConfig.MINUTES_PER_HOUR) % WorldConfig.HOURS_PER_DAY);
        for (Person person : world.people.values()) {
            if (!person.alive) continue;
            if (person.travelingPartyId != null && person.currentSettlementId == null) {
                person.currentActivity = PersonActivity.TRAVELING;
                continue;
            }
            int localHour = Math.floorMod(hour + person.scheduleOffsetHours, 24);
            if (person.type == Person.PersonType.SOLDIER) {
                person.currentActivity = localHour < 6 ? PersonActivity.SLEEPING
                        : localHour < 18 ? PersonActivity.GUARDING : PersonActivity.PATROLLING;
            } else if (person.type == Person.PersonType.CHILD) {
                person.currentActivity = localHour < 7 || localHour >= 21 ? PersonActivity.SLEEPING
                        : localHour < 15 ? PersonActivity.SOCIALIZING : PersonActivity.IDLE;
            } else if (localHour < 6 || localHour >= 22) {
                person.currentActivity = PersonActivity.SLEEPING;
            } else if (localHour < 8) {
                person.currentActivity = PersonActivity.COMMUTING;
            } else if (localHour < 17) {
                person.currentActivity = person.employerId == null ? PersonActivity.IDLE : PersonActivity.WORKING;
            } else if (localHour < 20) {
                person.currentActivity = PersonActivity.SHOPPING;
            } else {
                person.currentActivity = PersonActivity.SOCIALIZING;
            }
        }
    }
}
