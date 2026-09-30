package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiData;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CurrentStationQueryTest {

    private static DockingHistoryEntry createEntry(String stationName, String starSystem, long marketId, String stationType, double dist) {
        return new DockingHistoryEntry(1L, stationName, starSystem, 1000L, marketId, stationType, dist, "2026-09-28T10:00:00Z");
    }

    private static class TestCurrentStationQuery extends CurrentStationQuery {
        CurrentStationQuery.DataDto capturedDto;
        String capturedInstructions;

        TestCurrentStationQuery(
                BooleanSupplier isDockedSupplier,
                LongSupplier dockedMarketIdSupplier,
                Supplier<String> dockedStationNameSupplier,
                Supplier<String> currentStarSystemSupplier,
                Function<Integer, Optional<DockingHistoryEntry>> historyLookup) {
            super(isDockedSupplier, dockedMarketIdSupplier, dockedStationNameSupplier, currentStarSystemSupplier, historyLookup);
        }

        @Override
        protected JsonObject process(AiData struct, String userInput) {
            this.capturedInstructions = struct.getInstructions();
            if (struct.getData() instanceof CurrentStationQuery.DataDto dto) {
                this.capturedDto = dto;
            }
            JsonObject res = new JsonObject();
            res.addProperty("text_to_speech_response", "ok");
            return res;
        }
    }

    @Test
    void testGate1_dockedAndMatchesHistory_returnsCurrentStationDetails() throws Exception {
        DockingHistoryEntry currentEntry = createEntry("Jameson Memorial", "Shinrarta Dezhra", 12345L, "Coriolis", 300.0);

        TestCurrentStationQuery query = new TestCurrentStationQuery(
                () -> true,
                () -> 12345L,
                () -> "Jameson Memorial",
                () -> "Shinrarta Dezhra",
                back -> Optional.of(currentEntry)
        );

        JsonObject result = query.handle(CurrentStationQuery.ID, new JsonObject(), "今いるステーションは");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("docked", query.capturedDto.status());
        assertTrue(query.capturedDto.isDocked());
        assertEquals("Jameson Memorial", query.capturedDto.stationName());
        assertEquals("Shinrarta Dezhra", query.capturedDto.starSystem());
        assertEquals("Coriolis", query.capturedDto.stationType());
        assertEquals(300.0, query.capturedDto.distFromStarLS());
    }

    @Test
    void testGate2_dockedUnrecorded_returnsCurrentDockedName() throws Exception {
        // Docked at "New Outpost" (marketId=99999L), but history 0 is old "Jameson Memorial" (marketId=12345L)
        DockingHistoryEntry oldEntry = createEntry("Jameson Memorial", "Shinrarta Dezhra", 12345L, "Coriolis", 300.0);

        TestCurrentStationQuery query = new TestCurrentStationQuery(
                () -> true,
                () -> 99999L,
                () -> "New Outpost",
                () -> "Sol",
                back -> Optional.of(oldEntry)
        );

        JsonObject result = query.handle(CurrentStationQuery.ID, new JsonObject(), "今どこにドッキングしている");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("docked", query.capturedDto.status());
        assertTrue(query.capturedDto.isDocked());
        assertEquals("New Outpost", query.capturedDto.stationName());
        assertEquals("Sol", query.capturedDto.starSystem());
        assertNull(query.capturedDto.stationType());
    }

    @Test
    void testGate3_undockedWithHistory_returnsLastDockedStation() throws Exception {
        DockingHistoryEntry lastVisited = createEntry("Ray Gateway", "Diaguandri", 54321L, "Coriolis", 500.0);

        TestCurrentStationQuery query = new TestCurrentStationQuery(
                () -> false,
                () -> 0L,
                () -> null,
                () -> "Diaguandri",
                back -> Optional.of(lastVisited)
        );

        JsonObject result = query.handle(CurrentStationQuery.ID, new JsonObject(), "今いるステーションはどこ");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("undocked", query.capturedDto.status());
        assertFalse(query.capturedDto.isDocked());
        assertEquals("Ray Gateway", query.capturedDto.stationName());
        assertEquals("Diaguandri", query.capturedDto.starSystem());
        assertEquals("Coriolis", query.capturedDto.stationType());
        assertEquals(500.0, query.capturedDto.distFromStarLS());
    }

    @Test
    void testGate4_undockedWithoutHistory_returnsNoRecord() throws Exception {
        TestCurrentStationQuery query = new TestCurrentStationQuery(
                () -> false,
                () -> 0L,
                () -> null,
                () -> "Sol",
                back -> Optional.empty()
        );

        JsonObject result = query.handle(CurrentStationQuery.ID, new JsonObject(), "現在のステーションは");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("no_record", query.capturedDto.status());
        assertFalse(query.capturedDto.isDocked());
        assertNull(query.capturedDto.stationName());
        assertNull(query.capturedDto.starSystem());
    }

    @Test
    void testMetadata() {
        CurrentStationQuery query = new CurrentStationQuery();
        assertEquals("query_current_station", query.id());
        assertNotNull(query.llmDescription());
    }
}
