package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
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
}
