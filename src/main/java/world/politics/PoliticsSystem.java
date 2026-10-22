package world.politics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import world.Person;
import world.SettlementPressure;
import world.Realm;
import world.SimulationContext;
import world.WorldState;
import world.event.WorldEvent;
import world.geography.Settlement;

/** Monthly government, succession, office, law, and faction simulation. */
public final class PoliticsSystem {
    private final SimulationContext context;
    private final WorldState world;

    public PoliticsSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void processMonth(long currentMinute) {
        Map<Long, SettlementPressure> pressures = SettlementPressure.evaluateAll(world, currentMinute);
        for (Realm realm : world.realms.values()) {
            checkSuccession(realm, currentMinute);
            fillVacantOffices(realm, currentMinute);
            applyLawConsequences(realm, pressures);
        }
        for (PoliticalFaction faction : world.politicalFactions.values()) {
            updateFaction(faction, pressures);
        }
    }

    public void checkSuccession(Realm realm, long currentMinute) {
        Title title = world.titles.get(realm.rulerTitleId);
        if (title == null) return;
        Person holder = title.holderPersonId == null ? null : world.people.get(title.holderPersonId);
        if (holder != null && holder.alive) {
            realm.rulerPersonId = holder.id;
            return;
        }

        Government government = world.governments.get(realm.governmentId);
        List<Person> candidates = eligibleCandidates(realm, currentMinute);
        if (candidates.isEmpty()) {
            realm.rulerPersonId = null;
            realm.stability = Math.max(0.0, realm.stability - 15.0);
            return;
        }

        Person heir;
        if (government != null && government.successionLaw == Government.SuccessionLaw.HEREDITARY
                && holder != null) {
            heir = candidates.stream()
                    .filter(candidate -> candidate.fatherId != null && candidate.fatherId == holder.id
                            || candidate.motherId != null && candidate.motherId == holder.id)
                    .max(Comparator.comparingInt(candidate -> candidate.getAge(currentMinute)))
                    .orElseGet(() -> bestCandidate(candidates));
        } else {
            heir = bestCandidate(candidates);
        }

        title.installHolder(heir.id, currentMinute,
                government != null && government.successionLaw == Government.SuccessionLaw.ELECTIVE
                        ? "ELECTION" : "SUCCESSION");
        realm.rulerPersonId = heir.id;
        realm.legitimacy = government != null
                && government.successionLaw == Government.SuccessionLaw.HEREDITARY ? 65.0 : 55.0;
        realm.stability = Math.max(0.0, realm.stability - 5.0);
        heir.type = Person.PersonType.NOBLE;

        for (Person candidate : candidates) {
            if (candidate.id == heir.id || candidate.personality.ambition < 60.0) continue;
            Claim claim = new Claim(world.idGenerator.next(), candidate.id, title.id,
                    25.0 + candidate.personality.ambition * 0.35,
                    Claim.ClaimSource.DISPUTED_SUCCESSION, currentMinute);
            world.claims.put(claim.id, claim);
            title.claimIds.add(claim.id);
            break;
        }

        for (Office office : world.offices.values()) {
            if (office.realmId == realm.id && office.type == Office.OfficeType.RULER) {
                office.holderPersonId = heir.id;
                office.appointedMinute = currentMinute;
            }
        }
        context.eventBus.publish(new WorldEvent(world.idGenerator.next(), currentMinute,
                "RULER_SUCCEEDED", realm.name + " is now ruled by " + heir.givenName + " " + heir.familyName));
    }

    private List<Person> eligibleCandidates(Realm realm, long currentMinute) {
        List<Person> candidates = new ArrayList<>();
        for (Person person : world.people.values()) {
            if (!person.alive || person.isChild(currentMinute) || person.isElderly(currentMinute)) continue;
            if (!belongsToRealm(person, realm.id)) continue;
            candidates.add(person);
        }
        return candidates;
    }

    private Person bestCandidate(List<Person> candidates) {
        return candidates.stream().max(Comparator
                .comparingDouble((Person p) -> successionScore(p))
                .thenComparingLong(p -> -p.id)).orElseThrow();
    }

    private double successionScore(Person person) {
        double nobleBonus = person.type == Person.PersonType.NOBLE ? 35.0 : 0.0;
        return nobleBonus + person.skills.stewardship + person.skills.diplomacy
                + person.skills.leadership + person.personality.ambition * 0.25
                + person.personality.honor * 0.15
                + (person.currentGoal == Person.Goal.SEEK_OFFICE ? 8.0 : 0.0);
    }

    private void fillVacantOffices(Realm realm, long currentMinute) {
        List<Person> candidates = eligibleCandidates(realm, currentMinute);
        for (Office office : world.offices.values()) {
            if (office.realmId != realm.id || office.type == Office.OfficeType.RULER) continue;
            Person holder = office.holderPersonId == null ? null : world.people.get(office.holderPersonId);
            if (holder != null && holder.alive) continue;
            Person best = candidates.stream().max(Comparator.comparingInt(person -> officeScore(person, office.type)))
                    .orElse(null);
            if (best != null) {
                office.holderPersonId = best.id;
                office.appointedMinute = currentMinute;
                best.officeId = office.id;
                best.currentGoal = Person.Goal.SEEK_OFFICE;
                best.goalReason = "Appointed " + office.type.name().toLowerCase() + " of " + realm.name;
                best.goalUrgency = 62;
                best.goalUpdatedMinute = currentMinute;
                best.rememberLifeEvent("Appointed " + office.type.name().toLowerCase()
                        + " of " + realm.name, currentMinute);
            }
        }
    }

    private int officeScore(Person person, Office.OfficeType type) {
        int skill = switch (type) {
            case STEWARD -> person.skills.stewardship;
            case MARSHAL -> person.skills.martial + person.skills.leadership;
            case CHANCELLOR -> person.skills.diplomacy;
            case SPYMASTER -> person.skills.intrigue;
            case RULER -> person.skills.leadership;
        };
        return skill + (int) Math.round(person.personality.ambition / 6.0)
                + (person.currentGoal == Person.Goal.SEEK_OFFICE ? 12 : 0);
    }

    private void applyLawConsequences(Realm realm, Map<Long, SettlementPressure> pressures) {
        Government government = world.governments.get(realm.governmentId);
        if (government == null) return;
        int tax = government.getLawLevel(Government.LawType.TAXATION);
        int autonomy = government.getLawLevel(Government.LawType.LOCAL_AUTONOMY);
        RealmPressure average = RealmPressure.of(world, realm.id, pressures);
        realm.stability += (autonomy - 1) * 0.4 - (tax - 1) * 0.7 - realm.warExhaustion * 0.01
                + (average.loyalty - 0.50) * 1.3 - average.taxStress * 0.55
                - average.foodStress * 0.35;
        realm.stability = Math.max(0.0, Math.min(100.0, realm.stability));
        realm.legitimacy = Math.max(0.0, Math.min(100.0,
                realm.legitimacy + (realm.stability - 50.0) * 0.01));
    }

    private void updateFaction(PoliticalFaction faction, Map<Long, SettlementPressure> pressures) {
        Realm realm = world.realms.get(faction.realmId);
        Government government = realm == null ? null : world.governments.get(realm.governmentId);
        if (realm == null || government == null || !faction.active) return;

        RealmPressure average = RealmPressure.of(world, realm.id, pressures);
        double pressure = (100.0 - realm.stability) * 0.35 + realm.warExhaustion * 0.25
                + Math.max(0.0, average.overall - 0.45) * 18.0;
        String driver;
        switch (faction.goal) {
            case LOWER_TAXES -> {
                pressure += government.getLawLevel(Government.LawType.TAXATION) * 12.0
                        + average.taxStress * 24.0 + average.foodStress * 8.0;
                driver = "tax stress " + percent(average.taxStress) + " and food stress "
                        + percent(average.foodStress);
            }
            case LOCAL_AUTONOMY -> {
                pressure += (3 - government.getLawLevel(Government.LawType.LOCAL_AUTONOMY)) * 10.0
                        + average.unrest * 24.0;
                driver = "local unrest " + percent(average.unrest);
            }
            case END_WAR -> {
                pressure += realm.warExhaustion * 0.5 + average.warStress * 26.0;
                driver = "war stress " + percent(average.warStress) + " and exhaustion "
                        + Math.round(realm.warExhaustion);
            }
            case CONTINUE_WAR -> {
                pressure += Math.max(0.0, 50.0 - realm.warExhaustion) * 0.35 - average.warStress * 14.0;
                driver = "war exhaustion is still only " + Math.round(realm.warExhaustion);
            }
            case INDEPENDENCE -> {
                pressure += (100.0 - realm.stability) * 0.45 + (1.0 - average.loyalty) * 26.0;
                driver = "loyalty is only " + percent(average.loyalty);
            }
            case REPLACE_RULER -> {
                pressure += 100.0 - realm.legitimacy + (1.0 - average.loyalty) * 18.0;
                driver = "legitimacy " + Math.round(realm.legitimacy) + " and loyalty "
                        + percent(average.loyalty);
            }
            case MERCHANT_PRIVILEGES -> {
                pressure += government.getLawLevel(Government.LawType.TARIFFS) * 8.0
                        + average.jobStress * 13.0 + average.crimeStress * 7.0;
                driver = "job stress " + percent(average.jobStress) + " and unsafe trade";
            }
            case NOBLE_PRIVILEGES -> {
                pressure += government.getLawLevel(Government.LawType.CONSCRIPTION) * 7.0
                        + average.warStress * 8.0;
                driver = "conscription and war burdens";
            }
            default -> driver = "general realm pressure";
        }
        double memberPower = 0.0;
        for (Long memberId : faction.memberPersonIds) {
            Person member = world.people.get(memberId);
            if (member != null && member.alive) {
                memberPower += 1.0 + member.personality.ambition / 100.0
                        + (member.currentGoal == Person.Goal.SEEK_OFFICE ? 0.35 : 0.0);
            }
        }
        faction.support = clamp(pressure + memberPower);
        faction.organization = clamp(faction.organization + (faction.support - 45.0) * 0.03);
        faction.militancy = clamp(faction.militancy + (faction.support > 70.0 ? 1.5 : -0.5));
        faction.pressureReason = "Support is driven by " + driver + "; realm pressure "
                + percent(average.overall) + ", stability " + Math.round(realm.stability)
                + ", member power " + String.format(java.util.Locale.ROOT, "%.1f", memberPower);
        if (faction.support > 80.0 && faction.organization > 50.0) {
            realm.stability = Math.max(0.0, realm.stability - 1.0);
        }
    }

    private record RealmPressure(double loyalty, double overall, double foodStress, double jobStress,
            double crimeStress, double taxStress, double warStress, double unrest) {
        static RealmPressure of(WorldState world, long realmId, Map<Long, SettlementPressure> pressures) {
            double loyalty = 0.0, overall = 0.0, food = 0.0, jobs = 0.0, crime = 0.0;
            double tax = 0.0, war = 0.0, unrest = 0.0;
            int count = 0;
            for (Settlement settlement : world.geography.getSettlements().values()) {
                if (settlement.controllerRealmId == null
                        || settlement.controllerRealmId.longValue() != realmId) continue;
                SettlementPressure pressure = pressures.get(settlement.id);
                if (pressure == null) continue;
                loyalty += pressure.loyalty;
                overall += pressure.overallPressure;
                food += pressure.foodStress;
                jobs += pressure.jobStress;
                crime += pressure.crimeStress;
                tax += pressure.taxStress;
                war += pressure.warStress;
                unrest += SettlementPressure.clamp01(settlement.unrest);
                count++;
            }
            if (count == 0) return new RealmPressure(0.5, 0.4, 0.3, 0.3, 0.2, 0.2, 0.0, 0.2);
            return new RealmPressure(loyalty / count, overall / count, food / count, jobs / count,
                    crime / count, tax / count, war / count, unrest / count);
        }
    }

    private static String percent(double value) {
        return Math.round(SettlementPressure.clamp01(value) * 100.0) + "%";
    }

    private boolean belongsToRealm(Person person, long realmId) {
        Long settlementId = person.homeSettlementId != null ? person.homeSettlementId : person.currentSettlementId;
        if (settlementId == null) return false;
        Settlement settlement = world.geography.getSettlement(settlementId);
        return settlement != null && settlement.controllerRealmId != null
                && settlement.controllerRealmId == realmId;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
