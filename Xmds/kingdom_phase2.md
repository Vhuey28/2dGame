# Kingdom Phase 2 — Real Diplomacy AI, Trait-Driven Behavior, Marriage & Succession

Builds directly on the Phase 1 classes (`Character`, `Kingdom`, `War`, `KingdomSimulation`). This replaces the random-roll stubs with actual scoring, and adds the marriage/aging systems that make succession mean something.

---

## 1. Trait-driven diplomacy modifiers

Add to `Character.java`:
```java
public int getTraitWarModifier() {
    int mod = 0;
    if (traits.contains(Trait.AMBITIOUS)) mod += 20;
    if (traits.contains(Trait.CONTENT)) mod -= 20;
    if (traits.contains(Trait.CRUEL)) mod += 10;
    if (traits.contains(Trait.KIND)) mod -= 10;
    if (traits.contains(Trait.CRAVEN)) mod -= 25;
    if (traits.contains(Trait.BRAVE)) mod += 10;
    return mod;
}

public int getTraitAllianceModifier() {
    int mod = 0;
    if (traits.contains(Trait.GENEROUS)) mod += 15;
    if (traits.contains(Trait.GREEDY)) mod -= 15;
    if (traits.contains(Trait.JUST)) mod += 10;
    if (traits.contains(Trait.ARBITRARY)) mod -= 10;
    return mod;
}
```
These are deliberately simple linear modifiers — the point isn't sophistication yet, it's giving traits *any* mechanical weight so "this ruler is Ambitious" actually changes AI behavior instead of being flavor text.

---

## 2. Opinion-weighted scoring — replacing the random rolls

Replace `KingdomSimulation.runAiDecisions()` and its two stub methods entirely:

```java
private void runAiDecisions(Kingdom k) {
    if (k.ruler == null || !k.ruler.alive) return;

    // Evaluate every kingdom AI could plausibly act toward, score each possible action, take the best if it clears a threshold
    Kingdom bestWarTarget = null;
    int bestWarScore = Integer.MIN_VALUE;
    Kingdom bestAllianceTarget = null;
    int bestAllianceScore = Integer.MIN_VALUE;

    for (Kingdom other : allKingdoms) {
        if (other == k) continue;
        Kingdom.DiplomaticRelation rel = k.relations.getOrDefault(other, Kingdom.DiplomaticRelation.PEACE);

        if (rel == Kingdom.DiplomaticRelation.PEACE) {
            int warScore = computeWarScore(k, other);
            if (warScore > bestWarScore) { bestWarScore = warScore; bestWarTarget = other; }

            int allianceScore = computeAllianceScore(k, other);
            if (allianceScore > bestAllianceScore) { bestAllianceScore = allianceScore; bestAllianceTarget = other; }
        }
    }

    // Thresholds prevent AI from acting on marginal scores every single tick — tune these to taste
    if (k.activeWars.isEmpty() && bestWarTarget != null && bestWarScore > 60) {
        declareWar(k, bestWarTarget, War.CasusBelli.CONQUEST);
    } else if (bestAllianceTarget != null && bestAllianceScore > 50) {
        proposeAlliance(k, bestAllianceTarget);
    }

    considerMarriageProposals(k); // section 3
}

private int computeWarScore(Kingdom k, Kingdom target) {
    int score = 0;

    // Military calculus — favor picking on weaker kingdoms, avoid stronger ones
    int strengthRatio = target.getTotalLevy() == 0 ? 100 : (k.getTotalLevy() * 100) / target.getTotalLevy();
    score += (strengthRatio - 100) / 2; // positive if k is stronger, negative if weaker

    // Ruler personality
    score += k.ruler.getTraitWarModifier();

    // Opinion of the target's ruler — attacking someone you like is a hard sell
    score -= k.ruler.getOpinionOf(target.ruler) / 2;

    // Low stability makes rulers risk-averse about opening new wars
    score -= (100 - k.stability) / 3;

    // Existing alliance with the target makes war unthinkable — hard block, not just a penalty
    if (k.relations.get(target) == Kingdom.DiplomaticRelation.ALLIANCE) score -= 1000;

    return score;
}

private int computeAllianceScore(Kingdom k, Kingdom target) {
    int score = 0;
    score += k.ruler.getOpinionOf(target.ruler);
    score += k.ruler.getTraitAllianceModifier();

    // A kingdom already at war with someone is more receptive to alliances (mutual defense logic)
    if (!k.activeWars.isEmpty()) score += 20;

    // Already allied or at war with them — no point re-proposing
    Kingdom.DiplomaticRelation rel = k.relations.get(target);
    if (rel == Kingdom.DiplomaticRelation.ALLIANCE || rel == Kingdom.DiplomaticRelation.WAR) score -= 1000;

    return score;
}

private void proposeAlliance(Kingdom k, Kingdom target) {
    // Phase 2 simplification: AI-to-AI alliances auto-accept if the target's own score for k is also positive —
    // avoids needing a full back-and-forth negotiation UI yet. Player-initiated proposals should go through
    // a real accept/reject UI instead of this auto-accept path (see section 5).
    int targetScore = computeAllianceScore(target, k);
    if (targetScore > 20) {
        k.relations.put(target, Kingdom.DiplomaticRelation.ALLIANCE);
        target.relations.put(k, Kingdom.DiplomaticRelation.ALLIANCE);
        k.ruler.adjustOpinion(target.ruler, 15);
        target.ruler.adjustOpinion(k.ruler, 15);
    }
}
```
Delete the old `considerDeclaringWar`/`considerAlliance` stub methods from Phase 1 — they're fully replaced by the scoring versions above.

---

## 3. Marriage system

### New file: `kingdom/MarriageSystem.java`
```java
package kingdom;

import java.util.*;

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
        if (candidate.age < 16) return; // adjust minimum age to your game's tone
        if (candidate.gender == forCharacter.gender) return; // remove this line if same-sex marriage should be allowed
        result.add(candidate);
    }

    public static int computeCompatibilityScore(Character a, Character b) {
        int score = 0;
        // Political value: marrying into a stronger/allied kingdom is more attractive
        if (a.rulesKingdom != null && b.rulesKingdom != null) {
            Kingdom.DiplomaticRelation rel = a.rulesKingdom.relations.get(b.rulesKingdom);
            if (rel == Kingdom.DiplomaticRelation.ALLIANCE) score += 20;
            if (rel == Kingdom.DiplomaticRelation.WAR) score -= 40;
        }
        // Personality compatibility — simple opposite-trait friction / same-trait harmony
        if (a.traits.contains(Character.Trait.KIND) && b.traits.contains(Character.Trait.CRUEL)) score -= 15;
        if (a.traits.contains(Character.Trait.AMBITIOUS) && b.traits.contains(Character.Trait.AMBITIOUS)) score += 10;
        // Age gap penalty — keeps AI from proposing wildly mismatched pairings
        score -= Math.abs(a.age - b.age) / 2;
        return score;
    }

    public static void marry(Character a, Character b) {
        a.spouse = b;
        b.spouse = a;
        a.adjustOpinion(b, 25);
        b.adjustOpinion(a, 25);
        // If the two are rulers of different kingdoms, marriage is a soft diplomatic signal —
        // nudge relations toward friendlier, though not a full alliance on its own.
        if (a.rulesKingdom != null && b.rulesKingdom != null && a.rulesKingdom != b.rulesKingdom) {
            a.rulesKingdom.ruler.adjustOpinion(b.rulesKingdom.ruler, 10);
            b.rulesKingdom.ruler.adjustOpinion(a.rulesKingdom.ruler, 10);
        }
    }
}
```

### Wire into `KingdomSimulation`
```java
private void considerMarriageProposals(Kingdom k) {
    Character candidate = findUnmarriedEligibleCharacter(k);
    if (candidate == null) return;
    if (Math.random() > 0.1) return; // don't re-roll every single tick — keeps this from spamming proposals

    List<Character> pool = MarriageSystem.findEligibleSpouses(candidate, allKingdoms);
    Character best = null;
    int bestScore = Integer.MIN_VALUE;
    for (Character c : pool) {
        int score = MarriageSystem.computeCompatibilityScore(candidate, c);
        if (score > bestScore) { bestScore = score; best = c; }
    }
    if (best != null && bestScore > 10) {
        MarriageSystem.marry(candidate, best);
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
```

---

## 4. Aging, births, and natural death — the yearly tick

Monthly ticks handle income/AI decisions; aging needs a slower cadence layered on top.

Add to `KingdomSimulation`:
```java
private int monthCounter = 0;
private static final int MONTHS_PER_YEAR = 12;

// Call this from processMonthlyTick(), at the end
private void maybeProcessYearlyTick() {
    monthCounter++;
    if (monthCounter >= MONTHS_PER_YEAR) {
        monthCounter = 0;
        processYearlyTick();
    }
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
    double deathChance = (c.age - 50) * 0.01; // 1% per year past 50, tune to taste
    if (Math.random() < deathChance) {
        c.alive = false;
    }
}

private void checkChildbirth(Character c) {
    if (c.spouse == null || !c.spouse.alive) return;
    if (c.gender != Character.Gender.FEMALE) return; // only process from one side of the couple to avoid double-birth
    if (c.age < 18 || c.age > 45) return;
    if (Math.random() < 0.15) { // 15% chance per year for a married woman of childbearing age
        Character child = new Character();
        child.name = "Child of " + c.name; // placeholder — hook up a real name generator later
        child.age = 0;
        child.gender = Math.random() < 0.5 ? Character.Gender.MALE : Character.Gender.FEMALE;
        child.father = c.spouse;
        child.mother = c;
        c.children.add(child);
        c.spouse.children.add(child);
    }
}
```
Call `maybeProcessYearlyTick()` at the end of `processMonthlyTick()`.

---

## 5. Player-initiated diplomacy — the one place AI logic shouldn't auto-decide

Everything above governs AI-to-AI behavior. For the **player's own kingdom**, diplomatic actions should be player choices made through the UI, not auto-resolved by the scoring functions — the scoring functions are useful here only for showing the player *predicted* outcomes ("this kingdom seems favorable to an alliance") rather than deciding for them.

```java
// Called from a UI button — proposing FROM the player's kingdom TO another
public boolean playerProposeAlliance(Kingdom target) {
    int targetScore = computeAllianceScore(target, playerKingdom); // does the AI target want this?
    boolean accepted = targetScore > 0; // player proposals face a lower bar than AI-to-AI, reflecting player agency
    if (accepted) {
        playerKingdom.relations.put(target, Kingdom.DiplomaticRelation.ALLIANCE);
        target.relations.put(playerKingdom, Kingdom.DiplomaticRelation.ALLIANCE);
    }
    return accepted; // UI shows accept/reject feedback based on this
}

public void playerDeclareWar(Kingdom target, War.CasusBelli reason) {
    declareWar(playerKingdom, target, reason); // player wars don't need AI acceptance — declaring war is unilateral
}
```
Wire these into the Kingdom screen's Diplomacy tab buttons from the Phase 1 roadmap — each button calls the corresponding method and shows the result (accepted/rejected, or an immediate war declaration).

---

## What's still stubbed after this phase (future work, not needed now)
- Real intrigue actions (scheme to fabricate a claim, assassination) — Phase 3 per the original roadmap.
- Name generation for children (currently placeholder strings) — worth a simple name-pool generator whenever you want it to feel less mechanical.
- Same-kingdom marriage restrictions (currently nothing stops a kingdom's own family members from being in the eligible pool if you remove the gender-filter line) — add a shared-ancestor check if you want to prevent that.
- Elective succession is still a stub in Phase 1's `determineHeir()` — worth revisiting once you want elective kingdoms to feel meaningfully different from primogeniture ones (currently it just picks the first surviving child either way).

## Suggested test order
1. Run several months of simulated time with just 2-3 AI kingdoms and confirm wars/alliances trigger sensibly (stronger kingdoms attacking weaker ones, allied kingdoms never going to war).
2. Confirm trait modifiers are visibly changing behavior — try giving one kingdom's ruler `AMBITIOUS` and another `CONTENT`, confirm the ambitious one starts more wars over a long simulated run.
3. Let a full year pass and confirm characters age, marriages form, and children are born.
4. Kill a ruler manually (`ruler.alive = false`) and confirm succession correctly picks the oldest living child as heir.