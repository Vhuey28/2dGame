package world;

import world.economy.GoodType;

/**
 * Standalone headless runner for validating the world simulation.
 * Creates a small world, advances it, and reports final state.
 */
public class HeadlessRunner {
    public static void main(String[] args) {
        System.out.println("Chronicle Conquest — Headless World Simulation Runner");

        long seed = WorldConfig.DEFAULT_SEED;
        int years = 1;
        boolean targetScale = args.length >= 3 && "target".equalsIgnoreCase(args[2]);

        if (args.length >= 1) {
            try {
                seed = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid seed: " + args[0]);
            }
        }
        if (args.length >= 2) {
            try {
                years = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid years: " + args[1]);
            }
        }

        System.out.println("Seed: " + seed + ", Years: " + years
                + ", Scale: " + (targetScale ? "target continent" : "vertical slice"));

        // Setup
        WorldClock clock = new WorldClock(0L);
        WorldState state = new WorldState();
        WorldSimulation sim = new WorldSimulation(clock, state);
        SimulationContext context = sim.getContext();

        // Initialize seeded random streams for this seed
        context.randomStreams.put("DEMOGRAPHICS", new SeededRandom(seed + 1));
        context.randomStreams.put("ECONOMY", new SeededRandom(seed + 2));
        context.randomStreams.put("POLITICS", new SeededRandom(seed + 3));
        context.randomStreams.put("MILITARY", new SeededRandom(seed + 4));
        context.randomStreams.put("GENERATION", new SeededRandom(seed + 5));
        context.randomStreams.put("DIPLOMACY", new SeededRandom(seed + 6));

        // Generate world
        WorldGenerator generator = new WorldGenerator(context);
        if (targetScale) generator.generateScale(WorldGenerationScale.TARGET_CONTINENT);
        else generator.generateVerticalSlice();
        sim.initializeGeneratedWorld();

        System.out.println("World after generation:");
        System.out.println("  People: " + state.people.size());
        System.out.println("  Households: " + state.households.size());
        System.out.println("  Settlements: " + state.geography.getSettlementCount());
        System.out.println("  Provinces: " + state.geography.getProvinceCount());
        System.out.println("  Realms: " + state.realms.size());
        System.out.println("  Roads: " + state.geography.getRouteGraph().getAllRoads().size());
        System.out.println("  Next ID: " + state.idGenerator.getNextId());

        // Simulate the given number of years
        long totalMinutes = (long) years * WorldConfig.MINUTES_PER_YEAR;

        System.out.println("Simulating " + totalMinutes + " minutes...");
        for (long m = 0; m < totalMinutes; m += WorldConfig.STEP_MINUTES) {
            sim.advanceMinutes(WorldConfig.STEP_MINUTES);
        }

        long endMinute = sim.getClock().getWorldMinute();
        System.out.println("Simulation complete. End minute: " + endMinute +
            " (" + WorldCalendar.getYear(endMinute) + "Y " + WorldCalendar.getMonth(endMinute) + "M " + WorldCalendar.getDay(endMinute) + "D)");

        System.out.println("Events in history: " + context.eventHistory.getHistorical().size());

        System.out.println("Final world state:");
        System.out.println("  People: " + state.people.size());
        System.out.println("  Households: " + state.households.size());
        System.out.println("  Settlements: " + state.geography.getSettlementCount());
        System.out.println("  Realms: " + state.realms.size());
        System.out.println("  Workplaces: " + state.workplaces.size());
        System.out.println("  Governments: " + state.governments.size());
        System.out.println("  Titles: " + state.titles.size());
        System.out.println("  Political factions: " + state.politicalFactions.size());
        System.out.println("  Diplomatic relations: " + state.diplomaticStates.size());
        System.out.println("  Treaties: " + state.treaties.size());
        System.out.println("  Schemes: " + state.schemes.size());
        System.out.println("  Regiments: " + state.regiments.size());
        System.out.println("  Battles resolved: " + state.battleReports.size()
                + " (contexts=" + state.battleContexts.size()
                + ", reconciled=" + state.reconciledBattleIds.size() + ")");
        System.out.println("  Wars: " + state.wars.size() + ", sieges: " + state.sieges.size()
                + ", peace offers: " + state.peaceOffers.size());
        world.military.MilitarySystem military = sim.getMilitarySystem();
        for (Army army : state.armies.values()) {
            System.out.println("  Army " + army.id + ": " + military.strength(army)
                    + " soldiers, morale=" + Math.round(army.morale)
                    + ", food=" + army.supplies.getQuantity(GoodType.GRAIN));
        }
        for (Realm realm : state.realms.values()) {
            Person ruler = realm.rulerPersonId == null ? null : state.people.get(realm.rulerPersonId);
            System.out.println("  " + realm.name + " ruler: "
                    + (ruler == null ? "VACANT" : ruler.givenName + " " + ruler.familyName)
                    + ", legitimacy=" + String.format("%.1f", realm.legitimacy));
        }

        // Report on food economy
        System.out.println("Food economy status:");
        int totalGrain = 0;
        int totalVeg = 0;
        double avgFoodSecurity = 0.0;
        int householdCount = 0;
        for (Household h : state.households.values()) {
            if (h.getMemberCount() > 0) {
                totalGrain += h.inventory.getQuantity(GoodType.GRAIN);
                totalVeg += h.inventory.getQuantity(GoodType.VEGETABLES);
                avgFoodSecurity += h.foodSecurity;
                householdCount++;
            }
        }
        if (householdCount > 0) {
            avgFoodSecurity /= householdCount;
        }
        System.out.println("  Total grain in households: " + totalGrain);
        System.out.println("  Total vegetables in households: " + totalVeg);
        System.out.println("  Average food security: " + String.format("%.2f", avgFoodSecurity));

        // Validation: every person has valid household and location
        int invalidPeople = 0;
        for (Person p : state.people.values()) {
            if (p.householdId == null && p.alive) {
                invalidPeople++;
            }
        }
        System.out.println("  People without household: " + invalidPeople);
        WorldInvariantValidator.Report invariants = new WorldInvariantValidator().validate(state);
        SimulationProfile profile = sim.getProfile();
        System.out.println("Scale diagnostics:");
        System.out.println("  Invariants: " + invariants);
        System.out.println("  Average tick ms: " + String.format("%.3f", profile.getAverageTickMillis()));
        System.out.println("  Slowest tick ms: " + String.format("%.3f", profile.getSlowestTickMillis()));
        System.out.println("  Heap used MiB: " + String.format("%.1f", profile.getHeapUsedBytes() / 1048576.0));
        System.out.println("  Route cache hits/misses: "
                + state.geography.getRouteGraph().getCacheHits() + "/"
                + state.geography.getRouteGraph().getCacheMisses());

        System.out.println("Headless run complete.");
    }
}