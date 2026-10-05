package world;

import world.economy.MoneyAccount;
import world.economy.Inventory;
import world.economy.Market;
import world.economy.Workplace;
import world.economy.GoodType;
import world.economy.ProductionRecipe;
import world.geography.*;

/**
 * Generates an initial world for testing and vertical slice.
 * Creates two realms, settlements, geography, and initial population.
 */
public final class WorldGenerator {
    private final WorldState world;
    private final SimulationContext context;

    public WorldGenerator(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    /**
     * Generate a simple vertical slice world:
     * - Two realms
     * - Three settlements per realm
     * - One disputed border province
     * - Roads connecting all settlements
     * - 200-500 persistent people
     */
    public void generateVerticalSlice() {
        System.out.println("Generating vertical slice world...");

        // 1. Create realms (kingdoms)
        long realmAId = world.idGenerator.next();
        long realmBId = world.idGenerator.next();
        Realm realmA = new Realm(realmAId, "Kingdom of Arden");
        realmA.treasury = new MoneyAccount(10000L);
        Realm realmB = new Realm(realmBId, "Kingdom of Balor");
        realmB.treasury = new MoneyAccount(10000L);
        world.realms.put(realmAId, realmA);
        world.realms.put(realmBId, realmB);
        System.out.println("Created realms: " + realmA.name + " (ID: " + realmAId + ") and " + realmB.name + " (ID: " + realmBId + ")");

        // 2. Create a disputed border province
        long disputedProvinceId = world.idGenerator.next();
        Province disputedProvince = new Province(disputedProvinceId, "Border Hills", "border_hills_region",
                Province.TerrainType.HILLS, Province.ClimateType.TEMPERATE);
        disputedProvince.controllerRealmId = realmAId; // Initially controlled by Realm A
        world.geography.addProvince(disputedProvince);
        System.out.println("Created disputed province: Border Hills (ID: " + disputedProvinceId + ") controlled by " + realmA.name);

        // 3. Create settlements: 3 per realm (total 6)
        // Realm A settlements
        long settlementA1 = createSettlement("Arden Vale", realmAId, 100, 100, disputedProvinceId);
        long settlementA2 = createSettlement("Eastwatch", realmAId, 200, 150, disputedProvinceId);
        long settlementA3 = createSettlement("Northhold", realmAId, 150, 200, disputedProvinceId);

        // Realm B settlements
        long settlementB1 = createSettlement("Balor's Rest", realmBId, 400, 100, disputedProvinceId);
        long settlementB2 = createSettlement("Southford", realmBId, 350, 200, disputedProvinceId);
        long settlementB3 = createSettlement("Westwatch", realmBId, 300, 300, disputedProvinceId);

        disputedProvince.settlementIds.addAll(java.util.Arrays.asList(
                settlementA1, settlementA2, settlementA3,
                settlementB1, settlementB2, settlementB3));
        realmA.capitalSettlementId = settlementA1;
        realmB.capitalSettlementId = settlementB1;
        realmA.controlledProvinceIds.add(disputedProvinceId);
        System.out.println("Created 6 settlements");

        // 3.1 Create initial workplaces (farms) for each settlement
        for (world.geography.Settlement settlement : world.geography.getSettlements().values()) {
            createFarmsForSettlement(settlement);
        }
        System.out.println("Created initial farms for settlements");

        // 4. Create roads connecting settlements in a basic network
        createRoadBetween(settlementA1, settlementA2);
        createRoadBetween(settlementA2, settlementA3);
        createRoadBetween(settlementA1, settlementA3);
        createRoadBetween(settlementB1, settlementB2);
        createRoadBetween(settlementB2, settlementB3);
        createRoadBetween(settlementB1, settlementB3);
        // Cross-realm roads
        createRoadBetween(settlementA2, settlementB1); // Connects Eastwatch to Balor's Rest
        createRoadBetween(settlementA3, settlementB2); // Connects Northhold to Southford
        System.out.println("Created road network");

        // 5. Generate initial population (200-500 people)
        int targetPopulation = 300; // Start with 300
        generateInitialPopulation(targetPopulation, realmAId, realmBId);
        System.out.println("Generated initial population of " + targetPopulation + " people");

        // 6. Set initial world time
        context.getClock().setWorldMinute(0L);

        // 7. Give households initial food and money, assign workers to farms
        initializeHouseholdResources();
        seedSettlementStockpiles();
        assignFarmsToHouseholds();
        assignWorkersToFarms();
        createInitialCaravans();

        System.out.println("Vertical slice generation complete");
    }

    private long createSettlement(String name, long controllingRealmId, int x, int y, long provinceId) {
        long settlementId = world.idGenerator.next();
        Settlement settlement = new Settlement(settlementId, name, provinceId, new WorldPosition(x, y), Settlement.SettlementType.VILLAGE);
        settlement.controllerRealmId = controllingRealmId;
        settlement.market = new Market(settlementId);
        settlement.publicStockpile = new Inventory();
        settlement.treasury = new MoneyAccount(500L + context.getRandom("GENERATION").nextInt(1000));
        world.geography.addSettlement(settlement);
        return settlementId;
    }

    private void createRoadBetween(long settlementAId, long settlementBId) {
        long roadId = world.idGenerator.next();
        Settlement settlementA = world.geography.getSettlement(settlementAId);
        Settlement settlementB = world.geography.getSettlement(settlementBId);
        if (settlementA == null || settlementB == null) return;

        double distance = Math.sqrt(
                Math.pow(settlementB.position.x - settlementA.position.x, 2) +
                Math.pow(settlementB.position.y - settlementA.position.y, 2));

        Road road = new Road(roadId, settlementAId, settlementBId, distance, 1.0, 0.5, 0.2);
        world.geography.getRouteGraph().addRoad(road);
    }

    private void createFarmsForSettlement(world.geography.Settlement settlement) {
        long farmId = world.idGenerator.next();
        Workplace farm = new Workplace(farmId, settlement.name + " Farm", settlement.id);
        farm.maxWorkers = 5; // A farm can have up to 5 workers

        // Define a simple grain production recipe
        java.util.EnumMap<GoodType, Integer> grainOutputs = new java.util.EnumMap<>(GoodType.class);
        grainOutputs.put(GoodType.GRAIN, 10); // Produces 10 units of grain per batch

        ProductionRecipe grainRecipe = new ProductionRecipe("Grain Production",
                new java.util.EnumMap<>(GoodType.class), // No inputs for grain initially
                grainOutputs, 2.0, 5, "farming"); // 2 labor, 5 batches/day, requires farming skill

        farm.recipes.add(grainRecipe);
        settlement.addWorkplace(farm);
        world.workplaces.put(farmId, farm);

        long gardenId = world.idGenerator.next();
        Workplace garden = new Workplace(gardenId, settlement.name + " Market Garden", settlement.id);
        garden.maxWorkers = 5;
        java.util.EnumMap<GoodType, Integer> vegetableOutputs = new java.util.EnumMap<>(GoodType.class);
        vegetableOutputs.put(GoodType.VEGETABLES, 8);
        garden.recipes.add(new ProductionRecipe("Vegetable Production",
                new java.util.EnumMap<>(GoodType.class), vegetableOutputs,
                2.0, 5, "farming"));
        settlement.addWorkplace(garden);
        world.workplaces.put(gardenId, garden);
        System.out.println("Created farm and garden in " + settlement.name);
    }

    /**
     * Assign each farm to a household in the same settlement as its owner.
     * This ensures farm output goes to the owning household.
     */
    private void assignFarmsToHouseholds() {
        for (world.geography.Settlement settlement : world.geography.getSettlements().values()) {
            for (Workplace workplace : settlement.workplaces) {
                if (workplace.ownerHouseholdId != null) continue; // Already owned

                // Find a household in this settlement
                for (Household h : world.households.values()) {
                    if (h.homeSettlementId == settlement.id && h.getMemberCount() > 0
                            && h.ownedWorkplaceId == null) {
                        // Find the head person of the household
                        Long headPersonId = h.headPersonId;
                        if (headPersonId != null) {
                            Person head = world.people.get(headPersonId);
                            if (head != null && head.alive) {
                                workplace.ownerHouseholdId = h.id;
                                h.ownedWorkplaceId = workplace.id;
                                head.employerId = workplace.id;
                                workplace.addWorker(headPersonId);
                                break;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Assigned farms to households");
    }

    private void generateInitialPopulation(int targetPopulation, long realmAId, long realmBId) {
        int peoplePerRealm = targetPopulation / 2;
        int realmAPeople = peoplePerRealm + (targetPopulation % 2); // Give remainder to realm A

        // Distribute people among settlements
        java.util.List<Long> realmASettlementIds = new java.util.ArrayList<>();
        java.util.List<Long> realmBSettlementIds = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, Settlement> entry : world.geography.getSettlements().entrySet()) {
            Settlement s = entry.getValue();
            if (s.controllerRealmId == realmAId) {
                realmASettlementIds.add(s.id);
            } else if (s.controllerRealmId == realmBId) {
                realmBSettlementIds.add(s.id);
            }
        }

        SeededRandom generationRandom = context.getRandom("GENERATION");

        // Generate people for realm A
        for (int i = 0; i < realmAPeople; i++) {
            long settlementId = realmASettlementIds.get(i % realmASettlementIds.size());
            long personId = world.idGenerator.next();
            int age = generationRandom.nextInt(1, 76);
            long birthMinute = -((long) age * WorldConfig.MINUTES_PER_YEAR)
                    - generationRandom.nextInt(WorldConfig.MINUTES_PER_YEAR);
            Person person = new Person(personId, "Person_" + personId, "A",
                    generationRandom.nextBoolean() ? Person.Sex.MALE : Person.Sex.FEMALE,
                    birthMinute);
            person.cultureId = 1L;
            person.homeSettlementId = settlementId;
            person.currentSettlementId = settlementId;
            person.type = age < 18 ? Person.PersonType.CHILD : Person.PersonType.CITIZEN;
            world.people.put(personId, person);
            assignToHousehold(person);
        }

        // Generate people for realm B. IDs always come directly from IdGenerator;
        // artificial offsets can collide after a long-running campaign.
        for (int i = 0; i < peoplePerRealm; i++) {
            long settlementId = realmBSettlementIds.get(i % realmBSettlementIds.size());
            long personId = world.idGenerator.next();
            int age = generationRandom.nextInt(1, 76);
            long birthMinute = -((long) age * WorldConfig.MINUTES_PER_YEAR)
                    - generationRandom.nextInt(WorldConfig.MINUTES_PER_YEAR);
            Person person = new Person(personId, "Person_" + personId, "B",
                    generationRandom.nextBoolean() ? Person.Sex.MALE : Person.Sex.FEMALE,
                    birthMinute);
            person.cultureId = 2L;
            person.homeSettlementId = settlementId;
            person.currentSettlementId = settlementId;
            person.type = age < 18 ? Person.PersonType.CHILD : Person.PersonType.CITIZEN;
            world.people.put(personId, person);
            assignToHousehold(person);
        }
    }

    private void assignToHousehold(Person person) {
        // Find or create a household for this person's settlement
        long settlementId = person.homeSettlementId;
        Household household = null;

        // Look for existing household in this settlement
        for (Household h : world.households.values()) {
            if (h.homeSettlementId == settlementId && h.getMemberCount() < 5) { // Max 5 per household for simplicity
                household = h;
                break;
            }
        }

        // If no household found, create a new one
        if (household == null) {
            long householdId = world.idGenerator.next();
            household = new Household(householdId, settlementId, person.id);
            world.households.put(householdId, household);
        }

        // Add person to household
        household.addMember(person.id);
        person.householdId = household.id;
        if (household.headPersonId == null) {
            household.headPersonId = person.id;
        }
    }

    /**
     * Give each household initial food (grain + vegetables) and some money.
     * This ensures the simulation starts with viable food stocks.
     */
    private void initializeHouseholdResources() {
        for (Household household : world.households.values()) {
            // Give initial food - enough for ~10 days per person
            int memberCount = household.getMemberCount();
            if (memberCount > 0) {
                household.inventory.add(GoodType.GRAIN, memberCount * 10);
                household.inventory.add(GoodType.VEGETABLES, memberCount * 5);
            }
            // Give some starting money
            household.account.add(50L + context.getRandom("GENERATION").nextInt(100));
            // Set initial food security
            household.foodSecurity = 1.0;
        }
        System.out.println("Initialized household food and money");
    }

    /**
     * Assign workers to farms in each settlement.
     * Prioritizes working-age adults who aren't already employed.
     */
    private void assignWorkersToFarms() {
        int assigned = 0;
        for (world.geography.Settlement settlement : world.geography.getSettlements().values()) {
            for (Workplace workplace : settlement.workplaces) {
                // Find available workers in this settlement
                for (Person person : world.people.values()) {
                    if (!person.alive) continue;
                    if (person.currentSettlementId == null || !person.currentSettlementId.equals(settlement.id)) continue;
                    if (person.employerId != null) continue; // Already employed
                    if (person.isChild(0L) || person.isElderly(0L)) continue; // Skip children and elderly

                    // Check if workplace has room
                    if (workplace.getWorkerCount() >= workplace.maxWorkers) break;

                    // Assign worker
                    workplace.addWorker(person.id);
                    person.employerId = workplace.id;
                    assigned++;
                }
            }
        }
        System.out.println("Assigned " + assigned + " workers to workplaces");
    }

    private void seedSettlementStockpiles() {
        java.util.List<Settlement> settlements = new java.util.ArrayList<>(
                world.geography.getSettlements().values());
        settlements.sort(java.util.Comparator.comparingLong(s -> s.id));
        for (int i = 0; i < settlements.size(); i++) {
            Settlement settlement = settlements.get(i);
            settlement.publicStockpile.add(GoodType.GRAIN, 40 + i * 25);
            settlement.publicStockpile.add(GoodType.VEGETABLES, 40 + (settlements.size() - i) * 18);
            settlement.publicStockpile.add(GoodType.TIMBER, 15 + i * 4);
        }
    }

    private void createInitialCaravans() {
        java.util.List<Settlement> settlements = new java.util.ArrayList<>(
                world.geography.getSettlements().values());
        settlements.sort(java.util.Comparator.comparingLong(s -> s.id));
        int created = 0;
        for (Settlement settlement : settlements) {
            Person leader = null;
            for (Person candidate : world.people.values()) {
                if (candidate.alive && candidate.currentSettlementId != null
                        && candidate.currentSettlementId == settlement.id
                        && !candidate.isChild(0L) && !candidate.isElderly(0L)
                        && candidate.householdId != null && candidate.employerId == null
                        && candidate.type != Person.PersonType.MERCHANT) {
                    leader = candidate;
                    break;
                }
            }
            if (leader == null) continue;
            Household owner = world.households.get(leader.householdId);
            if (owner == null) continue;
            long caravanId = world.idGenerator.next();
            Caravan caravan = new Caravan(caravanId, owner.id, leader.id, settlement.id,
                    new WorldPosition(settlement.position.x, settlement.position.y));
            long investment = Math.min(200L, owner.account.copperCoins);
            if (investment > 0 && owner.account.subtract(investment)) caravan.cash.add(investment);
            leader.type = Person.PersonType.MERCHANT;
            world.caravans.put(caravanId, caravan);
            created++;
            if (created >= 2) break;
        }
        System.out.println("Created " + created + " merchant caravans");
    }
}