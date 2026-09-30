package elite.intel.db.dao;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@RegisterRowMapper(EngineerProgressDao.EngineerProgressRowMapper.class)
public interface EngineerProgressDao {

    record EngineerProgressRecord(
            String nameKey,
            String displayName,
            Long engineerId,
            String progress,
            Integer rank,
            Integer rankProgress,
            String eventTimestamp,
            String updatedAt
    ) {}

    @SqlUpdate("""
            INSERT INTO engineer_progress (name_key, display_name, engineer_id, progress, rank, rank_progress, event_timestamp, updated_at)
            VALUES (:nameKey, :displayName, :engineerId, :progress, :rank, :rankProgress, :eventTimestamp, :updatedAt)
            ON CONFLICT(name_key) DO UPDATE SET
                display_name = excluded.display_name,
                engineer_id = COALESCE(excluded.engineer_id, engineer_progress.engineer_id),
                progress = excluded.progress,
                rank = excluded.rank,
                rank_progress = excluded.rank_progress,
                event_timestamp = excluded.event_timestamp,
                updated_at = excluded.updated_at
            WHERE excluded.event_timestamp >= engineer_progress.event_timestamp
            """)
    void upsert(
            @Bind("nameKey") String nameKey,
            @Bind("displayName") String displayName,
            @Bind("engineerId") Long engineerId,
            @Bind("progress") String progress,
            @Bind("rank") Integer rank,
            @Bind("rankProgress") Integer rankProgress,
            @Bind("eventTimestamp") String eventTimestamp,
            @Bind("updatedAt") String updatedAt
    );

    @SqlQuery("""
            SELECT name_key, display_name, engineer_id, progress, rank, rank_progress, event_timestamp, updated_at
            FROM engineer_progress
            WHERE name_key = :nameKey
            """)
    EngineerProgressRecord findByNameKey(@Bind("nameKey") String nameKey);

    @SqlQuery("""
            SELECT name_key, display_name, engineer_id, progress, rank, rank_progress, event_timestamp, updated_at
            FROM engineer_progress
            ORDER BY display_name ASC
            """)
    List<EngineerProgressRecord> findAll();

    @SqlQuery("""
            SELECT name_key, display_name, engineer_id, progress, rank, rank_progress, event_timestamp, updated_at
            FROM engineer_progress
            WHERE LOWER(progress) = LOWER(:progress)
            ORDER BY display_name ASC
            """)
    List<EngineerProgressRecord> findByProgress(@Bind("progress") String progress);

    @SqlUpdate("DELETE FROM engineer_progress")
    void clear();

    class EngineerProgressRowMapper implements RowMapper<EngineerProgressRecord> {
        @Override
        public EngineerProgressRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
            long engIdVal = rs.getLong("engineer_id");
            Long engineerId = rs.wasNull() ? null : engIdVal;

            int rankVal = rs.getInt("rank");
            Integer rank = rs.wasNull() ? null : rankVal;

            int rankProgVal = rs.getInt("rank_progress");
            Integer rankProgress = rs.wasNull() ? null : rankProgVal;

            return new EngineerProgressRecord(
                    rs.getString("name_key"),
                    rs.getString("display_name"),
                    engineerId,
                    rs.getString("progress"),
                    rank,
                    rankProgress,
                    rs.getString("event_timestamp"),
                    rs.getString("updated_at")
            );
        }
    }
}
