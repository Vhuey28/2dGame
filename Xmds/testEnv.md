# Test Environment for Kingdom + Party/Battle Systems

Two separate tools, because these systems have very different testing needs:
- **Kingdom simulation is pure data/logic** — no rendering, no Swing dependency. It can be tested standalone in a console app that runs years of simulated time in seconds, which is far faster than waiting through real monthly ticks in the actual game.
- **Party/Battle needs the real game loop** — spawning, movement, the deployment screen, battle-map transitions all depend on `GamePanel`. This gets an in-game debug overlay and hotkeys instead.

Build the event-logging addition first (section 1) — both tools depend on it for actually seeing what happened, rather than just staring at final state.

---

## 1. Prerequisite — event logging in KingdomSimulation

Right now nothing in the Kingdom system records *what happened*, only current state. Add a simple log:

```java
// KingdomSimulation field
public List<String> eventLog = new ArrayList<>();
private static final int MAX_LOG_SIZE = 500; // trim old entries so this doesn't grow unbounded over a long session

private void logEvent(String message) {
    eventLog.add("[Month " + totalMonthsElapsed + "] " + message);
    if (eventLog.size() > MAX_LOG_SIZE) eventLog.remove(0);
}

private int totalMonthsElapsed = 0; // increment in processMonthlyTick()
```

Add `logEvent(...)` calls at every meaningful state change across the classes you've already built:
```java
// In declareWar()
logEvent(attacker.name + " declared war on " + defender.name + " (" + actualReason + ")");

// In proposeAlliance(), right after the alliance is formed
logEvent(k.name + " and " + target.name + " formed an alliance");

// In MarriageSystem.marry() — pass the log call in, or log from the caller in KingdomSimulation right after marry() returns
logEvent(candidate.name + " married " + best.name);

// In checkChildbirth(), right after adding the child
logEvent(c.name + " gave birth to " + child.name);

// In checkNaturalDeath(), right after setting alive = false
logEvent(c.name + " died of old age at " + c.age);

// In checkSuccession(), right after the heir takes over
logEvent(heir.name + " succeeded to the throne of " + k.name);

// In IntrigueSystem.resolveScheme() — pass a logger callback in, or have IntrigueSystem return a result KingdomSimulation logs
// In IntrigueSystem.handleDiscovery() — same idea, log discoveries specifically since they're the most interesting events
```
For the `IntrigueSystem` calls specifically, since that class doesn't currently have a reference back to `KingdomSimulation`, the cleanest approach is a simple callback interface:
```java
// IntrigueSystem field
public java.util.function.Consumer<String> onEvent = msg -> {}; // no-op by default, KingdomSimulation wires a real logger in

// call onEvent.accept("...") wherever resolveScheme/handleDiscovery would want to log
```
```java
// KingdomSimulation constructor or init
intrigueSystem.onEvent = this::logEvent;
```

---

## 2. Standalone console test harness for the Kingdom system

This runs entirely outside the game — no `GamePanel`, no rendering, just the simulation classes. Create `kingdom/KingdomTestHarness.java`:

```java
package kingdom;

import java.util.*;

public class KingdomTestHarness {
    public static void main(String[] args) {
        KingdomSimulation sim = buildTestScenario();

        int monthsToSimulate = 240; // 20 years — enough for wars, marriages, successions, and schemes to all surface
        System.out.println("=== Simulating " + monthsToSimulate + " months (" + (monthsToSimulate/12) + " years) ===\n");

        for (int i = 0; i < monthsToSimulate; i++) {
            // Directly call the tick logic rather than the real-time-gated update() — bypasses the
            // TICKS_PER_MONTH frame-based timer entirely so this runs at console speed, not game speed.
            sim.forceMonthlyTickForTesting();
        }

        System.out.println("=== Event Log ===");
        for (String event : sim.eventLog) {
            System.out.println(event);
        }

        System.out.println("\n=== Final State ===");
        for (Kingdom k : sim.allKingdoms) {
            System.out.println(k.name + ": treasury=" + k.treasury + ", stability=" + k.stability
                + ", ruler=" + (k.ruler != null ? k.ruler.name + " (age " + k.ruler.age + ")" : "NONE")
                + ", at war with " + k.activeWars.size() + " kingdom(s)");
        }
    }

    private static KingdomSimulation buildTestScenario() {
        KingdomSimulation sim = new KingdomSimulation();

        Kingdom aggressive = new Kingdom();
        aggressive.name = "Aggravia";
        aggressive.ruler = makeCharacter("King Vorn", 35, Character.Gender.MALE, Character.Trait.AMBITIOUS, Character.Trait.CRUEL);
        aggressive.territories.add(makeTerritory("Vorn's Hold", 5000, 200));

        Kingdom peaceful = new Kingdom();
        peaceful.name = "Serenholm";
        peaceful.ruler = makeCharacter("Queen Alys", 30, Character.Gender.FEMALE, Character.Trait.CONTENT, Character.Trait.KIND);
        peaceful.territories.add(makeTerritory("Alys' Reach", 2000, 60)); // deliberately weaker — should draw Aggravia's aggression

        Kingdom rival = new Kingdom();
        rival.name = "Ironmere";
        rival.ruler = makeCharacter("King Bran", 40, Character.Gender.MALE, Character.Trait.GENEROUS, Character.Trait.JUST);
        rival.territories.add(makeTerritory("Bran's Keep", 4500, 180));

        sim.allKingdoms.add(aggressive);
        sim.allKingdoms.add(peaceful);
        sim.allKingdoms.add(rival);
        sim.playerKingdom = peaceful; // treat Serenholm as "the player" for this test — irrelevant for a pure-AI run, but keeps AI logic from ever skipping it as playerKingdom

        return sim;
    }

    private static Character makeCharacter(String name, int age, Character.Gender gender, Character.Trait... traits) {
        Character c = new Character();
        c.name = name;
        c.age = age;
        c.gender = gender;
        c.traits.addAll(Arrays.asList(traits));
        return c;
    }

    private static Territory makeTerritory(String name, int population, int garrison) {
        Territory t = new Territory();
        t.name = name;
        t.population = population;
        t.garrisonSize = garrison;
        return t;
    }
}
```

Add the bypass method to `KingdomSimulation` (keeps the real `update()` timer-gated for actual gameplay, while giving the test harness a way to skip straight to tick logic):
```java
/** Test-only entry point — runs one monthly tick immediately, bypassing the real-time TICKS_PER_MONTH gate. */
public void forceMonthlyTickForTesting() {
    processMonthlyTick();
}
```

### Running it
Since this has its own `main()`, run it directly without touching your game's build script:
```bash
javac -d /tmp/kingdomtest src/kingdom/*.java
java -cp /tmp/kingdomtest kingdom.KingdomTestHarness
```
(adjust paths to match wherever your Maven-restructured source lives, e.g. `src/main/java/kingdom/*.java` post-migration)

This prints every war, alliance, marriage, birth, death, succession, and discovered scheme chronologically, plus final kingdom states — you can read through 20 years of simulated politics in under a second of actual run time. **This is genuinely the fastest way to catch Kingdom-system bugs** — if Aggravia (aggressive/cruel ruler, stronger military) never declares war on the deliberately weaker Serenholm over 20 years, that's a real signal something's off in `computeWarScore`, and you'll know in seconds instead of playing through real-time monthly ticks in the actual game.

### Suggested scenario variations to test different systems
- **Test alliances**: give two kingdoms `GENEROUS` rulers with no existing hostility — confirm they ally within the simulated period.
- **Test marriage/succession**: start with a childless ruler, run long enough to see a marriage, a birth, the child aging to adulthood, and (force it by setting `ruler.alive = false` partway through) confirm succession picks that child correctly.
- **Test intrigue discovery**: give one kingdom a very high-`intrigue` ruler and the target a very low one — confirm scheme success rate visibly differs from the reverse matchup by re-running with traits swapped.

---

## 3. In-game debug overlay + hotkeys for Party/Battle testing

This needs the real game loop, so it's a `GamePanel` addition.

### 3a. Overlay toggle and fields
```java
private boolean debugOverlayVisible = false;
```
Bind to a key that doesn't collide with anything existing — F3 is a common convention for debug overlays and isn't used elsewhere in your bindings:
```java
// In your existing KeyAdapter, alongside the T-key debug tile-print you already have
if (code == KeyEvent.VK_F3) {
    debugOverlayVisible = !debugOverlayVisible;
}
```

### 3b. Drawing the overlay
```java
private void drawDebugOverlay(Graphics2D g2) {
    if (!debugOverlayVisible) return;

    g2.setColor(new Color(0, 0, 0, 180));
    g2.fillRect(screenWidth - 280, 0, 280, screenHeight);
    g2.setColor(Color.green);
    g2.setFont(new Font("Monospaced", Font.PLAIN, 12));

    int y = 20;
    g2.drawString("=== DEBUG (F3) ===", screenWidth - 270, y); y += 20;
    g2.drawString("Layer: " + currentLayer, screenWidth - 270, y); y += 16;
    g2.drawString("Player strength: " + playerParty.getTotalStrength(), screenWidth - 270, y); y += 16;
    g2.drawString("Gold carried: " + playerParty.carriedGold, screenWidth - 270, y); y += 20;

    g2.drawString("AI Parties (" + partyManager.aiParties.size() + "):", screenWidth - 270, y); y += 16;
    for (party.Party p : partyManager.aiParties) {
        String factionName = p.faction != null ? p.faction.name : "none";
        g2.drawString(String.format("  %s [%s] str=%d %s", factionName, p.state, p.getTotalStrength(),
            p.faction != null && playerParty.faction != null
                ? p.faction.relations.getOrDefault(playerParty.faction, kingdom.Kingdom.DiplomaticRelation.PEACE).toString()
                : ""), screenWidth - 270, y);
        y += 14;
        if (y > screenHeight - 140) { g2.drawString("  ...(more)", screenWidth - 270, y); break; }
    }

    y += 10;
    g2.drawString("Kingdoms:", screenWidth - 270, y); y += 16;
    for (kingdom.Kingdom k : kingdomSim.allKingdoms) {
        g2.drawString(String.format("  %s: treasury=%d wars=%d", k.name, k.treasury, k.activeWars.size()), screenWidth - 270, y);
        y += 14;
    }

    y += 10;
    g2.drawString("Controls:", screenWidth - 270, y); y += 16;
    g2.drawString("F4: spawn weak enemy party", screenWidth - 270, y); y += 14;
    g2.drawString("F5: spawn strong enemy party", screenWidth - 270, y); y += 14;
    g2.drawString("F6: fast-forward 6 months", screenWidth - 270, y); y += 14;
    g2.drawString("F7: toggle war with nearest faction", screenWidth - 270, y); y += 14;
}
```
Call it at the very end of `paintComponent()`, after everything else including menus, so it always renders on top:
```java
drawDebugOverlay(g2);
```

### 3c. Debug spawn/skip hotkeys
```java
if (code == KeyEvent.VK_F4) {
    spawnDebugParty(0.4); // weak relative to player — should trigger fleeing behavior from Phase 2
}
if (code == KeyEvent.VK_F5) {
    spawnDebugParty(2.5); // strong relative to player — should trigger pursuing behavior
}
if (code == KeyEvent.VK_F6) {
    for (int i = 0; i < 6; i++) kingdomSim.forceMonthlyTickForTesting(); // fast-forward without waiting real-time
}
if (code == KeyEvent.VK_F7) {
    toggleWarWithNearestFaction();
}
```
```java
private void spawnDebugParty(double strengthRatioToPlayer) {
    if (kingdomSim.allKingdoms.isEmpty()) return;
    party.Party p = new party.Party();
    p.faction = kingdomSim.allKingdoms.get(0); // first kingdom in the list — adjust if you want a specific test faction
    p.faction.relations.put(playerParty.faction, kingdom.Kingdom.DiplomaticRelation.WAR); // force hostile so it's guaranteed to trigger an encounter
    p.x = player.x + 150; // spawn just off-screen-ish, close enough to reach quickly for testing
    p.y = player.y;

    int targetStrength = (int) (playerParty.getTotalStrength() * strengthRatioToPlayer);
    for (int i = 0; i < Math.max(1, targetStrength); i++) {
        entity.Troop t = new entity.Troop(this, p.x, p.y, entity.Troop.Role.MELEE);
        p.troops.add(t);
    }
    partyManager.aiParties.add(p);
    System.out.println("Debug: spawned party with strength " + p.getTotalStrength() + " (target ratio " + strengthRatioToPlayer + ")");
}

private void toggleWarWithNearestFaction() {
    if (playerParty.faction == null || kingdomSim.allKingdoms.isEmpty()) return;
    kingdom.Kingdom target = kingdomSim.allKingdoms.stream()
        .filter(k -> k != playerParty.faction)
        .findFirst().orElse(null);
    if (target == null) return;

    boolean atWar = playerParty.faction.relations.get(target) == kingdom.Kingdom.DiplomaticRelation.WAR;
    if (atWar) {
        playerParty.faction.relations.put(target, kingdom.Kingdom.DiplomaticRelation.PEACE);
        target.relations.put(playerParty.faction, kingdom.Kingdom.DiplomaticRelation.PEACE);
        System.out.println("Debug: peace with " + target.name);
    } else {
        kingdomSim.declareWar(playerParty.faction, target, kingdom.War.CasusBelli.CONQUEST);
        System.out.println("Debug: war with " + target.name);
    }
}
```

---

## 4. Putting it together — a practical test session

1. Run the **console harness** first, before touching the actual game at all. Confirm wars, alliances, marriages, and successions all show up sensibly in the event log over a simulated 20 years. Fix any Kingdom-logic bugs here — it's dramatically faster to iterate on than playing the real game.
2. Launch the actual game, press **F3** to confirm the overlay renders and shows your starting kingdoms/parties correctly.
3. Press **F7** to force war with a faction, then **F4** to spawn a weak enemy party from a hostile faction near you — confirm it starts `FLEEING` per Phase 2's logic, and that catching it triggers the deployment screen (Phase 3) with the "caught them fleeing" message.
4. Press **F5** to spawn a strong party instead — confirm it enters `PURSUING` state and actively closes distance on you, and that retreating from the deployment screen has a real chance of failing per Phase 3's pursuing-party logic.
5. Press **F6** a few times mid-session to fast-forward kingdom time while you're testing parties — confirms the two systems don't interfere with each other (e.g., a kingdom going to war mid-session should be reflected in F3's overlay and in which AI parties are hostile, without needing to restart).

This combination — instant console iteration for the Kingdom logic, and on-demand spawn/skip hotkeys for the real-time Party/Battle flow — should let you exercise all six phases you've built without waiting through real monthly ticks or hoping the right kind of AI party wanders near you naturally.