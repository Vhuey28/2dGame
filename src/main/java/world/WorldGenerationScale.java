package world;

/** Opt-in generation dimensions. The default campaign continues to use the six-settlement slice. */
public final class WorldGenerationScale {
    public static final WorldGenerationScale TARGET_CONTINENT = new WorldGenerationScale(
            ScaleBudgets.TARGET_REALMS, ScaleBudgets.TARGET_SETTLEMENTS, ScaleBudgets.TARGET_PEOPLE);

    public final int realms;
    public final int settlements;
    public final int people;

    public WorldGenerationScale(int realms, int settlements, int people) {
        if (realms < 1 || settlements < realms || people < 0) {
            throw new IllegalArgumentException("Scale needs at least one settlement per realm");
        }
        this.realms = realms;
        this.settlements = settlements;
        this.people = people;
    }
}
