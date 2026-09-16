package kingdom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KingdomSimulation {
    public List<Kingdom> allKingdoms = new ArrayList<>();
    public Kingdom playerKingdom;
    public IntrigueSystem intrigueSystem = new IntrigueSystem();
    public List<String> eventLog = new ArrayList<>();

    private int tickTimer = 0;
    private int monthCounter = 0;
    private int totalMonthsElapsed = 0;
    private static final int TICKS_PER_MONTH = 3600;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int MAX_LOG_SIZE = 500;

    public KingdomSimulation() {
        intrigueSystem.onEvent = this::logEvent;
    }

    public void update() {
        tickTimer++;
        if (tickTimer >= TICKS_PER_MONTH) {
            tickTimer = 0;
            processMonthlyTick();
        }
    }

    public void forceMonthlyTickForTesting() {
        processMonthlyTick();
    }

    private void logEvent(String message) {
        eventLog.add("[Month " + totalMonthsElapsed + "] " + message);
        if (eventLog.size() > MAX_LOG_SIZE) {
            eventLog.remove(0);
        }
    }

    private void processMonthlyTick() {
        totalMonthsElapsed++;
        for (Kingdom k : allKingdoms) {
            collectIncome(k);
            if (k != playerKingdom) {
                runAiDecisions(k);
            }
            checkSuccession(k);
        }
        intrigueSystem.update(allKingdoms);
        maybeProcessYearlyTick();
    }

    private void maybeProcessYearlyTick() {
        monthCounter++;
        if (monthCounter >= MONTHS_PER_YEAR) {
            monthCounter = 0;
            processYearlyTick();
        }
    }

    private void collectIncome(Kingdom k) {
        k.treasury += k.getTotalIncome();
        k.influence += 1;
    }

    private void runAiDecisions(Kingdom k) {
        if (k.ruler == null || !k.ruler.alive) return;

        Kingdom bestWarTarget = null;
        int bestWarScore = Integer.MIN_VALUE;
        Kingdom bestAllianceTarget = null;
        int bestAllianceScore = Integer.MIN_VALUE;

        for (Kingdom other : allKingdoms) {
            if (other == k) continue;
            Kingdom.DiplomaticRelation rel = k.relations.getOrDefault(other, Kingdom.DiplomaticRelation.PEACE);

            if (rel == Kingdom.DiplomaticRelation.PEACE) {
                int warScore = computeWarScore(k, other);
                if (warScore > bestWarScore) {
                    bestWarScore = warScore;
                    bestWarTarget = other;
                }

                int allianceScore = computeAllianceScore(k, other);
                if (allianceScore > bestAllianceScore) {
                    bestAllianceScore = allianceScore;
                    bestAllianceTarget = other;
                }
            }
        }

        if (k.activeWars.isEmpty() && bestWarTarget != null && bestWarScore > 60) {
            declareWar(k, bestWarTarget, War.CasusBelli.CONQUEST);
        } else if (bestAllianceTarget != null && bestAllianceScore > 50) {
            proposeAlliance(k, bestAllianceTarget);
        }

        considerSchemes(k);
        considerMarriageProposals(k);
    }

    private int computeWarScore(Kingdom k, Kingdom target) {
        int score = 0;

        if (k.ruler == null || target.ruler == null) {
            return score;
        }

        int strengthRatio = target.getTotalLevy() == 0 ? 100 : (k.getTotalLevy() * 100) / target.getTotalLevy();
        score += (strengthRatio - 100) / 2;

        score += k.ruler.getTraitWarModifier();
        score -= k.ruler.getOpinionOf(target.ruler) / 2;
        score -= (100 - k.stability) / 3;

        if (k.ruler.claims.contains(target)) score += 40;
        if (k.grievancesAgainst.contains(target)) score += 30;

        if (k.relations.get(target) == Kingdom.DiplomaticRelation.ALLIANCE) score -= 1000;

        return score;
    }

    private int computeAllianceScore(Kingdom k, Kingdom target) {
        int score = 0;

        if (k.ruler == null || target.ruler == null) {
            return score;
        }

        score += k.ruler.getOpinionOf(target.ruler);
        score += k.ruler.getTraitAllianceModifier();

        if (!k.activeWars.isEmpty()) score += 20;

        Kingdom.DiplomaticRelation rel = k.relations.get(target);
        if (rel == Kingdom.DiplomaticRelation.ALLIANCE || rel == Kingdom.DiplomaticRelation.WAR) score -= 1000;

        return score;
    }

    private void proposeAlliance(Kingdom k, Kingdom target) {
        int targetScore = computeAllianceScore(target, k);
        if (targetScore > 20) {
            k.relations.put(target, Kingdom.DiplomaticRelation.ALLIANCE);
            target.relations.put(k, Kingdom.DiplomaticRelation.ALLIANCE);
            k.ruler.adjustOpinion(target.ruler, 15);
            target.ruler.adjustOpinion(k.ruler, 15);
            logEvent(k.name + " and " + target.name + " formed an alliance");
        }
    }

    public boolean playerProposeAlliance(Kingdom target) {
        if (playerKingdom == null || target == null) {
            return false;
        }

        int targetScore = computeAllianceScore(target, playerKingdom);
        boolean accepted = targetScore > 0;
        if (accepted) {
            playerKingdom.relations.put(target, Kingdom.DiplomaticRelation.ALLIANCE);
            target.relations.put(playerKingdom, Kingdom.DiplomaticRelation.ALLIANCE);
            logEvent(playerKingdom.name + " and " + target.name + " formed an alliance");
        }
        return accepted;
    }

    public void playerDeclareWar(Kingdom target, War.CasusBelli reason) {
        if (playerKingdom == null || target == null) {
            return;
        }
        declareWar(playerKingdom, target, reason);
    }

    private void considerMarriageProposals(Kingdom k) {
        Character candidate = findUnmarriedEligibleCharacter(k);
        if (candidate == null) return;
        if (Math.random() > 0.1) return;

        List<Character> pool = MarriageSystem.findEligibleSpouses(candidate, allKingdoms);
        Character best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Character c : pool) {
            int score = MarriageSystem.computeCompatibilityScore(candidate, c);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != null && bestScore > 10) {
            MarriageSystem.marry(candidate, best);
            logEvent(candidate.name + " married " + best.name);
        }
    }

    private Character findUnmarriedEligibleCharacter(Kingdom k) {
        if (k.ruler != null && k.ruler.spouse == null && k.ruler.age >= 16) return k.ruler;
        if (k.ruler != null) {
            for (Character child : k.ruler.children) {
                if (child.alive && child.spouse == null && child.age >= 16) return child;
            }
        }
        return null;
    }

    public void declareWar(Kingdom attacker, Kingdom defender, War.CasusBelli reason) {
        if (attacker == null || defender == null || attacker == defender) {
            return;
        }

        War.CasusBelli actualReason = reason;
        if (attacker.ruler != null && attacker.ruler.claims.contains(defender)) {
            actualReason = War.CasusBelli.CLAIM_THRONE;
            attacker.ruler.claims.remove(defender);
        } else if (attacker.grievancesAgainst.contains(defender)) {
            actualReason = War.CasusBelli.ASSASSINATION_ATTEMPT;
            attacker.grievancesAgainst.remove(defender);
        }

        War war = new War(attacker, defender, actualReason);
        attacker.activeWars.add(war);
        defender.activeWars.add(war);
        attacker.relations.put(defender, Kingdom.DiplomaticRelation.WAR);
        defender.relations.put(attacker, Kingdom.DiplomaticRelation.WAR);
        logEvent(attacker.name + " declared war on " + defender.name + " (" + actualReason + ")");
    }

    private void checkSuccession(Kingdom k) {
        if (k.ruler == null || k.ruler.alive) {
            return;
        }

        Character heir = determineHeir(k);
        if (heir != null) {
            k.ruler = heir;
            heir.rulesKingdom = k;
            k.stability -= 10;
            logEvent(heir.name + " succeeded to the throne of " + k.name);
        }
    }

    private Character determineHeir(Kingdom k) {
        if (k.ruler == null) {
            return null;
        }

        if (k.successionLaw == Kingdom.SuccessionLaw.PRIMOGENITURE) {
            return k.ruler.children.stream()
                .filter(c -> c.alive)
                .max(Comparator.comparingInt(c -> c.age))
                .orElse(null);
        }

        return k.ruler.children.stream().filter(c -> c.alive).findFirst().orElse(null);
    }

    private void considerSchemes(Kingdom k) {
        if (k.ruler == null || !k.ruler.alive) return;
        if (Math.random() > 0.08) return;

        if (k.ruler.traits.contains(Character.Trait.AMBITIOUS) || k.ruler.traits.contains(Character.Trait.CRUEL)) {
            Kingdom worstRival = findMostHatedRivalKingdom(k);
            if (worstRival != null && worstRival.ruler != null) {
                intrigueSystem.startScheme(new Scheme(Scheme.Type.ASSASSINATE, k.ruler, worstRival.ruler, null, 6));
                return;
            }
        }

        for (Kingdom other : allKingdoms) {
            if (other == k) continue;
            if (k.relations.getOrDefault(other, Kingdom.DiplomaticRelation.PEACE) != Kingdom.DiplomaticRelation.PEACE) continue;
            if (computeWarScore(k, other) > 30 && !k.ruler.claims.contains(other)) {
                intrigueSystem.startScheme(new Scheme(Scheme.Type.FABRICATE_CLAIM, k.ruler, null, other, 8));
                return;
            }
        }

        if (!k.activeWars.isEmpty()) {
            Kingdom enemy = k.activeWars.get(0).defender == k ? k.activeWars.get(0).attacker : k.activeWars.get(0).defender;
            intrigueSystem.startScheme(new Scheme(Scheme.Type.SPY_NETWORK, k.ruler, null, enemy, 10));
        }
    }

    private Kingdom findMostHatedRivalKingdom(Kingdom k) {
        Kingdom worst = null;
        int worstOpinion = 0;
        for (Kingdom other : allKingdoms) {
            if (other == k || other.ruler == null) continue;
            int opinion = k.ruler.getOpinionOf(other.ruler);
            if (opinion < worstOpinion) {
                worstOpinion = opinion;
                worst = other;
            }
        }
        return worst;
    }

    public boolean playerStartScheme(Scheme.Type type, Character target, Kingdom targetKingdom) {
        int durationTicks = switch (type) {
            case FABRICATE_CLAIM -> 8;
            case ASSASSINATE -> 6;
            case SPY_NETWORK -> 10;
        };
        Scheme scheme = new Scheme(type, playerKingdom.ruler, target, targetKingdom, durationTicks);
        return intrigueSystem.startScheme(scheme);
    }

    private void processYearlyTick() {
        for (Kingdom k : allKingdoms) {
            List<Character> allCharacters = collectAllCharacters(k);
            for (Character c : allCharacters) {
                if (!c.alive) continue;
                c.age++;
                checkNaturalDeath(c);
                checkChildbirth(c);
            }
        }
    }

    private List<Character> collectAllCharacters(Kingdom k) {
        List<Character> all = new ArrayList<>();
        if (k.ruler != null) {
            all.add(k.ruler);
            all.addAll(k.ruler.children);
        }
        return all;
    }

    private void checkNaturalDeath(Character c) {
        if (c.age < 50) return;
        double deathChance = (c.age - 50) * 0.01;
        if (Math.random() < deathChance) {
            c.alive = false;
            logEvent(c.name + " died of old age at " + c.age);
        }
    }

    private void checkChildbirth(Character c) {
        if (c.spouse == null || !c.spouse.alive) return;
        if (c.gender != Character.Gender.FEMALE) return;
        if (c.age < 18 || c.age > 45) return;
        if (Math.random() < 0.15) {
            Character child = new Character();
            child.name = "Child of " + c.name;
            child.age = 0;
            child.gender = Math.random() < 0.5 ? Character.Gender.MALE : Character.Gender.FEMALE;
            child.father = c.spouse;
            child.mother = c;
            child.rulesKingdom = c.rulesKingdom;
            child.liege = c.liege;
            c.children.add(child);
            c.spouse.children.add(child);
            logEvent(c.name + " gave birth to " + child.name);
        }
    }
}
