package entity;

import java.awt.Color;

public class Projectile {
    public int x, y;
    public int dx, dy;
    public int speed;
    public int life = 60;
    public int size = 8;
    public Color color;

    public Projectile(int x, int y, int dx, int dy, int speed, int size, Color color) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.speed = speed; this.size = size; this.color = color;
    }

    public void update() {
        x += dx * speed;
        y += dy * speed;
        life--;
    }
}


