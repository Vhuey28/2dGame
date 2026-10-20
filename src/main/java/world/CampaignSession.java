package world;

import world.command.CommandResult;
import world.diplomacy.Grievance;
import world.economy.GoodType;
import world.geography.Road;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.Siege;
import world.politics.Claim;
import world.politics.Government;
import world.politics.Title;
import world.save.CampaignSaveCodec;

import java.util.ArrayList;

/**
 * Owns one playable campaign simulation and publishes immutable UI snapshots.
 * This is the integration boundary between the Swing game and the headless world.
 */
public final class CampaignSession {
    private final long seed;
    private final WorldState world;
    private final WorldClock clock;
    private final WorldSimulation simulation;
    private final PlayerCampaignState playerState;
    private final ContractSystem contractSystem;
    private final CheatService cheatService;
    private world.local.LocalSceneSimulation localScene;
    private volatile CampaignSnapshot snapshot;
    private long lastSnapshotMinute = Long.MIN_VALUE;

    public CampaignSession(long seed) {
        this.seed = seed;
        this.world = new WorldState();
        this.clock = new WorldClock(0L);
        this.simulation = new WorldSimulation(clock, world);
        this.contractSystem = new ContractSystem(simulation.getContext());
        this.cheatService = new CheatService(simulation);
        configureRandomStreams(seed);

        WorldGenerator generator = new WorldGenerator(simulation.getContext());
        generator.generateVerticalSlice();
        this.playerState = createPlayerAdventurer();
        simulation.initializeGeneratedWorld();
        contractSystem.generateInitialOffers(clock.getWorldMinute());

        // Campaign testing starts at one world hour per real second. Players can
        // select 1x, 60x, or 1440x from the campaign map.
        clock.setSpeed(60);
        refreshSnapshot();
    }

    private PlayerCampaignState createPlayerAdventurer() {
        java.util.List<world.geography.Settlement> settlements = new java.util.ArrayList<>(
                world.geography.getSettlements().values());
        settlements.sort(java.util.Comparator.comparingLong(s -> s.id));
        if (settlements.isEmpty()) throw new IllegalStateException("Campaign requires a settlement");
        long settlementId = settlements.get(0).id;
        long personId = world.idGenerator.next();
        Person person = new Person(personId, "The", "Adventurer", Person.Sex.MALE,
                -25L * WorldConfig.MINUTES_PER_YEAR);
        person.currentSettlementId = settlementId;
        person.homeSettlementId = settlementId;
        world.people.put(personId, person);
        long householdId = world.idGenerator.next();
        Household household = new Household(householdId, settlementId, personId);
        household.inventory.add(GoodType.GRAIN, 5);
        household.inventory.add(GoodType.VEGETABLES, 5);
        world.households.put(householdId, household);
        person.householdId = householdId;

        long partyId = world.idGenerator.next();
        WorldParty party = new WorldParty(partyId, personId, settlementId);
        party.money.add(250L);
        household.account = party.money; // one canonical purse shared by strategic household/party views
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement != null) party.position = new WorldPosition(
                settlement.position.x, settlement.position.y);
        world.parties.put(partyId, party);
        person.travelingPartyId = partyId;

        PlayerCampaignState state = new PlayerCampaignState(personId, householdId, partyId,
                settlementId, party.cargo);
        state.cargoCapacity = party.cargoCapacity;
        world.player = state;
        return state;
    }

    private void configureRandomStreams(long seed) {
        SimulationContext context = simulation.getContext();
        context.randomStreams.clear();
        context.randomStreams.put("DEFAULT", new SeededRandom(seed));
        context.randomStreams.put("DEMOGRAPHICS", new SeededRandom(seed + 1));
        context.randomStreams.put("ECONOMY", new SeededRandom(seed + 2));
        context.randomStreams.put("POLITICS", new SeededRandom(seed + 3));
        context.randomStreams.put("MILITARY", new SeededRandom(seed + 4));
        context.randomStreams.put("GENERATION", new SeededRandom(seed + 5));
        context.randomStreams.put("DIPLOMACY", new SeededRandom(seed + 6));
        context.randomStreams.put("CRIME", new SeededRandom(seed + 7));
    }

    public void update(double realSeconds) {
        simulation.update(realSeconds);
        long currentMinute = clock.getWorldMinute();
        processCaptivity(currentMinute);
        processHoldingIncome(currentMinute);
        contractSystem.processDeadlines(playerState, currentMinute);
        if (currentMinute != lastSnapshotMinute) {
            refreshSnapshot();
        }
    }

    public CommandResult buyFromSettlement(long settlementId, GoodType good, int quantity) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Household household = world.households.get(playerState.householdId);
        if (settlement == null || household == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (playerState.currentSettlementId != settlementId) return CommandResult.rejected("NOT_PRESENT", "Travel to the settlement first");
        CommandResult blocked = captivityBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        quantity = Math.min(quantity, playerState.remainingCapacity());
        quantity = Math.min(quantity, settlement.publicStockpile.getQuantity(good));
        long price = Math.max(1L, settlement.market.getLastPrice(good));
        quantity = (int) Math.min(quantity, household.account.copperCoins / price);
        if (quantity <= 0 || !household.account.subtract(quantity * price)) {
            return CommandResult.rejected("CANNOT_BUY", "No stock, money, or cargo space");
        }
        settlement.treasury.add(quantity * price);
        settlement.publicStockpile.remove(good, quantity);
        playerState.cargo.add(good, quantity);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult sellToSettlement(long settlementId, GoodType good, int quantity) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Household household = world.households.get(playerState.householdId);
        if (settlement == null || household == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (playerState.currentSettlementId != settlementId) return CommandResult.rejected("NOT_PRESENT", "Travel to the settlement first");
        CommandResult blocked = captivityBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        quantity = Math.min(quantity, playerState.cargo.getQuantity(good));
        long price = Math.max(1L, settlement.market.getLastPrice(good));
        quantity = (int) Math.min(quantity, settlement.treasury.copperCoins / price);
        if (quantity <= 0 || !settlement.treasury.subtract(quantity * price)) {
            return CommandResult.rejected("CANNOT_SELL", "No cargo or settlement funds");
        }
        playerState.cargo.remove(good, quantity);
        settlement.publicStockpile.add(good, quantity);
        household.account.add(quantity * price);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult travelPlayerTo(long settlementId) {
        if (settlementId == playerState.currentSettlementId) return CommandResult.accepted();
        CommandResult blocked = captivityBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        Settlement destination = world.geography.getSettlement(settlementId);
        if (destination == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        java.util.List<Road> route = world.geography.getRouteGraph()
                .findShortestRoute(playerState.currentSettlementId, settlementId);
        if (route.isEmpty()) return CommandResult.rejected("NO_ROUTE", "No open road reaches that settlement");
        double distance = route.stream().mapToDouble(Road::getEffectiveCostFactor).sum();
        long travelMinutes = Math.max(60L, Math.round(distance / 10.0 * 60.0));
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) {
            party.state = WorldParty.PartyState.TRAVELING;
            party.currentSettlementId = null;
            party.destinationSettlementId = settlementId;
        }
        boolean paused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes(travelMinutes);
        clock.setPaused(paused);
        playerState.currentSettlementId = settlementId;
        Person player = world.people.get(playerState.personId);
        Household household = world.households.get(playerState.householdId);
        if (player != null) {
            player.currentSettlementId = settlementId;
            player.homeSettlementId = settlementId;
        }
        if (party != null) {
            for (Long memberId : party.memberPersonIds) {
                Person member = world.people.get(memberId);
                if (member != null) member.currentSettlementId = settlementId;
            }
        }
        if (household != null) household.homeSettlementId = settlementId;
        if (party != null) {
            party.state = WorldParty.PartyState.AT_SETTLEMENT;
            party.currentSettlementId = settlementId;
            party.destinationSettlementId = null;
            party.position = new WorldPosition(destination.position.x, destination.position.y);
        }
        contractSystem.onPlayerArrived(playerState, clock.getWorldMinute());
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult recruitCompanion(long personId) {
        Person companion = world.people.get(personId);
        WorldParty party = world.parties.get(playerState.partyId);
        if (companion == null || party == null) return CommandResult.rejected("INVALID_PERSON", "Person not found");
        CommandResult blocked = captivityBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        if (!companion.alive || companion.currentSettlementId == null
                || companion.currentSettlementId != playerState.currentSettlementId) {
            return CommandResult.rejected("NOT_PRESENT", "Companion must be alive and present");
        }
        if (party.memberPersonIds.contains(personId)) {
            return CommandResult.rejected("ALREADY_RECRUITED", "This person is already in your party");
        }
        long minute = clock.getWorldMinute();
        if (companion.isChild(minute) || companion.type == Person.PersonType.CHILD) {
            return CommandResult.rejected("CHILD", "Children cannot be recruited into an adventuring party");
        }
        if (companion.regimentId != null || companion.type == Person.PersonType.SOLDIER) {
            return CommandResult.rejected("MILITARY_DUTY", "This person is serving in an army");
        }
        if (companion.travelingPartyId != null && companion.travelingPartyId != party.id) {
            return CommandResult.rejected("OTHER_PARTY", "This person already belongs to another traveling party");
        }
        if (companion.type == Person.PersonType.NOBLE) {
            return CommandResult.rejected("NOBLE_DUTY", "This noble will not abandon their obligations");
        }
        if (companion.employerId != null) {
            world.economy.Workplace workplace = world.workplaces.get(companion.employerId);
            if (workplace != null) workplace.removeWorker(companion.id);
            companion.employerId = null;
        }
        party.memberPersonIds.add(personId);
        companion.travelingPartyId = party.id;
        companion.currentActivity = PersonActivity.IDLE;
        world.indexes.rebuild(world, clock.getWorldMinute());
        refreshSnapshot();
        return new CommandResult(true, "RECRUITED",
                companion.givenName + " has joined your party");
    }

    /** Returns dialogue derived from the person's current schedule, needs, and personality. */
    public CommandResult talkToPerson(long personId) {
        Person person = world.people.get(personId);
        if (!isPersonPresent(person)) {
            return CommandResult.rejected("NOT_PRESENT", "That person is no longer nearby");
        }
        String concern = mostPressingConcern(person);
        String manner = person.personality.sociability >= 65.0 ? "warmly"
                : person.personality.patience < 35.0 ? "briefly" : "carefully";
        String activity = person.currentActivity.toString().toLowerCase().replace('_', ' ');
        person.needs.socialBelonging = Math.min(1.0, person.needs.socialBelonging + 0.01);
        refreshSnapshot();
        return new CommandResult(true, "CONVERSATION",
                person.givenName + " speaks " + manner + " about " + concern
                        + " before returning to " + activity + ".");
    }

    /** Transfers one unit of player grain to a nearby person's household. */
    public CommandResult giveFoodToPerson(long personId) {
        Person person = world.people.get(personId);
        if (!isPersonPresent(person)) {
            return CommandResult.rejected("NOT_PRESENT", "That person is no longer nearby");
        }
        if (!playerState.cargo.remove(GoodType.GRAIN, 1)) {
            return CommandResult.rejected("NO_GRAIN", "You have no grain in your cargo");
        }
        Household household = person.householdId == null ? null : world.households.get(person.householdId);
        if (household != null) household.inventory.add(GoodType.GRAIN, 1);
        person.needs.foodSecurity = Math.min(1.0, person.needs.foodSecurity + 0.15);
        person.needs.socialBelonging = Math.min(1.0, person.needs.socialBelonging + 0.05);
        playerState.changeSettlementReputation(playerState.currentSettlementId, 1);
        refreshSnapshot();
        return new CommandResult(true, "GIFT_ACCEPTED",
                person.givenName + " accepts the food. Local reputation increased.");
    }

    private boolean isPersonPresent(Person person) {
        return person != null && person.alive && person.currentSettlementId != null
                && person.currentSettlementId == playerState.currentSettlementId;
    }

    private String mostPressingConcern(Person person) {
        double lowest = person.needs.foodSecurity;
        String concern = "the price of food";
        if (person.needs.safety < lowest) { lowest = person.needs.safety; concern = "safety on the roads"; }
        if (person.needs.health < lowest) { lowest = person.needs.health; concern = "their health"; }
        if (person.needs.wealthSecurity < lowest) { lowest = person.needs.wealthSecurity; concern = "finding steady work"; }
        if (person.needs.politicalSatisfaction < lowest) concern = "the local rulers";
        return concern;
    }

    /** Gives supplies to an army only when it is physically at the player's settlement. */
    public CommandResult supplyArmy(long armyId, int grain) {
        Army army = world.armies.get(armyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "That army is no longer active");
        }
        if (army.currentSettlementId != playerState.currentSettlementId
                || army.state == Army.ArmyState.TRAVELING) {
            return CommandResult.rejected("NOT_PRESENT", "The army must be at your current settlement");
        }
        if (grain <= 0 || playerState.cargo.getQuantity(GoodType.GRAIN) < grain) {
            return CommandResult.rejected("NO_GRAIN", "You do not have enough grain in your cargo");
        }
        playerState.cargo.remove(GoodType.GRAIN, grain);
        army.supplies.add(GoodType.GRAIN, grain);
        if (!army.playerCommanded) playerState.realmReputation.merge(army.realmId, 1, Integer::sum);
        refreshSnapshot();
        return new CommandResult(true, "ARMY_SUPPLIED", army.playerCommanded
                ? "Your quartermaster takes " + grain + " grain. The company is fed."
                : "The quartermaster accepts " + grain + " grain. Realm reputation increased.");
    }

    public CommandResult joinKingdom(long realmId, PlayerCampaignState.KingdomRole role) {
        Realm realm = world.realms.get(realmId);
        if (realm == null) return CommandResult.rejected("INVALID_REALM", "Kingdom not found");
        if (role != PlayerCampaignState.KingdomRole.MERCENARY
                && role != PlayerCampaignState.KingdomRole.LORD) {
            return CommandResult.rejected("INVALID_ROLE", "Join as a mercenary or lord");
        }
        playerState.affiliatedRealmId = realmId;
        playerState.kingdomRole = role;
        Person player = world.people.get(playerState.personId);
        if (player != null && role == PlayerCampaignState.KingdomRole.LORD) {
            player.type = Person.PersonType.NOBLE;
        }
        refreshSnapshot();
        return CommandResult.accepted();
    }

    /** Installs the player as ruler; succession/events and test tools can call the same hook. */
    public CommandResult appointPlayerAsKing(long realmId) {
        Realm realm = world.realms.get(realmId);
        Person player = world.people.get(playerState.personId);
        if (realm == null || player == null) {
            return CommandResult.rejected("INVALID_REALM", "Kingdom or player not found");
        }
        realm.rulerPersonId = player.id;
        player.type = Person.PersonType.NOBLE;
        playerState.affiliatedRealmId = realmId;
        playerState.kingdomRole = PlayerCampaignState.KingdomRole.KING;
        refreshSnapshot();
        return CommandResult.accepted();
    }

    public CommandResult adjustKingdomLaw(Government.LawType law, int delta) {
        Realm realm = effectivePlayerRealm();
        if (realm == null || realm.rulerPersonId == null || realm.rulerPersonId != playerState.personId) {
            return CommandResult.rejected("NOT_RULER", "Only the kingdom's ruler can change laws");
        }
        Government government = world.governments.get(realm.governmentId);
        if (government == null) return CommandResult.rejected("NO_GOVERNMENT", "Kingdom government not found");
        government.setLawLevel(law, government.getLawLevel(law) + delta);
        refreshSnapshot();
        return CommandResult.accepted();
    }

    /** How badly a lost campaign battle hit the adventurer. Rolled from the margin of defeat. */
    public enum DefeatSeverity { WOUNDED, CAPTURED, ROUTED }

    /** Severity of the defeat that was just applied, so the UI can describe it. */
    private DefeatSeverity lastDefeatSeverity = DefeatSeverity.WOUNDED;

    public DefeatSeverity getLastDefeatSeverity() {
        return lastDefeatSeverity;
    }

    /**
     * Applies the consequences of losing a campaign battle.
     *
     * <p>{@code defeatMargin} runs from 0 (you nearly held the field) to 1 (total loss) and
     * shifts a single seeded roll across three outcomes: a wounding that costs coin and cargo,
     * capture for ransom, or a rout that destroys a commanded army and can kill companions.
     */
    public CommandResult resolvePlayerDefeat(Long captorRealmId, double defeatMargin) {
        long minute = clock.getWorldMinute();
        double margin = Math.max(0.0, Math.min(1.0, defeatMargin));
        SeededRandom random = simulation.getContext().getRandom("MILITARY");
        double routedChance = 0.10 + margin * 0.40;
        double capturedChance = 0.20 + margin * 0.35;
        double roll = random.nextDouble();
        DefeatSeverity severity = roll < routedChance ? DefeatSeverity.ROUTED
                : roll < routedChance + capturedChance ? DefeatSeverity.CAPTURED
                        : DefeatSeverity.WOUNDED;
        lastDefeatSeverity = severity;

        Person player = world.people.get(playerState.personId);
        WorldParty party = world.parties.get(playerState.partyId);
        if (player == null || party == null) {
            return CommandResult.rejected("INVALID_STATE", "The adventurer cannot be resolved");
        }

        StringBuilder message = new StringBuilder();
        long lostCoins = takeCoins(party, severity == DefeatSeverity.WOUNDED ? 0.15
                : severity == DefeatSeverity.CAPTURED ? 0.40 : 0.60);
        int lostGoods = takeCargo(party, severity == DefeatSeverity.WOUNDED ? 0.10
                : severity == DefeatSeverity.CAPTURED ? 0.30 : 0.50);
        message.append("Defeat: you lose ").append(lostCoins).append("c");
        if (lostGoods > 0) message.append(" and ").append(lostGoods).append(" cargo");
        message.append(". ");

        woundPlayer(player, severity == DefeatSeverity.ROUTED ? 25.0 : 45.0);
        boolean hadArmy = playerState.commandedArmyId != null;
        damageCommandedArmy(minute, severity);

        if (severity == DefeatSeverity.ROUTED) {
            int deaths = scatterCompanions(party, minute, 0.25 + margin * 0.35);
            if (deaths > 0) message.append(deaths).append(deaths == 1 ? " companion falls" : " companions fall").append(". ");
            if (hadArmy) message.append("Your army is destroyed. ");
        }

        if (severity == DefeatSeverity.CAPTURED) {
            int days = 4 + random.nextInt(10);
            long ransom = 150L + 75L * Math.max(0, party.memberPersonIds.size() - 1)
                    + Math.max(0L, party.money.copperCoins / 8L);
            playerState.captiveUntilMinute = minute + days * WorldConfig.MINUTES_PER_DAY;
            playerState.captorRealmId = captorRealmId;
            playerState.ransomCoins = ransom;
            player.capturedByRealmId = captorRealmId;
            Realm captor = captorRealmId == null ? null : world.realms.get(captorRealmId);
            message.append("You are taken prisoner by ")
                    .append(captor == null ? "the enemy" : captor.name).append(" for ")
                    .append(days).append(" days unless you pay ").append(ransom).append("c.");
            publish("PLAYER_CAPTURED", minute);
        } else {
            message.append(severity == DefeatSeverity.ROUTED
                    ? "You escape the field wounded and alone."
                    : "You are left wounded on the field but keep your freedom.");
        }
        refreshSnapshot();
        return new CommandResult(true, "DEFEAT_" + severity.name(), message.toString());
    }

    /** Pays the ransom and ends captivity immediately. */
    public CommandResult payRansom() {
        long minute = clock.getWorldMinute();
        if (!playerState.isCaptiveAt(minute)) {
            return CommandResult.rejected("NOT_CAPTIVE", "You are not a prisoner");
        }
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return CommandResult.rejected("INVALID_STATE", "Your party is unavailable");
        if (party.money.copperCoins < playerState.ransomCoins) {
            return CommandResult.rejected("INSUFFICIENT_FUNDS",
                    "The ransom is " + playerState.ransomCoins + "c and you have "
                            + party.money.copperCoins + "c");
        }
        party.money.subtract(playerState.ransomCoins);
        Settlement settlement = world.geography.getSettlement(playerState.currentSettlementId);
        if (settlement != null) settlement.treasury.add(playerState.ransomCoins);
        releaseFromCaptivity(minute);
        refreshSnapshot();
        return new CommandResult(true, "RANSOM_PAID", "You pay the ransom and walk free.");
    }

    /** Releases the prisoner once the captivity term expires. Called every update. */
    private void processCaptivity(long minute) {
        if (playerState.captiveUntilMinute == null) return;
        if (minute < playerState.captiveUntilMinute) return;
        releaseFromCaptivity(minute);
        publish("PLAYER_RELEASED", minute);
    }

    private void releaseFromCaptivity(long minute) {
        playerState.captiveUntilMinute = null;
        playerState.captorRealmId = null;
        playerState.ransomCoins = 0L;
        Person player = world.people.get(playerState.personId);
        if (player != null) player.capturedByRealmId = null;
    }

    /** Rejection used to gate every free-action verb while the adventurer is a prisoner. */
    private CommandResult captivityBlock(long minute) {
        if (!playerState.isCaptiveAt(minute)) return null;
        return CommandResult.rejected("CAPTIVE", "You are a prisoner for another "
                + playerState.captiveDaysRemaining(minute) + " day(s) — pay the ransom of "
                + playerState.ransomCoins + "c to go free");
    }

    /** Rests at the settlement inn: costs coin, advances the clock, and heals wounds. */
    public CommandResult restAtInn(int hours) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        Person player = world.people.get(playerState.personId);
        Household household = world.households.get(playerState.householdId);
        if (player == null || household == null) {
            return CommandResult.rejected("INVALID_STATE", "There is nowhere for you to rest");
        }
        if (world.geography.getSettlement(playerState.currentSettlementId) == null) {
            return CommandResult.rejected("NOT_PRESENT", "You must be inside a settlement to rest");
        }
        int nights = Math.max(1, hours / 8);
        long cost = 5L * nights;
        if (household.account.copperCoins < cost) {
            return CommandResult.rejected("CANNOT_AFFORD", "A room costs " + cost + "c and you have "
                    + household.account.copperCoins + "c");
        }
        household.account.subtract(cost);
        Settlement settlement = world.geography.getSettlement(playerState.currentSettlementId);
        if (settlement != null) settlement.treasury.add(cost);

        boolean paused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes((long) nights * 8L * 60L);
        clock.setPaused(paused);

        player.health.healthLevel = Math.min(100.0, player.health.healthLevel + 30.0 * nights);
        player.health.clamp();
        if (player.health.healthLevel >= 70.0) {
            player.health.wounded = false;
            player.health.injured = false;
            player.health.injurySeverity = Math.min(player.health.injurySeverity, 10.0);
        }
        refreshSnapshot();
        return new CommandResult(true, "RESTED", "You rest " + (nights * 8) + " hours for " + cost
                + "c. Health " + Math.round(player.health.healthLevel) + "%.");
    }

    /**
     * Presses the adventurer's claim to the throne of a realm they serve as a lord.
     * Requires either a legal claim on the crown or a realm in crisis that trusts them.
     */
    public CommandResult seizeThrone(long realmId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        Realm realm = world.realms.get(realmId);
        Person player = world.people.get(playerState.personId);
        if (realm == null || player == null) {
            return CommandResult.rejected("INVALID_REALM", "Kingdom or adventurer not found");
        }
        if (realm.rulerPersonId != null && realm.rulerPersonId == playerState.personId) {
            return CommandResult.rejected("ALREADY_RULER", "You already rule this kingdom");
        }
        if (playerState.affiliatedRealmId == null || playerState.affiliatedRealmId != realmId) {
            return CommandResult.rejected("NOT_A_MEMBER", "Pledge allegiance to this kingdom first");
        }
        if (playerState.kingdomRole != PlayerCampaignState.KingdomRole.LORD) {
            return CommandResult.rejected("NOT_A_LORD",
                    "Only a lord of the realm can press a claim to its throne");
        }
        world.politics.Title crown = world.titles.get(realm.rulerTitleId);
        boolean hasClaim = crown != null && world.claims.values().stream()
                .anyMatch(claim -> claim.titleId == crown.id
                        && claim.claimantPersonId == playerState.personId
                        && (claim.expiryMinute == null || claim.expiryMinute > minute));
        Person currentRuler = realm.rulerPersonId == null ? null : world.people.get(realm.rulerPersonId);
        boolean realmInCrisis = currentRuler == null || !currentRuler.alive || realm.stability < 25.0;
        int reputation = playerState.realmReputation.getOrDefault(realmId, 0);
        if (!hasClaim && !(realmInCrisis && reputation >= 25)) {
            return CommandResult.rejected("NO_BASIS", hasClaim ? ""
                    : "You need a claim on the crown, or a kingdom in crisis that trusts you"
                            + " (stability below 25 and 25+ realm reputation; you have " + reputation + ")");
        }

        Long deposedId = realm.rulerPersonId;
        CommandResult installed = appointPlayerAsKing(realmId);
        if (!installed.accepted) return installed;

        realm.stability = Math.max(0.0, realm.stability - 20.0);
        realm.legitimacy = Math.max(0.0, realm.legitimacy - (hasClaim ? 5.0 : 20.0));
        // A deposed ruler keeps a claim, so succession and factions can contest the new reign.
        if (deposedId != null && deposedId != playerState.personId && crown != null) {
            Person deposed = world.people.get(deposedId);
            if (deposed != null && deposed.alive) {
                world.politics.Claim counter = new world.politics.Claim(world.idGenerator.next(),
                        deposedId, crown.id, hasClaim ? 45.0 : 70.0,
                        Claim.ClaimSource.DISPUTED_SUCCESSION, minute);
                world.claims.put(counter.id, counter);
                crown.claimIds.add(counter.id);
            }
        }
        publish(hasClaim ? "THRONE_CLAIMED" : "THRONE_USURPED", minute);
        refreshSnapshot();
        return new CommandResult(true, "THRONE_TAKEN", player.givenName + " takes the throne of "
                + realm.name + (hasClaim ? " by right of claim." : " by force. Legitimacy suffers."));
    }

    private void woundPlayer(Person player, double resultingHealth) {
        player.health.healthLevel = Math.min(player.health.healthLevel, resultingHealth);
        player.health.clamp();
        player.health.wounded = true;
        player.health.injured = true;
        player.health.injurySeverity = Math.max(player.health.injurySeverity,
                100.0 - player.health.healthLevel);
    }

    private long takeCoins(WorldParty party, double fraction) {
        long available = Math.max(0L, party.money.copperCoins);
        long lost = Math.min(available, Math.round(available * fraction));
        if (lost > 0) party.money.subtract(lost);
        return lost;
    }

    private int takeCargo(WorldParty party, double fraction) {
        int total = 0;
        for (GoodType good : GoodType.values()) {
            int held = party.cargo.getQuantity(good);
            int lost = Math.min(held, (int) Math.round(held * fraction));
            if (lost <= 0) continue;
            if (party.cargo.remove(good, lost)) total += lost;
        }
        return total;
    }

    private void damageCommandedArmy(long minute, DefeatSeverity severity) {
        if (playerState.commandedArmyId == null) return;
        Army army = world.armies.get(playerState.commandedArmyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return;
        }
        if (severity == DefeatSeverity.ROUTED) {
            simulation.getMilitarySystem().demobilize(army.id, minute);
            playerState.commandedArmyId = null;
            return;
        }
        army.morale = Math.max(0.0, army.morale - (severity == DefeatSeverity.CAPTURED ? 45.0 : 20.0));
        army.fatigue = Math.min(100.0, army.fatigue + (severity == DefeatSeverity.CAPTURED ? 40.0 : 25.0));
        if (army.homeSettlementId == null || army.homeSettlementId == army.currentSettlementId) {
            army.state = Army.ArmyState.MUSTERED;
            army.order = Army.ArmyOrder.DEFEND;
            return;
        }
        // Limp home; MilitarySystem.arrive() demobilizes a RETURN_HOME army that reaches home.
        simulation.getMilitarySystem().issueMoveOrder(army.id, army.homeSettlementId,
                Army.ArmyOrder.RETURN_HOME, minute);
    }

    /** Kills a random share of companions after a rout and returns how many died. */
    private int scatterCompanions(WorldParty party, long minute, double deathChance) {
        SeededRandom random = simulation.getContext().getRandom("MILITARY");
        int deaths = 0;
        for (Long memberId : new ArrayList<>(party.memberPersonIds)) {
            if (memberId == playerState.personId) continue;
            Person companion = world.people.get(memberId);
            if (companion == null || !companion.alive) continue;
            if (random.nextDouble() >= deathChance) continue;
            companion.die(minute, Person.DeathCause.WAR);
            companion.regimentId = null;
            companion.travelingPartyId = null;
            party.memberPersonIds.remove(memberId);
            deaths++;
            publish("COMPANION_KILLED", minute);
        }
        return deaths;
    }

    private void publish(String type, long minute) {
        simulation.getContext().eventBus.publish(
                new world.event.WorldEvent(world.idGenerator.next(), minute, type));
    }

    // ===== Army command ==================================================

    /** Realm name lookup for player-facing messages. */
    private String realmNameOf(Long realmId) {
        if (realmId == null) return "no realm";
        Realm realm = world.realms.get(realmId);
        return realm == null ? "an unknown realm" : realm.name;
    }

    /**
     * Raises a company of your own, or adds to the one you already command. Paid from your
     * own purse at 35c a head plus one weapon each, so it needs no conscription law and no
     * realm treasury.
     */
    public CommandResult raiseCompany(int count, world.military.Regiment.RegimentType type) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return CommandResult.rejected("INVALID_STATE", "Your party is unavailable");
        Long settlementId = playerState.currentSettlementId;
        if (settlementId == null) return CommandResult.rejected("NOT_PRESENT", "Enter a settlement first");
        Army previous = playerState.commandedArmyId == null
                ? null : world.armies.get(playerState.commandedArmyId);
        CommandResult raised = simulation.getMilitarySystem().raisePersonalCompany(
                playerState.personId, settlementId, count, type, party.money, minute);
        if (!raised.accepted) return raised;
        Army army = simulation.getMilitarySystem().armyCommandedBy(playerState.personId);
        if (army == null) return CommandResult.rejected("INVALID_STATE", "The company could not be formed");
        if (previous != null && previous.id != army.id && previous.state != Army.ArmyState.DISBANDED) {
            // Fold the old company in so the player always commands exactly one army.
            for (Long regimentId : new ArrayList<>(previous.regimentIds)) {
                previous.regimentIds.remove(regimentId);
                army.regimentIds.add(regimentId);
            }
            for (GoodType good : GoodType.values()) {
                int quantity = previous.supplies.getQuantity(good);
                if (quantity > 0 && previous.supplies.remove(good, quantity)) army.supplies.add(good, quantity);
            }
            previous.playerCommanded = false;
            previous.playerFunded = false;
            previous.state = Army.ArmyState.DISBANDED;
        }
        playerState.commandedArmyId = army.id;
        refreshSnapshot();
        return new CommandResult(true, raised.reasonCode, raised.message
                + " Your company: " + simulation.getMilitarySystem().strength(army) + " troops, "
                + Math.round(army.supplies.getQuantity(GoodType.GRAIN)) + " grain.");
    }

    /** A ruler may take personal command of one of their own realm's armies. */
    public CommandResult takeCommandOfArmy(long armyId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        Army army = world.armies.get(armyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "That army is not active");
        }
        Realm realm = effectivePlayerRealm();
        boolean isRuler = realm != null && realm.rulerPersonId != null
                && realm.rulerPersonId == playerState.personId;
        if (!isRuler) {
            return CommandResult.rejected("NOT_RULER",
                    "Only a ruler takes personal command of a realm army. Raise your own company instead.");
        }
        if (army.realmId != realm.id) {
            return CommandResult.rejected("FOREIGN_ARMY", "That army serves " + realmNameOf(army.realmId));
        }
        if (playerState.commandedArmyId != null && playerState.commandedArmyId != armyId) {
            Army current = world.armies.get(playerState.commandedArmyId);
            if (current != null && current.state != Army.ArmyState.DISBANDED) {
                return CommandResult.rejected("ALREADY_COMMANDING",
                        current.playerFunded
                                ? "Release or disband your own company before taking command of a realm army"
                                : "You already command another army");
            }
        }
        army.commanderPersonId = playerState.personId;
        army.playerCommanded = true;
        // A realm army stays on the realm's payroll; only a private company costs you.
        army.playerFunded = false;
        playerState.commandedArmyId = army.id;
        publish("PLAYER_TOOK_COMMAND", minute);
        refreshSnapshot();
        return new CommandResult(true, "COMMAND_TAKEN", "You take command of " + realm.name
                + " army #" + army.id + " (" + simulation.getMilitarySystem().strength(army) + " troops).");
    }

    /** Gives up command: a private company is paid off, a realm army returns to its officers. */
    public CommandResult relinquishCommand() {
        long minute = clock.getWorldMinute();
        if (playerState.commandedArmyId == null) {
            return CommandResult.rejected("NO_ARMY", "You command no army");
        }
        Army army = world.armies.get(playerState.commandedArmyId);
        playerState.commandedArmyId = null;
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            refreshSnapshot();
            return new CommandResult(true, "COMMAND_RELEASED", "You no longer command an army.");
        }
        boolean wasFunded = army.playerFunded;
        army.playerCommanded = false;
        army.playerFunded = false;
        if (wasFunded) {
            simulation.getMilitarySystem().demobilize(army.id, minute);
            refreshSnapshot();
            return new CommandResult(true, "COMPANY_DISBANDED",
                    "Your company is paid off and the men go home.");
        }
        if (!army.regimentIds.isEmpty()) {
            world.military.Regiment regiment = world.regiments.get(army.regimentIds.get(0));
            if (regiment != null && regiment.officerPersonId != null) {
                army.commanderPersonId = regiment.officerPersonId;
            }
        }
        refreshSnapshot();
        return new CommandResult(true, "COMMAND_RELEASED",
                "Command returns to the army's own officers.");
    }

    /** Orders the army you command to a settlement. BESIEGE and RAID are validated first. */
    public CommandResult issueArmyOrder(long destinationSettlementId, Army.ArmyOrder order) {
        long minute = clock.getWorldMinute();
        if (playerState.commandedArmyId == null) {
            return CommandResult.rejected("NO_ARMY", "You command no army. Raise a company first.");
        }
        Army army = world.armies.get(playerState.commandedArmyId);
        if (army == null || army.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return CommandResult.rejected("INVALID_ARMY", "Your army no longer exists");
        }
        Settlement destination = world.geography.getSettlement(destinationSettlementId);
        if (destination == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        if (order == Army.ArmyOrder.BESIEGE) {
            String obstacle = siegeObstacle(army, destination);
            if (obstacle != null) return CommandResult.rejected("NO_SIEGE", obstacle);
        }
        if (order == Army.ArmyOrder.RAID && destination.controllerRealmId != null
                && destination.controllerRealmId == army.realmId) {
            return CommandResult.rejected("FRIENDLY_RAID", "You cannot raid your own realm's settlement");
        }
        CommandResult issued = simulation.getMilitarySystem()
                .issueMoveOrder(army.id, destinationSettlementId, order, minute);
        if (!issued.accepted) return issued;
        refreshSnapshot();
        return new CommandResult(true, "ORDER_ISSUED", describeOrder(order, destination));
    }

    private String describeOrder(Army.ArmyOrder order, Settlement destination) {
        switch (order) {
            case BESIEGE:
                return "Your army marches to besiege " + destination.name
                        + ". The siege starts when it arrives.";
            case RAID:
                return "Your army marches to raid " + destination.name
                        + ". Raiding provokes any army it meets.";
            case DEFEND:
                return "Your army marches to defend " + destination.name + ".";
            case PATROL:
                return "Your army patrols toward " + destination.name + ".";
            case RESUPPLY:
                return "Your army marches to resupply at " + destination.name + ".";
            case RETURN_HOME:
                return "Your army marches home. It disbands when it arrives.";
            default:
                return "Your army marches to " + destination.name + " (" + order + ").";
        }
    }

    /** Why this army cannot besiege that settlement, or null when it can. */
    private String siegeObstacle(Army army, Settlement target) {
        if (target.controllerRealmId == null) {
            return "That settlement answers to no realm, so there is nothing to besiege";
        }
        if (target.controllerRealmId == army.realmId) {
            return "That settlement belongs to your own realm";
        }
        if (army.realmId < 0L) {
            return "A stateless company cannot besiege. Pledge to a realm that is at war, or found your own.";
        }
        for (War war : world.wars.values()) {
            if (war.state != War.WarState.ACTIVE) continue;
            if (war.attackerRealmIds.contains(army.realmId)
                    && war.defenderRealmIds.contains(target.controllerRealmId)) return null;
        }
        return "Your realm is not at war with " + realmNameOf(target.controllerRealmId)
                + ". A ruler can declare war from the REALM tab.";
    }

    /** Checks whether your army can attack a target army; GamePanel then builds the battle. */
    public CommandResult canEngageArmy(long targetArmyId) {
        if (playerState.commandedArmyId == null) {
            return CommandResult.rejected("NO_ARMY", "You command no army. Raise a company first.");
        }
        Army mine = world.armies.get(playerState.commandedArmyId);
        Army target = world.armies.get(targetArmyId);
        if (mine == null || mine.state == Army.ArmyState.DISBANDED) {
            playerState.commandedArmyId = null;
            return CommandResult.rejected("INVALID_ARMY", "Your army no longer exists");
        }
        if (target == null || target.state == Army.ArmyState.DISBANDED) {
            return CommandResult.rejected("INVALID_ARMY", "That army is no longer active");
        }
        if (target.id == mine.id) return CommandResult.rejected("INVALID_ARMY", "That is your own army");
        if (mine.state == Army.ArmyState.ENGAGED || target.state == Army.ArmyState.ENGAGED) {
            return CommandResult.rejected("ALREADY_ENGAGED", "One of those armies is already fighting");
        }
        if (target.realmId == mine.realmId) {
            return CommandResult.rejected("NOT_HOSTILE", "That army serves the same realm as yours");
        }
        if (simulation.getMilitarySystem().strength(target) <= 0) {
            return CommandResult.rejected("NO_TROOPS", "That army has no troops left");
        }
        if (mine.position == null || target.position == null) {
            return CommandResult.rejected("OUT_OF_RANGE", "Positions are not resolved yet");
        }
        double distance = Math.hypot(mine.position.x - target.position.x, mine.position.y - target.position.y);
        if (distance > 12.0) {
            return CommandResult.rejected("OUT_OF_RANGE", "That army is " + Math.round(distance)
                    + " away; you can engage within 12. March closer first.");
        }
        return CommandResult.accepted();
    }

    /** Storms the walls of an active siege your army is part of. */
    public CommandResult assaultSiege(long siegeId) {
        long minute = clock.getWorldMinute();
        world.military.Siege siege = world.sieges.get(siegeId);
        if (siege == null || siege.state != Siege.SiegeState.ACTIVE) {
            return CommandResult.rejected("INVALID_SIEGE", "There is no active siege to assault");
        }
        Army mine = playerState.commandedArmyId == null
                ? null : world.armies.get(playerState.commandedArmyId);
        boolean playerLed = mine != null && siege.besiegingArmyId == mine.id;
        boolean relieving = mine != null && mine.state != Army.ArmyState.DISBANDED
                && mine.currentSettlementId == siege.settlementId;
        if (!playerLed && !relieving) {
            return CommandResult.rejected("NOT_YOUR_SIEGE",
                    "Only the besieging army can storm these walls. March your army there first,"
                            + " or engage the besiegers in the field to relieve the siege.");
        }
        CommandResult result = simulation.getWarSystem().assaultSiege(siegeId, playerLed, minute);
        if (playerLed && result.accepted && "SIEGE_ASSAULT_CAPTURED".equals(result.reasonCode)) {
            // You took the place yourself, so you may keep it.
            playerState.conquestContribution += 35.0;
        }
        refreshSnapshot();
        return result;
    }

    /** Realms a ruling player could legally declare war on right now. */
    public java.util.List<Long> declarableWarTargets() {
        java.util.List<Long> targets = new java.util.ArrayList<>();
        Realm realm = effectivePlayerRealm();
        if (realm == null || realm.rulerPersonId == null || realm.rulerPersonId != playerState.personId) {
            return targets;
        }
        for (Realm other : world.realms.values()) {
            if (other.id == realm.id) continue;
            boolean atWar = false;
            for (War war : world.wars.values()) {
                if (war.state != War.WarState.ACTIVE) continue;
                if ((war.attackerRealmIds.contains(realm.id) && war.defenderRealmIds.contains(other.id))
                        || (war.attackerRealmIds.contains(other.id) && war.defenderRealmIds.contains(realm.id))) {
                    atWar = true;
                    break;
                }
            }
            if (atWar) continue;
            for (world.geography.Province province : world.geography.getProvinces().values()) {
                if (province.controllerRealmId != null && province.controllerRealmId == other.id) {
                    targets.add(other.id);
                    break;
                }
            }
        }
        targets.sort(Long::compareTo);
        return targets;
    }

    /**
     * A ruler declares war for their realm, using the same legal goals as the AI. A conquest
     * war without a named province takes the enemy's first held province as its goal.
     */
    public CommandResult declareWarAsRuler(long defenderRealmId, War.WarGoal goal,
            Long targetProvinceId, Long targetTitleId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        Realm realm = effectivePlayerRealm();
        if (realm == null || realm.rulerPersonId == null || realm.rulerPersonId != playerState.personId) {
            return CommandResult.rejected("NOT_RULER", "Only a ruler can declare war");
        }
        if (goal == War.WarGoal.CONQUER_PROVINCE && targetProvinceId == null) {
            for (world.geography.Province province : world.geography.getProvinces().values()) {
                if (province.controllerRealmId != null && province.controllerRealmId == defenderRealmId) {
                    targetProvinceId = province.id;
                    break;
                }
            }
            if (targetProvinceId == null) {
                return CommandResult.rejected("NO_TARGET", realmNameOf(defenderRealmId) + " holds no province to conquer");
            }
        }
        CommandResult declared = simulation.getWarSystem().declareWar(
                realm.id, defenderRealmId, goal, targetProvinceId, targetTitleId, minute);
        refreshSnapshot();
        return declared;
    }

    // ===== Fiefs by conquest ==============================================

    /** What a title pays its holder each day, out of the settlement's own treasury. */
    public static long dailyRent(world.politics.Title title, Settlement settlement) {
        if (title == null || settlement == null) return 0L;
        long base = title.rank == Title.Rank.BARONY ? 5L : 12L;
        double prosperity = 1.0 + Math.max(0.0, Math.min(1.5, settlement.prosperity));
        double calm = 1.0 - Math.min(0.8, Math.max(0.0, settlement.unrest));
        return Math.max(1L, Math.round(base * prosperity * calm));
    }

    private long lastHoldingIncomeMinute = Long.MIN_VALUE / 4;

    /** Land pays. Every title you hold draws a daily rent share from its settlement. */
    private void processHoldingIncome(long minute) {
        if (playerState.titleIds.isEmpty()) return;
        if (minute - lastHoldingIncomeMinute < WorldConfig.MINUTES_PER_DAY) return;
        lastHoldingIncomeMinute = minute;
        WorldParty party = world.parties.get(playerState.partyId);
        if (party == null) return;
        int lost = 0;
        for (Long titleId : new ArrayList<>(playerState.titleIds)) {
            world.politics.Title title = world.titles.get(titleId);
            if (title == null || title.holderPersonId == null || title.holderPersonId != playerState.personId) {
                // Taken from you, inherited away, or revoked: stop counting it.
                playerState.titleIds.remove(titleId);
                lost++;
                continue;
            }
            Settlement settlement = title.settlementId == null
                    ? null : world.geography.getSettlement(title.settlementId);
            if (settlement == null) continue;
            long rent = dailyRent(title, settlement);
            long paid = Math.min(rent, settlement.treasury.copperCoins);
            // Rent is quiet income; the FIEFS tab shows the rate so it is never a mystery.
            if (paid > 0 && settlement.treasury.subtract(paid)) party.money.add(paid);
        }
        if (lost > 0) publish("HOLDING_LOST", minute);
    }

    /** Records that you fought a strategic battle, so conquest can be earned from it. */
    public void notePlayerBattleResult(boolean playerHeldField) {
        if (playerHeldField) playerState.conquestContribution += 25.0;
    }

    /**
     * Claims a settlement your realm occupies as your personal holding. Occupation is not
     * ownership: this is the step that turns it into a title, and it costs you - the realm
     * you dispossessed gets a grievance and its ruler a claim on your new land, so they can
     * legally come after it. Requires that you won the place yourself.
     */
    public CommandResult claimHolding(long settlementId) {
        long minute = clock.getWorldMinute();
        CommandResult blocked = captivityBlock(minute);
        if (blocked != null) return blocked;
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement == null) return CommandResult.rejected("INVALID_LOCATION", "Settlement not found");
        Realm realm = effectivePlayerRealm();
        if (realm == null) {
            return CommandResult.rejected("NO_REALM",
                    "Land is held under a realm. Pledge to one first: right-click its settlement.");
        }
        if (settlement.occupyingRealmId == null || settlement.occupyingRealmId != realm.id) {
            return CommandResult.rejected("NOT_OCCUPIED", settlement.name + " is not occupied by " + realm.name
                    + ". Win the siege for it first - and claim it before peace gives it back.");
        }
        boolean county = settlement.type == Settlement.SettlementType.CITY
                || settlement.type == Settlement.SettlementType.FORT
                || settlement.type == Settlement.SettlementType.CAPITAL;
        double needed = county ? 40.0 : 25.0;
        if (playerState.conquestContribution < needed) {
            return CommandResult.rejected("NOT_YOUR_CONQUEST", "You did not win " + settlement.name
                    + " yourself: " + Math.round(playerState.conquestContribution) + " of "
                    + Math.round(needed) + " contribution. Storm its siege (+35) or win its battle (+25).");
        }
        Long dispossessed = settlement.controllerRealmId;
        world.politics.Title title = new world.politics.Title(world.idGenerator.next(),
                (county ? "County of " : "Barony of ") + settlement.name,
                county ? Title.Rank.COUNTY : Title.Rank.BARONY, realm.id);
        title.deJureProvinceId = settlement.provinceId;
        title.settlementId = settlement.id;
        title.installHolder(playerState.personId, minute, "CONQUEST");
        world.titles.put(title.id, title);
        playerState.titleIds.add(title.id);
        playerState.conquestContribution -= needed;

        Person adventurer = world.people.get(playerState.personId);
        if (adventurer != null) adventurer.type = Person.PersonType.NOBLE;
        if (playerState.kingdomRole == PlayerCampaignState.KingdomRole.NONE
                || playerState.kingdomRole == PlayerCampaignState.KingdomRole.MERCENARY) {
            playerState.kingdomRole = PlayerCampaignState.KingdomRole.LORD;
        }

        settlement.controllerRealmId = realm.id;
        settlement.occupyingRealmId = null;
        world.geography.Province province = world.geography.getProvince(settlement.provinceId);
        if (province != null) {
            boolean wholeProvince = true;
            for (Long otherId : province.settlementIds) {
                Settlement other = world.geography.getSettlement(otherId);
                if (other == null) continue;
                if (other.controllerRealmId == null || other.controllerRealmId != realm.id) {
                    wholeProvince = false;
                    break;
                }
            }
            if (wholeProvince) {
                province.controllerRealmId = realm.id;
                province.occupyingRealmId = null;
            }
        }

        // The diplomatic cost of keeping conquered land.
        if (dispossessed != null && dispossessed != realm.id && world.realms.containsKey(dispossessed)) {
            world.diplomacy.Grievance grievance = new world.diplomacy.Grievance(world.idGenerator.next(),
                    dispossessed, realm.id, Grievance.GrievanceType.BORDER_DISPUTE,
                    county ? 70 : 45, minute, minute + 360L * WorldConfig.MINUTES_PER_DAY);
            world.grievances.put(grievance.id, grievance);
            Realm loser = world.realms.get(dispossessed);
            if (loser != null && loser.rulerPersonId != null) {
                world.politics.Claim counter = new world.politics.Claim(world.idGenerator.next(),
                        loser.rulerPersonId, title.id, county ? 70.0 : 55.0,
                        Claim.ClaimSource.DISPUTED_SUCCESSION, minute);
                world.claims.put(counter.id, counter);
                title.claimIds.add(counter.id);
            }
        }
        simulation.getContext().eventBus.publish(new world.event.WorldEvent(world.idGenerator.next(), minute, "HOLDING_CLAIMED",
                (adventurer == null ? "The adventurer" : adventurer.givenName + " " + adventurer.familyName)
                        + " is granted " + title.name + " by conquest under " + realm.name));
        refreshSnapshot();
        return new CommandResult(true, "HOLDING_CLAIMED", title.name + " is yours, held under " + realm.name
                + ". It pays about " + dailyRent(title, settlement) + "c a day"
                + (dispossessed != null && dispossessed != realm.id
                        ? ", and " + realmNameOf(dispossessed) + " now has a grievance and a claim against you."
                        : "."));
    }

    private Realm effectivePlayerRealm() {
        for (Realm realm : world.realms.values()) {
            if (realm.rulerPersonId != null && realm.rulerPersonId == playerState.personId) return realm;
        }
        return playerState.affiliatedRealmId == null ? null : world.realms.get(playerState.affiliatedRealmId);
    }

    public CommandResult acceptContract(long contractId) {
        CommandResult blocked = captivityBlock(clock.getWorldMinute());
        if (blocked != null) return blocked;
        CommandResult result = contractSystem.accept(contractId, playerState, clock.getWorldMinute());
        refreshSnapshot();
        return result;
    }

    public java.util.List<Contract> getAvailableContractsAt(long settlementId) {
        java.util.List<Contract> result = new java.util.ArrayList<>();
        for (Contract contract : world.contracts.values()) {
            if (contract.issuerSettlementId == settlementId
                    && contract.status == Contract.ContractStatus.OPEN) result.add(contract);
        }
        result.sort(java.util.Comparator.comparingLong(contract -> contract.id));
        return result;
    }

    public LocalPlayerState enterLocalScene() {
        return enterLocalScene(1600, 1200);
    }

    public LocalPlayerState enterLocalScene(int localWidth, int localHeight) {
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) party.state = WorldParty.PartyState.LOCAL_SCENE;
        localScene = new world.local.LocalSceneSimulation(world, playerState.currentSettlementId,
                localWidth, localHeight);
        Person person = world.people.get(playerState.personId);
        return new LocalPlayerState(person == null ? 100 : (int) Math.round(person.health.healthLevel),
                playerState.currentSettlementId);
    }

    public void updateLocalScene(double seconds, int localWidth, int localHeight) {
        if (localScene != null) localScene.update(seconds, clock.getWorldMinute(), localWidth, localHeight);
    }

    public java.util.List<world.local.LocalActor> getLocalActors() {
        return localScene == null ? java.util.List.of() : localScene.getActors();
    }

    public void leaveLocalScene(LocalPlayerState local) {
        Person person = world.people.get(playerState.personId);
        if (person != null && local != null) {
            person.health.healthLevel = local.health;
            person.health.clamp();
            if (person.health.healthLevel <= 0.0 && person.alive) person.die(clock.getWorldMinute(), Person.DeathCause.WAR);
        }
        WorldParty party = world.parties.get(playerState.partyId);
        if (party != null) party.state = WorldParty.PartyState.AT_SETTLEMENT;
        localScene = null;
        refreshSnapshot();
    }

    public PlayerCampaignState getPlayerState() {
        return playerState;
    }

    public CheatService getCheats() {
        return cheatService;
    }

    public void refreshAfterCheat() {
        world.indexes.rebuild(world, clock.getWorldMinute());
        world.geography.getSpatialIndex().rebuildDynamic(world);
        refreshSnapshot();
    }

    public void setSpeed(int speed) {
        clock.setSpeed(speed);
        refreshSnapshot();
    }

    public void toggleWorldPaused() {
        clock.setPaused(!clock.isPaused());
        refreshSnapshot();
    }

    public void setWorldPaused(boolean paused) {
        clock.setPaused(paused);
        refreshSnapshot();
    }

    public void advanceOneDayForTesting() {
        advanceDaysForTesting(1);
    }

    public void advanceDaysForTesting(int days) {
        if (days <= 0) return;
        boolean wasPaused = clock.isPaused();
        clock.setPaused(false);
        simulation.advanceMinutes((long) days * WorldConfig.MINUTES_PER_DAY);
        contractSystem.processDeadlines(playerState, clock.getWorldMinute());
        clock.setPaused(wasPaused);
        refreshSnapshot();
    }

    public CampaignSnapshot getSnapshot() {
        return snapshot;
    }

    public WorldSimulation getSimulation() {
        return simulation;
    }

    public WorldState getWorld() {
        return world;
    }

    public long getSeed() {
        return seed;
    }

    public void save(java.nio.file.Path path) throws java.io.IOException {
        CampaignSaveCodec.save(this, path);
    }

    public static CampaignSession load(java.nio.file.Path path) throws java.io.IOException {
        return CampaignSaveCodec.load(path);
    }

    /** Called by the versioned loader after canonical registries have been restored. */
    public void rebuildDerivedStateAfterLoad() {
        world.indexes.rebuild(world, clock.getWorldMinute());
        world.geography.getSpatialIndex().rebuildSettlements(world.geography.getSettlements().values());
        world.geography.getSpatialIndex().rebuildDynamic(world);
        new WorldInvariantValidator().validate(world).throwIfInvalid();
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        snapshot = CampaignSnapshot.capture(simulation);
        lastSnapshotMinute = clock.getWorldMinute();
    }
}
