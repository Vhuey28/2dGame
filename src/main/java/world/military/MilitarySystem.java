package world.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.Army;
import world.Household;
import world.Person;
import world.Realm;
import world.SimulationContext;
import world.WorldState;
import world.command.CommandResult;
import world.economy.GoodType;
import world.economy.Workplace;
import world.event.WorldEvent;
import world.geography.Road;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.politics.Government;

/** Recruitment, strategic movement, supply, encounters, losses, and demobilization. */
public final class MilitarySystem {
    private static final long RECRUITMENT_COST = 20L;
    private final SimulationContext context;
    private final WorldState world;

    public MilitarySystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public CommandResult recruitRegiment(long realmId, long settlementId, int requested,
            Regiment.RegimentType type, long currentMinute) {
        Realm realm = world.realms.get(realmId);
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (realm == null || settlement == null || settlement.controllerRealmId == null
                || settlement.controllerRealmId != realmId) {
            return CommandResult.rejected("INVALID_MUSTER", "Recruitment requires a friendly settlement");
        }
        Government government = world.governments.get(realm.governmentId);
        if (government == null || government.getLawLevel(Government.LawType.CONSCRIPTION) <= 0) {
            return CommandResult.rejected("CONSCRIPTION_FORBIDDEN", "Realm law does not permit recruitment");
        }
        if (requested <= 0) return CommandResult.rejected("INVALID_STRENGTH", "Recruitment size must be positive");

        List<Person> eligible = eligibleResidents(settlementId, currentMinute);
        int equipmentAvailable = settlement.publicStockpile.getQuantity(GoodType.WEAPONS);
        int affordable = (int) Math.min(Integer.MAX_VALUE, realm.treasury.copperCoins / RECRUITMENT_COST);
        int count = Math.min(requested, Math.min(eligible.size(), Math.min(equipmentAvailable, affordable)));
        if (count <= 0) {
            return CommandResult.rejected("RECRUITMENT_RESOURCES", "People, weapons, or treasury are insufficient");
        }

        Regiment regiment = new Regiment(world.idGenerator.next(), realmId, type, settlementId);
        regiment.equipmentQuality = type == Regiment.RegimentType.LEVY_INFANTRY ? 35 : 50;
        for (int i = 0; i < count; i++) {
            Person person = eligible.get(i);
            removeFromCivilianJob(person);
            person.regimentId = regiment.id;
            person.preMilitaryType = person.type;
            person.type = Person.PersonType.SOLDIER;
            person.travelingPartyId = null;
            regiment.soldierPersonIds.add(person.id);
        }
        Person commander = eligible.subList(0, count).stream()
                .max(Comparator.comparingInt(person -> person.skills.leadership + person.skills.martial))
                .orElseThrow();
        regiment.officerPersonId = commander.id;
        world.regiments.put(regiment.id, regiment);
        settlement.publicStockpile.remove(GoodType.WEAPONS, count);
        realm.treasury.subtract(count * RECRUITMENT_COST);

        Army army = new Army(world.idGenerator.next(), realmId, commander.id, settlementId,
                new WorldPosition(settlement.position.x, settlement.position.y));
        army.regimentIds.add(regiment.id);
        army.order = Army.ArmyOrder.DEFEND;
        transferSupply(settlement, army, GoodType.GRAIN, count * 12);
        transferSupply(settlement, army, GoodType.VEGETABLES, count * 4);
        world.armies.put(army.id, army);
        assignToArmy(army);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "ARMY_MUSTERED"));
        return CommandResult.accepted();
    }

    public CommandResult issueMoveOrder(long armyId, long destinationSettlementId,
            Army.ArmyOrder order, long currentMinute) {
        Army army = world.armies.get(armyId);
        Settlement destination = world.geography.getSettlement(destinationSettlementId);
        if (army == null || army.state == Army.ArmyState.DISBANDED || destination == null) {
            return CommandResult.rejected("INVALID_ARMY_ORDER", "Army or destination does not exist");
        }
        if (army.currentSettlementId == destinationSettlementId) {
            army.order = order;
            army.destinationSettlementId = destinationSettlementId;
            return CommandResult.accepted();
        }
        List<Road> route = world.geography.getRouteGraph()
                .findShortestRoute(army.currentSettlementId, destinationSettlementId);
        if (route.isEmpty()) return CommandResult.rejected("NO_ROUTE", "No open route reaches the destination");
        army.routeRoadIds.clear();
        for (Road road : route) army.routeRoadIds.add(road.id);
        army.routeIndex = 0;
        army.roadProgress = 0.0;
        army.destinationSettlementId = destinationSettlementId;
        army.order = order;
        army.state = Army.ArmyState.TRAVELING;
        assignToArmy(army);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "ARMY_DEPARTED"));
        return CommandResult.accepted();
    }

    public void processHour(long currentMinute) {
        for (Army army : new ArrayList<>(world.armies.values())) {
            if (army.state == Army.ArmyState.TRAVELING) moveOneHour(army, currentMinute);
        }
        detectEncounters(currentMinute);
    }

    public void processDay(long currentMinute) {
        for (Army army : new ArrayList<>(world.armies.values())) {
            if (army.state == Army.ArmyState.DISBANDED) continue;
            RegimentSummary summary = summarize(army);
            if (summary.strength <= 0) {
                army.state = Army.ArmyState.DISBANDED;
                continue;
            }
            if (army.state != Army.ArmyState.TRAVELING) resupplyFromFriendlySettlement(army, summary.strength);
            int grainNeeded = Math.max(1, (summary.strength + 1) / 2);
            int vegetablesNeeded = Math.max(1, (summary.strength + 3) / 4);
            boolean fed = army.supplies.remove(GoodType.GRAIN, grainNeeded);
            boolean variedFood = army.supplies.remove(GoodType.VEGETABLES, vegetablesNeeded);
            if (fed) {
                army.daysWithoutFood = 0;
                army.morale = clamp(army.morale + (variedFood ? 0.5 : 0.1));
                army.fatigue = clamp(army.fatigue + (army.state == Army.ArmyState.TRAVELING ? 2.0 : -3.0));
            } else {
                army.daysWithoutFood++;
                army.morale = clamp(army.morale - 7.0);
                army.fatigue = clamp(army.fatigue + 8.0);
                if (army.daysWithoutFood >= 3) desertOneSoldier(army, currentMinute);
                if (army.daysWithoutFood >= 5 && context.getRandom("MILITARY").nextDouble() < 0.25) {
                    applyCasualties(army, 1, currentMinute);
                }
            }
        }
    }

    public void processMonth(long currentMinute) {
        for (Army army : new ArrayList<>(world.armies.values())) {
            if (army.state == Army.ArmyState.DISBANDED) continue;
            Realm realm = world.realms.get(army.realmId);
            for (Long regimentId : army.regimentIds) {
                Regiment regiment = world.regiments.get(regimentId);
                if (regiment == null || !regiment.active) continue;
                for (Long personId : new ArrayList<>(regiment.soldierPersonIds)) {
                    Person soldier = world.people.get(personId);
                    if (soldier == null || !soldier.alive) continue;
                    long pay = regiment.dailyPayPerSoldier * 30L;
                    if (realm != null && realm.treasury.subtract(pay)) {
                        Household household = soldier.householdId == null ? null : world.households.get(soldier.householdId);
                        if (household != null) household.account.add(pay);
                        soldier.militaryExperience = Math.min(100.0, soldier.militaryExperience + 1.0);
                    } else army.morale = clamp(army.morale - 3.0);
                }
            }
        }
    }

    public BattleReport autoResolveEncounter(long firstArmyId, long secondArmyId, long currentMinute) {
        Army first = world.armies.get(firstArmyId);
        Army second = world.armies.get(secondArmyId);
        if (first == null || second == null || first.realmId == second.realmId
                || first.state == Army.ArmyState.DISBANDED || second.state == Army.ArmyState.DISBANDED) return null;
        RegimentSummary firstSummary = summarize(first);
        RegimentSummary secondSummary = summarize(second);
        if (firstSummary.strength == 0 || secondSummary.strength == 0) return null;
        first.state = Army.ArmyState.ENGAGED;
        second.state = Army.ArmyState.ENGAGED;
        double firstPower = combatPower(first, firstSummary);
        double secondPower = combatPower(second, secondSummary);
        boolean firstWins = firstPower >= secondPower;
        int firstLosses = Math.max(1, (int) Math.round(firstSummary.strength
                * (firstWins ? 0.08 : 0.28) * casualtyVariance()));
        int secondLosses = Math.max(1, (int) Math.round(secondSummary.strength
                * (firstWins ? 0.28 : 0.08) * casualtyVariance()));
        firstLosses = Math.min(firstSummary.strength, firstLosses);
        secondLosses = Math.min(secondSummary.strength, secondLosses);
        applyCasualties(first, firstLosses, currentMinute);
        applyCasualties(second, secondLosses, currentMinute);
        Army winner = firstWins ? first : second;
        Army loser = firstWins ? second : first;
        winner.morale = clamp(winner.morale + 8.0);
        winner.fatigue = clamp(winner.fatigue + 12.0);
        winner.state = Army.ArmyState.MUSTERED;
        loser.morale = clamp(loser.morale - 20.0);
        loser.state = Army.ArmyState.ROUTED;
        loser.order = Army.ArmyOrder.RETURN_HOME;
        if (loser.homeSettlementId != null && summarize(loser).strength > 0) {
            issueMoveOrder(loser.id, loser.homeSettlementId, Army.ArmyOrder.RETURN_HOME, currentMinute);
        }
        BattleReport report = new BattleReport(world.idGenerator.next(), first.id, second.id,
                winner.id, firstLosses, secondLosses, currentMinute);
        world.battleReports.put(report.id, report);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "ARMIES_CLASHED"));
        return report;
    }

    public CommandResult demobilize(long armyId, long currentMinute) {
        Army army = world.armies.get(armyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "Army is not active");
        }
        Settlement home = army.homeSettlementId == null ? null : world.geography.getSettlement(army.homeSettlementId);
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null) continue;
            regiment.active = false;
            for (Long personId : new ArrayList<>(regiment.soldierPersonIds)) {
                Person soldier = world.people.get(personId);
                if (soldier == null || !soldier.alive) continue;
                soldier.regimentId = null;
                soldier.travelingPartyId = null;
                soldier.type = soldier.preMilitaryType == null ? Person.PersonType.CITIZEN : soldier.preMilitaryType;
                soldier.preMilitaryType = null;
                if (home != null) soldier.currentSettlementId = home.id;
                restoreCivilianJob(soldier);
            }
        }
        if (home != null) {
            for (GoodType type : GoodType.values()) {
                int quantity = army.supplies.getQuantity(type);
                if (quantity > 0 && army.supplies.remove(type, quantity)) home.publicStockpile.add(type, quantity);
            }
        }
        army.state = Army.ArmyState.DISBANDED;
        army.routeRoadIds.clear();
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "ARMY_DEMOBILIZED"));
        return CommandResult.accepted();
    }

    public int strength(Army army) { return summarize(army).strength; }

    private List<Person> eligibleResidents(long settlementId, long minute) {
        List<Person> people = new ArrayList<>();
        for (Person person : world.people.values()) {
            if (!person.alive || person.currentSettlementId == null || person.currentSettlementId != settlementId
                    || person.regimentId != null || person.travelingPartyId != null
                    || person.isChild(minute) || person.isElderly(minute)
                    || person.type == Person.PersonType.NOBLE || person.type == Person.PersonType.MERCHANT) continue;
            if (world.player != null && world.player.personId == person.id) continue;
            people.add(person);
        }
        people.sort(Comparator.comparingInt((Person person) -> person.skills.martial).reversed()
                .thenComparingLong(person -> person.id));
        return people;
    }

    private void removeFromCivilianJob(Person person) {
        person.preMilitaryEmployerId = person.employerId;
        if (person.employerId != null) {
            Workplace workplace = world.workplaces.get(person.employerId);
            if (workplace != null) workplace.removeWorker(person.id);
            person.employerId = null;
        }
    }

    private void restoreCivilianJob(Person person) {
        if (person.preMilitaryEmployerId == null) return;
        Workplace workplace = world.workplaces.get(person.preMilitaryEmployerId);
        if (workplace != null && workplace.addWorker(person.id)) person.employerId = workplace.id;
        person.preMilitaryEmployerId = null;
    }

    private void assignToArmy(Army army) {
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person soldier = world.people.get(personId);
                if (soldier == null || !soldier.alive) continue;
                soldier.travelingPartyId = army.id;
                soldier.currentSettlementId = null;
            }
        }
    }

    private void moveOneHour(Army army, long minute) {
        double supplyPenalty = army.daysWithoutFood > 0 ? 0.55 : 1.0;
        double fatiguePenalty = Math.max(0.35, 1.0 - army.fatigue / 140.0);
        double distanceRemaining = army.speedPerHour * supplyPenalty * fatiguePenalty;
        while (distanceRemaining > 0.0 && army.routeIndex < army.routeRoadIds.size()) {
            Road road = world.geography.getRouteGraph().getRoad(army.routeRoadIds.get(army.routeIndex));
            if (road == null || road.blocked) {
                army.state = Army.ArmyState.MUSTERED;
                army.routeRoadIds.clear();
                return;
            }
            double length = Math.max(1.0, road.getEffectiveCostFactor());
            double remaining = length * (1.0 - army.roadProgress);
            double traveled = Math.min(distanceRemaining, remaining);
            army.roadProgress += traveled / length;
            army.movementProgress += traveled;
            distanceRemaining -= traveled;
            updatePosition(army, road);
            if (army.roadProgress >= 0.999999) {
                army.currentSettlementId = road.toSettlementId;
                army.routeIndex++;
                army.roadProgress = 0.0;
            }
        }
        if (army.routeIndex >= army.routeRoadIds.size()) arrive(army, minute);
    }

    private void updatePosition(Army army, Road road) {
        Settlement from = world.geography.getSettlement(road.fromSettlementId);
        Settlement to = world.geography.getSettlement(road.toSettlementId);
        if (from == null || to == null) return;
        army.position = new WorldPosition(
                from.position.x + (to.position.x - from.position.x) * army.roadProgress,
                from.position.y + (to.position.y - from.position.y) * army.roadProgress);
        army.position.routeEdgeId = road.id;
        army.position.routeProgress = army.roadProgress;
    }

    private void arrive(Army army, long minute) {
        if (army.destinationSettlementId != null) army.currentSettlementId = army.destinationSettlementId;
        Settlement settlement = world.geography.getSettlement(army.currentSettlementId);
        if (settlement != null) army.position = new WorldPosition(settlement.position.x, settlement.position.y);
        army.routeRoadIds.clear();
        army.routeIndex = 0;
        army.state = Army.ArmyState.MUSTERED;
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person soldier = world.people.get(personId);
                if (soldier != null && soldier.alive) soldier.currentSettlementId = army.currentSettlementId;
            }
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "ARMY_ARRIVED"));
        if (army.order == Army.ArmyOrder.RETURN_HOME && army.homeSettlementId != null
                && army.currentSettlementId == army.homeSettlementId) demobilize(army.id, minute);
    }

    private void detectEncounters(long minute) {
        List<Army> armies = new ArrayList<>(world.armies.values());
        armies.sort(Comparator.comparingLong(army -> army.id));
        for (int i = 0; i < armies.size(); i++) {
            Army first = armies.get(i);
            if (!active(first)) continue;
            for (int j = i + 1; j < armies.size(); j++) {
                Army second = armies.get(j);
                if (!active(second) || first.realmId == second.realmId || distance(first, second) > 12.0) continue;
                first.detectedArmyIds.add(second.id);
                second.detectedArmyIds.add(first.id);
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "ARMY_DETECTED"));
                if (isAggressiveToward(first, second) || isAggressiveToward(second, first)) {
                    autoResolveEncounter(first.id, second.id, minute);
                }
            }
        }
    }

    private boolean active(Army army) {
        return army.state != Army.ArmyState.DISBANDED && army.state != Army.ArmyState.ENGAGED
                && summarize(army).strength > 0 && army.position != null;
    }

    private boolean isAggressiveToward(Army first, Army second) {
        return (first.order == Army.ArmyOrder.PURSUE && first.targetArmyId != null
                && first.targetArmyId == second.id) || first.order == Army.ArmyOrder.RAID;
    }

    private double distance(Army first, Army second) {
        double dx = first.position.x - second.position.x;
        double dy = first.position.y - second.position.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void resupplyFromFriendlySettlement(Army army, int strength) {
        Settlement settlement = world.geography.getSettlement(army.currentSettlementId);
        if (settlement == null || settlement.controllerRealmId == null
                || settlement.controllerRealmId != army.realmId) return;
        int grainTarget = strength * 12;
        int vegetablesTarget = strength * 4;
        transferSupply(settlement, army, GoodType.GRAIN,
                Math.max(0, grainTarget - army.supplies.getQuantity(GoodType.GRAIN)));
        transferSupply(settlement, army, GoodType.VEGETABLES,
                Math.max(0, vegetablesTarget - army.supplies.getQuantity(GoodType.VEGETABLES)));
    }

    private void transferSupply(Settlement settlement, Army army, GoodType type, int requested) {
        int amount = Math.min(requested, settlement.publicStockpile.getQuantity(type));
        if (amount > 0 && settlement.publicStockpile.remove(type, amount)) army.supplies.add(type, amount);
    }

    private void desertOneSoldier(Army army, long minute) {
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null) continue;
            for (Long personId : new ArrayList<>(regiment.soldierPersonIds)) {
                Person soldier = world.people.get(personId);
                if (soldier == null || !soldier.alive) continue;
                regiment.soldierPersonIds.remove(personId);
                soldier.regimentId = null;
                soldier.travelingPartyId = null;
                soldier.type = soldier.preMilitaryType == null ? Person.PersonType.CITIZEN : soldier.preMilitaryType;
                soldier.preMilitaryType = null;
                if (soldier.homeSettlementId != null) soldier.currentSettlementId = soldier.homeSettlementId;
                restoreCivilianJob(soldier);
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "SOLDIER_DESERTED"));
                return;
            }
        }
    }

    private void applyCasualties(Army army, int casualties, long minute) {
        int remaining = casualties;
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null) continue;
            List<Long> soldiers = new ArrayList<>(regiment.soldierPersonIds);
            soldiers.sort(Long::compareTo);
            for (Long personId : soldiers) {
                if (remaining <= 0) return;
                Person soldier = world.people.get(personId);
                if (soldier == null || !soldier.alive) continue;
                soldier.die(minute, Person.DeathCause.WAR);
                soldier.travelingPartyId = null;
                regiment.soldierPersonIds.remove(personId);
                removeFromHousehold(soldier);
                remaining--;
            }
        }
    }

    private void removeFromHousehold(Person soldier) {
        if (soldier.householdId == null) return;
        Household household = world.households.get(soldier.householdId);
        if (household == null) return;
        household.removeMember(soldier.id);
        if (household.headPersonId != null && household.headPersonId == soldier.id) {
            household.headPersonId = household.memberIds.stream()
                    .map(world.people::get).filter(person -> person != null && person.alive)
                    .map(person -> person.id).findFirst().orElse(null);
        }
    }

    private double combatPower(Army army, RegimentSummary summary) {
        Person commander = world.people.get(army.commanderPersonId);
        double leadership = commander == null ? 0.0 : commander.skills.leadership * 0.004;
        return summary.strength * (0.45 + army.morale / 100.0) * (1.0 - army.fatigue / 180.0)
                * (1.0 + summary.averageExperience / 120.0 + leadership);
    }

    private double casualtyVariance() {
        return 0.85 + context.getRandom("MILITARY").nextDouble() * 0.30;
    }

    private RegimentSummary summarize(Army army) {
        int strength = 0;
        double experience = 0.0;
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null || !regiment.active) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person person = world.people.get(personId);
                if (person != null && person.alive) {
                    strength++;
                    experience += person.militaryExperience;
                }
            }
        }
        return new RegimentSummary(strength, strength == 0 ? 0.0 : experience / strength);
    }

    private double clamp(double value) { return Math.max(0.0, Math.min(100.0, value)); }

    private static final class RegimentSummary {
        final int strength;
        final double averageExperience;
        RegimentSummary(int strength, double averageExperience) {
            this.strength = strength;
            this.averageExperience = averageExperience;
        }
    }
}
