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

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import entity.Enemy;
import entity.Hero;
import entity.HeroAbilityEffect;
import entity.Player;
import entity.Projectile;
import entity.Squad;
import tile.tileManager;
import world.CampaignSession;
import world.CampaignSnapshot;
import world.military.BattleContext;
import world.military.BattleResult;
import world.WorldConfig;
import world.economy.GoodType;
import world.politics.Government;

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

	// GamePanel field
	private JFrame parentWindow;
	public void setParentWindow(JFrame window) { this.parentWindow = window; }

	private float minimapZoom = 1.0f;          // current smoothed zoom
	private float targetMinimapZoom = 1.0f;    // zoom level being eased toward
	private float minZoom = 0.5f;              // most zoomed-out
	private float maxZoom = 3.0f;              // most zoomed-in
	private float zoomLerpSpeed = 6f;          // higher = snappier zoom transitions
	private int minimapScreenSize = 250;       // on-screen pixel size of the minimap (fixed square)
	private float baseViewRadiusTiles = 20f;   // how many tiles are visible at zoom = 1.0

	public static final boolean WEB_MODE = "true".equals(System.getProperty("chronicle.webmode"));

	private static final int MAX_AI_PARTIES = WEB_MODE ? 6 : 12;
	
	// Camera fields — replace with your actual camera tracking if named differently
	private float cameraX, cameraY;

	//Controller
	BindingManager bindings = new BindingManager();
	ControllerHandler controllerH = GameConfig.WEB_BUILD ? null : new ControllerHandler(bindings);
	boolean prevCtrlUp, prevCtrlDown, prevCtrlLeft, prevCtrlRight, prevCtrlSpace;
	boolean prevCtrlNum1, prevCtrlNum2, prevCtrlNum3, prevCtrlE, prevCtrlB;
	boolean prevCtrlC, prevCtrlV, prevCtrlX, prevCtrlM, prevCtrlShift;

	// Game State
	public enum GameState {
		PLAYING, PAUSED, INVENTORY, KINGDOM_AFFAIRS
	}
		public enum GameLayer { OVERWORLD, WORLD_MAP, DEPLOYMENT, BATTLE }
	GameState gameState = GameState.PLAYING;
	public GameLayer currentLayer = GameLayer.OVERWORLD;
	private boolean debugOverlayVisible = false;
	private boolean cheatMenuOpen;
	private int cheatRealmIndex;
	private int cheatPersonIndex;
	private String cheatStatus = "F1 opens/closes developer cheats";
	private enum CheatAction {
		NEXT_REALM, NEXT_PERSON, ADD_FOOD, ADD_GOLD, ADD_CARGO, HEAL_PLAYER,
		SPAWN_ARMY, ADD_TROOPS, SPAWN_CARAVAN, PLAYER_KING, SET_KING,
		CHANGE_OWNER, APPOINT_LORD, TOGGLE_WAR, TRIGGER_CRIME,
		COMPLETE_CONTRACTS, ADVANCE_DAY, ADVANCE_MONTH
	}
	private final java.util.Map<CheatAction, Rectangle> cheatButtonHitboxes = new java.util.EnumMap<>(CheatAction.class);

	public void setGameState(GameState state) {
		gameState = state;
	}

	//FPS
	int FPS = WEB_MODE ? 30 : 60;
	public int getFramesPerSecond() { return FPS; }


	tileManager tileM = new tileManager(this);
	KeyHandler keyH = new KeyHandler(bindings);
	Thread gameThread;
	public Player player = new Player(this,keyH);
	public java.util.List<Enemy> enemies = new java.util.ArrayList<>();
    public java.util.List<entity.Troop> troops = new java.util.ArrayList<>();
	// public java.util.List<entity.NPC> npcs = new java.util.ArrayList<>();
	public java.util.List<Hero> heroes = new java.util.ArrayList<>();
	public Hero activeHero = null;
	private Hero adventurerCompanion;
	public final java.util.List<HeroAbilityEffect> heroAbilityEffects = new java.util.ArrayList<>();
	private Hero pendingPortalHero;
	private Float portalEntranceX, portalEntranceY, portalExitX, portalExitY;
	private int heroPortalDuration;
	private int heroPortalCooldown;
	private int storedPlayerHealth = 100;
	private int storedPlayerMaxHealth = 100;
	private int storedPlayerMeleeDamage = 12;
	private int storedPlayerProjectileDamage = 8;
	public java.util.List<Hero> recruitedHeroes = new java.util.ArrayList<>(); // persists across maps
    java.util.List<CoinItem> coins = new java.util.ArrayList<>();
	public Squad enemySquad = new Squad();
	public Squad allySquad = new Squad(); 
    public int gold = 100;
	String currentMap = "map1.txt";
	public party.PartyManager partyManager = new party.PartyManager();
	public party.Party playerParty = new party.Party();
		public kingdom.KingdomSimulation kingdomSim = new kingdom.KingdomSimulation();
		private CampaignSession campaignSession;
		private volatile CampaignSnapshot campaignSnapshot;
		private Long selectedCampaignSettlementId;
		private Long hoveredCampaignSettlementId;
		private java.awt.Point campaignMousePoint = new java.awt.Point();
		private enum CampaignTab { OVERVIEW, INVENTORY, PARTY, CONTRACTS, KINGDOM, ENCYCLOPEDIA, CRIME }
		private CampaignTab activeCampaignTab = CampaignTab.OVERVIEW;
		private final java.util.Map<CampaignTab, Rectangle> campaignTabHitboxes = new java.util.EnumMap<>(CampaignTab.class);
		private final java.util.Map<String, Rectangle> kingdomManagementHitboxes = new java.util.LinkedHashMap<>();
		private enum CampaignContextMenu { NONE, SETTLEMENT, MARKET, NOTICE_BOARD, PERSON, ARMY, CARAVAN }
		private CampaignContextMenu campaignContextMenu = CampaignContextMenu.NONE;
		private Long selectedCampaignActorId;
		private final java.util.Map<String, Rectangle> campaignContextButtons = new java.util.LinkedHashMap<>();
		private int marketGoodIndex;
		private String campaignContextMessage = "";
		private final java.util.Map<Long, Rectangle> campaignSettlementHitboxes = new java.util.HashMap<>();
		private final java.util.Map<Long, Rectangle> campaignArmyHitboxes = new java.util.HashMap<>();
		private final java.util.Map<Long, Rectangle> campaignCaravanHitboxes = new java.util.HashMap<>();
		private final java.util.List<LocalPlaceholder> campaignLocalPlaceholders = new java.util.ArrayList<>();
		private String localInteractionMessage = "Walk near a marker and press F";
		private BattleContext activeCampaignBattle;
		private world.LocalPlayerState campaignLocalPlayerState;
		private final java.nio.file.Path campaignSavePath = java.nio.file.Paths.get(
			System.getProperty("user.home"), ".chronicle-conquest", "campaign.ccq");
		private static final long CAMPAIGN_SEED = WorldConfig.DEFAULT_SEED;
		private party.Party pendingEnemyParty;
	private boolean pendingCaughtFleeing;
	private entity.Formation.Type selectedFormation = entity.Formation.Type.WEDGE;
	private java.util.Map<party.Party, Integer> retreatCooldowns = new java.util.HashMap<>();
	private String overworldMapBeforeBattle;
	private float overworldPlayerXBeforeBattle;
	private float overworldPlayerYBeforeBattle;
	private static final float ENCOUNTER_RADIUS = 40f;
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
	// Settings menu
	boolean settingsMenuOpen = false;
	private BindingManager.Action awaitingRebindAction = null;
	private boolean awaitingRebindIsController = false;
	private Rectangle settingsButtonRect = new Rectangle(screenWidth/2 - 80, screenHeight/2 + 40, 160, 36);
	private Rectangle[][] settingsRowRects;
	
		// Game mode state
		public enum GameMode { SANDBOX, CAMPAIGN, SURVIVAL }
		public GameMode gameMode = GameMode.SANDBOX;

	public enum MenuStage { MODE_SELECT, ALLY_YES_NO, ALLY_TYPE, HERO_CHOICE, READY }
	public MenuStage menuStage = MenuStage.MODE_SELECT;

	public enum AllyChoice { NONE, MELEE_ONLY, ARCHER_ONLY, BOTH }
	public AllyChoice survivalAllyChoice = AllyChoice.NONE;
	private enum SurvivalHeroChoice { NONE, PLAY_WARRIOR, PLAY_MAGE, ALLY_WARRIOR, ALLY_MAGE }
	private SurvivalHeroChoice survivalHeroChoice = SurvivalHeroChoice.NONE;

	// Survival session state
	public int survivalWaveNumber = 0;
	public int survivalEnemyCountForWave = 10; // first wave; +15 each wave after
	private static final int MAX_ENEMIES_PER_WAVE =WEB_MODE ? 20 : 9999; // cap to prevent unbounded spawn bursts
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
		private Rectangle sandboxModeButton = new Rectangle(screenWidth/2 - 240, screenHeight/2, 140, 44);
		private Rectangle campaignModeButton = new Rectangle(screenWidth/2 - 70, screenHeight/2, 140, 44);
		private Rectangle survivalModeButton = new Rectangle(screenWidth/2 + 100, screenHeight/2, 140, 44);
	private Rectangle allyYesButton = new Rectangle(screenWidth/2 - 160, screenHeight/2, 140, 44);
	private Rectangle allyNoButton = new Rectangle(screenWidth/2 + 20, screenHeight/2, 140, 44);
	private Rectangle allyMeleeButton = new Rectangle(screenWidth/2 - 220, screenHeight/2, 130, 44);
	private Rectangle allyArcherButton = new Rectangle(screenWidth/2 - 65, screenHeight/2, 130, 44);
	private Rectangle allyBothButton = new Rectangle(screenWidth/2 + 90, screenHeight/2, 130, 44);
	private Rectangle[] survivalHeroButtons = {
		new Rectangle(54, screenHeight/2 - 5, 125, 48),
		new Rectangle(188, screenHeight/2 - 5, 125, 48),
		new Rectangle(322, screenHeight/2 - 5, 125, 48),
		new Rectangle(456, screenHeight/2 - 5, 125, 48),
		new Rectangle(590, screenHeight/2 - 5, 125, 48)
	};
	private Rectangle backButton = new Rectangle(20, screenHeight - 60, 100, 36);

	private Rectangle formationLineButton = new Rectangle(60, 300, 130, 40);
	private Rectangle formationWedgeButton = new Rectangle(200, 300, 130, 40);
	private Rectangle formationCircleButton = new Rectangle(340, 300, 130, 40);
	private Rectangle formationScatteredButton = new Rectangle(480, 300, 130, 40);
	private Rectangle beginBattleButton = new Rectangle(screenWidth/2 - 100, 420, 200, 50);
	private Rectangle retreatButton = new Rectangle(screenWidth/2 - 100, 480, 200, 44);

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
				if (code == KeyEvent.VK_F1 && gameStarted && gameMode == GameMode.CAMPAIGN) {
					cheatMenuOpen = !cheatMenuOpen;
					cheatStatus = cheatMenuOpen ? "Cheat menu enabled" : "Cheat menu closed";
					repaint();
					return;
				}
				if (cheatMenuOpen) return;
				if (campaignContextMenu != CampaignContextMenu.NONE && code == KeyEvent.VK_ESCAPE) {
					campaignContextMenu = CampaignContextMenu.NONE;
					repaint();
					return;
				}
				if (campaignContextMenu != CampaignContextMenu.NONE) return;
				if (!gameStarted && code == KeyEvent.VK_ENTER && menuStage == MenuStage.READY) {
					gameStarted = true;
					gamePaused = false;
						if (gameMode == GameMode.SURVIVAL) {
							startSurvivalMode();
						} else if (gameMode == GameMode.CAMPAIGN) {
							startCampaignMode();
						}
						repaint();
					return;
				}
					if (gameStarted && code == KeyEvent.VK_P) {
						gamePaused = !gamePaused;
						if (gameMode == GameMode.CAMPAIGN && campaignSession != null) {
							campaignSession.setWorldPaused(gamePaused);
							campaignSnapshot = campaignSession.getSnapshot();
						}
						repaint();
					}
					if (gameStarted && gameMode == GameMode.CAMPAIGN) {
						if (code == KeyEvent.VK_F5 && campaignSession != null) {
							try {
								campaignSession.save(campaignSavePath);
								localInteractionMessage = "Campaign saved";
							} catch (java.io.IOException failure) {
								localInteractionMessage = "Save failed: " + failure.getMessage();
							}
							repaint();
							return;
						}
						if (code == KeyEvent.VK_F9) {
							try {
								campaignSession = CampaignSession.load(campaignSavePath);
								campaignSnapshot = campaignSession.getSnapshot();
								selectedCampaignSettlementId = campaignSnapshot.player.settlementId;
								currentLayer = GameLayer.WORLD_MAP;
								localInteractionMessage = "Campaign loaded";
							} catch (java.io.IOException failure) {
								localInteractionMessage = "Load failed: " + failure.getMessage();
							}
							repaint();
							return;
						}
						if (code == KeyEvent.VK_TAB) {
							if (currentLayer == GameLayer.WORLD_MAP) {
								enterCampaignLocalView();
							} else {
								if (campaignLocalPlayerState != null) {
									campaignLocalPlayerState.health = player.health;
									campaignSession.leaveLocalScene(campaignLocalPlayerState);
									campaignLocalPlayerState = null;
								}
								currentLayer = GameLayer.WORLD_MAP;
							}
							repaint();
							return;
						}
						if (currentLayer == GameLayer.OVERWORLD && code == KeyEvent.VK_F) {
							interactWithCampaignPlaceholder();
							repaint();
							return;
						}
						if (currentLayer == GameLayer.WORLD_MAP && code == KeyEvent.VK_K) {
							startCampaignTacticalBattle();
							repaint();
							return;
						}
						if (currentLayer == GameLayer.BATTLE && activeCampaignBattle != null
								&& code == KeyEvent.VK_R) {
							resolveCampaignTacticalBattle();
							repaint();
							return;
						}
						if (campaignSession != null && currentLayer == GameLayer.WORLD_MAP) {
							if (code == KeyEvent.VK_1) campaignSession.setSpeed(1);
							if (code == KeyEvent.VK_2) campaignSession.setSpeed(60);
							if (code == KeyEvent.VK_3) campaignSession.setSpeed(1440);
							if (code == KeyEvent.VK_0) campaignSession.toggleWorldPaused();
							if (code == KeyEvent.VK_F6) campaignSession.advanceOneDayForTesting();
							if (selectedCampaignSettlementId != null) {
								if (code == KeyEvent.VK_B) campaignSession.buyFromSettlement(
									selectedCampaignSettlementId, GoodType.GRAIN, 5);
								if (code == KeyEvent.VK_V) campaignSession.sellToSettlement(
									selectedCampaignSettlementId, GoodType.GRAIN, 5);
								if (code == KeyEvent.VK_N) campaignSession.buyFromSettlement(
									selectedCampaignSettlementId, GoodType.VEGETABLES, 5);
								if (code == KeyEvent.VK_M) campaignSession.sellToSettlement(
									selectedCampaignSettlementId, GoodType.VEGETABLES, 5);
								if (code == KeyEvent.VK_C) {
									java.util.List<world.Contract> offers = campaignSession.getAvailableContractsAt(
										selectedCampaignSettlementId);
									if (!offers.isEmpty()) campaignSession.acceptContract(offers.get(0).id);
								}
								if (code == KeyEvent.VK_T) campaignSession.travelPlayerTo(selectedCampaignSettlementId);
							}
							campaignSnapshot = campaignSession.getSnapshot();
							repaint();
							if (code == KeyEvent.VK_T) return;
						}
					}
					if (code == KeyEvent.VK_T) {
					int col = (int) player.x / tileSize;
					int row = (int) player.y / tileSize;
					System.out.println("Ground tile: " + tileM.getMapTileNum()[col][row]
						+ " | Decoration tile: " + tileM.getDecorationTileNum(col, row));
				}
				if (code == KeyEvent.VK_F11) {
					Main.toggleFullscreen(parentWindow, GamePanel.this);
				}
				if (code == KeyEvent.VK_F3) {
					debugOverlayVisible = !debugOverlayVisible;
				}
					if (gameStarted && gameMode != GameMode.CAMPAIGN && code == KeyEvent.VK_F4) {
						spawnDebugParty(0.4);
					}
					if (gameStarted && gameMode != GameMode.CAMPAIGN && code == KeyEvent.VK_F5) {
						spawnDebugParty(2.5);
					}
					if (gameStarted && gameMode != GameMode.CAMPAIGN && code == KeyEvent.VK_F6) {
						for (int i = 0; i < 6; i++) {
							kingdomSim.forceMonthlyTickForTesting();
						}
					}
					if (gameStarted && gameMode != GameMode.CAMPAIGN && code == KeyEvent.VK_F7) {
						toggleWarWithNearestFaction();
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
		// Key listener for rebinding keys in settings menu
		this.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (awaitingRebindAction != null && !awaitingRebindIsController) {
					if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						awaitingRebindAction = null; // cancel without changing anything
						return;
					}
					bindings.setKeyBinding(awaitingRebindAction, e.getKeyCode());
					awaitingRebindAction = null;
				} else if (settingsMenuOpen && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					settingsMenuOpen = false;
				}
			}
		});
			this.setFocusable(true);
			this.setFocusTraversalKeysEnabled(false); // allow TAB to toggle the campaign world map
			playerParty.isPlayerParty = true;
		playerParty.factionName = "Player";
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				java.awt.Point point = toGamePoint(e.getPoint());
				if (cheatMenuOpen) {
					handleCheatMenuClick(point);
					return;
				}
				if (campaignContextMenu != CampaignContextMenu.NONE) {
					handleCampaignContextClick(point);
					return;
				}
				if (currentLayer == GameLayer.DEPLOYMENT) {
					if (formationLineButton.contains(point)) {
						selectedFormation = entity.Formation.Type.LINE;
						return;
					}
					if (formationWedgeButton.contains(point)) {
						selectedFormation = entity.Formation.Type.WEDGE;
						return;
					}
					if (formationCircleButton.contains(point)) {
						selectedFormation = entity.Formation.Type.CIRCLE;
						return;
					}
					if (formationScatteredButton.contains(point)) {
						selectedFormation = entity.Formation.Type.SCATTERED;
						return;
					}
					if (beginBattleButton.contains(point)) {
						startBattle(pendingEnemyParty, pendingCaughtFleeing, selectedFormation);
						return;
					}
					if (retreatButton.contains(point)) {
						attemptRetreat();
						return;
					}
					return;
				}
				
				if (!gameStarted) {
					handleStartMenuClick(point);
					return;
				}
				if (pendingPortalHero != null && !gamePaused && currentLayer != GameLayer.WORLD_MAP) {
					placeMagePortal(point.x + cameraX, point.y + cameraY);
					return;
				}
				if (gameMode == GameMode.CAMPAIGN && currentLayer == GameLayer.WORLD_MAP && !gamePaused) {
					handleCampaignMapClick(point);
					return;
				}
				if (gamePaused && settingsMenuOpen && !survivalPowerUpMenuOpen) {
					BindingManager.Action[] actions = BindingManager.Action.values();
					for (int i = 0; i < actions.length; i++) {
						try{if (settingsRowRects[i][0].contains(point)) {
							awaitingRebindAction = actions[i];
							awaitingRebindIsController = false;
							return;
						} }catch (ArrayIndexOutOfBoundsException ex) { /* ignore */ }
						if (settingsRowRects[i][1].contains(point)) {
							awaitingRebindAction = actions[i];
							awaitingRebindIsController = true;
							return;
						}
					}
					return; // swallow other clicks while settings screen is open
				}
				if (gamePaused && !settingsMenuOpen && settingsButtonRect.contains(point)) {
					settingsMenuOpen = true;
					return;
				}
				if (gamePaused) {
					// Power-up choices must be handled before generic pause buttons. The
					// center choice overlaps the old restart rectangle.
					if (gameMode == GameMode.SURVIVAL && survivalPowerUpMenuOpen) {
						for (int i = 0; i < powerUpButtons.length && i < currentPowerUpChoices.length; i++) {
							if (powerUpButtons[i].contains(point) && currentPowerUpChoices[i] != null) {
								applyPowerUp(currentPowerUpChoices[i]);
								survivalPowerUpMenuOpen = false;
								gamePaused = false;
								repaint();
								return;
							}
						}
						if (getQuitButtonRect().contains(point)) {
							quitSurvival();
							return;
						}
						return; // never fall through to restart while choosing a power-up
					}
					if (getRestartButtonRect().contains(point)) {
						restartGame();
						return;
					}
					if (getQuitButtonRect().contains(point)) {
						quitSurvival();
						return;
					}
				}
				if (gameMode != GameMode.SURVIVAL && inventoryButton.contains(point)) {
					inventoryOpen = !inventoryOpen;
					selectedInventoryIndex = -1;
					repaint();
					return;
				}
				if (inventoryOpen) {
					handleInventoryClick(point);
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
		this.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override public void mouseMoved(MouseEvent e) {
				campaignMousePoint = toGamePoint(e.getPoint());
				hoveredCampaignSettlementId = null;
				if (gameMode == GameMode.CAMPAIGN && currentLayer == GameLayer.WORLD_MAP) {
					for (java.util.Map.Entry<Long, Rectangle> entry : campaignSettlementHitboxes.entrySet()) {
						if (entry.getValue().contains(campaignMousePoint)) {
							hoveredCampaignSettlementId = entry.getKey();
							break;
						}
					}
				}
				repaint();
			}
		});
		this.setFocusable(true);
		setupMap("map1.txt");
	}

	private java.awt.Point toGamePoint(java.awt.Point componentPoint) {
		double sx = getWidth() / (double) screenWidth;
		double sy = getHeight() / (double) screenHeight;
		double renderScale = Math.max(0.0001, Math.min(sx, sy));
		int offsetX = (int) ((getWidth() - screenWidth * renderScale) / 2.0);
		int offsetY = (int) ((getHeight() - screenHeight * renderScale) / 2.0);
		return new java.awt.Point((int) ((componentPoint.x - offsetX) / renderScale),
			(int) ((componentPoint.y - offsetY) / renderScale));
	}
	

	public Rectangle getQuitButtonRect(){
		return new Rectangle(100, screenHeight - 100, 200, 50);
	}

	public void quitSurvival(){
		gameMode = GameMode.SANDBOX;
		gameStarted = false;
		gamePaused = false;
		currentLayer = GameLayer.OVERWORLD;
		campaignSession = null;
		campaignSnapshot = null;
		selectedCampaignSettlementId = null;
		menuStage = MenuStage.MODE_SELECT;
		survivalWaveNumber = 0;
		survivalEnemyCountForWave = 10;
		survivalWaveTransition = false;
		survivalPowerUpMenuOpen = false;
		survivalPortal = null;
		survivalHeroChoice = SurvivalHeroChoice.NONE;
		survivalAllyChoice = AllyChoice.NONE;
		setActiveHero(null);
		heroes.clear();
		recruitedHeroes.clear();
		activePowerUps.clear();
		setupMap("map1.txt");
		teleportPlayerForMap("map1.txt");
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
			gameMode = GameMode.SANDBOX;
			gameStarted = false;
			gamePaused = false;
			currentLayer = GameLayer.OVERWORLD;
			campaignSession = null;
			campaignSnapshot = null;
			selectedCampaignSettlementId = null;
			menuStage = MenuStage.MODE_SELECT;
			setupMap("map1.txt");
		teleportPlayerForMap("map1.txt");
		waveActive = false;
		waveMessageTimer = 0;
		repaint();
	}

	private void syncPlayerPartyFromTroops() {
		playerParty.troops.clear();
		playerParty.troops.addAll(troops);
		playerParty.heroes.clear();
		playerParty.heroes.addAll(heroes);
	}

	private void checkPartyEncounters() {
		for (party.Party aiParty : partyManager.aiParties) {
			Integer cooldown = retreatCooldowns.get(aiParty);
			if (cooldown != null && cooldown > 0) continue;

			float dx = aiParty.x - playerParty.x;
			float dy = aiParty.y - playerParty.y;
			float dist = (float) Math.sqrt(dx * dx + dy * dy);
			if (dist >= ENCOUNTER_RADIUS) continue;

			boolean hostile = aiParty.faction == null || playerParty.faction == null
				|| aiParty.faction.relations.get(playerParty.faction) == kingdom.Kingdom.DiplomaticRelation.WAR;
			if (!hostile) continue;

			boolean caughtFleeing = aiParty.state == party.Party.State.FLEEING;
			enterDeploymentScreen(aiParty, caughtFleeing);
			break;
		}
	}

	private void enterDeploymentScreen(party.Party enemyParty, boolean caughtFleeing) {
		pendingEnemyParty = enemyParty;
		pendingCaughtFleeing = caughtFleeing;
		currentLayer = GameLayer.DEPLOYMENT;
	}

	private void startBattle(party.Party enemyParty, boolean firstStrikeBonus, entity.Formation.Type formation) {
		if (enemyParty == null) return;
		overworldMapBeforeBattle = currentMap;
		overworldPlayerXBeforeBattle = player.x;
		overworldPlayerYBeforeBattle = player.y;
		selectedFormation = formation;
		allySquad.formation.type = formation;
		currentLayer = GameLayer.BATTLE;
		setupMap("battlefield.txt");
		enemies.clear();
		troops.clear();
		deployPartyAsEnemies(enemyParty);
		deployPartyAsAllies(playerParty);
		teleportPlayerForMap("battlefield.txt");
		pendingEnemyParty = null;
		pendingCaughtFleeing = false;
	}

	private void endBattle(boolean victory, party.Party defeatedEnemyParty, boolean firstStrikeBonus) {
		if (victory && defeatedEnemyParty != null) {
			int lootGold = defeatedEnemyParty.getTotalStrength() * 5;
			playerParty.carriedGold += lootGold;
			int prisonersToTake = Math.min(3, defeatedEnemyParty.troops.size());
			for (int i = 0; i < prisonersToTake && i < defeatedEnemyParty.troops.size(); i++) {
				playerParty.prisoners.add(defeatedEnemyParty.troops.get(i));
			}
			partyManager.aiParties.remove(defeatedEnemyParty);
		}
		currentLayer = GameLayer.OVERWORLD;
		if (overworldMapBeforeBattle != null) {
			setupMap(overworldMapBeforeBattle);
		}
		player.x = overworldPlayerXBeforeBattle;
		player.y = overworldPlayerYBeforeBattle;
		teleportPlayerForMap(overworldMapBeforeBattle != null ? overworldMapBeforeBattle : currentMap);
		pendingEnemyParty = null;
		pendingCaughtFleeing = false;
	}

	private void attemptRetreat() {
		if (pendingEnemyParty == null) return;
		boolean forcedFight = pendingEnemyParty.state == party.Party.State.PURSUING && Math.random() < 0.4;
		if (forcedFight) {
			startBattle(pendingEnemyParty, pendingCaughtFleeing, selectedFormation);
			return;
		}
		retreatCooldowns.put(pendingEnemyParty, 600);
		currentLayer = GameLayer.OVERWORLD;
		pendingEnemyParty = null;
		pendingCaughtFleeing = false;
	}

	private void deployPartyAsAllies(party.Party alliedParty) {
		if (alliedParty == null) return;
		allySquad.members.clear();
		allySquad.commander = null;
		for (int i = 0; i < alliedParty.troops.size(); i++) {
			entity.Troop troop = alliedParty.troops.get(i);
			if (troop == null) continue;
			troop.mode = entity.Troop.Mode.FOLLOW;
			troop.squad = allySquad;
			troops.add(troop);
			allySquad.addMember(troop);
		}
	}

	private void deployPartyAsEnemies(party.Party enemyParty) {
		if (enemyParty == null) return;
		enemySquad.members.clear();
		enemySquad.commander = null;
		for (int i = 0; i < enemyParty.troops.size(); i++) {
			entity.Troop troop = enemyParty.troops.get(i);
			if (troop == null) continue;
			Enemy enemy = new Enemy(this);
			enemy.setType(troop.role == entity.Troop.Role.ARCHER ? Enemy.Type.ARCHER : Enemy.Type.TROOP);
			int sx = tileSize * (10 + (i % 4) * 2);
			int sy = tileSize * (8 + (i / 4) * 2);
			java.awt.Point openPt = findOpenSpawnSpace(sx, sy, enemy);
			enemy.x = openPt.x;
			enemy.y = openPt.y;
			enemy.setSpawnAnchor(openPt.x, openPt.y);
			enemies.add(enemy);
			enemySquad.addMember(enemy);
		}
	}

	// ===== Survival Mode Methods =====

	private void handleStartMenuClick(java.awt.Point p) {
		if ((menuStage == MenuStage.ALLY_YES_NO || menuStage == MenuStage.ALLY_TYPE
				|| menuStage == MenuStage.HERO_CHOICE) && backButton.contains(p)) {
			menuStage = menuStage == MenuStage.HERO_CHOICE ? MenuStage.ALLY_YES_NO
				: (menuStage == MenuStage.ALLY_TYPE ? MenuStage.ALLY_YES_NO : MenuStage.MODE_SELECT);
			return;
		}
		switch (menuStage) {
			case MODE_SELECT:
					if (sandboxModeButton.contains(p)) {
						gameMode = GameMode.SANDBOX;
						menuStage = MenuStage.READY;
					} else if (campaignModeButton.contains(p)) {
						gameMode = GameMode.CAMPAIGN;
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
					menuStage = MenuStage.HERO_CHOICE;
				}
				break;
			case ALLY_TYPE:
				if (allyMeleeButton.contains(p)) survivalAllyChoice = AllyChoice.MELEE_ONLY;
				else if (allyArcherButton.contains(p)) survivalAllyChoice = AllyChoice.ARCHER_ONLY;
				else if (allyBothButton.contains(p)) survivalAllyChoice = AllyChoice.BOTH;
				else break;
				menuStage = MenuStage.HERO_CHOICE;
				break;
			case HERO_CHOICE:
				for (int i = 0; i < survivalHeroButtons.length; i++) {
					if (survivalHeroButtons[i].contains(p)) {
						survivalHeroChoice = SurvivalHeroChoice.values()[i];
						menuStage = MenuStage.READY;
						break;
					}
				}
				break;
			case READY:
				break; // ENTER key starts the game from here, no click needed
		}
		}

		private void startCampaignMode() {
			campaignSession = new CampaignSession(CAMPAIGN_SEED);
			campaignSnapshot = campaignSession.getSnapshot();
			selectedCampaignSettlementId = campaignSnapshot.settlements.isEmpty()
				? null : campaignSnapshot.settlements.get(0).id;
			currentLayer = GameLayer.WORLD_MAP;
			gamePaused = false;
			setupMap("map1.txt");
			teleportPlayerForMap("map1.txt");
		}

		private void enterCampaignLocalView() {
			if (campaignSession == null || campaignSnapshot == null || campaignSnapshot.player == null) return;
			CampaignSnapshot.SettlementView settlement = campaignSnapshot.findSettlement(
				campaignSnapshot.player.settlementId);
			String[] settlementMaps = {"map1.txt", "mapA.txt", "home.txt"};
			String localMap = settlementMaps[(int) Math.floorMod(
				campaignSnapshot.player.settlementId, settlementMaps.length)];
			setupMap(localMap);
			enemies.clear();
			troops.clear();
			teleportPlayerForMap(localMap);
			campaignLocalPlayerState = campaignSession.enterLocalScene(
				getCurrentMapWidthTiles() * tileSize, getCurrentMapHeightTiles() * tileSize);
			player.health = campaignLocalPlayerState.health;
			syncCampaignHeroesFromParty();
			campaignLocalPlaceholders.clear();
			String place = settlement == null ? "Settlement" : settlement.name;
			String marketText = settlement == null ? "Market unavailable" : "Grain "
				+ settlement.grainPrice + "c, vegetables " + settlement.vegetablePrice + "c";
			campaignLocalPlaceholders.add(new LocalPlaceholder(tileSize * 11, tileSize * 14,
				LocalPlaceholder.Type.MARKET, "Market Stall", marketText));
			campaignLocalPlaceholders.add(new LocalPlaceholder(tileSize * 21, tileSize * 7,
				LocalPlaceholder.Type.INN, "Inn", "The world keeps moving while you rest and explore."));
			campaignLocalPlaceholders.add(new LocalPlaceholder(tileSize * 16, tileSize * 17,
				LocalPlaceholder.Type.NOTICE_BOARD, "Notice Board", "Interact to accept the first local contract."));
			localInteractionMessage = place + " local view - placeholder interactions are active";
			currentLayer = GameLayer.OVERWORLD;
		}

		private void interactWithCampaignPlaceholder() {
			LocalPlaceholder nearest = null;
			world.local.LocalActor nearestActor = null;
			double best = tileSize * 3.0;
			for (LocalPlaceholder placeholder : campaignLocalPlaceholders) {
				double distance = Math.hypot(player.x - placeholder.worldX, player.y - placeholder.worldY);
				if (distance < best) {
					best = distance;
					nearest = placeholder;
					nearestActor = null;
				}
			}
			for (world.local.LocalActor actor : campaignSession.getLocalActors()) {
				double distance = Math.hypot(player.x - actor.x, player.y - actor.y);
				if (distance < best) {
					best = distance;
					nearestActor = actor;
					nearest = null;
				}
			}
			if (nearestActor != null) {
				selectedCampaignActorId = nearestActor.sourceId;
				campaignContextMenu = switch (nearestActor.kind) {
					case PERSON -> CampaignContextMenu.PERSON;
					case ARMY -> CampaignContextMenu.ARMY;
					case CARAVAN -> CampaignContextMenu.CARAVAN;
				};
				campaignContextMessage = "Choose how to interact with " + nearestActor.label;
			} else if (nearest == null) {
				interactWithNearbyHero();
				if (localInteractionMessage == null || localInteractionMessage.isBlank())
					localInteractionMessage = "Move closer to a highlighted person, hero, or location";
			} else if (nearest.type == LocalPlaceholder.Type.NOTICE_BOARD) {
				campaignContextMenu = CampaignContextMenu.NOTICE_BOARD;
				campaignContextMessage = "Select a contract to accept";
			} else if (nearest.type == LocalPlaceholder.Type.MARKET) {
				campaignContextMenu = CampaignContextMenu.MARKET;
				campaignContextMessage = "Choose a good and buy or sell";
			} else {
				localInteractionMessage = nearest.label + ": " + nearest.interactionText;
			}
		}

		private void drawCampaignLocalActors(Graphics2D g2) {
			if (gameMode != GameMode.CAMPAIGN || currentLayer != GameLayer.OVERWORLD
					|| campaignSession == null) return;
			for (world.local.LocalActor actor : campaignSession.getLocalActors()) {
				int x = (int) actor.x - (int) cameraX;
				int y = (int) actor.y - (int) cameraY;
				switch (actor.kind) {
					case CARAVAN -> {
						g2.setColor(new Color(215, 155, 52));
						g2.fillRoundRect(x - 14, y - 9, 28, 18, 5, 5);
					}
					case ARMY -> {
						g2.setColor(new Color(180, 65, 55));
						g2.fillRect(x - 12, y - 12, 24, 24);
					}
					default -> {
						g2.setColor(actor.companion ? Color.CYAN : colorForActivity(actor.activity));
						g2.fillOval(x - 7, y - 11, 14, 22);
					}
				}
				if (Math.hypot(player.x - actor.x, player.y - actor.y) < tileSize * 3.0) {
					g2.setColor(Color.WHITE);
					g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
					g2.drawString("[F] " + actor.label + " · " + actor.activity, x - 24, y - 16);
				}
			}
		}

		private Color colorForActivity(world.PersonActivity activity) {
			return switch (activity) {
				case WORKING, COMMUTING -> new Color(90, 155, 220);
				case SHOPPING -> new Color(235, 185, 70);
				case GUARDING, PATROLLING -> new Color(190, 80, 75);
				case SLEEPING -> new Color(105, 105, 145);
				default -> new Color(105, 195, 135);
			};
		}

		private void drawCampaignLocalPlaceholders(Graphics2D g2) {
			if (gameMode != GameMode.CAMPAIGN || currentLayer != GameLayer.OVERWORLD) return;
			for (LocalPlaceholder placeholder : campaignLocalPlaceholders) {
				int x = placeholder.worldX - (int) cameraX;
				int y = placeholder.worldY - (int) cameraY;
				g2.setColor(placeholder.type == LocalPlaceholder.Type.PERSON
					? new Color(75, 190, 220) : new Color(245, 196, 65));
				if (placeholder.type == LocalPlaceholder.Type.PERSON) g2.fillOval(x - 10, y - 14, 20, 28);
				else g2.fillRoundRect(x - 14, y - 14, 28, 28, 6, 6);
				g2.setColor(Color.BLACK);
				g2.drawOval(x - 3, y - 8, 3, 3);
				g2.setColor(Color.WHITE);
				g2.setFont(new Font("SansSerif", Font.BOLD, 10));
				g2.drawString(placeholder.label, x - 18, y - 20);
				if (Math.hypot(player.x - placeholder.worldX, player.y - placeholder.worldY) < tileSize * 3.0) {
					g2.setColor(Color.YELLOW);
					g2.drawString("[F] interact", x - 22, y + 28);
				}
			}
			g2.setColor(new Color(0, 0, 0, 180));
			g2.fillRoundRect(14, screenHeight - 48, screenWidth - 28, 32, 8, 8);
			g2.setColor(Color.WHITE);
			g2.drawString("TAB: world map | " + localInteractionMessage, 24, screenHeight - 27);
		}

		private void startCampaignTacticalBattle() {
			if (campaignSession == null || activeCampaignBattle != null) return;
			java.util.List<world.Army> armies = new java.util.ArrayList<>(campaignSession.getWorld().armies.values());
			armies.removeIf(army -> army.state == world.Army.ArmyState.DISBANDED
				|| campaignSession.getSimulation().getMilitarySystem().strength(army) <= 0);
			armies.sort(java.util.Comparator.comparingLong(army -> army.id));
			world.Army first = null;
			world.Army second = null;
			for (int i = 0; i < armies.size() && first == null; i++) {
				for (int j = i + 1; j < armies.size(); j++) {
					if (armies.get(i).realmId != armies.get(j).realmId) {
						first = armies.get(i);
						second = armies.get(j);
						break;
					}
				}
			}
			if (first == null || second == null) return;
			activeCampaignBattle = campaignSession.getSimulation().getBattleBridge().createContext(
				first.id, second.id, true, campaignSnapshot.worldMinute);
			setupMap("map1.txt");
			enemies.clear();
			troops.clear();
			allySquad.members.clear();
			enemySquad.members.clear();
			int index = 0;
			for (BattleContext.TacticalCombatant combatant : activeCampaignBattle.attacker.combatants) {
				entity.Troop.Role role = combatant.role == BattleContext.TacticalRole.ARCHER
					? entity.Troop.Role.ARCHER : entity.Troop.Role.MELEE;
				entity.Troop troop = new entity.Troop(this, tileSize * (5 + index % 4), tileSize * (7 + index / 4), role);
				applyStrategicSource(troop, combatant);
				troop.maxHealth = Math.max(10, (int) Math.round(30 * combatant.defenseModifier));
				troop.health = troop.maxHealth;
				troops.add(troop);
				allySquad.addMember(troop);
				index++;
			}
			index = 0;
			for (BattleContext.TacticalCombatant combatant : activeCampaignBattle.defender.combatants) {
				Enemy enemy = new Enemy(this);
				enemy.x = tileSize * (25 + index % 4);
				enemy.y = tileSize * (7 + index / 4);
				enemy.setSpawnAnchor(enemy.x, enemy.y);
				enemy.setType(combatant.role == BattleContext.TacticalRole.ARCHER
					? Enemy.Type.ARCHER : Enemy.Type.TROOP);
				applyStrategicSource(enemy, combatant);
				enemy.maxHealth = Math.max(10, (int) Math.round(25 * combatant.defenseModifier));
				enemy.health = enemy.maxHealth;
				enemies.add(enemy);
				enemySquad.addMember(enemy);
				index++;
			}
			teleportPlayerForMap("map1.txt");
			currentLayer = GameLayer.BATTLE;
		}

		private void applyStrategicSource(entity.Entity entity, BattleContext.TacticalCombatant combatant) {
			entity.sourcePersonId = combatant.sourcePersonId;
			entity.sourceRegimentId = combatant.sourceRegimentId;
			entity.sourceArmyId = combatant.sourceArmyId;
		}

		private void resolveCampaignTacticalBattle() {
			if (activeCampaignBattle == null || campaignSession == null) return;
			java.util.Map<Long, BattleResult.CasualtyOutcome> outcomes = new java.util.HashMap<>();
			int attackerLiving = 0;
			for (entity.Troop troop : troops) {
				if (troop.sourcePersonId == null) continue;
				BattleResult.CasualtyOutcome outcome;
				if (troop.health <= 0 || troop.isMeleeDying || troop.isArcherDying) outcome = BattleResult.CasualtyOutcome.KILLED;
				else if (troop.health < troop.maxHealth / 3) outcome = BattleResult.CasualtyOutcome.SEVERELY_WOUNDED;
				else if (troop.health < troop.maxHealth) outcome = BattleResult.CasualtyOutcome.WOUNDED;
				else outcome = BattleResult.CasualtyOutcome.UNHARMED;
				if (outcome != BattleResult.CasualtyOutcome.KILLED) attackerLiving++;
				outcomes.put(troop.sourcePersonId, outcome);
			}
			int defenderLiving = 0;
			for (Enemy enemy : enemies) {
				if (enemy.sourcePersonId == null) continue;
				BattleResult.CasualtyOutcome outcome;
				if (enemy.dead || enemy.health <= 0) outcome = BattleResult.CasualtyOutcome.KILLED;
				else if (enemy.health < enemy.maxHealth / 3) outcome = BattleResult.CasualtyOutcome.SEVERELY_WOUNDED;
				else if (enemy.health < enemy.maxHealth) outcome = BattleResult.CasualtyOutcome.WOUNDED;
				else outcome = BattleResult.CasualtyOutcome.UNHARMED;
				if (outcome != BattleResult.CasualtyOutcome.KILLED) defenderLiving++;
				outcomes.put(enemy.sourcePersonId, outcome);
			}
			BattleResult.WinningSide winner = attackerLiving == defenderLiving ? BattleResult.WinningSide.DRAW
				: attackerLiving > defenderLiving ? BattleResult.WinningSide.ATTACKER : BattleResult.WinningSide.DEFENDER;
			BattleResult result = campaignSession.getSimulation().getBattleBridge().resultFromTactical(
				activeCampaignBattle.battleId, winner, outcomes, 60L);
			campaignSession.getSimulation().getBattleBridge().reconcile(result);
			activeCampaignBattle = null;
			campaignSnapshot = CampaignSnapshot.capture(campaignSession.getSimulation());
			currentLayer = GameLayer.WORLD_MAP;
		}

		private void startSurvivalMode() {
		survivalWaveNumber = 1;
		survivalEnemyCountForWave = 10;
		activePowerUps.clear();
		setActiveHero(null);
		heroes.clear();
		recruitedHeroes.clear();
		setupMap("forest.tmx");
		teleportPlayerForMap("forest.tmx");
		spawnSelectedSurvivalHero();
		spawnSurvivalWave(survivalEnemyCountForWave);
	}

	private void spawnSelectedSurvivalHero() {
		if (survivalHeroChoice == SurvivalHeroChoice.NONE) return;
		boolean mage = survivalHeroChoice == SurvivalHeroChoice.PLAY_MAGE
			|| survivalHeroChoice == SurvivalHeroChoice.ALLY_MAGE;
		boolean playable = survivalHeroChoice == SurvivalHeroChoice.PLAY_MAGE
			|| survivalHeroChoice == SurvivalHeroChoice.PLAY_WARRIOR;
		Hero hero = new Hero(this, mage ? "Triss" : "Vince",
			mage ? Hero.HeroClass.MAGE : Hero.HeroClass.WARRIOR,
			(int) player.x + tileSize, (int) player.y);
		hero.isRecruited = true;
		hero.companionRole = Hero.CompanionRole.HYBRID;
		hero.activePlayerReference = player;
		heroes.add(hero);
		recruitedHeroes.add(hero);
		if (playable) setActiveHero(hero);
	}

	private void spawnSurvivalWave(int enemyCount) {
		enemies.clear();
		enemySquad.members.clear();
		enemySquad.commander = null;

		int actualCount = Math.min(enemyCount, MAX_ENEMIES_PER_WAVE);
		for (int i = 0; i < actualCount; i++) {
			Enemy enemy = new Enemy(this);
			int angle = random.nextInt(360);
			int dist = tileSize * 8 + random.nextInt(tileSize * 6); // 8-14 tiles away
			int sx = (int)player.x + (int)(Math.cos(Math.toRadians(angle)) * dist);
			int sy = (int)player.y + (int)(Math.sin(Math.toRadians(angle)) * dist);
			int[] clamped = clampToCurrentMapBounds(sx, sy);   // <-- add this
			java.awt.Point openPt = findOpenSpawnSpace(clamped[0], clamped[1], enemy, 60);   // <-- use clamped values with smaller radius
			enemy.x = openPt.x;
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
		heroAbilityEffects.clear();
		pendingPortalHero = null;
		portalEntranceX = portalEntranceY = portalExitX = portalExitY = null;
		heroPortalDuration = 0;
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
		if (gameMode != GameMode.SURVIVAL)
		if ("map1.txt".equals(currentMap)) {
			// Recruitable heroes in the starting area
			if (!heroAlreadyPresent("vince")) {
				Hero warrior = new Hero(this, "vince", Hero.HeroClass.WARRIOR, tileSize * 4, tileSize * 10);
				warrior.recruitmentLines = new String[]{
					"vince: \"The road ahead is perilous. My shield is yours.\"",
					"vince: \"I've fought bandits on these roads. Let me join you.\""
				};
				heroes.add(warrior);
			}

			if (!heroAlreadyPresent("triss")) {
				Hero mage = new Hero(this, "triss", Hero.HeroClass.MAGE, tileSize * 12, tileSize * 10);
				mage.recruitmentLines = new String[]{
					"Elara: \"The arcane arts are at your disposal.\"",
					"Elara: \"Fire and ice at your command. Shall we?\""
				};
				heroes.add(mage);
			}

		} else if ("home.txt".equals(currentMap)) {
			// More heroes available at home base
			if (!heroAlreadyPresent("Sylas")) {
				Hero archer = new Hero(this, "Sylas", Hero.HeroClass.ARCHER, tileSize * 15, tileSize * 15);
				archer.recruitmentLines = new String[]{
					"Sylas: \"My arrows find their mark. You'll not be disappointed.\"",
					"Sylas: \"The forest has eyes, and I've got the bow.\""
				};
				heroes.add(archer);
			}

			if (!heroAlreadyPresent("Sister Mara")) {
				Hero cleric = new Hero(this, "Sister Mara", Hero.HeroClass.CLERIC, tileSize * 17, tileSize * 15);
				cleric.recruitmentLines = new String[]{
					"Sister Mara: \"The light guides my path, and yours.\"",
					"Sister Mara: \"Healing hands and holy fire. I'm with you.\""
				};
				heroes.add(cleric);
			}

			if (!heroAlreadyPresent("Kira")) {
				Hero rogue = new Hero(this, "Kira", Hero.HeroClass.ROGUE, tileSize * 19, tileSize * 15);
				rogue.recruitmentLines = new String[]{
					"Kira: \"Secrets are my trade. What's yours?\"",
					"Kira: \"Behind every enemy... there's a back.\""
				};
				heroes.add(rogue);
			}
		}

		// Re-apply recruitment status for already recruited heroes
		for (Hero hero : heroes) {
			hero.activePlayerReference = player;
			hero.update();
		}
	}

	private boolean heroAlreadyPresent(String name) {
	    for (Hero h : heroes) {
	        if (h.name.equals(name)) return true;
	    }
	    return false;
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

	private static final class LocalPlaceholder {
		enum Type { PERSON, MARKET, INN, NOTICE_BOARD, GATE }
		final int worldX;
		final int worldY;
		final Type type;
		final String label;
		final String interactionText;

		LocalPlaceholder(int worldX, int worldY, Type type, String label, String interactionText) {
			this.worldX = worldX;
			this.worldY = worldY;
			this.type = type;
			this.label = label;
			this.interactionText = interactionText;
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
	/*public void run() {
		
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
		
	} */
	public void run() {
    double drawInterval = 1000000000 / FPS; // 0.01666 seconds
    double nextDrawTime = System.nanoTime() + drawInterval;

    while (gameThread != null) {
        // update information like player position
        try {
            if (!gameCrashed) {
                update();
            }
        } catch (Exception e) {
            handleCrash(e);
        }

        // Render the screen with updated info
        SwingUtilities.invokeLater(() -> {
            try {
                repaint();
            } catch (Exception e) {
                handleCrash(e);
            }
        });

        try {
            double remainingTime = nextDrawTime - 
System.nanoTime();
            remainingTime = remainingTime / 1000000;

            if (remainingTime < 0) {
                remainingTime = 0;
            }

            // Control frame rate
            long sleepTime = (long) remainingTime;
            Thread.sleep(sleepTime);

            nextDrawTime += drawInterval;
        } catch (InterruptedException e) {
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

			if (!gameStarted || gamePaused || cheatMenuOpen || campaignContextMenu != CampaignContextMenu.NONE) {
				return;
			}
			if (gameMode == GameMode.CAMPAIGN && campaignSession != null) {
				campaignSession.update(1.0 / FPS);
				campaignSnapshot = campaignSession.getSnapshot();
				if (currentLayer == GameLayer.OVERWORLD) {
					campaignSession.updateLocalScene(1.0 / FPS,
						getCurrentMapWidthTiles() * tileSize, getCurrentMapHeightTiles() * tileSize);
				}
				if (currentLayer == GameLayer.WORLD_MAP) {
					return;
				}
			}
			if (gameMode == GameMode.SANDBOX && currentLayer == GameLayer.OVERWORLD) {
			syncPlayerPartyFromTroops();
			partyManager.update(this, kingdomSim.allKingdoms, playerParty);
			checkPartyEncounters();
			retreatCooldowns.replaceAll((party, ticks) -> ticks - 1);
			retreatCooldowns.values().removeIf(ticks -> ticks <= 0);
		}
		// Poll controller and merge its state into keyH — every existing keyH.xPressed
		// check throughout Player/Troop/Enemy now also responds to the gamepad automatically.
		if (controllerH != null) {
			controllerH.poll();
			if (awaitingRebindAction != null && awaitingRebindIsController) {
				Integer pressed = controllerH.consumeLastButtonPressedForRebind();
				if (pressed != null) {
					bindings.setControllerBinding(awaitingRebindAction, pressed);
					awaitingRebindAction = null;
				}
				return; // skip normal gameplay update while capturing a rebind
			}
		}
		if (controllerH != null && controllerH.isConnected()) {
			// "&& !prevCtrlX" clears exactly what the controller set last frame before re-checking its
			// current state — so a released stick/button actually goes back to false instead of sticking.
			keyH.upPressed = (keyH.upPressed && !prevCtrlUp) || controllerH.upPressed;
			keyH.downPressed = (keyH.downPressed && !prevCtrlDown) || controllerH.downPressed;
			keyH.leftPressed = (keyH.leftPressed && !prevCtrlLeft) || controllerH.leftPressed;
			keyH.rightPressed = (keyH.rightPressed && !prevCtrlRight) || controllerH.rightPressed;
			keyH.spacePressed = (keyH.spacePressed && !prevCtrlSpace) || controllerH.spacePressed;
			keyH.num1Pressed = (keyH.num1Pressed && !prevCtrlNum1) || controllerH.num1Pressed;
			keyH.num2Pressed = (keyH.num2Pressed && !prevCtrlNum2) || controllerH.num2Pressed;
			keyH.num3Pressed = (keyH.num3Pressed && !prevCtrlNum3) || controllerH.num3Pressed;
			keyH.ePressed = (keyH.ePressed && !prevCtrlE) || controllerH.ePressed;
			keyH.bPressed = (keyH.bPressed && !prevCtrlB) || controllerH.bPressed;
			keyH.cPressed = (keyH.cPressed && !prevCtrlC) || controllerH.cPressed;
			keyH.vPressed = (keyH.vPressed && !prevCtrlV) || controllerH.vPressed;
			keyH.xPressed = (keyH.xPressed && !prevCtrlX) || controllerH.xPressed;
			keyH.mPressed = (keyH.mPressed && !prevCtrlM) || controllerH.mPressed;
			keyH.shiftPressed = (keyH.shiftPressed && !prevCtrlShift) || controllerH.shiftPressed;

			prevCtrlUp = controllerH.upPressed;
			prevCtrlDown = controllerH.downPressed;
			prevCtrlLeft = controllerH.leftPressed;
			prevCtrlRight = controllerH.rightPressed;
			prevCtrlSpace = controllerH.spacePressed;
			prevCtrlNum1 = controllerH.num1Pressed;
			prevCtrlNum2 = controllerH.num2Pressed;
			prevCtrlNum3 = controllerH.num3Pressed;
			prevCtrlE = controllerH.ePressed;
			prevCtrlB = controllerH.bPressed;
			prevCtrlC = controllerH.cPressed;
			prevCtrlV = controllerH.vPressed;
			prevCtrlX = controllerH.xPressed;
			prevCtrlM = controllerH.mPressed;
			prevCtrlShift = controllerH.shiftPressed;
		}
		 // Check if the game is paused, but only if the power-up menu is not open
		if (gamePaused && !survivalPowerUpMenuOpen && !settingsMenuOpen) {
			update();
		}
		player.update();
		if (activeHero != null && activeHero.isActivePlayer) {
			activeHero.x = player.x;
			activeHero.y = player.y;
			activeHero.health = player.health;
			activeHero.direction = player.direction;
			activeHero.syncControlledAttack(player.isAttacking, player.attackAnimationFrame, 6);
		}
		updateHeroAbilityEffects();
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
			for (entity.Troop troop : troops) troop.beginCharge();
		}
		if (keyH.vPressed) {
			for (entity.Troop t1 : troops) {
				t1.mode = entity.Troop.Mode.DEFEND;
			}
		}

		keyH.fPressedLastFrame = keyH.fPressed;
		keyH.qPressedLastFrame = keyH.qPressed;

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

		// Active heroes update cooldowns/animation without AI; companions use AI.
		if (activeHero != null && activeHero.isActivePlayer) activeHero.health = player.health;
		for (Hero hero : heroes) {
			if (hero.isRecruited) hero.activePlayerReference = player;
			hero.update();
		}
		if (activeHero != null && activeHero.isActivePlayer) player.health = activeHero.health;

		updateMinimapZoom(1f / FPS);   // FPS = 60, so this advances zoom by a 60th of a second each tick
		updateCamera();
		
	}

	public boolean isTileBlocked(int worldX, int worldY) {
		return tileM.isBlocked(worldX, worldY);
	}

	public boolean isCollidingWithAnyEntity(float nextX, float nextY, Object self) {
		int padding = 4;
		Rectangle nextRect = new Rectangle((int)Math.floor(nextX) + padding, (int)Math.floor(nextY) + padding, tileSize - padding * 2, tileSize - padding * 2);

		// Check player (unchanged)
		if (self != player) {
			Rectangle playerRect = new Rectangle((int)Math.floor(player.x) + padding, (int)Math.floor(player.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
			if (nextRect.intersects(playerRect)) {
				return true;
			}
		}

		// Check enemies (unchanged)
		for (int i = 0; i < enemies.size(); i++) {
			Enemy enemy = enemies.get(i);
			if (enemy != null && enemy != self && !enemy.dead) {
				Rectangle enemyRect = new Rectangle((int)Math.floor(enemy.x) + padding, (int)Math.floor(enemy.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(enemyRect)) {
					return true;
				}
			}
		}

		// Check troops (unchanged)
		for (int i = 0; i < troops.size(); i++) {
			entity.Troop troop = troops.get(i);
			if (troop != null && troop != self && troop.health > 0) {
				Rectangle troopRect = new Rectangle((int)Math.floor(troop.x) + padding, (int)Math.floor(troop.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(troopRect)) {
					return true;
				}
			}
		}

		// NEW — check heroes, so troops/enemies (and heroes moving past each other) can't walk through them
		for (int i = 0; i < heroes.size(); i++) {
			Hero hero = heroes.get(i);
			if (hero != null && hero != self) {
				Rectangle heroRect = new Rectangle((int)Math.floor(hero.x) + padding, (int)Math.floor(hero.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
				if (nextRect.intersects(heroRect)) {
					return true;
				}
			}
		}

		return false;
	}

	/** Player-specific movement check — deliberately excludes troops and heroes so
	    the player can always walk through their own allies, never gets boxed in. */
	public boolean isCollidingForPlayerMovement(float nextX, float nextY) {
	    int padding = 4;
	    Rectangle nextRect = new Rectangle((int)Math.floor(nextX) + padding, (int)Math.floor(nextY) + padding, tileSize - padding * 2, tileSize - padding * 2);

	    for (int i = 0; i < enemies.size(); i++) {
	        Enemy enemy = enemies.get(i);
	        if (enemy != null && !enemy.dead) {
	            Rectangle enemyRect = new Rectangle((int)Math.floor(enemy.x) + padding, (int)Math.floor(enemy.y) + padding, tileSize - padding * 2, tileSize - padding * 2);
	            if (nextRect.intersects(enemyRect)) return true;
	        }
	    }
	    return false; // troops and heroes intentionally NOT checked — player walks through allies freely
	}

	// Backward compatibility for int coordinates
	public boolean isCollidingWithAnyEntity(int nextX, int nextY, Object self) {
		return isCollidingWithAnyEntity((float)nextX, (float)nextY, self);
	}

	public java.awt.Point findOpenSpawnSpace(int startX, int startY, Object self) {
		return findOpenSpawnSpace(startX, startY, self, 150); // backward-compatible default
	}

	public java.awt.Point findOpenSpawnSpace(int startX, int startY, Object self, int maxRadius) {
		int step = 8;

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

		// --- Fullscreen scaling: keep internal resolution (screenWidth x screenHeight)
		// while stretching/letterboxing to fill the actual window size.
		double scaleX = getWidth() / (double) screenWidth;
		double scaleY = getHeight() / (double) screenHeight;
		double scale = Math.min(scaleX, scaleY); // uniform scale, preserves aspect ratio
		int offsetX = (int) ((getWidth() - screenWidth * scale) / 2);
		int offsetY = (int) ((getHeight() - screenHeight * scale) / 2);

		java.awt.geom.AffineTransform oldTransform = g2.getTransform();
		g2.translate(offsetX, offsetY);
		g2.scale(scale, scale);

		if (!gameStarted) {
			drawStartMenu(g2);
			if (gameCrashed) {
				drawCrashMenu(g2);
			}
			g2.setTransform(oldTransform); // restore before g2.dispose()
			g2.dispose();
			return;
		}

		if (gameMode == GameMode.CAMPAIGN && currentLayer == GameLayer.WORLD_MAP) {
			drawCampaignWorld(g2);
			if (gamePaused) drawPauseMenu(g2);
			if (gameCrashed) drawCrashMenu(g2);
			drawCampaignContextMenu(g2);
			drawCheatMenu(g2);
			g2.setTransform(oldTransform);
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
		java.util.List<HeroAbilityEffect> heroEffectsSnapshot = new java.util.ArrayList<>(heroAbilityEffects);

		
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
		for (HeroAbilityEffect effect : heroEffectsSnapshot) {
			effect.draw(g2, (int) cameraX, (int) cameraY);
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
		// Draw canonical local projections before interaction landmarks and companions.
		drawCampaignLocalActors(g2);
		drawCampaignLocalPlaceholders(g2);
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
		if (activeHero == null || !activeHero.isActivePlayer) {
			player.draw(g2, (int)cameraX,(int) cameraY);
		}
		tileM.drawDecorationFront(g2, (int)cameraX, (int)cameraY, player.y); // trees below player draw last (in front)
		drawWaveSpawnArea(g2);
		drawMapLinks(g2);
		drawMiniMap(g2);
		drawPlayerStats(g2);
		if (gameMode == GameMode.CAMPAIGN && activeCampaignBattle != null) {
			g2.setColor(new Color(0, 0, 0, 190));
			g2.fillRoundRect(180, 12, 410, 30, 8, 8);
			g2.setColor(Color.YELLOW);
			g2.drawString("Strategic battle " + activeCampaignBattle.battleId
				+ " - fight, then press R to reconcile exact participants", 194, 32);
		}
		if (currentLayer == GameLayer.DEPLOYMENT) {
			drawDeploymentScreen(g2);
		} else if (gamePaused) {
			if(survivalPowerUpMenuOpen) {
				drawPowerUpMenu(g2);
			}else if (settingsMenuOpen) {
				drawSettingsMenu(g2);
			} else {
				drawPauseMenu(g2);
			}
		}
		if (pendingPortalHero != null) {
			g2.setColor(new Color(25, 10, 45, 220));
			g2.fillRoundRect(screenWidth / 2 - 190, 55, 380, 34, 10, 10);
			g2.setColor(new Color(220, 170, 255));
			g2.setFont(new Font("SansSerif", Font.BOLD, 14));
			String portalPrompt = "Click the world to place the portal destination";
			g2.drawString(portalPrompt, (screenWidth - g2.getFontMetrics().stringWidth(portalPrompt)) / 2, 77);
		}
		if (gameCrashed) {
			drawCrashMenu(g2);
		}
		drawDebugOverlay(g2);
		drawCampaignContextMenu(g2);
		drawCheatMenu(g2);

		g2.setTransform(oldTransform); // restore before g2.dispose()
		g2.dispose();
	}

	private void drawCampaignContextMenu(Graphics2D g2) {
		if (campaignContextMenu == CampaignContextMenu.NONE || campaignSession == null || campaignSnapshot == null) return;
		int x = 145, y = 70, w = 478, h = 410;
		g2.setColor(new Color(0, 0, 0, 195)); g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setColor(new Color(20, 28, 36)); g2.fillRoundRect(x, y, w, h, 16, 16);
		g2.setColor(new Color(225, 190, 105)); g2.drawRoundRect(x, y, w, h, 16, 16);
		campaignContextButtons.clear();
		CampaignSnapshot.SettlementView settlement = campaignSnapshot.findSettlement(campaignSnapshot.player.settlementId);
		g2.setFont(new Font("Serif", Font.BOLD, 22)); g2.setColor(new Color(235, 205, 130));
		String title = switch (campaignContextMenu) {
			case MARKET -> "MARKET STALL";
			case NOTICE_BOARD -> "NOTICE BOARD";
			case PERSON -> "PERSON";
			case ARMY -> "ARMY";
			case CARAVAN -> "MERCHANT CARAVAN";
			default -> "SETTLEMENT";
		};
		g2.drawString(title, x + 20, y + 30);
		g2.setFont(new Font("Monospaced", Font.PLAIN, 11)); g2.setColor(Color.WHITE);
		if (settlement != null && (campaignContextMenu == CampaignContextMenu.SETTLEMENT
				|| campaignContextMenu == CampaignContextMenu.MARKET
				|| campaignContextMenu == CampaignContextMenu.NOTICE_BOARD)) {
			g2.drawString(settlement.name, x + 20, y + 50);
		}
		if (campaignContextMenu == CampaignContextMenu.SETTLEMENT && settlement != null) {
			g2.drawString("Population " + settlement.population + " | Households " + settlement.households, x + 20, y + 75);
			g2.drawString("Treasury " + settlement.treasury + "c | Food security " + Math.round(settlement.foodSecurity * 100) + "%", x + 20, y + 94);
			g2.drawString("Security " + Math.round(settlement.security * 100) + "% | Unrest " + Math.round(settlement.unrest * 100) + "%", x + 20, y + 113);
			addContextButton(g2, "OPEN_MARKET", "Open market: buy and sell", x + 28, y + 145, 200, 48);
			addContextButton(g2, "OPEN_BOARD", "Open notice board", x + 250, y + 145, 200, 48);
			addContextButton(g2, "ENTER_LOCAL", "Enter local settlement", x + 28, y + 210, 200, 48);
			addContextButton(g2, "OPEN_INFO", "Open encyclopedia", x + 250, y + 210, 200, 48);
			if (settlement.realmId != null && campaignSnapshot.player.affiliatedRealmId == null) {
				addContextButton(g2, "JOIN_MERCENARY", "Join realm as mercenary", x + 28, y + 275, 200, 42);
				addContextButton(g2, "JOIN_LORD", "Pledge allegiance as lord", x + 250, y + 275, 200, 42);
			}
		} else if (campaignContextMenu == CampaignContextMenu.MARKET && settlement != null) {
			GoodType good = GoodType.values()[Math.floorMod(marketGoodIndex, GoodType.values().length)];
			int stock = campaignSession.getWorld().geography.getSettlement(settlement.id).publicStockpile.getQuantity(good);
			int cargo = campaignSession.getPlayerState().cargo.getQuantity(good);
			long price = campaignSession.getWorld().geography.getSettlement(settlement.id).market.getLastPrice(good);
			g2.setFont(new Font("SansSerif", Font.BOLD, 20)); g2.drawString(good.toString(), x + 20, y + 90);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
			g2.drawString("Price " + Math.max(1, price) + "c | Market stock " + stock + " | Your cargo " + cargo, x + 20, y + 116);
			g2.drawString("Coins " + campaignSnapshot.player.coins + " | Capacity " + campaignSnapshot.player.cargoUsed + "/" + campaignSnapshot.player.cargoCapacity, x + 20, y + 136);
			addContextButton(g2, "PREV_GOOD", "Previous good", x + 20, y + 165, 135, 42);
			addContextButton(g2, "NEXT_GOOD", "Next good", x + 165, y + 165, 135, 42);
			addContextButton(g2, "BUY_1", "Buy 1", x + 20, y + 225, 100, 42);
			addContextButton(g2, "BUY_5", "Buy 5", x + 130, y + 225, 100, 42);
			addContextButton(g2, "SELL_1", "Sell 1", x + 250, y + 225, 100, 42);
			addContextButton(g2, "SELL_5", "Sell 5", x + 360, y + 225, 90, 42);
		} else if (campaignContextMenu == CampaignContextMenu.NOTICE_BOARD) {
			int row = 0;
			for (CampaignSnapshot.ContractView contract : campaignSnapshot.contracts) {
				if (!"OPEN".equals(contract.status) || contract.issuerSettlementId != campaignSnapshot.player.settlementId) continue;
				int cy = y + 72 + row * 82;
				g2.setColor(Color.WHITE); g2.drawString(contract.objective, x + 20, cy);
				g2.setColor(Color.LIGHT_GRAY); g2.drawString("To " + contract.destinationName + " | " + contract.rewardCoins + "c | " + contract.remainingDays() + " days", x + 20, cy + 17);
				addContextButton(g2, "ACCEPT_" + contract.id, "Accept contract", x + 310, cy + 27, 140, 34);
				if (++row >= 3) break;
			}
			if (row == 0) g2.drawString("No new contracts are posted here.", x + 20, y + 85);
		} else if (campaignContextMenu == CampaignContextMenu.PERSON) {
			world.Person person = selectedCampaignActorId == null ? null
					: campaignSession.getWorld().people.get(selectedCampaignActorId);
			if (person == null) {
				g2.drawString("This person is no longer present.", x + 20, y + 82);
			} else {
				long minute = campaignSnapshot.worldMinute;
				g2.setFont(new Font("Serif", Font.BOLD, 20));
				g2.drawString(person.givenName + " " + person.familyName, x + 20, y + 78);
				g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
				g2.drawString("Age " + person.getAge(minute) + " | " + person.type + " | " + person.currentActivity, x + 20, y + 102);
				String work = person.employerId == null ? "No current employer" : "Workplace #" + person.employerId;
				g2.drawString(work + " | Wealth " + Math.round(person.wealth.netWorth) + "c", x + 20, y + 121);
				g2.drawString("Needs: food " + percent(person.needs.foodSecurity) + "  safety " + percent(person.needs.safety)
						+ "  social " + percent(person.needs.socialBelonging), x + 20, y + 140);
				g2.drawString("Traits: " + describePersonality(person), x + 20, y + 159);
				if (person.spouseId != null) {
					world.Person spouse = campaignSession.getWorld().people.get(person.spouseId);
					if (spouse != null) g2.drawString("Family: spouse " + spouse.givenName + " " + spouse.familyName, x + 20, y + 178);
				}
				addContextButton(g2, "TALK_PERSON", "Talk", x + 24, y + 218, 128, 44);
				addContextButton(g2, "GIFT_GRAIN", "Give 1 grain", x + 174, y + 218, 128, 44);
				addContextButton(g2, "RECRUIT_PERSON", "Invite to party", x + 324, y + 218, 128, 44);
			}
		} else if (campaignContextMenu == CampaignContextMenu.ARMY) {
			world.Army army = selectedCampaignActorId == null ? null
					: campaignSession.getWorld().armies.get(selectedCampaignActorId);
			if (army == null) {
				g2.drawString("This army is no longer active.", x + 20, y + 82);
			} else {
				world.Person commander = campaignSession.getWorld().people.get(army.commanderPersonId);
				CampaignSnapshot.RealmView realm = campaignSnapshot.findRealm(army.realmId);
				int strength = campaignSession.getSimulation().getMilitarySystem().strength(army);
				g2.setFont(new Font("Serif", Font.BOLD, 20));
				g2.drawString((realm == null ? "Unknown" : realm.name) + " Army #" + army.id, x + 20, y + 78);
				g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
				g2.drawString("Commander: " + personName(commander) + " | Strength " + strength, x + 20, y + 103);
				g2.drawString("Order " + army.order + " | State " + army.state + " | Regiments " + army.regimentIds.size(), x + 20, y + 122);
				g2.drawString("Morale " + Math.round(army.morale) + "% | Fatigue " + Math.round(army.fatigue)
						+ "% | Days unfed " + army.daysWithoutFood, x + 20, y + 141);
				g2.drawString("Supplies: grain " + army.supplies.getQuantity(GoodType.GRAIN)
						+ " | vegetables " + army.supplies.getQuantity(GoodType.VEGETABLES), x + 20, y + 160);
				addContextButton(g2, "SPEAK_COMMANDER", "Speak to commander", x + 20, y + 210, 132, 44);
				addContextButton(g2, "SUPPLY_ARMY", "Donate 5 grain", x + 173, y + 210, 132, 44);
				addContextButton(g2, "VIEW_ARMY_PEOPLE", "View soldiers", x + 326, y + 210, 132, 44);
			}
		} else if (campaignContextMenu == CampaignContextMenu.CARAVAN) {
			world.Caravan caravan = selectedCampaignActorId == null ? null
					: campaignSession.getWorld().caravans.get(selectedCampaignActorId);
			if (caravan == null) {
				g2.drawString("This caravan has moved on.", x + 20, y + 82);
			} else {
				world.Person leader = campaignSession.getWorld().people.get(caravan.leaderPersonId);
				g2.setFont(new Font("Serif", Font.BOLD, 20));
				g2.drawString("Caravan led by " + personName(leader), x + 20, y + 78);
				g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
				g2.drawString("State " + caravan.state + " | Guards " + caravan.guardPersonIds.size(), x + 20, y + 103);
				g2.drawString("Cargo " + caravan.cargoQuantity() + "/" + caravan.carryingCapacity
						+ " | Primary " + (caravan.primaryCargo() == null ? "none" : caravan.primaryCargo()), x + 20, y + 122);
				g2.drawString("Cash " + caravan.cash.copperCoins + "c | Lifetime profit " + caravan.lifetimeProfit
						+ "c | Trips " + caravan.completedTrips, x + 20, y + 141);
				addContextButton(g2, "SPEAK_MERCHANT", "Ask about trade", x + 24, y + 200, 200, 44);
				addContextButton(g2, "OPEN_MARKET", "Open local market", x + 246, y + 200, 206, 44);
			}
		}
		g2.setColor(new Color(170, 220, 170)); g2.drawString(campaignContextMessage, x + 20, y + h - 42);
		addContextButton(g2, "CLOSE", "Close", x + w - 105, y + h - 34, 85, 26);
	}

	private String percent(double value) {
		return Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0) + "%";
	}

	private String personName(world.Person person) {
		return person == null ? "Unknown" : person.givenName + " " + person.familyName;
	}

	private String describePersonality(world.Person person) {
		java.util.List<java.util.Map.Entry<String, Double>> traits = new java.util.ArrayList<>();
		traits.add(java.util.Map.entry("ambitious", person.personality.ambition));
		traits.add(java.util.Map.entry("brave", person.personality.bravery));
		traits.add(java.util.Map.entry("compassionate", person.personality.compassion));
		traits.add(java.util.Map.entry("honorable", person.personality.honor));
		traits.add(java.util.Map.entry("loyal", person.personality.loyalty));
		traits.add(java.util.Map.entry("sociable", person.personality.sociability));
		traits.sort(java.util.Map.Entry.<String, Double>comparingByValue().reversed());
		return traits.get(0).getKey() + ", " + traits.get(1).getKey();
	}

	private void addContextButton(Graphics2D g2, String id, String label, int x, int y, int w, int h) {
		Rectangle rect = new Rectangle(x, y, w, h); campaignContextButtons.put(id, rect);
		g2.setColor(new Color(58, 76, 91)); g2.fillRoundRect(x, y, w, h, 8, 8);
		g2.setColor(new Color(135, 165, 184)); g2.drawRoundRect(x, y, w, h, 8, 8);
		g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.BOLD, 11));
		g2.drawString(label, x + (w - g2.getFontMetrics().stringWidth(label)) / 2, y + h / 2 + 4);
	}

	private void handleCampaignContextClick(java.awt.Point point) {
		String clicked = null;
		for (java.util.Map.Entry<String, Rectangle> entry : campaignContextButtons.entrySet()) if (entry.getValue().contains(point)) { clicked = entry.getKey(); break; }
		if (clicked == null) return;
		if ("CLOSE".equals(clicked)) { campaignContextMenu = CampaignContextMenu.NONE; return; }
		if ("OPEN_MARKET".equals(clicked)) { campaignContextMenu = CampaignContextMenu.MARKET; return; }
		if ("OPEN_BOARD".equals(clicked)) { campaignContextMenu = CampaignContextMenu.NOTICE_BOARD; return; }
		if ("OPEN_INFO".equals(clicked)) { campaignContextMenu = CampaignContextMenu.NONE; activeCampaignTab = CampaignTab.ENCYCLOPEDIA; return; }
		if ("ENTER_LOCAL".equals(clicked)) { campaignContextMenu = CampaignContextMenu.NONE; enterCampaignLocalView(); return; }
		if ("VIEW_ARMY_PEOPLE".equals(clicked)) {
			campaignContextMenu = CampaignContextMenu.NONE;
			activeCampaignTab = CampaignTab.ENCYCLOPEDIA;
			campaignContextMessage = "Army personnel are listed with the simulated population";
			return;
		}
		if ("SPEAK_COMMANDER".equals(clicked)) {
			world.Army army = selectedCampaignActorId == null ? null : campaignSession.getWorld().armies.get(selectedCampaignActorId);
			campaignContextMessage = army == null ? "The army has moved on"
					: "The commander reports: " + army.order + ", morale " + Math.round(army.morale) + "%";
			return;
		}
		if ("SPEAK_MERCHANT".equals(clicked)) {
			world.Caravan caravan = selectedCampaignActorId == null ? null : campaignSession.getWorld().caravans.get(selectedCampaignActorId);
			campaignContextMessage = caravan == null ? "The caravan has moved on"
					: "The merchant has completed " + caravan.completedTrips + " trips and carries "
						+ (caravan.primaryCargo() == null ? "no cargo" : caravan.primaryCargo().toString().toLowerCase());
			return;
		}
		if ("PREV_GOOD".equals(clicked)) { marketGoodIndex--; return; }
		if ("NEXT_GOOD".equals(clicked)) { marketGoodIndex++; return; }
		GoodType good = GoodType.values()[Math.floorMod(marketGoodIndex, GoodType.values().length)];
		world.command.CommandResult result = null;
		if ("SUPPLY_ARMY".equals(clicked) && selectedCampaignActorId != null) {
			result = campaignSession.supplyArmy(selectedCampaignActorId, 5);
		}
		if ("TALK_PERSON".equals(clicked) && selectedCampaignActorId != null) {
			result = campaignSession.talkToPerson(selectedCampaignActorId);
		}
		if ("GIFT_GRAIN".equals(clicked) && selectedCampaignActorId != null) {
			result = campaignSession.giveFoodToPerson(selectedCampaignActorId);
		}
		if ("RECRUIT_PERSON".equals(clicked) && selectedCampaignActorId != null) {
			result = campaignSession.recruitCompanion(selectedCampaignActorId);
		}
		long settlementId = campaignSnapshot.player.settlementId;
		CampaignSnapshot.SettlementView currentSettlement = campaignSnapshot.findSettlement(settlementId);
		if ("JOIN_MERCENARY".equals(clicked) && currentSettlement != null && currentSettlement.realmId != null) {
			result = campaignSession.joinKingdom(currentSettlement.realmId, world.PlayerCampaignState.KingdomRole.MERCENARY);
		}
		if ("JOIN_LORD".equals(clicked) && currentSettlement != null && currentSettlement.realmId != null) {
			result = campaignSession.joinKingdom(currentSettlement.realmId, world.PlayerCampaignState.KingdomRole.LORD);
		}
		if ("BUY_1".equals(clicked)) result = campaignSession.buyFromSettlement(settlementId, good, 1);
		if ("BUY_5".equals(clicked)) result = campaignSession.buyFromSettlement(settlementId, good, 5);
		if ("SELL_1".equals(clicked)) result = campaignSession.sellToSettlement(settlementId, good, 1);
		if ("SELL_5".equals(clicked)) result = campaignSession.sellToSettlement(settlementId, good, 5);
		if (clicked.startsWith("ACCEPT_")) result = campaignSession.acceptContract(Long.parseLong(clicked.substring(7)));
		if (result != null) {
			campaignSnapshot = campaignSession.getSnapshot();
			campaignContextMessage = result.accepted && (result.message == null || result.message.isBlank())
					? "Action completed" : result.message;
		}
	}

	private void drawCheatMenu(Graphics2D g2) {
		if (!cheatMenuOpen || campaignSession == null || campaignSnapshot == null) return;
		g2.setColor(new Color(0, 0, 0, 205));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		int x = 40, y = 35, width = screenWidth - 80, height = screenHeight - 70;
		g2.setColor(new Color(24, 30, 38));
		g2.fillRoundRect(x, y, width, height, 16, 16);
		g2.setColor(new Color(230, 178, 65));
		g2.drawRoundRect(x, y, width, height, 16, 16);
		g2.setFont(new Font("Serif", Font.BOLD, 24));
		g2.drawString("CHEAT & TEST MENU", x + 18, y + 30);
		g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
		g2.setColor(Color.WHITE);
		world.geography.Settlement settlement = cheatSettlement();
		world.Realm realm = cheatRealm();
		world.Person person = cheatPerson();
		g2.drawString("Settlement: " + (settlement == null ? "none (select one on map)" : settlement.name), x + 18, y + 50);
		g2.drawString("Realm: " + (realm == null ? "none" : realm.name) + "   Person: "
			+ (person == null ? "none" : person.givenName + " " + person.familyName), x + 18, y + 66);
		g2.setColor(new Color(170, 220, 170));
		g2.drawString(cheatStatus, x + 18, y + 84);

		CheatAction[] actions = CheatAction.values();
		cheatButtonHitboxes.clear();
		int columns = 3, buttonW = 205, buttonH = 42, gapX = 10, gapY = 9;
		int startY = y + 100;
		for (int i = 0; i < actions.length; i++) {
			int col = i % columns, row = i / columns;
			Rectangle rect = new Rectangle(x + 18 + col * (buttonW + gapX), startY + row * (buttonH + gapY), buttonW, buttonH);
			cheatButtonHitboxes.put(actions[i], rect);
			g2.setColor(new Color(58, 72, 86));
			g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8);
			g2.setColor(new Color(125, 153, 174));
			g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8);
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("SansSerif", Font.BOLD, 11));
			String label = cheatLabel(actions[i]);
			g2.drawString(label, rect.x + (rect.width - g2.getFontMetrics().stringWidth(label)) / 2, rect.y + 25);
		}
		g2.setColor(Color.LIGHT_GRAY);
		g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
		g2.drawString("F1 closes this menu. Changes are immediate and intentionally bypass normal costs and laws.", x + 18, y + height - 12);
	}

	private String cheatLabel(CheatAction action) {
		return switch (action) {
			case NEXT_REALM -> "Next target kingdom";
			case NEXT_PERSON -> "Next target person";
			case ADD_FOOD -> "+500 settlement food";
			case ADD_GOLD -> "+10,000 player gold";
			case ADD_CARGO -> "+25 every cargo good";
			case HEAL_PLAYER -> "Heal player";
			case SPAWN_ARMY -> "Spawn 20-person army";
			case ADD_TROOPS -> "Add 10 troops";
			case SPAWN_CARAVAN -> "Spawn loaded caravan";
			case PLAYER_KING -> "Make player king";
			case SET_KING -> "Make target person king";
			case CHANGE_OWNER -> "Give settlement to realm";
			case APPOINT_LORD -> "Appoint settlement lord";
			case TOGGLE_WAR -> "Toggle war with next realm";
			case TRIGGER_CRIME -> "Trigger local crime";
			case COMPLETE_CONTRACTS -> "Complete active contracts";
			case ADVANCE_DAY -> "Advance one day";
			case ADVANCE_MONTH -> "Advance one month";
		};
	}

	private void handleCheatMenuClick(java.awt.Point point) {
		for (java.util.Map.Entry<CheatAction, Rectangle> entry : cheatButtonHitboxes.entrySet()) {
			if (!entry.getValue().contains(point)) continue;
			runCheat(entry.getKey());
			repaint();
			return;
		}
	}

	private void runCheat(CheatAction action) {
		if (campaignSession == null) return;
		java.util.List<world.Realm> realms = cheatRealms();
		java.util.List<world.Person> people = cheatPeople();
		if (action == CheatAction.NEXT_REALM) {
			cheatRealmIndex = realms.isEmpty() ? 0 : (cheatRealmIndex + 1) % realms.size();
			cheatStatus = "Target realm changed";
			return;
		}
		if (action == CheatAction.NEXT_PERSON) {
			cheatPersonIndex = people.isEmpty() ? 0 : (cheatPersonIndex + 1) % people.size();
			cheatStatus = "Target person changed";
			return;
		}
		world.CheatService cheats = campaignSession.getCheats();
		world.geography.Settlement settlement = cheatSettlement();
		world.Realm realm = cheatRealm();
		world.Person person = cheatPerson();
		long settlementId = settlement == null ? -1 : settlement.id;
		long realmId = realm == null ? -1 : realm.id;
		world.command.CommandResult result;
		switch (action) {
			case ADD_FOOD -> result = cheats.addFood(settlementId, 500);
			case ADD_GOLD -> result = cheats.addPlayerGold(10_000);
			case ADD_CARGO -> result = cheats.addPlayerCargo(25);
			case HEAL_PLAYER -> result = cheats.healPlayer();
			case SPAWN_ARMY -> result = cheats.spawnArmy(settlementId, 20);
			case ADD_TROOPS -> result = cheats.addTroops(settlementId, 10);
			case SPAWN_CARAVAN -> result = cheats.spawnCaravan(settlementId);
			case PLAYER_KING -> result = cheats.makePlayerKing(realmId);
			case SET_KING -> result = person == null ? world.command.CommandResult.rejected("CHEAT", "No target person")
				: cheats.setKing(realmId, person.id);
			case CHANGE_OWNER -> result = cheats.changeSettlementOwner(settlementId, realmId);
			case APPOINT_LORD -> result = person == null ? world.command.CommandResult.rejected("CHEAT", "No target person")
				: cheats.appointLord(settlementId, person.id);
			case TOGGLE_WAR -> {
				world.Realm other = realms.stream().filter(value -> value.id != realmId).findFirst().orElse(null);
				result = other == null ? world.command.CommandResult.rejected("CHEAT", "Need another realm")
					: cheats.toggleWar(realmId, other.id);
			}
			case TRIGGER_CRIME -> result = cheats.triggerCrime(settlementId);
			case COMPLETE_CONTRACTS -> result = cheats.completePlayerContracts();
			case ADVANCE_DAY -> { campaignSession.advanceDaysForTesting(1); result = world.command.CommandResult.accepted(); }
			case ADVANCE_MONTH -> { campaignSession.advanceDaysForTesting(30); result = world.command.CommandResult.accepted(); }
			default -> { return; }
		}
		campaignSession.refreshAfterCheat();
		campaignSnapshot = campaignSession.getSnapshot();
		cheatStatus = result.accepted ? (result.message.isEmpty() ? action.toString() + " applied" : result.message)
			: "Rejected: " + result.message;
	}

	private world.geography.Settlement cheatSettlement() {
		if (campaignSession == null) return null;
		long id = selectedCampaignSettlementId != null ? selectedCampaignSettlementId
			: campaignSession.getPlayerState().currentSettlementId;
		return campaignSession.getWorld().geography.getSettlement(id);
	}

	private java.util.List<world.Realm> cheatRealms() {
		if (campaignSession == null) return java.util.List.of();
		java.util.List<world.Realm> values = new java.util.ArrayList<>(campaignSession.getWorld().realms.values());
		values.sort(java.util.Comparator.comparingLong(value -> value.id));
		return values;
	}

	private world.Realm cheatRealm() {
		java.util.List<world.Realm> values = cheatRealms();
		return values.isEmpty() ? null : values.get(Math.floorMod(cheatRealmIndex, values.size()));
	}

	private java.util.List<world.Person> cheatPeople() {
		if (campaignSession == null) return java.util.List.of();
		world.geography.Settlement settlement = cheatSettlement();
		java.util.List<world.Person> values = new java.util.ArrayList<>();
		for (world.Person person : campaignSession.getWorld().people.values()) {
			if (!person.alive) continue;
			if (settlement == null || (person.currentSettlementId != null
					&& person.currentSettlementId == settlement.id)) values.add(person);
		}
		values.sort(java.util.Comparator.comparingLong(value -> value.id));
		return values;
	}

	private world.Person cheatPerson() {
		java.util.List<world.Person> values = cheatPeople();
		return values.isEmpty() ? null : values.get(Math.floorMod(cheatPersonIndex, values.size()));
	}

	private void drawDebugOverlay(Graphics2D g2) {
		if (!debugOverlayVisible) return;

		g2.setColor(new Color(0, 0, 0, 180));
		g2.fillRect(screenWidth - 280, 0, 280, screenHeight);
		g2.setColor(Color.green);
		g2.setFont(new Font("Monospaced", Font.PLAIN, 12));

		int y = 20;
		g2.drawString("=== DEBUG (F3) ===", screenWidth - 270, y); y += 20;
		g2.drawString("Layer: " + currentLayer, screenWidth - 270, y); y += 16;
		g2.drawString("Player strength: " + playerParty.getTotalStrength(), screenWidth - 270, y); y += 16;
		g2.drawString("Gold carried: " + playerParty.carriedGold, screenWidth - 270, y); y += 20;

		g2.drawString("AI Parties (" + partyManager.aiParties.size() + "):", screenWidth - 270, y); y += 16;
		for (party.Party p : partyManager.aiParties) {
			String factionName = p.faction != null ? p.faction.name : "none";
			String relation = p.faction != null && playerParty.faction != null
				? p.faction.relations.getOrDefault(playerParty.faction, kingdom.Kingdom.DiplomaticRelation.PEACE).toString()
				: "";
			g2.drawString(String.format("  %s [%s] str=%d %s", factionName, p.state, p.getTotalStrength(), relation),
				screenWidth - 270, y);
			y += 14;
			if (y > screenHeight - 140) {
				g2.drawString("  ...(more)", screenWidth - 270, y);
				break;
			}
		}

		y += 10;
		g2.drawString("Kingdoms:", screenWidth - 270, y); y += 16;
		for (kingdom.Kingdom k : kingdomSim.allKingdoms) {
			g2.drawString(String.format("  %s: treasury=%d wars=%d", k.name, k.treasury, k.activeWars.size()),
				screenWidth - 270, y);
			y += 14;
		}

		y += 10;
		g2.drawString("Controls:", screenWidth - 270, y); y += 16;
		g2.drawString("F4: spawn weak enemy party", screenWidth - 270, y); y += 14;
		g2.drawString("F5: spawn strong enemy party", screenWidth - 270, y); y += 14;
		g2.drawString("F6: fast-forward 6 months", screenWidth - 270, y); y += 14;
		g2.drawString("F7: toggle war with nearest faction", screenWidth - 270, y); y += 14;
	}

	private void spawnDebugParty(double strengthRatioToPlayer) {
		if (kingdomSim.allKingdoms.isEmpty()) return;
		if (playerParty.faction == null) {
			playerParty.faction = kingdomSim.allKingdoms.get(0);
		}

		party.Party p = new party.Party();
		kingdom.Kingdom hostileFaction = kingdomSim.allKingdoms.stream()
			.filter(k -> k != playerParty.faction)
			.findFirst()
			.orElse(playerParty.faction);
		p.faction = hostileFaction;
		p.faction.relations.put(playerParty.faction, kingdom.Kingdom.DiplomaticRelation.WAR);
		p.x = player.x + 150;
		p.y = player.y;

		int targetStrength = (int) (playerParty.getTotalStrength() * strengthRatioToPlayer);
		for (int i = 0; i < Math.max(1, targetStrength); i++) {
			entity.Troop t = new entity.Troop(this, p.x, p.y, entity.Troop.Role.MELEE);
			p.troops.add(t);
		}
		partyManager.aiParties.add(p);
		System.out.println("Debug: spawned party with strength " + p.getTotalStrength() + " (target ratio " + strengthRatioToPlayer + ")");
	}

	private void toggleWarWithNearestFaction() {
		if (playerParty.faction == null || kingdomSim.allKingdoms.isEmpty()) return;
		kingdom.Kingdom target = kingdomSim.allKingdoms.stream()
			.filter(k -> k != playerParty.faction)
			.findFirst()
			.orElse(null);
		if (target == null) return;

		boolean atWar = playerParty.faction.relations.get(target) == kingdom.Kingdom.DiplomaticRelation.WAR;
		if (atWar) {
			playerParty.faction.relations.put(target, kingdom.Kingdom.DiplomaticRelation.PEACE);
			target.relations.put(playerParty.faction, kingdom.Kingdom.DiplomaticRelation.PEACE);
			System.out.println("Debug: peace with " + target.name);
		} else {
			kingdomSim.declareWar(playerParty.faction, target, kingdom.War.CasusBelli.CONQUEST);
			System.out.println("Debug: war with " + target.name);
		}
	}

	private void drawDeploymentScreen(Graphics2D g2) {
		g2.setColor(new Color(10, 10, 10, 235));
		g2.fillRect(0, 0, screenWidth, screenHeight);

		g2.setColor(Color.white);
		g2.setFont(new Font("Arial", Font.BOLD, 30));
		String title = pendingCaughtFleeing ? "You caught them fleeing!" : "Enemy party sighted!";
		g2.drawString(title, screenWidth/2 - g2.getFontMetrics().stringWidth(title)/2, 60);

		g2.setFont(new Font("Arial", Font.PLAIN, 18));
		int meleeCount = (int) playerParty.troops.stream().filter(t -> t.role == entity.Troop.Role.MELEE).count();
		int archerCount = (int) playerParty.troops.stream().filter(t -> t.role == entity.Troop.Role.ARCHER).count();
		g2.drawString("Your Forces:", 60, 120);
		g2.drawString("Melee: " + meleeCount, 80, 150);
		g2.drawString("Archers: " + archerCount, 80, 175);
		g2.drawString("Heroes: " + playerParty.heroes.size(), 80, 200);

		String enemyStrengthLabel = getApproximateStrengthLabel(pendingEnemyParty, playerParty);
		g2.drawString("Enemy Forces:", 400, 120);
		g2.drawString("Estimated strength: " + enemyStrengthLabel, 420, 150);
		g2.drawString("Troop count: ~" + roundToNearest(pendingEnemyParty != null ? pendingEnemyParty.troops.size() : 0, 3), 420, 175);

		g2.drawString("Choose Formation:", 60, 270);
		drawFormationButton(g2, formationLineButton, "Line", entity.Formation.Type.LINE);
		drawFormationButton(g2, formationWedgeButton, "Wedge", entity.Formation.Type.WEDGE);
		drawFormationButton(g2, formationCircleButton, "Circle", entity.Formation.Type.CIRCLE);
		drawFormationButton(g2, formationScatteredButton, "Scattered", entity.Formation.Type.SCATTERED);

		g2.setColor(new Color(40, 120, 40));
		g2.fillRoundRect(beginBattleButton.x, beginBattleButton.y, beginBattleButton.width, beginBattleButton.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawString("Begin Battle", beginBattleButton.x + 45, beginBattleButton.y + 32);

		boolean canRetreat = pendingEnemyParty == null || pendingEnemyParty.state != party.Party.State.PURSUING;
		g2.setColor(canRetreat ? new Color(120, 40, 40) : new Color(60, 60, 60));
		g2.fillRoundRect(retreatButton.x, retreatButton.y, retreatButton.width, retreatButton.height, 10, 10);
		g2.setColor(Color.white);
		String retreatLabel = canRetreat ? "Retreat" : "Retreat (risky — they're pursuing)";
		g2.drawString(retreatLabel, retreatButton.x + 20, retreatButton.y + 28);
	}

	private void drawFormationButton(Graphics2D g2, Rectangle btn, String label, entity.Formation.Type type) {
		boolean selected = selectedFormation == type;
		g2.setColor(selected ? new Color(80, 80, 160) : new Color(50, 50, 50));
		g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 8, 8);
		g2.setColor(Color.white);
		g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 8, 8);
		int textW = g2.getFontMetrics().stringWidth(label);
		g2.drawString(label, btn.x + (btn.width - textW) / 2, btn.y + 26);
	}

	private String getApproximateStrengthLabel(party.Party enemy, party.Party player) {
		double ratio = player.getTotalStrength() == 0 ? 1.0 : (double) enemy.getTotalStrength() / player.getTotalStrength();
		if (ratio < 0.7) return "Weak";
		if (ratio < 1.3) return "Moderate";
		if (ratio < 2.0) return "Strong";
		return "Overwhelming";
	}

	private int roundToNearest(int value, int nearest) {
		return Math.max(nearest, Math.round((float) value / nearest) * nearest);
	}

		private void handleCampaignMapClick(java.awt.Point point) {
			if (activeCampaignTab == CampaignTab.KINGDOM && campaignSession != null) {
				for (java.util.Map.Entry<String, Rectangle> entry : kingdomManagementHitboxes.entrySet()) {
					if (!entry.getValue().contains(point)) continue;
					String[] command = entry.getKey().split(":");
					campaignSession.adjustKingdomLaw(
							Government.LawType.valueOf(command[0]), Integer.parseInt(command[1]));
					campaignSnapshot = campaignSession.getSnapshot();
					repaint();
					return;
				}
			}
			for (java.util.Map.Entry<CampaignTab, Rectangle> entry : campaignTabHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					activeCampaignTab = entry.getKey();
					repaint();
					return;
				}
			}
			for (java.util.Map.Entry<Long, Rectangle> entry : campaignArmyHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					selectedCampaignActorId = entry.getKey();
					campaignContextMenu = CampaignContextMenu.ARMY;
					campaignContextMessage = "Inspect this army and its commander";
					repaint();
					return;
				}
			}
			for (java.util.Map.Entry<Long, Rectangle> entry : campaignCaravanHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					selectedCampaignActorId = entry.getKey();
					campaignContextMenu = CampaignContextMenu.CARAVAN;
					campaignContextMessage = "Inspect this merchant caravan";
					repaint();
					return;
				}
			}
			for (java.util.Map.Entry<Long, Rectangle> entry : campaignSettlementHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					selectedCampaignSettlementId = entry.getKey();
					if (campaignSnapshot != null && campaignSnapshot.player != null
							&& campaignSnapshot.player.settlementId == entry.getKey()) {
						campaignContextMenu = CampaignContextMenu.SETTLEMENT;
						campaignContextMessage = "You are currently at this settlement";
					}
					repaint();
					return;
				}
			}
		}

		private void drawCampaignWorld(Graphics2D g2) {
			CampaignSnapshot snapshot = campaignSnapshot;
			g2.setColor(new Color(16, 22, 29));
			g2.fillRect(0, 0, screenWidth, screenHeight);

			g2.setColor(new Color(222, 204, 155));
			g2.setFont(new Font("Serif", Font.BOLD, 28));
			g2.drawString("Chronicle Conquest - Campaign", 20, 34);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("TAB local | T travel | C contract | F1 cheats | F5 save | F9 load", 20, 55);

			if (snapshot == null) {
				g2.setColor(Color.WHITE);
				g2.drawString("Generating campaign world...", 20, 90);
				return;
			}

			final int mapX = 20;
			final int mapY = 72;
			final int mapW = 500;
			final int mapH = 468;
			g2.setColor(new Color(35, 52, 48));
			g2.fillRoundRect(mapX, mapY, mapW, mapH, 14, 14);
			g2.setColor(new Color(90, 110, 92));
			g2.drawRoundRect(mapX, mapY, mapW, mapH, 14, 14);

			double[] bounds = getCampaignBounds(snapshot);
			// Soft territory fields make the two regional clusters readable before
			// roads and symbols are drawn.
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				int x = campaignMapCoordinate(settlement.worldX, bounds[0], bounds[1], mapX, mapW);
				int y = campaignMapCoordinate(settlement.worldY, bounds[2], bounds[3], mapY, mapH);
				Color realmColor = colorForRealm(settlement.realmId);
				g2.setColor(new Color(realmColor.getRed(), realmColor.getGreen(), realmColor.getBlue(), 28));
				g2.fillOval(x - 78, y - 62, 156, 124);
			}
			for (CampaignSnapshot.RoadView road : snapshot.roads) {
				int x1 = campaignMapCoordinate(road.fromX, bounds[0], bounds[1], mapX, mapW);
				int y1 = campaignMapCoordinate(road.fromY, bounds[2], bounds[3], mapY, mapH);
				int x2 = campaignMapCoordinate(road.toX, bounds[0], bounds[1], mapX, mapW);
				int y2 = campaignMapCoordinate(road.toY, bounds[2], bounds[3], mapY, mapH);
				g2.setColor(road.blocked ? new Color(160, 60, 60) : new Color(137, 116, 80));
				g2.setStroke(new BasicStroke(road.danger > 0.5 ? 1f : 2f));
				g2.drawLine(x1, y1, x2, y2);
			}
			g2.setStroke(new BasicStroke(1f));

			campaignSettlementHitboxes.clear();
			java.util.List<Rectangle> occupiedMapLabels = new java.util.ArrayList<>();
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				int x = campaignMapCoordinate(settlement.worldX, bounds[0], bounds[1], mapX, mapW);
				int y = campaignMapCoordinate(settlement.worldY, bounds[2], bounds[3], mapY, mapH);
				int radius = selectedCampaignSettlementId != null && selectedCampaignSettlementId == settlement.id ? 10 : 7;
				Rectangle hitbox = new Rectangle(x - 12, y - 12, 24, 24);
				campaignSettlementHitboxes.put(settlement.id, hitbox);
				if (snapshot.player != null && snapshot.player.settlementId == settlement.id) {
					g2.setColor(Color.CYAN);
					g2.drawOval(x - radius - 4, y - radius - 4, radius * 2 + 8, radius * 2 + 8);
				}
				if (isActiveContractDestination(snapshot, settlement.id)) {
					g2.setColor(new Color(255, 190, 45));
					g2.setStroke(new BasicStroke(3f));
					g2.drawOval(x - radius - 8, y - radius - 8, radius * 2 + 16, radius * 2 + 16);
					g2.drawString("CONTRACT", x + 11, y - 11);
					g2.setStroke(new BasicStroke(1f));
				}
				g2.setColor(colorForRealm(settlement.realmId));
				g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);
				g2.setColor(Color.WHITE);
				g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
				if (settlement.occupyingRealmId != null) {
					g2.setColor(colorForRealm(settlement.occupyingRealmId));
					g2.setStroke(new BasicStroke(3f));
					g2.drawRect(x - radius - 4, y - radius - 4, radius * 2 + 8, radius * 2 + 8);
					g2.setStroke(new BasicStroke(1f));
				}
				g2.setFont(new Font("SansSerif", Font.BOLD, 11));
				int labelWidth = Math.max(g2.getFontMetrics().stringWidth(settlement.name), 66) + 8;
				Rectangle label = placeCampaignLabel(x, y, labelWidth, 29,
					mapX + 4, mapY + 4, mapX + mapW - 4, mapY + mapH - 4, occupiedMapLabels);
				occupiedMapLabels.add(label);
				g2.setColor(new Color(9, 16, 22, 205));
				g2.fillRoundRect(label.x, label.y, label.width, label.height, 6, 6);
				g2.setColor(new Color(205, 205, 190));
				g2.drawLine(x, y, label.x + label.width / 2, label.y + label.height / 2);
				g2.setColor(Color.WHITE);
				g2.drawString(settlement.name, label.x + 4, label.y + 11);
				g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
				g2.drawString("Pop " + settlement.population, label.x + 4, label.y + 23);
			}

			campaignArmyHitboxes.clear();
			for (CampaignSnapshot.ArmyView army : snapshot.armies) {
				int x = campaignMapCoordinate(army.worldX, bounds[0], bounds[1], mapX, mapW);
				int y = campaignMapCoordinate(army.worldY, bounds[2], bounds[3], mapY, mapH);
				g2.setColor(colorForRealm(army.realmId));
				g2.fillRect(x - 6, y - 6, 12, 12);
				g2.setColor(Color.WHITE);
				g2.drawRect(x - 6, y - 6, 12, 12);
				g2.drawString(Integer.toString(army.strength), x + 8, y + 4);
				campaignArmyHitboxes.put(army.id, new Rectangle(x - 10, y - 10, 28, 20));
			}

			campaignCaravanHitboxes.clear();
			for (CampaignSnapshot.CaravanView caravan : snapshot.caravans) {
				int x = campaignMapCoordinate(caravan.worldX, bounds[0], bounds[1], mapX, mapW);
				int y = campaignMapCoordinate(caravan.worldY, bounds[2], bounds[3], mapY, mapH);
				g2.setColor(new Color(245, 196, 65));
				int[] xs = {x, x + 6, x, x - 6};
				int[] ys = {y - 6, y, y + 6, y};
				g2.fillPolygon(xs, ys, 4);
				g2.setColor(Color.BLACK);
				g2.drawPolygon(xs, ys, 4);
				campaignCaravanHitboxes.put(caravan.id, new Rectangle(x - 10, y - 10, 20, 20));
			}

			int panelX = 535;
			int y = 80;
			g2.setFont(new Font("SansSerif", Font.BOLD, 15));
			g2.setColor(new Color(222, 204, 155));
			g2.drawString("WORLD STATUS", panelX, y); y += 22;
			g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
			g2.setColor(Color.WHITE);
			g2.drawString(snapshot.getDateLabel(), panelX, y); y += 17;
			String speedText = snapshot.paused ? "PAUSED" : snapshot.speed + "x";
			g2.drawString("Time: " + speedText, panelX, y); y += 17;
			g2.drawString("Population: " + snapshot.livingPopulation, panelX, y); y += 17;
			g2.drawString("Households: " + snapshot.householdCount, panelX, y); y += 17;
			g2.drawString(String.format("Food security: %.0f%%", snapshot.averageFoodSecurity * 100.0), panelX, y); y += 17;
			g2.drawString("Armies/Caravans: " + snapshot.armyCount + "/" + snapshot.caravanCount, panelX, y); y += 17;
			g2.drawString("Wars/Sieges/Treaties: " + snapshot.warCount + "/"
				+ snapshot.activeSiegeCount + "/" + snapshot.activeTreatyCount, panelX, y); y += 17;
			if (!snapshot.armies.isEmpty()) {
				CampaignSnapshot.ArmyView army = snapshot.armies.get(0);
				g2.drawString("Army: " + army.strength + " " + army.order + " food " + army.grain, panelX, y); y += 17;
			}
			if (!snapshot.caravans.isEmpty()) {
				CampaignSnapshot.CaravanView caravan = snapshot.caravans.get(0);
				g2.drawString("Merchant: " + caravan.state + " " + caravan.cargoQuantity, panelX, y); y += 17;
			}
			if (snapshot.player != null) {
				g2.setColor(new Color(245, 196, 65));
				g2.drawString("You: " + snapshot.player.coins + "c cargo "
					+ snapshot.player.cargoUsed + "/" + snapshot.player.cargoCapacity, panelX, y); y += 17;
				g2.drawString("Party " + snapshot.player.partySize + " contracts "
					+ snapshot.player.activeContracts + " rep " + snapshot.player.reputation, panelX, y); y += 17;
			}
			y += 7;

			g2.setColor(new Color(222, 204, 155));
			g2.setFont(new Font("SansSerif", Font.BOLD, 13));
			g2.drawString("REALMS", panelX, y); y += 18;
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			for (CampaignSnapshot.RealmView realm : snapshot.realms) {
				g2.setColor(colorForRealm(realm.id));
				g2.drawString(realm.name, panelX, y); y += 13;
				g2.setColor(Color.LIGHT_GRAY);
				g2.drawString(" " + realm.rulerName + "  leg " + Math.round(realm.legitimacy), panelX, y); y += 13;
				g2.drawString(" pop " + realm.population + " treasury " + realm.treasury, panelX, y); y += 13;
				g2.drawString(" " + realm.successionLaw + " factions " + realm.factionCount, panelX, y); y += 13;
				g2.drawString(" treaties " + realm.treatyCount + " " + realm.diplomacySummary, panelX, y); y += 14;
			}

			y += 5;
			g2.setColor(new Color(222, 204, 155));
			g2.setFont(new Font("SansSerif", Font.BOLD, 12));
			g2.drawString("RECENT EVENTS", panelX, y); y += 16;
			g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
			g2.setColor(Color.LIGHT_GRAY);
			if (snapshot.recentEvents.isEmpty()) {
				g2.drawString("No major events yet", panelX, y);
			} else {
				for (CampaignSnapshot.EventView event : snapshot.recentEvents) {
					g2.drawString("D" + (event.worldMinute / WorldConfig.MINUTES_PER_DAY + 1) + " " + event.type, panelX, y);
					y += 12;
					if (y > screenHeight - 10) break;
				}
			}

			drawCampaignTabs(g2, snapshot);
			drawSettlementHoverCard(g2, snapshot);
		}

		private void drawCampaignTabs(Graphics2D g2, CampaignSnapshot snapshot) {
			int barY = screenHeight - 32;
			g2.setColor(new Color(10, 15, 21, 238));
			g2.fillRect(0, barY, screenWidth, 32);
			campaignTabHitboxes.clear();
			java.util.List<CampaignTab> tabs = new java.util.ArrayList<>(java.util.List.of(CampaignTab.values()));
			boolean kingdomAvailable = snapshot.player != null && snapshot.player.affiliatedRealmId != null;
			if (!kingdomAvailable) {
				tabs.remove(CampaignTab.KINGDOM);
				if (activeCampaignTab == CampaignTab.KINGDOM) activeCampaignTab = CampaignTab.OVERVIEW;
			}
			int width = screenWidth / tabs.size();
			for (int i = 0; i < tabs.size(); i++) {
				CampaignTab tab = tabs.get(i);
				Rectangle rect = new Rectangle(i * width, barY, i == tabs.size() - 1 ? screenWidth - i * width : width, 32);
				campaignTabHitboxes.put(tab, rect);
				g2.setColor(tab == activeCampaignTab ? new Color(174, 132, 58) : new Color(42, 54, 64));
				g2.fillRoundRect(rect.x + 2, rect.y + 3, rect.width - 4, rect.height - 5, 7, 7);
				g2.setColor(Color.WHITE);
				g2.setFont(new Font("SansSerif", Font.BOLD, 10));
				String label = tab.toString();
				g2.drawString(label, rect.x + (rect.width - g2.getFontMetrics().stringWidth(label)) / 2, rect.y + 20);
			}
			if (activeCampaignTab == CampaignTab.OVERVIEW) return;
			int panelY = screenHeight - 205;
			g2.setColor(new Color(12, 18, 25, 242));
			g2.fillRoundRect(18, panelY, screenWidth - 36, 166, 12, 12);
			g2.setColor(new Color(205, 178, 112));
			g2.drawRoundRect(18, panelY, screenWidth - 36, 166, 12, 12);
			g2.setFont(new Font("Serif", Font.BOLD, 17));
			g2.drawString(activeCampaignTab.toString(), 32, panelY + 24);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
			g2.setColor(Color.WHITE);
			int y = panelY + 44;
			kingdomManagementHitboxes.clear();
			switch (activeCampaignTab) {
				case INVENTORY -> {
					if (snapshot.player != null) {
						g2.drawString("Coins: " + snapshot.player.coins + "   Capacity: "
							+ snapshot.player.cargoUsed + "/" + snapshot.player.cargoCapacity, 32, y); y += 16;
						g2.drawString("Grain: " + snapshot.player.grain + "   Vegetables: " + snapshot.player.vegetables, 32, y);
					}
				}
				case PARTY -> {
					if (snapshot.player != null) for (Long memberId : snapshot.player.partyMemberIds) {
						CampaignSnapshot.PersonView person = findPerson(snapshot, memberId);
						if (person != null) { g2.drawString(person.name + "  " + person.type + "  " + person.activity, 32, y); y += 15; }
					}
				}
				case CONTRACTS -> {
					int shown = 0;
					for (CampaignSnapshot.ContractView contract : snapshot.contracts) {
						if (!"ACTIVE".equals(contract.status)) continue;
						g2.setColor("ACTIVE".equals(contract.status) ? new Color(255, 205, 85) : Color.WHITE);
						g2.drawString("#" + contract.id + " [" + contract.status + "] " + contract.objective, 32, y); y += 14;
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("From " + contract.issuerName + " -> " + contract.destinationName
							+ " | " + contract.progress, 44, y); y += 14;
						g2.drawString("Reward " + contract.rewardCoins + "c | Penalty " + contract.penaltyCoins
							+ "c | " + contract.remainingDays() + " days left", 44, y); y += 18;
						if (++shown >= 3 || y > panelY + 150) break;
					}
					if (shown == 0) g2.drawString("No active contracts. Visit a notice board for new work.", 32, y);
				}
				case KINGDOM -> {
					CampaignSnapshot.PlayerView playerView = snapshot.player;
					CampaignSnapshot.RealmView realm = playerView == null || playerView.affiliatedRealmId == null
							? null : snapshot.findRealm(playerView.affiliatedRealmId);
					if (realm == null) {
						g2.drawString("You are not affiliated with a kingdom.", 32, y);
						break;
					}
					g2.drawString(realm.name + " | Your role: " + playerView.kingdomRole, 32, y); y += 15;
					g2.drawString("Ruler " + realm.rulerName + " | Treasury " + realm.treasury + "c | Stability "
							+ Math.round(realm.stability) + " | Legitimacy " + Math.round(realm.legitimacy), 32, y); y += 18;
					g2.drawString(realm.governmentType + " | " + realm.successionLaw + " | Settlements "
							+ realm.settlementCount + " | Population " + realm.population, 32, y); y += 17;
					if (!playerView.canManageKingdom) {
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("Kingdom management is reserved for the ruler.", 32, y);
						break;
					}
					g2.setColor(new Color(255, 215, 110));
					g2.drawString("Manage laws (0-3):", 32, y); y += 14;
					int lawIndex = 0;
					for (java.util.Map.Entry<String, Integer> law : realm.laws.entrySet()) {
						int column = lawIndex % 2;
						int row = lawIndex / 2;
						int lawX = 44 + column * 360;
						int lawY = y + row * 18;
						g2.setColor(Color.WHITE);
						g2.drawString(law.getKey() + "  " + law.getValue(), lawX, lawY + 11);
						Rectangle minus = new Rectangle(lawX + 250, lawY - 2, 25, 15);
						Rectangle plus = new Rectangle(lawX + 282, lawY - 2, 25, 15);
						kingdomManagementHitboxes.put(law.getKey() + ":-1", minus);
						kingdomManagementHitboxes.put(law.getKey() + ":1", plus);
						g2.setColor(new Color(58, 76, 91)); g2.fillRect(minus.x, minus.y, minus.width, minus.height); g2.fillRect(plus.x, plus.y, plus.width, plus.height);
						g2.setColor(Color.WHITE); g2.drawString("-", minus.x + 10, minus.y + 12); g2.drawString("+", plus.x + 8, plus.y + 12);
						lawIndex++;
					}
				}
				case ENCYCLOPEDIA -> {
					long working = snapshot.people.stream().filter(person -> "WORKING".equals(person.activity)).count();
					long traveling = snapshot.people.stream().filter(person -> "TRAVELING".equals(person.activity)).count();
					g2.drawString("WORLD: " + snapshot.settlements.size() + " settlements, " + snapshot.realms.size()
						+ " kingdoms, " + snapshot.warCount + " active wars", 32, y); y += 16;
					g2.drawString("PEOPLE: " + snapshot.livingPopulation + " living, " + working + " working, "
						+ traveling + " traveling", 32, y); y += 20;
					for (CampaignSnapshot.RealmView realm : snapshot.realms) {
						g2.setColor(colorForRealm(realm.id));
						g2.drawString(realm.name + " — ruler " + realm.rulerName + ", pop " + realm.population
							+ ", settlements " + realm.settlementCount + ", treasury " + realm.treasury + "c", 32, y); y += 15;
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("  " + realm.governmentType + ", " + realm.successionLaw + ", legitimacy "
							+ Math.round(realm.legitimacy) + ", factions " + realm.factionCount, 32, y); y += 16;
						if (y > panelY + 150) break;
					}
				}
				case CRIME -> {
					for (int i = snapshot.crimes.size() - 1; i >= 0 && y <= panelY + 150; i--) {
						CampaignSnapshot.CrimeView crime = snapshot.crimes.get(i);
						g2.drawString(crime.type + " at settlement " + crime.settlementId + " severity "
							+ crime.severity + (crime.discovered ? " discovered" : " hidden"), 32, y); y += 15;
					}
				}
				default -> { }
			}
		}

		private boolean isActiveContractDestination(CampaignSnapshot snapshot, long settlementId) {
			for (CampaignSnapshot.ContractView contract : snapshot.contracts) {
				if ("ACTIVE".equals(contract.status) && contract.destinationSettlementId == settlementId) return true;
			}
			return false;
		}

		private CampaignSnapshot.PersonView findPerson(CampaignSnapshot snapshot, long id) {
			for (CampaignSnapshot.PersonView person : snapshot.people) if (person.id == id) return person;
			return null;
		}

		private void drawSettlementHoverCard(Graphics2D g2, CampaignSnapshot snapshot) {
			if (hoveredCampaignSettlementId == null) return;
			CampaignSnapshot.SettlementView settlement = snapshot.findSettlement(hoveredCampaignSettlementId);
			if (settlement == null) return;
			int width = 220;
			int x = Math.min(screenWidth - width - 8, campaignMousePoint.x + 14);
			int y = Math.min(screenHeight - 150, campaignMousePoint.y + 14);
			g2.setColor(new Color(8, 13, 18, 235));
			g2.fillRoundRect(x, y, width, 132, 10, 10);
			g2.setColor(new Color(225, 194, 120));
			g2.drawRoundRect(x, y, width, 132, 10, 10);
			g2.setFont(new Font("Serif", Font.BOLD, 15));
			g2.drawString(settlement.name, x + 10, y + 20);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			g2.setColor(Color.WHITE);
			g2.drawString("Population " + settlement.population + "  households " + settlement.households, x + 10, y + 39);
			g2.drawString("Treasury " + settlement.treasury + "c", x + 10, y + 55);
			g2.drawString("Grain " + settlement.grain + " @ " + settlement.grainPrice + "c", x + 10, y + 71);
			g2.drawString("Vegetables " + settlement.vegetables + " @ " + settlement.vegetablePrice + "c", x + 10, y + 87);
			g2.drawString(String.format("Security %.0f%%  unrest %.0f%%", settlement.security * 100, settlement.unrest * 100), x + 10, y + 103);
			long crimeCount = snapshot.crimes.stream().filter(crime -> crime.settlementId == settlement.id).count();
			g2.drawString("Recorded crime " + crimeCount + "  food " + Math.round(settlement.foodSecurity * 100) + "%", x + 10, y + 119);
		}

		private Rectangle placeCampaignLabel(int x, int y, int width, int height,
				int minX, int minY, int maxX, int maxY, java.util.List<Rectangle> occupied) {
			int[][] offsets = {{12, -34}, {12, 10}, {-width - 12, -34}, {-width - 12, 10},
				{-width / 2, -52}, {-width / 2, 18}};
			Rectangle best = null;
			int bestOverlap = Integer.MAX_VALUE;
			for (int[] offset : offsets) {
				int px = Math.max(minX, Math.min(maxX - width, x + offset[0]));
				int py = Math.max(minY, Math.min(maxY - height, y + offset[1]));
				Rectangle candidate = new Rectangle(px, py, width, height);
				int overlap = 0;
				for (Rectangle other : occupied) {
					Rectangle intersection = candidate.intersection(other);
					if (!intersection.isEmpty()) overlap += intersection.width * intersection.height;
				}
				if (overlap < bestOverlap) {
					best = candidate;
					bestOverlap = overlap;
					if (overlap == 0) break;
				}
			}
			return best == null ? new Rectangle(x + 10, y - 20, width, height) : best;
		}

		private double[] getCampaignBounds(CampaignSnapshot snapshot) {
			double minX = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				minX = Math.min(minX, settlement.worldX);
				maxX = Math.max(maxX, settlement.worldX);
				minY = Math.min(minY, settlement.worldY);
				maxY = Math.max(maxY, settlement.worldY);
			}
			if (snapshot.settlements.isEmpty()) return new double[]{0, 1, 0, 1};
			if (maxX <= minX) maxX = minX + 1;
			if (maxY <= minY) maxY = minY + 1;
			return new double[]{minX, maxX, minY, maxY};
		}

		private int campaignMapCoordinate(double value, double min, double max, int start, int size) {
			double normalized = (value - min) / (max - min);
			return start + 35 + (int) Math.round(normalized * (size - 70));
		}

		private Color colorForRealm(Long realmId) {
			if (realmId == null) return new Color(150, 150, 150);
			return (realmId & 1L) == 0L ? new Color(70, 145, 230) : new Color(210, 85, 75);
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
				drawMenuButton(g2, campaignModeButton, "Campaign");
				drawMenuButton(g2, survivalModeButton, "Survival");
				break;
			case ALLY_YES_NO:
				drawMenuPrompt(g2, "Start with allies?");
				drawMenuButton(g2, allyYesButton, "Yes");
				drawMenuButton(g2, allyNoButton, "No");
				drawMenuButton(g2, backButton, "< Back");
				break;
			case ALLY_TYPE:
				drawMenuPrompt(g2, "Choose ally type");
				drawMenuButton(g2, allyMeleeButton, "Melee Only");
				drawMenuButton(g2, allyArcherButton, "Archer Only");
				drawMenuButton(g2, allyBothButton, "Both");
				drawMenuButton(g2, backButton, "< Back");
				break;
			case HERO_CHOICE:
				drawMenuPrompt(g2, "Choose a playable hero or companion");
				String[] heroLabels = {"No Hero", "Play Warrior", "Play Mage", "Warrior Ally", "Mage Ally"};
				for (int i = 0; i < survivalHeroButtons.length; i++) drawMenuButton(g2, survivalHeroButtons[i], heroLabels[i]);
				drawMenuButton(g2, backButton, "< Back");
				break;
			case READY:
				String selectedMode = gameMode == GameMode.CAMPAIGN ? "Campaign"
					: gameMode == GameMode.SURVIVAL ? "Survival" : "Sandbox";
				String modeLabel = selectedMode + " selected";
				int modeWidth = g2.getFontMetrics().stringWidth(modeLabel);
				g2.drawString(modeLabel, (screenWidth - modeWidth) / 2, screenHeight / 2 - 30);
				if (gameMode == GameMode.SURVIVAL) {
					String heroLabel = "Hero: " + survivalHeroChoice.toString().replace('_', ' ');
					g2.drawString(heroLabel, (screenWidth - g2.getFontMetrics().stringWidth(heroLabel)) / 2, screenHeight / 2 - 5);
				}
				String prompt = "Press ENTER to start";
				int promptWidth = g2.getFontMetrics().stringWidth(prompt);
				g2.drawString(prompt, (screenWidth - promptWidth) / 2,
					gameMode == GameMode.SURVIVAL ? screenHeight / 2 + 25 : screenHeight / 2);
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

		// Draw Settings Button
		g2.setColor(new Color(64, 64, 64, 220));
		g2.fillRoundRect(settingsButtonRect.x, settingsButtonRect.y, settingsButtonRect.width, settingsButtonRect.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(settingsButtonRect.x, settingsButtonRect.y, settingsButtonRect.width, settingsButtonRect.height, 10, 10);
		g2.setFont(new Font("Arial", Font.BOLD, 18));
		String settingsText = "Settings";
		int settingsTextW = g2.getFontMetrics().stringWidth(settingsText);
		g2.drawString(settingsText, settingsButtonRect.x + (settingsButtonRect.width - settingsTextW) / 2, settingsButtonRect.y + 24);

		g2.setFont(new Font("Arial", Font.PLAIN, 14));
		String controls = "Controls: WASD Move, SPACE Melee, 1/2/3 Magic, E Dodge";
		int cw = g2.getFontMetrics().stringWidth(controls);
		g2.drawString(controls, x + (width - cw) / 2, y + 200);

		if (gameMode == GameMode.SURVIVAL) drawQuitSurvivalButton(g2);
	}

	// private void drawSettingsMenu(Graphics2D g2) {
	// 	g2.setColor(new Color(0, 0, 0, 220));
	// 	g2.fillRect(0, 0, screenWidth, screenHeight);
	// 	g2.setColor(Color.white);
	// 	g2.setFont(new Font("Arial", Font.BOLD, 28));
	// 	g2.drawString("Key & Controller Bindings", screenWidth/2 - 160, 60);

	// 	g2.setFont(new Font("Arial", Font.PLAIN, 16));
	// 	int y = 110;
	// 	int rowHeight = 32;
	// 	BindingManager.Action[] actions = BindingManager.Action.values();
	// 	settingsRowRects = new Rectangle[actions.length][2]; // [action index][0 = keyboard col, 1 = controller col]

	// 	for (int i = 0; i < actions.length; i++) {
	// 		BindingManager.Action action = actions[i];
	// 		g2.setColor(Color.white);
	// 		g2.drawString(action.name(), 60, y + 20);

	// 		// Keyboard binding button
	// 		Rectangle kbRect = new Rectangle(320, y, 140, 26);
	// 		settingsRowRects[i][0] = kbRect;
	// 		boolean awaitingThisKb = awaitingRebindAction == action && !awaitingRebindIsController;
	// 		g2.setColor(awaitingThisKb ? new Color(200, 150, 0) : new Color(50, 50, 50));
	// 		g2.fillRect(kbRect.x, kbRect.y, kbRect.width, kbRect.height);
	// 		g2.setColor(Color.white);
	// 		String kbLabel = awaitingThisKb ? "Press a key..." : KeyEvent.getKeyText(bindings.getKeyBinding(action));
	// 		g2.drawString(kbLabel, kbRect.x + 8, kbRect.y + 18);

	// 		// Controller binding button
	// 		Rectangle ctrlRect = new Rectangle(480, y, 160, 26);
	// 		settingsRowRects[i][1] = ctrlRect;
	// 		boolean awaitingThisCtrl = awaitingRebindAction == action && awaitingRebindIsController;
	// 		g2.setColor(awaitingThisCtrl ? new Color(200, 150, 0) : new Color(50, 50, 50));
	// 		g2.fillRect(ctrlRect.x, ctrlRect.y, ctrlRect.width, ctrlRect.height);
	// 		g2.setColor(Color.white);
	// 		int ctrlBinding = bindings.getControllerBinding(action);
	// 		String ctrlLabel = awaitingThisCtrl ? "Press a button..."
	// 			: (ctrlBinding >= 0 && controllerH != null ? controllerH.getButtonLabel(ctrlBinding) : "—");
	// 		g2.drawString(ctrlLabel, ctrlRect.x + 8, ctrlRect.y + 18);

	// 		y += rowHeight;
	// 	}

	// 	g2.setColor(Color.yellow);
	// 	g2.drawString("Click a binding, then press the new key/button. ESC to go back.", 60, y + 30);
	// }

	private void drawSettingsMenu(Graphics2D g2) {
    g2.setColor(new Color(0, 0, 0, 220));
    g2.fillRect(0, 0, screenWidth, screenHeight);
    g2.setColor(Color.white);
    g2.setFont(new Font("Arial", Font.BOLD, 28));
    g2.drawString("Key & Controller Bindings", screenWidth/2 - 160, 60);

    g2.setFont(new Font("Arial", Font.PLAIN, 16));
    int y = 110;
    int rowHeight = 32;
    BindingManager.Action[] actions = BindingManager.Action.values();
    settingsRowRects = new Rectangle[actions.length][2]; // Initialize the array here

    for (int i = 0; i < actions.length; i++) {
        BindingManager.Action action = actions[i];
        g2.setColor(Color.white);
        g2.drawString(action.name(), 60, y + 20);

        // Keyboard binding button
        Rectangle kbRect = new Rectangle(320, y, 140, 26);
        settingsRowRects[i][0] = kbRect;
        boolean awaitingThisKb = awaitingRebindAction == action && !awaitingRebindIsController;
        g2.setColor(awaitingThisKb ? new Color(200, 150, 0) : new Color(50, 50, 50));
        g2.fillRect(kbRect.x, kbRect.y, kbRect.width, kbRect.height);
        g2.setColor(Color.white);
        String kbLabel = awaitingThisKb ? "Press a key..." : KeyEvent.getKeyText(bindings.getKeyBinding(action));
        g2.drawString(kbLabel, kbRect.x + 8, kbRect.y + 18);

        // Controller binding button
        Rectangle ctrlRect = new Rectangle(480, y, 160, 26);
        settingsRowRects[i][1] = ctrlRect;
        boolean awaitingThisCtrl = awaitingRebindAction == action && awaitingRebindIsController;
        g2.setColor(awaitingThisCtrl ? new Color(200, 150, 0) : new Color(50, 50, 50));
        g2.fillRect(ctrlRect.x, ctrlRect.y, ctrlRect.width, ctrlRect.height);
        g2.setColor(Color.white);
        int ctrlBinding = bindings.getControllerBinding(action);
        String ctrlLabel = awaitingThisCtrl ? "Press a button..."
                : (ctrlBinding >= 0 && controllerH != null ? controllerH.getButtonLabel(ctrlBinding) : "—");
        g2.drawString(ctrlLabel, ctrlRect.x + 8, ctrlRect.y + 18);

        y += rowHeight;
    }

		g2.setColor(Color.yellow);
		g2.drawString("Click a binding, then press the new key/button. ESC to go back.", 60, y + 30);
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
		drawQuitSurvivalButton(g2);
	}

	private void drawQuitSurvivalButton(Graphics2D g2) {
		Rectangle quitBtn = getQuitButtonRect();
		g2.setColor(new Color(128, 0, 0, 220));
		g2.fillRoundRect(quitBtn.x, quitBtn.y, quitBtn.width, quitBtn.height, 12, 12);
		g2.setColor(Color.white);
		g2.drawRoundRect(quitBtn.x, quitBtn.y, quitBtn.width, quitBtn.height, 12, 12);
		g2.setFont(new Font("Arial", Font.BOLD, 20));
		String quitText = "Quit Survival Mode";
		int quitTw = g2.getFontMetrics().stringWidth(quitText);
		g2.drawString(quitText, quitBtn.x + (quitBtn.width - quitTw) / 2, quitBtn.y + 32);
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
    drawHeroMarkers(g2, miniX, miniY, centerCol, centerRow, viewRadiusTiles);   // <-- add this
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

    // Survival portal — separate field, not part of mapLinks, needs its own marker
    if (gameMode == GameMode.SURVIVAL && survivalPortal != null) {
        float col = (float) survivalPortal.area.x / tileSize;
        float row = (float) survivalPortal.area.y / tileSize;
        float pxPerTile = mapSizeHalf() / viewRadiusTiles;
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;

        g2.setColor(Color.yellow); // distinct from the magenta regular-portal color
        int r = 5;
        int[] xs = {(int) px, (int) px + r, (int) px, (int) px - r};
        int[] ys = {(int) py - r, (int) py, (int) py + r, (int) py};
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

private void drawHeroMarkers(Graphics2D g2, int miniX, int miniY, float centerCol, float centerRow,
                              float viewRadiusTiles) {
    float pxPerTile = mapSizeHalf() / viewRadiusTiles;
    for (Hero hero : new java.util.ArrayList<>(heroes)) {
        if (hero == null) continue;
        float col = hero.x / tileSize;
        float row = hero.y / tileSize;
        float px = miniX + mapSizeHalf() + (col - centerCol) * pxPerTile;
        float py = miniY + mapSizeHalf() + (row - centerRow) * pxPerTile;
        int size = Math.max(3, (int) pxPerTile);

        g2.setColor(hero.isRecruited ? Color.green : Color.gray); // recruited vs. not-yet-recruited reads differently at a glance
        g2.fillOval((int) px, (int) py, size, size);
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
 
	//player spawns 
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
			} else {
				targetX = tileM.currentMapWidth * tileSize / 2;
				targetY = tileM.currentMapHeight * tileSize / 2;
			}
		} else {
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

	private void syncCampaignHeroesFromParty() {
		if (campaignSession == null) return;
		world.WorldParty party = campaignSession.getWorld().parties.get(campaignSession.getPlayerState().partyId);
		if (party == null) return;
		for (Long personId : party.memberPersonIds) {
			if (personId == campaignSession.getPlayerState().personId) continue;
			boolean exists = heroes.stream().anyMatch(hero -> personId.equals(hero.sourcePersonId));
			if (exists) continue;
			world.Person person = campaignSession.getWorld().people.get(personId);
			if (person == null || !person.alive) continue;
			Hero.HeroClass[] classes = Hero.HeroClass.values();
			Hero hero = new Hero(this, (person.givenName + " " + person.familyName).trim(),
				classes[(int) Math.floorMod(person.id, classes.length)], (int) player.x + tileSize, (int) player.y);
			hero.sourcePersonId = person.id;
			hero.isRecruited = true;
			hero.activePlayerReference = player;
			heroes.add(hero);
			recruitedHeroes.add(hero);
		}
	}

	private void recruitHero(Hero hero) {
		hero.isRecruited = true;
		hero.onRecruited(this);
		hero.companionRole = Hero.CompanionRole.HYBRID;
		hero.activePlayerReference = player;
		if (!recruitedHeroes.contains(hero)) recruitedHeroes.add(hero);
		if (gameMode == GameMode.CAMPAIGN && campaignSession != null && hero.sourcePersonId == null) {
			world.WorldParty party = campaignSession.getWorld().parties.get(campaignSession.getPlayerState().partyId);
			world.Person person = campaignSession.getWorld().people.values().stream()
				.filter(value -> value.alive && value.currentSettlementId != null
					&& value.currentSettlementId == campaignSession.getPlayerState().currentSettlementId
					&& (party == null || !party.memberPersonIds.contains(value.id)))
				.sorted(java.util.Comparator.comparingLong(value -> value.id)).findFirst().orElse(null);
			if (person != null && campaignSession.recruitCompanion(person.id).accepted) {
				hero.sourcePersonId = person.id;
				person.givenName = hero.name;
				person.familyName = "";
				person.type = world.Person.PersonType.SOLDIER;
				campaignSession.refreshAfterCheat();
				campaignSnapshot = campaignSession.getSnapshot();
			}
		}
		localInteractionMessage = hero.name + " recruited. Press Q to play as recruited heroes.";
	}

	private void setActiveHero(Hero hero) {
		boolean leavingAdventurer = activeHero == null && hero != null;
		if (activeHero != null) {
			activeHero.health = player.health;
			activeHero.x = player.x;
			activeHero.y = player.y;
			activeHero.isActivePlayer = false;
		} else {
			storedPlayerHealth = player.health;
			storedPlayerMaxHealth = player.maxHealth;
			storedPlayerMeleeDamage = player.meleeDamage;
			storedPlayerProjectileDamage = player.projectileDamage;
		}
		if (leavingAdventurer) {
			// Preserve any health or progression changes made while controlling the starter.
			storedPlayerHealth = player.health;
			storedPlayerMaxHealth = player.maxHealth;
			storedPlayerMeleeDamage = player.meleeDamage;
			storedPlayerProjectileDamage = player.projectileDamage;
			showAdventurerCompanion();
		}
		activeHero = hero;
		if (hero != null) {
			hero.isActivePlayer = true;
			hero.activePlayerReference = player;
			hero.x = player.x;
			hero.y = player.y;
			player.maxHealth = hero.maxHealth;
			player.health = Math.max(1, hero.health);
			player.meleeDamage = hero.attack;
			player.projectileDamage = Math.max(6, hero.attack);
		} else {
			if (adventurerCompanion != null) {
				storedPlayerHealth = adventurerCompanion.health;
				storedPlayerMaxHealth = adventurerCompanion.maxHealth;
				hideAdventurerCompanion();
			}
			player.maxHealth = storedPlayerMaxHealth;
			player.health = Math.max(1, Math.min(storedPlayerMaxHealth, storedPlayerHealth));
			player.meleeDamage = storedPlayerMeleeDamage;
			player.projectileDamage = storedPlayerProjectileDamage;
		}
	}

	private void showAdventurerCompanion() {
		if (adventurerCompanion == null) {
			adventurerCompanion = new Hero(this, "Adventurer", Hero.HeroClass.ADVENTURER,
					(int) player.x, (int) player.y);
			adventurerCompanion.isRecruited = true;
			adventurerCompanion.companionRole = Hero.CompanionRole.HYBRID;
			adventurerCompanion.usePlayerAbilities = false;
		}
		adventurerCompanion.x = player.x;
		adventurerCompanion.y = player.y;
		adventurerCompanion.health = Math.max(1, storedPlayerHealth);
		adventurerCompanion.maxHealth = storedPlayerMaxHealth;
		adventurerCompanion.attack = storedPlayerMeleeDamage;
		adventurerCompanion.activePlayerReference = player;
		adventurerCompanion.isActivePlayer = false;
		if (!heroes.contains(adventurerCompanion)) heroes.add(adventurerCompanion);
		if (!recruitedHeroes.contains(adventurerCompanion)) recruitedHeroes.add(adventurerCompanion);
	}

	private void hideAdventurerCompanion() {
		heroes.remove(adventurerCompanion);
		recruitedHeroes.remove(adventurerCompanion);
	}

	private void switchActiveHero() {
		java.util.List<Hero> recruited = new java.util.ArrayList<>();
		for (Hero h : heroes) if (h.isRecruited && h != adventurerCompanion) recruited.add(h);
		if (recruited.isEmpty()) { setActiveHero(null); return; }
		if (activeHero == null) setActiveHero(recruited.get(0));
		else {
			int currentIndex = recruited.indexOf(activeHero);
			if (currentIndex < 0 || currentIndex + 1 >= recruited.size()) setActiveHero(null);
			else setActiveHero(recruited.get(currentIndex + 1));
		}
		System.out.println(activeHero == null ? "Switched to adventurer" : "Switched to " + activeHero.name);
	}

	public void spawnHeroAbilityEffect(HeroAbilityEffect effect) {
		if (effect != null) heroAbilityEffects.add(effect);
	}

	public void beginMagePortalTargeting(Hero hero) {
		pendingPortalHero = hero;
	}

	private void placeMagePortal(float destinationX, float destinationY) {
		if (pendingPortalHero == null) return;
		float offsetX = 0f, offsetY = tileSize * 1.5f;
		String facing = pendingPortalHero.direction;
		if (facing.contains("Left") || "left".equals(facing)) offsetX = -tileSize * 1.5f;
		if (facing.contains("Right") || "right".equals(facing)) offsetX = tileSize * 1.5f;
		if (facing.startsWith("up")) offsetY = -tileSize * 1.5f;
		else if (facing.startsWith("down")) offsetY = tileSize * 1.5f;
		if (offsetX != 0f && (facing.startsWith("up") || facing.startsWith("down"))) offsetY *= 0.70710678f;

		java.awt.Point entrance = findOpenSpawnSpace(
				(int) (pendingPortalHero.x + offsetX), (int) (pendingPortalHero.y + offsetY), player, tileSize * 3);
		portalEntranceX = entrance.x + tileSize / 2f;
		portalEntranceY = entrance.y + tileSize / 2f;
		float maxX = getCurrentMapWidthTiles() * tileSize - tileSize;
		float maxY = getCurrentMapHeightTiles() * tileSize - tileSize;
		int destinationTopLeftX = (int) Math.max(0, Math.min(maxX, destinationX - tileSize / 2f));
		int destinationTopLeftY = (int) Math.max(0, Math.min(maxY, destinationY - tileSize / 2f));
		java.awt.Point exit = findOpenSpawnSpace(destinationTopLeftX, destinationTopLeftY, player, tileSize * 4);
		portalExitX = exit.x + tileSize / 2f;
		portalExitY = exit.y + tileSize / 2f;
		heroPortalDuration = 1800;
		heroPortalCooldown = 45;
		heroAbilityEffects.removeIf(HeroAbilityEffect::isPortal);
		heroAbilityEffects.add(HeroAbilityEffect.portal(this, portalEntranceX, portalEntranceY, heroPortalDuration));
		heroAbilityEffects.add(HeroAbilityEffect.portal(this, portalExitX, portalExitY, heroPortalDuration));
		pendingPortalHero = null;
	}

	private void updateHeroAbilityEffects() {
		for (Iterator<HeroAbilityEffect> iterator = heroAbilityEffects.iterator(); iterator.hasNext();) {
			HeroAbilityEffect effect = iterator.next();
			effect.update();
			if (effect.isExpired()) iterator.remove();
		}
		if (heroPortalDuration > 0) heroPortalDuration--;
		if (heroPortalCooldown > 0) heroPortalCooldown--;
		if (heroPortalDuration <= 0) {
			portalEntranceX = portalEntranceY = portalExitX = portalExitY = null;
			return;
		}
		if (heroPortalCooldown > 0 || portalEntranceX == null || portalExitX == null) return;
		float playerCenterX = player.x + tileSize / 2f;
		float playerCenterY = player.y + tileSize / 2f;
		float entranceDistance = distanceSquared(playerCenterX, playerCenterY, portalEntranceX, portalEntranceY);
		float exitDistance = distanceSquared(playerCenterX, playerCenterY, portalExitX, portalExitY);
		float triggerDistance = tileSize * tileSize;
		if (entranceDistance <= triggerDistance) {
			teleportThroughHeroPortal(portalExitX, portalExitY);
		} else if (exitDistance <= triggerDistance) {
			teleportThroughHeroPortal(portalEntranceX, portalEntranceY);
		}
	}

	private float distanceSquared(float x1, float y1, float x2, float y2) {
		float dx = x2 - x1, dy = y2 - y1;
		return dx * dx + dy * dy;
	}

	private void teleportThroughHeroPortal(float centerX, float centerY) {
		player.x = centerX - tileSize / 2f;
		player.y = centerY - tileSize / 2f;
		if (activeHero != null) {
			activeHero.x = player.x;
			activeHero.y = player.y;
		}
		heroPortalCooldown = 60;
	}

	private void handleHeroAbilities() {
		if (activeHero == null) return;

		Hero hero = activeHero;

		// Key 1 - first class ability
		if (keyH.num1Pressed) {
			if (!hero.unlockedAbilities.isEmpty()) {
				Hero.Ability ability = hero.unlockedAbilities.get(0);
				Enemy target = findNearestEnemyToHero(hero);
				if (canCastHeroAbility(hero, ability, target)) {
					hero.useAbility(ability, target);
				}
			}
			keyH.num1Pressed = false; // Consume press
		}

		// Key 2 - second class ability
		if (keyH.num2Pressed) {
			if (hero.unlockedAbilities.size() > 1) {
				Hero.Ability ability = hero.unlockedAbilities.get(1);
				Enemy target = findNearestEnemyToHero(hero);
				if (canCastHeroAbility(hero, ability, target)) {
					hero.useAbility(ability, target);
				}
			}
			keyH.num2Pressed = false; // Consume press
		}

		// Key 3 - third class ability
		if (keyH.num3Pressed) {
			if (hero.unlockedAbilities.size() > 2) {
				Hero.Ability ability = hero.unlockedAbilities.get(2);
				Enemy target = findNearestEnemyToHero(hero);
				if (canCastHeroAbility(hero, ability, target)) {
					hero.useAbility(ability, target);
				}
			}
			keyH.num3Pressed = false; // Consume press
		}
	}

	private boolean canCastHeroAbility(Hero hero, Hero.Ability ability, Enemy target) {
		if (hero.mana < ability.manaCost || ability.cooldown > 0) return false;
		if (target != null) return true;
		return switch (ability.type) {
			case WARRIOR_WAVE, WARRIOR_AURA, WARRIOR_PULL,
				MAGE_PORTAL, MAGE_STORM, MAGE_ORB, AOE_DAMAGE,
				AOE_HEAL, BUFF, MOBILITY, SUMMON, TAUNT, PASSIVE -> true;
			default -> false;
		};
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
		int displayedMana = activeHero != null && activeHero.isActivePlayer ? (int) activeHero.mana : player.mana;
		int displayedMaxMana = activeHero != null && activeHero.isActivePlayer ? activeHero.maxMana : player.maxMana;
		int manaWidth = (int)((double) displayedMana / Math.max(1, displayedMaxMana) * width);
		g2.setColor(Color.blue);
		g2.fillRect(x, y, manaWidth, height);
		g2.setColor(Color.white);
		g2.drawRect(x, y, width, height);
		g2.drawString("Mana", x + 6, y + height - 4);

		y += spacing;
		g2.drawString("Gold: " + gold, x + 6, y + height - 4);
		g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
		if (activeHero != null && activeHero.isActivePlayer) {
			for (int i = 0; i < activeHero.unlockedAbilities.size() && i < 3; i++) {
				Hero.Ability ability = activeHero.unlockedAbilities.get(i);
				String cooldown = ability.cooldown > 0 ? " [" + ability.cooldown + "]" : "";
				g2.drawString((i + 1) + ": " + ability.name + cooldown, x + 6, y + 30 + i * 14);
			}
		} else {
			g2.drawString("1: Fire Bolt", x + 6, y + 30);
			g2.drawString("2: Conqueror Field", x + 6, y + 44);
			g2.drawString("3: Healing Field", x + 6, y + 58);
		}

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
		} else {
		g2.setColor(new Color(64, 64, 64, 200));
		g2.fillRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.drawString("Wave: "+survivalWaveNumber, inventoryButton.x + 12, inventoryButton.y + 20);
			
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

