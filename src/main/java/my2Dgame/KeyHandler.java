package my2Dgame;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener {

    private BindingManager bindings;

    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean spacePressed;
    public boolean num1Pressed, num2Pressed, num3Pressed;
    public boolean num4Pressed, num5Pressed, num6Pressed; // for hero abilities
    public boolean ePressed;
    public boolean shiftPressed;
    public boolean cPressed, vPressed;
    public boolean bPressed;
    public boolean mPressed;
    public boolean xPressed;
    public boolean fPressed; // interact with hero
    public boolean qPressed; // switch active hero
    public boolean fPressedLastFrame = false;
    public boolean qPressedLastFrame = false;

    public KeyHandler(BindingManager bindings) {
        this.bindings = bindings;
    }

    @Override
    public void keyTyped(KeyEvent e) {

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
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.INTERACT)) fPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.SWITCH_HERO)) qPressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.ABILITY_1)) num4Pressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.ABILITY_2)) num5Pressed = state;
        else if (keyCode == bindings.getKeyBinding(BindingManager.Action.ABILITY_3)) num6Pressed = state;
    }
}