package world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import world.economy.GoodType;
import world.geography.Settlement;
import world.military.Siege;
import world.politics.Government;

/**
 * Derived, player-readable assessment of what is helping or hurting one
 * settlement right now.
 *
 * <p>The persistent settlement fields (prosperity, sanitation, security and
 * unrest) remain canonical. This assessment explains them from food, work,
 * crime, taxation, public finances and war, and is used consistently by the
 * simulation, contracts, kingdom strategy and campaign snapshot. It is a
 * derived value, so it deliberately is not serialized.</p>
 */
public final class SettlementPressure {
    public enum Outlook { THRIVING, STABLE, STRAINED, CRISIS }

    public final long settlementId;
    public final int population;
    public final int households;
    public final int workingAgeResidents;
    public final int employedResidents;
    public final double unemploymentRate;

    public final double averageFoodSecurity;
    public final int grainTotal;
    public final int vegetableTotal;
    public final int publicGrain;
    public final int publicVegetables;
    public final double foodReserveDays;
    public final double publicFoodReserveDays;

    public final int taxLawLevel;
    public final double taxBurdenRate;
    public final double treasuryPerResident;
    public final int crimeIncidents30Days;
    public final int undiscoveredCrime30Days;
    public final double crimePer100Residents30Days;

    public final boolean occupied;
    public final boolean besieged;
    public final int friendlyArmyStrength;
    public final int hostileArmyStrength;

    public final double foodStress;
    public final double jobStress;
    public final double crimeStress;
    public final double taxStress;
    public final double fiscalStress;
    public final double warStress;
    public final double overallPressure;
    public final double loyalty;
    public final double migrationPressure;

    public final double targetProsperity;
    public final double targetSanitation;
    public final double targetSecurity;
    public final double targetUnrest;

    public final Outlook outlook;
    public final String mainPressure;
    public final String summary;
    public final String reason;

    private SettlementPressure(long settlementId, int population, int households,
            int workingAgeResidents, int employedResidents, double unemploymentRate,
            double averageFoodSecurity, int grainTotal, int vegetableTotal,
            int publicGrain, int publicVegetables, double foodReserveDays,
            double publicFoodReserveDays, int taxLawLevel, double taxBurdenRate,
            double treasuryPerResident, int crimeIncidents30Days,
            int undiscoveredCrime30Days, double crimePer100Residents30Days,
            boolean occupied, boolean besieged, int friendlyArmyStrength, int hostileArmyStrength,
            double foodStress, double jobStress, double crimeStress, double taxStress,
            double fiscalStress, double warStress, double overallPressure, double loyalty,
            double migrationPressure, double targetProsperity, double targetSanitation,
            double targetSecurity, double targetUnrest, Outlook outlook, String mainPressure,
            String summary, String reason) {
        this.settlementId = settlementId;
        this.population = population;
        this.households = households;
        this.workingAgeResidents = workingAgeResidents;
        this.employedResidents = employedResidents;
        this.unemploymentRate = unemploymentRate;
        this.averageFoodSecurity = averageFoodSecurity;
        this.grainTotal = grainTotal;
        this.vegetableTotal = vegetableTotal;
        this.publicGrain = publicGrain;
        this.publicVegetables = publicVegetables;
        this.foodReserveDays = foodReserveDays;
        this.publicFoodReserveDays = publicFoodReserveDays;
        this.taxLawLevel = taxLawLevel;
        this.taxBurdenRate = taxBurdenRate;
        this.treasuryPerResident = treasuryPerResident;
        this.crimeIncidents30Days = crimeIncidents30Days;
        this.undiscoveredCrime30Days = undiscoveredCrime30Days;
        this.crimePer100Residents30Days = crimePer100Residents30Days;
        this.occupied = occupied;
        this.besieged = besieged;
        this.friendlyArmyStrength = friendlyArmyStrength;
        this.hostileArmyStrength = hostileArmyStrength;
        this.foodStress = foodStress;
        this.jobStress = jobStress;
        this.crimeStress = crimeStress;
        this.taxStress = taxStress;
        this.fiscalStress = fiscalStress;
        this.warStress = warStress;
        this.overallPressure = overallPressure;
        this.loyalty = loyalty;
        this.migrationPressure = migrationPressure;
        this.targetProsperity = targetProsperity;
        this.targetSanitation = targetSanitation;
        this.targetSecurity = targetSecurity;
        this.targetUnrest = targetUnrest;
        this.outlook = outlook;
        this.mainPressure = mainPressure;
        this.summary = summary;
        this.reason = reason;
    }

    public static SettlementPressure evaluate(WorldState world, Settlement settlement, long minute) {
        if (world == null || settlement == null) return null;
        return evaluateAll(world, minute).get(settlement.id);
    }

    /** Evaluates every settlement while scanning crimes and armies only once. */
    public static Map<Long, SettlementPressure> evaluateAll(WorldState world, long minute) {
        Map<Long, SettlementPressure> result = new LinkedHashMap<>();
        if (world == null) return result;
        world.indexes.ensureBuilt(world, minute);

        Map<Long, CrimeStats> crimeStats = new HashMap<>();
        long crimeCutoff = minute - 30L * WorldConfig.MINUTES_PER_DAY;
        for (CrimeIncident incident : world.crimeIncidents.values()) {
            if (incident.minute < crimeCutoff || incident.minute > minute) continue;
            CrimeStats stats = crimeStats.computeIfAbsent(incident.settlementId,
                    ignored -> new CrimeStats());
            stats.incidents++;
            if (!incident.discovered) stats.undiscovered++;
            stats.severity += Math.max(0, incident.severity);
        }

        Map<Long, ArmyPresence> presence = new HashMap<>();
        for (Army army : world.armies.values()) {
            if (army == null || army.state == Army.ArmyState.DISBANDED
                    || army.state == Army.ArmyState.TRAVELING
                    || army.state == Army.ArmyState.ENGAGED) continue;
            Settlement at = world.geography.getSettlement(army.currentSettlementId);
            if (at == null || at.position == null) continue;
            if (army.position != null && (!Double.isFinite(army.position.x)
                    || !Double.isFinite(army.position.y)
                    || Math.hypot(army.position.x - at.position.x,
                            army.position.y - at.position.y) > 12.0)) continue;
            int strength = armyStrength(world, army);
            if (strength <= 0) continue;
            ArmyPresence stats = presence.computeIfAbsent(at.id, ignored -> new ArmyPresence());
            if (at.controllerRealmId != null && at.controllerRealmId.longValue() == army.realmId) {
                stats.friendly += strength;
            } else if (at.controllerRealmId != null
                    && activelyOpposes(world, army.realmId, at.controllerRealmId.longValue())) {
                stats.hostile += strength;
            }
        }

        List<Settlement> settlements = new ArrayList<>(world.geography.getSettlements().values());
        settlements.sort(Comparator.comparingLong(settlement -> settlement.id));
        for (Settlement settlement : settlements) {
            result.put(settlement.id, evaluateOne(world, settlement, minute,
                    crimeStats.get(settlement.id), presence.get(settlement.id)));
        }
        return result;
    }

    /** Higher is a better migration destination. Used by households and AI alike. */
    public double destinationScore() {
        return loyalty * 34.0 + averageFoodSecurity * 24.0
                + Math.min(14.0, Math.max(0.0, foodReserveDays)) * 1.4
                + (1.0 - unemploymentRate) * 10.0 - overallPressure * 28.0
                - warStress * 18.0 - crimeStress * 8.0;
    }

    private static SettlementPressure evaluateOne(WorldState world, Settlement settlement,
            long minute, CrimeStats crime, ArmyPresence armies) {
        List<Long> residentIds = world.indexes.livingPeopleAt(settlement.id);
        int population = residentIds.size();
        int workingAge = 0;
        int employed = 0;
        for (Long personId : residentIds) {
            Person person = world.people.get(personId);
            if (person == null || !person.alive || person.isChild(minute) || person.isElderly(minute)
                    || person.regimentId != null || person.travelingPartyId != null
                    || person.type == Person.PersonType.CHILD) continue;
            workingAge++;
            if (person.employerId != null || person.officeId != null
                    || person.type == Person.PersonType.NOBLE) employed++;
        }
        double unemployment = workingAge == 0 ? 0.0
                : clamp01(1.0 - employed / (double) workingAge);

        int occupiedHouseholds = 0;
        double foodSecurityTotal = 0.0;
        int householdGrain = 0;
        int householdVegetables = 0;
        for (Long householdId : world.indexes.householdsAt(settlement.id)) {
            Household household = world.households.get(householdId);
            if (household == null || household.getMemberCount() == 0) continue;
            occupiedHouseholds++;
            foodSecurityTotal += clamp01(household.foodSecurity);
            householdGrain += household.inventory.getQuantity(GoodType.GRAIN);
            householdVegetables += household.inventory.getQuantity(GoodType.VEGETABLES);
        }
        double averageFood = occupiedHouseholds == 0 ? 0.45 : foodSecurityTotal / occupiedHouseholds;
        int publicGrain = settlement.publicStockpile.getQuantity(GoodType.GRAIN);
        int publicVegetables = settlement.publicStockpile.getQuantity(GoodType.VEGETABLES);
        int grainTotal = publicGrain + householdGrain;
        int vegetableTotal = publicVegetables + householdVegetables;
        int divisor = Math.max(1, population);
        double reserveDays = Math.min(grainTotal, vegetableTotal) / (double) divisor;
        double publicReserveDays = Math.min(publicGrain, publicVegetables) / (double) divisor;

        Realm controller = settlement.controllerRealmId == null
                ? null : world.realms.get(settlement.controllerRealmId);
        Government government = controller == null ? null : world.governments.get(controller.governmentId);
        int taxLaw = government == null ? 1 : government.getLawLevel(Government.LawType.TAXATION);
        double taxRate = taxRateForLaw(taxLaw);
        double treasuryPerResident = settlement.treasury == null ? 0.0
                : settlement.treasury.copperCoins / (double) divisor;

        int crimeIncidents = crime == null ? 0 : crime.incidents;
        int undiscovered = crime == null ? 0 : crime.undiscovered;
        double crimeRate = crimeIncidents * 100.0 / divisor;

        boolean besieged = false;
        for (Siege siege : world.sieges.values()) {
            if (siege.settlementId == settlement.id && siege.state == Siege.SiegeState.ACTIVE) {
                besieged = true;
                break;
            }
        }
        boolean occupied = settlement.occupyingRealmId != null
                && (settlement.controllerRealmId == null
                        || settlement.occupyingRealmId.longValue()
                                != settlement.controllerRealmId.longValue());
        boolean controllerAtWar = controller != null && world.wars.values().stream().anyMatch(war ->
                war.state == War.WarState.ACTIVE && war.includes(controller.id));
        int friendlyArmy = armies == null ? 0 : armies.friendly;
        int hostileArmy = armies == null ? 0 : armies.hostile;

        double reserveStress = reserveDays >= 7.0 ? 0.0
                : clamp01((7.0 - Math.max(0.0, reserveDays)) / 7.0);
        double foodStress = clamp01((0.74 - averageFood) * 1.35 + reserveStress * 0.42);
        double jobStress = clamp01((unemployment - 0.18) * 1.45);
        double undiscoveredShare = crimeIncidents == 0 ? 0.0 : undiscovered / (double) crimeIncidents;
        double crimeStress = clamp01(crimeRate / 34.0 + undiscoveredShare * 0.16
                + (crime == null ? 0.0 : crime.severity / Math.max(1.0, population * 2.5)));
        double taxStress = clamp01((taxRate - 0.03) / 0.15 * 0.78
                + foodStress * taxRate * 0.65);
        double fiscalStress = clamp01((2.5 - Math.min(2.5, treasuryPerResident)) / 2.5 * 0.72);
        double warStress = clamp01((besieged ? 0.58 : 0.0) + (occupied ? 0.34 : 0.0)
                + (hostileArmy > 0 ? 0.28 : 0.0) + (controllerAtWar ? 0.10 : 0.0));

        double currentUnrest = clamp01(settlement.unrest);
        double overall = clamp01(foodStress * 0.30 + jobStress * 0.16 + crimeStress * 0.16
                + taxStress * 0.10 + fiscalStress * 0.06 + warStress * 0.12
                + currentUnrest * 0.10);
        double loyalty = clamp01(0.56 + clamp01(settlement.prosperity) * 0.13
                + clamp01(settlement.security) * 0.10 + averageFood * 0.18
                - currentUnrest * 0.24 - taxStress * 0.13 - jobStress * 0.08
                - crimeStress * 0.08 - warStress * 0.16 - (occupied ? 0.10 : 0.0));
        loyalty = Math.max(0.02, Math.min(0.98, loyalty));
        double migration = clamp01(foodStress * 0.54 + jobStress * 0.19
                + warStress * 0.16 + currentUnrest * 0.11);

        double employmentRate = 1.0 - unemployment;
        double funds = clamp01(treasuryPerResident / 6.0);
        double armyBonus = friendlyArmy <= 0 ? 0.0 : Math.min(0.18, 0.05 + friendlyArmy / 180.0);
        double targetProsperity = clamp01(0.15 + averageFood * 0.24 + employmentRate * 0.27
                + funds * 0.10 + clamp01(settlement.security) * 0.10
                - taxStress * 0.08 - crimeStress * 0.11 - warStress * 0.20);
        double targetSanitation = clamp01(0.24 + targetProsperity * 0.22 + funds * 0.17
                + averageFood * 0.10 - Math.min(0.12, Math.max(0, population - 120) / 900.0)
                - warStress * 0.16);
        double targetSecurity = clamp01(0.24 + loyalty * 0.20 + funds * 0.13 + armyBonus
                - crimeStress * 0.30 - currentUnrest * 0.10 - warStress * 0.18);
        double targetUnrest = clamp01(overall * 0.72 + (1.0 - loyalty) * 0.22 - armyBonus * 0.18);

        String main = "Balanced";
        double mainValue = 0.22;
        if (foodStress > mainValue) { main = "Food shortage"; mainValue = foodStress; }
        if (jobStress > mainValue) { main = "Unemployment"; mainValue = jobStress; }
        if (crimeStress > mainValue) { main = "Crime and safety"; mainValue = crimeStress; }
        if (taxStress > mainValue) { main = "Tax burden"; mainValue = taxStress; }
        if (fiscalStress > mainValue) { main = "Empty public purse"; mainValue = fiscalStress; }
        if (warStress > mainValue) { main = besieged ? "Siege" : occupied ? "Occupation" : "War strain"; }

        Outlook outlook = overall >= 0.68 ? Outlook.CRISIS
                : overall >= 0.46 ? Outlook.STRAINED
                : overall <= 0.24 ? Outlook.THRIVING : Outlook.STABLE;
        String summary = buildSummary(main, averageFood, reserveDays, unemployment,
                crimeIncidents, taxRate, treasuryPerResident, besieged, occupied);
        String reason = buildReason(averageFood, reserveDays, unemployment, crimeIncidents,
                crimeRate, taxRate, treasuryPerResident, loyalty, besieged, occupied,
                hostileArmy, friendlyArmy);

        return new SettlementPressure(settlement.id, population, occupiedHouseholds,
                workingAge, employed, unemployment, averageFood, grainTotal, vegetableTotal,
                publicGrain, publicVegetables, reserveDays, publicReserveDays, taxLaw, taxRate,
                treasuryPerResident, crimeIncidents, undiscovered, crimeRate, occupied, besieged,
                friendlyArmy, hostileArmy, foodStress, jobStress, crimeStress, taxStress,
                fiscalStress, warStress, overall, loyalty, migration, targetProsperity,
                targetSanitation, targetSecurity, targetUnrest, outlook, main, summary, reason);
    }

    public static double taxRateForLaw(int lawLevel) {
        return switch (Math.max(0, Math.min(3, lawLevel))) {
            case 0 -> 0.03;
            case 1 -> 0.07;
            case 2 -> 0.12;
            default -> 0.18;
        };
    }

    private static String buildSummary(String main, double averageFood, double reserveDays,
            double unemployment, int crimeIncidents, double taxRate, double treasuryPerResident,
            boolean besieged, boolean occupied) {
        return switch (main) {
            case "Food shortage" -> "Food shortage: households " + percent(averageFood)
                    + " fed, " + oneDecimal(reserveDays) + " balanced food-days stored";
            case "Unemployment" -> "Unemployment: " + percent(unemployment)
                    + " of working-age residents have no work";
            case "Crime and safety" -> "Crime and safety: " + crimeIncidents
                    + " recorded incidents in 30 days";
            case "Tax burden" -> "Tax burden: households are assessed about "
                    + percent(taxRate) + " of visible wealth";
            case "Empty public purse" -> "Empty public purse: only "
                    + oneDecimal(treasuryPerResident) + "c of local treasury per resident";
            case "Siege" -> "Siege: food, safety and trade are under direct military pressure";
            case "Occupation" -> "Occupation: a foreign power holds the streets";
            case "War strain" -> "War strain: soldiers and requisitioning burden daily life";
            default -> besieged ? "Under siege, but civil order is holding"
                    : occupied ? "Occupied, but households are coping"
                    : "No single crisis dominates local life";
        };
    }

    private static String buildReason(double averageFood, double reserveDays, double unemployment,
            int crimeIncidents, double crimeRate, double taxRate, double treasuryPerResident,
            double loyalty, boolean besieged, boolean occupied, int hostileArmy, int friendlyArmy) {
        StringBuilder text = new StringBuilder();
        text.append("Food ").append(percent(averageFood)).append(" / ")
                .append(oneDecimal(reserveDays)).append("d reserves; jobs ")
                .append(percent(1.0 - unemployment)).append(" employed; crime ")
                .append(crimeIncidents).append(" (").append(oneDecimal(crimeRate))
                .append("/100 in 30d); tax ").append(percent(taxRate))
                .append("; treasury ").append(oneDecimal(treasuryPerResident))
                .append("c/person; loyalty ").append(percent(loyalty));
        if (besieged) text.append("; under siege");
        if (occupied) text.append("; occupied");
        if (hostileArmy > 0) text.append("; hostile troops present ").append(hostileArmy);
        if (friendlyArmy > 0) text.append("; friendly garrison ").append(friendlyArmy);
        return text.toString();
    }

    private static boolean activelyOpposes(WorldState world, long firstRealmId, long secondRealmId) {
        for (War war : world.wars.values()) {
            if (war.state == War.WarState.ACTIVE && war.opposing(firstRealmId, secondRealmId)) return true;
        }
        return false;
    }

    private static int armyStrength(WorldState world, Army army) {
        int strength = 0;
        for (Long regimentId : army.regimentIds) {
            world.military.Regiment regiment = world.regiments.get(regimentId);
            if (regiment != null) strength += regiment.livingStrength(world);
        }
        return strength;
    }

    private static String percent(double value) {
        return Math.round(clamp01(value) * 100.0) + "%";
    }

    private static String oneDecimal(double value) {
        if (!Double.isFinite(value)) return "0.0";
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class CrimeStats {
        int incidents;
        int undiscovered;
        int severity;
    }

    private static final class ArmyPresence {
        int friendly;
        int hostile;
    }
}
