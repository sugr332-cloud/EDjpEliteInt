package elite.intel.db.managers;

import elite.intel.db.dao.EngineerProgressDao.EngineerProgressRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EngineerProgressManagerTest {

    private final EngineerProgressManager manager = EngineerProgressManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.clear();
    }

    @Test
    void recordAndFindByName() {
        manager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");

        Optional<EngineerProgressRecord> recordOpt = manager.findByName("Felicity Farseer");
        assertTrue(recordOpt.isPresent());

        EngineerProgressRecord record = recordOpt.get();
        assertEquals("felicity farseer", record.nameKey());
        assertEquals("Felicity Farseer", record.displayName());
        assertEquals(300000L, record.engineerId());
        assertEquals("Unlocked", record.progress());
        assertEquals(5, record.rank());
        assertEquals(0, record.rankProgress());
        assertEquals("2026-10-01T10:00:00Z", record.eventTimestamp());

        // Search with case/quote variations
        assertTrue(manager.findByName("felicity farseer").isPresent());
        assertTrue(manager.findByName("  FELICITY FARSEER  ").isPresent());
    }

    @Test
    void updatesWithNewerOrSameTimestamp() {
        manager.recordProgress("Elvira Martuuk", 300001L, "Known", null, null, "2026-10-01T10:00:00Z");

        // Same timestamp -> allowed to update
        manager.recordProgress("Elvira Martuuk", 300001L, "Invited", null, null, "2026-10-01T10:00:00Z");
        EngineerProgressRecord rec1 = manager.findByName("Elvira Martuuk").orElseThrow();
        assertEquals("Invited", rec1.progress());

        // Newer timestamp -> updates
        manager.recordProgress("Elvira Martuuk", 300001L, "Unlocked", 1, 10, "2026-10-01T11:00:00Z");
        EngineerProgressRecord rec2 = manager.findByName("Elvira Martuuk").orElseThrow();
        assertEquals("Unlocked", rec2.progress());
        assertEquals(1, rec2.rank());
        assertEquals(10, rec2.rankProgress());
        assertEquals("2026-10-01T11:00:00Z", rec2.eventTimestamp());
    }

    @Test
    void doesNotOverwriteWithOlderTimestamp() {
        // Record at 12:00:00Z
        manager.recordProgress("Tod 'The Blaster' McQuinn", 300004L, "Unlocked", 5, 0, "2026-10-01T12:00:00Z");

        // Stale event arrives with earlier timestamp (10:00:00Z)
        manager.recordProgress("Tod 'The Blaster' McQuinn", 300004L, "Invited", 2, 20, "2026-10-01T10:00:00Z");

        EngineerProgressRecord record = manager.findByName("Tod 'The Blaster' McQuinn").orElseThrow();
        assertEquals("Unlocked", record.progress(), "Stale event must not overwrite newer progress");
        assertEquals(5, record.rank(), "Stale event must not overwrite newer rank");
        assertEquals(0, record.rankProgress(), "Stale event must not overwrite newer rankProgress");
        assertEquals("2026-10-01T12:00:00Z", record.eventTimestamp());
    }

    @Test
    void getAllAndGetByProgress() {
        manager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");
        manager.recordProgress("Elvira Martuuk", 300001L, "Unlocked", 3, 40, "2026-10-01T10:00:00Z");
        manager.recordProgress("Marco Qwent", 300015L, "Invited", null, null, "2026-10-01T10:00:00Z");

        List<EngineerProgressRecord> all = manager.getAll();
        assertEquals(3, all.size());

        List<EngineerProgressRecord> unlocked = manager.getByProgress("Unlocked");
        assertEquals(2, unlocked.size());
        assertTrue(unlocked.stream().anyMatch(r -> r.displayName().equals("Felicity Farseer")));
        assertTrue(unlocked.stream().anyMatch(r -> r.displayName().equals("Elvira Martuuk")));

        List<EngineerProgressRecord> invited = manager.getByProgress("Invited");
        assertEquals(1, invited.size());
        assertEquals("Marco Qwent", invited.get(0).displayName());

        List<EngineerProgressRecord> unknown = manager.getByProgress("UnknownStatus");
        assertTrue(unknown.isEmpty());
    }
}
