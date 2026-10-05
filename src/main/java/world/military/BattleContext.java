package world.military;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import world.geography.Province;

/** Immutable boundary snapshot passed from strategic simulation to tactical combat. */
public final class BattleContext {
    public final long battleId;
    public final long strategicMinute;
    public final BattleSide attacker;
    public final BattleSide defender;
    public final Province.TerrainType terrain;
    public final WeatherType weather;
    public final boolean playerInvolved;

    public BattleContext(long battleId, long strategicMinute, BattleSide attacker,
            BattleSide defender, Province.TerrainType terrain, WeatherType weather,
            boolean playerInvolved) {
        this.battleId = battleId;
        this.strategicMinute = strategicMinute;
        this.attacker = attacker;
        this.defender = defender;
        this.terrain = terrain;
        this.weather = weather;
        this.playerInvolved = playerInvolved;
    }

    public boolean containsPerson(long personId) {
        return attacker.containsPerson(personId) || defender.containsPerson(personId);
    }

    public BattleSide sideForArmy(long armyId) {
        if (attacker.armyId == armyId) return attacker;
        if (defender.armyId == armyId) return defender;
        return null;
    }

    public enum WeatherType { CLEAR, RAIN, FOG, SNOW }

    public static final class BattleSide {
        public final long armyId;
        public final long realmId;
        public final long commanderPersonId;
        public final double startingMorale;
        public final double startingFatigue;
        public final List<TacticalCombatant> combatants;

        public BattleSide(long armyId, long realmId, long commanderPersonId,
                double startingMorale, double startingFatigue,
                List<TacticalCombatant> combatants) {
            this.armyId = armyId;
            this.realmId = realmId;
            this.commanderPersonId = commanderPersonId;
            this.startingMorale = startingMorale;
            this.startingFatigue = startingFatigue;
            this.combatants = Collections.unmodifiableList(new ArrayList<>(combatants));
        }

        public boolean containsPerson(long personId) {
            return combatants.stream().anyMatch(combatant -> combatant.sourcePersonId == personId);
        }
    }

    public static final class TacticalCombatant {
        public final long sourcePersonId;
        public final long sourceRegimentId;
        public final long sourceArmyId;
        public final TacticalRole role;
        public final double attackModifier;
        public final double defenseModifier;
        public final double accuracyModifier;
        public final double morale;

        public TacticalCombatant(long sourcePersonId, long sourceRegimentId, long sourceArmyId,
                TacticalRole role, double attackModifier, double defenseModifier,
                double accuracyModifier, double morale) {
            this.sourcePersonId = sourcePersonId;
            this.sourceRegimentId = sourceRegimentId;
            this.sourceArmyId = sourceArmyId;
            this.role = role;
            this.attackModifier = attackModifier;
            this.defenseModifier = defenseModifier;
            this.accuracyModifier = accuracyModifier;
            this.morale = morale;
        }
    }

    public enum TacticalRole { MELEE, SPEAR, ARCHER, CAVALRY, COMMANDER }
}
