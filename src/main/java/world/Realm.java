package world;

import world.economy.MoneyAccount;

import java.util.HashSet;
import java.util.Set;

/**
 * Realm (kingdom) model. Represents a political entity that commands resources
 * and conducts diplomacy.
 */
public final class Realm {
    public long id;
    public String name;
    public long governmentId;
    public long rulerTitleId;
    public Long rulerPersonId;
    public Long capitalSettlementId;
    public Set<Long> controlledProvinceIds = new HashSet<>();
    public Set<Long> vassalRealmIds = new HashSet<>();
    public MoneyAccount treasury;
    public double legitimacy = 50.0;
    public double stability = 50.0;
    public double warExhaustion = 0.0;

    /** Current court plan; armies and the UI read the same target and reason. */
    public Strategy strategy = Strategy.DEVELOP;
    public Long strategyTargetSettlementId;
    public String strategyReason = "No plan assessed yet";
    public long strategyUpdatedMinute = Long.MIN_VALUE;

    public enum Strategy {
        DEVELOP, CONSOLIDATE, DEFEND, RELIEVE_SIEGE, CONQUER, REBUILD, SEEK_PEACE
    }

    public Realm() {
        this.treasury = new MoneyAccount(0L);
    }

    public Realm(long id, String name) {
        this.id = id;
        this.name = name;
        this.treasury = new MoneyAccount(0L);
    }

    @Override
    public String toString() {
        return String.format("Realm[id=%d, name=%s, treasury=%d]", id, name, treasury.copperCoins);
    }
}