package my2Dgame;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener{
    
	public boolean upPressed, downPressed, leftPressed, rightPressed;
	public boolean spacePressed;
	public boolean num1Pressed, num2Pressed, num3Pressed;
	public boolean ePressed;
	public boolean shiftPressed;
	public boolean cPressed, vPressed;
	public boolean bPressed;

	@Override
	public void keyTyped(KeyEvent e) {
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
		
		int code = e.getKeyCode();
		
		if(code == KeyEvent.VK_W) {
			upPressed = true;
		}
		if(code == KeyEvent.VK_S) {
			downPressed = true;
		}
		if(code == KeyEvent.VK_A) {
			leftPressed = true;
		}
		if(code == KeyEvent.VK_D) {
			rightPressed = true;
		}
		if(code == KeyEvent.VK_SPACE) {
			spacePressed = true;
		}
		if(code == KeyEvent.VK_1) {
			num1Pressed = true;
		}
		if(code == KeyEvent.VK_2) {
			num2Pressed = true;
		}
		if(code == KeyEvent.VK_3) {
			num3Pressed = true;
		}
		if(code == KeyEvent.VK_E) {
			ePressed = true;
		}
		if(code == KeyEvent.VK_SHIFT) {
			shiftPressed = true;
		}
		if(code == KeyEvent.VK_C) {
			cPressed = true;
		}
		if(code == KeyEvent.VK_V) {
			vPressed = true;
		}
		if(code == KeyEvent.VK_B) {
			bPressed = true;
		}
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		
		int code = e.getKeyCode();
		
		if(code == KeyEvent.VK_W) {
			upPressed = false;
		}
		if(code == KeyEvent.VK_S) {
			downPressed = false;
		}
		if(code == KeyEvent.VK_A) {
			leftPressed = false;
		}
		if(code == KeyEvent.VK_D) {
			rightPressed = false;
		}
		if(code == KeyEvent.VK_SPACE) {
			spacePressed = false;
		}
		if(code == KeyEvent.VK_1) {
			num1Pressed = false;
		}
		if(code == KeyEvent.VK_2) {
			num2Pressed = false;
		}
		if(code == KeyEvent.VK_3) {
			num3Pressed = false;
		}
		if(code == KeyEvent.VK_E) {
			ePressed = false;
		}
		if(code == KeyEvent.VK_SHIFT) {
			shiftPressed = false;
		}
		if(code == KeyEvent.VK_C) {
			cPressed = false;
		}
		if(code == KeyEvent.VK_V) {
			vPressed = false;
		}
		if(code == KeyEvent.VK_B) {
			bPressed = false;
		}
		
		
	}

}

