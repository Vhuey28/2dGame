package entity;

import java.awt.image.BufferedImage;
import my2Dgame.GamePanel;

public class Entity {

	GamePanel gp;

	public float x, y;
	public float speed;

	public BufferedImage up1,up2,down1,down2,left1,left2,right1,right2;
	// Diagonal sprites (optional - if not provided, will use nearest cardinal)
	public BufferedImage upLeft1, upLeft2, upRight1, upRight2, downLeft1, downLeft2, downRight1, downRight2;
	public String direction;

	public int spriteCounter = 0;
	public int spriteNum = 1;

	// For smooth movement - track velocity
	public float velocityX = 0;
	public float velocityY = 0;

}