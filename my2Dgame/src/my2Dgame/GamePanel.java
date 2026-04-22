package my2Dgame;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;

import javax.swing.JPanel;

import entity.Enemy;
import entity.Player;
import tile.tileManager;

public class GamePanel extends JPanel implements Runnable{
	//Screen Settings
	final int originalTileSize = 16; //16x16 tile
	final int scale = 3;
	
	public final int tileSize = originalTileSize * scale;
	public final int maxScreenCol = 16;
	public final int maxScreenRow = 12;
	public final int screenWidth = tileSize * maxScreenCol; //768 pixels
	public final int screenHeight = tileSize * maxScreenRow; //576 pixels

	public final int maxWorldCol = 50;
	public final int maxWorldRow = 50;
	public final int worldWidth = tileSize * maxWorldCol;
	public final int worldHeight = tileSize * maxWorldRow;

	public int cameraX = 0;
	public int cameraY = 0;
	public final int screenX = screenWidth / 2 - tileSize / 2;
	public final int screenY = screenHeight / 2 - tileSize / 2;
	
	//FPS
	int FPS = 60;
	
	tileManager tileM = new tileManager(this);
	KeyHandler keyH = new KeyHandler();
	Thread gameThread;
	Player player = new Player(this,keyH);
	Enemy enemy = new Enemy(this);
	Rectangle inventoryButton = new Rectangle(screenWidth - 140, 20, 120, 32);
	boolean inventoryOpen = false;
	int selectedInventoryIndex = -1;
	RedBoxItem redBox;
	Random random = new Random();
	boolean gameStarted = false;
	boolean gamePaused = false;
	boolean gameCrashed = false;
	String crashError = null;

	public GamePanel() {
		this.setPreferredSize(new Dimension(screenWidth, screenHeight));
		this.setBackground(Color.black);
		this.setDoubleBuffered(true);
		this.addKeyListener(keyH);
		this.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int code = e.getKeyCode();
				if (!gameStarted && code == KeyEvent.VK_ENTER) {
					gameStarted = true;
					gamePaused = false;
					repaint();
					return;
				}
				if (gameStarted && code == KeyEvent.VK_P) {
					gamePaused = !gamePaused;
					repaint();
				}
			}
		});
		this.setFocusable(true);
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (inventoryButton.contains(e.getPoint())) {
					inventoryOpen = !inventoryOpen;
					selectedInventoryIndex = -1;
					repaint();
					return;
				}
				if (inventoryOpen) {
					handleInventoryClick(e.getPoint());
				}
			}
		});
		spawnRedBox();
	}

	public void startGameThread() {
		gameThread = new Thread(this);
		gameThread.start();
	}
	
	@Override
	//sleep mathod: a game loop
	public void run() {
		
		double drawInterval = 1000000000/FPS; //0.01666 seconds
		double nextDrawTime = System.nanoTime() + drawInterval;
		
		while( gameThread != null) {
			
			long currentTime = System.nanoTime();
			System.out.println(currentTime);
			
			// update information like player position
			try {
				if (!gameCrashed) {
					update();
				}
			} catch (Exception e) {
				handleCrash(e);
			}
			
			// draw the screen with updated info
			try {
				repaint();
			} catch (Exception e) {
				handleCrash(e);
			}
			
			try {
			double remainingTime = nextDrawTime - System.nanoTime();
			remainingTime = remainingTime/1000000;
			
			if(remainingTime < 0) {
				remainingTime = 0;
			}
			
			Thread.sleep((long)remainingTime);
			
			nextDrawTime += drawInterval;
			
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	} 
	
	// delta method: a alternate version of the run game loop
	/*public void run() {
		
		double drawInterval = 1000000000/FPS;
		double delta = 0;
		long lastTime = System.nanoTime();
		long currentTime;
		
		
		while( gameThread != null) {
			
			currentTime = System.nanoTime();
			
			delta += (currentTime - lastTime)/ drawInterval;
			
			lastTime = currentTime;
			
			if(delta >= 1) {
			update();
			repaint();
			delta--;
			}
		}
		
	}*/
	
	public void update() {
		if (!gameStarted || gamePaused) {
			return;
		}
		player.update();
		if (redBox != null) {
			redBox.update(player);
		}
		if (enemy != null) {
			enemy.update(player);
		}
		updateCamera();
	}

	public boolean isTileBlocked(int worldX, int worldY) {
		return tileM.isBlocked(worldX, worldY);
	}

	public void updateCamera() {
		cameraX = player.x - screenX;
		cameraY = player.y - screenY;

		int maxCameraX = Math.max(0, worldWidth - screenWidth);
		int maxCameraY = Math.max(0, worldHeight - screenHeight);

		if (cameraX < 0) cameraX = 0;
		if (cameraY < 0) cameraY = 0;
		if (cameraX > maxCameraX) cameraX = maxCameraX;
		if (cameraY > maxCameraY) cameraY = maxCameraY;
	}
	
	public void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		Graphics2D g2 = (Graphics2D)g;
		
		if (!gameStarted) {
			drawStartMenu(g2);
			if (gameCrashed) {
				drawCrashMenu(g2);
			}
			g2.dispose();
			return;
		}
		
		tileM.draw(g2, cameraX, cameraY);
		if (redBox != null) {
			redBox.draw(g2, cameraX, cameraY);
		}
		if (enemy != null) {
			enemy.draw(g2, cameraX, cameraY);
		}
		player.draw(g2, cameraX, cameraY);
		drawPlayerStats(g2);
		if (gamePaused) {
			drawPauseMenu(g2);
		}
		if (gameCrashed) {
			drawCrashMenu(g2);
		}
		
		g2.dispose();
	}

	private void drawStartMenu(Graphics2D g2) {
		g2.setColor(Color.black);
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setColor(Color.white);
		g2.setFont(new Font("Arial", Font.BOLD, 40));
		String title = "Retribution Beta";
		int titleWidth = g2.getFontMetrics().stringWidth(title);
		g2.drawString(title, (screenWidth - titleWidth) / 2, screenHeight / 3);

		g2.setFont(new Font("Arial", Font.PLAIN, 20));
		String prompt = "Press ENTER to start";
		int promptWidth = g2.getFontMetrics().stringWidth(prompt);
		g2.drawString(prompt, (screenWidth - promptWidth) / 2, screenHeight / 2);

		String controls1 = "WASD to move";
		String controls2 = "P to pause / resume";
		String controls3 = "Click Inventory to open";
		int controlsY = screenHeight / 2 + 40;
		g2.drawString(controls1, (screenWidth - g2.getFontMetrics().stringWidth(controls1)) / 2, controlsY);
		g2.drawString(controls2, (screenWidth - g2.getFontMetrics().stringWidth(controls2)) / 2, controlsY + 26);
		g2.drawString(controls3, (screenWidth - g2.getFontMetrics().stringWidth(controls3)) / 2, controlsY + 52);
	}

	private void drawPauseMenu(Graphics2D g2) {
		int width = 400;
		int height = 180;
		int x = (screenWidth - width) / 2;
		int y = (screenHeight - height) / 2;
		g2.setColor(new Color(0, 0, 0, 180));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setColor(new Color(32, 32, 32, 220));
		g2.fillRoundRect(x, y, width, height, 20, 20);
		g2.setColor(Color.white);
		g2.setFont(new Font("Arial", Font.BOLD, 36));
		String text = "Game Paused";
		int textWidth = g2.getFontMetrics().stringWidth(text);
		g2.drawString(text, x + (width - textWidth) / 2, y + 60);
		g2.setFont(new Font("Arial", Font.PLAIN, 20));
		String resume = "Press P to resume";
		int resumeWidth = g2.getFontMetrics().stringWidth(resume);
		g2.drawString(resume, x + (width - resumeWidth) / 2, y + 110);
	}

	private void drawCrashMenu(Graphics2D g2) {
		int width = screenWidth - 100;
		int height = screenHeight - 120;
		int x = 50;
		int y = 40;
		g2.setColor(new Color(0, 0, 0, 210));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setColor(new Color(48, 0, 0, 230));
		g2.fillRoundRect(x, y, width, height, 24, 24);
		g2.setColor(Color.white);
		g2.setFont(new Font("Arial", Font.BOLD, 36));
		String title = "Game Crashed";
		int titleWidth = g2.getFontMetrics().stringWidth(title);
		g2.drawString(title, x + (width - titleWidth) / 2, y + 60);
		g2.setFont(new Font("Arial", Font.PLAIN, 18));
		String subtitle = "An error occurred during gameplay.";
		int subtitleWidth = g2.getFontMetrics().stringWidth(subtitle);
		g2.drawString(subtitle, x + (width - subtitleWidth) / 2, y + 95);

		int textX = x + 20;
		int textY = y + 130;
		int lineHeight = 22;
		String[] lines = crashError == null ? new String[] {"Unknown error."} : crashError.split("\n");
		for (int i = 0; i < lines.length && i < 10; i++) {
			String line = lines[i];
			if (line.length() > 80) {
				line = line.substring(0, 77) + "...";
			}
			g2.drawString(line, textX, textY + i * lineHeight);
		}
	}

	private void handleCrash(Exception e) {
		gameCrashed = true;
		StringWriter writer = new StringWriter();
		e.printStackTrace(new PrintWriter(writer));
		crashError = writer.toString();
		System.err.println("Game crash: " + crashError);
	}

	private void drawPlayerStats(Graphics2D g2) {
		int width = 220;
		int height = 18;
		int x = 20;
		int y = 20;
		int padding = 4;
		int spacing = height + 10;

		// background panel
		g2.setColor(new Color(0, 0, 0, 160));
		g2.fillRoundRect(x - padding, y - padding - 2, width + padding * 2, height * 3 + 14 + padding * 2, 12, 12);

		// Health bar
		int healthWidth = (int)((double)player.health / player.maxHealth * width);
		g2.setColor(Color.red);
		g2.fillRect(x, y, healthWidth, height);
		g2.setColor(Color.white);
		g2.drawRect(x, y, width, height);
		g2.drawString("Health", x + 6, y + height - 4);

		// Stamina bar
		y += spacing;
		int staminaWidth = (int)((double)player.stamina / player.maxStamina * width);
		g2.setColor(Color.green);
		g2.fillRect(x, y, staminaWidth, height);
		g2.setColor(Color.white);
		g2.drawRect(x, y, width, height);
		g2.drawString("Stamina", x + 6, y + height - 4);

		// Mana bar
		y += spacing;
		int manaWidth = (int)((double)player.mana / player.maxMana * width);
		g2.setColor(Color.blue);
		g2.fillRect(x, y, manaWidth, height);
		g2.setColor(Color.white);
		g2.drawRect(x, y, width, height);
		g2.drawString("Mana", x + 6, y + height - 4);

		// Inventory button
		g2.setColor(new Color(64, 64, 64, 200));
		g2.fillRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.drawString("Inventory", inventoryButton.x + 12, inventoryButton.y + 20);

		if (inventoryOpen) {
			drawInventoryPanel(g2);
		}
	}

	private void drawInventoryPanel(Graphics2D g2) {
		int width = 240;
		int x = screenWidth - width - 20;
		int y = 70;
		int lineHeight = 20;
		int contentHeight = Math.max(player.inventory.size() * lineHeight + 40, 70);

		g2.setColor(new Color(0, 0, 0, 190));
		g2.fillRoundRect(x, y, width, contentHeight, 12, 12);
		g2.setColor(Color.white);
		g2.drawRoundRect(x, y, width, contentHeight, 12, 12);
		g2.drawString("Inventory", x + 12, y + 20);

		if (player.inventory.isEmpty()) {
			g2.drawString("(empty)", x + 12, y + 40);
		} else {
			for (int i = 0; i < player.inventory.size(); i++) {
				int itemY = y + 40 + i * lineHeight;
				if (i == selectedInventoryIndex) {
					g2.setColor(new Color(128, 128, 255, 120));
					g2.fillRect(x + 8, itemY - 14, width - 16, lineHeight);
					g2.setColor(Color.white);
				}
				g2.drawString("- " + player.inventory.get(i) + " (click to drop)", x + 12, itemY);
			}
		}
	}

	private void handleInventoryClick(java.awt.Point point) {
		int width = 240;
		int x = screenWidth - width - 20;
		int y = 70;
		int lineHeight = 20;
		int contentHeight = Math.max(player.inventory.size() * lineHeight + 40, 70);
		Rectangle panel = new Rectangle(x, y, width, contentHeight);
		if (!panel.contains(point) || player.inventory.isEmpty()) {
			return;
		}

		for (int i = 0; i < player.inventory.size(); i++) {
			int itemY = y + 40 + i * lineHeight;
			Rectangle itemRect = new Rectangle(x + 8, itemY - 14, width - 16, lineHeight);
			if (itemRect.contains(point)) {
				player.inventory.remove(i);
				selectedInventoryIndex = -1;
				repaint();
				return;
			}
		}
	}

	private void spawnRedBox() {
		for(int i  = 0; i <10; i++){
		int margin = tileSize * 3;
		int x = margin + random.nextInt(worldWidth - margin * 2);
		int y = margin + random.nextInt(worldHeight - margin * 2);
		redBox = new RedBoxItem(x, y);
		}
	}

	private class RedBoxItem {
		int x;
		int y;
		int size = tileSize / 2;
		boolean collected = false;

		RedBoxItem(int x, int y) {
			this.x = x;
			this.y = y;
		}

		void update(Player player) {
			if (collected) {
				return;
			}

			int dx = player.x - x;
			int dy = player.y - y;
			double distance = Math.sqrt(dx * dx + dy * dy);

			if (distance < tileSize * 4 && distance > 0) {
				double step = 2.5;
				x += (int) Math.round(dx / distance * step);
				y += (int) Math.round(dy / distance * step);
			}

			if (distance < tileSize) {
				collected = true;
				player.addInventoryItem("Red Box");
			}
		}

		void draw(Graphics2D g2, int cameraX, int cameraY) {
			if (collected) {
				return;
			}
			int screenX = x - cameraX;
			int screenY = y - cameraY;
			if (screenX + size < 0 || screenX > screenWidth || screenY + size < 0 || screenY > screenHeight) {
				return;
			}
			g2.setColor(Color.red);
			g2.fillRect(screenX, screenY, size, size);
			g2.setColor(Color.white);
			g2.drawRect(screenX, screenY, size, size);
		}
	}
}

