package my2Dgame;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;

public class ControllerHandler {

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

    public String getButtonLabel(int buttonIndex) {
        String b="";
        if (brand == ControllerBrand.PLAYSTATION) {
            switch (buttonIndex) {
                case 0 :
                    b= "Cross";
                    break; 
                case 1:
                    b= "Circle";
                    break;
                case 2: 
                    b="Square";
                    break;
                case 3:
                    b="Triangle";
                    break;
                case 4:
                    b= "L1";
                    break;
                case 5:
                    b= "R1";
                    break; 
                case 6:
                    b= "Share";
                    break;
                case 7:
                    b= "Options";
                    break;
                default:
                    b= "Button " + buttonIndex;
            };
        } else if (brand == ControllerBrand.XBOX) {
            switch (buttonIndex) {
                case 0:
                    b= "A";
                    break; 
                case 1:
                    b= "B"; 
                    break;
                case 2:
                    b= "X";
                    break; 
                case 3:
                     b= "Y";
                    break;
                case 4:
                    b= "LB";
                    break;
                case 5:
                    b= "RB";
                    break;
                case 6:
                    b= "Back";
                    break;
                case 7:
                    b= "Start";
                default:
                    b= "Button " + buttonIndex;
            };
        }
        return b;
    }

    private Controller gamepad;
    private BindingManager bindings;

    // Mirrors KeyHandler's fields exactly, so GamePanel can OR these into keyH
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean spacePressed;      // melee attack
    public boolean num1Pressed, num2Pressed, num3Pressed; // spells
    public boolean ePressed;          // dodge
    public boolean bPressed;          // buy troop / spawn wave
    public boolean cPressed, vPressed; // troop commands
    public boolean xPressed;          // roam toggle
    public boolean mPressed;          // minimap toggle
    public boolean shiftPressed;      // archer-troop modifier

    // For controller rebind capture
    private Integer lastButtonPressedForRebind = null;

    // Controller reconnect logic
    private int reconnectScanTimer = 0;
    private static final int RECONNECT_SCAN_INTERVAL = 120; // ~2 seconds at 60fps

    private static final float STICK_DEADZONE = 0.3f; // ignore small stick drift near center

    public ControllerHandler(BindingManager bindings) {
        this.bindings = bindings;
        findGamepad();
    }

    private void findGamepad() {
        Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
        for (Controller c : controllers) {
            if (c.getType() == Controller.Type.GAMEPAD || c.getType() == Controller.Type.STICK) {
                gamepad = c;
                brand = detectBrand(c.getName());
                System.out.println("Controller found: " + c.getName() + " (detected as: " + brand + ")");
                return;
            }
        }
        System.out.println("No controller detected — controller support will be inactive.");
    }

    public boolean isConnected() {
        return gamepad != null;
    }

    public Integer consumeLastButtonPressedForRebind() {
        Integer result = lastButtonPressedForRebind;
        lastButtonPressedForRebind = null;
        return result;
    }

    /** Call once per game tick, before reading any of the boolean fields above. */
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


        // Reset all flags each tick — poll() reports current state, not edge transitions
        upPressed = downPressed = leftPressed = rightPressed = false;
        spacePressed = num1Pressed = num2Pressed = num3Pressed = false;
        ePressed = bPressed = cPressed = vPressed = xPressed = mPressed = shiftPressed = false;

        for (Component comp : gamepad.getComponents()) {
            Component.Identifier id = comp.getIdentifier();
            float value = comp.getPollData();
            // Left analog stick -> movement (digital thresholding to match your existing 4-direction Player.update())
            if (id == Component.Identifier.Axis.X) {
                if (value < -STICK_DEADZONE) leftPressed = true;
                if (value > STICK_DEADZONE) rightPressed = true;
            } else if (id == Component.Identifier.Axis.Y) {
                if (value < -STICK_DEADZONE) upPressed = true;
                if (value > STICK_DEADZONE) downPressed = true;
            }
            // D-pad (reported as a POV hat on most gamepads)
            else if (id == Component.Identifier.Axis.POV) {
                if (value == Component.POV.UP || value == Component.POV.UP_LEFT || value == Component.POV.UP_RIGHT) upPressed = true;
                if (value == Component.POV.DOWN || value == Component.POV.DOWN_LEFT || value == Component.POV.DOWN_RIGHT) downPressed = true;
                if (value == Component.POV.LEFT || value == Component.POV.UP_LEFT || value == Component.POV.DOWN_LEFT) leftPressed = true;
                if (value == Component.POV.RIGHT || value == Component.POV.UP_RIGHT || value == Component.POV.DOWN_RIGHT) rightPressed = true;
            }
            // Face buttons — use binding manager instead of hardcoded indices
            else if (id instanceof Component.Identifier.Button) {
                int buttonIndex = getButtonIndex(id);
                if (buttonIndex >= 0) {
                    boolean pressed = value > 0.5f;
                    if (pressed) {
                        lastButtonPressedForRebind = buttonIndex; // record for rebind capture
                    }
                    BindingManager.Action action = bindings.getActionForButton(buttonIndex);
                    if (action != null) {
                        applyControllerAction(action, pressed);
                    }
                }
            }
        }
    }

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
            case MELEE :
                 spacePressed = pressed;
                 break;
            case SPELL_1:
                 num1Pressed = pressed;
                 break;
            case SPELL_2:
                 num2Pressed = pressed;
                 break;
            case SPELL_3:
                 num3Pressed = pressed;
                 break;
            case DODGE:
                 ePressed = pressed;
                 break;
            case ARCHER_MODIFIER:
                 shiftPressed = pressed;
                 break;
            case BUY_TROOP:
                 bPressed = pressed;
                 break;
            case MINIMAP_TOGGLE:
                 mPressed = pressed;
                 break;
            case TROOP_CHARGE:
                 cPressed = pressed;
                 break;
            case TROOP_DEFEND:
                 vPressed = pressed;
                 break;
            case TROOP_ROAM_TOGGLE:
                 xPressed = pressed;
                 break;
            case INTERACT: 
                { }; // Not mapped to a boolean field in keyH yet, handled elsewhere if needed
                break;
            case SWITCH_HERO:
                 { };
                 break;
            // Movement actions are handled by the stick/POV code separately, not button lookup
            default: {};
        }
    }
}