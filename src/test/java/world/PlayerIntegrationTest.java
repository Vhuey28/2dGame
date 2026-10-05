package world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import world.command.CommandResult;
import world.economy.GoodType;

class PlayerIntegrationTest {
    @Test
    void playerHasCanonicalWorldPersonPartyMoneyAndCargo() {
        CampaignSession session = new CampaignSession(901L);
        PlayerCampaignState player = session.getPlayerState();
        WorldState world = session.getWorld();
        WorldParty party = world.parties.get(player.partyId);

        assertNotNull(world.people.get(player.personId));
        assertNotNull(party);
        assertSame(party.cargo, player.cargo);
        assertSame(party.money, world.households.get(player.householdId).account);

        long coins = party.money.copperCoins;
        world.geography.getSettlement(player.currentSettlementId).publicStockpile.add(GoodType.GRAIN, 5);
        assertTrue(session.buyFromSettlement(player.currentSettlementId, GoodType.GRAIN, 1).accepted);
        assertEquals(1, party.cargo.getQuantity(GoodType.GRAIN));
        assertTrue(party.money.copperCoins < coins);

        Person companion = world.people.values().stream()
                .filter(person -> person.id != player.personId && person.alive
                        && person.currentSettlementId != null
                        && person.currentSettlementId == player.currentSettlementId)
                .findFirst().orElseThrow();
        assertTrue(session.recruitCompanion(companion.id).accepted);
        assertTrue(party.memberPersonIds.contains(companion.id));
        assertEquals(party.id, companion.travelingPartyId);
    }

    @Test
    void generatedContractCanBeAcceptedAndCompletedByTravel() {
        CampaignSession session = new CampaignSession(902L);
        PlayerCampaignState player = session.getPlayerState();
        List<Contract> offers = session.getAvailableContractsAt(player.currentSettlementId);
        assertFalse(offers.isEmpty());
        Contract contract = offers.get(0);
        CommandResult accepted = session.acceptContract(contract.id);
        assertTrue(accepted.accepted);
        assertEquals(Contract.ContractStatus.ACTIVE, contract.status);

        if (contract.requiredGood != null) {
            session.getWorld().parties.get(player.partyId).cargo.add(
                    contract.requiredGood, contract.requiredQuantity);
        }
        assertTrue(session.travelPlayerTo(contract.destinationSettlementId).accepted);
        assertEquals(Contract.ContractStatus.COMPLETED, contract.status);
        assertTrue(player.reputationAtSettlement(contract.issuerSettlementId) > 0);
        assertFalse(player.acceptedContractIds.contains(contract.id));
    }

    @Test
    void localAdapterRoundTripsHealthWhileWorldTimeContinues() {
        CampaignSession session = new CampaignSession(903L);
        long before = session.getSnapshot().worldMinute;
        LocalPlayerState local = session.enterLocalScene();
        session.update(24.0); // default campaign speed is 60 world minutes per real second
        assertTrue(session.getSnapshot().worldMinute > before);
        local.health = 63;
        session.leaveLocalScene(local);
        assertEquals(63.0, session.getWorld().people.get(session.getPlayerState().personId).health.healthLevel);
        assertEquals(WorldParty.PartyState.AT_SETTLEMENT,
                session.getWorld().parties.get(session.getPlayerState().partyId).state);
    }

    @Test
    void campaignSaveReloadPreservesPlayerTradeTravelContractsAndClock() throws Exception {
        CampaignSession session = new CampaignSession(904L);
        PlayerCampaignState player = session.getPlayerState();
        Contract contract = session.getAvailableContractsAt(player.currentSettlementId).get(0);
        assertTrue(session.acceptContract(contract.id).accepted);
        session.getWorld().parties.get(player.partyId).cargo.add(GoodType.TOOLS, 3);
        long destination = contract.destinationSettlementId;
        session.travelPlayerTo(destination);
        session.advanceOneDayForTesting();

        Path directory = Files.createTempDirectory("campaign-save-test");
        Path save = directory.resolve("campaign.ccq");
        session.save(save);
        CampaignSession loaded = CampaignSession.load(save);

        assertEquals(session.getSnapshot().worldMinute, loaded.getSnapshot().worldMinute);
        assertEquals(destination, loaded.getPlayerState().currentSettlementId);
        assertEquals(3, loaded.getPlayerState().cargo.getQuantity(GoodType.TOOLS));
        assertEquals(session.getWorld().idGenerator.getNextId(), loaded.getWorld().idGenerator.getNextId());
        assertEquals(session.getWorld().contracts.get(contract.id).status,
                loaded.getWorld().contracts.get(contract.id).status);
        assertTrue(new WorldInvariantValidator().validate(loaded.getWorld()).isValid());
    }
}
