package world;

/**
 * Registry of all persistent world entities. Owns the canonical maps
 * indexed by stable IDs. No Swing, sprites, or audio — purely data
 * and computation for headless simulation.
 */
public final class WorldState {
    /** All persistent people in the world. Never null after init. */
    public final java.util.Map<Long, Person> people = new java.util.HashMap<>();

    /** All persistent households. Never null after init. */
    public final java.util.Map<Long, Household> households = new java.util.HashMap<>();

    /** All persistent settlements. Never null after init. */
    public final java.util.Map<Long, world.geography.Settlement> settlements = new java.util.HashMap<>();

    /** All persistent realms (kingdoms). Never null after init. */
    public final java.util.Map<Long, Realm> realms = new java.util.HashMap<>();

    /** All persistent armies. Never null after init. */
    public final java.util.Map<Long, Army> armies = new java.util.HashMap<>();

    /** All persistent caravans. Never null after init. */
    public final java.util.Map<Long, Caravan> caravans = new java.util.HashMap<>();

    /** All persistent wars. Never null after init. */
    public final java.util.Map<Long, War> wars = new java.util.HashMap<>();

    public final java.util.Map<Long, world.economy.Workplace> workplaces = new java.util.HashMap<>();

    /** All generated IDs for validation. Never null after init. */
    public final IdGenerator idGenerator = new IdGenerator();

    /** Geography system for provinces, settlements, roads, route graph. */
    public final world.geography.GeographySystem geography = new world.geography.GeographySystem();
}