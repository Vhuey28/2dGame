package world.economy;

import java.util.EnumMap;

/**
 * A production recipe defines inputs, outputs, and modifiers for a batch of production.
 * Example: Farm daily batch: inputs seed grain + labor -> outputs grain.
 * Example: Smithy daily batch: inputs iron ore + labor -> outputs tools or weapons.
 */
public final class ProductionRecipe {
    public final String name;
    public final EnumMap<GoodType, Integer> inputs;
    public final EnumMap<GoodType, Integer> outputs;
    public final double dailyLaborRequired;
    public final int dailyBatchSize; // number of output units per batch
    public final String skillRequired; // skill type required (e.g. "farming", "crafting")

    public ProductionRecipe(String name, EnumMap<GoodType, Integer> inputs,
                            EnumMap<GoodType, Integer> outputs, double dailyLaborRequired,
                            String skillRequired) {
        this.name = name;
        this.inputs = inputs;
        this.outputs = outputs;
        this.dailyLaborRequired = dailyLaborRequired;
        this.dailyBatchSize = 1;
        this.skillRequired = skillRequired;
    }

    public ProductionRecipe(String name, EnumMap<GoodType, Integer> inputs,
                            EnumMap<GoodType, Integer> outputs, double dailyLaborRequired,
                            int dailyBatchSize, String skillRequired) {
        this.name = name;
        this.inputs = inputs;
        this.outputs = outputs;
        this.dailyLaborRequired = dailyLaborRequired;
        this.dailyBatchSize = dailyBatchSize;
        this.skillRequired = skillRequired;
    }

    /**
     * Check if this recipe's mandatory inputs are available in the given inventory.
     * Returns true if all required inputs are present.
     */
    public boolean canProduce(Inventory inventory) {
        for (java.util.Map.Entry<GoodType, Integer> entry : inputs.entrySet()) {
            if (inventory.getQuantity(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Consume input goods from inventory. Returns false if insufficient. */
    public boolean consumeInputs(Inventory inventory) {
        // First check all inputs
        for (java.util.Map.Entry<GoodType, Integer> entry : inputs.entrySet()) {
            if (inventory.getQuantity(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        // Then consume
        for (java.util.Map.Entry<GoodType, Integer> entry : inputs.entrySet()) {
            inventory.remove(entry.getKey(), entry.getValue());
        }
        return true;
    }

    /** Produce output goods into inventory. */
    public void produce(Inventory inventory, int batches) {
        for (java.util.Map.Entry<GoodType, Integer> entry : outputs.entrySet()) {
            inventory.add(entry.getKey(), entry.getValue() * batches);
        }
    }

    @Override
    public String toString() {
        return String.format("ProductionRecipe[name=%s, labor=%.1f, skill=%s]",
            name, dailyLaborRequired, skillRequired);
    }
}
