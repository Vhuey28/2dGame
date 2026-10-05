package world.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import world.Army;
import world.Household;
import world.Person;
import world.SimulationContext;
import world.WorldState;
import world.WorldConfig;
import world.command.CommandResult;
import world.economy.GoodType;
import world.event.WorldEvent;
import world.geography.Province;
import world.geography.Settlement;

/** Immutable strategic/tactical projection and exactly-once reconciliation boundary. */
public final class BattleBridge {
    private final SimulationContext context;
    private final WorldState world;

    public BattleBridge(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public BattleContext createContext(long attackerArmyId, long defenderArmyId,
            boolean playerInvolved, long strategicMinute) {
        Army attacker = requireArmy(attackerArmyId);
        Army defender = requireArmy(defenderArmyId);
        if (attacker.realmId == defender.realmId) throw new IllegalArgumentException("Battle sides must be hostile realms");
        if (attacker.state == Army.ArmyState.DISBANDED || defender.state == Army.ArmyState.DISBANDED) {
            throw new IllegalStateException("Disbanded armies cannot enter battle");
        }
        long battleId = world.idGenerator.next();
        BattleContext contextSnapshot = new BattleContext(battleId, strategicMinute,
                projectSide(attacker), projectSide(defender), terrainAt(attacker),
                weatherAt(strategicMinute), playerInvolved);
        world.battleContexts.put(battleId, contextSnapshot);
        attacker.state = Army.ArmyState.ENGAGED;
        defender.state = Army.ArmyState.ENGAGED;
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), strategicMinute, "BATTLE_CONTEXT_CREATED"));
        return contextSnapshot;
    }

    public BattleResult autoResolve(BattleContext battle) {
        double attackerPower = sidePower(battle.attacker, battle.terrain, battle.weather);
        double defenderPower = sidePower(battle.defender, battle.terrain, battle.weather) * 1.06;
        BattleResult.WinningSide winner = attackerPower >= defenderPower
                ? BattleResult.WinningSide.ATTACKER : BattleResult.WinningSide.DEFENDER;
        BattleContext.BattleSide winningSide = winner == BattleResult.WinningSide.ATTACKER
                ? battle.attacker : battle.defender;
        BattleContext.BattleSide losingSide = winner == BattleResult.WinningSide.ATTACKER
                ? battle.defender : battle.attacker;
        List<BattleResult.BattleCasualty> casualties = new ArrayList<>();
        addAutoCasualties(losingSide, 0.28, casualties);
        addAutoCasualties(winningSide, 0.09, casualties);
        List<Long> prisoners = new ArrayList<>();
        if (losingSide.combatants.size() >= 4) {
            BattleContext.TacticalCombatant prisoner = losingSide.combatants.get(losingSide.combatants.size() - 1);
            boolean alreadyAffected = casualties.stream().anyMatch(c -> c.personId == prisoner.sourcePersonId);
            if (!alreadyAffected) {
                casualties.add(toCasualty(prisoner, BattleResult.CasualtyOutcome.CAPTURED, 0.0));
                prisoners.add(prisoner.sourcePersonId);
            }
        }
        Map<GoodType, Integer> loot = projectedLoot(losingSide.armyId);
        Map<Long, Double> experience = new HashMap<>();
        for (BattleContext.TacticalCombatant combatant : battle.attacker.combatants) {
            experience.put(combatant.sourceRegimentId, 1.5);
        }
        for (BattleContext.TacticalCombatant combatant : battle.defender.combatants) {
            experience.put(combatant.sourceRegimentId, 1.5);
        }
        BattleResult.RetreatOutcome retreat = winner == BattleResult.WinningSide.ATTACKER
                ? BattleResult.RetreatOutcome.DEFENDER_WITHDREW
                : BattleResult.RetreatOutcome.ATTACKER_WITHDREW;
        return new BattleResult(battle.battleId, winner, casualties, prisoners, loot,
                experience, retreat, 45L + casualties.size() * 3L);
    }

    /** Build a result from player-observed tactical outcomes without exposing strategic objects. */
    public BattleResult resultFromTactical(long battleId, BattleResult.WinningSide winner,
            Map<Long, BattleResult.CasualtyOutcome> outcomes, long durationMinutes) {
        BattleContext battle = world.battleContexts.get(battleId);
        if (battle == null) throw new IllegalArgumentException("Unknown battle context");
        List<BattleResult.BattleCasualty> casualties = new ArrayList<>();
        List<Long> prisoners = new ArrayList<>();
        for (Map.Entry<Long, BattleResult.CasualtyOutcome> entry : outcomes.entrySet()) {
            BattleContext.TacticalCombatant combatant = findCombatant(battle, entry.getKey());
            if (combatant == null) throw new IllegalArgumentException("Tactical result contains a non-participant");
            double damage = switch (entry.getValue()) {
                case UNHARMED -> 0.0;
                case WOUNDED -> 30.0;
                case SEVERELY_WOUNDED -> 65.0;
                case KILLED -> 100.0;
                case CAPTURED, MISSING_DESERTED -> 0.0;
            };
            casualties.add(toCasualty(combatant, entry.getValue(), damage));
            if (entry.getValue() == BattleResult.CasualtyOutcome.CAPTURED) prisoners.add(entry.getKey());
        }
        BattleContext.BattleSide loser = winner == BattleResult.WinningSide.ATTACKER
                ? battle.defender : battle.attacker;
        return new BattleResult(battleId, winner, casualties, prisoners,
                projectedLoot(loser.armyId), experienceFor(battle), retreatFor(winner), durationMinutes);
    }

    public CommandResult reconcile(BattleResult result) {
        BattleContext battle = result == null ? null : world.battleContexts.get(result.battleId);
        if (battle == null) return CommandResult.rejected("UNKNOWN_BATTLE", "No matching immutable battle context");
        if (world.reconciledBattleIds.contains(result.battleId)) {
            return CommandResult.rejected("BATTLE_ALREADY_APPLIED", "Battle result was already reconciled");
        }
        String invalid = validateResult(battle, result);
        if (invalid != null) return CommandResult.rejected("INVALID_BATTLE_RESULT", invalid);

        Army attacker = world.armies.get(battle.attacker.armyId);
        Army defender = world.armies.get(battle.defender.armyId);
        Army winner = result.winner == BattleResult.WinningSide.ATTACKER ? attacker
                : result.winner == BattleResult.WinningSide.DEFENDER ? defender : null;
        Army loser = winner == attacker ? defender : winner == defender ? attacker : null;
        for (BattleResult.BattleCasualty casualty : result.casualties) applyCasualty(casualty, winner, battle.strategicMinute);
        transferLoot(result, loser, winner);
        applyExperience(result);
        applyArmyOutcome(result, attacker, defender, winner, loser);

        world.battleResults.put(result.battleId, result);
        world.reconciledBattleIds.add(result.battleId);
        int attackerLosses = countAffected(result, battle.attacker.armyId);
        int defenderLosses = countAffected(result, battle.defender.armyId);
        long winnerArmyId = winner == null ? 0L : winner.id;
        world.battleReports.put(result.battleId, new BattleReport(result.battleId,
                attacker.id, defender.id, winnerArmyId, attackerLosses, defenderLosses,
                battle.strategicMinute));
        if (context.getClock().getWorldMinute() < battle.strategicMinute) {
            context.getClock().setWorldMinute(battle.strategicMinute);
        }
        context.getClock().advance(result.tacticalDurationMinutes);
        long resolvedMinute = context.getClock().getWorldMinute();
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), resolvedMinute, "BATTLE_RECONCILED"));
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), resolvedMinute, "AUTOSAVE_SAFE_POINT"));
        return CommandResult.accepted();
    }

    private BattleContext.BattleSide projectSide(Army army) {
        List<BattleContext.TacticalCombatant> projections = new ArrayList<>();
        Person commander = world.people.get(army.commanderPersonId);
        double command = commander == null ? 0.0 : commander.skills.leadership / 250.0;
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null || !regiment.active) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person person = world.people.get(personId);
                if (person == null || !person.alive || person.capturedByRealmId != null) continue;
                BattleContext.TacticalRole role = roleFor(regiment, person.id == army.commanderPersonId);
                double equipment = regiment.equipmentQuality / 100.0;
                double experience = (person.militaryExperience + regiment.experience) / 200.0;
                projections.add(new BattleContext.TacticalCombatant(person.id, regiment.id, army.id, role,
                        0.75 + equipment * 0.45 + experience + command,
                        0.75 + equipment * 0.50 + experience * 0.7,
                        0.70 + person.skills.martial / 250.0 + experience,
                        Math.max(0.0, Math.min(100.0, army.morale + person.personality.bravery * 0.1))));
            }
        }
        projections.sort(Comparator.comparingLong(combatant -> combatant.sourcePersonId));
        return new BattleContext.BattleSide(army.id, army.realmId, army.commanderPersonId,
                army.morale, army.fatigue, projections);
    }

    private BattleContext.TacticalRole roleFor(Regiment regiment, boolean commander) {
        if (commander) return BattleContext.TacticalRole.COMMANDER;
        return switch (regiment.type) {
            case ARCHER -> BattleContext.TacticalRole.ARCHER;
            case CAVALRY -> BattleContext.TacticalRole.CAVALRY;
            case SPEAR -> BattleContext.TacticalRole.SPEAR;
            case LEVY_INFANTRY, GUARD -> BattleContext.TacticalRole.MELEE;
        };
    }

    private Province.TerrainType terrainAt(Army army) {
        Settlement settlement = world.geography.getSettlement(army.currentSettlementId);
        Province province = settlement == null ? null : world.geography.getProvince(settlement.provinceId);
        return province == null ? Province.TerrainType.PLAINS : province.terrain;
    }

    private BattleContext.WeatherType weatherAt(long minute) {
        long month = minute / WorldConfig.MINUTES_PER_MONTH;
        int pick = (int) Math.floorMod(month + world.idGenerator.getNextId(), 10L);
        if (pick == 0) return BattleContext.WeatherType.FOG;
        if (pick <= 2) return BattleContext.WeatherType.RAIN;
        return BattleContext.WeatherType.CLEAR;
    }

    private double sidePower(BattleContext.BattleSide side, Province.TerrainType terrain,
            BattleContext.WeatherType weather) {
        double total = 0.0;
        for (BattleContext.TacticalCombatant combatant : side.combatants) {
            double role = combatant.role == BattleContext.TacticalRole.ARCHER
                    && weather == BattleContext.WeatherType.RAIN ? 0.8 : 1.0;
            if (combatant.role == BattleContext.TacticalRole.CAVALRY
                    && (terrain == Province.TerrainType.FORESTS || terrain == Province.TerrainType.SWAMP)) role *= 0.75;
            total += (combatant.attackModifier + combatant.defenseModifier) * 0.5 * role;
        }
        return total * (0.5 + side.startingMorale / 100.0)
                * Math.max(0.4, 1.0 - side.startingFatigue / 150.0);
    }

    private void addAutoCasualties(BattleContext.BattleSide side, double fraction,
            List<BattleResult.BattleCasualty> casualties) {
        int count = Math.min(side.combatants.size(), Math.max(1, (int) Math.round(side.combatants.size() * fraction)));
        for (int i = 0; i < count; i++) {
            BattleContext.TacticalCombatant combatant = side.combatants.get(i);
            BattleResult.CasualtyOutcome outcome;
            double roll = context.getRandom("MILITARY").nextDouble();
            if (i == 0 || roll < 0.58) outcome = BattleResult.CasualtyOutcome.KILLED;
            else if (roll < 0.82) outcome = BattleResult.CasualtyOutcome.SEVERELY_WOUNDED;
            else outcome = BattleResult.CasualtyOutcome.WOUNDED;
            double damage = outcome == BattleResult.CasualtyOutcome.KILLED ? 100.0
                    : outcome == BattleResult.CasualtyOutcome.SEVERELY_WOUNDED ? 65.0 : 30.0;
            casualties.add(toCasualty(combatant, outcome, damage));
        }
    }

    private BattleResult.BattleCasualty toCasualty(BattleContext.TacticalCombatant combatant,
            BattleResult.CasualtyOutcome outcome, double damage) {
        return new BattleResult.BattleCasualty(combatant.sourcePersonId,
                combatant.sourceRegimentId, combatant.sourceArmyId, outcome, damage);
    }

    private Map<GoodType, Integer> projectedLoot(long loserArmyId) {
        Army loser = world.armies.get(loserArmyId);
        Map<GoodType, Integer> loot = new EnumMap<>(GoodType.class);
        if (loser == null) return loot;
        for (GoodType good : GoodType.values()) {
            int quantity = loser.supplies.getQuantity(good) / 4;
            if (quantity > 0) loot.put(good, quantity);
        }
        return loot;
    }

    private Map<Long, Double> experienceFor(BattleContext battle) {
        Map<Long, Double> gains = new HashMap<>();
        for (BattleContext.TacticalCombatant combatant : battle.attacker.combatants) gains.put(combatant.sourceRegimentId, 2.0);
        for (BattleContext.TacticalCombatant combatant : battle.defender.combatants) gains.put(combatant.sourceRegimentId, 2.0);
        return gains;
    }

    private BattleResult.RetreatOutcome retreatFor(BattleResult.WinningSide winner) {
        return winner == BattleResult.WinningSide.ATTACKER ? BattleResult.RetreatOutcome.DEFENDER_WITHDREW
                : winner == BattleResult.WinningSide.DEFENDER ? BattleResult.RetreatOutcome.ATTACKER_WITHDREW
                : BattleResult.RetreatOutcome.BOTH_WITHDREW;
    }

    private String validateResult(BattleContext battle, BattleResult result) {
        Set<Long> affected = new HashSet<>();
        for (BattleResult.BattleCasualty casualty : result.casualties) {
            BattleContext.TacticalCombatant projection = findCombatant(battle, casualty.personId);
            if (projection == null || projection.sourceArmyId != casualty.armyId
                    || projection.sourceRegimentId != casualty.regimentId) return "Casualty does not match projected participant";
            if (!affected.add(casualty.personId)) return "Participant has multiple outcomes";
        }
        for (Long prisonerId : result.prisonerPersonIds) {
            boolean captured = result.casualties.stream().anyMatch(casualty -> casualty.personId == prisonerId
                    && casualty.outcome == BattleResult.CasualtyOutcome.CAPTURED);
            if (!captured) return "Prisoner lacks captured outcome";
        }
        Army loser = result.winner == BattleResult.WinningSide.ATTACKER
                ? world.armies.get(battle.defender.armyId) : world.armies.get(battle.attacker.armyId);
        if (loser != null) {
            for (Map.Entry<GoodType, Integer> entry : result.capturedGoods.entrySet()) {
                if (entry.getValue() < 0 || loser.supplies.getQuantity(entry.getKey()) < entry.getValue()) {
                    return "Captured goods exceed loser inventory";
                }
            }
        }
        return null;
    }

    private void applyCasualty(BattleResult.BattleCasualty casualty, Army winner, long minute) {
        Person person = world.people.get(casualty.personId);
        Regiment regiment = world.regiments.get(casualty.regimentId);
        if (person == null || !person.alive) return;
        switch (casualty.outcome) {
            case UNHARMED -> { }
            case WOUNDED -> wound(person, casualty.healthDamage, false);
            case SEVERELY_WOUNDED -> wound(person, casualty.healthDamage, true);
            case KILLED -> {
                person.die(minute, Person.DeathCause.WAR);
                person.regimentId = null;
                person.travelingPartyId = null;
                if (regiment != null) regiment.soldierPersonIds.remove(Long.valueOf(person.id));
                removeFromHousehold(person);
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "BATTLE_PERSON_DIED"));
            }
            case CAPTURED -> {
                if (regiment != null) regiment.soldierPersonIds.remove(Long.valueOf(person.id));
                person.capturedByRealmId = winner == null ? null : winner.realmId;
                person.travelingPartyId = winner == null ? null : winner.id;
                if (winner != null) winner.prisonerPersonIds.add(person.id);
            }
            case MISSING_DESERTED -> {
                if (regiment != null) regiment.soldierPersonIds.remove(Long.valueOf(person.id));
                person.regimentId = null;
                person.travelingPartyId = null;
                person.type = person.preMilitaryType == null ? Person.PersonType.CITIZEN : person.preMilitaryType;
                person.preMilitaryType = null;
                person.currentSettlementId = person.homeSettlementId;
            }
        }
    }

    private void wound(Person person, double damage, boolean severe) {
        person.health.healthLevel = Math.max(1.0, person.health.healthLevel - damage);
        person.health.injured = true;
        person.health.wounded = true;
        person.health.injurySeverity = Math.max(person.health.injurySeverity, severe ? 70.0 : 35.0);
    }

    private void removeFromHousehold(Person person) {
        if (person.householdId == null) return;
        Household household = world.households.get(person.householdId);
        if (household == null) return;
        household.removeMember(person.id);
        if (household.headPersonId != null && household.headPersonId == person.id) {
            household.headPersonId = household.memberIds.stream().map(world.people::get)
                    .filter(member -> member != null && member.alive).map(member -> member.id)
                    .findFirst().orElse(null);
        }
    }

    private void transferLoot(BattleResult result, Army loser, Army winner) {
        if (loser == null || winner == null) return;
        for (Map.Entry<GoodType, Integer> entry : result.capturedGoods.entrySet()) {
            if (entry.getValue() > 0 && loser.supplies.remove(entry.getKey(), entry.getValue())) {
                winner.supplies.add(entry.getKey(), entry.getValue());
            }
        }
    }

    private void applyExperience(BattleResult result) {
        for (Map.Entry<Long, Double> entry : result.regimentExperienceGain.entrySet()) {
            Regiment regiment = world.regiments.get(entry.getKey());
            if (regiment != null) regiment.experience = Math.min(100.0, regiment.experience + entry.getValue());
        }
    }

    private void applyArmyOutcome(BattleResult result, Army attacker, Army defender, Army winner, Army loser) {
        if (winner != null) {
            winner.morale = Math.min(100.0, winner.morale + 8.0);
            winner.fatigue = Math.min(100.0, winner.fatigue + 12.0);
            winner.state = Army.ArmyState.MUSTERED;
        }
        if (loser != null) {
            loser.morale = Math.max(0.0, loser.morale - 20.0);
            loser.fatigue = Math.min(100.0, loser.fatigue + 18.0);
            loser.state = hasLivingSoldiers(loser) ? Army.ArmyState.ROUTED : Army.ArmyState.DISBANDED;
            loser.order = Army.ArmyOrder.RETURN_HOME;
        }
        if (result.winner == BattleResult.WinningSide.DRAW) {
            attacker.state = hasLivingSoldiers(attacker) ? Army.ArmyState.ROUTED : Army.ArmyState.DISBANDED;
            defender.state = hasLivingSoldiers(defender) ? Army.ArmyState.ROUTED : Army.ArmyState.DISBANDED;
        }
    }

    private boolean hasLivingSoldiers(Army army) {
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment != null && regiment.livingStrength(world) > 0) return true;
        }
        return false;
    }

    private int countAffected(BattleResult result, long armyId) {
        // BattleReport retains its historical meaning of permanent population losses.
        return (int) result.casualties.stream().filter(casualty -> casualty.armyId == armyId
                && casualty.outcome == BattleResult.CasualtyOutcome.KILLED).count();
    }

    private BattleContext.TacticalCombatant findCombatant(BattleContext battle, long personId) {
        for (BattleContext.TacticalCombatant combatant : battle.attacker.combatants) if (combatant.sourcePersonId == personId) return combatant;
        for (BattleContext.TacticalCombatant combatant : battle.defender.combatants) if (combatant.sourcePersonId == personId) return combatant;
        return null;
    }

    private Army requireArmy(long armyId) {
        Army army = world.armies.get(armyId);
        if (army == null) throw new IllegalArgumentException("Unknown army " + armyId);
        return army;
    }
}
