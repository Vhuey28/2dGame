# Party Phase 3 — Pre-Battle Deployment Screen

Builds on Phase 2 (`Party`, `PartyManager`, faction-aware encounters, fleeing) and reuses the `Formation` class from your existing Squad AI system — the same `LINE`/`WEDGE`/`CIRCLE`/`SCATTERED` types your troops already understand get repurposed here as a player-facing pre-battle choice instead of only an AI-driven one.

This inserts a new state **between** "encounter detected" and "battle actually starts" — right now `checkPartyEncounters()` calls `startBattle()` immediately; this phase splits that into "show the deployment screen" and "confirm and actually begin," with a retreat option in between.

---

## 1. New game layer

Extend the `GameLayer` enum from the Part 3 architecture doc:
```java
public enum GameLayer { OVERWORLD, DEPLOYMENT, BATTLE }
```

New fields in `GamePanel`:
```java
private party.Party pendingEnemyParty;
private boolean pendingCaughtFleeing;
private entity.Formation.Type selectedFormation = entity.Formation.Type.WEDGE; // default — tune to your preferred starting choice
private Map<party.Party, Integer> retreatCooldowns = new HashMap<>(); // prevents an instantly-re-triggered encounter after retreating
```

---

## 2. Encounter detection now opens the deployment screen instead of battling immediately

Update `checkPartyEncounters()` from Phase 2:
```java
private void checkPartyEncounters() {
    for (party.Party aiParty : partyManager.aiParties) {
        Integer cooldown = retreatCooldowns.get(aiParty);
        if (cooldown != null && cooldown > 0) continue; // recently retreated from this specific party — skip it

        float dx = aiParty.x - playerParty.x;
        float dy = aiParty.y - playerParty.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist >= ENCOUNTER_RADIUS) continue;

        boolean hostile = aiParty.faction == null || playerParty.faction == null
            || aiParty.faction.relations.get(playerParty.faction) == kingdom.Kingdom.DiplomaticRelation.WAR;
        if (!hostile) continue;

        boolean caughtFleeing = aiParty.state == Party.State.FLEEING;
        enterDeploymentScreen(aiParty, caughtFleeing);
        break;
    }
}

private void enterDeploymentScreen(party.Party enemyParty, boolean caughtFleeing) {
    pendingEnemyParty = enemyParty;
    pendingCaughtFleeing = caughtFleeing;
    currentLayer = GameLayer.DEPLOYMENT;
}
```

Tick down retreat cooldowns once per overworld update, alongside the existing `partyManager.update()` call:
```java
if (gameMode == GameMode.SANDBOX && currentLayer == GameLayer.OVERWORLD) {
    partyManager.update(this, kingdomSim.allKingdoms, playerParty);
    checkPartyEncounters();

    retreatCooldowns.replaceAll((party, ticks) -> ticks - 1);
    retreatCooldowns.values().removeIf(ticks -> ticks <= 0);
}
```

---

## 3. The deployment screen

### Layout buttons
```java
private Rectangle formationLineButton = new Rectangle(60, 300, 130, 40);
private Rectangle formationWedgeButton = new Rectangle(200, 300, 130, 40);
private Rectangle formationCircleButton = new Rectangle(340, 300, 130, 40);
private Rectangle formationScatteredButton = new Rectangle(480, 300, 130, 40);
private Rectangle beginBattleButton = new Rectangle(screenWidth/2 - 100, 420, 200, 50);
private Rectangle retreatButton = new Rectangle(screenWidth/2 - 100, 480, 200, 44);
```

### Draw method
```java
private void drawDeploymentScreen(Graphics2D g2) {
    g2.setColor(new Color(10, 10, 10, 235));
    g2.fillRect(0, 0, screenWidth, screenHeight);

    g2.setColor(Color.white);
    g2.setFont(new Font("Arial", Font.BOLD, 30));
    String title = pendingCaughtFleeing ? "You caught them fleeing!" : "Enemy party sighted!";
    g2.drawString(title, screenWidth/2 - g2.getFontMetrics().stringWidth(title)/2, 60);

    g2.setFont(new Font("Arial", Font.PLAIN, 18));

    // Player force summary
    int meleeCount = (int) playerParty.troops.stream().filter(t -> t.role == entity.Troop.Role.MELEE).count();
    int archerCount = (int) playerParty.troops.stream().filter(t -> t.role == entity.Troop.Role.ARCHER).count();
    g2.drawString("Your Forces:", 60, 120);
    g2.drawString("Melee: " + meleeCount, 80, 150);
    g2.drawString("Archers: " + archerCount, 80, 175);
    g2.drawString("Heroes: " + playerParty.heroes.size(), 80, 200);

    // Enemy force summary — deliberately approximate rather than exact numbers, for a bit of pre-battle uncertainty
    String enemyStrengthLabel = getApproximateStrengthLabel(pendingEnemyParty, playerParty);
    g2.drawString("Enemy Forces:", 400, 120);
    g2.drawString("Estimated strength: " + enemyStrengthLabel, 420, 150);
    g2.drawString("Troop count: ~" + roundToNearest(pendingEnemyParty.troops.size(), 3), 420, 175);

    // Formation selection
    g2.drawString("Choose Formation:", 60, 270);
    drawFormationButton(g2, formationLineButton, "Line", entity.Formation.Type.LINE);
    drawFormationButton(g2, formationWedgeButton, "Wedge", entity.Formation.Type.WEDGE);
    drawFormationButton(g2, formationCircleButton, "Circle", entity.Formation.Type.CIRCLE);
    drawFormationButton(g2, formationScatteredButton, "Scattered", entity.Formation.Type.SCATTERED);

    // Actions
    g2.setColor(new Color(40, 120, 40));
    g2.fillRoundRect(beginBattleButton.x, beginBattleButton.y, beginBattleButton.width, beginBattleButton.height, 10, 10);
    g2.setColor(Color.white);
    g2.drawString("Begin Battle", beginBattleButton.x + 45, beginBattleButton.y + 32);

    boolean canRetreat = pendingEnemyParty.state != Party.State.PURSUING; // pursuing parties are harder to shake — see attemptRetreat()
    g2.setColor(canRetreat ? new Color(120, 40, 40) : new Color(60, 60, 60));
    g2.fillRoundRect(retreatButton.x, retreatButton.y, retreatButton.width, retreatButton.height, 10, 10);
    g2.setColor(Color.white);
    String retreatLabel = canRetreat ? "Retreat" : "Retreat (risky — they're pursuing)";
    g2.drawString(retreatLabel, retreatButton.x + 20, retreatButton.y + 28);
}

private void drawFormationButton(Graphics2D g2, Rectangle btn, String label, entity.Formation.Type type) {
    boolean selected = selectedFormation == type;
    g2.setColor(selected ? new Color(80, 80, 160) : new Color(50, 50, 50));
    g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 8, 8);
    g2.setColor(Color.white);
    g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 8, 8);
    int textW = g2.getFontMetrics().stringWidth(label);
    g2.drawString(label, btn.x + (btn.width - textW)/2, btn.y + 26);
}

private String getApproximateStrengthLabel(party.Party enemy, party.Party player) {
    double ratio = player.getTotalStrength() == 0 ? 1.0 : (double) enemy.getTotalStrength() / player.getTotalStrength();
    if (ratio < 0.7) return "Weak";
    if (ratio < 1.3) return "Moderate";
    if (ratio < 2.0) return "Strong";
    return "Overwhelming";
}

private int roundToNearest(int value, int nearest) {
    return Math.max(nearest, Math.round((float) value / nearest) * nearest); // fuzzes the exact count, keeps some fog-of-war
}
```

Call it from `paintComponent()`:
```java
if (currentLayer == GameLayer.DEPLOYMENT) {
    drawDeploymentScreen(g2);
} else {
    // ...existing overworld/battle drawing...
}
```

---

## 4. Click handling

Add to `mouseClicked`, as a new top-level branch (check this before the existing `gamePaused`/inventory handling, since deployment is its own distinct state):
```java
if (currentLayer == GameLayer.DEPLOYMENT) {
    if (formationLineButton.contains(e.getPoint())) { selectedFormation = entity.Formation.Type.LINE; return; }
    if (formationWedgeButton.contains(e.getPoint())) { selectedFormation = entity.Formation.Type.WEDGE; return; }
    if (formationCircleButton.contains(e.getPoint())) { selectedFormation = entity.Formation.Type.CIRCLE; return; }
    if (formationScatteredButton.contains(e.getPoint())) { selectedFormation = entity.Formation.Type.SCATTERED; return; }

    if (beginBattleButton.contains(e.getPoint())) {
        startBattle(pendingEnemyParty, pendingCaughtFleeing, selectedFormation);
        return;
    }
    if (retreatButton.contains(e.getPoint())) {
        attemptRetreat();
        return;
    }
    return; // swallow all other clicks while on this screen
}
```

---

## 5. Applying the chosen formation, and the retreat outcome

### `startBattle` gains a formation parameter
```java
private void startBattle(party.Party enemyParty, boolean firstStrikeBonus, entity.Formation.Type formation) {
    overworldMapBeforeBattle = currentMap;
    overworldPlayerXBeforeBattle = player.x;
    overworldPlayerYBeforeBattle = player.y;

    allySquad.formation.type = formation; // apply the player's chosen formation before troops deploy

    currentLayer = GameLayer.BATTLE;
    setupMap("battlefield.txt");

    enemies.clear();
    troops.clear();
    deployPartyAsEnemies(enemyParty);
    deployPartyAsAllies(playerParty);

    teleportPlayerForMap("battlefield.txt");
    pendingEnemyParty = null;
}
```

### `attemptRetreat` — respects the PURSUING state as a real risk, not just a UI label
```java
private void attemptRetreat() {
    boolean forcedFight = pendingEnemyParty.state == Party.State.PURSUING && Math.random() < 0.4;
    if (forcedFight) {
        // They caught you anyway — battle starts, but without the deployment screen's benefits of a chosen formation delay;
        // keep it simple and just start with whatever formation was already selected.
        startBattle(pendingEnemyParty, pendingCaughtFleeing, selectedFormation);
        return;
    }
    retreatCooldowns.put(pendingEnemyParty, 600); // ~10 seconds at 60fps before this specific party can re-trigger an encounter
    currentLayer = GameLayer.OVERWORLD;
    pendingEnemyParty = null;
}
```

---

## Why the fog-of-war approximation on enemy strength
Showing the player exact enemy troop counts and composition would make the deployment screen almost pointless as a decision point — with perfect information, "should I fight or retreat" stops being a judgment call and becomes a lookup. Rounding troop count to the nearest 3 and showing a coarse strength label (`Weak`/`Moderate`/`Strong`/`Overwhelming`) instead of exact numbers keeps some genuine uncertainty in the decision, closer to how Bannerlord's own pre-battle screen works — you get a read on the situation, not a spreadsheet.

## What's still stubbed after this phase
- No way yet to choose *which* specific troops deploy vs stay behind (e.g., leaving wounded troops out of a risky fight) — the whole party always deploys. Worth adding if you want more granular pre-battle control later.
- Formation only affects the player's allied troops' starting positions — enemy deployment is still whatever your existing `deployPartyAsEnemies` placement logic does. A symmetrical "enemy also picks a formation based on their faction/commander personality" would be a nice Phase 4-style addition tying back into the Kingdom traits system.
- The retreat cooldown is a flat 10 seconds regardless of context — could scale with distance fled or the pursuing party's speed if you want retreating to feel less like a fixed timer and more like an actual chase outcome.

## Suggested test order
1. Trigger an encounter and confirm the deployment screen shows before any battle map loads — no auto-battle should happen anymore.
2. Test each formation selection and confirm `allySquad.formation.type` actually changes what you see in `startBattle` (spot-check troop starting positions in the battle map).
3. Test retreating against a `ROAMING` or `FLEEING` enemy party — should always succeed, and re-approaching that same party within ~10 seconds shouldn't immediately re-trigger the screen.
4. Test retreating against a `PURSUING` party several times and confirm the ~40% forced-fight chance feels right — adjust the probability if it's too punishing or too easy to escape.