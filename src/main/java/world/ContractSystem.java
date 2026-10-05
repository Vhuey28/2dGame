package world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Road;
import world.geography.Settlement;
import world.event.WorldEvent;

/** Generates contracts from world conditions and reconciles them against canonical party state. */
public final class ContractSystem {
    private final SimulationContext context;
    private final WorldState world;

    public ContractSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void generateInitialOffers(long minute) {
        if (!world.contracts.isEmpty()) return;
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(value -> value.id));
        for (int i = 0; i < settlements.size(); i++) {
            Settlement issuer = settlements.get(i);
            Settlement destination = settlements.get((i + 1) % settlements.size());
            List<Road> route = world.geography.getRouteGraph().findShortestRoute(issuer.id, destination.id);
            double danger = route.stream().mapToDouble(road -> road.dangerLevel).average().orElse(0.0);
            Contract.ContractType type;
            GoodType good = null;
            int quantity = 0;
            if (destination.publicStockpile.getQuantity(GoodType.GRAIN) < 100) {
                type = Contract.ContractType.DELIVER_GOODS;
                good = GoodType.GRAIN;
                quantity = 10;
            } else if (danger >= 0.25) {
                type = Contract.ContractType.ESCORT_ROUTE;
            } else {
                type = Contract.ContractType.DELIVER_MESSAGE;
            }
            long reward = 60L + Math.round(route.stream().mapToDouble(Road::getEffectiveCostFactor).sum() / 5.0);
            Contract contract = new Contract(world.idGenerator.next(), type, issuer.id, destination.id,
                    minute, minute + 30L * WorldConfig.MINUTES_PER_DAY, reward, 15L, good, quantity);
            world.contracts.put(contract.id, contract);
        }
    }

    public CommandResult accept(long contractId, PlayerCampaignState player, long minute) {
        Contract contract = world.contracts.get(contractId);
        WorldParty party = world.parties.get(player.partyId);
        Settlement issuer = contract == null ? null : world.geography.getSettlement(contract.issuerSettlementId);
        if (contract == null || party == null || issuer == null) return rejected("INVALID_CONTRACT", "Contract not found");
        if (contract.status != Contract.ContractStatus.OPEN) return rejected("NOT_OPEN", "Contract is no longer open");
        if (player.currentSettlementId != contract.issuerSettlementId) return rejected("NOT_PRESENT", "Visit the issuer first");
        if (minute > contract.deadlineMinute) return rejected("EXPIRED", "The deadline has passed");
        contract.escrowCoins = Math.min(contract.rewardCoins, issuer.treasury.copperCoins);
        issuer.treasury.subtract(contract.escrowCoins);
        contract.status = Contract.ContractStatus.ACTIVE;
        contract.takerPartyId = party.id;
        player.acceptedContractIds.add(contract.id);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_ACCEPTED"));
        return CommandResult.accepted();
    }

    public void onPlayerArrived(PlayerCampaignState player, long minute) {
        WorldParty party = world.parties.get(player.partyId);
        if (party == null) return;
        for (Long id : new ArrayList<>(player.acceptedContractIds)) {
            Contract contract = world.contracts.get(id);
            if (contract == null || contract.status != Contract.ContractStatus.ACTIVE) continue;
            if (minute > contract.deadlineMinute) {
                fail(contract, player, party, minute);
                continue;
            }
            if (player.currentSettlementId != contract.destinationSettlementId) continue;
            if (contract.type == Contract.ContractType.DELIVER_GOODS) {
                if (!party.cargo.remove(contract.requiredGood, contract.requiredQuantity)) continue;
                Settlement destination = world.geography.getSettlement(contract.destinationSettlementId);
                if (destination != null) destination.publicStockpile.add(contract.requiredGood, contract.requiredQuantity);
            }
            complete(contract, player, party, minute);
        }
    }

    public void processDeadlines(PlayerCampaignState player, long minute) {
        WorldParty party = world.parties.get(player.partyId);
        if (party == null) return;
        for (Contract contract : world.contracts.values()) {
            if (contract.status == Contract.ContractStatus.OPEN && minute > contract.deadlineMinute) {
                contract.status = Contract.ContractStatus.EXPIRED;
            } else if (contract.status == Contract.ContractStatus.ACTIVE
                    && contract.takerPartyId != null && contract.takerPartyId == party.id
                    && minute > contract.deadlineMinute) {
                fail(contract, player, party, minute);
            }
        }
    }

    private void complete(Contract contract, PlayerCampaignState player, WorldParty party, long minute) {
        party.money.add(contract.escrowCoins);
        contract.escrowCoins = 0L;
        contract.status = Contract.ContractStatus.COMPLETED;
        contract.resolvedMinute = minute;
        player.acceptedContractIds.remove(Long.valueOf(contract.id));
        player.changeSettlementReputation(contract.issuerSettlementId, 5);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_COMPLETED"));
    }

    private void fail(Contract contract, PlayerCampaignState player, WorldParty party, long minute) {
        Settlement issuer = world.geography.getSettlement(contract.issuerSettlementId);
        if (issuer != null) issuer.treasury.add(contract.escrowCoins);
        contract.escrowCoins = 0L;
        party.money.subtract(Math.min(contract.penaltyCoins, party.money.copperCoins));
        contract.status = Contract.ContractStatus.FAILED;
        contract.resolvedMinute = minute;
        player.acceptedContractIds.remove(Long.valueOf(contract.id));
        player.changeSettlementReputation(contract.issuerSettlementId, -5);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_FAILED"));
    }

    private CommandResult rejected(String code, String message) {
        return CommandResult.rejected(code, message);
    }
}
