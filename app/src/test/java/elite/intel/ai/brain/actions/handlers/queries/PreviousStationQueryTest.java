package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiData;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PreviousStationQueryTest {

    private static DockingHistoryEntry createEntry(String stationName, String starSystem, long marketId, String stationType, double dist) {
        return new DockingHistoryEntry(1L, stationName, starSystem, 1000L, marketId, stationType, dist, "2026-09-28T10:00:00Z");
    }

    private static class TestPreviousStationQuery extends PreviousStationQuery {
        PreviousStationQuery.DataDto capturedDto;
        String capturedInstructions;

        TestPreviousStationQuery(PreviousStationResolver resolver, java.util.function.BooleanSupplier isDocked, java.util.function.LongSupplier marketId) {
            super(resolver, isDocked, marketId);
        }

        @Override
        protected JsonObject process(AiData struct, String userInput) {
            this.capturedInstructions = struct.getInstructions();
            if (struct.getData() instanceof PreviousStationQuery.DataDto dto) {
                this.capturedDto = dto;
            }
            JsonObject res = new JsonObject();
            res.addProperty("text_to_speech_response", "ok");
            return res;
        }
    }

    @Test
    void testGate1_dockedAndMatchesHistory_returnsFirstPreviousStation() throws Exception {
        // Docked, matches history 0 -> previous station is index 1
        DockingHistoryEntry stationA = createEntry("StationA", "SysA", 10L, "Coriolis", 120.0);

        TestPreviousStationQuery query = new TestPreviousStationQuery(
                (back, isDocked, marketId) -> {
                    assertTrue(isDocked);
                    assertEquals(20L, marketId);
                    assertEquals(1, back);
                    return Optional.of(stationA);
                },
                () -> true,
                () -> 20L
        );

        JsonObject result = query.handle(PreviousStationQuery.ID, new JsonObject(), "前のステーションはどこ");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("ok", query.capturedDto.status());
        assertEquals("StationA", query.capturedDto.stationName());
        assertEquals("SysA", query.capturedDto.starSystem());
        assertEquals("Coriolis", query.capturedDto.stationType());
        assertEquals(120.0, query.capturedDto.distFromStarLS());
        assertTrue(query.capturedInstructions.contains("distFromStarLS"));
    }

    @Test
    void testGate2_dockedAndUnrecordedMismatch_returnsLatestEntry() throws Exception {
        // Docked at unrecorded station (marketId=99L), previous station is index 0 (StationB)
        DockingHistoryEntry stationB = createEntry("StationB", "SysB", 20L, "Orbis", 350.0);

        TestPreviousStationQuery query = new TestPreviousStationQuery(
                (back, isDocked, marketId) -> {
                    assertTrue(isDocked);
                    assertEquals(99L, marketId);
                    return Optional.of(stationB);
                },
                () -> true,
                () -> 99L
        );

        JsonObject result = query.handle(PreviousStationQuery.ID, new JsonObject(), "前のステーションを教えて");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("ok", query.capturedDto.status());
        assertEquals("StationB", query.capturedDto.stationName());
        assertEquals("SysB", query.capturedDto.starSystem());
        assertEquals("Orbis", query.capturedDto.stationType());
        assertEquals(350.0, query.capturedDto.distFromStarLS());
    }

    @Test
    void testGate3_undocked_returnsLastVisitedStation() throws Exception {
        // Undocked -> previous station is index 0 (StationB)
        DockingHistoryEntry stationB = createEntry("StationB", "SysB", 20L, "Orbis", 350.0);

        TestPreviousStationQuery query = new TestPreviousStationQuery(
                (back, isDocked, marketId) -> {
                    assertFalse(isDocked);
                    assertEquals(0L, marketId);
                    return Optional.of(stationB);
                },
                () -> false,
                () -> 0L
        );

        JsonObject result = query.handle(PreviousStationQuery.ID, new JsonObject(), "さっきどこにいた");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("ok", query.capturedDto.status());
        assertEquals("StationB", query.capturedDto.stationName());
        assertEquals("SysB", query.capturedDto.starSystem());
    }

    @Test
    void testGate4_noHistory_returnsNoRecord() throws Exception {
        TestPreviousStationQuery query = new TestPreviousStationQuery(
                (back, isDocked, marketId) -> Optional.empty(),
                () -> false,
                () -> 0L
        );

        JsonObject result = query.handle(PreviousStationQuery.ID, new JsonObject(), "前のステーションはどこ");
        assertNotNull(result);
        assertNotNull(query.capturedDto);
        assertEquals("no_record", query.capturedDto.status());
        assertNull(query.capturedDto.stationName());
        assertNull(query.capturedDto.starSystem());
    }

    @Test
    void testMetadata() {
        PreviousStationQuery query = new PreviousStationQuery();
        assertEquals("query_previous_station", query.id());
        assertNotNull(query.llmDescription());
    }
}
