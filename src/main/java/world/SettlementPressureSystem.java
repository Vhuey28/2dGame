package world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import world.event.WorldEvent;
import world.geography.Settlement;

/**
 * Weekly feedback loop between lived conditions and the persistent settlement
 * indicators. Food, work, crime, tax income and war move prosperity,
 * sanitation, security and unrest toward explainable targets instead of
 * leaving those numbers as decorative generation values.
 */
public final class SettlementPressureSystem {
    private final SimulationContext context;
    private final WorldState world;
    private final Map<Long, SettlementPressure.Outlook> lastOutlook = new HashMap<>();
    private final Map<Long, Long> lastEventMinute = new HashMap<>();

    public SettlementPressureSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void initialize(long minute) {
        processWeek(minute, false);
    }

    public void processWeek(long minute) {
        processWeek(minute, true);
    }

    public void processWeek(long minute, boolean publishChanges) {
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        for (Settlement settlement : settlements) {
            SettlementPressure pressure = pressures.get(settlement.id);
            if (pressure == null) continue;
            settlement.prosperity = approach(settlement.prosperity, pressure.targetProsperity, 0.18);
            settlement.sanitation = approach(settlement.sanitation, pressure.targetSanitation, 0.16);
            settlement.security = approach(settlement.security, pressure.targetSecurity, 0.18);
            settlement.unrest = approach(settlement.unrest, pressure.targetUnrest, 0.20);
            clampSettlement(settlement);
            maybePublishChange(settlement, pressure, minute, publishChanges);
        }
    }

    /** Monthly civic spending, household-need feedback and realm-level political effect. */
    public void processMonth(long minute) {
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, minute);
        Map<Long, Double> loyaltyTotals = new HashMap<>();
        Map<Long, Double> unrestTotals = new HashMap<>();
        Map<Long, Integer> realmSettlementCounts = new HashMap<>();

        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        for (Settlement settlement : settlements) {
            SettlementPressure pressure = pressures.get(settlement.id);
            if (pressure == null) continue;
            spendOnCivicOrder(settlement, pressure);
            updateResidentNeeds(settlement, pressure);
            if (settlement.controllerRealmId != null) {
                long realmId = settlement.controllerRealmId.longValue();
                loyaltyTotals.merge(realmId, pressure.loyalty, Double::sum);
                unrestTotals.merge(realmId, SettlementPressure.clamp01(settlement.unrest), Double::sum);
                realmSettlementCounts.merge(realmId, 1, Integer::sum);
            }
        }

        for (Realm realm : world.realms.values()) {
            int count = realmSettlementCounts.getOrDefault(realm.id, 0);
            if (count == 0) continue;
            double averageLoyalty = loyaltyTotals.getOrDefault(realm.id, 0.5) / count;
            double averageUnrest = unrestTotals.getOrDefault(realm.id, 0.0) / count;
            double delta = (averageLoyalty - 0.50) * 2.4 - averageUnrest * 1.1;
            realm.stability = clamp100(realm.stability + Math.max(-1.6, Math.min(1.2, delta)));
        }
    }

    private void spendOnCivicOrder(Settlement settlement, SettlementPressure pressure) {
        if (settlement.treasury == null || pressure.population <= 0) return;
        long minimumReserve = Math.max(30L, pressure.population / 2L);
        long available = settlement.treasury.copperCoins - minimumReserve;
        if (available < 20L) return;
        boolean needsWatch = settlement.security < pressure.targetSecurity - 0.04;
        boolean needsSanitation = settlement.sanitation < pressure.targetSanitation - 0.04;
        if (!needsWatch && !needsSanitation) return;
        long spend = Math.min(available, Math.max(20L, pressure.population / 3L));
        if (!settlement.treasury.subtract(spend)) return;
        if (needsWatch) settlement.security = SettlementPressure.clamp01(settlement.security + 0.012);
        if (needsSanitation) settlement.sanitation = SettlementPressure.clamp01(settlement.sanitation + 0.012);
    }

    private void updateResidentNeeds(Settlement settlement, SettlementPressure pressure) {
        for (Long personId : world.indexes.livingPeopleAt(settlement.id)) {
            Person person = world.people.get(personId);
            if (person == null || !person.alive || person.needs == null) continue;
            person.needs.safety = approach(person.needs.safety,
                    SettlementPressure.clamp01(settlement.security), 0.22);
            person.needs.politicalSatisfaction = approach(person.needs.politicalSatisfaction,
                    pressure.loyalty, 0.20);
            double wealthTarget = person.employerId != null || person.officeId != null
                    || person.regimentId != null ? 0.58 + settlement.prosperity * 0.25
                    : 0.24 + settlement.prosperity * 0.18;
            person.needs.wealthSecurity = approach(person.needs.wealthSecurity, wealthTarget, 0.18);
            Household household = person.householdId == null ? null : world.households.get(person.householdId);
            if (household != null) {
                person.needs.foodSecurity = approach(person.needs.foodSecurity,
                        SettlementPressure.clamp01(household.foodSecurity), 0.25);
            }
            person.needs.clamp();
        }
    }

    private void maybePublishChange(Settlement settlement, SettlementPressure pressure,
            long minute, boolean publishChanges) {
        SettlementPressure.Outlook previous = lastOutlook.put(settlement.id, pressure.outlook);
        if (!publishChanges || previous == null || previous == pressure.outlook) return;
        boolean significant = pressure.outlook == SettlementPressure.Outlook.CRISIS
                || pressure.outlook == SettlementPressure.Outlook.STRAINED
                || previous == SettlementPressure.Outlook.CRISIS;
        if (!significant) return;
        Long last = lastEventMinute.get(settlement.id);
        boolean firstCrisis = pressure.outlook == SettlementPressure.Outlook.CRISIS
                && previous != SettlementPressure.Outlook.CRISIS;
        if (!firstCrisis && last != null && minute - last < 30L * WorldConfig.MINUTES_PER_DAY) return;
        lastEventMinute.put(settlement.id, minute);
        String direction = pressure.outlook.ordinal() > previous.ordinal() ? "worsens" : "eases";
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute,
                "SETTLEMENT_PRESSURE_CHANGED", settlement.name + " " + direction + " to "
                        + pressure.outlook.name().toLowerCase() + ": " + pressure.summary
                        + " (" + pressure.reason + ")"));
    }

    private static void clampSettlement(Settlement settlement) {
        settlement.prosperity = SettlementPressure.clamp01(settlement.prosperity);
        settlement.sanitation = SettlementPressure.clamp01(settlement.sanitation);
        settlement.security = SettlementPressure.clamp01(settlement.security);
        settlement.unrest = SettlementPressure.clamp01(settlement.unrest);
    }

    private static double approach(double current, double target, double fraction) {
        double boundedCurrent = SettlementPressure.clamp01(current);
        return SettlementPressure.clamp01(boundedCurrent + (target - boundedCurrent) * fraction);
    }

    private static double clamp100(double value) {
        if (!Double.isFinite(value)) return 50.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
