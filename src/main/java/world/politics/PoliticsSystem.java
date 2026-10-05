package world.politics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.Person;
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
        for (Realm realm : world.realms.values()) {
            checkSuccession(realm, currentMinute);
            fillVacantOffices(realm, currentMinute);
            applyLawConsequences(realm);
        }
        for (PoliticalFaction faction : world.politicalFactions.values()) {
            updateFaction(faction);
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
                "RULER_SUCCEEDED"));
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
                + person.personality.honor * 0.15;
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
            }
        }
    }

    private int officeScore(Person person, Office.OfficeType type) {
        return switch (type) {
            case STEWARD -> person.skills.stewardship;
            case MARSHAL -> person.skills.martial + person.skills.leadership;
            case CHANCELLOR -> person.skills.diplomacy;
            case SPYMASTER -> person.skills.intrigue;
            case RULER -> person.skills.leadership;
        };
    }

    private void applyLawConsequences(Realm realm) {
        Government government = world.governments.get(realm.governmentId);
        if (government == null) return;
        int tax = government.getLawLevel(Government.LawType.TAXATION);
        int autonomy = government.getLawLevel(Government.LawType.LOCAL_AUTONOMY);
        realm.stability += (autonomy - 1) * 0.4 - (tax - 1) * 0.7 - realm.warExhaustion * 0.01;
        realm.stability = Math.max(0.0, Math.min(100.0, realm.stability));
        realm.legitimacy = Math.max(0.0, Math.min(100.0,
                realm.legitimacy + (realm.stability - 50.0) * 0.01));
    }

    private void updateFaction(PoliticalFaction faction) {
        Realm realm = world.realms.get(faction.realmId);
        Government government = realm == null ? null : world.governments.get(realm.governmentId);
        if (realm == null || government == null || !faction.active) return;

        double pressure = (100.0 - realm.stability) * 0.35 + realm.warExhaustion * 0.25;
        switch (faction.goal) {
            case LOWER_TAXES -> pressure += government.getLawLevel(Government.LawType.TAXATION) * 12.0;
            case LOCAL_AUTONOMY -> pressure += (3 - government.getLawLevel(Government.LawType.LOCAL_AUTONOMY)) * 10.0;
            case END_WAR -> pressure += realm.warExhaustion * 0.5;
            case REPLACE_RULER -> pressure += 100.0 - realm.legitimacy;
            case MERCHANT_PRIVILEGES -> pressure += government.getLawLevel(Government.LawType.TARIFFS) * 8.0;
            case NOBLE_PRIVILEGES -> pressure += government.getLawLevel(Government.LawType.CONSCRIPTION) * 7.0;
        }
        double memberPower = 0.0;
        for (Long memberId : faction.memberPersonIds) {
            Person member = world.people.get(memberId);
            if (member != null && member.alive) {
                memberPower += 1.0 + member.personality.ambition / 100.0;
            }
        }
        faction.support = clamp(pressure + memberPower);
        faction.organization = clamp(faction.organization + (faction.support - 45.0) * 0.03);
        faction.militancy = clamp(faction.militancy + (faction.support > 70.0 ? 1.5 : -0.5));
        if (faction.support > 80.0 && faction.organization > 50.0) {
            realm.stability = Math.max(0.0, realm.stability - 1.0);
        }
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
