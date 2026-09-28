package elite.intel.db.managers;

import elite.intel.db.dao.DockingHistoryDao;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.db.util.Database;
import elite.intel.gameapi.journal.events.DockedEvent;
import elite.intel.gameapi.journal.events.LocationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DockingHistoryManager {

    private static final Logger log = LogManager.getLogger(DockingHistoryManager.class);
    private static final DockingHistoryManager INSTANCE = new DockingHistoryManager();

    public static final int MAX_ENTRIES = 50;

    private DockingHistoryManager() {
    }

    public static DockingHistoryManager getInstance() {
        return INSTANCE;
    }

    public record DistinctStations(DockingHistoryEntry current, DockingHistoryEntry previous) {
    }

    public synchronized void recordDocked(DockedEvent event) {
        if (event == null) {
            return;
        }
        String stationName = event.getStationName();
        String starSystem = event.getStarSystem();
        long systemAddress = event.getSystemAddress();
        long marketId = event.getMarketID();
        String stationType = event.getStationType();
        double distFromStarLS = event.getDistFromStarLS();
        String dockedAt = event.getTimestamp();

        recordInternal(stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt, true);
    }

    public synchronized void recordLocation(LocationEvent event) {
        if (event == null || !event.isDocked()) {
            return;
        }
        String stationName = event.getStationName();
        String starSystem = event.getStarSystem();
        long systemAddress = event.getSystemAddress();
        long marketId = event.getMarketID();
        String stationType = event.getStationType();
        double distFromStarLS = event.getDistFromStarLS();
        String dockedAt = event.getTimestamp();

        recordInternal(stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt, false);
    }

    private void recordInternal(String stationName, String starSystem, long systemAddress, long marketId,
                                String stationType, double distFromStarLS, String dockedAt, boolean updateIfSame) {
        try {
            Database.withDao(DockingHistoryDao.class, dao -> {
                DockingHistoryEntry latest = dao.getLatest();
                if (isSameStation(latest, marketId, stationName, systemAddress)) {
                    if (updateIfSame) {
                        dao.update(latest.id(), stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt);
                    }
                } else {
                    dao.insert(stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt);
                    dao.trimHistory(MAX_ENTRIES);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to record docking history: {}", e.getMessage(), e);
        }
    }

    private boolean isSameStation(DockingHistoryEntry previous, long marketId, String stationName, long systemAddress) {
        if (previous == null) {
            return false;
        }
        if (marketId != 0 && previous.marketId() != 0) {
            return marketId == previous.marketId();
        }
        return Objects.equals(stationName, previous.stationName()) && systemAddress == previous.systemAddress();
    }

    private boolean isDifferentStation(DockingHistoryEntry a, DockingHistoryEntry b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.marketId() != 0 && b.marketId() != 0) {
            return a.marketId() != b.marketId();
        }
        return !Objects.equals(a.stationName(), b.stationName()) || a.systemAddress() != b.systemAddress();
    }

    public Optional<DockingHistoryEntry> getNthPreviousStation(int back) {
        if (back < 0) {
            return Optional.empty();
        }
        try {
            return Database.withDao(DockingHistoryDao.class, dao -> {
                List<DockingHistoryEntry> list = dao.getRecent(back + 1);
                if (list == null || list.size() <= back) {
                    return Optional.empty();
                }
                return Optional.of(list.get(back));
            });
        } catch (Exception e) {
            log.error("Failed to get nth previous station (back={}): {}", back, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<DistinctStations> getRecentDistinctStations() {
        try {
            return Database.withDao(DockingHistoryDao.class, dao -> {
                List<DockingHistoryEntry> history = dao.getRecent(MAX_ENTRIES);
                if (history == null || history.isEmpty()) {
                    return Optional.empty();
                }
                DockingHistoryEntry current = history.get(0);
                for (int i = 1; i < history.size(); i++) {
                    DockingHistoryEntry candidate = history.get(i);
                    if (isDifferentStation(current, candidate)) {
                        return Optional.of(new DistinctStations(current, candidate));
                    }
                }
                return Optional.empty();
            });
        } catch (Exception e) {
            log.error("Failed to get recent distinct stations: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public List<DockingHistoryEntry> getHistory(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        try {
            return Database.withDao(DockingHistoryDao.class, dao -> dao.getRecent(limit));
        } catch (Exception e) {
            log.error("Failed to get docking history (limit={}): {}", limit, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public synchronized void clear() {
        try {
            Database.withDao(DockingHistoryDao.class, dao -> {
                dao.clear();
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to clear docking history: {}", e.getMessage(), e);
        }
    }
}
