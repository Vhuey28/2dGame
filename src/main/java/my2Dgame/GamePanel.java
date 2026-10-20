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
		private Long hoveredCampaignArmyId;
		private Long hoveredCampaignCaravanId;
		private Rectangle campaignPlayerHitbox;
		private java.awt.Point campaignMousePoint = new java.awt.Point();
		/** Bannerlord-style strategic camera: drag pan, cursor-centred wheel zoom. */
		private final CampaignMapCamera campaignCamera = new CampaignMapCamera();
		private boolean campaignCameraInitialized;
		private java.awt.Point campaignDragStart;
		private java.awt.Point campaignDragLast;
		private boolean campaignDraggingMap;
		private boolean campaignSuppressNextMapClick;
		private final java.util.Map<String, Rectangle> campaignHudHitboxes =
				new java.util.LinkedHashMap<>();
		/** Moving map actors the player party is riding to intercept or shadow. */
		private Long campaignFollowArmyId;
		private Long campaignFollowCaravanId;
		private Long campaignFollowAnchorSettlementId;
		/** Hostile contact state for the paused encounter screen. */
		private Long campaignEncounterArmyId;
		private Long campaignEncounterIgnoredArmyId;
		private long campaignEncounterGraceUntilMinute = Long.MIN_VALUE;
		/** Used to open a settlement once, when a road journey actually arrives. */
		private boolean campaignWasTraveling;
		private Long campaignTravelDestinationSeen;
		/** Deterministic pre-rendered terrain, regenerated only for a new world layout. */
		private java.awt.image.BufferedImage campaignTerrainImage;
		private int campaignTerrainImageKey;
		private double[] campaignTerrainBounds;
		private enum CampaignTab { OVERVIEW, INVENTORY, PARTY, CONTRACTS, KINGDOM, WAR, FIEFS, ENCYCLOPEDIA, CRIME }
		private CampaignTab activeCampaignTab = CampaignTab.OVERVIEW;
		private final java.util.Map<CampaignTab, Rectangle> campaignTabHitboxes = new java.util.EnumMap<>(CampaignTab.class);
		private final java.util.Map<String, Rectangle> kingdomManagementHitboxes = new java.util.LinkedHashMap<>();
		private enum CampaignContextMenu { NONE, SETTLEMENT, MARKET, NOTICE_BOARD, PERSON, ARMY, CARAVAN, ENCOUNTER, BATTLE_DECISION }
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
		/** Preserved tactical projections, so dead units removed from the live sprite lists still reconcile. */
		private final java.util.List<entity.Troop> campaignBattleTroops = new java.util.ArrayList<>();
		private final java.util.List<Enemy> campaignBattleEnemies = new java.util.ArrayList<>();
		private int campaignBattleInitialAttackers;
		private int campaignBattleInitialDefenders;
		private int campaignBattlePlayerKillsAtStart;
		private boolean campaignBattleEnemyBroken;
		private boolean campaignBattlePursuingRout;
		private int campaignBattlePursuitTicks;
		private boolean campaignBattleShowedMercy;
		private boolean campaignBattleRewardsRecorded;
		private boolean campaignBattleResolvingPlayerFall;
		private boolean campaignBattlePlayerDownAutoResolved;
		private String campaignBattlePhaseMessage = "";
		/** Transient world-map banner: defeat results, captivity, rest, and action feedback. */
		private String campaignNotice;
		private int campaignNoticeTicks;
		private static final int CAMPAIGN_NOTICE_TICKS = 60 * 12;
		/** Stacked notifications for major world events, each shown exactly once. */
		private final java.util.List<String> campaignToasts = new java.util.ArrayList<>();
		private int campaignToastTicks;
		private long lastCampaignEventId = -1L;
		private boolean campaignToastsPrimed;
		private static final int CAMPAIGN_TOAST_TICKS = 60 * 9;
		/** Full-screen control and systems reference, toggled with H on the world map. */
		private boolean campaignHelpOpen;
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
	boolean deathMenuOpen = false;
	private BindingManager.Action awaitingRebindAction = null;
	private boolean awaitingRebindIsController = false;
	private Rectangle pauseResumeButtonRect = new Rectangle(screenWidth/2 - 170, screenHeight/2 - 45, 340, 44);
	private Rectangle settingsButtonRect = new Rectangle(screenWidth/2 - 170, screenHeight/2 + 10, 340, 44);
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
	public int survivalCurrentEnemyCount = survivalEnemyCountForWave;
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
	private Rectangle sandboxModeButton = new Rectangle(34, 270, 220, 58);
	private Rectangle survivalModeButton = new Rectangle(274, 270, 220, 58);
	private Rectangle campaignModeButton = new Rectangle(514, 270, 220, 58);
	private Rectangle allyYesButton = new Rectangle(screenWidth/2 - 190, 270, 175, 54);
	private Rectangle allyNoButton = new Rectangle(screenWidth/2 + 15, 270, 175, 54);
	private Rectangle allyMeleeButton = new Rectangle(screenWidth/2 - 225, 270, 140, 54);
	private Rectangle allyArcherButton = new Rectangle(screenWidth/2 - 70, 270, 140, 54);
	private Rectangle allyBothButton = new Rectangle(screenWidth/2 + 85, 270, 140, 54);
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
				if (code == KeyEvent.VK_H && gameStarted && gameMode == GameMode.CAMPAIGN
						&& currentLayer == GameLayer.WORLD_MAP) {
					campaignHelpOpen = !campaignHelpOpen;
					repaint();
					return;
				}
				if (campaignHelpOpen) {
					if (code == KeyEvent.VK_ESCAPE) {
						campaignHelpOpen = false;
						repaint();
					}
					return;
				}
				if (campaignContextMenu != CampaignContextMenu.NONE && code == KeyEvent.VK_ESCAPE) {
					if (campaignContextMenu == CampaignContextMenu.ENCOUNTER) {
						leaveCampaignEncounter();
					} else if (campaignContextMenu == CampaignContextMenu.BATTLE_DECISION) {
						chooseCampaignBattlePursuit();
					} else {
						campaignContextMenu = CampaignContextMenu.NONE;
					}
					repaint();
					return;
				}
				if (campaignContextMenu == CampaignContextMenu.BATTLE_DECISION) {
					if (code == KeyEvent.VK_1) chooseCampaignBattlePursuit();
					else if (code == KeyEvent.VK_2) chooseCampaignBattleLetGo();
					else if (code == KeyEvent.VK_3) chooseCampaignBattleDemandSurrender();
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
						// The death menu and the survival power-up menu are modal: they have to be
						// answered with a click. Unpausing behind them would hide the menu but leave
						// the run dead (or waiting on a wave) with no way back.
						if (deathMenuOpen || survivalPowerUpMenuOpen) {
							return;
						}
						if (settingsMenuOpen) settingsMenuOpen = false;
						gamePaused = !gamePaused;
						// if (gameMode == GameMode.CAMPAIGN && campaignSession != null) {
						// 	campaignSession.setWorldPaused(gamePaused);
						// 	campaignSnapshot = campaignSession.getSnapshot();
						// }
						repaint();
					}
					if (gameStarted && gameMode == GameMode.CAMPAIGN) {
						if (code == KeyEvent.VK_F5 && campaignSession != null) {
							try {
								campaignSession.save(campaignSavePath);
								localInteractionMessage = "Campaign saved";
								showCampaignNotice("Campaign saved. This is a partial (beta) save: wars, sieges,"
									+ " titles and armies are re-simulated from the seed when you load.");
							} catch (java.io.IOException failure) {
								localInteractionMessage = "Save failed: " + failure.getMessage();
								showCampaignNotice("Save failed: " + failure.getMessage());
							}
							repaint();
							return;
						}
						if (code == KeyEvent.VK_F9) {
							try {
								campaignSession = CampaignSession.load(campaignSavePath);
								resetCampaignMapState();
								campaignSnapshot = campaignSession.getSnapshot();
								selectedCampaignSettlementId = campaignSnapshot.player.settlementId;
								currentLayer = GameLayer.WORLD_MAP;
								// A loaded session has no projected battle and none of the old
								// person IDs, so drop tactical actors instead of dangling them.
								activeCampaignBattle = null;
								enemies.clear();
								troops.clear();
								heroes.clear();
								recruitedHeroes.clear();
								allySquad.members.clear();
								enemySquad.members.clear();
								campaignLocalPlayerState = null;
								clearPlayerDeathState();
								localInteractionMessage = "Campaign loaded";
								showCampaignNotice("Campaign loaded. Wars, sieges, titles and armies were"
									+ " re-simulated from the seed, so they may differ from what you saved.");
							} catch (java.io.IOException failure) {
								localInteractionMessage = "Load failed: " + failure.getMessage();
								showCampaignNotice("Load failed: " + failure.getMessage());
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
							if (code == KeyEvent.VK_HOME) centerCampaignCameraOnPlayer();
							if (code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_ADD) {
								campaignCamera.zoomAt(screenWidth / 2, screenHeight / 2,
										screenWidth, screenHeight, 1.16, 0.22, 4.0);
							}
							if (code == KeyEvent.VK_MINUS || code == KeyEvent.VK_SUBTRACT) {
								campaignCamera.zoomAt(screenWidth / 2, screenHeight / 2,
										screenWidth, screenHeight, 1.0 / 1.16, 0.22, 4.0);
							}
							if (code == KeyEvent.VK_X && campaignSnapshot != null
									&& campaignSnapshot.player != null && campaignSnapshot.player.traveling) {
								campaignFollowArmyId = null;
								campaignFollowCaravanId = null;
								campaignFollowAnchorSettlementId = null;
								world.command.CommandResult result = campaignSession.cancelTravel();
								if (result != null && result.message != null && !result.message.isBlank()) {
									showCampaignNotice(result.accepted
											? "You turn back along the road." : result.message);
								}
							}
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
								if (code == KeyEvent.VK_T) {
									campaignFollowArmyId = null;
									campaignFollowCaravanId = null;
									campaignFollowAnchorSettlementId = null;
									world.command.CommandResult result =
											campaignSession.beginTravelTo(selectedCampaignSettlementId);
									if (result != null && result.message != null && !result.message.isBlank()) {
										showCampaignNotice(result.message);
									}
								}
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
			public void mousePressed(MouseEvent e) {
				java.awt.Point point = toGamePoint(e.getPoint());
				campaignMousePoint = point;
				if (gameStarted && gameMode == GameMode.CAMPAIGN
						&& currentLayer == GameLayer.WORLD_MAP && !gamePaused
						&& campaignContextMenu == CampaignContextMenu.NONE && !campaignHelpOpen
						&& !cheatMenuOpen && e.getButton() == MouseEvent.BUTTON1
						&& point.y < screenHeight - 32
						&& (activeCampaignTab == CampaignTab.OVERVIEW
								|| point.y < screenHeight - 39
										- (activeCampaignTab == CampaignTab.WAR
												|| activeCampaignTab == CampaignTab.FIEFS ? 212 : 166))) {
					campaignDragStart = point;
					campaignDragLast = point;
					campaignDraggingMap = false;
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (campaignDraggingMap) campaignSuppressNextMapClick = true;
				campaignDragStart = null;
				campaignDragLast = null;
				campaignDraggingMap = false;
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				java.awt.Point point = toGamePoint(e.getPoint());
				if (cheatMenuOpen) {
					handleCheatMenuClick(point);
					return;
				}
				if (campaignHelpOpen) {
					campaignHelpOpen = false;
					repaint();
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
					if (campaignSuppressNextMapClick) {
						campaignSuppressNextMapClick = false;
						return;
					}
					handleCampaignMapClick(point, e.getButton());
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
				// The pause-menu RESUME / SETTINGS buttons only exist on the pause menu itself.
				// While the power-up menu or the death menu is on screen they are not drawn, so
				// clicks in those rectangles must not silently resume a run the player died in.
				boolean pauseMenuVisible = gamePaused && !settingsMenuOpen
						&& !survivalPowerUpMenuOpen && !deathMenuOpen;
				if (pauseMenuVisible && pauseResumeButtonRect.contains(point)) {
					gamePaused = false;
					repaint();
					return;
				}
				if (pauseMenuVisible && settingsButtonRect.contains(point)) {
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
						// "Quit Survival Mode" only exists on the power-up menu
						if (getQuitButtonRect().contains(point)) {
							quitSurvival();
							return;
						}
						return; // never fall through to restart while choosing a power-up
					}
					if (deathMenuOpen) {
						if (getDeathRestartButtonRect().contains(point)) {
							restartSurvivalRun();
							return;
						}
						if (getDeathMainMenuButtonRect().contains(point)) {
							quitSurvival();
							return;
						}
						return; // swallow every other click while the death menu is up
					}
					if (getRestartButtonRect().contains(point)) {
						restartGame();
						return;
					}
					return;
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
			@Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
				if (gameStarted && gameMode == GameMode.CAMPAIGN
						&& currentLayer == GameLayer.WORLD_MAP
						&& campaignContextMenu == CampaignContextMenu.NONE && !campaignHelpOpen) {
					java.awt.Point point = toGamePoint(e.getPoint());
					campaignMousePoint = point;
					double factor = e.getWheelRotation() < 0 ? 1.16 : 1.0 / 1.16;
					campaignCamera.zoomAt(point.x, point.y, screenWidth, screenHeight,
							factor, 0.22, 4.0);
					if (campaignSnapshot != null) {
						double[] bounds = getCampaignBounds(campaignSnapshot);
						campaignCamera.clampTo(bounds[0] - 90, bounds[1] + 90,
								bounds[2] - 90, bounds[3] + 90, screenWidth, screenHeight, 70);
					}
					repaint();
					return;
				}
        		if (miniMapVisible) {
           		 // scrolling up (negative rotation) zooms in
            	adjustMinimapZoom(-e.getWheelRotation() * 0.2f);
        		}
   			 }
		});
	
		this.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override public void mouseDragged(MouseEvent e) {
				java.awt.Point point = toGamePoint(e.getPoint());
				campaignMousePoint = point;
				if (campaignDragStart != null && campaignDragLast != null
						&& gameMode == GameMode.CAMPAIGN && currentLayer == GameLayer.WORLD_MAP) {
					if (campaignDragStart.distance(point) > 4.0) campaignDraggingMap = true;
					if (campaignDraggingMap) {
						campaignCamera.panByScreenDelta(point.x - campaignDragLast.x,
								point.y - campaignDragLast.y);
						if (campaignSnapshot != null) {
							double[] bounds = getCampaignBounds(campaignSnapshot);
							campaignCamera.clampTo(bounds[0] - 90, bounds[1] + 90,
									bounds[2] - 90, bounds[3] + 90, screenWidth, screenHeight, 70);
						}
					}
					campaignDragLast = point;
				}
				repaint();
			}

			@Override public void mouseMoved(MouseEvent e) {
				campaignMousePoint = toGamePoint(e.getPoint());
				hoveredCampaignSettlementId = null;
				hoveredCampaignArmyId = null;
				hoveredCampaignCaravanId = null;
				if (gameMode == GameMode.CAMPAIGN && currentLayer == GameLayer.WORLD_MAP) {
					// Moving tokens sit above settlement art, so test them first.
					for (java.util.Map.Entry<Long, Rectangle> entry : campaignArmyHitboxes.entrySet()) {
						if (entry.getValue().contains(campaignMousePoint)) {
							hoveredCampaignArmyId = entry.getKey();
							break;
						}
					}
					if (hoveredCampaignArmyId == null) {
						for (java.util.Map.Entry<Long, Rectangle> entry : campaignCaravanHitboxes.entrySet()) {
							if (entry.getValue().contains(campaignMousePoint)) {
								hoveredCampaignCaravanId = entry.getKey();
								break;
							}
						}
					}
					if (hoveredCampaignArmyId == null && hoveredCampaignCaravanId == null
							&& (campaignPlayerHitbox == null
									|| !campaignPlayerHitbox.contains(campaignMousePoint))) {
						for (java.util.Map.Entry<Long, Rectangle> entry : campaignSettlementHitboxes.entrySet()) {
							if (entry.getValue().contains(campaignMousePoint)) {
								hoveredCampaignSettlementId = entry.getKey();
								break;
							}
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
	

	/** "Quit Survival Mode" button - only ever drawn on the survival power-up menu. */
	public Rectangle getQuitButtonRect(){
		return new Rectangle(100, screenHeight - 100, 200, 50);
	}

	/** "START AGAIN" button inside the death menu panel. */
	public Rectangle getDeathRestartButtonRect() {
		return new Rectangle(screenWidth / 2 - 170, screenHeight / 2 - 30, 340, 44);
	}

	/** "MAIN MENU" button inside the death menu panel. */
	public Rectangle getDeathMainMenuButtonRect() {
		return new Rectangle(screenWidth / 2 - 170, screenHeight / 2 + 30, 340, 44);
	}

	/** Restarts the survival run from wave 1 after a death, keeping the menu choices. */
	private void restartSurvivalRun() {
		deathMenuOpen = false;
		survivalPowerUpMenuOpen = false;
		settingsMenuOpen = false;
		gamePaused = false;
		player.projectiles.clear();
		player.areas.clear();
		startSurvivalMode(); // clears death state, refills health and restarts at wave 1
		repaint();
	}

	public void quitSurvival(){
		deathMenuOpen = false;
		settingsMenuOpen = false;
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
		survivalCurrentEnemyCount = survivalEnemyCountForWave;
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
		// setActiveHero(null) restores the stored (possibly 0) health, so clear the death
		// state last - otherwise the next run would start dead and reopen the death menu.
		clearPlayerDeathState();
	}

	/** Wipes every trace of a finished death: flags, animation frame and the hero mirror. */
	private void clearPlayerDeathState() {
		player.resetDeathState();
		player.health = player.maxHealth;
		player.stamina = player.maxStamina;
		player.mana = player.maxMana;
		if (activeHero != null) {
			activeHero.isDying = false;
			activeHero.resetDeathAnimation();
			activeHero.health = player.health;
		}
		for (Hero hero : heroes) {
			if (hero != null && hero.health <= 0) {
				hero.isDying = false;
				hero.resetDeathAnimation();
				hero.health = hero.maxHealth;
			}
		}
	}
	
	public Rectangle getRestartButtonRect() {
		return new Rectangle(screenWidth / 2 - 170, screenHeight / 2 + 65, 340, 44);
	}

	public void restartGame() {
		gold = 100;
		deathMenuOpen = false;
		settingsMenuOpen = false;
		player.health = player.maxHealth;
		player.stamina = player.maxStamina;
		player.mana = player.maxMana;
		player.projectiles.clear();
		player.areas.clear();
		player.inventory.clear();
		player.resetDeathState();
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

	/** Respawn the player with full health at a safe spot on the current map, clearing death state. */
	private void respawnPlayer() {
		player.health = player.maxHealth;
		player.stamina = player.maxStamina;
		player.mana = player.maxMana;
		player.resetDeathState();
		if (activeHero != null && activeHero.isActivePlayer) {
			activeHero.isDying = false;
			activeHero.resetDeathAnimation();
			activeHero.health = player.health;
			activeHero.x = player.x;
			activeHero.y = player.y;
		}
		// Move to an open spawn space near current position
		java.awt.Point openPt = findOpenSpawnSpace((int) player.x, (int) player.y, player);
		player.x = openPt.x;
		player.y = openPt.y;
		if (activeHero != null && activeHero.isActivePlayer) {
			activeHero.x = player.x;
			activeHero.y = player.y;
		}
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

		private void resetCampaignMapState() {
			campaignCameraInitialized = false;
			campaignTerrainImage = null;
			campaignTerrainImageKey = 0;
			campaignTerrainBounds = null;
			campaignFollowArmyId = null;
			campaignFollowCaravanId = null;
			campaignFollowAnchorSettlementId = null;
			campaignEncounterArmyId = null;
			campaignEncounterIgnoredArmyId = null;
			campaignEncounterGraceUntilMinute = Long.MIN_VALUE;
			campaignWasTraveling = false;
			campaignTravelDestinationSeen = null;
			campaignDragStart = null;
			campaignDragLast = null;
			campaignDraggingMap = false;
			campaignSuppressNextMapClick = false;
		}

		/** Centres the strategic camera on the moving player-party token. */
		private void centerCampaignCameraOnPlayer() {
			if (campaignSnapshot == null || campaignSnapshot.player == null) return;
			campaignCamera.centerX = campaignSnapshot.player.worldX;
			campaignCamera.centerY = campaignSnapshot.player.worldY;
			campaignCameraInitialized = true;
		}

		/**
		 * Per-frame campaign-map bookkeeping: camera bounds, riding to intercept a
		 * moving army/caravan, hostile contact, and opening a settlement on arrival.
		 */
		private void updateCampaignMapState() {
			CampaignSnapshot snapshot = campaignSnapshot;
			if (snapshot == null || snapshot.player == null || campaignSession == null) return;
			double[] bounds = getCampaignBounds(snapshot);
			if (!campaignCameraInitialized) {
				campaignCamera.fit(bounds[0] - 100, bounds[1] + 100,
						bounds[2] - 100, bounds[3] + 100, screenWidth, screenHeight, 42);
				campaignCameraInitialized = true;
			}
			campaignCamera.clampTo(bounds[0] - 90, bounds[1] + 90,
					bounds[2] - 90, bounds[3] + 90, screenWidth, screenHeight, 70);

			// Follow a moving target by riding to the settlement it is currently
			// marching toward. Re-issue only when that anchor changes, so the road
			// route is not rebuilt every frame.
			Long followAnchor = null;
			if (campaignFollowArmyId != null) {
				CampaignSnapshot.ArmyView target = snapshot.findArmy(campaignFollowArmyId);
				if (target == null) {
					campaignFollowArmyId = null;
					campaignFollowAnchorSettlementId = null;
				} else {
					followAnchor = target.destinationSettlementId != null
							? target.destinationSettlementId : target.currentSettlementId;
				}
			} else if (campaignFollowCaravanId != null) {
				CampaignSnapshot.CaravanView target = snapshot.findCaravan(campaignFollowCaravanId);
				if (target == null) {
					campaignFollowCaravanId = null;
					campaignFollowAnchorSettlementId = null;
				} else {
					followAnchor = target.destinationSettlementId != null
							? target.destinationSettlementId : target.currentSettlementId;
				}
			}
			if (followAnchor != null) {
				boolean needsRide = !followAnchor.equals(campaignFollowAnchorSettlementId)
						|| (!snapshot.player.traveling
								&& followAnchor.longValue() != snapshot.player.settlementId);
				if (needsRide && followAnchor.longValue() != snapshot.player.settlementId) {
					campaignFollowAnchorSettlementId = followAnchor;
					world.command.CommandResult result = campaignSession.beginTravelTo(followAnchor);
					if (result != null && !result.accepted) {
						campaignFollowArmyId = null;
						campaignFollowCaravanId = null;
						campaignFollowAnchorSettlementId = null;
						showCampaignNotice(result.message);
					} else {
						campaignSnapshot = campaignSession.getSnapshot();
						snapshot = campaignSnapshot;
					}
				}
			}

			boolean traveling = snapshot.player.traveling;
			if (tryOpenCampaignEncounter(snapshot)) {
				if (traveling) {
					campaignTravelDestinationSeen = snapshot.player.travelDestinationSettlementId;
				}
				campaignWasTraveling = traveling;
				return;
			}

			if (traveling) {
				campaignTravelDestinationSeen = snapshot.player.travelDestinationSettlementId;
			} else if (campaignWasTraveling) {
				Long arrivedAt = campaignTravelDestinationSeen;
				campaignTravelDestinationSeen = null;
				if (arrivedAt != null && snapshot.player.settlementId == arrivedAt.longValue()
						&& campaignFollowArmyId == null && campaignFollowCaravanId == null
						&& campaignContextMenu == CampaignContextMenu.NONE) {
					selectedCampaignSettlementId = arrivedAt;
					campaignContextMenu = CampaignContextMenu.SETTLEMENT;
					campaignContextMessage = "You have arrived.";
				}
			}
			campaignWasTraveling = traveling;
		}

		/**
		 * Opens the paused encounter when a hostile army reaches your commanded
		 * army (or, if you have none, your own party). Clicking an army to ride to
		 * it makes that army a valid contact even before a formal war exists; the
		 * session still validates the actual attack.
		 */
		private boolean tryOpenCampaignEncounter(CampaignSnapshot snapshot) {
			if (snapshot == null || snapshot.player == null || activeCampaignBattle != null
					|| campaignContextMenu != CampaignContextMenu.NONE
					|| currentLayer != GameLayer.WORLD_MAP || snapshot.player.isCaptive()) return false;
			CampaignSnapshot.ArmyView mine = findCommandedArmy(snapshot);
			CampaignSnapshot.ArmyView best = null;
			double bestDistance = Double.MAX_VALUE;
			for (CampaignSnapshot.ArmyView army : snapshot.armies) {
				if (army.strength <= 0 || "ENGAGED".equals(army.state)
						|| "DISBANDED".equals(army.state) || army.playerCommanded) continue;
				if (mine != null && army.id == mine.id) continue;
				if (campaignEncounterIgnoredArmyId != null
						&& campaignEncounterIgnoredArmyId.longValue() == army.id
						&& snapshot.worldMinute < campaignEncounterGraceUntilMinute) continue;
				boolean followed = campaignFollowArmyId != null
						&& campaignFollowArmyId.longValue() == army.id;
				double distance;
				boolean contact;
				if (mine != null) {
					distance = Math.hypot(mine.worldX - army.worldX, mine.worldY - army.worldY);
					contact = distance <= 12.0 && mine.realmId != army.realmId
							&& (army.hostileToPlayer || followed);
				} else {
					distance = Math.hypot(snapshot.player.worldX - army.worldX,
							snapshot.player.worldY - army.worldY);
					contact = distance <= 12.0 && army.hostileToPlayer;
				}
				if (contact && (followed || distance < bestDistance)) {
					best = army;
					bestDistance = distance;
					if (followed) break;
				}
			}
			if (best == null) return false;
			selectedCampaignActorId = best.id;
			campaignEncounterArmyId = best.id;
			campaignContextMenu = CampaignContextMenu.ENCOUNTER;
			campaignContextMessage = "";
			repaint();
			return true;
		}

		private void startCampaignMode() {
			resetCampaignMapState();
			campaignSession = new CampaignSession(CAMPAIGN_SEED);
			campaignSnapshot = campaignSession.getSnapshot();
			selectedCampaignSettlementId = campaignSnapshot.settlements.isEmpty()
				? null : campaignSnapshot.settlements.get(0).id;
			currentLayer = GameLayer.WORLD_MAP;
			gamePaused = false;
			deathMenuOpen = false;
			clearPlayerDeathState();
			setupMap("map1.txt");
			teleportPlayerForMap("map1.txt");
		}

		private void enterCampaignLocalView() {
			if (campaignSession == null || campaignSnapshot == null || campaignSnapshot.player == null) return;
			if (campaignSnapshot.player.traveling) {
				showCampaignNotice("You are on the road to "
						+ (campaignSnapshot.player.travelDestinationName == null
								? "your destination" : campaignSnapshot.player.travelDestinationName)
						+ ". Arrive there, or press X to turn back, before entering a settlement.");
				return;
			}
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
			} else if (nearest.type == LocalPlaceholder.Type.INN) {
				world.command.CommandResult result = campaignSession.restAtInn(8);
				localInteractionMessage = result.message;
				if (result.accepted) syncCampaignPlayerHealthFromPerson();
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
			if (campaignSnapshot == null || campaignSnapshot.player == null) return;
			if (campaignSnapshot.player.commandedArmyId == null) {
				showCampaignNotice("You command no army. Right-click a settlement you are standing in and raise a"
					+ " company, or take command of a realm army once you rule one.");
				return;
			}
			if (selectedCampaignActorId == null) {
				showCampaignNotice("Right-click an enemy army on the map to select it, then press K to attack it.");
				return;
			}
			world.command.CommandResult cleared = campaignSession.canEngageArmy(selectedCampaignActorId);
			if (!cleared.accepted) {
				showCampaignNotice(cleared.message);
				return;
			}
			launchCampaignBattle(campaignSnapshot.player.commandedArmyId, selectedCampaignActorId);
		}

		/**
		 * Projects two strategic armies into a tactical battle the player fights themselves.
		 * Your own army is always the attacker, so troops are yours and enemies are theirs.
		 */
		private void launchCampaignBattle(long attackerArmyId, long defenderArmyId) {
			if (campaignSession == null || activeCampaignBattle != null) return;
			world.Army attacker = campaignSession.getWorld().armies.get(attackerArmyId);
			world.Army defender = campaignSession.getWorld().armies.get(defenderArmyId);
			if (attacker == null || defender == null) {
				showCampaignNotice("That army is gone - the world moved on.");
				return;
			}
			world.command.CommandResult health = campaignSession.playerCanFight();
			if (!health.accepted) {
				showCampaignNotice(health.message);
				return;
			}
			// Companions fight beside you, and their wounds and deaths are reconciled.
			java.util.List<Long> companions = new java.util.ArrayList<>();
			if (campaignSnapshot.player != null) {
				long adventurer = campaignSession.getPlayerState().personId;
				for (Long memberId : campaignSnapshot.player.partyMemberIds) {
					if (memberId != null && memberId != adventurer) companions.add(memberId);
				}
			}
			try {
				activeCampaignBattle = campaignSession.getSimulation().getBattleBridge().createContext(
					attacker.id, defender.id, true, companions, campaignSnapshot.worldMinute);
			} catch (RuntimeException e) {
				showCampaignNotice("You cannot attack that army: " + e.getMessage());
				return;
			}
			setupMap("map1.txt");
			enemies.clear();
			troops.clear();
			allySquad.members.clear();
			enemySquad.members.clear();
			resetCampaignBattleProjectionState();
			int index = 0;
			for (BattleContext.TacticalCombatant combatant : activeCampaignBattle.attacker.combatants) {
				entity.Troop.Role role = combatant.role == BattleContext.TacticalRole.ARCHER
					? entity.Troop.Role.ARCHER : entity.Troop.Role.MELEE;
				entity.Troop troop = new entity.Troop(this, tileSize * (5 + index % 4), tileSize * (7 + index / 4), role);
				applyStrategicSource(troop, combatant);
				troop.maxHealth = Math.max(10, (int) Math.round(30 * combatant.defenseModifier));
				troop.health = troop.maxHealth;
				troop.beginCharge();
				troops.add(troop);
				campaignBattleTroops.add(troop);
				allySquad.addMember(troop);
				index++;
			}
			index = 0;
			for (BattleContext.TacticalCombatant combatant : activeCampaignBattle.defender.combatants) {
				Enemy enemy = new Enemy(this);
				enemy.x = tileSize * (25 + index % 4);
				enemy.y = tileSize * (7 + index / 4);
				enemy.setSpawnAnchor(enemy.x, enemy.y);
				boolean enemyCommander = combatant.role == BattleContext.TacticalRole.COMMANDER
						|| combatant.sourcePersonId == activeCampaignBattle.defender.commanderPersonId;
				enemy.setType(enemyCommander ? Enemy.Type.COMMANDER
						: combatant.role == BattleContext.TacticalRole.ARCHER
								? Enemy.Type.ARCHER : Enemy.Type.TROOP);
				applyStrategicSource(enemy, combatant);
				enemy.maxHealth = Math.max(10, (int) Math.round(
						25 * combatant.defenseModifier * (enemyCommander ? 1.8 : 1.0)));
				enemy.health = enemy.maxHealth;
				enemies.add(enemy);
				campaignBattleEnemies.add(enemy);
				enemySquad.addMember(enemy);
				index++;
			}
			teleportPlayerForMap("map1.txt");
			// The campaign health gate above proved the adventurer is alive, so
			// clear any stale local death animation before restoring that health.
			player.resetDeathState();
			// Any health above zero is enough to fight, including while wounded.
			syncCampaignPlayerHealthFromPerson();
			// Bring the recruited companions onto the battlefield next to the player.
			syncCampaignHeroesFromParty();
			int heroIndex = 0;
			for (Hero hero : heroes) {
				if (!hero.isRecruited || hero.isActivePlayer) continue;
				hero.x = tileSize * (3 + heroIndex % 3);
				hero.y = tileSize * (9 + heroIndex / 3);
				hero.health = hero.maxHealth;
				heroIndex++;
			}
			campaignBattleInitialAttackers = activeCampaignBattle.attacker.combatants.size();
			campaignBattleInitialDefenders = activeCampaignBattle.defender.combatants.size();
			campaignBattlePlayerKillsAtStart = player.totalKills;
			campaignBattlePhaseMessage = "Break their line: kill them, rout them, or bring down their commander.";
			currentLayer = GameLayer.BATTLE;
		}

		private void applyStrategicSource(entity.Entity entity, BattleContext.TacticalCombatant combatant) {
			entity.sourcePersonId = combatant.sourcePersonId;
			entity.sourceRegimentId = combatant.sourceRegimentId;
			entity.sourceArmyId = combatant.sourceArmyId;
		}

		private void resetCampaignBattleProjectionState() {
			campaignBattleTroops.clear();
			campaignBattleEnemies.clear();
			campaignBattleInitialAttackers = 0;
			campaignBattleInitialDefenders = 0;
			campaignBattlePlayerKillsAtStart = player == null ? 0 : player.totalKills;
			campaignBattleEnemyBroken = false;
			campaignBattlePursuingRout = false;
			campaignBattlePursuitTicks = 0;
			campaignBattleShowedMercy = false;
			campaignBattleRewardsRecorded = false;
			campaignBattleResolvingPlayerFall = false;
			campaignBattlePlayerDownAutoResolved = false;
			campaignBattlePhaseMessage = "";
			if (campaignContextMenu == CampaignContextMenu.BATTLE_DECISION) {
				campaignContextMenu = CampaignContextMenu.NONE;
			}
		}

		private boolean campaignBattleTroopStanding(entity.Troop troop) {
			return troop != null && troop.health > 0 && !troop.isMeleeDying && !troop.isArcherDying;
		}

		private int campaignBattleFriendlyStanding() {
			int count = 0;
			for (entity.Troop troop : campaignBattleTroops) if (campaignBattleTroopStanding(troop)) count++;
			return count;
		}

		private int campaignBattleEnemyStanding() {
			int count = 0;
			for (Enemy enemy : campaignBattleEnemies) if (enemy != null && enemy.isStandingToFight()) count++;
			return count;
		}

		private int campaignBattleEnemyRoutedAlive() {
			int count = 0;
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy != null && enemy.routed && enemy.isTargetableInBattle()) count++;
			}
			return count;
		}

		private int campaignBattleEnemySurrendered() {
			int count = 0;
			for (Enemy enemy : campaignBattleEnemies) if (enemy != null && enemy.surrendered && enemy.health > 0) count++;
			return count;
		}

		private int campaignBattleEnemyKilled() {
			int count = 0;
			for (Enemy enemy : campaignBattleEnemies) if (enemy != null && enemy.health <= 0) count++;
			return count;
		}

		private boolean campaignBattleEnemyCommanderDown() {
			if (activeCampaignBattle == null) return false;
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy == null) continue;
				boolean commander = enemy.type == Enemy.Type.COMMANDER
						|| (enemy.sourcePersonId != null
								&& enemy.sourcePersonId.longValue() == activeCampaignBattle.defender.commanderPersonId);
				if (commander && (enemy.health <= 0 || enemy.surrendered)) return true;
			}
			return false;
		}

		private int campaignBattlePersonalKills() {
			return Math.max(0, player.totalKills - campaignBattlePlayerKillsAtStart);
		}

		private int campaignBattleOpposingStanding() {
			return campaignBattleFriendlyStanding() + (player.health > 0 ? 1 : 0);
		}

		private void breakCampaignEnemyFormation() {
			if (activeCampaignBattle == null || campaignBattleEnemyBroken) return;
			campaignBattleEnemyBroken = true;
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy != null && enemy.isStandingToFight()) enemy.beginRout();
			}
			campaignBattlePhaseMessage = campaignBattleEnemyCommanderDown()
					? "Their commander is down and the enemy line breaks!"
					: "The enemy line breaks!";
			if (player.health > 0 && !player.isDead) {
				campaignContextMenu = CampaignContextMenu.BATTLE_DECISION;
				campaignContextMessage = campaignBattlePhaseMessage;
				repaint();
			} else {
				// Do not pause a dying player's death animation for a menu.
				campaignBattlePursuingRout = true;
				campaignBattlePursuitTicks = 60 * 20;
			}
		}

		private void chooseCampaignBattlePursuit() {
			campaignContextMenu = CampaignContextMenu.NONE;
			campaignContextMessage = "";
			campaignBattlePursuingRout = true;
			campaignBattlePursuitTicks = 60 * 20;
			campaignBattlePhaseMessage = "Pursuit! Cut them down or let the survivors escape. Press R to call it off.";
			for (entity.Troop troop : campaignBattleTroops) {
				if (campaignBattleTroopStanding(troop)) troop.beginCharge();
			}
			repaint();
		}

		private void chooseCampaignBattleLetGo() {
			campaignContextMenu = CampaignContextMenu.NONE;
			campaignContextMessage = "";
			campaignBattleShowedMercy = true;
			campaignBattlePursuingRout = false;
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy != null && enemy.routed && enemy.isTargetableInBattle()) enemy.markEscaped();
			}
			finishCampaignBattle(BattleResult.WinningSide.ATTACKER);
		}

		private void chooseCampaignBattleDemandSurrender() {
			boolean accepted = activeCampaignBattle != null
					&& entity.BattlefieldRules.surrenderAccepted(
							campaignBattleInitialDefenders,
							campaignBattleEnemyRoutedAlive() + campaignBattleEnemyStanding(),
							activeCampaignBattle.defender.startingMorale,
							campaignBattleEnemyCommanderDown(), campaignBattlePersonalKills(),
							campaignBattleOpposingStanding());
			campaignContextMenu = CampaignContextMenu.NONE;
			campaignContextMessage = "";
			if (!accepted) {
				campaignBattlePhaseMessage = "They refuse quarter and keep running!";
				chooseCampaignBattlePursuit();
				return;
			}
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy != null && enemy.health > 0 && !enemy.escaped) enemy.surrender();
			}
			finishCampaignBattle(BattleResult.WinningSide.ATTACKER);
		}

		/** Per-frame campaign battle referee: zero standing enemies, routs, pursuit, and player-down resolution. */
		private void updateCampaignBattleState() {
			if (activeCampaignBattle == null || campaignSession == null
					|| currentLayer != GameLayer.BATTLE) return;
			if (campaignContextMenu == CampaignContextMenu.BATTLE_DECISION) return;

			int enemyStanding = campaignBattleEnemyStanding();
			if (enemyStanding == 0 && !campaignBattlePursuingRout) {
				if (player.isDead) resolveCampaignBattleAfterPlayerFalls();
				else finishCampaignBattle(null);
				return;
			}

			if (!campaignBattleEnemyBroken && entity.BattlefieldRules.shouldEnemyBreak(
					campaignBattleInitialDefenders, enemyStanding,
					activeCampaignBattle.defender.startingMorale,
					campaignBattleEnemyCommanderDown(), campaignBattlePersonalKills(),
					campaignBattleOpposingStanding())) {
				breakCampaignEnemyFormation();
				return;
			}

			if (campaignBattlePursuingRout) {
				if (player.isDead) {
					resolveCampaignBattleAfterPlayerFalls();
					return;
				}
				if (campaignBattleEnemyRoutedAlive() == 0) {
					if (player.isDead) resolveCampaignBattleAfterPlayerFalls();
					else finishCampaignBattle(BattleResult.WinningSide.ATTACKER);
					return;
				}
				if (--campaignBattlePursuitTicks <= 0) {
					for (Enemy enemy : campaignBattleEnemies) {
						if (enemy != null && enemy.routed && enemy.isTargetableInBattle()) enemy.markEscaped();
					}
					if (player.isDead) resolveCampaignBattleAfterPlayerFalls();
					else finishCampaignBattle(BattleResult.WinningSide.ATTACKER);
					return;
				}
			}

			if (player.isDead) {
				if (campaignBattleFriendlyStanding() > 0) resolveCampaignBattleAfterPlayerFalls();
				else handleCampaignDefeat();
			}
			// If every friendly troop is down but the player still has health, the
			// battle deliberately continues: the player can finish it alone.
		}

		private double campaignBattleRemainingFriendlyPower() {
			double power = 0.0;
			for (entity.Troop troop : campaignBattleTroops) {
				if (!campaignBattleTroopStanding(troop)) continue;
				double healthFraction = troop.maxHealth <= 0 ? 0.0
						: troop.health / (double) troop.maxHealth;
				power += healthFraction * (troop.role == entity.Troop.Role.ARCHER ? 0.9 : 1.1);
			}
			return power;
		}

		private double campaignBattleRemainingEnemyPower() {
			double power = 0.0;
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy == null || !enemy.isStandingToFight()) continue;
				double healthFraction = enemy.maxHealth <= 0 ? 0.0
						: enemy.health / (double) enemy.maxHealth;
				power += healthFraction * (enemy.type == Enemy.Type.COMMANDER ? 1.8
						: enemy.type == Enemy.Type.ARCHER ? 0.9 : 1.0);
			}
			return power;
		}

		private void applyRemainingBattleCasualties(int result) {
			java.util.List<entity.Troop> friendly = new java.util.ArrayList<>();
			for (entity.Troop troop : campaignBattleTroops) {
				if (campaignBattleTroopStanding(troop)) friendly.add(troop);
			}
			friendly.sort(java.util.Comparator.comparingDouble(troop ->
					troop.maxHealth <= 0 ? 0.0 : troop.health / (double) troop.maxHealth));
			double friendlyKillFraction = result > 0 ? 0.12 : result == 0 ? 0.22 : 0.35;
			int friendlyKills = (int) Math.round(friendly.size() * friendlyKillFraction);
			for (int i = 0; i < friendly.size(); i++) {
				entity.Troop troop = friendly.get(i);
				if (i < friendlyKills) troop.health = 0;
				else if (i < friendlyKills + Math.max(1, friendly.size() / 3)) {
					troop.health = Math.max(1, troop.health / 2);
				}
			}

			java.util.List<Enemy> enemiesStanding = new java.util.ArrayList<>();
			for (Enemy enemy : campaignBattleEnemies) {
				if (enemy != null && enemy.isStandingToFight()) enemiesStanding.add(enemy);
			}
			enemiesStanding.sort(java.util.Comparator.comparingDouble(enemy ->
					enemy.maxHealth <= 0 ? 0.0 : enemy.health / (double) enemy.maxHealth));
			double enemyKillFraction = result > 0 ? 0.30 : result == 0 ? 0.22 : 0.12;
			int enemyKills = (int) Math.round(enemiesStanding.size() * enemyKillFraction);
			for (int i = 0; i < enemiesStanding.size(); i++) {
				Enemy enemy = enemiesStanding.get(i);
				if (i < enemyKills) enemy.health = 0;
				else if (i < enemyKills + Math.max(1, enemiesStanding.size() / 3)) {
					enemy.health = Math.max(1, enemy.health / 2);
				}
				if (result >= 0 && enemy.health > 0) enemy.beginRout();
			}
			campaignBattleEnemyBroken = campaignBattleEnemyBroken || result >= 0;
		}

		/** Fast-resolves the rest of a battle after the adventurer is knocked down. */
		private void resolveCampaignBattleAfterPlayerFalls() {
			if (activeCampaignBattle == null || campaignBattleResolvingPlayerFall) return;
			campaignBattleResolvingPlayerFall = true;
			if (campaignBattleEnemyStanding() == 0) {
				// The field was already cleared or broken before the fall; do not
				// invent extra casualties. finishCampaignBattle decides victory
				// (friendly troops remain) versus draw (nobody remains standing).
				finishCampaignBattle(null);
				return;
			}
			int result = entity.BattlefieldRules.remainingForceResult(
					campaignBattleRemainingFriendlyPower(), campaignBattleRemainingEnemyPower());
			applyRemainingBattleCasualties(result);
			if (result > 0) {
				finishCampaignBattle(BattleResult.WinningSide.ATTACKER);
			} else if (result == 0 && campaignBattleFriendlyStanding() > 0) {
				finishCampaignBattle(BattleResult.WinningSide.DRAW);
			} else {
				campaignBattlePlayerDownAutoResolved = true;
				handleCampaignDefeat();
			}
		}

		private void resolveCampaignTacticalBattle() {
			if (activeCampaignBattle == null
					|| campaignContextMenu == CampaignContextMenu.BATTLE_DECISION) return;
			if (campaignBattleEnemyStanding() == 0) {
				finishCampaignBattle(null);
			} else if (campaignBattlePursuingRout) {
				finishCampaignBattle(BattleResult.WinningSide.ATTACKER);
			} else {
				// R is now an explicit withdrawal order while the enemy still stands.
				finishCampaignBattle(BattleResult.WinningSide.DEFENDER);
			}
		}

		/**
		 * Ends the projected strategic battle and reconciles it exactly once.
		 * Victory is not a mere living-count comparison: no standing defenders
		 * means the attackers hold the field, routed/surrendered defenders no
		 * longer count as standing, and a living player can win after every
		 * friendly troop has fallen.
		 */
		private void finishCampaignBattle(BattleResult.WinningSide forcedWinner) {
			if (activeCampaignBattle == null || campaignSession == null) return;
			BattleContext battle = activeCampaignBattle;
			java.util.List<entity.Troop> projectedTroops = campaignBattleTroops.isEmpty()
					? troops : campaignBattleTroops;
			java.util.List<Enemy> projectedEnemies = campaignBattleEnemies.isEmpty()
					? enemies : campaignBattleEnemies;
			java.util.Map<Long, BattleResult.CasualtyOutcome> outcomes = new java.util.HashMap<>();

			int attackerStanding = 0;
			int attackerKilled = 0;
			for (entity.Troop troop : projectedTroops) {
				if (troop == null || troop.sourcePersonId == null) continue;
				BattleResult.CasualtyOutcome outcome;
				if (troop.health <= 0 || troop.isMeleeDying || troop.isArcherDying) {
					outcome = BattleResult.CasualtyOutcome.KILLED;
					attackerKilled++;
				} else if (troop.health < troop.maxHealth / 3) {
					outcome = BattleResult.CasualtyOutcome.SEVERELY_WOUNDED;
					attackerStanding++;
				} else if (troop.health < troop.maxHealth) {
					outcome = BattleResult.CasualtyOutcome.WOUNDED;
					attackerStanding++;
				} else {
					outcome = BattleResult.CasualtyOutcome.UNHARMED;
					attackerStanding++;
				}
				outcomes.put(troop.sourcePersonId, outcome);
			}

			int defenderStanding = 0;
			int defenderKilled = 0;
			int defenderRouted = 0;
			int defenderCaptured = 0;
			for (Enemy enemy : projectedEnemies) {
				if (enemy == null || enemy.sourcePersonId == null) continue;
				BattleResult.CasualtyOutcome outcome;
				if (enemy.surrendered && enemy.health > 0) {
					outcome = BattleResult.CasualtyOutcome.CAPTURED;
					defenderCaptured++;
				} else if (enemy.dead || enemy.health <= 0) {
					outcome = BattleResult.CasualtyOutcome.KILLED;
					defenderKilled++;
				} else if (enemy.health < enemy.maxHealth / 3) {
					outcome = BattleResult.CasualtyOutcome.SEVERELY_WOUNDED;
				} else if (enemy.health < enemy.maxHealth) {
					outcome = BattleResult.CasualtyOutcome.WOUNDED;
				} else {
					outcome = BattleResult.CasualtyOutcome.UNHARMED;
				}
				if (enemy.isStandingToFight()) defenderStanding++;
				if (enemy.routed && enemy.health > 0) defenderRouted++;
				outcomes.put(enemy.sourcePersonId, outcome);
			}

			boolean playerStanding = player.health > 0 && !player.isDead;
			BattleResult.WinningSide winner;
			if (forcedWinner != null) {
				winner = forcedWinner;
			} else if (defenderStanding == 0 && (attackerStanding > 0 || playerStanding)) {
				winner = BattleResult.WinningSide.ATTACKER;
			} else if (attackerStanding == 0 && !playerStanding && defenderStanding > 0) {
				winner = BattleResult.WinningSide.DEFENDER;
			} else if (defenderStanding == 0 && attackerStanding == 0 && !playerStanding) {
				winner = BattleResult.WinningSide.DRAW;
			} else {
				int attackerScore = attackerStanding + (playerStanding ? 1 : 0);
				winner = attackerScore == defenderStanding ? BattleResult.WinningSide.DRAW
						: attackerScore > defenderStanding ? BattleResult.WinningSide.ATTACKER
								: BattleResult.WinningSide.DEFENDER;
			}

			BattleResult result = campaignSession.getSimulation().getBattleBridge().resultFromTactical(
					battle.battleId, winner, outcomes, 60L);
			campaignSession.getSimulation().getBattleBridge().reconcile(result);

			int personalKills = campaignBattlePersonalKills();
			boolean commanderDown = campaignBattleEnemyCommanderDown();
			String rewardSummary = "";
			if (winner != BattleResult.WinningSide.DEFENDER && !campaignBattleRewardsRecorded) {
				campaignBattleRewardsRecorded = true;
				rewardSummary = campaignSession.recordPlayerBattleOutcome(
						winner == BattleResult.WinningSide.ATTACKER,
						winner == BattleResult.WinningSide.DRAW,
						personalKills, defenderKilled, defenderCaptured, defenderRouted,
						commanderDown, campaignBattlePursuingRout, campaignBattleShowedMercy,
						battle.defender.realmId);
				if (activeHero != null && activeHero.isActivePlayer) {
					activeHero.gainExperience(20 + personalKills * 4 + defenderCaptured * 4
							+ (commanderDown ? 15 : 0));
				}
			}

			String fieldReport;
			if (forcedWinner == BattleResult.WinningSide.DEFENDER && player.isDead) {
				fieldReport = null; // handleCampaignDefeat reports the personal consequence.
			} else if (winner == BattleResult.WinningSide.DEFENDER) {
				fieldReport = "Your force is driven from the field. " + defenderStanding
						+ " enemy soldiers still stand; your losses: " + attackerKilled + ".";
			} else if (winner == BattleResult.WinningSide.DRAW) {
				fieldReport = "The battle ends indecisively. Enemy losses: " + defenderKilled
						+ " killed, " + defenderRouted + " routed. " + rewardSummary;
			} else {
				fieldReport = "Victory — you hold the field. Enemy: " + defenderKilled
						+ " killed, " + defenderRouted + " routed/escaped, "
						+ defenderCaptured + " captured. Your standing troops: "
						+ attackerStanding + (attackerStanding == 0 && playerStanding
								? " — you finished it alone" : "") + ". " + rewardSummary;
			}
			if (commanderDown && winner == BattleResult.WinningSide.ATTACKER && fieldReport != null) {
				fieldReport += " Their commander was brought down.";
			}
			if (player.isDead && winner != BattleResult.WinningSide.DEFENDER) {
				world.command.CommandResult knockedOut =
						campaignSession.recordPlayerKnockedOutArmyHeldField(
								winner == BattleResult.WinningSide.ATTACKER);
				respawnPlayer();
				syncCampaignPlayerHealthFromPerson();
				fieldReport = knockedOut.message + " " + (fieldReport == null ? "" : fieldReport);
			}

			activeCampaignBattle = null;
			campaignContextMenu = CampaignContextMenu.NONE;
			campaignContextMessage = "";
			campaignContextButtons.clear();
			campaignBattleTroops.clear();
			campaignBattleEnemies.clear();
			campaignBattleEnemyBroken = false;
			campaignBattlePursuingRout = false;
			campaignBattlePursuitTicks = 0;
			enemies.clear();
			troops.clear();
			allySquad.members.clear();
			enemySquad.members.clear();
			campaignSnapshot = CampaignSnapshot.capture(campaignSession.getSimulation());
			currentLayer = GameLayer.WORLD_MAP;
			if (fieldReport != null) showCampaignNotice(fieldReport);
		}

		/**
		 * The adventurer died in campaign mode. In a projected battle this no
		 * longer automatically erases a winning army: remaining troops first get
		 * their fast resolution, and only an actual remaining-force loss settles
		 * here as a campaign defeat.
		 */
		private void handleCampaignDefeat() {
			if (campaignSession == null) {
				currentLayer = GameLayer.WORLD_MAP;
				respawnPlayer();
				return;
			}
			if (activeCampaignBattle != null && !campaignBattlePlayerDownAutoResolved
					&& (campaignBattleFriendlyStanding() > 0 || campaignBattleEnemyStanding() == 0)) {
				resolveCampaignBattleAfterPlayerFalls();
				return;
			}
			double defeatMargin = 0.15;
			Long captorRealmId = null;
			if (activeCampaignBattle != null) {
				int yourStanding = campaignBattleFriendlyStanding();
				int enemyStanding = campaignBattleEnemyStanding();
				int total = yourStanding + enemyStanding;
				defeatMargin = total == 0 ? 1.0 : Math.max(0.0, Math.min(1.0,
						1.0 - yourStanding / (double) total));
				captorRealmId = activeCampaignBattle.defender.realmId;
				finishCampaignBattle(BattleResult.WinningSide.DEFENDER);
			}
			world.command.CommandResult result = campaignSession.resolvePlayerDefeat(captorRealmId, defeatMargin);
			enemies.clear();
			troops.clear();
			allySquad.members.clear();
			enemySquad.members.clear();
			currentLayer = GameLayer.WORLD_MAP;
			campaignSnapshot = campaignSession.getSnapshot();
			respawnPlayer();
			syncCampaignPlayerHealthFromPerson();
			showCampaignNotice(result.message);
		}

		/** Copies the world person's health onto the local Player sprite (campaign only). */
		private void syncCampaignPlayerHealthFromPerson() {
			if (campaignSession == null) return;
			world.Person adventurer = campaignSession.getWorld().people.get(
				campaignSession.getPlayerState().personId);
			if (adventurer == null) return;
			int restored = (int) Math.round(adventurer.health.healthLevel / 100.0 * player.maxHealth);
			player.health = Math.max(1, Math.min(player.maxHealth, restored));
			if (activeHero != null && activeHero.isActivePlayer) activeHero.health = player.health;
		}

		private void startSurvivalMode() {
		survivalWaveNumber = 1;
		survivalEnemyCountForWave = 10;
		survivalCurrentEnemyCount = survivalEnemyCountForWave;
		activePowerUps.clear();
		deathMenuOpen = false;
		survivalPowerUpMenuOpen = false;
		setActiveHero(null);
		heroes.clear();
		recruitedHeroes.clear();
		// A new run always starts alive: wipe any death state left over from a previous run
		// (setActiveHero(null) restores a stored health value that may already be 0).
		clearPlayerDeathState();
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
		// If the player went down on the same frame the wave cleared, the run is over - opening
		// the power-up menu here would stack on top of the death menu and swallow the click that
		// is supposed to restart/quit.
		if (player.isDying()) {
			return;
		}
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

			// A dead player always ends up in front of the death menu, even if the run was paused
			// for some other reason on the very frame the death animation finished. Without this
			// the pause gate below would swallow the death and the game would sit there unplayable.
			if (gameStarted && gameMode == GameMode.SURVIVAL && player.isDead && !deathMenuOpen) {
				survivalPowerUpMenuOpen = false;
				settingsMenuOpen = false;
				gamePaused = true;
				deathMenuOpen = true;
			}
			if (!gameStarted || gamePaused || cheatMenuOpen || campaignHelpOpen
					|| campaignContextMenu != CampaignContextMenu.NONE) {
				return;
			}
			if (gameMode == GameMode.CAMPAIGN && campaignSession != null) {
				campaignSession.update(1.0 / FPS);
				campaignSnapshot = campaignSession.getSnapshot();
				pumpCampaignToasts(campaignSnapshot);
				if (currentLayer == GameLayer.WORLD_MAP) updateCampaignMapState();
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

		// Handle player death according to game mode
		if (player.isDead && !deathMenuOpen && !gamePaused) {
			switch (gameMode) {
				case SURVIVAL:
					gamePaused = true;
					survivalPowerUpMenuOpen = false;
					settingsMenuOpen = false;
					deathMenuOpen = true;
					break;
				case CAMPAIGN:
					if (activeCampaignBattle != null
							&& (campaignBattleFriendlyStanding() > 0
									|| campaignBattleEnemyStanding() == 0)) {
						// Your soldiers get the rest of this frame; the battle referee
						// at the end of update resolves whether they hold the field.
					} else {
						handleCampaignDefeat();
					}
					break;
				case SANDBOX:
					// Respawn the player
					respawnPlayer();
					break;
			}
		}

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

		updateCampaignBattleState();
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
			drawCampaignHelp(g2);
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
			g2.setColor(new Color(0, 0, 0, 205));
			g2.fillRoundRect(118, 10, 532, 50, 9, 9);
			g2.setColor(new Color(225, 190, 105));
			g2.drawRoundRect(118, 10, 532, 50, 9, 9);
			g2.setFont(new Font("SansSerif", Font.BOLD, 12));
			g2.setColor(Color.YELLOW);
			String battleCounts = "Battle " + activeCampaignBattle.battleId
					+ "  |  Your troops " + campaignBattleFriendlyStanding() + "/"
					+ campaignBattleInitialAttackers
					+ "  |  Enemy standing " + campaignBattleEnemyStanding()
					+ ", routed " + campaignBattleEnemyRoutedAlive()
					+ "  |  Your kills " + campaignBattlePersonalKills();
			g2.drawString(battleCounts, 132, 29);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
			g2.setColor(Color.WHITE);
			String phase = campaignBattlePhaseMessage == null || campaignBattlePhaseMessage.isBlank()
					? "Victory: no enemy still stands, or the enemy breaks and runs."
					: campaignBattlePhaseMessage;
			String command = campaignBattlePursuingRout ? "  R: call off pursuit."
					: "  R: withdraw your force.";
			g2.drawString(phase + command, 132, 48);
		}
		if (currentLayer == GameLayer.DEPLOYMENT) {
			drawDeploymentScreen(g2);
		} else if (gamePaused) {
			if(survivalPowerUpMenuOpen) {
				drawPowerUpMenu(g2);
			}else if (settingsMenuOpen) {
				drawSettingsMenu(g2);
			} else if (deathMenuOpen) {
				drawDeathMenu(g2);
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

	/** Closes an encounter without fighting and suppresses immediate re-triggering. */
	private void leaveCampaignEncounter() {
		campaignEncounterIgnoredArmyId = campaignEncounterArmyId != null
				? campaignEncounterArmyId : selectedCampaignActorId;
		campaignEncounterGraceUntilMinute = campaignSnapshot == null ? Long.MAX_VALUE
				: campaignSnapshot.worldMinute + 12L * 60L;
		campaignEncounterArmyId = null;
		campaignFollowArmyId = null;
		campaignFollowAnchorSettlementId = null;
		campaignContextMenu = CampaignContextMenu.NONE;
		campaignContextMessage = "";
	}

	/**
	 * Bannerlord-style paused encounter: force comparison across the top, choices
	 * on the left, and the live campaign map still visible behind them.
	 */
	private void drawCampaignEncounter(Graphics2D g2) {
		campaignContextButtons.clear();
		Long targetId = campaignEncounterArmyId != null
				? campaignEncounterArmyId : selectedCampaignActorId;
		CampaignSnapshot.ArmyView enemy = targetId == null
				? null : campaignSnapshot.findArmy(targetId);
		CampaignSnapshot.ArmyView mine = findCommandedArmy(campaignSnapshot);

		// Top comparison bar.
		g2.setColor(new Color(16, 18, 20, 238));
		g2.fillRect(0, 0, screenWidth, 58);
		g2.setColor(new Color(190, 160, 95));
		g2.drawLine(0, 58, screenWidth, 58);
		g2.setFont(new Font("Serif", Font.BOLD, 20));
		g2.setColor(new Color(240, 228, 200));
		String title = "Battle";
		g2.drawString(title, screenWidth / 2 - g2.getFontMetrics().stringWidth(title) / 2, 24);
		g2.setStroke(new BasicStroke(2f));
		g2.drawLine(screenWidth / 2 - 8, 34, screenWidth / 2 + 8, 52);
		g2.drawLine(screenWidth / 2 - 8, 52, screenWidth / 2 + 8, 34);
		g2.setStroke(new BasicStroke(1f));

		int yourStrength = mine != null ? mine.strength
				: campaignSnapshot.player == null ? 0 : campaignSnapshot.player.partySize;
		int enemyStrength = enemy == null ? 0 : enemy.strength;
		g2.setFont(new Font("SansSerif", Font.BOLD, 17));
		g2.setColor(new Color(150, 230, 160));
		String yourNumber = Integer.toString(yourStrength);
		g2.drawString(yourNumber, screenWidth / 2 - 42 - g2.getFontMetrics().stringWidth(yourNumber), 50);
		g2.setColor(new Color(255, 140, 120));
		g2.drawString(Integer.toString(enemyStrength), screenWidth / 2 + 42, 50);

		// Heraldic blocks: yours on the left of the title, theirs on the right.
		int leftShieldX = screenWidth / 2 - 176;
		g2.setColor(mine == null ? new Color(245, 196, 65) : colorForRealm(mine.realmId));
		g2.fillRect(leftShieldX, 12, 34, 30);
		g2.fillPolygon(new int[] {leftShieldX, leftShieldX + 34, leftShieldX + 17},
				new int[] {42, 42, 54}, 3);
		g2.setColor(new Color(230, 205, 150));
		g2.drawRect(leftShieldX, 12, 34, 30);
		g2.setFont(new Font("SansSerif", Font.BOLD, 10));
		g2.setColor(new Color(150, 230, 160));
		String yourLabel = mine == null ? "Your Party" : "Your Army";
		g2.drawString(yourLabel, leftShieldX - 48, 32);

		int rightShieldX = screenWidth / 2 + 142;
		g2.setColor(enemy == null ? Color.GRAY : colorForRealm(enemy.realmId));
		g2.fillRect(rightShieldX, 12, 34, 30);
		g2.fillPolygon(new int[] {rightShieldX, rightShieldX + 34, rightShieldX + 17},
				new int[] {42, 42, 54}, 3);
		g2.setColor(new Color(230, 205, 150));
		g2.drawRect(rightShieldX, 12, 34, 30);
		g2.setColor(new Color(255, 140, 120));
		String enemyLabel = enemy == null ? "Gone"
				: campaignRealmName(campaignSnapshot, enemy.realmId) + " Army";
		g2.drawString(enemyLabel, rightShieldX + 42, 32);

		// Choice panel.
		int x = 10, y = 68, w = 226, h = 318;
		g2.setColor(new Color(18, 42, 62, 242));
		g2.fillRoundRect(x, y, w, h, 8, 8);
		g2.setColor(new Color(205, 178, 112));
		g2.drawRoundRect(x, y, w, h, 8, 8);
		g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
		g2.setColor(Color.WHITE);
		int textY = y + 24;
		String encounterText = enemy == null
				? "That army is no longer here."
				: "You encountered the " + campaignRealmName(campaignSnapshot, enemy.realmId)
						+ " army.";
		for (String line : wrapCampaignText(g2, encounterText, w - 20, 3)) {
			g2.drawString(line, x + 10, textY);
			textY += 15;
		}
		textY += 4;
		g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
		g2.setColor(new Color(205, 215, 225));
		if (enemy != null) {
			g2.drawString("Commander:", x + 10, textY); textY += 13;
			for (String line : wrapCampaignText(g2, enemy.commanderName, w - 28, 2)) {
				g2.drawString("  " + line, x + 10, textY);
				textY += 13;
			}
			g2.drawString("Troops " + enemy.strength + "  morale " + Math.round(enemy.morale) + "%",
					x + 10, textY); textY += 13;
			g2.drawString(enemy.order + " -> " + settlementName(campaignSnapshot,
					enemy.destinationSettlementId), x + 10, textY); textY += 18;
		}
		if (mine != null) {
			g2.setColor(new Color(255, 224, 145));
			g2.drawString("Yours: " + mine.strength + " troops, morale "
					+ Math.round(mine.morale) + "%", x + 10, textY);
			textY += 15;
			g2.drawString("Health " + campaignSnapshot.player.healthPercent + "%"
					+ (campaignSnapshot.player.wounded ? " (wounded)" : "")
					+ " | Grain " + mine.grain + (mine.daysWithoutFood > 0
					? " (" + mine.daysWithoutFood + "d unfed)" : ""), x + 10, textY);
			textY += 18;
		} else {
			g2.setColor(new Color(255, 190, 140));
			for (String line : wrapCampaignText(g2,
					"You command no army. Raise a company at a settlement to fight one.",
					w - 20, 3)) {
				g2.drawString(line, x + 10, textY);
				textY += 13;
			}
			textY += 5;
		}
		addContextButton(g2, "ENCOUNTER_ATTACK", "Attack!", x + 12, y + 176, w - 24, 34);
		addContextButton(g2, "ENCOUNTER_SEND", "Send in your troops.", x + 12, y + 216, w - 24, 34);
		addContextButton(g2, "ENCOUNTER_LEAVE", "Leave...", x + 12, y + 256, w - 24, 34);
		if (campaignContextMessage != null && !campaignContextMessage.isBlank()) {
			g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
			g2.setColor(new Color(255, 205, 160));
			int messageY = y + h - 8;
			java.util.List<String> lines = wrapCampaignText(g2, campaignContextMessage, w - 20, 2);
			for (int i = lines.size() - 1; i >= 0; i--) {
				g2.drawString(lines.get(i), x + 10, messageY);
				messageY -= 12;
			}
		}

		g2.setFont(new Font("SansSerif", Font.BOLD, 16));
		String paused = "PAUSED";
		int pausedX = screenWidth / 2 - g2.getFontMetrics().stringWidth(paused) / 2;
		g2.setColor(Color.BLACK);
		g2.drawString(paused, pausedX + 1, screenHeight - 45);
		g2.setColor(Color.WHITE);
		g2.drawString(paused, pausedX, screenHeight - 46);
	}

	/** Post-rout order: pursue for more salvage, show mercy for reputation, or demand prisoners. */
	private void drawCampaignBattleDecision(Graphics2D g2) {
		campaignContextButtons.clear();
		int x = screenWidth / 2 - 246, y = 104, w = 492, h = 286;
		g2.setColor(new Color(0, 0, 0, 175));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setColor(new Color(22, 30, 38, 246));
		g2.fillRoundRect(x, y, w, h, 14, 14);
		g2.setColor(new Color(225, 190, 105));
		g2.drawRoundRect(x, y, w, h, 14, 14);
		g2.setFont(new Font("Serif", Font.BOLD, 23));
		g2.setColor(new Color(235, 205, 130));
		g2.drawString("THE ENEMY BREAKS!", x + 22, y + 36);
		g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
		g2.setColor(Color.WHITE);
		int routed = campaignBattleEnemyRoutedAlive();
		int killed = campaignBattleEnemyKilled();
		boolean surrenderLikely = activeCampaignBattle != null
				&& entity.BattlefieldRules.surrenderAccepted(
						campaignBattleInitialDefenders, routed,
						activeCampaignBattle.defender.startingMorale,
						campaignBattleEnemyCommanderDown(), campaignBattlePersonalKills(),
						campaignBattleOpposingStanding());
		g2.drawString(killed + " enemy dead, " + routed + " running. "
				+ (campaignBattleEnemyCommanderDown() ? "Their commander is down." : "Their formation has collapsed."), x + 22, y + 62);
		g2.drawString("Victory is yours. Choose what your soldiers do next:", x + 22, y + 82);
		addContextButton(g2, "BATTLE_PURSUE", "1  Pursue them — more cuts and salvage (20s)", x + 22, y + 104, w - 44, 42);
		addContextButton(g2, "BATTLE_LET_GO", "2  Let them go — end cleanly, gain mercy reputation", x + 22, y + 156, w - 44, 42);
		addContextButton(g2, "BATTLE_DEMAND_SURRENDER",
				"3  Demand surrender — " + (surrenderLikely ? "likely prisoners" : "they may refuse"), x + 22, y + 208, w - 44, 42);
		g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
		g2.setColor(new Color(190, 205, 215));
		g2.drawString("ESC also chooses pursuit.", x + 22, y + h - 12);
	}

	private void drawCampaignContextMenu(Graphics2D g2) {
		if (campaignContextMenu == CampaignContextMenu.NONE || campaignSession == null || campaignSnapshot == null) return;
		if (campaignContextMenu == CampaignContextMenu.ENCOUNTER) {
			drawCampaignEncounter(g2);
			return;
		}
		if (campaignContextMenu == CampaignContextMenu.BATTLE_DECISION) {
			drawCampaignBattleDecision(g2);
			return;
		}
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
			world.geography.Settlement garrisoned = campaignSession.getWorld().geography
					.getSettlement(settlement.id);
			int weaponsHere = garrisoned == null ? 0
					: garrisoned.publicStockpile.getQuantity(GoodType.WEAPONS);
			g2.setColor(new Color(190, 200, 210));
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			g2.drawString("Raise your own company: 35c and one weapon per volunteer, from your purse."
					+ "  Weapons here: " + weaponsHere, x + 28, y + 330);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
			addContextButton(g2, "RAISE_INFANTRY", "Raise 10 infantry", x + 28, y + 336, 140, 32);
			addContextButton(g2, "RAISE_ARCHERS", "Raise 5 archers", x + 176, y + 336, 130, 32);
			addContextButton(g2, "RAISE_SPEARS", "Raise 5 spears", x + 314, y + 336, 130, 32);
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
				Long commandedArmyId = campaignSnapshot.player == null
						? null : campaignSnapshot.player.commandedArmyId;
				boolean isYourArmy = commandedArmyId != null && commandedArmyId == army.id;
				g2.setFont(new Font("Serif", Font.BOLD, 20));
				g2.drawString(isYourArmy ? "YOUR ARMY #" + army.id
						: (realm == null ? "Stateless" : realm.name) + " Army #" + army.id, x + 20, y + 78);
				g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
				g2.drawString((isYourArmy ? "You command. " : "")
						+ "Commander: " + personName(commander) + " | Strength " + strength, x + 20, y + 103);
				g2.drawString("Order " + army.order + " | State " + army.state + " | Regiments " + army.regimentIds.size(), x + 20, y + 122);
				g2.drawString("Morale " + Math.round(army.morale) + "% | Fatigue " + Math.round(army.fatigue)
						+ "% | Days unfed " + army.daysWithoutFood, x + 20, y + 141);
				g2.drawString("Supplies: grain " + army.supplies.getQuantity(GoodType.GRAIN)
						+ " | vegetables " + army.supplies.getQuantity(GoodType.VEGETABLES), x + 20, y + 160);
				g2.drawString("Destination: " + (army.destinationSettlementId == null ? "none"
						: settlementName(campaignSnapshot, army.destinationSettlementId))
						+ (army.routeRoadIds.isEmpty() ? "" : "   |   road leg " + (army.routeIndex + 1)
								+ " of " + army.routeRoadIds.size())
						+ "   |   at " + settlementName(campaignSnapshot, army.currentSettlementId), x + 20, y + 179);
				if (isYourArmy) {
					if (campaignSnapshot.player != null) {
						g2.setColor(campaignSnapshot.player.canFight()
								? new Color(170, 230, 175) : new Color(255, 150, 130));
						g2.drawString("Your health " + campaignSnapshot.player.healthPercent + "%"
								+ (campaignSnapshot.player.wounded ? " (wounded)" : "")
								+ " - any health above 0% can fight", x + 20, y + 197);
						g2.setColor(Color.WHITE);
					}
					addContextButton(g2, "SUPPLY_ARMY", "Feed 5 grain", x + 173, y + 210, 132, 44);
					addContextButton(g2, "VIEW_ARMY_PEOPLE", "View soldiers", x + 326, y + 210, 132, 44);
					CampaignSnapshot.SettlementView orderTarget = selectedCampaignSettlementId == null
							? null : campaignSnapshot.findSettlement(selectedCampaignSettlementId);
					g2.setColor(new Color(255, 215, 110));
					g2.setFont(new Font("SansSerif", Font.BOLD, 11));
					g2.drawString("YOUR ARMY  -  orders go to: "
							+ (orderTarget == null ? "nothing selected (click a settlement first)"
									: orderTarget.name), x + 20, y + 266);
					g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
					addContextButton(g2, "ORDER_MOVE", "Move", x + 20, y + 274, 104, 32);
					addContextButton(g2, "ORDER_DEFEND", "Defend", x + 132, y + 274, 104, 32);
					CampaignSnapshot.SiegeView targetSiege = orderTarget == null ? null
							: campaignSnapshot.findActiveSiegeAt(orderTarget.id);
					boolean occupiedByYourRealm = orderTarget != null
							&& orderTarget.occupyingRealmId != null
							&& orderTarget.occupyingRealmId.longValue() == army.realmId;
					boolean targetSiegeJoinable = targetSiege != null
							&& (targetSiege.playerArmyCanJoin || targetSiege.playerArmyJoined);
					boolean targetHeldByYourRealm = orderTarget != null
							&& orderTarget.realmId != null
							&& orderTarget.realmId.longValue() == army.realmId;
					String siegeOrderLabel = occupiedByYourRealm ? "Occupied"
							: targetSiege != null && targetHeldByYourRealm ? "Besieged"
							: !targetSiegeJoinable ? "Start siege"
							: targetSiege.playerArmyAtSiege ? "In siege"
							: targetSiege.playerArmyJoined ? "Joining siege" : "Join siege";
					addContextButton(g2, "ORDER_BESIEGE", siegeOrderLabel, x + 244, y + 274, 104, 32);
					addContextButton(g2, "ORDER_RAID", "Raid", x + 356, y + 274, 100, 32);
					addContextButton(g2, "ORDER_HOME", "March home", x + 20, y + 314, 122, 32);
					int buttonX = 150;
					CampaignSnapshot.SiegeView siegeHere = campaignSnapshot.findActiveSiegeAt(
							army.currentSettlementId);
					if (siegeHere != null && siegeHere.playerArmyAtSiege) {
						addContextButton(g2, "ASSAULT_SIEGE", "Storm the walls", x + buttonX, y + 314, 142, 32);
						buttonX += 150;
					} else if (siegeHere != null
							&& (siegeHere.playerArmyCanJoin || siegeHere.playerArmyJoined)) {
						addContextButton(g2, "JOIN_SIEGE_HERE",
								siegeHere.playerArmyJoined ? "Siege joined" : "Join siege here",
								x + buttonX, y + 314, 142, 32);
						buttonX += 150;
					}
					addContextButton(g2, "RELEASE_COMMAND",
							army.playerFunded ? "Pay off company" : "Give up command", x + buttonX, y + 314, 148, 32);
				} else {
					addContextButton(g2, "SPEAK_COMMANDER", "Speak to commander", x + 20, y + 210, 132, 44);
					addContextButton(g2, "SUPPLY_ARMY", "Donate 5 grain", x + 173, y + 210, 132, 44);
					addContextButton(g2, "VIEW_ARMY_PEOPLE", "View soldiers", x + 326, y + 210, 132, 44);
					boolean youRule = campaignSnapshot.player != null && campaignSnapshot.player.canManageKingdom;
					int buttonX = x + 20;
					if (youRule) {
						addContextButton(g2, "TAKE_COMMAND", "Take command", buttonX, y + 274, 142, 40);
						buttonX += 152;
					}
					addContextButton(g2, "ENGAGE_ARMY", "Attack this army", buttonX, y + 274, 152, 40);
					g2.setColor(Color.LIGHT_GRAY);
					g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
					g2.drawString(youRule
							? "Attacking needs your own army within 12 of this one."
							: "Raise your own company at a settlement, or rule a realm to take command of its armies.",
							x + 20, y + 332);
					g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
				}
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
		if ("BATTLE_PURSUE".equals(clicked)) { chooseCampaignBattlePursuit(); return; }
		if ("BATTLE_LET_GO".equals(clicked)) { chooseCampaignBattleLetGo(); return; }
		if ("BATTLE_DEMAND_SURRENDER".equals(clicked)) { chooseCampaignBattleDemandSurrender(); return; }
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
		if ("ENCOUNTER_LEAVE".equals(clicked)) {
			leaveCampaignEncounter();
			return;
		}
		if ("ENCOUNTER_ATTACK".equals(clicked) || "ENCOUNTER_SEND".equals(clicked)) {
			Long targetId = campaignEncounterArmyId != null
					? campaignEncounterArmyId : selectedCampaignActorId;
			if (targetId == null || campaignSnapshot.player == null
					|| campaignSnapshot.player.commandedArmyId == null) {
				campaignContextMessage = "You command no army. Raise a company at a settlement first.";
				return;
			}
			if ("ENCOUNTER_ATTACK".equals(clicked)) {
				world.command.CommandResult cleared = campaignSession.canEngageArmy(targetId);
				if (!cleared.accepted) {
					campaignContextMessage = cleared.message;
					return;
				}
				long attackerId = campaignSnapshot.player.commandedArmyId;
				campaignContextMenu = CampaignContextMenu.NONE;
				campaignEncounterArmyId = null;
				campaignFollowArmyId = null;
				campaignFollowAnchorSettlementId = null;
				launchCampaignBattle(attackerId, targetId);
				if (activeCampaignBattle == null && currentLayer == GameLayer.WORLD_MAP) {
					// Launch refused (for example, the army vanished): do not reopen forever.
					campaignEncounterIgnoredArmyId = targetId;
					campaignEncounterGraceUntilMinute = campaignSnapshot.worldMinute + 60L;
				}
				return;
			}
			world.command.CommandResult result = campaignSession.autoResolveEngagement(targetId);
			campaignSnapshot = campaignSession.getSnapshot();
			if (result != null && result.accepted) {
				campaignContextMenu = CampaignContextMenu.NONE;
				campaignEncounterArmyId = null;
				campaignFollowArmyId = null;
				campaignFollowAnchorSettlementId = null;
				showCampaignNotice(result.message);
			} else {
				campaignContextMessage = result == null ? "The armies could not engage." : result.message;
			}
			return;
		}
		GoodType good = GoodType.values()[Math.floorMod(marketGoodIndex, GoodType.values().length)];
		world.command.CommandResult result = null;
		if ("SUPPLY_ARMY".equals(clicked) && selectedCampaignActorId != null) {
			result = campaignSession.supplyArmy(selectedCampaignActorId, 5);
		}
		if ("RAISE_INFANTRY".equals(clicked)) {
			result = campaignSession.raiseCompany(10, world.military.Regiment.RegimentType.LEVY_INFANTRY);
		}
		if ("RAISE_ARCHERS".equals(clicked)) {
			result = campaignSession.raiseCompany(5, world.military.Regiment.RegimentType.ARCHER);
		}
		if ("RAISE_SPEARS".equals(clicked)) {
			result = campaignSession.raiseCompany(5, world.military.Regiment.RegimentType.SPEAR);
		}
		if ("TAKE_COMMAND".equals(clicked) && selectedCampaignActorId != null) {
			result = campaignSession.takeCommandOfArmy(selectedCampaignActorId);
		}
		if ("RELEASE_COMMAND".equals(clicked)) {
			result = campaignSession.relinquishCommand();
		}
		if ("JOIN_SIEGE_HERE".equals(clicked) && selectedCampaignActorId != null) {
			world.Army yourArmy = campaignSession.getWorld().armies.get(selectedCampaignActorId);
			CampaignSnapshot.SiegeView siege = yourArmy == null ? null
					: campaignSnapshot.findActiveSiegeAt(yourArmy.currentSettlementId);
			result = siege == null
					? world.command.CommandResult.rejected("INVALID_SIEGE", "There is no active siege here")
					: campaignSession.joinSiege(siege.id);
		}
		if ("ASSAULT_SIEGE".equals(clicked) && selectedCampaignActorId != null) {
			world.Army yourArmy = campaignSession.getWorld().armies.get(selectedCampaignActorId);
			CampaignSnapshot.SiegeView siege = yourArmy == null ? null
					: campaignSnapshot.findActiveSiegeAt(yourArmy.currentSettlementId);
			result = siege == null
					? world.command.CommandResult.rejected("INVALID_SIEGE", "There is no active siege here")
					: campaignSession.assaultSiege(siege.id);
		}
		if (clicked.startsWith("ORDER_") && selectedCampaignSettlementId != null) {
			world.Army.ArmyOrder order = switch (clicked) {
				case "ORDER_DEFEND" -> world.Army.ArmyOrder.DEFEND;
				case "ORDER_BESIEGE" -> world.Army.ArmyOrder.BESIEGE;
				case "ORDER_RAID" -> world.Army.ArmyOrder.RAID;
				case "ORDER_HOME" -> world.Army.ArmyOrder.RETURN_HOME;
				default -> world.Army.ArmyOrder.MOVE;
			};
			result = campaignSession.issueArmyOrder(selectedCampaignSettlementId, order);
		}
		if (clicked.startsWith("ORDER_") && selectedCampaignSettlementId == null) {
			result = world.command.CommandResult.rejected("NO_DESTINATION",
					"Click a settlement on the map first, then choose the order");
		}
		if ("ENGAGE_ARMY".equals(clicked) && selectedCampaignActorId != null) {
			world.command.CommandResult cleared = campaignSession.canEngageArmy(selectedCampaignActorId);
			if (cleared.accepted) {
				campaignContextMenu = CampaignContextMenu.NONE;
				launchCampaignBattle(campaignSnapshot.player.commandedArmyId, selectedCampaignActorId);
				return;
			}
			result = cleared;
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

		private void handleCampaignMapClick(java.awt.Point point, int button) {
			boolean rightClick = button == MouseEvent.BUTTON3;
			if (campaignSession != null) {
				for (java.util.Map.Entry<String, Rectangle> entry : campaignHudHitboxes.entrySet()) {
					if (!entry.getValue().contains(point)) continue;
					switch (entry.getKey()) {
						case "PAUSE" -> campaignSession.toggleWorldPaused();
						case "SPEED_1" -> campaignSession.setSpeed(1);
						case "SPEED_60" -> campaignSession.setSpeed(60);
						case "SPEED_1440" -> campaignSession.setSpeed(1440);
						case "CENTER" -> centerCampaignCameraOnPlayer();
						case "STOP_TRAVEL" -> {
							campaignFollowArmyId = null;
							campaignFollowCaravanId = null;
							campaignFollowAnchorSettlementId = null;
							world.command.CommandResult result = campaignSession.cancelTravel();
							if (result != null && !result.accepted && result.message != null) {
								showCampaignNotice(result.message);
							} else {
								showCampaignNotice("You turn back along the road.");
							}
						}
						default -> { }
					}
					campaignSnapshot = campaignSession.getSnapshot();
					repaint();
					return;
				}
			}
			if (campaignSession != null && !kingdomManagementHitboxes.isEmpty()) {
				for (java.util.Map.Entry<String, Rectangle> entry : kingdomManagementHitboxes.entrySet()) {
					if (!entry.getValue().contains(point)) continue;
					world.command.CommandResult result;
					if ("SEIZE_THRONE".equals(entry.getKey())) {
						Long realmId = campaignSnapshot.player == null
							? null : campaignSnapshot.player.affiliatedRealmId;
						result = realmId == null
							? world.command.CommandResult.rejected("NO_REALM", "You serve no kingdom")
							: campaignSession.seizeThrone(realmId);
					} else if ("PAY_RANSOM".equals(entry.getKey())) {
						result = campaignSession.payRansom();
					} else if (entry.getKey().startsWith("DECLARE_WAR:")) {
						result = campaignSession.declareWarAsRuler(
								Long.parseLong(entry.getKey().substring("DECLARE_WAR:".length())),
								world.War.WarGoal.CONQUER_PROVINCE, null, null);
					} else if (entry.getKey().startsWith("CLAIM_HOLDING:")) {
						result = campaignSession.claimHolding(
								Long.parseLong(entry.getKey().substring("CLAIM_HOLDING:".length())));
					} else if (entry.getKey().startsWith("JOIN_SIEGE:")) {
						result = campaignSession.joinSiege(
								Long.parseLong(entry.getKey().substring("JOIN_SIEGE:".length())));
					} else if (entry.getKey().startsWith("STORM_SIEGE:")) {
						result = campaignSession.assaultSiege(
								Long.parseLong(entry.getKey().substring("STORM_SIEGE:".length())));
					} else {
						String[] command = entry.getKey().split(":");
						result = campaignSession.adjustKingdomLaw(
								Government.LawType.valueOf(command[0]), Integer.parseInt(command[1]));
					}
					campaignSnapshot = campaignSession.getSnapshot();
					if (result != null && result.message != null && !result.message.isBlank()) {
						showCampaignNotice(result.message);
					}
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
			if (campaignPlayerHitbox != null && campaignPlayerHitbox.contains(point)) {
				if (rightClick) {
					activeCampaignTab = CampaignTab.PARTY;
				} else {
					centerCampaignCameraOnPlayer();
				}
				repaint();
				return;
			}
			for (java.util.Map.Entry<Long, Rectangle> entry : campaignArmyHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					selectedCampaignActorId = entry.getKey();
					CampaignSnapshot.ArmyView army = campaignSnapshot == null
							? null : campaignSnapshot.findArmy(entry.getKey());
					boolean yours = army != null && (army.playerCommanded
							|| (campaignSnapshot.player != null
									&& campaignSnapshot.player.commandedArmyId != null
									&& campaignSnapshot.player.commandedArmyId.longValue() == army.id));
					if (rightClick || yours || army == null || campaignSnapshot.player == null) {
						campaignContextMenu = CampaignContextMenu.ARMY;
						campaignContextMessage = yours
								? "Your army. Orders use the right-click-selected settlement."
								: "Inspect this army and its commander";
						repaint();
						return;
					}
					// Bannerlord behaviour: left-click a party to ride to it. The
					// per-frame updater keeps retargeting the moving army's anchor.
					campaignFollowArmyId = army.id;
					campaignFollowCaravanId = null;
					campaignFollowAnchorSettlementId = null;
					Long anchor = army.destinationSettlementId != null
							? army.destinationSettlementId : army.currentSettlementId;
					if (anchor != null && anchor.longValue() != campaignSnapshot.player.settlementId) {
						campaignFollowAnchorSettlementId = anchor;
						world.command.CommandResult result = campaignSession.beginTravelTo(anchor);
						campaignSnapshot = campaignSession.getSnapshot();
						if (result != null && !result.accepted) {
							campaignFollowArmyId = null;
							campaignFollowAnchorSettlementId = null;
							showCampaignNotice(result.message);
						} else {
							showCampaignNotice("Riding to intercept "
									+ campaignRealmName(campaignSnapshot, army.realmId)
									+ " army under " + army.commanderName + ".");
						}
					} else if (!tryOpenCampaignEncounter(campaignSnapshot)) {
						showCampaignNotice("That army is here. Right-click it to inspect it; "
								+ "an encounter opens when your own army is in reach.");
					}
					repaint();
					return;
				}
			}
			for (java.util.Map.Entry<Long, Rectangle> entry : campaignCaravanHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					selectedCampaignActorId = entry.getKey();
					CampaignSnapshot.CaravanView caravan = campaignSnapshot == null
							? null : campaignSnapshot.findCaravan(entry.getKey());
					if (rightClick || caravan == null || campaignSnapshot.player == null) {
						campaignContextMenu = CampaignContextMenu.CARAVAN;
						campaignContextMessage = "Inspect this merchant caravan";
						repaint();
						return;
					}
					campaignFollowCaravanId = caravan.id;
					campaignFollowArmyId = null;
					campaignFollowAnchorSettlementId = null;
					Long anchor = caravan.destinationSettlementId != null
							? caravan.destinationSettlementId : caravan.currentSettlementId;
					if (anchor != null && anchor.longValue() != campaignSnapshot.player.settlementId) {
						campaignFollowAnchorSettlementId = anchor;
						world.command.CommandResult result = campaignSession.beginTravelTo(anchor);
						campaignSnapshot = campaignSession.getSnapshot();
						if (result != null && !result.accepted) {
							campaignFollowCaravanId = null;
							campaignFollowAnchorSettlementId = null;
							showCampaignNotice(result.message);
						} else {
							showCampaignNotice("Following the caravan of " + caravan.leaderName + ".");
						}
					} else {
						showCampaignNotice("That caravan is here. Right-click it to inspect it.");
					}
					repaint();
					return;
				}
			}
			for (java.util.Map.Entry<Long, Rectangle> entry : campaignSettlementHitboxes.entrySet()) {
				if (entry.getValue().contains(point)) {
					selectedCampaignSettlementId = entry.getKey();
					boolean playerHere = campaignSnapshot != null && campaignSnapshot.player != null
							&& !campaignSnapshot.player.traveling
							&& campaignSnapshot.player.settlementId == entry.getKey().longValue();
					if (playerHere) {
						campaignContextMenu = CampaignContextMenu.SETTLEMENT;
						campaignContextMessage = "You are currently at this settlement";
					} else if (!rightClick && campaignSession != null) {
						// Right-click remains "select only", preserving its use as an
						// army-order destination; left-click rides there on the roads.
						campaignFollowArmyId = null;
						campaignFollowCaravanId = null;
						campaignFollowAnchorSettlementId = null;
						world.command.CommandResult result =
								campaignSession.beginTravelTo(entry.getKey());
						campaignSnapshot = campaignSession.getSnapshot();
						if (result != null && result.message != null && !result.message.isBlank()) {
							showCampaignNotice(result.message);
						}
					}
					repaint();
					return;
				}
			}
		}

		/** Shows a temporary banner on the campaign world map. */
		private void showCampaignNotice(String message) {
			campaignNotice = message;
			campaignNoticeTicks = message == null || message.isBlank() ? 0 : CAMPAIGN_NOTICE_TICKS;
		}

		/** Word-wraps campaign text to a pixel width. Shared by notices, tooltips, and event feed. */
		private java.util.List<String> wrapCampaignText(Graphics2D g2, String text, int maxWidth, int maxLines) {
			java.util.List<String> lines = new java.util.ArrayList<>();
			if (text == null || text.isEmpty()) return lines;
			java.awt.FontMetrics fm = g2.getFontMetrics();
			StringBuilder line = new StringBuilder();
			for (String word : text.split("\\s+")) {
				if (word.isEmpty()) continue;
				if (line.length() == 0) {
					line.append(word);
					continue;
				}
				if (fm.stringWidth(line + " " + word) <= maxWidth) {
					line.append(' ').append(word);
				} else {
					lines.add(line.toString());
					if (lines.size() >= maxLines) return lines;
					line.setLength(0);
					line.append(word);
				}
			}
			if (lines.size() < maxLines && line.length() > 0) lines.add(line.toString());
			return lines;
		}

		private void drawCampaignNotice(Graphics2D g2) {
			if (campaignNotice == null || campaignNoticeTicks <= 0) return;
			campaignNoticeTicks--;
			g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
			int boxW = 500;
			java.util.List<String> lines = wrapCampaignText(g2, campaignNotice, boxW - 24, 4);
			if (lines.isEmpty()) return;
			int boxH = 12 + lines.size() * 16;
			int boxX = (screenWidth - boxW) / 2;
			int boxY = 84;
			int alpha = Math.min(235, campaignNoticeTicks * 6);
			g2.setColor(new Color(12, 18, 25, alpha));
			g2.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
			g2.setColor(new Color(215, 140, 90, alpha));
			g2.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);
			g2.setColor(new Color(255, 236, 210, Math.min(255, alpha + 20)));
			int y = boxY + 18;
			for (String line : lines) {
				g2.drawString(line, boxX + 12, y);
				y += 16;
			}
		}

		/**
		 * Renders one world event as a readable line. Returns null for internal bookkeeping
		 * events that should never reach the player.
		 */
		private String readableEvent(CampaignSnapshot.EventView event) {
			String text = event.detail != null && !event.detail.isBlank()
					? event.detail : eventFallbackText(event.type);
			if (text == null) return null;
			return "Day " + (event.worldMinute / WorldConfig.MINUTES_PER_DAY + 1) + ": " + text;
		}

		/** Fallback wording for event types whose system does not publish a detail yet. */
		private String eventFallbackText(String type) {
			switch (type) {
				case "WAR_DECLARED": return "A war is declared";
				case "SIEGE_STARTED": return "A siege begins";
				case "SIEGE_JOINED": return "Another army joins a siege";
				case "SIEGE_COMMAND_CHANGED": return "A supporting army takes over a siege";
				case "PLAYER_SIEGE_STARTED": return "Your army lays siege";
				case "SETTLEMENT_OCCUPIED": return "A settlement is occupied";
				case "SIEGE_CIVILIAN_DIED": return "A civilian starves in a besieged settlement";
				case "PEACE_ENFORCED": return "Peace is enforced";
				case "TREATY_SIGNED": return "A treaty is signed";
				case "TREATY_VIOLATED": return "A treaty is violated";
				case "TREATY_EXPIRED": return "A treaty expires";
				case "RULER_SUCCEEDED": return "A new ruler takes the throne";
				case "BATTLE_RECONCILED": return "A battle is resolved";
				case "BATTLE_CONTEXT_CREATED": return "Armies meet in battle";
				case "BATTLE_PERSON_DIED": return "A soldier dies in battle";
				case "ARMY_MUSTERED": return "An army is mustered";
				case "ARMY_DEPARTED": return "An army marches out";
				case "ARMY_ARRIVED": return "An army reaches its destination";
				case "ARMY_DETECTED": return "Scouts spot a foreign army";
				case "ARMY_DEMOBILIZED": return "An army is disbanded";
				case "SOLDIER_DESERTED": return "A soldier deserts";
				case "PERSON_DIED": return "Someone dies";
				case "BIRTH": return "A child is born";
				case "HOUSEHOLD_MIGRATED": return "A household migrates";
				case "CONTRACT_ACCEPTED": return "A contract is accepted";
				case "CONTRACT_COMPLETED": return "A contract is completed";
				case "CONTRACT_FAILED": return "A contract fails";
				case "CRIME_DISCOVERED": return "A crime is discovered";
				case "SCHEME_EXPOSED": return "A covert scheme is exposed";
				case "PLAYER_CAPTURED": return "You are taken prisoner";
				case "PLAYER_RELEASED": return "You are released from captivity";
				case "COMPANION_KILLED": return "One of your companions is killed";
				case "THRONE_CLAIMED": return "The throne is claimed by right";
				case "THRONE_USURPED": return "The throne is usurped";
				case "CHEAT_RULER_CHANGED": return "A ruler is replaced (cheat menu)";
				case "CHEAT_ARMY_SPAWNED": return "An army appears (cheat menu)";
				case "CHEAT_SETTLEMENT_TRANSFERRED": return "A settlement changes hands (cheat menu)";
				case "SIEGE_ASSAULT": return "The walls are stormed";
				case "HOLDING_CLAIMED": return "Conquered land is claimed as a holding";
				case "HOLDING_LOST": return "One of your holdings is no longer yours";
				case "PLAYER_TOOK_COMMAND": return "You take command of an army";
				// Internal bookkeeping: never shown to the player.
				case "AUTOSAVE_SAFE_POINT": return null;
				default: return type.replace('_', ' ').toLowerCase();
			}
		}

		/** Colour coding so the eye finds wars, sieges and personal news in the feed. */
		private Color eventColor(String type) {
			switch (type) {
				case "WAR_DECLARED":
				case "SIEGE_STARTED":
				case "SIEGE_JOINED":
				case "SIEGE_COMMAND_CHANGED":
				case "PLAYER_SIEGE_STARTED":
				case "SETTLEMENT_OCCUPIED":
				case "BATTLE_RECONCILED":
				case "BATTLE_CONTEXT_CREATED":
				case "SIEGE_ASSAULT":
					return new Color(255, 150, 110);
				case "SIEGE_CIVILIAN_DIED":
				case "PERSON_DIED":
				case "BATTLE_PERSON_DIED":
				case "SOLDIER_DESERTED":
				case "COMPANION_KILLED":
					return new Color(190, 190, 200);
				case "PLAYER_CAPTURED":
				case "PLAYER_RELEASED":
				case "THRONE_CLAIMED":
				case "THRONE_USURPED":
				case "RULER_SUCCEEDED":
				case "HOLDING_CLAIMED":
				case "HOLDING_LOST":
				case "PLAYER_TOOK_COMMAND":
					return new Color(255, 214, 120);
				case "PEACE_ENFORCED":
				case "TREATY_SIGNED":
					return new Color(160, 220, 170);
				default:
					return Color.LIGHT_GRAY;
			}
		}

		/** Major world events the player should be told about without opening a tab. */
		private boolean isToastWorthy(String type) {
			switch (type) {
				case "WAR_DECLARED":
				case "SIEGE_STARTED":
				case "SIEGE_JOINED":
				case "PLAYER_SIEGE_STARTED":
				case "SETTLEMENT_OCCUPIED":
				case "PEACE_ENFORCED":
				case "TREATY_SIGNED":
				case "TREATY_VIOLATED":
				case "RULER_SUCCEEDED":
				case "BATTLE_RECONCILED":
				case "PLAYER_CAPTURED":
				case "PLAYER_RELEASED":
				case "COMPANION_KILLED":
				case "THRONE_CLAIMED":
				case "THRONE_USURPED":
				case "SIEGE_CIVILIAN_DIED":
				case "SIEGE_ASSAULT":
				case "HOLDING_CLAIMED":
				case "HOLDING_LOST":
					return true;
				default:
					return false;
			}
		}

		/** Queues unseen major events as toasts, exactly once each. */
		private void pumpCampaignToasts(CampaignSnapshot snapshot) {
			if (snapshot == null) return;
			long newest = lastCampaignEventId;
			for (CampaignSnapshot.EventView event : snapshot.recentEvents) {
				if (event.eventId > newest) newest = event.eventId;
			}
			if (!campaignToastsPrimed) {
				// First frame after entering or loading a campaign: do not replay history.
				campaignToastsPrimed = true;
				lastCampaignEventId = newest;
				return;
			}
			for (CampaignSnapshot.EventView event : snapshot.recentEvents) {
				if (event.eventId <= lastCampaignEventId) continue;
				lastCampaignEventId = event.eventId;
				if (!isToastWorthy(event.type)) continue;
				String text = readableEvent(event);
				if (text == null) continue;
				campaignToasts.add(text);
				while (campaignToasts.size() > 3) campaignToasts.remove(0);
				campaignToastTicks = CAMPAIGN_TOAST_TICKS;
			}
		}

		private void drawCampaignToasts(Graphics2D g2) {
			if (campaignToasts.isEmpty() || campaignToastTicks <= 0) return;
			campaignToastTicks--;
			if (campaignToastTicks == 0) {
				campaignToasts.clear();
				return;
			}
			int alpha = Math.min(225, campaignToastTicks * 9);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
			int boxY = 232;
			for (String toast : campaignToasts) {
				java.util.List<String> lines = wrapCampaignText(g2, toast, 272, 2);
				int h = 8 + lines.size() * 14;
				g2.setColor(new Color(14, 20, 27, alpha));
				g2.fillRoundRect(28, boxY, 296, h, 8, 8);
				g2.setColor(new Color(226, 178, 76, alpha));
				g2.drawRoundRect(28, boxY, 296, h, 8, 8);
				g2.setColor(new Color(255, 240, 205, Math.min(255, alpha + 25)));
				int textY = boxY + 15;
				for (String line : lines) {
					g2.drawString(line, 38, textY);
					textY += 14;
				}
				boxY += h + 6;
			}
		}

		/** Symbol key for the strategic map, drawn inside the map frame. */
		private void drawCampaignMapLegend(Graphics2D g2, int mapX, int mapY, int mapW, int mapH) {
			int boxW = 268;
			int boxH = 62;
			int x = mapX + mapW - boxW - 8;
			int y = mapY + mapH - boxH - 8;
			g2.setColor(new Color(9, 16, 22, 210));
			g2.fillRoundRect(x, y, boxW, boxH, 8, 8);
			g2.setColor(new Color(120, 140, 120));
			g2.drawRoundRect(x, y, boxW, boxH, 8, 8);
			g2.setFont(new Font("SansSerif", Font.BOLD, 9));
			g2.setColor(new Color(222, 204, 155));
			g2.drawString("MAP KEY", x + 8, y + 12);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));

			int row1 = y + 26;
			int row2 = y + 42;
			int row3 = y + 56;
			int col1 = x + 8;
			int col2 = x + 140;

			// Settlement: house/keep with a banner tag in the owner realm's colour.
			g2.setColor(new Color(190, 158, 110));
			g2.fillRect(col1 + 1, row1 - 2, 8, 6);
			g2.setColor(colorForRealm(1L));
			g2.fillPolygon(new int[] {col1, col1 + 10, col1 + 5},
					new int[] {row1 - 2, row1 - 2, row1 - 8}, 3);
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Settlement (banner = owner)", col1 + 14, row1 + 2);

			// Army: token ringed by realm colour, number is troops. Gold = yours.
			g2.setColor(new Color(25, 28, 28));
			g2.fillOval(col1, row2 - 7, 11, 11);
			g2.setColor(colorForRealm(2L));
			g2.setStroke(new BasicStroke(2f));
			g2.drawOval(col1, row2 - 7, 11, 11);
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(Color.WHITE);
			g2.drawLine(col1 + 3, row2 - 4, col1 + 8, row2 + 1);
			g2.drawLine(col1 + 3, row2 + 1, col1 + 8, row2 - 4);
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Army (number = troops)", col1 + 15, row2 + 2);

			// Caravan: yellow diamond.
			g2.setColor(new Color(245, 196, 65));
			g2.fillPolygon(new int[] {col1 + 4, col1 + 9, col1 + 4, col1 - 1},
					new int[] {row3 - 7, row3 - 2, row3 + 3, row3 - 2}, 4);
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Merchant caravan", col1 + 13, row3 + 2);

			// You are here: gold-rimmed portrait token (number = party size).
			g2.setColor(new Color(30, 88, 98));
			g2.fillOval(col2, row1 - 7, 12, 12);
			g2.setColor(new Color(255, 215, 90));
			g2.setStroke(new BasicStroke(2f));
			g2.drawOval(col2, row1 - 7, 12, 12);
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(new Color(238, 205, 165));
			g2.fillOval(col2 + 4, row1 - 4, 4, 4);
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Your party", col2 + 17, row1 + 2);

			// Contract destination: gold ring.
			g2.setColor(new Color(255, 190, 45));
			g2.setStroke(new BasicStroke(2f));
			g2.drawOval(col2, row2 - 7, 11, 11);
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Contract destination", col2 + 16, row2 + 2);

			// Occupation + siege.
			g2.setColor(new Color(255, 92, 62));
			g2.setStroke(new BasicStroke(2f));
			g2.drawLine(col2, row3 - 6, col2 + 10, row3 + 4);
			g2.drawLine(col2, row3 + 4, col2 + 10, row3 - 6);
			g2.setStroke(new BasicStroke(1f));
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Under siege", col2 + 16, row3 + 2);
		}

		/** Full-screen campaign reference: every control plus how the simulation fits together. */
		private void drawCampaignHelp(Graphics2D g2) {
			if (!campaignHelpOpen) return;
			g2.setColor(new Color(0, 0, 0, 225));
			g2.fillRect(0, 0, screenWidth, screenHeight);
			int x = 26, y = 22, w = screenWidth - 52, h = screenHeight - 44;
			g2.setColor(new Color(20, 27, 35));
			g2.fillRoundRect(x, y, w, h, 14, 14);
			g2.setColor(new Color(230, 178, 65));
			g2.drawRoundRect(x, y, w, h, 14, 14);
			g2.setFont(new Font("Serif", Font.BOLD, 21));
			g2.setColor(new Color(235, 205, 130));
			g2.drawString("CAMPAIGN - CONTROLS AND HOW THE WORLD WORKS", x + 18, y + 28);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));

			String[] left = {
				"WORLD MAP",
				"  LEFT-click settlement  ride there on the roads",
				"  LEFT-click army/caravan ride to intercept/follow",
				"  RIGHT-click anything   inspect / select target",
				"  click your own settlement       full menu",
				"  drag map   pan        mouse wheel  zoom",
				"  Home       centre on your party",
				"  X          turn back on the current road",
				"  TAB        enter/leave the local settlement",
				"  T          ride to the selected settlement",
				"  B / V      buy 5 grain / 5 vegetables",
				"  N / M      sell 5 grain / 5 vegetables",
				"  C          accept the first contract offered",
				"  K          attack the selected enemy army with yours",
				"  Health > 0 you may attack or siege even while wounded",
				"  R          (in battle) withdraw / call off pursuit",
				"  1/2/3      after a rout: pursue, mercy, demand surrender",
				"  1 / 2 / 3  speed: 1x, 1 hour/s, 1 day/s",
				"  0          pause or resume the world",
				"  H          this help      F1  cheat menu",
				"  F5 / F9    save / load (partial beta save)",
				"  F3         debug overlay  F6  advance a day",
				"  ESC        close the open menu",
				"",
				"LOCAL SETTLEMENT",
				"  move with WASD or the arrows",
				"  F          use the Market, Notice Board or Inn",
				"  TAB        back to the world map",
				"",
				"TABS (bottom bar)",
				"  OVERVIEW   world status and news",
				"  INVENTORY  your coins and cargo",
				"  PARTY      companions you have recruited",
				"  CONTRACTS  work you have accepted",
				"  REALM      your kingdom, role and laws",
				"  WAR        every war and siege, with progress",
				"  FIEFS      titles you hold and every crown",
				"  WORLD      the world encyclopedia",
				"  CRIME      recent incidents",
			};
			String[] right = {
				"HOW THE WORLD WORKS",
				"  Every person is simulated: they work, earn, eat, marry,",
				"  age, migrate and die. Markets set real prices.",
				"  Realms raise armies, pay and feed them, march them along",
				"  roads, and fight over claims, provinces and grievances.",
				"  A siege eats a settlement's grain, breaks defender",
				"  resolve, starves civilians and ends in occupation at",
				"  100% breach. Occupation is not ownership: legal title",
				"  changes only through peace terms or a claim.",
				"",
				"YOUR ARMY",
				"  Right-click a settlement you stand in and raise a",
				"  company: 35c and one weapon per volunteer, your purse.",
				"  Right-click your army (gold ring) for orders: move,",
				"  defend, start/join a siege, raid, march home, storm.",
				"  Besieging needs an active war against the settlement's",
				"  realm. On the WAR tab, Join an allied siege; Storm it",
				"  after your army arrives. Extra armies strengthen it.",
				"  K attacks a selected enemy army with yours, as a battle",
				"  you fight yourself. Kill or rout every standing enemy,",
				"  or break them by losses and commander kills. If you fall,",
				"  your remaining troops can still hold the field.",
				"  Sieges and victories earn war contribution, which is",
				"  what lets you claim land. Pursuit pays salvage; mercy",
				"  and prisoners build reputation and conquest credit.",
				"",
				"YOUR PATH",
				"  1. Trade goods and carry contracts to earn coin.",
				"  2. Recruit companions (talk, gift food, invite).",
				"  3. Supply a realm's army with grain for reputation.",
				"  4. Pledge to that realm as a mercenary or a lord.",
				"  5. As a lord, press a claim to the throne when you have",
				"     a legal claim, or when the realm is in crisis and",
				"     trusts you (stability under 25, 25+ reputation).",
				"  6. Win a siege or battle yourself, then claim the",
				"     occupied land as your holding on the FIEFS tab. It",
				"     pays you daily - and its old owner gets a claim.",
				"  7. As ruler, change the laws of the realm and declare",
				"     wars from the WAR tab.",
				"",
				"  Losing a battle costs you: wounds, coin, cargo, capture",
				"  for ransom, or a rout that destroys your army and can",
				"  kill companions. Captivity blocks travel, trade,",
				"  contracts and recruiting until you pay or serve the term.",
			};
			int colY = y + 48;
			int leftX = x + 18;
			int rightX = x + 300;
			for (String line : left) {
				g2.setColor(line.isEmpty() ? Color.WHITE
						: Character.isSpaceChar(line.charAt(0)) || line.startsWith("  ")
								? new Color(215, 220, 225) : new Color(245, 196, 65));
				g2.drawString(line, leftX, colY);
				colY += 13;
			}
			colY = y + 48;
			for (String line : right) {
				g2.setColor(line.isEmpty() ? Color.WHITE
						: line.startsWith("  ") ? new Color(215, 220, 225) : new Color(245, 196, 65));
				g2.drawString(line, rightX, colY);
				colY += 13;
			}
			g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
			g2.setColor(Color.LIGHT_GRAY);
			g2.drawString("Press H or ESC, or click anywhere, to close.", x + 18, y + h - 12);
		}

		/**
		 * Full-screen Bannerlord-style strategic map. Settlements are fixed places
		 * with banner nameplates; every simulated army, caravan, and the player's
		 * own party is a moving token on the road network. Co-located tokens are
		 * fanned out in screen space, so nothing important can hide behind a town.
		 */
		private void drawCampaignWorld(Graphics2D g2) {
			CampaignSnapshot snapshot = campaignSnapshot;
			campaignHudHitboxes.clear();
			campaignPlayerHitbox = null;
			g2.setColor(new Color(35, 62, 70));
			g2.fillRect(0, 0, screenWidth, screenHeight);

			if (snapshot == null) {
				g2.setColor(new Color(222, 204, 155));
				g2.setFont(new Font("Serif", Font.BOLD, 28));
				g2.drawString("Chronicle Conquest - Campaign", 20, 38);
				g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
				g2.setColor(Color.WHITE);
				g2.drawString("Generating campaign world...", 20, 70);
				return;
			}

			double[] rawBounds = getCampaignBounds(snapshot);
			double minX = rawBounds[0] - 110.0;
			double maxX = rawBounds[1] + 110.0;
			double minY = rawBounds[2] - 110.0;
			double maxY = rawBounds[3] + 110.0;
			if (!campaignCameraInitialized) {
				campaignCamera.fit(minX, maxX, minY, maxY, screenWidth, screenHeight, 42);
				campaignCameraInitialized = true;
			}
			campaignCamera.clampTo(minX, maxX, minY, maxY, screenWidth, screenHeight, 70);

			drawCampaignTerrain(g2, snapshot, minX, maxX, minY, maxY);

			// Dirt roads: a broad worn track with a lighter centre line.
			for (CampaignSnapshot.RoadView road : snapshot.roads) {
				int x1 = campaignCamera.worldToScreenX(road.fromX, screenWidth);
				int y1 = campaignCamera.worldToScreenY(road.fromY, screenHeight);
				int x2 = campaignCamera.worldToScreenX(road.toX, screenWidth);
				int y2 = campaignCamera.worldToScreenY(road.toY, screenHeight);
				if ((x1 < -80 && x2 < -80) || (x1 > screenWidth + 80 && x2 > screenWidth + 80)
						|| (y1 < -80 && y2 < -80) || (y1 > screenHeight + 80 && y2 > screenHeight + 80)) continue;
				float width = (float) Math.max(2.0, Math.min(8.0, 5.0 * campaignCamera.zoom));
				g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(road.blocked ? new Color(145, 55, 45, 210) : new Color(112, 92, 64, 190));
				g2.drawLine(x1, y1, x2, y2);
				g2.setStroke(new BasicStroke(Math.max(1f, width * 0.42f),
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(road.danger > 0.5 ? new Color(205, 150, 110, 220)
						: new Color(188, 159, 108, 220));
				g2.drawLine(x1, y1, x2, y2);
			}
			g2.setStroke(new BasicStroke(1f));

			java.util.Map<Long, java.awt.Point> settlementPoints = new java.util.HashMap<>();
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				settlementPoints.put(settlement.id, new java.awt.Point(
						campaignCamera.worldToScreenX(settlement.worldX, screenWidth),
						campaignCamera.worldToScreenY(settlement.worldY, screenHeight)));
			}

			// Count co-located moving tokens first. Their simulated point does not
			// change; only the drawing is fanned out around it.
			java.util.Map<String, Integer> tokenGroupCounts = new java.util.HashMap<>();
			if (snapshot.player != null) {
				tokenGroupCounts.merge(campaignTokenGroupKey(snapshot,
						snapshot.player.worldX, snapshot.player.worldY), 1, Integer::sum);
			}
			for (CampaignSnapshot.ArmyView army : snapshot.armies) {
				tokenGroupCounts.merge(campaignTokenGroupKey(snapshot, army.worldX, army.worldY),
						1, Integer::sum);
			}
			for (CampaignSnapshot.CaravanView caravan : snapshot.caravans) {
				tokenGroupCounts.merge(campaignTokenGroupKey(snapshot, caravan.worldX, caravan.worldY),
						1, Integer::sum);
			}
			java.util.Map<String, Integer> nextTokenSlots = new java.util.HashMap<>();
			java.awt.Point playerPoint = snapshot.player == null ? null
					: campaignTokenPoint(snapshot, snapshot.player.worldX, snapshot.player.worldY,
							tokenGroupCounts, nextTokenSlots);

			java.util.List<Rectangle> occupiedLabels = new java.util.ArrayList<>();
			campaignSettlementHitboxes.clear();
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				java.awt.Point point = settlementPoints.get(settlement.id);
				int x = point.x;
				int y = point.y;
				if (x < -140 || x > screenWidth + 140 || y < -80 || y > screenHeight + 80) continue;
				int radius = settlement.capital ? 13
						: "CITY".equals(settlement.type) ? 12
						: "TOWN".equals(settlement.type) || "FORT".equals(settlement.type) ? 10 : 8;
				if (campaignCamera.zoom < 0.55) radius = Math.max(6, radius - 2);
				campaignSettlementHitboxes.put(settlement.id,
						new Rectangle(x - radius - 5, y - radius - 5, radius * 2 + 10, radius * 2 + 10));

				boolean selected = selectedCampaignSettlementId != null
						&& selectedCampaignSettlementId.longValue() == settlement.id;
				if (selected) {
					g2.setColor(new Color(255, 220, 105));
					g2.setStroke(new BasicStroke(2f));
					g2.drawOval(x - radius - 5, y - radius - 5, radius * 2 + 10, radius * 2 + 10);
					g2.setStroke(new BasicStroke(1f));
				}
				if (isActiveContractDestination(snapshot, settlement.id)) {
					g2.setColor(new Color(255, 190, 45));
					g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
							1f, new float[] {5f, 4f}, 0f));
					g2.drawOval(x - radius - 9, y - radius - 9, radius * 2 + 18, radius * 2 + 18);
					g2.setStroke(new BasicStroke(1f));
				}
				if (snapshot.player != null && snapshot.player.traveling
						&& snapshot.player.travelDestinationSettlementId != null
						&& snapshot.player.travelDestinationSettlementId.longValue() == settlement.id) {
					int pulse = 3 + (int) ((snapshot.worldMinute / 20L) % 3L);
					g2.setColor(new Color(120, 235, 255));
					g2.setStroke(new BasicStroke(2f));
					g2.drawOval(x - radius - 7 - pulse, y - radius - 7 - pulse,
							radius * 2 + 14 + pulse * 2, radius * 2 + 14 + pulse * 2);
					g2.setStroke(new BasicStroke(1f));
				}

				drawCampaignSettlementIcon(g2, settlement, x, y, radius);
				if (settlement.occupyingRealmId != null) {
					g2.setColor(colorForRealm(settlement.occupyingRealmId));
					g2.setStroke(new BasicStroke(2f));
					g2.drawRect(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
					g2.setStroke(new BasicStroke(1f));
				}
				boolean underSiege = snapshot.findActiveSiegeAt(settlement.id) != null;
				if (underSiege) {
					g2.setColor(new Color(255, 75, 50));
					g2.setStroke(new BasicStroke(2f));
					g2.drawLine(x - radius - 4, y - radius - 4, x + radius + 4, y + radius + 4);
					g2.drawLine(x - radius - 4, y + radius + 4, x + radius + 4, y - radius - 4);
					g2.setStroke(new BasicStroke(1f));
				}

				String labelText = settlement.name + (underSiege ? "  SIEGE" : "");
				g2.setFont(new Font("SansSerif", Font.BOLD, 10));
				int labelWidth = g2.getFontMetrics().stringWidth(labelText) + 31;
				Rectangle label = placeCampaignLabel(x, y, labelWidth, 17,
						4, 50, screenWidth - 4, screenHeight - 36, occupiedLabels);
				occupiedLabels.add(label);
				drawCampaignBannerTag(g2, label, x, y, labelText,
						colorForRealm(settlement.realmId), underSiege);
			}

			campaignCaravanHitboxes.clear();
			for (CampaignSnapshot.CaravanView caravan : snapshot.caravans) {
				java.awt.Point point = campaignTokenPoint(snapshot, caravan.worldX, caravan.worldY,
						tokenGroupCounts, nextTokenSlots);
				int x = point.x;
				int y = point.y;
				if (x < -50 || x > screenWidth + 50 || y < -50 || y > screenHeight + 50) continue;
				campaignCaravanHitboxes.put(caravan.id, new Rectangle(x - 13, y - 13, 34, 26));
				g2.setColor(new Color(0, 0, 0, 70));
				g2.fillOval(x - 7, y + 5, 15, 6);
				int[] xs = {x, x + 8, x, x - 8};
				int[] ys = {y - 8, y, y + 8, y};
				g2.setColor(new Color(238, 184, 65));
				g2.fillPolygon(xs, ys, 4);
				g2.setColor(new Color(95, 62, 25));
				g2.drawPolygon(xs, ys, 4);
				g2.setColor(new Color(255, 226, 130));
				g2.fillOval(x - 2, y - 2, 5, 5);
				if (caravan.cargoQuantity > 0) {
					g2.setFont(new Font("SansSerif", Font.BOLD, 9));
					g2.setColor(Color.BLACK);
					g2.drawString(Integer.toString(caravan.cargoQuantity), x + 11, y + 4);
					g2.setColor(Color.WHITE);
					g2.drawString(Integer.toString(caravan.cargoQuantity), x + 10, y + 3);
				}
				boolean labelled = (hoveredCampaignCaravanId != null
						&& hoveredCampaignCaravanId.longValue() == caravan.id)
						|| (campaignFollowCaravanId != null
								&& campaignFollowCaravanId.longValue() == caravan.id);
				if (labelled) {
					String text = caravan.leaderName + "  ·  " + caravan.cargoQuantity;
					g2.setFont(new Font("SansSerif", Font.BOLD, 9));
					int width = g2.getFontMetrics().stringWidth(text) + 14;
					Rectangle label = placeCampaignLabel(x, y, width, 15,
							4, 50, screenWidth - 4, screenHeight - 36, occupiedLabels);
					occupiedLabels.add(label);
					drawCampaignNamePill(g2, label, text, new Color(112, 78, 26), Color.WHITE);
				}
			}

			campaignArmyHitboxes.clear();
			java.util.List<CampaignSnapshot.ArmyView> armiesByPriority =
					new java.util.ArrayList<>(snapshot.armies);
			armiesByPriority.sort((first, second) -> {
				int byCommand = Boolean.compare(second.playerCommanded, first.playerCommanded);
				return byCommand != 0 ? byCommand : Long.compare(first.id, second.id);
			});
			for (CampaignSnapshot.ArmyView army : armiesByPriority) {
				java.awt.Point point = campaignTokenPoint(snapshot, army.worldX, army.worldY,
						tokenGroupCounts, nextTokenSlots);
				int x = point.x;
				int y = point.y;
				if (x < -60 || x > screenWidth + 60 || y < -60 || y > screenHeight + 60) continue;
				boolean yours = army.playerCommanded || (snapshot.player != null
						&& snapshot.player.commandedArmyId != null
						&& snapshot.player.commandedArmyId.longValue() == army.id);
				int radius = yours ? 11 : 9;
				campaignArmyHitboxes.put(army.id, new Rectangle(x - 14, y - 14, 42, 28));
				g2.setColor(new Color(0, 0, 0, 75));
				g2.fillOval(x - radius, y + radius - 4, radius * 2, 7);
				g2.setColor(new Color(25, 28, 28));
				g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);
				g2.setStroke(new BasicStroke(3f));
				g2.setColor(colorForRealm(army.realmId));
				g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
				if (yours) {
					g2.setStroke(new BasicStroke(2f));
					g2.setColor(new Color(255, 215, 90));
					g2.drawOval(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
				} else if (army.hostileToPlayer) {
					g2.setStroke(new BasicStroke(2f));
					g2.setColor(new Color(255, 80, 60));
					g2.drawOval(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
				}
				g2.setStroke(new BasicStroke(2f));
				g2.setColor(Color.WHITE);
				g2.drawLine(x - 4, y - 4, x + 4, y + 4);
				g2.drawLine(x - 4, y + 4, x + 4, y - 4);
				g2.setStroke(new BasicStroke(1f));
				g2.setFont(new Font("SansSerif", Font.BOLD, 10));
				String strength = Integer.toString(army.strength);
				g2.setColor(Color.BLACK);
				g2.drawString(strength, x + radius + 4, y + 5);
				g2.setColor(yours ? new Color(255, 228, 140) : Color.WHITE);
				g2.drawString(strength, x + radius + 3, y + 4);

				boolean labelled = yours
						|| (hoveredCampaignArmyId != null && hoveredCampaignArmyId.longValue() == army.id)
						|| (selectedCampaignActorId != null && selectedCampaignActorId.longValue() == army.id)
						|| (campaignFollowArmyId != null && campaignFollowArmyId.longValue() == army.id);
				if (labelled) {
					String text = (yours ? "YOUR ARMY"
							: campaignRealmName(snapshot, army.realmId) + " Army") + "  ·  " + army.strength;
					g2.setFont(new Font("SansSerif", Font.BOLD, 9));
					int width = g2.getFontMetrics().stringWidth(text) + 14;
					Rectangle label = placeCampaignLabel(x, y, width, 15,
							4, 50, screenWidth - 4, screenHeight - 36, occupiedLabels);
					occupiedLabels.add(label);
					drawCampaignNamePill(g2, label, text, colorForRealm(army.realmId), Color.WHITE);
				}
			}

			if (snapshot.player != null && playerPoint != null) {
				int x = playerPoint.x;
				int y = playerPoint.y;
				if (x >= -50 && x <= screenWidth + 50 && y >= -50 && y <= screenHeight + 50) {
					int radius = 12;
					campaignPlayerHitbox = new Rectangle(x - 16, y - 16, 38, 32);
					g2.setColor(new Color(0, 0, 0, 80));
					g2.fillOval(x - radius, y + radius - 4, radius * 2, 7);
					g2.setColor(new Color(30, 88, 98));
					g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);
					g2.setStroke(new BasicStroke(3f));
					g2.setColor(new Color(255, 215, 90));
					g2.drawOval(x - radius, y - radius, radius * 2, radius * 2);
					g2.setStroke(new BasicStroke(1f));
					// Simple portrait token: head and shoulders inside the gold ring.
					g2.setColor(new Color(238, 205, 165));
					g2.fillOval(x - 4, y - 8, 8, 8);
					g2.setColor(new Color(245, 238, 210));
					g2.fillOval(x - 7, y + 1, 14, 9);
					String partySize = Integer.toString(snapshot.player.partySize);
					g2.setFont(new Font("SansSerif", Font.BOLD, 9));
					int badgeW = Math.max(16, g2.getFontMetrics().stringWidth(partySize) + 7);
					g2.setColor(new Color(10, 14, 18, 230));
					g2.fillRoundRect(x + radius - 4, y + radius - 7, badgeW, 14, 6, 6);
					g2.setColor(Color.WHITE);
					g2.drawString(partySize, x + radius, y + radius + 3);
					String text = snapshot.player.traveling ? "Your Party  ·  riding" : "YOUR PARTY";
					g2.setFont(new Font("SansSerif", Font.BOLD, 9));
					int width = g2.getFontMetrics().stringWidth(text) + 14;
					Rectangle label = placeCampaignLabel(x, y, width, 15,
							4, 50, screenWidth - 4, screenHeight - 36, occupiedLabels);
					occupiedLabels.add(label);
					drawCampaignNamePill(g2, label, text, new Color(20, 72, 82), Color.WHITE);
				}
			}

			if (activeCampaignTab == CampaignTab.OVERVIEW) {
				drawCampaignMapLegend(g2, 0, 0, screenWidth, screenHeight - 32);
			}
			drawCampaignHud(g2, snapshot);
			drawCampaignTabs(g2, snapshot);
			drawSettlementHoverCard(g2, snapshot);
			drawCampaignArmyHoverCard(g2, snapshot);
			drawCampaignCaravanHoverCard(g2, snapshot);
			drawCampaignPlayerHoverCard(g2, snapshot);
			drawCaptivityBanner(g2, snapshot);
			drawCampaignToasts(g2);
			drawCampaignNotice(g2);
		}

		/** Group key for tokens sharing a simulated point, with exact settlement matches named. */
		private String campaignTokenGroupKey(CampaignSnapshot snapshot, double worldX, double worldY) {
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				if (Math.abs(settlement.worldX - worldX) <= 0.01
						&& Math.abs(settlement.worldY - worldY) <= 0.01) {
					return "S:" + settlement.id;
				}
			}
			return "W:" + Math.round(worldX) + ":" + Math.round(worldY);
		}

		/** Converts a token position and applies its deterministic de-overlap slot. */
		private java.awt.Point campaignTokenPoint(CampaignSnapshot snapshot, double worldX, double worldY,
				java.util.Map<String, Integer> groupCounts,
				java.util.Map<String, Integer> nextSlots) {
			String key = campaignTokenGroupKey(snapshot, worldX, worldY);
			int slot = nextSlots.merge(key, 1, Integer::sum) - 1;
			int count = groupCounts.getOrDefault(key, 1);
			int[] fan = CampaignMapCamera.fanOutOffset(slot, count, 17);
			int baseX = key.startsWith("S:") ? 21 : 0;
			int baseY = key.startsWith("S:") ? 14 : 0;
			return new java.awt.Point(
					campaignCamera.worldToScreenX(worldX, screenWidth) + baseX + fan[0],
					campaignCamera.worldToScreenY(worldY, screenHeight) + baseY + fan[1]);
		}

		/** Pre-renders deterministic plains, forests, hills, mountains, and fields once per world. */
		private void drawCampaignTerrain(Graphics2D g2, CampaignSnapshot snapshot,
				double minX, double maxX, double minY, double maxY) {
			int key = 1;
			for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
				key = 31 * key + Long.hashCode(settlement.id);
				key = 31 * key + (int) Math.round(settlement.worldX * 10.0);
				key = 31 * key + (int) Math.round(settlement.worldY * 10.0);
				key = 31 * key + settlement.terrain.hashCode();
			}
			boolean boundsChanged = campaignTerrainBounds == null
					|| Math.abs(campaignTerrainBounds[0] - minX) > 0.01
					|| Math.abs(campaignTerrainBounds[1] - maxX) > 0.01
					|| Math.abs(campaignTerrainBounds[2] - minY) > 0.01
					|| Math.abs(campaignTerrainBounds[3] - maxY) > 0.01;
			if (campaignTerrainImage == null || campaignTerrainImageKey != key || boundsChanged) {
				double scale = 1.25;
				int imageWidth = Math.max(2, (int) Math.ceil((maxX - minX) * scale));
				int imageHeight = Math.max(2, (int) Math.ceil((maxY - minY) * scale));
				java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
						imageWidth, imageHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
				Graphics2D terrain = image.createGraphics();
				terrain.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
						java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				terrain.scale(scale, scale);
				terrain.translate(-minX, -minY);
				terrain.setPaint(new java.awt.GradientPaint((float) minX, (float) minY,
						new Color(111, 128, 76), (float) maxX, (float) maxY,
						new Color(145, 132, 84)));
				terrain.fillRect((int) Math.floor(minX), (int) Math.floor(minY),
						(int) Math.ceil(maxX - minX) + 2, (int) Math.ceil(maxY - minY) + 2);

				Random scatter = new Random(987654321L ^ key);
				for (int i = 0; i < 280; i++) {
					double cx = minX + scatter.nextDouble() * (maxX - minX);
					double cy = minY + scatter.nextDouble() * (maxY - minY);
					int radius = 16 + scatter.nextInt(70);
					Color[] tints = {
							new Color(88, 116, 62, 34), new Color(150, 143, 88, 30),
							new Color(70, 96, 58, 28), new Color(173, 151, 92, 24)};
					terrain.setColor(tints[scatter.nextInt(tints.length)]);
					terrain.fillOval((int) (cx - radius), (int) (cy - radius * 0.65),
							radius * 2, (int) (radius * 1.3));
				}

				for (CampaignSnapshot.SettlementView settlement : snapshot.settlements) {
					Color realm = colorForRealm(settlement.realmId);
					terrain.setColor(new Color(realm.getRed(), realm.getGreen(), realm.getBlue(), 18));
					terrain.fillOval((int) settlement.worldX - 72, (int) settlement.worldY - 55, 144, 110);
					Random local = new Random(settlement.id * 7919L + 17L);
					String terrainType = settlement.terrain == null ? "PLAINS" : settlement.terrain;
					if ("FORESTS".equals(terrainType)) {
						terrain.setColor(new Color(45, 82, 48, 52));
						terrain.fillOval((int) settlement.worldX - 92, (int) settlement.worldY - 72, 184, 144);
						for (int i = 0; i < 58; i++) {
							double angle = local.nextDouble() * Math.PI * 2.0;
							double distance = 14.0 + local.nextDouble() * 78.0;
							int tx = (int) (settlement.worldX + Math.cos(angle) * distance);
							int ty = (int) (settlement.worldY + Math.sin(angle) * distance * 0.72);
							int crown = 3 + local.nextInt(4);
							terrain.setColor(new Color(91, 67, 38));
							terrain.drawLine(tx, ty + crown, tx, ty + crown + 3);
							terrain.setColor(new Color(48, 92, 48));
							terrain.fillOval(tx - crown, ty - crown, crown * 2, crown * 2);
							terrain.setColor(new Color(82, 122, 63));
							terrain.fillOval(tx - crown / 2, ty - crown, Math.max(2, crown), Math.max(2, crown));
						}
					} else if ("HILLS".equals(terrainType)) {
						terrain.setStroke(new BasicStroke(2f));
						for (int i = 0; i < 18; i++) {
							int hx = (int) settlement.worldX - 80 + local.nextInt(160);
							int hy = (int) settlement.worldY - 55 + local.nextInt(110);
							int width = 18 + local.nextInt(26);
							terrain.setColor(new Color(126, 105, 70, 150));
							terrain.drawArc(hx, hy, width, width / 2, 0, 180);
						}
						terrain.setStroke(new BasicStroke(1f));
					} else if ("MOUNTAINS".equals(terrainType)) {
						for (int i = 0; i < 15; i++) {
							int mx = (int) settlement.worldX - 80 + local.nextInt(160);
							int my = (int) settlement.worldY - 45 + local.nextInt(95);
							int width = 16 + local.nextInt(18);
							terrain.setColor(new Color(112, 112, 112));
							terrain.fillPolygon(new int[] {mx, mx + width, mx + width / 2},
									new int[] {my + width / 2, my + width / 2, my}, 3);
							terrain.setColor(new Color(220, 220, 215));
							terrain.fillPolygon(new int[] {mx + width / 2 - 3, mx + width / 2 + 3, mx + width / 2},
									new int[] {my + 5, my + 5, my}, 3);
						}
					} else if ("DESERT".equals(terrainType)) {
						terrain.setColor(new Color(197, 164, 94, 70));
						terrain.fillOval((int) settlement.worldX - 90, (int) settlement.worldY - 65, 180, 130);
					} else if ("SWAMP".equals(terrainType)) {
						for (int i = 0; i < 18; i++) {
							int sx = (int) settlement.worldX - 75 + local.nextInt(150);
							int sy = (int) settlement.worldY - 50 + local.nextInt(100);
							terrain.setColor(new Color(61, 105, 105, 120));
							terrain.fillOval(sx, sy, 18 + local.nextInt(22), 7 + local.nextInt(8));
						}
					}
					// Cultivated plots make inhabited land read differently from wilderness.
					for (int i = 0; i < 6; i++) {
						int fx = (int) settlement.worldX - 48 + local.nextInt(92);
						int fy = (int) settlement.worldY - 34 + local.nextInt(68);
						terrain.setColor(i % 2 == 0 ? new Color(154, 132, 70, 95)
								: new Color(105, 139, 66, 95));
						terrain.fillRect(fx, fy, 13 + local.nextInt(12), 8 + local.nextInt(8));
					}
				}
				terrain.dispose();
				campaignTerrainImage = image;
				campaignTerrainImageKey = key;
				campaignTerrainBounds = new double[] {minX, maxX, minY, maxY};
			}
			int sx = campaignCamera.worldToScreenX(minX, screenWidth);
			int sy = campaignCamera.worldToScreenY(minY, screenHeight);
			int ex = campaignCamera.worldToScreenX(maxX, screenWidth);
			int ey = campaignCamera.worldToScreenY(maxY, screenHeight);
			g2.drawImage(campaignTerrainImage, sx, sy, Math.max(1, ex - sx), Math.max(1, ey - sy), null);
			g2.setColor(new Color(224, 198, 135, 75));
			g2.drawRect(sx, sy, Math.max(1, ex - sx), Math.max(1, ey - sy));
		}

		/** Small 2D settlement silhouette: village house, or a stone keep for larger places. */
		private void drawCampaignSettlementIcon(Graphics2D g2,
				CampaignSnapshot.SettlementView settlement, int x, int y, int radius) {
			Color realm = colorForRealm(settlement.realmId);
			g2.setColor(new Color(0, 0, 0, 75));
			g2.fillOval(x - radius, y + radius - 3, radius * 2, 7);
			boolean fortified = settlement.capital || "CITY".equals(settlement.type)
					|| "TOWN".equals(settlement.type) || "FORT".equals(settlement.type)
					|| "CAPITAL".equals(settlement.type);
			if (fortified) {
				g2.setColor(new Color(178, 169, 151));
				g2.fillRect(x - radius, y - radius + 3, radius * 2, radius * 2 - 3);
				g2.setColor(new Color(91, 84, 72));
				g2.drawRect(x - radius, y - radius + 3, radius * 2, radius * 2 - 3);
				for (int i = -1; i <= 1; i++) {
					g2.setColor(new Color(178, 169, 151));
					g2.fillRect(x + i * (radius - 1) - 2, y - radius, 4, 5);
					g2.setColor(new Color(91, 84, 72));
					g2.drawRect(x + i * (radius - 1) - 2, y - radius, 4, 5);
				}
				g2.setColor(realm);
				g2.fillPolygon(new int[] {x - radius - 1, x + radius + 1, x},
						new int[] {y - radius + 3, y - radius + 3, y - radius - 3}, 3);
				g2.setColor(new Color(54, 42, 32));
				g2.fillRect(x - 2, y + 1, 4, Math.max(4, radius - 1));
				if (settlement.capital) {
					g2.setColor(new Color(255, 215, 90));
					g2.fillPolygon(new int[] {x - 5, x - 2, x, x + 2, x + 5, x + 5, x - 5},
							new int[] {y - radius - 4, y - radius - 8, y - radius - 5,
									y - radius - 8, y - radius - 4, y - radius - 1, y - radius - 1}, 7);
				}
			} else {
				g2.setColor(new Color(190, 158, 110));
				g2.fillRect(x - radius + 1, y - 1, radius * 2 - 2, radius);
				g2.setColor(new Color(105, 76, 48));
				g2.drawRect(x - radius + 1, y - 1, radius * 2 - 2, radius);
				g2.setColor(realm);
				g2.fillPolygon(new int[] {x - radius - 1, x + radius + 1, x},
						new int[] {y, y, y - radius}, 3);
				g2.setColor(new Color(70, 48, 30));
				g2.fillRect(x - 2, y + 2, 4, Math.max(3, radius - 2));
			}
		}

		/** Realm-coloured pointed name tag with a tiny shield, after the Bannerlord map tags. */
		private void drawCampaignBannerTag(Graphics2D g2, Rectangle label, int anchorX, int anchorY,
				String text, Color realmColor, boolean urgent) {
			int centerX = label.x + label.width / 2;
			int centerY = label.y + label.height / 2;
			if (Math.hypot(centerX - anchorX, centerY - anchorY) > 34) {
				g2.setColor(new Color(245, 235, 205, 150));
				g2.drawLine(anchorX, anchorY, centerX, centerY);
			}
			boolean pointsLeft = label.x + label.width < anchorX;
			Color fill = new Color(realmColor.getRed(), realmColor.getGreen(), realmColor.getBlue(), 228);
			g2.setColor(fill);
			if (pointsLeft) {
				g2.fillPolygon(new int[] {label.x + 8, label.x + label.width, label.x + label.width,
								label.x + 8, label.x},
						new int[] {label.y, label.y, label.y + label.height,
								label.y + label.height, label.y + label.height / 2}, 5);
			} else {
				g2.fillPolygon(new int[] {label.x, label.x + label.width - 8, label.x + label.width,
								label.x + label.width - 8, label.x},
						new int[] {label.y, label.y, label.y + label.height / 2,
								label.y + label.height, label.y + label.height}, 5);
			}
			g2.setColor(new Color(18, 20, 20, 190));
			g2.drawPolygon(pointsLeft
					? new int[] {label.x + 8, label.x + label.width, label.x + label.width,
							label.x + 8, label.x}
					: new int[] {label.x, label.x + label.width - 8, label.x + label.width,
							label.x + label.width - 8, label.x},
					pointsLeft
					? new int[] {label.y, label.y, label.y + label.height,
							label.y + label.height, label.y + label.height / 2}
					: new int[] {label.y, label.y, label.y + label.height / 2,
							label.y + label.height, label.y + label.height}, 5);
			int shieldX = label.x + 3;
			g2.setColor(new Color(245, 226, 160));
			g2.fillPolygon(new int[] {shieldX, shieldX + 10, shieldX + 10, shieldX + 5, shieldX},
					new int[] {label.y + 2, label.y + 2, label.y + 10, label.y + 15, label.y + 10}, 5);
			g2.setColor(urgent ? new Color(255, 210, 190) : Color.WHITE);
			g2.setFont(new Font("SansSerif", Font.BOLD, 10));
			g2.drawString(text, label.x + 17, label.y + 12);
		}

		/** Shared dark pill for token nameplates. */
		private void drawCampaignNamePill(Graphics2D g2, Rectangle label, String text,
				Color background, Color foreground) {
			g2.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 225));
			g2.fillRoundRect(label.x, label.y, label.width, label.height, 6, 6);
			g2.setColor(new Color(230, 210, 160));
			g2.drawRoundRect(label.x, label.y, label.width, label.height, 6, 6);
			g2.setColor(foreground);
			g2.setFont(new Font("SansSerif", Font.BOLD, 9));
			g2.drawString(text, label.x + 7, label.y + 11);
		}

		/** Compact Bannerlord-like frame: date and speed controls, purse/party, local news. */
		private void drawCampaignHud(Graphics2D g2, CampaignSnapshot snapshot) {
			// Date / world summary.
			g2.setColor(new Color(12, 16, 20, 220));
			g2.fillRoundRect(8, 7, 238, 38, 8, 8);
			g2.setColor(new Color(205, 178, 112));
			g2.drawRoundRect(8, 7, 238, 38, 8, 8);
			g2.setFont(new Font("Serif", Font.BOLD, 13));
			g2.setColor(new Color(240, 224, 175));
			g2.drawString(snapshot.getDateLabel(), 16, 23);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
			g2.setColor(new Color(205, 215, 220));
			g2.drawString("Pop " + snapshot.livingPopulation + " · Wars " + snapshot.warCount
					+ " · Sieges " + snapshot.activeSiegeCount + " · Food "
					+ Math.round(snapshot.averageFoodSecurity * 100.0) + "%", 16, 37);

			// Speed controls.
			int controlW = 214;
			int controlX = screenWidth / 2 - controlW / 2;
			g2.setColor(new Color(12, 16, 20, 220));
			g2.fillRoundRect(controlX, 7, controlW, 38, 8, 8);
			g2.setColor(new Color(205, 178, 112));
			g2.drawRoundRect(controlX, 7, controlW, 38, 8, 8);
			String[][] controls = {
					{"PAUSE", snapshot.paused ? ">" : "II"},
					{"SPEED_1", "1x"}, {"SPEED_60", "60x"}, {"SPEED_1440", "1d/s"},
					{"CENTER", "Party"}};
			int buttonX = controlX + 7;
			for (String[] control : controls) {
				int width = "CENTER".equals(control[0]) ? 48 : 36;
				Rectangle button = new Rectangle(buttonX, 13, width, 26);
				campaignHudHitboxes.put(control[0], button);
				boolean active = ("PAUSE".equals(control[0]) && snapshot.paused)
						|| ("SPEED_1".equals(control[0]) && snapshot.speed == 1)
						|| ("SPEED_60".equals(control[0]) && snapshot.speed == 60)
						|| ("SPEED_1440".equals(control[0]) && snapshot.speed == 1440);
				g2.setColor(active ? new Color(174, 132, 58) : new Color(45, 58, 68));
				g2.fillRoundRect(button.x, button.y, button.width, button.height, 6, 6);
				g2.setColor(new Color(220, 196, 140));
				g2.drawRoundRect(button.x, button.y, button.width, button.height, 6, 6);
				g2.setFont(new Font("SansSerif", Font.BOLD, 10));
				g2.setColor(Color.WHITE);
				int textW = g2.getFontMetrics().stringWidth(control[1]);
				g2.drawString(control[1], button.x + (button.width - textW) / 2, button.y + 17);
				buttonX += width + 5;
			}
			if (snapshot.player != null && snapshot.player.traveling) {
				Rectangle stop = new Rectangle(controlX + controlW + 6, 13, 58, 26);
				campaignHudHitboxes.put("STOP_TRAVEL", stop);
				g2.setColor(new Color(110, 58, 42, 235));
				g2.fillRoundRect(stop.x, stop.y, stop.width, stop.height, 6, 6);
				g2.setColor(new Color(230, 190, 130));
				g2.drawRoundRect(stop.x, stop.y, stop.width, stop.height, 6, 6);
				g2.setColor(Color.WHITE);
				g2.setFont(new Font("SansSerif", Font.BOLD, 9));
				g2.drawString("Turn back", stop.x + 7, stop.y + 17);
			}

			// Party resources, top right.
			if (snapshot.player != null) {
				int x = screenWidth - 252;
				g2.setColor(new Color(12, 16, 20, 220));
				g2.fillRoundRect(x, 7, 244, 38, 8, 8);
				g2.setColor(new Color(205, 178, 112));
				g2.drawRoundRect(x, 7, 244, 38, 8, 8);
				g2.setColor(new Color(245, 196, 65));
				g2.fillOval(x + 9, 17, 11, 11);
				g2.setColor(new Color(120, 82, 20));
				g2.drawOval(x + 9, 17, 11, 11);
				g2.setFont(new Font("SansSerif", Font.BOLD, 10));
				g2.setColor(Color.WHITE);
				g2.drawString(Long.toString(snapshot.player.coins), x + 24, 26);
				g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
				g2.setColor(new Color(205, 215, 220));
				g2.drawString("Party " + snapshot.player.partySize + " · Food "
						+ (snapshot.player.grain + snapshot.player.vegetables) + " · Rep "
						+ snapshot.player.reputation, x + 78, 26);
				String place = snapshot.player.traveling
						? "Riding to " + snapshot.player.travelDestinationName
								+ " · " + campaignEtaText(snapshot.player.travelEtaMinutes)
						: "At " + settlementName(snapshot, snapshot.player.settlementId);
				g2.drawString(place, x + 10, 39);
			}

			String hint = "Left-click: ride   Right-click: inspect/select   Drag: pan   Wheel: zoom   TAB: local   H: help";
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
			int hintX = screenWidth / 2 - g2.getFontMetrics().stringWidth(hint) / 2;
			g2.setColor(Color.BLACK);
			g2.drawString(hint, hintX + 1, 58);
			g2.setColor(new Color(240, 235, 215));
			g2.drawString(hint, hintX, 57);

			if (snapshot.paused) {
				g2.setFont(new Font("SansSerif", Font.BOLD, 16));
				String paused = "PAUSED";
				int px = screenWidth / 2 - g2.getFontMetrics().stringWidth(paused) / 2;
				g2.setColor(Color.BLACK);
				g2.drawString(paused, px + 1, screenHeight - 47);
				g2.setColor(Color.WHITE);
				g2.drawString(paused, px, screenHeight - 48);
			}

			if (activeCampaignTab != CampaignTab.OVERVIEW || snapshot.player == null) return;
			java.util.List<String> eventLines = new java.util.ArrayList<>();
			for (int i = snapshot.recentEvents.size() - 1; i >= 0 && eventLines.size() < 4; i--) {
				String line = readableEvent(snapshot.recentEvents.get(i));
				if (line != null && !line.isBlank()) eventLines.add(0, line);
			}
			CampaignSnapshot.ArmyView yourArmy = findCommandedArmy(snapshot);
			int statusLines = yourArmy == null ? 1 : 2;
			int panelHeight = 10 + statusLines * 13 + (eventLines.isEmpty() ? 0 : 7 + eventLines.size() * 12);
			int panelY = screenHeight - 32 - panelHeight - 8;
			int panelX = 8;
			int panelW = 354;
			g2.setColor(new Color(10, 15, 20, 190));
			g2.fillRoundRect(panelX, panelY, panelW, panelHeight, 8, 8);
			g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
			int lineY = panelY + 14;
			g2.setColor(new Color(255, 224, 145));
			String partyLine = snapshot.player.traveling
					? "Your party (" + snapshot.player.partySize + ") rides to "
							+ snapshot.player.travelDestinationName + " · "
							+ String.format("%.1f", snapshot.player.travelSpeedPerHour) + "/h · "
							+ campaignEtaText(snapshot.player.travelEtaMinutes)
					: "Your party (" + snapshot.player.partySize + ") at "
							+ settlementName(snapshot, snapshot.player.settlementId);
			g2.drawString(partyLine, panelX + 8, lineY);
			lineY += 13;
			if (yourArmy != null) {
				g2.setColor(new Color(205, 220, 225));
				g2.drawString("Your army: " + yourArmy.strength + " · morale "
						+ Math.round(yourArmy.morale) + "% · " + yourArmy.grain + " grain · "
						+ yourArmy.order, panelX + 8, lineY);
				lineY += 13;
			}
			if (!eventLines.isEmpty()) {
				lineY += 2;
				g2.setColor(new Color(150, 130, 90));
				g2.drawLine(panelX + 8, lineY - 7, panelX + panelW - 8, lineY - 7);
				for (String line : eventLines) {
					java.util.List<String> wrapped = wrapCampaignText(g2, line, panelW - 16, 1);
					g2.setColor(new Color(220, 224, 220));
					if (!wrapped.isEmpty()) g2.drawString(wrapped.get(0), panelX + 8, lineY);
					lineY += 12;
				}
			}
		}

		/** Short ETA such as "5h 20m" or "2d 3h". */
		private String campaignEtaText(long minutes) {
			if (minutes <= 0) return "arriving";
			long days = minutes / (24L * 60L);
			long hours = (minutes % (24L * 60L)) / 60L;
			long mins = minutes % 60L;
			if (days > 0) return days + "d " + hours + "h";
			if (hours > 0) return hours + "h " + mins + "m";
			return mins + "m";
		}

		/** Bannerlord-like hover card for an army token. */
		private void drawCampaignArmyHoverCard(Graphics2D g2, CampaignSnapshot snapshot) {
			if (hoveredCampaignArmyId == null) return;
			CampaignSnapshot.ArmyView army = snapshot.findArmy(hoveredCampaignArmyId);
			if (army == null) return;
			int width = 252;
			int height = 148;
			int x = Math.max(6, Math.min(screenWidth - width - 6, campaignMousePoint.x + 14));
			int y = Math.max(48, Math.min(screenHeight - height - 38, campaignMousePoint.y + 14));
			g2.setColor(new Color(10, 18, 22, 238));
			g2.fillRoundRect(x, y, width, height, 9, 9);
			g2.setColor(colorForRealm(army.realmId));
			g2.setStroke(new BasicStroke(2f));
			g2.drawRoundRect(x, y, width, height, 9, 9);
			g2.setStroke(new BasicStroke(1f));
			g2.setFont(new Font("Serif", Font.BOLD, 14));
			g2.setColor(new Color(240, 224, 175));
			g2.drawString(campaignRealmName(snapshot, army.realmId) + " Army", x + 10, y + 20);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			g2.setColor(army.playerCommanded ? new Color(170, 230, 170)
					: army.hostileToPlayer ? new Color(255, 150, 125) : Color.LIGHT_GRAY);
			g2.drawString(army.playerCommanded ? "Under your command"
					: army.hostileToPlayer ? "Hostile to your realm" : "Neutral to you", x + 10, y + 37);
			g2.setColor(Color.WHITE);
			g2.drawString("Commander " + army.commanderName, x + 10, y + 54);
			g2.drawString("Troops " + army.strength + "  morale " + Math.round(army.morale)
					+ "%  fatigue " + Math.round(army.fatigue) + "%", x + 10, y + 70);
			g2.drawString("Order " + army.order + "  state " + army.state, x + 10, y + 86);
			g2.drawString("At " + settlementName(snapshot, army.currentSettlementId)
					+ " -> " + settlementName(snapshot, army.destinationSettlementId), x + 10, y + 102);
			g2.drawString("Grain " + army.grain + (army.daysWithoutFood > 0
					? "  (" + army.daysWithoutFood + "d unfed)" : ""), x + 10, y + 118);
			g2.setColor(new Color(190, 200, 205));
			g2.drawString(army.playerCommanded ? "Right-click for orders"
					: "Left-click ride to it · right-click inspect", x + 10, y + 137);
		}

		/** Hover card for a merchant caravan token. */
		private void drawCampaignCaravanHoverCard(Graphics2D g2, CampaignSnapshot snapshot) {
			if (hoveredCampaignCaravanId == null || hoveredCampaignArmyId != null) return;
			CampaignSnapshot.CaravanView caravan = snapshot.findCaravan(hoveredCampaignCaravanId);
			if (caravan == null) return;
			int width = 242;
			int height = 128;
			int x = Math.max(6, Math.min(screenWidth - width - 6, campaignMousePoint.x + 14));
			int y = Math.max(48, Math.min(screenHeight - height - 38, campaignMousePoint.y + 14));
			g2.setColor(new Color(18, 22, 20, 238));
			g2.fillRoundRect(x, y, width, height, 9, 9);
			g2.setColor(new Color(238, 184, 65));
			g2.drawRoundRect(x, y, width, height, 9, 9);
			g2.setFont(new Font("Serif", Font.BOLD, 14));
			g2.setColor(new Color(245, 220, 150));
			g2.drawString("Caravan of " + caravan.leaderName, x + 10, y + 20);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			g2.setColor(Color.WHITE);
			g2.drawString("Cargo " + caravan.cargo + " x" + caravan.cargoQuantity
					+ "  cash " + caravan.cash + "c", x + 10, y + 41);
			g2.drawString("Guards " + caravan.guardCount + "  trips " + caravan.completedTrips,
					x + 10, y + 57);
			g2.drawString("State " + caravan.state, x + 10, y + 73);
			g2.drawString(settlementName(snapshot, caravan.currentSettlementId) + " -> "
					+ settlementName(snapshot, caravan.destinationSettlementId), x + 10, y + 89);
			g2.setColor(new Color(190, 200, 205));
			g2.drawString("Left-click follow · right-click inspect", x + 10, y + 113);
		}

		/** Hover card for the player's own party token. */
		private void drawCampaignPlayerHoverCard(Graphics2D g2, CampaignSnapshot snapshot) {
			if (snapshot.player == null || campaignPlayerHitbox == null
					|| !campaignPlayerHitbox.contains(campaignMousePoint)
					|| hoveredCampaignArmyId != null || hoveredCampaignCaravanId != null) return;
			int width = 238;
			int height = 126;
			int x = Math.max(6, Math.min(screenWidth - width - 6, campaignMousePoint.x + 14));
			int y = Math.max(48, Math.min(screenHeight - height - 38, campaignMousePoint.y + 14));
			g2.setColor(new Color(12, 22, 26, 238));
			g2.fillRoundRect(x, y, width, height, 9, 9);
			g2.setColor(new Color(255, 215, 90));
			g2.drawRoundRect(x, y, width, height, 9, 9);
			g2.setFont(new Font("Serif", Font.BOLD, 14));
			g2.setColor(new Color(245, 226, 160));
			g2.drawString("Your Party", x + 10, y + 20);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			g2.setColor(Color.WHITE);
			g2.drawString("Companions " + snapshot.player.partySize + "  coins "
					+ snapshot.player.coins + "c", x + 10, y + 41);
			g2.drawString("Cargo " + snapshot.player.cargoUsed + "/"
					+ snapshot.player.cargoCapacity + "  contracts "
					+ snapshot.player.activeContracts, x + 10, y + 57);
			if (snapshot.player.traveling) {
				g2.drawString("Riding to " + snapshot.player.travelDestinationName, x + 10, y + 76);
				g2.drawString("Speed " + String.format("%.1f", snapshot.player.travelSpeedPerHour)
						+ "/h  ETA " + campaignEtaText(snapshot.player.travelEtaMinutes), x + 10, y + 92);
			} else {
				g2.drawString("At " + settlementName(snapshot, snapshot.player.settlementId),
						x + 10, y + 76);
				g2.drawString("TAB enters the local settlement", x + 10, y + 92);
			}
			g2.setColor(new Color(190, 200, 205));
			g2.drawString("Click token to centre · right-click party", x + 10, y + 112);
		}

		/**
		 * Prominent banner while the adventurer is a prisoner of war. Registers its button in
		 * the kingdom hitbox map, which drawCampaignTabs has already cleared and refilled, so
		 * this must run after the tabs.
		 */
		private void drawCaptivityBanner(Graphics2D g2, CampaignSnapshot snapshot) {
			if (snapshot == null || snapshot.player == null || !snapshot.player.isCaptive()) return;
			int w = 520, h = 60;
			int x = (screenWidth - w) / 2, y = 150;
			g2.setColor(new Color(38, 18, 18, 235));
			g2.fillRoundRect(x, y, w, h, 12, 12);
			g2.setColor(new Color(215, 120, 90));
			g2.drawRoundRect(x, y, w, h, 12, 12);
			g2.setFont(new Font("Serif", Font.BOLD, 15));
			g2.setColor(new Color(255, 214, 170));
			String captor = snapshot.player.captorRealmName == null
					? "THE ENEMY" : snapshot.player.captorRealmName.toUpperCase();
			g2.drawString("PRISONER OF " + captor, x + 16, y + 22);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
			g2.setColor(Color.WHITE);
			g2.drawString(snapshot.player.captiveDaysRemaining
					+ " day(s) left. Travel, trade, contracts and recruiting are blocked.", x + 16, y + 42);
			Rectangle pay = new Rectangle(x + w - 172, y + 13, 156, 34);
			kingdomManagementHitboxes.put("PAY_RANSOM", pay);
			g2.setColor(new Color(72, 52, 34));
			g2.fillRoundRect(pay.x, pay.y, pay.width, pay.height, 8, 8);
			g2.setColor(new Color(230, 195, 110));
			g2.drawRoundRect(pay.x, pay.y, pay.width, pay.height, 8, 8);
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("SansSerif", Font.BOLD, 11));
			String label = "Pay ransom " + snapshot.player.ransomCoins + "c";
			g2.drawString(label, pay.x + (pay.width - g2.getFontMetrics().stringWidth(label)) / 2, pay.y + 21);
		}

		/** Short labels so nine tabs still fit the 768px tab bar. */
		private String campaignTabLabel(CampaignTab tab) {
			switch (tab) {
				case KINGDOM:
					return "REALM";
				case WAR:
					return "WAR";
				case FIEFS:
					return "FIEFS";
				case ENCYCLOPEDIA:
					return "WORLD";
				default:
					return tab.toString();
			}
		}

		/** Turns a raw war-goal enum name into something a player can read. */
		private String readableWarGoal(String goal, String provinceName, String titleName) {
			String text;
			switch (goal) {
				case "CONQUER_PROVINCE":
					text = "Conquest of " + (provinceName == null ? "a province" : provinceName);
					break;
				case "CLAIM_TITLE":
					text = "Claim to " + (titleName == null ? "a title" : titleName);
					break;
				case "INDEPENDENCE":
					text = "Independence";
					break;
				case "VASSALIZE":
					text = "Vassalage";
					break;
				case "PUNITIVE":
					text = "Punitive war over a grievance";
					break;
				default:
					text = goal;
					break;
			}
			return text;
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
				String label = campaignTabLabel(tab);
				g2.drawString(label, rect.x + (rect.width - g2.getFontMetrics().stringWidth(label)) / 2, rect.y + 20);
			}
			if (activeCampaignTab == CampaignTab.OVERVIEW) return;
			// The war and fief panels need two-column detail, so they are allowed to be taller.
			int panelHeight = activeCampaignTab == CampaignTab.WAR || activeCampaignTab == CampaignTab.FIEFS
					? 212 : 166;
			int panelY = screenHeight - 39 - panelHeight;
			g2.setColor(new Color(12, 18, 25, 242));
			g2.fillRoundRect(18, panelY, screenWidth - 36, panelHeight, 12, 12);
			g2.setColor(new Color(205, 178, 112));
			g2.drawRoundRect(18, panelY, screenWidth - 36, panelHeight, 12, 12);
			g2.setFont(new Font("Serif", Font.BOLD, 17));
			g2.drawString(campaignTabLabel(activeCampaignTab), 32, panelY + 24);
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
						if ("LORD".equals(playerView.kingdomRole)) {
							Rectangle throne = new Rectangle(32, y + 10, 268, 26);
							kingdomManagementHitboxes.put("SEIZE_THRONE", throne);
							g2.setColor(new Color(70, 48, 34));
							g2.fillRoundRect(throne.x, throne.y, throne.width, throne.height, 8, 8);
							g2.setColor(new Color(225, 190, 105));
							g2.drawRoundRect(throne.x, throne.y, throne.width, throne.height, 8, 8);
							g2.setColor(new Color(255, 226, 160));
							g2.drawString("Press your claim to the throne", throne.x + 14, throne.y + 17);
							g2.setColor(Color.LIGHT_GRAY);
							g2.drawString("Needs a claim on the crown, or a kingdom in crisis (stability under 25)"
								+ " and 25+ realm reputation (you have " + playerView.realmReputation + ")",
								throne.x + throne.width + 14, throne.y + 17);
						}
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
				case WAR -> {
					int warsShown = 0;
					for (CampaignSnapshot.WarView war : snapshot.wars) {
						if (!war.isActive()) continue;
						g2.setColor(war.involvesPlayerRealm ? new Color(255, 170, 90) : new Color(228, 218, 194));
						g2.drawString(war.belligerents() + "  -  "
							+ readableWarGoal(war.goal, war.targetProvinceName, war.targetTitleName), 32, y);
						y += 14;
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("Day " + war.daysRunning + "  |  war score "
							+ Math.round(war.attackerScore) + " - " + Math.round(war.defenderScore)
							+ "  |  exhaustion " + Math.round(war.attackerExhaustion) + " - "
							+ Math.round(war.defenderExhaustion), 44, y);
						y += 13;
						g2.drawString("Contribution: "
							+ (war.contributions.isEmpty() ? "none yet" : String.join(", ", war.contributions))
							+ "  (contribution decides who may claim captured land)", 44, y);
						y += 18;
						if (++warsShown >= 2 || y > panelY + panelHeight - 46) break;
					}
					if (warsShown == 0) {
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("No active wars. Realms go to war over claims, provinces, grievances and vassalage.", 32, y);
						y += 16;
					}
					g2.setColor(new Color(222, 204, 155));
					g2.setFont(new Font("SansSerif", Font.BOLD, 12));
					g2.drawString("SIEGES", 32, y);
					g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
					y += 15;
					int siegesShown = 0;
					for (CampaignSnapshot.SiegeView siege : snapshot.sieges) {
						if (!siege.isActive()) continue;
						g2.setColor(siege.involvesPlayerRealm ? new Color(255, 170, 90) : Color.WHITE);
						g2.drawString(siege.besiegerRealmName + " besieges " + siege.settlementName
								+ " (held by " + siege.defenderRealmName + ")  -  " + siege.besiegerStrength
								+ " in lines / " + siege.besiegerCount + " committed, day "
								+ siege.daysRunning, 32, y);
						y += 14;
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("Breach " + Math.round(siege.breachProgress)
							+ "%  |  defender resolve " + Math.round(siege.defenderResolve)
							+ "%  |  food consumed " + siege.foodConsumed
							+ "  |  civilian deaths " + siege.civilianDeaths, 44, y);
						y += 17;
						if (++siegesShown >= 2 || y > panelY + panelHeight - 14) break;
					}
					if (siegesShown == 0) {
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("No active sieges. A besieging army has to reach an enemy settlement while a war is on.", 32, y);
					}
					// Rulers can open a war from here; that is what makes a BESIEGE order legal.
					java.util.List<Long> warTargets = campaignSession == null
						? java.util.Collections.emptyList() : campaignSession.declarableWarTargets();
					if (!warTargets.isEmpty()) {
						g2.setColor(new Color(222, 204, 155));
						g2.setFont(new Font("SansSerif", Font.BOLD, 12));
						g2.drawString("AS RULER: DECLARE A WAR OF CONQUEST", 430, panelY + 40);
						g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("War is what lets an army besiege and take land.", 430, panelY + 56);
						int by = panelY + 64;
						int shown = 0;
						for (Long targetRealmId : warTargets) {
							CampaignSnapshot.RealmView target = snapshot.findRealm(targetRealmId);
							if (target == null) continue;
							Rectangle button = new Rectangle(430, by, 152, 22);
							drawCampaignTabButton(g2, button, "War: " + target.name);
							kingdomManagementHitboxes.put("DECLARE_WAR:" + targetRealmId, button);
							by += 26;
							if (++shown >= 5 || by > panelY + panelHeight - 20) break;
						}
					}
					if (!snapshot.sieges.isEmpty()) {
						g2.setColor(new Color(222, 204, 155));
						g2.setFont(new Font("SansSerif", Font.BOLD, 12));
						g2.drawString("JOIN / STORM", 590, panelY + 40);
						g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString(snapshot.player == null ? ""
								: "HP " + snapshot.player.healthPercent + "% (fight > 0%)", 590, panelY + 54);
						g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
						int sy = panelY + 64;
						for (CampaignSnapshot.SiegeView siege : snapshot.sieges) {
							if (!siege.isActive()) continue;
							// Only offer Join for a siege whose war actually puts
							// your commanded army on the besieging side. Backend
							// checks still explain health, route, and army state.
							if (!siege.playerArmyAtSiege && !siege.playerArmyJoined
									&& !siege.playerArmyCanJoin) continue;
							Rectangle button = new Rectangle(590, sy, 140, 20);
							if (siege.playerArmyAtSiege) {
								drawCampaignTabButton(g2, button, "Storm " + siege.settlementName);
								kingdomManagementHitboxes.put("STORM_SIEGE:" + siege.id, button);
							} else if (siege.playerArmyJoined) {
								drawCampaignTabButton(g2, button, "Joining " + siege.settlementName);
								kingdomManagementHitboxes.put("JOIN_SIEGE:" + siege.id, button);
							} else {
								drawCampaignTabButton(g2, button, "Join " + siege.settlementName);
								kingdomManagementHitboxes.put("JOIN_SIEGE:" + siege.id, button);
							}
							sy += 24;
							if (sy > panelY + panelHeight - 20) break;
						}
					}
				}
				case FIEFS -> {
					java.util.List<CampaignSnapshot.TitleView> held = snapshot.playerTitles();
					int heldShown = 0;
					for (CampaignSnapshot.TitleView title : held) {
						g2.setColor(new Color(255, 215, 110));
						g2.drawString(title.rank + "  -  " + title.name, 32, y);
						y += 14;
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("Held under " + title.realmName + "  |  de jure "
							+ (title.deJureProvinceName == null ? "unassigned" : title.deJureProvinceName)
							+ "  |  claims against you " + title.claimCount, 44, y);
						y += 13;
						g2.drawString((title.settlementName == null ? "" : title.settlementName + "  |  ")
							+ "pays about " + title.rentPerDay + "c a day into your purse", 44, y);
						y += 17;
						if (++heldShown >= 3 || y > panelY + panelHeight - 60) break;
					}
					if (heldShown == 0) {
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("You hold no land. Fiefs are won by conquest and then claimed, or granted by a ruler.", 32, y);
						y += 16;
					}
					Long claimRealmId = snapshot.player == null ? null : snapshot.player.affiliatedRealmId;
					if (claimRealmId != null && campaignSession != null) {
						g2.setColor(new Color(222, 204, 155));
						g2.setFont(new Font("SansSerif", Font.BOLD, 12));
						g2.drawString("CLAIM CONQUERED LAND", 430, panelY + 40);
						g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
						g2.setColor(Color.LIGHT_GRAY);
						g2.drawString("Your conquest contribution: "
							+ Math.round(campaignSession.getPlayerState().conquestContribution)
							+ "  (25 barony, 40 county)", 430, panelY + 56);
						int claimY = panelY + 66;
						int claimable = 0;
						for (CampaignSnapshot.SettlementView occupied : snapshot.settlements) {
							if (occupied.occupyingRealmId == null
									|| occupied.occupyingRealmId.longValue() != claimRealmId.longValue()) continue;
							Rectangle button = new Rectangle(430, claimY, 300, 22);
							drawCampaignTabButton(g2, button, "Claim " + occupied.name + " as your holding");
							kingdomManagementHitboxes.put("CLAIM_HOLDING:" + occupied.id, button);
							claimY += 26;
							if (++claimable >= 5 || claimY > panelY + panelHeight - 20) break;
						}
						if (claimable == 0) {
							g2.setColor(Color.LIGHT_GRAY);
							g2.drawString(campaignRealmName(snapshot, claimRealmId)
								+ " occupies nothing. Take a settlement by siege first.", 430, claimY);
						}
					}
					g2.setColor(new Color(222, 204, 155));
					g2.setFont(new Font("SansSerif", Font.BOLD, 12));
					g2.drawString("CROWNS", 32, y);
					g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
					y += 15;
					for (CampaignSnapshot.TitleView title : snapshot.titles) {
						if (!title.isCrown) continue;
						g2.setColor(title.heldByPlayer ? new Color(170, 230, 170) : Color.WHITE);
						g2.drawString(title.realmName + ": " + title.name + "  -  " + title.holderName
							+ (title.claimCount > 0 ? "  (" + title.claimCount + " claimants)" : ""), 32, y);
						y += 14;
						if (y > panelY + panelHeight - 14) break;
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
			int width = 236;
			int height = 168;
			int x = Math.min(screenWidth - width - 8, campaignMousePoint.x + 14);
			int y = Math.min(screenHeight - height - 40, campaignMousePoint.y + 14);
			g2.setColor(new Color(8, 13, 18, 235));
			g2.fillRoundRect(x, y, width, height, 10, 10);
			g2.setColor(new Color(225, 194, 120));
			g2.drawRoundRect(x, y, width, height, 10, 10);
			g2.setFont(new Font("Serif", Font.BOLD, 15));
			g2.drawString(settlement.name, x + 10, y + 20);
			g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
			// Ownership first: it is the question the map colours only half answer.
			g2.setColor(colorForRealm(settlement.realmId));
			g2.drawString("Owner: " + campaignRealmName(snapshot, settlement.realmId), x + 10, y + 38);
			if (settlement.occupyingRealmId != null) {
				g2.setColor(new Color(255, 150, 110));
				g2.drawString("Occupied by " + campaignRealmName(snapshot, settlement.occupyingRealmId), x + 10, y + 52);
			}
			CampaignSnapshot.SiegeView siege = snapshot.findActiveSiegeAt(settlement.id);
			if (siege != null) {
				g2.setColor(new Color(255, 110, 90));
				g2.drawString("UNDER SIEGE by " + siege.besiegerRealmName, x + 10, y + 66);
				g2.drawString("  breach " + Math.round(siege.breachProgress) + "%  resolve "
						+ Math.round(siege.defenderResolve) + "%", x + 10, y + 79);
			}
			g2.setColor(Color.WHITE);
			int row = siege != null ? 96 : 68;
			g2.drawString("Population " + settlement.population + "  households " + settlement.households, x + 10, y + row);
			g2.drawString("Treasury " + settlement.treasury + "c", x + 10, y + row + 15);
			g2.drawString("Grain " + settlement.grain + " @ " + settlement.grainPrice + "c"
					+ "   Veg " + settlement.vegetables + " @ " + settlement.vegetablePrice + "c", x + 10, y + row + 30);
			g2.drawString(String.format("Security %.0f%%  unrest %.0f%%  food %.0f%%",
					settlement.security * 100, settlement.unrest * 100, settlement.foodSecurity * 100), x + 10, y + row + 45);
			long crimeCount = snapshot.crimes.stream().filter(crime -> crime.settlementId == settlement.id).count();
			g2.drawString("Recorded crime " + crimeCount, x + 10, y + row + 60);
		}

		/** Realm name lookup for hover cards and panels, tolerant of stale ids. */
		/** Small gold-outlined button used inside the world tab panel. */
		private void drawCampaignTabButton(Graphics2D g2, Rectangle button, String label) {
			g2.setColor(new Color(58, 40, 30));
			g2.fillRoundRect(button.x, button.y, button.width, button.height, 6, 6);
			g2.setColor(new Color(205, 178, 112));
			g2.drawRoundRect(button.x, button.y, button.width, button.height, 6, 6);
			g2.setColor(new Color(240, 228, 200));
			g2.drawString(label, button.x + 8, button.y + button.height - 7);
		}

		private CampaignSnapshot.ArmyView findCommandedArmy(CampaignSnapshot snapshot) {
			if (snapshot == null || snapshot.player == null || snapshot.player.commandedArmyId == null) return null;
			long wanted = snapshot.player.commandedArmyId;
			for (CampaignSnapshot.ArmyView army : snapshot.armies) {
				if (army.id == wanted) return army;
			}
			return null;
		}

		private String settlementName(CampaignSnapshot snapshot, Long settlementId) {
			if (snapshot == null || settlementId == null) return "the field";
			CampaignSnapshot.SettlementView settlement = snapshot.findSettlement(settlementId);
			return settlement == null ? "the field" : settlement.name;
		}

		private String campaignRealmName(CampaignSnapshot snapshot, Long realmId) {
			if (realmId == null) return "independent";
			CampaignSnapshot.RealmView realm = snapshot.findRealm(realmId);
			return realm == null ? "Realm #" + realmId : realm.name;
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
		g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(8, 18, 29),
				screenWidth, screenHeight, new Color(35, 22, 18)));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		// Subtle horizon and frame keep the screen readable without requiring menu art.
		g2.setColor(new Color(211, 164, 72, 24));
		for (int i = 0; i < 5; i++) g2.drawOval(screenWidth / 2 - 260 - i * 18, 80 - i * 8, 520 + i * 36, 320 + i * 16);
		g2.setColor(new Color(225, 190, 105));
		g2.setFont(new Font("Serif", Font.BOLD, 44));
		drawCenteredString(g2, "CHRONICLE CONQUEST", 86);
		g2.setColor(new Color(185, 195, 202));
		g2.setFont(new Font("SansSerif", Font.PLAIN, 13));

		g2.setColor(new Color(7, 12, 18, 215));
		g2.fillRoundRect(118, 145, screenWidth - 236, 245, 18, 18);
		g2.setColor(new Color(112, 91, 60));
		g2.setFont(new Font("SansSerif", Font.PLAIN, 18));

		switch (menuStage) {
			case MODE_SELECT:
				drawMenuPrompt(g2, "Choose your experience");
				drawMenuButton(g2, sandboxModeButton, "SANDBOX");
				drawMenuButton(g2, survivalModeButton, "SURVIVAL");
				drawMenuButton(g2, campaignModeButton, "CAMPAIGN");
				g2.setColor(new Color(170, 178, 184));
				g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
				drawCenteredAt(g2, "Explore, recruit, and fight", sandboxModeButton.x + sandboxModeButton.width / 2, 347);
				drawCenteredAt(g2, "Fight endles waves of ennemies", survivalModeButton.x + survivalModeButton.width / 2, 347);
				drawCenteredAt(g2, "Create your own legend", campaignModeButton.x + campaignModeButton.width / 2, 347);

				break;
			case ALLY_YES_NO:
				drawMenuPrompt(g2, "Begin survival with allied troops?");
				drawMenuButton(g2, allyYesButton, "BRING ALLIES");
				drawMenuButton(g2, allyNoButton, "FIGHT ALONE");
				drawMenuButton(g2, backButton, "BACK");
				break;
			case ALLY_TYPE:
				drawMenuPrompt(g2, "Choose your starting formation");
				drawMenuButton(g2, allyMeleeButton, "MELEE");
				drawMenuButton(g2, allyArcherButton, "ARCHERS");
				drawMenuButton(g2, allyBothButton, "BALANCED");
				drawMenuButton(g2, backButton, "BACK");
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
				g2.setColor(new Color(225, 190, 105));
				g2.setFont(new Font("Serif", Font.BOLD, 25));
				drawCenteredString(g2, gameMode == GameMode.SURVIVAL ? "SURVIVAL READY" : "SANDBOX READY", 230);
				g2.setColor(Color.WHITE);
				g2.setFont(new Font("SansSerif", Font.BOLD, 18));
				drawCenteredString(g2, "Press ENTER to begin", 292);
				g2.setColor(new Color(160, 170, 178));
				g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
				drawCenteredString(g2, "Your choices are set. The world is waiting.", 322);
				break;
		}

		g2.setColor(new Color(12, 18, 24, 210));
		g2.fillRoundRect(120, 420, screenWidth - 240, 92, 14, 14);
		g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
		g2.setColor(new Color(210, 216, 220));
		drawCenteredString(g2, "WASD  Move     SPACE  Attack     1/2/3  Abilities     E  Dodge", 451);
		drawCenteredString(g2, "F  Interact     Q  Switch Hero     P  Pause     F3  Debug", 480);
		g2.setColor(new Color(125, 135, 145));
		g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
		drawCenteredString(g2, "BETA BUILD", 548);
	}

	private void drawMenuPrompt(Graphics2D g2, String text) {
		g2.setColor(new Color(232, 232, 224));
		g2.setFont(new Font("Serif", Font.BOLD, 23));
		drawCenteredString(g2, text, 215);
	}

	private void drawMenuButton(Graphics2D g2, Rectangle btn, String label) {
		g2.setColor(new Color(0, 0, 0, 90));
		g2.fillRoundRect(btn.x + 3, btn.y + 4, btn.width, btn.height, 10, 10);
		g2.setPaint(new java.awt.GradientPaint(btn.x, btn.y, new Color(70, 83, 94),
				btn.x, btn.y + btn.height, new Color(39, 48, 57)));
		g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setColor(new Color(205, 170, 92));
		g2.drawRoundRect(btn.x, btn.y, btn.width, btn.height, 10, 10);
		g2.setColor(Color.WHITE);
		g2.setFont(new Font("SansSerif", Font.BOLD, btn.height < 45 ? 12 : 14));
		int textW = g2.getFontMetrics().stringWidth(label);
		int baseline = btn.y + (btn.height - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
		g2.drawString(label, btn.x + (btn.width - textW) / 2, baseline);
	}

	private void drawCenteredString(Graphics2D g2, String text, int y) {
		drawCenteredAt(g2, text, screenWidth / 2, y);
	}

	private void drawCenteredAt(Graphics2D g2, String text, int centerX, int y) {
		g2.drawString(text, centerX - g2.getFontMetrics().stringWidth(text) / 2, y);
	}

	private void drawPauseMenu(Graphics2D g2) {
		int width = 480;
		int height = 410;
		int x = (screenWidth - width) / 2;
		int y = (screenHeight - height) / 2;
		g2.setColor(new Color(2, 5, 8, 205));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setPaint(new java.awt.GradientPaint(x, y, new Color(31, 43, 53, 248),
				x + width, y + height, new Color(18, 24, 31, 248)));
		g2.fillRoundRect(x, y, width, height, 20, 20);
		g2.setColor(new Color(207, 169, 89));
		g2.setStroke(new BasicStroke(2f));
		g2.drawRoundRect(x, y, width, height, 20, 20);
		g2.setStroke(new BasicStroke(1f));

		g2.setColor(new Color(230, 205, 145));
		g2.setFont(new Font("Serif", Font.BOLD, 34));
		drawCenteredString(g2, "GAME PAUSED", y + 52);
		g2.setColor(new Color(170, 180, 188));
		g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
		drawCenteredString(g2, gameMode == GameMode.SURVIVAL
				? "The current survival run is safely suspended"
				: "The world is waiting for your return", y + 78);

		drawMenuButton(g2, pauseResumeButtonRect, "RESUME GAME");
		drawMenuButton(g2, settingsButtonRect, "CONTROLS & SETTINGS");
		drawMenuButton(g2, getRestartButtonRect(), "RETURN TO MAIN MENU");

		g2.setColor(new Color(10, 15, 20, 180));
		g2.fillRoundRect(x + 30, y + 325, width - 60, 54, 10, 10);
		g2.setColor(new Color(185, 194, 200));
		g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
		drawCenteredString(g2, "WASD Move  ·  SPACE Attack  ·  1/2/3 Abilities  ·  E Dodge", y + 349);
		g2.setColor(new Color(215, 178, 95));
		drawCenteredString(g2, "P also resumes", y + 368);

		// NOTE: "Quit Survival Mode" intentionally lives only on the power-up menu.
	}

	private void drawDeathMenu(Graphics2D g2) {
		int width = 480;
		int height = 340;
		int x = (screenWidth - width) / 2;
		int y = (screenHeight - height) / 2;
		g2.setColor(new Color(20, 5, 5, 220));
		g2.fillRect(0, 0, screenWidth, screenHeight);
		g2.setPaint(new java.awt.GradientPaint(x, y, new Color(60, 20, 20, 248),
				x + width, y + height, new Color(24, 12, 12, 248)));
		g2.fillRoundRect(x, y, width, height, 20, 20);
		g2.setColor(new Color(207, 69, 89));
		g2.setStroke(new BasicStroke(2f));
		g2.drawRoundRect(x, y, width, height, 20, 20);
		g2.setStroke(new BasicStroke(1f));

		g2.setColor(new Color(230, 190, 190));
		g2.setFont(new Font("Serif", Font.BOLD, 38));
		drawCenteredString(g2, "YOU DIED", y + 60);
		g2.setColor(new Color(180, 170, 170));
		g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
		drawCenteredString(g2, "Your survival run has ended.", y + 96);

		drawMenuButton(g2, getDeathRestartButtonRect(), "START AGAIN");
		drawMenuButton(g2, getDeathMainMenuButtonRect(), "MAIN MENU");
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

	/**
	 * Picks a companion's hero class from the simulated person rather than their ID, so a
	 * blacksmith no longer shows up as a necromancer. Uses the person's real skills and
	 * social type; an undeveloped person stays a generic ADVENTURER.
	 */
	private Hero.HeroClass heroClassForPerson(world.Person person) {
		if (person == null) return Hero.HeroClass.ADVENTURER;
		world.PersonSkills s = person.skills;
		java.util.Map<Hero.HeroClass, Double> scores = new java.util.EnumMap<>(Hero.HeroClass.class);
		scores.put(Hero.HeroClass.WARRIOR, s.martial * 2.0 + s.leadership);
		scores.put(Hero.HeroClass.PALADIN, s.martial + s.medicine * 0.6 + s.diplomacy * 0.6);
		scores.put(Hero.HeroClass.ARCHER, s.martial * 0.9 + s.crafting * 0.9);
		scores.put(Hero.HeroClass.ROGUE, s.intrigue * 1.4 + s.trade * 0.6);
		scores.put(Hero.HeroClass.CLERIC, s.medicine * 1.5 + s.diplomacy * 0.5);
		scores.put(Hero.HeroClass.MAGE, s.learning * 1.5 + s.intrigue * 0.3);
		scores.put(Hero.HeroClass.DRUID, s.farming * 1.2 + s.medicine * 0.6);
		// Necromancy is the rare dark-scholar path: it only wins on very high intrigue + learning.
		scores.put(Hero.HeroClass.NECROMANCER, s.intrigue * 1.1 + s.learning * 0.7 - 25.0);
		switch (person.type) {
			case SOLDIER:
				scores.merge(Hero.HeroClass.WARRIOR, 20.0, Double::sum);
				break;
			case NOBLE:
				scores.merge(Hero.HeroClass.PALADIN, 15.0, Double::sum);
				scores.merge(Hero.HeroClass.WARRIOR, 10.0, Double::sum);
				break;
			case MERCHANT:
				scores.merge(Hero.HeroClass.ROGUE, 15.0, Double::sum);
				break;
			case SERVANT:
				scores.merge(Hero.HeroClass.CLERIC, 10.0, Double::sum);
				break;
			default:
				break;
		}
		double best = -1.0;
		Hero.HeroClass bestClass = Hero.HeroClass.ADVENTURER;
		for (java.util.Map.Entry<Hero.HeroClass, Double> entry : scores.entrySet()) {
			if (entry.getValue() > best) {
				best = entry.getValue();
				bestClass = entry.getKey();
			}
		}
		return best < 20.0 ? Hero.HeroClass.ADVENTURER : bestClass;
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
			Hero hero = new Hero(this, (person.givenName + " " + person.familyName).trim(),
				heroClassForPerson(person), (int) player.x + tileSize, (int) player.y);
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
		// Both branches above guarantee at least 1 HP, so a finished/half-finished death
		// animation must not carry over into the newly controlled character.
		player.resetDeathState();
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
		// Dying is not escapable: setActiveHero() always hands back at least 1 HP, so swapping
		// bodies mid-death-animation would resurrect the player and skip the death menu.
		if (player.isDying()) {
			return;
		}
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
		
		g2.drawString("Gold: " + gold, x + 6, y + height - 4);

		if (inventoryOpen) {
			drawInventoryPanel(g2);
			}
		} else {
		g2.setColor(new Color(64, 64, 64, 200));
		g2.fillRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.setColor(Color.white);
		g2.drawRoundRect(inventoryButton.x, inventoryButton.y, inventoryButton.width, inventoryButton.height, 10, 10);
		g2.drawString("Wave: "+survivalWaveNumber, inventoryButton.x + 12, inventoryButton.y + 20);
		
		g2.drawString("Wave Enemy Coount: " + survivalEnemyCountForWave, x + 6, y + height - 4);
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

