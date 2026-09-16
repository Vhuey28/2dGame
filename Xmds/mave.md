# Cross-Platform Build, Kingdom Politics, and Party/Battle System — Architecture & Phased Roadmap

## Honest scope framing before anything else
This document covers three features, and two of them (the kingdom system and the party/battle system) are each comparable in scope to entire commercial games' core loops. This guide gives you:
- A **complete, ready-to-use** solution for the Maven migration (Part 1) — this one's genuinely finishable in one pass.
- A **real architecture** and **working foundational code** for the kingdom system and battle system (Parts 2 and 3) — data models, state machines, integration points, and the trickiest pieces solved concretely.
- A **phased roadmap** for everything beyond the foundation, since attempting full CK-depth intrigue/succession/AI diplomacy and a complete Bannerlord-style battle simulator in one document would produce guidance too shallow to actually implement correctly.

Build Part 1 first (you need a working cross-platform build before anything else matters), then Part 2's Phase 1 and Part 3's Phase 1 in parallel-ish — they intersect at the treasury/upkeep link described in Part 3.

---

# Part 1 — Maven Migration (complete solution)

## 1.1 Standard Maven directory layout
Maven expects a specific structure. Your current layout (`my2Dgame/src`, `my2Dgame/res`) needs restructuring:
```
project-root/
  pom.xml
  src/
    main/
      java/
        entity/
          Enemy.java
          Player.java
          ... (everything currently in my2Dgame/src/entity)
        my2Dgame/
          GamePanel.java
          Main.java
          ... (everything currently in my2Dgame/src/my2Dgame)
        tile/
          tileManager.java
      resources/
        player/          (your sprite folders — everything currently in my2Dgame/res)
        tiles/
        maps/
        effects/
  natives/
    windows/    (jinput .dll files)
    macos/      (jinput .jnilib/.dylib files)
    linux/      (jinput .so files)
```
Move `my2Dgame/src/*` into `src/main/java/`, and `my2Dgame/res/*` into `src/main/resources/`. Maven automatically puts `src/main/resources` contents on the classpath, which is why your `getClass().getResourceAsStream("/player/...")` calls keep working unchanged — no code changes needed for this part, just file moves.

## 1.2 pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.chronicleconquest</groupId>
    <artifactId>my2Dgame</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>net.java.jinput</groupId>
            <artifactId>jinput</artifactId>
            <version>2.0.10</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Bundles all dependencies into one runnable "fat jar" -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>my2Dgame.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
Replace `my2Dgame.Main` with your actual entry-point class if it differs.

**Note on JInput natives with Maven:** the JInput jar dependency pulls in the Java-side classes, but native `.dll`/`.so`/`.dylib` files aren't something Maven auto-installs to `java.library.path` — that's an OS-level concern Maven's dependency system doesn't solve for you. Keep the `natives/` folder approach (checked into your repo, one subfolder per OS) and point `-Djava.library.path` at the right one based on detected OS — that's exactly what the cross-platform scripts below do.

## 1.3 Cross-platform run.sh (macOS/Linux)
```bash
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Detect OS for native library path
OS_NAME="$(uname -s)"
case "$OS_NAME" in
    Darwin*) NATIVES_DIR="natives/macos" ;;
    Linux*)  NATIVES_DIR="natives/linux" ;;
    *)       echo "Unsupported OS for run.sh: $OS_NAME (use run.ps1 on Windows)"; exit 1 ;;
esac

echo "Building with Maven..."
mvn -q clean package

echo "Starting game (natives: $NATIVES_DIR)..."
java -Djava.library.path="$NATIVES_DIR" -jar target/my2Dgame-1.0.0.jar
```

## 1.4 Cross-platform run.ps1 (Windows)
```powershell
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "Building with Maven..."
mvn -q clean package

Write-Host "Starting game..."
java -Djava.library.path="natives\windows" -jar target\my2Dgame-1.0.0.jar
```

Both scripts assume `mvn` is installed and on `PATH` — link your teammates/players to [Maven's install docs](https://maven.apache.org/install.html) if this is meant to be run by anyone besides you.

---

# Part 2 — Kingdom Politics System (CK-style)

## 2.1 Relationship to the existing `Hero.KingdomAffairs` stub
You already have a `KingdomAffairs` inner class inside `Hero.java` with `treasury`, `happiness`, `activeIssues`, and a simple decision system. That stub is the right *idea* but far too small for CK-depth — this section replaces it with a real standalone system. Once built, `Hero.openKingdomAffairs()` should open the new screen instead of the old stub, and the old `KingdomAffairs` inner class can be deleted.

## 2.2 Core data model
Create a new package, `kingdom/`, for all of this — it's a big enough subsystem to warrant its own namespace separate from `entity/`.

### `kingdom/Character.java` — every ruler, heir, vassal, and diplomatic contact is one of these
```java
package kingdom;

import java.util.*;

public class Character {
    public String name;
    public int age;
    public boolean alive = true;
    public Gender gender;
    public enum Gender { MALE, FEMALE }

    // Stats — drive AI decision-making and event outcomes
    public int diplomacy = 5, martial = 5, stewardship = 5, intrigue = 5, learning = 5; // 0-20 scale
    public List<Trait> traits = new ArrayList<>();

    public Kingdom liege;           // null if this character IS a ruler (no liege above them)
    public Kingdom rulesKingdom;    // non-null if this character rules a kingdom
    public Character spouse;
    public List<Character> children = new ArrayList<>();
    public Character father, mother;

    public Map<Character, Integer> opinions = new HashMap<>(); // -100 to 100, keyed by other character

    public enum Trait { AMBITIOUS, CONTENT, CRUEL, KIND, BRAVE, CRAVEN, GENEROUS, GREEDY, JUST, ARBITRARY }

    public int getOpinionOf(Character other) {
        return opinions.getOrDefault(other, 0);
    }

    public void adjustOpinion(Character other, int amount) {
        opinions.merge(other, amount, Integer::sum);
    }
}
```

### `kingdom/Kingdom.java` — the political entity itself
```java
package kingdom;

import java.util.*;

public class Kingdom {
    public String name;
    public Character ruler;
    public List<Territory> territories = new ArrayList<>();
    public List<Kingdom> vassalKingdoms = new ArrayList<>();  // sub-kingdoms sworn to this one
    public Kingdom liegeKingdom;                               // non-null if this kingdom is itself a vassal

    // Resources
    public int treasury = 1000;
    public int stability = 50;  // 0-100 — low stability increases revolt/succession-crisis chance
    public int influence = 0;   // spent on diplomatic actions (declare war, form alliance, etc.)

    public SuccessionLaw successionLaw = SuccessionLaw.PRIMOGENITURE;
    public enum SuccessionLaw { PRIMOGENITURE, ELECTIVE }

    public Map<Kingdom, DiplomaticRelation> relations = new HashMap<>();
    public enum DiplomaticRelation { PEACE, WAR, ALLIANCE, TRUCE, VASSAL }

    public List<War> activeWars = new ArrayList<>();

    public int getTotalIncome() {
        int income = 0;
        for (Territory t : territories) income += t.getTaxIncome();
        return income;
    }

    public int getTotalLevy() {
        int levy = 0;
        for (Territory t : territories) levy += t.garrisonSize;
        return levy;
    }
}
```

### `kingdom/Territory.java`
```java
package kingdom;

public class Territory {
    public String name;
    public int population = 1000;
    public int taxRate = 10; // percent
    public int garrisonSize = 50;
    public BuildingLevel[] buildings = new BuildingLevel[BuildingType.values().length];

    public enum BuildingType { FARM, MARKET, BARRACKS, WALLS, KEEP }
    public enum BuildingLevel { NONE, LEVEL_1, LEVEL_2, LEVEL_3 }

    public int getTaxIncome() {
        return (population / 100) * taxRate / 10;
    }
}
```

### `kingdom/War.java` and a stub casus belli concept
```java
package kingdom;

public class War {
    public Kingdom attacker, defender;
    public CasusBelli reason;
    public int warScoreAttacker = 0; // -100 to 100, drives AI peace-offer willingness

    public enum CasusBelli { CONQUEST, INDEPENDENCE, CLAIM_THRONE, HOLY_WAR }

    public War(Kingdom attacker, Kingdom defender, CasusBelli reason) {
        this.attacker = attacker;
        this.defender = defender;
        this.reason = reason;
    }
}
```

## 2.3 The monthly tick — the actual "simulation" driving AI kingdoms
CK-style games don't run politics every frame — they run it on a slower simulated clock ("monthly tick"). This is the right model here too, both for performance and for making AI decisions feel deliberate rather than frantic.

### `kingdom/KingdomSimulation.java`
```java
package kingdom;

import java.util.*;

public class KingdomSimulation {
    public List<Kingdom> allKingdoms = new ArrayList<>();
    public Kingdom playerKingdom;

    private int tickTimer = 0;
    private static final int TICKS_PER_MONTH = 3600; // 60 seconds at 60fps — tune to taste

    /** Call once per game update tick from GamePanel.update(). */
    public void update() {
        tickTimer++;
        if (tickTimer >= TICKS_PER_MONTH) {
            tickTimer = 0;
            processMonthlyTick();
        }
    }

    private void processMonthlyTick() {
        for (Kingdom k : allKingdoms) {
            collectIncome(k);
            if (k != playerKingdom) {
                runAiDecisions(k); // AI kingdoms act autonomously; player acts through the UI
            }
            checkSuccession(k);
        }
    }

    private void collectIncome(Kingdom k) {
        k.treasury += k.getTotalIncome();
        k.influence += 1; // simple flat influence generation — expand with events/traits later
    }

    private void runAiDecisions(Kingdom k) {
        // Phase 1 AI: simple weighted-random decision each tick. Expand with real scoring later.
        double roll = Math.random();
        if (roll < 0.05 && k.activeWars.isEmpty()) {
            considerDeclaringWar(k);
        } else if (roll < 0.10) {
            considerAlliance(k);
        }
        // ...more AI behaviors (build, marry off children, launch schemes) slot in here as Phase 2+
    }

    private void considerDeclaringWar(Kingdom k) {
        for (Kingdom other : allKingdoms) {
            if (other == k) continue;
            DiplomaticRelation rel = k.relations.getOrDefault(other, DiplomaticRelation.PEACE);
            if (rel == DiplomaticRelation.PEACE && k.getTotalLevy() > other.getTotalLevy() * 1.5) {
                declareWar(k, other, War.CasusBelli.CONQUEST);
                return;
            }
        }
    }

    private void considerAlliance(Kingdom k) {
        // Stub — pick a random non-hostile kingdom and propose alliance based on opinion/relation state.
        // Expand with real diplomacy scoring in Phase 2.
    }

    public void declareWar(Kingdom attacker, Kingdom defender, War.CasusBelli reason) {
        War war = new War(attacker, defender, reason);
        attacker.activeWars.add(war);
        defender.activeWars.add(war);
        attacker.relations.put(defender, Kingdom.DiplomaticRelation.WAR);
        defender.relations.put(attacker, Kingdom.DiplomaticRelation.WAR);
    }

    private void checkSuccession(Kingdom k) {
        if (k.ruler != null && !k.ruler.alive) {
            Character heir = determineHeir(k);
            if (heir != null) {
                k.ruler = heir;
                heir.rulesKingdom = k;
                k.stability -= 10; // succession always costs some stability
            }
        }
    }

    private Character determineHeir(Kingdom k) {
        if (k.successionLaw == Kingdom.SuccessionLaw.PRIMOGENITURE) {
            return k.ruler.children.stream()
                .filter(c -> c.alive)
                .min(Comparator.comparingInt(c -> -c.age)) // oldest surviving child
                .orElse(null);
        }
        // ELECTIVE: stub — pick highest-opinion vassal-ruler. Expand into a real election/vote system later.
        return k.ruler.children.stream().filter(c -> c.alive).findFirst().orElse(null);
    }
}
```

## 2.4 Integration into GamePanel
```java
// GamePanel field
public kingdom.KingdomSimulation kingdomSim = new kingdom.KingdomSimulation();

// In GamePanel.update(), once per tick (only relevant in Sandbox mode — survival mode has no kingdom layer)
if (gameMode == GameMode.SANDBOX) {
    kingdomSim.update();
}
```
Initialize `kingdomSim.playerKingdom` and a handful of AI `Kingdom` instances during your existing sandbox setup — this is where you decide how many rival kingdoms exist, their starting territories, etc. This is genuinely game-design work, not something to auto-generate blindly — you'll want to hand-author the starting political map the same way you hand-authored your maps.

## 2.5 UI — Kingdom management screen
Follow the same `Graphics2D` + click-rect pattern as your pause/settings menus (not HTML, per the earlier discussion — this screen needs tabbed navigation and data tables, which Swing/Graphics2D handles more directly than fighting `HTMLEditorKit`). Structure it as tabs: **Overview** (treasury/stability/income), **Diplomacy** (relations list, declare war/propose alliance buttons), **Family** (character tree, marriage prospects), **Territories** (per-territory management). This is a substantial UI build in its own right — happy to write the full `KingdomScreen` class in a follow-up once the data model above is in place and tested.

## Kingdom system phased roadmap
1. **Phase 1 (this section)**: data model, monthly tick, basic AI war/alliance stub, succession on death, minimal UI to view state.
2. **Phase 2**: real diplomacy scoring (opinion-weighted AI decisions instead of random rolls), marriage system connecting characters across kingdoms, trait-driven behavior differences.
3. **Phase 3**: intrigue actions (fabricate claims, assassination schemes, spy networks) with success/detection chance based on `intrigue` stat.
4. **Phase 4**: full elective/succession-crisis mechanics, civil wars when succession is contested, vassal revolt mechanics tied to `stability`/opinion.

---

# Part 3 — Party & Battle System (Bannerlord-style)

## 3.1 Overworld party representation
Your existing `troops` list + `heroes` list, grouped under the player, already **is** most of "the party" — the missing piece is representing *enemy* parties as independent roaming entities on the overworld (distinct from your current fixed per-map enemy spawns), and the encounter → battle-instance → return flow.

### `party/Party.java`
```java
package party;

import java.util.*;
import entity.Troop;
import entity.Hero;

public class Party {
    public float x, y; // overworld position
    public float speed = 2f;
    public List<Troop> troops = new ArrayList<>();
    public List<Hero> heroes = new ArrayList<>();
    public int goldUpkeepPerMonth = 0; // recalculated from troop count — ties into Kingdom treasury, see 3.4
    public boolean isPlayerParty;
    public String factionName; // for AI parties, which Kingdom they belong to

    public int getTotalStrength() {
        return troops.size() + heroes.size() * 3; // heroes count for more — tune this weighting
    }
}
```

### `party/AiPartyController.java` — roaming behavior for enemy parties
```java
package party;

import my2Dgame.GamePanel;
import entity.AStarPathfinder;
import java.util.List;

public class AiPartyController {
    private Party party;
    private java.awt.Point roamTarget;
    private int roamWaitTimer = 0;

    public AiPartyController(Party party) {
        this.party = party;
    }

    public void update(GamePanel gp) {
        // Same roam-and-wait pattern as your existing Enemy roamBehavior() — reuse that logic here
        // rather than duplicating it; both are "wander toward a random point, pause, repeat."
        roamWaitTimer--;
        if (roamTarget == null || roamWaitTimer <= 0) {
            double angle = Math.random() * Math.PI * 2;
            float dist = 300 + (float)(Math.random() * 500);
            roamTarget = new java.awt.Point(
                (int)(party.x + Math.cos(angle) * dist),
                (int)(party.y + Math.sin(angle) * dist)
            );
            roamWaitTimer = 300 + (int)(Math.random() * 300);
        }
        // Move party.x/y toward roamTarget — same style as your Enemy/Troop movement code
    }
}
```

## 3.2 Encounter detection
In `GamePanel.update()`, check the player's overworld party position against every AI party's position each tick:
```java
private static final float ENCOUNTER_RADIUS = 40f; // pixels — tune to map scale

private void checkPartyEncounters() {
    for (party.Party aiParty : overworldParties) {
        float dx = aiParty.x - playerParty.x;
        float dy = aiParty.y - playerParty.y;
        if (Math.sqrt(dx*dx + dy*dy) < ENCOUNTER_RADIUS) {
            startBattle(aiParty);
            break;
        }
    }
}
```

## 3.3 The battle-instance state machine — the trickiest integration point
This is the part that needs the most care: your `GamePanel` currently has exactly one "mode" of play at a time (sandbox map, or survival wave). Adding instanced battles means a **third distinct state** that temporarily replaces the overworld, then hands control back.

### Approach — a `BattleInstance` that snapshots and restores overworld state
```java
public enum GameLayer { OVERWORLD, BATTLE }
public GameLayer currentLayer = GameLayer.OVERWORLD;

private party.Party battleEnemyParty;
private String overworldMapBeforeBattle;
private float overworldPlayerXBeforeBattle, overworldPlayerYBeforeBattle;

private void startBattle(party.Party enemyParty) {
    // 1. Snapshot overworld state so we can restore it after
    overworldMapBeforeBattle = currentMap;
    overworldPlayerXBeforeBattle = player.x;
    overworldPlayerYBeforeBattle = player.y;
    battleEnemyParty = enemyParty;

    // 2. Switch to a dedicated battle map (a plain map file works fine — battles don't need
    //    a unique map per encounter to start; you can add battle-specific terrain generation later)
    currentLayer = GameLayer.BATTLE;
    setupMap("battlefield.txt"); // or .tmx — a neutral arena map

    // 3. Deploy both sides as real Enemy/Troop entities using your EXISTING spawn machinery —
    //    this is the payoff of reusing Squad/ThreatTable/Formation: battles use the same
    //    combat systems you already built, just triggered from a different entry point.
    enemies.clear();
    troops.clear();
    deployPartyAsEnemies(battleEnemyParty);
    deployPartyAsAllies(playerParty); // player's overworld troops become the in-battle troops list

    teleportPlayerForMap("battlefield.txt");
}

private void checkBattleEndCondition() {
    if (currentLayer != GameLayer.BATTLE) return;
    boolean allEnemiesDead = enemies.stream().allMatch(e -> e.dead);
    boolean allPlayerTroopsDead = troops.isEmpty() && player.health <= 0; // adjust win/loss condition to taste
    if (allEnemiesDead || allPlayerTroopsDead) {
        endBattle(allEnemiesDead);
    }
}

private void endBattle(boolean victory) {
    // Apply results: loot, remove dead troops from playerParty permanently, adjust battleEnemyParty/remove it
    // if defeated, etc. — this is where "party casualties persist across battles" logic lives.

    currentLayer = GameLayer.OVERWORLD;
    setupMap(overworldMapBeforeBattle);
    player.x = overworldPlayerXBeforeBattle;
    player.y = overworldPlayerYBeforeBattle;
    // Re-sync playerParty.troops with whatever survived the battle
}
```
Call `checkBattleEndCondition()` once per tick in `update()`, guarded by `currentLayer == GameLayer.BATTLE`.

**Why this design and not something fancier:** rather than building an entirely separate battle-rendering/combat engine, this reuses your existing map-loading, `Enemy`/`Troop`/`Squad` systems wholesale — a "battle" is just a temporary map swap with both sides' rosters deployed as normal entities. This is dramatically less work than a from-scratch tactical battle system, and it's genuinely how a fair amount of this genre works under the hood conceptually (Bannerlord's battles are also just a separate scene with your troops and theirs spawned in).

## 3.4 Tying party upkeep to the Kingdom treasury
```java
// Monthly tick (same cadence as KingdomSimulation's processMonthlyTick)
private void processPartyUpkeep() {
    int upkeep = playerParty.troops.size() * 2; // gold per troop per month — tune
    kingdomSim.playerKingdom.treasury -= upkeep;
    if (kingdomSim.playerKingdom.treasury < 0) {
        // Can't afford upkeep — desert troops, lower morale, or block recruiting until resolved
    }
}
```
Call this from the same tick handler as `KingdomSimulation.update()`, keeping the two systems' time scale consistent.

## Party/battle system phased roadmap
1. **Phase 1 (this section)**: single AI party roaming, encounter detection, battle-instance swap reusing existing combat systems, basic win/loss, treasury upkeep link.
2. **Phase 2**: multiple simultaneous AI parties with faction allegiance (tied to `Kingdom`), parties that flee when outmatched instead of always fighting, loot/prisoner mechanics.
3. **Phase 3**: pre-battle deployment screen (choose formation before the fight starts, matching Bannerlord's pre-battle setup phase) rather than instant auto-deploy.
4. **Phase 4**: siege battles against `Territory` fortifications, tying into the Kingdom war system from Part 2.

---

## Suggested overall build order
1. Part 1 (Maven migration) — do this first, it's the only fully self-contained piece and everything else benefits from having real dependency management.
2. Part 2 Phase 1 (kingdom data model + monthly tick, minimal UI).
3. Part 3 Phase 1 (single AI party, battle-instance swap) — test this thoroughly on its own before connecting it to the kingdom treasury.
4. Wire Part 3.4 (upkeep) once both systems are independently stable.
5. Everything past Phase 1 in either system — tackle incrementally, and given the scope, I'd treat each phase listed above as its own separate implementation session rather than trying to batch multiple phases into one sitting.