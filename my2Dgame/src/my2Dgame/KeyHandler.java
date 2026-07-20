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
	public boolean mPressed;

	@Override
	public void keyTyped(KeyEvent e) {
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
		
		int code = e.getKeyCode();
		
		if(code == KeyEvent.VK_W || code == KeyEvent.VK_UP) {
			upPressed = true;
		}
		if(code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) {
			downPressed = true;
		}
		if(code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) {
			leftPressed = true;
		}
		if(code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) {
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
		if(code == KeyEvent.VK_M) {
			mPressed = true;
		}

	}

	@Override
	public void keyReleased(KeyEvent e) {
		
		int code = e.getKeyCode();
		
		if(code == KeyEvent.VK_W || code == KeyEvent.VK_UP) {
			upPressed = false;
		}
		if(code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) {
			downPressed = false;
		}
		if(code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) {
			leftPressed = false;
		}
		if(code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) {
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
		if(code == KeyEvent.VK_M) {
			mPressed = false;
		}


	}

}

