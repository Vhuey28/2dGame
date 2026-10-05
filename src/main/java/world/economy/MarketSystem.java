package world.economy;

import world.geography.Settlement;

import java.util.Map;

/**
 * Market system: provides demand scores for goods in settlements.
 * Demand score is based on current inventory, recent trade volume, and price trends.
 */
public final class MarketSystem {
    private final Map<Long, Settlement> settlements;

    public MarketSystem(Map<Long, Settlement> settlements) {
        this.settlements = settlements;
    }

    /**
     * Get demand score for a good in a settlement.
     * Higher score means higher demand.
     *
     * @param settlementId the settlement
     * @param good the good type
     * @return demand score (>= 0.0)
     */
    public double getDemandScore(long settlementId, GoodType good) {
        Settlement settlement = settlements.get(settlementId);
        if (settlement == null) {
            return 1.0; // default demand
        }

        // Demand is inversely related to current inventory in public stockpile
        int currentStock = settlement.publicStockpile.getQuantity(good);
        // Assume ideal stock is 30 days of consumption for a settlement of size 100
        // We don't have population here, so use a fixed baseline
        double idealStock = 100.0; // arbitrary baseline
        double stockRatio = currentStock / idealStock;
        // Demand is high when stock is low: use 2.0 - stockRatio, but bound between 0.5 and 2.0
        double demand = 2.0 - Math.min(stockRatio, 2.0);
        if (demand < 0.5) {
            demand = 0.5;
        }

        // Adjust by recent market activity: if volume traded is high, demand is high
        long volumeTraded = settlement.market.getVolumeTraded().getOrDefault(good, 0L);
        if (volumeTraded > 50) {
            demand *= 1.2;
        } else if (volumeTraded > 20) {
            demand *= 1.1;
        }

        // Adjust by price relative to base price: if price is high, demand might be high (or low depending on elasticity)
        long lastPrice = settlement.market.getLastPrice(good);
        double basePrice = GoodsCatalog.getBasePrice(good);
        if (basePrice > 0) {
            double priceRatio = lastPrice / basePrice;
            // If price is significantly above base, assume high demand (or low supply)
            if (priceRatio > 1.5) {
                demand *= 1.3;
            } else if (priceRatio < 0.8) {
                demand *= 0.9; // low price might indicate low demand or high supply
            }
        }

        return demand;
    }

    /**
     * Clear all markets in the world at the end of the day.
     * Matches buy/sell orders and updates inventories/accounts.
     */
    public void clearMarket(long currentMinute,
                            Map<Long, Inventory> inventories,
                            Map<Long, MoneyAccount> accounts) {
        for (Settlement settlement : settlements.values()) {
            settlement.market.clearMarket(currentMinute, inventories, accounts);
        }
    }
}