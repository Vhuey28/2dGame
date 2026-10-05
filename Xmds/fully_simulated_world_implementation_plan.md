# Fully Simulated World — Detailed Java Implementation Plan

> Project: Chronicle Conquest / `2dGame`
> Target runtime: Java 17, Swing-based client
> Target experience: a persistent continent in which the player begins as an independent adventurer while people, households, settlements, markets, kingdoms, politics, armies, and wars continue to develop.

This document is an implementation specification, not an implementation. It describes how to evolve the systems currently in this repository without placing the entire simulation inside `GamePanel` or attempting to update every inhabitant every rendered frame.

## Implementation progress

The repository now contains the first foundation and food-economy systems described by this plan. The campaign test integration adds:

- A **Campaign** option on the start menu.
- A generated two-realm, six-settlement vertical-slice world.
- A continuously advancing headless `WorldSimulation` while Campaign mode is active.
- An immutable `CampaignSnapshot` boundary so Swing does not render mutable simulation collections.
- A campaign world map showing settlements, roads, realm ownership, population, treasuries, food stocks, food security, and events.
- Campaign time controls and a one-day debug advance.
- Deterministic generation fixes for IDs, ages, settlement treasuries, and household wealth.
- Correct processing of every crossed day/month during large time advances.
- Initial campaign integration tests.
- Phase 3 employment and trade: weekly job matching, wages, producer-to-market food sales, household purchasing, route finding, physical merchant caravans, cargo accounting, arrival sales, and bandit losses.
- Visible caravan markers and trade status on the Campaign map.
- Real migration toward better-supplied settlements instead of deleting migrants from the social model.
- Phase 4 player foundation: a persistent adventurer person/household, personal funds, carried cargo, route-based strategic travel, and settlement grain buying/selling from the Campaign map.

Contracts, local-map settlement variants, political core, military, war, diplomacy, tactical reconciliation, and complete save/load remain future work.

---

## Table of contents

1. [Target design](#1-target-design)
2. [Current codebase assessment](#2-current-codebase-assessment)
3. [Non-negotiable architecture rules](#3-non-negotiable-architecture-rules)
4. [Recommended source layout](#4-recommended-source-layout)
5. [World identity and references](#5-world-identity-and-references)
6. [World state and simulation clock](#6-world-state-and-simulation-clock)
7. [Commands, events, and system boundaries](#7-commands-events-and-system-boundaries)
8. [Geography and world-map model](#8-geography-and-world-map-model)
9. [People, households, and dynasties](#9-people-households-and-dynasties)
10. [Needs, employment, and individual decisions](#10-needs-employment-and-individual-decisions)
11. [Settlements, buildings, and ownership](#11-settlements-buildings-and-ownership)
12. [Goods, production, markets, and money](#12-goods-production-markets-and-money)
13. [Trade routes and physical caravans](#13-trade-routes-and-physical-caravans)
14. [Kingdoms, titles, laws, and government](#14-kingdoms-titles-laws-and-government)
15. [Political factions, diplomacy, and intrigue](#15-political-factions-diplomacy-and-intrigue)
16. [Armies, recruitment, logistics, and wars](#16-armies-recruitment-logistics-and-wars)
17. [Strategic-to-tactical battle integration](#17-strategic-to-tactical-battle-integration)
18. [Independent adventurer progression](#18-independent-adventurer-progression)
19. [AI planning](#19-ai-planning)
20. [Simulation fidelity and performance](#20-simulation-fidelity-and-performance)
21. [Save/load and migration](#21-saveload-and-migration)
22. [UI and presentation integration](#22-ui-and-presentation-integration)
23. [Testing, diagnostics, and balancing](#23-testing-diagnostics-and-balancing)
24. [Implementation phases](#24-implementation-phases)
25. [Detailed vertical-slice specification](#25-detailed-vertical-slice-specification)
26. [Definition of done](#26-definition-of-done)
27. [Common failure modes to avoid](#27-common-failure-modes-to-avoid)

---

# 1. Target design

The intended final world has the following properties:

- Dozens of kingdoms and hundreds of settlements can exist on one continent.
- Every person has a persistent identity and can have parents, children, a household, a job, possessions, loyalties, skills, relationships, and a history.
- The player begins without owning a kingdom and can travel, trade, fight, take contracts, build relationships, join organizations, gain land, and eventually become a ruler.
- Armies, warbands, merchants, and caravans exist as visible parties on the strategic map.
- Battles involving the player can enter the existing deployment and tactical combat layers.
- Remote AI battles use auto-resolution but consume the same strategic resources and produce the same result structure as tactical battles.
- Economics, population, politics, and war form causal loops rather than unrelated minigames.
- The world advances continuously and can be completely saved and restored.

## 1.1 Meaning of “every person is simulated”

Every person must remain a persistent world record. This does **not** mean every person performs pathfinding, searches all jobs, evaluates all relationships, and updates all needs 60 times per second.

Use three levels of simulation fidelity:

1. **Local physical simulation** — loaded people have coordinates, sprites, collision, schedules, combat, and immediate interactions.
2. **Regional individual simulation** — nearby but unloaded people remain individual and receive hourly/daily decisions without physical tile movement.
3. **Remote batched simulation** — remote people remain individual records, while routine work, consumption, travel, and demographic operations are processed in batches or scheduled events.

A remote farmer can still be a specific person with a spouse, children, job, wage, and health. The optimization is that the farmer does not need a sprite or per-frame update.

## 1.2 Design principle: simulated causes, visible consequences

A system should produce consequences through shared world state:

```text
Drought
 -> reduced grain production
 -> market stock falls
 -> grain price rises
 -> poor households lose food security
 -> sickness, crime, and migration increase
 -> taxable income falls
 -> ruler imports grain or suppresses unrest
 -> caravans reroute toward the shortage
 -> hostile armies may raid those caravans
```

The goal is not to create a large number of isolated statistics. The goal is to connect the statistics through understandable causes.

---

# 2. Current codebase assessment

## 2.1 Existing strategic simulation

The `src/main/java/kingdom` package currently provides prototype implementations for:

- Kingdoms and territories
- Rulers and direct descendants
- Character traits and opinions
- Tax income and treasury growth
- Alliances and war declarations
- Marriage, birth, aging, death, and succession
- Claims, assassination attempts, and spy networks
- Monthly and yearly processing
- A standalone `KingdomTestHarness`

Relevant files:

```text
src/main/java/kingdom/Character.java
src/main/java/kingdom/Kingdom.java
src/main/java/kingdom/KingdomSimulation.java
src/main/java/kingdom/Territory.java
src/main/java/kingdom/War.java
src/main/java/kingdom/Scheme.java
src/main/java/kingdom/IntrigueSystem.java
src/main/java/kingdom/MarriageSystem.java
src/main/java/kingdom/KingdomTestHarness.java
```

These classes are useful prototypes but do not yet constitute a complete world model.

## 2.2 Existing parties and battles

The `party` package and `GamePanel` currently support:

- Player and AI parties
- Party-to-kingdom affiliation
- Roaming, pursuit, and fleeing
- Hostile proximity encounters
- A deployment layer
- Formation selection
- Transition to a tactical battlefield map
- Loot and prisoner fields

Relevant files:

```text
src/main/java/party/Party.java
src/main/java/party/PartyManager.java
src/main/java/party/AiPartyController.java
src/main/java/my2Dgame/GamePanel.java
```

## 2.3 Integration gaps that must be addressed

1. `GamePanel.update()` updates the party system but does not normally call `kingdomSim.update()`.
2. Normal gameplay does not appear to populate `kingdomSim.allKingdoms`; the test harness creates test kingdoms separately.
3. AI parties are randomly spawned rather than recruited from settlements, manpower, equipment, or treasury.
4. Territory has no world coordinate, owner history, neighbors, resource deposits, or settlements.
5. `War` records a declaration and score but does not simulate armies, battles, sieges, occupation, or peace.
6. `kingdom.Character` only supports a shallow ruling family rather than the full population.
7. Direct object references and object-keyed maps will complicate save/load and unloaded entities.
8. `Hero.KingdomAffairs` stores an independent treasury/happiness model disconnected from `kingdom.Kingdom`.
9. There is no complete persistent save format for the world.
10. `GamePanel` already owns too many responsibilities and should not become the simulation engine.
11. AI parties only reason about the player; there is no general party-versus-party strategic interaction.
12. Strategic parties and tactical troops are not separated by a formal conversion/reconciliation boundary.

## 2.4 Migration policy

Do not rewrite everything in one step. Preserve playable behavior while introducing a new headless world core alongside the existing prototype. Migrate one connection at a time behind adapters.

Recommended migration order:

1. World clock and stable IDs
2. Geography and settlement registry
3. People and households
4. Production and markets
5. Physical travel parties
6. Kingdom and diplomacy migration
7. Army and war migration
8. Tactical battle bridge
9. Removal of obsolete duplicate state

---

# 3. Non-negotiable architecture rules

## Rule 1: the simulation must run without Swing

A headless program must be able to create a world, advance it for 100 years, validate it, and save it without constructing `JFrame`, `JPanel`, sprites, or audio.

## Rule 2: rendering must not own authoritative world data

`GamePanel`, sprites, buttons, and tactical render entities are presentation objects. Persistent world state belongs in `WorldState`.

## Rule 3: persistent entities require stable IDs

Never depend on Java object identity for persistent relationships. People, settlements, realms, armies, titles, wars, workplaces, caravans, and households need stable IDs.

## Rule 4: strategic time must not depend on frame count

A month cannot simply mean 3,600 rendered frames. The world clock must advance from elapsed time and configured game speed.

## Rule 5: systems mutate world state through controlled boundaries

A market system should not directly rewrite diplomatic relations. It should change market data and emit events that diplomacy or politics may consume.

## Rule 6: one source of truth per value

There must not be separate authoritative kingdom treasuries in `Hero.KingdomAffairs`, `GamePanel`, and `Kingdom`. UI values must read from the simulation.

## Rule 7: strategic and tactical entities are not the same object

Strategic soldiers persist for months or years. Tactical `Troop` and `Enemy` objects are temporary battle projections. Battle results reconcile back into strategic data.

## Rule 8: expensive decisions run infrequently

Local movement may update per frame. Marriage, migration, employment, diplomacy, and production planning should run daily, weekly, monthly, or in response to events.

## Rule 9: randomness is seeded and injectable

Simulation classes should never call `Math.random()` directly. Use named seeded random streams so bugs and test scenarios can be reproduced.

## Rule 10: each phase requires invariants and acceptance tests

Do not add the next major subsystem until the current one survives long headless simulations without invalid state.

---

# 4. Recommended source layout

The exact names can change, but responsibilities should remain separated.

```text
src/main/java/world/
    WorldState.java
    WorldSimulation.java
    WorldClock.java
    WorldCalendar.java
    WorldConfig.java
    WorldGenerator.java
    IdGenerator.java
    SimulationContext.java

src/main/java/world/command/
    WorldCommand.java
    CommandQueue.java
    CommandResult.java

src/main/java/world/event/
    WorldEvent.java
    EventBus.java
    EventHistory.java
    ScheduledEvent.java
    ScheduledEventQueue.java

src/main/java/world/people/
    Person.java
    PersonRepository.java
    Household.java
    Dynasty.java
    Relationship.java
    PersonSkills.java
    PersonNeeds.java
    DemographicSystem.java
    HouseholdSystem.java
    EmploymentSystem.java
    PersonDecisionSystem.java

src/main/java/world/geography/
    Province.java
    Settlement.java
    District.java
    Building.java
    Road.java
    RouteGraph.java
    WorldPosition.java
    GeographySystem.java
    TravelSystem.java

src/main/java/world/economy/
    GoodType.java
    GoodsCatalog.java
    Inventory.java
    MoneyAccount.java
    ProductionRecipe.java
    Workplace.java
    Market.java
    MarketOrder.java
    MarketSystem.java
    ProductionSystem.java
    TaxSystem.java

src/main/java/world/trade/
    Caravan.java
    TradePlan.java
    TradeRoute.java
    MerchantSystem.java

src/main/java/world/politics/
    Realm.java
    Government.java
    Title.java
    Office.java
    Law.java
    PoliticalFaction.java
    Claim.java
    Treaty.java
    DiplomaticState.java
    PoliticsSystem.java
    DiplomacySystem.java
    SuccessionSystem.java
    IntrigueSystem.java

src/main/java/world/military/
    Army.java
    Regiment.java
    SoldierRecord.java
    ArmyOrder.java
    CommanderAssignment.java
    SupplyState.java
    War.java
    WarGoal.java
    Siege.java
    Occupation.java
    PeaceOffer.java
    MilitarySystem.java
    WarSystem.java
    BattleAutoResolver.java

src/main/java/world/battle/
    BattleContext.java
    BattleParticipant.java
    BattleResult.java
    BattleCasualty.java
    TacticalBattleBridge.java

src/main/java/world/save/
    SaveGame.java
    SaveMetadata.java
    WorldSerializer.java
    SaveValidator.java
    SaveMigration.java

src/main/java/world/validation/
    WorldValidator.java
    ValidationError.java
    SimulationMetrics.java

src/main/java/presentation/world/
    WorldMapController.java
    WorldMapRenderer.java
    WorldUiSnapshot.java
    WorldEventFeed.java
```

Do not create all classes empty at once. Add each file only when its phase requires it.

---

# 5. World identity and references

## 5.1 Stable identifiers

Use a `long` ID for persistent entities. The simplest Java 11 approach is a monotonically increasing `IdGenerator` stored in the save file.

```java
public final class IdGenerator {
    private long nextId = 1L;

    public long next() {
        return nextId++;
    }

    public long getNextId() {
        return nextId;
    }

    public void restoreNextId(long value) {
        if (value < 1L) throw new IllegalArgumentException("Invalid next ID");
        nextId = value;
    }
}
```

Do not recycle IDs after death or destruction. Historical events need to continue referring to old entities.

## 5.2 Repositories

`WorldState` should own registries:

```java
public final class WorldState {
    public final Map<Long, Person> people = new HashMap<>();
    public final Map<Long, Household> households = new HashMap<>();
    public final Map<Long, Settlement> settlements = new HashMap<>();
    public final Map<Long, Realm> realms = new HashMap<>();
    public final Map<Long, Army> armies = new HashMap<>();
    public final Map<Long, Caravan> caravans = new HashMap<>();
    public final Map<Long, War> wars = new HashMap<>();
}
```

Later, repositories can encapsulate indexes and validation, but initially explicit maps are acceptable.

## 5.3 References

Use IDs in persistent models:

```java
public final class Person {
    public long id;
    public Long householdId;
    public Long spouseId;
    public Long fatherId;
    public Long motherId;
    public Long currentSettlementId;
    public Long employerId;
}
```

Use `Long` only when a relationship may be absent. Prefer primitive `long` for mandatory references.

## 5.4 Secondary indexes

Avoid repeatedly scanning the entire population. Maintain indexes such as:

```text
peopleBySettlement
peopleByHousehold
workersByWorkplace
armiesByRealm
warsByRealm
caravansByRoute
childrenByParent
```

All modifications must update the primary record and relevant indexes together. Add validation tests that compare indexes to source records.

## 5.5 Historical identity

Dead people remain in `people` or move to a history store. Never delete them while events, children, claims, or dynasties refer to them. Mark them with:

```java
boolean alive;
Long deathMinute;
DeathCause deathCause;
```

---

# 6. World state and simulation clock

## 6.1 Calendar representation

Store time as a single monotonically increasing count, such as world minutes. Derive hour/day/month/year from it.

```java
public final class WorldClock {
    private long worldMinute;
    private int speed = 1;
    private boolean paused;
}
```

Avoid storing separately mutable day/month/year fields because they can disagree.

## 6.2 Real-time accumulation

The client should accumulate elapsed real time and request strategic steps:

```java
double accumulatedWorldMinutes;

void update(double realSeconds) {
    if (paused) return;
    accumulatedWorldMinutes += realSeconds * worldMinutesPerRealSecond * speed;

    int budget = MAX_STEPS_PER_FRAME;
    while (accumulatedWorldMinutes >= STEP_MINUTES && budget-- > 0) {
        simulation.advanceMinutes(STEP_MINUTES);
        accumulatedWorldMinutes -= STEP_MINUTES;
    }
}
```

Prevent a long frame from causing an unlimited catch-up loop. Carry remaining accumulated time into later frames.

## 6.3 Recommended frequencies

| Frequency | Responsibilities |
|---|---|
| Render frame | Loaded physical movement, animation, tactical combat |
| Strategic hour | Travel progress, patrol detection, local schedules |
| Strategic day | Consumption, production, market clearing, health, army supply |
| Strategic week | Employment search, trade planning, recruitment planning |
| Strategic month | Taxes, rent, wages if monthly, diplomacy, faction pressure |
| Strategic season | Harvests, campaign modifiers, migration review |
| Strategic year | Aging summaries, long-term demographics, title history |

Systems should expose only needed methods:

```java
interface DailySystem {
    void processDay(SimulationContext context);
}
```

Alternatively, register systems with a scheduler and frequency. Begin with explicit ordering because it is easier to reason about and test.

## 6.4 Pausing policy

Recommended first policy:

- Start menus: paused
- Pause/settings menus: paused
- Deployment screen: paused
- Tactical battles: strategic world paused
- Normal map exploration: advancing
- Dialogue: optionally paused at first

Later, tactical battles may advance strategic time by the battle duration when results are applied.

---

# 7. Commands, events, and system boundaries

## 7.1 Commands

Commands request a state change and may fail:

```text
MoveArmyCommand
OfferTreatyCommand
AcceptContractCommand
BuyGoodsCommand
RecruitRegimentCommand
DeclareWarCommand
AppointOfficeCommand
StartSchemeCommand
```

A command includes actor, target, parameters, and issue time. Validation should return a reason rather than silently doing nothing.

```java
public final class CommandResult {
    public final boolean accepted;
    public final String reasonCode;
    public final String message;
}
```

## 7.2 Events

Events state that something happened:

```text
PersonBorn
PersonDied
HouseholdMigrated
PriceChanged
ShortageStarted
CaravanDeparted
CaravanArrived
TreatySigned
WarDeclared
ArmyEngaged
BattleEnded
SettlementOccupied
RulerSucceeded
RebellionStarted
```

Events serve three purposes:

1. Other systems can react without tight coupling.
2. The UI can display news.
3. Tests and historical records can inspect causal sequences.

## 7.3 Event history levels

Do not persist every routine transaction forever. Use levels:

- **Ephemeral:** routine market matches, discarded after processing.
- **Recent:** retained for a fixed period for UI/debugging.
- **Historical:** births of important people, deaths, wars, titles, battles, treaties, settlement ownership.

## 7.4 Scheduled events

Use a priority queue ordered by due world time for events such as:

- Pregnancy completion
- Contract deadline
- Treaty expiration
- Truce expiration
- Caravan arrival estimate
- Construction completion
- Scheme phase completion

Scheduled events avoid checking every person and agreement every frame.

---

# 8. Geography and world-map model

## 8.1 Distinguish strategic and local geography

The current game has multiple local maps connected through portals. A continental simulation needs an explicit strategic geography.

Use:

- A **strategic map** for settlements, roads, provinces, armies, caravans, and long-distance travel.
- Existing tile maps as **local scenes** entered when the player visits a settlement, wilderness location, or battlefield.

A world location links to an optional local map resource:

```java
public final class Settlement {
    public long id;
    public String name;
    public WorldPosition position;
    public String localMapResource;
}
```

## 8.2 World position

```java
public final class WorldPosition {
    public double x;
    public double y;
    public Long routeEdgeId;
    public double routeProgress;
}
```

Armies and caravans should normally move along a route graph. Free movement can be added later.

## 8.3 Provinces

A province contains:

- ID and name
- Polygon or region identifier
- Terrain and climate
- Resource deposits
- Settlement IDs
- Legal title ID
- Current controller realm ID
- Occupying realm ID if different
- Neighbor province IDs
- Road connections

Legal ownership and current military control must be separate.

## 8.4 Route graph

Each route edge should provide:

```text
length
terrain cost
road quality
seasonal modifier
river crossing cost
danger level
owner/toll authority
blocked state
```

Use Dijkstra or A* for strategic routing. Cache routes and invalidate caches when bridges, roads, control, or blockades change.

## 8.5 Spatial index

A large map requires efficient proximity queries. Begin with a uniform grid keyed by strategic cell. Register moving parties by cell and query adjacent cells for:

- Encounters
- Patrol detection
- Army interception
- Nearby rendering
- Bandit attacks

Do not scan all parties against all other parties each update.

---

# 9. People, households, and dynasties

## 9.1 Person model

Start with a focused data model:

```java
public final class Person {
    public long id;
    public String givenName;
    public String familyName;
    public Sex sex;
    public long birthMinute;
    public Long deathMinute;
    public boolean alive = true;

    public long cultureId;
    public long religionId;
    public Long dynastyId;
    public Long householdId;
    public Long spouseId;
    public Long fatherId;
    public Long motherId;

    public Long homeSettlementId;
    public Long currentSettlementId;
    public Long travelingPartyId;
    public Long employerId;
    public Long officeId;

    public PersonSkills skills = new PersonSkills();
    public PersonNeeds needs = new PersonNeeds();
    public Personality personality = new Personality();
    public HealthState health = new HealthState();
    public WealthState wealth = new WealthState();
}
```

Do not put Swing, images, tile coordinates, or combat animation in this class.

## 9.2 Skills

Initial skill set:

- Farming
- Crafting
- Trade
- Medicine
- Learning
- Stewardship
- Diplomacy
- Intrigue
- Martial
- Leadership

Use bounded integer ranges such as 0–100. Skills improve through work, training, education, and experience.

## 9.3 Personality

Use numeric axes for decision-making:

```text
ambition
bravery
compassion
greed
honor
loyalty
sociability
zeal
patience
```

Named traits shown to the player can be derived from thresholds. Existing traits can be mapped during migration.

## 9.4 Household model

```java
public final class Household {
    public long id;
    public Long homeBuildingId;
    public long homeSettlementId;
    public List<Long> memberIds;
    public Inventory inventory;
    public MoneyAccount account;
    public Long ownedWorkplaceId;
    public Long rentedPropertyId;
    public int socialStatus;
    public double foodSecurity;
    public double safety;
}
```

Households pool food and housing. This reduces transaction volume while preserving individual members.

## 9.5 Family relationships

Parent IDs are authoritative. Maintain a child index instead of storing duplicate mutable child lists on every person. Validate:

- Person is not their own ancestor.
- Parent is older than child by a configured minimum.
- Spouse links are symmetric.
- A person belongs to at most one active household.

## 9.6 Birth

A birth requires:

- Valid pregnancy or simplified fertility decision
- Living parent record(s), depending on world rules
- Household capacity and health conditions
- New stable person ID
- Parent and dynasty links
- Newborn needs and household membership
- `PersonBorn` historical event

Do not name every child “Child of X.” Use culture-specific name pools and uniqueness is not required.

## 9.7 Death

Death processing must:

1. Set the person dead and record cause/time.
2. Remove them from employment and offices.
3. Remove them from active parties/armies.
4. Update spouse/household status.
5. Trigger inheritance.
6. Trigger succession if they held titles.
7. Update dependents.
8. Emit an event.
9. Preserve the record for history.

## 9.8 Migration

Migration is a household decision based on:

```text
expected wages
food prices
housing availability
safety
cultural acceptance
family ties
travel cost
border access
war risk
```

Remote migration can be scheduled. Nearby migrants may become a physical traveling party.

---

# 10. Needs, employment, and individual decisions

## 10.1 Needs

Begin with seven needs:

- Food security
- Shelter
- Health
- Safety
- Wealth security
- Social belonging
- Political satisfaction

Represent each as 0–100 or 0.0–1.0. Do not update all needs every hour if daily processing is sufficient.

## 10.2 Daily consumption

Households purchase and consume food. A simple initial order:

1. Determine household requirement from member ages.
2. Consume inventory.
3. Submit market demand for shortfall.
4. Consume successful purchases.
5. Reduce food security if still short.
6. Apply health risk after repeated shortages.

## 10.3 Employment

A workplace advertises jobs with:

- Required skill
- Wage
- Capacity
- Stability
- Social restrictions if the setting uses them

A person evaluates nearby opportunities weekly, not continuously. Employers evaluate applicants by skill, relationship, status, and wage.

## 10.4 Decision utility

Use explainable scores:

```text
utility = expected benefit
        - money cost
        - danger cost
        - travel cost
        + personality modifiers
        + relationship modifiers
        + loyalty/duty modifiers
        + urgency
```

Record the top factors in debug mode so AI behavior can be explained.

## 10.5 Individual decision budget

Not every person requires a complex decision each day. Assign decision schedules:

- Hungry/unemployed/displaced people: evaluate frequently.
- Stable workers: continue routines until an event changes circumstances.
- Important characters: evaluate politics and relationships more often.
- Children: limited decisions.
- Soldiers: follow army orders, with morale/desertion checks.

## 10.6 Information limits

People should not know global truth. Give agents knowledge from:

- Home settlement market
- Recently visited locations
- Rumors
- Realm announcements
- Scouts and spies
- Merchant contacts
- Family and faction networks

A merchant’s trade plan should use known or estimated prices, not perfect current prices across the continent.

---

# 11. Settlements, buildings, and ownership

## 11.1 Settlement model

```java
public final class Settlement {
    public long id;
    public String name;
    public long provinceId;
    public Long controllerRealmId;
    public WorldPosition position;
    public SettlementType type;

    public List<Long> districtIds;
    public List<Long> buildingIds;
    public Market market;
    public Inventory publicStockpile;
    public MoneyAccount treasury;

    public double security;
    public double sanitation;
    public double prosperity;
    public double unrest;
}
```

Population should be derived from resident people rather than independently edited.

## 11.2 Building model

Buildings can be:

- Houses
- Farms
- Workshops
- Markets
- Warehouses
- Barracks
- Walls
- Keeps
- Temples
- Taverns
- Hospitals
- Schools
- Ports

A building may provide housing, production slots, storage, defense, services, or offices.

## 11.3 Ownership

Keep these relationships separate:

- Legal owner
- Current tenant/operator
- Tax authority
- Military controller

This allows occupation, rented businesses, confiscation, and feudal obligations.

## 11.4 Construction

Construction requires:

- Land or building slot
- Builder/workplace
- Material inventory
- Labor
- Money
- Time
- Legal permission if required

Construction progresses daily and emits completion events.

---

# 12. Goods, production, markets, and money

## 12.1 Initial goods catalog

Start with approximately 12 goods:

```text
GRAIN
VEGETABLES
MEAT
TIMBER
STONE
IRON_ORE
TOOLS
WEAPONS
ARMOR
CLOTH
HORSES
MEDICINE
```

Add salt, fuel, alcohol, luxury goods, and specialized resources after the vertical slice works.

## 12.2 Goods definition

```java
public final class GoodDefinition {
    public GoodType type;
    public String displayName;
    public double basePrice;
    public double weight;
    public boolean perishable;
    public int spoilageDays;
}
```

## 12.3 Inventory

Use quantities keyed by enum or good ID:

```java
EnumMap<GoodType, Integer> quantities;
```

All mutations go through methods that reject negative values. Large quantities should use `long` if integer overflow becomes possible.

## 12.4 Money

Choose an integer smallest currency unit. Never use floating-point money.

```java
long copperCoins;
```

Money transfers must be atomic:

```java
boolean transfer(MoneyAccount from, MoneyAccount to, long amount)
```

Track sources and sinks deliberately:

- Sources: minting, scenario initialization, externally modeled income.
- Sinks: maintenance loss, destruction, fees deliberately removed from circulation.
- Transfers: trade, taxes, wages, ransom, loot.

## 12.5 Production recipes

Example:

```text
Farm daily batch:
inputs: seed grain, labor, land
outputs: grain
modifiers: season, fertility, weather, tools, worker skill

Smithy daily batch:
inputs: iron ore, fuel, labor
outputs: tools or weapons
modifiers: worker skill, building quality
```

Production cannot occur if mandatory inputs are missing.

## 12.6 Production planning

Workplaces estimate expected profit and local demand weekly. They choose a recipe and target quantity, then execute daily within labor and input limits.

Avoid granting producers perfect knowledge of future prices.

## 12.7 Market orders

A market order contains:

```java
long ownerId;
GoodType good;
OrderSide side;
int quantity;
long limitPrice;
long createdMinute;
long expiryMinute;
```

For the first implementation, daily aggregate matching is sufficient:

1. Collect buy and sell orders.
2. Sort buys descending by price.
3. Sort sells ascending by price.
4. Match while buy price >= sell price.
5. Transfer goods and money.
6. Record volume and clearing price.
7. Expire old orders.

## 12.8 Price formation

Use the last clearing price where trades occur. When no trades occur, adjust a reference price slowly from stock-to-demand pressure. Clamp movement to avoid explosive oscillations.

## 12.9 Taxes and rents

Initially support:

- Market sales tax
- Household head tax or income tax
- Land/building rent
- Realm tariff on imported goods

Every tax must transfer actual money to a treasury. Failure to pay can create debt, confiscation, imprisonment, or unrest later.

---

# 13. Trade routes and physical caravans

## 13.1 Trade opportunity

A merchant estimates:

```text
expected profit = expected sale revenue
                - purchase cost
                - tariff
                - food and guard wages
                - travel cost
                - expected loss from danger
                - opportunity cost of time
```

## 13.2 Caravan model

```java
public final class Caravan {
    public long id;
    public long ownerId;
    public long leaderPersonId;
    public List<Long> memberIds;
    public Inventory cargo;
    public MoneyAccount cash;
    public WorldPosition position;
    public long originSettlementId;
    public long destinationSettlementId;
    public List<Long> routeEdgeIds;
    public int routeIndex;
    public CaravanState state;
}
```

## 13.3 Departure

Before departure:

- Reserve cargo from seller inventory.
- Transfer purchase money.
- Assign leader, guards, animals, and carts.
- Calculate carrying capacity.
- Choose route from known information.
- Estimate provisions.
- Emit departure event.

## 13.4 Travel and encounters

Each strategic hour:

- Advance along route according to speed.
- Consume provisions at daily boundaries.
- Check route hazards through spatial index or scheduled risk.
- Check hostile parties/patrols.
- Enter destination when complete.

If near the player, represent the caravan with a visible party view. Do not create a separate duplicate caravan state.

## 13.5 Arrival

At arrival:

- Add orders to the destination market or sell directly under the chosen model.
- Pay tariffs.
- Update merchant knowledge.
- Pay wages.
- Record profit/loss.
- Select return cargo or next destination.

## 13.6 Banditry

Bandit activity should respond to:

- Poverty and displaced people
- Low patrol coverage
- Valuable trade volume
- Terrain concealment
- War
- Realm stability

Bandits should steal real cargo and money. Losses must affect destination supply.

---

# 14. Kingdoms, titles, laws, and government

## 14.1 Separate concepts

Do not combine all political concepts into one `Kingdom` object.

- **Realm:** organization that commands resources and conducts diplomacy.
- **Title:** legal claim to rule land or an institution.
- **Province:** physical land.
- **Government:** decision and succession rules.
- **Office:** position held by a person.
- **Controller:** current military/administrative power.

## 14.2 Realm model

```java
public final class Realm {
    public long id;
    public String name;
    public long governmentId;
    public long rulerTitleId;
    public Long capitalSettlementId;
    public Set<Long> controlledProvinceIds;
    public Set<Long> vassalRealmIds;
    public MoneyAccount treasury;
    public double legitimacy;
    public double stability;
    public double warExhaustion;
}
```

## 14.3 Titles

A title includes:

- Rank
- Name
- De jure province/realm
- Current holder
- Claimants
- Inheritance law
- Holder history

Claims should have strength, source, expiry rules, and inheritance behavior.

## 14.4 Government

Government defines:

- Succession method
- Who can hold offices
- Who votes or advises
- Tax authority
- Recruitment authority
- Vassal obligations
- Legal limits on rulers
- Methods for changing laws

## 14.5 Laws

Start with a small number of consequential laws:

- Tax level
- Conscription level
- Trade tariff
- Religious tolerance
- Noble privilege
- Succession law
- Local autonomy

Each law affects systems rather than existing only as flavor text.

## 14.6 Succession

Succession procedure:

1. Detect vacant title.
2. Gather eligible candidates.
3. Apply legal eligibility.
4. Calculate lawful heir or election.
5. Transfer title and offices.
6. Update realm ruler.
7. Create claims for bypassed candidates when appropriate.
8. Adjust legitimacy and faction support.
9. Emit succession event.
10. Permit disputed succession or civil war.

---

# 15. Political factions, diplomacy, and intrigue

## 15.1 Political factions

A political faction has:

```java
long id;
long realmId;
FactionGoal goal;
Set<Long> memberPersonIds;
Long leaderPersonId;
double support;
double organization;
double militancy;
```

Initial goals:

- Lower taxes
- Increase noble privilege
- Increase merchant privilege
- Replace ruler
- Independence
- Religious policy
- End war
- Continue war

## 15.2 Faction power

Faction power derives from member wealth, offices, military command, social status, relationships, and regional support. A faction should not revolt merely because a random roll succeeded; it should need organization and a perceived chance of success.

## 15.3 Diplomatic state

Replace one relation enum with richer bilateral data:

```java
public final class DiplomaticState {
    public long firstRealmId;
    public long secondRealmId;
    public int opinion;
    public int trust;
    public int fear;
    public int rivalry;
    public int borderTension;
    public int tradeDependence;
    public Set<Long> treatyIds;
    public Set<Long> grievanceIds;
}
```

War status can be derived from active wars rather than stored independently in two maps.

## 15.4 Treaties

Initial treaty types:

- Truce
- Non-aggression pact
- Trade agreement
- Defensive alliance
- Military access
- Vassalage
- Tribute

Treaties include participants, start/end times, obligations, and violation consequences.

## 15.5 Diplomacy AI

A proposal score should include:

- Strategic threat
- Existing trust
- Trade dependence
- Border disputes
- Ruler personality
- Faction pressure
- Military balance
- Treaty reliability
- Common enemies
- Legitimacy

Use hard validation before utility scoring. A realm cannot offer money it does not possess or military access it cannot legally grant.

## 15.6 Intrigue migration

Preserve existing concepts—claims, assassination, and spy networks—but expand schemes into phases:

1. Select goal.
2. Recruit agents.
3. Pay costs.
4. Gather preparation.
5. Make periodic detection checks.
6. Resolve attempt.
7. Apply evidence, suspicion, and consequences.

A spy network should provide information quality and scheme support, not only a numeric advantage.

---

# 16. Armies, recruitment, logistics, and wars

## 16.1 Army model

```java
public final class Army {
    public long id;
    public long realmId;
    public long commanderPersonId;
    public List<Long> regimentIds;
    public WorldPosition position;
    public ArmyOrder order;
    public Inventory supplies;
    public double morale;
    public double fatigue;
    public double movementProgress;
}
```

## 16.2 Regiment model

A regiment stores strategic organization. For the full individual model, keep soldier person IDs:

```java
public final class Regiment {
    public long id;
    public RegimentType type;
    public List<Long> soldierPersonIds;
    public Long officerPersonId;
    public int equipmentQuality;
    public double cohesion;
    public double experience;
}
```

At large scale, primitive ID arrays or compressed soldier assignments may later replace boxed `List<Long>` for memory efficiency.

## 16.3 Recruitment

Recruitment requires:

- Eligible resident people
- Recruitment law or contract
- Treasury for pay
- Weapons/armor where required
- Training location
- Officer or commander

Recruitment removes or reduces workers from the civilian economy. It must not create population.

## 16.4 Soldier state

A recruited person receives:

- Military employment
- Regiment assignment
- Pay agreement
- Equipment assignment
- Training/experience
- Loyalty and morale effects

Upon demobilization, surviving people return or migrate. Injuries may reduce future work capacity.

## 16.5 Army supply

Daily consumption depends on soldiers, animals, climate, and activity. Armies obtain supplies from:

- Carried inventory
- Friendly settlements
- Supply caravans
- Legal requisition
- Foraging
- Pillaging

Consequences of shortage:

- Morale loss
- Fatigue
- Attrition
- Desertion
- Slower movement
- Disease risk
- Reduced combat effectiveness

## 16.6 Orders

Initial army orders:

```text
MUSTER
MOVE
PATROL
ESCORT
DEFEND
PURSUE
AVOID
RAID
BESIEGE
RESUPPLY
RETURN_HOME
```

Each order needs a target and completion/failure conditions.

## 16.7 War model

```java
public final class War {
    public long id;
    public Set<Long> attackerRealmIds;
    public Set<Long> defenderRealmIds;
    public WarGoal goal;
    public long startMinute;
    public WarState state;
    public Map<Long, Double> contributionByRealm;
    public double attackerScore;
    public double defenderScore;
    public double attackerExhaustion;
    public double defenderExhaustion;
}
```

## 16.8 War score sources

- Battle victories and losses
- Objective control
- Settlement occupation
- Capital occupation
- Blockades
- Prisoners of importance
- War-goal progress
- Relative casualties

War score should not by itself decide everything. Peace acceptance also considers treasury, army strength, allies, exhaustion, ruler personality, and internal factions.

## 16.9 Siege

A siege tracks:

- Besieging army
- Settlement fortification
- Defender garrison
- Food stocks
- Breach progress
- Disease
- Assault attempts
- Relief army

A settlement consumes its real stockpile. A prolonged siege should affect civilians, prices, deaths, and unrest.

## 16.10 Peace terms

Initial terms:

- White peace
- Transfer province/title
- Tribute
- Reparations
- Release prisoners
- Vassalization
- Recognition of independence

Validate terms and apply them atomically. Record a treaty and truce.

---

# 17. Strategic-to-tactical battle integration

## 17.1 Battle boundary

When strategic parties engage, create an immutable `BattleContext` snapshot. Do not hand strategic `Regiment` or `Person` objects to tactical combat for direct mutation.

```java
public final class BattleContext {
    public long battleId;
    public long strategicMinute;
    public BattleSide attacker;
    public BattleSide defender;
    public TerrainType terrain;
    public WeatherType weather;
    public boolean playerInvolved;
}
```

## 17.2 Tactical projection

The bridge converts strategic participants into tactical entities:

```text
Strategic regiment type -> Troop/Enemy role
Equipment quality -> tactical attack/defense modifiers
Experience -> accuracy/morale modifiers
Commander skill -> formation/command modifiers
Strategic morale -> tactical starting morale
Terrain -> battlefield selection/modifiers
```

Each tactical combatant should retain a source person ID or source cohort reference.

## 17.3 Battle result

```java
public final class BattleResult {
    public long battleId;
    public WinningSide winner;
    public List<BattleCasualty> casualties;
    public List<Long> prisonerPersonIds;
    public Inventory capturedGoods;
    public Map<Long, Double> regimentExperienceGain;
    public RetreatOutcome retreat;
    public long tacticalDurationMinutes;
}
```

Casualty outcomes should distinguish:

- Unharmed
- Wounded
- Severely wounded
- Killed
- Captured
- Missing/deserted

## 17.4 Reconciliation

Applying a result must:

1. Confirm the battle has not already been applied.
2. Update soldier health/death/capture.
3. Update regiments.
4. Transfer captured inventory.
5. Update army morale, fatigue, and position.
6. Remove destroyed armies.
7. Update war contribution and score.
8. Notify households of deaths when information arrives.
9. Emit battle and person events.
10. Save or autosave at a safe point.

## 17.5 AI auto-resolution

AI-only battles create the same `BattleContext` and return the same `BattleResult` type. Inputs should include:

- Troop counts and types
- Equipment
- Commander skill
- Experience
- Morale
- Fatigue
- Terrain
- Fortifications
- Surprise
- Supply state

Use multiple combat rounds with bounded random variance rather than one unconstrained strength roll.

## 17.6 Existing `GamePanel` integration

The current deployment and battle transition can remain initially, but should be called through a controller:

```text
World simulation detects engagement
 -> pauses strategic advancement
 -> creates BattleContext
 -> GamePanel enters DEPLOYMENT
 -> tactical entities are created
 -> battle completes
 -> BattleResult is returned
 -> strategic reconciliation runs
 -> GamePanel returns to strategic/local map
 -> strategic advancement resumes
```

---

# 18. Independent adventurer progression

## 18.1 Player world identity

The player requires a persistent person ID and party ID. `Player` remains the local physical controller, while persistent state belongs to a world person/party record.

Synchronize only through explicit adapters:

```text
Enter local map: world person -> Player presentation state
Leave local map: Player results -> world person state
```

## 18.2 Early-game activities

- Escort caravan
- Hunt bandits
- Deliver message
- Carry goods
- Join tournament or local fight
- Rescue prisoner
- Scout enemy movement
- Recruit companions
- Work as mercenary
- Discover resource or route information

## 18.3 Reputation

Track reputation by settlement, realm, organization, and important person. Avoid one universal morality score.

Actions affect relevant observers. Secret crimes should not affect reputation unless discovered.

## 18.4 Contracts

A contract includes:

```text
issuer
taker
objective
deadline
reward
collateral or penalty
visibility
completion conditions
failure conditions
```

Contracts should derive from simulation needs:

- Merchant requests escort because route danger is high.
- Settlement requests grain because stock is low.
- Realm requests scouts because enemy information is poor.
- Noble requests support because a faction is organizing.

## 18.5 Progression to rulership

Possible route:

```text
Adventurer
 -> renowned party leader
 -> mercenary captain or wealthy merchant
 -> office holder/retainer
 -> land grant or purchased estate
 -> vassal/title holder
 -> claimant or rebel
 -> independent ruler
```

Rulership should grant access to the same world systems already used by AI, not a separate player-only kingdom minigame.

---

# 19. AI planning

## 19.1 Hierarchical AI

Use different planners by level:

- Person AI: survival, work, family, local ambition
- Household AI: consumption, saving, migration, property
- Workplace AI: production, hiring, purchases, sales
- Merchant AI: trade route and cargo
- Army AI: orders and tactical posture
- Realm AI: economy, diplomacy, law, military strategy

## 19.2 Goals and actions

Realm goals might include:

```text
Restore treasury
Prevent famine
Defend border
Acquire disputed province
Suppress rebellion
Secure trade route
Build alliance
Break encirclement
Increase legitimacy
```

Candidate actions should have prerequisites, expected utility, cost, risk, and cooldown.

## 19.3 Commitment and inertia

AI should not switch decisions every tick. Once an action is chosen:

- Store the plan.
- Reevaluate only on schedule or major event.
- Apply cancellation costs.
- Preserve personality-based persistence.

## 19.4 Explainability

In debug mode, retain:

```text
Chosen action: Import Grain
Score: 84
+45 capital food shortage
+25 projected unrest
+20 treasury can afford purchase
-6 dangerous route
```

This is essential for balancing an interconnected simulation.

## 19.5 No omniscience

Realm AI uses reports, scouts, diplomats, and spies. Information has age and confidence. An unseen army should not influence exact decisions as if its location and strength were known perfectly.

---

# 20. Simulation fidelity and performance

## 20.1 Fidelity tiers

### Tier A — loaded local scene

- Per-frame movement
- Tile collision
- Combat AI
- Animation
- Immediate dialogue
- Physical schedules

### Tier B — nearby region

- Individual hourly/daily activity
- Strategic positions
- Party encounters
- Detailed market and workplace processing

### Tier C — remote region

- Individual persistent records
- Batched household consumption
- Batched routine production
- Scheduled travel and life events
- Infrequent complex decisions

## 20.2 Activation and deactivation

When a location loads:

1. Read persistent residents and visitors.
2. Create physical entities only for those who should be visible.
3. Position them from schedule/location state.
4. Run local simulation.

When unloading:

1. Write relevant physical results back to persistent state.
2. Remove render/combat objects.
3. Schedule next abstract activities.

## 20.3 Performance budgets

Set budgets before final scale. Example targets:

- Rendering/local combat: maintain expected frame rate.
- Strategic simulation: limited milliseconds per frame under normal speed.
- Daily world step: benchmark separately.
- Save: complete within an acceptable pause or use snapshot writing.
- Headless 50-year simulation: complete within a defined test duration.

## 20.4 Data structure concerns

Hundreds of thousands of people stored as ordinary Java objects can consume substantial memory. Begin with clear objects for correctness. Profile before optimizing. Potential later optimizations:

- Primitive collections
- Arrays by entity ID
- Compact enums/bytes
- Separate hot and cold data
- Compressed history
- Batch household records
- Avoid boxed `Long` lists for massive soldier populations

Do not prematurely optimize away persistent identity.

## 20.5 Threading policy

Start single-threaded for correctness. Keep all authoritative simulation mutation on one simulation thread.

If background work is later added:

- UI submits commands through a queue.
- Simulation publishes immutable/read-only snapshots.
- Rendering never iterates mutable simulation collections.
- Save operations serialize a stable snapshot.

Do not allow Swing’s event thread and game thread to mutate the same world collections directly.

---

# 21. Save/load and migration

## 21.1 Save contents

Persist:

- Save schema version
- Game build/version metadata
- World seed and random stream states
- ID generator state
- Current world minute
- All persistent people, households, and histories
- Geography and ownership
- Settlements, buildings, inventories, markets, and prices
- Realms, titles, governments, offices, laws, claims, and treaties
- Parties, caravans, armies, regiments, and soldiers
- Wars, sieges, occupations, and scheduled events
- Player identity, party, inventory, contracts, and reputation

Do not persist:

- Buffered images
- Swing components
- Tile render caches
- Temporary A* nodes
- Temporary tactical objects after reconciliation
- Derived indexes that can be rebuilt safely

## 21.2 Save envelope

```java
public final class SaveGame {
    public int schemaVersion;
    public SaveMetadata metadata;
    public WorldData world;
    public PlayerData player;
}
```

## 21.3 Format

The current `pom.xml` only declares JInput. When implementation begins, choose one explicit persistence approach:

- Add Jackson for JSON during development and debugging.
- Use a custom binary format later if save size/performance requires it.
- Optionally gzip JSON saves.

Do not use default Java object serialization as the long-term save format; it is fragile across class changes.

## 21.4 Atomic save process

1. Create simulation snapshot.
2. Validate snapshot.
3. Write `save.tmp`.
4. Flush and close.
5. Preserve previous save as backup.
6. Atomically replace target where supported.
7. Record success/failure for UI.

## 21.5 Save migration

Each schema version has a migration:

```text
v1 -> v2: add household food security default
v2 -> v3: convert kingdom relation enum into treaties/diplomatic state
v3 -> v4: add army supply inventories
```

Never silently load incompatible data into partially initialized objects.

## 21.6 Deterministic random streams

Use separate random streams for demographics, economy, politics, military, and generation. Persist their state or use a reproducible generator with serializable state.

---

# 22. UI and presentation integration

## 22.1 Keep `GamePanel` as coordinator temporarily

Initially, `GamePanel` may own a `WorldSimulation` reference and call a small facade. It should not contain demographic, market, diplomacy, or war algorithms.

## 22.2 Read-only UI snapshot

Publish a snapshot containing only what the UI needs:

```java
public final class WorldUiSnapshot {
    public WorldDate date;
    public List<VisiblePartyView> parties;
    public List<SettlementView> settlements;
    public List<RecentEventView> events;
    public PlayerWorldView player;
}
```

## 22.3 Map rendering

Strategic map markers:

- Player party
- Friendly/hostile/neutral armies
- Caravans
- Settlements
- Sieges
- Known danger
- Known borders
- Contract targets

Respect information limits; do not render unknown parties.

## 22.4 Menus

Recommended screens:

- Character/household
- Party
- Inventory and cargo
- Settlement market
- Contracts
- Relationships/reputation
- Realm overview
- Diplomacy
- War overview
- Army details
- Event/news history
- Time controls

## 22.5 Advisor/kingdom affairs migration

`Hero.KingdomAffairs` should eventually become an advisor UI reading from a real `Realm`. Its treasury and happiness fields must not remain a second source of truth.

Advisor choices should submit world commands and display validated outcomes.

## 22.6 Currency unification

The following concepts must eventually reconcile:

- `GamePanel.gold`
- `Party.carriedGold`
- Household/person money
- Realm treasury
- Caravan cash

Use explicit accounts and transfers. The UI may display a convenient value, but it must identify whose account it represents.

---

# 23. Testing, diagnostics, and balancing

## 23.1 Test layout

Add tests under Maven’s standard structure when implementation starts:

```text
src/test/java/world/...
```

Use JUnit as an explicit test dependency.

## 23.2 Unit tests

Test pure operations:

- Inventory transfer
- Money transfer
- Market matching
- Route cost
- Production recipe
- Succession eligibility
- Treaty validation
- Recruitment eligibility
- Battle result application

## 23.3 Integration tests

Examples:

- Farm produces grain, household buys it, tax reaches treasury.
- Caravan moves goods and changes destination price.
- Recruitment removes workers and adds soldiers.
- Siege consumes settlement stockpile.
- Ruler death triggers valid succession.
- Tactical result kills the correct strategic soldier.

## 23.4 Invariants

Validate regularly in debug/headless mode:

- No negative inventory.
- No negative account unless debt is explicitly modeled.
- Every living person has one valid location state.
- No person is simultaneously a civilian worker and active soldier unless allowed.
- Household membership is unique.
- Spouse links are symmetric.
- Parent relationships are acyclic.
- Population derived from residents matches indexes.
- Regiment soldiers exist and are alive at assignment time.
- Armies belong to existing realms.
- Wars have valid opposing sides.
- Treaties reference valid realms.
- Market transfers preserve goods and money.
- Battle results are applied once.

## 23.5 Long-run soak tests

Run seeded scenarios for 1, 10, 50, and 100 years. Measure:

- Population by settlement
- Birth/death rates
- Household starvation
- Employment
- Good prices and volatility
- Total money and goods
- Migration
- Realm treasury
- War frequency/duration
- Army sizes/casualties
- Realm collapse/expansion
- CPU time and memory

## 23.6 Scenario tests

Create small deterministic scenarios:

- Isolated farming village
- Mining town dependent on food imports
- Blockaded port
- Disputed succession
- Border war
- Famine plus high taxes
- Caravan route under bandit threat
- Army operating without supply

## 23.7 Simulation metrics

Expose CSV or structured metrics in headless mode. Balancing through visual play alone will be too slow.

## 23.8 Debug tools

Extend the existing F3 concept with:

- Current world date and speed
- Last system processing times
- Entity counts
- Settlement market summary
- Selected person decision reasons
- Selected realm AI reasons
- Route and supply overlays
- Validation errors
- Event feed

Keep debug tools outside core rules.

---

# 24. Implementation phases

Each phase must compile, run, and have tests before proceeding.

## Phase 0 — Foundation

### Tasks

- Add stable ID generation.
- Add `WorldState`, `WorldClock`, and `WorldSimulation`.
- Add seeded random streams.
- Add event and scheduled-event foundations.
- Add a headless runner.
- Add JUnit and initial validation tests.
- Define strategic pause/time policy.

### Do not yet add

- Full politics
- Hundreds of goods
- Large world generation
- Complex tactical integration

### Acceptance

- A generated empty/small world advances one year headlessly.
- Same seed and commands produce the same important results.
- Save/load preserves clock and IDs.

## Phase 1 — Geography and vertical-slice population

### Tasks

- Add provinces, settlements, roads, and route graph.
- Add people and households with stable IDs.
- Generate two realms, six settlements, and 200–500 people.
- Add birth, aging, death, and household membership.
- Add local versus remote simulation state.

### Acceptance

- Every generated person has valid parents/household/location where applicable.
- Population indexes remain valid for 20 simulated years.

## Phase 2 — Food economy

### Tasks

- Add goods, inventory, money accounts, and transfer APIs.
- Add farms, workers, production recipes, and grain.
- Add household food consumption.
- Add daily market matching.
- Add wages, simple rent, and taxes.
- Add famine/shortage events.

### Acceptance

- Goods and money are conserved except documented sources/sinks.
- Failed harvest changes grain stock, price, food security, and deaths/migration.

## Phase 3 — Employment and trade

### Tasks

- Add workplaces and weekly employment matching.
- Add merchant knowledge.
- Add caravans and route travel.
- Add tariffs, guards, carrying capacity, and arrival sales.
- Add basic bandit threats.
- Render nearby caravans/parties strategically.

### Acceptance

- Price differences produce trade.
- Destroying a caravan removes its cargo and affects destination supply.
- A merchant can make a reproducible profit or loss.

## Phase 4 — Player integration

### Tasks

- Give player a world person and party record.
- Create adapters between persistent world state and `Player`/companions.
- Add travel between strategic locations and local maps.
- Add settlement market UI.
- Add simulation-generated contracts.
- Unify carried money/cargo.

### Acceptance

- Player can buy goods, travel, sell goods, take a contract, save, and reload.
- World continues to change during exploration.

## Phase 5 — Political core

### Tasks

- Add realms, titles, offices, governments, and laws.
- Migrate rulers and succession from `kingdom` prototypes.
- Add claims and historical title holders.
- Add political factions and legitimacy.
- Convert advisor UI to read real world state.

### Acceptance

- Ruler death transfers titles lawfully.
- Disputed succession can create factions/claims.
- Taxes and conscription have economic and political effects.

## Phase 6 — Diplomacy and intrigue

### Tasks

- Add diplomatic state and treaties.
- Add information quality.
- Migrate schemes into phased intrigue.
- Add AI proposals and player commands.
- Add treaty expiration/violation.

### Acceptance

- Alliances and war decisions can be explained by AI factors.
- Undiscovered schemes do not leak full information to targets/player.

## Phase 7 — Armies and logistics

### Tasks

- Add individual recruitment and regiments.
- Add army parties on strategic map.
- Add supplies, morale, fatigue, and orders.
- Add army-versus-army encounter detection.
- Add auto-resolution.
- Add demobilization and household consequences.

### Acceptance

- Armies cannot exceed actual recruited people.
- Campaigning consumes real supplies.
- Losses reduce population and affect households/economy.

## Phase 8 — Wars, sieges, and peace

### Tasks

- Add war goals and coalitions.
- Add occupation and sieges.
- Add war score/contribution/exhaustion.
- Add peace offers and territorial transfer.
- Add rebellion/civil-war support.

### Acceptance

- A war starts for a valid goal, has physical operations, and ends with enforceable terms.
- A siege affects settlement food and civilians.

## Phase 9 — Tactical bridge

### Tasks

- Add `BattleContext` and `BattleResult`.
- Convert strategic regiments to tactical entities.
- Track tactical source person IDs.
- Reconcile deaths, wounds, prisoners, loot, morale, and time.
- Use same result structure for auto-resolved battles.

### Acceptance

- A player battle updates the exact strategic participants once.
- Save/load before and after battle remains valid.

## Phase 10 — Scale-up

### Tasks

- Profile before optimizing.
- Add spatial indexes and route caches.
- Batch remote household/economic routines.
- Optimize population storage only where measured.
- Expand world generation gradually.
- Add immutable UI snapshots if threading is required.

### Acceptance

- Target continent meets defined CPU, memory, save-size, and frame-rate budgets.
- A 100-year soak test completes without invariant violations.

---

# 25. Detailed vertical-slice specification

The first playable vertical slice should deliberately remain small while proving every major connection.

## 25.1 World

- Two realms
- Three settlements per realm
- One disputed border province
- Roads connecting all settlements
- One safer long route and one shorter dangerous route
- 200–500 persistent people

## 25.2 Economy

Goods:

```text
GRAIN
TIMBER
IRON_ORE
TOOLS
WEAPONS
CLOTH
MEDICINE
HORSES
```

Workplaces:

```text
FARM
LUMBER_CAMP
MINE
SMITHY
WEAVER
MARKET
```

Required chains:

```text
Farm -> grain -> household/army consumption
Mine -> iron ore -> smithy -> tools/weapons
Lumber camp -> timber -> construction/smithy
```

## 25.3 Population

Every person has:

- Name and age
- Household
- Settlement or traveling-party location
- Basic skills
- Employment status
- Food/health state
- Culture/realm affiliation
- Family links where generated

## 25.4 Trade

- At least two merchant caravans
- Caravans respond to price differences
- Cargo physically travels
- Bandit risk changes route selection
- Player can escort, trade with, or attack a caravan

## 25.5 Politics

- One ruler per realm
- One succession law
- Tax and conscription laws
- Basic diplomatic state
- Border claim capable of causing war

## 25.6 Military

- Each realm can recruit one army from actual residents
- Army needs grain and wages
- Army travels visibly
- AI battle can auto-resolve
- Player can join or be caught in an encounter

## 25.7 Tactical integration

- Deployment receives strategic participants
- Tactical deaths map back to people
- Prisoners remain persistent
- Captured supplies transfer
- Strategic army retreats or is destroyed

## 25.8 Player loop

1. Start as independent adventurer in settlement A.
2. Inspect local prices and contracts.
3. Accept caravan escort or grain-delivery contract.
4. Travel on strategic map.
5. Enter encounter or local map.
6. Complete delivery/trade.
7. Improve reputation and finances.
8. Observe border tension and army movement.
9. Choose whether to assist a realm in battle.
10. Save, exit, load, and continue the same world.

## 25.9 Required causal demonstration

The slice is successful when this sequence can occur without scripting each result:

```text
Poor harvest in border settlement
 -> grain stocks fall
 -> price rises
 -> merchant identifies profit
 -> caravan transports grain
 -> bandits threaten route
 -> settlement issues escort contract
 -> player protects or fails caravan
 -> arrival prevents famine OR destruction deepens shortage
 -> realm tax and unrest change
 -> army supply availability changes
 -> border-war planning changes
```

---

# 26. Definition of done

The broader simulated-world feature is complete only when all of the following are true.

## Architecture

- Simulation runs headlessly.
- `GamePanel` does not contain world-rule implementations.
- Persistent records use stable IDs.
- Strategic time is independent from rendering FPS.
- Seeded randomness is used throughout simulation code.

## People

- Every person has a persistent life record.
- Families, households, jobs, migration, birth, and death function.
- Death and military casualties affect households and history.

## Economy

- Goods come from production and move through inventories.
- Prices respond to supply and demand.
- Money transfers are traceable and valid.
- Caravans move actual cargo.
- Shortages have demographic and political consequences.

## Politics

- Realms have rulers, laws, titles, offices, factions, claims, and treaties.
- Succession can be peaceful or disputed.
- Diplomacy and intrigue operate with incomplete information.

## War

- Soldiers come from the population.
- Armies consume supplies and money.
- Armies move physically on the strategic map.
- Battles, sieges, occupation, and peace alter real state.
- Tactical and auto-resolved battles use compatible outcomes.

## Player

- Player begins independently.
- Player can interact with markets, people, caravans, armies, contracts, and politics.
- Player can progress toward land ownership and rulership through the same systems used by AI.

## Persistence

- Full world saves and reloads correctly.
- Save versions can migrate.
- Autosave failures do not destroy the last valid save.

## Stability

- Long-run tests have no invariant violations.
- Target-scale performance meets measured budgets.
- Major AI decisions can be explained in debug output.

---

# 27. Common failure modes to avoid

## 27.1 Updating every person every frame

This will not scale. Preserve individual identity while scheduling or batching remote routine behavior.

## 27.2 Building final continent scale first

A huge world hides broken causal links and makes debugging slow. Prove the vertical slice first.

## 27.3 Adding disconnected numeric systems

A “happiness” number that does not alter decisions, migration, revolt, production, or loyalty is not meaningful simulation.

## 27.4 Randomly spawning resources, parties, or armies

Spawns should have an origin and cost. If an army appears, identify who recruited it, which people joined, what equipment it received, and who paid.

## 27.5 Sharing mutable objects between strategic and tactical layers

This risks duplicate deaths, lost soldiers, and concurrent modification. Use context/result conversion.

## 27.6 Direct use of `Math.random()`

This prevents reproducible bugs and stable tests. Use injected seeded random streams.

## 27.7 Saving Java object graphs directly

Circular references and class evolution will make saves fragile. Save versioned data with stable IDs.

## 27.8 Multiple sources of truth

Do not retain separate authoritative treasury, gold, army, happiness, or relationship values in UI and world objects.

## 27.9 Omniscient AI

Perfect knowledge makes spying, scouting, rumors, and exploration meaningless. Model known information separately from true state.

## 27.10 Unexplained AI scores

Complex AI becomes impossible to balance if it cannot report why it chose an action.

## 27.11 Premature multithreading

First establish deterministic single-threaded correctness. Add workers only around immutable snapshots or isolated calculations.

## 27.12 Removing dead people from history

Dynasties, inheritance, claims, and historical events depend on stable records for dead characters.

---

## Recommended immediate next action

Implement only **Phase 0** first: stable IDs, `WorldState`, `WorldClock`, deterministic random streams, an event foundation, a headless runner, and validation tests. Do not begin detailed trade, politics, or war logic until the simulation can advance and save a small deterministic world independently of `GamePanel`.

After Phase 0, build the six-settlement vertical slice incrementally. This produces a reliable foundation for the requested large, persistent, person-level continent without sacrificing the existing real-time combat game.
