package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import my2Dgame.GamePanel;
import entity.Player;

public class Troop extends Entity {
    public enum Role { MELEE, ARCHER }
    public Role role = Role.MELEE;
    public enum Mode { FOLLOW, CHARGE, DEFEND, ROAM }
    public Mode mode = Mode.ROAM; // was Mode.FOLLOW — roam is now the default idle state
    public float targetX = 0, targetY = 0;
    public Squad squad;
    public int maxHealth = 30;
    public int health = maxHealth;
    public int shootCooldown = 0;
    public ThreatTable threatTable = new ThreatTable();
    public Morale morale = new Morale();
    public java.util.List<java.awt.Point> currentPath = null;
    public int pathIndex = 0;
    public int pathRecomputeTimer = 0;
    public java.util.List<Projectile> projectiles = new ArrayList<>();

    // Roam behavior fields
    private float roamTargetX = -1, roamTargetY = -1;
    private int roamWaitTimer = 0;
    private static final float ROAM_RADIUS = 150f;
    private float spawnX, spawnY;

    // Animation state for archer
    public boolean isArcherAttacking = false;
    public int archerAttackAnimationCounter = 0;
    public int archerAttackAnimationFrame = 0;
    public boolean isArcherDying = false;
    public int archerDeathAnimationCounter = 0;
    public int archerDeathAnimationFrame = 1;
    public boolean archerDeathAnimationComplete = false;
    public boolean archerDeathLootDropped = false;

    // Animation state for melee troop
    public boolean isMeleeAttacking = false;
    public int meleeAttackAnimationCounter = 0;
    public int meleeAttackAnimationFrame = 1;
    public boolean isMeleeDying = false;
    public int meleeDeathAnimationCounter = 0;
    public int meleeDeathAnimationFrame = 1;
    public boolean meleeDeathAnimationComplete = false;
    public boolean meleeDeathLootDropped = false;

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
    // Archer shoot animation frames (standard/shoot: 13 frames per direction, 1-13)
    BufferedImage[] archerAttackUp = new BufferedImage[14];
    BufferedImage[] archerAttackDown = new BufferedImage[14];
    BufferedImage[] archerAttackLeft = new BufferedImage[14];
    BufferedImage[] archerAttackRight = new BufferedImage[14];
    BufferedImage[] archerAttackUpLeft = new BufferedImage[14];
    BufferedImage[] archerAttackUpRight = new BufferedImage[14];
    BufferedImage[] archerAttackDownLeft = new BufferedImage[14];
    BufferedImage[] archerAttackDownRight = new BufferedImage[14];
    // Archer death/hurt animation frames (standard/hurt: 6 frames per direction)
    BufferedImage[] archerDeathUp = new BufferedImage[7];
    BufferedImage[] archerDeathDown = new BufferedImage[7];
    BufferedImage[] archerDeathLeft = new BufferedImage[7];
    BufferedImage[] archerDeathRight = new BufferedImage[7];
    BufferedImage[] archerDeathUpLeft = new BufferedImage[7];
    BufferedImage[] archerDeathUpRight = new BufferedImage[7];
    BufferedImage[] archerDeathDownLeft = new BufferedImage[7];
    BufferedImage[] archerDeathDownRight = new BufferedImage[7];

    // Melee walk animation frames (standard/walk: 9 frames per direction)
    BufferedImage[] meleeWalkUp = new BufferedImage[10]; // index 1-9
    BufferedImage[] meleeWalkDown = new BufferedImage[10];
    BufferedImage[] meleeWalkLeft = new BufferedImage[10];
    BufferedImage[] meleeWalkRight = new BufferedImage[10];
    // Diagonal walk frames (reuse cardinal frames)
    BufferedImage[] meleeWalkUpLeft = new BufferedImage[10];
    BufferedImage[] meleeWalkUpRight = new BufferedImage[10];
    BufferedImage[] meleeWalkDownLeft = new BufferedImage[10];
    BufferedImage[] meleeWalkDownRight = new BufferedImage[10];
    // Melee attack animation frames (custom/slash_oversize: 6 frames per direction, 1-6)
    BufferedImage[] meleeAttackUp = new BufferedImage[7];
    BufferedImage[] meleeAttackDown = new BufferedImage[7];
    BufferedImage[] meleeAttackLeft = new BufferedImage[7];
    BufferedImage[] meleeAttackRight = new BufferedImage[7];
    BufferedImage[] meleeAttackUpLeft = new BufferedImage[7];
    BufferedImage[] meleeAttackUpRight = new BufferedImage[7];
    BufferedImage[] meleeAttackDownLeft = new BufferedImage[7];
    BufferedImage[] meleeAttackDownRight = new BufferedImage[7];
    // Melee death/hurt animation frames (standard/hurt: 6 frames per direction)
    BufferedImage[] meleeDeathUp = new BufferedImage[7];
    BufferedImage[] meleeDeathDown = new BufferedImage[7];
    BufferedImage[] meleeDeathLeft = new BufferedImage[7];
    BufferedImage[] meleeDeathRight = new BufferedImage[7];
    BufferedImage[] meleeDeathUpLeft = new BufferedImage[7];
    BufferedImage[] meleeDeathUpRight = new BufferedImage[7];
    BufferedImage[] meleeDeathDownLeft = new BufferedImage[7];
    BufferedImage[] meleeDeathDownRight = new BufferedImage[7];

    public Troop(GamePanel gp, float x, float y, Role role) {
        this.gp = gp;
        this.x = x;
        this.y = y;
        this.spawnX = x;  // anchor for roaming
        this.spawnY = y;
        this.role = role;
        this.speed = 3f;
        direction = "down";
        loadTroopImages();
    }

    public void setSpawnAnchor(float x, float y) {
        this.spawnX = x;
        this.spawnY = y;
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

    private float getPlayerFacingAngle() {
        //Player player = new Player(gp, null);
        switch (gp.player.direction) {
            case "right": return 0f;
            case "left": return (float) Math.PI;
            case "up": return (float) (-Math.PI / 2);
            case "down": return (float) (Math.PI / 2);
            case "upRight": return (float) (-Math.PI / 4);
            case "upLeft": return (float) (-3 * Math.PI / 4);
            case "downRight": return (float) (Math.PI / 4);
            case "downLeft": return (float) (3 * Math.PI / 4);
            default: return 0f;
         }  
    }

    public void update() {
        // Handle archer attack animation
        if (role == Role.ARCHER && isArcherAttacking && !isArcherDying) {
            archerAttackAnimationCounter++;
            if (archerAttackAnimationCounter > 4) {
                archerAttackAnimationFrame++;
                archerAttackAnimationCounter = 0;
                if (archerAttackAnimationFrame >= 13) {
                    archerAttackAnimationFrame = 0;
                    isArcherAttacking = false;
                    spriteNum = 1;
                    spriteCounter = 0;
                }
            }
        }
        // Handle melee attack animation
        if (role == Role.MELEE && isMeleeAttacking && !isMeleeDying) {
            meleeAttackAnimationCounter++;
            if (meleeAttackAnimationCounter > 4) {
                meleeAttackAnimationFrame++;
                meleeAttackAnimationCounter = 0;
                if (meleeAttackAnimationFrame >= 6) {
                    meleeAttackAnimationFrame = 1;
                    isMeleeAttacking = false;
                    spriteNum = 1;
                    spriteCounter = 0;
                }
            }
        }

        Player player = gp.player;
        float dx = 0, dy = 0;
        if (mode == Mode.FOLLOW || mode == Mode.DEFEND) {
            if (squad != null) {
                float facingAngle = getPlayerFacingAngle();
                int slotIndex = squad.members.indexOf(this);
                if (slotIndex < 0) slotIndex = 0;
                java.awt.Point slot = squad.formation.getSlotPosition(slotIndex, player.x, player.y, facingAngle);
                dx = slot.x - x;
                dy = slot.y - y;
            } else {
                dx = player.x - x - 100; // fallback if squad wasn't assigned
                dy = player.y - y;
            }
        } else if (mode == Mode.CHARGE) {
            // Periodic path recompute for moving targets
            // pathRecomputeTimer--;
            // if (currentPath == null || pathIndex >= currentPath.size() || pathRecomputeTimer <= 0) {
            //     currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)targetX, (int)targetY);
            //     pathIndex = 0;
            //     pathRecomputeTimer = 45; // ~0.75s at 60fps
            // }
            // if (currentPath != null && pathIndex < currentPath.size()) {
            //     java.awt.Point waypoint = currentPath.get(pathIndex);
            //     dx = waypoint.x - x;
            //     dy = waypoint.y - y;
            //     if (Math.abs(dx) < 4 && Math.abs(dy) < 4) pathIndex++;
            // } else {
            //     dx = targetX - x;
            //     dy = targetY - y;
            // }
                        pathRecomputeTimer--;
                boolean needsRecompute = currentPath == null
                    || pathIndex >= currentPath.size()
                    || pathRecomputeTimer <= 0;

                if (needsRecompute) {
                    currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y,
            (int)targetX, (int)targetY);
                    pathIndex = 0;
                    pathRecomputeTimer = 45;
                }

                if (currentPath != null && pathIndex < currentPath.size()) {
                    java.awt.Point waypoint = currentPath.get(pathIndex);
                    dx = waypoint.x - x;
                    dy = waypoint.y - y;
                    if (Math.abs(dx) < 4 && Math.abs(dy) < 4) pathIndex++;
                } else {
                    dx = targetX - x;
                    dy = targetY - y;
                }
  
        } else if (mode == Mode.ROAM) {
            roamWaitTimer--;
            boolean needNewTarget = roamTargetX < 0 || roamWaitTimer <= 0;
            if (!needNewTarget) {
                float rdx = roamTargetX - x, rdy = roamTargetY - y;
                if (Math.sqrt(rdx * rdx + rdy * rdy) < 4f) needNewTarget = true;
            }
            if (needNewTarget) {
                double angle = gp.random.nextDouble() * Math.PI * 2;
                roamTargetX = spawnX + (float)(Math.cos(angle) * ROAM_RADIUS * gp.random.nextDouble());
                roamTargetY = spawnY + (float)(Math.sin(angle) * ROAM_RADIUS * gp.random.nextDouble());
                currentPath = AStarPathfinder.findPath(gp, (int)x, (int)y, (int)roamTargetX, (int)roamTargetY);
                pathIndex = 0;
                roamWaitTimer = 120 + gp.random.nextInt(120); // pause 2-4s at each stop
            } else if (currentPath != null && pathIndex < currentPath.size()) {
                java.awt.Point waypoint = currentPath.get(pathIndex);
                dx = waypoint.x - x;
                dy = waypoint.y - y;
                if (Math.abs(dx) < 4 && Math.abs(dy) < 4) pathIndex++;
            }
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
        
        // Walk animation for both roles when moving
        if (moved && !isArcherAttacking && !isArcherDying && !isMeleeAttacking && !isMeleeDying) {
            spriteCounter++;
            if (spriteCounter > 12) {
                spriteNum++;
                if (spriteNum > 9) spriteNum = 1;
                spriteCounter = 0;
            }
        } else if (!isArcherAttacking && !isArcherDying && !isMeleeAttacking && !isMeleeDying) {
            spriteNum = 1;
            spriteCounter = 0;
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
                    // Trigger shoot animation
                    isArcherAttacking = true;
                    archerAttackAnimationCounter = 0;
                    archerAttackAnimationFrame = 0;
                    // Update direction toward target for shoot animation
                    float dirX = tx;
                    float dirY = ty;
                    if (dirY < -0.707f && dirX < -0.707f) direction = "upLeft";
                    else if (dirY < -0.707f && dirX > 0.707f) direction = "upRight";
                    else if (dirY > 0.707f && dirX < -0.707f) direction = "downLeft";
                    else if (dirY > 0.707f && dirX > 0.707f) direction = "downRight";
                    else if (Math.abs(dirX) > Math.abs(dirY)) {
                        if (dirX < 0) direction = "left";
                        else direction = "right";
                    } else {
                        if (dirY < 0) direction = "up";
                        else direction = "down";
                    }
                }
            }
        } else if (role == Role.MELEE) {
            // Melee attack logic - attack when in range of enemy
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
            // Attack cooldown stored in attackCooldown
            if (target != null && shortest < gp.tileSize + 4 && !isMeleeAttacking && !isMeleeDying) {
                // Face the target
                float dirX = target.x + gp.tileSize/2f - (x + gp.tileSize/2f);
                float dirY = target.y + gp.tileSize/2f - (y + gp.tileSize/2f);
                if (dirY < -0.707f && dirX < -0.707f) direction = "upLeft";
                else if (dirY < -0.707f && dirX > 0.707f) direction = "upRight";
                else if (dirY > 0.707f && dirX < -0.707f) direction = "downLeft";
                else if (dirY > 0.707f && dirX > 0.707f) direction = "downRight";
                else if (Math.abs(dirX) > Math.abs(dirY)) {
                    if (dirX < 0) direction = "left";
                    else direction = "right";
                } else {
                    if (dirY < 0) direction = "up";
                    else direction = "down";
                }
                isMeleeAttacking = true;
                meleeAttackAnimationCounter = 0;
                meleeAttackAnimationFrame = 1;
                // Deal damage to enemy
                target.health -= 10;
                if (target.health < 0) target.health = 0;
                target.showHealthCounter = 60;
                target.threatTable.addThreat(this, 10);   // <-- add this, makes the enemy retarget this troop
            }
        }

        // Handle death animation for archer
        if (role == Role.ARCHER && health <= 0 && !isArcherDying) {
            isArcherDying = true;
            archerDeathAnimationCounter = 0;
            archerDeathAnimationFrame = 1;
            archerDeathAnimationComplete = false;
            archerDeathLootDropped = false;
            speed = 0;
            shootCooldown = 0;
            isArcherAttacking = false;
        }
        if (role == Role.ARCHER && isArcherDying) {
            archerDeathAnimationCounter++;
            if (archerDeathAnimationCounter > 6) {
                archerDeathAnimationFrame++;
                archerDeathAnimationCounter = 0;
                if (archerDeathAnimationFrame > 6) {
                    archerDeathAnimationFrame = 6;
                    archerDeathAnimationComplete = true;
                    if (!archerDeathLootDropped) {
                        gp.spawnCoins((int)x + gp.tileSize/2, (int)y + gp.tileSize/2, 5);
                        archerDeathLootDropped = true;
                    }
                    x = -1000;
                    y = -1000;
                }
            }
            return;
        }

        // Handle death animation for melee
        if (role == Role.MELEE && health <= 0 && !isMeleeDying) {
            isMeleeDying = true;
            meleeDeathAnimationCounter = 0;
            meleeDeathAnimationFrame = 1;
            meleeDeathAnimationComplete = false;
            meleeDeathLootDropped = false;
            speed = 0;
            isMeleeAttacking = false;
        }
        if (role == Role.MELEE && isMeleeDying) {
            meleeDeathAnimationCounter++;
            if (meleeDeathAnimationCounter > 6) {
                meleeDeathAnimationFrame++;
                meleeDeathAnimationCounter = 0;
                if (meleeDeathAnimationFrame > 6) {
                    meleeDeathAnimationFrame = 6;
                    meleeDeathAnimationComplete = true;
                    if (!meleeDeathLootDropped) {
                        gp.spawnCoins((int)x + gp.tileSize/2, (int)y + gp.tileSize/2, 5);
                        meleeDeathLootDropped = true;
                    }
                    x = -1000;
                    y = -1000;
                }
            }
            return;
        }

        Iterator<Projectile> projIterator = projectiles.iterator();
        while (projIterator.hasNext()) {
            Projectile p = projIterator.next();
            p.update();
            threatTable.decay();
            morale.update();
            if (p.life <= 0) projIterator.remove();
        }
    }

    private void loadTroopImages() {
        try {
            if (role == Role.ARCHER) {
                // Load archer walk animations (walk_128: 9 frames per direction)
                for (int i = 1; i <= 9; i++) {
                    archerWalkUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/custom/walk_128/up/" + i + ".png"));
                }
                for (int i = 1; i <= 9; i++) {
                    archerWalkDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/custom/walk_128/down/" + i + ".png"));
                }
                for (int i = 1; i <= 9; i++) {
                    archerWalkLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/custom/walk_128/left/" + i + ".png"));
                }
                for (int i = 1; i <= 9; i++) {
                    archerWalkRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/custom/walk_128/right/" + i + ".png"));
                }
                // Diagonal walk frames - horizontal priority only
                copyArrayFrom1(archerWalkLeft, archerWalkUpLeft);
                copyArrayFrom1(archerWalkRight, archerWalkUpRight);
                copyArrayFrom1(archerWalkLeft, archerWalkDownLeft);
                copyArrayFrom1(archerWalkRight, archerWalkDownRight);

                // Load archer shoot animations (standard/shoot: 13 frames per direction, 1-13)
                for (int i = 1; i <= 13; i++) {
                    archerAttackUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/standard/shoot/up/" + i + ".png"));
                }
                for (int i = 1; i <= 13; i++) {
                    archerAttackDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/standard/shoot/down/" + i + ".png"));
                }
                for (int i = 1; i <= 13; i++) {
                    archerAttackLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/standard/shoot/left/" + i + ".png"));
                }
                for (int i = 1; i <= 13; i++) {
                    archerAttackRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/standard/shoot/right/" + i + ".png"));
                }
                // Diagonal shoot frames - horizontal priority only
                copyArrayFrom1(archerAttackLeft, archerAttackUpLeft);
                copyArrayFrom1(archerAttackRight, archerAttackUpRight);
                copyArrayFrom1(archerAttackLeft, archerAttackDownLeft);
                copyArrayFrom1(archerAttackRight, archerAttackDownRight);

                // Load archer death animation (standard/hurt: 6 frames per direction)
                // Only "up" direction files exist; copy to all other directions
                for (int i = 1; i <= 6; i++) {
                    archerDeathUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyArchers/standard/hurt/up/" + i + ".png"));
                }
                copyArrayFrom1(archerDeathUp, archerDeathDown);
                copyArrayFrom1(archerDeathUp, archerDeathLeft);
                copyArrayFrom1(archerDeathUp, archerDeathRight);
                // Diagonal death frames - horizontal priority only
                copyArrayFrom1(archerDeathLeft, archerDeathUpLeft);
                copyArrayFrom1(archerDeathRight, archerDeathUpRight);
                copyArrayFrom1(archerDeathLeft, archerDeathDownLeft);
                copyArrayFrom1(archerDeathRight, archerDeathDownRight);
            } else if (role == Role.MELEE) {
                // Load melee walk animations (standard/walk: 9 frames per direction)
                for (int i = 1; i <= 9; i++) {
                    meleeWalkUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/standard/walk/up/" + i + ".png"));
                }
                for (int i = 1; i <= 9; i++) {
                    meleeWalkDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/standard/walk/down/" + i + ".png"));
                }
                for (int i = 1; i <= 9; i++) {
                    meleeWalkLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/standard/walk/left/" + i + ".png"));
                }
                for (int i = 1; i <= 9; i++) {
                    meleeWalkRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/standard/walk/right/" + i + ".png"));
                }
                // Diagonal walk frames - horizontal priority only
                copyArrayFrom1(meleeWalkLeft, meleeWalkUpLeft);
                copyArrayFrom1(meleeWalkRight, meleeWalkUpRight);
                copyArrayFrom1(meleeWalkLeft, meleeWalkDownLeft);
                copyArrayFrom1(meleeWalkRight, meleeWalkDownRight);

                // Load melee attack animations (custom/slash_oversize: 6 frames per direction, 1-6)
                for (int i = 1; i <= 6; i++) {
                    meleeAttackUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/custom/slash_oversize/up/" + i + ".png"));
                }
                for (int i = 1; i <= 6; i++) {
                    meleeAttackDown[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/custom/slash_oversize/down/" + i + ".png"));
                }
                for (int i = 1; i <= 6; i++) {
                    meleeAttackLeft[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/custom/slash_oversize/left/" + i + ".png"));
                }
                for (int i = 1; i <= 6; i++) {
                    meleeAttackRight[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/custom/slash_oversize/right/" + i + ".png"));
                }
                // Diagonal attack frames - horizontal priority only
                copyArrayFrom1(meleeAttackLeft, meleeAttackUpLeft);
                copyArrayFrom1(meleeAttackRight, meleeAttackUpRight);
                copyArrayFrom1(meleeAttackLeft, meleeAttackDownLeft);
                copyArrayFrom1(meleeAttackRight, meleeAttackDownRight);

                // Load melee death animation (standard/hurt: 6 frames per direction)
                // Only "up" direction files exist; copy to all other directions
                for (int i = 1; i <= 6; i++) {
                    meleeDeathUp[i] = ImageIO.read(
                        getClass().getResourceAsStream("/player/allyFootTroop/standard/hurt/up/" + i + ".png"));
                }
                copyArrayFrom1(meleeDeathUp, meleeDeathDown);
                copyArrayFrom1(meleeDeathUp, meleeDeathLeft);
                copyArrayFrom1(meleeDeathUp, meleeDeathRight);
                // Diagonal death frames - horizontal priority only
                copyArrayFrom1(meleeDeathLeft, meleeDeathUpLeft);
                copyArrayFrom1(meleeDeathRight, meleeDeathUpRight);
                copyArrayFrom1(meleeDeathLeft, meleeDeathDownLeft);
                copyArrayFrom1(meleeDeathRight, meleeDeathDownRight);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    

    // Copy array starting from index 1 (since all animation arrays use 1-based indexing)
    private void copyArrayFrom1(BufferedImage[] src, BufferedImage[] dest) {
        if (src != null && dest != null) {
            int len = Math.min(src.length, dest.length);
            for (int i = 1; i < len; i++) {
                dest[i] = src[i];
            }
        }
    }

    private BufferedImage getCurrentTroopAnimationFrame() {
        if (role == Role.ARCHER) {
            if (isArcherDying) {
                return getArcherDeathFramesForDirection(direction)[archerDeathAnimationFrame];
            }
            if (isArcherAttacking) {
                BufferedImage[] attackFrames = getArcherAttackFramesForDirection(direction);
                if (attackFrames != null && archerAttackAnimationFrame < attackFrames.length && attackFrames[archerAttackAnimationFrame] != null) {
                    return attackFrames[archerAttackAnimationFrame];
                }
            }
            // Walk animation
            BufferedImage[] walkFrames = getArcherWalkFramesForDirection(direction);
            if (walkFrames != null && spriteNum < walkFrames.length && walkFrames[spriteNum] != null) {
                return walkFrames[spriteNum];
            }
        } else if (role == Role.MELEE) {
            if (isMeleeDying) {
                return getMeleeDeathFramesForDirection(direction)[meleeDeathAnimationFrame];
            }
            if (isMeleeAttacking) {
                BufferedImage[] attackFrames = getMeleeAttackFramesForDirection(direction);
                if (attackFrames != null && meleeAttackAnimationFrame < attackFrames.length && attackFrames[meleeAttackAnimationFrame] != null) {
                    return attackFrames[meleeAttackAnimationFrame];
                }
            }
            // Walk animation
            BufferedImage[] walkFrames = getMeleeWalkFramesForDirection(direction);
            if (walkFrames != null && spriteNum < walkFrames.length && walkFrames[spriteNum] != null) {
                return walkFrames[spriteNum];
            }
        }
        return null;
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

    private BufferedImage[] getArcherDeathFramesForDirection(String dir) {
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

    private BufferedImage[] getMeleeWalkFramesForDirection(String dir) {
        switch (dir) {
            case "up": return meleeWalkUp;
            case "down": return meleeWalkDown;
            case "left": return meleeWalkLeft;
            case "right": return meleeWalkRight;
            case "upLeft": return meleeWalkUpLeft;
            case "upRight": return meleeWalkUpRight;
            case "downLeft": return meleeWalkDownLeft;
            case "downRight": return meleeWalkDownRight;
            default: return meleeWalkDown;
        }
    }

    private BufferedImage[] getMeleeAttackFramesForDirection(String dir) {
        switch (dir) {
            case "up": return meleeAttackUp;
            case "down": return meleeAttackDown;
            case "left": return meleeAttackLeft;
            case "right": return meleeAttackRight;
            case "upLeft": return meleeAttackUpLeft;
            case "upRight": return meleeAttackUpRight;
            case "downLeft": return meleeAttackDownLeft;
            case "downRight": return meleeAttackDownRight;
            default: return meleeAttackDown;
        }
    }

    private BufferedImage[] getMeleeDeathFramesForDirection(String dir) {
        switch (dir) {
            case "up": return meleeDeathUp;
            case "down": return meleeDeathDown;
            case "left": return meleeDeathLeft;
            case "right": return meleeDeathRight;
            case "upLeft": return meleeDeathUpLeft;
            case "upRight": return meleeDeathUpRight;
            case "downLeft": return meleeDeathDownLeft;
            case "downRight": return meleeDeathDownRight;
            default: return meleeDeathDown;
        }
    }

    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int sx = (int)x - cameraX;
        int sy = (int)y - cameraY;

        if (role == Role.ARCHER) {
            int drawWidth = gp.tileSize * 2;  // 2x tile = 96px
            int drawHeight = gp.tileSize * 2;
            // Adjust size per animation state
            if (isArcherAttacking) {
                drawWidth = (int)(gp.tileSize + 10);
                drawHeight = (int)(gp.tileSize + 10);
            } else if (isArcherDying) {
                drawWidth = (int)(gp.tileSize * 1.5);
                drawHeight = (int)(gp.tileSize * 1.5);
            }
            int screenX = sx + (gp.tileSize - drawWidth) / 2;
            int screenY = sy + (gp.tileSize - drawHeight) / 2;

            BufferedImage currentFrame = getCurrentTroopAnimationFrame();
            if (currentFrame != null) {
                g2.drawImage(currentFrame, screenX, screenY, drawWidth, drawHeight, null);
            } else {
                // Fallback to triangle if frame not loaded
                int[] xs = {sx + gp.tileSize/2, sx, sx + gp.tileSize};
                int[] ys = {sy, sy + gp.tileSize, sy + gp.tileSize};
                g2.setColor(Color.blue);
                g2.fillPolygon(xs, ys, 3);
            }
        } else if (role == Role.MELEE) {
            int drawWidth = gp.tileSize +10;  // 2x tile = 96px
            int drawHeight = gp.tileSize +10;
            // Adjust size per animation state
            if (isMeleeAttacking) {
                drawWidth = (int)(gp.tileSize * 2.5);
                drawHeight = (int)(gp.tileSize * 2.5);
            } else if (isMeleeDying) {
                drawWidth = (int)(gp.tileSize * 1.5);
                drawHeight = (int)(gp.tileSize * 1.5);
            }
            int screenX = sx + (gp.tileSize - drawWidth) / 2;
            int screenY = sy + (gp.tileSize - drawHeight) / 2;

            BufferedImage currentFrame = getCurrentTroopAnimationFrame();
            if (currentFrame != null) {
                g2.drawImage(currentFrame, screenX, screenY, drawWidth, drawHeight, null);
            } else {
                // Fallback to rectangle if frame not loaded
                g2.setColor(Color.blue);
                g2.fillRect(sx, sy, gp.tileSize, gp.tileSize);
                g2.setColor(Color.white);
                g2.drawRect(sx, sy, gp.tileSize, gp.tileSize);
            }
        }

        // Draw projectiles
        for (Projectile p : projectiles) {
            int px = (int)p.x - cameraX - p.size/2;
            int py = (int)p.y - cameraY - p.size/2;
            g2.setColor(new Color(p.color.getRGB()));
            int[] xs = {px, px + p.size, px + p.size/2};
            int[] ys = {py + p.size, py + p.size, py};
            g2.fillPolygon(xs, ys, 3);
        }

        // Health bar for both roles
        if ((health < maxHealth || isArcherDying || isMeleeDying)) {
            int barWidth = (role == Role.ARCHER) ? gp.tileSize * 2 : gp.tileSize * 2;
            int drawWidth = (role == Role.ARCHER) ? gp.tileSize * 2 : gp.tileSize * 2;
            int barX = sx + (gp.tileSize - drawWidth) / 2;
            if (role == Role.ARCHER) barX = sx + (gp.tileSize - barWidth) / 2;
            int barY = sy - 8;
            int filled = (int)((double)health / maxHealth * barWidth);
            g2.setColor(Color.red);
            g2.fillRect(barX, barY, filled, 6);
            g2.setColor(Color.white);
            g2.drawRect(barX, barY, barWidth, 6);
        }
    }
}