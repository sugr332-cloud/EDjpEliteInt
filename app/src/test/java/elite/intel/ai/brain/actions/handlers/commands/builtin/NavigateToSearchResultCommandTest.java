package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.MatchedModuleDto;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.gameapi.ReminderContact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class NavigateToSearchResultCommandTest {

    private final QueryResultDisplayManager displayManager = QueryResultDisplayManager.getInstance();

    private record ReminderRecord(String text, String starSystem, String stationName, ReminderContact contact) {
    }

    private record RoutePlotRecord(String answer, String destination) {
    }

    private final List<ReminderRecord> reminders = new ArrayList<>();
    private final List<RoutePlotRecord> plottedRoutes = new ArrayList<>();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final AtomicBoolean inMainShip = new AtomicBoolean(true);

    private NavigateToSearchResultCommand command;

    @BeforeEach
    void setUp() {
        displayManager.clearAll();
        reminders.clear();
        plottedRoutes.clear();
        publishedEvents.clear();
        inMainShip.set(true);

        command = new NavigateToSearchResultCommand(
                displayManager::getLatest,
                inMainShip::get,
                (text, system, station, contact) -> reminders.add(new ReminderRecord(text, system, station, contact)),
                (answer, dest) -> {
                    plottedRoutes.add(new RoutePlotRecord(answer, dest));
                    return answer + " [PLOTTED]";
                },
                publishedEvents::add
        );
    }

    @AfterEach
    void tearDown() {
        displayManager.clearAll();
    }

    private static TradeCandidateDto createTradeCandidate(int rank, String buySys, String buyStn, String sellSys, String sellStn) {
        return new TradeCandidateDto(
                rank,
                "Gold",
                buySys,
                buyStn,
                10000,
                "10,000",
                5000L,
                "5,000",
                200.0,
                "200",
                "2026-09-26 10:00:00+00",
                sellSys,
                sellStn,
                20000,
                "20,000",
                3000L,
                "3,000",
                500.0,
                "500",
                "2026-09-26 11:00:00+00",
                10000,
                "10,000",
                700,
                "700",
                7000000L,
                "7,000,000",
                5.0,
                "5.00",
                15.0,
                "15.00"
        );
    }

    private static OutfittingDataDto createOutfittingDto(String system, String station) {
        return new OutfittingDataDto(
                "found",
                "5A FSD",
                new MatchedModuleDto("Frame Shift Drive", 5, "A"),
                system,
                station,
                "Coriolis",
                10.5,
                120.0,
                5000000L,
                "2026-09-26 12:00:00+00",
                1L,
                false
        );
    }

    @Test
    void isAvailableInGuiAndVega() {
        assertTrue(command.isAvailableIn(IntelActionContext.GUI));
        assertTrue(command.isAvailableIn(IntelActionContext.VEGA_COMMANDER));
    }

    @Test
    void parametersConformToValidation() {
        List<ActionParameterSpec> params = command.parameters();
        assertEquals(2, params.size());
        assertEquals("rank", params.get(0).getName());
        assertEquals("number", params.get(0).getType());
        assertEquals("leg", params.get(1).getName());
        assertEquals("string", params.get(1).getType());
    }

    @Test
    void tradeCandidatesRoutePlottingForRankAndLegCombinations() {
        TradeCandidatesDataDto dto = new TradeCandidatesDataDto(
                "ok",
                "Sol",
                "Sol",
                "current",
                3,
                "profit",
                30,
                List.of(
                        createTradeCandidate(1, "Sol", "Galileo", "Barnard's Star", "Boston Base"),
                        createTradeCandidate(2, "Alpha Centauri", "Columbus", "Wolf 359", "Hopkins"),
                        createTradeCandidate(3, "Sirius", "Patterson", "Procyon", "Trigg")
                )
        );
        displayManager.saveTradeCandidates(dto);

        // 1. rank 1, buy (default)
        JsonObject p1 = new JsonObject();
        p1.addProperty("rank", 1);
        p1.addProperty("leg", "buy");
        String res1 = command.execute(p1, "");
        assertNotNull(res1);
        assertTrue(res1.contains("Galileo"));
        assertTrue(res1.contains("Sol"));
        assertEquals(1, reminders.size());
        assertEquals("Sol", reminders.get(0).starSystem());
        assertEquals("Galileo", reminders.get(0).stationName());
        assertEquals(1, plottedRoutes.size());
        assertEquals("Sol", plottedRoutes.get(0).destination());
        assertTrue(publishedEvents.isEmpty());

        reminders.clear();
        plottedRoutes.clear();

        // 2. rank 1, sell
        JsonObject p2 = new JsonObject();
        p2.addProperty("rank", 1);
        p2.addProperty("leg", "sell");
        String res2 = command.execute(p2, "");
        assertNotNull(res2);
        assertTrue(res2.contains("Boston Base"));
        assertTrue(res2.contains("Barnard's Star"));
        assertEquals("Barnard's Star", reminders.get(0).starSystem());
        assertEquals("Boston Base", reminders.get(0).stationName());
        assertEquals("Barnard's Star", plottedRoutes.get(0).destination());

        reminders.clear();
        plottedRoutes.clear();

        // 3. rank 2, buy
        JsonObject p3 = new JsonObject();
        p3.addProperty("rank", 2);
        p3.addProperty("leg", "buy");
        command.execute(p3, "");
        assertEquals("Alpha Centauri", reminders.get(0).starSystem());
        assertEquals("Columbus", reminders.get(0).stationName());
        assertEquals("Alpha Centauri", plottedRoutes.get(0).destination());

        reminders.clear();
        plottedRoutes.clear();

        // 4. rank 2, sell
        JsonObject p4 = new JsonObject();
        p4.addProperty("rank", 2);
        p4.addProperty("leg", "sell");
        command.execute(p4, "");
        assertEquals("Wolf 359", reminders.get(0).starSystem());
        assertEquals("Hopkins", reminders.get(0).stationName());
        assertEquals("Wolf 359", plottedRoutes.get(0).destination());

        reminders.clear();
        plottedRoutes.clear();

        // 5. rank 3, buy
        JsonObject p5 = new JsonObject();
        p5.addProperty("rank", 3);
        p5.addProperty("leg", "buy");
        command.execute(p5, "");
        assertEquals("Sirius", reminders.get(0).starSystem());
        assertEquals("Patterson", reminders.get(0).stationName());
        assertEquals("Sirius", plottedRoutes.get(0).destination());

        reminders.clear();
        plottedRoutes.clear();

        // 6. rank 3, sell
        JsonObject p6 = new JsonObject();
        p6.addProperty("rank", 3);
        p6.addProperty("leg", "sell");
        command.execute(p6, "");
        assertEquals("Procyon", reminders.get(0).starSystem());
        assertEquals("Trigg", reminders.get(0).stationName());
        assertEquals("Procyon", plottedRoutes.get(0).destination());
    }

    @Test
    void outfittingRoutePlotting() {
        OutfittingDataDto dto = createOutfittingDto("Sol", "Daedalus");
        displayManager.saveOutfitting(dto);

        JsonObject p = new JsonObject();
        p.addProperty("rank", 1);
        p.addProperty("leg", "buy");
        String res = command.execute(p, "");

        assertNotNull(res);
        assertTrue(res.contains("Daedalus"));
        assertTrue(res.contains("Sol"));
        assertEquals(1, reminders.size());
        assertEquals("Sol", reminders.get(0).starSystem());
        assertEquals("Daedalus", reminders.get(0).stationName());
        assertEquals(1, plottedRoutes.size());
        assertEquals("Sol", plottedRoutes.get(0).destination());
    }

    @Test
    void guiSourcePublishesAiVoxResponseEventAndReturnsNull() {
        OutfittingDataDto dto = createOutfittingDto("Sol", "Daedalus");
        displayManager.saveOutfitting(dto);

        JsonObject p = new JsonObject();
        p.addProperty("rank", 1);
        p.addProperty("source", "gui");

        String res = command.execute(p, "");
        assertNull(res, "Return value must be null when source=gui");
        assertEquals(1, publishedEvents.size(), "AiVoxResponseEvent must be published exactly once");
        assertTrue(publishedEvents.get(0) instanceof AiVoxResponseEvent);
        AiVoxResponseEvent event = (AiVoxResponseEvent) publishedEvents.get(0);
        assertTrue(event.getText().contains("Daedalus"));
    }

    @Test
    void rejectsWhenNotInMainShip() {
        inMainShip.set(false);
        OutfittingDataDto dto = createOutfittingDto("Sol", "Daedalus");
        displayManager.saveOutfitting(dto);

        // Voice route
        JsonObject pVoice = new JsonObject();
        pVoice.addProperty("rank", 1);
        String resVoice = command.execute(pVoice, "");
        assertNotNull(resVoice);
        assertTrue(plottedRoutes.isEmpty(), "RoutePlotter must not be called");
        assertTrue(reminders.isEmpty(), "ReminderManager must not be called");

        // GUI route
        JsonObject pGui = new JsonObject();
        pGui.addProperty("rank", 1);
        pGui.addProperty("source", "gui");
        String resGui = command.execute(pGui, "");
        assertNull(resGui);
        assertEquals(1, publishedEvents.size());
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }

    @Test
    void rejectsWhenNoResultOrExpired() {
        // No results saved
        JsonObject p = new JsonObject();
        String res = command.execute(p, "");
        assertNotNull(res);
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());

        // Result saved but expired (> 10 hours ago)
        OutfittingDataDto dto = createOutfittingDto("Sol", "Daedalus");
        displayManager.saveOutfitting(dto);

        NavigateToSearchResultCommand cmdExpired = new NavigateToSearchResultCommand(
                () -> Optional.of(new LatestDisplay(
                        QueryResultDisplayManager.TYPE_OUTFITTING,
                        Instant.now().minus(Duration.ofHours(11)),
                        null,
                        dto
                )),
                () -> true,
                (t, s, st, c) -> reminders.add(new ReminderRecord(t, s, st, c)),
                (a, d) -> {
                    plottedRoutes.add(new RoutePlotRecord(a, d));
                    return a;
                },
                publishedEvents::add
        );

        String expiredRes = cmdExpired.execute(p, "");
        assertNotNull(expiredRes);
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }

    @Test
    void rejectsInvalidRankOrLeg() {
        TradeCandidatesDataDto dto = new TradeCandidatesDataDto(
                "ok",
                "Sol",
                "Sol",
                "current",
                2,
                "profit",
                30,
                List.of(
                        createTradeCandidate(1, "Sol", "Galileo", "Barnard's Star", "Boston Base"),
                        createTradeCandidate(2, "Alpha Centauri", "Columbus", "Wolf 359", "Hopkins")
                )
        );
        displayManager.saveTradeCandidates(dto);

        // 1. Rank out of range (< 1)
        JsonObject pRankLow = new JsonObject();
        pRankLow.addProperty("rank", 0);
        String resRankLow = command.execute(pRankLow, "");
        assertNotNull(resRankLow);
        assertTrue(plottedRoutes.isEmpty());

        // 2. Rank out of range (> count)
        JsonObject pRankHigh = new JsonObject();
        pRankHigh.addProperty("rank", 3);
        String resRankHigh = command.execute(pRankHigh, "");
        assertNotNull(resRankHigh);
        assertTrue(plottedRoutes.isEmpty());

        // 3. Rank not an integer (2.5)
        JsonObject pRankFloat = new JsonObject();
        pRankFloat.addProperty("rank", 2.5);
        String resRankFloat = command.execute(pRankFloat, "");
        assertNotNull(resRankFloat);
        assertTrue(plottedRoutes.isEmpty());

        // 4. Invalid leg
        JsonObject pInvalidLeg = new JsonObject();
        pInvalidLeg.addProperty("rank", 1);
        pInvalidLeg.addProperty("leg", "unknown");
        String resInvalidLeg = command.execute(pInvalidLeg, "");
        assertNotNull(resInvalidLeg);
        assertTrue(plottedRoutes.isEmpty());
    }

    @Test
    void rejectsRankOtherThanOneForOutfitting() {
        OutfittingDataDto dto = createOutfittingDto("Sol", "Daedalus");
        displayManager.saveOutfitting(dto);

        JsonObject p = new JsonObject();
        p.addProperty("rank", 2);
        String res = command.execute(p, "");
        assertNotNull(res);
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }
}
