package elite.intel.gameapi.journal.subscribers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import elite.intel.db.dao.EngineerProgressDao.EngineerProgressRecord;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.gameapi.JournalPreScanner;
import elite.intel.gameapi.journal.events.EngineerProgressEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineerProgressSubscriberTest {

    private final EngineerProgressSubscriber subscriber = new EngineerProgressSubscriber();
    private final EngineerProgressManager manager = EngineerProgressManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.clear();
    }

    @Test
    void receivesEventDirectlyAndPersists() {
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
        eng2.addProperty("Progress", "Invited");
        arr.add(eng2);

        json.add("Engineers", arr);

        EngineerProgressEvent event = new EngineerProgressEvent(json);
        subscriber.onEngineerProgressEvent(event);

        EngineerProgressRecord r1 = manager.findByName("Felicity Farseer").orElseThrow();
        assertEquals("Unlocked", r1.progress());
        assertEquals(5, r1.rank());

        EngineerProgressRecord r2 = manager.findByName("Elvira Martuuk").orElseThrow();
        assertEquals("Invited", r2.progress());
        assertNull(r2.rank());
    }

    @Test
    void preScanReadsPreLaunchEngineerProgress(@TempDir Path journalDir) throws IOException {
        String journalContent = """
                { "timestamp":"2026-10-01T08:00:00Z", "event":"Fileheader", "part":1, "language":"Japanese/Default", "Odyssey":true, "gameversion":"4.0.0.1901", "build":"r310452 " }
                { "timestamp":"2026-10-01T08:00:01Z", "event":"Commander", "FID":"F12345", "Name":"Commander Test" }
                { "timestamp":"2026-10-01T08:00:02Z", "event":"EngineerProgress", "Engineers":[ {"Engineer":"Felicity Farseer", "EngineerID":300000, "Progress":"Unlocked", "Rank":5, "RankProgress":0}, {"Engineer":"The Dweller", "EngineerID":300006, "Progress":"Unlocked", "Rank":4, "RankProgress":25} ] }
                """;

        Files.writeString(journalDir.resolve("Journal.2026-10-01T080000.01.log"), journalContent, StandardCharsets.UTF_8);

        JournalPreScanner.scan(journalDir);

        EngineerProgressRecord felicity = manager.findByName("Felicity Farseer").orElseThrow();
        assertEquals("Unlocked", felicity.progress());
        assertEquals(5, felicity.rank());
        assertEquals("2026-10-01T08:00:02Z", felicity.eventTimestamp());

        EngineerProgressRecord dweller = manager.findByName("The Dweller").orElseThrow();
        assertEquals("Unlocked", dweller.progress());
        assertEquals(4, dweller.rank());
        assertEquals(25, dweller.rankProgress());
    }

    @Test
    void olderPreScanRecordDoesNotOverwriteNewerRecord(@TempDir Path journalDir) throws IOException {
        // Step 1: newer record already arrived (e.g. from live session at 15:00:00Z)
        manager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T15:00:00Z");

        // Step 2: pre-scan journal file from earlier time (08:00:00Z) when progress was only "Invited"
        String oldJournalContent = """
                { "timestamp":"2026-10-01T08:00:00Z", "event":"Fileheader", "part":1, "language":"Japanese/Default", "Odyssey":true, "gameversion":"4.0.0.1901", "build":"r310452 " }
                { "timestamp":"2026-10-01T08:00:02Z", "event":"EngineerProgress", "Engineers":[ {"Engineer":"Felicity Farseer", "EngineerID":300000, "Progress":"Invited"} ] }
                """;
        Files.writeString(journalDir.resolve("Journal.2026-10-01T080000.01.log"), oldJournalContent, StandardCharsets.UTF_8);

        // Run pre-scan
        JournalPreScanner.scan(journalDir);

        // Verify that newer record at 15:00:00Z was NOT overwritten
        EngineerProgressRecord felicity = manager.findByName("Felicity Farseer").orElseThrow();
        assertEquals("Unlocked", felicity.progress(), "Pre-scan must not overwrite newer progress with older record");
        assertEquals(5, felicity.rank());
        assertEquals("2026-10-01T15:00:00Z", felicity.eventTimestamp());
    }
}
