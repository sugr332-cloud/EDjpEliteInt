package elite.intel.gameapi.search.spansh.station.refuel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchCriteria;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchResultDto;
import elite.intel.gameapi.search.spansh.station.refuel.OrbitalStationSearch.OrbitalStation;
import elite.intel.util.ShipPadSizes;
import elite.intel.util.json.GsonFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "The nearest space station" is a type filter plus the pad rule. Spansh matches nothing against a type it
 * does not know, so the serialized body is asserted, and the ranking is asserted against a captured page.
 */
class OrbitalStationSearchTest {

    @Test
    void onlyOrbitalTypesAreSearchedAndMegaShipsAreLeftOut() {
        JsonObject filters = filtersOf(OrbitalStationSearch.searchCriteria(
                1, 2, 3, 40, RefuelStationSearch.PadFilter.ANY));

        List<String> types = filters.getAsJsonObject("type").getAsJsonArray("value").asList().stream()
                .map(element -> element.getAsString()).toList();
        assertEquals(List.of("Asteroid base", "Coriolis Starport", "Dodec Starport",
                "Ocellus Starport", "Orbis Starport", "Outpost"), types);
        assertFalse(types.contains("Mega ship"));
        assertFalse(types.contains("Drake-Class Carrier"), () -> "carriers move: " + types);
        assertFalse(types.contains("Planetary Port"));
        assertFalse(types.contains("Planetary Outpost"));
        assertFalse(types.contains("Settlement"));
    }

    @Test
    void noServiceOrMarketIsRequired() {
        JsonObject filters = filtersOf(OrbitalStationSearch.searchCriteria(
                1, 2, 3, 40, RefuelStationSearch.PadFilter.ANY));

        assertFalse(filters.has("services"));
        assertFalse(filters.has("marketplace"));
    }

    @Test
    void theRadiusAndArrivalCapMatchTheRefuelSearch() {
        JsonObject filters = filtersOf(OrbitalStationSearch.searchCriteria(
                1, 2, 3, 40, RefuelStationSearch.PadFilter.ANY));

        assertEquals("0", filters.getAsJsonObject("distance").get("min").getAsString());
        assertEquals("40", filters.getAsJsonObject("distance").get("max").getAsString());
        assertEquals(25_000, filters.getAsJsonObject("distance_to_arrival").getAsJsonArray("value").get(1).getAsInt());
    }

    @Test
    void padFiltersFollowTheShipSize() {
        assertEquals(List.of(RefuelStationSearch.PadFilter.LARGE), RefuelStationSearch.padFilters(ShipPadSizes.LARGE));
        assertEquals(List.of(RefuelStationSearch.PadFilter.MEDIUM, RefuelStationSearch.PadFilter.LARGE),
                RefuelStationSearch.padFilters(ShipPadSizes.MEDIUM));

        JsonObject large = filtersOf(OrbitalStationSearch.searchCriteria(
                1, 2, 3, 40, RefuelStationSearch.PadFilter.LARGE));
        assertTrue(large.has("large_pads"));
        assertFalse(large.has("medium_pads"));

        JsonObject medium = filtersOf(OrbitalStationSearch.searchCriteria(
                1, 2, 3, 40, RefuelStationSearch.PadFilter.MEDIUM));
        assertTrue(medium.has("medium_pads"));
        assertFalse(medium.has("large_pads"));
    }

    @Test
    void theRadiusWidensLikeTheRefuelSearch() {
        assertEquals(List.of(40, 80, 1000), RefuelStationSearch.radiiToTry(40));
    }

    @Test
    void groundPortsSettlementsCarriersAndMegaShipsAreNeverAnswers() {
        List<OrbitalStation> found = OrbitalStationSearch.rank(mixedPage(), ShipPadSizes.SMALL, 0);

        assertEquals(List.of("Ehrlich City"), found.stream().map(OrbitalStation::stationName).toList());
    }

    @Test
    void aLargeShipIsNotOfferedAnOutpost() {
        List<OrbitalStation> found = OrbitalStationSearch.rank(outpostPage(), ShipPadSizes.LARGE, 0);

        assertEquals(List.of("Big Coriolis"), found.stream().map(OrbitalStation::stationName).toList());
    }

    @Test
    void aMediumShipMayUseAnOutpost() {
        List<OrbitalStation> found = OrbitalStationSearch.rank(outpostPage(), ShipPadSizes.MEDIUM, 0);

        assertEquals("Near Outpost", found.getFirst().stationName());
    }

    @Test
    void theNearestComesFirstThenTheShorterSupercruise() {
        List<OrbitalStation> found = OrbitalStationSearch.rank(sameSystemPage(), ShipPadSizes.SMALL, 0);

        assertEquals(List.of("Walz Depot", "Haberlandt Survey", "Far Orbis"),
                found.stream().map(OrbitalStation::stationName).toList());
    }

    @Test
    void theStationTheCommanderIsDockedAtIsSkippedAndTheNextOneWins() {
        List<OrbitalStation> found = OrbitalStationSearch.rank(sameSystemPage(), ShipPadSizes.SMALL, 111L);

        assertEquals("Haberlandt Survey", found.getFirst().stationName());
        assertTrue(found.stream().noneMatch(station -> station.stationName().equals("Walz Depot")));
    }

    private static List<TradeStationSearchResultDto.StationResult> stations(String json) {
        return GsonFactory.getGson().fromJson(json, TradeStationSearchResultDto.class).getResults();
    }

    private static JsonObject filtersOf(TradeStationSearchCriteria criteria) {
        return JsonParser.parseString(criteria.toJson()).getAsJsonObject().getAsJsonObject("filters");
    }

    private static List<TradeStationSearchResultDto.StationResult> mixedPage() {
        return stations("""
                {"results":[
                  {"name":"Aithal Garrison","system_name":"Sol","type":"Settlement","distance":0.0,
                   "distance_to_arrival":10.0,"small_pads":2,"medium_pads":0,"large_pads":0},
                  {"name":"Ground Port","system_name":"Sol","type":"Planetary Port","distance":0.0,
                   "distance_to_arrival":20.0,"small_pads":4,"medium_pads":4,"large_pads":4},
                  {"name":"Ground Outpost","system_name":"Sol","type":"Planetary Outpost","distance":0.0,
                   "distance_to_arrival":30.0,"small_pads":4,"medium_pads":4,"large_pads":0},
                  {"name":"Drifting Carrier","system_name":"Sol","type":"Drake-Class Carrier","distance":0.0,
                   "distance_to_arrival":40.0,"small_pads":4,"medium_pads":4,"large_pads":4},
                  {"name":"Weekly Mega","system_name":"Sol","type":"Mega ship","distance":1.0,
                   "distance_to_arrival":50.0,"small_pads":4,"medium_pads":4,"large_pads":4},
                  {"name":"Ehrlich City","system_name":"Alpha Centauri","type":"Coriolis Starport","distance":9.7,
                   "distance_to_arrival":153.0,"small_pads":8,"medium_pads":8,"large_pads":8}
                ]}
                """);
    }

    private static List<TradeStationSearchResultDto.StationResult> outpostPage() {
        return stations("""
                {"results":[
                  {"name":"Near Outpost","system_name":"Sol","type":"Outpost","distance":2.0,
                   "distance_to_arrival":100.0,"small_pads":2,"medium_pads":2,"large_pads":0},
                  {"name":"Big Coriolis","system_name":"Alpha Centauri","type":"Coriolis Starport","distance":9.7,
                   "distance_to_arrival":153.0,"small_pads":8,"medium_pads":8,"large_pads":8}
                ]}
                """);
    }

    private static List<TradeStationSearchResultDto.StationResult> sameSystemPage() {
        return stations("""
                {"results":[
                  {"name":"Haberlandt Survey","system_name":"Sol","type":"Coriolis Starport","distance":0.0,
                   "distance_to_arrival":2623.0,"small_pads":4,"medium_pads":4,"large_pads":4,"market_id":"222"},
                  {"name":"Walz Depot","system_name":"Sol","type":"Coriolis Starport","distance":0.0,
                   "distance_to_arrival":153.0,"small_pads":4,"medium_pads":2,"large_pads":2,"market_id":"111"},
                  {"name":"Far Orbis","system_name":"Wolf 359","type":"Orbis Starport","distance":7.8,
                   "distance_to_arrival":10.0,"small_pads":4,"medium_pads":4,"large_pads":4,"market_id":"333"}
                ]}
                """);
    }
}
