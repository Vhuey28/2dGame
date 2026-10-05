package world.geography;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import world.Army;
import world.Caravan;
import world.WorldState;

/** Grid index for strategic proximity queries; persistent entities remain canonical in WorldState. */
public final class WorldSpatialIndex {
    private final double cellSize;
    private final Map<Cell, List<Long>> settlements = new HashMap<>();
    private final Map<Cell, List<Long>> armies = new HashMap<>();
    private final Map<Cell, List<Long>> caravans = new HashMap<>();
    private long rebuildCount;

    public WorldSpatialIndex(double cellSize) {
        if (cellSize <= 0.0) throw new IllegalArgumentException("Cell size must be positive");
        this.cellSize = cellSize;
    }

    public void addSettlement(Settlement settlement) {
        if (settlement != null && settlement.position != null) add(settlements, settlement.id,
                settlement.position.x, settlement.position.y);
    }

    public void rebuildSettlements(Iterable<Settlement> values) {
        settlements.clear();
        for (Settlement settlement : values) addSettlement(settlement);
    }

    public void rebuildDynamic(WorldState world) {
        armies.clear();
        caravans.clear();
        for (Army army : world.armies.values()) {
            if (army.position != null && army.state != Army.ArmyState.DISBANDED) {
                add(armies, army.id, army.position.x, army.position.y);
            }
        }
        for (Caravan caravan : world.caravans.values()) {
            if (caravan.position != null && caravan.state != Caravan.CaravanState.DESTROYED) {
                add(caravans, caravan.id, caravan.position.x, caravan.position.y);
            }
        }
        rebuildCount++;
    }

    public List<Long> querySettlementIds(double x, double y, double radius) {
        return query(settlements, x, y, radius);
    }

    public List<Long> queryArmyIds(double x, double y, double radius) {
        return query(armies, x, y, radius);
    }

    public List<Long> queryCaravanIds(double x, double y, double radius) {
        return query(caravans, x, y, radius);
    }

    public long getRebuildCount() { return rebuildCount; }
    public double getCellSize() { return cellSize; }

    private void add(Map<Cell, List<Long>> buckets, long id, double x, double y) {
        buckets.computeIfAbsent(cell(x, y), ignored -> new ArrayList<>()).add(id);
    }

    private List<Long> query(Map<Cell, List<Long>> buckets, double x, double y, double radius) {
        int minX = coordinate(x - radius);
        int maxX = coordinate(x + radius);
        int minY = coordinate(y - radius);
        int maxY = coordinate(y + radius);
        Set<Long> result = new LinkedHashSet<>();
        for (int cellX = minX; cellX <= maxX; cellX++) {
            for (int cellY = minY; cellY <= maxY; cellY++) {
                result.addAll(buckets.getOrDefault(new Cell(cellX, cellY), List.of()));
            }
        }
        return new ArrayList<>(result);
    }

    private Cell cell(double x, double y) { return new Cell(coordinate(x), coordinate(y)); }
    private int coordinate(double value) { return (int) Math.floor(value / cellSize); }

    private static final class Cell {
        final int x;
        final int y;
        Cell(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell)) return false;
            Cell cell = (Cell) other;
            return x == cell.x && y == cell.y;
        }
        @Override public int hashCode() { return 31 * x + y; }
    }
}
