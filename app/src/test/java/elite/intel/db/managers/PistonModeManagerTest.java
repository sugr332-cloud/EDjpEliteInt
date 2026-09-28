package elite.intel.db.managers;

import com.google.gson.JsonObject;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.dao.DockingHistoryDao;
import elite.intel.db.dao.PistonModeDao;
import elite.intel.db.dao.QueryResultDisplayDao;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.db.util.Database;
import elite.intel.gameapi.journal.events.DockedEvent;
import elite.intel.gameapi.journal.events.UndockedEvent;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.session.StatusFlags;
import elite.intel.util.Cypher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PistonModeManagerTest {

    private final List<String> plottedRoutes = new ArrayList<>();
    private final List<String> reminders = new ArrayList<>();
    private final List<String> voicedMessages = new ArrayList<>();
    private final List<String> targetedStations = new ArrayList<>();

    private final AtomicBoolean isForeground = new AtomicBoolean(true);
    private final AtomicReference<Status> mockStatus = new AtomicReference<>();
    private final AtomicReference<String> mockStarSystem = new AtomicReference<>("Shinrarta Dezhra");
    private final AtomicReference<Long> mockSystemAddress = new AtomicReference<>(12345678L);
    private final AtomicLong mockDockedMarketId = new AtomicLong(0L);

    private PistonModeManager manager;

    @BeforeAll
    static void initDb() throws Exception {
        Cypher.initializeKey();
        Database.init().close();
    }

    @BeforeEach
    void setUp() {
        plottedRoutes.clear();
        reminders.clear();
        voicedMessages.clear();
        targetedStations.clear();

        isForeground.set(true);
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE));
        mockStarSystem.set("Shinrarta Dezhra");
        mockSystemAddress.set(12345678L);
        mockDockedMarketId.set(0L);

        Database.withDao(PistonModeDao.class, dao -> {
            dao.clear();
            return null;
        });
        Database.withDao(DockingHistoryDao.class, dao -> {
            dao.clear();
            return null;
        });
        Database.withDao(QueryResultDisplayDao.class, dao -> {
            dao.clear();
            return null;
        });

        manager = new PistonModeManager(
                (answer, dest) -> {
                    plottedRoutes.add(dest);
                    return answer;
                },
                (text, sys, stn, contact) -> reminders.add(stn + "@" + sys),
                event -> {
                    if (event instanceof AiVoxResponseEvent vox) {
                        voicedMessages.add(vox.getText());
                    }
                },
                isForeground::get,
                () -> 0, // no delay in tests
                (sys, stn) -> targetedStations.add(stn + "@" + sys),
                Runnable::run, // synchronous direct execution
                mockStatus::get,
                DockingHistoryManager::getInstance,
                QueryResultDisplayManager::getInstance,
                mockStarSystem::get,
                mockSystemAddress::get,
                mockDockedMarketId::get
        );
    }

    @AfterEach
    void tearDown() {
        manager.stop();
    }

    // 1. Start from history: docked at X -> wait without route; not docked at X -> route to Y
    @Test
    void startFromHistoryWhenDockedAtXWaitsWithoutRoute() {
        seedDockingHistory("Station Y", "System Y", 200L, 2000L);
        seedDockingHistory("Station X", "System X", 100L, 1000L);

        // Player is docked at Station X
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DOCKED));
        mockDockedMarketId.set(1000L);
        mockStarSystem.set("System X");
        mockSystemAddress.set(100L);

        PistonModeManager.StartResult result = manager.startFromHistory();
        assertTrue(result.success());
        assertTrue(manager.isActive());
        assertTrue(plottedRoutes.isEmpty(), "No route should be plotted when already docked at X");
        assertTrue(reminders.isEmpty());

        PistonModeDao.PistonModeEntry state = manager.getState();
        assertNotNull(state);
        assertEquals("Station X", state.stationAName());
        assertEquals("Station Y", state.stationBName());
    }

    @Test
    void startFromHistoryWhenNotDockedRoutesToY() {
        seedDockingHistory("Station Y", "System Y", 200L, 2000L);
        seedDockingHistory("Station X", "System X", 100L, 1000L);

        // Player is flying in space outside X
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE));
        mockDockedMarketId.set(0L);
        mockStarSystem.set("System X");
        mockSystemAddress.set(100L);

        PistonModeManager.StartResult result = manager.startFromHistory();
        assertTrue(result.success());
        assertTrue(manager.isActive());
        assertEquals(1, plottedRoutes.size());
        assertEquals("System Y", plottedRoutes.get(0));
        assertEquals(1, reminders.size());
        assertEquals("Station Y@System Y", reminders.get(0));
    }

    // 2. Start from trade candidates: docked at A -> wait; not docked at A -> route to A
    @Test
    void startFromTradeCandidatesWhenDockedAtAWaitsWithoutRoute() {
        seedTradeCandidates("Station A", "System A", "Station B", "System B", "Gold");

        // Docked at Station A
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DOCKED));
        mockDockedMarketId.set(5555L);
        mockStarSystem.set("System A");
        mockSystemAddress.set(500L);

        PistonModeManager.StartResult result = manager.startFromTradeCandidates(1);
        assertTrue(result.success());
        assertTrue(manager.isActive());
        assertTrue(plottedRoutes.isEmpty());

        PistonModeDao.PistonModeEntry state = manager.getState();
        assertNotNull(state);
        assertEquals("Station A", state.stationAName());
        assertEquals("Station B", state.stationBName());
        assertEquals(5555L, state.stationAMarketId(), "MarketId should be captured when docked at A");
    }

    @Test
    void startFromTradeCandidatesWhenElsewhereRoutesToA() {
        seedTradeCandidates("Station A", "System A", "Station B", "System B", "Gold");

        // Player is at Station B or elsewhere
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE));
        mockStarSystem.set("System B");
        mockSystemAddress.set(600L);

        PistonModeManager.StartResult result = manager.startFromTradeCandidates(1);
        assertTrue(result.success());
        assertTrue(manager.isActive());
        assertEquals(1, plottedRoutes.size());
        assertEquals("System A", plottedRoutes.get(0));
        assertEquals(1, reminders.size());
        assertEquals("Station A@System A", reminders.get(0));
    }

    // 3. Undocked: from A -> route to B; from B -> route to A
    @Test
    void undockedFromARoutesToBAndUndockedFromBRoutesToA() {
        seedDockingHistory("Station B", "System B", 200L, 2000L);
        seedDockingHistory("Station A", "System A", 100L, 1000L);
        manager.startFromHistory();

        // 1. Undock from A
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE));
        mockStarSystem.set("System A");
        mockSystemAddress.set(100L);
        plottedRoutes.clear();
        reminders.clear();

        JsonObject undockAJson = createUndockJson("Station A", 1000L);
        manager.onUndocked(new UndockedEvent(undockAJson));

        assertEquals(1, plottedRoutes.size());
        assertEquals("System B", plottedRoutes.get(0));
        assertEquals(1, reminders.size());
        assertEquals("Station B@System B", reminders.get(0));
        assertEquals(1, targetedStations.size());
        assertEquals("Station B@System B", targetedStations.get(0));

        // 2. Dock at B
        JsonObject dockBJson = createDockJson("Station B", "System B", 2000L, 200L);
        manager.onDocked(new DockedEvent(dockBJson));
        assertTrue(manager.isActive(), "Still active when docked at B");

        // 3. Undock from B
        plottedRoutes.clear();
        reminders.clear();
        targetedStations.clear();
        mockStarSystem.set("System B");
        mockSystemAddress.set(200L);

        JsonObject undockBJson = createUndockJson("Station B", 2000L);
        manager.onUndocked(new UndockedEvent(undockBJson));

        assertEquals(1, plottedRoutes.size());
        assertEquals("System A", plottedRoutes.get(0));
        assertEquals(1, reminders.size());
        assertEquals("Station A@System A", reminders.get(0));
        assertEquals(1, targetedStations.size());
        assertEquals("Station A@System A", targetedStations.get(0));
    }

    // 4. Undocked but conditions not met -> routeSkipped
    @Test
    void undockedWhenNotForegroundSkipsRoute() {
        seedDockingHistory("Station B", "System B", 200L, 2000L);
        seedDockingHistory("Station A", "System A", 100L, 1000L);
        manager.startFromHistory();

        plottedRoutes.clear();
        voicedMessages.clear();
        isForeground.set(false); // Game not in foreground!

        JsonObject undockJson = createUndockJson("Station A", 1000L);
        manager.onUndocked(new UndockedEvent(undockJson));

        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when not in foreground");
        assertEquals(1, voicedMessages.size());
        assertTrue(voicedMessages.get(0).contains("前のステーションへ") || voicedMessages.get(0).contains("previous station"));
    }

    @Test
    void undockedWhenGuiPanelOpenSkipsRoute() {
        seedDockingHistory("Station B", "System B", 200L, 2000L);
        seedDockingHistory("Station A", "System A", 100L, 1000L);
        manager.startFromHistory();

        plottedRoutes.clear();
        voicedMessages.clear();
        // GuiFocus is internal panel
        Status statusWithPanel = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        statusWithPanel.getStatus().setGuiFocus(StatusFlags.GuiFocus.INTERNAL_PANEL.getValue());
        mockStatus.set(statusWithPanel);

        JsonObject undockJson = createUndockJson("Station A", 1000L);
        manager.onUndocked(new UndockedEvent(undockJson));

        assertTrue(plottedRoutes.isEmpty());
        assertEquals(1, voicedMessages.size());
    }

    // 5. Dock at third station (not A or B) -> auto-terminates
    @Test
    void dockingAtForeignStationAutoTerminatesPistonMode() {
        seedDockingHistory("Station B", "System B", 200L, 2000L);
        seedDockingHistory("Station A", "System A", 100L, 1000L);
        manager.startFromHistory();
        assertTrue(manager.isActive());

        voicedMessages.clear();
        JsonObject foreignDock = createDockJson("Outpost C", "System C", 9999L, 999L);
        manager.onDocked(new DockedEvent(foreignDock));

        assertFalse(manager.isActive(), "Should auto-terminate when docked at foreign station");
        assertEquals(1, voicedMessages.size());
        assertTrue(voicedMessages.get(0).contains("Outpost C"));
    }

    // 6. Trade candidate start: MarketIDs saved on first docking, subsequent undocks use MarketID
    @Test
    void tradeCandidateCapturesMarketIdOnFirstDock() {
        seedTradeCandidates("Station Alpha", "System 1", "Station Beta", "System 2", "Silver");
        manager.startFromTradeCandidates(1);

        PistonModeDao.PistonModeEntry state0 = manager.getState();
        assertEquals(0L, state0.stationAMarketId());
        assertEquals(0L, state0.stationBMarketId());

        // First dock at Station Alpha
        JsonObject dockA = createDockJson("Station Alpha", "System 1", 1111L, 100L);
        manager.onDocked(new DockedEvent(dockA));

        PistonModeDao.PistonModeEntry state1 = manager.getState();
        assertEquals(1111L, state1.stationAMarketId());
        assertEquals(100L, state1.stationASystemAddress());
        assertEquals(0L, state1.stationBMarketId());

        // Undock from Alpha -> MarketID 1111 used to identify Alpha, routes to Beta
        plottedRoutes.clear();
        JsonObject undockA = createUndockJson("Station Alpha", 1111L);
        manager.onUndocked(new UndockedEvent(undockA));

        assertEquals(1, plottedRoutes.size());
        assertEquals("System 2", plottedRoutes.get(0));

        // First dock at Station Beta
        JsonObject dockB = createDockJson("Station Beta", "System 2", 2222L, 200L);
        manager.onDocked(new DockedEvent(dockB));

        PistonModeDao.PistonModeEntry state2 = manager.getState();
        assertEquals(1111L, state2.stationAMarketId());
        assertEquals(2222L, state2.stationBMarketId());
        assertEquals(200L, state2.stationBSystemAddress());
    }

    // 7. Same system endpoints -> sameSystem voice, no route plotted, reminder set
    @Test
    void sameSystemEndpointsSetsReminderWithoutRoute() {
        seedDockingHistory("Outpost B", "Sol", 100L, 2000L);
        seedDockingHistory("Starport A", "Sol", 100L, 1000L);

        // Player flying in Sol
        mockStatus.set(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE));
        mockStarSystem.set("Sol");
        mockSystemAddress.set(100L);

        PistonModeManager.StartResult result = manager.startFromHistory();
        assertTrue(result.success());
        assertTrue(plottedRoutes.isEmpty(), "No route should be plotted when in the same system");
        assertEquals(1, reminders.size());
        assertEquals("Outpost B@Sol", reminders.get(0));
    }

    // 8. Re-instantiating manager restores state from DB
    @Test
    void managerRestoresActiveStateFromDatabase() {
        seedDockingHistory("Station B", "System B", 200L, 2000L);
        seedDockingHistory("Station A", "System A", 100L, 1000L);
        manager.startFromHistory();
        assertTrue(manager.isActive());

        // Create new manager instance
        PistonModeManager newManager = new PistonModeManager(
                (ans, dest) -> ans,
                (t, s, st, c) -> {},
                event -> {},
                () -> true,
                () -> 0,
                (s, st) -> {},
                Runnable::run,
                mockStatus::get,
                DockingHistoryManager::getInstance,
                QueryResultDisplayManager::getInstance,
                mockStarSystem::get,
                mockSystemAddress::get,
                mockDockedMarketId::get
        );

        assertTrue(newManager.isActive());
        PistonModeDao.PistonModeEntry state = newManager.getState();
        assertNotNull(state);
        assertEquals("Station A", state.stationAName());
        assertEquals("Station B", state.stationBName());
    }

    // 9. Inactive manager ignores docked and undocked events
    @Test
    void inactiveManagerIgnoresEvents() {
        assertFalse(manager.isActive());

        JsonObject dockJson = createDockJson("Station C", "System C", 9999L, 999L);
        manager.onDocked(new DockedEvent(dockJson));
        assertTrue(voicedMessages.isEmpty());

        JsonObject undockJson = createUndockJson("Station C", 9999L);
        manager.onUndocked(new UndockedEvent(undockJson));
        assertTrue(plottedRoutes.isEmpty());
    }

    private void seedDockingHistory(String stationName, String starSystem, long systemAddress, long marketId) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "Docked");
        json.addProperty("timestamp", Instant.now().toString());
        json.addProperty("StationName", stationName);
        json.addProperty("StarSystem", starSystem);
        json.addProperty("SystemAddress", systemAddress);
        json.addProperty("MarketID", marketId);
        json.addProperty("StationType", "Coriolis");
        json.addProperty("DistFromStarLS", 100.0);
        DockingHistoryManager.getInstance().recordDocked(new DockedEvent(json));
    }

    private void seedTradeCandidates(String buyStn, String buySys, String sellStn, String sellSys, String commodity) {
        TradeCandidateDto candidate = new TradeCandidateDto(
                1,
                commodity,
                buySys,
                buyStn,
                1000,
                "1,000",
                5000L,
                "5,000",
                10.0,
                "10",
                "2026-09-28 10:00:00+00",
                sellSys,
                sellStn,
                2000,
                "2,000",
                5000L,
                "5,000",
                15.0,
                "15",
                "2026-09-28 10:00:00+00",
                1000,
                "1,000",
                700,
                "700",
                700000L,
                "700,000",
                5.0,
                "5.00",
                15.0,
                "15.00"
        );
        TradeCandidatesDataDto data = new TradeCandidatesDataDto(
                "ok",
                buySys,
                "profit",
                30,
                List.of(candidate)
        );
        QueryResultDisplayManager.getInstance().saveTradeCandidates(data);
    }

    private JsonObject createDockJson(String stationName, String starSystem, long marketId, long systemAddress) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "Docked");
        json.addProperty("timestamp", Instant.now().toString());
        json.addProperty("StationName", stationName);
        json.addProperty("StarSystem", starSystem);
        json.addProperty("SystemAddress", systemAddress);
        json.addProperty("MarketID", marketId);
        return json;
    }

    private JsonObject createUndockJson(String stationName, long marketId) {
        JsonObject json = new JsonObject();
        json.addProperty("event", "Undocked");
        json.addProperty("timestamp", Instant.now().toString());
        json.addProperty("StationName", stationName);
        json.addProperty("MarketID", marketId);
        return json;
    }
}
