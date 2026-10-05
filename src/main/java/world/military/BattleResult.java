package world.military;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import world.economy.GoodType;

/** Immutable tactical outcome reconciled into the strategic world exactly once. */
public final class BattleResult {
    public final long battleId;
    public final WinningSide winner;
    public final List<BattleCasualty> casualties;
    public final List<Long> prisonerPersonIds;
    public final Map<GoodType, Integer> capturedGoods;
    public final Map<Long, Double> regimentExperienceGain;
    public final RetreatOutcome retreat;
    public final long tacticalDurationMinutes;

    public BattleResult(long battleId, WinningSide winner, List<BattleCasualty> casualties,
            List<Long> prisonerPersonIds, Map<GoodType, Integer> capturedGoods,
            Map<Long, Double> regimentExperienceGain, RetreatOutcome retreat,
            long tacticalDurationMinutes) {
        this.battleId = battleId;
        this.winner = winner;
        this.casualties = Collections.unmodifiableList(new ArrayList<>(casualties));
        this.prisonerPersonIds = Collections.unmodifiableList(new ArrayList<>(prisonerPersonIds));
        EnumMap<GoodType, Integer> goods = new EnumMap<>(GoodType.class);
        goods.putAll(capturedGoods);
        this.capturedGoods = Collections.unmodifiableMap(goods);
        this.regimentExperienceGain = Collections.unmodifiableMap(new HashMap<>(regimentExperienceGain));
        this.retreat = retreat;
        this.tacticalDurationMinutes = Math.max(1L, tacticalDurationMinutes);
    }

    public enum WinningSide { ATTACKER, DEFENDER, DRAW }
    public enum CasualtyOutcome { UNHARMED, WOUNDED, SEVERELY_WOUNDED, KILLED, CAPTURED, MISSING_DESERTED }
    public enum RetreatOutcome { NONE, ATTACKER_WITHDREW, DEFENDER_WITHDREW, BOTH_WITHDREW }

    public static final class BattleCasualty {
        public final long personId;
        public final long regimentId;
        public final long armyId;
        public final CasualtyOutcome outcome;
        public final double healthDamage;

        public BattleCasualty(long personId, long regimentId, long armyId,
                CasualtyOutcome outcome, double healthDamage) {
            this.personId = personId;
            this.regimentId = regimentId;
            this.armyId = armyId;
            this.outcome = outcome;
            this.healthDamage = Math.max(0.0, healthDamage);
        }
    }
}
