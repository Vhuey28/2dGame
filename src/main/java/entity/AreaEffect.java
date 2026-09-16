 package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class AreaEffect {
    public enum Type { STUN_AND_DAMAGE, HEAL }
    public float x, y;
    public int radius;
    public int duration; // frames
    public Type type;
    public boolean followsPlayer;
    private Player owner;
    private int blinkCounter = 0;

    // Optional looping animation — if null, draw() falls back to the
    // original colored-oval look, so untouched effects keep working as-is.
    private BufferedImage[] animFrames;
    private int frameCounter = 0;
    private int currentFrame = 0;
    private int animSpeed = 5;

    public AreaEffect(float x, float y, int radius, int duration, Type type) {
        this(x, y, radius, duration, type, false, null);
    }

    public AreaEffect(float x, float y, int radius, int duration, Type type, boolean followsPlayer, Player owner) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.duration = duration;
        this.type = type;
        this.followsPlayer = followsPlayer;
        this.owner = owner;
    }

    /** Overload for an animated area effect. Pass the sliced frame array from your effect spritesheet. */
    public AreaEffect(float x, float y, int radius, int duration, Type type, boolean followsPlayer, Player owner, BufferedImage[] animFrames) {
        this(x, y, radius, duration, type, followsPlayer, owner);
        this.animFrames = animFrames;
    }

    public void setAnimSpeed(int ticksPerFrame) {
        this.animSpeed = ticksPerFrame;
    }

    public void update(Player player) {
        if (followsPlayer && owner != null) {
            x = owner.x + owner.gp.tileSize / 2f;
            y = owner.y + owner.gp.tileSize / 2f;
        }
        duration--;
        blinkCounter++;

        if (animFrames != null && animFrames.length > 0) {
            frameCounter++;
            if (frameCounter >= animSpeed) {
                frameCounter = 0;
                currentFrame = (currentFrame + 1) % animFrames.length;
            }
        }
    }

    public boolean isBlinkVisible() {
        return (blinkCounter / 8) % 2 == 0;
    }

    /**
     * Draws the area effect. Call this from GamePanel.paintComponent() instead
     * of the old inline fillOval color logic.
     */
    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int screenX = (int) x - cameraX - radius;
        int screenY = (int) y - cameraY - radius;

        boolean hasValidAnimation = animFrames != null && animFrames.length > 0 && animFrames[currentFrame] != null;

        if (hasValidAnimation) {
            // Always draw the current frame at full opacity — no blink check, no fallback shape, ever.
            g2.drawImage(animFrames[currentFrame], screenX, screenY, radius * 2, radius * 2, null);
            return; // exit early so nothing below can also run
        }

        // Fallback path — only reached when there is no image data at all.
        // Blinking is intentionally confined to this branch only.
        if (type == Type.STUN_AND_DAMAGE) {
            if (followsPlayer) {
                int alpha = isBlinkVisible() ? 140 : 60;
                g2.setColor(new Color(220, 0, 0, alpha));
            } else {
                g2.setColor(new Color(0, 200, 0, 100));
            }
        } else {
            g2.setColor(new Color(200, 200, 0, 100));
        }
        g2.fillOval(screenX, screenY, radius * 2, radius * 2);
    }
}