package elite.intel.gameapi.search.spansh.outfitting;

import com.google.gson.annotations.SerializedName;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchCriteria.StationType;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.util.json.GsonFactory;
import elite.intel.util.json.ToJsonConvertible;

import java.util.*;

/**
 * Request criteria for Spansh station outfitting search.
 * Filters by specific module (canonical name, optional class, optional rating),
 * station types based on player trade profile, distance to arrival, and pad size.
 */
public class OutfittingStationSearchCriteria implements ToJsonConvertible {

    @SerializedName("filters")
    private Filters filters;

    @SerializedName("reference_system")
    private String referenceSystem;

    @SerializedName("sort")
    private List<SortOrder> sort = List.of(new SortOrder("distance", "asc"));

    @SerializedName("size")
    private int size = 5;

    @SerializedName("page")
    private int page = 0;

    public OutfittingStationSearchCriteria() {
    }

    public static OutfittingStationSearchCriteria create(
            MatchedModule module,
            String starSystem,
            TradeRouteSearchCriteria profile
    ) {
        OutfittingStationSearchCriteria criteria = new OutfittingStationSearchCriteria();
        criteria.referenceSystem = starSystem;

        Filters f = new Filters();

        // 1. Module filter
        ModuleFilter mf = new ModuleFilter(module.canonicalName());
        if (module.moduleClass() != null) {
            mf.setClazz(List.of(module.moduleClass()));
        }
        if (module.rating() != null) {
            mf.setRating(List.of(module.rating()));
        }
        f.modules = List.of(mf);

        // 2. Station types from profile
        List<String> types = new ArrayList<>(StationType.ORBITAL_TRADE_TYPES);
        if (profile == null) {
            // Default when no profile: ORBITAL + PLANETARY, no carrier
            types.addAll(StationType.PLANETARY_TRADE_TYPES);
        } else {
            if (profile.isAllowPlanetary()) {
                types.addAll(StationType.PLANETARY_TRADE_TYPES);
            }
            if (profile.isAllowFleetCarriers()) {
                types.addAll(StationType.CARRIER_TRADE_TYPES);
            }
            // 3. Arrival distance
            if (profile.getMaxLsFromArrival() > 0) {
                f.distanceToArrival = new RangeFilter(0, profile.getMaxLsFromArrival());
            }
            // 4. Large pad
            if (profile.isRequiresLargePad()) {
                f.largePads = new RangeFilter(1, 100);
            }
        }
        f.type = new StationTypeFilter(types);

        criteria.filters = f;
        return criteria;
    }

    public Filters getFilters() {
        return filters;
    }

    public String getReferenceSystem() {
        return referenceSystem;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public String toJson() {
        return GsonFactory.getGson().toJson(this);
    }

    public static class Filters {
        @SerializedName("modules")
        private List<ModuleFilter> modules;

        @SerializedName("type")
        private StationTypeFilter type;

        @SerializedName("distance_to_arrival")
        private RangeFilter distanceToArrival;

        @SerializedName("large_pads")
        private RangeFilter largePads;

        public List<ModuleFilter> getModules() {
            return modules;
        }

        public StationTypeFilter getType() {
            return type;
        }

        public RangeFilter getDistanceToArrival() {
            return distanceToArrival;
        }

        public RangeFilter getLargePads() {
            return largePads;
        }
    }

    public static class ModuleFilter {
        @SerializedName("name")
        private List<String> name;

        @SerializedName("class")
        private List<Integer> clazz;

        @SerializedName("rating")
        private List<String> rating;

        public ModuleFilter(String canonicalName) {
            this.name = List.of(canonicalName);
        }

        public List<String> getName() {
            return name;
        }

        public List<Integer> getClazz() {
            return clazz;
        }

        public void setClazz(List<Integer> clazz) {
            this.clazz = clazz;
        }

        public List<String> getRating() {
            return rating;
        }

        public void setRating(List<String> rating) {
            this.rating = rating;
        }
    }

    public static class StationTypeFilter {
        @SerializedName("value")
        private List<String> value;

        public StationTypeFilter(List<String> value) {
            this.value = value;
        }

        public List<String> getValue() {
            return value;
        }
    }

    public static class RangeFilter {
        @SerializedName("min")
        private Integer min;

        @SerializedName("max")
        private Integer max;

        public RangeFilter(Integer min, Integer max) {
            this.min = min;
            this.max = max;
        }

        public Integer getMin() {
            return min;
        }

        public Integer getMax() {
            return max;
        }
    }

    public static class SortOrder {
        @SerializedName("distance")
        private Direction distance;

        public SortOrder(String field, String dir) {
            this.distance = new Direction(dir);
        }

        public static class Direction {
            @SerializedName("direction")
            private String direction;

            public Direction(String direction) {
                this.direction = direction;
            }
        }
    }
}
