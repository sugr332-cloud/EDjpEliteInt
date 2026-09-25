package elite.intel.gameapi.search.spansh.tradecandidates;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TradeCandidatesSearchCriteriaTest {

    @Test
    void testCriteriaDefaultsWithoutProfile() {
        Instant now = Instant.parse("2026-09-25T12:00:00Z");
        TradeCandidatesSearchCriteria criteria = TradeCandidatesSearchCriteria.create("Sol", 30, null, now);

        String jsonStr = criteria.toJson();
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();

        assertEquals("Sol", root.get("reference_system").getAsString());
        assertEquals(20, root.get("size").getAsInt());
        assertEquals(0, root.get("page").getAsInt());

        JsonObject filters = root.getAsJsonObject("filters");
        assertNotNull(filters);

        // Required service: Market
        var services = filters.getAsJsonArray("services");
        assertEquals(1, services.size());
        assertEquals("Market", services.get(0).getAsJsonObject().getAsJsonArray("name").get(0).getAsString());

        // Distance: 0 to 30 as String
        var dist = filters.getAsJsonObject("distance");
        assertEquals("0", dist.get("min").getAsString());
        assertEquals("30", dist.get("max").getAsString());

        // Station types: orbital by default
        var types = filters.getAsJsonObject("type").getAsJsonArray("value");
        assertTrue(types.toString().contains("Coriolis Starport"));
        assertFalse(types.toString().contains("Planetary Port"));
        assertFalse(types.toString().contains("Drake-Class Carrier"));

        // Updated at: past 10 hours
        var updatedAt = filters.getAsJsonObject("updated_at");
        assertEquals("<=>", updatedAt.get("comparison").getAsString());
        var updatedValues = updatedAt.getAsJsonArray("value");
        assertEquals("2026-09-25T02:00:00Z", updatedValues.get(0).getAsString());
        assertEquals("2026-09-25T12:00:00Z", updatedValues.get(1).getAsString());

        // Large pads and arrival distance unset when profile is null
        assertNull(filters.get("large_pads"));
        assertNull(filters.get("distance_to_arrival"));
    }

    @Test
    void testCriteriaWithProfileSettings() {
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setAllowPlanetary(true);
        profile.setAllowFleetCarriers(true);
        profile.setMaxLsFromArrival(3500);
        profile.setRequiresLargePad(true);

        Instant now = Instant.parse("2026-09-25T15:00:00Z");
        TradeCandidatesSearchCriteria criteria = TradeCandidatesSearchCriteria.create("Shinrarta Dezhra", 45, profile, now);

        String jsonStr = criteria.toJson();
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        JsonObject filters = root.getAsJsonObject("filters");

        var types = filters.getAsJsonObject("type").getAsJsonArray("value");
        assertTrue(types.toString().contains("Planetary Port"));
        assertTrue(types.toString().contains("Drake-Class Carrier"));

        // Large pads
        assertNotNull(filters.getAsJsonObject("large_pads"));
        assertEquals(1, filters.getAsJsonObject("large_pads").getAsJsonArray("value").get(0).getAsInt());

        // Arrival distance
        assertNotNull(filters.getAsJsonObject("distance_to_arrival"));
        assertEquals(3500, filters.getAsJsonObject("distance_to_arrival").getAsJsonArray("value").get(1).getAsInt());

        // Radius
        assertEquals("45", filters.getAsJsonObject("distance").get("max").getAsString());
    }
}
