# Fixing Enemy/Ally Targeting + Pathfinding, and Adding a Roam State

## Part 1 — Why enemies only target the player (the actual bug)

Looking at `Enemy.update()`, the aggro system computes a target correctly, then **immediately throws it away**:

```java
Object aggroTarget = threatTable.getHighestThreatTarget();
float targetX = (aggroTarget != null) ? gp.getEntityX(aggroTarget) : player.x;
float targetY = (aggroTarget != null) ? gp.getEntityY(aggroTarget) : player.y;

if (currentPath == null || pathIndex >= currentPath.size()) {
    currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)targetX, (int)targetY);
    pathIndex = 0;
}
float dx = player.x - x;   // <-- BUG: hardcoded to player, ignores targetX
float dy = player.y - y;   // <-- BUG: hardcoded to player, ignores targetY
float distance = (float)Math.sqrt(dx * dx + dy * dy);
```

`targetX`/`targetY` correctly reflect the highest-threat target (could be a troop), and a path even gets computed toward that target — but `dx`/`dy` (which drive the actual movement direction below) are computed straight from `player.x`/`player.y` regardless. So the enemy always walks toward the player no matter what the threat table says. This is also why pathfinding doesn't seem to help avoid walls for enemies — the path is computed but the movement code never reads from `currentPath`'s waypoints; it moves in a straight line toward `dx`/`dy` instead.

### Fix — use targetX/targetY, and actually follow the path waypoints

Replace this block in `Enemy.update()`:
```java
float dx = player.x - x;
float dy = player.y - y;
float distance = (float)Math.sqrt(dx * dx + dy * dy);
if (stunTimer == 0 && distance < chaseRange && distance > 0) {
```
with:
```java
float dx = targetX - x;
float dy = targetY - y;
float distToTarget = (float)Math.sqrt(dx * dx + dy * dy);

// Distance to the player specifically still gates whether the enemy engages at all —
// an enemy shouldn't chase a troop from clear across the map just because threat exists.
float pdx = player.x - x;
float pdy = player.y - y;
float distToPlayer = (float)Math.sqrt(pdx * pdx + pdy * pdy);

if (stunTimer == 0 && distToPlayer < chaseRange && distToTarget > 0) {
    float distance = distToTarget; // keep the rest of the method's variable name intact
```
(Everything below that currently references `distance` and `dx`/`dy` for direction/animation stays as-is — it now correctly points at the threat-table target instead of always the player.)

Then make movement actually use the computed path instead of a straight line. Find this part further down in the same block:
```java
// Calculate movement with normalized direction
float moveX = dirX * speed;
float moveY = dirY * speed;
float nextX = x + moveX;
float nextY = y + moveY;
```
Replace it with path-following logic mirroring what `Troop`'s `CHARGE` mode already does successfully:
```java
float moveX, moveY;
if (currentPath != null && pathIndex < currentPath.size()) {
    java.awt.Point waypoint = currentPath.get(pathIndex);
    float wpDx = waypoint.x - x;
    float wpDy = waypoint.y - y;
    float wpDist = (float) Math.sqrt(wpDx * wpDx + wpDy * wpDy);
    if (wpDist < 4f) {
        pathIndex++;
        moveX = 0; moveY = 0; // will pick up next waypoint next tick
    } else {
        moveX = (wpDx / wpDist) * speed;
        moveY = (wpDy / wpDist) * speed;
    }
} else {
    // No path available (target out of search range, or search failed) — fall back to direct movement
    moveX = dirX * speed;
    moveY = dirY * speed;
}
float nextX = x + moveX;
float nextY = y + moveY;
```

### Also recompute the path periodically, not just when exhausted
Right now `currentPath` only recomputes when `null` or fully walked — if the target (a moving troop, or the player) shifts position mid-path, the enemy keeps walking toward a stale waypoint until the path runs out. Add a recompute timer:

In the field declarations near `currentPath`/`pathIndex`, add:
```java
private int pathRecomputeTimer = 0;
```
Change the path computation condition from:
```java
if (currentPath == null || pathIndex >= currentPath.size()) {
    currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)targetX, (int)targetY);
    pathIndex = 0;
}
```
to:
```java
pathRecomputeTimer--;
if (currentPath == null || pathIndex >= currentPath.size() || pathRecomputeTimer <= 0) {
    currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)targetX, (int)targetY);
    pathIndex = 0;
    pathRecomputeTimer = 45; // ~0.75s at 60fps — frequent enough to react, not so frequent it's expensive
}
```

---

## Part 2 — Why only some troops charge correctly

`Troop`'s `CHARGE` branch already does the right thing structurally (path-follow with waypoint advancement), so the partial failure comes from `AStarPathfinder` itself, specifically these two caps:
```java
private static final int MAX_SEARCH_RADIUS_TILES = 40; // tiles in each direction from the start
...
int maxIterations = 4000; // hard safety cap regardless of radius
```
If a troop's target enemy is more than 40 tiles away (very possible on your 800x310-tile world, or even the 63x45 forest map if troops spawn far from the fight), `findPath()` returns `null` outright — no search attempted. `Troop.update()`'s `CHARGE` branch already has a fallback for this (`dx = targetX - x; dy = targetY - y;`), but that fallback is a straight line with **no wall avoidance at all**, which is exactly the "walks into a wall" symptom you're describing for the troops that don't path correctly.

### Fix — raise the radius cap and add a graceful non-path behavior
In `AStarPathfinder.java`, raise the cap so it covers realistic combat distances on your maps:
```java
private static final int MAX_SEARCH_RADIUS_TILES = 40; // OLD
private static final int MAX_SEARCH_RADIUS_TILES = 80; // NEW — covers larger maps/spawn distances
```
This does cost more per search (larger open set), so also raise the iteration ceiling proportionally:
```java
int maxIterations = 4000; // OLD
int maxIterations = 8000; // NEW
```
If you have performance concerns at 8000, profile it — but given troops only recompute paths periodically (not every frame) once you add the timer from Part 1's approach to `Troop` as well (see below), this cost is spread out, not paid every tick.

### Also add the same periodic-recompute timer to Troop's CHARGE mode
Right now `Troop`'s path only recomputes on exhaustion, same staleness issue as Enemy. Add the identical fields/logic:
```java
private int pathRecomputeTimer = 0;
```
```java
} else if (mode == Mode.CHARGE) {
    pathRecomputeTimer--;
    if (currentPath == null || pathIndex >= currentPath.size() || pathRecomputeTimer <= 0) {
        currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)targetX, (int)targetY);
        pathIndex = 0;
        pathRecomputeTimer = 45;
    }
    if (currentPath != null && pathIndex < currentPath.size()) {
        java.awt.Point waypoint = currentPath.get(pathIndex);
        dx = waypoint.x - x;
        dy = waypoint.y - y;
        if (Math.abs(dx) < 4 && Math.abs(dy) < 4) pathIndex++;
    } else {
        dx = targetX - x;
        dy = targetY - y;
    }
}
```

### Why "only a few" specifically, not all-or-nothing
Since `GamePanel`'s C-key handler picks each troop's **nearest enemy independently**, troops near the front of the fight get short, easy paths; troops spawned further back (or on the opposite side of an obstacle) are more likely to exceed the old 40-tile cap or hit the iteration ceiling on a cluttered map — hence a subset succeeding while others silently fall back to broken straight-line movement. The radius/iteration fix above should convert most of those failures into successful paths, since 40 tiles was almost certainly too conservative for your map sizes (your forest map alone is 63 tiles wide).

---

## Part 3 — Adding a Roam state (idle wandering when not in combat or under orders)

### Design
Both `Enemy` and `Troop` get a new default behavioral state: when there's no combat target and (for allies) no active commander order, the unit picks a random nearby point, paths to it, waits briefly, then picks another. This replaces standing perfectly still when idle.

### 3a. Enemy — add Roam as the default idle state
Add fields near your other AI state fields:
```java
private float roamTargetX = -1, roamTargetY = -1;
private int roamWaitTimer = 0;
private static final float ROAM_RADIUS = 200f; // pixels from spawn point unit will wander within
private float spawnX, spawnY; // anchor point for roaming, set once
```
Set the anchor in `setDefaultValues()`:
```java
public void setDefaultValues() {
    x = gp.tileSize * 20f;
    y = gp.tileSize * 10f;
    speed = 2f;
    direction = "down";
    spawnX = x;   // <-- add this
    spawnY = y;   // <-- add this
}
```
(Also set `spawnX`/`spawnY` at the point in `GamePanel` where you assign `enemy.x`/`enemy.y` after spawning, since `setDefaultValues()` runs before the real spawn position is chosen — otherwise the roam anchor will be wrong. Simplest fix: add a public method `enemy.setSpawnAnchor(enemy.x, enemy.y)` call right after each `enemy.x = openPt.x; enemy.y = openPt.y;` line in `GamePanel`'s spawn methods.)
```java
public void setSpawnAnchor(float x, float y) {
    this.spawnX = x;
    this.spawnY = y;
}
```

Now change the aggro-gating condition from Part 1:
```java
if (stunTimer == 0 && distToPlayer < chaseRange && distToTarget > 0) {
    // ... existing combat movement/attack logic ...
} else if (stunTimer == 0 && !isDying && !isArcherDying && !isTroopDying) {
    // Nothing to fight — roam instead of standing still
    roamBehavior();
}
```
Add the roam method:
```java
private void roamBehavior() {
    roamWaitTimer--;
    boolean needNewTarget = roamTargetX < 0 || roamWaitTimer <= 0;

    if (!needNewTarget) {
        float rdx = roamTargetX - x;
        float rdy = roamTargetY - y;
        float rdist = (float) Math.sqrt(rdx * rdx + rdy * rdy);
        if (rdist < 4f) needNewTarget = true; // reached it
    }

    if (needNewTarget) {
        double angle = gp.random.nextDouble() * Math.PI * 2;
        float candidateX = spawnX + (float)(Math.cos(angle) * ROAM_RADIUS * gp.random.nextDouble());
        float candidateY = spawnY + (float)(Math.sin(angle) * ROAM_RADIUS * gp.random.nextDouble());
        roamTargetX = candidateX;
        roamTargetY = candidateY;
        currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)roamTargetX, (int)roamTargetY);
        pathIndex = 0;
        roamWaitTimer = 120 + gp.random.nextInt(120); // pause 2-4s at each stop before picking a new spot
        return; // don't move this tick, just picked a destination
    }

    if (currentPath != null && pathIndex < currentPath.size()) {
        java.awt.Point waypoint = currentPath.get(pathIndex);
        float wpDx = waypoint.x - x;
        float wpDy = waypoint.y - y;
        float wpDist = (float) Math.sqrt(wpDx * wpDx + wpDy * wpDy);
        if (wpDist < 4f) {
            pathIndex++;
            return;
        }
        float roamSpeed = speed * 0.5f; // roam slower than chase, reads as idle wandering not alertness
        float moveX = (wpDx / wpDist) * roamSpeed;
        float moveY = (wpDy / wpDist) * roamSpeed;
        float nextX = x + moveX;
        float nextY = y + moveY;
        if (moveX != 0f && canMoveTo(nextX, y)) x = nextX;
        if (moveY != 0f && canMoveTo(x, nextY)) y = nextY;

        // reuse existing 8-direction facing logic for the walk animation
        if (Math.abs(moveX) > Math.abs(moveY)) direction = moveX < 0 ? "left" : "right";
        else direction = moveY < 0 ? "up" : "down";

        spriteCounter++;
        if (spriteCounter > 12) {
            spriteNum++;
            if (spriteNum > 9) spriteNum = 1;
            spriteCounter = 0;
        }
    }
}
```
`gp.random` needs to be accessible — it's currently a private field on `GamePanel` (`Random random = new Random();`). Change its visibility:
```java
Random random = new Random();      // OLD
public Random random = new Random(); // NEW
```

### 3b. Troop — add Roam as a Mode, defaulting to it, with 'x' toggling it for allies
Add to the existing enum:
```java
public enum Mode { FOLLOW, CHARGE, DEFEND, ROAM }
public Mode mode = Mode.ROAM; // was Mode.FOLLOW — roam is now the default idle state
```
Add the same roam-anchor and timer fields as Enemy:
```java
private float roamTargetX = -1, roamTargetY = -1;
private int roamWaitTimer = 0;
private static final float ROAM_RADIUS = 150f;
private float spawnX, spawnY;
```
Set the anchor in the constructor:
```java
public Troop(GamePanel gp, float x, float y, Role role) {
    this.gp = gp;
    this.x = x;
    this.y = y;
    this.spawnX = x;  // <-- add this
    this.spawnY = y;  // <-- add this
    this.role = role;
    this.speed = 3f;
    direction = "down";
    loadTroopImages();
}
```
Add a `ROAM` branch to the mode dispatch in `update()`, alongside the existing `FOLLOW`/`DEFEND`/`CHARGE` branches:
```java
} else if (mode == Mode.ROAM) {
    roamWaitTimer--;
    boolean needNewTarget = roamTargetX < 0 || roamWaitTimer <= 0;
    if (!needNewTarget) {
        float rdx = roamTargetX - x, rdy = roamTargetY - y;
        if (Math.sqrt(rdx*rdx + rdy*rdy) < 4f) needNewTarget = true;
    }
    if (needNewTarget) {
        double angle = new java.util.Random().nextDouble() * Math.PI * 2;
        roamTargetX = spawnX + (float)(Math.cos(angle) * ROAM_RADIUS * Math.random());
        roamTargetY = spawnY + (float)(Math.sin(angle) * ROAM_RADIUS * Math.random());
        currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)roamTargetX, (int)roamTargetY);
        pathIndex = 0;
        roamWaitTimer = 120 + (int)(Math.random() * 120);
    } else if (currentPath != null && pathIndex < currentPath.size()) {
        java.awt.Point waypoint = currentPath.get(pathIndex);
        dx = waypoint.x - x;
        dy = waypoint.y - y;
        if (Math.abs(dx) < 4 && Math.abs(dy) < 4) pathIndex++;
    }
}
```
This sits in the same `if (mode == Mode.FOLLOW || mode == Mode.DEFEND) { ... } else if (mode == Mode.CHARGE) { ... }` chain — just add `else if (mode == Mode.ROAM) { ... }` after the `CHARGE` branch, before the diagonal-normalize code that follows.

**Important:** roam movement should be noticeably slower than combat movement so it visually reads as idle wandering rather than alert chasing. Since `dx`/`dy` get normalized and multiplied by `speed` a few lines below (`dx = dx / distance * speed;`), either temporarily scale `speed` down during roam or scale `dx`/`dy` by a roam-specific factor before that normalization — simplest is capping the movement magnitude right in the `ROAM` branch before the shared normalize step runs.

### 3c. Wiring the 'x' key to toggle ally roaming
Your `KeyHandler` class isn't shown, but based on the existing pattern (`bPressed`, `cPressed`, `vPressed`, `mPressed`), it has a `KeyListener` setting booleans on key down/up. Add an `xPressed` boolean there the same way the others are declared and set.

In `GamePanel.update()`, alongside your existing `cPressed`/`vPressed` troop-command blocks:
```java
if (keyH.cPressed) {
    for (entity.Troop t : troops) {
        t.mode = entity.Troop.Mode.CHARGE;
        ...
    }
}
if (keyH.vPressed) {
    for (entity.Troop t : troops) {
        t.mode = entity.Troop.Mode.DEFEND;
    }
}
```
add a toggle (not a hold — 'x' should flip a state, not force it every frame it's held), using the same press-edge pattern your minimap toggle already uses (`mWasPressedLastFrame`):
```java
if (keyH.xPressed && !xWasPressedLastFrame) {
    for (entity.Troop t : troops) {
        t.mode = (t.mode == entity.Troop.Mode.ROAM) ? entity.Troop.Mode.FOLLOW : entity.Troop.Mode.ROAM;
    }
}
xWasPressedLastFrame = keyH.xPressed;
```
Add the tracking field near your other `WasPressedLastFrame` booleans:
```java
boolean xWasPressedLastFrame = false;
```

### 3d. Making Enemy actually enter Roam by default (not just when losing aggro mid-fight)
Part 3a's `roamBehavior()` call already fires whenever an enemy has no player in `chaseRange` — since that's also true immediately after spawning (before any aggro exists), newly spawned enemies will roam automatically without any extra wiring. No separate "default state" flag is needed for `Enemy` the way `Troop` needed a `Mode` — the existing `if/else` gate in Part 1 already covers both "in combat" and "idle" as its two branches.

---

## Suggested build/test order
1. Fix the Enemy targeting bug (Part 1) first and confirm — put a troop between you and an enemy, verify the enemy paths toward and attacks the troop instead of always beelining for you.
2. Raise the pathfinder caps (Part 2) and retest the `C` charge command with troops spawned at varying distances from the fight — confirm all of them path correctly now, not just the nearest ones.
3. Add Enemy roaming (3a) on a map with a few enemies and watch them wander when the player is far away, rather than standing frozen.
4. Add Troop roam mode + the `x` toggle (3b, 3c) and confirm allies default to wandering near their spawn point until `x` is pressed, then follow formation normally, and toggle back to roam on a second press.