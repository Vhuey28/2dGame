package party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import kingdom.Kingdom;
import my2Dgame.GamePanel;

public class PartyManager {
    public List<Party> aiParties = new ArrayList<>();
    private final Map<Party, AiPartyController> controllers = new HashMap<>();

    private int spawnTimer = 0;
    private static final int SPAWN_INTERVAL_TICKS = 1800;
    private static final int MAX_AI_PARTIES = 12;

    public void update(GamePanel gp, List<Kingdom> allKingdoms, Party playerParty) {
        spawnTimer++;
        if (spawnTimer >= SPAWN_INTERVAL_TICKS && aiParties.size() < MAX_AI_PARTIES) {
            spawnTimer = 0;
            trySpawnParty(allKingdoms, gp);
        }

        Iterator<Party> it = aiParties.iterator();
        while (it.hasNext()) {
            Party p = it.next();
            if (p.isDefeated()) {
                controllers.remove(p);
                it.remove();
                continue;
            }
            AiPartyController controller = controllers.get(p);
            if (controller == null) {
                controller = new AiPartyController(p);
                controllers.put(p, controller);
            }
            controller.update(gp, playerParty);
        }
    }

    private void trySpawnParty(List<Kingdom> allKingdoms, GamePanel gp) {
        if (allKingdoms.isEmpty()) return;
        Kingdom faction = allKingdoms.get((int) (Math.random() * allKingdoms.size()));

        Party party = new Party();
        party.faction = faction;
        party.isPlayerParty = false;
        party.x = (float) (Math.random() * gp.worldWidth);
        party.y = (float) (Math.random() * gp.worldHeight);

        int troopCount = 3 + (int) (Math.random() * 8);
        for (int i = 0; i < troopCount; i++) {
            entity.Troop.Role role = Math.random() < 0.3 ? entity.Troop.Role.ARCHER : entity.Troop.Role.MELEE;
            entity.Troop t = new entity.Troop(gp, party.x, party.y, role);
            party.troops.add(t);
        }

        aiParties.add(party);
        controllers.put(party, new AiPartyController(party));
    }
}
