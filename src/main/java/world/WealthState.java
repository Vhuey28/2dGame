package world;

/**
 * Wealth state for a person (world.md section 9.1).
 * Tracks portable personal wealth alongside household-level economy.
 */
public final class WealthState {
    public double netWorth = 0.0; // monetary wealth, for display/reputation only
    public long ownedItemsCount = 0; // portable possessions count
    public boolean isPoor = false;
    public boolean isWealthy = false;

    /** Update poverty/wealth flags based on net worth. */
    public void evaluate() {
        isPoor = netWorth < 100.0;
        isWealthy = netWorth >= 10000.0;
    }

    @Override
    public String toString() {
        return String.format("Wealth[netWorth=%.0f, items=%d, poor=%b, wealthy=%b]",
            netWorth, ownedItemsCount, isPoor, isWealthy);
    }
}