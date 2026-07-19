package entity;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.InputStream;
import javax.imageio.ImageIO;
import my2Dgame.GamePanel;
public class Enemy extends Entity {
    Entity emEntity;
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
    // Animation state for boss
    public boolean isAttacking = false;
    public int attackAnimationCounter = 0;
    public int attackAnimationFrame = 1;
    public boolean isDying = false;
    public int deathAnimationCounter = 0;
    public int deathAnimationFrame = 1;
    public boolean deathAnimationComplete = false;
    public boolean deathLootDropped = false;
    // Boss walk animation frames (walk_128: 9 frames per direction)
    BufferedImage[] bossWalkUp = new BufferedImage[10]; // index 1-9
    BufferedImage[] bossWalkDown = new BufferedImage[10];
    BufferedImage[] bossWalkLeft = new BufferedImage[10];
    BufferedImage[] bossWalkRight = new BufferedImage[10];
    // Diagonal walk frames (reuse cardinal frames)
    BufferedImage[] bossWalkUpLeft = new BufferedImage[10];
    BufferedImage[] bossWalkUpRight = new BufferedImage[10];
    BufferedImage[] bossWalkDownLeft = new BufferedImage[10];
    BufferedImage[] bossWalkDownRight = new BufferedImage[10];
    // Boss attack animation frames (thrust_oversize: 8 frames per direction)
    BufferedImage[] bossAttackUp = new BufferedImage[9]; // index 1-8
    BufferedImage[] bossAttackDown = new BufferedImage[9];
    BufferedImage[] bossAttackLeft = new BufferedImage[9];
    BufferedImage[] bossAttackRight = new BufferedImage[9];
    BufferedImage[] bossAttackUpLeft = new BufferedImage[9];
    BufferedImage[] bossAttackUpRight = new BufferedImage[9];
    BufferedImage[] bossAttackDownLeft = new BufferedImage[9];
    BufferedImage[] bossAttackDownRight = new BufferedImage[9];
    // Boss death/hurt animation frames (standard/hurt: 6 frames per direction)
    BufferedImage[] bossDeathUp = new BufferedImage[7];    // index 1-6
    BufferedImage[] bossDeathDown = new BufferedImage[7];
    BufferedImage[] bossDeathLeft = new BufferedImage[7];
    BufferedImage[] bossDeathRight = new BufferedImage[7];
    // Diagonal death frames (reuse nearest cardinal)
    BufferedImage[] bossDeathUpLeft = new BufferedImage[7];
    BufferedImage[] bossDeathUpRight = new BufferedImage[7];
    BufferedImage[] bossDeathDownLeft = new BufferedImage[7];
    BufferedImage[] bossDeathDownRight = new BufferedImage[7];
    public Enemy(GamePanel gp) {
        this.gp = gp;
        setDefaultValues();
        getEnemyImage();
    }
    public void setType(Type type) {
        this.type = type;
        if (type == Type.BOSS) {
            // Boss needs custom stats
            maxHealth = 60;
            health = maxHealth;
            goldDrop = 10;
            speed = 1.5f;
            damage = 12;
        }
        getEnemyImage();
    }
    public void setDefaultValues() {
        x = gp.tileSize * 20f;
        y = gp.tileSize * 10f;
        speed = 2f;
        direction = "down";
    }
    public void getEnemyImage() {
        try {
            // Load boss walk animations (walk_128: 9 frames per direction)
            if (type == Type.BOSS) {
                // Walk up
                for (int i = 1; i <= 9; i++) {
                    bossWalkUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/walk_128/up/" + i + ".png"));
                }
                // Walk down
                for (int i = 1; i <= 9; i++) {
                    bossWalkDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/walk_128/down/" + i + ".png"));
                }
                // Walk left
                for (int i = 1; i <= 9; i++) {
                    bossWalkLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/walk_128/left/" + i + ".png"));
                }
                // Walk right
                for (int i = 1; i <= 9; i++) {
                    bossWalkRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/walk_128/right/" + i + ".png"));
                }
                // Diagonal walk frames - horizontal priority only (single copy, no overwrites)
                copyArrayFrom1(bossWalkLeft, bossWalkUpLeft);    // upLeft uses left frames
                copyArrayFrom1(bossWalkRight, bossWalkUpRight);  // upRight uses right frames
                copyArrayFrom1(bossWalkLeft, bossWalkDownLeft);  // downLeft uses left frames
                copyArrayFrom1(bossWalkRight, bossWalkDownRight); // downRight uses right frames
                // Load boss attack animations (thrust_oversize: 8 frames per direction)
                // Attack up
                for (int i = 0; i <= 7; i++) {
                    bossAttackUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/thrust_oversize/up/" + (i+1) + ".png"));
                }
                // Attack down
                for (int i = 0; i <= 7; i++) {
                    bossAttackDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/thrust_oversize/down/" + (i+1) + ".png"));
                }
                // Attack left
                for (int i = 0; i <= 7; i++) {
                    bossAttackLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/thrust_oversize/left/" + (i+1) + ".png"));
                }
                // Attack right
                for (int i = 0; i <= 7; i++) {
                    bossAttackRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/custom/thrust_oversize/right/" + (i+1) + ".png"));
                }
                // Diagonal attack frames - horizontal priority only (single copy, no overwrites)
                copyArrayFrom1(bossAttackLeft, bossAttackUpLeft);
                copyArrayFrom1(bossAttackRight, bossAttackUpRight);
                copyArrayFrom1(bossAttackLeft, bossAttackDownLeft);
                copyArrayFrom1(bossAttackRight, bossAttackDownRight);
                // Load boss death animation (standard/hurt: 6 frames per direction)
                // Only "up" direction files exist; copy to all other directions
                // Death up
                for (int i = 1; i <= 6; i++) {
                    bossDeathUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyBoss_animations/standard/hurt/up/" + i + ".png"));
                }
                // Copy up to all other cardinal directions (no separate hurt files exist)
                copyArrayFrom1(bossDeathUp, bossDeathDown);
                copyArrayFrom1(bossDeathUp, bossDeathLeft);
                copyArrayFrom1(bossDeathUp, bossDeathRight);

                // Diagonal death frames - horizontal priority only (single copy, no overwrites)
                copyArrayFrom1(bossDeathLeft, bossDeathUpLeft);
                copyArrayFrom1(bossDeathRight, bossDeathUpRight);
                copyArrayFrom1(bossDeathLeft, bossDeathDownLeft);
                copyArrayFrom1(bossDeathRight, bossDeathDownRight);
                // Set initial image
                image = bossWalkDown[1];
            } else {
                // Basic/Archer enemies don't need loaded images - they're drawn as shapes in draw()
                image = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // Copy array starting from index 1 (since all animation arrays use 1-based indexing)
    private void copyArrayFrom1(BufferedImage[] src, BufferedImage[] dest) {
        if (src != null && dest != null) {
            int len = Math.min(src.length, dest.length);
            // Start from index 1 to skip null index 0
            for (int i = 1; i < len; i++) {
                dest[i] = src[i];
            }
        }
    }
    private void copyArray(BufferedImage[] src, BufferedImage[] dest) {
        if (src != null && dest != null) {
            int len = Math.min(src.length, dest.length);
            System.arraycopy(src, 0, dest, 0, len);
        }
    }
    public void update(Player player) {
        if (player == null) {
            return;
        }
        // Handle death animation for boss
        if (type == Type.BOSS && health <= 0 && !dead && !isDying) {
            dead = true;
            isDying = true;
            deathAnimationCounter = 0;
            deathAnimationFrame = 1;
            deathAnimationComplete = false;
            deathLootDropped = false;
            speed = 0; // Stop movement
            damage = 0; // Stop dealing damage
            attackCooldown = 0;
            isAttacking = false;
        }
        if (type == Type.BOSS && isDying) {
            deathAnimationCounter++;
            if (deathAnimationCounter > 6) { // Death animation speed
                deathAnimationFrame++;
                deathAnimationCounter = 0;
                if (deathAnimationFrame > 6) {
                    deathAnimationFrame = 6;
                    deathAnimationComplete = true;
                    if (!deathLootDropped) {
                        gp.spawnCoins((int)x + gp.tileSize/2, (int)y + gp.tileSize/2, 10);
                        deathLootDropped = true;
                    }
                    // Move off screen after death animation
                    x = -1000;
                    y = -1000;
                }
            }
            return;
        }
        if (health <= 0 && !dead) {
            dead = true;
            if (type != Type.BOSS) {
                gp.spawnCoins((int)x + gp.tileSize/2, (int)y + gp.tileSize/2, goldDrop);
                x = -1000; y = -1000;
            }
            return;
        }
        float dx = player.x - x;
        float dy = player.y - y;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (stunTimer == 0 && distance < chaseRange && distance > 0) {
            // Normalize direction vector for smooth diagonal movement
            float dirX = dx / distance;
            float dirY = dy / distance;
            // Determine direction for sprite (8 directions)
            if (dirY < -0.707f && dirX < -0.707f) direction = "upLeft";
            else if (dirY < -0.707f && dirX > 0.707f) direction = "upRight";
            else if (dirY > 0.707f && dirX < -0.707f) direction = "downLeft";
            else if (dirY > 0.707f && dirX > 0.707f) direction = "downRight";
            else if (Math.abs(dirX) > Math.abs(dirY)) {
                // Horizontal is dominant
                if (dirX < 0) direction = "left";
                else direction = "right";
            } else {
                // Vertical is dominant
                if (dirY < 0) direction = "up";
                else direction = "down";
            }
            // Calculate movement with normalized direction
            float moveX = dirX * speed;
            float moveY = dirY * speed;
            float nextX = x + moveX;
            float nextY = y + moveY;
            // Try to move - handle X and Y separately for better collision sliding
            boolean moved = false;
            if (moveX != 0f) {
                if (canMoveTo(nextX, y)) {
                    x = nextX;
                    moved = true;
                }
            }
            if (moveY != 0f) {
                if (canMoveTo(x, nextY)) {
                    y = nextY;
                    moved = true;
                }
            }
            // Update walk animation for boss
            if (type == Type.BOSS && moved && !isAttacking && !isDying) {
                spriteCounter++;
                if (spriteCounter > 12) { // Animation speed
                    spriteNum++;
                    if (spriteNum > 9) spriteNum = 1;
                    spriteCounter = 0;
                }
            } else if (type == Type.BOSS && !isAttacking && !isDying) {
                // Reset to first frame when idle
                spriteNum = 1;
                spriteCounter = 0;
            }
            // Archer enemies fire projectiles toward player
            if (type == Type.ARCHER && shootCooldown == 0) {
                int tx = (int)(player.x + gp.tileSize/2f - (x + gp.tileSize/2f));
                int ty = (int)(player.y + gp.tileSize/2f - (y + gp.tileSize/2f));
                int projDirX = Integer.signum(tx);
                int projDirY = Integer.signum(ty);
                if (projDirX != 0 || projDirY != 0) {
                    // Normalize diagonal for projectile
                    if (projDirX != 0 && projDirY != 0) {
                        float invSqrt2 = 0.70710678f;
                        projectiles.add(new Projectile(x + gp.tileSize/2f, y + gp.tileSize/2f, projDirX * invSqrt2, projDirY * invSqrt2, 4, 8, java.awt.Color.MAGENTA));
                    } else {
                        projectiles.add(new Projectile(x + gp.tileSize/2f, y + gp.tileSize/2f, projDirX, projDirY, 4, 8, java.awt.Color.MAGENTA));
                    }
                    shootCooldown = 50;
                }
            }
        }
        // Boss attack logic - trigger attack animation when in range
        if (type == Type.BOSS && stunTimer == 0 && distance < gp.tileSize + 4 && attackCooldown == 0 && !isDying) {
            // Update attack direction toward player
            float dirX = dx / distance;
            float dirY = dy / distance;
            if (dirY < -0.707f && dirX < -0.707f) direction = "upLeft";
            else if (dirY < -0.707f && dirX > 0.707f) direction = "upRight";
            else if (dirY > 0.707f && dirX < -0.707f) direction = "downLeft";
            else if (dirY > 0.707f && dirX > 0.707f) direction = "downRight";
            else if (Math.abs(dirX) > Math.abs(dirY)) {
                // Horizontal is dominant
                if (dirX < 0) direction = "left";
                else direction = "right";
            } else {
                // Vertical is dominant
                if (dirY < 0) direction = "up";
                else direction = "down";
            }
            isAttacking = true;
            attackAnimationCounter = 0;
            attackAnimationFrame = 0; // Start from frame 0 (8 frames: 0-7)
            player.health -= damage;
            if (player.health < 0) {
                player.health = 0;
            }
            attackCooldown = 40; // Boss attack cooldown
        }
        // Update attack animation for boss (independent of cooldown)
        if (type == Type.BOSS && isAttacking && !isDying) {
            attackAnimationCounter++;
            if (attackAnimationCounter > 4) { // Attack animation speed
                attackAnimationFrame++;
                attackAnimationCounter = 0;
                if (attackAnimationFrame >= 8) { // 8 frames (0-7)
                    attackAnimationFrame = 0;
                    isAttacking = false;
                    // Reset walk animation for smooth transition
                    spriteNum = 1;
                    spriteCounter = 0;
                }
            }
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
        // Check projectiles from player
        for (Projectile p : player.projectiles) {
            int px = (int)p.x;
            int py = (int)p.y;
            if (px > x && px < x + gp.tileSize && py > y && py < y + gp.tileSize) {
                health -= 10;
                p.life = 0;
                showHealthCounter = 60;
            }
        }
        // Check areas from player
        for (AreaEffect a : player.areas) {
            float adx = (x + gp.tileSize/2f) - a.x;
            float ady = (y + gp.tileSize/2f) - a.y;
            float dist = (float)Math.sqrt(adx*adx+ady*ady);
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
    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        // Boss uses larger sprite size (2x tile size for boss)
        int drawWidth, drawHeight;
        if (type == Type.BOSS) {
            drawWidth = gp.tileSize * 3;  // 3x tile = 144px
            drawHeight = gp.tileSize * 3;
        } else {
            drawWidth = gp.tileSize;  // 1x tile = 48px
            drawHeight = gp.tileSize;
        }
        int screenX = (int)x - cameraX + (gp.tileSize - drawWidth) / 2;
        int screenY = (int)y - cameraY + (gp.tileSize - drawHeight) / 2;
        BufferedImage currentFrame = getCurrentAnimationFrame();
        if (type == Type.BOSS) {
            if (currentFrame != null) {
                g2.drawImage(currentFrame, screenX, screenY, drawWidth, drawHeight, null);
            } else {
                // Debug: draw red box if no frame
                g2.setColor(java.awt.Color.red);
                g2.fillRect(screenX, screenY, drawWidth, drawHeight);
            }
        } else if (type == Type.ARCHER) {
            int[] xs = {screenX+drawWidth/2, screenX, screenX+drawWidth};
            int[] ys = {screenY, screenY+drawHeight, screenY+drawHeight};
            g2.setColor(java.awt.Color.orange);
            g2.fillPolygon(xs, ys, 3);
            g2.setColor(java.awt.Color.black);
            g2.drawPolygon(xs, ys, 3);
        } else if (type == Type.BASIC) {
            g2.setColor(java.awt.Color.orange);
            g2.fillRect(screenX, screenY, drawWidth, drawHeight);
            g2.setColor(java.awt.Color.black);
            g2.drawRect(screenX, screenY, drawWidth, drawHeight);
        }
        for (Projectile p : projectiles) {
            int px = (int)p.x - cameraX - p.size/2;
            int py = (int)p.y - cameraY - p.size/2;
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
    private BufferedImage getCurrentAnimationFrame() {
        if (type != Type.BOSS) {
            return image;
        }
        // Death animation takes priority
        if (isDying) {
            return getDeathFramesForDirection(direction)[deathAnimationFrame];
        }
        // Attack animation
        if (isAttacking) {
            BufferedImage[] attackFrames = getAttackFramesForDirection(direction);
            if (attackFrames != null && attackAnimationFrame < attackFrames.length && attackFrames[attackAnimationFrame] != null) {
                return attackFrames[attackAnimationFrame];
            }
            // Fallback to walk frame if attack frame not loaded
        }
        // Walk animation
        BufferedImage[] walkFrames = getWalkFramesForDirection(direction);
        if (walkFrames != null && spriteNum < walkFrames.length && walkFrames[spriteNum] != null) {
            return walkFrames[spriteNum];
        }
        // Fallback
        return image;
    }
    private BufferedImage[] getDeathFramesForDirection(String dir) {
        switch (dir) {
            case "up": return bossDeathUp;
            case "down": return bossDeathDown;
            case "left": return bossDeathLeft;
            case "right": return bossDeathRight;
            case "upLeft": return bossDeathUpLeft;
            case "upRight": return bossDeathUpRight;
            case "downLeft": return bossDeathDownLeft;
            case "downRight": return bossDeathDownRight;
            default: return bossDeathDown;
        }
    }
    private BufferedImage[] getWalkFramesForDirection(String dir) {
        switch (dir) {
            case "up": return bossWalkUp;
            case "down": return bossWalkDown;
            case "left": return bossWalkLeft;
            case "right": return bossWalkRight;
            case "upLeft": return bossWalkUpLeft;
            case "upRight": return bossWalkUpRight;
            case "downLeft": return bossWalkDownLeft;
            case "downRight": return bossWalkDownRight;
            default: return bossWalkDown;
        }
    }
    private BufferedImage[] getAttackFramesForDirection(String dir) {
        switch (dir) {
            case "up": return bossAttackUp;
            case "down": return bossAttackDown;
            case "left": return bossAttackLeft;
            case "right": return bossAttackRight;
            case "upLeft": return bossAttackUpLeft;
            case "upRight": return bossAttackUpRight;
            case "downLeft": return bossAttackDownLeft;
            case "downRight": return bossAttackDownRight;
            default: return bossAttackDown;
        }
    }
}