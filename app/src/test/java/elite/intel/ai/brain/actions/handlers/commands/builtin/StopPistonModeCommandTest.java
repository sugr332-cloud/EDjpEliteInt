package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.db.dao.PistonModeDao;
import elite.intel.db.managers.PistonModeManager;
import elite.intel.db.util.Database;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.util.Cypher;
import elite.intel.util.StringUtls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StopPistonModeCommandTest {

    private StopPistonModeCommand command;

    @BeforeAll
    static void initDb() throws Exception {
        Cypher.initializeKey();
        Database.init().close();
    }

    @BeforeEach
    void setUp() {
        Database.withDao(PistonModeDao.class, dao -> {
            dao.clear();
            return null;
        });
        command = new StopPistonModeCommand(PistonModeManager::getInstance);
    }

    @AfterEach
    void tearDown() {
        PistonModeManager.getInstance().stop();
    }

    @Test
    void metadata() {
        assertEquals("stop_piston_mode", command.id());
        assertFalse(command.sendsGameInput(), "Must not send game input");
        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE)));
    }

    @Test
    void returnsStoppedNotActiveWhenNotActive() {
        assertFalse(PistonModeManager.getInstance().isActive());
        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.stoppedNotActive"), result);
    }

    @Test
    void stopsActiveModeAndReturnsStopped() {
        // Activate piston mode directly via DAO
        Database.withDao(PistonModeDao.class, dao -> {
            dao.saveState(true, "HISTORY", "Station A", "System A", 100L, 1000L,
                    "Station B", "System B", 200L, 2000L, null, Instant.now().toString());
            return null;
        });
        assertTrue(PistonModeManager.getInstance().isActive());

        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.pistonMode.stopped"), result);
        assertFalse(PistonModeManager.getInstance().isActive());
    }
}
