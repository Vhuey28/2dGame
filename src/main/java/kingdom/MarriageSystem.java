package kingdom;

import java.util.ArrayList;
import java.util.List;

public class MarriageSystem {

    public static List<Character> findEligibleSpouses(Character forCharacter, List<Kingdom> allKingdoms) {
        List<Character> eligible = new ArrayList<>();
        for (Kingdom k : allKingdoms) {
            eligible.addAll(collectEligibleFromKingdom(k, forCharacter));
        }
        return eligible;
    }

    private static List<Character> collectEligibleFromKingdom(Kingdom k, Character forCharacter) {
        List<Character> result = new ArrayList<>();
        addIfEligible(k.ruler, forCharacter, result);
        if (k.ruler != null) {
            for (Character child : k.ruler.children) {
                addIfEligible(child, forCharacter, result);
            }
        }
        return result;
    }

    private static void addIfEligible(Character candidate, Character forCharacter, List<Character> result) {
        if (candidate == null || candidate == forCharacter) return;
        if (!candidate.alive || candidate.spouse != null) return;
        if (candidate.age < 16) return;
        if (candidate.gender == forCharacter.gender) return;
        result.add(candidate);
    }

    public static int computeCompatibilityScore(Character a, Character b) {
        int score = 0;

        if (a.rulesKingdom != null && b.rulesKingdom != null) {
            Kingdom.DiplomaticRelation rel = a.rulesKingdom.relations.get(b.rulesKingdom);
            if (rel == Kingdom.DiplomaticRelation.ALLIANCE) score += 20;
            if (rel == Kingdom.DiplomaticRelation.WAR) score -= 40;
        }

        if (a.traits.contains(Character.Trait.KIND) && b.traits.contains(Character.Trait.CRUEL)) score -= 15;
        if (a.traits.contains(Character.Trait.AMBITIOUS) && b.traits.contains(Character.Trait.AMBITIOUS)) score += 10;

        score -= Math.abs(a.age - b.age) / 2;
        return score;
    }

    public static void marry(Character a, Character b) {
        a.spouse = b;
        b.spouse = a;
        a.adjustOpinion(b, 25);
        b.adjustOpinion(a, 25);

        if (a.rulesKingdom != null && b.rulesKingdom != null && a.rulesKingdom != b.rulesKingdom) {
            a.rulesKingdom.ruler.adjustOpinion(b.rulesKingdom.ruler, 10);
            b.rulesKingdom.ruler.adjustOpinion(a.rulesKingdom.ruler, 10);
        }
    }
}
