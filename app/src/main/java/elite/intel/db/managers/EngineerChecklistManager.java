package elite.intel.db.managers;

import elite.intel.db.dao.EngineerChecklistDao;
import elite.intel.db.dao.EngineerChecklistDao.ChecklistEntry;
import elite.intel.db.util.Database;
import elite.intel.gameapi.engineers.EngineerDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.*;

public final class EngineerChecklistManager {

    private static final Logger log = LogManager.getLogger(EngineerChecklistManager.class);
    private static final EngineerChecklistManager INSTANCE = new EngineerChecklistManager();

    public static final String ITEM_INVITE = "invite";
    public static final String ITEM_UNLOCK = "unlock";
    public static final String ITEM_REFERRAL_TASK = "referral_task";

    private EngineerChecklistManager() {
    }

    public static EngineerChecklistManager getInstance() {
        return INSTANCE;
    }

    public boolean isChecked(String engineerName, String item) {
        if (engineerName == null || engineerName.isBlank() || item == null || item.isBlank()) {
            return false;
        }
        String nameKey = EngineerDirectory.normalizeName(engineerName);
        try {
            return Database.withDao(EngineerChecklistDao.class, dao -> {
                Boolean checked = dao.isChecked(nameKey, item);
                return checked != null && checked;
            });
        } catch (Exception e) {
            log.error("Failed to query engineer checklist status for {} / {}: {}", engineerName, item, e.getMessage(), e);
            return false;
        }
    }

    public void setChecked(String engineerName, String item, boolean checked) {
        if (engineerName == null || engineerName.isBlank() || item == null || item.isBlank()) {
            return;
        }
        String nameKey = EngineerDirectory.normalizeName(engineerName);
        String now = Instant.now().toString();
        try {
            Database.withDao(EngineerChecklistDao.class, dao -> {
                dao.setChecked(nameKey, item, checked, now);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to update engineer checklist status for {} / {}: {}", engineerName, item, e.getMessage(), e);
        }
    }

    public Map<String, Boolean> getChecklistForEngineer(String engineerName) {
        if (engineerName == null || engineerName.isBlank()) {
            return Collections.emptyMap();
        }
        String nameKey = EngineerDirectory.normalizeName(engineerName);
        try {
            return Database.withDao(EngineerChecklistDao.class, dao -> {
                List<ChecklistEntry> entries = dao.getAllForEngineer(nameKey);
                if (entries == null || entries.isEmpty()) {
                    return Collections.emptyMap();
                }
                Map<String, Boolean> map = new HashMap<>();
                for (ChecklistEntry entry : entries) {
                    map.put(entry.item(), entry.checked());
                }
                return map;
            });
        } catch (Exception e) {
            log.error("Failed to retrieve engineer checklist for {}: {}", engineerName, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    public String getUpdatedAt(String engineerName, String item) {
        if (engineerName == null || engineerName.isBlank() || item == null || item.isBlank()) {
            return null;
        }
        String nameKey = EngineerDirectory.normalizeName(engineerName);
        try {
            return Database.withDao(EngineerChecklistDao.class, dao -> {
                List<ChecklistEntry> entries = dao.getAllForEngineer(nameKey);
                if (entries != null) {
                    for (ChecklistEntry entry : entries) {
                        if (item.equals(entry.item())) {
                            return entry.updatedAt();
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to get updatedAt for {} / {}: {}", engineerName, item, e.getMessage(), e);
            return null;
        }
    }

    public void clear() {
        try {
            Database.withDao(EngineerChecklistDao.class, dao -> {
                dao.clear();
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to clear engineer checklist: {}", e.getMessage(), e);
        }
    }
}
