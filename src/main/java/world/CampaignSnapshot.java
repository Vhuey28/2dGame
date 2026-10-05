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
    public final PlayerView player;
    public final List<EventView> recentEvents;

    private CampaignSnapshot(long worldMinute, int speed, boolean paused,
            int livingPopulation, int householdCount, int armyCount,
            int caravanCount, int warCount, int activeTreatyCount, int activeSiegeCount,
            double averageFoodSecurity,
            List<RealmView> realms, List<SettlementView> settlements,
            List<RoadView> roads, List<CaravanView> caravans, List<ArmyView> armies,
            PlayerView player, List<EventView> recentEvents) {
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
        this.player = player;
        this.recentEvents = Collections.unmodifiableList(recentEvents);
    }

    public static CampaignSnapshot capture(WorldSimulation simulation) {
        WorldState world = simulation.getWorld();
        long minute = simulation.getClock().getWorldMinute();

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
                    factionCount, strongestFaction, strongestSupport, treatyCount, diplomacySummary));
        }
        realmViews.sort(Comparator.comparing(view -> view.name));

        List<SettlementView> settlementViews = new ArrayList<>();
        for (Settlement settlement : world.geography.getSettlements().values()) {
            int population = countPopulation(world, settlement.id);
            int households = 0;
            double settlementFood = 0.0;
            int grain = settlement.publicStockpile.getQuantity(GoodType.GRAIN);
            int vegetables = settlement.publicStockpile.getQuantity(GoodType.VEGETABLES);
            for (Household household : world.households.values()) {
                if (household.homeSettlementId == settlement.id && household.getMemberCount() > 0) {
                    households++;
                    settlementFood += household.foodSecurity;
                    grain += household.inventory.getQuantity(GoodType.GRAIN);
                    vegetables += household.inventory.getQuantity(GoodType.VEGETABLES);
                }
            }
            double food = households == 0 ? 0.0 : settlementFood / households;
            settlementViews.add(new SettlementView(settlement.id, settlement.name,
                    settlement.controllerRealmId, settlement.occupyingRealmId,
                    settlement.position.x, settlement.position.y,
                    population, households, settlement.treasury.copperCoins, grain,
                    vegetables, food, settlement.security, settlement.unrest));
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
            caravanViews.add(new CaravanView(caravan.id, caravan.position.x, caravan.position.y,
                    caravan.state.toString(), cargoType == null ? "Empty" : cargoType.toString(),
                    caravan.cargoQuantity(), caravan.cash.copperCoins, caravan.completedTrips));
        }
        caravanViews.sort(Comparator.comparingLong(view -> view.id));

        List<ArmyView> armyViews = new ArrayList<>();
        for (Army army : world.armies.values()) {
            if (army.position == null || army.state == Army.ArmyState.DISBANDED) continue;
            int strength = 0;
            for (Long regimentId : army.regimentIds) {
                world.military.Regiment regiment = world.regiments.get(regimentId);
                if (regiment != null) strength += regiment.livingStrength(world);
            }
            armyViews.add(new ArmyView(army.id, army.realmId, army.position.x, army.position.y,
                    strength, army.morale, army.fatigue,
                    army.supplies.getQuantity(GoodType.GRAIN), army.order.toString(), army.state.toString()));
        }
        armyViews.sort(Comparator.comparingLong(view -> view.id));

        PlayerView playerView = null;
        if (world.player != null) {
            Household playerHousehold = world.households.get(world.player.householdId);
            long coins = playerHousehold == null ? 0L : playerHousehold.account.copperCoins;
            playerView = new PlayerView(world.player.currentSettlementId, coins,
                    world.player.cargo.getQuantity(GoodType.GRAIN),
                    world.player.cargo.getQuantity(GoodType.VEGETABLES),
                    world.player.cargo.totalQuantity(), world.player.cargoCapacity,
                    world.player.reputation);
        }

        List<EventView> eventViews = new ArrayList<>();
        List<WorldEvent> events = simulation.getContext().eventHistory.getRecent();
        int first = Math.max(0, events.size() - 6);
        for (int i = first; i < events.size(); i++) {
            WorldEvent event = events.get(i);
            eventViews.add(new EventView(event.worldMinute, event.type));
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
        return new CampaignSnapshot(minute, simulation.getClock().getSpeed(),
                simulation.getClock().isPaused(), living, world.households.size(),
                armyViews.size(), world.caravans.size(), activeWars, activeTreaties, activeSieges, averageFood,
                realmViews, settlementViews, roadViews, caravanViews, armyViews, playerView, eventViews);
    }

    private static int countPopulation(WorldState world, long settlementId) {
        int count = 0;
        for (Person person : world.people.values()) {
            if (person.alive && person.currentSettlementId != null
                    && person.currentSettlementId == settlementId) {
                count++;
            }
        }
        return count;
    }

    public SettlementView findSettlement(long id) {
        for (SettlementView settlement : settlements) {
            if (settlement.id == id) return settlement;
        }
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

        RealmView(long id, String name, long treasury, int population,
                int settlementCount, double stability, double legitimacy,
                double warExhaustion, String rulerName, String governmentType,
                String successionLaw, int factionCount, String strongestFaction,
                double strongestFactionSupport, int treatyCount, String diplomacySummary) {
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
        public final double foodSecurity;
        public final double security;
        public final double unrest;

        SettlementView(long id, String name, Long realmId, Long occupyingRealmId,
                double worldX, double worldY, int population, int households, long treasury,
                int grain, int vegetables, double foodSecurity,
                double security, double unrest) {
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
            this.foodSecurity = foodSecurity;
            this.security = security;
            this.unrest = unrest;
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

        PlayerView(long settlementId, long coins, int grain, int vegetables,
                int cargoUsed, int cargoCapacity, int reputation) {
            this.settlementId = settlementId;
            this.coins = coins;
            this.grain = grain;
            this.vegetables = vegetables;
            this.cargoUsed = cargoUsed;
            this.cargoCapacity = cargoCapacity;
            this.reputation = reputation;
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

        CaravanView(long id, double worldX, double worldY, String state,
                String cargo, int cargoQuantity, long cash, int completedTrips) {
            this.id = id;
            this.worldX = worldX;
            this.worldY = worldY;
            this.state = state;
            this.cargo = cargo;
            this.cargoQuantity = cargoQuantity;
            this.cash = cash;
            this.completedTrips = completedTrips;
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

        ArmyView(long id, long realmId, double worldX, double worldY, int strength,
                double morale, double fatigue, int grain, String order, String state) {
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
        }
    }

    public static final class EventView {
        public final long worldMinute;
        public final String type;

        EventView(long worldMinute, String type) {
            this.worldMinute = worldMinute;
            this.type = type;
        }
    }
}
