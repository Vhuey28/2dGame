package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.InputStream;
import javax.imageio.ImageIO;

import my2Dgame.GamePanel;
import my2Dgame.KeyHandler;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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

	// Attack animation state
	public boolean isAttacking = false;
	public int attackAnimationCounter = 0;
	public int attackAnimationFrame = 0;

	// Player attack animations (8 frames per direction)
	public BufferedImage[] attackUp = new BufferedImage[7];    // 6 frames
	public BufferedImage[] attackDown = new BufferedImage[6];
	public BufferedImage[] attackLeft = new BufferedImage[6];
	public BufferedImage[] attackRight = new BufferedImage[6];
	public BufferedImage[] attackUpLeft = new BufferedImage[6];
	public BufferedImage[] attackUpRight = new BufferedImage[6];
	public BufferedImage[] attackDownLeft = new BufferedImage[6];
	public BufferedImage[] attackDownRight = new BufferedImage[6];

	public java.util.List<Projectile> projectiles = new ArrayList<>();
	public java.util.List<AreaEffect> areas = new ArrayList<>();

	public Player(GamePanel gp, KeyHandler keyH) {

		this.gp = gp;
		this.keyH = keyH;


		setDefaultValues();
		getPlayerImage();
	}
	private BufferedImage loadImage(String path) throws IOException {

    	InputStream is = getClass().getResourceAsStream(path);

   		 if (is == null) {
       	 	throw new IOException("Resource not found: " + path);
    	}

   		 return ImageIO.read(is);
	}
	/**
   * Crops transparent borders from an image, returning a new image
   * containing only the non-transparent content.
   */
  private BufferedImage cropTransparentBorders(BufferedImage src) {
    if (src == null) return null;

    int width = src.getWidth();
    int height = src.getHeight();
    int minX = width, minY = height, maxX = -1, maxY = -1;

    // Scan for non-transparent pixels
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int argb = src.getRGB(x, y);
        if ((argb >> 24) != 0) { // alpha != 0
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }

    // If image is fully transparent, return original
    if (maxX < minX || maxY < minY) {
      return src;
    }

    // Crop to content bounds
    int cropWidth = maxX - minX + 1;
    int cropHeight = maxY - minY + 1;
    return src.getSubimage(minX, minY, cropWidth, cropHeight);
  }

	public void setDefaultValues() {

		x = 100f;
		y = 100f;
		speed = 5f;
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

			up2 = loadImage("/player/sword_animations/standard/walk/up/8.png");
			up1 = loadImage("/player/sword_animations/standard/walk/up/4.png");
			down1 = loadImage("/player/sword_animations/standard/walk/down/4.png");
			down2 = loadImage("/player/sword_animations/standard/walk/down/8.png");
			right1 = loadImage("/player/sword_animations/standard/walk/right/4.png");
			right2 = loadImage("/player/sword_animations/standard/walk/right/8.png");
			left1 = loadImage("/player/sword_animations/standard/walk/left/4.png");
			left2 = loadImage("/player/sword_animations/standard/walk/left/8.png");
			upRight1 = loadImage("/player/sword_animations/standard/walk/right/4.png");
			upRight2 = loadImage("/player/sword_animations/standard/walk/right/8.png");
			downRight1 = loadImage("/player/sword_animations/standard/walk/right/4.png");
			downRight2 = loadImage("/player/sword_animations/standard/walk/right/8.png");
			upLeft1 = loadImage("/player/sword_animations/standard/walk/left/4.png");
			upLeft2 = loadImage("/player/sword_animations/standard/walk/left/8.png");
			downLeft1= loadImage("/player/sword_animations/standard/walk/left/4.png");
			downLeft2= loadImage("/player/sword_animations/standard/walk/left/8.png");

			// Load attack animations (8 frames per direction)
			// Attack up
			 attackUp[0] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/up/1.png"));
      		attackUp[1] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/up/2.png"));
      		attackUp[2] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/up/3.png"));
      		attackUp[3] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/up/4.png"));
      		attackUp[4] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/up/5.png"));
     		 attackUp[5] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/up/6.png"));		
			 attackUp[6] = cropTransparentBorders(loadImage("/player/sword_animations/standard/walk/up/4.png"));				//Users/vaughnhuey/Homework/code/2dGame/my2Dgame/bin/player/sword_animations/slash_oversize/up/1.png
			// Attack down
			 attackDown[0] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/down/1.png"));
     		attackDown[1] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/down/2.png"));
      		attackDown[2] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/down/3.png"));
      		attackDown[3] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/down/4.png"));
     		 attackDown[4] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/down/5.png"));
     		 attackDown[5] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/down/6.png"));
			// Attack left
			attackLeft[0] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/left/1.png"));
      		attackLeft[1] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/left/2.png"));
      		attackLeft[2] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/left/3.png"));
      		attackLeft[3] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/left/4.png"));
      		attackLeft[4] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/left/5.png"));
      		attackLeft[5] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/left/6.png"));
			// Attack right
			attackRight[0] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/right/1.png"));
      		attackRight[1] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/right/2.png"));
      		attackRight[2] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/right/3.png"));
      		attackRight[3] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/right/4.png"));
      		attackRight[4] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/right/5.png"));
      		attackRight[5] = cropTransparentBorders(loadImage("/player/sword_animations/custom/slash_oversize/right/6.png"));
			
			// Diagonal attack frames - horizontal priority only (single copy, no overwrites)
			copyArray(attackLeft, attackUpLeft);
			copyArray(attackRight, attackUpRight);
			copyArray(attackLeft, attackDownLeft);
			copyArray(attackRight, attackDownRight);

		}catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void copyArray(BufferedImage[] src, BufferedImage[] dest) {
		if (src != null && dest != null) {
			int len = Math.min(src.length, dest.length);
			System.arraycopy(src, 0, dest, 0, len);
		}
	}

	public void update() {
		// Calculate movement vector from input
		float moveX = 0f;
		float moveY = 0f;
		boolean moved = false;

		if (keyH.upPressed) moveY -= 1f;
		if (keyH.downPressed) moveY += 1f;
		if (keyH.leftPressed) moveX -= 1f;
		if (keyH.rightPressed) moveX += 1f;

		// Normalize diagonal movement (so diagonal speed equals cardinal speed)
		if (moveX != 0f && moveY != 0f) {
			float invSqrt2 = 0.70710678f; // 1/sqrt(2)
			moveX *= invSqrt2;
			moveY *= invSqrt2;
		}

		// Apply speed
		moveX *= speed;
		moveY *= speed;

		// Calculate new position
		float newX = x + moveX;
		float newY = y + moveY;

		// Determine direction for sprite (8 directions)
		if (moveX != 0f || moveY != 0f) {
			if (moveY < 0 && moveX < 0) direction = "upLeft";
			else if (moveY < 0 && moveX > 0) direction = "upRight";
			else if (moveY > 0 && moveX < 0) direction = "downLeft";
			else if (moveY > 0 && moveX > 0) direction = "downRight";
			else if (moveY < 0) direction = "up";
			else if (moveY > 0) direction = "down";
			else if (moveX < 0) direction = "left";
			else if (moveX > 0) direction = "right";
		}

		// Try to move - handle X and Y separately for better collision sliding
		if (moveX != 0f) {
			if (pcanMoveTo(newX, y)) {
				x = newX;
				moved = true;
			}
		}
		if (moveY != 0f) {
			if (pcanMoveTo(x, newY)) {
				y = newY;
				moved = true;
			}
		}

		if (moved) {
			spriteCounter++;
			if (spriteCounter > 20) {
				if (spriteNum == 1) {
					spriteNum = 2;
				} else if (spriteNum == 2) {
					spriteNum = 1;
				}
				spriteCounter = 0;
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
		// Update attack animation independently of cooldown (so it completes fully)
		if (isAttacking) {
			attackAnimationCounter++;
			if (attackAnimationCounter > 2) { // Animation speed
				attackAnimationFrame++;
				attackAnimationCounter = 0;
				if (attackAnimationFrame >= 6) {
					attackAnimationFrame = 0;
					isAttacking = false;
					// Reset walking animation to first frame for smooth transition
					spriteNum = 1;
					spriteCounter = 0;
				}
			}
		}
		if (attackCooldown > 0) {
			attackCooldown--;
		}
		if (keyH.spacePressed && attackCooldown == 0 && stamina >= 10) {
			stamina -= 10;
			attackCooldown = 16;
			isAttacking = true;
			attackAnimationCounter = 0;
			attackAnimationFrame = 0; // Start attack animation from first frame
			performMeleeAttack();
		}
		
		if (keyH.num1Pressed && mana >= 15) {
			mana -= 15;
			// spawn red triangle projectile
			int dirX = 0, dirY = 0;
			switch (direction) {
				case "up": dirY = -1; break;
				case "upRight": dirX = 1; dirY = -1; break;
				case "right": dirX = 1; break;
				case "downRight": dirX = 1; dirY = 1; break;
				case "down": dirY = 1; break;
				case "downLeft": dirX = -1; dirY = 1; break;
				case "left": dirX = -1; break;
				case "upLeft": dirX = -1; dirY = -1; break;
			}
			if (dirX != 0 || dirY != 0) {
				// Normalize diagonal for projectile
				if (dirX != 0 && dirY != 0) {
					float invSqrt2 = 0.70710678f;
					projectiles.add(new Projectile(x + gp.tileSize/2f, y + gp.tileSize/2f, dirX * invSqrt2, dirY * invSqrt2, 6, 8, java.awt.Color.RED));
				} else {
					projectiles.add(new Projectile(x + gp.tileSize/2f, y + gp.tileSize/2f, dirX, dirY, 6, 8, java.awt.Color.RED));
				}
			}
		}
		if (keyH.num2Pressed && mana >= 25) {
			mana -= 25;
			areas.add(new AreaEffect(x + gp.tileSize/2f, y + gp.tileSize/2f, gp.tileSize*3, 120, AreaEffect.Type.STUN_AND_DAMAGE, true, this));
		}
		if (keyH.num3Pressed && mana >= 20) {
			mana -= 20;
			areas.add(new AreaEffect(x + gp.tileSize/2f, y + gp.tileSize/2f, gp.tileSize*3, 180, AreaEffect.Type.HEAL));
		}

		// dodge
		if (keyH.ePressed && stamina >= 20) {
			stamina -= 3;
			// quick move in facing direction
			float dx = 0, dy = 0;
			switch (direction) {
				case "up": dy = -gp.tileSize/2f; break;
				case "upRight": dx = gp.tileSize/2f * 0.70710678f; dy = -gp.tileSize/2f * 0.70710678f; break;
				case "right": dx = gp.tileSize/2f; break;
				case "downRight": dx = gp.tileSize/2f * 0.70710678f; dy = gp.tileSize/2f * 0.70710678f; break;
				case "down": dy = gp.tileSize/2f; break;
				case "downLeft": dx = -gp.tileSize/2f * 0.70710678f; dy = gp.tileSize/2f * 0.70710678f; break;
				case "left": dx = -gp.tileSize/2f; break;
				case "upLeft": dx = -gp.tileSize/2f * 0.70710678f; dy = -gp.tileSize/2f * 0.70710678f; break;
			}
			float nextX = x + dx;
			float nextY = y + dy;
			if (pcanMoveTo(nextX, nextY)) {
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
				float adx = (x + gp.tileSize/2f) - a.x;
				float ady = (y + gp.tileSize/2f) - a.y;
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
						float tdx = (troop.x + gp.tileSize/2f) - a.x;
						float tdy = (troop.y + gp.tileSize/2f) - a.y;
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
		float attackX = x;
		float attackY = y;
		int attackW = gp.tileSize;
		int attackH = gp.tileSize;

		switch (direction) {
		case "up":
			attackY -= range;
			break;
		case "upRight":
			attackX += range * 0.70710678f;
			attackY -= range * 0.70710678f;
			break;
		case "right":
			attackX += range;
			break;
		case "downRight":
			attackX += range * 0.70710678f;
			attackY += range * 0.70710678f;
			break;
		case "down":
			attackY += range;
			break;
		case "downLeft":
			attackX -= range * 0.70710678f;
			attackY += range * 0.70710678f;
			break;
		case "left":
			attackX -= range;
			break;
		case "upLeft":
			attackX -= range * 0.70710678f;
			attackY -= range * 0.70710678f;
			break;
		}

		for (Enemy enemy : gp.enemies) {
			if (enemy.dead) continue;
			float ex = enemy.x;
			float ey = enemy.y;
			int ew = gp.tileSize;
			int eh = gp.tileSize;
			if (ex + ew > attackX && ex < attackX + attackW && ey + eh > attackY && ey < attackY + attackH) {
				enemy.health -= 12;
				enemy.showHealthCounter = 60;
				if (enemy.health < 0) enemy.health = 0;
			}
		}
	}

	private boolean pcanMoveTo(float nextX, float nextY) {
		int left = (int)Math.floor(nextX);
		int right = (int)Math.floor(nextX + gp.tileSize - 1);
		int top = (int)Math.floor(nextY);
		int bottom = (int)Math.floor(nextY + gp.tileSize - 1);

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
		// Draw attack animation if attacking
		if (isAttacking) {
			BufferedImage attackImage = getAttackFrameForDirection(direction);
			if (attackImage != null) {
				g2.drawImage(attackImage, (int)x - cameraX, (int)y - cameraY, gp.tileSize, gp.tileSize, null);
			} else {
				// Fallback to idle sprite if attack frame not loaded
				BufferedImage image = getIdleFrameForDirection(direction);
				if (image != null) {
					g2.drawImage(image, (int)x - cameraX, (int)y - cameraY, gp.tileSize, gp.tileSize, null);
				}
			}
		} else {
			// --- Fallback: old static images ---
			BufferedImage image = getIdleFrameForDirection(direction);
				g2.drawImage(image, (int)x - cameraX, (int)y - cameraY, gp.tileSize, gp.tileSize, null);
			
		}
	}

	private BufferedImage getIdleFrameForDirection(String dir) {
		switch(dir) {
		case("up"):
			if(spriteNum == 1) return up1;
			if(spriteNum == 2) return up2;
			break;
		case("upRight"):
			if(spriteNum == 1) return upRight1;
			if(spriteNum == 2) return upRight2;
			break;
		case("right"):
			if(spriteNum == 1) return right1;
			if(spriteNum == 2) return right2;
			break;
		case("downRight"):
			if(spriteNum == 1) return downRight1;
			if(spriteNum == 2) return downRight2;
			break;
		case("down"):
			if(spriteNum == 1) return down1;
			if(spriteNum == 2) return down2;
			break;
		case("downLeft"):
			if(spriteNum == 1) return downLeft1;
			if(spriteNum == 2) return downLeft2;
			break;
		case("left"):
			if(spriteNum == 1) return left1;
			if(spriteNum == 2) return left2;
			break;
		case("upLeft"):
			if(spriteNum == 1) return upLeft1;
			if(spriteNum == 2) return upLeft2;
			break;
		}
		return null;
	}

	private BufferedImage getAttackFrameForDirection(String dir) {
		
		switch (dir) {
			case "up": return attackUp[attackAnimationFrame];
			case "down": return attackDown[attackAnimationFrame];
			case "left": return attackLeft[attackAnimationFrame];
			case "right": return attackRight[attackAnimationFrame];
			case "upLeft": return attackUpLeft[attackAnimationFrame];
			case "upRight": return attackUpRight[attackAnimationFrame];
			case "downLeft": return attackDownLeft[attackAnimationFrame];
			case "downRight": return attackDownRight[attackAnimationFrame];
			default: return up1;
		}
	}

}