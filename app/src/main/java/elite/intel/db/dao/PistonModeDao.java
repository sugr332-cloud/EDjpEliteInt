package elite.intel.db.dao;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterRowMapper(PistonModeDao.PistonModeRowMapper.class)
public interface PistonModeDao {

    @SqlQuery("""
            SELECT id, active, start_type, station_a_name, station_a_system, station_a_system_address,
                   station_a_market_id, station_b_name, station_b_system, station_b_system_address,
                   station_b_market_id, commodity, updated_at
            FROM piston_mode
            WHERE id = 1
            """)
    PistonModeEntry getState();

    @SqlUpdate("""
            INSERT OR REPLACE INTO piston_mode (
                id, active, start_type, station_a_name, station_a_system, station_a_system_address,
                station_a_market_id, station_b_name, station_b_system, station_b_system_address,
                station_b_market_id, commodity, updated_at
            ) VALUES (
                1, :active, :startType, :stationAName, :stationASystem, :stationASystemAddress,
                :stationAMarketId, :stationBName, :stationBSystem, :stationBSystemAddress,
                :stationBMarketId, :commodity, :updatedAt
            )
            """)
    void saveState(
            @Bind("active") boolean active,
            @Bind("startType") String startType,
            @Bind("stationAName") String stationAName,
            @Bind("stationASystem") String stationASystem,
            @Bind("stationASystemAddress") long stationASystemAddress,
            @Bind("stationAMarketId") long stationAMarketId,
            @Bind("stationBName") String stationBName,
            @Bind("stationBSystem") String stationBSystem,
            @Bind("stationBSystemAddress") long stationBSystemAddress,
            @Bind("stationBMarketId") long stationBMarketId,
            @Bind("commodity") String commodity,
            @Bind("updatedAt") String updatedAt
    );

    @SqlUpdate("""
            UPDATE piston_mode
            SET station_a_market_id = :marketId,
                station_a_system_address = :systemAddress,
                updated_at = :updatedAt
            WHERE id = 1
            """)
    void updateStationAMarketId(
            @Bind("marketId") long marketId,
            @Bind("systemAddress") long systemAddress,
            @Bind("updatedAt") String updatedAt
    );

    @SqlUpdate("""
            UPDATE piston_mode
            SET station_b_market_id = :marketId,
                station_b_system_address = :systemAddress,
                updated_at = :updatedAt
            WHERE id = 1
            """)
    void updateStationBMarketId(
            @Bind("marketId") long marketId,
            @Bind("systemAddress") long systemAddress,
            @Bind("updatedAt") String updatedAt
    );

    @SqlUpdate("""
            UPDATE piston_mode
            SET active = FALSE,
                updated_at = :updatedAt
            WHERE id = 1
            """)
    void deactivate(@Bind("updatedAt") String updatedAt);

    @SqlUpdate("DELETE FROM piston_mode")
    void clear();

    record PistonModeEntry(
            int id,
            boolean active,
            String startType,
            String stationAName,
            String stationASystem,
            long stationASystemAddress,
            long stationAMarketId,
            String stationBName,
            String stationBSystem,
            long stationBSystemAddress,
            long stationBMarketId,
            String commodity,
            String updatedAt
    ) {
    }

    class PistonModeRowMapper implements RowMapper<PistonModeEntry> {
        @Override
        public PistonModeEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PistonModeEntry(
                    rs.getInt("id"),
                    rs.getBoolean("active"),
                    rs.getString("start_type"),
                    rs.getString("station_a_name"),
                    rs.getString("station_a_system"),
                    rs.getLong("station_a_system_address"),
                    rs.getLong("station_a_market_id"),
                    rs.getString("station_b_name"),
                    rs.getString("station_b_system"),
                    rs.getLong("station_b_system_address"),
                    rs.getLong("station_b_market_id"),
                    rs.getString("commodity"),
                    rs.getString("updated_at")
            );
        }
    }
}
