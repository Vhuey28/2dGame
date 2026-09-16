package kingdom;

public class Territory {
    public String name;
    public int population = 1000;
    public int taxRate = 10;
    public int garrisonSize = 50;
    public BuildingLevel[] buildings = new BuildingLevel[BuildingType.values().length];

    public enum BuildingType { FARM, MARKET, BARRACKS, WALLS, KEEP }
    public enum BuildingLevel { NONE, LEVEL_1, LEVEL_2, LEVEL_3 }

    public int getTaxIncome() {
        return (population / 100) * taxRate / 10;
    }
}
