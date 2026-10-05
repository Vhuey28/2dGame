package world;

/**
 * Registry of all persistent world entities. Owns the canonical maps
 * indexed by stable IDs. No Swing, sprites, or audio — purely data
 * and computation for headless simulation.
 */
public final class WorldState {
    /** Geography owns provinces, settlements, buildings, roads, and routes. */
    public final world.geography.GeographySystem geography = new world.geography.GeographySystem();

    /** All persistent people in the world. Never null after init. */
    public final java.util.Map<Long, Person> people = new java.util.HashMap<>();

    /** All persistent households. Never null after init. */
    public final java.util.Map<Long, Household> households = new java.util.HashMap<>();

    /**
     * Compatibility view of GeographySystem's canonical settlement registry.
     * Both references point to the same map; there is only one source of truth.
     */
    public final java.util.Map<Long, world.geography.Settlement> settlements = geography.getSettlements();

    /** All persistent realms (kingdoms). Never null after init. */
    public final java.util.Map<Long, Realm> realms = new java.util.HashMap<>();

    /** All persistent armies. Never null after init. */
    public final java.util.Map<Long, Army> armies = new java.util.HashMap<>();

    /** All persistent caravans. Never null after init. */
    public final java.util.Map<Long, Caravan> caravans = new java.util.HashMap<>();

    /** All persistent wars. Never null after init. */
    public final java.util.Map<Long, War> wars = new java.util.HashMap<>();

    /** Political institutions and legal state. */
    public final java.util.Map<Long, world.politics.Government> governments = new java.util.HashMap<>();
    public final java.util.Map<Long, world.politics.Title> titles = new java.util.HashMap<>();
    public final java.util.Map<Long, world.politics.Office> offices = new java.util.HashMap<>();
    public final java.util.Map<Long, world.politics.PoliticalFaction> politicalFactions = new java.util.HashMap<>();
    public final java.util.Map<Long, world.politics.Claim> claims = new java.util.HashMap<>();

    /** Bilateral diplomacy, public treaties, grievances, and secret intelligence operations. */
    public final java.util.Map<Long, world.diplomacy.DiplomaticState> diplomaticStates = new java.util.HashMap<>();
    public final java.util.Map<Long, world.diplomacy.Treaty> treaties = new java.util.HashMap<>();
    public final java.util.Map<Long, world.diplomacy.Grievance> grievances = new java.util.HashMap<>();
    public final java.util.Map<Long, world.diplomacy.SpyNetwork> spyNetworks = new java.util.HashMap<>();
    public final java.util.Map<Long, world.diplomacy.Scheme> schemes = new java.util.HashMap<>();

    /** Player's persistent adventurer state in Campaign mode, when present. */
    public PlayerCampaignState player;

    public final java.util.Map<Long, world.economy.Workplace> workplaces = new java.util.HashMap<>();

    /** All generated IDs for validation. Never null after init. */
    public final IdGenerator idGenerator = new IdGenerator();
}
