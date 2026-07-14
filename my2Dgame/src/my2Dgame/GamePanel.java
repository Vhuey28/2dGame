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
import java.util.Iterator;
import java.util.Random;

import javax.swing.JPanel;

import entity.Enemy;
import entity.Player;
import entity.Projectile;
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
	public Player player = new Player(this,keyH);
	public java.util.List<Enemy> enemies = new java.util.ArrayList<>();
    public java.util.List<entity.Troop> troops = new java.util.ArrayList<>();
    java.util.List<CoinItem> coins = new java.util.ArrayList<>();
    public int gold = 100;
	String currentMap = "map1.txt";
	java.util.List<MapLink> mapLinks = new java.util.ArrayList<>();
	Rectangle shopArea;
	Rectangle waveSpawnArea;
	Rectangle inventoryButton = new Rectangle(screenWidth - 140, 20, 120, 32);
	boolean inventoryOpen = false;
	int selectedInventoryIndex = -1;
	RedBoxItem redBox;
    entity.DefendBox defendBox;
	Random random = new Random();
	boolean gameStarted = false;
	boolean gamePaused = false;
	boolean gameCrashed = false;
	boolean bWasPressedLastFrame = false;
	int portalCooldown = 0;
	boolean waveActive = false;
	int waveMessageTimer = 0;
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

				if (gamePaused) {
					if (getRestartButtonRect().contains(e.getPoint())) {
						restartGame();
						return;
					}
				}
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
		this.setFocusable(true);
		setupMap("map1.txt");
	}

	public Rectangle getRestartButtonRect() {
		int width = 450;
		int height = 240;
		int x = (screenWidth - width) / 2;
		int y = (screenHeight - height) / 2;
		int btnW = 160;
		int btnH = 36;
		int btnX = x + (width - btnW) / 2;
		int btnY = y + 115;
		return new Rectangle(btnX, btnY, btnW, btnH);
	}

	public void restartGame() {
		gold = 100;
		player.health = player.maxHealth;
		player.stamina = player.maxStamina;
		player.mana = player.maxMana;
		player.projectiles.clear();
		player.areas.clear();
		player.inventory.clear();
		
		setupMap("map1.txt");
		teleportPlayerForMap("map1.txt");
		
		gamePaused = false;
		gameCrashed = false;
		waveActive = false;
		waveMessageTimer = 0;
		repaint();
	}

	public void startGameThread() {
		if (gameThread == null) {
			gameThread = new Thread(this);
			gameThread.start();
		}
	}

	private void setupMap(String mapFile) {
		currentMap = mapFile;
		tileM.loadMap(mapFile);
		enemies.clear();
		troops.clear();
		coins.clear();
		redBox = null;
		defendBox = new entity.DefendBox(tileSize * 30, tileSize * 10, tileSize * 3);
		spawnEnemiesForMap(mapFile);
		initializePortals();
	}

	private void initializePortals() {
		mapLinks.clear();
		if ("map1.txt".equals(currentMap)) {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map2.txt", "Forest"));
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, worldHeight - tileSize + 4, tileSize, 8), "map3.txt", "Cave"));
			mapLinks.add(new MapLink(new Rectangle(-4, tileSize * 5, 8, tileSize), "map4.txt", "Ruins"));
			mapLinks.add(new MapLink(new Rectangle(worldWidth - 4, tileSize * 5, 8, tileSize), "map5.txt", "Tower"));
		} else {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map1.txt", "Return"));
		}
	}

	private void spawnEnemiesForMap(String mapFile) {
		enemies.clear();
		int baseY = worldHeight - tileSize * 3;
		if ("map1.txt".equals(mapFile)) {
			for (int i = 0; i < 4; i++) {
				Enemy enemy = new Enemy(this);
				int sx = tileSize * 4 + random.nextInt(tileSize * 6);
				java.awt.Point openPt = findOpenSpawnSpace(sx, baseY, enemy);
				enemy.x = openPt.x;
				enemy.y = openPt.y;
				enemy.type = (i == 1 ? Enemy.Type.ARCHER : Enemy.Type.BASIC);
				enemies.add(enemy);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				Enemy enemy = new Enemy(this);
				int sx = tileSize * 4 + random.nextInt(tileSize * 8);
				java.awt.Point openPt = findOpenSpawnSpace(sx, baseY, enemy);
				enemy.x = openPt.x;
				enemy.y = openPt.y;
				enemy.type = Enemy.Type.BASIC;
				enemies.add(enemy);
			}
			Enemy boss = new Enemy(this);
			java.awt.Point openPt = findOpenSpawnSpace(tileSize * 20, baseY, boss);
			boss.x = openPt.x;
			boss.y = openPt.y;
			boss.type = Enemy.Type.BOSS;
			boss.maxHealth = 60;
			boss.health = boss.maxHealth;
			boss.goldDrop = 10;
			enemies.add(boss);
		}
	}

	private class MapLink {
		Rectangle area;
		String targetMap;
		String label;

		MapLink(Rectangle area, String targetMap, String label) {
			this.area = area;
			this.targetMap = targetMap;
			this.label = label;
		}
	}

	public void loadMap(String filename) {
		tileM.loadMap(filename);
	}

	public String getCurrentMap() {
		return currentMap;
	}

	public java.util.List<MapLink> getMapLinks() {
		return mapLinks;
	}

	public Rectangle getShopArea() {
		return new Rectangle(tileSize * 2, tileSize * 2, tileSize * 3, tileSize * 3);
	}

	public Rectangle getWaveSpawnArea() {
		return new Rectangle(tileSize * 12, tileSize * 2, tileSize * 6, tileSize * 4);
	}

	public int getGold() {
		return gold;
	}

	public void addGold(int amount) {
		gold += amount;
	}

	public int getCoinCount() {
		return gold;
	}

	public java.util.List<Enemy> getEnemies() {
		return enemies;
	}

	@Override
	//sleep mathod: a game loop
	public void run() {
		
		double drawInterval = 1000000000/FPS; //0.01666 seconds
		double nextDrawTime = System.nanoTime() + drawInterval;
		
		while( gameThread != null) {
			
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
		for (Iterator<CoinItem> cit = coins.iterator(); cit.hasNext();) {
			CoinItem c = cit.next();
			c.update(player);
			if (c.collected) {
				cit.remove();
			}
		}
		for (Enemy enemy : enemies) {
			enemy.update(player);
		}
		for (Enemy enemy : enemies) {
			Iterator<Projectile> projIterator = enemy.projectiles.iterator();
			while (projIterator.hasNext()) {
				Projectile p = projIterator.next();
				int px = p.x;
				int py = p.y;
				boolean hit = false;
				if (px > player.x && px < player.x + tileSize && py > player.y && py < player.y + tileSize) {
					player.health -= 8;
					if (player.health < 0) player.health = 0;
					p.life = 0;
					hit = true;
				}
				if (!hit) {
					for (int i = 0; i < troops.size(); i++) {
						entity.Troop t = troops.get(i);
						if (t != null && t.health > 0) {
							if (px > t.x && px < t.x + tileSize && py > t.y && py < t.y + tileSize) {
								t.health -= 8;
								if (t.health < 0) t.health = 0;
								p.life = 0;
								break;
							}
						}
					}
				}
				if (p.life <= 0) {
					projIterator.remove();
				}
			}
		}
		// shop area (green box)
		// buy troops if in shop and B pressed
		Rectangle playerRect = new Rectangle(player.x, player.y, tileSize, tileSize);
		Rectangle waveArea = getWaveSpawnArea();
		boolean inShop = playerRect.intersects(getShopArea());
		boolean inWaveArea = playerRect.intersects(waveArea);

		if (keyH.bPressed && !bWasPressedLastFrame) {
			if (inShop && gold >= 5) {
				gold -= 5;
				entity.Troop.Role role = keyH.shiftPressed ? entity.Troop.Role.ARCHER : entity.Troop.Role.MELEE;
				entity.Troop newTroop = new entity.Troop(this, player.x, player.y, role);
				java.awt.Point openPt = findOpenSpawnSpace(player.x + tileSize, player.y, newTroop);
				newTroop.x = openPt.x;
				newTroop.y = openPt.y;
				troops.add(newTroop);
			} else if (inWaveArea) {
				spawnWaveEnemies();
			}
		}
		bWasPressedLastFrame = keyH.bPressed;

		if (portalCooldown > 0) {
			portalCooldown--;
		}

		if (portalCooldown == 0) {
			for (MapLink link : mapLinks) {
				if (playerRect.intersects(link.area)) {
					setupMap(link.targetMap);
					teleportPlayerForMap(link.targetMap);
					portalCooldown = 30;
					waveMessageTimer = 0;
					break;
				}
			}
		}

		// troop commands
		if (keyH.cPressed) {
			for (entity.Troop t : troops) {
				t.mode = entity.Troop.Mode.CHARGE;
				if (!enemies.isEmpty()) {
					Enemy target = enemies.get(0);
					t.targetX = target.x;
					t.targetY = target.y;
				}
			}
		}
		if (keyH.vPressed) {
			for (entity.Troop t : troops) {
				t.mode = entity.Troop.Mode.DEFEND;
			}
		}

		for (Iterator<entity.Troop> tit = troops.iterator(); tit.hasNext();) {
			entity.Troop t = tit.next();
			t.update();
			if (t.health <= 0) {
				tit.remove();
			}
		}

		// troop archer projectile collision with enemies
		for (entity.Troop t : troops) {
			Iterator<entity.Projectile> pit = t.projectiles.iterator();
			while (pit.hasNext()) {
				entity.Projectile p = pit.next();
				boolean hit = false;
				for (Enemy enemy : enemies) {
					if (enemy.dead) continue;
					int px = p.x;
					int py = p.y;
					if (px > enemy.x && px < enemy.x + tileSize && py > enemy.y && py < enemy.y + tileSize) {
						enemy.health -= 8;
						enemy.showHealthCounter = 60;
						p.life = 0;
						if (enemy.health < 0) enemy.health = 0;
						hit = true;
						break;
					}
				}
				if (hit) {
					pit.remove();
				}
			}
		}

		updateCamera();
	}

	public boolean isTileBlocked(int worldX, int worldY) {
		return tileM.isBlocked(worldX, worldY);
	}

	public boolean isCollidingWithAnyEntity(int nextX, int nextY, Object self) {
		int padding = 4;
		Rectangle nextRect = new Rectangle(nextX + padding, nextY + padding, tileSize - padding * 2, tileSize - padding * 2);

		// Check player
		if (self != player) {
			Rectangle playerRect = new Rectangle(player.x + padding, player.y + padding, tileSize - padding * 2, tileSize - padding * 2);
			if (nextRect.intersects(playerRect)) {
				return true;
			}
		}

		// Check enemies
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy != null && enemy != self && !enemy.dead) {
				Rectangle enemyRect = new Rectangle(enemy.x + padding, enemy.y + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(enemyRect)) {
					return true;
				}
			}
		}

		// Check troops
		for (int i = 0; i < troops.size(); i++) {
			entity.Troop troop = troops.get(i);
			if (troop != null && troop != self && troop.health > 0) {
				Rectangle troopRect = new Rectangle(troop.x + padding, troop.y + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(troopRect)) {
					return true;
				}
			}
		}

		return false;
	}

	public java.awt.Point findOpenSpawnSpace(int startX, int startY, Object self) {
		int step = 8;
		int maxRadius = 150;
		
		if (!isTileBlocked(startX, startY) && 
		    !isTileBlocked(startX + tileSize - 1, startY) && 
		    !isTileBlocked(startX, startY + tileSize - 1) && 
		    !isTileBlocked(startX + tileSize - 1, startY + tileSize - 1) && 
		    !isCollidingWithAnyEntity(startX, startY, self)) {
			return new java.awt.Point(startX, startY);
		}
		
		for (int r = step; r <= maxRadius; r += step) {
			for (int dx = -r; dx <= r; dx += step) {
				for (int dy = -r; dy <= r; dy += step) {
					if (Math.abs(dx) == r || Math.abs(dy) == r) {
						int testX = startX + dx;
						int testY = startY + dy;
						
						if (testX < 0 || testX + tileSize > worldWidth || testY < 0 || testY + tileSize > worldHeight) {
							continue;
						}
						
						if (!isTileBlocked(testX, testY) && 
						    !isTileBlocked(testX + tileSize - 1, testY) && 
						    !isTileBlocked(testX, testY + tileSize - 1) && 
						    !isTileBlocked(testX + tileSize - 1, testY + tileSize - 1) && 
						    !isCollidingWithAnyEntity(testX, testY, self)) {
							return new java.awt.Point(testX, testY);
						}
					}
				}
			}
		}
		
		return new java.awt.Point(startX, startY);
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
		for (CoinItem coin : coins) {
			coin.draw(g2, cameraX, cameraY);
		}
		if (defendBox != null) defendBox.draw(g2, cameraX, cameraY);
		for (Enemy enemy : enemies) {
			if (!enemy.dead) {
				enemy.draw(g2, cameraX, cameraY);
			}
		}
		// draw player projectiles
		for (entity.Projectile p : player.projectiles) {
			int sx = p.x - cameraX - p.size/2;
			int sy = p.y - cameraY - p.size/2;
			g2.setColor(new java.awt.Color(p.color.getRGB()));
			int[] xs = {sx, sx + p.size, sx + p.size/2};
			int[] ys = {sy + p.size, sy + p.size, sy};
			g2.fillPolygon(xs, ys, 3);
		}
		// draw areas
		for (entity.AreaEffect a : player.areas) {
			int sx = a.x - cameraX - a.radius;
			int sy = a.y - cameraY - a.radius;
			if (a.type == entity.AreaEffect.Type.STUN_AND_DAMAGE) {
				if (a.followsPlayer) {
					int alpha = a.isBlinkVisible() ? 140 : 60;
					g2.setColor(new java.awt.Color(220, 0, 0, alpha));
				} else {
					g2.setColor(new java.awt.Color(0, 200, 0, 100));
				}
			} else {
				g2.setColor(new java.awt.Color(200, 200, 0, 100));
			}
			g2.fillOval(sx, sy, a.radius*2, a.radius*2);
		}

		// draw shop green box
		int shopX = tileSize * 2 - cameraX;
		int shopY = tileSize * 2 - cameraY;
		int shopSize = tileSize * 3;
		g2.setColor(new java.awt.Color(0,200,0,160));
		g2.fillRect(shopX, shopY, shopSize, shopSize);
		g2.setColor(java.awt.Color.white);
		g2.drawString("Shop: B=melee, Shift+B=archer", shopX + 8, shopY + 16);

		// draw troops
		for (entity.Troop t : troops) {
			t.draw(g2, cameraX, cameraY);
		}
		player.draw(g2, cameraX, cameraY);
		drawWaveSpawnArea(g2);
		drawMapLinks(g2);
		drawMiniMap(g2);
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
		int width = 450;
		int height = 240;
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
		g2.drawString(text, x + (width - textWidth) / 2, y + 50);
		g2.setFont(new Font("Arial", Font.PLAIN, 18));
		String resume = "Press P to resume";
		int resumeWidth = g2.getFontMetrics().stringWidth(resume);
		g2.drawString(resume, x + (width - resumeWidth) / 2, y + 90);

		// Draw Restart Button
		Rectangle btn = getRestartButtonRect();
		g2.setColor(new Color(180, 50, 50, 255));
		g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setFont(new Font("Arial", Font.BOLD, 18));
		String btnText = "Restart Game";
		int btnTextW = g2.getFontMetrics().stringWidth(btnText);
		g2.drawString(btnText, btn.x + (btn.width - btnTextW) / 2, btn.y + 24);

		g2.setFont(new Font("Arial", Font.PLAIN, 14));
		String controls = "Controls: WASD Move, SPACE Melee, 1/2/3 Magic, E Dodge";
		int cw = g2.getFontMetrics().stringWidth(controls);
		g2.drawString(controls, x + (width - cw) / 2, y + 200);
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

		//restart button
		Rectangle btn = getRestartButtonRect();
		g2.setColor(new Color(180, 50, 50, 255));
		g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setFont(new Font("Arial", Font.BOLD, 18));
		String btnText = "Restart Game";
		int btnTextW = g2.getFontMetrics().stringWidth(btnText);
		g2.drawString(btnText, btn.x + (btn.width - btnTextW) / 2, btn.y + 24);

		g2.setFont(new Font("Arial", Font.PLAIN, 14));
		String controls = "Controls: WASD Move, SPACE Melee, 1/2/3 Magic, E Dodge";
		int cw = g2.getFontMetrics().stringWidth(controls);
		g2.drawString(controls, x + (width - cw) / 2, y + 200);

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

	private void drawWaveSpawnArea(Graphics2D g2) {
		Rectangle waveArea = getWaveSpawnArea();
		int x = waveArea.x - cameraX;
		int y = waveArea.y - cameraY;
		int w = waveArea.width;
		int h = waveArea.height;
		g2.setColor(new Color(255, 255, 0, 80));
		g2.fillRect(x, y, w, h);
		g2.setColor(Color.yellow);
		g2.drawRect(x, y, w, h);
		g2.setFont(new Font("Arial", Font.PLAIN, 12));
		g2.drawString("Wave Zone: Press B", x + 6, y + 16);
		if (waveMessageTimer > 0) {
			g2.setColor(Color.white);
			g2.drawString("Wave spawned!", x + 6, y + 32);
		}
	}

	private void drawMapLinks(Graphics2D g2) {
		for (MapLink link : mapLinks) {
			int x = link.area.x - cameraX;
			int y = link.area.y - cameraY;
			int w = link.area.width;
			int h = link.area.height;
			g2.setColor(new Color(0, 180, 255, 120));
			g2.fillRect(x, y, w, h);
			g2.setColor(Color.cyan);
			g2.drawRect(x, y, w, h);
			g2.setFont(new Font("Arial", Font.PLAIN, 12));
			if (w > 12 && h > 12) {
				g2.drawString(link.label, x + 2, y + 12);
			}
		}
	}

	private void drawMiniMap(Graphics2D g2) {
		int mapSize = 150;
		int margin = 12;
		int miniX = screenWidth - mapSize - margin;
		int miniY = screenHeight - mapSize - margin;
		int cellW = Math.max(1, mapSize / maxWorldCol);
		int cellH = Math.max(1, mapSize / maxWorldRow);
		g2.setColor(new Color(0, 0, 0, 180));
		g2.fillRect(miniX - 4, miniY - 4, mapSize + 8, mapSize + 8);
		for (int row = 0; row < maxWorldRow; row++) {
			for (int col = 0; col < maxWorldCol; col++) {
				int tileNum = tileM.getMapTileNum()[col][row];
				Color color = tileNum == 1 ? Color.darkGray : (tileNum == 2 ? Color.blue : Color.black);
				g2.setColor(color);
				int px = miniX + col * cellW;
				int py = miniY + row * cellH;
				g2.fillRect(px, py, cellW, cellH);
			}
		}
		for (MapLink link : mapLinks) {
			int px = miniX + link.area.x * cellW / tileSize;
			int py = miniY + link.area.y * cellH / tileSize;
			int pw = Math.max(2, link.area.width * cellW / tileSize);
			int ph = Math.max(2, link.area.height * cellH / tileSize);
			g2.setColor(Color.magenta);
			g2.fillRect(px, py, pw, ph);
		}
		// Draw ally troops
		for (int i = 0; i < troops.size(); i++) {
			entity.Troop t = troops.get(i);
			if (t != null && t.health > 0) {
				int troopPx = miniX + t.x * cellW / tileSize;
				int troopPy = miniY + t.y * cellH / tileSize;
				g2.setColor(Color.blue);
				g2.fillRect(troopPx, troopPy, Math.max(2, cellW), Math.max(2, cellH));
			}
		}
		// Draw enemies
		for (Enemy enemy : enemies) {
			if (enemy != null && !enemy.dead) {
				int enemyPx = miniX + enemy.x * cellW / tileSize;
				int enemyPy = miniY + enemy.y * cellH / tileSize;
				if (enemy.type == Enemy.Type.BOSS) {
					g2.setColor(Color.red);
					g2.fillRect(enemyPx - 1, enemyPy - 1, Math.max(2, cellW) + 2, Math.max(2, cellH) + 2);
					g2.setColor(Color.yellow);
					g2.drawRect(enemyPx - 1, enemyPy - 1, Math.max(2, cellW) + 2, Math.max(2, cellH) + 2);
				} else {
					g2.setColor(Color.red);
					g2.fillRect(enemyPx, enemyPy, Math.max(2, cellW), Math.max(2, cellH));
				}
			}
		}
		int playerPx = miniX + player.x * cellW / tileSize;
		int playerPy = miniY + player.y * cellH / tileSize;
		g2.setColor(Color.orange);
		g2.fillOval(playerPx, playerPy, Math.max(2, cellW), Math.max(2, cellH));
		g2.setColor(Color.white);
		g2.drawRect(miniX, miniY, mapSize, mapSize);
		g2.drawString(currentMap.replace(".txt", ""), miniX + 6, miniY + 14);
	}

	private void teleportPlayerForMap(String targetMap) {
		int targetX = player.x;
		int targetY = player.y;
		if ("map1.txt".equals(targetMap)) {
			targetX = tileSize * 4;
			targetY = tileSize * 7;
		} else if ("map2.txt".equals(targetMap)) {
			targetX = tileSize * 7;
			targetY = tileSize * 2;
		} else if ("map3.txt".equals(targetMap)) {
			targetX = tileSize * 7;
			targetY = tileSize * 2;
		} else if ("map4.txt".equals(targetMap)) {
			targetX = tileSize * 1;
			targetY = tileSize * 2;
		} else if ("map5.txt".equals(targetMap)) {
			targetX = tileSize * 2;
			targetY = tileSize * 0;
		}
		java.awt.Point openPt = findOpenSpawnSpace(targetX, targetY, player);
		player.x = openPt.x;
		player.y = openPt.y;
	}

	private void spawnWaveEnemies() {
		for (int i = 0; i < 10; i++) {
			Enemy enemy = new Enemy(this);
			int sx = tileSize * 4 + random.nextInt(tileSize * 36);
			int sy = worldHeight - tileSize * 3;
			java.awt.Point openPt = findOpenSpawnSpace(sx, sy, enemy);
			enemy.x = openPt.x;
			enemy.y = openPt.y;
			enemy.type = Enemy.Type.BASIC;
			enemies.add(enemy);
		}
		Enemy boss = new Enemy(this);
		int bx = tileSize * 20;
		int by = worldHeight - tileSize * 3;
		java.awt.Point openPt = findOpenSpawnSpace(bx, by, boss);
		boss.x = openPt.x;
		boss.y = openPt.y;
		boss.type = Enemy.Type.BOSS;
		boss.maxHealth = 80;
		boss.health = boss.maxHealth;
		boss.goldDrop = 12;
		enemies.add(boss);
		waveActive = true;
		waveMessageTimer = 0;
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

		y += spacing;
		g2.drawString("Gold: " + gold, x + 6, y + height - 4);

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
		int contentHeight = Math.max(player.inventory.size() * lineHeight + 70, 90);

		g2.setColor(new Color(0, 0, 0, 190));
		g2.fillRoundRect(x, y, width, contentHeight, 12, 12);
		g2.setColor(Color.white);
		g2.drawRoundRect(x, y, width, contentHeight, 12, 12);
		g2.drawString("Inventory", x + 12, y + 20);
		g2.drawString("Gold: " + gold, x + 12, y + 40);

		if (player.inventory.isEmpty()) {
			g2.drawString("(empty)", x + 12, y + 60);
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

	public void spawnCoins(int startX, int startY, int count) {
		for (int i = 0; i < count; i++) {
			int offsetX = random.nextInt(tileSize) - tileSize / 2;
			int offsetY = random.nextInt(tileSize) - tileSize / 2;
			coins.add(new CoinItem(startX + offsetX, startY + offsetY));
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

	private class CoinItem {
		int x;
		int y;
		int size = tileSize / 3;
		boolean collected = false;

		CoinItem(int x, int y) {
			this.x = x;
			this.y = y;
		}

		void update(Player player) {
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
				player.addInventoryItem("Gold Coin");
				gold++;
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
			g2.setColor(new Color(255, 215, 0));
			g2.fillOval(screenX, screenY, size, size);
			g2.setColor(Color.black);
			g2.drawOval(screenX, screenY, size, size);
		}
	}
}

