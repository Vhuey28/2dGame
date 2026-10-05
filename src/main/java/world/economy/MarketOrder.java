package world.economy;

/**
 * A market order: either a buy or sell request for a good.
 * Contains: owner, good, side, quantity, limit price, created time, expiry.
 * For daily aggregate matching, all orders are matched at the end of the day.
 */
public final class MarketOrder {
    public final long ownerId;
    public final GoodType good;
    public final OrderSide side;
    public int quantity;
    public final long limitPrice; // per unit
    public final long createdMinute;
    public final long expiryMinute;

    public MarketOrder(long ownerId, GoodType good, OrderSide side, int quantity,
                       long limitPrice, long createdMinute, long expiryMinute) {
        this.ownerId = ownerId;
        this.good = good;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.createdMinute = createdMinute;
        this.expiryMinute = expiryMinute;
    }

    /** Check if this order has expired. */
    public boolean isExpired(long currentMinute) {
        return currentMinute > expiryMinute;
    }

    /** Get total value of this order. */
    public long getTotalValue() {
        return (long) quantity * limitPrice;
    }

    @Override
    public String toString() {
        return String.format("MarketOrder[%s %d %s @ %d, owner=%d, created=%d, expires=%d]",
            side, quantity, good, limitPrice, ownerId, createdMinute, expiryMinute);
    }

    public enum OrderSide { BUY, SELL }
}
