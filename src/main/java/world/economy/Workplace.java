package world.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * A workplace that produces goods. Examples: Farm, Lumber Camp, Mine, Smithy, Weaver.
 * Workplaces evaluate expected profit and local demand weekly, choosing a recipe
 * and target quantity, then execute daily within labor and input limits.
 */
public final class Workplace {
    public long id;
    public String name;
    public long settlementId;
    public Long ownerHouseholdId; // Household that owns this workplace
    public List<Long> workerIds = new ArrayList<>(); // People employed here
    public Inventory inventory = new Inventory(); // Input materials
    public Inventory outputInventory = new Inventory(); // Produced goods
    public List<ProductionRecipe> recipes = new ArrayList<>();
    public double productionEfficiency = 1.0; // modifier based on worker skill, building quality
    public double seasonalModifier = 1.0; // harvest modifiers
    public int maxWorkers = 4;

    public Workplace(long id, String name, long settlementId) {
        this.id = id;
        this.name = name;
        this.settlementId = settlementId;
    }

    /** Add a worker to this workplace. Returns false if at capacity. */
    public boolean addWorker(long personId) {
        if (workerIds.size() >= maxWorkers) return false;
        if (workerIds.contains(personId)) return false;
        workerIds.add(personId);
        return true;
    }

    /** Remove a worker from this workplace. */
    public boolean removeWorker(long personId) {
        return workerIds.remove(Long.valueOf(personId));
    }

    /** Get current number of workers. */
    public int getWorkerCount() {
        return workerIds.size();
    }

    /** Check if any recipe's inputs are available. */
    public boolean canProduceAny() {
        for (ProductionRecipe recipe : recipes) {
            if (recipe.canProduce(inventory)) return true;
        }
        return false;
    }

    /** Produce one day's output for all applicable recipes. */
    public void produceDaily() {
        for (ProductionRecipe recipe : recipes) {
            int batches = 0;
            while (recipe.consumeInputs(inventory)) {
                batches++;
                if (batches >= recipe.dailyBatchSize) break;
            }
            if (batches > 0) {
                // Apply efficiency modifier
                int modifiedBatches = (int) Math.round(batches * productionEfficiency);
                if (modifiedBatches > 0) {
                    recipe.produce(outputInventory, modifiedBatches);
                }
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Workplace[id=%d, name=%s, workers=%d/%d]", id, name, workerIds.size(), maxWorkers);
    }
}
