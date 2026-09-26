package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.db.managers.TradeProfileManager;
import elite.intel.gameapi.search.edsm.EdsmApiClient;
import elite.intel.gameapi.search.edsm.EdsmApiClient.StarSystemLookupResult;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidate;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidatesResult;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidatesSearchClient;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidatesSearchCriteria;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.session.PlayerSession;
import elite.intel.util.StringUtls;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Query handler that recommends the top 3 single-hop commodity trade candidates based on fresh market data (<= 10 hours).
 */
@RegisterQuery
public class TradeCandidatesQuery extends BaseQueryAnalyzer implements IntelQuery {

    private static final Logger log = LogManager.getLogger(TradeCandidatesQuery.class);

    public static final String ID = "query_trade_candidates";
    public static final String PARAM_REFERENCE_SYSTEM = "referenceSystem";
    public static final String PARAM_PRIORITY = "priority";
    public static final String PARAM_RADIUS = "radius";

    public static final int DEFAULT_RADIUS = 30;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 50;
    public static final int OUT_OF_BOUNDS_RADIUS = 50;

    public static final String PRIORITY_PROFIT = "profit";
    public static final String PRIORITY_NEAREST = "nearest";

    @FunctionalInterface
    public interface StarSystemLookup {
        StarSystemLookupResult lookup(String starSystemName);
    }

    private final TradeCandidatesSearchClient searchClient;
    private final StarSystemLookup starSystemLookup;

    public TradeCandidatesQuery() {
        this(TradeCandidatesSearchClient.getInstance(), EdsmApiClient::lookupStarSystemName);
    }

    public TradeCandidatesQuery(TradeCandidatesSearchClient searchClient) {
        this(searchClient, EdsmApiClient::lookupStarSystemName);
    }

    // Visible for testing
    public TradeCandidatesQuery(TradeCandidatesSearchClient searchClient, StarSystemLookup starSystemLookup) {
        this.searchClient = searchClient;
        this.starSystemLookup = (starSystemLookup != null) ? starSystemLookup : EdsmApiClient::lookupStarSystemName;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Find the top 3 single-hop commodity trade candidates within range based on 10-hour fresh market data.";
    }

    @Override
    public List<ActionParameterSpec> parameters() {
        return List.of(
                new ActionParameterSpec(
                        PARAM_REFERENCE_SYSTEM,
                        "string",
                        false,
                        "Optional star system name to search trade candidates around (defaults to current system if omitted)",
                        null,
                        null
                ),
                new ActionParameterSpec(
                        PARAM_PRIORITY,
                        "string",
                        false,
                        "Sort priority for trade candidates: 'profit' (highest trip profit, default) or 'nearest' (closest purchase station)",
                        List.of(PRIORITY_PROFIT, PRIORITY_NEAREST),
                        null
                ),
                new ActionParameterSpec(
                        PARAM_RADIUS,
                        "number",
                        false,
                        "Search radius in light years (1-50 ly, default 30 ly)",
                        List.of("30", "50"),
                        null
                )
        );
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        String referenceSystemParam = extractReferenceSystemParam(params);
        String priority = extractPriorityParam(params);
        int radiusLy = extractRadiusParam(params);

        // 1. Obtain reference system: explicit parameter takes precedence, fallback to current primary star
        String rawCurrentSystem = PlayerSession.getInstance().getPrimaryStarName();
        String currentSystem = (rawCurrentSystem != null && !rawCurrentSystem.isBlank()) ? rawCurrentSystem.trim() : null;
        String searchedFromSystem;
        String referenceSource;
        if (referenceSystemParam != null) {
            referenceSource = "specified";
            StarSystemLookupResult lookupResult;
            try {
                lookupResult = starSystemLookup.lookup(referenceSystemParam);
            } catch (Exception e) {
                log.warn("Failed to lookup star system in EDSM: {}", e.getMessage());
                lookupResult = StarSystemLookupResult.lookupFailed();
            }

            if (lookupResult != null && lookupResult.isFound() && lookupResult.canonicalName() != null) {
                String canonical = lookupResult.canonicalName();
                log.info("Star system lookup: specified={}, normalized={}, result=found", referenceSystemParam, canonical);
                searchedFromSystem = canonical;
            } else if (lookupResult != null && lookupResult.isNotFound()) {
                log.info("Star system lookup: specified={}, result=not_found", referenceSystemParam);
                log.info("Trade candidates query: referenceSystem={}, referenceSource={}, radius={}ly, priority={}",
                        referenceSystemParam, referenceSource, radiusLy, priority);
                log.info("Trade candidates result: status=reference_system_not_found, stations=0, freshStations=0, pairs=0, candidates=0 (search: 0ms, calc: 0ms)");
                TradeCandidatesDataDto notFoundDto = TradeCandidatesDataDto.referenceSystemNotFound(
                        currentSystem, referenceSystemParam, priority, radiusLy);
                storeDisplay(notFoundDto);
                return process(new AiDataStruct(buildInstructions(), notFoundDto), originalUserInput);
            } else {
                log.info("Star system lookup: specified={}, result=lookup_failed (using specified name)", referenceSystemParam);
                searchedFromSystem = referenceSystemParam;
            }
        } else if (currentSystem != null) {
            searchedFromSystem = currentSystem;
            referenceSource = "current";
        } else {
            searchedFromSystem = null;
            referenceSource = null;
        }

        if (searchedFromSystem == null) {
            log.info("Trade candidates query: referenceSystem=unknown, referenceSource=none, radius={}ly, priority={}", radiusLy, priority);
            log.info("Trade candidates result: status=location_unknown, stations=0, freshStations=0, pairs=0, candidates=0 (search: 0ms, calc: 0ms)");
            TradeCandidatesDataDto locUnknownDto = TradeCandidatesDataDto.locationUnknown(currentSystem, priority, radiusLy);
            storeDisplay(locUnknownDto);
            return process(new AiDataStruct(buildInstructions(), locUnknownDto), originalUserInput);
        }

        // 2. Obtain trade profile; profile and cargo capacity must be available
        TradeRouteSearchCriteria profile = getTradeProfile();
        if (profile == null || profile.getMaxCargo() <= 0) {
            log.info("Trade candidates query: referenceSystem={}, referenceSource={}, radius={}ly, priority={}, profile={}",
                    searchedFromSystem, referenceSource, radiusLy, priority,
                    profile == null ? "null" : "[maxCargo=" + profile.getMaxCargo() + "]");
            log.info("Trade candidates result: status=profile_unavailable, stations=0, freshStations=0, pairs=0, candidates=0 (search: 0ms, calc: 0ms)");
            TradeCandidatesDataDto profileUnavailableDto = TradeCandidatesDataDto.profileUnavailable(
                    currentSystem, searchedFromSystem, referenceSource, priority, radiusLy);
            storeDisplay(profileUnavailableDto);
            return process(new AiDataStruct(buildInstructions(), profileUnavailableDto), originalUserInput);
        }

        log.info("Trade candidates query: referenceSystem={}, referenceSource={}, radius={}ly, priority={}, profile=[requiresLargePad={}, maxLs={}, allowPlanetary={}, allowFleetCarriers={}, allowProhibited={}]",
                searchedFromSystem, referenceSource, radiusLy, priority,
                profile.isRequiresLargePad(), profile.getMaxLsFromArrival(),
                profile.isAllowPlanetary(), profile.isAllowFleetCarriers(), profile.isAllowProhibited());

        // 3. Execute search via Spansh API
        long searchStart = System.currentTimeMillis();
        TradeCandidatesSearchCriteria criteria = TradeCandidatesSearchCriteria.create(searchedFromSystem, radiusLy, profile, Instant.now());
        TradeStationSearchResultDto searchResult = searchClient.searchTradeStations(criteria);
        long searchDurationMs = System.currentTimeMillis() - searchStart;

        int rawStationCount = (searchResult != null && searchResult.getResults() != null)
                ? searchResult.getResults().size()
                : 0;

        if (searchResult == null || searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
            log.info("Trade candidates result: status=too_few_stations, stations=0, freshStations=0, pairs=0, candidates=0 (search: {}ms, calc: 0ms)",
                    searchDurationMs);
            TradeCandidatesDataDto tooFewDto = TradeCandidatesDataDto.tooFewStations(
                    currentSystem, searchedFromSystem, referenceSource, priority, radiusLy, 0);
            storeDisplay(tooFewDto);
            return process(new AiDataStruct(buildInstructions(), tooFewDto), originalUserInput);
        }

        // 4. Calculate top trade candidates
        long calcStart = System.currentTimeMillis();
        TradeCandidatesResult calcResult = TradeCandidateCalculator.calculate(
                searchResult.getResults(),
                profile,
                priority,
                Instant.now()
        );
        long calcDurationMs = System.currentTimeMillis() - calcStart;

        log.info("Trade candidates result: status={}, stations={}, freshStations={}, pairs={}, candidates={} (search: {}ms, calc: {}ms)",
                calcResult.status(),
                rawStationCount,
                calcResult.freshStationsCount(),
                calcResult.routePairsCount(),
                calcResult.candidates().size(),
                searchDurationMs,
                calcDurationMs);

        TradeCandidatesDataDto dataDto = TradeCandidatesDataDto.create(
                calcResult.status(),
                currentSystem,
                searchedFromSystem,
                referenceSource,
                calcResult.freshStationsCount(),
                priority,
                radiusLy,
                calcResult.candidates()
        );

        storeDisplay(dataDto);

        if (!calcResult.candidates().isEmpty()) {
            if ("ok".equals(calcResult.status())) {
                return process(StringUtls.localizedResponse("handler.tradeCandidates.ok", calcResult.candidates().size()));
            } else if ("insufficient_fresh_data".equals(calcResult.status())) {
                return process(StringUtls.localizedResponse("handler.tradeCandidates.insufficientFreshData", calcResult.candidates().size()));
            }
        }

        return process(new AiDataStruct(buildInstructions(), dataDto), originalUserInput);
    }

    private void storeDisplay(TradeCandidatesDataDto dto) {
        try {
            elite.intel.db.managers.QueryResultDisplayManager.getInstance().saveTradeCandidates(dto);
        } catch (Exception e) {
            log.warn("Failed to store trade candidates display: {}", e.getMessage());
        }
    }

    TradeRouteSearchCriteria getTradeProfile() {
        return TradeProfileManager.getInstance().getCriteria(false);
    }

    public static int normalizeRadius(Integer rawRadius) {
        if (rawRadius == null) {
            return DEFAULT_RADIUS;
        }
        if (rawRadius < MIN_RADIUS || rawRadius > MAX_RADIUS) {
            return OUT_OF_BOUNDS_RADIUS;
        }
        return rawRadius;
    }

    String extractReferenceSystemParam(JsonObject params) {
        if (params != null && params.has(PARAM_REFERENCE_SYSTEM)) {
            try {
                JsonElement elem = params.get(PARAM_REFERENCE_SYSTEM);
                if (elem != null && !elem.isJsonNull()) {
                    String val = elem.getAsString();
                    if (val != null && !val.isBlank()) {
                        return val.trim();
                    }
                }
            } catch (Exception e) {
                // fall through
            }
        }
        return null;
    }

    private String extractPriorityParam(JsonObject params) {
        if (params != null && params.has(PARAM_PRIORITY)) {
            String val = params.get(PARAM_PRIORITY).getAsString();
            if (val != null && PRIORITY_NEAREST.equalsIgnoreCase(val.trim())) {
                return PRIORITY_NEAREST;
            }
        }
        return PRIORITY_PROFIT;
    }

    int extractRadiusParam(JsonObject params) {
        if (params != null && params.has(PARAM_RADIUS)) {
            try {
                JsonElement elem = params.get(PARAM_RADIUS);
                if (elem != null && !elem.isJsonNull()) {
                    double val = elem.getAsDouble();
                    if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                        int rounded = (int) Math.round(val);
                        return normalizeRadius(rounded);
                    }
                }
            } catch (Exception e) {
                // fall through to default
            }
        }
        return DEFAULT_RADIUS;
    }

    public static String formatInteger(Number n) {
        if (n == null) return null;
        return String.format(Locale.US, "%,d", n.longValue());
    }

    public static String formatLightYears(Double ly) {
        if (ly == null) return null;
        return String.format(Locale.US, "%.2f", ly);
    }

    public static String formatLightSeconds(Double ls) {
        if (ls == null) return null;
        return String.format(Locale.US, "%,d", Math.round(ls));
    }

    private static String buildInstructions() {
        return """
                Answer the commander's request for trade candidate recommendations based strictly on the provided data fields below.
                
                Data fields:
                - status: "ok" (trade candidates found), "insufficient_fresh_data" (fewer candidates found than requested due to freshness constraint), "too_few_stations" (fewer than 2 stations with fresh market data found in range to form trade routes), "no_result" (no profitable trade found), "profile_unavailable" (trade profile or cargo capacity is unavailable), "location_unknown" (commander's reference location is unknown), or "reference_system_not_found" (the specified reference star system was not found in the galaxy database)
                - currentSystem: commander's current star system
                - searchedFromSystem: star system used as the center reference for this search
                - referenceSource: "specified" (commander explicitly requested this reference system) or "current" (commander's current star system was used as default)
                - freshStationCount: number of stations with fresh market data found in range (relevant when status is "too_few_stations")
                - priority: sorting priority applied ("profit" or "nearest")
                - searchRadiusLy / searchRadiusLyDisplay: effective search radius in light years
                - candidates: list of candidate trades, ranked 1 to 3, already sorted and calculated:
                  - rank: rank (1, 2, 3)
                  - commodity: canonical commodity name
                  - buySystem / buyStation / buyPrice / buyPriceDisplay / supply / supplyDisplay / buyStationDistanceLs / buyStationDistanceLsDisplay / buyMarketUpdatedAt
                  - sellSystem / sellStation / sellPrice / sellPriceDisplay / demand / demandDisplay / sellStationDistanceLs / sellStationDistanceLsDisplay / sellMarketUpdatedAt
                  - unitProfit / unitProfitDisplay: profit per ton (sellPrice - buyPrice)
                  - units / unitsDisplay: cargo units to carry based on cargo capacity, capital, and supply/demand
                  - tripProfit / tripProfitDisplay: total profit for one run (unitProfit * units)
                  - distanceFromCurrentLy / distanceFromCurrentLyDisplay: distance from searchedFromSystem to buy system
                  - routeDistanceLy / routeDistanceLyDisplay: distance from buy system to sell system
                
                Rules:
                - If status is "reference_system_not_found": inform the commander in their language that the star system searchedFromSystem could not be found, and advise them to verify or correct the system name.
                - If status is "profile_unavailable": inform the commander in their language that trade candidate search cannot be performed because trade profile or cargo capacity is not available.
                - If status is "location_unknown": inform the commander in their language that trade candidate search cannot be performed because their reference location is unknown.
                - If status is "too_few_stations": inform the commander in their language that trade candidates cannot be generated because there are only freshStationCount station(s) (mention the exact count) with fresh market data matching the criteria within searchRadiusLy ly of searchedFromSystem.
                - If status is "no_result": inform the commander in their language that no profitable trade candidates matching their criteria were found within range.
                - If status is "insufficient_fresh_data": inform the commander in their language that fewer trade candidates than usual (mention the exact count found) were found within the 10-hour fresh market data window, and present the available candidate(s).
                - If status is "ok": present the trade candidates clearly in the given order.
                - Distances (distanceFromCurrentLy) are measured from searchedFromSystem.
                - When referenceSource is "specified", present the results as candidates around searchedFromSystem rather than commander's current position.
                - For each candidate: answer with exactly one concise sentence per candidate, reporting only the commodity, buy station and system, sell station and system, and total trip profit. Do NOT report other numerical details (such as cargo units, unit profit, Ls distance, or Ly distance) unless the user explicitly asks for them.
                - Currency must always be explicitly stated as credits (e.g. 'クレジット' in Japanese). Never refer to currency as yen (円) or any other real-world currency.
                - Use the pre-formatted display string fields (*Display) as the source of truth for all numbers, prices, profits, quantities, and distances. Never recalculate or re-round them.
                - Light seconds (Ls) measure distance from the star to the station, NOT travel time. Never describe Ls as time (do NOT say 'takes X seconds' or '〜秒かかる').
                - Do NOT re-rank or recalculate any candidate values; present the ranks and numbers exactly as given.
                - Do NOT automatically plot routes or claim to have plotted a route. Advise the commander that they can instruct route plotting to a chosen destination separately if desired.
                - Never invent commodities, stations, or numbers not present in the data.
                - Always respond in the commander's language (e.g. Japanese).
                """;
    }

    public record TradeCandidateDto(
            int rank,
            String commodity,
            String buySystem,
            String buyStation,
            int buyPrice,
            String buyPriceDisplay,
            long supply,
            String supplyDisplay,
            Double buyStationDistanceLs,
            String buyStationDistanceLsDisplay,
            String buyMarketUpdatedAt,
            String sellSystem,
            String sellStation,
            int sellPrice,
            String sellPriceDisplay,
            long demand,
            String demandDisplay,
            Double sellStationDistanceLs,
            String sellStationDistanceLsDisplay,
            String sellMarketUpdatedAt,
            int unitProfit,
            String unitProfitDisplay,
            int units,
            String unitsDisplay,
            long tripProfit,
            String tripProfitDisplay,
            Double distanceFromCurrentLy,
            String distanceFromCurrentLyDisplay,
            Double routeDistanceLy,
            String routeDistanceLyDisplay
    ) {
        public static TradeCandidateDto from(TradeCandidate c) {
            if (c == null) return null;
            return new TradeCandidateDto(
                    c.rank(),
                    c.commodity(),
                    c.buySystem(),
                    c.buyStation(),
                    c.buyPrice(),
                    formatInteger(c.buyPrice()),
                    c.supply(),
                    formatInteger(c.supply()),
                    c.buyStationDistanceLs(),
                    formatLightSeconds(c.buyStationDistanceLs()),
                    c.buyMarketUpdatedAt(),
                    c.sellSystem(),
                    c.sellStation(),
                    c.sellPrice(),
                    formatInteger(c.sellPrice()),
                    c.demand(),
                    formatInteger(c.demand()),
                    c.sellStationDistanceLs(),
                    formatLightSeconds(c.sellStationDistanceLs()),
                    c.sellMarketUpdatedAt(),
                    c.unitProfit(),
                    formatInteger(c.unitProfit()),
                    c.units(),
                    formatInteger(c.units()),
                    c.tripProfit(),
                    formatInteger(c.tripProfit()),
                    c.distanceFromCurrentLy(),
                    formatLightYears(c.distanceFromCurrentLy()),
                    c.routeDistanceLy(),
                    formatLightYears(c.routeDistanceLy())
            );
        }
    }

    public record TradeCandidatesDataDto(
            String status,
            String currentSystem,
            String searchedFromSystem,
            String referenceSource,
            Integer freshStationCount,
            String priority,
            Integer searchRadiusLy,
            String searchRadiusLyDisplay,
            List<TradeCandidateDto> candidates
    ) implements ToYamlConvertable {

        public TradeCandidatesDataDto(
                String status,
                String currentSystem,
                String searchedFromSystem,
                String referenceSource,
                Integer freshStationCount,
                String priority,
                Integer searchRadiusLy,
                List<TradeCandidateDto> candidates
        ) {
            this(status, currentSystem, searchedFromSystem, referenceSource, freshStationCount, priority, searchRadiusLy, formatInteger(searchRadiusLy), candidates);
        }

        public TradeCandidatesDataDto(
                String status,
                String currentSystem,
                String priority,
                Integer searchRadiusLy,
                List<TradeCandidateDto> candidates
        ) {
            this(status, currentSystem, currentSystem, "current", 0, priority, searchRadiusLy, formatInteger(searchRadiusLy), candidates);
        }

        public static TradeCandidatesDataDto referenceSystemNotFound(String currentSystem, String searchedFromSystem, String priority, int radiusLy) {
            return new TradeCandidatesDataDto("reference_system_not_found", currentSystem, searchedFromSystem, "specified", 0, priority, radiusLy, formatInteger(radiusLy), Collections.emptyList());
        }

        public static TradeCandidatesDataDto locationUnknown(String currentSystem, String priority, int radiusLy) {
            return new TradeCandidatesDataDto("location_unknown", currentSystem, null, null, 0, priority, radiusLy, formatInteger(radiusLy), Collections.emptyList());
        }

        public static TradeCandidatesDataDto locationUnknown(String priority, int radiusLy) {
            return locationUnknown(null, priority, radiusLy);
        }

        public static TradeCandidatesDataDto profileUnavailable(String currentSystem, String searchedFromSystem, String referenceSource, String priority, int radiusLy) {
            return new TradeCandidatesDataDto("profile_unavailable", currentSystem, searchedFromSystem, referenceSource, 0, priority, radiusLy, formatInteger(radiusLy), Collections.emptyList());
        }

        public static TradeCandidatesDataDto profileUnavailable(String currentSystem, String priority, int radiusLy) {
            return profileUnavailable(currentSystem, currentSystem, "current", priority, radiusLy);
        }

        public static TradeCandidatesDataDto tooFewStations(String currentSystem, String searchedFromSystem, String referenceSource, String priority, int radiusLy, int freshStationCount) {
            return new TradeCandidatesDataDto("too_few_stations", currentSystem, searchedFromSystem, referenceSource, freshStationCount, priority, radiusLy, formatInteger(radiusLy), Collections.emptyList());
        }

        public static TradeCandidatesDataDto noResult(String currentSystem, String searchedFromSystem, String referenceSource, String priority, int radiusLy) {
            return new TradeCandidatesDataDto("no_result", currentSystem, searchedFromSystem, referenceSource, 0, priority, radiusLy, formatInteger(radiusLy), Collections.emptyList());
        }

        public static TradeCandidatesDataDto noResult(String currentSystem, String priority, int radiusLy) {
            return noResult(currentSystem, currentSystem, "current", priority, radiusLy);
        }

        public static TradeCandidatesDataDto create(
                String status,
                String currentSystem,
                String searchedFromSystem,
                String referenceSource,
                int freshStationCount,
                String priority,
                int radiusLy,
                List<TradeCandidate> rawCandidates
        ) {
            List<TradeCandidateDto> candidateDtos = (rawCandidates == null)
                    ? Collections.emptyList()
                    : rawCandidates.stream().map(TradeCandidateDto::from).toList();
            return new TradeCandidatesDataDto(
                    status,
                    currentSystem,
                    searchedFromSystem,
                    referenceSource,
                    freshStationCount,
                    priority,
                    radiusLy,
                    formatInteger(radiusLy),
                    candidateDtos
            );
        }

        public static TradeCandidatesDataDto create(
                String status,
                String currentSystem,
                String priority,
                int radiusLy,
                List<TradeCandidate> rawCandidates
        ) {
            return create(status, currentSystem, currentSystem, "current", 0, priority, radiusLy, rawCandidates);
        }

        @Override
        public String toYaml() {
            return YamlFactory.toYaml(this);
        }
    }
}
