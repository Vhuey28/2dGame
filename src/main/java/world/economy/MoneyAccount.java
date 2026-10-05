package world.economy;

/**
 * Stub placeholder for MoneyAccount. Full implementation in later phases.
 * Uses long for copper coins to avoid floating-point money issues.
 */
public final class MoneyAccount {
    public long copperCoins;

    public MoneyAccount() {
        this.copperCoins = 0L;
    }

    public MoneyAccount(long initialCoins) {
        if (initialCoins < 0) throw new IllegalArgumentException("Initial coins cannot be negative");
        this.copperCoins = initialCoins;
    }

    public void add(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot add negative amount");
        this.copperCoins += amount;
    }

    public boolean subtract(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Cannot subtract negative amount");
        if (this.copperCoins < amount) {
            return false; // Insufficient funds
        }
        this.copperCoins -= amount;
        return true;
    }

    @Override
    public String toString() {
        return String.format("MoneyAccount[coins=%d]", copperCoins);
    }
}