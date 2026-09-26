package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidatesSearchClient;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidatesSearchCriteria;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.session.PlayerSession;
import elite.intel.util.json.GsonFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class TradeCandidatesQueryTest {

    @Test
    void testNormalizeRadiusRounding() {
        // radius 0 -> 50 (out of bounds)
        assertEquals(50, TradeCandidatesQuery.normalizeRadius(0));
        // radius -10 -> 50 (out of bounds)
        assertEquals(50, TradeCandidatesQuery.normalizeRadius(-10));
        // radius 51 -> 50 (out of bounds)
        assertEquals(50, TradeCandidatesQuery.normalizeRadius(51));
        // radius 100 -> 50 (out of bounds)
        assertEquals(50, TradeCandidatesQuery.normalizeRadius(100));

        // null / unspecified -> 30 (default)
        assertEquals(30, TradeCandidatesQuery.normalizeRadius(null));

        // valid range 1-50 -> exact value preserved
        assertEquals(1, TradeCandidatesQuery.normalizeRadius(1));
        assertEquals(30, TradeCandidatesQuery.normalizeRadius(30));
        assertEquals(45, TradeCandidatesQuery.normalizeRadius(45));
        assertEquals(50, TradeCandidatesQuery.normalizeRadius(50));
    }

    @Test
    void testLocationUnknownReturnsLocationUnknownWithoutSearch() throws Exception {
        boolean[] searchCalled = {false};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                searchCalled[0] = true;
                return new TradeStationSearchResultDto();
            }
        };

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName(null);

            JsonObject params = new JsonObject();
            params.addProperty("radius", 40);
            params.addProperty("priority", "profit");

            query.handle("query_trade_candidates", params, "一番儲かる交易候補");

            assertFalse(searchCalled[0], "External search must NOT be called when location is unknown");
            assertNotNull(capturedDto[0]);
            assertEquals("location_unknown", capturedDto[0].status());
            assertNull(capturedDto[0].currentSystem());
            assertEquals(40, capturedDto[0].searchRadiusLy());
            assertEquals("profit", capturedDto[0].priority());
            assertTrue(capturedDto[0].candidates().isEmpty());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testProfileUnavailableWhenNullOrMaxCargoZeroWithoutSearch() throws Exception {
        boolean[] searchCalled = {false};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                searchCalled[0] = true;
                return new TradeStationSearchResultDto();
            }
        };

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeRouteSearchCriteria[] profileToReturn = {null};

        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            TradeRouteSearchCriteria getTradeProfile() {
                return profileToReturn[0];
            }

            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");

            // Case 1: profile is null
            profileToReturn[0] = null;
            query.handle("query_trade_candidates", new JsonObject(), "交易候補");

            assertFalse(searchCalled[0], "Search must NOT be called when profile is null");
            assertNotNull(capturedDto[0]);
            assertEquals("profile_unavailable", capturedDto[0].status());
            assertEquals("Shinrarta Dezhra", capturedDto[0].currentSystem());
            assertEquals(30, capturedDto[0].searchRadiusLy(), "Unspecified radius should default to 30");

            // Case 2: profile has maxCargo = 0
            capturedDto[0] = null;
            TradeRouteSearchCriteria zeroCargo = new TradeRouteSearchCriteria();
            zeroCargo.setMaxCargo(0);
            profileToReturn[0] = zeroCargo;

            JsonObject params = new JsonObject();
            params.addProperty("radius", 51); // out of bounds, should normalize to 50
            query.handle("query_trade_candidates", params, "交易候補");

            assertFalse(searchCalled[0], "Search must NOT be called when maxCargo <= 0");
            assertNotNull(capturedDto[0]);
            assertEquals("profile_unavailable", capturedDto[0].status());
            assertEquals(50, capturedDto[0].searchRadiusLy(), "Radius 51 should be normalized to 50");
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testNormalFlowWithTradeCandidates() throws Exception {
        Instant now = Instant.now();
        String freshTime = now.minus(1, ChronoUnit.HOURS).toString();

        String s1Json = """
                {
                  "id": "s1",
                  "system_name": "Shinrarta Dezhra",
                  "name": "Jameson Memorial",
                  "distance": 0.0,
                  "distance_to_arrival": 300.0,
                  "market_updated_at": "%s",
                  "system_x": 0.0,
                  "system_y": 0.0,
                  "system_z": 0.0,
                  "market": [
                    {"commodity": "Gold", "buy_price": 4000, "sell_price": 0, "supply": 1000, "demand": 0}
                  ]
                }
                """.formatted(freshTime);

        String s2Json = """
                {
                  "id": "s2",
                  "system_name": "Sol",
                  "name": "Columbus",
                  "distance": 15.0,
                  "distance_to_arrival": 500.0,
                  "market_updated_at": "%s",
                  "system_x": 10.0,
                  "system_y": 0.0,
                  "system_z": 0.0,
                  "market": [
                    {"commodity": "Gold", "buy_price": 0, "sell_price": 9000, "supply": 0, "demand": 1000}
                  ]
                }
                """.formatted(freshTime);

        StationResult s1 = GsonFactory.getGson().fromJson(s1Json, StationResult.class);
        StationResult s2 = GsonFactory.getGson().fromJson(s2Json, StationResult.class);

        TradeStationSearchResultDto searchDto = new TradeStationSearchResultDto();
        searchDto.setResults(List.of(s1, s2));

        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                return searchDto;
            }
        };

        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);
        profile.setStartingCapital(0);

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            TradeRouteSearchCriteria getTradeProfile() {
                return profile;
            }

            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock response");
                return res;
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");

            JsonObject params = new JsonObject();
            params.addProperty("radius", 0); // should be rounded to 50
            params.addProperty("priority", "nearest");

            query.handle("query_trade_candidates", params, "近くて儲かる交易候補");

            assertNotNull(capturedDto[0]);
            assertEquals("insufficient_fresh_data", capturedDto[0].status()); // 1 candidate found
            assertEquals("Shinrarta Dezhra", capturedDto[0].currentSystem());
            assertEquals("Shinrarta Dezhra", capturedDto[0].searchedFromSystem());
            assertEquals("current", capturedDto[0].referenceSource());
            assertEquals(2, capturedDto[0].freshStationCount());
            assertEquals("nearest", capturedDto[0].priority());
            assertEquals(50, capturedDto[0].searchRadiusLy()); // 0 normalized to 50
            assertEquals(1, capturedDto[0].candidates().size());

            var c = capturedDto[0].candidates().get(0);
            assertEquals(1, c.rank());
            assertEquals("Gold", c.commodity());
            assertEquals("Shinrarta Dezhra", c.buySystem());
            assertEquals("Jameson Memorial", c.buyStation());
            assertEquals("Sol", c.sellSystem());
            assertEquals("Columbus", c.sellStation());
            assertEquals(5000, c.unitProfit());
            assertEquals(100, c.units());
            assertEquals(500000L, c.tripProfit());
            assertEquals(10.0, c.routeDistanceLy());
            assertNotNull(capturedDto[0].toYaml());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testAllActionParameterSpecsPassValidate() {
        TradeCandidatesQuery query = new TradeCandidatesQuery();
        List<ActionParameterSpec> specs = query.parameters();
        assertNotNull(specs);
        assertFalse(specs.isEmpty());
        for (ActionParameterSpec spec : specs) {
            assertDoesNotThrow(spec::validate, "ActionParameterSpec '" + spec.getName() + "' must be valid");
        }
    }

    @Test
    void testExtractRadiusParam() {
        TradeCandidatesQuery query = new TradeCandidatesQuery();

        // 40 (integer) -> 40
        JsonObject p40 = new JsonObject();
        p40.addProperty("radius", 40);
        assertEquals(40, query.extractRadiusParam(p40));

        // 40.0 (double) -> 40
        JsonObject p40Double = new JsonObject();
        p40Double.addProperty("radius", 40.0);
        assertEquals(40, query.extractRadiusParam(p40Double));

        // "abc" (non-numeric string) -> default 30
        JsonObject pAbc = new JsonObject();
        pAbc.addProperty("radius", "abc");
        assertEquals(30, query.extractRadiusParam(pAbc));

        // 40.4 -> 40, 40.6 -> 41 (rounding check)
        JsonObject p404 = new JsonObject();
        p404.addProperty("radius", 40.4);
        assertEquals(40, query.extractRadiusParam(p404));

        JsonObject p406 = new JsonObject();
        p406.addProperty("radius", 40.6);
        assertEquals(41, query.extractRadiusParam(p406));

        // null or empty -> default 30
        assertEquals(30, query.extractRadiusParam(new JsonObject()));
        assertEquals(30, query.extractRadiusParam(null));
    }

    @Test
    void testRadiusVariantsInHandle() throws Exception {
        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(null) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName(null);

            // 40
            JsonObject p40 = new JsonObject();
            p40.addProperty("radius", 40);
            query.handle("query_trade_candidates", p40, "query");
            assertEquals(40, capturedDto[0].searchRadiusLy());

            // 40.0
            JsonObject p40Double = new JsonObject();
            p40Double.addProperty("radius", 40.0);
            query.handle("query_trade_candidates", p40Double, "query");
            assertEquals(40, capturedDto[0].searchRadiusLy());

            // "abc" -> default 30
            JsonObject pAbc = new JsonObject();
            pAbc.addProperty("radius", "abc");
            query.handle("query_trade_candidates", pAbc, "query");
            assertEquals(30, capturedDto[0].searchRadiusLy());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testNumberFormattingRules() {
        // Standard expected values
        assertEquals("5,103,950", TradeCandidatesQuery.formatInteger(5103950));
        assertEquals("12.61", TradeCandidatesQuery.formatLightYears(12.6115723766904));
        assertEquals("473", TradeCandidatesQuery.formatLightSeconds(473.444649));

        // 0 handling
        assertEquals("0", TradeCandidatesQuery.formatInteger(0));
        assertEquals("0.00", TradeCandidatesQuery.formatLightYears(0.0));
        assertEquals("0", TradeCandidatesQuery.formatLightSeconds(0.0));

        // null handling
        assertNull(TradeCandidatesQuery.formatInteger(null));
        assertNull(TradeCandidatesQuery.formatLightYears(null));
        assertNull(TradeCandidatesQuery.formatLightSeconds(null));

        // Locale independence (e.g. Locale.GERMANY where commas and dots differ)
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("5,103,950", TradeCandidatesQuery.formatInteger(5103950));
            assertEquals("12.61", TradeCandidatesQuery.formatLightYears(12.6115723766904));
            assertEquals("473", TradeCandidatesQuery.formatLightSeconds(473.444649));
            assertEquals("0", TradeCandidatesQuery.formatInteger(0));
            assertEquals("0.00", TradeCandidatesQuery.formatLightYears(0.0));
            assertEquals("0", TradeCandidatesQuery.formatLightSeconds(0.0));
            assertNull(TradeCandidatesQuery.formatInteger(null));
            assertNull(TradeCandidatesQuery.formatLightYears(null));
            assertNull(TradeCandidatesQuery.formatLightSeconds(null));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void testTradeCandidatesDataDtoCarriesFormattedDisplayFields() {
        elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidate rawCandidate =
                new elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidate(
                        1,
                        "Gold",
                        "Shinrarta Dezhra",
                        "Jameson Memorial",
                        1000,
                        50000L,
                        473.444649,
                        "2026-09-26T00:00:00Z",
                        "Sol",
                        "Columbus",
                        6000,
                        100000L,
                        120.0,
                        "2026-09-26T01:00:00Z",
                        5000,
                        100,
                        500000L,
                        12.6115723766904,
                        30.5
                );

        TradeCandidatesDataDto dto = TradeCandidatesDataDto.create(
                "ok",
                "Shinrarta Dezhra",
                "profit",
                30,
                List.of(rawCandidate)
        );

        assertEquals("30", dto.searchRadiusLyDisplay());
        assertEquals(1, dto.candidates().size());
        TradeCandidatesQuery.TradeCandidateDto c = dto.candidates().get(0);
        assertEquals("1,000", c.buyPriceDisplay());
        assertEquals("50,000", c.supplyDisplay());
        assertEquals("473", c.buyStationDistanceLsDisplay());
        assertEquals("6,000", c.sellPriceDisplay());
        assertEquals("100,000", c.demandDisplay());
        assertEquals("120", c.sellStationDistanceLsDisplay());
        assertEquals("5,000", c.unitProfitDisplay());
        assertEquals("100", c.unitsDisplay());
        assertEquals("500,000", c.tripProfitDisplay());
        assertEquals("12.61", c.distanceFromCurrentLyDisplay());
        assertEquals("30.50", c.routeDistanceLyDisplay());
        assertNotNull(dto.toYaml());
    }

    @Test
    void testExtractReferenceSystemParam() {
        TradeCandidatesQuery query = new TradeCandidatesQuery();

        JsonObject p1 = new JsonObject();
        p1.addProperty("referenceSystem", "Sol");
        assertEquals("Sol", query.extractReferenceSystemParam(p1));

        JsonObject p2 = new JsonObject();
        p2.addProperty("referenceSystem", "  Achenar  ");
        assertEquals("Achenar", query.extractReferenceSystemParam(p2));

        JsonObject p3 = new JsonObject();
        p3.addProperty("referenceSystem", "");
        assertNull(query.extractReferenceSystemParam(p3));

        JsonObject p4 = new JsonObject();
        p4.addProperty("referenceSystem", "   ");
        assertNull(query.extractReferenceSystemParam(p4));

        assertNull(query.extractReferenceSystemParam(new JsonObject()));
        assertNull(query.extractReferenceSystemParam(null));
    }

    @Test
    void testReferenceSystemExplicitUsageSetsCriteriaAndReferenceSource() throws Exception {
        String[] capturedCriteriaSystem = {null};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                capturedCriteriaSystem[0] = criteria.getReferenceSystem();
                return new TradeStationSearchResultDto();
            }
        };

        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            TradeRouteSearchCriteria getTradeProfile() {
                return profile;
            }

            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");

            JsonObject params = new JsonObject();
            params.addProperty("referenceSystem", "Sol");
            query.handle("query_trade_candidates", params, "Sol 周辺の交易候補");

            assertEquals("Sol", capturedCriteriaSystem[0], "Spansh criteria must use explicit reference system");
            assertNotNull(capturedDto[0]);
            assertEquals("Sol", capturedDto[0].searchedFromSystem());
            assertEquals("specified", capturedDto[0].referenceSource());
            assertEquals("Shinrarta Dezhra", capturedDto[0].currentSystem());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testReferenceSystemOmittedDefaultsToCurrentLocation() throws Exception {
        String[] capturedCriteriaSystem = {null};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                capturedCriteriaSystem[0] = criteria.getReferenceSystem();
                return new TradeStationSearchResultDto();
            }
        };

        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            TradeRouteSearchCriteria getTradeProfile() {
                return profile;
            }

            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");

            JsonObject params = new JsonObject();
            query.handle("query_trade_candidates", params, "交易候補を探して");

            assertEquals("Shinrarta Dezhra", capturedCriteriaSystem[0], "Spansh criteria must default to current primary star");
            assertNotNull(capturedDto[0]);
            assertEquals("Shinrarta Dezhra", capturedDto[0].searchedFromSystem());
            assertEquals("current", capturedDto[0].referenceSource());
            assertEquals("Shinrarta Dezhra", capturedDto[0].currentSystem());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testLocationUnknownWhenBothReferenceSystemAndCurrentLocationMissing() throws Exception {
        boolean[] searchCalled = {false};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                searchCalled[0] = true;
                return new TradeStationSearchResultDto();
            }
        };

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName(null);

            JsonObject params = new JsonObject();
            query.handle("query_trade_candidates", params, "交易候補を探して");

            assertFalse(searchCalled[0], "External search must not be called when location is unknown");
            assertNotNull(capturedDto[0]);
            assertEquals("location_unknown", capturedDto[0].status());
            assertNull(capturedDto[0].searchedFromSystem());
            assertNull(capturedDto[0].referenceSource());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testReferenceSystemSpecifiedWorksEvenWhenCurrentLocationIsNull() throws Exception {
        String[] capturedCriteriaSystem = {null};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                capturedCriteriaSystem[0] = criteria.getReferenceSystem();
                return new TradeStationSearchResultDto();
            }
        };

        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            TradeRouteSearchCriteria getTradeProfile() {
                return profile;
            }

            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName(null);

            JsonObject params = new JsonObject();
            params.addProperty("referenceSystem", "Sol");
            query.handle("query_trade_candidates", params, "Sol 周辺の交易候補");

            assertEquals("Sol", capturedCriteriaSystem[0]);
            assertNotNull(capturedDto[0]);
            assertEquals("Sol", capturedDto[0].searchedFromSystem());
            assertEquals("specified", capturedDto[0].referenceSource());
            assertNull(capturedDto[0].currentSystem());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testTooFewStationsWhenSpanshReturnsZeroOrOneStation() throws Exception {
        TradeStationSearchResultDto emptyDto = new TradeStationSearchResultDto();
        emptyDto.setResults(List.of());

        TradeStationSearchResultDto[] toReturn = {emptyDto};
        TradeCandidatesSearchClient mockClient = new TradeCandidatesSearchClient() {
            @Override
            public TradeStationSearchResultDto searchTradeStations(TradeCandidatesSearchCriteria criteria) {
                return toReturn[0];
            }
        };

        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        TradeCandidatesDataDto[] capturedDto = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery(mockClient) {
            @Override
            TradeRouteSearchCriteria getTradeProfile() {
                return profile;
            }

            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                if (struct.getData() instanceof TradeCandidatesDataDto dto) {
                    capturedDto[0] = dto;
                }
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName("Shinrarta Dezhra");

            // Case 1: 0 stations returned (or unknown system) -> status=too_few_stations, freshStationCount=0
            toReturn[0] = emptyDto;
            query.handle("query_trade_candidates", new JsonObject(), "交易候補");
            assertNotNull(capturedDto[0]);
            assertEquals("too_few_stations", capturedDto[0].status());
            assertEquals(0, capturedDto[0].freshStationCount());

            // Case 2: 1 station returned -> status=too_few_stations, freshStationCount=1
            String s1Json = """
                    {
                      "id": "s1",
                      "system_name": "Shinrarta Dezhra",
                      "name": "Jameson Memorial",
                      "distance": 0.0,
                      "distance_to_arrival": 300.0,
                      "market_updated_at": "%s"
                    }
                    """.formatted(Instant.now().minus(1, ChronoUnit.HOURS).toString());
            StationResult s1 = GsonFactory.getGson().fromJson(s1Json, StationResult.class);
            TradeStationSearchResultDto singleDto = new TradeStationSearchResultDto();
            singleDto.setResults(List.of(s1));

            toReturn[0] = singleDto;
            capturedDto[0] = null;
            query.handle("query_trade_candidates", new JsonObject(), "交易候補");
            assertNotNull(capturedDto[0]);
            assertEquals("too_few_stations", capturedDto[0].status());
            assertEquals(1, capturedDto[0].freshStationCount());
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }

    @Test
    void testBuildInstructionsConciseSentenceAndCreditsRule() throws Exception {
        String[] capturedInstruction = {null};
        TradeCandidatesQuery query = new TradeCandidatesQuery() {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                capturedInstruction[0] = struct.getInstructions();
                return new JsonObject();
            }
        };

        String prevStar = PlayerSession.getInstance().getPrimaryStarName();
        try {
            PlayerSession.getInstance().setCurrentPrimaryStarName(null);
            query.handle("query_trade_candidates", new JsonObject(), "交易候補");

            assertNotNull(capturedInstruction[0]);
            String inst = capturedInstruction[0];
            // Rule 1: exactly one concise sentence per candidate
            assertTrue(inst.contains("one concise sentence per candidate"),
                    "Instructions must state one concise sentence per candidate");
            // Rule 2: Currency explicitly stated as credits, never yen
            assertTrue(inst.contains("Currency must always be explicitly stated as credits"),
                    "Instructions must require currency as credits");
            assertTrue(inst.contains("yen (円)"),
                    "Instructions must explicitly prohibit yen");
        } finally {
            PlayerSession.getInstance().setCurrentPrimaryStarName(prevStar);
        }
    }
}

