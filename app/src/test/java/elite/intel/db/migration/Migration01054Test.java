package elite.intel.db.migration;

import elite.intel.db.dao.EngineerChecklistDao;
import elite.intel.db.dao.QueryResultDisplayDao;
import elite.intel.db.util.Database;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Migration01054Test {

    @Test
    void queryResultDisplayPreservesExistingAndAllowsEngineers() {
        Database.withDao(QueryResultDisplayDao.class, dao -> {
            dao.save("trade_candidates", Instant.now().toString(), "{\"status\":\"ok\"}");
            dao.save("outfitting", Instant.now().toString(), "{\"status\":\"found\"}");
            dao.save("engineers", Instant.now().toString(), "{\"queryKind\":\"module\"}");

            QueryResultDisplayDao.QueryResultRow tradeRow = dao.get("trade_candidates");
            assertNotNull(tradeRow);
            assertEquals("trade_candidates", tradeRow.queryType());

            QueryResultDisplayDao.QueryResultRow outfittingRow = dao.get("outfitting");
            assertNotNull(outfittingRow);
            assertEquals("outfitting", outfittingRow.queryType());

            QueryResultDisplayDao.QueryResultRow engineersRow = dao.get("engineers");
            assertNotNull(engineersRow);
            assertEquals("engineers", engineersRow.queryType());

            return null;
        });
    }

    @Test
    void engineerChecklistTableConstraints() {
        Database.withDao(EngineerChecklistDao.class, dao -> {
            dao.clear();
            dao.setChecked("felicity farseer", "invite", true, "2026-10-01T12:00:00Z");
            dao.setChecked("felicity farseer", "unlock", false, "2026-10-01T12:00:00Z");
            dao.setChecked("jude navarro", "referral_task", true, "2026-10-01T12:00:00Z");

            assertTrue(dao.isChecked("felicity farseer", "invite"));
            assertFalse(dao.isChecked("felicity farseer", "unlock"));
            assertTrue(dao.isChecked("jude navarro", "referral_task"));

            // Check invalid item fails CHECK constraint
            assertThrows(Exception.class, () -> {
                dao.setChecked("felicity farseer", "invalid_item", true, "2026-10-01T12:00:00Z");
            });

            return null;
        });
    }
}
