package elite.intel.db.managers;

import com.google.gson.Gson;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.dao.QueryResultDisplayDao;
import elite.intel.db.dao.QueryResultDisplayDao.QueryResultRow;
import elite.intel.db.util.Database;
import elite.intel.eventbus.UiBus;
import elite.intel.ui.event.QueryResultDisplayUpdatedEvent;
import elite.intel.util.json.GsonFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages persisted query results (trade candidates and nearest outfitting) for display in the AI tab
 * and the native HUD overlay, following the derive-never-remember rule.
 */
public final class QueryResultDisplayManager {

    private static final Logger log = LogManager.getLogger(QueryResultDisplayManager.class);
    private static final QueryResultDisplayManager INSTANCE = new QueryResultDisplayManager();

    public static final String TYPE_TRADE_CANDIDATES = "trade_candidates";
    public static final String TYPE_OUTFITTING = "outfitting";

    private final Gson gson = GsonFactory.getGson();

    private QueryResultDisplayManager() {
    }

    public static QueryResultDisplayManager getInstance() {
        return INSTANCE;
    }

    public record DisplayRecord<T>(String queryType, Instant savedAt, T data) {
    }

    public record LatestDisplay(
            String queryType,
            Instant savedAt,
            TradeCandidatesDataDto tradeCandidates,
            OutfittingDataDto outfitting
    ) {
        public boolean isTradeCandidates() {
            return TYPE_TRADE_CANDIDATES.equals(queryType);
        }

        public boolean isOutfitting() {
            return TYPE_OUTFITTING.equals(queryType);
        }
    }

    private static void validateType(String queryType) {
        if (!TYPE_TRADE_CANDIDATES.equals(queryType) && !TYPE_OUTFITTING.equals(queryType)) {
            throw new IllegalArgumentException("Unsupported query_type: " + queryType);
        }
    }

    public void saveTradeCandidates(TradeCandidatesDataDto dto) {
        if (dto == null) {
            clearTradeCandidates();
            return;
        }
        boolean hasCandidates = dto.candidates() != null && !dto.candidates().isEmpty();
        boolean validStatus = "ok".equalsIgnoreCase(dto.status()) || "insufficient_fresh_data".equalsIgnoreCase(dto.status());
        if (validStatus && hasCandidates) {
            saveInternal(TYPE_TRADE_CANDIDATES, dto);
        } else {
            clearTradeCandidates();
        }
    }

    public void clearTradeCandidates() {
        clearInternal(TYPE_TRADE_CANDIDATES);
    }

    public void saveOutfitting(OutfittingDataDto dto) {
        if (dto == null) {
            clearOutfitting();
            return;
        }
        if ("found".equalsIgnoreCase(dto.status())) {
            saveInternal(TYPE_OUTFITTING, dto);
        } else {
            clearOutfitting();
        }
    }

    public void clearOutfitting() {
        clearInternal(TYPE_OUTFITTING);
    }

    private void saveInternal(String queryType, Object dto) {
        validateType(queryType);
        try {
            String json = gson.toJson(dto);
            String now = Instant.now().toString();
            Database.withDao(QueryResultDisplayDao.class, dao -> {
                dao.save(queryType, now, json);
                return null;
            });
            UiBus.publish(new QueryResultDisplayUpdatedEvent(queryType));
        } catch (Exception e) {
            log.error("Failed to save query result display for type {}: {}", queryType, e.getMessage(), e);
        }
    }

    private void clearInternal(String queryType) {
        validateType(queryType);
        try {
            Database.withDao(QueryResultDisplayDao.class, dao -> {
                dao.delete(queryType);
                return null;
            });
            UiBus.publish(new QueryResultDisplayUpdatedEvent(queryType));
        } catch (Exception e) {
            log.error("Failed to clear query result display for type {}: {}", queryType, e.getMessage(), e);
        }
    }

    public void clearAll() {
        try {
            Database.withDao(QueryResultDisplayDao.class, dao -> {
                dao.clear();
                return null;
            });
            UiBus.publish(new QueryResultDisplayUpdatedEvent(TYPE_TRADE_CANDIDATES));
            UiBus.publish(new QueryResultDisplayUpdatedEvent(TYPE_OUTFITTING));
        } catch (Exception e) {
            log.error("Failed to clear all query result displays: {}", e.getMessage(), e);
        }
    }

    public Optional<DisplayRecord<TradeCandidatesDataDto>> getTradeCandidates() {
        return getRecord(TYPE_TRADE_CANDIDATES, TradeCandidatesDataDto.class);
    }

    public Optional<DisplayRecord<OutfittingDataDto>> getOutfitting() {
        return getRecord(TYPE_OUTFITTING, OutfittingDataDto.class);
    }

    private <T> Optional<DisplayRecord<T>> getRecord(String queryType, Class<T> clazz) {
        validateType(queryType);
        try {
            QueryResultRow row = Database.withDao(QueryResultDisplayDao.class, dao -> dao.get(queryType));
            if (row == null || row.payloadJson() == null || row.payloadJson().isBlank()) {
                return Optional.empty();
            }
            Instant savedAt = parseInstantOrNow(row.savedAt());
            T data = gson.fromJson(row.payloadJson(), clazz);
            if (data == null) {
                return Optional.empty();
            }
            return Optional.of(new DisplayRecord<>(queryType, savedAt, data));
        } catch (Exception e) {
            log.error("Failed to read query result display for type {}: {}", queryType, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<LatestDisplay> getLatest() {
        try {
            List<QueryResultRow> rows = Database.withDao(QueryResultDisplayDao.class, QueryResultDisplayDao::getAll);
            if (rows == null || rows.isEmpty()) {
                return Optional.empty();
            }
            return rows.stream()
                    .map(this::toLatestDisplay)
                    .filter(Objects::nonNull)
                    .max(Comparator.comparing(LatestDisplay::savedAt));
        } catch (Exception e) {
            log.error("Failed to get latest query result display: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private LatestDisplay toLatestDisplay(QueryResultRow row) {
        if (row == null || row.payloadJson() == null || row.payloadJson().isBlank()) {
            return null;
        }
        Instant savedAt = parseInstantOrNow(row.savedAt());
        if (TYPE_TRADE_CANDIDATES.equals(row.queryType())) {
            TradeCandidatesDataDto data = gson.fromJson(row.payloadJson(), TradeCandidatesDataDto.class);
            return (data != null) ? new LatestDisplay(TYPE_TRADE_CANDIDATES, savedAt, data, null) : null;
        } else if (TYPE_OUTFITTING.equals(row.queryType())) {
            OutfittingDataDto data = gson.fromJson(row.payloadJson(), OutfittingDataDto.class);
            return (data != null) ? new LatestDisplay(TYPE_OUTFITTING, savedAt, null, data) : null;
        }
        return null;
    }

    private static Instant parseInstantOrNow(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
