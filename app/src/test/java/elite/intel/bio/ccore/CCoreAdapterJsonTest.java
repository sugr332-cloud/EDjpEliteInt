package elite.intel.bio.ccore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JSON mapping in isolation, with no real process involved: does {@link CCoreAdapter} produce the
 * request shape EDpjKinsaku's {@code app/cli/bio.py} actually reads, and parse the response shape it
 * actually writes.
 */
class CCoreAdapterJsonTest {

    @Test
    void requestJsonUsesTheFieldNamesTheCliExpects() {
        BodyContext body = new BodyContext(
                "CarbonDioxide", 0.04, 180.0, 0.0161, "Rocky body", "None", null);

        JsonObject request = JsonParser.parseString(CCoreAdapter.toRequestJson("Aleoida", body)).getAsJsonObject();

        assertEquals("Aleoida", request.get("genus").getAsString());
        JsonObject bodyJson = request.getAsJsonObject("body");
        assertEquals("CarbonDioxide", bodyJson.get("atmosphere").getAsString());
        assertEquals(0.04, bodyJson.get("gravity").getAsDouble());
        assertEquals(180.0, bodyJson.get("temperature").getAsDouble());
        assertEquals(0.0161, bodyJson.get("pressure").getAsDouble());
        // body_type, not bodyType: this is the one field the CLI would silently read as null if the
        // @SerializedName here and app/cli/bio.py's _body_context_from_json ever drifted apart.
        assertEquals("Rocky body", bodyJson.get("body_type").getAsString());
        assertEquals("None", bodyJson.get("volcanism").getAsString());
        assertFalse(bodyJson.has("regions"), "a null field should be omitted, not written as JSON null");
    }

    @Test
    void requestJsonIncludesRegionsWhenPresent() {
        BodyContext body = new BodyContext(null, null, null, null, null, null, Set.of("Odin's Hold"));

        JsonObject bodyJson = JsonParser.parseString(CCoreAdapter.toRequestJson("Frutexa", body))
                .getAsJsonObject().getAsJsonObject("body");

        assertTrue(bodyJson.get("regions").getAsJsonArray().size() == 1);
    }

    @Test
    void parseResponseReadsEveryFieldOfEveryEvaluation() {
        String stdout = """
                [{"species_code": "$Codex_Ent_Aleoids_01_Name;", "species_name": "Aleoida Arcus", "status": "MATCH", "reason": "all predicates satisfied"},
                 {"species_code": "$Codex_Ent_Aleoids_02_Name;", "species_name": "Aleoida Coronamus", "status": "NO_MATCH", "reason": "pressure: below minimum"}]
                """;

        List<RuleEvaluation> evaluations = CCoreAdapter.parseResponse(stdout);

        assertEquals(2, evaluations.size());
        assertEquals(new RuleEvaluation("$Codex_Ent_Aleoids_01_Name;", "Aleoida Arcus",
                RuleStatus.MATCH, "all predicates satisfied"), evaluations.get(0));
        assertEquals(RuleStatus.NO_MATCH, evaluations.get(1).status());
    }

    @Test
    void parseResponseRejectsMalformedJson() {
        CCoreAdapterException error = assertThrows(CCoreAdapterException.class,
                () -> CCoreAdapter.parseResponse("not json"));
        assertTrue(error.getMessage().contains("not valid JSON"), error.getMessage());
    }

    @Test
    void parseResponseRejectsAnEmptyBody() {
        assertThrows(CCoreAdapterException.class, () -> CCoreAdapter.parseResponse(""));
    }
}
