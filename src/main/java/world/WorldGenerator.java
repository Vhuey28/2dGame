package world;

import world.economy.MoneyAccount;
import world.economy.Inventory;
import world.economy.Market;
import world.economy.Workplace;
import world.economy.GoodType;
import world.economy.ProductionRecipe;
import world.geography.*;
import world.politics.*;
import world.diplomacy.*;
import world.military.MilitarySystem;
import world.military.Regiment;

/**
 * Generates an initial world for testing and vertical slice.
 * Creates two realms, settlements, geography, and initial population.
 */
public final class WorldGenerator {
    private final WorldState world;
    private final SimulationContext context;
    /** Generation-time batching avoids a continent-wide household scan per person. */
    private final java.util.Map<Long, Household> openHouseholdBySettlement = new java.util.HashMap<>();

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

        // 2. Create distinct homelands and a disputed frontier. Separate provinces
        // provide meaningful war objectives and a readable strategic map.
        long ardenProvinceId = world.idGenerator.next();
        long borderProvinceId = world.idGenerator.next();
        long balorProvinceId = world.idGenerator.next();
        Province ardenProvince = new Province(ardenProvinceId, "Arden Heartland", "arden_heartland",
                Province.TerrainType.PLAINS, Province.ClimateType.TEMPERATE);
        Province borderProvince = new Province(borderProvinceId, "Border Hills", "border_hills_region",
                Province.TerrainType.HILLS, Province.ClimateType.TEMPERATE);
        Province balorProvince = new Province(balorProvinceId, "Balor March", "balor_march",
                Province.TerrainType.FORESTS, Province.ClimateType.TEMPERATE);
        ardenProvince.controllerRealmId = realmAId;
        borderProvince.controllerRealmId = realmAId;
        balorProvince.controllerRealmId = realmBId;
        ardenProvince.neighborProvinceIds.add(borderProvinceId);
        borderProvince.neighborProvinceIds.add(ardenProvinceId);
        borderProvince.neighborProvinceIds.add(balorProvinceId);
        balorProvince.neighborProvinceIds.add(borderProvinceId);
        world.geography.addProvince(ardenProvince);
        world.geography.addProvince(borderProvince);
        world.geography.addProvince(balorProvince);

        // 3. Create six widely separated settlements in two regional clusters.
        long settlementA1 = createSettlement("Arden Vale", realmAId, 100, 100, ardenProvinceId);
        long settlementA2 = createSettlement("Eastwatch", realmAId, 390, 270, borderProvinceId);
        long settlementA3 = createSettlement("Northhold", realmAId, 120, 520, ardenProvinceId);
        long settlementB1 = createSettlement("Balor's Rest", realmBId, 920, 100, balorProvinceId);
        long settlementB2 = createSettlement("Southford", realmBId, 650, 330, balorProvinceId);
        long settlementB3 = createSettlement("Westwatch", realmBId, 930, 540, balorProvinceId);

        ardenProvince.settlementIds.addAll(java.util.Arrays.asList(settlementA1, settlementA3));
        borderProvince.settlementIds.add(settlementA2);
        balorProvince.settlementIds.addAll(java.util.Arrays.asList(settlementB1, settlementB2, settlementB3));
        realmA.capitalSettlementId = settlementA1;
        realmB.capitalSettlementId = settlementB1;
        realmA.controlledProvinceIds.add(ardenProvinceId);
        realmA.controlledProvinceIds.add(borderProvinceId);
        realmB.controlledProvinceIds.add(balorProvinceId);
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
        createPoliticalCore();
        createInitialArmies();
        createDiplomaticCore();

        System.out.println("Vertical slice generation complete");
    }

    /**
     * Generate an opt-in synthetic continent for profiling. It deliberately
     * avoids the bespoke vertical-slice story setup and leaves that default unchanged.
     */
    public void generateScale(WorldGenerationScale scale) {
        if (!world.realms.isEmpty() || !world.geography.getSettlements().isEmpty()) {
            throw new IllegalStateException("Scale generation requires an empty world");
        }
        java.util.List<Long> realmIds = new java.util.ArrayList<>();
        java.util.List<Long> provinceIds = new java.util.ArrayList<>();
        java.util.List<Long> settlementIds = new java.util.ArrayList<>();
        for (int i = 0; i < scale.realms; i++) {
            long realmId = world.idGenerator.next();
            Realm realm = new Realm(realmId, "Realm " + (i + 1));
            realm.treasury = new MoneyAccount(10_000L);
            world.realms.put(realmId, realm);
            realmIds.add(realmId);

            long provinceId = world.idGenerator.next();
            Province province = new Province(provinceId, "Province " + (i + 1), "scale_" + i,
                    Province.TerrainType.PLAINS, Province.ClimateType.TEMPERATE);
            province.controllerRealmId = realmId;
            world.geography.addProvince(province);
            realm.controlledProvinceIds.add(provinceId);
            provinceIds.add(provinceId);
        }
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(scale.settlements)));
        for (int i = 0; i < scale.settlements; i++) {
            int owner = i % scale.realms;
            long settlementId = createSettlement("Settlement " + (i + 1), realmIds.get(owner),
                    80 + (i % columns) * 70, 80 + (i / columns) * 70, provinceIds.get(owner));
            settlementIds.add(settlementId);
            world.geography.getProvince(provinceIds.get(owner)).settlementIds.add(settlementId);
            Realm realm = world.realms.get(realmIds.get(owner));
            if (realm.capitalSettlementId == null) realm.capitalSettlementId = settlementId;
            createFarmsForSettlement(world.geography.getSettlement(settlementId));
            if (i > 0) createRoadBetween(settlementIds.get(i - 1), settlementId);
            if (i >= columns) createRoadBetween(settlementIds.get(i - columns), settlementId);
        }
        for (int i = 0; i < scale.people; i++) {
            long personId = world.idGenerator.next();
            int age = context.getRandom("GENERATION").nextInt(1, 76);
            Person person = new Person(personId, "Citizen" + personId, "Scale", i % 2 == 0
                    ? Person.Sex.FEMALE : Person.Sex.MALE, -((long) age * WorldConfig.MINUTES_PER_YEAR));
            person.homeSettlementId = settlementIds.get(i % settlementIds.size());
            person.currentSettlementId = person.homeSettlementId;
            person.type = age < 18 ? Person.PersonType.CHILD : Person.PersonType.CITIZEN;
            world.people.put(personId, person);
            assignToHousehold(person);
        }
        initializeHouseholdResources();
        world.indexes.rebuild(world, context.getClock().getWorldMinute());
    }

    private long createSettlement(String name, long controllingRealmId, int x, int y, long provinceId) {
        long settlementId = world.idGenerator.next();
        // Cast both coordinates so Java selects the fixed-coordinate constructor,
        // not the (routeEdgeId, routeProgress) overload. The old overload choice
        // put every generated settlement at 0,0 on the campaign map.
        Settlement settlement = new Settlement(settlementId, name, provinceId,
                new WorldPosition((double) x, (double) y), Settlement.SettlementType.VILLAGE);
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
        long settlementId = person.homeSettlementId;
        Household household = openHouseholdBySettlement.get(settlementId);
        if (household == null || household.getMemberCount() >= 5) {
            long householdId = world.idGenerator.next();
            household = new Household(householdId, settlementId, person.id);
            world.households.put(householdId, household);
            openHouseholdBySettlement.put(settlementId, household);
        }
        household.addMember(person.id);
        person.householdId = household.id;
        if (household.headPersonId == null) household.headPersonId = person.id;
        if (household.getMemberCount() >= 5) openHouseholdBySettlement.remove(settlementId);
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
            settlement.publicStockpile.add(GoodType.WEAPONS, 16);
            settlement.publicStockpile.add(GoodType.ARMOR, 8);
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

    private void createPoliticalCore() {
        java.util.List<Realm> realms = new java.util.ArrayList<>(world.realms.values());
        realms.sort(java.util.Comparator.comparingLong(realm -> realm.id));
        java.util.List<Title> createdTitles = new java.util.ArrayList<>();

        for (int realmIndex = 0; realmIndex < realms.size(); realmIndex++) {
            Realm realm = realms.get(realmIndex);
            Government.SuccessionLaw succession = realmIndex == 0
                    ? Government.SuccessionLaw.HEREDITARY
                    : Government.SuccessionLaw.ELECTIVE;
            Government.GovernmentType governmentType = realmIndex == 0
                    ? Government.GovernmentType.FEUDAL_MONARCHY
                    : Government.GovernmentType.ELECTIVE_MONARCHY;
            Government government = new Government(world.idGenerator.next(), realm.id,
                    governmentType, succession);
            government.setLawLevel(Government.LawType.TAXATION, realmIndex == 0 ? 2 : 1);
            government.setLawLevel(Government.LawType.CONSCRIPTION, 1);
            government.setLawLevel(Government.LawType.TARIFFS, realmIndex == 0 ? 1 : 2);
            world.governments.put(government.id, government);
            realm.governmentId = government.id;

            java.util.List<Person> candidates = realmResidents(realm.id);
            if (candidates.isEmpty()) continue;
            Person ruler = candidates.get(0);
            ruler.type = Person.PersonType.NOBLE;
            ruler.givenName = realmIndex == 0 ? "Alaric" : "Mira";
            ruler.familyName = realmIndex == 0 ? "Arden" : "Balor";
            ruler.skills.stewardship = 55 + realmIndex * 5;
            ruler.skills.diplomacy = 45 + realmIndex * 10;
            ruler.skills.leadership = 60;
            ruler.personality.ambition = 65.0;

            Title title = new Title(world.idGenerator.next(),
                    realmIndex == 0 ? "Crown of Arden" : "Crown of Balor",
                    Title.Rank.KINGDOM, realm.id);
            title.installHolder(ruler.id, 0L, "FOUNDING_RULER");
            world.titles.put(title.id, title);
            createdTitles.add(title);
            realm.rulerTitleId = title.id;
            realm.rulerPersonId = ruler.id;

            Office rulerOffice = new Office(world.idGenerator.next(), realm.id,
                    Office.OfficeType.RULER, ruler.id, 0L);
            world.offices.put(rulerOffice.id, rulerOffice);
            ruler.officeId = rulerOffice.id;

            Office.OfficeType[] councilTypes = {Office.OfficeType.STEWARD,
                    Office.OfficeType.MARSHAL, Office.OfficeType.CHANCELLOR,
                    Office.OfficeType.SPYMASTER};
            for (int i = 0; i < councilTypes.length; i++) {
                Person holder = candidates.get(Math.min(i + 1, candidates.size() - 1));
                holder.type = Person.PersonType.NOBLE;
                holder.skills.stewardship = 25 + context.getRandom("POLITICS").nextInt(51);
                holder.skills.martial = 25 + context.getRandom("POLITICS").nextInt(51);
                holder.skills.diplomacy = 25 + context.getRandom("POLITICS").nextInt(51);
                holder.skills.intrigue = 25 + context.getRandom("POLITICS").nextInt(51);
                Office office = new Office(world.idGenerator.next(), realm.id,
                        councilTypes[i], holder.id, 0L);
                world.offices.put(office.id, office);
                holder.officeId = office.id;
            }

            PoliticalFaction taxFaction = new PoliticalFaction(world.idGenerator.next(), realm.id,
                    PoliticalFaction.FactionGoal.LOWER_TAXES);
            PoliticalFaction powerFaction = new PoliticalFaction(world.idGenerator.next(), realm.id,
                    realmIndex == 0 ? PoliticalFaction.FactionGoal.NOBLE_PRIVILEGES
                            : PoliticalFaction.FactionGoal.MERCHANT_PRIVILEGES);
            for (int i = 1; i < Math.min(10, candidates.size()); i++) {
                PoliticalFaction faction = i % 2 == 0 ? taxFaction : powerFaction;
                faction.memberPersonIds.add(candidates.get(i).id);
                candidates.get(i).personality.ambition = 40.0
                        + context.getRandom("POLITICS").nextInt(51);
            }
            taxFaction.leaderPersonId = taxFaction.memberPersonIds.stream().findFirst().orElse(ruler.id);
            powerFaction.leaderPersonId = powerFaction.memberPersonIds.stream().findFirst().orElse(ruler.id);
            world.politicalFactions.put(taxFaction.id, taxFaction);
            world.politicalFactions.put(powerFaction.id, powerFaction);
        }

        if (!createdTitles.isEmpty()) {
            for (Province province : world.geography.getProvinces().values()) {
                if (province.legalTitleId != null || province.controllerRealmId == null) continue;
                createdTitles.stream().filter(title -> title.realmId == province.controllerRealmId)
                        .findFirst().ifPresent(title -> {
                            province.legalTitleId = title.id;
                            if (title.deJureProvinceId == null) title.deJureProvinceId = province.id;
                        });
            }
        }
        if (createdTitles.size() >= 2) {
            for (int i = 0; i < createdTitles.size(); i++) {
                Title target = createdTitles.get((i + 1) % createdTitles.size());
                Realm claimantRealm = realms.get(i);
                if (claimantRealm.rulerPersonId == null) continue;
                Claim claim = new Claim(world.idGenerator.next(), claimantRealm.rulerPersonId,
                        target.id, 20.0, Claim.ClaimSource.GRANT, 0L);
                world.claims.put(claim.id, claim);
                target.claimIds.add(claim.id);
            }
        }
        System.out.println("Created governments, crowns, councils, claims, and factions");
    }

    private void createInitialArmies() {
        MilitarySystem military = new MilitarySystem(context);
        java.util.List<Realm> realms = new java.util.ArrayList<>(world.realms.values());
        realms.sort(java.util.Comparator.comparingLong(realm -> realm.id));
        int created = 0;
        for (Realm realm : realms) {
            if (realm.capitalSettlementId == null) continue;
            world.command.CommandResult result = military.recruitRegiment(realm.id,
                    realm.capitalSettlementId, 12, Regiment.RegimentType.LEVY_INFANTRY, 0L);
            if (result.accepted) created++;
        }
        System.out.println("Created " + created + " person-level armies");
    }

    private void createDiplomaticCore() {
        java.util.List<Realm> realms = new java.util.ArrayList<>(world.realms.values());
        realms.sort(java.util.Comparator.comparingLong(realm -> realm.id));
        if (realms.size() < 2) return;
        Realm first = realms.get(0);
        Realm second = realms.get(1);

        DiplomaticState state = new DiplomaticState(world.idGenerator.next(), first.id, second.id);
        state.opinion = 20;
        state.trust = 25;
        state.fear = 20;
        state.rivalry = 15;
        state.borderTension = 35;
        state.tradeDependence = 40;
        state.lastAiExplanation = "Border rivals cooperate because trade limits the cost of tension";
        world.diplomaticStates.put(state.id, state);

        Treaty trade = new Treaty(world.idGenerator.next(), Treaty.TreatyType.TRADE_AGREEMENT,
                first.id, second.id, 0L, 12L * WorldConfig.MINUTES_PER_MONTH);
        world.treaties.put(trade.id, trade);
        state.treatyIds.add(trade.id);

        Grievance border = new Grievance(world.idGenerator.next(), second.id, first.id,
                Grievance.GrievanceType.BORDER_DISPUTE, 30, 0L,
                24L * WorldConfig.MINUTES_PER_MONTH);
        world.grievances.put(border.id, border);
        state.grievanceIds.add(border.id);

        SpyNetwork network = new SpyNetwork(world.idGenerator.next(), first.id, second.id, 20.0, 0L);
        world.spyNetworks.put(network.id, network);

        Person schemer = world.offices.values().stream()
                .filter(office -> office.realmId == second.id && office.type == Office.OfficeType.SPYMASTER)
                .map(office -> office.holderPersonId == null ? null : world.people.get(office.holderPersonId))
                .filter(java.util.Objects::nonNull).findFirst()
                .orElse(second.rulerPersonId == null ? null : world.people.get(second.rulerPersonId));
        if (schemer != null) {
            Scheme scheme = new Scheme(world.idGenerator.next(), Scheme.SchemeType.BUILD_SPY_NETWORK,
                    second.id, schemer.id, first.id, first.rulerPersonId, 150L, 0L);
            world.schemes.put(scheme.id, scheme);
        }
        System.out.println("Created diplomatic relations, treaty, grievance, and intelligence operations");
    }

    private java.util.List<Person> realmResidents(long realmId) {
        java.util.List<Person> residents = new java.util.ArrayList<>();
        for (Person person : world.people.values()) {
            if (!person.alive || person.currentSettlementId == null
                    || person.isChild(0L) || person.isElderly(0L)) continue;
            Settlement settlement = world.geography.getSettlement(person.currentSettlementId);
            if (settlement != null && settlement.controllerRealmId != null
                    && settlement.controllerRealmId == realmId) residents.add(person);
        }
        residents.sort(java.util.Comparator.comparingLong(person -> person.id));
        return residents;
    }
}