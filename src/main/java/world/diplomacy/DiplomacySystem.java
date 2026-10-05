package world.diplomacy;

import world.Person;
import world.Realm;
import world.SimulationContext;
import world.WorldConfig;
import world.WorldState;
import world.command.CommandResult;
import world.event.WorldEvent;
import world.politics.Claim;
import world.politics.PoliticalFaction;
import world.politics.Title;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Monthly diplomacy, treaty, intelligence, and phased-intrigue simulation. */
public final class DiplomacySystem {
    private final SimulationContext context;
    private final WorldState world;

    public DiplomacySystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void processMonth(long currentMinute) {
        processTreaties(currentMinute);
        processGrievances(currentMinute);
        for (Scheme scheme : new ArrayList<>(world.schemes.values())) {
            if (scheme.active) processScheme(scheme, currentMinute);
        }
        for (DiplomaticState state : world.diplomaticStates.values()) {
            driftRelationship(state);
            evaluateAiProposal(state, currentMinute);
        }
    }

    public DiplomaticState findState(long firstRealmId, long secondRealmId) {
        for (DiplomaticState state : world.diplomaticStates.values()) {
            if (state.connects(firstRealmId, secondRealmId)) return state;
        }
        return null;
    }

    public SpyNetwork findNetwork(long ownerRealmId, long targetRealmId) {
        for (SpyNetwork network : world.spyNetworks.values()) {
            if (network.ownerRealmId == ownerRealmId && network.targetRealmId == targetRealmId) return network;
        }
        return null;
    }

    public double informationQuality(long observerRealmId, long targetRealmId) {
        SpyNetwork network = findNetwork(observerRealmId, targetRealmId);
        return network == null ? 10.0 : network.informationQuality;
    }

    public String explainProposal(long proposerRealmId, long recipientRealmId, Treaty.TreatyType type) {
        DiplomaticState state = findState(proposerRealmId, recipientRealmId);
        if (state == null) return "Rejected: the realms have no diplomatic channel";
        int threat = state.fear / 2;
        int relations = state.opinion / 2 + state.trust;
        int commerce = type == Treaty.TreatyType.TRADE_AGREEMENT ? state.tradeDependence : state.tradeDependence / 3;
        int tension = state.borderTension + state.rivalry / 2;
        Realm proposer = world.realms.get(proposerRealmId);
        Realm recipient = world.realms.get(recipientRealmId);
        int legitimacy = (int) (((proposer == null ? 0 : proposer.legitimacy)
                + (recipient == null ? 0 : recipient.legitimacy) - 100.0) / 5.0);
        int factionPressure = factionPressureAgainstWar(proposerRealmId) + factionPressureAgainstWar(recipientRealmId);
        int score = relations + commerce + threat + legitimacy + factionPressure - tension;
        return "score=" + score + " (relations " + relations + ", trade " + commerce
                + ", shared threat " + threat + ", legitimacy " + legitimacy
                + ", peace factions " + factionPressure + ", tension -" + tension + ")";
    }

    public CommandResult proposeTreaty(long proposerRealmId, long recipientRealmId,
            Treaty.TreatyType type, long durationMinutes, long currentMinute) {
        Realm proposer = world.realms.get(proposerRealmId);
        Realm recipient = world.realms.get(recipientRealmId);
        if (proposer == null || recipient == null || proposerRealmId == recipientRealmId) {
            return CommandResult.rejected("INVALID_REALMS", "Treaties require two existing realms");
        }
        DiplomaticState state = findState(proposerRealmId, recipientRealmId);
        if (state == null) return CommandResult.rejected("NO_DIPLOMATIC_CHANNEL", "No bilateral state exists");
        if (hasActiveTreaty(proposerRealmId, recipientRealmId, type, currentMinute)) {
            return CommandResult.rejected("DUPLICATE_TREATY", "That treaty is already active");
        }
        if (type == Treaty.TreatyType.TRIBUTE && proposer.treasury.copperCoins < 100L) {
            return CommandResult.rejected("INSUFFICIENT_TREASURY", "The proposer cannot fund tribute");
        }
        String explanation = explainProposal(proposerRealmId, recipientRealmId, type);
        state.lastAiExplanation = type + ": " + explanation;
        int score = parseScore(explanation);
        int threshold = type == Treaty.TreatyType.DEFENSIVE_ALLIANCE ? 35 : 5;
        if (score < threshold) {
            return CommandResult.rejected("RECIPIENT_REFUSED", explanation);
        }
        Long end = durationMinutes <= 0L ? null : currentMinute + durationMinutes;
        Treaty treaty = new Treaty(world.idGenerator.next(), type, proposerRealmId,
                recipientRealmId, currentMinute, end);
        if (type == Treaty.TreatyType.TRIBUTE) {
            treaty.tributePayerRealmId = proposerRealmId;
            treaty.tributePerMonth = 100L;
        }
        world.treaties.put(treaty.id, treaty);
        state.treatyIds.add(treaty.id);
        state.trust = DiplomaticState.clamp(state.trust + 5);
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "TREATY_SIGNED"));
        return CommandResult.accepted();
    }

    public CommandResult violateTreaty(long treatyId, long violatorRealmId, long currentMinute) {
        Treaty treaty = world.treaties.get(treatyId);
        if (treaty == null || !treaty.isActiveAt(currentMinute)) {
            return CommandResult.rejected("INACTIVE_TREATY", "Treaty is not active");
        }
        if (!treaty.includes(violatorRealmId)) {
            return CommandResult.rejected("NOT_PARTICIPANT", "Realm is not bound by the treaty");
        }
        treaty.violated = true;
        treaty.active = false;
        treaty.violatedByRealmId = violatorRealmId;
        treaty.violationMinute = currentMinute;
        long offended = treaty.otherParticipant(violatorRealmId);
        addGrievance(offended, violatorRealmId, Grievance.GrievanceType.TREATY_VIOLATION,
                55, currentMinute);
        DiplomaticState state = findState(offended, violatorRealmId);
        if (state != null) {
            state.trust = DiplomaticState.clamp(state.trust - 40);
            state.opinion = DiplomaticState.clamp(state.opinion - 25);
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute, "TREATY_VIOLATED"));
        return CommandResult.accepted();
    }

    public CommandResult startScheme(Scheme scheme) {
        Person schemer = scheme == null ? null : world.people.get(scheme.schemerPersonId);
        Realm sponsor = scheme == null ? null : world.realms.get(scheme.sponsorRealmId);
        Realm target = scheme == null ? null : world.realms.get(scheme.targetRealmId);
        if (scheme == null || schemer == null || !schemer.alive || sponsor == null || target == null
                || scheme.sponsorRealmId == scheme.targetRealmId) {
            return CommandResult.rejected("INVALID_SCHEME", "Scheme participants are invalid");
        }
        if (sponsor.treasury.copperCoins < scheme.monetaryCost) {
            return CommandResult.rejected("INSUFFICIENT_TREASURY", "Scheme costs cannot be funded");
        }
        boolean duplicate = world.schemes.values().stream().anyMatch(existing -> existing.active
                && existing.schemerPersonId == scheme.schemerPersonId);
        if (duplicate) return CommandResult.rejected("SCHEMER_BUSY", "Schemer already leads an operation");
        world.schemes.put(scheme.id, scheme);
        return CommandResult.accepted();
    }

    private void processTreaties(long minute) {
        for (Treaty treaty : world.treaties.values()) {
            if (!treaty.active) continue;
            if (treaty.endMinute != null && minute >= treaty.endMinute) {
                treaty.active = false;
                context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "TREATY_EXPIRED"));
                continue;
            }
            if (treaty.type == Treaty.TreatyType.TRIBUTE && treaty.tributePayerRealmId != null) {
                Realm payer = world.realms.get(treaty.tributePayerRealmId);
                Realm receiver = world.realms.get(treaty.otherParticipant(treaty.tributePayerRealmId));
                if (payer == null || receiver == null || !payer.treasury.subtract(treaty.tributePerMonth)) {
                    violateTreaty(treaty.id, treaty.tributePayerRealmId, minute);
                    addGrievance(receiver == null ? treaty.otherParticipant(treaty.tributePayerRealmId) : receiver.id,
                            treaty.tributePayerRealmId, Grievance.GrievanceType.UNPAID_TRIBUTE, 45, minute);
                } else receiver.treasury.add(treaty.tributePerMonth);
            }
        }
    }

    private void processGrievances(long minute) {
        for (Grievance grievance : world.grievances.values()) {
            if (!grievance.resolved && grievance.expiryMinute != null && minute >= grievance.expiryMinute) {
                grievance.resolved = true;
            }
        }
    }

    private void processScheme(Scheme scheme, long minute) {
        Person schemer = world.people.get(scheme.schemerPersonId);
        Realm sponsor = world.realms.get(scheme.sponsorRealmId);
        if (schemer == null || !schemer.alive || sponsor == null) {
            abortScheme(scheme, minute, "INVALID_PARTICIPANT");
            return;
        }
        switch (scheme.phase) {
            case RECRUIT_AGENTS -> {
                recruitAgent(scheme);
                scheme.preparation += 10.0 + schemer.skills.intrigue * 0.1;
                scheme.phase = Scheme.SchemePhase.PAY_COSTS;
            }
            case PAY_COSTS -> {
                if (!sponsor.treasury.subtract(scheme.monetaryCost)) {
                    abortScheme(scheme, minute, "UNFUNDED");
                    return;
                }
                scheme.phase = Scheme.SchemePhase.PREPARATION;
            }
            case PREPARATION -> {
                scheme.preparation += 18.0 + schemer.skills.intrigue * 0.18
                        + scheme.agentPersonIds.size() * 4.0 + networkSupport(scheme);
                checkDetection(scheme, schemer, minute);
                if (scheme.preparation >= 70.0) scheme.phase = Scheme.SchemePhase.ATTEMPT;
            }
            case ATTEMPT -> resolveScheme(scheme, schemer, minute);
            case RESOLVED, ABORTED -> scheme.active = false;
        }
    }

    private void recruitAgent(Scheme scheme) {
        world.people.values().stream()
                .filter(person -> person.alive && person.id != scheme.schemerPersonId
                        && person.currentSettlementId != null)
                .max(Comparator.comparingInt(person -> person.skills.intrigue))
                .ifPresent(person -> scheme.agentPersonIds.add(person.id));
    }

    private void checkDetection(Scheme scheme, Person schemer, long minute) {
        Person target = scheme.targetPersonId == null ? null : world.people.get(scheme.targetPersonId);
        double defense = target == null ? 10.0 : target.skills.intrigue;
        SpyNetwork counter = findNetwork(scheme.targetRealmId, scheme.sponsorRealmId);
        if (counter != null) defense += counter.informationQuality * 0.35;
        double chance = Math.max(0.02, Math.min(0.65,
                0.10 + defense / 180.0 - schemer.skills.intrigue / 250.0 - scheme.secrecy / 500.0));
        if (context.getRandom("DIPLOMACY").nextDouble() < chance) {
            scheme.discoveredByRealmIds.add(scheme.targetRealmId);
            scheme.secrecy = Math.max(0.0, scheme.secrecy - 30.0);
            addGrievance(scheme.targetRealmId, scheme.sponsorRealmId,
                    Grievance.GrievanceType.EXPOSED_SCHEME, 40, minute);
            context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "SCHEME_EXPOSED"));
        }
    }

    private void resolveScheme(Scheme scheme, Person schemer, long minute) {
        Person target = scheme.targetPersonId == null ? null : world.people.get(scheme.targetPersonId);
        double targetDefense = target == null ? 10.0 : target.skills.intrigue;
        double chance = Math.max(0.05, Math.min(0.95,
                0.30 + scheme.preparation / 180.0 + schemer.skills.intrigue / 250.0
                        - targetDefense / 250.0));
        scheme.successful = context.getRandom("DIPLOMACY").nextDouble() < chance;
        if (scheme.successful) applySchemeEffect(scheme, target, minute);
        scheme.outcome = scheme.successful ? "SUCCESS" : "FAILED";
        scheme.phase = Scheme.SchemePhase.RESOLVED;
        scheme.active = false;
        scheme.resolvedMinute = minute;
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute,
                scheme.successful ? "SCHEME_SUCCEEDED" : "SCHEME_FAILED"));
    }

    private void applySchemeEffect(Scheme scheme, Person target, long minute) {
        switch (scheme.type) {
            case BUILD_SPY_NETWORK -> {
                SpyNetwork network = findNetwork(scheme.sponsorRealmId, scheme.targetRealmId);
                if (network == null) {
                    network = new SpyNetwork(world.idGenerator.next(), scheme.sponsorRealmId,
                            scheme.targetRealmId, 30.0, minute);
                    world.spyNetworks.put(network.id, network);
                } else network.improve(20.0, minute);
            }
            case FABRICATE_CLAIM -> {
                Title title = world.titles.values().stream()
                        .filter(candidate -> candidate.realmId == scheme.targetRealmId)
                        .findFirst().orElse(null);
                if (title != null) {
                    Claim claim = new Claim(world.idGenerator.next(), scheme.schemerPersonId,
                            title.id, 35.0, Claim.ClaimSource.FABRICATION, minute);
                    world.claims.put(claim.id, claim);
                    title.claimIds.add(claim.id);
                }
            }
            case ASSASSINATE -> {
                if (target != null && target.alive) target.die(minute, Person.DeathCause.OTHER);
            }
        }
    }

    private void abortScheme(Scheme scheme, long minute, String outcome) {
        scheme.outcome = outcome;
        scheme.phase = Scheme.SchemePhase.ABORTED;
        scheme.active = false;
        scheme.resolvedMinute = minute;
    }

    private double networkSupport(Scheme scheme) {
        SpyNetwork network = findNetwork(scheme.sponsorRealmId, scheme.targetRealmId);
        return network == null ? 0.0 : network.strength * 0.1;
    }

    private void driftRelationship(DiplomaticState state) {
        if (state.opinion > 0) state.opinion--;
        else if (state.opinion < 0) state.opinion++;
        int activeTrade = 0;
        for (Long treatyId : state.treatyIds) {
            Treaty treaty = world.treaties.get(treatyId);
            if (treaty != null && treaty.active && treaty.type == Treaty.TreatyType.TRADE_AGREEMENT) activeTrade++;
        }
        state.tradeDependence = Math.max(0, Math.min(100, state.tradeDependence + activeTrade - 1));
    }

    private void evaluateAiProposal(DiplomaticState state, long minute) {
        long month = minute / WorldConfig.MINUTES_PER_MONTH;
        if (month % 6 != 0) return;
        Treaty.TreatyType type = hasActiveTreaty(state.firstRealmId, state.secondRealmId,
                Treaty.TreatyType.TRADE_AGREEMENT, minute)
                ? Treaty.TreatyType.DEFENSIVE_ALLIANCE : Treaty.TreatyType.TRADE_AGREEMENT;
        CommandResult result = proposeTreaty(state.firstRealmId, state.secondRealmId, type,
                12L * WorldConfig.MINUTES_PER_MONTH, minute);
        if (!result.accepted && !"DUPLICATE_TREATY".equals(result.reasonCode)) {
            state.lastAiExplanation = type + " refused: " + result.message;
        }
    }

    private boolean hasActiveTreaty(long first, long second, Treaty.TreatyType type, long minute) {
        return world.treaties.values().stream().anyMatch(treaty -> treaty.type == type
                && treaty.includes(first) && treaty.includes(second) && treaty.isActiveAt(minute));
    }

    private int factionPressureAgainstWar(long realmId) {
        int pressure = 0;
        for (PoliticalFaction faction : world.politicalFactions.values()) {
            if (faction.realmId == realmId && faction.active
                    && faction.goal == PoliticalFaction.FactionGoal.END_WAR) {
                pressure += (int) (faction.support / 10.0);
            }
        }
        return pressure;
    }

    private int parseScore(String explanation) {
        int start = explanation.indexOf('=') + 1;
        int end = explanation.indexOf(' ', start);
        return Integer.parseInt(explanation.substring(start, end));
    }

    private void addGrievance(long offended, long offender, Grievance.GrievanceType type,
            int severity, long minute) {
        Grievance grievance = new Grievance(world.idGenerator.next(), offended, offender,
                type, severity, minute, minute + 24L * WorldConfig.MINUTES_PER_MONTH);
        world.grievances.put(grievance.id, grievance);
        DiplomaticState state = findState(offended, offender);
        if (state != null) state.grievanceIds.add(grievance.id);
    }
}
