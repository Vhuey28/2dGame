package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import my2Dgame.GamePanel;

/** Runtime visuals and gameplay behavior for warrior and mage class abilities. */
public final class HeroAbilityEffect {
    public enum Type { WARRIOR_WAVE, WARRIOR_AURA, WARRIOR_PULL, MAGE_STORM, MAGE_ORB, PORTAL }

    private final GamePanel gp;
    private final Hero owner;
    private final Type type;
    private final BufferedImage[] frames;
    private final Set<Enemy> hitEnemies = new HashSet<>();
    private Enemy target;
    private Enemy previousTarget;
    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private int radius;
    private int duration;
    private int frameCounter;
    private int frameIndex;
    private int hitDelay;

    private HeroAbilityEffect(GamePanel gp, Hero owner, Type type, float x, float y,
            int radius, int duration, BufferedImage[] frames) {
        this.gp = gp;
        this.owner = owner;
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.duration = duration;
        this.frames = frames;
    }

    public static HeroAbilityEffect warriorWave(GamePanel gp, Hero owner) {
        HeroAbilityEffect effect = new HeroAbilityEffect(gp, owner, Type.WARRIOR_WAVE,
                centerX(gp, owner), centerY(gp, owner), gp.tileSize, 55,
                load("/effects/fire_effect_sliced_tiles/tiles/group2/r11_c07.png"));
        float[] facing = facingVector(owner.direction);
        effect.velocityX = facing[0] * 11f;
        effect.velocityY = facing[1] * 11f;
        return effect;
    }

    public static HeroAbilityEffect warriorAura(GamePanel gp, Hero owner) {
        return new HeroAbilityEffect(gp, owner, Type.WARRIOR_AURA,
                centerX(gp, owner), centerY(gp, owner), gp.tileSize * 3, 360,
                load(
                    "/effects/fire_effect_sliced_tiles/tiles/group3/r05_c10.png",
                    "/effects/fire_effect_sliced_tiles/tiles/group3/r05_c11.png",
                    "/effects/fire_effect_sliced_tiles/tiles/group3/r05_c12.png"));
    }

    public static HeroAbilityEffect warriorPull(GamePanel gp, Hero owner) {
        return new HeroAbilityEffect(gp, owner, Type.WARRIOR_PULL,
                centerX(gp, owner), centerY(gp, owner), gp.tileSize * 4, 150,
                load(
                    "/effects/fire_effect_sliced_tiles/tiles/group3/r06_c12.png",
                    "/effects/fire_effect_sliced_tiles/tiles/group3/r06_c11.png",
                    "/effects/fire_effect_sliced_tiles/tiles/group3/r06_c10.png"));
    }

    public static HeroAbilityEffect mageStorm(GamePanel gp, Hero owner, Entity selectedTarget) {
        float sx = selectedTarget == null ? centerX(gp, owner) : selectedTarget.x + gp.tileSize / 2f;
        float sy = selectedTarget == null ? centerY(gp, owner) : selectedTarget.y + gp.tileSize / 2f;
        return new HeroAbilityEffect(gp, owner, Type.MAGE_STORM, sx, sy,
                gp.tileSize * 3, 300,
                load(
                    "/effects/purple_effect_sliced_tiles/tiles/group4/r08_c14.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group4/r08_c15.png"));
    }

    public static HeroAbilityEffect mageOrb(GamePanel gp, Hero owner, Entity selectedTarget) {
        HeroAbilityEffect effect = new HeroAbilityEffect(gp, owner, Type.MAGE_ORB,
                centerX(gp, owner), centerY(gp, owner), gp.tileSize, gp.getFramesPerSecond() * 10,
                load(
                    "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c19.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c20.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c21.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group5/r09_c22.png"));
        if (selectedTarget instanceof Enemy enemy && effect.valid(enemy)) effect.target = enemy;
        return effect;
    }

    public static HeroAbilityEffect portal(GamePanel gp, float x, float y, int duration) {
        return new HeroAbilityEffect(gp, null, Type.PORTAL, x, y, gp.tileSize, duration,
                load(
                    "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c14.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c15.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c16.png",
                    "/effects/purple_effect_sliced_tiles/tiles/group4/r04_c17.png"));
    }

    public void update() {
        duration--;
        frameCounter++;
        if (frames.length > 0 && frameCounter >= 6) {
            frameCounter = 0;
            frameIndex = (frameIndex + 1) % frames.length;
        }
        switch (type) {
            case WARRIOR_WAVE -> updateWave();
            case WARRIOR_AURA -> updateAura();
            case WARRIOR_PULL -> updatePull();
            case MAGE_STORM -> updateStorm();
            case MAGE_ORB -> updateOrb();
            case PORTAL -> { }
        }
    }

    private void updateWave() {
        x += velocityX;
        y += velocityY;
        for (Enemy enemy : gp.enemies) {
            if (!valid(enemy) || hitEnemies.contains(enemy) || distanceTo(enemy) > radius) continue;
            damage(enemy, owner.abilityDamage(14));
            hitEnemies.add(enemy);
        }
    }

    private void updateAura() {
        if (inside(centerX(gp, gp.player), centerY(gp, gp.player))) {
            gp.player.applyAbilityAura(3, 6, 4);
        }
        for (Troop troop : gp.troops) {
            if (troop.health > 0 && inside(centerX(gp, troop), centerY(gp, troop))) {
                troop.applyAbilityAura(3, 6, 4);
            }
        }
        for (Hero hero : gp.heroes) {
            if (hero.isRecruited && hero.health > 0 && inside(centerX(gp, hero), centerY(gp, hero))) {
                hero.applyAbilityAura(3, 6, 4);
            }
        }
    }

    private void updatePull() {
        for (Enemy enemy : gp.enemies) {
            if (!valid(enemy)) continue;
            float ex = centerX(gp, enemy);
            float ey = centerY(gp, enemy);
            float dx = x - ex;
            float dy = y - ey;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= 1f || distance > radius) continue;
            enemy.x += dx / distance * Math.min(4f, distance);
            enemy.y += dy / distance * Math.min(4f, distance);
            if (duration % 30 == 0) damage(enemy, owner.abilityDamage(4));
        }
    }

    private void updateStorm() {
        if (duration % 15 != 0) return;
        for (Enemy enemy : gp.enemies) {
            if (valid(enemy) && distanceTo(enemy) <= radius) damage(enemy, owner.abilityDamage(7));
        }
    }

    private void updateOrb() {
        if (hitDelay > 0) hitDelay--;
        if (!valid(target)) target = findBounceTarget(previousTarget);
        if (target == null) return;
        float tx = centerX(gp, target) - x;
        float ty = centerY(gp, target) - y;
        float distance = (float) Math.sqrt(tx * tx + ty * ty);
        if (distance <= 14f && hitDelay == 0) {
            damage(target, owner.abilityDamage(10));
            previousTarget = target;
            target = findBounceTarget(previousTarget);
            hitDelay = 8;
            return;
        }
        if (distance > 0f) {
            float step = Math.min(13f, distance);
            x += tx / distance * step;
            y += ty / distance * step;
        }
    }

    private Enemy findBounceTarget(Enemy excluded) {
        Enemy best = null;
        float bestDistance = gp.tileSize * 12f;
        for (Enemy enemy : gp.enemies) {
            if (!valid(enemy) || enemy == excluded) continue;
            float dx = centerX(gp, enemy) - x;
            float dy = centerY(gp, enemy) - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = enemy;
            }
        }
        if (best == null && valid(excluded)) best = excluded;
        return best;
    }

    private boolean valid(Enemy enemy) {
        return enemy != null && !enemy.dead && enemy.health > 0;
    }

    private float distanceTo(Entity entity) {
        float dx = centerX(gp, entity) - x;
        float dy = centerY(gp, entity) - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean inside(float otherX, float otherY) {
        float dx = otherX - x;
        float dy = otherY - y;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void damage(Enemy enemy, int amount) {
        enemy.health = Math.max(0, enemy.health - amount);
        enemy.showHealthCounter = 60;
        enemy.threatTable.addThreat(owner, amount);
    }

    public boolean isExpired() {
        return duration <= 0;
    }

    public boolean isPortal() {
        return type == Type.PORTAL;
    }

    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int drawRadius = type == Type.MAGE_ORB || type == Type.WARRIOR_WAVE ? gp.tileSize : radius;
        int drawX = Math.round(x) - cameraX - drawRadius;
        int drawY = Math.round(y) - cameraY - drawRadius;
        if (type == Type.WARRIOR_AURA || type == Type.WARRIOR_PULL || type == Type.MAGE_STORM) {
            Color groundColor = type == Type.MAGE_STORM
                    ? new Color(105, 45, 180, 65) : new Color(230, 105, 25, 55);
            g2.setColor(groundColor);
            g2.fillOval(drawX, drawY, drawRadius * 2, drawRadius * 2);
            g2.setColor(new Color(255, 220, 150, 130));
            g2.drawOval(drawX, drawY, drawRadius * 2, drawRadius * 2);
        }
        if (frames.length == 0 || frames[frameIndex] == null) return;
        g2.drawImage(frames[frameIndex], drawX, drawY, drawRadius * 2, drawRadius * 2, null);
    }

    private static float centerX(GamePanel gp, Entity entity) {
        return entity.x + gp.tileSize / 2f;
    }

    private static float centerY(GamePanel gp, Entity entity) {
        return entity.y + gp.tileSize / 2f;
    }

    private static float[] facingVector(String direction) {
        float x = 0f;
        float y = 0f;
        if (direction.contains("Left") || "left".equals(direction)) x = -1f;
        if (direction.contains("Right") || "right".equals(direction)) x = 1f;
        if (direction.startsWith("up")) y = -1f;
        if (direction.startsWith("down")) y = 1f;
        if (x == 0f && y == 0f) y = 1f;
        if (x != 0f && y != 0f) {
            x *= 0.70710678f;
            y *= 0.70710678f;
        }
        return new float[] {x, y};
    }

    private static BufferedImage[] load(String... paths) {
        BufferedImage[] images = new BufferedImage[paths.length];
        for (int i = 0; i < paths.length; i++) {
            try (InputStream stream = HeroAbilityEffect.class.getResourceAsStream(paths[i])) {
                if (stream == null) throw new IOException("Missing ability effect " + paths[i]);
                images[i] = ImageIO.read(stream);
            } catch (IOException error) {
                System.err.println(error.getMessage());
            }
        }
        return images;
    }
}
