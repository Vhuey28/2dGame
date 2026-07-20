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
    public enum Type { BASIC, ARCHER, BOSS, TROOP }
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
     // Animation state for archer
    public boolean isArcherAttacking = false;
    public int archerAttackAnimationCounter = 0;
    public int archerAttackAnimationFrame = 0;
    public boolean isArcherDying = false;
    public int archerDeathAnimationCounter = 0;
    public int archerDeathAnimationFrame = 1;
    public boolean archerDeathAnimationComplete = false;
    public boolean archerDeathLootDropped = false;
     // Animation state for troop
    public boolean isTroopAttacking = false;
    public int troopAttackAnimationCounter = 0;
    public int troopAttackAnimationFrame = 1;
    public boolean isTroopDying = false;
    public int troopDeathAnimationCounter = 0;
    public int troopDeathAnimationFrame = 1;
    public boolean troopDeathAnimationComplete = false;
    public boolean troopDeathLootDropped = false;
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

    // Archer walk animation frames (walk_128: 9 frames per direction)
    BufferedImage[] archerWalkUp = new BufferedImage[10]; // index 1-9
    BufferedImage[] archerWalkDown = new BufferedImage[10];
    BufferedImage[] archerWalkLeft = new BufferedImage[10];
    BufferedImage[] archerWalkRight = new BufferedImage[10];
    // Diagonal walk frames (reuse cardinal frames)
    BufferedImage[] archerWalkUpLeft = new BufferedImage[10];
    BufferedImage[] archerWalkUpRight = new BufferedImage[10];
    BufferedImage[] archerWalkDownLeft = new BufferedImage[10];
    BufferedImage[] archerWalkDownRight = new BufferedImage[10];
    // Archer attack/shoot animation frames (standard/shoo, 0-12)
    BufferedImage[] archerAttackUp = new BufferedImage[13];
    BufferedImage[] archerAttackDown = new BufferedImage[13];
    BufferedImage[] archerAttackLeft = new BufferedImage[13];
    BufferedImage[] archerAttackRight = new BufferedImage[13];
    BufferedImage[] archerAttackUpLeft = new BufferedImage[13];
    BufferedImage[] archerAttackUpRight = new BufferedImage[13];
    BufferedImage[] archerAttackDownLeft = new BufferedImage[13];
    BufferedImage[] archerAttackDownRight = new BufferedImage[13];
    // Archer death/hurt animation frames (standard/hurt: 6 frames per direction)
    BufferedImage[] archerDeathUp = new BufferedImage[7];
    BufferedImage[] archerDeathDown = new BufferedImage[7];
    BufferedImage[] archerDeathLeft = new BufferedImage[7];
    BufferedImage[] archerDeathRight = new BufferedImage[7];
    BufferedImage[] archerDeathUpLeft = new BufferedImage[7];
    BufferedImage[] archerDeathUpRight = new BufferedImage[7];
    BufferedImage[] archerDeathDownLeft = new BufferedImage[7];
    BufferedImage[] archerDeathDownRight = new BufferedImage[7];
     // Troop walk animation frames (walk_128: 9 frames per direction)
    BufferedImage[] troopWalkUp = new BufferedImage[10]; // index 1-9
    BufferedImage[] troopWalkDown = new BufferedImage[10];
    BufferedImage[] troopWalkLeft = new BufferedImage[10];
    BufferedImage[] troopWalkRight = new BufferedImage[10];
    // Diagonal walk frames (reuse cardinal frames)
    BufferedImage[] troopWalkUpLeft = new BufferedImage[10];
    BufferedImage[] troopWalkUpRight = new BufferedImage[10];
    BufferedImage[] troopWalkDownLeft = new BufferedImage[10];
    BufferedImage[] troopWalkDownRight = new BufferedImage[10];
    // Troop attack animation frames (custom/slash_128: 8 frames per direction)
    BufferedImage[] troopAttackUp = new BufferedImage[9]; // index 1-8
    BufferedImage[] troopAttackDown = new BufferedImage[9];
    BufferedImage[] troopAttackLeft = new BufferedImage[9];
    BufferedImage[] troopAttackRight = new BufferedImage[9];
    BufferedImage[] troopAttackUpLeft = new BufferedImage[9];
    BufferedImage[] troopAttackUpRight = new BufferedImage[9];
    BufferedImage[] troopAttackDownLeft = new BufferedImage[9];
    BufferedImage[] troopAttackDownRight = new BufferedImage[9];
    // Troop death/hurt animation frames (standard/hurt: 6 frames per direction)
    BufferedImage[] troopDeathUp = new BufferedImage[7];
    BufferedImage[] troopDeathDown = new BufferedImage[7];
    BufferedImage[] troopDeathLeft = new BufferedImage[7];
    BufferedImage[] troopDeathRight = new BufferedImage[7];
    BufferedImage[] troopDeathUpLeft = new BufferedImage[7];
    BufferedImage[] troopDeathUpRight = new BufferedImage[7];
    BufferedImage[] troopDeathDownLeft = new BufferedImage[7];
    BufferedImage[] troopDeathDownRight = new BufferedImage[7];

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
        } else if (type == Type.ARCHER) {
            // Archer needs custom stats
            maxHealth = 30;
            health = maxHealth;
            goldDrop = 5;
            speed = 1.5f;
            damage = 8;
        }else if (type == Type.TROOP) {
            // Troop needs custom stats
            maxHealth = 40;
            health = maxHealth;
            goldDrop = 8;
            speed = 1.5f;
            damage = 10;
        }
        getEnemyImage();
    }
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
                    bossDeathUp[i] =  ImageIO.read(
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
            } else if (type == Type.ARCHER) {
                // Load archer walk animations (walk_128: 9 frames per direction)
                // Walk up
                for (int i = 1; i <= 9; i++) {
                    archerWalkUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/custom/walk_128/up/" + i + ".png"));
                }
                // Walk down
                for (int i = 1; i <= 9; i++) {
                    archerWalkDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/custom/walk_128/down/" + i + ".png"));
                }
                // Walk left
                for (int i = 1; i <= 9; i++) {
                    archerWalkLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/custom/walk_128/left/" + i + ".png"));
                }
                // Walk right
                for (int i = 1; i <= 9; i++) {
                    archerWalkRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/custom/walk_128/right/" + i + ".png"));
                }
                // Diagonal walk frames - horizontal priority only
                copyArrayFrom1(archerWalkLeft, archerWalkUpLeft);
                copyArrayFrom1(archerWalkRight, archerWalkUpRight);
                copyArrayFrom1(archerWalkLeft, archerWalkDownLeft);
                copyArrayFrom1(archerWalkRight, archerWalkDownRight);
                // Load archer attack/shoot animations (ser direction, 0-12)
                // Shoot up
                for (int i = 0; i <= 12; i++) {
                    archerAttackUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/standard/shoot/up/" + (i+1) + ".png"));
                }
                // Shoot down
                for (int i = 0; i <= 12; i++) {
                    archerAttackDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/standard/shoot/down/" + (i+1) + ".png"));
                }
                // Shoot left
                for (int i = 0; i <= 12; i++) {
                    archerAttackLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/standard/shoot/left/" + (i+1) + ".png"));
                }
                // Shoot right
                for (int i = 0; i <= 12; i++) {
                    archerAttackRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/standard/shoot/right/" + (i+1) + ".png"));
                }
                // Diagonal shoot frames - horizontal priority only
                copyArray(archerAttackLeft, archerAttackUpLeft);
                copyArray(archerAttackRight, archerAttackUpRight);
                copyArray(archerAttackLeft, archerAttackDownLeft);
                copyArray(archerAttackRight, archerAttackDownRight);
                // Load archer death animation (standard/on)
                // Only "up" direction files exist; copy to all
                for (int i = 1; i <= 6; i++) {
                    archerDeathUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyArchers/standard/hurt/up/" + i + ".png"));
                }
                copyArrayFrom1(archerDeathUp, archerDeathDown);
                copyArrayFrom1(archerDeathUp, archerDeathLeft);
                copyArrayFrom1(archerDeathUp, archerDeathRight);
                // Diagonal death frames - horizontal priority only
                copyArrayFrom1(archerDeathLeft, archerDeathLeft);
                copyArrayFrom1(archerDeathRight, archerDeathUpRight);
                copyArrayFrom1(archerDeathLeft, archerDeathDownLeft);
                copyArrayFrom1(archerDeathRight, archerDeathDownRight);
                // Set initial image
                image = archerWalkDown[1]; 
            
                } else if (type == Type.TROOP) {
                // Load troop walk animations (walk_128: 9 frames per direction)
                // Walk up
                for (int i = 1; i <= 9; i++) {
                    troopWalkUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/walk_128/up/" + i + ".png"));
                }
                // Walk down
                for (int i = 1; i <= 9; i++) {
                    troopWalkDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/walk_128/down/" + i + ".png"));
                }
                // Walk left
                for (int i = 1; i <= 9; i++) {
                    troopWalkLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/walk_128/left/" + i + ".png"));
                }
                // Walk right
                for (int i = 1; i <= 9; i++) {
                    troopWalkRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/walk_128/right/" + i + ".png"));
                }
                // Diagonal walk frames - horizontal priority only
                copyArrayFrom1(troopWalkLeft, troopWalkUpLeft);
                copyArrayFrom1(troopWalkRight, troopWalkUpRight);
                copyArrayFrom1(troopWalkLeft, troopWalkDownLeft);
                copyArrayFrom1(troopWalkRight, troopWalkDownRight);
                // Load troop attack animations (custom/slash_128: 6 frames per direction, 1-6)
                // Attack up
                for (int i = 1; i <= 6; i++) {
                    troopAttackUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/slash_128/up/" + i + ".png"));
                }
                // Attack down
                for (int i = 1; i <= 6; i++) {
                    troopAttackDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/slash_128/down/" + i + ".png"));
                }
                // Attack left
                for (int i = 1; i <= 6; i++) {
                    troopAttackLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/custom/slash_128/left/" + i + ".png"));
                }
                // Attack right
                for (int i = 1; i <= 6; i++) {
                    troopAttackRight[i] = ImageIO.read(
                 getClass().getResourceAsStream("/player/enemyTroops/custom/slash_128/right/" + i + ".png"));
                }
                // Diagonal attack frames - horizontal priority only
                copyArrayFrom1(troopAttackLeft, troopAttackUpLeft);
                copyArrayFrom1(troopAttackRight, troopAttackUpRight);
                copyArrayFrom1(troopAttackLeft, troopAttackDownLeft);
                copyArrayFrom1(troopAttackRight, troopAttackDownRight);
                // Load troop death animation (standard/hurt: 6 frames per direc
                // Only "up" direction files exist; copy to all
                for (int i = 1; i <= 6; i++) {
                    troopDeathUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/enemyTroops/standard/hurt/up/" + i + ".png"));
                }
                 copyArrayFrom1(troopDeathUp, troopDeathDown);
                copyArrayFrom1(troopDeathUp, troopDeathLeft);
                copyArrayFrom1(troopDeathUp, troopDeathRight);
                // Diagonal death frames - horizontal priority only
                copyArrayFrom1(troopDeathLeft, troopDeathUpLeft);
                copyArrayFrom1(troopDeathRight, troopDeathUpRight);
                copyArrayFrom1(troopDeathLeft, troopDeathDownLeft);
                copyArrayFrom1(troopDeathRight, troopDeathDownRight);
                // Set initial image
                image = troopWalkDown[1];
                image = troopWalkDown[1];
            }
                
                else {
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
        // Handle death animation for archer
        if (type == Type.ARCHER && health <= 0 && !dead && !isArcherDying) {
            dead = true;
            isArcherDying = true;
            archerDeathAnimationCounter = 0;
            archerDeathAnimationFrame = 1;
            archerDeathAnimationComplete = false;
            archerDeathLootDropped = false;
            speed = 0;
            damage = 0;
            shootCooldown = 0;
            isArcherAttacking = false;
        }
        if (type == Type.ARCHER && isArcherDying) {
            archerDeathAnimationCounter++;
            if (archerDeathAnimationCounter > 6) { // Dea
                archerDeathAnimationFrame++;
                archerDeathAnimationCounter = 0;
                if (archerDeathAnimationFrame > 6) {
                    archerDeathAnimationFrame = 6;
                    archerDeathAnimationComplete = true;
                    if (!archerDeathLootDropped) {
                        gp.spawnCoins((int)x + gp.tileSize/2, (int)y + gp.tileSize/2, goldDrop);
                        archerDeathLootDropped = true;
                    }
                    x = -1000;
                    y = -1000;
                }
            }
            return;
        }
         // Handle death animation for troop
        if (type == Type.TROOP && health <= 0 && !dead && !isTroopDying) {
            dead = true;
            isTroopDying = true;
            troopDeathAnimationCounter = 0;
            troopDeathAnimationFrame = 1;
            troopDeathAnimationComplete = false;
            troopDeathLootDropped = false;
            speed = 0;
            damage = 0;
            attackCooldown = 0;
            isTroopAttacking = false;
        }
        if (type == Type.TROOP && isTroopDying) {
            troopDeathAnimationCounter++;
            if (troopDeathAnimationCounter > 6) { // Death animation speed
                troopDeathAnimationFrame++;
                troopDeathAnimationCounter = 0;
                if (troopDeathAnimationFrame > 6) {
                    troopDeathAnimationFrame = 6;
                    troopDeathAnimationComplete = true;
                    if (!troopDeathLootDropped) {
                        gp.spawnCoins((int)x + gp.tileSize/2, (int)y + gp.tileSize/2, goldDrop);
                        troopDeathLootDropped = true;
                    }
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
            if ((type == Type.BOSS || type == Type.ARCHER || type == Type.TROOP) && moved && !isAttacking && !isDying && !isArcherAttacking && !isArcherDying && !isTroopAttacking && !isTroopDying) {
                spriteCounter++;
                if (spriteCounter > 12) { // Animation speed
                    spriteNum++;
                    if (spriteNum > 9) spriteNum = 1;
                    spriteCounter = 0;
                }
            } else if ((type == Type.BOSS || type == Type.ARCHER || type == Type.TROOP) && !isAttacking && !isDying && !isArcherAttacking && !isArcherDying && !isTroopAttacking && !isTroopDying) {
                // Reset to first frame when idle
                spriteNum = 1;
                spriteCounter = 0;
            }
            // Archer enemies fire projectiles toward player
            if (type == Type.ARCHER && shootCooldown == 0 && !isArcherDying) {
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
                     // Trigger shoot animation
                    isArcherAttacking = true;
                    archerAttackAnimationCounter = 0;
                    archerAttackAnimationFrame = 0;
                    // Update direction toward player for shoot animation
                    float ardirX = dx / distance;
                    float ardirY = dy / distance;
                    if (ardirY < -0.707f && ardirX < -0.707f) direction = "upLeft";
                    else if (ardirY < -0.707f && ardirX > 0.707f) direction = "upRight";
                    else if (ardirY > 0.707f && ardirX < -0.707f) direction = "downLeft";
                    else if (ardirY > 0.707f && ardirX > 0.707f) direction = "downRight";
                    else if (Math.abs(dirX) > Math.abs(dirY)) {
                        if (ardirX < 0) direction = "left";
                        else direction = "right";
                    } else {
                        if (ardirY < 0) direction = "up";
                        else direction = "down";
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
         // Troop attack logic - trigger attack animation when in range
        if (type == Type.TROOP && stunTimer == 0 && distance < gp.tileSize + 4 && attackCooldown == 0 && !isTroopDying) {
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
            isTroopAttacking = true;
            troopAttackAnimationCounter = 0;
            troopAttackAnimationFrame = 1; // Start from frame 1 (8 frames:1-8)
            player.health -= damage;
            if (player.health < 0) {
                player.health = 0;
            }
            attackCooldown = 30; // Troop attack cooldown
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
         // Update shoot/attack animation for archer (independent of cooldown)
        if (type == Type.ARCHER && isArcherAttacking && !isArcherDying) {
            archerAttackAnimationCounter++;
            if (archerAttackAnimationCounter > 4) { // Shoot animation speed
                archerAttackAnimationFrame++;
                archerAttackAnimationCounter = 0;
                if (archerAttackAnimationFrame >= 13) { // 13 frames (0-12)
                    archerAttackAnimationFrame = 0;
                    isArcherAttacking = false;
                    // Reset walk animation for smooth transition
                    spriteNum = 1;
                    spriteCounter = 0;
                }
            }
        }
         // Update attack animation for troop (independent of cooldown)
        if (type == Type.TROOP && isTroopAttacking && !isTroopDying) {
            troopAttackAnimationCounter++;
            if (troopAttackAnimationCounter > 4) { // Attack animation speed
                troopAttackAnimationFrame++;
                troopAttackAnimationCounter = 0;
                if (troopAttackAnimationFrame >= 6) { // 6 frames (1-6, using index 1-6)
                    troopAttackAnimationFrame = 1;
                    isTroopAttacking = false;
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
            drawWidth = gp.tileSize +40;  // 3x tile = 144px
            drawHeight = gp.tileSize +40;
            // Adjust size per animation state
            if (isAttacking) {
                // Attack animation: make slightly larger 
                drawWidth = (int)(gp.tileSize*2.5);
                drawHeight = (int)(gp.tileSize*2.5);
            } else if (isDying) {
                // Death animation: make smaller (collapse effect)
                drawWidth = (int)(gp.tileSize );
                drawHeight = (int)(gp.tileSize );
            }
        } else if (type == Type.ARCHER) {
            drawWidth = gp.tileSize+50 ;  // 2x tile = 96px
            drawHeight = gp.tileSize+50 ;
            // Adjust size per animation state
            if (isArcherAttacking) {
                // Attack animation: make slightly larger (draw bow effect)
                drawWidth = (int)(gp.tileSize + 10 );
                drawHeight = (int)(gp.tileSize + 10);
            } else if (isArcherDying) {
                // Death animation: make smaller (collapse effect)
                drawWidth = (int)(gp.tileSize * 1.5);
                drawHeight = (int)(gp.tileSize * 1.5);
            }
        }else if (type == Type.TROOP) {
            drawWidth = gp.tileSize * 2;  // 2x tile = 96px (same as archer)
            drawHeight = gp.tileSize * 2;
            // Adjust size per animation state
            if (isTroopAttacking) {
                // Attack animation: make slightly larger (slash effect)
                drawWidth = (int)(gp.tileSize * 2.5);
                drawHeight = (int)(gp.tileSize * 2.5);
            } else if (isTroopDying) {
                // Death animation: make smaller (collapse effect)
                drawWidth = (int)(gp.tileSize * 1.5);
                drawHeight = (int)(gp.tileSize * 1.5);
            }
        }else {
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
            if (currentFrame != null) {
                g2.drawImage(currentFrame, screenX, screenY, drawWidth, drawHeight, null);
            } else {
                // Fallback to triangle if frame not loaded
                int[] xs = {screenX+drawWidth/2, screenX, screenX+drawWidth};
                int[] ys = {screenY, screenY+drawHeight, screenY+drawHeight};
                g2.setColor(java.awt.Color.orange);
                g2.fillPolygon(xs, ys, 3);
                g2.setColor(java.awt.Color.black);
                g2.drawPolygon(xs, ys, 3);
            }
        }  else if (type == Type.TROOP) {
            if (currentFrame != null) {
                g2.drawImage(currentFrame, screenX, screenY, drawWidth, drawHeight, null);
            } else {
                // Fallback to rectangle if frame not loaded
                g2.setColor(java.awt.Color.cyan);
                g2.fillRect(screenX, screenY, drawWidth, drawHeight);
                g2.setColor(java.awt.Color.black);
                g2.drawRect(screenX, screenY, drawWidth, drawHeight);
            }
        }else if (type == Type.BASIC) {
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
         if (type == Type.BOSS) {
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
         }else if (type == Type.ARCHER) {
            // Death animation takes priority
            if (isArcherDying) {
                return getArcherDeathFramesForDirection(direction)[archerDeathAnimationFrame];
            }
             // Attack/shoot animation
            if (isArcherAttacking) {
                BufferedImage[] attackFrames = getArcherAttackFramesForDirection(direction);
                if (attackFrames != null && archerAttackAnimationFrame < attackFrames.length && attackFrames[archerAttackAnimationFrame] != null) {
                    return attackFrames[archerAttackAnimationFrame];
                }
                // Fallback to walk frame if attack frame not loaded
            }
            // Walk animation
            BufferedImage[] walkFrames = getArcherWalkFramesForDirection(direction);
            if (walkFrames != null && spriteNum < walkFrames.length && walkFrames[spriteNum] != null) {
                return walkFrames[spriteNum];
            }
            // Fallback
            return image;
        }else if (type == Type.TROOP) {
            // Death animation takes priority
            if (isTroopDying) {
                return getTroopDeathFramesForDirection(direction)[troopDeathAnimationFrame];
            }
            // Attack animation
            if (isTroopAttacking) {
                BufferedImage[] attackFrames = getTroopAttackFramesForDirection(direction);
                if (attackFrames != null && troopAttackAnimationFrame < attackFrames.length && attackFrames[troopAttackAnimationFrame] != null) {
                    return attackFrames[troopAttackAnimationFrame];
                }
                // Fallback to walk frame if attack frame not loaded
            }
            // Walk animation
            BufferedImage[] walkFrames = getTroopWalkFramesForDirection(direction);
            if (walkFrames != null && spriteNum < walkFrames.length && walkFrames[spriteNum] != null) {
                return walkFrames[spriteNum];
            }
            // Fallback
            return image;
        }
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
     private BufferedImage[] getArcherWalkFramesForDirection(String dir) {
        switch (dir) {
            case "up": return archerWalkUp;
            case "down": return archerWalkDown;
            case "left": return archerWalkLeft;
            case "right": return archerWalkRight;
            case "upLeft": return archerWalkUpLeft;
            case "upRight": return archerWalkUpRight;
            case "downLeft": return archerWalkDownLeft;
            case "downRight": return archerWalkDownRight;
            default: return archerWalkDown;
        }
    }
    private BufferedImage[] getArcherAttackFramesForDirection(String dir) {
        switch (dir) {
            case "up": return archerAttackUp;
            case "down": return archerAttackDown;
            case "left": return archerAttackLeft;
            case "right": return archerAttackRight;
            case "upLeft": return archerAttackUpLeft;
            case "upRight": return archerAttackUpRight;
            case "downLeft": return archerAttackDownLeft;
            case "downRight": return archerAttackDownRight;
            default: return archerAttackDown;
        }
    }
    private BufferedImage[] getArcherDeathFramesForDirection(String dir){
        switch (dir) {
            case "up": return archerDeathUp;
            case "down": return archerDeathDown;
            case "left": return archerDeathLeft;
            case "right": return archerDeathRight;
            case "upLeft": return archerDeathUpLeft;
            case "upRight": return archerDeathUpRight;
            case "downLeft": return archerDeathDownLeft;
            case "downRight": return archerDeathDownRight;
            default: return archerDeathDown;
        }
    }
     private BufferedImage[] getTroopWalkFramesForDirection(String dir) {
        switch (dir) {
            case "up": return troopWalkUp;
            case "down": return troopWalkDown;
            case "left": return troopWalkLeft;
            case "right": return troopWalkRight;
            case "upLeft": return troopWalkUpLeft;
            case "upRight": return troopWalkUpRight;
            case "downLeft": return troopWalkDownLeft;
            case "downRight": return troopWalkDownRight;
            default: return troopWalkDown;
        }
    }
    private BufferedImage[] getTroopAttackFramesForDirection(String dir) {
        switch (dir) {
            case "up": return troopAttackUp;
            case "down": return troopAttackDown;
            case "left": return troopAttackLeft;
            case "right": return troopAttackRight;
            case "upLeft": return troopAttackUpLeft;
            case "upRight": return troopAttackUpRight;
            case "downLeft": return troopAttackDownLeft;
            case "downRight": return troopAttackDownRight;
            default: return troopAttackDown;
        }
    }
    private BufferedImage[] getTroopDeathFramesForDirection(String dir) {
        switch (dir) {
            case "up": return troopDeathUp;
            case "down": return troopDeathDown;
            case "left": return troopDeathLeft;
            case "right": return troopDeathRight;
            case "upLeft": return troopDeathUpLeft;
            case "upRight": return troopDeathUpRight;
            case "downLeft": return troopDeathDownLeft;
            case "downRight": return troopDeathDownRight;
            default: return troopDeathDown;
        }
    }
}