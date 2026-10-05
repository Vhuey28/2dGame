package world.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog of all good definitions.
 * In later phases, this can be loaded from config files, but for now
 * it provides the initial set as recommended in world.md section 12.1.
 */
public final class GoodsCatalog {
    // Initial 12 goods from world.md section 12.1
    public static final GoodDefinition GRAIN = new GoodDefinition(
            GoodType.GRAIN, "Grain", 5.0, 50.0, true, 14);
    public static final GoodDefinition VEGETABLES = new GoodDefinition(
            GoodType.VEGETABLES, "Vegetables", 8.0, 30.0, true, 7);
    public static final GoodDefinition MEAT = new GoodDefinition(
            GoodType.MEAT, "Meat", 15.0, 100.0, true, 3);
    public static final GoodDefinition TIMBER = new GoodDefinition(
            GoodType.TIMBER, "Timber", 3.0, 80.0, true, 21);
    public static final GoodDefinition STONE = new GoodDefinition(
            GoodType.STONE, "Stone", 2.0, 150.0, false, 0);
    public static final GoodDefinition IRON_ORE = new GoodDefinition(
            GoodType.IRON_ORE, "Iron Ore", 4.0, 60.0, false, 0);
    public static final GoodDefinition TOOLS = new GoodDefinition(
            GoodType.TOOLS, "Tools", 12.0, 20.0, false, 0);
    public static final GoodDefinition WEAPONS = new GoodDefinition(
            GoodType.WEAPONS, "Weapons", 25.0, 5.0, false, 0);
    public static final GoodDefinition ARMOR = new GoodDefinition(
            GoodType.ARMOR, "Armor", 35.0, 15.0, false, 0);
    public static final GoodDefinition CLOTH = new GoodDefinition(
            GoodType.CLOTH, "Cloth", 10.0, 15.0, false, 0);
    public static final GoodDefinition HORSES = new GoodDefinition(
            GoodType.HORSES, "Horses", 50.0, 100.0, false, 0);
    public static final GoodDefinition MEDICINE = new GoodDefinition(
            GoodType.MEDICINE, "Medicine", 20.0, 2.0, false, 0);

    /** All good definitions in order. */
    public static final List<GoodDefinition> ALL = new ArrayList<>();

    static {
        for (GoodDefinition gd : new GoodDefinition[]{
                GRAIN, VEGETABLES, MEAT, TIMBER, STONE,
                IRON_ORE, TOOLS, WEAPONS, ARMOR, CLOTH, HORSES, MEDICINE}) {
            ALL.add(gd);
        }
    }

    /** Get a GoodDefinition by GoodType. */
    public static GoodDefinition get(GoodType type) {
        for (GoodDefinition gd : ALL) {
            if (gd.type == type) return gd;
        }
        throw new IllegalArgumentException("Unknown good type: " + type);
    }

    /** Get base price for a good type. */
    public static double getBasePrice(GoodType type) {
        return get(type).basePrice;
    }

    /** Check if a good type is perishable. */
    public static boolean isPerishable(GoodType type) {
        return get(type).perishable;
    }
}