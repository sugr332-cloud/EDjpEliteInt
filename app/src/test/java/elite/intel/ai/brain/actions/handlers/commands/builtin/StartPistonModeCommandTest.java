package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.db.dao.DockingHistoryDao;
import elite.intel.db.dao.PistonModeDao;
import elite.intel.db.dao.QueryResultDisplayDao;
import elite.intel.db.managers.DockingHistoryManager;
import elite.intel.db.managers.PistonModeManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.util.Database;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.gameapi.journal.events.DockedEvent;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.util.Cypher;
import elite.intel.util.StringUtls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StartPistonModeCommandTest {

    private final AtomicBoolean inMainShip = new AtomicBoolean(true);
    private StartPistonModeCommand command;

    @BeforeAll
    static void initDb() throws Exception {
        Cypher.initializeKey();
        Database.init().close();
    }

    @BeforeEach
    void setUp() {
        inMainShip.set(true);
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

        command = new StartPistonModeCommand(PistonModeManager::getInstance, inMainShip::get);
    }

    @AfterEach
    void tearDown() {
        PistonModeManager.getInstance().stop();
    }

    @Test
    void metadata() {
        assertEquals("start_piston_mode", command.id());
        assertTrue(command.sendsGameInput(), "Must send game input by default for route plotting");
        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE)));
        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.ON_FOOT_STATION)));
    }

    @Test
    void refusesOutsideMainShip() {
        inMainShip.set(false);
        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.notInMainShip"), result);
    }

    @Test
    void refusesWhenDockingHistoryInsufficient() {
        // No docking history
        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.noHistory"), result);

        // Only 1 station in history
        seedDockingHistory("Station A", "System A", 100L, 1000L);
        result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.noHistory"), result);
    }

    @Test
    void refusesWhenNoRecentTradeCandidates() {
        JsonObject params = new JsonObject();
        params.addProperty("rank", 1);

        String result = command.execute(params, null);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.noRecentResult"), result);
    }

    @Test
    void refusesInvalidRank() {
        seedTradeCandidates("Station A", "System A", "Station B", "System B");

        // Non-positive rank
        JsonObject params0 = new JsonObject();
        params0.addProperty("rank", 0);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.invalidRank", 0), command.execute(params0, null));

        // Fraction rank
        JsonObject paramsFraction = new JsonObject();
        paramsFraction.addProperty("rank", 1.5);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.invalidRank", 1.5), command.execute(paramsFraction, null));

        // Out of bounds rank
        JsonObject paramsOutOfBounds = new JsonObject();
        paramsOutOfBounds.addProperty("rank", 5);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.invalidRank", 5), command.execute(paramsOutOfBounds, null));
    }

    @Test
    void startFromHistorySuccess() {
        seedDockingHistory("Station Y", "System Y", 200L, 2000L);
        seedDockingHistory("Station X", "System X", 100L, 1000L);

        String result = command.execute(new JsonObject(), null);
        assertNotNull(result);
        assertTrue(PistonModeManager.getInstance().isActive());
        assertTrue(result.contains("Station X") && result.contains("Station Y"));
    }

    @Test
    void startFromTradeCandidateSuccess() {
        seedTradeCandidates("Station Alpha", "System 1", "Station Beta", "System 2");

        JsonObject params = new JsonObject();
        params.addProperty("rank", 1);

        String result = command.execute(params, null);
        assertNotNull(result);
        assertTrue(PistonModeManager.getInstance().isActive());
        assertTrue(result.contains("Station Alpha") && result.contains("Station Beta"));
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

    private void seedTradeCandidates(String buyStn, String buySys, String sellStn, String sellSys) {
        TradeCandidateDto candidate = new TradeCandidateDto(
                1,
                "Gold",
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
}
