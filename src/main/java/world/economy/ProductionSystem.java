package world.economy;

import world.geography.Settlement;
import world.Household;

/**
 * Production system: processes daily production at workplaces.
 * Workplaces estimate expected profit and local demand weekly, choose a recipe,
 * and execute daily within labor and input limits.
 */
public final class ProductionSystem {
    private final MarketSystem marketSystem;

    public ProductionSystem(MarketSystem marketSystem) {
        this.marketSystem = marketSystem;
    }

    /**
     * Process one day of production at all workplaces.
     * Called by the simulation at daily intervals.
     */
    public void processDay(java.util.Map<Long, Settlement> settlements,
                           java.util.Map<Long, world.Person> people,
                           java.util.Map<Long, world.Household> households) {
        for (Settlement settlement : settlements.values()) {
            for (Workplace workplace : settlement.workplaces) {
                produceAtWorkplace(workplace, people, households);
            }
        }
    }

    /**
     * Produce one day's output at a workplace.
     */
    private void produceAtWorkplace(Workplace workplace,
                                    java.util.Map<Long, world.Person> people,
                                    java.util.Map<Long, world.Household> households) {
        // If no recipes, skip
        if (workplace.recipes.isEmpty()) return;

        // Find the best recipe based on expected profit and local demand
        ProductionRecipe bestRecipe = null;
        double bestScore = -1.0;

        for (ProductionRecipe recipe : workplace.recipes) {
            if (!recipe.canProduce(workplace.inventory)) continue;

            double demand = marketSystem.getDemandScore(workplace.settlementId, recipe.outputs.keySet().iterator().next());
            double score = demand * (1.0 + workplace.productionEfficiency);

            if (score > bestScore) {
                bestScore = score;
                bestRecipe = recipe;
            }
        }

        if (bestRecipe != null) {
            int availableLabor = workplace.getWorkerCount() * 8; // 8 hours per worker per day
            int laborRequired = (int) bestRecipe.dailyLaborRequired;
            int batches = Math.min(availableLabor / Math.max(1, laborRequired), bestRecipe.dailyBatchSize);

            // Consume inputs
            for (int i = 0; i < batches; i++) {
                if (!bestRecipe.consumeInputs(workplace.inventory)) break;
            }

            // Produce outputs
            if (batches > 0) {
                bestRecipe.produce(workplace.outputInventory, batches);
            }

            // Distribute output to owner household
            if (workplace.ownerHouseholdId != null) {
                Household ownerHousehold = households.get(workplace.ownerHouseholdId);
                if (ownerHousehold != null) {
                    for (GoodType good : GoodType.values()) {
                        int amount = workplace.outputInventory.getQuantity(good);
                        if (amount > 0) {
                            // Transfer from workplace output to household inventory
                            workplace.outputInventory.remove(good, amount);
                            ownerHousehold.inventory.add(good, amount);
                        }
                    }
                }
            }
        }
    }
}
