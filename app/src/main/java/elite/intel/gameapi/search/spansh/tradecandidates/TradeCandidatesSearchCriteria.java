package elite.intel.gameapi.search.spansh.tradecandidates;

import com.google.gson.annotations.SerializedName;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchCriteria.StationType;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.util.json.GsonFactory;
import elite.intel.util.json.ToJsonConvertible;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Request criteria for Spansh station search tailored for trade candidate recommendations.
 * Filters stations that offer commodity Market service, within search radius from reference system,
 * updated within the past 10 hours, respecting commander's trade profile (pad size, planetary, carrier, arrival distance).
 */
public class TradeCandidatesSearchCriteria implements ToJsonConvertible {

    public static final String MARKET_SERVICE = "Market";
    public static final int FRESH_HOURS = 10;
    public static final int DEFAULT_SIZE = 20;

    @SerializedName("filters")
    private Filters filters;

    @SerializedName("reference_system")
    private String referenceSystem;

    @SerializedName("sort")
    private List<SortOrder> sort = List.of(new SortOrder("distance", "asc"));

    @SerializedName("size")
    private int size = DEFAULT_SIZE;

    @SerializedName("page")
    private int page = 0;

    public TradeCandidatesSearchCriteria() {
    }

    public static TradeCandidatesSearchCriteria create(
            String starSystem,
            int radiusLy,
            TradeRouteSearchCriteria profile,
            Instant now
    ) {
        TradeCandidatesSearchCriteria criteria = new TradeCandidatesSearchCriteria();
        criteria.referenceSystem = starSystem;

        Filters f = new Filters();

        // 1. Required service: Market
        f.services = List.of(new Service(List.of(MARKET_SERVICE)));

        // 2. Search distance radius
        f.distance = new Distance(0, radiusLy);

        // 3. Station types based on profile
        List<String> types = new ArrayList<>(StationType.ORBITAL_TRADE_TYPES);
        if (profile != null) {
            if (profile.isAllowPlanetary()) {
                types.addAll(StationType.PLANETARY_TRADE_TYPES);
            }
            if (profile.isAllowFleetCarriers()) {
                types.addAll(StationType.CARRIER_TRADE_TYPES);
            }
            // 4. Arrival distance
            if (profile.getMaxLsFromArrival() > 0) {
                f.distanceToArrival = new RangeFilter(0, profile.getMaxLsFromArrival());
            }
            // 5. Large pad
            if (profile.isRequiresLargePad()) {
                f.largePads = new RangeFilter(1, 100);
            }
        }
        f.type = new StationTypeFilter(types);

        // 6. Updated at filter (10 hours fresh)
        Instant since = now.minus(FRESH_HOURS, ChronoUnit.HOURS);
        f.updatedAt = new UpdatedAt("<=>", List.of(since.toString(), now.toString()));

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
        @SerializedName("services")
        private List<Service> services;

        @SerializedName("distance")
        private Distance distance;

        @SerializedName("type")
        private StationTypeFilter type;

        @SerializedName("distance_to_arrival")
        private RangeFilter distanceToArrival;

        @SerializedName("large_pads")
        private RangeFilter largePads;

        @SerializedName("updated_at")
        private UpdatedAt updatedAt;

        public List<Service> getServices() {
            return services;
        }

        public Distance getDistance() {
            return distance;
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

        public UpdatedAt getUpdatedAt() {
            return updatedAt;
        }
    }

    public static class Service {
        @SerializedName("name")
        private List<String> name;

        public Service(List<String> name) {
            this.name = name;
        }

        public List<String> getName() {
            return name;
        }
    }

    public static class Distance {
        @SerializedName("min")
        private String min;

        @SerializedName("max")
        private String max;

        public Distance(int min, int max) {
            this.min = String.valueOf(min);
            this.max = String.valueOf(max);
        }

        public String getMin() {
            return min;
        }

        public String getMax() {
            return max;
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
        @SerializedName("comparison")
        private String comparison = "<=>";

        @SerializedName("value")
        private int[] value = new int[2];

        public RangeFilter(int min, int max) {
            this.value[0] = min;
            this.value[1] = max;
        }

        public int[] getValue() {
            return value;
        }
    }

    public static class UpdatedAt {
        @SerializedName("comparison")
        private String comparison;

        @SerializedName("value")
        private List<String> value;

        public UpdatedAt(String comparison, List<String> value) {
            this.comparison = comparison;
            this.value = value;
        }

        public String getComparison() {
            return comparison;
        }

        public List<String> getValue() {
            return value;
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

            public String getDirection() {
                return direction;
            }
        }

        public Direction getDistance() {
            return distance;
        }
    }
}
