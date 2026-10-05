package world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import world.economy.GoodType;
import world.economy.Inventory;
import world.economy.Market;
import world.economy.MarketOrder;
import world.economy.MoneyAccount;
import world.command.CommandResult;
import world.geography.Road;
import world.geography.Settlement;

class TradeSimulationTest {

    @Test
    void generatedCampaignCreatesFundedPhysicalCaravans() {
        CampaignSession session = new CampaignSession(12345L);
        assertEquals(2, session.getWorld().caravans.size());
        for (Caravan caravan : session.getWorld().caravans.values()) {
            assertTrue(caravan.cash.copperCoins >= 0);
            assertTrue(caravan.position != null);
            assertNotEquals(Caravan.CaravanState.DESTROYED, caravan.state);
        }
    }

    @Test
    void routeGraphFindsMultiRoadPath() {
        CampaignSession session = new CampaignSession(12345L);
        List<Settlement> settlements = new java.util.ArrayList<>(
                session.getWorld().geography.getSettlements().values());
        settlements.sort(java.util.Comparator.comparingLong(s -> s.id));
        List<Road> route = session.getWorld().geography.getRouteGraph()
                .findShortestRoute(settlements.get(0).id, settlements.get(settlements.size() - 1).id);
        assertFalse(route.isEmpty());
        assertEquals(settlements.get(0).id, route.get(0).fromSettlementId);
        assertEquals(settlements.get(settlements.size() - 1).id,
                route.get(route.size() - 1).toSettlementId);
    }

    @Test
    void playerCanTravelAndTradeAtSelectedSettlement() {
        CampaignSession session = new CampaignSession(12345L);
        PlayerCampaignState player = session.getPlayerState();
        long origin = player.currentSettlementId;
        CommandResult purchase = session.buyFromSettlement(origin, GoodType.GRAIN, 5);
        assertTrue(purchase.accepted);
        assertTrue(player.cargo.getQuantity(GoodType.GRAIN) > 0);

        Settlement destination = session.getWorld().geography.getSettlements().values().stream()
                .filter(s -> s.id != origin).findFirst().orElseThrow();
        CommandResult travel = session.travelPlayerTo(destination.id);
        assertTrue(travel.accepted);
        assertEquals(destination.id, player.currentSettlementId);
    }

    @Test
    void failedMarketPurchaseDoesNotDuplicateGoods() {
        Market market = new Market(1L);
        Inventory seller = new Inventory();
        Inventory buyer = new Inventory();
        seller.add(GoodType.GRAIN, 10);
        java.util.Map<Long, Inventory> inventories = new java.util.HashMap<>();
        inventories.put(1L, seller);
        inventories.put(2L, buyer);
        java.util.Map<Long, MoneyAccount> accounts = new java.util.HashMap<>();
        accounts.put(1L, new MoneyAccount());
        accounts.put(2L, new MoneyAccount());
        market.addSellOrder(new MarketOrder(1L, GoodType.GRAIN,
                MarketOrder.OrderSide.SELL, 10, 5L, 0L, 100L));
        market.addBuyOrder(new MarketOrder(2L, GoodType.GRAIN,
                MarketOrder.OrderSide.BUY, 10, 5L, 0L, 100L));

        market.clearMarket(1L, inventories, accounts);

        assertEquals(10, seller.getQuantity(GoodType.GRAIN));
        assertEquals(0, buyer.getQuantity(GoodType.GRAIN));
    }

    @Test
    void caravansMoveAndDeliverRealCargo() {
        CampaignSession session = new CampaignSession(12345L);
        Caravan caravan = session.getWorld().caravans.values().iterator().next();
        double initialX = caravan.position.x;
        double initialY = caravan.position.y;

        session.advanceOneDayForTesting();

        assertTrue(caravan.position.x != initialX || caravan.position.y != initialY
                || caravan.completedTrips > 0);
        assertTrue(caravan.completedTrips > 0
                || caravan.state == Caravan.CaravanState.TRAVELING
                || caravan.state == Caravan.CaravanState.SELLING);
    }
}
