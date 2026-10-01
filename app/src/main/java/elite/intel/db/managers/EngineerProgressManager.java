package elite.intel.db.managers;

import elite.intel.db.dao.EngineerProgressDao;
import elite.intel.db.dao.EngineerProgressDao.EngineerProgressRecord;
import elite.intel.db.util.Database;
import elite.intel.gameapi.engineers.EngineerDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class EngineerProgressManager {

    private static final Logger log = LogManager.getLogger(EngineerProgressManager.class);
    private static final EngineerProgressManager INSTANCE = new EngineerProgressManager();

    public static EngineerProgressManager getInstance() {
        return INSTANCE;
    }

    private EngineerProgressManager() {}

    public synchronized void recordProgress(
            String displayName,
            Long engineerId,
            String progress,
            Integer rank,
            Integer rankProgress,
            String eventTimestamp
    ) {
        if (displayName == null || displayName.isBlank()) {
            log.warn("Cannot record engineer progress without display name");
            return;
        }
        if ((progress == null || progress.isBlank()) && rank != null) {
            progress = "Unlocked";
        }
        if (progress == null || progress.isBlank()) {
            log.warn("Cannot record engineer progress without progress state or rank for {}", displayName);
            return;
        }
        if (eventTimestamp == null || eventTimestamp.isBlank()) {
            eventTimestamp = Instant.now().toString();
        }

        String nameKey = EngineerDirectory.normalizeName(displayName);
        String updatedAt = Instant.now().toString();

        String finalEventTimestamp = eventTimestamp;
        String finalProgress = progress.trim();
        Database.withDao(EngineerProgressDao.class, dao -> {
            dao.upsert(
                    nameKey,
                    displayName.trim(),
                    engineerId,
                    finalProgress,
                    rank,
                    rankProgress,
                    finalEventTimestamp,
                    updatedAt
            );
            return Void.class;
        });

        log.debug("Recorded engineer progress for {}: progress={}, rank={}, eventTs={}",
                displayName, progress, rank, eventTimestamp);
    }

    public Optional<EngineerProgressRecord> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String nameKey = EngineerDirectory.normalizeName(name);
        return Optional.ofNullable(Database.withDao(EngineerProgressDao.class, dao -> dao.findByNameKey(nameKey)));
    }

    public List<EngineerProgressRecord> getAll() {
        return Database.withDao(EngineerProgressDao.class, EngineerProgressDao::findAll);
    }

    public List<EngineerProgressRecord> getByProgress(String progress) {
        if (progress == null || progress.isBlank()) {
            return Collections.emptyList();
        }
        return Database.withDao(EngineerProgressDao.class, dao -> dao.findByProgress(progress.trim()));
    }

    public synchronized void clear() {
        Database.withDao(EngineerProgressDao.class, dao -> {
            dao.clear();
            return Void.class;
        });
    }
}
