package kingdom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Character {
    public String name;
    public int age;
    public boolean alive = true;
    public Gender gender;
    public enum Gender { MALE, FEMALE }

    public int diplomacy = 5, martial = 5, stewardship = 5, intrigue = 5, learning = 5;
    public List<Trait> traits = new ArrayList<>();

    public Kingdom liege;
    public Kingdom rulesKingdom;
    public Character spouse;
    public List<Character> children = new ArrayList<>();
    public Character father, mother;

    public Map<Character, Integer> opinions = new HashMap<>();
    public List<Kingdom> claims = new ArrayList<>();

    public enum Trait { AMBITIOUS, CONTENT, CRUEL, KIND, BRAVE, CRAVEN, GENEROUS, GREEDY, JUST, ARBITRARY }

    public int getOpinionOf(Character other) {
        return opinions.getOrDefault(other, 0);
    }

    public void adjustOpinion(Character other, int amount) {
        opinions.merge(other, amount, Integer::sum);
    }

    public int getTraitWarModifier() {
        int mod = 0;
        if (traits.contains(Trait.AMBITIOUS)) mod += 20;
        if (traits.contains(Trait.CONTENT)) mod -= 20;
        if (traits.contains(Trait.CRUEL)) mod += 10;
        if (traits.contains(Trait.KIND)) mod -= 10;
        if (traits.contains(Trait.CRAVEN)) mod -= 25;
        if (traits.contains(Trait.BRAVE)) mod += 10;
        return mod;
    }

    public int getTraitAllianceModifier() {
        int mod = 0;
        if (traits.contains(Trait.GENEROUS)) mod += 15;
        if (traits.contains(Trait.GREEDY)) mod -= 15;
        if (traits.contains(Trait.JUST)) mod += 10;
        if (traits.contains(Trait.ARBITRARY)) mod -= 10;
        return mod;
    }
}
