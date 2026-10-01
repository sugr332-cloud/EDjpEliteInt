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

@RegisterRowMapper(EngineerChecklistDao.EngineerChecklistRowMapper.class)
public interface EngineerChecklistDao {

    record ChecklistEntry(
            String nameKey,
            String item,
            boolean checked,
            String updatedAt
    ) {}

    @SqlUpdate("""
            INSERT INTO engineer_checklist (name_key, item, checked, updated_at)
            VALUES (:nameKey, :item, :checked, :updatedAt)
            ON CONFLICT(name_key, item) DO UPDATE SET
                checked = excluded.checked,
                updated_at = excluded.updated_at
            """)
    void setChecked(
            @Bind("nameKey") String nameKey,
            @Bind("item") String item,
            @Bind("checked") boolean checked,
            @Bind("updatedAt") String updatedAt
    );

    @SqlQuery("""
            SELECT checked FROM engineer_checklist
            WHERE name_key = :nameKey AND item = :item
            """)
    Boolean isChecked(@Bind("nameKey") String nameKey, @Bind("item") String item);

    @SqlQuery("""
            SELECT name_key, item, checked, updated_at
            FROM engineer_checklist
            WHERE name_key = :nameKey
            ORDER BY item ASC
            """)
    List<ChecklistEntry> getAllForEngineer(@Bind("nameKey") String nameKey);

    @SqlQuery("""
            SELECT name_key, item, checked, updated_at
            FROM engineer_checklist
            ORDER BY name_key ASC, item ASC
            """)
    List<ChecklistEntry> getAll();

    @SqlUpdate("DELETE FROM engineer_checklist WHERE name_key = :nameKey AND item = :item")
    void delete(@Bind("nameKey") String nameKey, @Bind("item") String item);

    @SqlUpdate("DELETE FROM engineer_checklist")
    void clear();

    class EngineerChecklistRowMapper implements RowMapper<ChecklistEntry> {
        @Override
        public ChecklistEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ChecklistEntry(
                    rs.getString("name_key"),
                    rs.getString("item"),
                    rs.getInt("checked") != 0,
                    rs.getString("updated_at")
            );
        }
    }
}
