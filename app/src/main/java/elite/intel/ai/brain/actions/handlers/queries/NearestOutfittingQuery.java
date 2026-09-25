package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.db.managers.TradeProfileManager;
import elite.intel.gameapi.search.spansh.outfitting.MatchedModule;
import elite.intel.gameapi.search.spansh.outfitting.ModuleDictionary;
import elite.intel.gameapi.search.spansh.outfitting.OutfittingStationSearchClient;
import elite.intel.gameapi.search.spansh.outfitting.OutfittingStationSearchCriteria;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.session.PlayerSession;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Query handler that finds the nearest station selling a specific ship outfitting module.
 */
@RegisterQuery
public class NearestOutfittingQuery extends BaseQueryAnalyzer implements IntelQuery {

    public static final String ID = "query_nearest_outfitting";
    public static final String PARAM_MODULE = "module";
    private static final int STALE_THRESHOLD_HOURS = 7 * 24; // 168 hours (7 days, spec §4)

    private final ModuleDictionary moduleDictionary;
    private final OutfittingStationSearchClient searchClient;

    public NearestOutfittingQuery() {
        this(ModuleDictionary.getInstance(), OutfittingStationSearchClient.getInstance());
    }

    // Visible for testing
    public NearestOutfittingQuery(ModuleDictionary moduleDictionary, OutfittingStationSearchClient searchClient) {
        this.moduleDictionary = moduleDictionary;
        this.searchClient = searchClient;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Find the nearest station selling a specific ship outfitting module (e.g. 5A FSD, Fuel Scoop).";
    }

    @Override
    public List<ActionParameterSpec> parameters() {
        return List.of(
                new ActionParameterSpec(
                        PARAM_MODULE,
                        "string",
                        true,
                        "The module to find, including class and rating if applicable (e.g. '5A FSD', '6A Fuel Scoop')",
                        List.of("5A FSD", "6A Fuel Scoop"),
                        null
                )
        );
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        String rawModule = extractModuleParam(params, originalUserInput);

        // 1. Resolve module deterministically using ModuleDictionary
        Optional<MatchedModule> matchOpt = moduleDictionary.match(rawModule);
        if (matchOpt.isEmpty()) {
            // UNKNOWN: do not perform external search (spec §6.2, §R.12 Rule 2).
            // Pass state to LLM without hardcoded strings (requirement 6).
            OutfittingDataDto unknownDto = OutfittingDataDto.unknown(rawModule);
            return process(new AiDataStruct(buildInstructions(), unknownDto), originalUserInput);
        }

        MatchedModule matched = matchOpt.get();

        // 2. Obtain current system and trade profile
        String currentSystem = PlayerSession.getInstance().getPrimaryStarName();
        if (currentSystem == null || currentSystem.isBlank()) {
            // LOCATION_UNKNOWN: do not perform external search when current system is unknown
            OutfittingDataDto locUnknownDto = OutfittingDataDto.locationUnknown(rawModule, matched);
            return process(new AiDataStruct(buildInstructions(), locUnknownDto), originalUserInput);
        }
        TradeRouteSearchCriteria profile = TradeProfileManager.getInstance().getCriteria(false);

        // 3. Build Spansh search criteria and execute search
        OutfittingStationSearchCriteria criteria = OutfittingStationSearchCriteria.create(matched, currentSystem, profile);
        TradeStationSearchResultDto searchResult = searchClient.searchOutfittingStations(criteria);

        if (searchResult == null || searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
            // NO_RESULT: pass state to LLM without hardcoded strings
            OutfittingDataDto noResultDto = OutfittingDataDto.noResult(rawModule, matched);
            return process(new AiDataStruct(buildInstructions(), noResultDto), originalUserInput);
        }

        // 4. Select closest station and compute data freshness
        TradeStationSearchResultDto.StationResult closest = searchResult.getResults().get(0);
        Long price = findModulePrice(closest, matched);
        String updatedAt = closest.getOutfittingUpdatedAt();
        Long ageHours = calculateAgeHours(updatedAt);
        boolean stale = isStale(ageHours);

        OutfittingDataDto foundDto = OutfittingDataDto.found(
                rawModule,
                matched,
                closest.getSystemName(),
                closest.getName(),
                closest.getType() != null ? closest.getType() : "Station",
                closest.getDistance(),
                closest.getDistanceToArrival(),
                price,
                updatedAt,
                ageHours,
                stale
        );

        return process(new AiDataStruct(buildInstructions(), foundDto), originalUserInput);
    }

    private String extractModuleParam(JsonObject params, String originalUserInput) {
        if (params != null && params.has(PARAM_MODULE)) {
            String val = params.get(PARAM_MODULE).getAsString();
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return originalUserInput != null ? originalUserInput.trim() : "";
    }

    private Long findModulePrice(TradeStationSearchResultDto.StationResult station, MatchedModule matched) {
        if (station.getModules() == null) return null;
        for (TradeStationSearchResultDto.StationResult.Module m : station.getModules()) {
            if (m.getName() != null && m.getName().equalsIgnoreCase(matched.canonicalName())) {
                boolean classMatches = matched.moduleClass() == null || Objects.equals(matched.moduleClass(), m.getModuleClass());
                boolean ratingMatches = matched.rating() == null || matched.rating().equalsIgnoreCase(m.getRating());
                if (classMatches && ratingMatches) {
                    return m.getPrice();
                }
            }
        }
        return null;
    }

    static Long calculateAgeHours(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }
        try {
            // Spansh outfitting_updated_at is ISO-8601 (e.g. 2026-09-24T12:00:00Z)
            Instant updated = Instant.parse(isoTimestamp.replace(" ", "T"));
            return ChronoUnit.HOURS.between(updated, Instant.now());
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isStale(Long ageHours) {
        return ageHours != null && ageHours > STALE_THRESHOLD_HOURS;
    }

    private static String buildInstructions() {
        return """
                Answer the commander's question about outfitting module purchase locations based on the data fields below.
                
                Data fields:
                - status: "found" (station located), "no_result" (no station found), "unknown_module" (module name could not be resolved), or "location_unknown" (current location is unknown)
                - rawModuleInput: user's module input text
                - module.name / module.moduleClass / module.rating: identified canonical module details
                - starSystem: star system name where the module is sold
                - stationName: station name selling the module
                - stationType: orbital port, planetary port, or carrier
                - distanceLy: distance from commander's current location in light years
                - distanceToArrivalLs: distance from system arrival star to the station in light seconds
                - price: module price in credits if known
                - outfittingUpdatedAt: ISO timestamp when outfitting data was recorded
                - dataAgeHours: age of the data in hours
                - stale: boolean flag indicating if data is older than 7 days
                
                Rules:
                - If status is "unknown_module": inform the commander in their language that the requested module could not be identified, and invite them to specify the module name clearly (with class and rating if applicable).
                - If status is "location_unknown": inform the commander in their language that outfitting search cannot be performed because their current location is unknown.
                - If status is "no_result": inform the commander in their language that no station matching their trade profile and search criteria was found selling this module.
                - If status is "found": report the star system, station name, distance in light years, and arrival distance in light seconds clearly. Mention the price if available.
                - When status is "found" and stale is true: warn the commander that the outfitting data is over 7 days old and availability may have changed. If stale is false, do not warn about data age.
                - Never invent star systems, stations, or prices not in the data.
                - Always reply in the commander's language (e.g. Japanese).
                """;
    }

    public record OutfittingDataDto(
            String status,
            String rawModuleInput,
            MatchedModuleDto module,
            String starSystem,
            String stationName,
            String stationType,
            Double distanceLy,
            Double distanceToArrivalLs,
            Long price,
            String outfittingUpdatedAt,
            Long dataAgeHours,
            boolean stale
    ) implements ToYamlConvertable {

        public static OutfittingDataDto unknown(String rawModuleInput) {
            return new OutfittingDataDto(
                    "unknown_module", rawModuleInput, null, null, null, null,
                    null, null, null, null, null, false
            );
        }

        public static OutfittingDataDto locationUnknown(String rawModuleInput, MatchedModule matched) {
            return new OutfittingDataDto(
                    "location_unknown", rawModuleInput, MatchedModuleDto.from(matched), null, null, null,
                    null, null, null, null, null, false
            );
        }

        public static OutfittingDataDto noResult(String rawModuleInput, MatchedModule matched) {
            return new OutfittingDataDto(
                    "no_result", rawModuleInput, MatchedModuleDto.from(matched), null, null, null,
                    null, null, null, null, null, false
            );
        }

        public static OutfittingDataDto found(
                String rawModuleInput,
                MatchedModule matched,
                String starSystem,
                String stationName,
                String stationType,
                Double distanceLy,
                Double distanceToArrivalLs,
                Long price,
                String outfittingUpdatedAt,
                Long dataAgeHours,
                boolean stale
        ) {
            return new OutfittingDataDto(
                    "found", rawModuleInput, MatchedModuleDto.from(matched), starSystem, stationName, stationType,
                    distanceLy, distanceToArrivalLs, price, outfittingUpdatedAt, dataAgeHours, stale
            );
        }

        @Override
        public String toYaml() {
            return YamlFactory.toYaml(this);
        }
    }

    public record MatchedModuleDto(
            String name,
            Integer moduleClass,
            String rating
    ) {
        public static MatchedModuleDto from(MatchedModule m) {
            return m != null ? new MatchedModuleDto(m.canonicalName(), m.moduleClass(), m.rating()) : null;
        }
    }
}
