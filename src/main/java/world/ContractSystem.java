package world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import world.command.CommandResult;
import world.economy.GoodType;
import world.geography.Road;
import world.geography.Settlement;
import world.event.WorldEvent;

/**
 * Generates contracts from actual settlement pressure and reconciles them
 * against canonical party state. Routine work is posted weekly by the world
 * simulation; CampaignSession only forwards player acceptance and arrival.
 */
public final class ContractSystem {
    private static final int MAX_OPEN_PER_ISSUER = 3;
    private static final int MAX_OPEN_TOTAL = 20;
    private static final int MAX_DYNAMIC_PER_WEEK = 2;

    private final SimulationContext context;
    private final WorldState world;

    public ContractSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void generateInitialOffers(long minute) {
        if (!world.contracts.isEmpty()) return;
        List<Settlement> settlements = sortedSettlements();
        if (settlements.isEmpty()) return;
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        for (int i = 0; i < settlements.size(); i++) {
            Settlement issuer = settlements.get(i);
            Settlement needy = worstFoodDestination(issuer, settlements, pressures);
            if (needy != null) {
                SettlementPressure pressure = pressures.get(needy.id);
                GoodType good = neededFood(pressure);
                int quantity = reliefQuantity(pressure);
                int urgency = urgency(pressure == null ? 0.45 : pressure.foodStress, 48);
                String reason = needy.name + " needs food: households are "
                        + percent(pressure == null ? 0.5 : pressure.averageFoodSecurity)
                        + " food-secure with " + oneDecimal(pressure == null ? 0.0 : pressure.foodReserveDays)
                        + " balanced food-days stored";
                createOffer(Contract.ContractType.DELIVER_GOODS, issuer, needy, good, quantity,
                        urgency, reason, minute, false);
                continue;
            }

            Settlement destination = settlements.get((i + 1) % settlements.size());
            List<Road> route = world.geography.getRouteGraph().findShortestRoute(issuer.id, destination.id);
            double danger = route.stream().mapToDouble(road -> road.dangerLevel).average().orElse(0.0);
            SettlementPressure destinationPressure = pressures.get(destination.id);
            if (destination.publicStockpile.getQuantity(GoodType.GRAIN) < 100) {
                createOffer(Contract.ContractType.DELIVER_GOODS, issuer, destination, GoodType.GRAIN, 10,
                        55, destination.name + " is below its public grain reserve", minute, false);
            } else if (danger >= 0.25 || destinationPressure != null && destinationPressure.crimeStress >= 0.42) {
                createOffer(Contract.ContractType.ESCORT_ROUTE, issuer, destination, null, 0, 58,
                        "Unsafe roads: route danger " + percent(danger) + " and "
                                + (destinationPressure == null ? 0 : destinationPressure.crimeIncidents30Days)
                                + " crimes near " + destination.name + " in 30 days",
                        minute, false);
            } else {
                createOffer(Contract.ContractType.DELIVER_MESSAGE, issuer, destination, null, 0, 42,
                        "Council correspondence must reach " + destination.name, minute, false);
            }
        }
    }

    /** Posts up to two new, funded offers from the world's most urgent current problems. */
    public int generateDynamicOffers(long minute) {
        if (world.contracts.isEmpty()) {
            generateInitialOffers(minute);
            return 0;
        }
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        List<Settlement> settlements = sortedSettlements();
        int created = 0;

        List<Settlement> hungry = new ArrayList<>(settlements);
        hungry.sort((first, second) -> {
            SettlementPressure a = pressures.get(first.id);
            SettlementPressure b = pressures.get(second.id);
            int byStress = Double.compare(b == null ? 0.0 : b.foodStress, a == null ? 0.0 : a.foodStress);
            return byStress != 0 ? byStress : Long.compare(first.id, second.id);
        });
        for (Settlement destination : hungry) {
            if (created >= MAX_DYNAMIC_PER_WEEK) break;
            SettlementPressure pressure = pressures.get(destination.id);
            if (pressure == null || pressure.foodStress < 0.58 && pressure.foodReserveDays >= 2.5) continue;
            GoodType good = neededFood(pressure);
            int quantity = reliefQuantity(pressure);
            Settlement issuer = surplusFoodIssuer(destination, settlements, pressures, good, quantity);
            if (issuer == null) continue;
            if (createOffer(Contract.ContractType.DELIVER_GOODS, issuer, destination, good, quantity,
                    urgency(pressure.foodStress, 58),
                    destination.name + " is short of " + good.name().toLowerCase() + ": households "
                            + percent(pressure.averageFoodSecurity) + " fed, reserves "
                            + oneDecimal(pressure.foodReserveDays) + " days, loyalty "
                            + percent(pressure.loyalty), minute, true)) created++;
        }

        for (Settlement destination : settlements) {
            if (created >= MAX_DYNAMIC_PER_WEEK) break;
            SettlementPressure pressure = pressures.get(destination.id);
            if (pressure == null || pressure.warStress < 0.62 && !pressure.besieged) continue;
            Settlement issuer = friendlyMessageIssuer(destination, settlements, pressures);
            if (issuer == null) continue;
            if (createOffer(Contract.ContractType.DELIVER_MESSAGE, issuer, destination, null, 0,
                    pressure.besieged ? 92 : 76,
                    pressure.besieged ? destination.name + " is besieged and needs orders from its relief command"
                            : destination.name + " faces hostile troops and needs urgent military orders",
                    minute, true)) created++;
        }

        List<Settlement> unsafe = new ArrayList<>(settlements);
        unsafe.sort((first, second) -> {
            SettlementPressure a = pressures.get(first.id);
            SettlementPressure b = pressures.get(second.id);
            int byStress = Double.compare(b == null ? 0.0 : b.crimeStress, a == null ? 0.0 : a.crimeStress);
            return byStress != 0 ? byStress : Long.compare(first.id, second.id);
        });
        for (Settlement issuer : unsafe) {
            if (created >= MAX_DYNAMIC_PER_WEEK) break;
            SettlementPressure pressure = pressures.get(issuer.id);
            if (pressure == null || pressure.crimeStress < 0.50 && pressure.crimeIncidents30Days < 12) continue;
            Settlement destination = nearestOther(issuer, settlements);
            if (destination == null) continue;
            if (createOffer(Contract.ContractType.ESCORT_ROUTE, issuer, destination, null, 0,
                    urgency(pressure.crimeStress, 52),
                    "A tax courier needs an escort out of " + issuer.name + ": "
                            + pressure.crimeIncidents30Days + " crimes in 30 days ("
                            + oneDecimal(pressure.crimePer100Residents30Days) + " per 100 residents)",
                    minute, true)) created++;
        }

        for (Settlement issuer : settlements) {
            if (created >= MAX_DYNAMIC_PER_WEEK) break;
            SettlementPressure pressure = pressures.get(issuer.id);
            if (pressure == null || pressure.loyalty >= 0.38 || issuer.controllerRealmId == null) continue;
            Realm realm = world.realms.get(issuer.controllerRealmId);
            Settlement capital = realm == null || realm.capitalSettlementId == null
                    ? null : world.geography.getSettlement(realm.capitalSettlementId);
            if (capital == null || capital.id == issuer.id) continue;
            if (createOffer(Contract.ContractType.DELIVER_MESSAGE, issuer, capital, null, 0,
                    urgency(pressure.overallPressure, 46),
                    "Elders in " + issuer.name + " petition the court: loyalty "
                            + percent(pressure.loyalty) + "; " + pressure.summary,
                    minute, true)) created++;
        }

        for (Settlement destination : settlements) {
            if (created >= MAX_DYNAMIC_PER_WEEK) break;
            SettlementPressure pressure = pressures.get(destination.id);
            if (pressure == null || pressure.jobStress < 0.58
                    || destination.publicStockpile.getQuantity(GoodType.TOOLS) >= 8) continue;
            Settlement issuer = surplusGoodIssuer(destination, settlements, GoodType.TOOLS, 6);
            if (issuer == null) continue;
            if (createOffer(Contract.ContractType.DELIVER_GOODS, issuer, destination, GoodType.TOOLS, 6,
                    urgency(pressure.jobStress, 44),
                    destination.name + " needs tools to reopen work: unemployment "
                            + percent(pressure.unemploymentRate), minute, true)) created++;
        }
        return created;
    }

    public CommandResult accept(long contractId, PlayerCampaignState player, long minute) {
        Contract contract = world.contracts.get(contractId);
        WorldParty party = player == null ? null : world.parties.get(player.partyId);
        Settlement issuer = contract == null ? null : world.geography.getSettlement(contract.issuerSettlementId);
        if (contract == null || party == null || issuer == null) return rejected("INVALID_CONTRACT", "Contract not found");
        if (contract.status != Contract.ContractStatus.OPEN) return rejected("NOT_OPEN", "Contract is no longer open");
        if (player.currentSettlementId != contract.issuerSettlementId || !player.atSettlement || player.isTraveling()) {
            return rejected("NOT_PRESENT", "Visit the issuer first");
        }
        if (minute > contract.deadlineMinute) return rejected("EXPIRED", "The deadline has passed");

        long fromIssuer = Math.min(contract.rewardCoins, Math.max(0L, issuer.treasury.copperCoins));
        long remaining = contract.rewardCoins - fromIssuer;
        Realm realm = issuer.controllerRealmId == null ? null : world.realms.get(issuer.controllerRealmId);
        if (remaining > 0 && (realm == null || realm.treasury.copperCoins < remaining)) {
            return rejected("UNFUNDED", issuer.name + " cannot escrow the promised reward");
        }
        if (fromIssuer > 0) issuer.treasury.subtract(fromIssuer);
        if (remaining > 0) realm.treasury.subtract(remaining);
        contract.escrowCoins = contract.rewardCoins;
        contract.status = Contract.ContractStatus.ACTIVE;
        contract.takerPartyId = party.id;
        player.acceptedContractIds.add(contract.id);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_ACCEPTED",
                contract.type.name() + " from " + issuer.name + ": " + contract.issueReason));
        return CommandResult.accepted();
    }

    public void onPlayerArrived(PlayerCampaignState player, long minute) {
        WorldParty party = player == null ? null : world.parties.get(player.partyId);
        if (party == null || !player.atSettlement || player.isTraveling()) return;
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
                if (destination != null) {
                    destination.publicStockpile.add(contract.requiredGood, contract.requiredQuantity);
                    if (contract.requiredGood == GoodType.GRAIN || contract.requiredGood == GoodType.VEGETABLES) {
                        distributeRelief(destination, contract, minute);
                    }
                }
            }
            complete(contract, player, party, minute);
        }
    }

    public void processDeadlines(PlayerCampaignState player, long minute) {
        WorldParty party = player == null ? null : world.parties.get(player.partyId);
        for (Contract contract : new ArrayList<>(world.contracts.values())) {
            if (contract.status == Contract.ContractStatus.OPEN && minute > contract.deadlineMinute) {
                contract.status = Contract.ContractStatus.EXPIRED;
                contract.resolvedMinute = minute;
                if (contract.urgency >= 75) {
                    Settlement destination = world.geography.getSettlement(contract.destinationSettlementId);
                    context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute,
                            "CONTRACT_EXPIRED", "Urgent work for "
                                    + (destination == null ? "a settlement" : destination.name)
                                    + " went unclaimed: " + contract.issueReason));
                }
            } else if (party != null && player != null && contract.status == Contract.ContractStatus.ACTIVE
                    && contract.takerPartyId != null && contract.takerPartyId == party.id
                    && minute > contract.deadlineMinute) {
                fail(contract, player, party, minute);
            }
        }
    }

    private boolean createOffer(Contract.ContractType type, Settlement issuer, Settlement destination,
            GoodType good, int quantity, int urgency, String reason, long minute, boolean dynamic) {
        if (issuer == null || destination == null || issuer.id == destination.id) return false;
        Map<Long, Integer> openByIssuer = new HashMap<>();
        int openTotal = 0;
        for (Contract existing : world.contracts.values()) {
            if (existing.status == Contract.ContractStatus.OPEN) {
                openTotal++;
                openByIssuer.merge(existing.issuerSettlementId, 1, Integer::sum);
            }
            if ((existing.status == Contract.ContractStatus.OPEN
                    || existing.status == Contract.ContractStatus.ACTIVE)
                    && existing.type == type && existing.issuerSettlementId == issuer.id
                    && existing.destinationSettlementId == destination.id
                    && existing.requiredGood == good) return false;
        }
        if (dynamic && (openTotal >= MAX_OPEN_TOTAL
                || openByIssuer.getOrDefault(issuer.id, 0) >= MAX_OPEN_PER_ISSUER)) return false;

        double distance = distance(issuer, destination);
        long desiredReward = Math.round(52.0 + distance * 0.28 + Math.max(0, quantity) * 2.0
                + Math.max(0, Math.min(100, urgency)) * 0.55);
        long fundedCap = Math.max(dynamic ? 0L : 35L, Math.round(Math.max(0L, issuer.treasury.copperCoins) * 0.55));
        long reward = dynamic ? Math.min(desiredReward, fundedCap) : Math.max(35L, Math.min(desiredReward, Math.max(35L, fundedCap)));
        if (dynamic && reward < 45L) return false;
        long penalty = Math.max(10L, reward / 5L);
        long deadlineDays = Math.max(7L, Math.min(35L, Math.round(distance / 170.0) + 8L));
        if (urgency >= 85) deadlineDays = Math.min(deadlineDays, 21L);
        Contract contract = new Contract(world.idGenerator.next(), type, issuer.id, destination.id,
                minute, minute + deadlineDays * WorldConfig.MINUTES_PER_DAY, reward, penalty, good,
                Math.max(0, quantity));
        contract.issueReason = reason == null || reason.isBlank() ? "Local notice-board request" : reason;
        contract.urgency = Math.max(1, Math.min(100, urgency));
        world.contracts.put(contract.id, contract);
        if (dynamic && contract.urgency >= 75) {
            context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_POSTED",
                    issuer.name + " posts urgent work for " + destination.name + ": " + contract.issueReason));
        }
        return true;
    }

    private void distributeRelief(Settlement destination, Contract contract, long minute) {
        int distribution = Math.max(1, contract.requiredQuantity / 2);
        if (!destination.publicStockpile.remove(contract.requiredGood, distribution)) return;
        world.indexes.ensureBuilt(world, minute);
        List<Household> households = new ArrayList<>();
        for (Long householdId : world.indexes.householdsAt(destination.id)) {
            Household household = world.households.get(householdId);
            if (household != null && household.getMemberCount() > 0) households.add(household);
        }
        households.sort(Comparator.comparingDouble((Household household) -> household.foodSecurity)
                .thenComparingLong(household -> household.id));
        for (int i = 0; i < distribution && !households.isEmpty(); i++) {
            Household household = households.get(i % households.size());
            household.inventory.add(contract.requiredGood, 1);
            household.foodSecurity = SettlementPressure.clamp01(household.foodSecurity + 0.035);
            if (household.headPersonId != null) {
                Person head = world.people.get(household.headPersonId);
                if (head != null && head.alive) {
                    head.rememberPlayerInteraction(3,
                            "You delivered relief food to " + destination.name, minute);
                    head.rememberLifeEvent("Received relief food in " + destination.name, minute);
                }
            }
        }
        destination.unrest = SettlementPressure.clamp01(destination.unrest - 0.018);
    }

    private void complete(Contract contract, PlayerCampaignState player, WorldParty party, long minute) {
        party.money.add(contract.escrowCoins);
        contract.escrowCoins = 0L;
        contract.status = Contract.ContractStatus.COMPLETED;
        contract.resolvedMinute = minute;
        player.acceptedContractIds.remove(Long.valueOf(contract.id));
        player.changeSettlementReputation(contract.issuerSettlementId, 4);
        if (contract.destinationSettlementId != contract.issuerSettlementId) {
            player.changeSettlementReputation(contract.destinationSettlementId,
                    contract.type == Contract.ContractType.DELIVER_GOODS ? 6 : 3);
        }
        Settlement issuer = world.geography.getSettlement(contract.issuerSettlementId);
        Settlement destination = world.geography.getSettlement(contract.destinationSettlementId);
        if (contract.type == Contract.ContractType.ESCORT_ROUTE) {
            if (issuer != null) issuer.security = SettlementPressure.clamp01(issuer.security + 0.012);
            if (destination != null) destination.security = SettlementPressure.clamp01(destination.security + 0.012);
        } else if (destination != null) {
            destination.unrest = SettlementPressure.clamp01(destination.unrest - 0.008);
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_COMPLETED",
                contract.type.name() + " completed for "
                        + (destination == null ? "destination" : destination.name)
                        + ": " + contract.issueReason));
    }

    private void fail(Contract contract, PlayerCampaignState player, WorldParty party, long minute) {
        Settlement issuer = world.geography.getSettlement(contract.issuerSettlementId);
        Settlement destination = world.geography.getSettlement(contract.destinationSettlementId);
        if (issuer != null) issuer.treasury.add(contract.escrowCoins);
        contract.escrowCoins = 0L;
        party.money.subtract(Math.min(contract.penaltyCoins, Math.max(0L, party.money.copperCoins)));
        contract.status = Contract.ContractStatus.FAILED;
        contract.resolvedMinute = minute;
        player.acceptedContractIds.remove(Long.valueOf(contract.id));
        player.changeSettlementReputation(contract.issuerSettlementId, -5);
        if (contract.destinationSettlementId != contract.issuerSettlementId) {
            player.changeSettlementReputation(contract.destinationSettlementId, -2);
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CONTRACT_FAILED",
                contract.type.name() + " failed for "
                        + (destination == null ? "destination" : destination.name)
                        + " after its deadline: " + contract.issueReason));
    }

    private Settlement worstFoodDestination(Settlement issuer, List<Settlement> settlements,
            Map<Long, SettlementPressure> pressures) {
        Settlement best = null;
        double bestStress = 0.34;
        for (Settlement candidate : settlements) {
            if (candidate.id == issuer.id) continue;
            SettlementPressure pressure = pressures.get(candidate.id);
            if (pressure == null) continue;
            if (pressure.foodStress > bestStress
                    || pressure.foodStress == bestStress && best != null
                            && distance(issuer, candidate) < distance(issuer, best)) {
                best = candidate;
                bestStress = pressure.foodStress;
            }
        }
        return best;
    }

    private Settlement surplusFoodIssuer(Settlement destination, List<Settlement> settlements,
            Map<Long, SettlementPressure> pressures, GoodType good, int quantity) {
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Settlement candidate : settlements) {
            if (candidate.id == destination.id) continue;
            SettlementPressure pressure = pressures.get(candidate.id);
            if (pressure == null || pressure.foodReserveDays < 4.0 || pressure.besieged) continue;
            if (candidate.publicStockpile.getQuantity(good) < quantity) continue;
            double candidateDistance = distance(candidate, destination);
            if (candidateDistance < bestDistance) {
                best = candidate;
                bestDistance = candidateDistance;
            }
        }
        return best;
    }

    private Settlement surplusGoodIssuer(Settlement destination, List<Settlement> settlements,
            GoodType good, int quantity) {
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Settlement candidate : settlements) {
            if (candidate.id == destination.id || candidate.publicStockpile.getQuantity(good) < quantity) continue;
            double candidateDistance = distance(candidate, destination);
            if (candidateDistance < bestDistance) {
                best = candidate;
                bestDistance = candidateDistance;
            }
        }
        return best;
    }

    private Settlement friendlyMessageIssuer(Settlement destination, List<Settlement> settlements,
            Map<Long, SettlementPressure> pressures) {
        if (destination.controllerRealmId == null) return null;
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Settlement candidate : settlements) {
            if (candidate.id == destination.id || candidate.controllerRealmId == null
                    || candidate.controllerRealmId.longValue() != destination.controllerRealmId.longValue()) continue;
            SettlementPressure pressure = pressures.get(candidate.id);
            if (pressure != null && pressure.besieged) continue;
            double candidateDistance = distance(candidate, destination);
            if (candidateDistance < bestDistance) {
                best = candidate;
                bestDistance = candidateDistance;
            }
        }
        return best;
    }

    private Settlement nearestOther(Settlement issuer, List<Settlement> settlements) {
        Settlement best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Settlement candidate : settlements) {
            if (candidate.id == issuer.id) continue;
            double candidateDistance = distance(issuer, candidate);
            if (candidateDistance < bestDistance) {
                best = candidate;
                bestDistance = candidateDistance;
            }
        }
        return best;
    }

    private List<Settlement> sortedSettlements() {
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        return settlements;
    }

    private static GoodType neededFood(SettlementPressure pressure) {
        if (pressure == null) return GoodType.GRAIN;
        return pressure.grainTotal <= pressure.vegetableTotal ? GoodType.GRAIN : GoodType.VEGETABLES;
    }

    private static int reliefQuantity(SettlementPressure pressure) {
        if (pressure == null) return 10;
        return Math.max(5, Math.min(24, Math.max(6, pressure.population / 9)));
    }

    private static int urgency(double stress, int base) {
        return Math.max(1, Math.min(99, base + (int) Math.round(SettlementPressure.clamp01(stress) * 34.0)));
    }

    private static double distance(Settlement first, Settlement second) {
        if (first == null || second == null || first.position == null || second.position == null) return 0.0;
        return Math.hypot(first.position.x - second.position.x, first.position.y - second.position.y);
    }

    private static String percent(double value) {
        return Math.round(SettlementPressure.clamp01(value) * 100.0) + "%";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", Double.isFinite(value) ? value : 0.0);
    }

    private CommandResult rejected(String code, String message) {
        return CommandResult.rejected(code, message);
    }
}
