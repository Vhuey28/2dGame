package world.economy;

/**
 * Definition of a good: type, display name, base price, weight, perishability.
 * Used by markets to determine pricing and by producers to determine outputs.
 */
public final class GoodDefinition {
    public final GoodType type;
    public final String displayName;
    public final double basePrice; // in copper coins
    public final double weight;    // in pounds
    public final boolean perishable;
    public final int spoilageDays; // days until spoiled if perishable

    public GoodDefinition(GoodType type, String displayName, double basePrice, double weight,
                           boolean perishable, int spoilageDays) {
        this.type = type;
        this.displayName = displayName;
        this.basePrice = basePrice;
        this.weight = weight;
        this.perishable = perishable;
        this.spoilageDays = perishable ? spoilageDays : 0;
    }

    @Override
    public String toString() {
        return String.format("GoodDefinition[type=%s, name=%s, basePrice=%.1f, weight=%.1f, perishable=%b]",
            type, displayName, basePrice, weight, perishable);
    }
}