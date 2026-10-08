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
        WARRIOR("Warrior", "Melee tank with high defense", 120, 100, 20, 70),
        MAGE("Mage", "Ranged spellcaster with high magic damage", 70, 10, 5, 200),
        ARCHER("Archer", "Ranged physical damage dealer", 80, 15, 8, 20),
        CLERIC("Cleric", "Support with healing and buffs", 90, 12, 6, 50),
        ROGUE("Rogue", "High crit/dodge melee DPS", 75, 18, 15, 10),
        PALADIN("Paladin", "Holy warrior with defensive auras", 110, 16, 8, 30),
        NECROMANCER("Necromancer", "Summons undead minions", 65, 8, 4, 90),
        DRUID("Druid", "Hybrid shapeshifter/nature magic", 85, 14, 7, 60),
        ADVENTURER("Adventurer", "The original player character", 100, 12, 5, 100);

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
    /** Canonical campaign person represented by this local hero, when applicable. */
    public Long sourcePersonId;
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
    public int abilityAuraTicks = 0;
    public int abilityDamageBonus = 0;
    public int abilityDefenseBonus = 0;
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
    private int spriteNumForWalk = 0;

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
            case WARRIOR :
                abilities.add(new Ability("Flame Wave", "Launch a damaging wave in the facing direction", 15, 90, Ability.AbilityType.WARRIOR_WAVE));
                abilities.add(new Ability("War Banner", "Create an aura that boosts allied damage and defense", 25, 240, Ability.AbilityType.WARRIOR_AURA));
                abilities.add(new Ability("Gravity Pull", "Pull nearby enemies into the center", 30, 210, Ability.AbilityType.WARRIOR_PULL));
               break;

            case MAGE :
                abilities.add(new Ability("Violet Portal", "Choose a portal destination with the mouse", 20, 180, Ability.AbilityType.MAGE_PORTAL));
                abilities.add(new Ability("Arcane Storm", "Summon a storm that rains damage", 30, 240, Ability.AbilityType.MAGE_STORM));
                abilities.add(new Ability("Ricochet Orb", "Launch an orb that bounces between enemies for 10 seconds", 35, 300, Ability.AbilityType.MAGE_ORB));
                break;

            case ARCHER : 
                abilities.add(new Ability("Multi-Shot", "Fires 3 arrows in spread", 15, 25, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Piercing Arrow", "Passes through enemies", 20, 30, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Rain of Arrows", "AoE arrow barrage", 35, 50, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Evasive Roll", "Dash with iframe", 10, 20, Ability.AbilityType.MOBILITY));
               break;

            case CLERIC : 
                abilities.add(new Ability("Heal", "Restores health to ally", 20, 30, Ability.AbilityType.HEAL));
                abilities.add(new Ability("Blessing", "Increases ally attack/defense", 25, 45, Ability.AbilityType.BUFF));
                abilities.add(new Ability("Sanctuary", "AoE heal over time", 35, 60, Ability.AbilityType.AOE_HEAL));
                abilities.add(new Ability("Smite", "Holy damage to undead", 20, 25, Ability.AbilityType.PROJECTILE));
                 break;

            case ROGUE : 
                abilities.add(new Ability("Backstab", "High damage from behind", 15, 20, Ability.AbilityType.CRIT));
                abilities.add(new Ability("Smoke Bomb", "Blind enemies in area", 20, 40, Ability.AbilityType.DEBUFF));
                abilities.add(new Ability("Shadow Step", "Teleport behind target", 15, 30, Ability.AbilityType.MOBILITY));
                abilities.add(new Ability("Poison Blade", "Applies DoT", 10, 15, Ability.AbilityType.DOT));
               break;
            case PALADIN : 
                abilities.add(new Ability("Holy Strike", "Bonus damage to evil", 20, 25, Ability.AbilityType.CRIT));
                abilities.add(new Ability("Divine Shield", "Block next 3 hits", 25, 50, Ability.AbilityType.BUFF));
                abilities.add(new Ability("Consecration", "AoE damage + ally heal", 30, 60, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Judgment", "Execute low-health enemies", 35, 80, Ability.AbilityType.EXECUTE));
               break;
            case NECROMANCER : 
                abilities.add(new Ability("Raise Skeleton", "Summons minion", 30, 60, Ability.AbilityType.SUMMON));
                abilities.add(new Ability("Life Drain", "Heal self, damage enemy", 20, 30, Ability.AbilityType.DRAIN));
                abilities.add(new Ability("Bone Prison", "Roots enemies", 25, 45, Ability.AbilityType.CC));
                abilities.add(new Ability("Death Nova", "AoE on minion death", 15, 20, Ability.AbilityType.PASSIVE));
               break;
            case DRUID : 
                abilities.add(new Ability("Shape: Bear", "Become tank form", 20, 40, Ability.AbilityType.TRANSFORM));
                abilities.add(new Ability("Shape: Cat", "Become DPS form", 15, 30, Ability.AbilityType.TRANSFORM));
                abilities.add(new Ability("Entangling Roots", "Root enemies", 20, 35, Ability.AbilityType.CC));
                abilities.add(new Ability("Wild Growth", "Heal over time AoE", 30, 50, Ability.AbilityType.AOE_HEAL));
               break;
            case ADVENTURER:
                abilities.add(new Ability("Fire Bolt", "Launch a fire projectile", 15, 60, Ability.AbilityType.PROJECTILE));
                abilities.add(new Ability("Conqueror Field", "Damage and stun nearby enemies", 25, 120, Ability.AbilityType.AOE_DAMAGE));
                abilities.add(new Ability("Healing Field", "Restore nearby allies", 20, 180, Ability.AbilityType.AOE_HEAL));
                break;
        }

        // Hero hotkeys 1/2/3 map directly to the first three class abilities.
        for (int i = 1; i < abilities.size() && i <= 3; i++) {
            unlockedAbilities.add(abilities.get(i));
        }
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
        String[] directions = {"up", "down", "left", "right"};
        String basePath = getAssetFolderForClass();
        String primaryAttack = heroClass == HeroClass.MAGE
                ? "custom/slash_oversize/"
                : "standard/slash/";
        String fallbackAttack = heroClass == HeroClass.MAGE
                ?  "custom/slash_oversize/"
                :"standard/slash/";

        if(heroClass == HeroClass.WARRIOR){          
            primaryAttack = heroClass == HeroClass.WARRIOR
                ? "custom/slash_oversize/"
                : "standard/slash/";
            fallbackAttack = heroClass == HeroClass.WARRIOR
                ?  "custom/slash_oversize/"
                :"standard/slash/";    
        } else if(heroClass == HeroClass.ADVENTURER){
            primaryAttack = heroClass == HeroClass.ADVENTURER
                ? "custom/slash_oversize/"
                : "standard/slash/";
            fallbackAttack = heroClass == HeroClass.ADVENTURER
                ?  "custom/slash_oversize/"
                :"standard/slash/"; 
        }
        for (String facing : directions) {
            BufferedImage[] walking = loadSequentialFrames(basePath, "standard/walk/", facing);
            if (walking.length > 0) walkFrames.put(facing, walking);

            BufferedImage[] attacking = loadSequentialFrames(basePath, primaryAttack, facing);
            if (attacking.length == 0) {
                attacking = loadSequentialFrames(basePath, fallbackAttack, facing);
            }
            if (attacking.length > 0) attackFrames.put(facing, attacking);
        }

        portrait = loadOptionalImage(basePath + "portrait.png");
        BufferedImage[] downFrames = walkFrames.get("down");
        spriteSheet = downFrames != null && downFrames.length > 0 ? downFrames[0] : null;
        if (spriteSheet == null) buildFallbackSquare();
        if (portrait == null) portrait = spriteSheet;
    }

    /** Loads numbered animation PNGs into zero-based playback slots. */
    private BufferedImage[] loadSequentialFrames(String basePath, String actionPath, String facing) {
        java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        for (int sourceFrame = 1; ; sourceFrame++) {
            BufferedImage image = loadOptionalImage(
                    basePath + actionPath + facing + "/" + sourceFrame + ".png");
            if (image == null) break;
            frames.add(image);
        }
        return frames.toArray(new BufferedImage[0]);
    }

    private BufferedImage loadOptionalImage(String path) {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException e) {
            System.err.println("Unable to load hero sprite " + path + ": " + e.getMessage());
            return null;
        }
    }

private void buildFallbackSquare() {
    spriteSheet = new BufferedImage(gp.tileSize, gp.tileSize, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = spriteSheet.createGraphics();
    g2.setColor(getClassColor());
    g2.fillRect(0, 0, gp.tileSize, gp.tileSize);
    g2.dispose();
    portrait = spriteSheet;
}

private String getAssetFolderForClass() {
    switch (heroClass) {
        case MAGE:
        case NECROMANCER:
            return "/player/triss/";
        case ADVENTURER:
            return "/player/sword_animations/";
        case WARRIOR:
        case ARCHER:
        case CLERIC:
        case ROGUE:
        case PALADIN:
        case DRUID:
        default:
            return "/player/vince/";
    }
}

private String getAssetDirection() {
    if ("down".equals(direction) || "downLeft".equals(direction) || "downRight".equals(direction)) {
        return "down";
    }
    if ("left".equals(direction)) return "left";
    if ("right".equals(direction)) return "right";
    return "up";
}

    private Color getClassColor() {
         Color C=new Color(0, 0, 255);
        switch (heroClass) {
            case WARRIOR : 
                C=new Color(0, 0, 255);
                break;

            case MAGE : 
               C = new Color(255, 0, 0);
                break;

            case ARCHER :
                C= new Color(34, 139, 34);
                break;

            case CLERIC :  
                C= new Color(255, 255, 0);
                break;

            case ROGUE : 
                C = new Color(255, 0, 255);
                break;

            case PALADIN :
                C = new Color(255, 165, 0);
                break;
            case NECROMANCER :
                C= new Color(128, 0, 128);
            case DRUID :C= new Color(34, 139, 34);
        };
        return C;
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
        BufferedImage[] frames = walkFrames.get(getAssetDirection());
        if (frames == null || frames.length == 0) return;
        spriteCounter++;
        if (spriteCounter > 12) {
            spriteNumForWalk = (spriteNumForWalk + 1) % frames.length;
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
            if (player instanceof Hero) {
                Hero hero = (Hero) player;
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
            case BASIC_ATTACK:  performAttack(target, ability);break;
            case PROJECTILE:  performAttack(target, ability);break;
            case CRIT : performAttack(target, ability);break;
            case AOE_DAMAGE : performAoE(ability);break;
            case HEAL: performHeal(target, ability);break;
            case AOE_HEAL : performHeal(target, ability);break;
            case BUFF : applyBuff(target, ability);break;
            case DEBUFF:applyDebuff(target, ability);break;
            case CC: applyDebuff(target, ability);break;
            case STUN : applyDebuff(target, ability);break;
            case MOBILITY : performMobility(ability);break;
            case SUMMON : performSummon(ability);break;
            case DRAIN : performDrain(target, ability);break;
            case DOT : applyDot(target, ability);break;
            case TRANSFORM : performTransform(ability);break;
            case TAUNT : performTaunt(ability);break;
            case EXECUTE : performExecute(target, ability);break;
            case WARRIOR_WAVE : gp.spawnHeroAbilityEffect(HeroAbilityEffect.warriorWave(gp, this));break;
            case WARRIOR_AURA : gp.spawnHeroAbilityEffect(HeroAbilityEffect.warriorAura(gp, this));break;
            case WARRIOR_PULL : gp.spawnHeroAbilityEffect(HeroAbilityEffect.warriorPull(gp, this));break;
            case MAGE_PORTAL : { if (isActivePlayer) gp.beginMagePortalTargeting(this); }break;
            case MAGE_STORM : gp.spawnHeroAbilityEffect(HeroAbilityEffect.mageStorm(gp, this, target));break;
            case MAGE_ORB : gp.spawnHeroAbilityEffect(HeroAbilityEffect.mageOrb(gp, this, target));break;
            case PASSIVE : {};break;
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
        if (target instanceof Enemy) {
            Enemy enemy = (Enemy) target;
            enemy.health -= dmg;
            enemy.showHealthCounter = 60;
            spawnDamageNumber(enemy.x, enemy.y, dmg);
        } else if (target instanceof Hero) {
            Hero hero =(Hero) target;
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
        if (target instanceof Hero) {
            Hero hero =(Hero) target;
            maxHp = hero.maxHealth;
            curHp = hero.health;
        } else if (target instanceof Player) {
            Player player =(Player) target;
            maxHp = player.maxHealth;
            curHp = player.health;
        } else if (target instanceof entity.Troop) {
            Troop troop = (Troop) target;
            maxHp = troop.maxHealth;
            curHp = troop.health;
        } else if (target instanceof Enemy) {
            Enemy enemy = (Enemy) target;
            maxHp = enemy.maxHealth;
            curHp = enemy.health;
        }
        if (maxHp > 0) {
            if (target instanceof Hero) {
                Hero h =(Hero) target;
                h.health = Math.min(h.maxHealth, h.health + heal);
            }else if (target instanceof Player){
                Player p = (Player)target;
             p.health = Math.min(p.maxHealth, p.health + heal);
            }else if (target instanceof entity.Troop){
                Troop t = (Troop)target;
                 t.health = Math.min(t.maxHealth, t.health + heal);
            }else if (target instanceof Enemy){
                Enemy e=(Enemy)target;
                 e.health = Math.min(e.maxHealth, e.health + heal);
            }
            spawnDamageNumber(target.x, target.y, -heal, Color.GREEN);
        }
    }

    private void applyBuff(Entity target, Ability ability) {
        if (target instanceof Hero) {
            Hero hero =(Hero) target;
            hero.attack += ability.power;
        }
    }

    private void applyDebuff(Entity target, Ability ability) {
        if (target instanceof Enemy) {
            Enemy enemy = (Enemy) target;
            enemy.stunTimer = ability.duration;
        }
    }

    private void performMobility(Ability ability) {
        float dashDist = gp.tileSize * 4;
        float dx = 0, dy = 0;
        switch (direction) {
            case "up" : dy = -dashDist;
            case "down" : dy = dashDist;
            case "left" : dx = -dashDist;
            case "right" : dx = dashDist;
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
        if (target instanceof Enemy) {
            Enemy enemy = (Enemy) target;
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
       Enemy enemy = (Enemy) target;
        if (target instanceof Enemy && enemy.health < enemy.maxHealth * 0.3) {
            enemy.health = 0;
        }
    }

    private int calculateDamage(Ability ability) {
        int base = attack + ability.power + (abilityAuraTicks > 0 ? abilityDamageBonus : 0);
        boolean crit = gp.random.nextInt(100) < critChance;
        return crit ? base * 2 : base;
    }

    public int abilityDamage(int power) {
        return Math.max(1, attack + power + (abilityAuraTicks > 0 ? abilityDamageBonus : 0));
    }

    public void applyAbilityAura(int ticks, int damageBonus, int defenseBonus) {
        abilityAuraTicks = Math.max(abilityAuraTicks, ticks);
        abilityDamageBonus = Math.max(abilityDamageBonus, damageBonus);
        abilityDefenseBonus = Math.max(abilityDefenseBonus, defenseBonus);
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
        if (abilityAuraTicks > 0 && --abilityAuraTicks == 0) {
            abilityDamageBonus = 0;
            abilityDefenseBonus = 0;
        }
        // The active hero is driven by GamePanel's player-control proxy. Other
        // recruited heroes remain autonomous companions.
        if (!isActivePlayer) updateCompanionAI();
        else animateWalk();

        if (isAttacking && !isActivePlayer) {
            attackAnimationCounter++;
            if (attackAnimationCounter > 4) {
                attackAnimationFrame++;
                attackAnimationCounter = 0;
                BufferedImage[] frames = attackFrames.get(getAssetDirection());
                int frameCount = frames == null ? 0 : frames.length;
                if (frameCount == 0 || attackAnimationFrame >= frameCount) {
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

    /** Keeps the selected hero's class animation aligned with the proxy Player attack. */
    public void syncControlledAttack(boolean attacking, int sourceFrame, int sourceFrameCount) {
        isAttacking = attacking;
        if (!attacking) {
            attackAnimationFrame = 0;
            return;
        }
        BufferedImage[] frames = attackFrames.get(getAssetDirection());
        if (frames == null || frames.length == 0) {
            attackAnimationFrame = 0;
            return;
        }
        int sourceLast = Math.max(1, sourceFrameCount - 1);
        float progress = Math.max(0, Math.min(sourceFrame, sourceLast)) / (float) sourceLast;
        attackAnimationFrame = Math.min(frames.length - 1,
                Math.round(progress * (frames.length - 1)));
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

        if (level % 5 == 0 && abilities.size() - 1 > unlockedAbilities.size()) {
            unlockNextAbility();
        }
    }

    private void unlockNextAbility() {
        for (Ability a : abilities) {
            if (a.type != Ability.AbilityType.BASIC_ATTACK && !unlockedAbilities.contains(a)) {
                unlockedAbilities.add(a);
                break;
            }
        }
    }

    public void equip(Equipment equipment) {
        switch (equipment.slot) {
            case WEAPON : weapon = equipment;
            case ARMOR : armor = equipment;
            case ACCESSORY : accessory = equipment;
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
      float s=1.8f;
        switch (heroClass) {
            case WARRIOR:
                s= 2.2f;   // melee classes read bigger during their swing
                break;    
            case PALADIN :
                s= 2.2f;   // melee classes read bigger during their swing
                break;
            case MAGE:
                s= 1.5f;        // casters stay closer to idle size, effect carries the visual weight
                break;    
            case CLERIC :
                s= 1.5f;        // casters stay closer to idle size, effect carries the visual weight
                break;
            default : s= 1.8f;
        };
        return s;
    }
    private BufferedImage getCurrentSpriteFrame() {
        String facing = getAssetDirection();
        if (isAttacking) {
            BufferedImage[] frames = attackFrames.get(facing);
            if (frames != null && frames.length > 0) {
                return frames[Math.floorMod(attackAnimationFrame, frames.length)];
            }
        }
        BufferedImage[] walk = walkFrames.get(facing);
        if (walk != null && walk.length > 0) {
            return walk[Math.floorMod(spriteNumForWalk, walk.length)];
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
            DOT, TRANSFORM, TAUNT, EXECUTE, PASSIVE, CRIT,
            WARRIOR_WAVE, WARRIOR_AURA, WARRIOR_PULL,
            MAGE_PORTAL, MAGE_STORM, MAGE_ORB
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