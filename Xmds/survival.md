# Survival Game Mode — Implementation Guide

## Overview of what's being built
A second game mode ("Survival") selectable from the start menu, alongside the existing game (renamed "Sandbox" for this purpose). Survival adds:
- A pre-game setup flow: ally yes/no → ally type (melee / archer / both) if yes.
- Escalating enemy waves: 10 enemies → +15 per wave thereafter.
- Allies spawned at wave start, count = half the wave's enemy count.
- A power-up choice screen after each cleared wave.
- A portal that spawns next to the player after each wave, leading to a random map, which then starts the next wave.
- Inventory panel disabled.
- Coin spawns disabled.

This is a genuinely large feature — it touches menu state, wave logic, map transitions, and UI gating throughout `GamePanel`. Build and test each section below in order rather than all at once.

---

## 1. New state fields

Add to `GamePanel`, near your other state booleans (`gameStarted`, `gamePaused`, etc.):

```java
public enum GameMode { SANDBOX, SURVIVAL }
public GameMode gameMode = GameMode.SANDBOX;

// Pre-game menu flow state — only relevant before gameStarted becomes true
public enum MenuStage { MODE_SELECT, ALLY_YES_NO, ALLY_TYPE, READY }
public MenuStage menuStage = MenuStage.MODE_SELECT;

public enum AllyChoice { NONE, MELEE_ONLY, ARCHER_ONLY, BOTH }
public AllyChoice survivalAllyChoice = AllyChoice.NONE;

// Survival session state
public int survivalWaveNumber = 0;
public int survivalEnemyCountForWave = 10; // first wave; +15 each wave after
public boolean survivalWaveTransition = false; // true while power-up menu / portal-wait is showing
public boolean survivalPowerUpMenuOpen = false;
public java.util.List<String> availablePowerUps = new java.util.ArrayList<>(
    java.util.Arrays.asList("Damage Up", "Speed Up", "Max Health Up", "Faster Regen", "Extra Dodge Range", "Attack Speed Up")
);
public java.util.List<String> activePowerUps = new java.util.ArrayList<>(); // stacked for the whole session
private String[] currentPowerUpChoices = new String[3];

// Survival portal — a single-use MapLink-like object spawned after a cleared wave
private MapLink survivalPortal = null;
private final String[] survivalMapPool = {"map1.txt", "mapA.txt", "forest.tmx", "home.txt"}; // extend as you add maps
```

`MapLink` is already a private inner class in your `GamePanel` — the survival portal reuses it directly rather than inventing a new type.

---

## 2. Start menu → mode select → ally setup flow

Your current `drawStartMenu()` just shows a title and "Press ENTER to start." Replace the flow so ENTER isn't the only way in — it becomes button-driven, matching your existing pattern of clickable `Rectangle` zones (like `inventoryButton`, `getRestartButtonRect()`).

### 2a. Add rectangles for the new menu buttons
```java
private Rectangle sandboxModeButton = new Rectangle(screenWidth/2 - 160, screenHeight/2, 140, 44);
private Rectangle survivalModeButton = new Rectangle(screenWidth/2 + 20, screenHeight/2, 140, 44);
private Rectangle allyYesButton = new Rectangle(screenWidth/2 - 160, screenHeight/2, 140, 44);
private Rectangle allyNoButton = new Rectangle(screenWidth/2 + 20, screenHeight/2, 140, 44);
private Rectangle allyMeleeButton = new Rectangle(screenWidth/2 - 220, screenHeight/2, 130, 44);
private Rectangle allyArcherButton = new Rectangle(screenWidth/2 - 65, screenHeight/2, 130, 44);
private Rectangle allyBothButton = new Rectangle(screenWidth/2 + 90, screenHeight/2, 130, 44);
```

### 2b. Replace `drawStartMenu()` with a stage-based version
```java
private void drawStartMenu(Graphics2D g2) {
    g2.setColor(Color.black);
    g2.fillRect(0, 0, screenWidth, screenHeight);
    g2.setColor(Color.white);
    g2.setFont(new Font("Arial", Font.BOLD, 40));
    String title = "Chronicle Conquest Beta";
    int titleWidth = g2.getFontMetrics().stringWidth(title);
    g2.drawString(title, (screenWidth - titleWidth) / 2, screenHeight / 3);

    g2.setFont(new Font("Arial", Font.PLAIN, 20));

    switch (menuStage) {
        case MODE_SELECT:
            drawMenuPrompt(g2, "Choose a game mode");
            drawMenuButton(g2, sandboxModeButton, "Sandbox");
            drawMenuButton(g2, survivalModeButton, "Survival");
            break;
        case ALLY_YES_NO:
            drawMenuPrompt(g2, "Start with allies?");
            drawMenuButton(g2, allyYesButton, "Yes");
            drawMenuButton(g2, allyNoButton, "No");
            break;
        case ALLY_TYPE:
            drawMenuPrompt(g2, "Choose ally type");
            drawMenuButton(g2, allyMeleeButton, "Melee Only");
            drawMenuButton(g2, allyArcherButton, "Archer Only");
            drawMenuButton(g2, allyBothButton, "Both");
            break;
        case READY:
            String prompt = "Press ENTER to start";
            int promptWidth = g2.getFontMetrics().stringWidth(prompt);
            g2.drawString(prompt, (screenWidth - promptWidth) / 2, screenHeight / 2);
            break;
    }

    String controls1 = "WASD to move";
    String controls2 = "P to pause / resume";
    int controlsY = screenHeight / 2 + 90;
    g2.drawString(controls1, (screenWidth - g2.getFontMetrics().stringWidth(controls1)) / 2, controlsY);
    g2.drawString(controls2, (screenWidth - g2.getFontMetrics().stringWidth(controls2)) / 2, controlsY + 26);
}

private void drawMenuPrompt(Graphics2D g2, String text) {
    int w = g2.getFontMetrics().stringWidth(text);
    g2.drawString(text, (screenWidth - w) / 2, screenHeight / 2 - 40);
}

private void drawMenuButton(Graphics2D g2, Rectangle btn, String label) {
    g2.setColor(new Color(64, 64, 64, 220));
    g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
    g2.setColor(Color.white);
    g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
    int textW = g2.getFontMetrics().stringWidth(label);
    g2.drawString(label, btn.x + (btn.width - textW) / 2, btn.y + 28);
}
```

### 2c. Handle menu clicks
In your constructor's existing `MouseAdapter`'s `mouseClicked`, add handling before the `gameStarted` gate (currently the first check inside `mouseClicked` is `if (gamePaused)`):
```java
@Override
public void mouseClicked(MouseEvent e) {
    if (!gameStarted) {
        handleStartMenuClick(e.getPoint());
        return;
    }
    if (gamePaused) {
        ...
```
Add the handler method:
```java
private void handleStartMenuClick(java.awt.Point p) {
    switch (menuStage) {
        case MODE_SELECT:
            if (sandboxModeButton.contains(p)) {
                gameMode = GameMode.SANDBOX;
                menuStage = MenuStage.READY;
            } else if (survivalModeButton.contains(p)) {
                gameMode = GameMode.SURVIVAL;
                menuStage = MenuStage.ALLY_YES_NO;
            }
            break;
        case ALLY_YES_NO:
            if (allyYesButton.contains(p)) {
                menuStage = MenuStage.ALLY_TYPE;
            } else if (allyNoButton.contains(p)) {
                survivalAllyChoice = AllyChoice.NONE;
                menuStage = MenuStage.READY;
            }
            break;
        case ALLY_TYPE:
            if (allyMeleeButton.contains(p)) {
                survivalAllyChoice = AllyChoice.MELEE_ONLY;
                menuStage = MenuStage.READY;
            } else if (allyArcherButton.contains(p)) {
                survivalAllyChoice = AllyChoice.ARCHER_ONLY;
                menuStage = MenuStage.READY;
            } else if (allyBothButton.contains(p)) {
                survivalAllyChoice = AllyChoice.BOTH;
                menuStage = MenuStage.READY;
            }
            break;
        case READY:
            break; // ENTER key starts the game from here, no click needed
    }
}
```

### 2d. Gate the ENTER key behind `MenuStage.READY`
Your existing keyPressed handler:
```java
if (!gameStarted && code == KeyEvent.VK_ENTER) {
    gameStarted = true;
    gamePaused = false;
    repaint();
    return;
}
```
becomes:
```java
if (!gameStarted && code == KeyEvent.VK_ENTER && menuStage == MenuStage.READY) {
    gameStarted = true;
    gamePaused = false;
    if (gameMode == GameMode.SURVIVAL) {
        startSurvivalMode();
    }
    repaint();
    return;
}
```

---

## 3. Starting survival mode

```java
private void startSurvivalMode() {
    survivalWaveNumber = 1;
    survivalEnemyCountForWave = 10;
    activePowerUps.clear();
    setupMap("map1.txt"); // or whichever map you want survival to always begin on
    teleportPlayerForMap("map1.txt");
    spawnSurvivalWave(survivalEnemyCountForWave);
}
```

### Spawning a wave with proportional allies
This generalizes your existing `spawnWaveEnemies()` (which currently hardcodes "10 enemies + 1 boss") into a parameterized version:
```java
private void spawnSurvivalWave(int enemyCount) {
    enemies.clear();
    enemySquad.members.clear();
    enemySquad.commander = null;

    for (int i = 0; i < enemyCount; i++) {
        Enemy enemy = new Enemy(this);
        int sx = tileSize * 4 + random.nextInt(tileSize * 30);
        int sy = tileSize * 4 + random.nextInt(tileSize * 20);
        java.awt.Point openPt = findOpenSpawnSpace(sx, sy, enemy);
        enemy.x = openPt.x;
        enemy.y = openPt.y;
        enemy.setType(i % 5 == 0 ? Enemy.Type.ARCHER : Enemy.Type.TROOP); // every 5th enemy is an archer, tune to taste
        enemies.add(enemy);
        enemySquad.addMember(enemy);
    }

    // Boss every wave, scaling with wave number
    Enemy boss = new Enemy(this);
    java.awt.Point bossPt = findOpenSpawnSpace((int)player.x + tileSize * 6, (int)player.y, boss);
    boss.x = bossPt.x;
    boss.y = bossPt.y;
    boss.setType(Enemy.Type.BOSS);
    boss.maxHealth = 60 + (survivalWaveNumber * 10);
    boss.health = boss.maxHealth;
    boss.goldDrop = 0; // coin spawns disabled in survival — see section 6
    enemies.add(boss);
    enemySquad.addMember(boss);
    enemySquad.setCommander(boss);

    spawnSurvivalAllies(enemyCount);

    waveActive = true;
    waveMessageTimer = 60;
    survivalWaveTransition = false;
}

private void spawnSurvivalAllies(int enemyCount) {
    if (survivalAllyChoice == AllyChoice.NONE) return;

    int allyCount = enemyCount / 2; // "always half of how many enemies there are in each wave"
    for (int i = 0; i < allyCount; i++) {
        entity.Troop.Role role;
        if (survivalAllyChoice == AllyChoice.MELEE_ONLY) {
            role = entity.Troop.Role.MELEE;
        } else if (survivalAllyChoice == AllyChoice.ARCHER_ONLY) {
            role = entity.Troop.Role.ARCHER;
        } else { // BOTH — alternate
            role = (i % 2 == 0) ? entity.Troop.Role.MELEE : entity.Troop.Role.ARCHER;
        }
        entity.Troop ally = new entity.Troop(this, (int)player.x, (int)player.y, role);
        java.awt.Point openPt = findOpenSpawnSpace((int)player.x + tileSize * (i % 4), (int)player.y + tileSize, ally);
        ally.x = openPt.x;
        ally.y = openPt.y;
        troops.add(ally);
        allySquad.addMember(ally);
    }
}
```

---

## 4. Detecting a cleared wave

In `update()`, after your existing enemy-update loop, add a survival-specific check. Place this right after the block that already updates `enemies` (the one calling `enemy.update(player)`):
```java
if (gameMode == GameMode.SURVIVAL && waveActive && !survivalWaveTransition) {
    boolean allDead = true;
    for (Enemy e : enemies) {
        if (!e.dead) { allDead = false; break; }
    }
    if (allDead) {
        onSurvivalWaveCleared();
    }
}
```

```java
private void onSurvivalWaveCleared() {
    waveActive = false;
    survivalWaveTransition = true;
    rollPowerUpChoices();
    survivalPowerUpMenuOpen = true;
    gamePaused = true; // reuse your existing pause gate so normal update logic halts during the choice
}
```

---

## 5. Power-up selection screen

### 5a. Rolling three random choices
```java
private void rollPowerUpChoices() {
    java.util.List<String> pool = new java.util.ArrayList<>(availablePowerUps);
    java.util.Collections.shuffle(pool, random);
    for (int i = 0; i < 3 && i < pool.size(); i++) {
        currentPowerUpChoices[i] = pool.get(i);
    }
}
```

### 5b. Drawing the screen
Add a new draw method, called from `paintComponent()` right where `drawPauseMenu()` is currently called:
```java
if (gamePaused) {
    if (survivalPowerUpMenuOpen) {
        drawPowerUpMenu(g2);
    } else {
        drawPauseMenu(g2);
    }
}
```

```java
private Rectangle[] powerUpButtons = {
    new Rectangle(screenWidth/2 - 330, screenHeight/2, 200, 60),
    new Rectangle(screenWidth/2 - 100, screenHeight/2, 200, 60),
    new Rectangle(screenWidth/2 + 130, screenHeight/2, 200, 60),
};

private void drawPowerUpMenu(Graphics2D g2) {
    g2.setColor(new Color(0, 0, 0, 200));
    g2.fillRect(0, 0, screenWidth, screenHeight);
    g2.setColor(Color.white);
    g2.setFont(new Font("Arial", Font.BOLD, 30));
    String title = "Choose a Power-Up";
    int tw = g2.getFontMetrics().stringWidth(title);
    g2.drawString(title, (screenWidth - tw) / 2, screenHeight / 2 - 60);

    g2.setFont(new Font("Arial", Font.PLAIN, 16));
    for (int i = 0; i < powerUpButtons.length; i++) {
        if (currentPowerUpChoices[i] == null) continue;
        Rectangle btn = powerUpButtons[i];
        g2.setColor(new Color(64, 64, 64, 220));
        g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 12, 12);
        g2.setColor(Color.white);
        g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 12, 12);
        int textW = g2.getFontMetrics().stringWidth(currentPowerUpChoices[i]);
        g2.drawString(currentPowerUpChoices[i], btn.x + (btn.width - textW) / 2, btn.y + 36);
    }
}
```

### 5c. Handling the click and applying the effect
In `mouseClicked`, add before your existing `if (gamePaused)` restart-button check:
```java
if (gamePaused && survivalPowerUpMenuOpen) {
    for (int i = 0; i < powerUpButtons.length; i++) {
        if (currentPowerUpChoices[i] != null && powerUpButtons[i].contains(e.getPoint())) {
            applyPowerUp(currentPowerUpChoices[i]);
            survivalPowerUpMenuOpen = false;
            gamePaused = false;
            spawnSurvivalPortal();
            return;
        }
    }
    return; // swallow clicks while this menu is open so they don't fall through to gameplay
}
```

```java
private void applyPowerUp(String powerUp) {
    activePowerUps.add(powerUp);
    switch (powerUp) {
        case "Damage Up":
            // Your attack damage constants are currently hardcoded literals (12, 8, etc.)
            // in Player.performMeleeAttack() and the num1/num2/num3 blocks. Convert those
            // to fields (e.g. meleeDamage, projectileDamage) and increment them here, e.g.:
            // player.meleeDamage += 5;
            break;
        case "Speed Up":
            player.speed += 0.5f;
            break;
        case "Max Health Up":
            player.maxHealth += 20;
            player.health += 20;
            break;
        case "Faster Regen":
            // reduce stamina/mana regen timers, e.g. player.staminaRegenTimer threshold
            break;
        case "Extra Dodge Range":
            // increase the dodge distance constant in Player's 'e' dodge handling
            break;
        case "Attack Speed Up":
            player.attackCooldown = Math.max(4, player.attackCooldown - 2); // only affects current cooldown; see note below
            break;
    }
}
```
**Note:** several of these (damage, attack speed, dodge range) currently exist as inline literals in `Player.java` rather than fields, so applying a permanent boost means first converting those literals to instance fields you can increment. I don't have enough of `Player.java`'s full attack code to make every one of these changes precisely — flag which power-ups you want first and I'll give you exact before/after edits once I can see the specific lines.

---

## 6. The survival portal

```java
private void spawnSurvivalPortal() {
    int px = (int) player.x + tileSize * 2;
    int py = (int) player.y;
    String targetMap = survivalMapPool[random.nextInt(survivalMapPool.length)];
    survivalPortal = new MapLink(new Rectangle(px, py, tileSize, tileSize), targetMap, "Next Wave");
}
```

Draw it alongside your existing `drawMapLinks()` — add a survival-specific check at the top of that method:
```java
private void drawMapLinks(Graphics2D g2) {
    if (gameMode == GameMode.SURVIVAL && survivalPortal != null) {
        int x = survivalPortal.area.x - (int)cameraX;
        int y = survivalPortal.area.y - (int)cameraY;
        g2.setColor(new Color(255, 215, 0, 150));
        g2.fillRect(x, y, survivalPortal.area.width, survivalPortal.area.height);
        g2.setColor(Color.yellow);
        g2.drawRect(x, y, survivalPortal.area.width, survivalPortal.area.height);
        g2.drawString("Next Wave", x + 2, y + 12);
    }
    for (MapLink link : mapLinks) {
        ...  // existing sandbox-mode portal drawing, unchanged
```

Detect the player walking into it — in `update()`, right where your existing portal-collision loop runs:
```java
if (gameMode == GameMode.SURVIVAL && survivalPortal != null && portalCooldown == 0) {
    if (playerRect.intersects(survivalPortal.area)) {
        advanceSurvivalWave(survivalPortal.targetMap);
        portalCooldown = 30;
    }
}
```

```java
private void advanceSurvivalWave(String targetMap) {
    survivalWaveNumber++;
    survivalEnemyCountForWave += 15;
    survivalPortal = null;

    currentMap = targetMap;
    tileM.loadMap(targetMap);
    coins.clear();
    troops.clear(); // allies respawn fresh each wave alongside the new enemy count
    allySquad.members.clear();

    java.awt.Point spawnPt = findOpenSpawnSpace((int)player.x, (int)player.y, player);
    player.x = spawnPt.x;
    player.y = spawnPt.y;

    spawnSurvivalWave(survivalEnemyCountForWave);
}
```
Note this deliberately does **not** call your existing `setupMap()`, since that method resets survival-irrelevant sandbox state (portrait links for the exploration mode) and calls `spawnEnemiesForMap()` (the sandbox per-map enemy sets) rather than the scaling survival wave — `advanceSurvivalWave()` is a parallel path built specifically for this mode.

---

## 7. Disabling the inventory panel in survival

In `drawPlayerStats()`, wrap the existing inventory button block:
```java
// Inventory button
g2.setColor(new Color(64, 64, 64, 200));
g2.fillRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
...
if (inventoryOpen) {
    drawInventoryPanel(g2);
}
```
becomes:
```java
if (gameMode != GameMode.SURVIVAL) {
    // Inventory button
    g2.setColor(new Color(64, 64, 64, 200));
    g2.fillRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
    ...
    if (inventoryOpen) {
        drawInventoryPanel(g2);
    }
}
```
And guard the click handler in `mouseClicked` the same way:
```java
if (gameMode != GameMode.SURVIVAL && inventoryButton.contains(e.getPoint())) {
    inventoryOpen = !inventoryOpen;
    selectedInventoryIndex = -1;
    repaint();
    return;
}
```

---

## 8. Disabling coin spawns in survival

Every call to `spawnCoins(...)` needs a guard. These currently happen in `Enemy.java`'s death-animation blocks (boss/archer/troop death loot) and in `Troop.java`'s archer/melee death blocks. Rather than adding `if` checks in a dozen places, the cleanest fix is a single guard inside `GamePanel.spawnCoins()` itself:
```java
public void spawnCoins(int startX, int startY, int count) {
    if (gameMode == GameMode.SURVIVAL) return; // coin spawns disabled in survival mode
    for (int i = 0; i < count; i++) {
        int offsetX = random.nextInt(tileSize) - tileSize / 2;
        int offsetY = random.nextInt(tileSize) - tileSize / 2;
        coins.add(new CoinItem(startX + offsetX, startY + offsetY));
    }
}
```
Since every death-loot call already routes through `gp.spawnCoins(...)`, this one change disables all coin drops in survival without touching `Enemy.java` or `Troop.java` at all.

---

## Suggested build/test order
1. Menu flow (section 2) — verify you can navigate mode select → ally choice → ready, and sandbox mode still works exactly as before.
2. Wave spawning + ally proportion (section 3) — verify wave 1 spawns 10 enemies + correct ally count.
3. Wave-clear detection + portal (sections 4, 6) — verify defeating all enemies triggers the transition and the portal appears/works.
4. Power-up menu (section 5) — start with 2-3 simple power-ups (Max Health Up, Speed Up are the easiest since they're direct field increments) before tackling the ones requiring `Player.java` refactors.
5. Inventory/coin disabling (sections 7, 8) — quick, low-risk, do last since nothing else depends on it.