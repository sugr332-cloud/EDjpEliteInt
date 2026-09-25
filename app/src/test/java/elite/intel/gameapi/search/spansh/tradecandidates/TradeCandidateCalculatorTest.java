package elite.intel.gameapi.search.spansh.tradecandidates;

import com.google.gson.JsonObject;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult.Commodity;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto.StationResult.MarketEntry;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidate;
import elite.intel.gameapi.search.spansh.tradecandidates.TradeCandidateCalculator.TradeCandidatesResult;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import elite.intel.util.json.GsonFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TradeCandidateCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void testMarketFreshnessBoundary() {
        // Exactly 10 hours ago -> fresh (inclusive boundary)
        String exact10h = NOW.minus(10, ChronoUnit.HOURS).toString();
        assertTrue(TradeCandidateCalculator.isMarketFresh(exact10h, NOW), "Exactly 10 hours must be fresh");

        // 9 hours and 59 minutes ago -> fresh
        String within10h = NOW.minus(9, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES).toString();
        assertTrue(TradeCandidateCalculator.isMarketFresh(within10h, NOW), "Within 10 hours must be fresh");

        // 10 hours and 1 second ago -> stale (exclusive boundary)
        String stale1s = NOW.minus(10, ChronoUnit.HOURS).minus(1, ChronoUnit.SECONDS).toString();
        assertFalse(TradeCandidateCalculator.isMarketFresh(stale1s, NOW), "10 hours and 1 second must NOT be fresh");

        // 11 hours ago -> stale
        String stale11h = NOW.minus(11, ChronoUnit.HOURS).toString();
        assertFalse(TradeCandidateCalculator.isMarketFresh(stale11h, NOW), "11 hours must NOT be fresh");

        // 4 minutes in the future (within 5 minutes tolerance) -> fresh
        String future4m = NOW.plus(4, ChronoUnit.MINUTES).toString();
        assertTrue(TradeCandidateCalculator.isMarketFresh(future4m, NOW), "4 minutes in future must be fresh (within 5m skew tolerance)");

        // 6 minutes in the future (exceeds 5 minutes tolerance) -> stale
        String future6m = NOW.plus(6, ChronoUnit.MINUTES).toString();
        assertFalse(TradeCandidateCalculator.isMarketFresh(future6m, NOW), "6 minutes in future must NOT be fresh (exceeds 5m tolerance)");

        // null / empty / malformed -> not fresh
        assertFalse(TradeCandidateCalculator.isMarketFresh(null, NOW));
        assertFalse(TradeCandidateCalculator.isMarketFresh("", NOW));
        assertFalse(TradeCandidateCalculator.isMarketFresh("invalid-date", NOW));
    }

    @Test
    void testSameStationNotPaired() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        StationResult s1 = createStation("station1", "Alpha", "Starport 1", 0.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(
                        createMarketEntry("Gold", 1000, 0, 5000L, 0L),
                        createMarketEntry("Gold", 0, 5000, 0L, 5000L)
                ), null);

        TradeCandidatesResult result = TradeCandidateCalculator.calculate(List.of(s1), profile, "profit", NOW);
        assertEquals("no_result", result.status());
        assertTrue(result.candidates().isEmpty(), "Single station cannot trade with itself (A != B)");
    }

    @Test
    void testNullDistanceRankedLastInNearestSort() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        // Buy station 1: distance 10.0 ly
        StationResult b1 = createStation("b1", "Sys1", "Buy1", 10.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(createMarketEntry("Gold", 1000, 0, 500L, 0L)), null);

        // Buy station 2: distance null
        StationResult b2 = createStation("b2", "Sys2", "Buy2", null, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 5.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 1000, 0, 500L, 0L)), null);

        // Buy station 3: distance 5.0 ly
        StationResult b3 = createStation("b3", "Sys3", "Buy3", 5.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 10.0, 0.0, 0.0,
                List.of(createMarketEntry("Silver", 1000, 0, 500L, 0L)), null);

        // Destination station for all
        StationResult dest = createStation("dest", "DestSys", "DestStation", 20.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 20.0, 0.0, 0.0,
                List.of(
                        createMarketEntry("Gold", 0, 5000, 0L, 500L),
                        createMarketEntry("Painite", 0, 50000, 0L, 500L),
                        createMarketEntry("Silver", 0, 3000, 0L, 500L)
                ), null);

        TradeCandidatesResult result = TradeCandidateCalculator.calculate(
                List.of(b1, b2, b3, dest), profile, "nearest", NOW
        );

        assertEquals("ok", result.status());
        assertEquals(3, result.candidates().size());

        // 1st: b3 (distance 5.0)
        assertEquals("Silver", result.candidates().get(0).commodity());
        assertEquals(5.0, result.candidates().get(0).distanceFromCurrentLy());

        // 2nd: b1 (distance 10.0)
        assertEquals("Gold", result.candidates().get(1).commodity());
        assertEquals(10.0, result.candidates().get(1).distanceFromCurrentLy());

        // 3rd: b2 (distance null, ranked last despite huge tripProfit)
        assertEquals("Painite", result.candidates().get(2).commodity());
        assertNull(result.candidates().get(2).distanceFromCurrentLy(), "Null distance must be preserved as null");
    }

    @Test
    void testSameStationNameInDifferentSystemsPreservedAsDistinctCandidates() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        // Two stations with same name "Columbus" in different star systems
        StationResult s1 = createStation("col_sol", "Sol", "Columbus", 10.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(createMarketEntry("Gold", 1000, 0, 500L, 0L)), null);

        StationResult s2 = createStation("col_alpha", "Alpha Centauri", "Columbus", 15.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 4.0, 0.0, 0.0,
                List.of(createMarketEntry("Silver", 1000, 0, 500L, 0L)), null);

        StationResult dest = createStation("dest", "Sirius", "Patterson", 20.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 8.0, 0.0, 0.0,
                List.of(
                        createMarketEntry("Gold", 0, 3000, 0L, 500L),
                        createMarketEntry("Silver", 0, 2500, 0L, 500L)
                ), null);

        TradeCandidatesResult result = TradeCandidateCalculator.calculate(
                List.of(s1, s2, dest), profile, "profit", NOW
        );

        assertEquals(2, result.candidates().size());
        assertEquals("Sol", result.candidates().get(0).buySystem());
        assertEquals("Columbus", result.candidates().get(0).buyStation());

        assertEquals("Alpha Centauri", result.candidates().get(1).buySystem());
        assertEquals("Columbus", result.candidates().get(1).buyStation());
    }

    @Test
    void testSameRouteLegKeepsHighestProfitCommodityAndProducesDistinctLegs() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);

        // Leg 1: PortA -> PortB has Gold (profit 300,000) and Silver (profit 200,000)
        StationResult portA = createStation("pA", "SysA", "PortA", 5.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(
                        createMarketEntry("Gold", 1000, 0, 500L, 0L),
                        createMarketEntry("Silver", 1000, 0, 500L, 0L)
                ), null);

        StationResult portB = createStation("pB", "SysB", "PortB", 10.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 5.0, 0.0, 0.0,
                List.of(
                        createMarketEntry("Gold", 0, 4000, 0L, 500L),   // profit 3000 * 100 = 300,000
                        createMarketEntry("Silver", 0, 3000, 0L, 500L)  // profit 2000 * 100 = 200,000
                ), null);

        // Leg 2: PortC -> PortD has Painite (profit 250,000)
        StationResult portC = createStation("pC", "SysC", "PortC", 15.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 10.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 1000, 0, 500L, 0L)), null);

        StationResult portD = createStation("pD", "SysD", "PortD", 20.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 15.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 0, 3500, 0L, 500L)), null); // profit 2500 * 100 = 250,000

        // Leg 3: PortE -> PortF has Bauxite (profit 150,000)
        StationResult portE = createStation("pE", "SysE", "PortE", 25.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 20.0, 0.0, 0.0,
                List.of(createMarketEntry("Bauxite", 500, 0, 500L, 0L)), null);

        StationResult portF = createStation("pF", "SysF", "PortF", 30.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 25.0, 0.0, 0.0,
                List.of(createMarketEntry("Bauxite", 0, 2000, 0L, 500L)), null); // profit 1500 * 100 = 150,000

        TradeCandidatesResult result = TradeCandidateCalculator.calculate(
                List.of(portA, portB, portC, portD, portE, portF), profile, "profit", NOW
        );

        assertEquals("ok", result.status());
        assertEquals(3, result.candidates().size());

        // 1st: Gold (PortA -> PortB, profit 300,000)
        assertEquals("Gold", result.candidates().get(0).commodity());
        assertEquals("PortA", result.candidates().get(0).buyStation());
        assertEquals("PortB", result.candidates().get(0).sellStation());
        assertEquals(300000L, result.candidates().get(0).tripProfit());

        // 2nd: Painite (PortC -> PortD, profit 250,000) - Silver was pruned from Leg 1!
        assertEquals("Painite", result.candidates().get(1).commodity());
        assertEquals("PortC", result.candidates().get(1).buyStation());
        assertEquals("PortD", result.candidates().get(1).sellStation());
        assertEquals(250000L, result.candidates().get(1).tripProfit());

        // 3rd: Bauxite (PortE -> PortF, profit 150,000)
        assertEquals("Bauxite", result.candidates().get(2).commodity());
        assertEquals("PortE", result.candidates().get(2).buyStation());
        assertEquals("PortF", result.candidates().get(2).sellStation());
        assertEquals(150000L, result.candidates().get(2).tripProfit());

        // All 3 candidates are from distinct legs
        assertNotEquals(result.candidates().get(0).buyStation(), result.candidates().get(1).buyStation());
        assertNotEquals(result.candidates().get(1).buyStation(), result.candidates().get(2).buyStation());
        assertNotEquals(result.candidates().get(0).buyStation(), result.candidates().get(2).buyStation());
    }

    @Test
    void testStartingCapitalZeroMeansNoCapitalConstraint() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(200);
        profile.setStartingCapital(0); // 0 means unbounded by capital

        StationResult buyStation = createStation("s1", "Alpha", "Buy Port", 5.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 50000, 0, 1000L, 0L)), null);

        StationResult sellStation = createStation("s2", "Beta", "Sell Port", 15.0, 200.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 10.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 0, 80000, 0L, 1000L)), null);

        TradeCandidatesResult result = TradeCandidateCalculator.calculate(
                List.of(buyStation, sellStation), profile, "profit", NOW
        );

        assertEquals("insufficient_fresh_data", result.status()); // only 1 candidate found
        assertEquals(1, result.candidates().size());
        TradeCandidate c = result.candidates().get(0);
        assertEquals(200, c.units(), "Units should equal maxCargo when startingCapital is 0");
        assertEquals(30000, c.unitProfit());
        assertEquals(6000000L, c.tripProfit()); // 30000 * 200
    }

    @Test
    void testStartingCapitalLimitsUnitsWhenPositive() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(200);
        profile.setStartingCapital(250000); // 250,000 credits / 50,000 buyPrice = 5 units max

        StationResult buyStation = createStation("s1", "Alpha", "Buy Port", 5.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 50000, 0, 1000L, 0L)), null);

        StationResult sellStation = createStation("s2", "Beta", "Sell Port", 15.0, 200.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 10.0, 0.0, 0.0,
                List.of(createMarketEntry("Painite", 0, 80000, 0L, 1000L)), null);

        TradeCandidatesResult result = TradeCandidateCalculator.calculate(
                List.of(buyStation, sellStation), profile, "profit", NOW
        );

        assertEquals(1, result.candidates().size());
        TradeCandidate c = result.candidates().get(0);
        assertEquals(5, c.units(), "Units should be constrained by startingCapital (250000 / 50000 = 5)");
        assertEquals(150000L, c.tripProfit()); // 30000 * 5
    }

    @Test
    void testProhibitedCommodityExclusion() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setMaxCargo(100);
        profile.setAllowProhibited(false); // Do not allow prohibited

        StationResult buyStation = createStation("s1", "Alpha", "Buy Port", 5.0, 100.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 0.0, 0.0, 0.0,
                List.of(createMarketEntry("Imperial Slaves", 10000, 0, 500L, 0L)), null);

        StationResult sellStation = createStation("s2", "Beta", "Sell Port", 15.0, 200.0,
                NOW.minus(1, ChronoUnit.HOURS).toString(), 10.0, 0.0, 0.0,
                List.of(createMarketEntry("Imperial Slaves", 0, 15000, 0L, 500L)),
                List.of(createCommodity("Imperial Slaves")));

        // Should be excluded because sellStation prohibits Imperial Slaves
        TradeCandidatesResult result = TradeCandidateCalculator.calculate(
                List.of(buyStation, sellStation), profile, "profit", NOW
        );
        assertEquals("no_result", result.status());

        // Now allow prohibited
        profile.setAllowProhibited(true);
        TradeCandidatesResult allowedResult = TradeCandidateCalculator.calculate(
                List.of(buyStation, sellStation), profile, "profit", NOW
        );
        assertEquals(1, allowedResult.candidates().size());
        assertEquals("Imperial Slaves", allowedResult.candidates().get(0).commodity());
    }

    private static StationResult createStation(
            String id, String systemName, String stationName, Double distFromCurrent, Double distLs,
            String marketUpdatedAt, Double x, Double y, Double z,
            List<MarketEntry> market, List<Commodity> prohibited
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("system_name", systemName);
        json.addProperty("name", stationName);
        if (distFromCurrent != null) {
            json.addProperty("distance", distFromCurrent);
        }
        json.addProperty("distance_to_arrival", distLs);
        json.addProperty("market_updated_at", marketUpdatedAt);
        json.addProperty("system_x", x);
        json.addProperty("system_y", y);
        json.addProperty("system_z", z);
        if (market != null) {
            json.add("market", GsonFactory.getGson().toJsonTree(market));
        }
        if (prohibited != null) {
            json.add("prohibited_commodities", GsonFactory.getGson().toJsonTree(prohibited));
        }
        return GsonFactory.getGson().fromJson(json, StationResult.class);
    }

    private static MarketEntry createMarketEntry(String commodity, int buyPrice, int sellPrice, long supply, long demand) {
        JsonObject json = new JsonObject();
        json.addProperty("commodity", commodity);
        json.addProperty("buy_price", buyPrice);
        json.addProperty("sell_price", sellPrice);
        json.addProperty("supply", supply);
        json.addProperty("demand", demand);
        return GsonFactory.getGson().fromJson(json, MarketEntry.class);
    }

    private static Commodity createCommodity(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        return GsonFactory.getGson().fromJson(json, Commodity.class);
    }
}
