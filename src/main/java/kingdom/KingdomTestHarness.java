package kingdom;

import java.util.Arrays;

public class KingdomTestHarness {
    public static void main(String[] args) {
        KingdomSimulation sim = buildTestScenario();

        int monthsToSimulate = 240;
        System.out.println("=== Simulating " + monthsToSimulate + " months (" + (monthsToSimulate / 12) + " years) ===\n");

        for (int i = 0; i < monthsToSimulate; i++) {
            sim.forceMonthlyTickForTesting();
        }

        System.out.println("=== Event Log ===");
        for (String event : sim.eventLog) {
            System.out.println(event);
        }

        System.out.println("\n=== Final State ===");
        for (Kingdom k : sim.allKingdoms) {
            System.out.println(k.name + ": treasury=" + k.treasury + ", stability=" + k.stability
                + ", ruler=" + (k.ruler != null ? k.ruler.name + " (age " + k.ruler.age + ")" : "NONE")
                + ", at war with " + k.activeWars.size() + " kingdom(s)");
        }
    }

    private static KingdomSimulation buildTestScenario() {
        KingdomSimulation sim = new KingdomSimulation();

        Kingdom aggressive = new Kingdom();
        aggressive.name = "Aggravia";
        aggressive.ruler = makeCharacter("King Vorn", 35, Character.Gender.MALE, Character.Trait.AMBITIOUS, Character.Trait.CRUEL);
        aggressive.territories.add(makeTerritory("Vorn's Hold", 5000, 200));

        Kingdom peaceful = new Kingdom();
        peaceful.name = "Serenholm";
        peaceful.ruler = makeCharacter("Queen Alys", 30, Character.Gender.FEMALE, Character.Trait.CONTENT, Character.Trait.KIND);
        peaceful.territories.add(makeTerritory("Alys' Reach", 2000, 60));

        Kingdom rival = new Kingdom();
        rival.name = "Ironmere";
        rival.ruler = makeCharacter("King Bran", 40, Character.Gender.MALE, Character.Trait.GENEROUS, Character.Trait.JUST);
        rival.territories.add(makeTerritory("Bran's Keep", 4500, 180));

        sim.allKingdoms.add(aggressive);
        sim.allKingdoms.add(peaceful);
        sim.allKingdoms.add(rival);
        sim.playerKingdom = peaceful;

        return sim;
    }

    private static Character makeCharacter(String name, int age, Character.Gender gender, Character.Trait... traits) {
        Character c = new Character();
        c.name = name;
        c.age = age;
        c.gender = gender;
        c.traits.addAll(Arrays.asList(traits));
        return c;
    }

    private static Territory makeTerritory(String name, int population, int garrison) {
        Territory t = new Territory();
        t.name = name;
        t.population = population;
        t.garrisonSize = garrison;
        return t;
    }
}
