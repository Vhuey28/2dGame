package world;

import world.geography.WorldPosition;

/**
 * Person model from world.md section 9.1. Every person has a persistent
 * world record with stable ID, family links, employment, skills, needs,
 * and location state.
 * Do not put Swing, images, tile coordinates, or combat animation in this class.
 */
public final class Person {
    public long id;
    public String givenName;
    public String familyName;
    public Sex sex;
    public long birthMinute;
    public Long deathMinute;
    public boolean alive = true;

    public long cultureId;
    public long religionId;
    public Long dynastyId;
    public Long householdId;
    public Long spouseId;
    public Long fatherId;
    public Long motherId;

    public Long homeSettlementId;
    public Long currentSettlementId;
    public Long travelingPartyId;
    public Long employerId;
    public Long officeId;
    public Long regimentId;
    public Long capturedByRealmId;
    public Long preMilitaryEmployerId;
    public PersonType preMilitaryType;
    public double militaryExperience;
    public PersonActivity currentActivity = PersonActivity.IDLE;
    public int scheduleOffsetHours;

    public PersonSkills skills = new PersonSkills();
    public PersonNeeds needs = new PersonNeeds();
    public Personality personality = new Personality();
    public HealthState health = new HealthState();
    public WealthState wealth = new WealthState();

    /** Current medium-term plan, refreshed from needs, work, family and local conditions. */
    public Goal currentGoal = Goal.WORK;
    public String goalReason = "No plan assessed yet";
    public int goalUrgency = 25;
    public long goalUpdatedMinute = Long.MIN_VALUE;

    /** Individual memory of the adventurer; ordinary settlement reputation stays aggregate. */
    public int opinionOfPlayer = 0;
    public String lastPlayerMemory = "";
    public long lastPlayerMemoryMinute = Long.MIN_VALUE;

    /** Most recent consequential non-player event in this person's life. */
    public String lastLifeEvent = "";
    public long lastLifeEventMinute = Long.MIN_VALUE;

    /** WorldPosition when traveling on strategic map */
    public WorldPosition position;

    /** Person type for generation/configuration */
    public PersonType type = PersonType.CITIZEN;

    /** Minutes per year for age calculation. */
    private static final long MINUTES_PER_YEAR = 60L * 24 * 30 * 12;

    public Person() {
        // Default constructor
    }

    public Person(long id, String givenName, String familyName, Sex sex, long birthMinute) {
        this.id = id;
        this.givenName = givenName;
        this.familyName = familyName;
        this.sex = sex;
        this.birthMinute = birthMinute;
        this.alive = true;
        this.scheduleOffsetHours = (int) Math.floorMod(id, 3L) - 1;
    }

    /** Age in years at the given world minute. */
    public int getAge(long currentMinute) {
        long endMinute = alive ? currentMinute : (deathMinute != null ? deathMinute : currentMinute);
        return Math.max(0, (int) ((endMinute - birthMinute) / MINUTES_PER_YEAR));
    }

    /** Check if this person is a child (under 18 years old). */
    public boolean isChild(long currentMinute) {
        if (!alive) return false;
        return getAge(currentMinute) < 18;
    }

    /** Check if this person is elderly (65 years or older). */
    public boolean isElderly(long currentMinute) {
        if (!alive) return false;
        return getAge(currentMinute) >= 65;
    }

    /** Records a player interaction and moves this person's individual opinion. */
    public void rememberPlayerInteraction(int opinionDelta, String memory, long minute) {
        opinionOfPlayer = Math.max(-100, Math.min(100, opinionOfPlayer + opinionDelta));
        lastPlayerMemory = memory == null ? "" : memory;
        lastPlayerMemoryMinute = minute;
    }

    /** Records a consequential event such as migration, victimisation or taking work. */
    public void rememberLifeEvent(String event, long minute) {
        lastLifeEvent = event == null ? "" : event;
        lastLifeEventMinute = minute;
    }

    /** Mark this person as dead and record the death minute. */
    public void die(long deathMinute, DeathCause cause) {
        this.alive = false;
        this.deathMinute = deathMinute;
        this.deathCause = cause;
    }

    public Long getDeathMinute() {
        return deathMinute;
    }

    @Override
    public String toString() {
        return String.format("Person[id=%d, %s %s, alive=%b]", id, givenName, familyName, alive);
    }

    /** Enums for person categorization */
    public enum PersonType { CITIZEN, MERCHANT, SOLDIER, NOBLE, SERVANT, CHILD }

    /** Medium-term aims used by employment, migration, crime and recruitment decisions. */
    public enum Goal {
        RECOVER, FIND_FOOD, FIND_WORK, SUPPORT_FAMILY, SERVE_REALM,
        SEEK_OFFICE, BUILD_WEALTH, SEEK_SAFER_HOME, SOCIALIZE, GROW_UP, WORK
    }

    /** Enums for sex */
    public enum Sex { MALE, FEMALE }

    /** Cause of death for historical records. */
    public enum DeathCause { OLD_AGE, DISEASE, STARVATION, WAR, ACCIDENT, OTHER }

    public DeathCause deathCause;
}