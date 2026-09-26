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

@RegisterRowMapper(QueryResultDisplayDao.QueryResultRowMapper.class)
public interface QueryResultDisplayDao {

    @SqlUpdate("""
            INSERT OR REPLACE INTO query_result_display (query_type, saved_at, payload_json)
            VALUES (:queryType, :savedAt, :payloadJson)
            """)
    void save(@Bind("queryType") String queryType,
              @Bind("savedAt") String savedAt,
              @Bind("payloadJson") String payloadJson);

    @SqlQuery("SELECT query_type, saved_at, payload_json FROM query_result_display WHERE query_type = :queryType")
    QueryResultRow get(@Bind("queryType") String queryType);

    @SqlQuery("SELECT query_type, saved_at, payload_json FROM query_result_display")
    List<QueryResultRow> getAll();

    @SqlUpdate("DELETE FROM query_result_display WHERE query_type = :queryType")
    void delete(@Bind("queryType") String queryType);

    @SqlUpdate("DELETE FROM query_result_display")
    void clear();

    record QueryResultRow(String queryType, String savedAt, String payloadJson) {
    }

    class QueryResultRowMapper implements RowMapper<QueryResultRow> {
        @Override
        public QueryResultRow map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new QueryResultRow(
                    rs.getString("query_type"),
                    rs.getString("saved_at"),
                    rs.getString("payload_json")
            );
        }
    }
}
