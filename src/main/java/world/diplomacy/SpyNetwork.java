package world.diplomacy;

/** Asymmetric intelligence capability owned by one realm inside another. */
public final class SpyNetwork {
    public long id;
    public long ownerRealmId;
    public long targetRealmId;
    public double strength;
    public double informationQuality;
    public boolean exposed;
    public long updatedMinute;

    public SpyNetwork(long id, long ownerRealmId, long targetRealmId, double strength, long minute) {
        this.id = id;
        this.ownerRealmId = ownerRealmId;
        this.targetRealmId = targetRealmId;
        this.strength = clamp(strength);
        this.informationQuality = clamp(strength * 0.8);
        this.updatedMinute = minute;
    }

    public void improve(double amount, long minute) {
        strength = clamp(strength + amount);
        informationQuality = clamp(informationQuality + amount * 0.75);
        updatedMinute = minute;
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(100.0, value)); }
}
