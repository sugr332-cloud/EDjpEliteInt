package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.db.managers.TradeProfileManager;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidate;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidatesResult;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidatesSearchClient;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidatesSearchCriteria;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.session.PlayerSession;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Query handler that recommends the top 3 single-hop commodity trade candidates based on fresh market data (<= 10 hours).
 */
@RegisterQuery
public class TradeCandidatesQuery extends BaseQueryAnalyzer implements IntelQuery {

    public static final String ID = "query_trade_candidates";
    public static final String PARAM_PRIORITY = "priority";
    public static final String PARAM_RADIUS = "radius";

    public static final int DEFAULT_RADIUS = 30;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 50;
    public static final int OUT_OF_BOUNDS_RADIUS = 50;

    public static final String PRIORITY_PROFIT = "profit";
    public static final String PRIORITY_NEAREST = "nearest";

    private final TradeCandidatesSearchClient searchClient;

    public TradeCandidatesQuery() {
        this(TradeCandidatesSearchClient.getInstance());
    }

    // Visible for testing
    public TradeCandidatesQuery(TradeCandidatesSearchClient searchClient) {
        this.searchClient = searchClient;
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
                        PARAM_PRIORITY,
                        "string",
                        false,
                        "Sort priority for trade candidates: 'profit' (highest trip profit, default) or 'nearest' (closest purchase station)",
                        List.of(PRIORITY_PROFIT, PRIORITY_NEAREST),
                        null
                ),
                new ActionParameterSpec(
                        PARAM_RADIUS,
                        "integer",
                        false,
                        "Search radius in light years (1-50 ly, default 30 ly)",
                        List.of("30", "50"),
                        null
                )
        );
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        String priority = extractPriorityParam(params);
        int radiusLy = extractRadiusParam(params);

        // 1. Obtain current system
        String currentSystem = PlayerSession.getInstance().getPrimaryStarName();
        if (currentSystem == null || currentSystem.isBlank()) {
            TradeCandidatesDataDto locUnknownDto = TradeCandidatesDataDto.locationUnknown(priority, radiusLy);
            return process(new AiDataStruct(buildInstructions(), locUnknownDto), originalUserInput);
        }

        // 2. Obtain trade profile; profile and cargo capacity must be available
        TradeRouteSearchCriteria profile = getTradeProfile();
        if (profile == null || profile.getMaxCargo() <= 0) {
            TradeCandidatesDataDto profileUnavailableDto = TradeCandidatesDataDto.profileUnavailable(currentSystem, priority, radiusLy);
            return process(new AiDataStruct(buildInstructions(), profileUnavailableDto), originalUserInput);
        }

        // 3. Execute search via Spansh API
        TradeCandidatesSearchCriteria criteria = TradeCandidatesSearchCriteria.create(currentSystem, radiusLy, profile, Instant.now());
        TradeStationSearchResultDto searchResult = searchClient.searchTradeStations(criteria);

        if (searchResult == null || searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
            TradeCandidatesDataDto noResultDto = TradeCandidatesDataDto.noResult(currentSystem, priority, radiusLy);
            return process(new AiDataStruct(buildInstructions(), noResultDto), originalUserInput);
        }

        // 4. Calculate top trade candidates
        TradeCandidatesResult calcResult = TradeCandidateCalculator.calculate(
                searchResult.getResults(),
                profile,
                priority,
                Instant.now()
        );

        TradeCandidatesDataDto dataDto = new TradeCandidatesDataDto(
                calcResult.status(),
                currentSystem,
                priority,
                radiusLy,
                calcResult.candidates()
        );

        return process(new AiDataStruct(buildInstructions(), dataDto), originalUserInput);
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

    private String extractPriorityParam(JsonObject params) {
        if (params != null && params.has(PARAM_PRIORITY)) {
            String val = params.get(PARAM_PRIORITY).getAsString();
            if (val != null && PRIORITY_NEAREST.equalsIgnoreCase(val.trim())) {
                return PRIORITY_NEAREST;
            }
        }
        return PRIORITY_PROFIT;
    }

    private int extractRadiusParam(JsonObject params) {
        if (params != null && params.has(PARAM_RADIUS)) {
            try {
                int r = params.get(PARAM_RADIUS).getAsInt();
                return normalizeRadius(r);
            } catch (Exception e) {
                // fall through to default
            }
        }
        return DEFAULT_RADIUS;
    }

    private static String buildInstructions() {
        return """
                Answer the commander's request for trade candidate recommendations based strictly on the provided data fields below.
                
                Data fields:
                - status: "ok" (trade candidates found), "insufficient_fresh_data" (fewer candidates found than requested due to freshness constraint), "no_result" (no profitable trade found), "profile_unavailable" (trade profile or cargo capacity is unavailable), or "location_unknown" (commander's current location is unknown)
                - currentSystem: commander's current star system
                - priority: sorting priority applied ("profit" or "nearest")
                - searchRadiusLy: effective search radius in light years
                - candidates: list of candidate trades, ranked 1 to 3, already sorted and calculated:
                  - rank: rank (1, 2, 3)
                  - commodity: canonical commodity name
                  - buySystem / buyStation / buyPrice / supply / buyStationDistanceLs / buyMarketUpdatedAt
                  - sellSystem / sellStation / sellPrice / demand / sellStationDistanceLs / sellMarketUpdatedAt
                  - unitProfit: profit per ton (sellPrice - buyPrice)
                  - units: cargo units to carry based on cargo capacity, capital, and supply/demand
                  - tripProfit: total profit for one run (unitProfit * units)
                  - distanceFromCurrentLy: distance from commander to buy system
                  - routeDistanceLy: distance from buy system to sell system
                
                Rules:
                - If status is "profile_unavailable": inform the commander in their language that trade candidate search cannot be performed because trade profile or cargo capacity is not available.
                - If status is "location_unknown": inform the commander in their language that trade candidate search cannot be performed because their current location is unknown.
                - If status is "no_result": inform the commander in their language that no profitable trade candidates matching their criteria were found within range.
                - If status is "insufficient_fresh_data": inform the commander in their language that fewer trade candidates than usual (mention the exact count found) were found within the 10-hour fresh market data window, and present the available candidate(s).
                - If status is "ok": present the trade candidates clearly in the given order.
                - For each candidate: report the commodity, buy station and system, sell station and system, cargo units, unit profit, total trip profit, distance to buy station, and route distance.
                - Do NOT re-rank or recalculate any candidate values; present the ranks and numbers exactly as given.
                - Do NOT automatically plot routes or claim to have plotted a route. Advise the commander that they can instruct route plotting to a chosen destination separately if desired.
                - Never invent commodities, stations, or numbers not present in the data.
                - Always respond in the commander's language (e.g. Japanese).
                """;
    }

    public record TradeCandidatesDataDto(
            String status,
            String currentSystem,
            String priority,
            Integer searchRadiusLy,
            List<TradeCandidate> candidates
    ) implements ToYamlConvertable {

        public static TradeCandidatesDataDto locationUnknown(String priority, int radiusLy) {
            return new TradeCandidatesDataDto("location_unknown", null, priority, radiusLy, Collections.emptyList());
        }

        public static TradeCandidatesDataDto profileUnavailable(String currentSystem, String priority, int radiusLy) {
            return new TradeCandidatesDataDto("profile_unavailable", currentSystem, priority, radiusLy, Collections.emptyList());
        }

        public static TradeCandidatesDataDto noResult(String currentSystem, String priority, int radiusLy) {
            return new TradeCandidatesDataDto("no_result", currentSystem, priority, radiusLy, Collections.emptyList());
        }

        @Override
        public String toYaml() {
            return YamlFactory.toYaml(this);
        }
    }
}
