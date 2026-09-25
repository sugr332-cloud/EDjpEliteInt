package elite.intel.gameapi.search.spansh.outfitting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.gameapi.search.spansh.station.marketstation.TradeStationSearchCriteria.StationType;
import elite.intel.gameapi.search.spansh.traderoute.TradeRouteSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OutfittingStationSearchCriteriaTest {

    @Test
    void testCriteriaWithNameClassRating() {
        MatchedModule module = new MatchedModule("Frame Shift Drive", 5, "A");
        OutfittingStationSearchCriteria criteria = OutfittingStationSearchCriteria.create(module, "Sol", null);

        String jsonStr = criteria.toJson();
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();

        assertEquals("Sol", root.get("reference_system").getAsString());
        assertEquals(5, root.get("size").getAsInt());
        assertEquals(0, root.get("page").getAsInt());

        JsonObject filters = root.getAsJsonObject("filters");
        assertNotNull(filters);

        // Modules filter
        var modulesArr = filters.getAsJsonArray("modules");
        assertEquals(1, modulesArr.size());
        JsonObject mf = modulesArr.get(0).getAsJsonObject();
        assertEquals("Frame Shift Drive", mf.getAsJsonArray("name").get(0).getAsString());
        assertEquals(5, mf.getAsJsonArray("class").get(0).getAsInt());
        assertEquals("A", mf.getAsJsonArray("rating").get(0).getAsString());

        // Default type when profile is null: ORBITAL + PLANETARY (no carrier)
        var typesArr = filters.getAsJsonObject("type").getAsJsonArray("value");
        assertTrue(typesArr.toString().contains("Coriolis Starport"));
        assertTrue(typesArr.toString().contains("Planetary Port"));
        assertFalse(typesArr.toString().contains("Drake-Class Carrier"));
    }

    @Test
    void testCriteriaWithNameOnly() {
        MatchedModule module = new MatchedModule("Fuel Scoop", null, null);
        OutfittingStationSearchCriteria criteria = OutfittingStationSearchCriteria.create(module, "Achenar", null);

        String jsonStr = criteria.toJson();
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        JsonObject filters = root.getAsJsonObject("filters");

        var modulesArr = filters.getAsJsonArray("modules");
        JsonObject mf = modulesArr.get(0).getAsJsonObject();
        assertEquals("Fuel Scoop", mf.getAsJsonArray("name").get(0).getAsString());
        assertNull(mf.get("class"), "class should be omitted when not specified");
        assertNull(mf.get("rating"), "rating should be omitted when not specified");
    }

    @Test
    void testStationTypeWithFleetCarriersAndPlanetary() {
        MatchedModule module = new MatchedModule("Thrusters", 6, "A");
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setAllowPlanetary(true);
        profile.setAllowFleetCarriers(true);
        profile.setMaxLsFromArrival(5000);
        profile.setRequiresLargePad(true);

        OutfittingStationSearchCriteria criteria = OutfittingStationSearchCriteria.create(module, "Sol", profile);
        String jsonStr = criteria.toJson();
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        JsonObject filters = root.getAsJsonObject("filters");

        // Station types must include carrier
        var typesArr = filters.getAsJsonObject("type").getAsJsonArray("value");
        assertTrue(typesArr.toString().contains("Drake-Class Carrier"), "Carrier should be included when allowFleetCarriers is true");
        assertTrue(typesArr.toString().contains("Planetary Port"), "Planetary should be included when allowPlanetary is true");

        // Ls and Large pad
        assertNotNull(filters.getAsJsonObject("distance_to_arrival"));
        assertEquals(5000, filters.getAsJsonObject("distance_to_arrival").get("max").getAsInt());
        assertNotNull(filters.getAsJsonObject("large_pads"));
        assertEquals(1, filters.getAsJsonObject("large_pads").get("min").getAsInt());
    }

    @Test
    void testStationTypeExcludesFleetCarriersWhenFalse() {
        MatchedModule module = new MatchedModule("Power Plant", 4, "A");
        TradeRouteSearchCriteria profile = new TradeRouteSearchCriteria();
        profile.setAllowPlanetary(false);
        profile.setAllowFleetCarriers(false);

        OutfittingStationSearchCriteria criteria = OutfittingStationSearchCriteria.create(module, "Sol", profile);
        String jsonStr = criteria.toJson();
        JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
        JsonObject filters = root.getAsJsonObject("filters");

        var typesArr = filters.getAsJsonObject("type").getAsJsonArray("value");
        assertFalse(typesArr.toString().contains("Drake-Class Carrier"), "Carrier should NOT be included when allowFleetCarriers is false");
        assertFalse(typesArr.toString().contains("Planetary Port"), "Planetary should NOT be included when allowPlanetary is false");
        assertTrue(typesArr.toString().contains("Orbis Starport"), "Orbital should always be included");
    }
}
