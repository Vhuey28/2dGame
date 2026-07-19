package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;

import my2Dgame.GamePanel;

public class Troop extends Entity {
    public enum Role { MELEE, ARCHER }
    public Role role = Role.MELEE;
    public enum Mode { FOLLOW, CHARGE, DEFEND }
    public Mode mode = Mode.FOLLOW;
    public float targetX = 0, targetY = 0;
    public int maxHealth = 30;
    public int health = maxHealth;
    public int shootCooldown = 0;
    public java.util.List<Projectile> projectiles = new ArrayList<>();

    public Troop(GamePanel gp, float x, float y, Role role) {
        this.gp = gp;
        this.x = x;
        this.y = y;
        this.role = role;
        this.speed = 3f;
        direction = "down";
    }

    GamePanel gp;
    private boolean canMoveTo(float nextX, float nextY) {
		int left = (int)Math.floor(nextX);
		int right = (int)Math.floor(nextX + gp.tileSize - 1);
		int top = (int)Math.floor(nextY);
		int bottom = (int)Math.floor(nextY + gp.tileSize - 1);

		boolean tileBlocked = gp.isTileBlocked(left, top)
			|| gp.isTileBlocked(left, top)
			|| gp.isTileBlocked(right, top)
			|| gp.isTileBlocked(left, bottom)
			|| gp.isTileBlocked(right, bottom);

		if(tileBlocked) return false;

		return !gp.isCollidingWithAnyEntity(nextX, nextY, this);
	}

    public void update() {
        Player player = gp.player;
        float dx = 0, dy = 0;
        if (mode == Mode.FOLLOW || mode == Mode.DEFEND) {
            dx = player.x - x - 100;
            dy = player.y - y;
        } else if (mode == Mode.CHARGE) {
            dx = targetX - x;
            dy = targetY - y;
        }

        // Normalize for diagonal movement
        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (distance > 0) {
            dx = dx / distance * speed;
            dy = dy / distance * speed;
        }

        // Determine direction for sprite
        if (dx != 0f || dy != 0f) {
            if (dy < -0.707f && dx < -0.707f) direction = "upLeft";
            else if (dy < -0.707f && dx > 0.707f) direction = "upRight";
            else if (dy > 0.707f && dx < -0.707f) direction = "downLeft";
            else if (dy > 0.707f && dx > 0.707f) direction = "downRight";
            else if (dy < 0) direction = "up";
            else if (dy > 0) direction = "down";
            else if (dx < 0) direction = "left";
            else if (dx > 0) direction = "right";
        }

        // Move with collision sliding
        float nextX = x + dx;
        float nextY = y + dy;
        boolean moved = false;
        if (dx != 0f) {
            if (canMoveTo(nextX, y)) {
                x = nextX;
                moved = true;
            }
        }
        if (dy != 0f) {
            if (canMoveTo(x, nextY)) {
                y = nextY;
                moved = true;
            }
        }

        if (moved) {
            spriteCounter++;
            if (spriteCounter > 20) {
                if (spriteNum == 1) spriteNum = 2;
                else if (spriteNum == 2) spriteNum = 1;
                spriteCounter = 0;
            }
        }

        if (role == Role.ARCHER) {
            if (shootCooldown > 0) {
                shootCooldown--;
            }
            Enemy target = null;
            float shortest = Float.MAX_VALUE;
            for (Enemy enemy : gp.enemies) {
                if (enemy.dead) continue;
                float tx = enemy.x - x;
                float ty = enemy.y - y;
                float dist = (float)Math.sqrt(tx * tx + ty * ty);
                if (dist < shortest) {
                    shortest = dist;
                    target = enemy;
                }
            }
            if (shootCooldown == 0 && target != null) {
                float tx = target.x + gp.tileSize/2f - (x + gp.tileSize/2f);
                float ty = target.y + gp.tileSize/2f - (y + gp.tileSize/2f);
                float projDist = (float)Math.sqrt(tx * tx + ty * ty);
                if (projDist < gp.tileSize * 20) {
                    if (projDist > 0) {
                        tx = tx / projDist;
                        ty = ty / projDist;
                    }
                    projectiles.add(new Projectile(x + gp.tileSize/2f, y + gp.tileSize/2f, tx, ty, 5, 6, Color.cyan));
                    shootCooldown = 40;
                }
            }
        }


        Iterator<Projectile> projIterator = projectiles.iterator();
        while (projIterator.hasNext()) {
            Projectile p = projIterator.next();
            p.update();
            if (p.life <= 0) projIterator.remove();
        }
    }

    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int sx = (int)x - cameraX;
        int sy = (int)y - cameraY;
        if (role == Role.ARCHER) {
            int[] xs = {sx + gp.tileSize/2, sx, sx + gp.tileSize};
            int[] ys = {sy, sy + gp.tileSize, sy + gp.tileSize};
            g2.setColor(Color.blue);
            g2.fillPolygon(xs, ys, 3);
        } else {
            g2.setColor(Color.blue);
            g2.fillRect(sx, sy, gp.tileSize, gp.tileSize);
            g2.setColor(Color.white);
            g2.drawRect(sx, sy, gp.tileSize, gp.tileSize);
        }

        for (Projectile p : projectiles) {
            int px = (int)p.x - cameraX - p.size/2;
            int py = (int)p.y - cameraY - p.size/2;
            g2.setColor(new Color(p.color.getRGB()));
            int[] xs = {px, px + p.size, px + p.size/2};
            int[] ys = {py + p.size, py + p.size, py};
            g2.fillPolygon(xs, ys, 3);
        }
    }
}