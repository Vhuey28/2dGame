package entity;

import java.util.ArrayList;
import java.util.List;

import my2Dgame.GamePanel;

/**
 * Groups units (Enemy and/or Troop instances) under a Commander that
 * evaluates squad state each tick and issues orders. Works with your
 * existing Enemy/Troop classes via instanceof checks rather than requiring
 * you to retrofit a shared interface across your whole entity hierarchy —
 * pragmatic given your current class structure, though if you later add a
 * `Combatant` interface with getX()/getY()/getHealth() this class can be
 * simplified to use it directly instead of the instanceof helpers below.
 *
 * Usage (see README_AI_SYSTEMS.md for full wiring):
 *   Squad enemySquad = new Squad();
 *   enemySquad.addMember(someEnemy);
 *   enemySquad.setCommander(bossEnemy);
 *   // once per tick:
 *   enemySquad.updateCommanderAI(gp);
 *   // each unit checks its current order:
 *   Squad.Order myOrder = enemySquad.getCurrentOrder();
 */
public class Squad {

    public enum Order { HOLD, ADVANCE, RETREAT, FOCUS_FIRE, DEFEND_HEALERS, CALL_REINFORCEMENTS }

    public List<Object> members = new ArrayList<>();
    public Object commander; // an Enemy (boss) or Troop instance
    public Formation formation = new Formation(Formation.Type.WEDGE, 60f);

    private Order currentOrder = Order.ADVANCE;
    private Object focusFireTarget;
    private int reinforcementCooldown = 0;
    private static final int REINFORCEMENT_COOLDOWN_TICKS = 600; // ~10 seconds at 60fps, prevents spam

    // Tuning thresholds — adjust to taste
    private float retreatHealthRatioThreshold = 0.3f;   // squad avg health % that triggers retreat
    private float reinforceHealthRatioThreshold = 0.25f; // commander health % that triggers a reinforcement call
    private float focusFireTargetHealthRatio = 0.35f;    // target this low on health gets focus-fired

    public void addMember(Object unit) {
        members.add(unit);
    }

    public void removeMember(Object unit) {
        members.remove(unit);
    }

    public void setCommander(Object unit) {
        this.commander = unit;
        if (!members.contains(unit)) members.add(unit);
    }

    public Order getCurrentOrder() {
        return currentOrder;
    }

    public Object getFocusFireTarget() {
        return focusFireTarget;
    }

    /** Call once per game tick from GamePanel.update(). Evaluates squad state and updates currentOrder. */
    public void updateCommanderAI(GamePanel gp) {
        if (reinforcementCooldown > 0) reinforcementCooldown--;

        purgeDeadMembers();
        if (members.isEmpty()) return;

        float avgHealthRatio = averageHealthRatio();
        float commanderHealthRatio = commander != null ? healthRatio(commander) : 1f;

        // Priority order: retreat > reinforcements > defend healers > focus fire > advance
        if (avgHealthRatio <= retreatHealthRatioThreshold) {
            currentOrder = Order.RETREAT;
            return;
        }

        if (commander != null && commanderHealthRatio <= reinforceHealthRatioThreshold && reinforcementCooldown == 0) {
            currentOrder = Order.CALL_REINFORCEMENTS;
            callReinforcements(gp);
            reinforcementCooldown = REINFORCEMENT_COOLDOWN_TICKS;
            return;
        }

        Object healer = findHealer();
        if (healer != null && healthRatio(healer) < 0.5f) {
            currentOrder = Order.DEFEND_HEALERS;
            return;
        }

        Object lowHealthTarget = findLowestHealthEnemyTarget(gp);
        if (lowHealthTarget != null) {
            currentOrder = Order.FOCUS_FIRE;
            focusFireTarget = lowHealthTarget;
            return;
        }

        currentOrder = Order.ADVANCE;
    }

    // ---- Order-specific helpers ----

    /**
     * Spawns reinforcement units near the commander. Hook this up to your
     * existing enemy-creation code (e.g. the pattern used in
     * GamePanel.spawnEnemiesForMap) rather than duplicating stat-setup here —
     * this method just demonstrates the call pattern and adds the new units
     * to both GamePanel.enemies and this squad.
     */
    private void callReinforcements(GamePanel gp) {
        if (commander == null) return;
        float cx = getX(commander);
        float cy = getY(commander);

        int count = 3;
        for (int i = 0; i < count; i++) {
            Enemy reinforcement = new Enemy(gp);
            int sx = (int) cx + (i - 1) * gp.tileSize * 2;
            int sy = (int) cy + gp.tileSize * 2;
            java.awt.Point openPt = gp.findOpenSpawnSpace(sx, sy, reinforcement);
            reinforcement.x = openPt.x;
            reinforcement.y = openPt.y;
            reinforcement.setType(Enemy.Type.TROOP);
            gp.enemies.add(reinforcement);
            addMember(reinforcement);
        }
    }

    /** Finds a squadmate acting as a healer. Placeholder: your codebase doesn't have a Healer role yet — see note below. */
    private Object findHealer() {
        // TODO: once you add a healer role/type (e.g. Troop.Role.HEALER or Enemy.Type.HEALER),
        // filter `members` for it here. Returning null means "no healer to defend" for now.
        return null;
    }

    private Object findLowestHealthEnemyTarget(GamePanel gp) {
        Object lowest = null;
        float lowestRatio = focusFireTargetHealthRatio;
        // Enemies focus-firing the player has only one possible target; this
        // matters more for troops focus-firing the enemy roster.
        for (Enemy e : gp.enemies) {
            if (e.dead) continue;
            float ratio = (float) e.health / e.maxHealth;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                lowest = e;
            }
        }
        return lowest;
    }

    // ---- Generic accessors across Enemy/Troop via instanceof ----

    private float getX(Object unit) {
        if (unit instanceof Enemy) return ((Enemy) unit).x;
        if (unit instanceof Troop) return ((Troop) unit).x;
        return 0f;
    }

    private float getY(Object unit) {
        if (unit instanceof Enemy) return ((Enemy) unit).y;
        if (unit instanceof Troop) return ((Troop) unit).y;
        return 0f;
    }

    private boolean isDead(Object unit) {
        if (unit instanceof Enemy) return ((Enemy) unit).dead;
        if (unit instanceof Troop) return ((Troop) unit).health <= 0;
        return false;
    }

    private float healthRatio(Object unit) {
        if (unit instanceof Enemy) {
            Enemy e = (Enemy) unit;
            return e.maxHealth == 0 ? 0f : (float) e.health / e.maxHealth;
        }
        if (unit instanceof Troop) {
            Troop t = (Troop) unit;
            return t.maxHealth == 0 ? 0f : (float) t.health / t.maxHealth;
        }
        return 1f;
    }

    private float averageHealthRatio() {
        if (members.isEmpty()) return 1f;
        float sum = 0f;
        int count = 0;
        for (Object m : members) {
            if (isDead(m)) continue;
            sum += healthRatio(m);
            count++;
        }
        return count == 0 ? 0f : sum / count;
    }

    private void purgeDeadMembers() {
        members.removeIf(this::isDead);
        if (commander != null && isDead(commander)) {
            commander = null; // squad loses its commander; caller may want to promote a replacement
        }
    }
}
