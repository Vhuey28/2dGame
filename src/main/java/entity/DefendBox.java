package entity;

import java.awt.Color;
import java.awt.Graphics2D;

import my2Dgame.GamePanel;

public class DefendBox {
    public int x, y;
    public int size;
    public int maxHealth = 200;
    public int health = maxHealth;

    public DefendBox(int x, int y, int size) {
        this.x = x; this.y = y; this.size = size;
    }

    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int sx = x - cameraX;
        int sy = y - cameraY;
        g2.setColor(new Color(255,140,0));
        g2.fillRect(sx, sy, size, size);
        g2.setColor(Color.white);
        g2.drawRect(sx, sy, size, size);

        int barW = size;
        int filled = (int)((double)health / maxHealth * barW);
        g2.setColor(Color.red);
        g2.fillRect(sx, sy - 10, filled, 6);
        g2.setColor(Color.white);
        g2.drawRect(sx, sy - 10, barW, 6);
    }
}
