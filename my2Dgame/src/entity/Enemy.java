package entity;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import my2Dgame.GamePanel;

public class Enemy extends Entity {

	GamePanel gp;
	BufferedImage image;

	int attackCooldown = 0;
	int stunTimer = 0;
	int shootCooldown = 0;
	int damage = 5;
	int chaseRange = 250;
	public int maxHealth = 20;
	public int health = maxHealth;
	public int showHealthCounter = 0;
	public int goldDrop = 2;
    public enum Type { BASIC, ARCHER, BOSS }
    public Type type = Type.BASIC;
    public boolean dead = false;
    public java.util.List<Projectile> projectiles = new ArrayList<>();

	public Enemy(GamePanel gp) {
		this.gp = gp;
		setDefaultValues();
		getEnemyImage();
	}

	public void setDefaultValues() {
		x = gp.tileSize * 20;
		y = gp.tileSize * 10;
		speed = 2;
		direction = "down";
	}

	public void getEnemyImage() {
		try {
			image = ImageIO.read(getClass().getResourceAsStream("/player/gen-248d0171-ced8-4380-b16f-842d5f23fde5.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void update(Player player) {
		if (player == null || image == null) {
			return;
		}

		if (health <= 0 && !dead) {
			dead = true;
			gp.spawnCoins(x + gp.tileSize/2, y + gp.tileSize/2, 3);
			// hide or move away
			x = -1000; y = -1000;
			return;
		}

		int dx = player.x - x;
		int dy = player.y - y;
		double distance = Math.sqrt(dx * dx + dy * dy);

		if (stunTimer == 0 && distance < chaseRange && distance > 0) {
			direction = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up");
			int stepX = (int) Math.signum(dx) * speed;
			int stepY = (int) Math.signum(dy) * speed;

			int nextX = x;
			int nextY = y;
			if (Math.abs(dx) > Math.abs(dy)) {
				nextX += stepX;
			} else {
				nextY += stepY;
			}

			if (canMoveTo(nextX, nextY)) {
				x = nextX;
				y = nextY;
			}

			// archer enemies fire projectiles toward player
			if (type == Type.ARCHER && shootCooldown == 0) {
				int tx = player.x + gp.tileSize/2 - (x + gp.tileSize/2);
				int ty = player.y + gp.tileSize/2 - (y + gp.tileSize/2);
				int dirX = Integer.signum(tx);
				int dirY = Integer.signum(ty);
				if (dirX != 0 || dirY != 0) {
					projectiles.add(new Projectile(x + gp.tileSize/2, y + gp.tileSize/2, dirX, dirY, 4, 8, java.awt.Color.MAGENTA));
					shootCooldown = 50;
				}
			}
		}

		if (stunTimer == 0 && distance < gp.tileSize + 4 && attackCooldown == 0) {
			player.health -= damage;
			if (player.health < 0) {
				player.health = 0;
			}
			attackCooldown = 30;
		}

		if (attackCooldown > 0) {
			attackCooldown--;
		}
		if (stunTimer > 0) {
			stunTimer--;
		}

		if (shootCooldown > 0) {
			shootCooldown--;
		}

		Iterator<Projectile> projIterator = projectiles.iterator();
		while (projIterator.hasNext()) {
			Projectile p = projIterator.next();
			p.update();
			if (p.life <= 0) {
				projIterator.remove();
			}
		}

		// check projectiles
		for (Projectile p : player.projectiles) {
			int px = p.x;
			int py = p.y;
			if (px > x && px < x + gp.tileSize && py > y && py < y + gp.tileSize) {
				health -= 10;
				p.life = 0;
				showHealthCounter = 60;
			}
		}

		// check areas
		for (AreaEffect a : player.areas) {
			int adx = (x + gp.tileSize/2) - a.x;
			int ady = (y + gp.tileSize/2) - a.y;
			double dist = Math.sqrt(adx*adx+ady*ady);
			if (dist < a.radius) {
				if (a.type == AreaEffect.Type.STUN_AND_DAMAGE) {
					health -= 1; // minor damage
					stunTimer = 30;
				}
				showHealthCounter = 60;
			}
		}

		if (showHealthCounter > 0) showHealthCounter--;
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
		if (image == null) {
			return;
		}
		int drawWidth = gp.tileSize * 3 / 4;
		int drawHeight = gp.tileSize * 3 / 4;
		int screenX = x - cameraX + (gp.tileSize - drawWidth) / 2;
		int screenY = y - cameraY + (gp.tileSize - drawHeight) / 2;
		if (type == Type.BOSS && image != null) {
			g2.drawImage(image, screenX, screenY, drawWidth, drawHeight, null);
		} else if (type == Type.ARCHER) {
			int[] xs = {screenX+drawWidth/2, screenX, screenX+drawWidth};
			int[] ys = {screenY, screenY+drawHeight, screenY+drawHeight};
			g2.setColor(java.awt.Color.orange);
			g2.fillPolygon(xs, ys, 3);
		} else {
			g2.setColor(java.awt.Color.orange);
			g2.fillRect(screenX, screenY, drawWidth, drawHeight);
		}
		for (Projectile p : projectiles) {
			int px = p.x - cameraX - p.size/2;
			int py = p.y - cameraY - p.size/2;
			g2.setColor(new java.awt.Color(p.color.getRGB()));
			int[] xs = {px, px + p.size, px + p.size/2};
			int[] ys = {py + p.size, py + p.size, py};
			g2.fillPolygon(xs, ys, 3);
		}
		if (type != Type.BOSS || showHealthCounter > 0 || health < maxHealth) {
			int barWidth = drawWidth;
			int barX = screenX;
			int barY = screenY - 8;
			int filled = (int)((double)health / maxHealth * barWidth);
			g2.setColor(java.awt.Color.red);
			g2.fillRect(barX, barY, filled, 6);
			g2.setColor(java.awt.Color.white);
			g2.drawRect(barX, barY, barWidth, 6);
		}
	}
}
