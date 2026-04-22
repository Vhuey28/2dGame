package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import my2Dgame.GamePanel;
import my2Dgame.KeyHandler;

public class Player extends Entity{
	
	GamePanel gp;
	KeyHandler keyH;
	public List<String> inventory = new ArrayList<>();

	public int maxHealth = 100;
	public int health = maxHealth;
	public int maxStamina = 100;
	public int stamina = maxStamina;
	public int maxMana = 100;
	public int mana = maxMana;
	
	public Player(GamePanel gp, KeyHandler keyH) {
		
		this.gp = gp;
		this.keyH = keyH;
		
		setDefaultValues();
		getPlayerImage();
	}
	
	public void setDefaultValues() {
		
		x = 100;
		y = 100;
		speed = 5;
		direction = "up";
	}

	public void addInventoryItem(String item) {
		if (!inventory.contains(item)) {
			inventory.add(item);
		}
	}
	
	public void getPlayerImage() {
		
		try {
			
			up2 = ImageIO.read(getClass().getResourceAsStream("/player/up2.png"));
			up1 = ImageIO.read(getClass().getResourceAsStream("/player/upDude.png"));
			down1 = ImageIO.read(getClass().getResourceAsStream("/player/down1.png"));
			down2 = ImageIO.read(getClass().getResourceAsStream("/player/down2.png"));
			right1 = ImageIO.read(getClass().getResourceAsStream("/player/right1.png"));
			right2 = ImageIO.read(getClass().getResourceAsStream("/player/right2.png"));
			left1 = ImageIO.read(getClass().getResourceAsStream("/player/left1.png"));
			left2 = ImageIO.read(getClass().getResourceAsStream("/player/left2.png"));


		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void update() {
		int newX = x;
		int newY = y;
		boolean moved = false;

		if(keyH.upPressed == true || keyH.downPressed == true ||
				keyH.leftPressed == true || keyH.rightPressed) {
			if(keyH.upPressed == true) {
				direction = "up";
				newY -= speed;
			} else if(keyH.downPressed == true) {
				direction = "down";
				newY += speed;
			} else if(keyH.leftPressed == true) {
				direction = "left";
				newX -= speed;
			} else if(keyH.rightPressed == true) {
				direction = "right";
				newX += speed;
			}

			if (canMoveTo(newX, newY)) {
				x = newX;
				y = newY;
				moved = true;
			}

			if (moved) {
				spriteCounter++;
				if(spriteCounter > 20) {
					if(spriteNum == 1) {
						spriteNum = 2;
					} else if(spriteNum == 2) {
						spriteNum = 1;
					}
					spriteCounter = 0;
				}
			}
		}
	}

	private boolean canMoveTo(int nextX, int nextY) {
		int left = nextX;
		int right = nextX + gp.tileSize - 1;
		int top = nextY;
		int bottom = nextY + gp.tileSize - 1;

		return !gp.isTileBlocked(left, top)
			&& !gp.isTileBlocked(right, top)
			&& !gp.isTileBlocked(left, bottom)
			&& !gp.isTileBlocked(right, bottom);
	}

	public void draw(Graphics2D g2, int cameraX, int cameraY) {
		//g2.setColor(Color.white);
		//g2.fillRect(x - cameraX, y - cameraY, gp.tileSize, gp.tileSize);
		
		BufferedImage image = null;
		
		switch(direction) {
		
		case("up"):
			if(spriteNum == 1) 
			image = up1;
		if(spriteNum == 2)
			image = up2;
		break;
		
		case("down"):
			if(spriteNum == 1) 
				image = down1;
			if(spriteNum == 2)
				image = down2;
		break;
		
		case("left"):
			if(spriteNum == 1) 
				image = left1;
			if(spriteNum == 2)
				image = left2;
		break;
		
		case("right"):
			if(spriteNum == 1) 
				image = right1;
			if(spriteNum == 2)
				image = right2;
		break;
		
		}
		
		g2.drawImage(image, x - cameraX, y - cameraY, gp.tileSize, gp.tileSize, null);
		
	}

}
