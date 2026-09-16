# Kingdom Phase 3 — Intrigue: Fabricated Claims, Assassination, Spy Networks

Builds on Phase 1 (`Character`, `Kingdom`, `War`, `KingdomSimulation`) and Phase 2 (opinion scoring, traits). This adds hidden, multi-tick schemes that can fail, succeed, or get discovered — the mechanic that makes "who's plotting against whom" a real layer instead of just wars and marriages.

---

## 1. Core scheme data model

### New file: `kingdom/Scheme.java`
```java
package kingdom;

public class Scheme {
    public enum Type { FABRICATE_CLAIM, ASSASSINATE, SPY_NETWORK }

    public Type type;
    public Character schemer;
    public Character target;        // the character being assassinated, or whose realm is being claimed against — null for SPY_NETWORK
    public Kingdom targetKingdom;    // the kingdom a claim/spy-network targets — null for ASSASSINATE
    public int progress = 0;
    public int durationTicks;        // months required to complete, once undiscovered
    public boolean discovered = false;
    public boolean active = true;

    public Scheme(Type type, Character schemer, Character target, Kingdom targetKingdom, int durationTicks) {
        this.type = type;
        this.schemer = schemer;
        this.target = target;
        this.targetKingdom = targetKingdom;
        this.durationTicks = durationTicks;
    }
}
```

### Additions to `Character.java`
```java
public List<Kingdom> claims = new ArrayList<>(); // kingdoms this character has a fabricated (or real) claim to throne over
```

### Additions to `Kingdom.java`
```java
// Espionage advantage this kingdom holds over another, built up by successful SPY_NETWORK schemes.
// Higher value = better at both scheming against them and detecting their schemes against you.
public Map<Kingdom, Integer> espionageAdvantage = new HashMap<>();

public int getEspionageAgainst(Kingdom other) {
    return espionageAdvantage.getOrDefault(other, 0);
}
```

---

## 2. The intrigue system — scheme lifecycle

### New file: `kingdom/IntrigueSystem.java`
```java
package kingdom;

import java.util.*;

public class IntrigueSystem {
    public List<Scheme> activeSchemes = new ArrayList<>();

    /** Call once per monthly tick from KingdomSimulation. */
    public void update(List<Kingdom> allKingdoms) {
        Iterator<Scheme> it = activeSchemes.iterator();
        while (it.hasNext()) {
            Scheme s = it.next();
            if (!s.active) { it.remove(); continue; }

            rollDetection(s);
            if (s.discovered) continue; // discovered schemes stall — see handleDiscovery, they don't auto-progress or auto-fail immediately

            s.progress++;
            if (s.progress >= s.durationTicks) {
                resolveScheme(s);
                s.active = false;
            }
        }
        activeSchemes.removeIf(s -> !s.active);
    }

    public boolean startScheme(Scheme scheme) {
        // One active scheme per schemer at a time — keeps this from being spammable
        boolean alreadyScheming = activeSchemes.stream().anyMatch(s -> s.schemer == scheme.schemer && s.active);
        if (alreadyScheming) return false;
        activeSchemes.add(scheme);
        return true;
    }

    private void rollDetection(Scheme s) {
        double baseDetectionChance = 0.03; // 3% per month baseline
        double schemerSkill = s.schemer.intrigue / 300.0; // higher intrigue = harder to catch
        double targetDefense = 0.0;

        if (s.target != null) {
            targetDefense += s.target.intrigue / 250.0;
        }
        if (s.targetKingdom != null && s.schemer.rulesKingdom != null) {
            // A target kingdom's espionage advantage over the schemer's kingdom makes them better at catching schemes
            targetDefense += s.targetKingdom.getEspionageAgainst(s.schemer.rulesKingdom) / 100.0;
        }

        double detectionChance = Math.max(0.01, baseDetectionChance - schemerSkill + targetDefense);
        if (Math.random() < detectionChance) {
            s.discovered = true;
            handleDiscovery(s);
        }
    }

    private void handleDiscovery(Scheme s) {
        // Discovery has real consequences — opinion hit, and for the most aggressive scheme types, a war-worthy grievance
        if (s.target != null) {
            s.target.adjustOpinion(s.schemer, -40);
        }
        if (s.type == Scheme.Type.ASSASSINATE && s.target != null && s.target.rulesKingdom != null
            && s.schemer.rulesKingdom != null) {
            // Being caught plotting murder is a legitimate reason for the victim's kingdom to declare war
            s.targetKingdomForWarGrievance(s); // see helper below — adds a claim-equivalent grievance
        }
        // Discovered schemes are aborted, not just paused — remove them next tick via active=false
        s.active = false;
    }

    private void resolveScheme(Scheme s) {
        double successChance = computeSuccessChance(s);
        boolean success = Math.random() < successChance;
        if (!success) return; // failed quietly — no discovery, just no effect. Distinct from being caught mid-scheme.

        switch (s.type) {
            case FABRICATE_CLAIM -> {
                if (!s.schemer.claims.contains(s.targetKingdom)) {
                    s.schemer.claims.add(s.targetKingdom);
                }
            }
            case ASSASSINATE -> {
                if (s.target != null) {
                    s.target.alive = false;
                    // Even a successful, undiscovered assassination often leaves suspicion — small universal opinion hit
                    // from anyone who had a positive opinion of the victim. Simplified here to a flat kingdom stability hit.
                    if (s.target.rulesKingdom != null) {
                        s.target.rulesKingdom.stability -= 15;
                    }
                }
            }
            case SPY_NETWORK -> {
                if (s.schemer.rulesKingdom != null && s.targetKingdom != null) {
                    s.schemer.rulesKingdom.espionageAdvantage.merge(s.targetKingdom, 15, Integer::sum);
                }
            }
        }
    }

    private double computeSuccessChance(Scheme s) {
        double base = switch (s.type) {
            case FABRICATE_CLAIM -> 0.7;
            case ASSASSINATE -> 0.4;      // harder — assassination should be the riskiest, highest-value scheme
            case SPY_NETWORK -> 0.8;
        };
        base += s.schemer.intrigue / 100.0;
        if (s.target != null) base -= s.target.intrigue / 150.0;
        return Math.max(0.05, Math.min(0.95, base));
    }
}
```

**On the `targetKingdomForWarGrievance` call above** — that's a helper you'll add to `Scheme` (or inline directly in `handleDiscovery`) that grants the victim's kingdom a `War.CasusBelli` option against the schemer's kingdom. Simplest implementation: extend `War.CasusBelli` with an `ASSASSINATION_ATTEMPT` value, and track "available grievances" on `Kingdom` the same way `claims` tracks fabricated-claim targets:
```java
// Kingdom.java addition
public Set<Kingdom> grievancesAgainst = new HashSet<>(); // kingdoms this kingdom has valid casus belli against

// In handleDiscovery, replace the placeholder call with:
if (s.type == Scheme.Type.ASSASSINATE && s.target != null && s.target.rulesKingdom != null && s.schemer.rulesKingdom != null) {
    s.target.rulesKingdom.grievancesAgainst.add(s.schemer.rulesKingdom);
}
```

---

## 3. AI deciding when to scheme

Add to `KingdomSimulation.runAiDecisions()` (alongside the existing war/alliance/marriage calls from Phase 2):
```java
considerSchemes(k);
```
```java
private void considerSchemes(Kingdom k) {
    if (k.ruler == null || !k.ruler.alive) return;
    if (Math.random() > 0.08) return; // schemes shouldn't be started every tick — keeps pacing reasonable

    // Assassination: ambitious/cruel rulers target whoever they have the most negative opinion of, if that person rules a rival kingdom
    if (k.ruler.traits.contains(Character.Trait.AMBITIOUS) || k.ruler.traits.contains(Character.Trait.CRUEL)) {
        Kingdom worstRival = findMostHatedRivalKingdom(k);
        if (worstRival != null && worstRival.ruler != null) {
            intrigueSystem.startScheme(new Scheme(Scheme.Type.ASSASSINATE, k.ruler, worstRival.ruler, null, 6));
            return;
        }
    }

    // Fabricate claim: kingdoms already sizing up a weaker neighbor (positive war score) build a claim before declaring,
    // rather than jumping straight to war — gives the player visible warning time via claims becoming known.
    for (Kingdom other : allKingdoms) {
        if (other == k) continue;
        if (k.relations.getOrDefault(other, Kingdom.DiplomaticRelation.PEACE) != Kingdom.DiplomaticRelation.PEACE) continue;
        if (computeWarScore(k, other) > 30 && !k.ruler.claims.contains(other)) {
            intrigueSystem.startScheme(new Scheme(Scheme.Type.FABRICATE_CLAIM, k.ruler, null, other, 8));
            return;
        }
    }

    // Spy network: kingdoms currently at war invest in intelligence against their enemy
    if (!k.activeWars.isEmpty()) {
        Kingdom enemy = k.activeWars.get(0).defender == k ? k.activeWars.get(0).attacker : k.activeWars.get(0).defender;
        intrigueSystem.startScheme(new Scheme(Scheme.Type.SPY_NETWORK, k.ruler, null, enemy, 10));
    }
}

private Kingdom findMostHatedRivalKingdom(Kingdom k) {
    Kingdom worst = null;
    int worstOpinion = 0; // only consider genuinely negative opinions, not just "least positive"
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
```
Add the field and call the new update method:
```java
// KingdomSimulation field
public IntrigueSystem intrigueSystem = new IntrigueSystem();

// In processMonthlyTick(), alongside the existing per-kingdom loop
intrigueSystem.update(allKingdoms);
```

---

## 4. Claims and grievances feeding back into war declarations

Phase 2's `computeWarScore` had no concept of legitimacy — Phase 3 gives fabricated claims and assassination grievances real weight:
```java
// Modify computeWarScore (Phase 2) to add this bonus
private int computeWarScore(Kingdom k, Kingdom target) {
    int score = 0;
    // ...existing Phase 2 scoring...

    // A valid claim or grievance makes war far more attractive — legitimate casus belli, not just opportunism
    if (k.ruler.claims.contains(target)) score += 40;
    if (k.grievancesAgainst.contains(target)) score += 30;

    return score;
}
```
And update `declareWar` to consume the claim/grievance and set the right `CasusBelli`:
```java
public void declareWar(Kingdom attacker, Kingdom defender, War.CasusBelli reason) {
    War.CasusBelli actualReason = reason;
    if (attacker.ruler.claims.contains(defender)) {
        actualReason = War.CasusBelli.CLAIM_THRONE;
        attacker.ruler.claims.remove(defender); // claim is spent once used
    } else if (attacker.grievancesAgainst.contains(defender)) {
        actualReason = War.CasusBelli.CONQUEST; // or add a dedicated enum value if you want it tracked distinctly
        attacker.grievancesAgainst.remove(defender);
    }

    War war = new War(attacker, defender, actualReason);
    attacker.activeWars.add(war);
    defender.activeWars.add(war);
    attacker.relations.put(defender, Kingdom.DiplomaticRelation.WAR);
    defender.relations.put(attacker, Kingdom.DiplomaticRelation.WAR);
}
```

---

## 5. Player-initiated schemes

Same principle as Phase 2's player diplomacy split — the player should *choose* to start a scheme, not have it auto-decided:
```java
// Called from a UI button, e.g. in a new "Intrigue" tab on the Kingdom screen
public boolean playerStartScheme(Scheme.Type type, Character target, Kingdom targetKingdom) {
    int durationTicks = switch (type) {
        case FABRICATE_CLAIM -> 8;
        case ASSASSINATE -> 6;
        case SPY_NETWORK -> 10;
    };
    Scheme scheme = new Scheme(type, playerKingdom.ruler, target, targetKingdom, durationTicks);
    return intrigueSystem.startScheme(scheme);
}
```
The UI should show the player their own active scheme's progress (`scheme.progress` / `scheme.durationTicks`) and whether it's been discovered — this is the one place where hiding information from the player would be a bad design call, since it's *their* scheme.

**One asymmetry worth deciding deliberately:** should the player see *other* kingdoms' active schemes against them, or only find out via the discovery/consequence events? Real CK-style intrigue keeps this hidden until either discovery or the scheme resolves — I'd recommend the same here (don't expose `activeSchemes` broadly in the UI, only surface events like "you've discovered a plot against you" when `rollDetection` catches something targeting the player's kingdom specifically).

---

## What's still stubbed after this phase
- Assassination currently has no bodyguard/defense stat on the target beyond raw `intrigue` — a dedicated "spymaster" council position (tying into a future council/vassal-appointment system) would be a natural Phase 4+ addition.
- `SPY_NETWORK`'s espionage advantage never decays — currently permanent once built. Consider a slow decay per year if it feels too snowbally in long games.
- No scheme exists yet for kingdom-vs-kingdom claim theft, sabotage, or fomenting revolt in a rival's territory — natural extensions of this same `Scheme` pattern if you want to expand the intrigue menu further.

## Suggested test order
1. Run a multi-kingdom simulation for a while and confirm `FABRICATE_CLAIM` schemes appear before wars against weaker neighbors (check `k.ruler.claims` populating).
2. Force a high-intrigue schemer against a low-intrigue target and confirm `ASSASSINATE` succeeds noticeably more often than the reverse matchup.
3. Confirm a discovered scheme actually damages opinion and (for assassination attempts) grants the victim's kingdom a real grievance usable in `declareWar`.
4. Test the player-initiated path end to end — start a scheme via `playerStartScheme`, watch it progress over several monthly ticks, confirm resolution applies correctly.