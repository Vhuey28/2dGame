package kingdom;

public class War {
    public Kingdom attacker, defender;
    public CasusBelli reason;
    public int warScoreAttacker = 0;

    public enum CasusBelli { CONQUEST, INDEPENDENCE, CLAIM_THRONE, HOLY_WAR, ASSASSINATION_ATTEMPT }

    public War(Kingdom attacker, Kingdom defender, CasusBelli reason) {
        this.attacker = attacker;
        this.defender = defender;
        this.reason = reason;
    }
}
