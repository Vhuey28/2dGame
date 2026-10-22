package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import world.command.CommandResult;
import world.economy.GoodType;
import world.economy.Inventory;
import world.event.WorldEvent;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.politics.KingdomStrategySystem;
import world.politics.Title;
import world.save.CampaignSaveCodec;

class LivingWorldPassTest {

    @Test
    void settlementPressureExplainsCrisisAndWeeklyFeedbackMovesPersistentIndicators() {
        CampaignSession session = new CampaignSession(20261022L);
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();
        Settlement settlement = sortedSettlements(world).stream()
                .filter(candidate -> !householdsAt(world, candidate.id, minute).isEmpty())
                .findFirst().orElseThrow();

        SettlementPressure before = SettlementPressure.evaluate(world, settlement, minute);
        assertNotNull(before);
        makeSettlementCrisis(world, settlement, minute);
        settlement.prosperity = 1.0;
        settlement.sanitation = 1.0;
        settlement.security = 1.0;
        settlement.unrest = 0.95;

        SettlementPressure crisis = SettlementPressure.evaluate(world, settlement, minute);
        assertNotNull(crisis);
        assertEquals(0.05, crisis.averageFoodSecurity, 0.000001);
        assertEquals(0.0, crisis.foodReserveDays, 0.000001);
        assertTrue(crisis.foodStress > before.foodStress);
        assertTrue(crisis.crimeIncidents30Days >= 30);
        assertTrue(crisis.crimeStress > before.crimeStress);
        assertTrue(crisis.jobStress >= 0.75);
        assertTrue(crisis.overallPressure > before.overallPressure + 0.20);
        assertTrue(crisis.loyalty < before.loyalty);
        assertTrue(crisis.migrationPressure > before.migrationPressure);
        assertEquals(SettlementPressure.Outlook.CRISIS, crisis.outlook);
        assertFalse(crisis.mainPressure.isBlank());
        assertFalse(crisis.summary.isBlank());
        assertTrue(crisis.reason.contains("loyalty"));
        assertFinite01(crisis.targetProsperity);
        assertFinite01(crisis.targetSanitation);
        assertFinite01(crisis.targetSecurity);
        assertFinite01(crisis.targetUnrest);

        double prosperityBeforeWeek = settlement.prosperity;
        double unrestBeforeWeek = settlement.unrest;
        session.getSimulation().getSettlementPressureSystem().processWeek(minute, false);
        assertTrue(settlement.prosperity < prosperityBeforeWeek,
                "weekly pressure feedback should move an unrealistic prosperity value down");
        assertTrue(settlement.unrest <= unrestBeforeWeek,
                "unrest approaches its derived target rather than exceeding the prior value");
        assertFinite01(settlement.prosperity);
        assertFinite01(settlement.sanitation);
        assertFinite01(settlement.security);
        assertFinite01(settlement.unrest);
    }

    @Test
    void weeklyCivicSpendingUsesSettlementTreasuryAndResidentNeedsFollowLocalConditions() {
        CampaignSession session = new CampaignSession(20261023L);
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();
        Settlement settlement = sortedSettlements(world).stream()
                .filter(candidate -> !world.indexes.livingPeopleAt(candidate.id).isEmpty())
                .findFirst().orElseThrow();
        settlement.treasury.copperCoins = 10_000L;
        settlement.security = 0.0;
        settlement.sanitation = 0.0;

        Person resident = world.people.get(world.indexes.livingPeopleAt(settlement.id).get(0));
        resident.needs.safety = 1.0;
        resident.needs.politicalSatisfaction = 0.0;

        session.getSimulation().getSettlementPressureSystem().processMonth(minute);

        assertTrue(settlement.treasury.copperCoins < 10_000L,
                "a funded settlement should spend from its own treasury on watch/sanitation gaps");
        assertTrue(settlement.security > 0.0 || settlement.sanitation > 0.0);
        assertTrue(resident.needs.safety < 1.0,
                "resident safety should move toward the settlement's actual security");
        assertTrue(resident.needs.politicalSatisfaction > 0.0,
                "resident political satisfaction should move toward derived loyalty");
    }

    @Test
    void personGoalsPrioritizeRecoveryFoodWorkAndServiceAndMemoriesClampOpinion() {
        CampaignSession session = new CampaignSession(20261024L);
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();
        Person person = world.people.values().stream()
                .filter(candidate -> candidate.alive)
                .filter(candidate -> candidate.id != session.getPlayerState().personId)
                .filter(candidate -> !candidate.isChild(minute) && !candidate.isElderly(minute))
                .filter(candidate -> candidate.type != Person.PersonType.CHILD)
                .filter(candidate -> candidate.currentSettlementId != null)
                .filter(candidate -> candidate.householdId != null)
                .findFirst().orElseThrow();
        Household household = world.households.get(person.householdId);
        assertNotNull(household);
        PersonGoalSystem goals = session.getSimulation().getPersonGoalSystem();
        person.type = Person.PersonType.CITIZEN;
        person.regimentId = null;
        person.travelingPartyId = null;

        person.health.healthLevel = 20.0;
        person.health.wounded = false;
        person.health.injured = false;
        person.health.sick = false;
        goals.refresh(person, (SettlementPressure) null, minute);
        assertEquals(Person.Goal.RECOVER, person.currentGoal);
        assertEquals(94, person.goalUrgency);
        assertEquals(minute, person.goalUpdatedMinute);
        assertFalse(person.goalReason.isBlank());

        person.health.healthLevel = 100.0;
        person.needs.foodSecurity = 0.10;
        household.foodSecurity = 0.10;
        goals.refresh(person, (SettlementPressure) null, minute);
        assertEquals(Person.Goal.FIND_FOOD, person.currentGoal);
        assertTrue(person.goalUrgency >= 88);
        assertTrue(person.goalReason.contains("food"));

        person.type = Person.PersonType.CITIZEN;
        person.regimentId = null;
        person.employerId = null;
        person.officeId = null;
        person.travelingPartyId = null;
        person.needs.foodSecurity = 0.95;
        household.foodSecurity = 0.95;
        goals.refresh(person, (SettlementPressure) null, minute);
        assertEquals(Person.Goal.FIND_WORK, person.currentGoal);
        assertTrue(person.goalReason.contains("No employer"));

        person.type = Person.PersonType.SOLDIER;
        goals.refresh(person, (SettlementPressure) null, minute);
        assertEquals(Person.Goal.SERVE_REALM, person.currentGoal);

        person.opinionOfPlayer = 0;
        person.rememberPlayerInteraction(250, "You saved their household", minute);
        assertEquals(100, person.opinionOfPlayer);
        assertEquals("You saved their household", person.lastPlayerMemory);
        person.rememberPlayerInteraction(-500, "You robbed them", minute);
        assertEquals(-100, person.opinionOfPlayer);
        person.rememberLifeEvent("Moved after a robbery", minute);
        assertEquals("Moved after a robbery", person.lastLifeEvent);
    }

    @Test
    void kingdomStrategyConsolidatesAroundTheWorstSettlementWithAReason() {
        CampaignSession session = new CampaignSession(20261025L);
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();

        for (Realm realm : world.realms.values()) {
            assertNotNull(realm.strategy);
            assertFalse(realm.strategyReason == null || realm.strategyReason.isBlank());
            if (realm.strategyTargetSettlementId != null) {
                assertNotNull(world.geography.getSettlement(realm.strategyTargetSettlementId));
            }
        }

        Realm realm = world.realms.values().stream()
                .min(Comparator.comparingLong(candidate -> candidate.id)).orElseThrow();
        List<Settlement> controlled = sortedSettlements(world).stream()
                .filter(candidate -> candidate.controllerRealmId != null
                        && candidate.controllerRealmId.longValue() == realm.id)
                .toList();
        assertFalse(controlled.isEmpty());
        Settlement worst = controlled.get(controlled.size() - 1);
        for (Settlement settlement : controlled) {
            if (settlement.id == worst.id) makeSettlementCrisis(world, settlement, minute);
            else makeSettlementHealthy(world, settlement, minute);
        }
        realm.treasury.copperCoins = 10_000L;

        session.getSimulation().getKingdomStrategySystem().evaluate(realm, minute);

        assertEquals(Realm.Strategy.CONSOLIDATE, realm.strategy);
        assertEquals(worst.id, realm.strategyTargetSettlementId.longValue());
        assertTrue(realm.strategyReason.contains(worst.name));
        assertEquals(minute, realm.strategyUpdatedMinute);
    }

    @Test
    void warObjectiveScoringPrefersReachableTargetsUntilLegalClaimsChangeTheScore() {
        WorldState world = new WorldState();
        Realm attacker = new Realm(10L, "Attacker");
        Realm defender = new Realm(20L, "Defender");
        world.realms.put(attacker.id, attacker);
        world.realms.put(defender.id, defender);

        Settlement capital = new Settlement(1L, "Attacker Capital", 1L,
                new WorldPosition(0.0, 0.0), Settlement.SettlementType.CAPITAL);
        capital.controllerRealmId = attacker.id;
        Settlement near = new Settlement(2L, "Near Border", 2L,
                new WorldPosition(10.0, 0.0), Settlement.SettlementType.TOWN);
        near.controllerRealmId = defender.id;
        near.security = 0.0;
        Settlement claimed = new Settlement(3L, "Claimed City", 3L,
                new WorldPosition(250.0, 0.0), Settlement.SettlementType.CITY);
        claimed.controllerRealmId = defender.id;
        claimed.security = 1.0;
        attacker.capitalSettlementId = capital.id;
        defender.capitalSettlementId = near.id;
        world.geography.addSettlement(capital);
        world.geography.addSettlement(near);
        world.geography.addSettlement(claimed);
        world.indexes.rebuild(world, 0L);

        War war = new War(99L, attacker.id, defender.id, War.WarGoal.CONQUER_PROVINCE, 0L);
        assertEquals(near.id, KingdomStrategySystem.chooseWarObjective(
                world, null, war, attacker.id, null).id);

        Title title = new Title(77L, "Claim to Claimed City", Title.Rank.COUNTY, attacker.id);
        title.settlementId = claimed.id;
        title.deJureProvinceId = claimed.provinceId;
        world.titles.put(title.id, title);
        war.goal = War.WarGoal.CLAIM_TITLE;
        war.targetProvinceId = claimed.provinceId;
        war.targetTitleId = title.id;

        assertEquals(claimed.id, KingdomStrategySystem.chooseWarObjective(
                world, null, war, attacker.id, null).id);
        assertEquals(near.id, KingdomStrategySystem.chooseWarObjective(
                world, null, war, attacker.id, claimed.id).id);
    }

    @Test
    void pressureCreatesFundedDeduplicatedReliefContractAndCompletionDistributesFood() {
        CampaignSession session = new CampaignSession(20261026L);
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();
        PlayerCampaignState player = session.getPlayerState();
        Settlement issuer = world.geography.getSettlement(player.currentSettlementId);
        assertNotNull(issuer);
        Settlement destination = sortedSettlements(world).stream()
                .filter(candidate -> candidate.id != issuer.id)
                .filter(candidate -> !householdsAt(world, candidate.id, minute).isEmpty())
                .findFirst().orElseThrow();

        for (Contract existing : world.contracts.values()) {
            existing.status = Contract.ContractStatus.EXPIRED;
        }
        for (Settlement settlement : sortedSettlements(world)) {
            clearFood(settlement.publicStockpile);
            for (Household household : householdsAt(world, settlement.id, minute)) {
                clearFood(household.inventory);
                household.foodSecurity = settlement.id == destination.id ? 0.05 : 0.95;
            }
        }
        int issuerPopulation = Math.max(1, world.indexes.livingPeopleAt(issuer.id).size());
        issuer.publicStockpile.add(GoodType.GRAIN, issuerPopulation * 6);
        issuer.publicStockpile.add(GoodType.VEGETABLES, issuerPopulation * 6);
        issuer.treasury.copperCoins = 10_000L;

        ContractSystem contracts = session.getSimulation().getContractSystem();
        assertTrue(contracts.generateDynamicOffers(minute) >= 1);
        Contract relief = world.contracts.values().stream()
                .filter(candidate -> candidate.status == Contract.ContractStatus.OPEN)
                .filter(candidate -> candidate.type == Contract.ContractType.DELIVER_GOODS)
                .filter(candidate -> candidate.issuerSettlementId == issuer.id)
                .filter(candidate -> candidate.destinationSettlementId == destination.id)
                .filter(candidate -> candidate.requiredGood == GoodType.GRAIN)
                .findFirst().orElseThrow();
        assertTrue(relief.issueReason.contains(destination.name));
        assertTrue(relief.issueReason.contains("short of grain"));
        assertTrue(relief.urgency >= 85);
        assertTrue(relief.rewardCoins > 0L);

        contracts.generateDynamicOffers(minute);
        long matching = world.contracts.values().stream()
                .filter(candidate -> candidate.status == Contract.ContractStatus.OPEN
                        || candidate.status == Contract.ContractStatus.ACTIVE)
                .filter(candidate -> candidate.type == relief.type)
                .filter(candidate -> candidate.issuerSettlementId == relief.issuerSettlementId)
                .filter(candidate -> candidate.destinationSettlementId == relief.destinationSettlementId)
                .filter(candidate -> candidate.requiredGood == relief.requiredGood)
                .count();
        assertEquals(1L, matching, "the same pressure problem must not duplicate the same offer");

        Realm issuerRealm = issuer.controllerRealmId == null
                ? null : world.realms.get(issuer.controllerRealmId);
        long issuerCoins = issuer.treasury.copperCoins;
        long realmCoins = issuerRealm == null ? 0L : issuerRealm.treasury.copperCoins;
        issuer.treasury.copperCoins = 0L;
        if (issuerRealm != null) issuerRealm.treasury.copperCoins = 0L;
        CommandResult unfunded = session.acceptContract(relief.id);
        assertFalse(unfunded.accepted);
        assertEquals("UNFUNDED", unfunded.reasonCode);
        assertEquals(Contract.ContractStatus.OPEN, relief.status);
        assertEquals(0L, relief.escrowCoins);

        issuer.treasury.copperCoins = issuerCoins;
        if (issuerRealm != null) issuerRealm.treasury.copperCoins = realmCoins;
        assertTrue(session.acceptContract(relief.id).accepted);
        assertEquals(Contract.ContractStatus.ACTIVE, relief.status);
        assertEquals(relief.rewardCoins, relief.escrowCoins);
        assertEquals(issuerCoins - relief.rewardCoins, issuer.treasury.copperCoins);

        player.cargo.add(GoodType.GRAIN, relief.requiredQuantity);
        WorldParty party = world.parties.get(player.partyId);
        long moneyBefore = party.money.copperCoins;
        assertTrue(session.travelPlayerTo(destination.id).accepted);

        assertEquals(Contract.ContractStatus.COMPLETED, relief.status);
        assertEquals(0L, relief.escrowCoins);
        assertTrue(party.money.copperCoins > moneyBefore);
        assertTrue(totalGrain(world, destination, minute) >= relief.requiredQuantity);
        assertTrue(householdGrain(world, destination, minute) > 0,
                "half of a food-relief delivery should enter needy households, not only the town stockpile");
        assertTrue(householdsAt(world, destination.id, minute).stream()
                .map(household -> household.headPersonId == null
                        ? null : world.people.get(household.headPersonId))
                .anyMatch(head -> head != null && head.opinionOfPlayer > 0
                        && head.lastPlayerMemory.contains("relief food")));
    }

    @Test
    void snapshotExposesPressureGoalsMemoriesContractsCrimesWarsAndBoundedChronicle() {
        CampaignSession session = new CampaignSession(20261027L);
        WorldState world = session.getWorld();
        long minute = session.getSimulation().getClock().getWorldMinute();
        List<Person> localPeople = world.people.values().stream()
                .filter(person -> person.alive)
                .filter(person -> person.id != session.getPlayerState().personId)
                .filter(person -> person.currentSettlementId != null)
                .sorted(Comparator.comparingLong(person -> person.id))
                .limit(2).toList();
        assertEquals(2, localPeople.size());
        Person remembered = localPeople.get(0);
        Person victim = localPeople.get(1);
        remembered.currentGoal = Person.Goal.FIND_WORK;
        remembered.goalReason = "No employer after the mill closed";
        remembered.goalUrgency = 77;
        remembered.rememberPlayerInteraction(19, "You brought food during the shortage", minute);
        remembered.rememberLifeEvent("Lost work when the mill closed", minute);

        CrimeIncident hidden = new CrimeIncident(world.idGenerator.next(),
                remembered.currentSettlementId, remembered.id, victim.id,
                CrimeIncident.CrimeType.THEFT, minute, false, 4);
        CrimeIncident caught = new CrimeIncident(world.idGenerator.next(),
                remembered.currentSettlementId, remembered.id, victim.id,
                CrimeIncident.CrimeType.ASSAULT, minute, true, 6);
        world.crimeIncidents.put(hidden.id, hidden);
        world.crimeIncidents.put(caught.id, caught);

        List<Realm> realms = world.realms.values().stream()
                .sorted(Comparator.comparingLong(realm -> realm.id)).toList();
        War war = new War(world.idGenerator.next(), realms.get(0).id, realms.get(1).id,
                War.WarGoal.CLAIM_TITLE, minute);
        war.attackerScore = 80.0;
        war.defenderScore = 10.0;
        war.attackerExhaustion = 20.0;
        war.defenderExhaustion = 35.0;
        war.declarationReason = "A documented title claim backed by an unpaid grievance";
        world.wars.put(war.id, war);

        session.getSimulation().getContext().eventHistory.clear();
        for (int i = 0; i < 45; i++) {
            session.getSimulation().getContext().eventHistory.record(
                    new WorldEvent(10_000L + i, minute, "REALM_STRATEGY_CHANGED", "history-" + i));
        }

        CampaignSnapshot snapshot = CampaignSnapshot.capture(session.getSimulation());
        assertEquals(12, snapshot.recentEvents.size());
        assertEquals(40, snapshot.chronicleEvents.size());
        assertEquals("history-5", snapshot.chronicleEvents.get(0).detail);
        assertEquals("history-44", snapshot.chronicleEvents.get(snapshot.chronicleEvents.size() - 1).detail);

        for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
            assertFinite01(settlement.loyalty);
            assertFinite01(settlement.unemploymentRate);
            assertFinite01(settlement.taxBurdenRate);
            assertFinite01(settlement.overallPressure);
            assertTrue(Double.isFinite(settlement.foodReserveDays));
            assertFalse(settlement.outlook.isBlank());
            assertFalse(settlement.mainPressure.isBlank());
            assertFalse(settlement.pressureSummary.isBlank());
            assertFalse(settlement.pressureReason.isBlank());
        }
        for (CampaignSnapshot.RealmView realm : snapshot.realms) {
            assertFalse(realm.strategy.isBlank());
            assertFalse(realm.strategyReason.isBlank());
            assertFinite01(realm.averageLoyalty);
            assertFinite01(realm.worstPressure);
            assertFalse(realm.worstSettlementName.isBlank());
            assertNotNull(realm.strongestFactionReason);
        }
        CampaignSnapshot.PersonView personView = snapshot.people.stream()
                .filter(candidate -> candidate.id == remembered.id).findFirst().orElseThrow();
        assertEquals("FIND_WORK", personView.goal);
        assertEquals("No employer after the mill closed", personView.goalReason);
        assertEquals(77, personView.goalUrgency);
        assertEquals(19, personView.opinionOfPlayer);
        assertEquals("You brought food during the shortage", personView.lastPlayerMemory);
        assertEquals("Lost work when the mill closed", personView.lastLifeEvent);
        assertFinite01(personView.householdFoodSecurity);

        assertFalse(snapshot.contracts.isEmpty());
        for (CampaignSnapshot.ContractView contract : snapshot.contracts) {
            assertFalse(contract.issueReason == null || contract.issueReason.isBlank());
            assertTrue(contract.urgency >= 0 && contract.urgency <= 100);
        }
        CampaignSnapshot.CrimeView hiddenView = snapshot.crimes.stream()
                .filter(candidate -> candidate.id == hidden.id).findFirst().orElseThrow();
        CampaignSnapshot.CrimeView caughtView = snapshot.crimes.stream()
                .filter(candidate -> candidate.id == caught.id).findFirst().orElseThrow();
        assertEquals("Unknown offender", hiddenView.offenderName);
        assertEquals(remembered.givenName + " " + remembered.familyName, caughtView.offenderName);
        assertEquals(victim.givenName + " " + victim.familyName, caughtView.victimName);
        assertFalse(caughtView.settlementName.isBlank());

        CampaignSnapshot.WarView warView = snapshot.wars.stream()
                .filter(candidate -> candidate.id == war.id).findFirst().orElseThrow();
        assertEquals(war.declarationReason, warView.declarationReason);
        assertEquals("Attackers pressing", warView.phase);
        assertTrue(warView.momentumExplanation.contains("attackers lead by 70"));
        Settlement defenderCapital = world.geography.getSettlement(realms.get(1).capitalSettlementId);
        assertEquals(defenderCapital.name, warView.objectiveSettlementName);
    }

    @Test
    void saveVersionSixPersistsContractReasonsAndSparsePlayerMemoriesAndRebuildsPlans(
            @TempDir Path directory) throws Exception {
        CampaignSession session = new CampaignSession(20261028L);
        WorldState world = session.getWorld();
        Person remembered = world.people.values().stream()
                .filter(person -> person.alive && person.id != session.getPlayerState().personId)
                .min(Comparator.comparingLong(person -> person.id)).orElseThrow();
        remembered.opinionOfPlayer = 0;
        remembered.rememberPlayerInteraction(37, "You escorted their family through danger", 0L);
        Contract contract = world.contracts.values().stream()
                .min(Comparator.comparingLong(candidate -> candidate.id)).orElseThrow();
        contract.issueReason = "A named shortage created this exact request";
        contract.urgency = 140;

        Path save = directory.resolve("living-world.ccq6");
        CampaignSaveCodec.save(session, save);
        try (DataInputStream in = new DataInputStream(Files.newInputStream(save))) {
            in.readInt();
            assertEquals(6, in.readInt());
        }

        CampaignSession loaded = CampaignSaveCodec.load(save);
        Person loadedPerson = loaded.getWorld().people.get(remembered.id);
        Contract loadedContract = loaded.getWorld().contracts.get(contract.id);
        assertEquals(37, loadedPerson.opinionOfPlayer);
        assertEquals("You escorted their family through danger", loadedPerson.lastPlayerMemory);
        assertEquals(0L, loadedPerson.lastPlayerMemoryMinute);
        assertEquals("A named shortage created this exact request", loadedContract.issueReason);
        assertEquals(100, loadedContract.urgency);
        assertNotNull(loadedPerson.currentGoal);
        assertFalse(loadedPerson.goalReason.isBlank());
        assertTrue(loadedPerson.goalUpdatedMinute >= 0L);
        for (Realm realm : loaded.getWorld().realms.values()) {
            assertNotNull(realm.strategy);
            assertFalse(realm.strategyReason.isBlank());
            assertTrue(realm.strategyUpdatedMinute >= 0L);
        }
    }

    @Test
    void soakMetricsAreFiniteAndDescribeTheGeneratedLivingWorld() {
        CampaignSession session = new CampaignSession(20261029L);
        SoakRunner.Result result = new SoakRunner().run(session.getSimulation(), 0, 1);
        SoakRunner.Metrics metrics = result.metrics;

        assertEquals(0, result.days);
        assertEquals(session.getSnapshot().livingPopulation, metrics.livingPopulation);
        assertTrue(metrics.occupiedHouseholds > 0);
        assertFinite01(metrics.averageFoodSecurity);
        assertFinite01(metrics.averageSettlementPressure);
        assertFinite01(metrics.worstSettlementPressure);
        assertFinite01(metrics.averageLoyalty);
        assertTrue(metrics.worstSettlementPressure >= metrics.averageSettlementPressure);
        assertTrue(metrics.crisisSettlements >= 0
                && metrics.crisisSettlements <= session.getSnapshot().settlements.size());
        assertEquals(session.getWorld().wars.size(), metrics.totalWars);
        assertEquals(session.getWorld().crimeIncidents.size(), metrics.retainedCrimeIncidents);
        assertEquals(session.getSimulation().getContext().eventHistory.getHistorical().size(),
                metrics.historicalEvents);
        assertTrue(metrics.activeArmies > 0);
        assertFalse(metrics.toString().isBlank());
    }

    private static List<Settlement> sortedSettlements(WorldState world) {
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        return settlements;
    }

    private static List<Household> householdsAt(WorldState world, long settlementId, long minute) {
        world.indexes.ensureBuilt(world, minute);
        List<Household> households = new ArrayList<>();
        for (Long householdId : world.indexes.householdsAt(settlementId)) {
            Household household = world.households.get(householdId);
            if (household != null && household.getMemberCount() > 0) households.add(household);
        }
        return households;
    }

    private static void makeSettlementCrisis(WorldState world, Settlement settlement, long minute) {
        world.indexes.ensureBuilt(world, minute);
        for (Household household : householdsAt(world, settlement.id, minute)) {
            household.foodSecurity = 0.05;
            clearFood(household.inventory);
        }
        List<Long> residents = world.indexes.livingPeopleAt(settlement.id);
        for (Long personId : residents) {
            Person person = world.people.get(personId);
            if (person == null) continue;
            person.employerId = null;
            person.officeId = null;
            person.needs.foodSecurity = 0.05;
        }
        clearFood(settlement.publicStockpile);
        settlement.treasury.copperCoins = 0L;
        settlement.prosperity = 0.05;
        settlement.sanitation = 0.05;
        settlement.security = 0.05;
        settlement.unrest = 0.95;
        if (!residents.isEmpty()) {
            long offenderId = residents.get(0);
            Long victimId = residents.size() > 1 ? residents.get(1) : null;
            for (int i = 0; i < 30; i++) {
                CrimeIncident incident = new CrimeIncident(world.idGenerator.next(), settlement.id,
                        offenderId, victimId, CrimeIncident.CrimeType.THEFT, minute, false, 5);
                world.crimeIncidents.put(incident.id, incident);
            }
        }
    }

    private static void makeSettlementHealthy(WorldState world, Settlement settlement, long minute) {
        world.indexes.ensureBuilt(world, minute);
        int population = Math.max(1, world.indexes.livingPeopleAt(settlement.id).size());
        for (Household household : householdsAt(world, settlement.id, minute)) {
            household.foodSecurity = 0.95;
        }
        settlement.publicStockpile.add(GoodType.GRAIN, population * 8);
        settlement.publicStockpile.add(GoodType.VEGETABLES, population * 8);
        settlement.treasury.copperCoins = 10_000L;
        settlement.prosperity = 0.90;
        settlement.sanitation = 0.90;
        settlement.security = 0.90;
        settlement.unrest = 0.0;
    }

    private static void clearFood(Inventory inventory) {
        inventory.remove(GoodType.GRAIN, inventory.getQuantity(GoodType.GRAIN));
        inventory.remove(GoodType.VEGETABLES, inventory.getQuantity(GoodType.VEGETABLES));
    }

    private static int householdGrain(WorldState world, Settlement settlement, long minute) {
        int total = 0;
        for (Household household : householdsAt(world, settlement.id, minute)) {
            total += household.inventory.getQuantity(GoodType.GRAIN);
        }
        return total;
    }

    private static int totalGrain(WorldState world, Settlement settlement, long minute) {
        return settlement.publicStockpile.getQuantity(GoodType.GRAIN)
                + householdGrain(world, settlement, minute);
    }

    private static void assertFinite01(double value) {
        assertTrue(Double.isFinite(value), "value must be finite");
        assertTrue(value >= 0.0 && value <= 1.0, "value must be in [0,1]: " + value);
    }
}
