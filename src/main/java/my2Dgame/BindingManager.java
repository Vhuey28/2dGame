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
        MINIMAP_TOGGLE, ARCHER_MODIFIER, PAUSE,
        INTERACT, SWITCH_HERO, ABILITY_1, ABILITY_2, ABILITY_3
    }

    private Map<Action, Integer> keyboardBindings = new EnumMap<>(Action.class);
    private Map<Action, Integer> controllerBindings = new EnumMap<>(Action.class);

    private static final String CONFIG_PATH = System.getProperty("user.home") + "/.chronicle_conquest/keybinds.properties";

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
        keyboardBindings.put(Action.INTERACT, java.awt.event.KeyEvent.VK_F);
        keyboardBindings.put(Action.SWITCH_HERO, java.awt.event.KeyEvent.VK_Q);
        keyboardBindings.put(Action.ABILITY_1, java.awt.event.KeyEvent.VK_4);
        keyboardBindings.put(Action.ABILITY_2, java.awt.event.KeyEvent.VK_5);
        keyboardBindings.put(Action.ABILITY_3, java.awt.event.KeyEvent.VK_6);

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
        controllerBindings.put(Action.INTERACT, 1);
        controllerBindings.put(Action.SWITCH_HERO, 2);
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
        File dir = new File(CONFIG_PATH).getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
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