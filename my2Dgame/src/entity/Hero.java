package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import my2Dgame.GamePanel;

/**
 * Hero - Playable character class with recruitment, switching, and companion system.
 * Supports multiple playable characters that can be recruited, switched, and serve as companions.
 */
public class Hero extends Entity {
    public enum HeroClass {
        WARRIOR("Warrior", "Melee tank with high defense", 120, 20, 10, 0),
        MAGE("Mage", "Ranged spellcaster with high magic damage", 70, 10, 5, 80),
        ARCHER("Archer", "Ranged physical damage dealer", 80, 15, 8, 20),
        CLERIC("Cleric", "Support with healing and buffs", 90, 12, 6, 50),
        ROGUE("Rogue", "High crit/dodge melee DPS", 75, 18, 15, 10),
        PALADIN("Paladin", "Holy warrior with defensive auras", 110, 16, 8, 30),
        NECROMANCER("Necromancer", "Summons undead minions", 65, 8, 4, 90),
        DRUID("Druid", "Hybrid shapeshifter/nature magic", 85, 14, 7, 60);

        public final String displayName;
        public final String description;
        public final int baseHealth;
        public final int baseAttack;
        public final int baseCritChance;
        public final int baseMana;
       

        HeroClass(String displayName, String description, int baseHealth, int baseAttack, int baseCritChance, int baseMana) {
            this.displayName = displayName;
            this.description = description;
            this.baseHealth = baseHealth;
            this.baseAttack = baseAttack;
            this.baseCritChance = baseCritChance;
            this.baseMana = baseMana;
        }
    }

    public enum CompanionRole {
        FIGHTER("Fights alongside player", true, false),
        ADVISOR("Provides kingdom dialogue options", false, true),
        HYBRID("Fights and advises", true, true);

        public final String description;
        public final boolean canFight;
        public final boolean canAdvise;

        CompanionRole(String description, boolean canFight, boolean canAdvise) {
            this.description = description;
            this.canFight = canFight;
            this.canAdvise = canAdvise;
        }
    }

    // ===== CORE IDENTITY =====
    public String name;
    public HeroClass heroClass;
    public int characterId;
    private static int nextCharacterId = 1;
    private java.util.Map<String, BufferedImage[]> attackFrames = new java.util.HashMap<>();
    public boolean isAttacking = false;
    public int attackAnimationCounter = 0;
    public int attackAnimationFrame = 0;

    // ===== STATS =====
    public int level = 1;
    public int experience = 0;
    public int experienceToNextLevel = 100;
    public int maxHealth;
    public int health;
    public int maxMana;
    public int mana;
    public int attack;
    public int defense = 5;
    public int critChance;
    public int dodgeChance = 5;
    public int manaRegen = 2;
    public int staminaRegen = 3;

    // ===== EQUIPMENT =====
    public Equipment weapon;
    public Equipment armor;
    public Equipment accessory;

    // ===== ABILITIES =====
    public List<Ability> abilities = new ArrayList<>();
    public List<Ability> unlockedAbilities = new ArrayList<>();

    // ===== COMPANION SYSTEM =====
    public boolean isRecruited = false;
    public boolean isActivePlayer = false;
    public CompanionRole companionRole = CompanionRole.HYBRID;
    public Entity activePlayerReference;
    public int companionAiTick = 0;
    public float followDistance = 60f;
    public boolean usePlayerAbilities = true;

    // ===== DIALOGUE/INTERACTION =====
    public List<DialogueOption> dialogueOptions = new ArrayList<>();
    public String[] recruitmentLines;
    public String[] switchCharacterLines;
    public String[] kingdomAffairsLines;
    public String[] companionIdleLines;
    public Rectangle interactionArea = new Rectangle();

    // ===== VISUAL =====
    public BufferedImage portrait;
    public BufferedImage spriteSheet;
    public String spritePath;
    public int spriteFrame = 0;
    public int spriteCounter = 0;
    public String direction = "down";
    private float idleDrawScale = 1.0f;
    private float attackDrawScale = 1.8f;

    // ===== KINGDOM AFFAIRS =====
    public KingdomAffairs kingdomAffairs;
    
    private java.util.Map<String, BufferedImage[]> walkFrames = new java.util.HashMap<>();
    private int spriteNumForWalk = 1;

    // ===== CONSTRUCTORS =====
    public Hero(GamePanel gp, String name, HeroClass heroClass, int startX, int startY) {
        this.gp = gp;
        this.name = name;
        this.heroClass = heroClass;
        this.characterId = nextCharacterId++;
        this.x = startX;
        this.y = startY;
        this.speed = 3f;
        initializeBaseStats();
        initializeAbilities();
        initializeDialogue();
        loadSprite();
    }

    public Hero(GamePanel gp, String name, HeroClass heroClass) {
        this(gp, name, heroClass, gp.tileSize * 2, gp.tileSize * 2);
    }

    // ===== INITIALIZATION =====
    private void initializeBaseStats() {
        maxHealth = heroClass.baseHealth + (level - 1) * 10;
        health = maxHealth;
        maxMana = heroClass.baseMana + (level - 1) * 5;
        mana = maxMana;
        attack = heroClass.baseAttack + (level - 1) * 3;
        critChance = heroClass.baseCritChance;
        defense = 5 + (level - 1) * 2;
    }

    private void initializeAbilities() {
        abilities.add(new Ability("Basic Attack", "Standard melee/ranged attack", 0, 0, Ability.AbilityType.BASIC_ATTACK));

        switch (heroClass) {
            case WARRIOR -> {
                abilities.add(new Ability("Shield Bash", "Stuns enemy", 15, 30, Ability.AbilityType.STUN));
                abilities.add(new Ability("Whirlwind", "AoE damage around self", 25, 45, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Taunt", "Forces enemies to target you", 10, 60, Ability.AbilityType.TAUNT));
                abilities.add(new Ability("Last Stand", "Immune to death for 5s", 40, 120, Ability.AbilityType.BUFF));
            }
            case MAGE -> {
                abilities.add(new Ability("Fireball", "Exploding projectile", 20, 15, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Ice Spike", "Piercing projectile that slows", 15, 20, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Meteor", "Large AoE damage", 40, 60, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Teleport", "Blink to target location", 30, 40, Ability.AbilityType.MOBILITY));
            }
            case ARCHER -> {
                abilities.add(new Ability("Multi-Shot", "Fires 3 arrows in spread", 15, 25, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Piercing Arrow", "Passes through enemies", 20, 30, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Rain of Arrows", "AoE arrow barrage", 35, 50, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Evasive Roll", "Dash with iframe", 10, 20, Ability.AbilityType.MOBILITY));
            }
            case CLERIC -> {
                abilities.add(new Ability("Heal", "Restores health to ally", 20, 30, Ability.AbilityType.HEAL));
                abilities.add(new Ability("Blessing", "Increases ally attack/defense", 25, 45, Ability.AbilityType.BUFF));
                abilities.add(new Ability("Sanctuary", "AoE heal over time", 35, 60, Ability.AbilityType.AOE_HEAL));
                abilities.add(new Ability("Smite", "Holy damage to undead", 20, 25, Ability.AbilityType.PROJECTILE));
            }
            case ROGUE -> {
                abilities.add(new Ability("Backstab", "High damage from behind", 15, 20, Ability.AbilityType.CRIT));
                abilities.add(new Ability("Smoke Bomb", "Blind enemies in area", 20, 40, Ability.AbilityType.DEBUFF));
                abilities.add(new Ability("Shadow Step", "Teleport behind target", 15, 30, Ability.AbilityType.MOBILITY));
                abilities.add(new Ability("Poison Blade", "Applies DoT", 10, 15, Ability.AbilityType.DOT));
            }
            case PALADIN -> {
                abilities.add(new Ability("Holy Strike", "Bonus damage to evil", 20, 25, Ability.AbilityType.CRIT));
                abilities.add(new Ability("Divine Shield", "Block next 3 hits", 25, 50, Ability.AbilityType.BUFF));
                abilities.add(new Ability("Consecration", "AoE damage + ally heal", 30, 60, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Judgment", "Execute low-health enemies", 35, 80, Ability.AbilityType.EXECUTE));
            }
            case NECROMANCER -> {
                abilities.add(new Ability("Raise Skeleton", "Summons minion", 30, 60, Ability.AbilityType.SUMMON));
                abilities.add(new Ability("Life Drain", "Heal self, damage enemy", 20, 30, Ability.AbilityType.DRAIN));
                abilities.add(new Ability("Bone Prison", "Roots enemies", 25, 45, Ability.AbilityType.CC));
                abilities.add(new Ability("Death Nova", "AoE on minion death", 15, 20, Ability.AbilityType.PASSIVE));
            }
            case DRUID -> {
                abilities.add(new Ability("Shape: Bear", "Become tank form", 20, 40, Ability.AbilityType.TRANSFORM));
                abilities.add(new Ability("Shape: Cat", "Become DPS form", 15, 30, Ability.AbilityType.TRANSFORM));
                abilities.add(new Ability("Entangling Roots", "Root enemies", 20, 35, Ability.AbilityType.CC));
                abilities.add(new Ability("Wild Growth", "Heal over time AoE", 30, 50, Ability.AbilityType.AOE_HEAL));
            }
        }

        if (abilities.size() > 1) unlockedAbilities.add(abilities.get(0));
        if (abilities.size() > 2) unlockedAbilities.add(abilities.get(1));
    }

    private void initializeDialogue() {
        recruitmentLines = new String[]{
            name + ": \"I've heard of your deeds. Let me join your cause.\"",
            name + ": \"A worthy leader. I'll fight at your side.\"",
            name + ": \"The road ahead is dangerous. Allow me to help.\""
        };

        switchCharacterLines = new String[]{
            name + ": \"You want me to take the lead? Very well.\"",
            name + ": \"Switching positions. I'll hold the line.\"",
            name + ": \"Understood. I'll watch your back from here.\""
        };

        kingdomAffairsLines = new String[]{
            name + ": \"The treasury needs attention, my liege.\"",
            name + ": \"Our borders need reinforcing.\"",
            name + ": \"The people seek your counsel.\"",
            name + ": \"Trade routes require protection.\""
        };

        companionIdleLines = new String[]{
            name + ": \"...\"",
            name + ": *sharpens weapon*",
            name + ": *studies a tome*",
            name + ": *scans the horizon*"
        };
    }

    public void onRecruited(GamePanel gp) {
        // Called when hero is recruited to party
        this.gp = gp;
        isRecruited = true;
    }

  

        private void loadSprite() {
            String[] dirs = {"up", "down", "left", "right"};
            String walkSubPath = "standard/walk/";
            String attackSubPath = "custom/slash_oversize/"; // pick ONE canonical subfolder per animation type — see note below
            String basePath = getAssetFolderForClass();
            boolean anyFrameLoaded = false;

            try {
                for (String dir : dirs) {
                    BufferedImage[] frames = new BufferedImage[10];
                    for (int i = 1; i <= 9; i++) {
                        String fullPath = basePath + walkSubPath + dir + "/" + i + ".png";
                        InputStream is = getClass().getResourceAsStream(fullPath);
                        if (is != null) {
                            frames[i] = ImageIO.read(is);
                            anyFrameLoaded = true;
                        } else {
                            System.out.println("Hero walk sprite missing: " + fullPath);
                        }
                    }
                    walkFrames.put(dir, frames); // now only called ONCE per direction — nothing left to overwrite it
                }

                for (String dir : dirs) {
                    BufferedImage[] frames = new BufferedImage[8];
                    for (int i = 1; i <= 7; i++) {
                        String fullPath = basePath + attackSubPath + dir + "/" + i + ".png";
                        InputStream is = getClass().getResourceAsStream(fullPath);
                        if (is != null) {
                            frames[i] = ImageIO.read(is);
                        } else {
                            System.out.println("Hero attack sprite missing: " + fullPath);
                        }
                    }
                    attackFrames.put(dir, frames);
                }

                InputStream portraitStream = getClass().getResourceAsStream(basePath + "portrait.png");
                portrait = (portraitStream != null) ? ImageIO.read(portraitStream) : null;
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!anyFrameLoaded) {
                buildFallbackSquare();
            } else {
                BufferedImage[] downFrames = walkFrames.get("down");
                spriteSheet = (downFrames != null && downFrames[1] != null) ? downFrames[1] : null;
                if (spriteSheet == null) buildFallbackSquare();
            }
            if (portrait == null) portrait = spriteSheet;
        }

        private BufferedImage[] loadFramesTryingSubpaths(String basePath, String[] candidateSubPaths, String dir, int frameCount) {
            for (String subPath : candidateSubPaths) {
                BufferedImage[] frames = new BufferedImage[frameCount + 1];
                boolean foundAny = false;
                for (int i = 1; i <= frameCount; i++) {
                    String fullPath = basePath + subPath + dir + "/" + i + ".png";
                    InputStream is = getClass().getResourceAsStream(fullPath);
                    if (is != null) {
                        try {
                            frames[i] = ImageIO.read(is);
                            foundAny = true;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (foundAny) return frames; // stop at the first subPath that actually produced data
            }
            return new BufferedImage[frameCount + 1]; // all-null array — caller's existing null checks handle this safely
        }

private void buildFallbackSquare() {
    spriteSheet = new BufferedImage(gp.tileSize, gp.tileSize, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = spriteSheet.createGraphics();
    g2.setColor(getClassColor());
    g2.fillRect(0, 0, gp.tileSize, gp.tileSize);
    g2.dispose();
    portrait = spriteSheet;
}

// You need to tell me the ACTUAL folder name for each hero class's assets —
// I don't have that information, so this is a placeholder mapping to fill in:
private String getAssetFolderForClass() {
    return switch (heroClass) {
        case WARRIOR -> "/player/vince/"; // <-- fill in your real warrior asset folder
        case MAGE -> "/player/triss/";
        case ARCHER -> "/player/???/";
        case CLERIC -> "/player/???/";
        case ROGUE -> "/player/???/";
        case PALADIN -> "/player/???/";
        case NECROMANCER -> "/player/???/";
        case DRUID -> "/player/???/";
    };
}

private String getAssetDirection(){
    return switch (direction) {
            case "up" -> "up";
            case "down" -> "down";
            case "left" -> "left";
            case "right" -> "right";
        default-> "up";};
}

    private Color getClassColor() {
        return switch (heroClass) {
            case WARRIOR -> Color.RED;
            case MAGE -> Color.BLUE;
            case ARCHER -> Color.GREEN;
            case CLERIC -> Color.YELLOW;
            case ROGUE -> Color.MAGENTA;
            case PALADIN -> Color.ORANGE;
            case NECROMANCER -> new Color(128, 0, 128);
            case DRUID -> new Color(34, 139, 34);
        };
    }

    // ===== MOVEMENT/COLLISION =====
    protected boolean canMoveTo(float nextX, float nextY) {
        int left = (int) Math.floor(nextX);
        int right = (int) Math.floor(nextX + gp.tileSize - 1);
        int top = (int) Math.floor(nextY);
        int bottom = (int) Math.floor(nextY + gp.tileSize - 1);

        boolean tileBlocked = gp.isTileBlocked(left, top)
            || gp.isTileBlocked(right, top)
            || gp.isTileBlocked(left, bottom)
            || gp.isTileBlocked(right, bottom);

        if (tileBlocked) return false;
        return !gp.isCollidingWithAnyEntity(nextX, nextY, this);
    }

    private void tryMove(float dx, float dy) {
        float nextX = x + dx;
        float nextY = y + dy;
        if (canMoveTo(nextX, y)) x = nextX;
        if (canMoveTo(x, nextY)) y = nextY;
    }

    private void updateDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            direction = dx < 0 ? "left" : "right";
        } else {
            direction = dy < 0 ? "up" : "down";
        }
    }

    private void animateWalk() {
        spriteCounter++;
        if (spriteCounter > 12) {
            spriteNumForWalk++;
            if (spriteNumForWalk > 9) spriteNumForWalk = 1;
            spriteCounter = 0;
        }
    }

    // ===== COMPANION AI =====
    public void updateCompanionAI() {
        if ( activePlayerReference == null) return;

        companionAiTick++;
        if (companionAiTick < 2) return;
        companionAiTick = 0;

        Entity player = activePlayerReference;
        float dx = player.x - x;
        float dy = player.y - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist > followDistance) {
            float moveX = (dx / dist) * speed;
            float moveY = (dy / dist) * speed;
            tryMove(moveX, moveY);
            updateDirection(moveX, moveY);
            animateWalk();
        } else {
            spriteFrame = 1;
        }

        if (companionRole.canFight) {
            autoAttackNearbyEnemies();
        }

        if (usePlayerAbilities && player != null) {
            maybeUsePlayerAbility(player);
        }
    }

    private void autoAttackNearbyEnemies() {
        for (Enemy enemy : gp.enemies) {
            if (enemy.dead) continue;
            float edx = enemy.x - x;
            float edy = enemy.y - y;
            float edist = (float) Math.sqrt(edx * edx + edy * edy);
            if (edist < gp.tileSize * 2) {
                attackEnemy(enemy);
                break;
            }
        }
    }

    private void attackEnemy(Enemy enemy) {
        Ability ability = unlockedAbilities.isEmpty() ? abilities.get(0) : unlockedAbilities.get(0);
        useAbility(ability, enemy);
    }

    private void maybeUsePlayerAbility(Entity player) {
        if (gp.random.nextInt(100) < 5) {
            // Only use abilities if player is a Hero
            if (player instanceof Hero hero) {
                if (!hero.unlockedAbilities.isEmpty()) {
                    Ability ability = hero.unlockedAbilities.get(gp.random.nextInt(hero.unlockedAbilities.size()));
                    Enemy target = findNearestEnemy();
                    if (target != null && mana >= ability.manaCost) {
                        useAbility(ability, target);
                    }
                }
            }
        }
    }

    private Enemy findNearestEnemy() {
        Enemy nearest = null;
        float shortest = Float.MAX_VALUE;
        for (Enemy enemy : gp.enemies) {
            if (enemy.dead) continue;
            float dx = enemy.x - x;
            float dy = enemy.y - y;
            float dist = dx * dx + dy * dy;
            if (dist < shortest) {
                shortest = dist;
                nearest = enemy;
            }
        }
        return nearest;
    }

    // ===== ABILITY SYSTEM =====
    public void useAbility(Ability ability, Entity target) {
        if (mana < ability.manaCost) return;
        if (ability.cooldown > 0) return;

        mana -= ability.manaCost;
        ability.cooldown = ability.maxCooldown;

         // Trigger attack animation for any ability that represents an offensive action
        if (ability.type != Ability.AbilityType.PASSIVE && ability.type != Ability.AbilityType.BUFF) {
            isAttacking = true;
            attackAnimationCounter = 0;
            attackAnimationFrame = 0;
            if (target != null) faceTarget(target);
        }

        switch (ability.type) {
            case BASIC_ATTACK, PROJECTILE, CRIT -> performAttack(target, ability);
            case AOE_DAMAGE -> performAoE(ability);
            case HEAL, AOE_HEAL -> performHeal(target, ability);
            case BUFF -> applyBuff(target, ability);
            case DEBUFF, CC, STUN -> applyDebuff(target, ability);
            case MOBILITY -> performMobility(ability);
            case SUMMON -> performSummon(ability);
            case DRAIN -> performDrain(target, ability);
            case DOT -> applyDot(target, ability);
            case TRANSFORM -> performTransform(ability);
            case TAUNT -> performTaunt(ability);
            case EXECUTE -> performExecute(target, ability);
            case PASSIVE -> {}
        }
    }

    private void faceTarget(Entity target) {
        float dx = target.x - x;
        float dy = target.y - y;
        if (dy < -0.707f * Math.abs(dx) && Math.abs(dx) < Math.abs(dy)) direction = "up";
        else if (dy > 0.707f * Math.abs(dx) && Math.abs(dx) < Math.abs(dy)) direction = "down";
        else if (dx < 0) direction = "left";
        else direction = "right";
        // Simplified 4-direction facing for attacks — swap in your existing 8-direction
        // dirX/dirY threshold logic from Enemy.update() if you want diagonal attack frames too.
    }

    private void performAttack(Entity target, Ability ability) {
        int dmg = calculateDamage(ability);
        if (target instanceof Enemy enemy) {
            enemy.health -= dmg;
            enemy.showHealthCounter = 60;
            spawnDamageNumber(enemy.x, enemy.y, dmg);
        } else if (target instanceof Hero hero) {
            hero.health = Math.max(0, hero.health - dmg);
        }
    }

    private void performAoE(Ability ability) {
        for (Enemy enemy : gp.enemies) {
            if (enemy.dead) continue;
            float dx = enemy.x - x;
            float dy = enemy.y - y;
            if (Math.sqrt(dx * dx + dy * dy) < gp.tileSize * 3) {
                enemy.health -= calculateDamage(ability);
                enemy.showHealthCounter = 60;
            }
        }
    }

    private void performHeal(Entity target, Ability ability) {
        int heal = ability.power + (level * 2);
        int maxHp = 0;
        int curHp = 0;
        if (target instanceof Hero hero) {
            maxHp = hero.maxHealth;
            curHp = hero.health;
        } else if (target instanceof Player player) {
            maxHp = player.maxHealth;
            curHp = player.health;
        } else if (target instanceof entity.Troop troop) {
            maxHp = troop.maxHealth;
            curHp = troop.health;
        } else if (target instanceof Enemy enemy) {
            maxHp = enemy.maxHealth;
            curHp = enemy.health;
        }
        if (maxHp > 0) {
            if (target instanceof Hero h) h.health = Math.min(h.maxHealth, h.health + heal);
            else if (target instanceof Player p) p.health = Math.min(p.maxHealth, p.health + heal);
            else if (target instanceof entity.Troop t) t.health = Math.min(t.maxHealth, t.health + heal);
            else if (target instanceof Enemy e) e.health = Math.min(e.maxHealth, e.health + heal);
            spawnDamageNumber(target.x, target.y, -heal, Color.GREEN);
        }
    }

    private void applyBuff(Entity target, Ability ability) {
        if (target instanceof Hero hero) {
            hero.attack += ability.power;
        }
    }

    private void applyDebuff(Entity target, Ability ability) {
        if (target instanceof Enemy enemy) {
            enemy.stunTimer = ability.duration;
        }
    }

    private void performMobility(Ability ability) {
        float dashDist = gp.tileSize * 4;
        float dx = 0, dy = 0;
        switch (direction) {
            case "up" -> dy = -dashDist;
            case "down" -> dy = dashDist;
            case "left" -> dx = -dashDist;
            case "right" -> dx = dashDist;
        }
        if (canMoveTo(x + dx, y + dy)) {
            x += dx;
            y += dy;
        }
    }

    private void performSummon(Ability ability) {
        // Summon minion logic
    }

    private void performDrain(Entity target, Ability ability) {
        int dmg = calculateDamage(ability);
        if (target instanceof Enemy enemy) {
            enemy.health -= dmg;
            health = Math.min(maxHealth, health + dmg / 2);
        }
    }

    private void applyDot(Entity target, Ability ability) {
        // Damage over time
    }

    private void performTransform(Ability ability) {
        // Shapeshift logic
    }

    private void performTaunt(Ability ability) {
        for (Enemy enemy : gp.enemies) {
            enemy.threatTable.addThreat(this, 1000);
        }
    }

    private void performExecute(Entity target, Ability ability) {
        if (target instanceof Enemy enemy && enemy.health < enemy.maxHealth * 0.3) {
            enemy.health = 0;
        }
    }

    private int calculateDamage(Ability ability) {
        int base = attack + ability.power;
        boolean crit = gp.random.nextInt(100) < critChance;
        return crit ? base * 2 : base;
    }

    private void spawnDamageNumber(float x, float y, int amount) {
        spawnDamageNumber(x, y, amount, Color.WHITE);
    }

    private void spawnDamageNumber(float x, float y, int amount, Color color) {
        // Add to damage number list for rendering
    }

    // ===== DIALOGUE SYSTEM =====
    public void addDialogueOption(DialogueOption option) {
        dialogueOptions.add(option);
    }

    public void clearDialogueOptions() {
        dialogueOptions.clear();
    }

    public void openDialogue() {
        clearDialogueOptions();

        if (activePlayerReference != null) {
            addDialogueOption(new DialogueOption("Talk", DialogueOption.DialogueType.CHAT, this));
            addDialogueOption(new DialogueOption("Switch Character", DialogueOption.DialogueType.SWITCH_CHARACTER, this));
            addDialogueOption(new DialogueOption("Recruit Troops", DialogueOption.DialogueType.RECRUIT_TROOPS, this));
            addDialogueOption(new DialogueOption("Kingdom Affairs", DialogueOption.DialogueType.KINGDOM_AFFAIRS, this));
            addDialogueOption(new DialogueOption("Dismiss", DialogueOption.DialogueType.DISMISS, this));
        } else if (!isRecruited) {
            addDialogueOption(new DialogueOption("Recruit " + name, DialogueOption.DialogueType.RECRUIT, this));
            addDialogueOption(new DialogueOption("Leave", DialogueOption.DialogueType.LEAVE, null));
        }
    }

    public String getRandomLine(String[] lines) {
        if (lines == null || lines.length == 0) return "...";
        return lines[gp.random.nextInt(lines.length)];
    }

    // ===== KINGDOM AFFAIRS =====
    public void openKingdomAffairs() {
            if (kingdomAffairs == null) {
                kingdomAffairs = new KingdomAffairs(this);
            }
            gp.setGameState(GamePanel.GameState.KINGDOM_AFFAIRS);
            kingdomAffairs.open();
        }

        // ===== UPDATE =====
        public void update() {
        // if (isActivePlayer) {
        //     updatePlayerControls();
        // } else {
             updateCompanionAI();
        // }

        if (isAttacking) {
            attackAnimationCounter++;
            if (attackAnimationCounter > 4) {
                attackAnimationFrame++;
                attackAnimationCounter = 0;
                if (attackAnimationFrame >= 7) { // match your actual frame count
                    attackAnimationFrame = 0;
                    isAttacking = false;
                }
            }
        }

        mana = Math.min(maxMana, mana + manaRegen);
        health = Math.min(maxHealth, health + 1);

        for (Ability a : abilities) {
            if (a.cooldown > 0) a.cooldown--;
        }

        interactionArea.setBounds((int) x - gp.tileSize, (int) y - gp.tileSize, gp.tileSize * 3, gp.tileSize * 3);
    }
    private void updatePlayerControls() {
        // Movement handled by KeyHandler in GamePanel
    }

    public void gainExperience(int exp) {
        experience += exp;
        while (experience >= experienceToNextLevel) {
            levelUp();
        }
    }

    private void levelUp() {
        level++;
        experience -= experienceToNextLevel;
        experienceToNextLevel = (int) (experienceToNextLevel * 1.5);

        maxHealth += 10;
        health = maxHealth;
        maxMana += 5;
        mana = maxMana;
        attack += 3;
        defense += 2;
        critChance += 1;

        if (level % 5 == 0 && abilities.size() > unlockedAbilities.size()) {
            unlockNextAbility();
        }
    }

    private void unlockNextAbility() {
        for (Ability a : abilities) {
            if (!unlockedAbilities.contains(a)) {
                unlockedAbilities.add(a);
                break;
            }
        }
    }

    public void equip(Equipment equipment) {
        switch (equipment.slot) {
            case WEAPON -> weapon = equipment;
            case ARMOR -> armor = equipment;
            case ACCESSORY -> accessory = equipment;
        }
        recalculateStats();
    }

    private void recalculateStats() {
        initializeBaseStats();
        if (weapon != null) applyEquipmentStats(weapon);
        if (armor != null) applyEquipmentStats(armor);
        if (accessory != null) applyEquipmentStats(accessory);
    }

    private void applyEquipmentStats(Equipment eq) {
        attack += eq.attackBonus;
        defense += eq.defenseBonus;
        maxHealth += eq.healthBonus;
        maxMana += eq.manaBonus;
        critChance += eq.critBonus;
    }

    public void draw(Graphics2D g2, int cameraX, int cameraY) {
        int drawWidth = (int) (gp.tileSize * (isAttacking ? attackDrawScale : idleDrawScale));
        int drawHeight = drawWidth; // square scaling — split into separate width/height fields if your art isn't square

        // Center the larger attack sprite on the hero's actual tile position, same technique as Enemy.draw()
        int screenX = (int) (x - cameraX) + (gp.tileSize - drawWidth) / 2;
        int screenY = (int) (y - cameraY) + (gp.tileSize - drawHeight) / 2;

        BufferedImage frame = getCurrentSpriteFrame();
        if (frame != null) {
            g2.drawImage(frame, screenX, screenY, drawWidth, drawHeight, null);
        }
        drawNameplate(g2, cameraX, cameraY);
    }

    private float getAttackDrawScale() {
        return switch (heroClass) {
            case WARRIOR, PALADIN -> 2.2f;   // melee classes read bigger during their swing
            case MAGE, CLERIC -> 1.5f;        // casters stay closer to idle size, effect carries the visual weight
            default -> 1.8f;
        };
    }
    private BufferedImage getCurrentSpriteFrame() {
        if (isAttacking) {
            BufferedImage[] frames = attackFrames.get(direction);
            if (frames != null && attackAnimationFrame < frames.length && frames[attackAnimationFrame] != null) {
                return frames[attackAnimationFrame];
            }
        }
        BufferedImage[] walk = walkFrames.get(direction);
        if (walk != null && spriteNumForWalk < walk.length && walk[spriteNumForWalk] != null) {
            return walk[spriteNumForWalk];
        }
        return spriteSheet;
		
    }

    private void drawNameplate(Graphics2D g2, int cameraX, int cameraY) {
        int screenX = (int) (x - cameraX);
        int screenY = (int) (y - cameraY) - 20;
        g2.setColor(isActivePlayer ? Color.CYAN : Color.WHITE);
        g2.drawString(name + " (" + heroClass.displayName + ")", screenX, screenY);
        g2.setColor(Color.RED);
        g2.fillRect(screenX, screenY + 5, 50, 5);
        g2.setColor(Color.GREEN);
        g2.fillRect(screenX, screenY + 5, (int) (50 * (health / (float) maxHealth)), 5);
    }

    // ===== ABILITY CLASSES =====
    public static class Ability {
        public String name;
        public String description;
        public int manaCost;
        public int maxCooldown;
        public int cooldown = 0;
        public int power = 10;
        public int duration = 0;
        public AbilityType type;

        public enum AbilityType {
            BASIC_ATTACK, PROJECTILE, AOE_DAMAGE, HEAL, AOE_HEAL,
            BUFF, DEBUFF, CC, STUN, MOBILITY, SUMMON, DRAIN,
            DOT, TRANSFORM, TAUNT, EXECUTE, PASSIVE, CRIT
        }

        public Ability(String name, String description, int manaCost, int cooldown, AbilityType type) {
            this.name = name;
            this.description = description;
            this.manaCost = manaCost;
            this.maxCooldown = cooldown;
            this.type = type;
        }
    }

    public static class Equipment {
        public String name;
        public Slot slot;
        public int attackBonus = 0;
        public int defenseBonus = 0;
        public int healthBonus = 0;
        public int manaBonus = 0;
        public int critBonus = 0;

        public enum Slot { WEAPON, ARMOR, ACCESSORY }

        public Equipment(String name, Slot slot) {
            this.name = name;
            this.slot = slot;
        }
    }

    public static class DialogueOption {
        public String text;
        public DialogueType type;
        public Hero targetHero;

        public enum DialogueType {
            RECRUIT, SWITCH_CHARACTER, RECRUIT_TROOPS, KINGDOM_AFFAIRS,
            CHAT, DISMISS, LEAVE
        }

        public DialogueOption(String text, DialogueType type, Hero targetHero) {
            this.text = text;
            this.type = type;
            this.targetHero = targetHero;
        }
    }

    public static class KingdomAffairs {
        private Hero advisor;
        public int treasury = 1000;
        public int armySize = 0;
        public int happiness = 75;
        public List<String> activeIssues = new ArrayList<>();
        public List<KingdomDecision> pendingDecisions = new ArrayList<>();

        public KingdomAffairs(Hero advisor) {
            this.advisor = advisor;
            generateInitialIssues();
        }

        private void generateInitialIssues() {
            activeIssues.add("Bandits reported on trade routes");
            activeIssues.add("Festival preparations needed");
            activeIssues.add("Border patrol requests reinforcements");
        }

        public void open() {
            if (advisor.gp.random.nextInt(100) < 30) {
                pendingDecisions.add(new KingdomDecision(
                    "Merchant Guild Petition",
                    "Guild requests tax reduction",
                    new String[]{"Reduce taxes (-200 gold, +10 happiness)", "Maintain taxes", "Increase taxes (+200 gold, -15 happiness)"}
                ));
            }
        }

        public void resolveDecision(int decisionIndex, int choiceIndex) {
            if (decisionIndex >= pendingDecisions.size()) return;
            KingdomDecision decision = pendingDecisions.get(decisionIndex);
            decision.resolve(choiceIndex, this);
            pendingDecisions.remove(decisionIndex);
        }

        public static class KingdomDecision {
            public String title;
            public String description;
            public String[] choices;

            public KingdomDecision(String title, String description, String[] choices) {
                this.title = title;
                this.description = description;
                this.choices = choices;
            }

            public void resolve(int choiceIndex, KingdomAffairs affairs) {
                if (choices.length > choiceIndex) {
                    String choice = choices[choiceIndex];
                    if (choice.contains("-200 gold")) {
                        affairs.treasury -= 200;
                        affairs.happiness += 10;
                    } else if (choice.contains("+200 gold")) {
                        affairs.treasury += 200;
                        affairs.happiness -= 15;
                    }
                }
            }
        }
    }
}