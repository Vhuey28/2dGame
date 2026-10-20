package world.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.Army;
import world.Household;
import world.Person;
import world.Realm;
import world.SimulationContext;
import world.War;
import world.WorldConfig;
import world.WorldState;
import world.command.CommandResult;
import world.economy.GoodType;
import world.economy.MoneyAccount;
import world.economy.Workplace;
import world.event.WorldEvent;
import world.geography.Road;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.politics.Government;

/** Recruitment, strategic movement, supply, encounters, losses, and demobilization. */
public final class MilitarySystem {
    private static final long RECRUITMENT_COST = 20L;
    /** A private company costs more per head than a realm levy: no conscription, no treasury. */
    public static final long COMPANY_RECRUITMENT_COST = 35L;
    private final SimulationContext context;
    private final WorldState world;
    private final BattleBridge battleBridge;

    public MilitarySystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
        this.battleBridge = new BattleBridge(context);
    }

    public BattleBridge getBattleBridge() {
        return battleBridge;
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

    /**
     * Raises a personal company for one commander, funded from that commander's own purse.
     *
     * <p>Unlike {@link #recruitRegiment} this needs neither a conscription law nor a realm
     * treasury, so a landless adventurer can hire volunteers wherever the local smithy has
     * weapons. The resulting army is flagged player-commanded (realm AI will not order it)
     * and player-funded (upkeep comes from the purse, not a treasury).
     */
    public CommandResult raisePersonalCompany(long commanderPersonId, long settlementId, int requested,
            Regiment.RegimentType type, MoneyAccount payer, long currentMinute) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Person commander = world.people.get(commanderPersonId);
        if (settlement == null) return CommandResult.rejected("INVALID_MUSTER", "Settlement not found");
        if (commander == null || !commander.alive) {
            return CommandResult.rejected("INVALID_COMMANDER", "Commander not found");
        }
        if (commander.currentSettlementId == null || commander.currentSettlementId != settlementId) {
            return CommandResult.rejected("NOT_PRESENT", "Travel to the settlement to raise a company");
        }
        if (settlement.occupyingRealmId != null
                && !settlement.occupyingRealmId.equals(settlement.controllerRealmId)) {
            return CommandResult.rejected("OCCUPIED", "An occupying power controls this settlement");
        }
        if (payer == null) return CommandResult.rejected("INVALID_STATE", "There is no purse to pay from");
        if (requested <= 0) return CommandResult.rejected("INVALID_STRENGTH", "Company size must be positive");

        List<Person> eligible = eligibleResidents(settlementId, currentMinute);
        int equipmentAvailable = settlement.publicStockpile.getQuantity(GoodType.WEAPONS);
        int affordable = (int) Math.min(Integer.MAX_VALUE, payer.copperCoins / COMPANY_RECRUITMENT_COST);
        int count = Math.min(requested, Math.min(eligible.size(), Math.min(equipmentAvailable, affordable)));
        if (count <= 0) {
            return CommandResult.rejected("RECRUITMENT_RESOURCES",
                    "Needs volunteers, a weapon each, and " + COMPANY_RECRUITMENT_COST + "c each from your purse"
                            + " (volunteers " + eligible.size() + ", weapons " + equipmentAvailable
                            + ", affordable " + affordable + ")");
        }

        Long ownerRealmId = settlement.controllerRealmId;
        long companyRealmId = ownerRealmId == null ? -1L : ownerRealmId;
        Regiment regiment = new Regiment(world.idGenerator.next(), companyRealmId, type, settlementId);
        regiment.equipmentQuality = type == Regiment.RegimentType.LEVY_INFANTRY ? 40 : 55;
        regiment.dailyPayPerSoldier = 3L;
        for (int i = 0; i < count; i++) {
            Person person = eligible.get(i);
            removeFromCivilianJob(person);
            person.regimentId = regiment.id;
            person.preMilitaryType = person.type;
            person.type = Person.PersonType.SOLDIER;
            person.travelingPartyId = null;
            regiment.soldierPersonIds.add(person.id);
        }
        regiment.officerPersonId = commanderPersonId;
        world.regiments.put(regiment.id, regiment);
        settlement.publicStockpile.remove(GoodType.WEAPONS, count);
        long cost = count * COMPANY_RECRUITMENT_COST;
        payer.subtract(cost);
        settlement.treasury.add(cost);

        Army army = new Army(world.idGenerator.next(), companyRealmId, commanderPersonId, settlementId,
                new WorldPosition(settlement.position.x, settlement.position.y));
        army.regimentIds.add(regiment.id);
        army.order = Army.ArmyOrder.DEFEND;
        army.playerCommanded = true;
        army.playerFunded = true;
        army.morale = 60.0;
        // A short recruitment ration; after that the commander has to keep it fed.
        transferSupply(settlement, army, GoodType.GRAIN, count * 6);
        transferSupply(settlement, army, GoodType.VEGETABLES, count * 2);
        world.armies.put(army.id, army);
        assignToArmy(army);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "ARMY_MUSTERED",
                (commander.givenName + " " + commander.familyName).trim() + " raises a company of "
                        + count + " at " + settlement.name));
        return new CommandResult(true, "COMPANY_RAISED",
                "You raise a company of " + count + " for " + cost + "c");
    }

    /** Applies casualties from outside the encounter loop: assaults, ambushes, skirmishes. */
    public CommandResult applyFieldCasualties(long armyId, int casualties, long currentMinute) {
        Army army = world.armies.get(armyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "Army is not active");
        }
        applyCasualties(army, Math.max(0, casualties), currentMinute);
        return CommandResult.accepted();
    }

    /** The active army this person commands, or null if they command none. */
    public Army armyCommandedBy(long commanderPersonId) {
        Army newest = null;
        for (Army army : world.armies.values()) {
            if (army.commanderPersonId != commanderPersonId
                    || army.state == Army.ArmyState.DISBANDED) continue;
            if (newest == null || army.id > newest.id) newest = army;
        }
        return newest;
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
            if (army.state == Army.ArmyState.TRAVELING && !movesWithPlayerParty(army)) {
                moveOneHour(army, currentMinute);
            }
        }
        world.geography.getSpatialIndex().rebuildDynamic(world);
        detectEncounters(currentMinute);
    }

    /**
     * A player-commanded army with no road route of its own is being carried
     * by CampaignSession's player-party movement. Letting moveOneHour run for
     * it would "arrive" it immediately because its route is intentionally empty.
     */
    private boolean movesWithPlayerParty(Army army) {
        return army != null && army.playerCommanded && army.routeRoadIds.isEmpty()
                && world.player != null && world.player.commandedArmyId != null
                && world.player.commandedArmyId.longValue() == army.id;
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
        issueIndependentArmyOrders(currentMinute);
    }

    public void processMonth(long currentMinute) {
        for (Army army : new ArrayList<>(world.armies.values())) {
            if (army.state == Army.ArmyState.DISBANDED) continue;
            Realm realm = world.realms.get(army.realmId);
            MoneyAccount purse = army.playerFunded ? playerPurse() : null;
            MoneyAccount payer = army.playerFunded ? purse : (realm == null ? null : realm.treasury);
            for (Long regimentId : army.regimentIds) {
                Regiment regiment = world.regiments.get(regimentId);
                if (regiment == null || !regiment.active) continue;
                for (Long personId : new ArrayList<>(regiment.soldierPersonIds)) {
                    Person soldier = world.people.get(personId);
                    if (soldier == null || !soldier.alive) continue;
                    long pay = regiment.dailyPayPerSoldier * 30L;
                    if (payer != null && payer.subtract(pay)) {
                        Household household = soldier.householdId == null ? null : world.households.get(soldier.householdId);
                        if (household != null) household.account.add(pay);
                        soldier.militaryExperience = Math.min(100.0, soldier.militaryExperience + 1.0);
                    } else {
                        // An unpaid company loses morale twice as fast as an unpaid levy.
                        army.morale = clamp(army.morale - (army.playerFunded ? 6.0 : 3.0));
                    }
                }
            }
        }
        musterRealmArmies(currentMinute);
    }

    /**
     * Bounded realm mustering: every realm tries to keep two independent
     * field armies in peace and three in war, raising at most one new army
     * per realm per call through the ordinary people/weapons/treasury rules.
     * Player-commanded and player-funded forces never count toward the
     * realm's autonomous force, so allies and enemies both replace losses.
     */
    public int musterRealmArmies(long currentMinute) {
        int created = 0;
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        for (Realm realm : realms) {
            int target = realmAtWar(realm.id) ? 3 : 2;
            int existing = 0;
            for (Army army : world.armies.values()) {
                if (army.realmId == realm.id && !army.playerCommanded && !army.playerFunded
                        && !army.temporaryBattleArmy && army.state != Army.ArmyState.DISBANDED
                        && strength(army) > 0) existing++;
            }
            if (existing >= target) continue;

            Settlement best = null;
            int bestRecruitable = 0;
            List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
            settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
            for (Settlement settlement : settlements) {
                if (settlement.controllerRealmId == null || settlement.controllerRealmId.longValue() != realm.id) continue;
                if (settlement.occupyingRealmId != null
                        && settlement.occupyingRealmId.longValue() != realm.id) continue;
                int affordable = (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                        realm.treasury.copperCoins / RECRUITMENT_COST));
                int recruitable = Math.min(12, Math.min(
                        eligibleResidents(settlement.id, currentMinute).size(),
                        Math.min(settlement.publicStockpile.getQuantity(GoodType.WEAPONS), affordable)));
                boolean capital = realm.capitalSettlementId != null
                        && realm.capitalSettlementId.longValue() == settlement.id;
                boolean bestCapital = best != null && realm.capitalSettlementId != null
                        && realm.capitalSettlementId.longValue() == best.id;
                if (recruitable > bestRecruitable
                        || recruitable == bestRecruitable && capital && !bestCapital) {
                    best = settlement;
                    bestRecruitable = recruitable;
                }
            }
            if (best == null || bestRecruitable < 6) continue;
            CommandResult recruited = recruitRegiment(realm.id, best.id, 12,
                    Regiment.RegimentType.LEVY_INFANTRY, currentMinute);
            if (recruited.accepted) {
                created++;
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute,
                        "REALM_ARMY_MUSTERED", realm.name + " musters a new army at " + best.name));
            }
        }
        return created;
    }

    /**
     * Daily orders for armies nobody is personally commanding. Each idle
     * army independently rallies, resupplies, pursues a nearby wartime foe,
     * advances to a war objective, defends a threatened settlement, or
     * patrols its realm in peace. Player-commanded, player-funded, and
     * temporary solo projections are never touched.
     */
    public void issueIndependentArmyOrders(long currentMinute) {
        List<Army> armies = new ArrayList<>(world.armies.values());
        armies.sort(Comparator.comparingLong(army -> army.id));
        for (Army army : armies) {
            if (army.playerCommanded || army.playerFunded || army.temporaryBattleArmy
                    || army.state == Army.ArmyState.DISBANDED || army.state == Army.ArmyState.ENGAGED
                    || army.state == Army.ArmyState.TRAVELING || army.position == null
                    || strength(army) <= 0) continue;

            if (army.state == Army.ArmyState.ROUTED) {
                Settlement current = world.geography.getSettlement(army.currentSettlementId);
                boolean safe = current != null && current.controllerRealmId != null
                        && current.controllerRealmId.longValue() == army.realmId
                        && (army.homeSettlementId == null
                                || army.homeSettlementId.longValue() == current.id);
                if (safe) {
                    army.state = Army.ArmyState.MUSTERED;
                    army.order = Army.ArmyOrder.DEFEND;
                    army.destinationSettlementId = current.id;
                } else {
                    Settlement home = friendlyResupplySettlement(army);
                    if (home != null) {
                        issueMoveOrder(army.id, home.id, Army.ArmyOrder.RETURN_HOME, currentMinute);
                    }
                }
                continue;
            }
            if (army.state != Army.ArmyState.MUSTERED) continue;
            if (army.order == Army.ArmyOrder.BESIEGE && activeSiegeWithArmy(army)) continue;

            int armyStrength = strength(army);
            int grainNeededPerDay = Math.max(1, (armyStrength + 1) / 2);
            boolean needsSupply = army.daysWithoutFood > 0
                    || army.supplies.getQuantity(GoodType.GRAIN) < grainNeededPerDay * 3
                    || army.morale < 35.0 || army.fatigue > 75.0;
            if (needsSupply) {
                Settlement supply = friendlyResupplySettlement(army);
                if (supply != null) {
                    if (supply.id == army.currentSettlementId) {
                        army.order = Army.ArmyOrder.RESUPPLY;
                        army.destinationSettlementId = supply.id;
                    } else {
                        issueMoveOrder(army.id, supply.id, Army.ArmyOrder.RESUPPLY, currentMinute);
                    }
                    continue;
                }
            }

            War war = firstActiveWarFor(army.realmId);
            int role = independentArmyRole(army);
            if (war != null) {
                Settlement threatened = threatenedFriendlySettlement(army, war);
                if (threatened != null && role > 0) {
                    issueIndependentMove(army, threatened.id, Army.ArmyOrder.DEFEND, currentMinute);
                    continue;
                }
                Army target = nearbyWartimeEnemy(army, war);
                if (target != null && armyStrength >= Math.max(4, strength(target) * 4 / 5)) {
                    Settlement targetSettlement = world.geography.getSettlement(target.currentSettlementId);
                    if (targetSettlement != null) {
                        army.targetArmyId = target.id;
                        issueIndependentMove(army, targetSettlement.id, Army.ArmyOrder.PURSUE, currentMinute);
                        army.targetArmyId = target.id;
                        continue;
                    }
                }
                Settlement objective = warObjectiveSettlement(army, war);
                if (objective != null && role != 1) {
                    issueIndependentMove(army, objective.id, Army.ArmyOrder.BESIEGE, currentMinute);
                    continue;
                }
                Settlement post = threatened != null ? threatened : friendlyResupplySettlement(army);
                if (post != null) {
                    issueIndependentMove(army, post.id, Army.ArmyOrder.DEFEND, currentMinute);
                }
                continue;
            }

            if (role == 1) {
                Settlement post = friendlyResupplySettlement(army);
                if (post != null) issueIndependentMove(army, post.id, Army.ArmyOrder.DEFEND, currentMinute);
            } else {
                Settlement patrol = patrolSettlement(army, currentMinute, role);
                if (patrol != null) issueIndependentMove(army, patrol.id, Army.ArmyOrder.PATROL, currentMinute);
            }
        }
    }

    private boolean realmAtWar(long realmId) {
        for (War war : world.wars.values()) {
            if (war.state == War.WarState.ACTIVE && war.includes(realmId)) return true;
        }
        return false;
    }

    private War firstActiveWarFor(long realmId) {
        War first = null;
        for (War war : world.wars.values()) {
            if (war.state != War.WarState.ACTIVE || !war.includes(realmId)) continue;
            if (first == null || war.id < first.id) first = war;
        }
        return first;
    }

    private int independentArmyRole(Army army) {
        List<Army> peers = new ArrayList<>();
        for (Army peer : world.armies.values()) {
            if (peer.realmId == army.realmId && !peer.playerCommanded && !peer.playerFunded
                    && !peer.temporaryBattleArmy && peer.state != Army.ArmyState.DISBANDED
                    && strength(peer) > 0) peers.add(peer);
        }
        peers.sort(Comparator.comparingInt((Army peer) -> strength(peer)).reversed()
                .thenComparingLong(peer -> peer.id));
        for (int i = 0; i < peers.size(); i++) if (peers.get(i).id == army.id) return i;
        return 0;
    }

    private void issueIndependentMove(Army army, long settlementId, Army.ArmyOrder order, long minute) {
        if (army.currentSettlementId == settlementId && army.state == Army.ArmyState.MUSTERED) {
            army.order = order;
            army.destinationSettlementId = settlementId;
            return;
        }
        issueMoveOrder(army.id, settlementId, order, minute);
    }

    private boolean activeSiegeWithArmy(Army army) {
        for (Siege siege : world.sieges.values()) {
            if (siege.state == Siege.SiegeState.ACTIVE && siege.settlementId == army.currentSettlementId
                    && siege.isBesiegingArmy(army.id)) return true;
        }
        return false;
    }

    private Settlement friendlyResupplySettlement(Army army) {
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Realm realm = world.realms.get(army.realmId);
        for (Settlement settlement : world.geography.getSettlements().values()) {
            if (settlement.controllerRealmId == null || settlement.controllerRealmId.longValue() != army.realmId
                    || settlement.position == null || army.position == null) continue;
            double distance = Math.hypot(army.position.x - settlement.position.x,
                    army.position.y - settlement.position.y);
            if (army.homeSettlementId != null && army.homeSettlementId.longValue() == settlement.id) distance -= 25.0;
            if (realm != null && realm.capitalSettlementId != null
                    && realm.capitalSettlementId.longValue() == settlement.id) distance -= 10.0;
            if (distance < bestDistance) {
                best = settlement;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Settlement threatenedFriendlySettlement(Army army, War war) {
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Siege siege : world.sieges.values()) {
            if (siege.state != Siege.SiegeState.ACTIVE || siege.warId != war.id) continue;
            Settlement settlement = world.geography.getSettlement(siege.settlementId);
            if (settlement == null || settlement.controllerRealmId == null || settlement.position == null) continue;
            if (!sameWarSide(war, army.realmId, settlement.controllerRealmId.longValue())) continue;
            double distance = Math.hypot(army.position.x - settlement.position.x,
                    army.position.y - settlement.position.y);
            if (distance < bestDistance) {
                best = settlement;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Army nearbyWartimeEnemy(Army army, War war) {
        Army best = null;
        double bestDistance = 450.0;
        for (Army other : world.armies.values()) {
            if (other.id == army.id || other.playerCommanded || other.temporaryBattleArmy
                    || other.state == Army.ArmyState.DISBANDED || other.state == Army.ArmyState.ENGAGED
                    || other.position == null || strength(other) <= 0
                    || !war.opposing(army.realmId, other.realmId)) continue;
            double distance = Math.hypot(army.position.x - other.position.x,
                    army.position.y - other.position.y);
            if (distance < bestDistance) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Settlement warObjectiveSettlement(Army army, War war) {
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Settlement settlement : world.geography.getSettlements().values()) {
            if (settlement.controllerRealmId == null || settlement.position == null) continue;
            long controller = settlement.controllerRealmId.longValue();
            if (!war.opposing(army.realmId, controller)) continue;
            if (settlement.occupyingRealmId != null
                    && !war.opposing(army.realmId, settlement.occupyingRealmId.longValue())) continue;
            double distance = Math.hypot(army.position.x - settlement.position.x,
                    army.position.y - settlement.position.y);
            if (war.targetProvinceId != null && settlement.provinceId == war.targetProvinceId.longValue()) {
                distance -= 100.0;
            }
            Realm controllerRealm = world.realms.get(controller);
            if (controllerRealm != null && controllerRealm.capitalSettlementId != null
                    && controllerRealm.capitalSettlementId.longValue() == settlement.id) distance -= 25.0;
            if (distance < bestDistance) {
                best = settlement;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Settlement patrolSettlement(Army army, long currentMinute, int role) {
        List<Settlement> friendly = new ArrayList<>();
        for (Settlement settlement : world.geography.getSettlements().values()) {
            if (settlement.controllerRealmId != null
                    && settlement.controllerRealmId.longValue() == army.realmId) friendly.add(settlement);
        }
        friendly.sort(Comparator.comparingLong(settlement -> settlement.id));
        if (friendly.isEmpty()) return null;
        long day = Math.max(0L, currentMinute / WorldConfig.MINUTES_PER_DAY);
        int index = (int) Math.floorMod(day + army.id + role * 2L, friendly.size());
        Settlement chosen = friendly.get(index);
        if (chosen.id == army.currentSettlementId && friendly.size() > 1) {
            chosen = friendly.get((index + 1) % friendly.size());
        }
        return chosen;
    }

    private boolean sameWarSide(War war, long firstRealmId, long secondRealmId) {
        return war.attackerRealmIds.contains(firstRealmId) && war.attackerRealmIds.contains(secondRealmId)
                || war.defenderRealmIds.contains(firstRealmId) && war.defenderRealmIds.contains(secondRealmId);
    }

    public BattleReport autoResolveEncounter(long firstArmyId, long secondArmyId, long currentMinute) {
        Army first = world.armies.get(firstArmyId);
        Army second = world.armies.get(secondArmyId);
        if (first == null || second == null || first.realmId == second.realmId
                || first.state == Army.ArmyState.DISBANDED || second.state == Army.ArmyState.DISBANDED
                || battleReadyStrength(first) <= 0 || battleReadyStrength(second) <= 0) return null;
        BattleContext battle;
        try {
            battle = battleBridge.createContext(firstArmyId, secondArmyId, false, currentMinute);
        } catch (RuntimeException ignored) {
            return null;
        }
        BattleResult result = battleBridge.autoResolve(battle);
        CommandResult reconciled = battleBridge.reconcile(result);
        return reconciled.accepted ? world.battleReports.get(battle.battleId) : null;
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

    /**
     * Soldiers BattleBridge can actually project: living, uncaptured people
     * in active regiments. This is deliberately stricter than {@link #strength}
     * so the UI never opens a tactical battle against an empty token.
     */
    public int battleReadyStrength(Army army) {
        if (army == null) return 0;
        int ready = 0;
        for (Long regimentId : army.regimentIds) {
            Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null || !regiment.active) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person person = world.people.get(personId);
                if (person != null && person.alive && person.capturedByRealmId == null) ready++;
            }
        }
        return ready;
    }

    /** The adventurer's purse, or null when there is no campaign player. */
    private MoneyAccount playerPurse() {
        if (world.player == null) return null;
        world.WorldParty party = world.parties.get(world.player.partyId);
        return party == null ? null : party.money;
    }

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
        for (Army first : armies) {
            if (!active(first)) continue;
            for (Long nearbyId : world.geography.getSpatialIndex()
                    .queryArmyIds(first.position.x, first.position.y, 12.0)) {
                if (nearbyId <= first.id) continue; // each pair once
                Army second = world.armies.get(nearbyId);
                if (second == null || !active(second) || first.realmId == second.realmId
                        || distance(first, second) > 12.0) continue;
                first.detectedArmyIds.add(second.id);
                second.detectedArmyIds.add(first.id);
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "ARMY_DETECTED"));
                // An enemy may force contact with the player's army, but it
                // must not silently auto-resolve the battle before the player
                // gets the paused Attack / Send troops / Leave choice.
                boolean playerInvolved = first.playerCommanded || second.playerCommanded;
                if (!playerInvolved
                        && (isAggressiveToward(first, second) || isAggressiveToward(second, first))) {
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
        // Military requisition preserves a civilian emergency reserve instead of
        // silently emptying the settlement market.
        int reserve = type == GoodType.GRAIN || type == GoodType.VEGETABLES ? 20 : 0;
        int available = Math.max(0, settlement.publicStockpile.getQuantity(type) - reserve);
        int amount = Math.min(requested, available);
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
