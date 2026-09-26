package elite.intel.ui.overlay;

import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.MatchedModuleDto;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QueryResultObjectiveSourceTest {

    private static TradeCandidateDto createCandidate(int rank, String commodity, String buySystem, String buyStation, String sellSystem, String sellStation, long profit) {
        return new TradeCandidateDto(
                rank,
                commodity,
                buySystem,
                buyStation,
                50000,
                "50,000",
                10000L,
                "10,000",
                500.0,
                "500",
                "2026-09-26 10:00:00+00",
                sellSystem,
                sellStation,
                60000,
                "60,000",
                5000L,
                "5,000",
                1200.0,
                "1,200",
                "2026-09-26 11:00:00+00",
                10000,
                "10,000",
                700,
                "700",
                profit,
                String.format("%,d", profit),
                4.37,
                "4.37",
                4.37,
                "4.37"
        );
    }

    @Test
    void emptyWhenNoSavedResult() {
        QueryResultObjectiveSource source = new QueryResultObjectiveSource(Optional::empty, Instant::now);
        assertTrue(source.currentObjective().isEmpty());
    }

    @Test
    void emptyWhenOlderThan10Hours() {
        Instant now = Instant.parse("2026-09-26T22:00:00Z");
        Instant olderThan10h = now.minus(11, ChronoUnit.HOURS);

        TradeCandidatesDataDto dto = new TradeCandidatesDataDto("ok", "Sol", "profit", 30, List.of(
                createCandidate(1, "Gold", "Sol", "Galileo", "Alpha Centauri", "Columbus", 7000000L)
        ));
        LatestDisplay display = new LatestDisplay("trade_candidates", olderThan10h, dto, null);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        assertTrue(source.currentObjective().isEmpty(), "Result older than 10 hours should not be shown");
    }

    @Test
    void tradeCandidatesObjectiveContainsFirstCandidateAndOthersRow() {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        Instant savedAt = now.minus(1, ChronoUnit.HOURS);

        TradeCandidatesDataDto dto = new TradeCandidatesDataDto("ok", "Sol", "profit", 30, List.of(
                createCandidate(1, "Gold", "Sol", "Galileo", "Alpha Centauri", "Columbus", 7000000L),
                createCandidate(2, "Silver", "Sol", "Daedalus", "Barnard's Star", "Boston Base", 5000000L),
                createCandidate(3, "Bauxite", "Sol", "Titan City", "Wolf 359", "Hub", 3000000L)
        ));
        LatestDisplay display = new LatestDisplay("trade_candidates", savedAt, dto, null);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        Optional<HudObjective> opt = source.currentObjective();
        assertTrue(opt.isPresent());

        HudObjective objective = opt.get();
        assertEquals(HudObjective.PRIORITY_AMBIENT, objective.priority());
        assertNotNull(objective.title());

        List<HudRow> rows = objective.rows();
        // Row 1: Commodity, Row 2: Buy, Row 3: Sell, Row 4: Profit, Row 5: Freshness, Row 6: otherCandidates
        assertEquals(6, rows.size());

        // Check content
        assertTrue(rows.get(1).value().contains("Galileo"));
        assertTrue(rows.get(1).value().contains("Sol"));
        assertTrue(rows.get(2).value().contains("Columbus"));
        assertTrue(rows.get(2).value().contains("Alpha Centauri"));
        assertTrue(rows.get(3).value().contains("7,000,000"));
        assertEquals("2", rows.get(5).value(), "other candidates count should be 2");
    }

    @Test
    void outfittingObjectiveContainsFiveRows() {
        Instant now = Instant.parse("2026-09-26T14:00:00Z");
        Instant savedAt = now.minus(2, ChronoUnit.HOURS);

        OutfittingDataDto dto = new OutfittingDataDto(
                "found",
                "5A FSD",
                new MatchedModuleDto("5A Frame Shift Drive", 5, "A"),
                "Shinrarta Dezhra",
                "Jameson Memorial",
                "Orbis",
                12.34,
                "12.34",
                450.0,
                "450",
                5100000L,
                "5,100,000",
                "2026-09-26 12:00:00+00",
                2L,
                "2",
                false
        );
        LatestDisplay display = new LatestDisplay("outfitting", savedAt, null, dto);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        Optional<HudObjective> opt = source.currentObjective();
        assertTrue(opt.isPresent());

        HudObjective objective = opt.get();
        assertEquals(HudObjective.PRIORITY_AMBIENT, objective.priority());

        List<HudRow> rows = objective.rows();
        assertEquals(5, rows.size(), "Outfitting card must have 5 rows: module, station, system, distance, price");

        assertTrue(rows.get(0).value().contains("Frame Shift Drive"));
        assertEquals("Jameson Memorial", rows.get(1).value());
        assertEquals("Shinrarta Dezhra", rows.get(2).value());
        assertTrue(rows.get(3).value().contains("12.34 ly"));
        assertTrue(rows.get(4).value().contains("5,100,000 cr"));
    }
}
