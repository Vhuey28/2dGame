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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;

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
	public int staminaRegenTimer = 0;
	public int manaRegenTimer = 0;
	public int attackCooldown = 0;

	public java.util.List<Projectile> projectiles = new ArrayList<>();
	public java.util.List<AreaEffect> areas = new ArrayList<>();
	
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
		if ("Gold Coin".equals(item)) {
			if (!inventory.contains(item)) {
				inventory.add(item);
			}
			return;
		}
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

		if(keyH.upPressed == true || keyH.downPressed == true || keyH.leftPressed == true || keyH.rightPressed) {
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

		// regenerate stamina slowly
		staminaRegenTimer++;
		if (staminaRegenTimer > 10) {
			stamina = Math.min(maxStamina, stamina + 3);
			staminaRegenTimer = 0;
		}
		// regenerate mana slowly
		manaRegenTimer++;
		if (manaRegenTimer > 30) {
			mana = Math.min(maxMana, mana + 10);
			manaRegenTimer = 0;
		}

		// handle attacks
		if (attackCooldown > 0) {
			attackCooldown--;
		}
		if (keyH.spacePressed && attackCooldown == 0 && stamina >= 10) {
			stamina -= 10;
			attackCooldown = 16;
			performMeleeAttack();
		}
		if (keyH.num1Pressed && mana >= 15) {
			mana -= 15;
			// spawn red triangle projectile
			int dirX = 0, dirY = 0;
			switch(direction) {
				case "up": dirY = -1; break;
				case "down": dirY = 1; break;
				case "left": dirX = -1; break;
				case "right": dirX = 1; break;
			}
			if (dirX != 0 || dirY != 0) {
				projectiles.add(new Projectile(x + gp.tileSize/2, y + gp.tileSize/2, dirX, dirY, 6, 8, java.awt.Color.RED));
			}
		}
		if (keyH.num2Pressed && mana >= 25) {
			mana -= 25;
			areas.add(new AreaEffect(x, y, gp.tileSize*3, 120, AreaEffect.Type.STUN_AND_DAMAGE, true, this));
		}
		if (keyH.num3Pressed && mana >= 20) {
			mana -= 20;
			areas.add(new AreaEffect(x, y, gp.tileSize*3, 180, AreaEffect.Type.HEAL));
		}

		// dodge
		if (keyH.ePressed && stamina >= 20) {
			stamina -= 3;
			// quick move in facing direction
			int dx = 0, dy = 0;
			switch(direction) {
				case "up": dy = -gp.tileSize/2; break;
				case "down": dy = gp.tileSize/2; break;
				case "left": dx = -gp.tileSize/2; break;
				case "right": dx = gp.tileSize/2; break;
			}
			int nextX = x + dx;
			int nextY = y + dy;
			if (canMoveTo(nextX, nextY)) {
				x = nextX; y = nextY;
			}
		}

		// update projectiles
		Iterator<Projectile> it = projectiles.iterator();
		while (it.hasNext()) {
			Projectile p = it.next();
			p.update();
			if (p.life <= 0) it.remove();
		}

		// update areas
		Iterator<AreaEffect> ait = areas.iterator();
		while (ait.hasNext()) {
			AreaEffect a = ait.next();
			a.update(this);

			if (a.type == AreaEffect.Type.HEAL) {
				// Heal player if within radius
				int adx = (x + gp.tileSize/2) - a.x;
				int ady = (y + gp.tileSize/2) - a.y;
				double dist = Math.sqrt(adx*adx + ady*ady);
				if (dist < a.radius) {
					if (a.duration % 6 == 0) {
						health = Math.min(maxHealth, health + 1);
					}
				}

				// Heal ally troops if within radius
				for (int i = 0; i < gp.troops.size(); i++) {
					entity.Troop troop = gp.troops.get(i);
					if (troop != null && troop.health > 0) {
						int tdx = (troop.x + gp.tileSize/2) - a.x;
						int tdy = (troop.y + gp.tileSize/2) - a.y;
						double tdist = Math.sqrt(tdx*tdx + tdy*tdy);
						if (tdist < a.radius) {
							if (a.duration % 6 == 0) {
								troop.health = Math.min(troop.maxHealth, troop.health + 1);
							}
						}
					}
				}
			}

			if (a.duration <= 0) ait.remove();
		}
	}

	private void performMeleeAttack() {
		int range = gp.tileSize + 4;
		int attackX = x;
		int attackY = y;
		int attackW = gp.tileSize;
		int attackH = gp.tileSize;

		switch (direction) {
		case "up":
			attackY -= range;
			break;
		case "down":
			attackY += range;
			break;
		case "left":
			attackX -= range;
			break;
		case "right":
			attackX += range;
			break;
		}

		for (Enemy enemy : gp.enemies) {
			if (enemy.dead) continue;
			int ex = enemy.x;
			int ey = enemy.y;
			int ew = gp.tileSize;
			int eh = gp.tileSize;
			if (ex + ew > attackX && ex < attackX + attackW && ey + eh > attackY && ey < attackY + attackH) {
				enemy.health -= 12;
				enemy.showHealthCounter = 60;
				if (enemy.health < 0) enemy.health = 0;
			}
		}
	}

	private boolean canMoveTo(int nextX, int nextY) {
		int left = nextX;
		int right = nextX + gp.tileSize - 1;
		int top = nextY;
		int bottom = nextY + gp.tileSize - 1;

		boolean tileBlocked = gp.isTileBlocked(left, top)
			|| gp.isTileBlocked(right, top)
			|| gp.isTileBlocked(left, bottom)
			|| gp.isTileBlocked(right, bottom);

		if (tileBlocked) {
			return false;
		}

		return !gp.isCollidingWithAnyEntity(nextX, nextY, this);
	}

	public void draw(Graphics2D g2, int cameraX, int cameraY) {
		// attack animation flash
		if (attackCooldown > 0) {
			g2.setColor(new Color(255, 255, 0, 150));
			int ax = x - cameraX;
			int ay = y - cameraY;
			switch (direction) {
			case "up":
				g2.fillOval(ax + gp.tileSize/4, ay - gp.tileSize/2, gp.tileSize/2, gp.tileSize/2);
				break;
			case "down":
				g2.fillOval(ax + gp.tileSize/4, ay + gp.tileSize, gp.tileSize/2, gp.tileSize/2);
				break;
			case "left":
				g2.fillOval(ax - gp.tileSize/2, ay + gp.tileSize/4, gp.tileSize/2, gp.tileSize/2);
				break;
			case "right":
				g2.fillOval(ax + gp.tileSize, ay + gp.tileSize/4, gp.tileSize/2, gp.tileSize/2);
				break;
			}
		}



		// --- Fallback: old static images ---
		BufferedImage image = null;
		switch(direction) {
		case("up"):
			if(spriteNum == 1) image = up1;
			if(spriteNum == 2) image = up2;
			break;
		case("down"):
			if(spriteNum == 1) image = down1;
			if(spriteNum == 2) image = down2;
			break;
		case("left"):
			if(spriteNum == 1) image = left1;
			if(spriteNum == 2) image = left2;
			break;
		case("right"):
			if(spriteNum == 1) image = right1;
			if(spriteNum == 2) image = right2;
			break;
		}
		if (image != null) {
			g2.drawImage(image, x - cameraX, y - cameraY, gp.tileSize, gp.tileSize, null);
		}
	}

}
