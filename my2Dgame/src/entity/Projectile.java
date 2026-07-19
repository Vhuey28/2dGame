package entity;

import java.awt.Color;

public class Projectile {
    public float x, y;
    public float dx, dy;
    public int speed;
    public int life = 60;
    public int size = 8;
    public Color color;

    public Projectile(float x, float y, float dx, float dy, int speed, int size, Color color) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.speed = speed; this.size = size; this.color = color;
    }

    public void update() {
        x += dx * (speed + 10);
        y += dy * (speed + 10);
        life--;
    }
}