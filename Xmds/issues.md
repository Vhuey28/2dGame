# Fixing: Collision, Performance, Controller Reconnect, Fullscreen, Menu Flow, Duplicate Heroes, Minimap Gaps

Eleven separate issues — grouped by root cause where they overlap. Build/test each section independently; a few share the same underlying method (`isCollidingWithAnyEntity`), so read sections 1 and 11 together before touching that method.

---

## 1. Player stuck between allies (collision blocks all directions)

### Root cause
`Player.pcanMoveTo()` calls `gp.isCollidingWithAnyEntity(nextX, nextY, this)`, which checks the player's next position against **every troop**, not just enemies. When several allies surround the player, every direction the player tries to move intersects a troop's collision rectangle, and movement fully locks up.

### Fix — give Player a dedicated movement-collision check that skips allies
Add to `GamePanel.java`, alongside the existing `isCollidingWithAnyEntity`:
```java
/** Player-specific movement check — deliberately excludes troops and heroes so
    the player can always walk through their own allies, never gets boxed in. */
public boolean isCollidingForPlayerMovement(float nextX, float nextY) {
    int padding = 4;
    Rectangle nextRect = new Rectangle((int)Math.floor(nextX) + padding, (int)Math.floor(nextY) + padding, tileSize - padding * 2, tileSize - padding * 2);

    for (int i = 0; i < enemies.size(); i++) {
        Enemy enemy = enemies.get(i);
        if (enemy != null && !enemy.dead) {
            Rectangle enemyRect = new Rectangle((int)Math.floor(enemy.x) + padding, (int)Math.floor(enemy.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
            if (nextRect.intersects(enemyRect)) return true;
        }
    }
    return false; // troops and heroes intentionally NOT checked — player walks through allies freely
}
```
In `Player.java`, change `pcanMoveTo()`:
```java
private boolean pcanMoveTo(float nextX, float nextY) {
    int left = (int)Math.floor(nextX);
    int right = (int)Math.floor(nextX + gp.tileSize - 1);
    int top = (int)Math.floor(nextY);
    int bottom = (int)Math.floor(nextY + gp.tileSize - 1);

    boolean tileBlocked = gp.isTileBlocked(left, top)
        || gp.isTileBlocked(right, top)
        || gp.isTileBlocked(left, bottom)
        || gp.isTileBlocked(right, bottom);

    if (tileBlocked) return false;
    return !gp.isCollidingForPlayerMovement(nextX, nextY);   // was isCollidingWithAnyEntity
}
```
Troops/enemies keep colliding with each other exactly as before — this change only affects what blocks the *player specifically*.

---

## 2. Home map doesn't appear correctly

You noted this map is being replaced, so no code fix needed here — the earlier TMX-loading and tile-clearing fixes from this conversation should apply cleanly once the new map file is dropped in. If the new map is also a `.tmx`, double check its actual tile/pixel dimensions get picked up the same way `forest.tmx`'s did (`tileM.currentMapWidth`/`currentMapHeight`), especially relevant to section 8 below if `home.txt` is being replaced with a `.tmx` — you'd need to update `initializeHeroes()`, `spawnEnemiesForMap()`, and `teleportPlayerForMap()`'s string checks from `"home.txt"` to the new filename.

---

## 3. Freezes after many enemies spawn

### Root cause
Two compounding costs hit at once during a big spawn burst:
1. `findOpenSpawnSpace()` does an expanding-ring search (up to radius 150, step 8) and calls `isCollidingWithAnyEntity()` — an O(entities) check — at **every candidate point** in that search. Called once per newly-spawned enemy, back to back, in a tight loop (`spawnSurvivalWave()`).
2. Wave size is unbounded and grows every wave (`10, 25, 40, 55...`) — by wave 10 you're spawning 145+ enemies in a single frame, each running that expensive spawn search against an ever-growing entity list.

### Fix A — cap total enemies spawned per wave regardless of wave number
In `spawnSurvivalWave()`:
```java
private static final int MAX_ENEMIES_PER_WAVE = 40; // tune to your performance budget

private void spawnSurvivalWave(int enemyCount) {
    int actualCount = Math.min(enemyCount, MAX_ENEMIES_PER_WAVE);
    enemies.clear();
    ...
    for (int i = 0; i < actualCount; i++) {
        ...
    }
```
This keeps wave *difficulty* scaling (via enemy stats, or by spawning a second smaller wave shortly after — up to you) without letting per-frame spawn cost grow unbounded.

### Fix B — cheaper initial placement, fall back to the expensive search only if actually blocked
Right now every single spawn goes through the full expanding-ring search even when the straightforward candidate point is already open. Add a fast path:
```java
// In findOpenSpawnSpace, this fast-path already exists at the top — good.
// The actual cost is spawnSurvivalWave calling it with essentially random points that
// often ARE blocked (inside water/walls/other entities), forcing the full search every time.
```
Better fix: pre-filter candidate angles/distances to avoid known-bad areas, or simply reduce `maxRadius` from 150 to something smaller (e.g. 60) specifically for survival spawns, since a failed search still costs the same as this reduced radius, just faster:
```java
// Add an overload with a caller-specified radius
public java.awt.Point findOpenSpawnSpace(int startX, int startY, Object self, int maxRadius) {
    int step = 8;
    // ...same body, but use the passed-in maxRadius instead of the hardcoded 150
}
// Keep the original 3-arg version calling this with 150 for backward compatibility
public java.awt.Point findOpenSpawnSpace(int startX, int startY, Object self) {
    return findOpenSpawnSpace(startX, startY, self, 150);
}
```
Call the survival-mode spawns with a smaller radius, e.g. `findOpenSpawnSpace(clamped[0], clamped[1], enemy, 60)`.

---

## 4. Stutter when many troops charge

### Root cause
Every troop's A* path recompute timer resets to the same fixed value (`pathRecomputeTimer = 45`). Since the `C` key command sets `Mode.CHARGE` on **all troops simultaneously**, their recompute timers tick down in lockstep — meaning every ~45 frames, *all* troops recompute an expensive A* path in the same single frame, causing a periodic stutter spike rather than smooth, spread-out cost.

### Fix — stagger recompute timing per troop
In `Troop.java`, when first entering `CHARGE` mode (or in the constructor), add a small random offset:
```java
private int pathRecomputeTimer = (int) (Math.random() * 45); // jittered starting value — staggers first recompute
```
And when resetting after a recompute:
```java
pathRecomputeTimer = 45 + (int) (Math.random() * 20); // 45-65 range instead of a fixed 45 — keeps recomputes spread out over time
```
This alone smooths the spikes significantly. If it's still noticeable with very large troop counts, the next lever is reducing `AStarPathfinder`'s `maxIterations`/`MAX_SEARCH_RADIUS_TILES` specifically for troop pathing (enemies and troops don't need identical search budgets — troops chasing a nearby target rarely need the full 80-tile radius).

---

## 5. Controller fails to connect if plugged in after the game starts

### Root cause
`ControllerHandler.findGamepad()` only runs once, in the constructor. If no controller was connected at that moment, `gamepad` stays `null` forever — `poll()` just returns immediately (`if (gamepad == null) return;`) with no retry logic.

### Fix — periodically re-scan while disconnected
```java
private int reconnectScanTimer = 0;
private static final int RECONNECT_SCAN_INTERVAL = 120; // ~2 seconds at 60fps

public void poll() {
    if (gamepad == null) {
        reconnectScanTimer++;
        if (reconnectScanTimer >= RECONNECT_SCAN_INTERVAL) {
            reconnectScanTimer = 0;
            findGamepad(); // retry — picks up a controller plugged in after launch
        }
        return;
    }
    if (!gamepad.poll()) {
        gamepad = null; // disconnected mid-session — next poll() call will start retrying automatically
        return;
    }
    ...
```
This makes both directions work: plugging in after launch gets detected within ~2 seconds, and unplugging mid-session cleanly falls back to keyboard-only and starts retrying for reconnection.

---

## 6. Fullscreen option

I don't have your actual window-creation file (`Main.java` or similar), so this is generalized — send that file if you want it wired in exactly rather than adapted by hand.

### Approach — scale the existing fixed-size render, don't rewrite the tile grid
Your rendering is built around fixed constants (`screenWidth = tileSize * maxScreenCol`, etc.) — rearchitecting that to be truly dynamic is a much bigger job. The practical fix: keep rendering at the fixed internal resolution, then **scale the whole `Graphics2D` context** to fill however large the actual window/screen is.

In whatever class creates your `JFrame` (adjust names to match your real file):
```java
private boolean isFullscreen = false;

public void toggleFullscreen(JFrame frame) {
    GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    frame.dispose();
    if (!isFullscreen) {
        frame.setUndecorated(true);
        device.setFullScreenWindow(frame);
        isFullscreen = true;
    } else {
        device.setFullScreenWindow(null);
        frame.setUndecorated(false);
        frame.setSize(gamePanel.screenWidth, gamePanel.screenHeight);
        isFullscreen = false;
    }
    frame.setVisible(true);
}
```
In `GamePanel.paintComponent()`, wrap all existing drawing in a scale transform based on the panel's actual current size versus its fixed internal resolution:
```java
public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;

    // Scale to fill whatever the actual window size is, while preserving aspect ratio
    double scaleX = getWidth() / (double) screenWidth;
    double scaleY = getHeight() / (double) screenHeight;
    double scale = Math.min(scaleX, scaleY); // uniform scale, avoids stretching/distortion
    int offsetX = (int) ((getWidth() - screenWidth * scale) / 2);
    int offsetY = (int) ((getHeight() - screenHeight * scale) / 2);

    java.awt.geom.AffineTransform oldTransform = g2.getTransform();
    g2.translate(offsetX, offsetY);
    g2.scale(scale, scale);

    // ... all of your EXISTING drawing code goes here, completely unchanged ...

    g2.setTransform(oldTransform); // restore before g2.dispose()
    g2.dispose();
}
```
This keeps every existing draw call (tile rendering, entity draws, UI, minimap) untouched — they still think they're drawing into a 768×576 canvas — while the transform stretches/letterboxes that output to fill an actual fullscreen window, centered with black bars if the aspect ratio doesn't match exactly.

Bind a toggle key (e.g. F11) in your existing keyboard listener to call `toggleFullscreen()`, and add it as a Settings-menu option too, following the same pattern as the rebind screen from the earlier controller-settings guide.

---

## 7. Menu flow needs back navigation

Your survival-mode setup flow (`MODE_SELECT → ALLY_YES_NO → ALLY_TYPE → READY`) is currently forward-only — no way to back out of a wrong click.

### Fix — add a Back button to each intermediate stage
```java
private Rectangle backButton = new Rectangle(20, screenHeight - 60, 100, 36);
```
In `drawStartMenu()`, draw it on every stage except `MODE_SELECT` (nothing to go back to) and `READY` (already at the end — Enter starts the game, no back needed there either, though you could add one to return to ALLY_TYPE if desired):
```java
case ALLY_YES_NO:
case ALLY_TYPE:
    drawMenuButton(g2, backButton, "< Back");
    break;
```
In `handleStartMenuClick()`, check it first, before the stage-specific logic:
```java
private void handleStartMenuClick(java.awt.Point p) {
    if ((menuStage == MenuStage.ALLY_YES_NO || menuStage == MenuStage.ALLY_TYPE) && backButton.contains(p)) {
        menuStage = (menuStage == MenuStage.ALLY_TYPE) ? MenuStage.ALLY_YES_NO : MenuStage.MODE_SELECT;
        return;
    }
    switch (menuStage) {
        ...
```
Same idea applies to the settings screen from the earlier guide — it already has ESC-to-back, but consider adding a visible on-screen Back button too, since ESC alone isn't discoverable for a player who hasn't read the code.

---

## 8. Duplicate heroes after recruiting and changing maps

### Root cause
`initializeHeroes()` correctly preserves recruited heroes:
```java
heroes.removeIf(h -> !h.isRecruited);
```
But then it **unconditionally re-adds** the map-specific heroes every single time that map loads:
```java
if ("map1.txt".equals(currentMap)) {
    Hero warrior = new Hero(this, "vince", Hero.HeroClass.WARRIOR, ...);
    heroes.add(warrior);   // <-- runs every time you enter map1.txt, no check for "already have one"
```
If you recruited Vince, left map1, and come back later, `removeIf` keeps the already-recruited Vince in the list — then this block adds a **second, brand-new, unrecruited** Vince right alongside him. Two Vinces.

### Fix — check for an existing hero by identity before adding
```java
private boolean heroAlreadyPresent(String name) {
    for (Hero h : heroes) {
        if (h.name.equals(name)) return true;
    }
    return false;
}
```
Wrap every map-specific `heroes.add(...)` call:
```java
if ("map1.txt".equals(currentMap)) {
    if (!heroAlreadyPresent("vince")) {
        Hero warrior = new Hero(this, "vince", Hero.HeroClass.WARRIOR, tileSize * 4, tileSize * 10);
        warrior.recruitmentLines = new String[]{...};
        heroes.add(warrior);
    }
    if (!heroAlreadyPresent("triss")) {
        Hero mage = new Hero(this, "triss", Hero.HeroClass.MAGE, tileSize * 12, tileSize * 10);
        mage.recruitmentLines = new String[]{...};
        heroes.add(mage);
    }
} else if ("home.txt".equals(currentMap)) {
    if (!heroAlreadyPresent("Sylas")) { ... }
    if (!heroAlreadyPresent("Sister Mara")) { ... }
    if (!heroAlreadyPresent("Kira")) { ... }
}
```
This correctly handles both cases: a recruited hero returning to their map isn't duplicated (already present, skipped), and an unrecruited hero who was removed by `removeIf` and never recruited gets a fresh instance next visit (not present, added) — exactly the intended behavior either way.

---

## 9. Survival portal not shown on minimap

### Root cause
`drawTeleporterMarkers()` (the minimap method) only iterates `mapLinks` — it has no idea `survivalPortal` exists, since that's a separate field entirely, not part of the `mapLinks` list.

### Fix — draw it alongside the other teleporter markers
Add to `drawTeleporterMarkers()`, right after the existing `mapLinks` loop:
```java
private void drawTeleporterMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                                    float viewRadiusTiles) {
    g2.setColor(Color.magenta);
    for (MapLink link : mapLinks) {
        ... existing code unchanged ...
    }

    // Survival portal — separate field, not part of mapLinks, needs its own marker
    if (gameMode == GameMode.SURVIVAL && survivalPortal != null) {
        float col = (float) survivalPortal.area.x / tileSize;
        float row = (float) survivalPortal.area.y / tileSize;
        float pxPerTile = mapSizeHalf() / viewRadiusTiles;
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;

        g2.setColor(Color.yellow); // distinct from the magenta regular-portal color
        int r = 5;
        int[] xs = {(int) px, (int) px + r, (int) px, (int) px - r};
        int[] ys = {(int) py - r, (int) py, (int) py + r, (int) py};
        g2.fillPolygon(xs, ys, 4);
    }
}
```

---

## 10. Heroes not shown on minimap

### Fix — new marker method, same pattern as troops/enemies
```java
private void drawHeroMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                              float viewRadiusTiles) {
    float pxPerTile = mapSizeHalf() / viewRadiusTiles;
    for (Hero hero : new java.util.ArrayList<>(heroes)) { // defensive copy, same reasoning as the earlier CME fix
        if (hero == null) continue;
        float col = hero.x / tileSize;
        float row = hero.y / tileSize;
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
        int size = Math.max(3, (int) pxPerTile);

        g2.setColor(hero.isRecruited ? Color.green : Color.gray); // recruited vs. not-yet-recruited reads differently at a glance
        g2.fillOval((int) px, (int) py, size, size);
    }
}
```
Call it inside `drawMiniMap()`, alongside the other marker calls:
```java
drawTroopMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
drawEnemyMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
drawHeroMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);   // <-- add this
drawCameraRect(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
```

---

## 11. Heroes need collision with ally/enemy troops (player-hero collision unchanged)

### Root cause
`isCollidingWithAnyEntity()` never checks the `heroes` list at all — meaning troops and enemies currently walk straight through heroes with no collision in either direction. `Hero.canMoveTo()` does call this same method, so heroes already avoid *player, enemies, and troops* — but nothing avoids *heroes*, since the check was never reciprocal.

### Fix — add a heroes loop to the shared method
```java
public boolean isCollidingWithAnyEntity(float nextX, float nextY, Object self) {
    int padding = 4;
    Rectangle nextRect = new Rectangle((int)Math.floor(nextX) + padding, (int)Math.floor(nextY) + padding, tileSize - padding * 2, tileSize - padding * 2);

    // Check player (unchanged)
    if (self != player) {
        Rectangle playerRect = new Rectangle((int)Math.floor(player.x) + padding, (int)Math.floor(player.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
        if (nextRect.intersects(playerRect)) return true;
    }

    // Check enemies (unchanged)
    for (int i = 0; i < enemies.size(); i++) {
        Enemy enemy = enemies.get(i);
        if (enemy != null && enemy != self && !enemy.dead) {
            Rectangle enemyRect = new Rectangle((int)Math.floor(enemy.x) + padding, (int)Math.floor(enemy.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
            if (nextRect.intersects(enemyRect)) return true;
        }
    }

    // Check troops (unchanged)
    for (int i = 0; i < troops.size(); i++) {
        entity.Troop troop = troops.get(i);
        if (troop != null && troop != self && troop.health > 0) {
            Rectangle troopRect = new Rectangle((int)Math.floor(troop.x) + padding, (int)Math.floor(troop.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
            if (nextRect.intersects(troopRect)) return true;
        }
    }

    // NEW — check heroes, so troops/enemies (and heroes moving past each other) can't walk through them
    for (int i = 0; i < heroes.size(); i++) {
        Hero hero = heroes.get(i);
        if (hero != null && hero != self) {
            Rectangle heroRect = new Rectangle((int)Math.floor(hero.x) + padding, (int)Math.floor(hero.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
            if (nextRect.intersects(heroRect)) return true;
        }
    }

    return false;
}
```

### Why this doesn't affect player-hero collision (as required)
After section 1's fix, `Player.pcanMoveTo()` calls the new `isCollidingForPlayerMovement()` instead of this method entirely — so the player's own movement never runs this heroes-check, and player-hero interaction is left exactly as it already was (unblocked, same as before this whole fix). Only `Troop.canMoveTo()`, `Enemy.canMoveTo()`, and `Hero.canMoveTo()` still route through `isCollidingWithAnyEntity()`, so they're the only ones affected by the new heroes loop — matching "heroes need collision with ally/enemy troops... collision between them and the player can be left the same" precisely.

---

## Suggested build/test order
1. Sections 1 + 11 together first (they touch the same shared method) — verify player walks through allies freely, then verify troops/enemies now stop at heroes.
2. Section 8 (duplicate heroes) — quick, isolated, test by recruiting a hero, changing maps, and returning.
3. Sections 9 + 10 (minimap markers) — visual-only, low risk, easy to verify at a glance.
4. Section 5 (controller reconnect) — test by unplugging/replugging mid-session.
5. Sections 3 + 4 (performance) — these need play-testing at scale (multiple waves, many troops charging) to confirm the stutter/freeze is actually resolved, not just theoretically fixed.
6. Section 6 (fullscreen) and Section 7 (menu back-navigation) last — both are larger, more visible UI changes worth testing in isolation once the underlying gameplay bugs are stable.