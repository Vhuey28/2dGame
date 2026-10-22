package world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import world.economy.GoodType;
import world.event.WorldEvent;
import world.geography.Road;
import world.geography.Settlement;
import world.politics.Government;
import world.politics.PoliticalFaction;
import world.diplomacy.DiplomaticState;
import world.diplomacy.Treaty;
import world.military.Siege;

/**
 * Immutable presentation snapshot of the simulated campaign world.
 * Swing reads this object instead of iterating collections that the simulation
 * thread may be mutating.
 */
public final class CampaignSnapshot {
    public final long worldMinute;
    public final int speed;
    public final boolean paused;
    public final int livingPopulation;
    public final int householdCount;
    public final int armyCount;
    public final int caravanCount;
    public final int warCount;
    public final int activeTreatyCount;
    public final int activeSiegeCount;
    public final double averageFoodSecurity;
    public final List<RealmView> realms;
    public final List<SettlementView> settlements;
    public final List<RoadView> roads;
    public final List<CaravanView> caravans;
    public final List<ArmyView> armies;
    public final List<ContractView> contracts;
    public final List<PersonView> people;
    public final List<CrimeView> crimes;
    public final PlayerView player;
    public final List<EventView> recentEvents;
    /** Active and recent wars, so the UI can explain who is fighting whom and why. */
    public final List<WarView> wars;
    /** Every siege, active first, with its breach/resolve/food/civilian progress. */
    public final List<SiegeView> sieges;
    /** Titles the player holds plus the crowns of every realm, for the holdings panel. */
    public final List<TitleView> titles;

    private CampaignSnapshot(long worldMinute, int speed, boolean paused,
            int livingPopulation, int householdCount, int armyCount,
            int caravanCount, int warCount, int activeTreatyCount, int activeSiegeCount,
            double averageFoodSecurity,
            List<RealmView> realms, List<SettlementView> settlements,
            List<RoadView> roads, List<CaravanView> caravans, List<ArmyView> armies,
            List<ContractView> contracts, List<PersonView> people, List<CrimeView> crimes,
            PlayerView player, List<EventView> recentEvents,
            List<WarView> wars, List<SiegeView> sieges, List<TitleView> titles) {
        this.worldMinute = worldMinute;
        this.speed = speed;
        this.paused = paused;
        this.livingPopulation = livingPopulation;
        this.householdCount = householdCount;
        this.armyCount = armyCount;
        this.caravanCount = caravanCount;
        this.warCount = warCount;
        this.activeTreatyCount = activeTreatyCount;
        this.activeSiegeCount = activeSiegeCount;
        this.averageFoodSecurity = averageFoodSecurity;
        this.realms = Collections.unmodifiableList(realms);
        this.settlements = Collections.unmodifiableList(settlements);
        this.roads = Collections.unmodifiableList(roads);
        this.caravans = Collections.unmodifiableList(caravans);
        this.armies = Collections.unmodifiableList(armies);
        this.contracts = Collections.unmodifiableList(contracts);
        this.people = Collections.unmodifiableList(people);
        this.crimes = Collections.unmodifiableList(crimes);
        this.player = player;
        this.recentEvents = Collections.unmodifiableList(recentEvents);
        this.wars = Collections.unmodifiableList(wars);
        this.sieges = Collections.unmodifiableList(sieges);
        this.titles = Collections.unmodifiableList(titles);
    }

    public static CampaignSnapshot capture(WorldSimulation simulation) {
        WorldState world = simulation.getWorld();
        long minute = simulation.getClock().getWorldMinute();
        world.indexes.ensureBuilt(world, minute);

        int living = 0;
        for (Person person : world.people.values()) {
            if (person.alive) living++;
        }

        double totalFoodSecurity = 0.0;
        int occupiedHouseholds = 0;
        for (Household household : world.households.values()) {
            if (household.getMemberCount() > 0) {
                totalFoodSecurity += household.foodSecurity;
                occupiedHouseholds++;
            }
        }
        double averageFood = occupiedHouseholds == 0 ? 0.0 : totalFoodSecurity / occupiedHouseholds;

        List<RealmView> realmViews = new ArrayList<>();
        for (Realm realm : world.realms.values()) {
            int realmPopulation = 0;
            int realmSettlements = 0;
            for (Settlement settlement : world.geography.getSettlements().values()) {
                if (settlement.controllerRealmId != null && settlement.controllerRealmId == realm.id) {
                    realmSettlements++;
                    realmPopulation += countPopulation(world, settlement.id);
                }
            }
            Person ruler = realm.rulerPersonId == null ? null : world.people.get(realm.rulerPersonId);
            Government government = world.governments.get(realm.governmentId);
            String strongestFaction = "None";
            double strongestSupport = 0.0;
            int factionCount = 0;
            for (PoliticalFaction faction : world.politicalFactions.values()) {
                if (faction.realmId != realm.id || !faction.active) continue;
                factionCount++;
                if (faction.support >= strongestSupport) {
                    strongestSupport = faction.support;
                    strongestFaction = faction.goal.toString();
                }
            }
            int treatyCount = 0;
            String diplomacySummary = "No foreign relations";
            for (Treaty treaty : world.treaties.values()) {
                if (treaty.includes(realm.id) && treaty.isActiveAt(minute)) treatyCount++;
            }
            for (DiplomaticState state : world.diplomaticStates.values()) {
                if (state.firstRealmId == realm.id || state.secondRealmId == realm.id) {
                    diplomacySummary = "relations " + state.relationshipScore()
                            + ", trust " + state.trust;
                    break;
                }
            }
            realmViews.add(new RealmView(realm.id, realm.name,
                    realm.treasury == null ? 0L : realm.treasury.copperCoins,
                    realmPopulation, realmSettlements, realm.stability,
                    realm.legitimacy, realm.warExhaustion,
                    ruler == null ? "Vacant" : ruler.givenName + " " + ruler.familyName,
                    government == null ? "Unknown" : government.type.toString(),
                    government == null ? "Unknown" : government.successionLaw.toString(),
                    factionCount, strongestFaction, strongestSupport, treatyCount, diplomacySummary,
                    snapshotLaws(government)));
        }
        realmViews.sort(Comparator.comparing(view -> view.name));

        Set<Long> capitalSettlementIds = new HashSet<>();
        for (Realm realm : world.realms.values()) {
            if (realm.capitalSettlementId != null) capitalSettlementIds.add(realm.capitalSettlementId);
        }

        List<SettlementView> settlementViews = new ArrayList<>();
        for (Settlement settlement : world.geography.getSettlements().values()) {
            int population = countPopulation(world, settlement.id);
            int households = 0;
            double settlementFood = 0.0;
            int grain = settlement.publicStockpile.getQuantity(GoodType.GRAIN);
            int vegetables = settlement.publicStockpile.getQuantity(GoodType.VEGETABLES);
            for (Long householdId : world.indexes.householdsAt(settlement.id)) {
                Household household = world.households.get(householdId);
                if (household != null && household.getMemberCount() > 0) {
                    households++;
                    settlementFood += household.foodSecurity;
                    grain += household.inventory.getQuantity(GoodType.GRAIN);
                    vegetables += household.inventory.getQuantity(GoodType.VEGETABLES);
                }
            }
            double food = households == 0 ? 0.0 : settlementFood / households;
            world.geography.Province province = world.geography.getProvince(settlement.provinceId);
            settlementViews.add(new SettlementView(settlement.id, settlement.name,
                    settlement.controllerRealmId, settlement.occupyingRealmId,
                    settlement.position.x, settlement.position.y,
                    population, households, settlement.treasury.copperCoins, grain,
                    vegetables, settlement.market.getLastPrice(GoodType.GRAIN),
                    settlement.market.getLastPrice(GoodType.VEGETABLES),
                    food, settlement.security, settlement.unrest,
                    settlement.type == null ? "VILLAGE" : settlement.type.toString(),
                    province == null ? "Unknown province" : province.name,
                    province == null || province.terrain == null
                            ? "PLAINS" : province.terrain.toString(),
                    capitalSettlementIds.contains(settlement.id)));
        }
        settlementViews.sort(Comparator.comparing(view -> view.name));

        List<RoadView> roadViews = new ArrayList<>();
        Set<String> roadPairs = new HashSet<>();
        for (Road road : world.geography.getRouteGraph().getAllRoads()) {
            long low = Math.min(road.fromSettlementId, road.toSettlementId);
            long high = Math.max(road.fromSettlementId, road.toSettlementId);
            String pair = low + ":" + high;
            if (!roadPairs.add(pair)) continue;
            Settlement from = world.geography.getSettlement(road.fromSettlementId);
            Settlement to = world.geography.getSettlement(road.toSettlementId);
            if (from == null || to == null) continue;
            roadViews.add(new RoadView(from.position.x, from.position.y,
                    to.position.x, to.position.y, road.dangerLevel, road.blocked));
        }

        List<CaravanView> caravanViews = new ArrayList<>();
        for (Caravan caravan : world.caravans.values()) {
            if (caravan.position == null || caravan.state == Caravan.CaravanState.DESTROYED) continue;
            GoodType cargoType = caravan.primaryCargo();
            Person caravanLeader = world.people.get(caravan.leaderPersonId);
            caravanViews.add(new CaravanView(caravan.id, caravan.position.x, caravan.position.y,
                    caravan.state.toString(), cargoType == null ? "Empty" : cargoType.toString(),
                    caravan.cargoQuantity(), caravan.cash.copperCoins, caravan.completedTrips,
                    caravan.currentSettlementId, caravan.destinationSettlementId,
                    caravanLeader == null ? "Unknown merchant"
                            : caravanLeader.givenName + " " + caravanLeader.familyName,
                    caravan.guardPersonIds.size()));
        }
        caravanViews.sort(Comparator.comparingLong(view -> view.id));

        Long playerRealmForHostility = null;
        if (world.player != null) {
            playerRealmForHostility = world.player.affiliatedRealmId;
            for (Realm realm : world.realms.values()) {
                if (realm.rulerPersonId != null && realm.rulerPersonId == world.player.personId) {
                    playerRealmForHostility = realm.id;
                    break;
                }
            }
            if (playerRealmForHostility == null && world.player.commandedArmyId != null) {
                Army commanded = world.armies.get(world.player.commandedArmyId);
                if (commanded != null) playerRealmForHostility = commanded.realmId;
            }
        }
        Set<Long> hostileRealmIds = new HashSet<>();
        if (playerRealmForHostility != null) {
            for (War war : world.wars.values()) {
                if (war.state == War.WarState.ENDED) continue;
                if (war.attackerRealmIds.contains(playerRealmForHostility)) {
                    hostileRealmIds.addAll(war.defenderRealmIds);
                }
                if (war.defenderRealmIds.contains(playerRealmForHostility)) {
                    hostileRealmIds.addAll(war.attackerRealmIds);
                }
            }
        }

        List<ArmyView> armyViews = new ArrayList<>();
        for (Army army : world.armies.values()) {
            if (army.position == null || army.state == Army.ArmyState.DISBANDED) continue;
            int strength = 0;
            for (Long regimentId : army.regimentIds) {
                world.military.Regiment regiment = world.regiments.get(regimentId);
                if (regiment != null) strength += regiment.livingStrength(world);
            }
            Person commander = world.people.get(army.commanderPersonId);
            armyViews.add(new ArmyView(army.id, army.realmId, army.position.x, army.position.y,
                    strength, army.morale, army.fatigue,
                    army.supplies.getQuantity(GoodType.GRAIN), army.order.toString(), army.state.toString(),
                    army.currentSettlementId, army.destinationSettlementId,
                    army.daysWithoutFood, army.playerCommanded,
                    commander == null ? "Unknown commander"
                            : commander.givenName + " " + commander.familyName,
                    !army.playerCommanded && hostileRealmIds.contains(army.realmId)));
        }
        armyViews.sort(Comparator.comparingLong(view -> view.id));

        List<ContractView> contractViews = new ArrayList<>();
        for (Contract contract : world.contracts.values()) {
            Settlement issuer = world.geography.getSettlement(contract.issuerSettlementId);
            Settlement destination = world.geography.getSettlement(contract.destinationSettlementId);
            String issuerName = issuer == null ? "Unknown" : issuer.name;
            String destinationName = destination == null ? "Unknown" : destination.name;
            String objective = switch (contract.type) {
                case DELIVER_GOODS -> "Bring " + contract.requiredQuantity + " "
                        + contract.requiredGood + " to " + destinationName;
                case DELIVER_MESSAGE -> "Carry the sealed message to " + destinationName;
                case ESCORT_ROUTE -> "Travel safely from " + issuerName + " to " + destinationName;
            };
            int carried = world.player == null || contract.requiredGood == null ? 0
                    : world.player.cargo.getQuantity(contract.requiredGood);
            String progress = contract.type == Contract.ContractType.DELIVER_GOODS
                    ? "Cargo " + Math.min(carried, contract.requiredQuantity) + "/" + contract.requiredQuantity
                    : playerAt(world, contract.destinationSettlementId) ? "At destination" : "Destination not reached";
            contractViews.add(new ContractView(contract.id, contract.type.toString(),
                    contract.issuerSettlementId, issuerName, contract.destinationSettlementId, destinationName,
                    contract.deadlineMinute, Math.max(0L, contract.deadlineMinute - minute),
                    contract.rewardCoins, contract.penaltyCoins, contract.requiredGood == null
                            ? "None" : contract.requiredGood.toString(), contract.requiredQuantity,
                    objective, progress, contract.status.toString()));
        }
        contractViews.sort(Comparator.comparingLong(view -> view.id));

        List<PersonView> personViews = new ArrayList<>();
        for (Person person : world.people.values()) {
            if (!person.alive) continue;
            personViews.add(new PersonView(person.id, person.givenName + " " + person.familyName,
                    person.currentSettlementId, person.type.toString(), person.currentActivity.toString(),
                    person.employerId, person.travelingPartyId));
        }
        personViews.sort(Comparator.comparingLong(view -> view.id));

        List<CrimeView> crimeViews = new ArrayList<>();
        for (CrimeIncident incident : world.crimeIncidents.values()) {
            crimeViews.add(new CrimeView(incident.id, incident.settlementId, incident.offenderPersonId,
                    incident.victimPersonId, incident.type.toString(), incident.minute,
                    incident.discovered, incident.severity));
        }
        crimeViews.sort(Comparator.comparingLong(view -> view.minute));

        PlayerView playerView = null;
        if (world.player != null) {
            Household playerHousehold = world.households.get(world.player.householdId);
            long coins = playerHousehold == null ? 0L : playerHousehold.account.copperCoins;
            WorldParty playerParty = world.parties.get(world.player.partyId);
            Long affiliatedRealmId = world.player.affiliatedRealmId;
            PlayerCampaignState.KingdomRole kingdomRole = world.player.kingdomRole;
            boolean playerIsRuler = false;
            for (Realm realm : world.realms.values()) {
                if (realm.rulerPersonId != null && realm.rulerPersonId == world.player.personId) {
                    affiliatedRealmId = realm.id;
                    kingdomRole = PlayerCampaignState.KingdomRole.KING;
                    playerIsRuler = true;
                    break;
                }
            }
            Realm affiliatedRealm = affiliatedRealmId == null ? null : world.realms.get(affiliatedRealmId);
            Realm captorRealm = world.player.captorRealmId == null
                    ? null : world.realms.get(world.player.captorRealmId);
            Settlement playerSettlement = world.geography.getSettlement(world.player.currentSettlementId);
            double playerWorldX = playerParty != null && playerParty.position != null
                    ? playerParty.position.x
                    : playerSettlement == null || playerSettlement.position == null
                            ? 0.0 : playerSettlement.position.x;
            double playerWorldY = playerParty != null && playerParty.position != null
                    ? playerParty.position.y
                    : playerSettlement == null || playerSettlement.position == null
                            ? 0.0 : playerSettlement.position.y;
            Long travelDestinationId = world.player.isTraveling()
                    ? world.player.travelDestinationSettlementId : null;
            Settlement travelDestination = travelDestinationId == null
                    ? null : world.geography.getSettlement(travelDestinationId);
            double travelDestinationX = Double.NaN;
            double travelDestinationY = Double.NaN;
            if (world.player.isTraveling()) {
                if (world.player.travelMode == PlayerCampaignState.TravelMode.CROSS_COUNTRY) {
                    travelDestinationX = world.player.travelDestinationX;
                    travelDestinationY = world.player.travelDestinationY;
                } else if (travelDestination != null && travelDestination.position != null) {
                    travelDestinationX = travelDestination.position.x;
                    travelDestinationY = travelDestination.position.y;
                }
            }
            String travelDestinationName = travelDestination != null ? travelDestination.name
                    : world.player.isTraveling() ? "the countryside" : null;
            Settlement nearestSettlement = null;
            double nearestSettlementDistance = Double.POSITIVE_INFINITY;
            for (Settlement settlement : world.geography.getSettlements().values()) {
                if (settlement.position == null) continue;
                double distance = Math.hypot(settlement.position.x - playerWorldX,
                        settlement.position.y - playerWorldY);
                if (distance < nearestSettlementDistance
                        || distance == nearestSettlementDistance && nearestSettlement != null
                                && settlement.id < nearestSettlement.id) {
                    nearestSettlement = settlement;
                    nearestSettlementDistance = distance;
                }
            }
            double travelSpeedDisplay = world.player.travelSpeedPerHour;
            if (world.player.isTraveling()
                    && world.player.travelMode == PlayerCampaignState.TravelMode.CROSS_COUNTRY) {
                travelSpeedDisplay *= CampaignSession.crossCountryTerrainSpeedFactor(
                        world, playerWorldX, playerWorldY);
            }
            Person playerPerson = world.people.get(world.player.personId);
            boolean playerHasHealthToFight = playerPerson != null && playerPerson.alive
                    && playerPerson.health != null && playerPerson.health.healthLevel > 0.0;
            int playerHealthPercent = !playerHasHealthToFight ? 0
                    : Math.max(1, Math.min(100,
                            (int) Math.round(playerPerson.health.healthLevel)));
            boolean playerWounded = playerPerson != null && playerPerson.health != null
                    && playerPerson.health.wounded;
            playerView = new PlayerView(world.player.currentSettlementId, coins,
                    world.player.cargo.getQuantity(GoodType.GRAIN),
                    world.player.cargo.getQuantity(GoodType.VEGETABLES),
                    world.player.cargo.totalQuantity(), world.player.cargoCapacity,
                    world.player.reputation, playerParty == null ? java.util.List.of(world.player.personId)
                            : new ArrayList<>(playerParty.memberPersonIds),
                    world.player.acceptedContractIds.size(), affiliatedRealmId,
                    kingdomRole.toString(), affiliatedRealm == null ? null : affiliatedRealm.name,
                    playerIsRuler, world.player.captiveDaysRemaining(minute), world.player.ransomCoins,
                    captorRealm == null ? null : captorRealm.name, world.player.commandedArmyId,
                    affiliatedRealmId == null ? 0
                            : world.player.realmReputation.getOrDefault(affiliatedRealmId, 0),
                    playerWorldX, playerWorldY, world.player.isTraveling(), travelDestinationId,
                    travelDestinationName, travelDestinationX, travelDestinationY,
                    world.player.atSettlement && !world.player.isTraveling(),
                    nearestSettlement == null ? null : nearestSettlement.name,
                    travelSpeedDisplay, playerTravelEtaMinutes(world),
                    playerHealthPercent, playerHasHealthToFight, playerWounded);
        }

        List<EventView> eventViews = new ArrayList<>();
        List<WorldEvent> events = simulation.getContext().eventHistory.getRecent();
        int first = Math.max(0, events.size() - 6);
        for (int i = first; i < events.size(); i++) {
            WorldEvent event = events.get(i);
            eventViews.add(new EventView(event.eventId, event.worldMinute, event.type, event.detail));
        }

        int activeTreaties = 0;
        for (Treaty treaty : world.treaties.values()) {
            if (treaty.isActiveAt(minute)) activeTreaties++;
        }
        int activeWars = 0;
        for (War war : world.wars.values()) if (war.state == War.WarState.ACTIVE) activeWars++;
        int activeSieges = 0;
        for (Siege siege : world.sieges.values()) {
            if (siege.state == Siege.SiegeState.ACTIVE) activeSieges++;
        }
        List<WarView> warViews = new ArrayList<>();
        for (War war : world.wars.values()) {
            warViews.add(new WarView(war.id, war.goal.toString(), war.state.toString(),
                    realmNames(world, war.attackerRealmIds), realmNames(world, war.defenderRealmIds),
                    war.attackerScore, war.defenderScore, war.attackerExhaustion, war.defenderExhaustion,
                    provinceName(world, war.targetProvinceId), titleName(world, war.targetTitleId),
                    contributions(world, war.contributionByRealm), war.startMinute,
                    daysSince(war.startMinute, minute), war.declarationReason,
                    involvesPlayerRealm(world, war)));
        }
        warViews.sort((a, b) -> {
            boolean firstActive = "ACTIVE".equals(a.state);
            boolean secondActive = "ACTIVE".equals(b.state);
            if (firstActive != secondActive) return firstActive ? -1 : 1;
            return Long.compare(a.id, b.id);
        });

        List<SiegeView> siegeViews = new ArrayList<>();
        for (Siege siege : world.sieges.values()) {
            Settlement besieged = world.geography.getSettlement(siege.settlementId);
            Army besieger = world.armies.get(siege.besiegingArmyId);
            War siegeWar = world.wars.get(siege.warId);
            List<Long> besiegingArmyIds = siege.besiegingArmyIds();
            int activeBesiegerStrength = 0;
            for (Long besiegingArmyId : besiegingArmyIds) {
                Army participant = besiegingArmyId == null ? null : world.armies.get(besiegingArmyId);
                // Match WarSystem: a registered army contributes only once it
                // is physically in the siege lines under BESIEGE orders. An
                // army still marching to join is committed, but not counted yet.
                boolean inSiegeLines = participant != null
                        && participant.currentSettlementId == siege.settlementId
                        && participant.state != Army.ArmyState.TRAVELING
                        && participant.state != Army.ArmyState.DISBANDED
                        && participant.state != Army.ArmyState.ENGAGED
                        && participant.order == Army.ArmyOrder.BESIEGE;
                if (inSiegeLines) {
                    activeBesiegerStrength += countRegimentSoldiers(world, participant);
                }
            }
            Long commandedArmyId = world.player == null ? null : world.player.commandedArmyId;
            Army playerArmy = commandedArmyId == null ? null : world.armies.get(commandedArmyId);
            boolean playerArmyJoined = commandedArmyId != null && siege.isBesiegingArmy(commandedArmyId);
            boolean playerArmyAtSiege = playerArmyJoined && playerArmy != null
                    && playerArmy.currentSettlementId == siege.settlementId
                    && playerArmy.state != Army.ArmyState.TRAVELING
                    && playerArmy.state != Army.ArmyState.DISBANDED
                    && playerArmy.state != Army.ArmyState.ENGAGED
                    && playerArmy.order == Army.ArmyOrder.BESIEGE;
            boolean playerArmyCanJoin = playerArmy != null
                    && playerArmy.state != Army.ArmyState.DISBANDED
                    && playerArmy.state != Army.ArmyState.ENGAGED
                    && playerArmy.state != Army.ArmyState.ROUTED
                    && countRegimentSoldiers(world, playerArmy) > 0
                    && siegeWar != null && siegeWar.state == War.WarState.ACTIVE
                    && besieged != null && besieged.controllerRealmId != null
                    && siegeWar.opposing(playerArmy.realmId, besieged.controllerRealmId.longValue())
                    && (besieged.occupyingRealmId == null
                            || siegeWar.opposing(playerArmy.realmId,
                                    besieged.occupyingRealmId.longValue()));
            siegeViews.add(new SiegeView(siege.id, siege.warId, siege.settlementId,
                    besieged == null ? "Unknown settlement" : besieged.name,
                    realmName(world, besieged == null ? null : besieged.controllerRealmId),
                    siege.besiegingArmyId, realmName(world, besieger == null ? null : besieger.realmId),
                    activeBesiegerStrength,
                    siege.state.toString(), siege.breachProgress, siege.defenderResolve,
                    siege.foodConsumed, siege.civilianDeaths, daysSince(siege.startMinute, minute),
                    siegeWar == null ? null : siegeWar.goal.toString(),
                    involvesPlayerRealmSiege(world, siege), besiegingArmyIds,
                    playerArmyJoined, playerArmyAtSiege, playerArmyCanJoin));
        }
        siegeViews.sort((a, b) -> {
            boolean firstActive = "ACTIVE".equals(a.state);
            boolean secondActive = "ACTIVE".equals(b.state);
            if (firstActive != secondActive) return firstActive ? -1 : 1;
            int byBreach = Double.compare(b.breachProgress, a.breachProgress);
            return byBreach != 0 ? byBreach : Long.compare(a.id, b.id);
        });

        List<TitleView> titleViews = new ArrayList<>();
        for (world.politics.Title title : world.titles.values()) {
            Person holder = title.holderPersonId == null ? null : world.people.get(title.holderPersonId);
            int liveClaims = 0;
            for (Long claimId : title.claimIds) {
                world.politics.Claim claim = world.claims.get(claimId);
                if (claim != null && (claim.expiryMinute == null || claim.expiryMinute > minute)) liveClaims++;
            }
            world.geography.Settlement holding = title.settlementId == null
                    ? null : world.geography.getSettlement(title.settlementId);
            titleViews.add(new TitleView(title.id, title.name, title.rank.toString(), title.realmId,
                    realmName(world, title.realmId), title.holderPersonId,
                    holder == null ? "Vacant" : (holder.givenName + " " + holder.familyName).trim(),
                    provinceName(world, title.deJureProvinceId), liveClaims,
                    world.player != null && title.holderPersonId != null
                            && title.holderPersonId == world.player.personId,
                    isRealmCrown(world, title),
                    holding == null ? null : holding.name,
                    CampaignSession.dailyRent(title, holding)));
        }
        titleViews.sort((a, b) -> {
            if (a.heldByPlayer != b.heldByPlayer) return a.heldByPlayer ? -1 : 1;
            if (a.isCrown != b.isCrown) return a.isCrown ? -1 : 1;
            return Long.compare(a.id, b.id);
        });

        return new CampaignSnapshot(minute, simulation.getClock().getSpeed(),
                simulation.getClock().isPaused(), living, world.households.size(),
                armyViews.size(), world.caravans.size(), activeWars, activeTreaties, activeSieges, averageFood,
                realmViews, settlementViews, roadViews, caravanViews, armyViews,
                contractViews, personViews, crimeViews, playerView, eventViews,
                warViews, siegeViews, titleViews);
    }

    private static long daysSince(long startMinute, long currentMinute) {
        return Math.max(0L, (currentMinute - startMinute) / WorldConfig.MINUTES_PER_DAY);
    }

    private static String realmName(WorldState world, Long realmId) {
        if (realmId == null) return null;
        Realm realm = world.realms.get(realmId);
        return realm == null ? "Realm #" + realmId : realm.name;
    }

    private static List<String> realmNames(WorldState world, Set<Long> realmIds) {
        List<String> names = new ArrayList<>();
        for (Long realmId : realmIds) names.add(realmName(world, realmId));
        Collections.sort(names);
        return names;
    }

    private static String provinceName(WorldState world, Long provinceId) {
        if (provinceId == null) return null;
        world.geography.Province province = world.geography.getProvince(provinceId);
        return province == null ? "Province #" + provinceId : province.name;
    }

    private static String titleName(WorldState world, Long titleId) {
        if (titleId == null) return null;
        world.politics.Title title = world.titles.get(titleId);
        return title == null ? "Title #" + titleId : title.name;
    }

    private static List<String> contributions(WorldState world, java.util.Map<Long, Double> contributionByRealm) {
        List<String> lines = new ArrayList<>();
        for (java.util.Map.Entry<Long, Double> entry : contributionByRealm.entrySet()) {
            lines.add(realmName(world, entry.getKey()) + " " + Math.round(entry.getValue()));
        }
        Collections.sort(lines);
        return lines;
    }

    private static boolean involvesPlayerRealm(WorldState world, War war) {
        if (world.player == null || world.player.affiliatedRealmId == null) return false;
        return war.includes(world.player.affiliatedRealmId);
    }

    private static boolean involvesPlayerRealmSiege(WorldState world, Siege siege) {
        if (world.player == null) return false;
        if (world.player.commandedArmyId != null
                && siege.isBesiegingArmy(world.player.commandedArmyId)) return true;
        Settlement besieged = world.geography.getSettlement(siege.settlementId);
        Long playerRealmId = world.player.affiliatedRealmId;
        if (playerRealmId != null) {
            for (Long besiegingArmyId : siege.besiegingArmyIds()) {
                Army besieger = besiegingArmyId == null ? null : world.armies.get(besiegingArmyId);
                if (besieger != null && besieger.realmId == playerRealmId.longValue()) return true;
            }
        }
        return besieged != null && besieged.controllerRealmId != null
                && besieged.controllerRealmId == playerRealmId;
    }

    private static int countRegimentSoldiers(WorldState world, Army army) {
        int total = 0;
        for (Long regimentId : army.regimentIds) {
            world.military.Regiment regiment = world.regiments.get(regimentId);
            if (regiment == null || !regiment.active) continue;
            for (Long personId : regiment.soldierPersonIds) {
                Person soldier = world.people.get(personId);
                if (soldier != null && soldier.alive) total++;
            }
        }
        return total;
    }

    private static boolean isRealmCrown(WorldState world, world.politics.Title title) {
        for (Realm realm : world.realms.values()) {
            if (realm.rulerTitleId == title.id) return true;
        }
        return false;
    }

    private static java.util.Map<String, Integer> snapshotLaws(Government government) {
        java.util.Map<String, Integer> laws = new java.util.LinkedHashMap<>();
        if (government != null) {
            for (Government.LawType law : Government.LawType.values()) {
                laws.put(law.toString(), government.getLawLevel(law));
            }
        }
        return java.util.Collections.unmodifiableMap(laws);
    }

    private static boolean playerAt(WorldState world, long settlementId) {
        return world.player != null && world.player.atSettlement
                && !world.player.isTraveling()
                && world.player.currentSettlementId == settlementId;
    }

    /** Remaining travel time for the continuously moving player party, in world minutes. */
    private static long playerTravelEtaMinutes(WorldState world) {
        if (world.player == null || !world.player.isTraveling()
                || world.player.travelSpeedPerHour <= 0.0) return 0L;
        if (world.player.travelMode == PlayerCampaignState.TravelMode.CROSS_COUNTRY) {
            WorldParty party = world.parties.get(world.player.partyId);
            Settlement anchor = world.geography.getSettlement(world.player.currentSettlementId);
            double startX = party != null && party.position != null ? party.position.x
                    : anchor == null || anchor.position == null ? world.player.travelDestinationX
                            : anchor.position.x;
            double startY = party != null && party.position != null ? party.position.y
                    : anchor == null || anchor.position == null ? world.player.travelDestinationY
                            : anchor.position.y;
            double distance = Math.hypot(world.player.travelDestinationX - startX,
                    world.player.travelDestinationY - startY);
            if (!(distance > 0.0)) return 0L;
            int samples = Math.max(1, (int) Math.ceil(distance / 5.0));
            double plainsEquivalentDistance = 0.0;
            double segmentDistance = distance / samples;
            for (int i = 0; i < samples; i++) {
                double midpoint = (i + 0.5) / samples;
                double sampleX = startX + (world.player.travelDestinationX - startX) * midpoint;
                double sampleY = startY + (world.player.travelDestinationY - startY) * midpoint;
                double factor = Math.max(0.1,
                        CampaignSession.crossCountryTerrainSpeedFactor(world, sampleX, sampleY));
                plainsEquivalentDistance += segmentDistance / factor;
            }
            return Math.max(0L, Math.round(
                    plainsEquivalentDistance / world.player.travelSpeedPerHour * 60.0));
        }
        double remainingDistance = 0.0;
        java.util.List<Long> route = world.player.travelRouteSettlementIds;
        for (int i = Math.max(1, world.player.travelRouteIndex); i < route.size(); i++) {
            Settlement from = world.geography.getSettlement(route.get(i - 1));
            Settlement to = world.geography.getSettlement(route.get(i));
            if (from == null || to == null || from.position == null || to.position == null) continue;
            double segment = Math.hypot(to.position.x - from.position.x,
                    to.position.y - from.position.y);
            if (i == world.player.travelRouteIndex) {
                remainingDistance += (1.0 - world.player.travelSegmentProgress) * segment;
            } else {
                remainingDistance += segment;
            }
        }
        return Math.max(0L, Math.round(remainingDistance / world.player.travelSpeedPerHour * 60.0));
    }

    private static int countPopulation(WorldState world, long settlementId) {
        return world.indexes.livingPeopleAt(settlementId).size();
    }

    public SettlementView findSettlement(long id) {
        for (SettlementView settlement : settlements) {
            if (settlement.id == id) return settlement;
        }
        return null;
    }

    public RealmView findRealm(long id) {
        for (RealmView realm : realms) if (realm.id == id) return realm;
        return null;
    }

    public ArmyView findArmy(long id) {
        for (ArmyView army : armies) if (army.id == id) return army;
        return null;
    }

    public CaravanView findCaravan(long id) {
        for (CaravanView caravan : caravans) if (caravan.id == id) return caravan;
        return null;
    }

    public String getDateLabel() {
        return String.format("Year %d, Month %d, Day %d - %02d:%02d",
                WorldCalendar.getYear(worldMinute) + 1,
                WorldCalendar.getMonth(worldMinute) + 1,
                WorldCalendar.getDay(worldMinute) + 1,
                WorldCalendar.getHour(worldMinute),
                WorldCalendar.getMinuteOfHour(worldMinute));
    }

    public static final class RealmView {
        public final long id;
        public final String name;
        public final long treasury;
        public final int population;
        public final int settlementCount;
        public final double stability;
        public final double legitimacy;
        public final double warExhaustion;
        public final String rulerName;
        public final String governmentType;
        public final String successionLaw;
        public final int factionCount;
        public final String strongestFaction;
        public final double strongestFactionSupport;
        public final int treatyCount;
        public final String diplomacySummary;
        public final java.util.Map<String, Integer> laws;

        RealmView(long id, String name, long treasury, int population,
                int settlementCount, double stability, double legitimacy,
                double warExhaustion, String rulerName, String governmentType,
                String successionLaw, int factionCount, String strongestFaction,
                double strongestFactionSupport, int treatyCount, String diplomacySummary,
                java.util.Map<String, Integer> laws) {
            this.id = id;
            this.name = name;
            this.treasury = treasury;
            this.population = population;
            this.settlementCount = settlementCount;
            this.stability = stability;
            this.legitimacy = legitimacy;
            this.warExhaustion = warExhaustion;
            this.rulerName = rulerName;
            this.governmentType = governmentType;
            this.successionLaw = successionLaw;
            this.factionCount = factionCount;
            this.strongestFaction = strongestFaction;
            this.strongestFactionSupport = strongestFactionSupport;
            this.treatyCount = treatyCount;
            this.diplomacySummary = diplomacySummary;
            this.laws = laws;
        }
    }

    public static final class SettlementView {
        public final long id;
        public final String name;
        public final Long realmId;
        public final Long occupyingRealmId;
        public final double worldX;
        public final double worldY;
        public final int population;
        public final int households;
        public final long treasury;
        public final int grain;
        public final int vegetables;
        public final long grainPrice;
        public final long vegetablePrice;
        public final double foodSecurity;
        public final double security;
        public final double unrest;
        /** Map presentation: settlement type, province/terrain, and capital marker. */
        public final String type;
        public final String provinceName;
        public final String terrain;
        public final boolean capital;

        SettlementView(long id, String name, Long realmId, Long occupyingRealmId,
                double worldX, double worldY, int population, int households, long treasury,
                int grain, int vegetables, long grainPrice, long vegetablePrice,
                double foodSecurity, double security, double unrest,
                String type, String provinceName, String terrain, boolean capital) {
            this.id = id;
            this.name = name;
            this.realmId = realmId;
            this.occupyingRealmId = occupyingRealmId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.population = population;
            this.households = households;
            this.treasury = treasury;
            this.grain = grain;
            this.vegetables = vegetables;
            this.grainPrice = grainPrice;
            this.vegetablePrice = vegetablePrice;
            this.foodSecurity = foodSecurity;
            this.security = security;
            this.unrest = unrest;
            this.type = type;
            this.provinceName = provinceName;
            this.terrain = terrain;
            this.capital = capital;
        }
    }

    public static final class RoadView {
        public final double fromX;
        public final double fromY;
        public final double toX;
        public final double toY;
        public final double danger;
        public final boolean blocked;

        RoadView(double fromX, double fromY, double toX, double toY,
                double danger, boolean blocked) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.danger = danger;
            this.blocked = blocked;
        }
    }

    public static final class PlayerView {
        public final long settlementId;
        public final long coins;
        public final int grain;
        public final int vegetables;
        public final int cargoUsed;
        public final int cargoCapacity;
        public final int reputation;
        public final int partySize;
        public final List<Long> partyMemberIds;
        public final int activeContracts;
        public final Long affiliatedRealmId;
        public final String kingdomRole;
        public final String kingdomName;
        public final boolean canManageKingdom;
        /** Days of remaining captivity; zero while free. */
        public final int captiveDaysRemaining;
        public final long ransomCoins;
        public final String captorRealmName;
        /** Army the adventurer personally commands, if any. */
        public final Long commandedArmyId;
        public final int realmReputation;
        /** Live campaign-map position and continuous travel state. */
        public final double worldX;
        public final double worldY;
        public final boolean traveling;
        public final Long travelDestinationSettlementId;
        public final String travelDestinationName;
        /** Marked map destination; NaN when the party is not travelling. */
        public final double travelDestinationWorldX;
        public final double travelDestinationWorldY;
        /** True only when physically inside {@link #settlementId}, not merely anchored to it. */
        public final boolean atSettlement;
        public final String nearestSettlementName;
        public final double travelSpeedPerHour;
        public final long travelEtaMinutes;
        /** Strategic health; wounded players may fight at any value above zero. */
        public final int healthPercent;
        public final boolean hasHealthToFight;
        public final boolean wounded;

        public boolean isCaptive() {
            return captiveDaysRemaining > 0;
        }

        public boolean canFight() {
            return hasHealthToFight && !isCaptive();
        }

        PlayerView(long settlementId, long coins, int grain, int vegetables,
                int cargoUsed, int cargoCapacity, int reputation,
                List<Long> partyMemberIds, int activeContracts, Long affiliatedRealmId,
                String kingdomRole, String kingdomName, boolean canManageKingdom,
                int captiveDaysRemaining, long ransomCoins, String captorRealmName,
                Long commandedArmyId, int realmReputation,
                double worldX, double worldY, boolean traveling, Long travelDestinationSettlementId,
                String travelDestinationName, double travelDestinationWorldX,
                double travelDestinationWorldY, boolean atSettlement, String nearestSettlementName,
                double travelSpeedPerHour, long travelEtaMinutes,
                int healthPercent, boolean hasHealthToFight, boolean wounded) {
            this.settlementId = settlementId;
            this.coins = coins;
            this.grain = grain;
            this.vegetables = vegetables;
            this.cargoUsed = cargoUsed;
            this.cargoCapacity = cargoCapacity;
            this.reputation = reputation;
            this.partyMemberIds = Collections.unmodifiableList(new ArrayList<>(partyMemberIds));
            this.partySize = partyMemberIds.size();
            this.activeContracts = activeContracts;
            this.affiliatedRealmId = affiliatedRealmId;
            this.kingdomRole = kingdomRole;
            this.kingdomName = kingdomName;
            this.canManageKingdom = canManageKingdom;
            this.captiveDaysRemaining = captiveDaysRemaining;
            this.ransomCoins = ransomCoins;
            this.captorRealmName = captorRealmName;
            this.commandedArmyId = commandedArmyId;
            this.realmReputation = realmReputation;
            this.worldX = worldX;
            this.worldY = worldY;
            this.traveling = traveling;
            this.travelDestinationSettlementId = travelDestinationSettlementId;
            this.travelDestinationName = travelDestinationName;
            this.travelDestinationWorldX = travelDestinationWorldX;
            this.travelDestinationWorldY = travelDestinationWorldY;
            this.atSettlement = atSettlement;
            this.nearestSettlementName = nearestSettlementName;
            this.travelSpeedPerHour = travelSpeedPerHour;
            this.travelEtaMinutes = travelEtaMinutes;
            this.healthPercent = healthPercent;
            this.hasHealthToFight = hasHealthToFight;
            this.wounded = wounded;
        }
    }

    public static final class PersonView {
        public final long id;
        public final String name;
        public final Long settlementId;
        public final String type;
        public final String activity;
        public final Long employerId;
        public final Long partyId;

        PersonView(long id, String name, Long settlementId, String type, String activity,
                Long employerId, Long partyId) {
            this.id = id;
            this.name = name;
            this.settlementId = settlementId;
            this.type = type;
            this.activity = activity;
            this.employerId = employerId;
            this.partyId = partyId;
        }
    }

    public static final class CrimeView {
        public final long id;
        public final long settlementId;
        public final long offenderPersonId;
        public final Long victimPersonId;
        public final String type;
        public final long minute;
        public final boolean discovered;
        public final int severity;

        CrimeView(long id, long settlementId, long offenderPersonId, Long victimPersonId,
                String type, long minute, boolean discovered, int severity) {
            this.id = id;
            this.settlementId = settlementId;
            this.offenderPersonId = offenderPersonId;
            this.victimPersonId = victimPersonId;
            this.type = type;
            this.minute = minute;
            this.discovered = discovered;
            this.severity = severity;
        }
    }

    public static final class ContractView {
        public final long id;
        public final String type;
        public final long issuerSettlementId;
        public final String issuerName;
        public final long destinationSettlementId;
        public final String destinationName;
        public final long deadlineMinute;
        public final long remainingMinutes;
        public final long rewardCoins;
        public final long penaltyCoins;
        public final String requiredGood;
        public final int requiredQuantity;
        public final String objective;
        public final String progress;
        public final String status;

        ContractView(long id, String type, long issuerSettlementId, String issuerName,
                long destinationSettlementId, String destinationName, long deadlineMinute,
                long remainingMinutes, long rewardCoins, long penaltyCoins, String requiredGood,
                int requiredQuantity, String objective, String progress, String status) {
            this.id = id;
            this.type = type;
            this.issuerSettlementId = issuerSettlementId;
            this.issuerName = issuerName;
            this.destinationSettlementId = destinationSettlementId;
            this.destinationName = destinationName;
            this.deadlineMinute = deadlineMinute;
            this.remainingMinutes = remainingMinutes;
            this.rewardCoins = rewardCoins;
            this.penaltyCoins = penaltyCoins;
            this.requiredGood = requiredGood;
            this.requiredQuantity = requiredQuantity;
            this.objective = objective;
            this.progress = progress;
            this.status = status;
        }

        public long remainingDays() {
            return (remainingMinutes + WorldConfig.MINUTES_PER_DAY - 1) / WorldConfig.MINUTES_PER_DAY;
        }
    }

    public static final class CaravanView {
        public final long id;
        public final double worldX;
        public final double worldY;
        public final String state;
        public final String cargo;
        public final int cargoQuantity;
        public final long cash;
        public final int completedTrips;
        public final Long currentSettlementId;
        public final Long destinationSettlementId;
        public final String leaderName;
        public final int guardCount;

        CaravanView(long id, double worldX, double worldY, String state,
                String cargo, int cargoQuantity, long cash, int completedTrips,
                Long currentSettlementId, Long destinationSettlementId,
                String leaderName, int guardCount) {
            this.id = id;
            this.worldX = worldX;
            this.worldY = worldY;
            this.state = state;
            this.cargo = cargo;
            this.cargoQuantity = cargoQuantity;
            this.cash = cash;
            this.completedTrips = completedTrips;
            this.currentSettlementId = currentSettlementId;
            this.destinationSettlementId = destinationSettlementId;
            this.leaderName = leaderName;
            this.guardCount = guardCount;
        }
    }

    public static final class ArmyView {
        public final long id;
        public final long realmId;
        public final double worldX;
        public final double worldY;
        public final int strength;
        public final double morale;
        public final double fatigue;
        public final int grain;
        public final String order;
        public final String state;
        /** Where the army stands right now, and where it is marching, for the order UI. */
        public final Long currentSettlementId;
        public final Long destinationSettlementId;
        public final int daysWithoutFood;
        /** True when the adventurer personally commands this army. */
        public final boolean playerCommanded;
        public final String commanderName;
        /** At war with the player's realm or commanded army's realm. */
        public final boolean hostileToPlayer;

        ArmyView(long id, long realmId, double worldX, double worldY, int strength,
                double morale, double fatigue, int grain, String order, String state,
                Long currentSettlementId, Long destinationSettlementId,
                int daysWithoutFood, boolean playerCommanded,
                String commanderName, boolean hostileToPlayer) {
            this.id = id;
            this.realmId = realmId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.strength = strength;
            this.morale = morale;
            this.fatigue = fatigue;
            this.grain = grain;
            this.order = order;
            this.state = state;
            this.currentSettlementId = currentSettlementId;
            this.destinationSettlementId = destinationSettlementId;
            this.daysWithoutFood = daysWithoutFood;
            this.playerCommanded = playerCommanded;
            this.commanderName = commanderName;
            this.hostileToPlayer = hostileToPlayer;
        }
    }

    public static final class EventView {
        /** World event id, so the UI can show a notification exactly once. */
        public final long eventId;
        public final long worldMinute;
        public final String type;
        /** Player-readable detail when the publishing system supplied one; may be null. */
        public final String detail;

        EventView(long eventId, long worldMinute, String type, String detail) {
            this.eventId = eventId;
            this.worldMinute = worldMinute;
            this.type = type;
            this.detail = detail;
        }
    }

    /** One war: who is fighting, why, how it is going, and whether the player's realm is in it. */
    public static final class WarView {
        public final long id;
        public final String goal;
        public final String state;
        public final List<String> attackerNames;
        public final List<String> defenderNames;
        public final double attackerScore;
        public final double defenderScore;
        public final double attackerExhaustion;
        public final double defenderExhaustion;
        public final String targetProvinceName;
        public final String targetTitleName;
        public final List<String> contributions;
        public final long startMinute;
        public final long daysRunning;
        public final String declarationReason;
        public final boolean involvesPlayerRealm;

        WarView(long id, String goal, String state, List<String> attackerNames, List<String> defenderNames,
                double attackerScore, double defenderScore, double attackerExhaustion,
                double defenderExhaustion, String targetProvinceName, String targetTitleName,
                List<String> contributions, long startMinute, long daysRunning, String declarationReason,
                boolean involvesPlayerRealm) {
            this.id = id;
            this.goal = goal;
            this.state = state;
            this.attackerNames = Collections.unmodifiableList(new ArrayList<>(attackerNames));
            this.defenderNames = Collections.unmodifiableList(new ArrayList<>(defenderNames));
            this.attackerScore = attackerScore;
            this.defenderScore = defenderScore;
            this.attackerExhaustion = attackerExhaustion;
            this.defenderExhaustion = defenderExhaustion;
            this.targetProvinceName = targetProvinceName;
            this.targetTitleName = targetTitleName;
            this.contributions = Collections.unmodifiableList(new ArrayList<>(contributions));
            this.startMinute = startMinute;
            this.daysRunning = daysRunning;
            this.declarationReason = declarationReason;
            this.involvesPlayerRealm = involvesPlayerRealm;
        }

        public boolean isActive() {
            return "ACTIVE".equals(state);
        }

        public String belligerents() {
            return String.join(", ", attackerNames) + " vs " + String.join(", ", defenderNames);
        }
    }

    /** One siege with the progress the simulation is actually tracking. */
    public static final class SiegeView {
        public final long id;
        public final long warId;
        public final long settlementId;
        public final String settlementName;
        public final String defenderRealmName;
        public final long besiegingArmyId;
        public final String besiegerRealmName;
        public final int besiegerStrength;
        public final String state;
        public final double breachProgress;
        public final double defenderResolve;
        public final int foodConsumed;
        public final int civilianDeaths;
        public final long daysRunning;
        public final String warGoal;
        public final boolean involvesPlayerRealm;
        public final List<Long> besiegingArmyIds;
        /** Armies committed to the siege, including any still marching to join. */
        public final int besiegerCount;
        public final boolean playerArmyJoined;
        public final boolean playerArmyAtSiege;
        /** The commanded army is on this siege's besieging side in an active war. */
        public final boolean playerArmyCanJoin;

        SiegeView(long id, long warId, long settlementId, String settlementName, String defenderRealmName,
                long besiegingArmyId, String besiegerRealmName, int besiegerStrength, String state,
                double breachProgress, double defenderResolve, int foodConsumed, int civilianDeaths,
                long daysRunning, String warGoal, boolean involvesPlayerRealm,
                List<Long> besiegingArmyIds, boolean playerArmyJoined, boolean playerArmyAtSiege,
                boolean playerArmyCanJoin) {
            this.id = id;
            this.warId = warId;
            this.settlementId = settlementId;
            this.settlementName = settlementName;
            this.defenderRealmName = defenderRealmName;
            this.besiegingArmyId = besiegingArmyId;
            this.besiegerRealmName = besiegerRealmName;
            this.besiegerStrength = besiegerStrength;
            this.state = state;
            this.breachProgress = breachProgress;
            this.defenderResolve = defenderResolve;
            this.foodConsumed = foodConsumed;
            this.civilianDeaths = civilianDeaths;
            this.daysRunning = daysRunning;
            this.warGoal = warGoal;
            this.involvesPlayerRealm = involvesPlayerRealm;
            this.besiegingArmyIds = Collections.unmodifiableList(new ArrayList<>(besiegingArmyIds));
            this.besiegerCount = this.besiegingArmyIds.size();
            this.playerArmyJoined = playerArmyJoined;
            this.playerArmyAtSiege = playerArmyAtSiege;
            this.playerArmyCanJoin = playerArmyCanJoin;
        }

        public boolean isActive() {
            return "ACTIVE".equals(state);
        }
    }

    /** A legal title: rank, holder, de jure province, and how many live claims contest it. */
    public static final class TitleView {
        public final long id;
        public final String name;
        public final String rank;
        public final long realmId;
        public final String realmName;
        public final Long holderPersonId;
        public final String holderName;
        public final String deJureProvinceName;
        public final int claimCount;
        public final boolean heldByPlayer;
        public final boolean isCrown;
        /** The settlement a conquered holding covers, and what it pays its holder per day. */
        public final String settlementName;
        public final long rentPerDay;

        TitleView(long id, String name, String rank, long realmId, String realmName, Long holderPersonId,
                String holderName, String deJureProvinceName, int claimCount, boolean heldByPlayer,
                boolean isCrown, String settlementName, long rentPerDay) {
            this.id = id;
            this.name = name;
            this.rank = rank;
            this.realmId = realmId;
            this.realmName = realmName;
            this.holderPersonId = holderPersonId;
            this.holderName = holderName;
            this.settlementName = settlementName;
            this.rentPerDay = rentPerDay;
            this.deJureProvinceName = deJureProvinceName;
            this.claimCount = claimCount;
            this.heldByPlayer = heldByPlayer;
            this.isCrown = isCrown;
        }
    }

    public WarView findWar(long id) {
        for (WarView war : wars) if (war.id == id) return war;
        return null;
    }

    public SiegeView findSiege(long id) {
        for (SiegeView siege : sieges) if (siege.id == id) return siege;
        return null;
    }

    public SiegeView findActiveSiegeAt(long settlementId) {
        for (SiegeView siege : sieges) {
            if (siege.settlementId == settlementId && siege.isActive()) return siege;
        }
        return null;
    }

    public TitleView findTitle(long id) {
        for (TitleView title : titles) if (title.id == id) return title;
        return null;
    }

    /** Titles the adventurer personally holds — their fiefs. */
    public List<TitleView> playerTitles() {
        List<TitleView> held = new ArrayList<>();
        for (TitleView title : titles) if (title.heldByPlayer) held.add(title);
        return held;
    }
}
