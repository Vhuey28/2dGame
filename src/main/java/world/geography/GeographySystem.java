package world.geography;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the geography state: provinces, settlements, roads, and route graph.
 * Provides accessors and spatial query helpers.
 */
public final class GeographySystem {
    private final Map<Long, Province> provinces = new HashMap<>();
    private final Map<Long, Settlement> settlements = new HashMap<>();
    private final Map<Long, Building> buildings = new HashMap<>();
    private final Map<Long, District> districts = new HashMap<>();
    private final RouteGraph routeGraph = new RouteGraph();
    private final WorldSpatialIndex spatialIndex = new WorldSpatialIndex(160.0);

    public void addProvince(Province province) {
        if (province == null) throw new IllegalArgumentException("Province cannot be null");
        provinces.put(province.id, province);
    }

    public Province getProvince(long provinceId) {
        return provinces.get(provinceId);
    }

    public java.util.Map<Long, Province> getProvinces() {
        return provinces;
    }

    public void addSettlement(Settlement settlement) {
        if (settlement == null) throw new IllegalArgumentException("Settlement cannot be null");
        settlements.put(settlement.id, settlement);
        spatialIndex.addSettlement(settlement);
    }

    public Settlement getSettlement(long settlementId) {
        return settlements.get(settlementId);
    }

    public java.util.Map<Long, Settlement> getSettlements() {
        return settlements;
    }

    public void addBuilding(Building building) {
        if (building == null) throw new IllegalArgumentException("Building cannot be null");
        buildings.put(building.id, building);
    }

    public Building getBuilding(long buildingId) {
        return buildings.get(buildingId);
    }

    public java.util.Map<Long, Building> getBuildings() {
        return buildings;
    }

    public void addDistrict(District district) {
        if (district == null) throw new IllegalArgumentException("District cannot be null");
        districts.put(district.id, district);
    }

    public District getDistrict(long districtId) {
        return districts.get(districtId);
    }

    public java.util.Map<Long, District> getDistricts() {
        return districts;
    }

    public RouteGraph getRouteGraph() {
        return routeGraph;
    }

    public WorldSpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    /** Get the total number of settlements. */
    public int getSettlementCount() {
        return settlements.size();
    }

    /** Get the total number of provinces. */
    public int getProvinceCount() {
        return provinces.size();
    }
}