package world;

/** Mutable local-scene presentation state transferred through explicit campaign adapters. */
public final class LocalPlayerState {
    public int health;
    public final long settlementId;

    public LocalPlayerState(int health, long settlementId) {
        this.health = Math.max(0, Math.min(100, health));
        this.settlementId = settlementId;
    }
}
