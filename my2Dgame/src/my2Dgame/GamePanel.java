package my2Dgame;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
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
//import entity.NPC;
import entity.Hero;
import entity.Player;
import entity.Projectile;
import entity.Squad;
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

	public final int maxWorldCol = 800;
	public final int maxWorldRow = 310;
	public final int worldWidth = tileSize * maxWorldCol;
	public final int worldHeight = tileSize * maxWorldRow;

	//public int cameraX = 0;
	//public int cameraY = 0;
	public final int screenX = screenWidth / 2 - tileSize / 2;
	public final int screenY = screenHeight / 2 - tileSize / 2;

	private float minimapZoom = 1.0f;          // current smoothed zoom
	private float targetMinimapZoom = 1.0f;    // zoom level being eased toward
	private float minZoom = 0.5f;              // most zoomed-out
	private float maxZoom = 3.0f;              // most zoomed-in
	private float zoomLerpSpeed = 6f;          // higher = snappier zoom transitions
	private int minimapScreenSize = 250;       // on-screen pixel size of the minimap (fixed square)
	private float baseViewRadiusTiles = 20f;   // how many tiles are visible at zoom = 1.0
 
	// Camera fields — replace with your actual camera tracking if named differently
	private float cameraX, cameraY;
	
	// Game State
	public enum GameState {
		PLAYING, PAUSED, INVENTORY, KINGDOM_AFFAIRS
	}
	GameState gameState = GameState.PLAYING;

	public void setGameState(GameState state) {
		gameState = state;
	}

	//FPS
	int FPS = 60;
	
	
	tileManager tileM = new tileManager(this);
	KeyHandler keyH = new KeyHandler();
	Thread gameThread;
	public Player player = new Player(this,keyH);
	public java.util.List<Enemy> enemies = new java.util.ArrayList<>();
    public java.util.List<entity.Troop> troops = new java.util.ArrayList<>();
	// public java.util.List<entity.NPC> npcs = new java.util.ArrayList<>();
	public java.util.List<Hero> heroes = new java.util.ArrayList<>();
	public Hero activeHero = null;
	public java.util.List<Hero> recruitedHeroes = new java.util.ArrayList<>(); // persists across maps
    java.util.List<CoinItem> coins = new java.util.ArrayList<>();
	public Squad enemySquad = new Squad();
	public Squad allySquad = new Squad(); 
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
	public Random random = new Random();
	boolean gameStarted = false;
	boolean gamePaused = false;
	boolean gameCrashed = false;
	boolean xWasPressedLastFrame = false;
	boolean bWasPressedLastFrame = false;
	boolean mWasPressedLastFrame = false;
	boolean qWasPressedLastFrame = false;
	boolean miniMapVisible = false;
	int portalCooldown = 0;
	boolean waveActive = false;
	int waveMessageTimer = 0;
	String crashError = null;
// Defensive copies — update() runs on a separate thread and can mutate these
		// lists mid-paint otherwise, causing ConcurrentModificationException.
		// java.util.List<Enemy> enemiesSnapshot = new java.util.ArrayList<>(enemies);
		// java.util.List<entity.Troop> troopsSnapshot = new java.util.ArrayList<>(troops);
		// java.util.List<Hero> heroesSnapshot = new java.util.ArrayList<>(heroes);
		// java.util.List<CoinItem> coinsSnapshot = new java.util.ArrayList<>(coins);
		// java.util.List<MapLink> mapLinksSnapshot = new java.util.ArrayList<>(mapLinks);
		// java.util.List<entity.Projectile> playerProjectilesSnapshot = new java.util.ArrayList<>(player.projectiles);
		// java.util.List<entity.AreaEffect> playerAreasSnapshot = new java.util.ArrayList<>(player.areas);

	// Survival mode state
	public enum GameMode { SANDBOX, SURVIVAL }
	public GameMode gameMode = GameMode.SANDBOX;

	public enum MenuStage { MODE_SELECT, ALLY_YES_NO, ALLY_TYPE, READY }
	public MenuStage menuStage = MenuStage.MODE_SELECT;

	public enum AllyChoice { NONE, MELEE_ONLY, ARCHER_ONLY, BOTH }
	public AllyChoice survivalAllyChoice = AllyChoice.NONE;

	// Survival session state
	public int survivalWaveNumber = 0;
	public int survivalEnemyCountForWave = 10; // first wave; +15 each wave after
	public boolean survivalWaveTransition = false; // true while power-up menu/portal-wait is showing
	public boolean survivalPowerUpMenuOpen = false;
	public java.util.List<String> availablePowerUps = new java.util.ArrayList<>(
	    java.util.Arrays.asList("Damage Up", "Speed Up", "Max Health Up", "Faster Regen", "Extra Dodge Range", "Attack Speed Up")
	);
	public java.util.List<String> activePowerUps = new java.util.ArrayList<>(); // stacked for the whole session
	private String[] currentPowerUpChoices = new String[3];

	// Survival portal — a single-use MapLink-like object spawned after a cleared wave
	private MapLink survivalPortal = null;
	private final String[] survivalMapPool = {"map1.txt", "mapA.txt", "forest.tmx", "home.txt"}; // extend as you add maps

	// Menu button rects (only used before gameStarted)
	private Rectangle sandboxModeButton = new Rectangle(screenWidth/2 - 160, screenHeight/2, 140, 44);
	private Rectangle survivalModeButton = new Rectangle(screenWidth/2 + 20, screenHeight/2, 140, 44);
	private Rectangle allyYesButton = new Rectangle(screenWidth/2 - 160, screenHeight/2, 140, 44);
	private Rectangle allyNoButton = new Rectangle(screenWidth/2 + 20, screenHeight/2, 140, 44);
	private Rectangle allyMeleeButton = new Rectangle(screenWidth/2 - 220, screenHeight/2, 130, 44);
	private Rectangle allyArcherButton = new Rectangle(screenWidth/2 - 65, screenHeight/2, 130, 44);
	private Rectangle allyBothButton = new Rectangle(screenWidth/2 + 90, screenHeight/2, 130, 44);

	// Power-up menu buttons
	private Rectangle[] powerUpButtons = {
	    new Rectangle(screenWidth/2 - 330, screenHeight/2, 200, 60),
	    new Rectangle(screenWidth/2 - 100, screenHeight/2, 200, 60),
	    new Rectangle(screenWidth/2 + 130, screenHeight/2, 200, 60)
	};
	public int getCurrentMapWidthTiles() { return tileM.currentMapWidth; }
	public int getCurrentMapHeightTiles() { return tileM.currentMapHeight; }



	public GamePanel() {
		this.setPreferredSize(new Dimension(screenWidth, screenHeight));
		this.setBackground(Color.black);
		this.setDoubleBuffered(true);
		this.addKeyListener(keyH);
		this.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int code = e.getKeyCode();
				if (!gameStarted && code == KeyEvent.VK_ENTER && menuStage == MenuStage.READY) {
					gameStarted = true;
					gamePaused = false;
					if (gameMode == GameMode.SURVIVAL) {
						startSurvivalMode();
					}
					repaint();
					return;
				}
				if (gameStarted && code == KeyEvent.VK_P) {
					gamePaused = !gamePaused;
					repaint();
				}
				if (code == KeyEvent.VK_T) {
					int col = (int) player.x / tileSize;
					int row = (int) player.y / tileSize;
					System.out.println("Ground tile: " + tileM.getMapTileNum()[col][row]
						+ " | Decoration tile: " + tileM.getDecorationTileNum(col, row));
				}
				
			}
		});
		this.addKeyListener(new KeyAdapter() {
   		 @Override
    		public void keyPressed(KeyEvent e) {
       		 if (!miniMapVisible) return;
        		int code = e.getKeyCode();
        		// = / + zooms in, - zooms out (works with or without shift, so no need to hold Shift for +)
       			 if (code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_PLUS || code == KeyEvent.VK_ADD) {
           		 adjustMinimapZoom(0.3f);
        	} else if (code == KeyEvent.VK_MINUS || code == KeyEvent.VK_SUBTRACT) {
            adjustMinimapZoom(-0.3f);
       		 }
    	}
		});
		this.setFocusable(true);
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {

				if (!gameStarted) {
					handleStartMenuClick(e.getPoint());
					return;
				}
				if (gamePaused) {
					if (getRestartButtonRect().contains(e.getPoint())) {
						restartGame();
						return;
					}
					// Handle power-up menu clicks in survival mode
					if (gameMode == GameMode.SURVIVAL && survivalPowerUpMenuOpen && powerUpButtons != null) {
						for (int i = 0; i < powerUpButtons.length; i++) {
							if (powerUpButtons[i] != null && powerUpButtons[i].contains(e.getPoint()) && i < currentPowerUpChoices.length && currentPowerUpChoices[i] != null) {
								applyPowerUp(currentPowerUpChoices[i]);
								survivalPowerUpMenuOpen = false;
								gamePaused = false;
								repaint();
								return;
							}
						}
					}
				}
				if (gameMode != GameMode.SURVIVAL && inventoryButton.contains(e.getPoint())) {
					inventoryOpen = !inventoryOpen;
					selectedInventoryIndex = -1;
					repaint();
					return;
				}
				if (inventoryOpen) {
					handleInventoryClick(e.getPoint());
				}
			}
			@Override
    		public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
        		if (miniMapVisible) {
           		 // scrolling up (negative rotation) zooms in
            	adjustMinimapZoom(-e.getWheelRotation() * 0.2f);
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

	// ===== Survival Mode Methods =====

	private void handleStartMenuClick(java.awt.Point p) {
		switch (menuStage) {
			case MODE_SELECT:
				if (sandboxModeButton.contains(p)) {
					gameMode = GameMode.SANDBOX;
					menuStage = MenuStage.READY;
				} else if (survivalModeButton.contains(p)) {
					gameMode = GameMode.SURVIVAL;
					menuStage = MenuStage.ALLY_YES_NO;
				}
				break;
			case ALLY_YES_NO:
				if (allyYesButton.contains(p)) {
					menuStage = MenuStage.ALLY_TYPE;
				} else if (allyNoButton.contains(p)) {
					survivalAllyChoice = AllyChoice.NONE;
					menuStage = MenuStage.READY;
				}
				break;
			case ALLY_TYPE:
				if (allyMeleeButton.contains(p)) {
					survivalAllyChoice = AllyChoice.MELEE_ONLY;
					menuStage = MenuStage.READY;
				} else if (allyArcherButton.contains(p)) {
					survivalAllyChoice = AllyChoice.ARCHER_ONLY;
					menuStage = MenuStage.READY;
				} else if (allyBothButton.contains(p)) {
					survivalAllyChoice = AllyChoice.BOTH;
					menuStage = MenuStage.READY;
				}
				break;
			case READY:
				break; // ENTER key starts the game from here, no click needed
		}
	}

	private void startSurvivalMode() {
		survivalWaveNumber = 1;
		survivalEnemyCountForWave = 10;
		activePowerUps.clear();
		setupMap("map1.txt");
		teleportPlayerForMap("map1.txt");
		spawnSurvivalWave(survivalEnemyCountForWave);
	}

	private void spawnSurvivalWave(int enemyCount) {
		enemies.clear();
		enemySquad.members.clear();
		enemySquad.commander = null;

		for (int i = 0; i < enemyCount; i++) {
			Enemy enemy = new Enemy(this);
			int angle = random.nextInt(360);
			int dist = tileSize * 8 + random.nextInt(tileSize * 6); // 8-14 tiles away
			int sx = (int)player.x + (int)(Math.cos(Math.toRadians(angle)) * dist);
			int sy = (int)player.y + (int)(Math.sin(Math.toRadians(angle)) * dist);
			int[] clamped = clampToCurrentMapBounds(sx, sy);   // <-- add this
			java.awt.Point openPt = findOpenSpawnSpace(clamped[0], clamped[1], enemy);   // <-- use clamped values			enemy.x = openPt.x;
			enemy.y = openPt.y;
			enemy.setSpawnAnchor(openPt.x, openPt.y);
			enemy.setType(i % 5 == 0 ? Enemy.Type.ARCHER : Enemy.Type.TROOP); // every 5th enemy is an archer
			enemies.add(enemy);
			enemySquad.addMember(enemy);
		}

		// commander every wave, scaling with wave number
		Enemy commander = new Enemy(this);
		int[] clampedCommander = clampToCurrentMapBounds((int)player.x + tileSize * 6, (int)player.y);
		java.awt.Point commanderPt = findOpenSpawnSpace(clampedCommander[0], clampedCommander[1], commander);		commander.x = commanderPt.x;
		commander.y = commanderPt.y;
		commander.setSpawnAnchor(commanderPt.x, commanderPt.y);
		commander.setType(Enemy.Type.COMMANDER);
		commander.maxHealth = 60 + (survivalWaveNumber * 10);
		commander.health = commander.maxHealth;
		commander.goldDrop = 0; // coin spawns disabled in survival
		enemies.add(commander);
		enemySquad.addMember(commander);
		enemySquad.setCommander(commander);

		spawnSurvivalAllies(enemyCount);

		waveActive = true;
		waveMessageTimer = 60;
		survivalWaveTransition = false;
	}

	private void spawnSurvivalAllies(int enemyCount) {
		if (survivalAllyChoice == AllyChoice.NONE) return;

		int allyCount = enemyCount / 2; // "always half of how many enemies there are in each wave"
		for (int i = 0; i < allyCount; i++) {
			entity.Troop.Role role;
			if (survivalAllyChoice == AllyChoice.MELEE_ONLY) {
				role = entity.Troop.Role.MELEE;
			} else if (survivalAllyChoice == AllyChoice.ARCHER_ONLY) {
				role = entity.Troop.Role.ARCHER;
			} else { // BOTH — alternate
				role = (i % 2 == 0) ? entity.Troop.Role.MELEE : entity.Troop.Role.ARCHER;
			}
			entity.Troop ally = new entity.Troop(this, (int)player.x, (int)player.y, role);
			int[] clampedAlly = clampToCurrentMapBounds((int)player.x + tileSize * (i % 4), (int)player.y + tileSize);
			java.awt.Point openPt = findOpenSpawnSpace(clampedAlly[0], clampedAlly[1], ally);			ally.x = openPt.x;
			ally.y = openPt.y;
			ally.setSpawnAnchor(openPt.x, openPt.y);
			troops.add(ally);
			allySquad.addMember(ally);
		}
	}

	private void onSurvivalWaveCleared() {
		waveActive = false;
		survivalWaveTransition = true;
		rollPowerUpChoices();
		survivalPowerUpMenuOpen = true;
		gamePaused = true; // reuse existing pause gate so normal update logic halts during the choice
	}

	private void rollPowerUpChoices() {
		java.util.List<String> pool = new java.util.ArrayList<>(availablePowerUps);
		java.util.Collections.shuffle(pool, random);
		for (int i = 0; i < 3 && i < pool.size(); i++) {
			currentPowerUpChoices[i] = pool.get(i);
		}
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
		enemySquad.members.clear();   // add this
		enemySquad.commander = null;
		allySquad.members.clear();
		coins.clear();
		redBox = null;
		defendBox = new entity.DefendBox(tileSize * 30, tileSize * 10, tileSize * 3);
		spawnEnemiesForMap(mapFile);
		initializePortals();
		initializeHeroes();
	}

	private void initializePortals() {
		mapLinks.clear();
		// In survival mode, we only use the survival portal (spawned after each wave)
		if (gameMode == GameMode.SURVIVAL) {
			return;
		}
		if ("map1.txt".equals(currentMap)) {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map2.txt", "Forest"));
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, worldHeight - tileSize + 4, tileSize, 8), "map3.txt", "Cave"));
			mapLinks.add(new MapLink(new Rectangle(-4, tileSize * 5, 8, tileSize), "map4.txt", "Ruins"));
			mapLinks.add(new MapLink(new Rectangle(-4, tileSize * 6, 8, tileSize), "home.txt", "Home"));
			mapLinks.add(new MapLink(new Rectangle(worldWidth - 4, tileSize * 5, 8, tileSize), "map5.txt", "Tower"));
			// Portal to mapA - middle left side
			mapLinks.add(new MapLink(new Rectangle(-4, tileSize * 12, 8, tileSize), "mapA.txt", "Map A"));
			// Portal to forest - middle right side
			mapLinks.add(new MapLink(new Rectangle(worldWidth - 4, tileSize * 12, 8, tileSize), "forest.tmx", "Forest"));
		} else if ("mapA.txt".equals(currentMap)) {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map1.txt", "Return"));
		} else if ("forest.tmx".equals(currentMap)) {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map1.txt", "Return"));
		} else if ("home.txt".equals(currentMap)) {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map1.txt", "Return"));
		} else {
			mapLinks.add(new MapLink(new Rectangle(tileSize * 7, tileSize - 4, tileSize, 8), "map1.txt", "Return"));
		}
	}

	private void initializeHeroes() {
		//heroes.clear();
		heroes.removeIf(h -> !h.isRecruited);

		if ("map1.txt".equals(currentMap)) {
			// Recruitable heroes in the starting area
			Hero warrior = new Hero(this, "vince", Hero.HeroClass.WARRIOR, tileSize * 4, tileSize * 10);
			warrior.recruitmentLines = new String[]{
				"vince: \"The road ahead is perilous. My shield is yours.\"",
				"vince: \"I've fought bandits on these roads. Let me join you.\""
			};
			heroes.add(warrior);

			Hero mage = new Hero(this, "triss", Hero.HeroClass.MAGE, tileSize * 12, tileSize * 10);
			mage.recruitmentLines = new String[]{
				"Elara: \"The arcane arts are at your disposal.\"",
				"Elara: \"Fire and ice at your command. Shall we?\""
			};
			heroes.add(mage);

		} else if ("home.txt".equals(currentMap)) {
			// More heroes available at home base
			Hero archer = new Hero(this, "Sylas", Hero.HeroClass.ARCHER, tileSize * 15, tileSize * 15);
			archer.recruitmentLines = new String[]{
				"Sylas: \"My arrows find their mark. You'll not be disappointed.\"",
				"Sylas: \"The forest has eyes, and I've got the bow.\""
			};
			heroes.add(archer);

			Hero cleric = new Hero(this, "Sister Mara", Hero.HeroClass.CLERIC, tileSize * 17, tileSize * 15);
			cleric.recruitmentLines = new String[]{
				"Sister Mara: \"The light guides my path, and yours.\"",
				"Sister Mara: \"Healing hands and holy fire. I'm with you.\""
			};
			heroes.add(cleric);

			Hero rogue = new Hero(this, "Kira", Hero.HeroClass.ROGUE, tileSize * 19, tileSize * 15);
			rogue.recruitmentLines = new String[]{
				"Kira: \"Secrets are my trade. What's yours?\"",
				"Kira: \"Behind every enemy... there's a back.\""
			};
			heroes.add(rogue);
		}

		// Re-apply recruitment status for already recruited heroes
		for (Hero hero : heroes) {
			hero.activePlayerReference = player;
			hero.update();
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
				enemy.setSpawnAnchor(openPt.x, openPt.y);
				enemy.setType(i == 1 ? Enemy.Type.ARCHER : Enemy.Type.TROOP);
				enemies.add(enemy);
				enemySquad.addMember(enemy);
			}
		} else if ("mapA.txt".equals(mapFile)) {
			for (int i = 0; i < 5; i++) {
				Enemy enemy = new Enemy(this);
				int sx = tileSize * 6 + random.nextInt(tileSize * 12);
				java.awt.Point openPt = findOpenSpawnSpace(sx, tileSize * 15, enemy);
				enemy.x = openPt.x;
				enemy.y = openPt.y;
				enemy.setSpawnAnchor(openPt.x, openPt.y);
				enemy.setType(Enemy.Type.TROOP);
				enemies.add(enemy);
				enemySquad.addMember(enemy);
			}
			Enemy commander = new Enemy(this);
			java.awt.Point openPt = findOpenSpawnSpace(tileSize * 18, tileSize * 10, commander);
			commander.x = openPt.x;
			commander.y = openPt.y;
			commander.setSpawnAnchor(openPt.x, openPt.y);
			commander.setType(Enemy.Type.COMMANDER);
			commander.maxHealth = 70;
			commander.health = commander.maxHealth;
			commander.goldDrop = 15;
			enemies.add(commander);
			enemySquad.addMember(commander);
			enemySquad.setCommander(commander);   // only for the commander line specifically
		} else if ("forest.tmx".equals(mapFile)) {
			int mapW = getCurrentMapWidthTiles();
    		int mapH = getCurrentMapHeightTiles();
			// Forest map - spawn forest-themed enemies
			for (int i = 0; i < 6; i++) {
				Enemy enemy = new Enemy(this);
				int sx = tileSize * 2 + random.nextInt(tileSize * Math.max(1, mapW - 4));
        		int sy = tileSize * 2 + random.nextInt(tileSize * Math.max(1, mapH - 4));
				java.awt.Point openPt = findOpenSpawnSpace(sx, sy, enemy);
				enemy.x = openPt.x;
				enemy.y = openPt.y;
				enemy.setSpawnAnchor(openPt.x, openPt.y);
				enemy.setType(i % 2 == 0 ? Enemy.Type.TROOP : Enemy.Type.ARCHER);
				enemies.add(enemy);
				enemySquad.addMember(enemy);
			}
			Enemy commander = new Enemy(this);
			java.awt.Point openPt = findOpenSpawnSpace(tileSize * 40, tileSize * 15, commander);
			commander.x = openPt.x;
			commander.y = openPt.y;
			commander.setSpawnAnchor(openPt.x, openPt.y);
			commander.setType(Enemy.Type.COMMANDER);
			commander.maxHealth = 80;
			commander.health = commander.maxHealth;
			commander.goldDrop = 20;
			enemies.add(commander);
			enemySquad.addMember(commander);
			enemySquad.setCommander(commander);   // only for the commander line specifically
		} else if ("home.txt".equals(mapFile)) {
			// Home map - spawn some enemies for testing
			for (int i = 0; i < 8; i++) {
				Enemy enemy = new Enemy(this);
				int sx = tileSize * 50 + random.nextInt(tileSize * 100);
				java.awt.Point openPt = findOpenSpawnSpace(sx, tileSize * 100, enemy);
				enemy.x = openPt.x;
				enemy.y = openPt.y;
				enemy.setSpawnAnchor(openPt.x, openPt.y);
				enemy.setType(Enemy.Type.TROOP);
				enemies.add(enemy);
				enemySquad.addMember(enemy);
			}
			Enemy commander = new Enemy(this);
			java.awt.Point openPt = findOpenSpawnSpace(tileSize * 150, tileSize * 80, commander);
			commander.x = openPt.x;
			commander.y = openPt.y;
			commander.setSpawnAnchor(openPt.x, openPt.y);
			commander.setType(Enemy.Type.COMMANDER);
			commander.maxHealth = 100;
			commander.health = commander.maxHealth;
			commander.goldDrop = 25;
			enemies.add(commander);
			enemySquad.addMember(commander);
			enemySquad.setCommander(commander);   // only for the commander line specifically
		} else {
			for (int i = 0; i < 3; i++) {
				Enemy enemy = new Enemy(this);
				int sx = tileSize * 4 + random.nextInt(tileSize * 8);
				java.awt.Point openPt = findOpenSpawnSpace(sx, baseY, enemy);
				enemy.x = openPt.x;
				enemy.y = openPt.y;
				enemy.setSpawnAnchor(openPt.x, openPt.y);
				enemy.setType(Enemy.Type.TROOP);
				enemies.add(enemy);
				enemySquad.addMember(enemy);
			}
			Enemy commander = new Enemy(this);
			java.awt.Point openPt = findOpenSpawnSpace(tileSize * 20, baseY, commander);
			commander.x = openPt.x;
			commander.y = openPt.y;
			commander.setType(Enemy.Type.COMMANDER);
			commander.maxHealth = 60;
			commander.health = commander.maxHealth;
			commander.goldDrop = 10;
			enemies.add(commander);
			enemySquad.addMember(commander);
			enemySquad.setCommander(commander);   // only for the commander line specifically
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

	public float getEntityX(Object aggroTarget) {
		// TODO Auto-generated method stub
		//throw new UnsupportedOperationException("Unimplemented method 'getEntityX'");
		if (aggroTarget instanceof Enemy) return ((Enemy) aggroTarget).x;
		if (aggroTarget instanceof entity.Troop) return ((entity.Troop) aggroTarget).x;
		if (aggroTarget instanceof Player) return ((Player) aggroTarget).x;
		return 0f;
	}

    public float getEntityY(Object aggroTarget) {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'getEntityY'");
		if (aggroTarget instanceof Enemy) return ((Enemy) aggroTarget).y;
		if (aggroTarget instanceof entity.Troop) return ((entity.Troop) aggroTarget).y;
		if (aggroTarget instanceof Player) return ((Player) aggroTarget).y;
		return 0f;
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
		// Update enemies - iterate over a copy to avoid ConcurrentModificationException if enemy.update() modifies the list
		java.util.List<Enemy> enemiesCopy = new java.util.ArrayList<>(enemies);
		java.util.List<Enemy> deadEnemies = new java.util.ArrayList<>();
		for (Enemy enemy : enemiesCopy) {
			enemy.update(player);
			// Collect dead enemies that have completed death animation
			if (enemy.dead && (enemy.deathAnimationComplete || enemy.archerDeathAnimationComplete || enemy.troopDeathAnimationComplete)) {
				deadEnemies.add(enemy);
			}
		}
		for (Enemy deadEnemy : deadEnemies) {
			enemies.remove(deadEnemy);
			enemySquad.removeMember(deadEnemy);
		}
		// Survival mode wave-clear detection
		if (gameMode == GameMode.SURVIVAL && waveActive && !survivalWaveTransition) {
			boolean allDead = true;
			for (Enemy e : enemies) {
				if (!e.dead) { allDead = false; break; }
			}
			if (allDead) {
				onSurvivalWaveCleared();
			}
		}
		for (Enemy enemy : enemies) {
			Iterator<Projectile> projIterator = enemy.projectiles.iterator();
			while (projIterator.hasNext()) {
				Projectile p = projIterator.next();
				int px = (int)p.x;
				int py = (int)p.y;
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
		Rectangle playerRect = new Rectangle((int)player.x, (int)player.y, tileSize, tileSize);
		Rectangle waveArea = getWaveSpawnArea();
		boolean inShop = playerRect.intersects(getShopArea());
		boolean inWaveArea = playerRect.intersects(waveArea);

		if (keyH.bPressed && !bWasPressedLastFrame) {
			if (inShop && gold >= 5) {
				gold -= 5;
				entity.Troop.Role role = keyH.shiftPressed ? entity.Troop.Role.ARCHER : entity.Troop.Role.MELEE;
				entity.Troop newTroop = new entity.Troop(this, (int)player.x, (int)player.y, role);
				java.awt.Point openPt = findOpenSpawnSpace((int)player.x + tileSize, (int)player.y, newTroop);
				newTroop.x = openPt.x;
				newTroop.y = openPt.y;
				troops.add(newTroop);
				troops.add(newTroop);
				allySquad.addMember(newTroop); 
			} else if (inWaveArea) {
				spawnWaveEnemies();
			}
		}
		bWasPressedLastFrame = keyH.bPressed;

		// Toggle minimap with M key
		if (keyH.mPressed && !mWasPressedLastFrame) {
			miniMapVisible = !miniMapVisible;
		}
		mWasPressedLastFrame = keyH.mPressed;
		// Toggle ally roam/follow with X key (edge-triggered)
		if (keyH.xPressed && !xWasPressedLastFrame) {
			for (entity.Troop t : troops) {
				t.mode = (t.mode == entity.Troop.Mode.ROAM) ? entity.Troop.Mode.FOLLOW : entity.Troop.Mode.ROAM;
			}
		}
		xWasPressedLastFrame = keyH.xPressed;

		// Hero interaction (F key)
		if (keyH.fPressed) {
			interactWithNearbyHero();
			keyH.fPressed = false; // Consume press
		}

		// Switch active hero (Q key)
		if (keyH.qPressed && !qWasPressedLastFrame) {
			switchActiveHero();
		}
		qWasPressedLastFrame = keyH.qPressed;

		// Hero abilities (4,5,6 keys)
		handleHeroAbilities();

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

		// Survival portal collision
		if (gameMode == GameMode.SURVIVAL && survivalPortal != null && portalCooldown == 0) {
			if (playerRect.intersects(survivalPortal.area)) {
				advanceSurvivalWave(survivalPortal.targetMap);
				portalCooldown = 30;
			}
		}

		// troop commands
		if (keyH.cPressed) {
			for (entity.Troop t1 : troops) {
				t1.mode = entity.Troop.Mode.CHARGE;
				Enemy nearest = null;
				float shortestDist = Float.MAX_VALUE;
				for (Enemy e : enemies) {
					if (e.dead) continue;
					float dx = e.x - t1.x;
					float dy = e.y - t1.y;
					float dist = (float) Math.sqrt(dx * dx + dy * dy);
					if (dist < shortestDist) {
						shortestDist = dist;
						nearest = e;
					}
				}
				if (nearest != null) {
					t1.targetX = (int) nearest.x;
					t1.targetY = (int) nearest.y;
				}
			}
		}
		if (keyH.vPressed) {
			for (entity.Troop t1 : troops) {
				t1.mode = entity.Troop.Mode.DEFEND;
			}
		}

		// Hero interaction - F key to interact with nearby hero
		if (keyH.fPressed && !keyH.fPressedLastFrame) {
			interactWithNearbyHero();
		}
		// Track F key state for edge detection
		keyH.fPressedLastFrame = keyH.fPressed;

		// Hero switching - Q key to cycle through recruited heroes
		if (keyH.qPressed && !keyH.qPressedLastFrame) {
			switchActiveHero();
		}
		keyH.qPressedLastFrame = keyH.qPressed;

		// Hero ability keys (4,5,6) for active hero
		if (activeHero != null && activeHero.isActivePlayer) {
			handleHeroAbilities();
		}

		for (Iterator<entity.Troop> tit = troops.iterator(); tit.hasNext();) {
			entity.Troop t1 = tit.next();
			t1.update();
			if (t1.health <= 0) {
				allySquad.removeMember(t1);   // <-- add this
				tit.remove();
			}
		}

		// troop archer projectile collision with enemies
		for (entity.Troop t1 : troops) {
			Iterator<entity.Projectile> pit = t1.projectiles.iterator();
			while (pit.hasNext()) {
				entity.Projectile p = pit.next();
				boolean hit = false;
				for (Enemy enemy : enemies) {
					if (enemy.dead) continue;
					int px = (int)p.x;
					int py = (int)p.y;
					if (px > enemy.x && px < enemy.x + tileSize && py > enemy.y && py < enemy.y + tileSize) {
						enemy.health -= 8;
						enemy.showHealthCounter = 60;
						enemy.threatTable.addThreat(t1, 8); 
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
		for (Iterator<entity.Troop> tit = troops.iterator(); tit.hasNext();) {
   			 entity.Troop t1 = tit.next();
   			 t1.update();
  			  if (t1.health <= 0) {
       			 tit.remove();
   				 }
		}

		enemySquad.updateCommanderAI(this);   // <-- add this line here

		// Update hero companions
		for (Hero hero : heroes) {
			if (hero.isRecruited && !hero.isActivePlayer) {
				hero.activePlayerReference = player;
				hero.update();
			} else if (!hero.isRecruited) {
				// Unrecruited heroes still update for idle animations
				hero.update();
			}
		}

		updateMinimapZoom(1f / FPS);   // FPS = 60, so this advances zoom by a 60th of a second each tick
		updateCamera();
		
	}

	public boolean isTileBlocked(int worldX, int worldY) {
		return tileM.isBlocked(worldX, worldY);
	}

	public boolean isCollidingWithAnyEntity(float nextX, float nextY, Object self) {
		int padding = 4;
		Rectangle nextRect = new Rectangle((int)Math.floor(nextX) + padding, (int)Math.floor(nextY) + padding, tileSize - padding * 2, tileSize - padding * 2);

		// Check player
		if (self != player) {
			Rectangle playerRect = new Rectangle((int)Math.floor(player.x) + padding, (int)Math.floor(player.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
			if (nextRect.intersects(playerRect)) {
				return true;
			}
		}

		// Check enemies
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy != null && enemy != self && !enemy.dead) {
				Rectangle enemyRect = new Rectangle((int)Math.floor(enemy.x) + padding, (int)Math.floor(enemy.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(enemyRect)) {
					return true;
				}
			}
		}

		// Check troops
		for (int i = 0; i < troops.size(); i++) {
			entity.Troop troop = troops.get(i);
			if (troop != null && troop != self && troop.health > 0) {
				Rectangle troopRect = new Rectangle((int)Math.floor(troop.x) + padding, (int)Math.floor(troop.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(troopRect)) {
					return true;
				}
			}
		}

		return false;
	}

	// Backward compatibility for int coordinates
	public boolean isCollidingWithAnyEntity(int nextX, int nextY, Object self) {
		return isCollidingWithAnyEntity((float)nextX, (float)nextY, self);
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
		cameraX = (int)player.x - screenX;
		cameraY = (int)player.y - screenY;

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

		// Defensive copies — update() runs on a separate thread and can mutate these
    // lists mid-paint otherwise, causing ConcurrentModificationException.
    java.util.List<Enemy> enemiesSnapshot = new java.util.ArrayList<>(enemies);
    java.util.List<entity.Troop> troopsSnapshot = new java.util.ArrayList<>(troops);
    java.util.List<Hero> heroesSnapshot = new java.util.ArrayList<>(heroes);
    java.util.List<CoinItem> coinsSnapshot = new java.util.ArrayList<>(coins);
    java.util.List<MapLink> mapLinksSnapshot = new java.util.ArrayList<>(mapLinks);
    java.util.List<entity.Projectile> playerProjectilesSnapshot = new java.util.ArrayList<>(player.projectiles);
    java.util.List<entity.AreaEffect> playerAreasSnapshot = new java.util.ArrayList<>(player.areas);

		
		 tileM.draw(g2, (int)cameraX, (int)cameraY); // ground layer only now
		tileM.drawDecorationBehind(g2, (int)cameraX, (int)cameraY, player.y); // trees above player draw first (behind)
		if (redBox != null) {
			redBox.draw(g2, (int)cameraX,(int) cameraY);
		}
		for (CoinItem coin : coinsSnapshot) {
			coin.draw(g2, (int)cameraX, (int)cameraY);
		}
		if (defendBox != null) defendBox.draw(g2, (int)cameraX, (int)cameraY);
		for (Enemy enemy : enemiesSnapshot) {
			if (!enemy.dead || enemy.isDying || enemy.isArcherDying || enemy.isTroopDying) {
				enemy.draw(g2, (int)cameraX, (int)cameraY);
			}
		}
		// draw player projectiles
		/* 
		for (entity.Projectile p : player.projectiles) {
			int sx = (int)p.x - (int)cameraX - p.size/2;
			int sy = (int)p.y - (int)cameraY - p.size/2;
			g2.setColor(new java.awt.Color(p.color.getRGB()));
			int[] xs = {sx, sx + p.size, sx + p.size/2};
			int[] ys = {sy + p.size, sy + p.size, sy};
			g2.fillPolygon(xs, ys, 3);
		}*/
		// for (entity.Projectile p : player.projectiles) {
		// 	p.draw(g2, (int)cameraX, (int)cameraY);
		// }
		// draw areas
		/* 
		for (entity.AreaEffect a : player.areas) {
			int sx = (int)a.x - (int)cameraX - a.radius;
			int sy = (int)a.y - (int)cameraY - a.radius;
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
		}*/
		// for (entity.AreaEffect a : player.areas) {
		// 	a.draw(g2, (int)cameraX, (int)cameraY);
		// }

		

		for (entity.Projectile p : playerProjectilesSnapshot) {
			p.draw(g2, (int)cameraX, (int)cameraY);
		}
		for (entity.AreaEffect a : playerAreasSnapshot) {
			a.draw(g2, (int)cameraX, (int)cameraY);
		}

		// draw shop green box
		int shopX = tileSize * 2 - (int)cameraX;
		int shopY = tileSize * 2 - (int)cameraY;
		int shopSize = tileSize * 3;
		g2.setColor(new java.awt.Color(0,200,0,160));
		g2.fillRect(shopX, shopY, shopSize, shopSize);
		g2.setColor(java.awt.Color.white);
		g2.drawString("Shop: B=melee, Shift+B=archer", shopX + 8, shopY + 16);

		// draw troops
		// for (entity.Troop t : troops) {
		// 	t.draw(g2, (int)cameraX, (int)cameraY);
		// }
		 for (entity.Troop t : troopsSnapshot) {
			t.draw(g2, (int)cameraX, (int)cameraY);
		}
		// draw hero companions
		for (Hero hero : heroesSnapshot) {
			if (hero.isRecruited) {
					hero.draw(g2, (int)cameraX, (int)cameraY);
				
			} else {
				// Unrecruited heroes appear as NPCs to interact with
				hero.draw(g2, (int)cameraX, (int)cameraY);
				// Draw interaction prompt
				int heroScreenX = (int)hero.x - (int)cameraX;
				int heroScreenY = (int)hero.y - (int)cameraY - tileSize / 2;
				g2.setColor(Color.YELLOW);
				g2.setFont(new Font("Arial", Font.PLAIN, 12));
				String prompt = "[F] Recruit " + hero.name;
				int promptWidth = g2.getFontMetrics().stringWidth(prompt);
				g2.drawString(prompt, heroScreenX - promptWidth / 2, heroScreenY);
			}
		}
		player.draw(g2, (int)cameraX,(int) cameraY);
		tileM.drawDecorationFront(g2, (int)cameraX, (int)cameraY, player.y); // trees below player draw last (in front)
		drawWaveSpawnArea(g2);
		drawMapLinks(g2);
		drawMiniMap(g2);
		drawPlayerStats(g2);
		if (gamePaused) {
			if (survivalPowerUpMenuOpen) {
				drawPowerUpMenu(g2);
			} else {
			drawPauseMenu(g2);
			}
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
		String title = "Chronicle Conquest Beta";
		int titleWidth = g2.getFontMetrics().stringWidth(title);
		g2.drawString(title, (screenWidth - titleWidth) / 2, screenHeight / 3);

		g2.setFont(new Font("Arial", Font.PLAIN, 20));

		switch (menuStage) {
			case MODE_SELECT:
				drawMenuPrompt(g2, "Choose a game mode");
				drawMenuButton(g2, sandboxModeButton, "Sandbox");
				drawMenuButton(g2, survivalModeButton, "Survival");
				break;
			case ALLY_YES_NO:
				drawMenuPrompt(g2, "Start with allies?");
				drawMenuButton(g2, allyYesButton, "Yes");
				drawMenuButton(g2, allyNoButton, "No");
				break;
			case ALLY_TYPE:
				drawMenuPrompt(g2, "Choose ally type");
				drawMenuButton(g2, allyMeleeButton, "Melee Only");
				drawMenuButton(g2, allyArcherButton, "Archer Only");
				drawMenuButton(g2, allyBothButton, "Both");
				break;
			case READY:
		String prompt = "Press ENTER to start";
		int promptWidth = g2.getFontMetrics().stringWidth(prompt);
		g2.drawString(prompt, (screenWidth - promptWidth) / 2, screenHeight / 2);
				break;
		}

		String controls1 = "WASD to move";
		String controls2 = "P to pause / resume";
		String controls3 = "Click Inventory to open";
		int controlsY = screenHeight / 2 + 90;
		g2.drawString(controls1, (screenWidth - g2.getFontMetrics().stringWidth(controls1)) / 2, controlsY);
		g2.drawString(controls2, (screenWidth - g2.getFontMetrics().stringWidth(controls2)) / 2, controlsY + 26);
	}

	private void drawMenuPrompt(Graphics2D g2, String text) {
		int w = g2.getFontMetrics().stringWidth(text);
		g2.drawString(text, (screenWidth - w) / 2, screenHeight / 2 - 40);
	}

	private void drawMenuButton(Graphics2D g2, Rectangle btn, String label) {
		g2.setColor(new Color(64, 64, 64, 220));
		g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		int textW = g2.getFontMetrics().stringWidth(label);
		g2.drawString(label, btn.x + (btn.width - textW) / 2, btn.y + 28);
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

	private void drawPowerUpMenu(Graphics2D g2) {
		g2.setColor(new Color(0, 0, 0, 200));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setColor(Color.white);
		g2.setFont(new Font("Arial", Font.BOLD, 30));
		String title = "Choose a Power-Up";
		int tw = g2.getFontMetrics().stringWidth(title);
		g2.drawString(title, (screenWidth - tw) / 2, screenHeight / 2 - 60);

		g2.setFont(new Font("Arial", Font.PLAIN, 16));
		for (int i = 0; i < powerUpButtons.length; i++) {
			if (currentPowerUpChoices[i] == null) continue;
			Rectangle btn = powerUpButtons[i];
			g2.setColor(new Color(64, 64, 64, 220));
			g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 12, 12);
			g2.setColor(Color.white);
			g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 12, 12);
			int textW = g2.getFontMetrics().stringWidth(currentPowerUpChoices[i]);
			g2.drawString(currentPowerUpChoices[i], btn.x + (btn.width - textW) / 2, btn.y + 36);
		}
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

	private void applyPowerUp(String powerUp) {
		activePowerUps.add(powerUp);
		switch (powerUp) {
			case "Damage Up":
				player.meleeDamage += 5;
				player.projectileDamage += 2;
				break;
			case "Speed Up":
				player.speed += 0.5f;
				break;
			case "Max Health Up":
				player.maxHealth += 20;
				player.health += 20;
				break;
			case "Faster Regen":
				player.staminaRegenTimer = Math.max(3, player.staminaRegenTimer - 2);
				player.manaRegenTimer = Math.max(10, player.manaRegenTimer - 5);
				break;
			case "Extra Dodge Range":
				player.dodgeRange += tileSize / 4;
				break;
			case "Attack Speed Up":
				player.attackCooldownBase = Math.max(4, player.attackCooldownBase - 2);
				break;
		}
		// Spawn survival portal near player after power-up selection
		if (gameMode == GameMode.SURVIVAL) {
			spawnSurvivalPortal();
		}
	}

	private void spawnSurvivalPortal() {
		// Pick a random map from the pool
		String targetMap = survivalMapPool[random.nextInt(survivalMapPool.length)];
		// Place portal near player (5-8 tiles away)
		int angle = random.nextInt(360);
		int dist = tileSize * 5 + random.nextInt(tileSize * 3);
		int px = (int)player.x + (int)(Math.cos(Math.toRadians(angle)) * dist);
		int py = (int)player.y + (int)(Math.sin(Math.toRadians(angle)) * dist);
		// Find open space for portal
		int[] clampedPortal = clampToCurrentMapBounds(px,py);
		java.awt.Point openPt = findOpenSpawnSpace(clampedPortal[0], clampedPortal[1], null);		Rectangle portalArea = new Rectangle(openPt.x - tileSize / 2, openPt.y - tileSize / 2, tileSize, tileSize);
		survivalPortal = new MapLink(portalArea, targetMap, "Next Wave: " + targetMap);
	}

	private void advanceSurvivalWave(String targetMap) {
		survivalWaveNumber++;
		survivalEnemyCountForWave += 15; // +15 enemies per wave
		survivalPortal = null; // remove portal

		// Switch map
		currentMap = targetMap;
		tileM.loadMap(targetMap);

		// Clear all map links (disable regular portals in survival)
		mapLinks.clear();

		// Clear enemies, coins, allies, and projectiles
		enemies.clear();
		coins.clear();
		troops.clear();
		allySquad.members.clear();
		player.projectiles.clear();
		player.areas.clear();

		// Teleport player to open spawn space
		teleportPlayerForMap(targetMap);

		// Spawn next wave
		spawnSurvivalWave(survivalEnemyCountForWave);
	}

	private int[] clampToCurrentMapBounds(int x, int y) {
		int mapW = getCurrentMapWidthTiles();
		int mapH = getCurrentMapHeightTiles();
		// Fall back to the global grid if the current map didn't report real dimensions for some reason
		int maxPixelX = (mapW > 0 ? mapW : maxWorldCol) * tileSize - tileSize;
		int maxPixelY = (mapH > 0 ? mapH : maxWorldRow) * tileSize - tileSize;
		int clampedX = Math.max(0, Math.min(x, maxPixelX));
		int clampedY = Math.max(0, Math.min(y, maxPixelY));
		return new int[]{clampedX, clampedY};
	}

	private void drawWaveSpawnArea(Graphics2D g2) {
		Rectangle waveArea = getWaveSpawnArea();
		int x = waveArea.x - (int)cameraX;
		int y = waveArea.y - (int)cameraY;
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
		for (MapLink link : new java.util.ArrayList<>(mapLinks)) {
			int x = link.area.x - (int)cameraX;
			int y = link.area.y - (int)cameraY;
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
		// Draw survival portal if active
		if (gameMode == GameMode.SURVIVAL && survivalPortal != null) {
			int x = survivalPortal.area.x - (int)cameraX;
			int y = survivalPortal.area.y - (int)cameraY;
			int w = survivalPortal.area.width;
			int h = survivalPortal.area.height;
			g2.setColor(new Color(255, 215, 0, 150)); // gold
			g2.fillRect(x, y, w, h);
			g2.setColor(Color.yellow);
			g2.drawRect(x, y, w, h);
			g2.setFont(new Font("Arial", Font.BOLD, 14));
			if (w > 12 && h > 12) {
				g2.drawString("Next Wave", x + 2, y + 16);
			}
		}
	}
	/* 
	private void drawMiniMap(Graphics2D g2) {
		if (!miniMapVisible) return;
		int mapSize = 250;
		int miniX = (screenWidth - mapSize) / 2;
		int miniY = (screenHeight - mapSize) / 2;
		int cellW = Math.max(1, mapSize / maxWorldCol);
		int cellH = Math.max(1, mapSize / maxWorldRow);
		g2.setColor(new Color(0, 0, 0, 200));
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
				int troopPx = miniX + (int)t.x * cellW / tileSize;
				int troopPy = miniY + (int)t.y * cellH / tileSize;
				g2.setColor(Color.blue);
				g2.fillRect(troopPx, troopPy, Math.max(2, cellW), Math.max(2, cellH));
			}
		}
		// Draw enemies
		for (Enemy enemy : enemies) {
			if (enemy != null && !enemy.dead) {
				int enemyPx = miniX + (int)enemy.x * cellW / tileSize;
				int enemyPy = miniY + (int)enemy.y * cellH / tileSize;
				if (enemy.type == Enemy.Type.COMMANDER) {
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
		int playerPx = miniX + (int)player.x * cellW / tileSize;
		int playerPy = miniY + (int)player.y * cellH / tileSize;
		g2.setColor(Color.orange);
		g2.fillOval(playerPx, playerPy, Math.max(2, cellW), Math.max(2, cellH));
		g2.setColor(Color.white);
		g2.drawRect(miniX, miniY, mapSize, mapSize);
		g2.setFont(new Font("Arial", Font.BOLD, 16));
		g2.drawString(currentMap.replace(".txt", ""), miniX + 10, miniY + 20);
	}*/

 
// ---- Call this once per game update tick (not per draw call) ----
public void updateMinimapZoom(float deltaTime) {
    if (Math.abs(minimapZoom - targetMinimapZoom) < 0.001f) {
        minimapZoom = targetMinimapZoom;
        return;
    }
    minimapZoom += (targetMinimapZoom - minimapZoom) * Math.min(1f, zoomLerpSpeed * deltaTime);
}
 
// ---- Hook this to scroll wheel / keybind input ----
public void adjustMinimapZoom(float delta) {
    targetMinimapZoom = Math.max(minZoom, Math.min(maxZoom, targetMinimapZoom + delta));
}
 
// ---- Converts a world pixel position into a minimap screen position ----
// centerWorldCol/Row = the tile column/row the minimap is centered on (player's position)
// viewRadiusTiles     = how many tiles are visible from center to edge at current zoom
private float[] worldToMiniMap(float worldX, float worldY, int miniX, int miniY,
                                float centerWorldCol, float centerWorldRow,
                                float viewRadiusTiles, int tileSize) {
    float col = worldX / tileSize;
    float row = worldY / tileSize;
 
    float halfSize = minimapScreenSize / 2f;
    float pxPerTile = halfSize / viewRadiusTiles;
 
    float screenX = miniX + halfSize + (col - centerWorldCol) * pxPerTile;
    float screenY = miniY + halfSize + (row - centerWorldRow) * pxPerTile;
 
    return new float[]{screenX, screenY, pxPerTile};
}
 
private void drawMiniMap(Graphics2D g2) {
    if (!miniMapVisible) return;
 
    int mapSize = minimapScreenSize;
    int miniX = (screenWidth - mapSize) / 2;
    int miniY = (screenHeight - mapSize) / 2;
 
    // Player-centered: everything is computed relative to the player's current tile position
    float centerCol = player.x / tileSize;
    float centerRow = player.y / tileSize;
 
    // Auto scaling: viewRadius shrinks as zoom increases (more zoom = fewer tiles visible = bigger tiles)
    float viewRadiusTiles = baseViewRadiusTiles / minimapZoom;
    float pxPerTile = (mapSize / 2f) / viewRadiusTiles;
 
    // Clip everything to the minimap's square so tiles/markers don't bleed outside the border
    Shape oldClip = g2.getClip();
    g2.setClip(miniX, miniY, mapSize, mapSize);
 
    drawBackground(g2, miniX, miniY, mapSize);
    drawTiles(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles, pxPerTile);
    drawTeleporterMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
    //drawNpcMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
    drawTroopMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
    drawEnemyMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
    drawCameraRect(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);
 
    // Player marker is always drawn dead-center since the map is centered on them
    float playerPx = miniX + mapSize / 2f;
    float playerPy = miniY + mapSize / 2f;
    g2.setColor(Color.orange);
    g2.fillOval((int) playerPx - 4, (int) playerPy - 4, 8, 8);
    g2.setColor(Color.white);
    g2.drawOval((int) playerPx - 4, (int) playerPy - 4, 8, 8);
 
    g2.setClip(oldClip);
 
    // Border + label (drawn outside the clip so they aren't cut off)
    g2.setColor(Color.white);
    g2.drawRect(miniX, miniY, mapSize, mapSize);
    g2.setFont(new Font("Arial", Font.BOLD, 16));
    g2.drawString(currentMap.replace(".txt", ""), miniX + 10, miniY + 20);
}
 
private void drawBackground(Graphics2D g2, int miniX, int miniY, int mapSize) {
    g2.setColor(new Color(0, 0, 0, 200));
    g2.fillRect(miniX - 4, miniY - 4, mapSize + 8, mapSize + 8);
}
 
private void drawTiles(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                        float viewRadiusTiles, float pxPerTile) {
    int startCol = Math.max(0, (int) (centerCol - viewRadiusTiles) - 1);
    int endCol = Math.min(maxWorldCol - 1, (int) (centerCol + viewRadiusTiles) + 1);
    int startRow = Math.max(0, (int) (centerRow - viewRadiusTiles) - 1);
    int endRow = Math.min(maxWorldRow - 1, (int) (centerRow + viewRadiusTiles) + 1);
 
    int cellSize = Math.max(1, (int) Math.ceil(pxPerTile));
 
    for (int row = startRow; row <= endRow; row++) {
        for (int col = startCol; col <= endCol; col++) {
            int tileNum = tileM.getMapTileNum()[col][row];
            Color color = tileNum == 1 ? Color.darkGray : (tileNum == 2 ? Color.blue : Color.black);
            g2.setColor(color);
 
            float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
            float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
            g2.fillRect((int) px, (int) py, cellSize, cellSize);
        }
    }
}
 
private float mapSizeHalf() {
    return minimapScreenSize / 2f;
}
 
private void drawTeleporterMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                                    float viewRadiusTiles) {
    g2.setColor(Color.magenta);
    for (MapLink link : mapLinks) {
        float col = (float) link.area.x / tileSize;
        float row = (float) link.area.y / tileSize;
        float pxPerTile = mapSizeHalf() / viewRadiusTiles;
 
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
        float pw = Math.max(3, link.area.width * pxPerTile / tileSize);
        float ph = Math.max(3, link.area.height * pxPerTile / tileSize);
 
        // Diamond marker so teleporters read differently from square tile icons
        int cx = (int) (px + pw / 2);
        int cy = (int) (py + ph / 2);
        int r = (int) Math.max(4, pw / 2);
        int[] xs = {cx, cx + r, cx, cx - r};
        int[] ys = {cy - r, cy, cy + r, cy};
        g2.fillPolygon(xs, ys, 4);
    }
}
 
private void drawNpcMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                             float viewRadiusTiles, Rectangle npc) {
	// NPC class doesn't exist - commented out
	// if (npcs == null|| npc.isEmpty()) return; // remove this guard once npcs is a guaranteed field
	// float pxPerTile = mapSizeHalf() / viewRadiusTiles;
	//
	// for (entity.NPC npcs : npcs) {
	//     if (npc == null) continue;
	//     float col = npc.x / tileSize;
	//     float row = npc.y / tileSize;
	//     float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
	//     float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
	//
	//     g2.setColor(Color.green);
	//     int size = Math.max(3, (int) pxPerTile);
	//     g2.fillRect((int) px, (int) py, size, size);
	// }
}
 
private void drawTroopMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                               float viewRadiusTiles) {
    float pxPerTile = mapSizeHalf() / viewRadiusTiles;
  for (entity.Troop t : new java.util.ArrayList<>(troops)) {
    for (int i = 0; i < troops.size(); i++) {
        /*entity.Troop*/ t = troops.get(i);
        if (t == null || t.health <= 0) continue;
 
        float col = t.x / tileSize;
        float row = t.y / tileSize;
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
 
        g2.setColor(Color.blue);
        int size = Math.max(3, (int) pxPerTile);
        g2.fillRect((int) px, (int) py, size, size);
    }
}
}
 
private void drawEnemyMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                               float viewRadiusTiles) {
    float pxPerTile = mapSizeHalf() / viewRadiusTiles;
 
    for (Enemy enemy : enemies) {
        if (enemy == null || enemy.dead) continue;
 
        float col = enemy.x / tileSize;
        float row = enemy.y / tileSize;
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
        int size = Math.max(3, (int) pxPerTile);
 
        if (enemy.type == Enemy.Type.COMMANDER) {
            // Bigger marker + yellow ring so commanderes stand out at a glance
            g2.setColor(Color.red);
            g2.fillRect((int) px - 2, (int) py - 2, size + 4, size + 4);
            g2.setColor(Color.yellow);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect((int) px - 2, (int) py - 2, size + 4, size + 4);
            g2.setStroke(new BasicStroke(1f));
        } else {
            g2.setColor(Color.red);
            g2.fillRect((int) px, (int) py, size, size);
        }
    }
}
 
// Draws a rectangle on the minimap showing the exact area currently visible
// on the main game screen (i.e. the real camera viewport).
private void drawCameraRect(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                             float viewRadiusTiles) {
    float pxPerTile = mapSizeHalf() / viewRadiusTiles;
 
    float camColStart = cameraX / tileSize;
    float camRowStart = cameraY / tileSize;
    float camColEnd = (cameraX + screenWidth) / tileSize;
    float camRowEnd = (cameraY + screenHeight) / tileSize;
 
    float x1 = miniX + mapSizeHalf() + (camColStart - centerCol) * pxPerTile;
    float y1 = miniY + mapSizeHalf() + (camRowStart - centerRow) * pxPerTile;
    float x2 = miniX + mapSizeHalf() + (camColEnd - centerCol) * pxPerTile;
    float y2 = miniY + mapSizeHalf() + (camRowEnd - centerRow) * pxPerTile;
 
    g2.setColor(Color.white);
    g2.setStroke(new BasicStroke(1.5f));
    g2.drawRect((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1));
    g2.setStroke(new BasicStroke(1f));
}
 

	private void teleportPlayerForMap(String targetMap) {
		int targetX = 0;
		int targetY = 0;
		if (gameMode != GameMode.SURVIVAL){
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
			} else if ("mapA.txt".equals(targetMap)) {
				targetX = tileSize * 3;
				targetY = tileSize * 12;
			} else if ("forest.tmx".equals(targetMap)) {
				targetX = tileSize * 3;
				targetY = tileSize * 12;
			} else if ("home.txt".equals(targetMap)) {
				targetX = tileSize * 10;
				targetY = tileSize * 10;
			} }else {
				// Default to center of map for unknown maps (survival mode random maps)
				targetX = tileM.currentMapWidth * tileSize / 2;
				targetY = tileM.currentMapHeight * tileSize / 2;
			}
			java.awt.Point openPt = findOpenSpawnSpace(targetX, targetY, player);
			player.x = openPt.x;
			player.y = openPt.y;
	}

	// ===== HERO INTERACTION METHODS =====

	private void interactWithNearbyHero() {
		for (Hero hero : heroes) {
			if (hero.isRecruited) continue; // Already recruited

			float dx = hero.x - player.x;
			float dy = hero.y - player.y;
			float dist = (float) Math.sqrt(dx * dx + dy * dy);

			if (dist < tileSize * 2) { // Within 2 tiles
				hero.openDialogue();
				// Auto-recruit for now (in future, could open dialogue UI)
				if (!hero.isRecruited) {
					recruitHero(hero);
				}
				return;
			}
		}

		// Also check for interaction with already recruited heroes (for switching, etc.)
		for (Hero hero : heroes) {
			if (!hero.isRecruited) continue;

			float dx = hero.x - player.x;
			float dy = hero.y - player.y;
			float dist = (float) Math.sqrt(dx * dx + dy * dy);

			if (dist < tileSize * 2) {
				hero.openDialogue();
				// Could open a UI for switching, dismissing, etc.
				return;
			}
		}
	}

	private void recruitHero(Hero hero) {
		hero.isRecruited = true;
		hero.onRecruited(this);
		hero.companionRole = Hero.CompanionRole.HYBRID;
		hero.activePlayerReference = player;
		if (!recruitedHeroes.contains(hero)) recruitedHeroes.add(hero);
		if (activeHero == null) setActiveHero(hero);
	}

	private void setActiveHero(Hero hero) {
		if (activeHero != null) {
			activeHero.isActivePlayer = false;
		}
		activeHero = hero;
		if (hero != null) {
			hero.isActivePlayer = true;
			hero.activePlayerReference = player;
		}
	}

	private void switchActiveHero() {
		// Get list of recruited heroes
		java.util.List<Hero> recruited = new java.util.ArrayList<>();
		for (Hero h : heroes) {
			if (h.isRecruited) recruited.add(h);
		}

		if (recruited.isEmpty()) return;

		// Find current active hero index
		int currentIndex = -1;
		if (activeHero != null) {
			currentIndex = recruited.indexOf(activeHero);
		}

		// Switch to next
		int nextIndex = (currentIndex + 1) % recruited.size();
		setActiveHero(recruited.get(nextIndex));

		System.out.println("Switched to " + activeHero.name);
	}

	private void handleHeroAbilities() {
		if (activeHero == null) return;

		Hero hero = activeHero;

		// Key 4 - first unlocked ability
		if (keyH.num4Pressed) {
			if (!hero.unlockedAbilities.isEmpty()) {
				Hero.Ability ability = hero.unlockedAbilities.get(0);
				Enemy target = findNearestEnemyToHero(hero);
				if (target != null && hero.mana >= ability.manaCost && ability.cooldown == 0) {
					hero.useAbility(ability, target);
				}
			}
			keyH.num4Pressed = false; // Consume press
		}

		// Key 5 - second unlocked ability
		if (keyH.num5Pressed) {
			if (hero.unlockedAbilities.size() > 1) {
				Hero.Ability ability = hero.unlockedAbilities.get(1);
				Enemy target = findNearestEnemyToHero(hero);
				if (target != null && hero.mana >= ability.manaCost && ability.cooldown == 0) {
					hero.useAbility(ability, target);
				}
			}
			keyH.num5Pressed = false; // Consume press
		}

		// Key 6 - third unlocked ability
		if (keyH.num6Pressed) {
			if (hero.unlockedAbilities.size() > 2) {
				Hero.Ability ability = hero.unlockedAbilities.get(2);
				Enemy target = findNearestEnemyToHero(hero);
				if (target != null && hero.mana >= ability.manaCost && ability.cooldown == 0) {
					hero.useAbility(ability, target);
				}
			}
			keyH.num6Pressed = false; // Consume press
		}
	}

	private Enemy findNearestEnemyToHero(Hero hero) {
		Enemy nearest = null;
		float shortestDist = Float.MAX_VALUE;
		for (Enemy enemy : enemies) {
			if (enemy.dead) continue;
			float dx = enemy.x - hero.x;
			float dy = enemy.y - hero.y;
			float dist = dx * dx + dy * dy;
			if (dist < shortestDist) {
				shortestDist = dist;
				nearest = enemy;
			}
		}
		return nearest;
	}

	private void spawnWaveEnemies() {
		for (int i = 0; i < 10; i++) {
			Enemy enemy = new Enemy(this);
			int sx = tileSize * 4 + random.nextInt(tileSize * 36);
			int sy = worldHeight - tileSize * 3;
			java.awt.Point openPt = findOpenSpawnSpace(sx, sy, enemy);
			enemy.x = openPt.x;
			enemy.y = openPt.y;
			enemy.setType(Enemy.Type.TROOP);
			enemies.add(enemy);
			enemySquad.addMember(enemy);
		}
		Enemy commander = new Enemy(this);
		int bx = tileSize * 20;
		int by = worldHeight - tileSize * 3;
		java.awt.Point openPt = findOpenSpawnSpace(bx, by, commander);
		commander.x = openPt.x;
		commander.y = openPt.y;
		commander.setType(Enemy.Type.COMMANDER);
		commander.maxHealth = 80;
		commander.health = commander.maxHealth;
		commander.goldDrop = 12;
		enemies.add(commander);
		enemySquad.addMember(commander);
		enemySquad.setCommander(commander);   // only for the commander line specifically
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

		// Inventory button - hide in survival mode
		if (gameMode != GameMode.SURVIVAL) {
		g2.setColor(new Color(64, 64, 64, 200));
		g2.fillRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.drawString("Inventory", inventoryButton.x + 12, inventoryButton.y + 20);

		if (inventoryOpen) {
			drawInventoryPanel(g2);
			}
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
		// No coins in survival mode
		if (gameMode == GameMode.SURVIVAL) return;

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

			int dx = (int)player.x - x;
			int dy = (int)player.y - y;
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
			int dx = (int)player.x - x;
			int dy = (int)player.y - y;
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

