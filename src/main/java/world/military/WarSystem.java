package world.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.Army;
import world.Household;
import world.Person;
import world.Realm;
import world.SimulationContext;
import world.War;
import world.WorldConfig;
import world.WorldState;
import world.command.CommandResult;
import world.diplomacy.DiplomaticState;
import world.diplomacy.Treaty;
import world.economy.GoodType;
import world.economy.Workplace;
import world.event.WorldEvent;
import world.geography.Province;
import world.geography.Settlement;
import world.politics.Claim;
import world.politics.PoliticalFaction;
import world.politics.Title;

/** War declarations, coalitions, occupations, sieges, score, exhaustion, and peace. */
public final class WarSystem {
    private final SimulationContext context;
    private final WorldState world;
    private final MilitarySystem military;

    public WarSystem(SimulationContext context, MilitarySystem military) {
        this.context = context;
        this.world = context.getWorld();
        this.military = military;
    }

    public CommandResult declareWar(long attackerRealmId, long defenderRealmId, War.WarGoal goal,
            Long targetProvinceId, Long targetTitleId, long currentMinute) {
        Realm attacker = world.realms.get(attackerRealmId);
        Realm defender = world.realms.get(defenderRealmId);
        if (attacker == null || defender == null || attackerRealmId == defenderRealmId) {
            return CommandResult.rejected("INVALID_BELLIGERENTS", "War requires two different existing realms");
        }
        if (world.wars.values().stream().anyMatch(war -> war.state == War.WarState.ACTIVE
                && war.opposing(attackerRealmId, defenderRealmId))) {
            return CommandResult.rejected("ALREADY_AT_WAR", "These realms are already at war");
        }
        if (hasBlockingTreaty(attackerRealmId, defenderRealmId, currentMinute)) {
            return CommandResult.rejected("TREATY_BLOCKS_WAR", "A truce or non-aggression pact is active");
        }
        String validation = validateGoal(attacker, defender, goal, targetProvinceId, targetTitleId);
        if (validation != null) return CommandResult.rejected("INVALID_WAR_GOAL", validation);
        Army attackingArmy = strongestArmy(attackerRealmId);
        Army defendingArmy = strongestArmy(defenderRealmId);
        if (attackingArmy == null) return CommandResult.rejected("NO_FIELD_ARMY", "Attacker has no physical army");

        War war = new War(world.idGenerator.next(), attackerRealmId, defenderRealmId, goal, currentMinute);
        war.targetProvinceId = targetProvinceId;
        war.targetTitleId = targetTitleId;
        war.claimantPersonId = attacker.rulerPersonId;
        war.declarationReason = goal + " validated against persistent legal and political state";
        addDefensiveAllies(war, defenderRealmId, currentMinute);
        world.wars.put(war.id, war);

        Settlement objective = objectiveSettlement(war, defenderRealmId);
        if (objective != null) {
            military.issueMoveOrder(attackingArmy.id, objective.id, Army.ArmyOrder.BESIEGE, currentMinute);
            if (defendingArmy != null) {
                military.issueMoveOrder(defendingArmy.id, objective.id, Army.ArmyOrder.DEFEND, currentMinute);
            }
        }
        attacker.stability = Math.max(0.0, attacker.stability - 2.0);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "WAR_DECLARED"));
        return CommandResult.accepted();
    }

    public void processDay(long currentMinute) {
        for (War war : new ArrayList<>(world.wars.values())) {
            if (war.state != War.WarState.ACTIVE) continue;
            processBattleReports(war);
            startEligibleSieges(war, currentMinute);
            for (Siege siege : new ArrayList<>(world.sieges.values())) {
                if (siege.warId == war.id && siege.state == Siege.SiegeState.ACTIVE) {
                    processSiege(war, siege, currentMinute);
                }
            }
            applyDailyExhaustion(war);
        }
    }

    public void processMonth(long currentMinute) {
        for (War war : new ArrayList<>(world.wars.values())) {
            if (war.state != War.WarState.ACTIVE) continue;
            Realm attacker = primaryAttacker(war);
            Realm defender = primaryDefender(war);
            if (attacker != null) attacker.warExhaustion = Math.min(100.0, war.attackerExhaustion);
            if (defender != null) defender.warExhaustion = Math.min(100.0, war.defenderExhaustion);
            if (war.attackerScore >= 75.0 || war.defenderExhaustion >= 85.0) {
                PeaceOffer offer = conquestOffer(war, currentMinute, true);
                submitPeaceOffer(offer, currentMinute, true);
            } else if (war.defenderScore >= 75.0 || war.attackerExhaustion >= 85.0) {
                PeaceOffer offer = whitePeaceOffer(war, currentMinute, war.defenderRealmIds.iterator().next());
                submitPeaceOffer(offer, currentMinute, true);
            }
        }
    }

    public PeaceOffer createPeaceOffer(long warId, long proposerRealmId, long recipientRealmId,
            long currentMinute) {
        PeaceOffer offer = new PeaceOffer(world.idGenerator.next(), warId, proposerRealmId,
                recipientRealmId, currentMinute);
        world.peaceOffers.put(offer.id, offer);
        return offer;
    }

    public CommandResult submitPeaceOffer(PeaceOffer offer, long currentMinute, boolean forceAccept) {
        War war = offer == null ? null : world.wars.get(offer.warId);
        if (offer == null || war == null || war.state != War.WarState.ACTIVE
                || !war.includes(offer.proposerRealmId) || !war.includes(offer.recipientRealmId)
                || war.attackerRealmIds.contains(offer.proposerRealmId)
                        == war.attackerRealmIds.contains(offer.recipientRealmId)) {
            if (offer != null) offer.state = PeaceOffer.OfferState.INVALID;
            return CommandResult.rejected("INVALID_PEACE_OFFER", "Offer does not join opposing active belligerents");
        }
        String invalid = validateTerms(war, offer);
        if (invalid != null) {
            offer.state = PeaceOffer.OfferState.INVALID;
            offer.evaluation = invalid;
            return CommandResult.rejected("INVALID_PEACE_TERMS", invalid);
        }
        double recipientPosition = war.defenderRealmIds.contains(offer.recipientRealmId)
                ? war.defenderScore - war.attackerScore : war.attackerScore - war.defenderScore;
        double demandedValue = offer.terms.stream().mapToDouble(this::termValue).sum();
        double exhaustion = war.defenderRealmIds.contains(offer.recipientRealmId)
                ? war.defenderExhaustion : war.attackerExhaustion;
        boolean accepted = forceAccept || demandedValue <= -recipientPosition + exhaustion * 0.7 + 10.0;
        offer.evaluation = "demand=" + Math.round(demandedValue) + ", military position="
                + Math.round(recipientPosition) + ", exhaustion=" + Math.round(exhaustion);
        if (!accepted) {
            offer.state = PeaceOffer.OfferState.REJECTED;
            return CommandResult.rejected("PEACE_REJECTED", offer.evaluation);
        }
        applyTerms(war, offer, currentMinute);
        return CommandResult.accepted();
    }

    private void startEligibleSieges(War war, long minute) {
        for (Army army : world.armies.values()) {
            if (army.state == Army.ArmyState.DISBANDED || army.order != Army.ArmyOrder.BESIEGE
                    || !war.attackerRealmIds.contains(army.realmId)) continue;
            Settlement settlement = world.geography.getSettlement(army.currentSettlementId);
            if (settlement == null || settlement.controllerRealmId == null
                    || !war.defenderRealmIds.contains(settlement.controllerRealmId)) continue;
            boolean exists = world.sieges.values().stream().anyMatch(siege -> siege.warId == war.id
                    && siege.settlementId == settlement.id && siege.state == Siege.SiegeState.ACTIVE);
            if (!exists) {
                Siege siege = new Siege(world.idGenerator.next(), war.id, army.id, settlement.id, minute);
                world.sieges.put(siege.id, siege);
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "SIEGE_STARTED"));
            }
        }
    }

    private void processSiege(War war, Siege siege, long minute) {
        Army besieger = world.armies.get(siege.besiegingArmyId);
        Settlement settlement = world.geography.getSettlement(siege.settlementId);
        if (besieger == null || settlement == null || besieger.state == Army.ArmyState.DISBANDED
                || besieger.currentSettlementId != settlement.id) {
            siege.state = Siege.SiegeState.ABANDONED;
            siege.endMinute = minute;
            return;
        }
        Army relief = world.armies.values().stream().filter(army -> army.id != besieger.id
                && war.defenderRealmIds.contains(army.realmId)
                && army.currentSettlementId == settlement.id
                && army.state != Army.ArmyState.DISBANDED).findFirst().orElse(null);
        if (relief != null) {
            BattleReport battle = military.autoResolveEncounter(besieger.id, relief.id, minute);
            if (battle != null && battle.winnerArmyId == relief.id) {
                siege.state = Siege.SiegeState.RELIEVED;
                siege.endMinute = minute;
                war.defenderScore += 12.0;
                return;
            }
        }

        int civilians = settlementPopulation(settlement.id);
        int dailyFood = Math.max(1, civilians / 12);
        int grain = Math.min(dailyFood, settlement.publicStockpile.getQuantity(GoodType.GRAIN));
        if (grain > 0) {
            settlement.publicStockpile.remove(GoodType.GRAIN, grain);
            siege.foodConsumed += grain;
            siege.defenderResolve = Math.max(0.0, siege.defenderResolve - 0.3);
        } else {
            int vegetables = Math.min(dailyFood, settlement.publicStockpile.getQuantity(GoodType.VEGETABLES));
            if (vegetables > 0) {
                settlement.publicStockpile.remove(GoodType.VEGETABLES, vegetables);
                siege.foodConsumed += vegetables;
            } else {
                siege.defenderResolve = Math.max(0.0, siege.defenderResolve - 4.0);
                settlement.unrest = Math.min(100.0, settlement.unrest + 2.0);
                reduceHouseholdFoodSecurity(settlement.id);
                long days = Math.max(1L, (minute - siege.startMinute) / WorldConfig.MINUTES_PER_DAY);
                if (days % 3L == 0L && killCivilian(settlement.id, minute)) siege.civilianDeaths++;
            }
        }
        int strength = military.strength(besieger);
        double fortification = Math.max(5.0, settlement.security * 0.45);
        siege.breachProgress += Math.max(0.5, strength / fortification) + (100.0 - siege.defenderResolve) * 0.015;
        if (siege.breachProgress >= 100.0 || siege.defenderResolve <= 0.0) captureSettlement(war, siege, besieger, settlement, minute);
    }

    private void captureSettlement(War war, Siege siege, Army army, Settlement settlement, long minute) {
        settlement.occupyingRealmId = army.realmId;
        Province province = world.geography.getProvince(settlement.provinceId);
        if (province != null && province.settlementIds.stream().allMatch(id -> {
            Settlement member = world.geography.getSettlement(id);
            return member != null && (member.controllerRealmId == army.realmId
                    || member.occupyingRealmId != null && member.occupyingRealmId == army.realmId);
        })) province.occupyingRealmId = army.realmId;
        siege.state = Siege.SiegeState.CAPTURED;
        siege.endMinute = minute;
        war.attackerScore = Math.min(100.0, war.attackerScore + 35.0);
        war.contributionByRealm.merge(army.realmId, 35.0, Double::sum);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "SETTLEMENT_OCCUPIED"));
    }

    private void processBattleReports(War war) {
        for (BattleReport report : world.battleReports.values()) {
            if (!war.processedBattleReportIds.add(report.id)) continue;
            Army first = world.armies.get(report.firstArmyId);
            Army second = world.armies.get(report.secondArmyId);
            Army winner = world.armies.get(report.winnerArmyId);
            if (first == null || second == null || winner == null || !war.opposing(first.realmId, second.realmId)) continue;
            int losses = report.firstCasualties + report.secondCasualties;
            if (war.attackerRealmIds.contains(winner.realmId)) war.attackerScore = Math.min(100.0, war.attackerScore + 10 + losses);
            else war.defenderScore = Math.min(100.0, war.defenderScore + 10 + losses);
            war.contributionByRealm.merge(winner.realmId, 10.0 + losses, Double::sum);
        }
    }

    private void applyDailyExhaustion(War war) {
        war.attackerExhaustion = Math.min(100.0, war.attackerExhaustion + 0.08);
        war.defenderExhaustion = Math.min(100.0, war.defenderExhaustion + 0.08);
        for (Siege siege : world.sieges.values()) {
            if (siege.warId == war.id && siege.state == Siege.SiegeState.ACTIVE) {
                war.defenderExhaustion = Math.min(100.0, war.defenderExhaustion + 0.18);
            }
        }
    }

    private String validateGoal(Realm attacker, Realm defender, War.WarGoal goal,
            Long provinceId, Long titleId) {
        return switch (goal) {
            case CONQUER_PROVINCE -> {
                Province province = provinceId == null ? null : world.geography.getProvince(provinceId);
                yield province == null || province.controllerRealmId == null
                        || province.controllerRealmId != defender.id ? "Target province is not controlled by defender" : null;
            }
            case CLAIM_TITLE -> {
                Title title = titleId == null ? null : world.titles.get(titleId);
                boolean claim = title != null && attacker.rulerPersonId != null
                        && world.claims.values().stream().anyMatch(candidate -> candidate.titleId == title.id
                                && candidate.claimantPersonId == attacker.rulerPersonId
                                && (candidate.expiryMinute == null || candidate.expiryMinute > context.getClock().getWorldMinute()));
                yield title == null || title.realmId != defender.id || !claim ? "Ruler lacks an active claim on defender's title" : null;
            }
            case INDEPENDENCE -> {
                boolean vassal = defender.vassalRealmIds.contains(attacker.id);
                boolean faction = world.politicalFactions.values().stream().anyMatch(candidate ->
                        candidate.realmId == attacker.id && candidate.active
                        && candidate.goal == PoliticalFaction.FactionGoal.INDEPENDENCE
                        && candidate.support >= 60.0 && candidate.organization >= 40.0);
                yield !vassal && !faction ? "No vassal relationship or organized independence movement" : null;
            }
            case VASSALIZE -> attacker.legitimacy < 40.0 ? "Attacker lacks legitimacy to demand vassalage" : null;
            case PUNITIVE -> hasActiveGrievance(attacker.id, defender.id) ? null : "Punitive war requires an active grievance";
        };
    }

    private boolean hasActiveGrievance(long offended, long offender) {
        return world.grievances.values().stream().anyMatch(grievance -> !grievance.resolved
                && grievance.offendedRealmId == offended && grievance.offenderRealmId == offender);
    }

    private boolean hasBlockingTreaty(long first, long second, long minute) {
        return world.treaties.values().stream().anyMatch(treaty -> treaty.includes(first) && treaty.includes(second)
                && treaty.isActiveAt(minute) && (treaty.type == Treaty.TreatyType.TRUCE
                        || treaty.type == Treaty.TreatyType.NON_AGGRESSION));
    }

    private void addDefensiveAllies(War war, long defenderRealmId, long minute) {
        for (Treaty treaty : world.treaties.values()) {
            if (treaty.type != Treaty.TreatyType.DEFENSIVE_ALLIANCE || !treaty.isActiveAt(minute)
                    || !treaty.includes(defenderRealmId)) continue;
            long ally = treaty.otherParticipant(defenderRealmId);
            if (!war.attackerRealmIds.contains(ally)) {
                war.defenderRealmIds.add(ally);
                war.contributionByRealm.putIfAbsent(ally, 0.0);
            }
        }
    }

    private Army strongestArmy(long realmId) {
        return world.armies.values().stream().filter(army -> army.realmId == realmId
                && army.state != Army.ArmyState.DISBANDED && military.strength(army) > 0)
                .max(Comparator.comparingInt(military::strength)).orElse(null);
    }

    private Settlement objectiveSettlement(War war, long defenderRealmId) {
        if (war.targetProvinceId != null) {
            Province province = world.geography.getProvince(war.targetProvinceId);
            if (province != null) {
                return province.settlementIds.stream().map(world.geography::getSettlement)
                        .filter(settlement -> settlement != null && settlement.controllerRealmId != null
                                && settlement.controllerRealmId == defenderRealmId)
                        .max(Comparator.comparingInt(this::settlementPopulation)).orElse(null);
            }
        }
        Realm defender = world.realms.get(defenderRealmId);
        return defender == null || defender.capitalSettlementId == null ? null
                : world.geography.getSettlement(defender.capitalSettlementId);
    }

    private int settlementPopulation(Settlement settlement) { return settlementPopulation(settlement.id); }
    private int settlementPopulation(long settlementId) {
        int count = 0;
        for (Person person : world.people.values()) {
            if (person.alive && person.homeSettlementId != null && person.homeSettlementId == settlementId) count++;
        }
        return count;
    }

    private void reduceHouseholdFoodSecurity(long settlementId) {
        for (Household household : world.households.values()) {
            if (household.homeSettlementId == settlementId) household.foodSecurity = Math.max(0.0, household.foodSecurity - 0.08);
        }
    }

    private boolean killCivilian(long settlementId, long minute) {
        Person victim = world.people.values().stream().filter(person -> person.alive
                && person.homeSettlementId != null && person.homeSettlementId == settlementId
                && person.regimentId == null && (world.player == null || world.player.personId != person.id))
                .min(Comparator.comparingDouble(person -> person.health.healthLevel)).orElse(null);
        if (victim == null) return false;
        victim.die(minute, Person.DeathCause.STARVATION);
        if (victim.employerId != null) {
            Workplace workplace = world.workplaces.get(victim.employerId);
            if (workplace != null) workplace.removeWorker(victim.id);
            victim.employerId = null;
        }
        victim.officeId = null;
        if (victim.householdId != null) {
            Household household = world.households.get(victim.householdId);
            if (household != null) {
                household.removeMember(victim.id);
                if (household.headPersonId != null && household.headPersonId == victim.id) {
                    household.headPersonId = household.memberIds.stream().findFirst().orElse(null);
                }
            }
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "SIEGE_CIVILIAN_DIED"));
        return true;
    }

    private PeaceOffer conquestOffer(War war, long minute, boolean attackersWin) {
        long proposer = attackersWin ? war.attackerRealmIds.iterator().next() : war.defenderRealmIds.iterator().next();
        long recipient = attackersWin ? war.defenderRealmIds.iterator().next() : war.attackerRealmIds.iterator().next();
        PeaceOffer offer = createPeaceOffer(war.id, proposer, recipient, minute);
        if (attackersWin && war.targetProvinceId != null) {
            offer.terms.add(new PeaceOffer.PeaceTerm(PeaceOffer.TermType.TRANSFER_PROVINCE,
                    war.targetProvinceId, recipient, proposer, 0L));
        } else offer.terms.add(new PeaceOffer.PeaceTerm(PeaceOffer.TermType.WHITE_PEACE, null, 0L, 0L, 0L));
        return offer;
    }

    private PeaceOffer whitePeaceOffer(War war, long minute, long proposer) {
        long recipient = war.attackerRealmIds.contains(proposer)
                ? war.defenderRealmIds.iterator().next() : war.attackerRealmIds.iterator().next();
        PeaceOffer offer = createPeaceOffer(war.id, proposer, recipient, minute);
        offer.terms.add(new PeaceOffer.PeaceTerm(PeaceOffer.TermType.WHITE_PEACE, null, 0L, 0L, 0L));
        return offer;
    }

    private String validateTerms(War war, PeaceOffer offer) {
        if (offer.terms.isEmpty()) return "Peace offer has no terms";
        for (PeaceOffer.PeaceTerm term : offer.terms) {
            if (term.type == PeaceOffer.TermType.TRANSFER_PROVINCE) {
                Province province = term.provinceId == null ? null : world.geography.getProvince(term.provinceId);
                if (province == null || !war.includes(term.payerRealmId) || !war.includes(term.receiverRealmId)
                        || province.controllerRealmId == null || province.controllerRealmId != term.payerRealmId) {
                    return "Province transfer is not legally enforceable";
                }
            }
            if (term.type == PeaceOffer.TermType.REPARATIONS) {
                Realm payer = world.realms.get(term.payerRealmId);
                if (payer == null || term.amount < 0 || payer.treasury.copperCoins < term.amount) {
                    return "Reparations exceed payer treasury";
                }
            }
        }
        return null;
    }

    private double termValue(PeaceOffer.PeaceTerm term) {
        return switch (term.type) {
            case WHITE_PEACE -> 0.0;
            case TRANSFER_PROVINCE -> 65.0;
            case REPARATIONS -> term.amount / 100.0;
            case VASSALIZATION -> 80.0;
            case INDEPENDENCE -> 70.0;
        };
    }

    private void applyTerms(War war, PeaceOffer offer, long minute) {
        for (PeaceOffer.PeaceTerm term : offer.terms) {
            switch (term.type) {
                case TRANSFER_PROVINCE -> transferProvince(term.provinceId, term.payerRealmId, term.receiverRealmId);
                case REPARATIONS -> {
                    Realm payer = world.realms.get(term.payerRealmId);
                    Realm receiver = world.realms.get(term.receiverRealmId);
                    payer.treasury.subtract(term.amount);
                    receiver.treasury.add(term.amount);
                }
                case VASSALIZATION -> world.realms.get(term.receiverRealmId).vassalRealmIds.add(term.payerRealmId);
                case INDEPENDENCE -> world.realms.get(term.payerRealmId).vassalRealmIds.remove(term.receiverRealmId);
                case WHITE_PEACE -> { }
            }
        }
        offer.state = PeaceOffer.OfferState.ACCEPTED;
        war.state = War.WarState.ENDED;
        war.endMinute = minute;
        war.enforcedPeaceOfferId = offer.id;
        clearOccupations(war);
        createTruce(offer.proposerRealmId, offer.recipientRealmId, minute);
        for (Long realmId : war.attackerRealmIds) resetRealmWarExhaustion(realmId);
        for (Long realmId : war.defenderRealmIds) resetRealmWarExhaustion(realmId);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "PEACE_ENFORCED"));
    }

    private void transferProvince(Long provinceId, long oldRealmId, long newRealmId) {
        Province province = world.geography.getProvince(provinceId);
        Realm oldRealm = world.realms.get(oldRealmId);
        Realm newRealm = world.realms.get(newRealmId);
        province.controllerRealmId = newRealmId;
        province.occupyingRealmId = null;
        if (oldRealm != null) oldRealm.controlledProvinceIds.remove(province.id);
        if (newRealm != null) newRealm.controlledProvinceIds.add(province.id);
        for (Long settlementId : province.settlementIds) {
            Settlement settlement = world.geography.getSettlement(settlementId);
            if (settlement != null && settlement.controllerRealmId != null
                    && settlement.controllerRealmId == oldRealmId) {
                settlement.controllerRealmId = newRealmId;
                settlement.occupyingRealmId = null;
            }
        }
        Title title = province.legalTitleId == null ? null : world.titles.get(province.legalTitleId);
        if (title != null && title.rank != Title.Rank.KINGDOM
                && newRealm != null && newRealm.rulerPersonId != null) {
            title.realmId = newRealmId;
            title.installHolder(newRealm.rulerPersonId, context.getClock().getWorldMinute(), "PEACE_TRANSFER");
        }
    }

    private void clearOccupations(War war) {
        for (Settlement settlement : world.geography.getSettlements().values()) {
            if (settlement.occupyingRealmId != null && war.includes(settlement.occupyingRealmId)) settlement.occupyingRealmId = null;
        }
        for (Province province : world.geography.getProvinces().values()) {
            if (province.occupyingRealmId != null && war.includes(province.occupyingRealmId)) province.occupyingRealmId = null;
        }
    }

    private void createTruce(long firstRealmId, long secondRealmId, long minute) {
        Treaty truce = new Treaty(world.idGenerator.next(), Treaty.TreatyType.TRUCE,
                firstRealmId, secondRealmId, minute, minute + 12L * WorldConfig.MINUTES_PER_MONTH);
        world.treaties.put(truce.id, truce);
        for (DiplomaticState state : world.diplomaticStates.values()) {
            if (state.connects(firstRealmId, secondRealmId)) {
                state.treatyIds.add(truce.id);
                state.trust = DiplomaticState.clamp(state.trust - 8);
            }
        }
    }

    private void resetRealmWarExhaustion(long realmId) {
        Realm realm = world.realms.get(realmId);
        if (realm != null) realm.warExhaustion *= 0.5;
    }

    private Realm primaryAttacker(War war) { return world.realms.get(war.attackerRealmIds.iterator().next()); }
    private Realm primaryDefender(War war) { return world.realms.get(war.defenderRealmIds.iterator().next()); }
}
