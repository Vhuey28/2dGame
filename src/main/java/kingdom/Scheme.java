package kingdom;

public class Scheme {
    public enum Type { FABRICATE_CLAIM, ASSASSINATE, SPY_NETWORK }

    public Type type;
    public Character schemer;
    public Character target;
    public Kingdom targetKingdom;
    public int progress = 0;
    public int durationTicks;
    public boolean discovered = false;
    public boolean active = true;

    public Scheme(Type type, Character schemer, Character target, Kingdom targetKingdom, int durationTicks) {
        this.type = type;
        this.schemer = schemer;
        this.target = target;
        this.targetKingdom = targetKingdom;
        this.durationTicks = durationTicks;
    }
}
