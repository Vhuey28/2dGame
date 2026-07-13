package entity;

import java.awt.Color;

public class AreaEffect {
    public enum Type { STUN_AND_DAMAGE, HEAL }
    public int x, y;
    public int radius;
    public int duration; // frames
    public Type type;
    public boolean followsPlayer;
    private Player owner;
    private int blinkCounter = 0;

    public AreaEffect(int x, int y, int radius, int duration, Type type) {
        this(x, y, radius, duration, type, false, null);
    }

    public AreaEffect(int x, int y, int radius, int duration, Type type, boolean followsPlayer, Player owner) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.duration = duration;
        this.type = type;
        this.followsPlayer = followsPlayer;
        this.owner = owner;
    }

    public void update(Player player) {
        if (followsPlayer && owner != null) {
            x = owner.x + owner.gp.tileSize / 2;
            y = owner.y + owner.gp.tileSize / 2;
        }
        duration--;
        blinkCounter++;
    }

    public boolean isBlinkVisible() {
        return (blinkCounter / 8) % 2 == 0;
    }
}
