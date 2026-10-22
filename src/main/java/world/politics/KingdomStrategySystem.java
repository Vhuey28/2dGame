package world.politics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import world.Army;
import world.Realm;
import world.SettlementPressure;
import world.SimulationContext;
import world.War;
import world.WorldConfig;
import world.WorldState;
import world.economy.GoodType;
import world.event.WorldEvent;
import world.geography.Settlement;
import world.military.Siege;

/**
 * Monthly court planning. A realm gets one explicit strategy, target and
 * reason; autonomous armies then coordinate around that plan instead of each
 * independently inventing a new purpose every day.
 */
public final class KingdomStrategySystem {
    private final SimulationContext context;
    private final WorldState world;

    public KingdomStrategySystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void initialize(long minute) {
        processMonth(minute, false);
    }

    public void processMonth(long minute) {
        processMonth(minute, true);
    }

    public void processMonth(long minute, boolean publishChanges) {
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        for (Realm realm : realms) evaluate(realm, pressures, minute, publishChanges);
    }

    public void evaluate(Realm realm, long minute) {
        evaluate(realm, SettlementPressure.evaluateAll(world, minute), minute, true);
    }

    private void evaluate(Realm realm, Map<Long, SettlementPressure> pressures,
            long minute, boolean publishChanges) {
        if (realm == null) return;
        List<Settlement> controlled = controlledSettlements(realm.id);
        Settlement worst = null;
        SettlementPressure worstPressure = null;
        double loyaltyTotal = 0.0;
        int loyaltyCount = 0;
        for (Settlement settlement : controlled) {
            SettlementPressure pressure = pressures.get(settlement.id);
            if (pressure == null) continue;
            loyaltyTotal += pressure.loyalty;
            loyaltyCount++;
            if (worstPressure == null || pressure.overallPressure > worstPressure.overallPressure) {
                worst = settlement;
                worstPressure = pressure;
            }
        }
        double averageLoyalty = loyaltyCount == 0 ? 0.5 : loyaltyTotal / loyaltyCount;
        int strength = realmStrength(realm.id);
        War war = primaryWar(realm.id);
        Settlement besiegedFriendly = besiegedFriendlySettlement(realm.id);
        Settlement invadedFriendly = invadedFriendlySettlement(realm.id);
        Settlement capital = realm.capitalSettlementId == null
                ? null : world.geography.getSettlement(realm.capitalSettlementId);

        Realm.Strategy strategy;
        Settlement target;
        String reason;
        if (besiegedFriendly != null) {
            strategy = Realm.Strategy.RELIEVE_SIEGE;
            target = besiegedFriendly;
            Siege siege = activeSiegeAt(target.id);
            reason = target.name + " is under siege"
                    + (siege == null ? "" : " at " + Math.round(siege.breachProgress)
                            + "% breach and " + Math.round(siege.defenderResolve) + "% resolve")
                    + "; relieving it outranks new offensives";
        } else if (war != null && shouldSeekPeace(realm, war, strength)) {
            strategy = Realm.Strategy.SEEK_PEACE;
            target = invadedFriendly != null ? invadedFriendly : capital;
            reason = "War exhaustion is " + Math.round(warExhaustionFor(war, realm.id))
                    + ", field strength is " + strength + ", treasury is "
                    + realm.treasury.copperCoins + "c and war score is "
                    + Math.round(war.attackerScore) + "-" + Math.round(war.defenderScore);
        } else if (invadedFriendly != null) {
            strategy = Realm.Strategy.DEFEND;
            target = invadedFriendly;
            reason = "Hostile troops are at " + target.name + "; field armies must clear home soil";
        } else if (war != null && (averageLoyalty < 0.36
                || worstPressure != null && worstPressure.overallPressure >= 0.78)) {
            strategy = Realm.Strategy.CONSOLIDATE;
            target = worst != null ? worst : capital;
            reason = "The home front is failing before the frontier: average loyalty "
                    + percent(averageLoyalty)
                    + (worstPressure == null ? "" : ", worst pressure at " + worst.name
                            + " (" + worstPressure.summary + ")");
        } else if (war != null) {
            strategy = Realm.Strategy.CONQUER;
            Army anchor = strongestArmy(realm.id);
            target = chooseWarObjective(world, anchor, war, realm.id, null);
            if (target == null) target = capital;
            reason = warReason(war, target, strength);
        } else if (strength < 10 || realm.treasury.copperCoins < 250L) {
            strategy = Realm.Strategy.REBUILD;
            target = bestRecruitmentSettlement(realm, controlled, pressures);
            if (target == null) target = capital;
            reason = "Only " + strength + " soldiers are under arms and the treasury holds "
                    + realm.treasury.copperCoins + "c; mustering comes before adventures";
        } else if (worstPressure != null && (worstPressure.overallPressure >= 0.52
                || averageLoyalty < 0.42)) {
            strategy = Realm.Strategy.CONSOLIDATE;
            target = worst;
            reason = worst.name + " is the realm's worst pressure point: " + worstPressure.summary
                    + " (" + worstPressure.reason + ")";
        } else {
            strategy = Realm.Strategy.DEVELOP;
            target = lowestProsperity(controlled);
            if (target == null) target = capital;
            reason = "No active war or domestic crisis; average loyalty is "
                    + percent(averageLoyalty) + ", so patrols protect trade and development";
        }

        boolean changed = realm.strategy != strategy
                || (realm.strategyTargetSettlementId == null) != (target == null)
                || target != null && (realm.strategyTargetSettlementId == null
                        || realm.strategyTargetSettlementId.longValue() != target.id);
        realm.strategy = strategy;
        realm.strategyTargetSettlementId = target == null ? null : target.id;
        realm.strategyReason = reason;
        realm.strategyUpdatedMinute = minute;
        if (publishChanges && changed) {
            context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute,
                    "REALM_STRATEGY_CHANGED", realm.name + " adopts " + readable(strategy)
                            + (target == null ? "" : " focused on " + target.name)
                            + ": " + reason));
        }
    }

    /** Scores enemy settlements by legal objective, value, vulnerability and logistics. */
    public static Settlement chooseWarObjective(WorldState world, Army anchorArmy, War war,
            long realmId, Long excludedSettlementId) {
        if (world == null || war == null) return null;
        double anchorX = 0.0;
        double anchorY = 0.0;
        boolean haveAnchor = false;
        if (anchorArmy != null && anchorArmy.position != null) {
            anchorX = anchorArmy.position.x;
            anchorY = anchorArmy.position.y;
            haveAnchor = true;
        } else {
            Realm realm = world.realms.get(realmId);
            Settlement capital = realm == null || realm.capitalSettlementId == null
                    ? null : world.geography.getSettlement(realm.capitalSettlementId);
            if (capital != null && capital.position != null) {
                anchorX = capital.position.x;
                anchorY = capital.position.y;
                haveAnchor = true;
            }
        }

        Settlement best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        for (Settlement settlement : settlements) {
            if (settlement.controllerRealmId == null || settlement.position == null) continue;
            if (excludedSettlementId != null && settlement.id == excludedSettlementId.longValue()) continue;
            long controller = settlement.controllerRealmId.longValue();
            if (!war.opposing(realmId, controller)) continue;
            if (settlement.occupyingRealmId != null
                    && !war.opposing(realmId, settlement.occupyingRealmId.longValue())) continue;

            double score = haveAnchor
                    ? Math.hypot(settlement.position.x - anchorX, settlement.position.y - anchorY)
                    : settlement.id;
            int population = world.indexes.livingPeopleAt(settlement.id).size();
            int food = settlement.publicStockpile.getQuantity(GoodType.GRAIN)
                    + settlement.publicStockpile.getQuantity(GoodType.VEGETABLES);
            score += SettlementPressure.clamp01(settlement.security) * 85.0;
            score += defendingStrengthAt(world, settlement, war) * 2.1;
            score -= population * 0.07;
            score -= SettlementPressure.clamp01(settlement.unrest) * 30.0;
            score -= Math.min(28.0, food / 22.0);
            if (war.targetProvinceId != null && settlement.provinceId == war.targetProvinceId.longValue()) {
                score -= 120.0;
            }
            Realm controllerRealm = world.realms.get(controller);
            if (controllerRealm != null && controllerRealm.capitalSettlementId != null
                    && controllerRealm.capitalSettlementId.longValue() == settlement.id) score -= 35.0;
            if (war.goal == War.WarGoal.PUNITIVE) score -= SettlementPressure.clamp01(settlement.prosperity) * 42.0;
            if (war.targetTitleId != null) {
                Title title = world.titles.get(war.targetTitleId);
                if (title != null && title.settlementId != null
                        && title.settlementId.longValue() == settlement.id) score -= 150.0;
                if (title != null && title.deJureProvinceId != null
                        && settlement.provinceId == title.deJureProvinceId.longValue()) score -= 75.0;
            }
            Siege siege = activeSiegeAt(world, settlement.id, war.id);
            if (siege != null) score -= 42.0 + siege.breachProgress * 0.25;
            if (score < bestScore) {
                best = settlement;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean shouldSeekPeace(Realm realm, War war, int strength) {
        double exhaustion = warExhaustionFor(war, realm.id);
        long warDays = Math.max(0L, (context.getClock().getWorldMinute() - war.startMinute)
                / WorldConfig.MINUTES_PER_DAY);
        return exhaustion >= 72.0 || strength <= 8 && warDays >= 30L
                || realm.treasury.copperCoins < 200L && exhaustion >= 55.0;
    }

    private String warReason(War war, Settlement target, int strength) {
        String objective = target == null ? "the enemy heartland" : target.name;
        String goal = switch (war.goal) {
            case CONQUER_PROVINCE -> "conquer the target province";
            case CLAIM_TITLE -> "press the legal title claim";
            case INDEPENDENCE -> "win independence";
            case VASSALIZE -> "force vassalage";
            case PUNITIVE -> "punish the recorded grievance";
        };
        return "At war to " + goal + "; " + objective + " is the best reachable objective for "
                + strength + " soldiers after counting walls, defenders, food and distance";
    }

    private Settlement bestRecruitmentSettlement(Realm realm, List<Settlement> controlled,
            Map<Long, SettlementPressure> pressures) {
        Settlement best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Settlement settlement : controlled) {
            if (settlement.occupyingRealmId != null
                    && settlement.occupyingRealmId.longValue() != realm.id) continue;
            SettlementPressure pressure = pressures.get(settlement.id);
            int population = pressure == null ? 0 : pressure.population;
            int weapons = settlement.publicStockpile.getQuantity(GoodType.WEAPONS);
            double score = Math.min(20, weapons) * 3.0 + population * 0.22
                    + (pressure == null ? 0.0 : pressure.loyalty * 10.0 - pressure.overallPressure * 9.0)
                    + (realm.capitalSettlementId != null
                            && realm.capitalSettlementId.longValue() == settlement.id ? 5.0 : 0.0);
            if (score > bestScore) {
                best = settlement;
                bestScore = score;
            }
        }
        return best;
    }

    private Settlement lowestProsperity(List<Settlement> controlled) {
        Settlement best = null;
        for (Settlement settlement : controlled) {
            if (best == null || settlement.prosperity < best.prosperity) best = settlement;
        }
        return best;
    }

    private List<Settlement> controlledSettlements(long realmId) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : world.geography.getSettlements().values()) {
            if (settlement.controllerRealmId != null
                    && settlement.controllerRealmId.longValue() == realmId) result.add(settlement);
        }
        result.sort(Comparator.comparingLong(settlement -> settlement.id));
        return result;
    }

    private Settlement besiegedFriendlySettlement(long realmId) {
        Settlement best = null;
        double lowestResolve = Double.POSITIVE_INFINITY;
        for (Siege siege : world.sieges.values()) {
            if (siege.state != Siege.SiegeState.ACTIVE) continue;
            Settlement settlement = world.geography.getSettlement(siege.settlementId);
            War war = world.wars.get(siege.warId);
            if (settlement == null || war == null || settlement.controllerRealmId == null) continue;
            if (!sameSide(war, realmId, settlement.controllerRealmId.longValue())) continue;
            if (siege.defenderResolve < lowestResolve) {
                best = settlement;
                lowestResolve = siege.defenderResolve;
            }
        }
        return best;
    }

    private Settlement invadedFriendlySettlement(long realmId) {
        Settlement best = null;
        int bestStrength = -1;
        for (Army army : world.armies.values()) {
            if (army.state == Army.ArmyState.DISBANDED || army.state == Army.ArmyState.ENGAGED) continue;
            Settlement settlement = world.geography.getSettlement(army.currentSettlementId);
            if (settlement == null || settlement.controllerRealmId == null
                    || settlement.controllerRealmId.longValue() != realmId) continue;
            boolean hostile = world.wars.values().stream().anyMatch(war ->
                    war.state == War.WarState.ACTIVE && war.opposing(realmId, army.realmId));
            if (!hostile) continue;
            int strength = armyStrength(army);
            if (strength > bestStrength) {
                best = settlement;
                bestStrength = strength;
            }
        }
        return best;
    }

    private War primaryWar(long realmId) {
        War best = null;
        for (War war : world.wars.values()) {
            if (war.state != War.WarState.ACTIVE || !war.includes(realmId)) continue;
            if (best == null || war.id < best.id) best = war;
        }
        return best;
    }

    private Army strongestArmy(long realmId) {
        Army best = null;
        int bestStrength = -1;
        for (Army army : world.armies.values()) {
            if (army.realmId != realmId || army.playerCommanded || army.temporaryBattleArmy
                    || army.state == Army.ArmyState.DISBANDED) continue;
            int strength = armyStrength(army);
            if (strength > bestStrength || strength == bestStrength && best != null && army.id < best.id) {
                best = army;
                bestStrength = strength;
            }
        }
        return best;
    }

    private int realmStrength(long realmId) {
        int total = 0;
        for (Army army : world.armies.values()) {
            if (army.realmId == realmId && !army.temporaryBattleArmy
                    && army.state != Army.ArmyState.DISBANDED) total += armyStrength(army);
        }
        return total;
    }

    private int armyStrength(Army army) {
        int total = 0;
        for (Long regimentId : army.regimentIds) {
            world.military.Regiment regiment = world.regiments.get(regimentId);
            if (regiment != null) total += regiment.livingStrength(world);
        }
        return total;
    }

    private static int defendingStrengthAt(WorldState world, Settlement settlement, War war) {
        int total = 0;
        if (settlement.controllerRealmId == null) return 0;
        for (Army army : world.armies.values()) {
            if (army.currentSettlementId != settlement.id || army.temporaryBattleArmy
                    || army.state == Army.ArmyState.DISBANDED || army.state == Army.ArmyState.TRAVELING
                    || army.state == Army.ArmyState.ENGAGED) continue;
            if (!sameSide(war, army.realmId, settlement.controllerRealmId.longValue())) continue;
            for (Long regimentId : army.regimentIds) {
                world.military.Regiment regiment = world.regiments.get(regimentId);
                if (regiment != null) total += regiment.livingStrength(world);
            }
        }
        return total;
    }

    private Siege activeSiegeAt(long settlementId) {
        return activeSiegeAt(world, settlementId, -1L);
    }

    private static Siege activeSiegeAt(WorldState world, long settlementId, long warId) {
        for (Siege siege : world.sieges.values()) {
            if (siege.settlementId == settlementId && siege.state == Siege.SiegeState.ACTIVE
                    && (warId < 0 || siege.warId == warId)) return siege;
        }
        return null;
    }

    private static double warExhaustionFor(War war, long realmId) {
        return war.attackerRealmIds.contains(realmId) ? war.attackerExhaustion : war.defenderExhaustion;
    }

    private static boolean sameSide(War war, long firstRealmId, long secondRealmId) {
        return war.attackerRealmIds.contains(firstRealmId) && war.attackerRealmIds.contains(secondRealmId)
                || war.defenderRealmIds.contains(firstRealmId) && war.defenderRealmIds.contains(secondRealmId);
    }

    private static String readable(Realm.Strategy strategy) {
        return strategy.name().replace('_', ' ').toLowerCase();
    }

    private static String percent(double value) {
        return Math.round(SettlementPressure.clamp01(value) * 100.0) + "%";
    }
}
