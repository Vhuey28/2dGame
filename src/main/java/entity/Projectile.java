package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;

public class Projectile {
    public float x, y;
    public float dx, dy;
    public int speed;
    public int life = 60;
    public int size = 8;
    public Color color;

    // Optional animated sprite — if null, draw() falls back to the original
    // colored-triangle shape, so existing projectiles that never call the
    // animated constructor keep working exactly as before.
    private BufferedImage[] animFrames;
    private int frameCounter = 0;
    private int currentFrame = 0;
    private int animSpeed = 4; // ticks per frame — lower = faster animation
    private boolean rotateToDirection = true; // face the sprite toward dx/dy

    public Projectile(float x, float y, float dx, float dy, int speed, int size, Color color) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.speed = speed; this.size = size; this.color = color;
    }

    /** Overload for an animated projectile. Pass the sliced frame array from your effect spritesheet. */
    public Projectile(float x, float y, float dx, float dy, int speed, int size, Color color, BufferedImage[] animFrames) {
        this(x, y, dx, dy, speed, size, color);
        this.animFrames = animFrames;
    }

    public void setAnimSpeed(int ticksPerFrame) {
        this.animSpeed = ticksPerFrame;
    }

    public void setRotateToDirection(boolean rotate) {
        this.rotateToDirection = rotate;
    }

    public void update() {
        x += dx * (speed + 10);
        y += dy * (speed + 10);
        life--;

        if (animFrames != null && animFrames.length > 0) {
            frameCounter++;
            if (frameCounter >= animSpeed) {
                frameCounter = 0;
                currentFrame = (currentFrame + 1) % animFrames.length;
            }
        }
    }

    /**
     * Draws the projectile. Call this from GamePanel/Enemy/Troop draw loops
     * instead of the old inline fillPolygon triangle code.
     */
    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int screenX = (int) x - cameraX;
        int screenY = (int) y - cameraY;

        if (animFrames != null && animFrames.length > 0 && animFrames[currentFrame] != null) {
            BufferedImage frame = animFrames[currentFrame];
            int drawSize = size * 2; // sprites read better a bit larger than the old triangle's bounding box

            if (rotateToDirection && (dx != 0 || dy != 0)) {
                double angle = Math.atan2(dy, dx);
                AffineTransform old = g2.getTransform();
                g2.translate(screenX, screenY);
                g2.rotate(angle);
                g2.drawImage(frame, -drawSize / 2, -drawSize / 2, drawSize, drawSize, null);
                g2.setTransform(old);
            } else {
                g2.drawImage(frame, screenX - drawSize / 2, screenY - drawSize / 2, drawSize, drawSize, null);
            }
        } else {
            // Fallback: original triangle shape for projectiles with no animation set
            g2.setColor(color);
            int[] xs = {screenX, screenX + size, screenX + size / 2};
            int[] ys = {screenY + size, screenY + size, screenY};
            g2.fillPolygon(xs, ys, 3);
        }
    }
}