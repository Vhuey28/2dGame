# Party/Battle Phase 2 — Multiple AI Parties, Factions, Fleeing, Loot & Prisoners

Builds on Part 3 Phase 1 from the original architecture doc (`Party`, `AiPartyController`, encounter detection, the `startBattle`/`endBattle` state machine) and ties faction allegiance directly to the `kingdom.Kingdom` classes from the Kingdom system. If you haven't built Kingdom Phase 1 yet, faction allegiance here degrades gracefully to "everyone's hostile" — but the real payoff is treating war/peace/alliance status as the actual gate for whether an encounter becomes a battle.

---

## 1. Faction allegiance on Party

### Updates to `party/Party.java`
```java
package party;

import kingdom.Kingdom;
import java.util.*;
import entity.Troop;
import entity.Hero;

public class Party {
    public float x, y;
    public float speed = 2f;
    public List<Troop> troops = new ArrayList<>();
    public List<Hero> heroes = new ArrayList<>();
    public List<Troop> prisoners = new ArrayList<>(); // captured troops — see section 5
    public int goldUpkeepPerMonth = 0;
    public int carriedGold = 0; // loot accumulates here, separate from the Kingdom treasury until the party returns/deposits it
    public boolean isPlayerParty;
    public Kingdom faction; // null for the player's own independent party unless you tie the player to a Kingdom too

    public enum State { ROAMING, FLEEING, PURSUING }
    public State state = State.ROAMING;

    public int getTotalStrength() {
        return troops.size() + heroes.size() * 3;
    }

    public boolean isDefeated() {
        return troops.isEmpty() && heroes.isEmpty();
    }
}
```

---

## 2. PartyManager — spawning and tracking multiple AI parties

### New file: `party/PartyManager.java`
```java
package party;

import kingdom.Kingdom;
import my2Dgame.GamePanel;
import java.util.*;

public class PartyManager {
    public List<Party> aiParties = new ArrayList<>();
    private Map<Party, AiPartyController> controllers = new HashMap<>();

    private int spawnTimer = 0;
    private static final int SPAWN_INTERVAL_TICKS = 1800; // ~30 seconds at 60fps — tune to your map's pacing
    private static final int MAX_AI_PARTIES = 12; // hard cap, same performance-safety reasoning as the enemy wave cap from earlier

    /** Call once per game tick from GamePanel.update(). */
    public void update(GamePanel gp, List<Kingdom> allKingdoms, Party playerParty) {
        spawnTimer++;
        if (spawnTimer >= SPAWN_INTERVAL_TICKS && aiParties.size() < MAX_AI_PARTIES) {
            spawnTimer = 0;
            trySpawnParty(allKingdoms, gp);
        }

        Iterator<Party> it = aiParties.iterator();
        while (it.hasNext()) {
            Party p = it.next();
            if (p.isDefeated()) {
                controllers.remove(p);
                it.remove();
                continue;
            }
            AiPartyController controller = controllers.get(p);
            controller.update(gp, playerParty); // now also handles fleeing — see section 3
        }
    }

    private void trySpawnParty(List<Kingdom> allKingdoms, GamePanel gp) {
        if (allKingdoms.isEmpty()) return;
        Kingdom faction = allKingdoms.get((int) (Math.random() * allKingdoms.size()));

        Party party = new Party();
        party.faction = faction;
        party.isPlayerParty = false;
        // Spawn position: somewhere on the current map, away from the player. Adjust bounds to your actual map scale.
        party.x = (float) (Math.random() * gp.worldWidth);
        party.y = (float) (Math.random() * gp.worldHeight);

        int troopCount = 3 + (int) (Math.random() * 8); // 3-10 troops per spawned party, tune to taste
        for (int i = 0; i < troopCount; i++) {
            entity.Troop.Role role = Math.random() < 0.3 ? entity.Troop.Role.ARCHER : entity.Troop.Role.MELEE;
            entity.Troop t = new entity.Troop(gp, party.x, party.y, role);
            party.troops.add(t);
        }

        aiParties.add(party);
        controllers.put(party, new AiPartyController(party));
    }
}
```

**Note on the spawn-position placeholder:** unlike your in-map enemy spawns, overworld AI parties probably shouldn't spawn on top of the player or inside unwalkable terrain — reuse `findOpenSpawnSpace` (with a smaller search radius per the earlier performance fix) if you want spawn placement to respect collision, or keep this simple random placement if the overworld scale is large enough that collisions are rare and self-correcting once the party starts roaming.

---

## 3. Fleeing behavior

### Updates to `party/AiPartyController.java`
```java
package party;

import my2Dgame.GamePanel;

public class AiPartyController {
    private Party party;
    private java.awt.Point roamTarget;
    private int roamWaitTimer = 0;

    private static final float AWARENESS_RADIUS = 250f; // how far the AI "notices" the player party
    private static final double FLEE_STRENGTH_RATIO = 0.6; // flee if own strength is below 60% of the player's

    public AiPartyController(Party party) {
        this.party = party;
    }

    public void update(GamePanel gp, Party playerParty) {
        float dx = playerParty.x - party.x;
        float dy = playerParty.y - party.y;
        float distToPlayer = (float) Math.sqrt(dx * dx + dy * dy);

        updateState(distToPlayer, playerParty);

        switch (party.state) {
            case FLEEING -> moveAwayFrom(playerParty, gp);
            case PURSUING -> moveToward(playerParty, gp);
            case ROAMING -> roam(gp);
        }
    }

    private void updateState(float distToPlayer, Party playerParty) {
        if (party.faction == null) return; // no faction data — default to always-roaming, skip flee/pursue logic entirely
        if (distToPlayer > AWARENESS_RADIUS) {
            party.state = Party.State.ROAMING;
            return;
        }

        // Only hostile relations trigger flee/pursue behavior — peaceful/allied parties always just roam past
        boolean isHostile = isFactionHostileToPlayer(playerParty);
        if (!isHostile) {
            party.state = Party.State.ROAMING;
            return;
        }

        double strengthRatio = playerParty.getTotalStrength() == 0 ? 1.0
            : (double) party.getTotalStrength() / playerParty.getTotalStrength();

        if (strengthRatio < FLEE_STRENGTH_RATIO) {
            party.state = Party.State.FLEEING;
        } else if (strengthRatio > 1.0 / FLEE_STRENGTH_RATIO) {
            party.state = Party.State.PURSUING; // significantly stronger — actively hunts the player instead of just roaming
        } else {
            party.state = Party.State.ROAMING; // roughly even match — neither flees nor actively pursues
        }
    }

    private boolean isFactionHostileToPlayer(Party playerParty) {
        if (party.faction == null || playerParty.faction == null) return true; // no faction data — default to hostile (matches old Phase 1 behavior)
        kingdom.Kingdom.DiplomaticRelation rel = party.faction.relations.get(playerParty.faction);
        return rel == kingdom.Kingdom.DiplomaticRelation.WAR;
    }

    private void moveAwayFrom(Party target, GamePanel gp) {
        float dx = party.x - target.x;
        float dy = party.y - target.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) return;
        float fleeSpeedMultiplier = 1.3f; // fleeing parties move faster than normal — makes catching them a real choice, not free
        party.x += (dx / dist) * party.speed * fleeSpeedMultiplier;
        party.y += (dy / dist) * party.speed * fleeSpeedMultiplier;
    }

    private void moveToward(Party target, GamePanel gp) {
        float dx = target.x - party.x;
        float dy = target.y - party.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) return;
        party.x += (dx / dist) * party.speed;
        party.y += (dy / dist) * party.speed;
    }

    private void roam(GamePanel gp) {
        roamWaitTimer--;
        if (roamTarget == null || roamWaitTimer <= 0) {
            double angle = Math.random() * Math.PI * 2;
            float dist = 300 + (float) (Math.random() * 500);
            roamTarget = new java.awt.Point(
                (int) (party.x + Math.cos(angle) * dist),
                (int) (party.y + Math.sin(angle) * dist)
            );
            roamWaitTimer = 300 + (int) (Math.random() * 300);
        }
        float dx = roamTarget.x - party.x;
        float dy = roamTarget.y - party.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 4f) {
            party.x += (dx / dist) * party.speed;
            party.y += (dy / dist) * party.speed;
        }
    }
}
```

---

## 4. Encounter resolution — respecting faction relations and flee outcomes

Replace the Phase 1 `checkPartyEncounters` in `GamePanel`:
```java
private static final float ENCOUNTER_RADIUS = 40f;

private void checkPartyEncounters() {
    for (party.Party aiParty : partyManager.aiParties) {
        float dx = aiParty.x - playerParty.x;
        float dy = aiParty.y - playerParty.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist >= ENCOUNTER_RADIUS) continue;

        // Peaceful/allied factions never force a battle, regardless of proximity — player can walk right past them
        boolean hostile = aiParty.faction == null || playerParty.faction == null
            || aiParty.faction.relations.get(playerParty.faction) == kingdom.Kingdom.DiplomaticRelation.WAR;
        if (!hostile) continue;

        // A fleeing party that still got caught (player intercepted despite the speed penalty) fights at a disadvantage —
        // reflected simply here as the player getting a first-strike bonus, applied once battle actually starts.
        boolean caughtFleeing = aiParty.state == Party.State.FLEEING;
        startBattle(aiParty, caughtFleeing);
        break;
    }
}
```

---

## 5. Loot and prisoners on battle end

### Updates to `endBattle` from the Part 3 state machine
```java
private void endBattle(boolean victory, party.Party defeatedEnemyParty, boolean firstStrikeBonus) {
    if (victory) {
        int lootGold = defeatedEnemyParty.getTotalStrength() * 5; // tune the payout curve to taste
        playerParty.carriedGold += lootGold;

        // A portion of the defeated party's surviving troops become prisoners instead of simply dying —
        // gives the player something to do with them (ransom for gold, or recruit if you want that option later)
        int prisonersToTake = Math.min(3, defeatedEnemyParty.troops.size());
        for (int i = 0; i < prisonersToTake && i < defeatedEnemyParty.troops.size(); i++) {
            playerParty.prisoners.add(defeatedEnemyParty.troops.get(i));
        }

        partyManager.aiParties.remove(defeatedEnemyParty);
    } else {
        // Player lost — surviving player troops stay in playerParty (whatever didn't die in-battle already left the list
        // naturally via your existing death-handling code), no additional penalty coded here beyond that loss itself.
        // Consider adding a gold penalty or captured-troops-for-the-enemy mechanic here if you want losses to sting more.
    }

    currentLayer = GameLayer.OVERWORLD;
    setupMap(overworldMapBeforeBattle);
    player.x = overworldPlayerXBeforeBattle;
    player.y = overworldPlayerYBeforeBattle;
}
```
Update `startBattle`'s signature to accept and store the `firstStrikeBonus` flag, and apply it however fits your combat feel — simplest version: give the player's troops a one-time damage or initiative boost on the first few ticks of the battle map, reflecting that they caught a fleeing, presumably less-prepared enemy off guard.

### Ransoming prisoners (simple version)
```java
// Called from a UI action, e.g. "Ransom Prisoners" button somewhere in a party-management screen
public void ransomPrisoners(kingdom.Kingdom playerKingdom) {
    int ransomValue = playerParty.prisoners.size() * 20; // tune to taste
    playerKingdom.treasury += ransomValue;
    playerParty.prisoners.clear();
}
```

---

## 6. Wiring it all together in GamePanel

```java
public party.PartyManager partyManager = new party.PartyManager();
public party.Party playerParty = new party.Party();

// In GamePanel.update(), once per tick (Sandbox mode only, same as the Kingdom simulation)
if (gameMode == GameMode.SANDBOX && currentLayer == GameLayer.OVERWORLD) {
    partyManager.update(this, kingdomSim.allKingdoms, playerParty);
    checkPartyEncounters();
}
```
Keep `playerParty.troops` synced with your existing `troops` list (the ones actually walking around with the player in real time) — either treat `playerParty.troops` as the authoritative source and derive display state from it, or keep them as the same list reference if you want zero duplication. The latter is simpler: just set `playerParty.troops = troops;` once at startup rather than maintaining two separate collections that could drift out of sync.

---

## What's still stubbed after this phase
- AI parties don't yet request reinforcements or merge with allied parties when weak — real Bannerlord-style parties sometimes band together against a threat.
- No visual indicator on the overworld/minimap yet for party strength relative to the player (useful player-facing info before committing to an encounter) — worth adding as a minimap marker color/size cue, same pattern as your existing hero/enemy minimap markers.
- Prisoners currently can only be ransomed for gold — recruiting them into your own party (defection mechanic) is a natural Phase 3 extension if you want it.

## Suggested test order
1. Spawn several AI parties from different (or the same) kingdoms and confirm they roam independently without overwhelming performance — watch `MAX_AI_PARTIES` behavior specifically.
2. Set one kingdom to `WAR` with the player's faction and confirm only that kingdom's parties trigger battles on contact — peaceful-faction parties should let the player walk through them.
3. Spawn a deliberately weak AI party near a strong player party and confirm it flees rather than engaging; confirm you can still catch it if you're faster/persistent.
4. Win a battle and confirm loot gold and prisoners land in `playerParty` correctly, and that the defeated party is actually removed from `partyManager.aiParties` (not lingering as a phantom).