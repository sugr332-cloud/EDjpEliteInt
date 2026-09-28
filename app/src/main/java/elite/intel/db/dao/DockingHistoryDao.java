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

@RegisterRowMapper(DockingHistoryDao.DockingHistoryRowMapper.class)
public interface DockingHistoryDao {

    @SqlUpdate("""
            INSERT INTO docking_history (stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt)
            VALUES (:stationName, :starSystem, :systemAddress, :marketId, :stationType, :distFromStarLS, :dockedAt)
            """)
    void insert(@Bind("stationName") String stationName,
                @Bind("starSystem") String starSystem,
                @Bind("systemAddress") long systemAddress,
                @Bind("marketId") long marketId,
                @Bind("stationType") String stationType,
                @Bind("distFromStarLS") double distFromStarLS,
                @Bind("dockedAt") String dockedAt);

    @SqlUpdate("""
            UPDATE docking_history
            SET stationName = :stationName,
                starSystem = :starSystem,
                systemAddress = :systemAddress,
                marketId = :marketId,
                stationType = :stationType,
                distFromStarLS = :distFromStarLS,
                dockedAt = :dockedAt
            WHERE id = :id
            """)
    void update(@Bind("id") long id,
                @Bind("stationName") String stationName,
                @Bind("starSystem") String starSystem,
                @Bind("systemAddress") long systemAddress,
                @Bind("marketId") long marketId,
                @Bind("stationType") String stationType,
                @Bind("distFromStarLS") double distFromStarLS,
                @Bind("dockedAt") String dockedAt);

    @SqlQuery("""
            SELECT id, stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt
            FROM docking_history
            ORDER BY id DESC
            LIMIT 1
            """)
    DockingHistoryEntry getLatest();

    @SqlQuery("""
            SELECT id, stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt
            FROM docking_history
            ORDER BY id DESC
            LIMIT :limit
            """)
    List<DockingHistoryEntry> getRecent(@Bind("limit") int limit);

    @SqlQuery("""
            SELECT id, stationName, starSystem, systemAddress, marketId, stationType, distFromStarLS, dockedAt
            FROM docking_history
            ORDER BY id DESC
            """)
    List<DockingHistoryEntry> getAll();

    @SqlQuery("SELECT COUNT(*) FROM docking_history")
    int count();

    @SqlUpdate("""
            DELETE FROM docking_history
            WHERE id NOT IN (
                SELECT id FROM docking_history
                ORDER BY id DESC
                LIMIT :keepCount
            )
            """)
    void trimHistory(@Bind("keepCount") int keepCount);

    @SqlUpdate("DELETE FROM docking_history")
    void clear();

    record DockingHistoryEntry(
            long id,
            String stationName,
            String starSystem,
            long systemAddress,
            long marketId,
            String stationType,
            double distFromStarLS,
            String dockedAt
    ) {
        public boolean isFleetCarrier() {
            return stationType != null && "FleetCarrier".equalsIgnoreCase(stationType);
        }
    }

    class DockingHistoryRowMapper implements RowMapper<DockingHistoryEntry> {
        @Override
        public DockingHistoryEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new DockingHistoryEntry(
                    rs.getLong("id"),
                    rs.getString("stationName"),
                    rs.getString("starSystem"),
                    rs.getLong("systemAddress"),
                    rs.getLong("marketId"),
                    rs.getString("stationType"),
                    rs.getDouble("distFromStarLS"),
                    rs.getString("dockedAt")
            );
        }
    }
}
