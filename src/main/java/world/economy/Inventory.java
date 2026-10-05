package world.economy;

import java.util.EnumMap;

/**
 * Inventory stores goods quantities keyed by GoodType.
 * All mutations go through methods that reject negative values.
 */
public final class Inventory {
    private final EnumMap<GoodType, Integer> quantities = new EnumMap<>(GoodType.class);

    public Inventory() {
        // Initialize all goods to 0
        for (GoodType type : GoodType.values()) {
            quantities.put(type, 0);
        }
    }

    /** Get quantity of a good type. */
    public int getQuantity(GoodType type) {
        return quantities.getOrDefault(type, 0);
    }

    /** Add quantity (must be non-negative). */
    public void add(GoodType type, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot add negative quantity");
        int current = quantities.getOrDefault(type, 0);
        quantities.put(type, current + amount);
    }

    /** Remove quantity. Returns false if insufficient. */
    public boolean remove(GoodType type, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot remove negative quantity");
        int current = quantities.getOrDefault(type, 0);
        if (current < amount) return false;
        quantities.put(type, current - amount);
        return true;
    }

    /** Transfer goods from this inventory to another. Returns false if insufficient. */
    public boolean transferTo(Inventory destination, GoodType type, int amount) {
        if (destination == null) throw new IllegalArgumentException("Destination cannot be null");
        if (!remove(type, amount)) return false;
        destination.add(type, amount);
        return true;
    }

    /** Check if inventory has at least the specified quantity. */
    public boolean has(GoodType type, int amount) {
        return getQuantity(type) >= amount;
    }

    /** Get total number of goods in inventory (all types combined). */
    public int totalQuantity() {
        int total = 0;
        for (int q : quantities.values()) {
            total += q;
        }
        return total;
    }

    @Override
    public String toString() {
        return "Inventory" + quantities;
    }
}