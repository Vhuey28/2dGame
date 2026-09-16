package party;

import java.awt.Point;

import kingdom.Kingdom;
import my2Dgame.GamePanel;

public class AiPartyController {
    private static final float AWARENESS_RADIUS = 250f;
    private static final double FLEE_STRENGTH_RATIO = 0.6;

    private final Party party;
    private Point roamTarget;
    private int roamWaitTimer = 0;

    public AiPartyController(Party party) {
        this.party = party;
    }

    public void update(GamePanel gp, Party playerParty) {
        float dx = playerParty.x - party.x;
        float dy = playerParty.y - party.y;
        float distToPlayer = (float) Math.sqrt(dx * dx + dy * dy);

        updateState(distToPlayer, playerParty);

        switch (party.state) {
            case FLEEING -> moveAwayFrom(playerParty, gp);
            case PURSUING -> moveToward(playerParty, gp);
            case ROAMING -> roam(gp);
            default -> roam(gp);
        }
    }

    private void updateState(float distToPlayer, Party playerParty) {
        if (party.faction == null || playerParty.faction == null) {
            party.state = Party.State.ROAMING;
            return;
        }

        if (distToPlayer > AWARENESS_RADIUS) {
            party.state = Party.State.ROAMING;
            return;
        }

        boolean isHostile = party.faction.relations.get(playerParty.faction) == Kingdom.DiplomaticRelation.WAR;
        if (!isHostile) {
            party.state = Party.State.ROAMING;
            return;
        }

        double strengthRatio = playerParty.getTotalStrength() == 0 ? 1.0
            : (double) party.getTotalStrength() / playerParty.getTotalStrength();

        if (strengthRatio < FLEE_STRENGTH_RATIO) {
            party.state = Party.State.FLEEING;
        } else if (strengthRatio > 1.0 / FLEE_STRENGTH_RATIO) {
            party.state = Party.State.PURSUING;
        } else {
            party.state = Party.State.ROAMING;
        }
    }

    private void moveAwayFrom(Party target, GamePanel gp) {
        float dx = party.x - target.x;
        float dy = party.y - target.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) return;
        float fleeSpeedMultiplier = 1.3f;
        party.x += (dx / dist) * party.speed * fleeSpeedMultiplier;
        party.y += (dy / dist) * party.speed * fleeSpeedMultiplier;
    }

    private void moveToward(Party target, GamePanel gp) {
        float dx = target.x - party.x;
        float dy = target.y - party.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1f) return;
        party.x += (dx / dist) * party.speed;
        party.y += (dy / dist) * party.speed;
    }

    private void roam(GamePanel gp) {
        roamWaitTimer--;
        if (roamTarget == null || roamWaitTimer <= 0) {
            double angle = Math.random() * Math.PI * 2;
            float dist = 300 + (float) (Math.random() * 500);
            roamTarget = new Point(
                (int) (party.x + Math.cos(angle) * dist),
                (int) (party.y + Math.sin(angle) * dist)
            );
            roamWaitTimer = 300 + (int) (Math.random() * 300);
        }

        float dx = roamTarget.x - party.x;
        float dy = roamTarget.y - party.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 4f) {
            party.x += (dx / dist) * party.speed;
            party.y += (dy / dist) * party.speed;
        }
    }
}
