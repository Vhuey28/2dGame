package party;

import java.util.ArrayList;
import java.util.List;

import entity.Hero;
import entity.Troop;
import kingdom.Kingdom;

public class Party {
    public float x, y;
    public float speed = 2f;
    public List<Troop> troops = new ArrayList<>();
    public List<Hero> heroes = new ArrayList<>();
    public List<Troop> prisoners = new ArrayList<>();
    public int goldUpkeepPerMonth = 0;
    public int carriedGold = 0;
    public boolean isPlayerParty;
    public String factionName;
    public Kingdom faction;

    public enum State { ROAMING, FLEEING, PURSUING }
    public State state = State.ROAMING;

    public int getTotalStrength() {
        return troops.size() + heroes.size() * 3;
    }

    public boolean isDefeated() {
        return troops.isEmpty() && heroes.isEmpty();
    }
}
