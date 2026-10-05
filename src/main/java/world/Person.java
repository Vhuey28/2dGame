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

    public PersonSkills skills = new PersonSkills();
    public PersonNeeds needs = new PersonNeeds();
    public Personality personality = new Personality();
    public HealthState health = new HealthState();
    public WealthState wealth = new WealthState();

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

    /** Enums for sex */
    public enum Sex { MALE, FEMALE }

    /** Cause of death for historical records. */
    public enum DeathCause { OLD_AGE, DISEASE, STARVATION, WAR, ACCIDENT, OTHER }

    public DeathCause deathCause;
}