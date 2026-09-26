package elite.intel.ui.screen;

import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.MatchedModuleDto;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.ui.screen.QueryResultCard.CardRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import javax.swing.JButton;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryResultDisplayPanelTest {

    private final QueryResultDisplayManager manager = QueryResultDisplayManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.clearAll();
    }

    @AfterEach
    void tearDown() {
        manager.clearAll();
    }

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
                505.0,
                "505",
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
                720,
                "720",
                profit,
                String.format("%,d", profit),
                3.20,
                "3.20",
                12.61,
                "12.61"
        );
    }

    @Test
    void tradeCandidateCardHasElevenRowsInSpecifiedOrder() {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        TradeCandidateDto cand = createCandidate(1, "Silver", "Sol", "Galileo", "Barnard's Star", "Boston Base", 5119200L);

        QueryResultCard card = QueryResultCard.forTradeCandidate(cand, now);

        assertTrue(card.getCardTitle().contains("1"), "Title must contain rank 1");
        List<CardRow> rows = card.getRows();
        assertEquals(11, rows.size(), "Trade candidate card must have exactly 11 rows");

        // Row 1: Commodity
        assertEquals("Silver", rows.get(0).value());
        // Row 2: Buy
        assertTrue(rows.get(1).value().contains("Galileo"));
        assertTrue(rows.get(1).value().contains("Sol"));
        // Row 3: Buy Ls
        assertEquals("505 Ls", rows.get(2).value());
        // Row 4: Sell
        assertTrue(rows.get(3).value().contains("Boston Base"));
        assertTrue(rows.get(3).value().contains("Barnard's Star"));
        // Row 5: Sell Ls
        assertEquals("1,200 Ls", rows.get(4).value());
        // Row 6: Trip profit
        assertEquals("5,119,200 cr", rows.get(5).value());
        assertTrue(rows.get(5).isHighlight());
        // Row 7: Unit profit
        assertEquals("10,000 cr/t", rows.get(6).value());
        // Row 8: Units
        assertEquals("720 t", rows.get(7).value());
        // Row 9: Route distance
        assertEquals("12.61 ly", rows.get(8).value());
        // Row 10: Distance from reference
        assertEquals("3.20 ly", rows.get(9).value());
        // Row 11: Freshness (older of 10:00 and 11:00 vs 12:00 -> 2 hours)
        assertTrue(rows.get(10).value().contains("2"), "Freshness must be calculated from older update timestamp (2 hours)");
    }

    @Test
    void outfittingCardHasEightRowsWithWarningStyleWhenStale() {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        OutfittingDataDto staleDto = new OutfittingDataDto(
                "found",
                "5A FSD",
                new MatchedModuleDto("5A Frame Shift Drive", 5, "A"),
                "Shinrarta Dezhra",
                "Jameson Memorial",
                "Orbis Starport",
                12.34,
                "12.34",
                450.0,
                "450",
                5100000L,
                "5,100,000",
                "2026-09-18 12:00:00+00", // 8 days ago
                192L,
                "192",
                true // stale
        );

        QueryResultCard card = QueryResultCard.forOutfitting(staleDto, now);
        List<CardRow> rows = card.getRows();
        assertEquals(8, rows.size(), "Outfitting card must have exactly 8 rows");

        assertEquals("5A Frame Shift Drive", rows.get(0).value());
        assertEquals("Jameson Memorial", rows.get(1).value());
        assertEquals("Shinrarta Dezhra", rows.get(2).value());
        assertEquals("Orbis Starport", rows.get(3).value());
        assertEquals("12.34 ly", rows.get(4).value());
        assertEquals("450 Ls", rows.get(5).value());
        assertEquals("5,100,000 cr", rows.get(6).value());
        assertTrue(rows.get(7).isWarn(), "Freshness row must have warning style when stale");
    }

    @Test
    void panelRendersThreeCardsForThreeTradeCandidates() {
        TradeCandidatesDataDto dto = new TradeCandidatesDataDto("ok", "Sol", "profit", 30, List.of(
                createCandidate(1, "Gold", "Sol", "Galileo", "Alpha Centauri", "Columbus", 7000000L),
                createCandidate(2, "Silver", "Sol", "Daedalus", "Barnard's Star", "Boston Base", 5000000L),
                createCandidate(3, "Bauxite", "Sol", "Titan City", "Wolf 359", "Hub", 3000000L)
        ));
        manager.saveTradeCandidates(dto);

        QueryResultDisplayPanel panel = new QueryResultDisplayPanel();
        panel.refreshFromDb();

        assertTrue(panel.getHeaderLabel().getText().contains("Sol"), "Header was: " + panel.getHeaderLabel().getText());
        assertTrue(panel.getHeaderLabel().getText().contains("30 ly"));

        Component[] comps = panel.getCardsContainer().getComponents();
        int cardCount = 0;
        for (Component c : comps) {
            if (c instanceof QueryResultCard card) {
                cardCount++;
                assertTrue(card.getCardTitle().contains(String.valueOf(cardCount)));
            }
        }
        assertEquals(3, cardCount, "Panel must render 3 distinct cards for 3 candidates");
        panel.dispose();
    }

    @Test
    void panelShowsEmptyMessageWhenNoSavedResults() {
        QueryResultDisplayPanel panel = new QueryResultDisplayPanel();
        panel.refreshFromDb();

        assertEquals("", panel.getHeaderLabel().getText());
        Component[] comps = panel.getCardsContainer().getComponents();
        assertEquals(1, comps.length);
        assertInstanceOf(javax.swing.JLabel.class, comps[0]);
        panel.dispose();
    }

    @Test
    void tradeCandidateCardHasTwoButtonsAndDisablesOnAction() {
        TradeCandidateDto cand = createCandidate(1, "Gold", "Sol", "Galileo", "Alpha Centauri", "Columbus", 7000000L);
        QueryResultCard card = QueryResultCard.forTradeCandidate(cand, Instant.now());

        List<JButton> buttons = card.getActionButtons();
        assertEquals(2, buttons.size(), "Trade candidate card must have exactly 2 action buttons");

        JButton buyBtn = buttons.get(0);
        JButton sellBtn = buttons.get(1);

        assertTrue(buyBtn.isEnabled());
        assertTrue(sellBtn.isEnabled());

        // Click buy button
        buyBtn.doClick();

        // Both buttons on the card should be disabled immediately for 5 seconds
        assertFalse(buyBtn.isEnabled(), "Buy button should be disabled after click");
        assertFalse(sellBtn.isEnabled(), "Sell button should be disabled after click");
    }

    @Test
    void outfittingCardHasOneButtonAndDisablesOnAction() {
        OutfittingDataDto dto = new OutfittingDataDto(
                "found",
                "5A FSD",
                new MatchedModuleDto("Frame Shift Drive", 5, "A"),
                "Sol",
                "Daedalus",
                "Coriolis",
                10.5,
                120.0,
                5000000L,
                "2026-09-26 12:00:00+00",
                1L,
                false
        );
        QueryResultCard card = QueryResultCard.forOutfitting(dto, Instant.now());

        List<JButton> buttons = card.getActionButtons();
        assertEquals(1, buttons.size(), "Outfitting card must have exactly 1 action button");

        JButton goBtn = buttons.get(0);
        assertTrue(goBtn.isEnabled());

        goBtn.doClick();
        assertFalse(goBtn.isEnabled(), "Outfitting button should be disabled after click");
    }
}
