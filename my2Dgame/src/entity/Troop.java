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
    public int targetX = 0, targetY = 0;
    public int maxHealth = 30;
    public int health = maxHealth;
    public int shootCooldown = 0;
    public java.util.List<Projectile> projectiles = new ArrayList<>();

    public Troop(GamePanel gp, int x, int y, Role role) {
        this.gp = gp;
        this.x = x;
        this.y = y;
        this.role = role;
        this.speed = 3;
    }

    GamePanel gp;
    private boolean canMoveTo(int nextX, int nextY) {
		int left = nextX;
		int right = nextX + gp.tileSize - 1;
		int top = nextY;
		int bottom = nextY + gp.tileSize - 1;

		boolean tileBlocked = gp.isTileBlocked(left, top)	
			|| gp.isTileBlocked(left, top)
			|| gp.isTileBlocked(right, top)
			|| gp.isTileBlocked(left, bottom)
			|| gp.isTileBlocked(right, bottom);
		
			if(tileBlocked) return false;

		return !gp.isCollidingWithAnyEntity(nextX,nextY, this);	
	}
    public void update() {
        Player player = gp.player;
        int dx = 0, dy = 0;
        if (mode == Mode.FOLLOW || mode == Mode.DEFEND) {
            dx = player.x - x - 100;
            dy = player.y - y;
        } else if (mode == Mode.CHARGE) {
            dx = targetX - x;
            dy = targetY - y;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            x += (int)Math.signum(dx) * speed;
        } else {
            y += (int)Math.signum(dy) * speed;
        }

        int nextX = x, nextY = y;
        if (canMoveTo(x, y)){
            x = nextX;
            y = nextY;
        }

        if (role == Role.ARCHER) {
            if (shootCooldown > 0) {
                shootCooldown--;
            }
            Enemy target = null;
            int shortest = Integer.MAX_VALUE;
            for (Enemy enemy : gp.enemies) {
                if (enemy.dead) continue;
                int tx = enemy.x - x;
                int ty = enemy.y - y;
                int dist = Math.abs(tx) + Math.abs(ty);
                if (dist < shortest) {
                    shortest = dist;
                    target = enemy;
                }
            }
            if (shootCooldown == 0 && target != null) {
                int tx = target.x + gp.tileSize/2 - (x + gp.tileSize/2);
                int ty = target.y + gp.tileSize/2 - (y + gp.tileSize/2);
                if (Math.abs(tx) + Math.abs(ty) < gp.tileSize * 20) {
                    int stepX = Integer.signum(tx);
                    int stepY = Integer.signum(ty);
                    if (stepX != 0 || stepY != 0) {
                        projectiles.add(new Projectile(x + gp.tileSize/2, y + gp.tileSize/2, stepX, stepY, 5, 6, Color.cyan));
                    }
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
        int sx = x - cameraX;
        int sy = y - cameraY;
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
            int px = p.x - cameraX - p.size/2;
            int py = p.y - cameraY - p.size/2;
            g2.setColor(new Color(p.color.getRGB()));
            int[] xs = {px, px + p.size, px + p.size/2};
            int[] ys = {py + p.size, py + p.size, py};
            g2.fillPolygon(xs, ys, 3);
        }
    }
}

