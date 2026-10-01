package elite.intel.gameapi.journal.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineerProgressEventTest {

    @Test
    void parseEngineersArrayFormat() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", "2026-10-01T12:00:00Z");
        json.addProperty("event", "EngineerProgress");

        JsonArray arr = new JsonArray();

        JsonObject eng1 = new JsonObject();
        eng1.addProperty("Engineer", "Felicity Farseer");
        eng1.addProperty("EngineerID", 300000L);
        eng1.addProperty("Progress", "Unlocked");
        eng1.addProperty("Rank", 5);
        eng1.addProperty("RankProgress", 0);
        arr.add(eng1);

        JsonObject eng2 = new JsonObject();
        eng2.addProperty("Engineer", "Elvira Martuuk");
        eng2.addProperty("EngineerID", 300001L);
        eng2.addProperty("Progress", "Known");
        arr.add(eng2);

        json.add("Engineers", arr);

        EngineerProgressEvent event = new EngineerProgressEvent(json);
        assertEquals("2026-10-01T12:00:00Z", event.getTimestamp());
        assertEquals("EngineerProgress", event.getEventType());

        List<EngineerProgressEvent.Engineer> engineers = event.getEngineers();
        assertNotNull(engineers);
        assertEquals(2, engineers.size());

        EngineerProgressEvent.Engineer e1 = engineers.get(0);
        assertEquals("Felicity Farseer", e1.getName());
        assertEquals(300000L, e1.getEngineerID());
        assertEquals(300000L, e1.getEngineerIdNullable());
        assertEquals("Unlocked", e1.getProgress());
        assertEquals(5, e1.getRank());
        assertEquals(5, e1.getRankNullable());
        assertEquals(0, e1.getRankProgress());
        assertEquals(0, e1.getRankProgressNullable());
        assertTrue(e1.isFullyUnlocked());

        EngineerProgressEvent.Engineer e2 = engineers.get(1);
        assertEquals("Elvira Martuuk", e2.getName());
        assertEquals(300001L, e2.getEngineerID());
        assertEquals("Known", e2.getProgress());
        assertNull(e2.getRankNullable());
        assertNull(e2.getRankProgressNullable());
        assertEquals(0, e2.getRank());
        assertFalse(e2.isFullyUnlocked());
    }

    @Test
    void parseSingleEngineerFormat() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", "2026-10-01T14:30:00Z");
        json.addProperty("event", "EngineerProgress");
        json.addProperty("Engineer", "Tod 'The Blaster' McQuinn");
        json.addProperty("EngineerID", 300004L);
        json.addProperty("Progress", "Unlocked");
        json.addProperty("Rank", 4);
        json.addProperty("RankProgress", 50);

        EngineerProgressEvent event = new EngineerProgressEvent(json);
        assertEquals("2026-10-01T14:30:00Z", event.getTimestamp());

        List<EngineerProgressEvent.Engineer> engineers = event.getEngineers();
        assertNotNull(engineers);
        assertEquals(1, engineers.size());

        EngineerProgressEvent.Engineer e = engineers.get(0);
        assertEquals("Tod 'The Blaster' McQuinn", e.getName());
        assertEquals(300004L, e.getEngineerID());
        assertEquals("Unlocked", e.getProgress());
        assertEquals(4, e.getRank());
        assertEquals(4, e.getRankNullable());
        assertEquals(50, e.getRankProgress());
        assertEquals(50, e.getRankProgressNullable());
        assertFalse(e.isFullyUnlocked(), "Rank 4 should not be fully unlocked");
    }

    @Test
    void parseSingleEngineerFormatWithoutRank() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", "2026-10-01T15:00:00Z");
        json.addProperty("event", "EngineerProgress");
        json.addProperty("Engineer", "Marco Qwent");
        json.addProperty("EngineerID", 300015L);
        json.addProperty("Progress", "Invited");

        EngineerProgressEvent event = new EngineerProgressEvent(json);
        List<EngineerProgressEvent.Engineer> engineers = event.getEngineers();
        assertEquals(1, engineers.size());

        EngineerProgressEvent.Engineer e = engineers.get(0);
        assertEquals("Marco Qwent", e.getName());
        assertEquals("Invited", e.getProgress());
        assertNull(e.getRankNullable());
        assertNull(e.getRankProgressNullable());
        assertEquals(0, e.getRank());
        assertEquals(0, e.getRankProgress());
        assertFalse(e.isFullyUnlocked());
    }

    @Test
    void parseEmptyOrMalformedEvent() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", "2026-10-01T15:00:00Z");
        json.addProperty("event", "EngineerProgress");

        EngineerProgressEvent event = new EngineerProgressEvent(json);
        assertNotNull(event.getEngineers());
        assertTrue(event.getEngineers().isEmpty());
    }
}
