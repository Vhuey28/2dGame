package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Settlement;
import world.military.BattleContext;
import world.military.BattleResult;
import world.military.MilitarySystem;
import world.military.Regiment;
import world.military.Siege;
import world.politics.Title;

/**
 * The adventurer's own path through the war: raising a company nobody else may command,
 * storming a siege, and turning conquered land into a personal holding that pays - and is
 * claimed against.
 */
class PlayerCommandAndHoldingsTest {

    private static List<Realm> sortedRealms(WorldState world) {
        List<Realm> realms = new ArrayList<>(world.realms.values());
        realms.sort(Comparator.comparingLong(realm -> realm.id));
        return realms;
    }

    private static List<Army> sortedArmies(WorldState world) {
        List<Army> armies = new ArrayList<>(world.armies.values());
        armies.sort(Comparator.comparingLong(army -> army.id));
        return armies;
    }

    /** The settlement the adventurer starts in, with weapons and coin enough to recruit. */
    private static Settlement preparedHome(CampaignSession session, int weapons, long coins) {
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        if (world.geography.getSettlement(player.currentSettlementId) == null) {
            List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
            settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
            session.travelPlayerTo(settlements.get(0).id);
        }
        Settlement home = world.geography.getSettlement(player.currentSettlementId);
        assertNotNull(home, "the adventurer must start in a settlement");
        home.publicStockpile.add(GoodType.WEAPONS, weapons);
        world.parties.get(player.partyId).money.add(coins);
        return home;
    }

    @Test
    void aPersonalCompanyIsPlayerCommandedAndNeverRequisitionedByRealmAi() {
        CampaignSession session = new CampaignSession(4242L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session, 20, 5000L);

        CommandResult raised = session.raiseCompany(5, Regiment.RegimentType.LEVY_INFANTRY);
        assertTrue(raised.accepted, raised.message);
        assertNotNull(player.commandedArmyId);

        Army company = world.armies.get(player.commandedArmyId);
        assertNotNull(company);
        assertTrue(company.playerCommanded, "the adventurer must command the company");
        assertTrue(company.playerFunded, "a private company is paid from the adventurer's purse");
        assertEquals(player.personId, company.commanderPersonId);
        assertEquals(home.controllerRealmId == null ? -1L : home.controllerRealmId.longValue(), company.realmId);
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        assertEquals(5, military.strength(company));
        assertEquals(company.id, military.armyCommandedBy(player.personId).id);

        // Realm AI picks field armies with the same query the war system uses.
        if (company.realmId >= 0L) {
            Army chosen = session.getSimulation().getWarSystem().strongestArmy(company.realmId);
            assertTrue(chosen == null || chosen.id != company.id,
                    "the realm must never spend the player's own company");
        }
        new WorldInvariantValidator().validate(world).throwIfInvalid();
    }

    @Test
    void raisingACompanyWithoutWeaponsOrCoinIsRefusedNotSilentlyShrunk() {
        CampaignSession session = new CampaignSession(8080L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Settlement home = preparedHome(session, 0, 0L);
        home.publicStockpile.remove(GoodType.WEAPONS, home.publicStockpile.getQuantity(GoodType.WEAPONS));

        CommandResult refused = session.raiseCompany(5, Regiment.RegimentType.ARCHER);
        assertFalse(refused.accepted);
        assertEquals("RECRUITMENT_RESOURCES", refused.reasonCode);
        assertNull(player.commandedArmyId);
        assertTrue(refused.message.contains("weapon"), refused.message);
    }

    @Test
    void conqueredLandBecomesAHoldingOnlyForThePlayerWhoWonIt() {
        CampaignSession session = new CampaignSession(99L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Realm realm = sortedRealms(world).get(0);
        Settlement target = enemySettlement(world, realm);

        session.joinKingdom(realm.id, PlayerCampaignState.KingdomRole.MERCENARY);

        // Occupation alone is not ownership.
        CommandResult notOccupied = session.claimHolding(target.id);
        assertFalse(notOccupied.accepted);
        assertEquals("NOT_OCCUPIED", notOccupied.reasonCode);

        Long dispossessed = target.controllerRealmId;
        target.occupyingRealmId = realm.id;
        CommandResult notYours = session.claimHolding(target.id);
        assertFalse(notYours.accepted);
        assertEquals("NOT_YOUR_CONQUEST", notYours.reasonCode);

        // Winning it yourself is what earns the land.
        player.conquestContribution = 50.0;
        int titlesBefore = world.titles.size();
        CommandResult claimed = session.claimHolding(target.id);
        assertTrue(claimed.accepted, claimed.message);

        assertEquals(titlesBefore + 1, world.titles.size());
        assertEquals(1, player.titleIds.size());
        Title title = world.titles.get(player.titleIds.get(0));
        assertNotNull(title);
        assertEquals(target.id, title.settlementId.longValue());
        assertEquals(target.provinceId, title.deJureProvinceId.longValue());
        assertEquals(realm.id, title.realmId);
        assertEquals(player.personId, title.holderPersonId.longValue());
        assertEquals("CONQUEST", title.holderHistory.get(title.holderHistory.size() - 1).reason);
        boolean county = target.type == Settlement.SettlementType.CITY
                || target.type == Settlement.SettlementType.FORT
                || target.type == Settlement.SettlementType.CAPITAL;
        assertEquals(county ? Title.Rank.COUNTY : Title.Rank.BARONY, title.rank);

        // The conquest is spent, the adventurer is now a lord holding land under the realm.
        assertTrue(player.conquestContribution < 50.0);
        assertEquals(PlayerCampaignState.KingdomRole.LORD, player.kingdomRole);
        assertEquals(realm.id, target.controllerRealmId.longValue());
        assertNull(target.occupyingRealmId);
        assertEquals(Person.PersonType.NOBLE, world.people.get(player.personId).type);

        // And it costs diplomatically: a grievance and a counter-claim from the loser.
        assertTrue(world.grievances.values().stream().anyMatch(grievance ->
                        grievance.offendedRealmId == dispossessed.longValue()
                                && grievance.offenderRealmId == realm.id && !grievance.resolved),
                "the dispossessed realm must have a grievance");
        assertTrue(world.claims.values().stream().anyMatch(claim -> claim.titleId == title.id),
                "the dispossessed ruler must be able to claim the land back");
        assertTrue(CampaignSession.dailyRent(title, target) > 0L);
        new WorldInvariantValidator().validate(world).throwIfInvalid();
    }

    @Test
    void holdingsPayRentIntoTheAdventurersPurse() {
        CampaignSession session = new CampaignSession(1234L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Realm realm = sortedRealms(world).get(0);
        Settlement target = enemySettlement(world, realm);
        session.joinKingdom(realm.id, PlayerCampaignState.KingdomRole.LORD);
        target.occupyingRealmId = realm.id;
        player.conquestContribution = 60.0;
        target.treasury.add(400L);
        assertTrue(session.claimHolding(target.id).accepted);

        Title holding = world.titles.get(player.titleIds.get(0));
        long rent = CampaignSession.dailyRent(holding, target);
        assertTrue(rent > 0L);
        target.treasury.add(400L);
        long purseBefore = world.parties.get(player.partyId).money.copperCoins;

        session.advanceDaysForTesting(2);
        session.update(0.0);

        long purseAfter = world.parties.get(player.partyId).money.copperCoins;
        assertTrue(purseAfter - purseBefore >= rent,
                "land must pay its holder at least its daily rent, paid " + (purseAfter - purseBefore)
                        + "c against " + rent + "c");
        assertEquals(1, player.titleIds.size());
        assertEquals(holding.id, world.titles.get(holding.id).id);
    }

    @Test
    void stormingASiegePushesTheBreachAndCostsTheAssaulters() {
        CampaignSession session = new CampaignSession(7L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        List<Realm> realms = sortedRealms(world);
        Realm attacker = realms.get(0);
        Realm defender = realms.get(1);
        MilitarySystem military = session.getSimulation().getMilitarySystem();
        Army besieger = session.getSimulation().getWarSystem().strongestArmy(attacker.id);
        assertNotNull(besieger, "the generated world must field an army for the attacker");
        Settlement target = defenderSettlement(world, defender);
        long minute = session.getSimulation().getClock().getWorldMinute();

        // A siege the player has no part in cannot be stormed.
        CommandResult noSiege = session.assaultSiege(987654L);
        assertFalse(noSiege.accepted);
        assertEquals("INVALID_SIEGE", noSiege.reasonCode);

        War war = new War(world.idGenerator.next(), attacker.id, defender.id, War.WarGoal.PUNITIVE, minute);
        world.wars.put(war.id, war);
        Siege siege = new Siege(world.idGenerator.next(), war.id, besieger.id, target.id, minute);
        world.sieges.put(siege.id, siege);

        int strengthBefore = military.strength(besieger);
        double resolveBefore = siege.defenderResolve;
        CommandResult stormed = session.getSimulation().getWarSystem()
                .assaultSiege(siege.id, true, minute);
        assertTrue(stormed.accepted, stormed.message);
        assertTrue(siege.breachProgress > 0.0, "an assault must make progress on the breach");
        assertTrue(siege.defenderResolve < resolveBefore);
        assertTrue(military.strength(besieger) < strengthBefore || siege.state != Siege.SiegeState.ACTIVE,
                "storming walls must cost the assaulting army");
        new WorldInvariantValidator().validate(world).throwIfInvalid();
    }

    @Test
    void onlyARulerDeclaresWarAndConquestNeedsALegalTarget() {
        CampaignSession session = new CampaignSession(31337L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        Realm realm = sortedRealms(world).get(0);

        assertTrue(session.declarableWarTargets().isEmpty(), "a landless adventurer declares no wars");
        assertFalse(session.declareWarAsRuler(sortedRealms(world).get(1).id,
                War.WarGoal.CONQUER_PROVINCE, null, null).accepted);

        // Clear any war the generator started so the target list is deterministic.
        for (War war : world.wars.values()) war.state = War.WarState.ENDED;
        session.joinKingdom(realm.id, PlayerCampaignState.KingdomRole.LORD);
        assertTrue(session.appointPlayerAsKing(realm.id).accepted);

        List<Long> targets = session.declarableWarTargets();
        assertFalse(targets.isEmpty(), "a ruler with a rival realm holding land can declare war");
        CommandResult declared = session.declareWarAsRuler(targets.get(0),
                War.WarGoal.CONQUER_PROVINCE, null, null);
        assertTrue(declared.accepted, declared.message);
        assertTrue(world.wars.values().stream().anyMatch(war -> war.state == War.WarState.ACTIVE
                && war.attackerRealmIds.contains(realm.id) && war.defenderRealmIds.contains(targets.get(0))));
    }

    @Test
    void companionsAreProjectedIntoAPlayerLedBattleAndReconcile() {
        CampaignSession session = new CampaignSession(555L);
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        List<Army> armies = sortedArmies(world);
        Army attacker = armies.get(0);
        Army defender = null;
        for (Army army : armies) {
            if (army.realmId != attacker.realmId) { defender = army; break; }
        }
        assertNotNull(defender, "the generated world must have two hostile realms");

        // A companion who belongs to no regiment: the player's own retinue.
        Person companion = world.people.values().stream()
                .filter(person -> person.alive && person.id != player.personId && person.regimentId == null)
                .min(Comparator.comparingLong(person -> person.id)).orElse(null);
        assertNotNull(companion);
        world.parties.get(player.partyId).memberPersonIds.add(companion.id);

        List<Long> companions = new ArrayList<>();
        companions.add(companion.id);
        BattleContext context = session.getSimulation().getBattleBridge().createContext(
                attacker.id, defender.id, true, companions, 100L);
        assertTrue(context.playerInvolved);
        assertTrue(context.attacker.combatants.stream()
                        .anyMatch(combatant -> combatant.sourcePersonId == companion.id),
                "companions fight beside the company");

        Map<Long, BattleResult.CasualtyOutcome> outcomes = new HashMap<>();
        for (BattleContext.TacticalCombatant combatant : context.attacker.combatants) {
            outcomes.put(combatant.sourcePersonId, BattleResult.CasualtyOutcome.UNHARMED);
        }
        for (BattleContext.TacticalCombatant combatant : context.defender.combatants) {
            outcomes.put(combatant.sourcePersonId, BattleResult.CasualtyOutcome.KILLED);
        }
        BattleResult result = session.getSimulation().getBattleBridge()
                .resultFromTactical(context.battleId, BattleResult.WinningSide.ATTACKER, outcomes, 60L);
        assertTrue(session.getSimulation().getBattleBridge().reconcile(result).accepted);
        assertTrue(companion.alive, "an unharmed companion survives the battle");
        new WorldInvariantValidator().validate(world).throwIfInvalid();
    }

    private static Settlement enemySettlement(WorldState world, Realm realm) {
        return world.geography.getSettlements().values().stream()
                .filter(settlement -> settlement.controllerRealmId != null
                        && settlement.controllerRealmId != realm.id)
                .min(Comparator.comparingLong(settlement -> settlement.id))
                .orElseThrow(() -> new IllegalStateException("no enemy settlement was generated"));
    }

    private static Settlement defenderSettlement(WorldState world, Realm defender) {
        return world.geography.getSettlements().values().stream()
                .filter(settlement -> settlement.controllerRealmId != null
                        && settlement.controllerRealmId == defender.id)
                .min(Comparator.comparingLong(settlement -> settlement.id))
                .orElseThrow(() -> new IllegalStateException("the defender holds no settlement"));
    }
}
