package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import world.economy.GoodType;
import world.politics.Government;

class CampaignSessionTest {

    @Test
    void campaignGeneratesVisibleVerticalSlice() {
        CampaignSession session = new CampaignSession(12345L);
        CampaignSnapshot snapshot = session.getSnapshot();

        assertNotNull(snapshot);
        assertEquals(301, snapshot.livingPopulation);
        assertEquals(6, snapshot.settlements.size());
        assertEquals(2, snapshot.realms.size());
        assertFalse(snapshot.roads.isEmpty());
        assertTrue(snapshot.householdCount > 0);
    }

    @Test
    void campaignTimeControlsAdvanceAndPauseWorld() {
        CampaignSession session = new CampaignSession(12345L);
        long initialMinute = session.getSnapshot().worldMinute;

        session.advanceOneDayForTesting();
        assertEquals(initialMinute + WorldConfig.MINUTES_PER_DAY,
                session.getSnapshot().worldMinute);

        session.setWorldPaused(true);
        long pausedMinute = session.getSnapshot().worldMinute;
        session.update(10.0);
        assertEquals(pausedMinute, session.getSnapshot().worldMinute);
        assertTrue(session.getSnapshot().paused);
    }

    @Test
    void kingdomAffiliationAppearsAndOnlyTheKingCanManageLaws() {
        CampaignSession session = new CampaignSession(2468L);
        Realm realm = session.getWorld().realms.values().stream().findFirst().orElseThrow();

        assertTrue(session.joinKingdom(realm.id, PlayerCampaignState.KingdomRole.MERCENARY).accepted);
        assertEquals(realm.id, session.getSnapshot().player.affiliatedRealmId);
        assertEquals("MERCENARY", session.getSnapshot().player.kingdomRole);
        assertFalse(session.getSnapshot().player.canManageKingdom);
        assertFalse(session.adjustKingdomLaw(
                Government.LawType.TAXATION, 1).accepted);

        assertTrue(session.appointPlayerAsKing(realm.id).accepted);
        int before = session.getSnapshot().findRealm(realm.id).laws.get("TAXATION");
        assertTrue(session.adjustKingdomLaw(
                Government.LawType.TAXATION, 1).accepted);
        assertTrue(session.getSnapshot().player.canManageKingdom);
        assertEquals(Math.min(3, before + 1),
                session.getSnapshot().findRealm(realm.id).laws.get("TAXATION"));
    }

    @Test
    void localPeopleSupportConversationGiftsAndRecruitment() {
        CampaignSession session = new CampaignSession(12345L);
        long settlementId = session.getPlayerState().currentSettlementId;
        Person person = session.getWorld().people.values().stream()
                .filter(candidate -> candidate.id != session.getPlayerState().personId)
                .filter(candidate -> candidate.currentSettlementId != null
                        && candidate.currentSettlementId == settlementId)
                .filter(candidate -> !candidate.isChild(session.getSnapshot().worldMinute))
                .filter(candidate -> candidate.type != Person.PersonType.NOBLE
                        && candidate.type != Person.PersonType.SOLDIER)
                .filter(candidate -> candidate.travelingPartyId == null)
                .findFirst().orElseThrow();

        double socialBefore = person.needs.socialBelonging;
        assertTrue(session.talkToPerson(person.id).accepted);
        assertTrue(person.needs.socialBelonging >= socialBefore);

        session.getPlayerState().cargo.add(GoodType.GRAIN, 1);
        double foodBefore = person.needs.foodSecurity;
        assertTrue(session.giveFoodToPerson(person.id).accepted);
        assertTrue(person.needs.foodSecurity >= foodBefore);
        assertEquals(0, session.getPlayerState().cargo.getQuantity(GoodType.GRAIN));
        assertTrue(session.getSnapshot().player.reputation > 0);

        session.enterLocalScene();
        assertTrue(session.recruitCompanion(person.id).accepted);
        session.updateLocalScene(0.1, 1600, 1200);
        assertTrue(session.getSnapshot().player.partyMemberIds.contains(person.id));
        assertTrue(session.getLocalActors().stream()
                .anyMatch(actor -> actor.sourceId == person.id && actor.companion));
        assertFalse(session.recruitCompanion(person.id).accepted);
    }

    @Test
    void playerCanSupplyAnArmyOnlyAtItsCurrentSettlement() {
        CampaignSession session = new CampaignSession(12345L);
        Army army = session.getWorld().armies.values().stream()
                .filter(candidate -> candidate.state != Army.ArmyState.DISBANDED)
                .findFirst().orElseThrow();
        assertTrue(session.travelPlayerTo(army.currentSettlementId).accepted);
        session.getPlayerState().cargo.add(GoodType.GRAIN, 5);
        int before = army.supplies.getQuantity(GoodType.GRAIN);

        assertTrue(session.supplyArmy(army.id, 5).accepted);
        assertEquals(before + 5, army.supplies.getQuantity(GoodType.GRAIN));
        assertEquals(0, session.getPlayerState().cargo.getQuantity(GoodType.GRAIN));
    }

    @Test
    void generationIsDeterministicForSameSeed() {
        CampaignSession first = new CampaignSession(777L);
        CampaignSession second = new CampaignSession(777L);

        CampaignSnapshot a = first.getSnapshot();
        CampaignSnapshot b = second.getSnapshot();
        assertEquals(a.livingPopulation, b.livingPopulation);
        assertEquals(a.householdCount, b.householdCount);
        assertEquals(a.settlements.size(), b.settlements.size());

        for (int i = 0; i < a.settlements.size(); i++) {
            CampaignSnapshot.SettlementView left = a.settlements.get(i);
            CampaignSnapshot.SettlementView right = b.settlements.get(i);
            assertEquals(left.name, right.name);
            assertEquals(left.treasury, right.treasury);
            assertEquals(left.population, right.population);
            assertEquals(left.grain, right.grain);
        }
    }
}
