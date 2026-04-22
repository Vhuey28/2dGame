package entity;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import my2Dgame.GamePanel;

public class Enemy extends Entity {

	GamePanel gp;
	BufferedImage image;

	int attackCooldown = 0;
	int damage = 5;
	int chaseRange = 250;

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

		int dx = player.x - x;
		int dy = player.y - y;
		double distance = Math.sqrt(dx * dx + dy * dy);

		if (distance < chaseRange && distance > 0) {
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

			// no directional sprite animation: single static enemy image
		}

		if (distance < gp.tileSize + 4 && attackCooldown == 0) {
			player.health -= damage;
			if (player.health < 0) {
				player.health = 0;
			}
			attackCooldown = 30;
		}

		if (attackCooldown > 0) {
			attackCooldown--;
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
		if (image == null) {
			return;
		}
		int drawWidth = gp.tileSize * 3 / 4;
		int drawHeight = gp.tileSize * 3 / 4;
		int screenX = x - cameraX + (gp.tileSize - drawWidth) / 2;
		int screenY = y - cameraY + (gp.tileSize - drawHeight) / 2;
		g2.drawImage(image, screenX, screenY, drawWidth, drawHeight, null);
	}
}
