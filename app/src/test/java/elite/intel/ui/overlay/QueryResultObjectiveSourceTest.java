package elite.intel.ui.overlay;

import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.MatchedModuleDto;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
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

    @Test
    void testCurrentObjectiveWithEngineersModuleQuery() {
        Instant now = Instant.now();
        Instant savedAt = now.minus(5, ChronoUnit.MINUTES);

        EngineerProgressManager.getInstance().clear();
        EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");

        EngineersDisplayDto dto = new EngineersDisplayDto(
                "module",
                "Frame Shift Drive",
                List.of("Felicity Farseer", "Elvira Martuuk", "Professor Palin")
        );
        LatestDisplay display = new LatestDisplay("engineers", savedAt, null, null, dto);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        Optional<HudObjective> opt = source.currentObjective();
        assertTrue(opt.isPresent());

        HudObjective objective = opt.get();
        assertEquals(HudObjective.PRIORITY_AMBIENT, objective.priority());
        List<HudRow> rows = objective.rows();
        assertEquals(2, rows.size(), "Module engineers card must have 2 rows");

        assertEquals("Frame Shift Drive", rows.get(0).value());
        assertTrue(rows.get(1).value().contains("Felicity Farseer"));
        assertTrue(rows.get(1).value().contains("G5"));
        assertTrue(rows.get(1).value().contains("開放済み ランク 5") || rows.get(1).value().contains("Unlocked Rank 5"));
        assertTrue(rows.get(1).value().contains("2"), "Must mention other candidates count (+2)");
    }

    @Test
    void testCurrentObjectiveWithEngineersSingleEngineerQuery() {
        Instant now = Instant.now();
        Instant savedAt = now.minus(5, ChronoUnit.MINUTES);

        EngineerProgressManager.getInstance().clear();
        EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");

        EngineersDisplayDto dto = new EngineersDisplayDto(
                "engineer",
                null,
                List.of("Felicity Farseer")
        );
        LatestDisplay display = new LatestDisplay("engineers", savedAt, null, null, dto);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        Optional<HudObjective> opt = source.currentObjective();
        assertTrue(opt.isPresent());

        HudObjective objective = opt.get();
        List<HudRow> rows = objective.rows();
        assertEquals(2, rows.size());
        assertEquals("Felicity Farseer", rows.get(0).value());
        assertTrue(rows.get(1).label().contains("Farseer Inc"));
        assertTrue(rows.get(1).value().contains("開放済み ランク 5") || rows.get(1).value().contains("Unlocked Rank 5"));
    }

    @Test
    void testCurrentObjectiveWithEngineersProgressQuery() {
        Instant now = Instant.now();
        Instant savedAt = now.minus(5, ChronoUnit.MINUTES);

        EngineerProgressManager.getInstance().clear();
        EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");
        EngineerProgressManager.getInstance().recordProgress("Elvira Martuuk", null, "Invited", null, null, "2026-10-01T10:00:00Z");

        EngineersDisplayDto dto = new EngineersDisplayDto(
                "progress",
                null,
                List.of("Felicity Farseer", "Elvira Martuuk")
        );
        LatestDisplay display = new LatestDisplay("engineers", savedAt, null, null, dto);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        Optional<HudObjective> opt = source.currentObjective();
        assertTrue(opt.isPresent());

        HudObjective objective = opt.get();
        List<HudRow> rows = objective.rows();
        assertEquals(2, rows.size());
        assertEquals(HudText.get("overlay.card.row.engineerProgressList"), rows.get(0).value());
        assertTrue(rows.get(1).value().contains("開放済み: 1人 / 全2人") || rows.get(1).value().contains("Unlocked: 1 / 2"));
    }

    @Test
    void testCurrentObjectiveWithEngineersSingleEngineerInvitedAndAcquaintedStatusLocalizes() {
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            session.setLanguage(Language.JA);

            Instant now = Instant.now();
            Instant savedAt = now.minus(5, ChronoUnit.MINUTES);

            // 1. Invited
            EngineerProgressManager.getInstance().clear();
            EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Invited", null, null, "2026-10-01T10:00:00Z");
            EngineersDisplayDto dtoInv = new EngineersDisplayDto("engineer", null, List.of("Felicity Farseer"));
            QueryResultObjectiveSource srcInv = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, dtoInv)), () -> now
            );
            HudObjective objInv = srcInv.currentObjective().orElseThrow();
            assertEquals("招待済み", objInv.rows().get(1).value());

            // 2. Acquainted
            EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Acquainted", null, null, "2026-10-01T10:00:00Z");
            QueryResultObjectiveSource srcAcq = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, dtoInv)), () -> now
            );
            HudObjective objAcq = srcAcq.currentObjective().orElseThrow();
            assertEquals("面識あり", objAcq.rows().get(1).value());

            // 3. Known
            EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Known", null, null, "2026-10-01T10:00:00Z");
            QueryResultObjectiveSource srcKnown = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, dtoInv)), () -> now
            );
            HudObjective objKnown = srcKnown.currentObjective().orElseThrow();
            assertEquals("知っている", objKnown.rows().get(1).value());

            // 4. Barred
            EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Barred", null, null, "2026-10-01T10:00:00Z");
            QueryResultObjectiveSource srcBarred = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, dtoInv)), () -> now
            );
            HudObjective objBarred = srcBarred.currentObjective().orElseThrow();
            assertTrue(objBarred.rows().get(1).value().contains("出入り禁止"));

            // 5. No Record
            EngineerProgressManager.getInstance().clear();
            QueryResultObjectiveSource srcNoRec = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, dtoInv)), () -> now
            );
            HudObjective objNoRec = srcNoRec.currentObjective().orElseThrow();
            assertEquals("記録なし", objNoRec.rows().get(1).value());
        } finally {
            session.setLanguage(orig);
        }
    }

    @Test
    void testCurrentObjectiveWithEngineersProgressQueryDoesNotContainSearchTime() {
        Instant now = Instant.now();
        Instant savedAt = now.minus(5, ChronoUnit.MINUTES);

        EngineersDisplayDto dto = new EngineersDisplayDto(
                "progress",
                null,
                List.of("Felicity Farseer", "Elvira Martuuk")
        );
        LatestDisplay display = new LatestDisplay("engineers", savedAt, null, null, dto);

        QueryResultObjectiveSource source = new QueryResultObjectiveSource(() -> Optional.of(display), () -> now);
        Optional<HudObjective> opt = source.currentObjective();
        assertTrue(opt.isPresent());

        HudObjective objective = opt.get();
        List<HudRow> rows = objective.rows();
        assertEquals(2, rows.size());

        assertFalse(rows.get(0).value().contains("検索時刻"), "Must not contain Japanese search time label");
        assertFalse(rows.get(0).value().contains("Time"), "Must not contain English search time label");
        assertEquals(HudText.get("overlay.card.row.engineerProgressList"), rows.get(0).value());
    }

    @Test
    void testCurrentObjectiveWithEngineersInEnglishLocaleHasNoJapanese() {
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            session.setLanguage(Language.EN);

            Instant now = Instant.now();
            Instant savedAt = now.minus(5, ChronoUnit.MINUTES);

            EngineerProgressManager.getInstance().clear();
            EngineerProgressManager.getInstance().recordProgress("Felicity Farseer", null, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");

            // 1. Module
            EngineersDisplayDto modDto = new EngineersDisplayDto(
                    "module", "Frame Shift Drive", List.of("Felicity Farseer", "Elvira Martuuk")
            );
            QueryResultObjectiveSource srcMod = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, modDto)), () -> now
            );
            HudObjective objMod = srcMod.currentObjective().orElseThrow();
            assertFalse(containsJapanese(objMod.title()), "Title contains Japanese in English locale: " + objMod.title());
            for (HudRow row : objMod.rows()) {
                assertFalse(containsJapanese(row.label()), "Label contains Japanese in English locale: " + row.label());
                assertFalse(containsJapanese(row.value()), "Value contains Japanese in English locale: " + row.value());
            }

            // 2. Single Engineer
            EngineersDisplayDto engDto = new EngineersDisplayDto(
                    "engineer", null, List.of("Felicity Farseer")
            );
            QueryResultObjectiveSource srcEng = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, engDto)), () -> now
            );
            HudObjective objEng = srcEng.currentObjective().orElseThrow();
            assertFalse(containsJapanese(objEng.title()), "Title contains Japanese in English locale: " + objEng.title());
            for (HudRow row : objEng.rows()) {
                assertFalse(containsJapanese(row.label()), "Label contains Japanese in English locale: " + row.label());
                assertFalse(containsJapanese(row.value()), "Value contains Japanese in English locale: " + row.value());
            }

            // 3. Progress
            EngineersDisplayDto progDto = new EngineersDisplayDto(
                    "progress", null, List.of("Felicity Farseer")
            );
            QueryResultObjectiveSource srcProg = new QueryResultObjectiveSource(
                    () -> Optional.of(new LatestDisplay("engineers", savedAt, null, null, progDto)), () -> now
            );
            HudObjective objProg = srcProg.currentObjective().orElseThrow();
            assertFalse(containsJapanese(objProg.title()), "Title contains Japanese in English locale: " + objProg.title());
            for (HudRow row : objProg.rows()) {
                assertFalse(containsJapanese(row.label()), "Label contains Japanese in English locale: " + row.label());
                assertFalse(containsJapanese(row.value()), "Value contains Japanese in English locale: " + row.value());
            }
        } finally {
            session.setLanguage(orig);
        }
    }

    private static boolean containsJapanese(String s) {
        if (s == null) return false;
        return s.chars().anyMatch(ch ->
                Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HIRAGANA ||
                Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.KATAKANA ||
                Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        );
    }
}
