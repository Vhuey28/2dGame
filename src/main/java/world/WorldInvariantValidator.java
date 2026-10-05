package world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import world.geography.Road;

/** Reusable consistency checks for generated, loaded, and long-running worlds. */
public final class WorldInvariantValidator {
    public Report validate(WorldState world) {
        List<String> violations = new ArrayList<>();
        validateRegistry("person", world.people, violations);
        validateRegistry("household", world.households, violations);
        validateRegistry("army", world.armies, violations);
        validateRegistry("realm", world.realms, violations);
        validateRegistry("party", world.parties, violations);
        validateRegistry("contract", world.contracts, violations);

        Set<Long> householdMembers = new HashSet<>();
        for (Household household : world.households.values()) {
            if (world.geography.getSettlement(household.homeSettlementId) == null) {
                violations.add("household " + household.id + " has missing settlement " + household.homeSettlementId);
            }
            for (Long personId : household.memberIds) {
                Person person = world.people.get(personId);
                if (person == null) violations.add("household " + household.id + " has missing person " + personId);
                else if (!householdMembers.add(personId)) violations.add("person " + personId + " belongs to multiple households");
                else if (person.householdId == null || person.householdId != household.id) {
                    violations.add("person " + personId + " household back-reference mismatch");
                }
            }
        }
        for (Person person : world.people.values()) {
            if (person.householdId != null && !world.households.containsKey(person.householdId)) {
                violations.add("person " + person.id + " has missing household " + person.householdId);
            }
            if (person.currentSettlementId != null && world.geography.getSettlement(person.currentSettlementId) == null) {
                violations.add("person " + person.id + " has missing current settlement " + person.currentSettlementId);
            }
            if (person.homeSettlementId != null && world.geography.getSettlement(person.homeSettlementId) == null) {
                violations.add("person " + person.id + " has missing home settlement " + person.homeSettlementId);
            }
            if (!person.alive && person.deathMinute == null) violations.add("dead person " + person.id + " lacks death minute");
        }
        for (WorldParty party : world.parties.values()) {
            for (Long personId : party.memberPersonIds) {
                if (!world.people.containsKey(personId)) violations.add("party " + party.id + " has missing person " + personId);
            }
            if (party.currentSettlementId != null
                    && world.geography.getSettlement(party.currentSettlementId) == null) {
                violations.add("party " + party.id + " has missing settlement " + party.currentSettlementId);
            }
        }
        for (Contract contract : world.contracts.values()) {
            if (world.geography.getSettlement(contract.issuerSettlementId) == null
                    || world.geography.getSettlement(contract.destinationSettlementId) == null) {
                violations.add("contract " + contract.id + " has missing settlement");
            }
            if (contract.takerPartyId != null && !world.parties.containsKey(contract.takerPartyId)) {
                violations.add("contract " + contract.id + " has missing taker party");
            }
        }
        for (Road road : world.geography.getRouteGraph().getAllRoads()) {
            if (world.geography.getSettlement(road.fromSettlementId) == null
                    || world.geography.getSettlement(road.toSettlementId) == null) {
                violations.add("road " + road.id + " has missing endpoint");
            }
            if (road.length < 0.0) violations.add("road " + road.id + " has negative length");
        }
        return new Report(violations);
    }

    private void validateRegistry(String name, Map<Long, ?> registry, List<String> violations) {
        for (Map.Entry<Long, ?> entry : registry.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) violations.add(name + " registry contains null");
        }
    }

    public static final class Report {
        private final List<String> violations;
        Report(List<String> violations) { this.violations = Collections.unmodifiableList(new ArrayList<>(violations)); }
        public boolean isValid() { return violations.isEmpty(); }
        public List<String> getViolations() { return violations; }
        public void throwIfInvalid() {
            if (!isValid()) throw new IllegalStateException("World invariant violations: " + violations);
        }
        @Override public String toString() { return isValid() ? "valid" : violations.toString(); }
    }
}
