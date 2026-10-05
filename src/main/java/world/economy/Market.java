package world.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Market for a settlement. Handles buy/sell orders and daily matching.
 * Uses daily aggregate matching: collect orders, sort buys descending, sells ascending,
 * match while buy price >= sell price, transfer goods and money, record clearing price.
 */
public final class Market {
    public final long id;
    private final List<MarketOrder> buyOrders = new ArrayList<>();
    private final List<MarketOrder> sellOrders = new ArrayList<>();
    private final java.util.Map<GoodType, Long> lastClearingPrices = new java.util.HashMap<>();
    private final java.util.Map<GoodType, Long> volumeTraded = new java.util.HashMap<>();

    public Market(long id) {
        this.id = id;
    }

    /** Add a buy order. */
    public void addBuyOrder(MarketOrder order) {
        if (order.side != MarketOrder.OrderSide.BUY)
            throw new IllegalArgumentException("Order must be BUY");
        buyOrders.add(order);
    }

    /** Add a sell order. */
    public void addSellOrder(MarketOrder order) {
        if (order.side != MarketOrder.OrderSide.SELL)
            throw new IllegalArgumentException("Order must be SELL");
        sellOrders.add(order);
    }

    /**
     * Daily market clearing. Matches buy and sell orders per good type.
     * Returns a map of goods traded and their clearing prices.
     */
    public java.util.Map<GoodType, MarketResult> clearMarket(long currentMinute,
                                                             java.util.Map<Long, Inventory> inventories,
                                                             java.util.Map<Long, MoneyAccount> accounts) {
        // Remove expired orders
        buyOrders.removeIf(o -> o.isExpired(currentMinute));
        sellOrders.removeIf(o -> o.isExpired(currentMinute));

        java.util.Map<GoodType, MarketResult> results = new java.util.HashMap<>();

        // Group orders by good type and match
        for (GoodType good : GoodType.values()) {
            List<MarketOrder> buys = new ArrayList<>();
            List<MarketOrder> sells = new ArrayList<>();

            for (MarketOrder o : buyOrders) {
                if (o.good == good) buys.add(o);
            }
            for (MarketOrder o : sellOrders) {
                if (o.good == good) sells.add(o);
            }

            if (buys.isEmpty() || sells.isEmpty()) continue;

            // Sort buys descending by price, sells ascending
            buys.sort(Comparator.comparingLong(o -> -o.limitPrice));
            sells.sort(Comparator.comparingLong(o -> o.limitPrice));

            int totalVolume = 0;
            long totalValue = 0;
            long lastPrice = 0;

            // Match while buy price >= sell price
            int buyIdx = 0, sellIdx = 0;
            while (buyIdx < buys.size() && sellIdx < sells.size()) {
                MarketOrder buy = buys.get(buyIdx);
                MarketOrder sell = sells.get(sellIdx);

                if (buy.limitPrice >= sell.limitPrice) {
                    int tradeQty = Math.min(buy.quantity, sell.quantity);
                    long tradePrice = sell.limitPrice; // clearing price at sell price

                    // Transfer goods
                    Inventory sellerInv = inventories.get(sell.ownerId);
                    Inventory buyerInv = inventories.get(buy.ownerId);
                    MoneyAccount sellerAcct = accounts.get(sell.ownerId);
                    MoneyAccount buyerAcct = accounts.get(buy.ownerId);

                    if (sellerInv != null && buyerInv != null &&
                        sellerAcct != null && buyerAcct != null &&
                        sellerInv.has(good, tradeQty)) {

                        long totalCost = (long) tradeQty * tradePrice;
                        // Validate and debit money before moving goods so a failed
                        // purchase cannot duplicate inventory in the buyer.
                        if (buyerAcct.subtract(totalCost)) {
                            sellerInv.remove(good, tradeQty);
                            buyerInv.add(good, tradeQty);
                            sellerAcct.add(totalCost);

                            buy.quantity -= tradeQty;
                            sell.quantity -= tradeQty;
                            totalVolume += tradeQty;
                            totalValue += totalCost;
                            lastPrice = tradePrice;
                        } else {
                            buyIdx++;
                            continue;
                        }
                    } else {
                        // Invalid state - skip this pair
                        break;
                    }

                    // Remove filled orders
                    if (buy.quantity <= 0) buyIdx++;
                    if (sell.quantity <= 0) sellIdx++;
                } else {
                    // No more matches possible
                    break;
                }
            }

            if (totalVolume > 0) {
                long clearingPrice = lastPrice;
                lastClearingPrices.put(good, clearingPrice);
                volumeTraded.put(good, (long) totalVolume);
                results.put(good, new MarketResult(good, totalVolume, clearingPrice));
            }
        }

        // Remove filled/empty orders
        buyOrders.removeIf(o -> o.quantity <= 0);
        sellOrders.removeIf(o -> o.quantity <= 0);

        return results;
    }

    /** Get last clearing price for a good, or base price if none. */
    public long getLastPrice(GoodType good) {
        return lastClearingPrices.getOrDefault(good, (long) GoodsCatalog.getBasePrice(good));
    }

    public java.util.Map<GoodType, Long> getVolumeTraded() {
        return volumeTraded;
    }

    public int getBuyOrderCount() {
        return buyOrders.size();
    }

    public int getSellOrderCount() {
        return sellOrders.size();
    }

    /** Result of a market clearing for one good. */
    public static class MarketResult {
        public final GoodType good;
        public final int volume;
        public final long clearingPrice;

        public MarketResult(GoodType good, int volume, long clearingPrice) {
            this.good = good;
            this.volume = volume;
            this.clearingPrice = clearingPrice;
        }

        @Override
        public String toString() {
            return String.format("MarketResult[good=%s, volume=%d, price=%d]", good, volume, clearingPrice);
        }
    }
}