package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import world.politics.Government;
import world.politics.PoliticalFaction;
import world.politics.Title;

class PoliticsSimulationTest {

    @Test
    void campaignGeneratesPoliticalInstitutions() {
        CampaignSession session = new CampaignSession(12345L);
        WorldState world = session.getWorld();

        assertEquals(2, world.governments.size());
        assertEquals(2, world.titles.size());
        assertEquals(10, world.offices.size());
        assertEquals(4, world.politicalFactions.size());
        assertEquals(2, world.claims.size());
        for (Realm realm : world.realms.values()) {
            assertNotNull(realm.rulerPersonId);
            assertNotNull(world.people.get(realm.rulerPersonId));
            assertNotNull(world.titles.get(realm.rulerTitleId));
            assertNotNull(world.governments.get(realm.governmentId));
        }
    }

    @Test
    void deadRulerTriggersLawfulSuccession() {
        CampaignSession session = new CampaignSession(12345L);
        WorldState world = session.getWorld();
        Realm realm = world.realms.values().iterator().next();
        long oldRulerId = realm.rulerPersonId;
        Person oldRuler = world.people.get(oldRulerId);
        oldRuler.die(session.getSnapshot().worldMinute, Person.DeathCause.OTHER);

        session.getSimulation().advanceMinutes(WorldConfig.MINUTES_PER_MONTH);

        assertNotNull(realm.rulerPersonId);
        assertNotEquals(oldRulerId, realm.rulerPersonId.longValue());
        assertTrue(world.people.get(realm.rulerPersonId).alive);
        Title title = world.titles.get(realm.rulerTitleId);
        assertEquals(realm.rulerPersonId, title.holderPersonId);
        assertEquals(2, title.holderHistory.size());
    }

    @Test
    void taxLawChangesRealmRevenueAndFactionPressure() {
        CampaignSession lowTax = new CampaignSession(9001L);
        CampaignSession highTax = new CampaignSession(9001L);
        Realm lowRealm = lowTax.getWorld().realms.values().iterator().next();
        Realm highRealm = highTax.getWorld().realms.get(lowRealm.id);
        Government lowGovernment = lowTax.getWorld().governments.get(lowRealm.governmentId);
        Government highGovernment = highTax.getWorld().governments.get(highRealm.governmentId);
        lowGovernment.setLawLevel(Government.LawType.TAXATION, 0);
        highGovernment.setLawLevel(Government.LawType.TAXATION, 3);

        lowTax.getSimulation().advanceMinutes(WorldConfig.MINUTES_PER_MONTH);
        highTax.getSimulation().advanceMinutes(WorldConfig.MINUTES_PER_MONTH);

        assertTrue(highRealm.treasury.copperCoins >= lowRealm.treasury.copperCoins);
        double lowPressure = strongestLowerTaxFaction(lowTax.getWorld(), lowRealm.id);
        double highPressure = strongestLowerTaxFaction(highTax.getWorld(), highRealm.id);
        assertTrue(highPressure > lowPressure);
    }

    private double strongestLowerTaxFaction(WorldState world, long realmId) {
        return world.politicalFactions.values().stream()
                .filter(faction -> faction.realmId == realmId
                        && faction.goal == PoliticalFaction.FactionGoal.LOWER_TAXES)
                .mapToDouble(faction -> faction.support)
                .max().orElse(0.0);
    }
}
