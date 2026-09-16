# Controller Brand Detection + Rebindable Key/Controller Settings

## Scope and a heads-up before starting
This is a genuinely large feature — it touches `ControllerHandler`, needs changes to `KeyHandler` (which hasn't been shared in this conversation, so the `KeyHandler` section below is a **generalized rewrite** based on how it's used elsewhere in your code — you'll need to reconcile it with whatever your actual `KeyHandler.java` looks like today), and adds a whole new UI screen plus persistence to disk. Build and test each section in order rather than all at once.

---

## 1. Controller brand detection

JInput's `Controller.getName()` returns a human-readable string from the OS — for a PlayStation controller this is typically something like `"Wireless Controller"` (DualShock 4) or `"DualSense Wireless Controller"` (PS5); for Xbox it's usually `"Xbox 360 Controller"`, `"Xbox One Controller"`, or `"Xbox Series X Controller"` depending on driver. Detection is just keyword matching on that string.

Add to `ControllerHandler.java`:
```java
public enum ControllerBrand { XBOX, PLAYSTATION, GENERIC }

private ControllerBrand brand = ControllerBrand.GENERIC;

public ControllerBrand getBrand() {
    return brand;
}

private ControllerBrand detectBrand(String controllerName) {
    if (controllerName == null) return ControllerBrand.GENERIC;
    String name = controllerName.toLowerCase();
    if (name.contains("xbox")) return ControllerBrand.XBOX;
    if (name.contains("playstation") || name.contains("dualshock") || name.contains("dualsense")
        || name.contains("wireless controller") || name.contains("sony")) {
        return ControllerBrand.PLAYSTATION;
    }
    return ControllerBrand.GENERIC;
}
```
Call it inside your existing `findGamepad()`, right where you already log the controller name:
```java
private void findGamepad() {
    Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
    for (Controller c : controllers) {
        if (c.getType() == Controller.Type.GAMEPAD || c.getType() == Controller.Type.STICK) {
            gamepad = c;
            brand = detectBrand(c.getName());   // <-- add this
            System.out.println("Controller found: " + c.getName() + " (detected as: " + brand + ")");
            return;
        }
    }
    System.out.println("No controller detected — controller support will be inactive.");
}
```

**Important nuance:** button *indices* (`Button._0`, `_1`, etc.) are almost always **positionally consistent** regardless of brand — index 0 is whatever the OS/driver maps to the "first" face button, regardless of whether it's labeled A (Xbox) or Cross (PlayStation). Brand detection is mainly useful for **labeling in the UI** ("Cross" vs "A" vs "Button 1" in your rebind screen), not for actually changing which physical button does what — that's what the rebinding system in section 3 handles. Don't assume brand detection alone fixes button-mapping differences; some third-party/generic controllers absolutely do report different indices, which is exactly why the rebind screen exists as a safety net.

Add a label helper you'll use in the settings UI:
```java
public String getButtonLabel(int buttonIndex) {
    if (brand == ControllerBrand.PLAYSTATION) {
        return switch (buttonIndex) {
            case 0 -> "Cross"; case 1 -> "Circle"; case 2 -> "Square"; case 3 -> "Triangle";
            case 4 -> "L1"; case 5 -> "R1"; case 6 -> "Share"; case 7 -> "Options";
            default -> "Button " + buttonIndex;
        };
    } else if (brand == ControllerBrand.XBOX) {
        return switch (buttonIndex) {
            case 0 -> "A"; case 1 -> "B"; case 2 -> "X"; case 3 -> "Y";
            case 4 -> "LB"; case 5 -> "RB"; case 6 -> "Back"; case 7 -> "Start";
            default -> "Button " + buttonIndex;
        };
    }
    return "Button " + buttonIndex;
}
```

---

## 2. A central binding manager (replaces hardcoded key/button mappings)

Right now your input handling has hardcoded mappings baked directly into `KeyHandler` (keyboard) and `ControllerHandler` (buttons). Rebinding means those mappings need to become **data** — a lookup you can change at runtime and save to disk — rather than fixed `if` statements.

Create `my2Dgame/BindingManager.java`:
```java
package my2Dgame;

import java.io.*;
import java.util.*;

/**
 * Central store for keyboard-key-code and controller-button-index bindings,
 * keyed by action name (e.g. "MOVE_UP", "MELEE", "SPELL_1"). Both KeyHandler
 * and ControllerHandler read from this instead of hardcoding key/button codes.
 */
public class BindingManager {

    public enum Action {
        MOVE_UP, MOVE_DOWN, MOVE_LEFT, MOVE_RIGHT,
        MELEE, SPELL_1, SPELL_2, SPELL_3, DODGE,
        BUY_TROOP, TROOP_CHARGE, TROOP_DEFEND, TROOP_ROAM_TOGGLE,
        MINIMAP_TOGGLE, ARCHER_MODIFIER, PAUSE
    }

    private Map<Action, Integer> keyboardBindings = new EnumMap<>(Action.class);
    private Map<Action, Integer> controllerBindings = new EnumMap<>(Action.class);

    private static final String CONFIG_PATH = "keybinds.properties";

    public BindingManager() {
        setDefaults();
        load(); // overrides defaults with saved bindings, if a config file exists
    }

    private void setDefaults() {
        // Keyboard defaults — java.awt.event.KeyEvent.VK_* constants
        keyboardBindings.put(Action.MOVE_UP, java.awt.event.KeyEvent.VK_W);
        keyboardBindings.put(Action.MOVE_DOWN, java.awt.event.KeyEvent.VK_S);
        keyboardBindings.put(Action.MOVE_LEFT, java.awt.event.KeyEvent.VK_A);
        keyboardBindings.put(Action.MOVE_RIGHT, java.awt.event.KeyEvent.VK_D);
        keyboardBindings.put(Action.MELEE, java.awt.event.KeyEvent.VK_SPACE);
        keyboardBindings.put(Action.SPELL_1, java.awt.event.KeyEvent.VK_1);
        keyboardBindings.put(Action.SPELL_2, java.awt.event.KeyEvent.VK_2);
        keyboardBindings.put(Action.SPELL_3, java.awt.event.KeyEvent.VK_3);
        keyboardBindings.put(Action.DODGE, java.awt.event.KeyEvent.VK_E);
        keyboardBindings.put(Action.BUY_TROOP, java.awt.event.KeyEvent.VK_B);
        keyboardBindings.put(Action.TROOP_CHARGE, java.awt.event.KeyEvent.VK_C);
        keyboardBindings.put(Action.TROOP_DEFEND, java.awt.event.KeyEvent.VK_V);
        keyboardBindings.put(Action.TROOP_ROAM_TOGGLE, java.awt.event.KeyEvent.VK_X);
        keyboardBindings.put(Action.MINIMAP_TOGGLE, java.awt.event.KeyEvent.VK_M);
        keyboardBindings.put(Action.ARCHER_MODIFIER, java.awt.event.KeyEvent.VK_SHIFT);
        keyboardBindings.put(Action.PAUSE, java.awt.event.KeyEvent.VK_P);

        // Controller defaults — button indices (see the calibration note from the earlier controller guide)
        controllerBindings.put(Action.MELEE, 0);
        controllerBindings.put(Action.SPELL_1, 1);
        controllerBindings.put(Action.SPELL_2, 2);
        controllerBindings.put(Action.SPELL_3, 3);
        controllerBindings.put(Action.DODGE, 4);
        controllerBindings.put(Action.ARCHER_MODIFIER, 5);
        controllerBindings.put(Action.BUY_TROOP, 6);
        controllerBindings.put(Action.MINIMAP_TOGGLE, 7);
        // MOVE_* and PAUSE aren't in controllerBindings since movement comes from
        // the analog stick/D-pad directly, and PAUSE is keyboard-only by default —
        // add a controller binding for it here if you want a Start-button pause too.
    }

    public int getKeyBinding(Action action) {
        return keyboardBindings.getOrDefault(action, -1);
    }

    public int getControllerBinding(Action action) {
        return controllerBindings.getOrDefault(action, -1);
    }

    public void setKeyBinding(Action action, int keyCode) {
        keyboardBindings.put(action, keyCode);
        save();
    }

    public void setControllerBinding(Action action, int buttonIndex) {
        controllerBindings.put(action, buttonIndex);
        save();
    }

    /** Returns the action currently bound to this key code, or null if unbound — used to detect/prevent conflicts. */
    public Action getActionForKey(int keyCode) {
        for (Map.Entry<Action, Integer> e : keyboardBindings.entrySet()) {
            if (e.getValue() == keyCode) return e.getKey();
        }
        return null;
    }

    public Action getActionForButton(int buttonIndex) {
        for (Map.Entry<Action, Integer> e : controllerBindings.entrySet()) {
            if (e.getValue() == buttonIndex) return e.getKey();
        }
        return null;
    }

    private void save() {
        Properties props = new Properties();
        for (Map.Entry<Action, Integer> e : keyboardBindings.entrySet()) {
            props.setProperty("kb." + e.getKey().name(), String.valueOf(e.getValue()));
        }
        for (Map.Entry<Action, Integer> e : controllerBindings.entrySet()) {
            props.setProperty("ctrl." + e.getKey().name(), String.valueOf(e.getValue()));
        }
        try (FileOutputStream out = new FileOutputStream(CONFIG_PATH)) {
            props.store(out, "Chronicle Conquest key/controller bindings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load() {
        File f = new File(CONFIG_PATH);
        if (!f.exists()) return; // no saved config yet — defaults stand
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
            for (Action a : Action.values()) {
                String kbVal = props.getProperty("kb." + a.name());
                if (kbVal != null) keyboardBindings.put(a, Integer.parseInt(kbVal));
                String ctrlVal = props.getProperty("ctrl." + a.name());
                if (ctrlVal != null) controllerBindings.put(a, Integer.parseInt(ctrlVal));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

`keybinds.properties` will be written to whatever your game's current working directory is at runtime — given your `run.sh` runs from `/tmp/my2Dgame_work`, that's a temp directory that gets wiped on every run (`rm -rf "$WORK_DIR"`). **You'll want the config file written somewhere persistent instead** — e.g. `System.getProperty("user.home") + "/.chronicle_conquest/keybinds.properties"` — otherwise every rebind gets lost the next time you run the script. Swap `CONFIG_PATH` accordingly once you confirm where you want it to live.

---

## 3. Wiring the binding manager into GamePanel, KeyHandler, and ControllerHandler

### GamePanel — one shared instance
```java
public BindingManager bindings = new BindingManager();
```
Pass it to both handlers at construction (adjust based on your actual constructors):
```java
KeyHandler keyH = new KeyHandler(bindings);
ControllerHandler controllerH = new ControllerHandler(bindings);
```

### KeyHandler — read from bindings instead of hardcoded VK_ constants
Since your actual `KeyHandler.java` hasn't been shared in this conversation, here's the general shape it needs — reconcile the action names and fields with whatever your real file currently has:
```java
package my2Dgame;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class KeyHandler extends KeyAdapter {
    private BindingManager bindings;

    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean spacePressed, num1Pressed, num2Pressed, num3Pressed;
    public boolean ePressed, bPressed, cPressed, vPressed, xPressed, mPressed, shiftPressed;
    // ...plus any other boolean fields your existing KeyHandler already has (fPressed, qPressed, etc.)

    public KeyHandler(BindingManager bindings) {
        this.bindings = bindings;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        setActionState(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        setActionState(e.getKeyCode(), false);
    }

    private void setActionState(int keyCode, boolean state) {
        if (keyCode == bindings.getKeyBinding(BindingManager.Action.MOVE_UP)) upPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.MOVE_DOWN)) downPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.MOVE_LEFT)) leftPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.MOVE_RIGHT)) rightPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.MELEE)) spacePressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.SPELL_1)) num1Pressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.SPELL_2)) num2Pressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.SPELL_3)) num3Pressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.DODGE)) ePressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.BUY_TROOP)) bPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.TROOP_CHARGE)) cPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.TROOP_DEFEND)) vPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.TROOP_ROAM_TOGGLE)) xPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.MINIMAP_TOGGLE)) mPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.ARCHER_MODIFIER)) shiftPressed = state;
        // add any other actions your real KeyHandler tracks
    }
}
```
**This is a placeholder shape, not a guaranteed drop-in.** If your real `KeyHandler` has additional fields (`fPressed`, `qPressed`, `num4Pressed`, etc. — all referenced in your `GamePanel` code from earlier) or a different structure (e.g., a `switch` statement instead of hardcoded booleans), send me that file and I'll give you an exact merge instead of this generalized version.

### ControllerHandler — same idea, replace hardcoded button indices
In your existing `poll()` method, replace the hardcoded `Button._0`/`_1`/etc. checks:
```java
else if (id == Component.Identifier.Button._0) { spacePressed = value > 0.5f; }
else if (id == Component.Identifier.Button._1) { num1Pressed = value > 0.5f; }
```
with a lookup against the binding manager instead:
```java
else if (id instanceof Component.Identifier.Button) {
    int buttonIndex = getButtonIndex(id); // helper below
    boolean pressed = value > 0.5f;
    BindingManager.Action action = bindings.getActionForButton(buttonIndex);
    if (action != null) applyControllerAction(action, pressed);
}
```
```java
private int getButtonIndex(Component.Identifier id) {
    // Button._0 through _31 map directly to 0-31 by naming convention
    String name = id.getName(); // e.g. "0", "1", ...
    try {
        return Integer.parseInt(name);
    } catch (NumberFormatException e) {
        return -1;
    }
}

private void applyControllerAction(BindingManager.Action action, boolean pressed) {
    switch (action) {
        case MELEE -> spacePressed = pressed;
        case SPELL_1 -> num1Pressed = pressed;
        case SPELL_2 -> num2Pressed = pressed;
        case SPELL_3 -> num3Pressed = pressed;
        case DODGE -> ePressed = pressed;
        case ARCHER_MODIFIER -> shiftPressed = pressed;
        case BUY_TROOP -> bPressed = pressed;
        case MINIMAP_TOGGLE -> mPressed = pressed;
        default -> {} // movement actions are handled by the stick/POV code separately, not button lookup
    }
}
```
Constructor needs the `bindings` field added:
```java
private BindingManager bindings;

public ControllerHandler(BindingManager bindings) {
    this.bindings = bindings;
    findGamepad();
}
```

---

## 4. Settings screen in the pause menu

### 4a. New state
```java
public boolean settingsMenuOpen = false;
private BindingManager.Action awaitingRebindAction = null; // non-null while waiting for the next key/button press
private boolean awaitingRebindIsController = false;
```

### 4b. Add a "Settings" button to the existing pause menu
In `drawPauseMenu()`, add a button alongside the existing restart button:
```java
private Rectangle settingsButtonRect = new Rectangle(screenWidth/2 - 80, screenHeight/2 + 40, 160, 36);
```
```java
// inside drawPauseMenu(), after the restart button drawing code
g2.setColor(new Color(64, 64, 64, 220));
g2.fillRoundRect(settingsButtonRect.x, settingsButtonRect.y, settingsButtonRect.width, settingsButtonRect.height, 10, 10);
g2.setColor(Color.white);
g2.drawRoundRect(settingsButtonRect.x, settingsButtonRect.y, settingsButtonRect.width, settingsButtonRect.height, 10, 10);
g2.drawString("Settings", settingsButtonRect.x + 45, settingsButtonRect.y + 24);
```

### 4c. The settings screen itself
```java
private void drawSettingsMenu(Graphics2D g2) {
    g2.setColor(new Color(0, 0, 0, 220));
    g2.fillRect(0, 0, screenWidth, screenHeight);
    g2.setColor(Color.white);
    g2.setFont(new Font("Arial", Font.BOLD, 28));
    g2.drawString("Key & Controller Bindings", screenWidth/2 - 160, 60);

    g2.setFont(new Font("Arial", Font.PLAIN, 16));
    int y = 110;
    int rowHeight = 32;
    BindingManager.Action[] actions = BindingManager.Action.values();
    settingsRowRects = new Rectangle[actions.length][2]; // [action index][0 = keyboard col, 1 = controller col]

    for (int i = 0; i < actions.length; i++) {
        BindingManager.Action action = actions[i];
        g2.setColor(Color.white);
        g2.drawString(action.name(), 60, y + 20);

        // Keyboard binding button
        Rectangle kbRect = new Rectangle(320, y, 140, 26);
        settingsRowRects[i][0] = kbRect;
        boolean awaitingThisKb = awaitingRebindAction == action && !awaitingRebindIsController;
        g2.setColor(awaitingThisKb ? new Color(200, 150, 0) : new Color(50, 50, 50));
        g2.fillRect(kbRect.x, kbRect.y, kbRect.width, kbRect.height);
        g2.setColor(Color.white);
        String kbLabel = awaitingThisKb ? "Press a key..." : KeyEvent.getKeyText(bindings.getKeyBinding(action));
        g2.drawString(kbLabel, kbRect.x + 8, kbRect.y + 18);

        // Controller binding button
        Rectangle ctrlRect = new Rectangle(480, y, 160, 26);
        settingsRowRects[i][1] = ctrlRect;
        boolean awaitingThisCtrl = awaitingRebindAction == action && awaitingRebindIsController;
        g2.setColor(awaitingThisCtrl ? new Color(200, 150, 0) : new Color(50, 50, 50));
        g2.fillRect(ctrlRect.x, ctrlRect.y, ctrlRect.width, ctrlRect.height);
        g2.setColor(Color.white);
        int ctrlBinding = bindings.getControllerBinding(action);
        String ctrlLabel = awaitingThisCtrl ? "Press a button..."
            : (ctrlBinding >= 0 ? controllerH.getButtonLabel(ctrlBinding) : "—");
        g2.drawString(ctrlLabel, ctrlRect.x + 8, ctrlRect.y + 18);

        y += rowHeight;
    }

    g2.setColor(Color.yellow);
    g2.drawString("Click a binding, then press the new key/button. ESC to go back.", 60, y + 30);
}
```
Add the tracking field near your other menu-rect fields:
```java
private Rectangle[][] settingsRowRects;
```

### 4d. Click handling — enter "awaiting rebind" mode
In your existing `mouseClicked` handler, add a branch (before the general pause-menu handling):
```java
if (gamePaused && settingsMenuOpen) {
    BindingManager.Action[] actions = BindingManager.Action.values();
    for (int i = 0; i < actions.length; i++) {
        if (settingsRowRects[i][0].contains(e.getPoint())) {
            awaitingRebindAction = actions[i];
            awaitingRebindIsController = false;
            return;
        }
        if (settingsRowRects[i][1].contains(e.getPoint())) {
            awaitingRebindAction = actions[i];
            awaitingRebindIsController = true;
            return;
        }
    }
    return; // swallow other clicks while the settings screen is open
}
```
And in the existing pause-menu click handling, add the Settings button:
```java
if (gamePaused && !settingsMenuOpen && settingsButtonRect.contains(e.getPoint())) {
    settingsMenuOpen = true;
    return;
}
```

### 4e. Capturing the actual rebind — keyboard side
Add a new `KeyAdapter` (or extend an existing one) in the `GamePanel` constructor:
```java
this.addKeyListener(new KeyAdapter() {
    @Override
    public void keyPressed(KeyEvent e) {
        if (awaitingRebindAction != null && !awaitingRebindIsController) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                awaitingRebindAction = null; // cancel without changing anything
                return;
            }
            bindings.setKeyBinding(awaitingRebindAction, e.getKeyCode());
            awaitingRebindAction = null;
        } else if (settingsMenuOpen && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            settingsMenuOpen = false;
        }
    }
});
```

### 4f. Capturing the actual rebind — controller side
This needs a small addition to `ControllerHandler.poll()`: when `awaitingRebindAction != null && awaitingRebindIsController`, instead of applying button state to gameplay flags, capture the **first pressed button** and report it back. Add a callback-style field:
```java
// In ControllerHandler
private Integer lastButtonPressedForRebind = null;

public Integer consumeLastButtonPressedForRebind() {
    Integer result = lastButtonPressedForRebind;
    lastButtonPressedForRebind = null;
    return result;
}
```
Inside `poll()`'s button-handling loop, before the normal action-dispatch logic:
```java
if (id instanceof Component.Identifier.Button) {
    int buttonIndex = getButtonIndex(id);
    if (value > 0.5f) {
        lastButtonPressedForRebind = buttonIndex; // always record, harmless if nobody's listening
    }
    ...
```
Then in `GamePanel.update()`, right after `controllerH.poll()`:
```java
if (awaitingRebindAction != null && awaitingRebindIsController) {
    Integer pressed = controllerH.consumeLastButtonPressedForRebind();
    if (pressed != null) {
        bindings.setControllerBinding(awaitingRebindAction, pressed);
        awaitingRebindAction = null;
    }
    return; // skip normal gameplay update while capturing a rebind
}
```

### 4g. Draw dispatch
In `paintComponent()`, where you currently do:
```java
if (gamePaused) {
    if (survivalPowerUpMenuOpen) {
        drawPowerUpMenu(g2);
    } else {
        drawPauseMenu(g2);
    }
}
```
add the settings branch:
```java
if (gamePaused) {
    if (survivalPowerUpMenuOpen) {
        drawPowerUpMenu(g2);
    } else if (settingsMenuOpen) {
        drawSettingsMenu(g2);
    } else {
        drawPauseMenu(g2);
    }
}
```

---

## Suggested build/test order
1. Add `BindingManager` and confirm the game still runs with default bindings unchanged (nothing should feel different yet).
2. Wire brand detection into `ControllerHandler` and confirm the console prints the detected brand correctly for your PS controller.
3. Convert `KeyHandler`/`ControllerHandler` to read from `BindingManager` instead of hardcoded codes — test that WASD/buttons still work exactly as before with default bindings.
4. Add the settings screen, test rebinding one keyboard action first, confirm it persists (check `keybinds.properties` gets written and reread on next launch).
5. Test controller rebinding the same way once keyboard rebinding is confirmed working.

Send me your actual `KeyHandler.java` if you want section 3's keyboard integration nailed down exactly rather than reconciled by hand — that's the piece I had to generalize without seeing the real file.