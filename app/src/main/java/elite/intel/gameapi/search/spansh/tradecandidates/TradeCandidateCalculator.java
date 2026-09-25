package elite.intel.gameapi.search.spansh.tradecandidates;

import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult.Commodity;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult.MarketEntry;
import elite.intel.gameapi.search.spansh.SpanshTimestamps;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Calculates candidate 1-hop commodity trades between stations with fresh market data (<= 10 hours).
 * Computes unit profit, cargo capacity, trip profit, and distances, and selects the top 3 candidates.
 */
public class TradeCandidateCalculator {

    public static final long MAX_MARKET_AGE_SECONDS = 10 * 3600; // 10 hours in seconds
    public static final long MAX_FUTURE_TOLERANCE_SECONDS = 300;  // 5 minutes tolerance for PC clock skew

    public record TradeCandidate(
            int rank,
            String commodity,
            String buySystem,
            String buyStation,
            int buyPrice,
            long supply,
            Double buyStationDistanceLs,
            String buyMarketUpdatedAt,
            String sellSystem,
            String sellStation,
            int sellPrice,
            long demand,
            Double sellStationDistanceLs,
            String sellMarketUpdatedAt,
            int unitProfit,
            int units,
            long tripProfit,
            Double distanceFromCurrentLy,
            Double routeDistanceLy
    ) {}

    public record TradeCandidatesResult(
            String status,
            List<TradeCandidate> candidates,
            int freshStationsCount,
            int routePairsCount
    ) {
        public TradeCandidatesResult(String status, List<TradeCandidate> candidates) {
            this(status, candidates, 0, 0);
        }
    }

    public static TradeCandidatesResult calculate(
            List<StationResult> stations,
            TradeRouteSearchCriteria profile,
            String priority,
            Instant now
    ) {
        if (stations == null || stations.isEmpty() || profile == null || profile.getMaxCargo() <= 0) {
            return new TradeCandidatesResult("no_result", Collections.emptyList(), 0, 0);
        }

        // 1. Filter stations with fresh market data (market_updated_at <= 10 hours, allowing up to 5 min clock skew)
        List<StationResult> freshStations = new ArrayList<>();
        for (StationResult s : stations) {
            if (isMarketFresh(s.getMarketUpdatedAt(), now)) {
                freshStations.add(s);
            }
        }

        if (freshStations.size() < 2) {
            // Cannot form a 1-hop pair with fewer than 2 fresh stations
            return new TradeCandidatesResult("no_result", Collections.emptyList(), freshStations.size(), 0);
        }

        int maxCargo = profile.getMaxCargo();
        int startingCapital = profile.getStartingCapital();
        boolean allowProhibited = profile.isAllowProhibited();

        // 2. Explore commodity trades and keep only the best trade per unique route leg (buyStation -> sellStation)
        // Spec §5.2: Same route leg keeps only 1 commodity with maximum tripProfit so top 3 aren't filled with the same stations.
        Map<String, TradeCandidate> bestTradePerRoute = new HashMap<>();

        for (int i = 0; i < freshStations.size(); i++) {
            StationResult buyStation = freshStations.get(i);
            if (buyStation.getMarket() == null || buyStation.getMarket().isEmpty()) {
                continue;
            }
            String buyKey = stationKey(buyStation);

            for (int j = 0; j < freshStations.size(); j++) {
                if (i == j) {
                    continue; // Same station is not a valid 1-hop pair
                }
                StationResult sellStation = freshStations.get(j);
                if (sellStation.getMarket() == null || sellStation.getMarket().isEmpty()) {
                    continue;
                }
                String sellKey = stationKey(sellStation);

                // Distinguish stations by system + station name or ID
                if (buyKey.equalsIgnoreCase(sellKey)) {
                    continue;
                }

                String routeKey = buyKey + "->" + sellKey;

                // Match commodities between buyStation and sellStation
                Map<String, MarketEntry> sellMarketMap = buildMarketMap(sellStation.getMarket());

                for (MarketEntry buyEntry : buyStation.getMarket()) {
                    String commodityName = buyEntry.getCommodity();
                    if (commodityName == null || commodityName.isBlank()) {
                        continue;
                    }
                    Integer buyPrice = buyEntry.getBuyPrice();
                    Long supply = buyEntry.getSupply();
                    if (buyPrice == null || buyPrice <= 0 || supply == null || supply <= 0) {
                        continue;
                    }

                    // Prohibited commodity check
                    if (!allowProhibited) {
                        if (isProhibited(commodityName, buyStation.getProhibitedCommodities())
                                || isProhibited(commodityName, sellStation.getProhibitedCommodities())) {
                            continue;
                        }
                    }

                    MarketEntry sellEntry = sellMarketMap.get(commodityName.toLowerCase(Locale.ROOT));
                    if (sellEntry == null) {
                        continue;
                    }
                    Integer sellPrice = sellEntry.getSellPrice();
                    Long demand = sellEntry.getDemand();
                    if (sellPrice == null || sellPrice <= buyPrice || demand == null || demand <= 0) {
                        continue;
                    }

                    int unitProfit = sellPrice - buyPrice;

                    // Units calculation
                    int maxAffordable = startingCapital > 0 ? (startingCapital / buyPrice) : maxCargo;
                    long effectiveUnitsLong = Math.min(
                            Math.min((long) maxCargo, (long) maxAffordable),
                            Math.min(supply, demand)
                    );
                    if (effectiveUnitsLong <= 0) {
                        continue;
                    }
                    int units = (int) Math.min((long) Integer.MAX_VALUE, effectiveUnitsLong);
                    long tripProfit = (long) unitProfit * units;

                    // Do not assume 0.0 if distance is null; keep null as-is
                    Double distFromCurrent = buyStation.getDistance();
                    Double routeDistance = calculateDistanceLy(buyStation, sellStation);

                    TradeCandidate candidate = new TradeCandidate(
                            0, // rank set later
                            commodityName,
                            buyStation.getSystemName(),
                            buyStation.getName(),
                            buyPrice,
                            supply,
                            buyStation.getDistanceToArrival(),
                            buyStation.getMarketUpdatedAt(),
                            sellStation.getSystemName(),
                            sellStation.getName(),
                            sellPrice,
                            demand,
                            sellStation.getDistanceToArrival(),
                            sellStation.getMarketUpdatedAt(),
                            unitProfit,
                            units,
                            tripProfit,
                            distFromCurrent,
                            routeDistance
                    );

                    // Retain only the candidate with highest tripProfit for this route leg
                    TradeCandidate existing = bestTradePerRoute.get(routeKey);
                    if (existing == null || candidate.tripProfit() > existing.tripProfit()) {
                        bestTradePerRoute.put(routeKey, candidate);
                    }
                }
            }
        }

        if (bestTradePerRoute.isEmpty()) {
            return new TradeCandidatesResult("no_result", Collections.emptyList(), freshStations.size(), 0);
        }

        List<TradeCandidate> candidates = new ArrayList<>(bestTradePerRoute.values());

        // 3. Sort candidates based on priority (Comparator.nullsLast for null distance)
        boolean nearestSort = "nearest".equalsIgnoreCase(priority);
        Comparator<TradeCandidate> comparator;
        if (nearestSort) {
            comparator = Comparator.comparing(
                    TradeCandidate::distanceFromCurrentLy,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ).thenComparing(Comparator.comparing(TradeCandidate::tripProfit).reversed());
        } else {
            // Default "profit"
            comparator = Comparator.comparing(TradeCandidate::tripProfit).reversed()
                    .thenComparing(
                            TradeCandidate::distanceFromCurrentLy,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    );
        }
        candidates.sort(comparator);

        // 4. Pick top 3 candidates and assign ranks
        int count = Math.min(3, candidates.size());
        List<TradeCandidate> topCandidates = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            TradeCandidate c = candidates.get(r);
            topCandidates.add(new TradeCandidate(
                    r + 1,
                    c.commodity(),
                    c.buySystem(),
                    c.buyStation(),
                    c.buyPrice(),
                    c.supply(),
                    c.buyStationDistanceLs(),
                    c.buyMarketUpdatedAt(),
                    c.sellSystem(),
                    c.sellStation(),
                    c.sellPrice(),
                    c.demand(),
                    c.sellStationDistanceLs(),
                    c.sellMarketUpdatedAt(),
                    c.unitProfit(),
                    c.units(),
                    c.tripProfit(),
                    c.distanceFromCurrentLy(),
                    c.routeDistanceLy()
            ));
        }

        String status = topCandidates.size() >= 3 ? "ok" : "insufficient_fresh_data";
        return new TradeCandidatesResult(status, topCandidates, freshStations.size(), bestTradePerRoute.size());
    }

    public static boolean isMarketFresh(String marketUpdatedAt, Instant now) {
        if (marketUpdatedAt == null || marketUpdatedAt.isBlank()) {
            return false;
        }
        try {
            Instant updated = SpanshTimestamps.parse(marketUpdatedAt);
            if (updated == null) {
                return false;
            }
            long ageSeconds = Duration.between(updated, now).toSeconds();
            // Fresh if age is not further in the future than tolerance (-300s) and not older than 10h (36000s)
            return ageSeconds >= -MAX_FUTURE_TOLERANCE_SECONDS && ageSeconds <= MAX_MARKET_AGE_SECONDS;
        } catch (Exception e) {
            return false;
        }
    }

    private static String stationKey(StationResult station) {
        if (station.getId() != null && !station.getId().isBlank()) {
            return station.getId();
        }
        String sys = station.getSystemName() != null ? station.getSystemName().trim() : "";
        String st = station.getName() != null ? station.getName().trim() : "";
        return sys + "|" + st;
    }

    private static Map<String, MarketEntry> buildMarketMap(List<MarketEntry> market) {
        Map<String, MarketEntry> map = new HashMap<>();
        for (MarketEntry entry : market) {
            if (entry.getCommodity() != null) {
                map.put(entry.getCommodity().toLowerCase(Locale.ROOT), entry);
            }
        }
        return map;
    }

    private static boolean isProhibited(String commodityName, List<Commodity> prohibited) {
        if (prohibited == null || prohibited.isEmpty() || commodityName == null) {
            return false;
        }
        for (Commodity c : prohibited) {
            if (c.getName() != null && c.getName().equalsIgnoreCase(commodityName)) {
                return true;
            }
        }
        return false;
    }

    private static Double calculateDistanceLy(StationResult a, StationResult b) {
        if (a.getSystemName() != null && b.getSystemName() != null
                && a.getSystemName().equalsIgnoreCase(b.getSystemName())) {
            return 0.0;
        }
        if (a.getSystemX() != null && a.getSystemY() != null && a.getSystemZ() != null
                && b.getSystemX() != null && b.getSystemY() != null && b.getSystemZ() != null) {
            double dx = a.getSystemX() - b.getSystemX();
            double dy = a.getSystemY() - b.getSystemY();
            double dz = a.getSystemZ() - b.getSystemZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return Math.round(dist * 100.0) / 100.0;
        }
        return null;
    }
}
