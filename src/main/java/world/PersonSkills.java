package world;

/**
 * Skill set for a person. Uses bounded integer ranges (0-100).
 * Skills improve through work, training, education, and experience.
 */
public final class PersonSkills {
    public int farming = 0;
    public int crafting = 0;
    public int trade = 0;
    public int medicine = 0;
    public int learning = 0;
    public int stewardship = 0;
    public int diplomacy = 0;
    public int intrigue = 0;
    public int martial = 0;
    public int leadership = 0;

    /** Get skill value by name (for flexibility). */
    public int get(String name) {
        return switch (name.toLowerCase()) {
            case "farming" -> farming;
            case "crafting" -> crafting;
            case "trade" -> trade;
            case "medicine" -> medicine;
            case "learning" -> learning;
            case "stewardship" -> stewardship;
            case "diplomacy" -> diplomacy;
            case "intrigue" -> intrigue;
            case "martial" -> martial;
            case "leadership" -> leadership;
            default -> 0;
        };
    }

    /** Set skill value by name, clamped to 0-100. */
    public void set(String name, int value) {
        int clamped = Math.max(0, Math.min(100, value));
        switch (name.toLowerCase()) {
            case "farming" -> farming = clamped;
            case "crafting" -> crafting = clamped;
            case "trade" -> trade = clamped;
            case "medicine" -> medicine = clamped;
            case "learning" -> learning = clamped;
            case "stewardship" -> stewardship = clamped;
            case "diplomacy" -> diplomacy = clamped;
            case "intrigue" -> intrigue = clamped;
            case "martial" -> martial = clamped;
            case "leadership" -> leadership = clamped;
        }
    }

    @Override
    public String toString() {
        return String.format("Skills[F:%d C:%d T:%d M:%d L:%d S:%d D:%d I:%d Ma:%d Le:%d]",
            farming, crafting, trade, medicine, learning, stewardship, diplomacy, intrigue, martial, leadership);
    }
}