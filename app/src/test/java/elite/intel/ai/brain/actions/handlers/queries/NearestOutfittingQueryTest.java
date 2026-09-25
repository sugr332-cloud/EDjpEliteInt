package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.gameapi.search.spansh.outfitting.MatchedModule;
import elite.intel.gameapi.search.spansh.outfitting.ModuleDictionary;
import elite.intel.gameapi.search.spansh.outfitting.OutfittingStationSearchClient;
import elite.intel.gameapi.search.spansh.outfitting.OutfittingStationSearchCriteria;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class NearestOutfittingQueryTest {

    @Test
    void testStaleBoundaryCalculation() {
        Instant now = Instant.now();

        // Exactly 7 days (168 hours): NOT stale (stale requires strictly > 7 days)
        Instant exact7Days = now.minus(168, ChronoUnit.HOURS);
        Long age7Days = NearestOutfittingQuery.calculateAgeHours(exact7Days.toString());
        assertNotNull(age7Days);
        assertEquals(168, age7Days);
        assertFalse(NearestOutfittingQuery.isStale(age7Days), "Exactly 168 hours must not be stale");

        // 7 days + 1 hour (169 hours): STALE
        Instant over7Days = now.minus(169, ChronoUnit.HOURS);
        Long ageOver7Days = NearestOutfittingQuery.calculateAgeHours(over7Days.toString());
        assertNotNull(ageOver7Days);
        assertEquals(169, ageOver7Days);
        assertTrue(NearestOutfittingQuery.isStale(ageOver7Days), "169 hours must be stale");

        // 1 day (24 hours): NOT stale
        Instant oneDay = now.minus(24, ChronoUnit.HOURS);
        Long ageOneDay = NearestOutfittingQuery.calculateAgeHours(oneDay.toString());
        assertNotNull(ageOneDay);
        assertEquals(24, ageOneDay);
        assertFalse(NearestOutfittingQuery.isStale(ageOneDay), "24 hours must not be stale");

        // Null / blank / invalid: NOT stale
        assertNull(NearestOutfittingQuery.calculateAgeHours(null));
        assertNull(NearestOutfittingQuery.calculateAgeHours(""));
        assertNull(NearestOutfittingQuery.calculateAgeHours("invalid-date"));
        assertFalse(NearestOutfittingQuery.isStale(null));
    }

    @Test
    void testUnknownModuleReturnsUnknownStatusWithoutSearch() throws Exception {
        boolean[] searchCalled = {false};
        OutfittingStationSearchClient mockClient = new OutfittingStationSearchClient() {
            @Override
            public TradeStationSearchResultDto searchOutfittingStations(OutfittingStationSearchCriteria criteria) {
                searchCalled[0] = true;
                return new TradeStationSearchResultDto();
            }
        };

        NearestOutfittingQuery query = new NearestOutfittingQuery(ModuleDictionary.getInstance(), mockClient);

        JsonObject params = new JsonObject();
        params.addProperty("module", "未知のモジュール99Z");

        // Unknown module
        NearestOutfittingQuery.OutfittingDataDto[] capturedDto = {null};
        NearestOutfittingQuery testQuery = new NearestOutfittingQuery(ModuleDictionary.getInstance(), mockClient) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof NearestOutfittingQuery.OutfittingDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        testQuery.handle("query_nearest_outfitting", params, "未知のモジュール99Z を売っている場所");

        assertFalse(searchCalled[0], "External search must NOT be called when module is unknown");
        assertNotNull(capturedDto[0]);
        assertEquals("unknown_module", capturedDto[0].status());
        assertEquals("未知のモジュール99Z", capturedDto[0].rawModuleInput());
        assertNull(capturedDto[0].module());
    }

    @Test
    void testNoResultReturnsNoResultStatus() throws Exception {
        OutfittingStationSearchClient emptyClient = new OutfittingStationSearchClient() {
            @Override
            public TradeStationSearchResultDto searchOutfittingStations(OutfittingStationSearchCriteria criteria) {
                TradeStationSearchResultDto dto = new TradeStationSearchResultDto();
                dto.setResults(List.of());
                return dto;
            }
        };

        NearestOutfittingQuery.OutfittingDataDto[] capturedDto = {null};
        NearestOutfittingQuery testQuery = new NearestOutfittingQuery(ModuleDictionary.getInstance(), emptyClient) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof NearestOutfittingQuery.OutfittingDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        JsonObject params = new JsonObject();
        params.addProperty("module", "5A FSD");

        elite.intel.session.PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");
        testQuery.handle("query_nearest_outfitting", params, "5A FSD はどこで買える");

        assertNotNull(capturedDto[0]);
        assertEquals("no_result", capturedDto[0].status());
        assertNotNull(capturedDto[0].module());
        assertEquals("Frame Shift Drive", capturedDto[0].module().name());
        assertEquals(5, capturedDto[0].module().moduleClass());
        assertEquals("A", capturedDto[0].module().rating());
    }

    @Test
    void testFoundStatusWithStationDetails() throws Exception {
        Instant tenDaysAgo = Instant.now().minus(240, ChronoUnit.HOURS);
        String stationJson = """
                {
                    "name": "Jameson Memorial",
                    "system_name": "Shinrarta Dezhra",
                    "type": "Coriolis Starport",
                    "distance": 12.5,
                    "distance_to_arrival": 300.0,
                    "outfitting_updated_at": "%s",
                    "modules": [
                        {
                            "name": "Frame Shift Drive",
                            "class": 5,
                            "rating": "A",
                            "price": 5100000
                        }
                    ]
                }
                """.formatted(tenDaysAgo.toString());

        TradeStationSearchResultDto.StationResult parsedStation = elite.intel.util.json.GsonFactory.getGson().fromJson(
                stationJson, TradeStationSearchResultDto.StationResult.class);

        TradeStationSearchResultDto searchDto = new TradeStationSearchResultDto();
        searchDto.setResults(List.of(parsedStation));

        OutfittingStationSearchClient foundClient = new OutfittingStationSearchClient() {
            @Override
            public TradeStationSearchResultDto searchOutfittingStations(OutfittingStationSearchCriteria criteria) {
                return searchDto;
            }
        };

        NearestOutfittingQuery.OutfittingDataDto[] capturedDto = {null};
        NearestOutfittingQuery testQuery = new NearestOutfittingQuery(ModuleDictionary.getInstance(), foundClient) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof NearestOutfittingQuery.OutfittingDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        JsonObject params = new JsonObject();
        params.addProperty("module", "5A FSD");

        elite.intel.session.PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");
        testQuery.handle("query_nearest_outfitting", params, "5A FSD の最寄り販売ステーション");

        assertNotNull(capturedDto[0]);
        assertEquals("found", capturedDto[0].status());
        assertEquals("Shinrarta Dezhra", capturedDto[0].starSystem());
        assertEquals("Jameson Memorial", capturedDto[0].stationName());
        assertEquals("Coriolis Starport", capturedDto[0].stationType());
        assertEquals(12.5, capturedDto[0].distanceLy());
        assertEquals(300.0, capturedDto[0].distanceToArrivalLs());
        assertEquals(5100000L, capturedDto[0].price());
        assertTrue(capturedDto[0].stale(), "10 days ago must be marked stale=true");
        assertNotNull(capturedDto[0].toYaml());
    }

    @Test
    void testLocationUnknownReturnsLocationUnknownStatusWithoutSearch() throws Exception {
        boolean[] searchCalled = {false};
        OutfittingStationSearchClient mockClient = new OutfittingStationSearchClient() {
            @Override
            public TradeStationSearchResultDto searchOutfittingStations(OutfittingStationSearchCriteria criteria) {
                searchCalled[0] = true;
                return new TradeStationSearchResultDto();
            }
        };

        NearestOutfittingQuery.OutfittingDataDto[] capturedDto = {null};
        NearestOutfittingQuery testQuery = new NearestOutfittingQuery(ModuleDictionary.getInstance(), mockClient) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof NearestOutfittingQuery.OutfittingDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        String previousStar = elite.intel.session.PlayerSession.getInstance().getPrimaryStarName();
        try {
            elite.intel.session.PlayerSession.getInstance().setCurrentPrimaryStarName(null);

            JsonObject params = new JsonObject();
            params.addProperty("module", "5A FSD");

            testQuery.handle("query_nearest_outfitting", params, "5A FSD を売っている場所");

            assertFalse(searchCalled[0], "External search must NOT be called when location is unknown");
            assertNotNull(capturedDto[0]);
            assertEquals("location_unknown", capturedDto[0].status());
            assertEquals("5A FSD", capturedDto[0].rawModuleInput());
            assertNotNull(capturedDto[0].module());
            assertEquals("Frame Shift Drive", capturedDto[0].module().name());
            assertEquals(5, capturedDto[0].module().moduleClass());
            assertEquals("A", capturedDto[0].module().rating());
        } finally {
            elite.intel.session.PlayerSession.getInstance().setCurrentPrimaryStarName(previousStar);
        }
    }

    @Test
    void testNumberFormattingRules() {
        // Standard expected values
        assertEquals("5,103,950", NearestOutfittingQuery.formatInteger(5103950));
        assertEquals("12.61", NearestOutfittingQuery.formatLightYears(12.6115723766904));
        assertEquals("473", NearestOutfittingQuery.formatLightSeconds(473.444649));

        // 0 handling
        assertEquals("0", NearestOutfittingQuery.formatInteger(0));
        assertEquals("0.00", NearestOutfittingQuery.formatLightYears(0.0));
        assertEquals("0", NearestOutfittingQuery.formatLightSeconds(0.0));
        assertEquals("0", NearestOutfittingQuery.formatHours(0L));

        // null handling
        assertNull(NearestOutfittingQuery.formatInteger(null));
        assertNull(NearestOutfittingQuery.formatLightYears(null));
        assertNull(NearestOutfittingQuery.formatLightSeconds(null));
        assertNull(NearestOutfittingQuery.formatHours(null));

        // Locale independence (e.g. Locale.GERMANY where commas and dots differ)
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("5,103,950", NearestOutfittingQuery.formatInteger(5103950));
            assertEquals("12.61", NearestOutfittingQuery.formatLightYears(12.6115723766904));
            assertEquals("473", NearestOutfittingQuery.formatLightSeconds(473.444649));
            assertEquals("0", NearestOutfittingQuery.formatInteger(0));
            assertEquals("0.00", NearestOutfittingQuery.formatLightYears(0.0));
            assertEquals("0", NearestOutfittingQuery.formatLightSeconds(0.0));
            assertNull(NearestOutfittingQuery.formatInteger(null));
            assertNull(NearestOutfittingQuery.formatLightYears(null));
            assertNull(NearestOutfittingQuery.formatLightSeconds(null));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void testOutfittingDataDtoCarriesFormattedDisplayFields() {
        NearestOutfittingQuery.OutfittingDataDto dto = NearestOutfittingQuery.OutfittingDataDto.found(
                "5A FSD",
                null,
                "Shinrarta Dezhra",
                "Jameson Memorial",
                "orbital",
                12.6115723766904,
                473.444649,
                5103950L,
                "2026-09-26T00:00:00Z",
                2L,
                false
        );

        assertEquals("12.61", dto.distanceLyDisplay());
        assertEquals("473", dto.distanceToArrivalLsDisplay());
        assertEquals("5,103,950", dto.priceDisplay());
        assertEquals("2", dto.dataAgeHoursDisplay());
        assertNotNull(dto.toYaml());
    }

    @Test
    void testCalculateAgeHoursWithSpanshFormat() {
        assertNull(NearestOutfittingQuery.calculateAgeHours(null));
        assertNull(NearestOutfittingQuery.calculateAgeHours(""));
        assertNull(NearestOutfittingQuery.calculateAgeHours("invalid"));

        // Spansh +00 format should not throw and should return age
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss+00")
                .withZone(java.time.ZoneOffset.UTC)
                .format(java.time.Instant.now().minus(java.time.Duration.ofHours(3)));
        Long ageHours = NearestOutfittingQuery.calculateAgeHours(timestamp);
        assertNotNull(ageHours);
        assertTrue(ageHours >= 2 && ageHours <= 4);
    }
}
