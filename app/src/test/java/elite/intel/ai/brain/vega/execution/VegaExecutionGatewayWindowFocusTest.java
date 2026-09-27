package elite.intel.ai.brain.vega.execution;

import com.google.gson.JsonObject;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.queries.IntelQuery;
import elite.intel.ai.brain.vega.VegaNarrator;
import elite.intel.ai.brain.vega.VegaRuntimeTestSupport;
import elite.intel.ai.brain.vega.model.execution.ExecutionRequest;
import elite.intel.ai.brain.vega.tools.SystemFunctionResultFields;
import elite.intel.ui.support.GameWindowActivator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class VegaExecutionGatewayWindowFocusTest {

    private static final Executor SYNC = Runnable::run;

    private RecordingNarrator narrator;
    private FakeWindowOps fakeOps;
    private final WinDef.HWND gameHwnd = new WinDef.HWND(new Pointer(0x1000));
    private final WinDef.HWND otherHwnd = new WinDef.HWND(new Pointer(0x9999));

    @BeforeEach
    void setUp() {
        narrator = new RecordingNarrator();
        VegaRuntimeTestSupport.installNarrator(narrator);

        fakeOps = new FakeWindowOps();
        fakeOps.isWindows = true;
        fakeOps.foundGameWindow = gameHwnd;
        fakeOps.foregroundWindow = otherHwnd;
        fakeOps.onActivate = () -> fakeOps.foregroundWindow = gameHwnd;

        GameWindowActivator.resetForTesting();
        GameWindowActivator.setNativeWindowOpsForTesting(fakeOps);
    }

    @AfterEach
    void tearDown() {
        VegaRuntimeTestSupport.clearInstalledGraph();
        GameWindowActivator.resetForTesting();
    }

    @Test
    void testC_commandWithInputEnsuresForegroundOnce() throws Exception {
        TestInputCommand cmd = new TestInputCommand("jump_hyperspace");
        VegaExecutionGateway gateway = new VegaExecutionGateway(
                Map.of(cmd.id(), cmd), Map.of(), Map.of(), SYNC, SYNC);

        ExecutionRequest request = new ExecutionRequest(
                "req-1", cmd.id(), new JsonObject(), "jump", 1L);

        JsonObject result = gateway.submit(request).get();

        assertEquals("completed_by_executor", result.get(SystemFunctionResultFields.STATUS).getAsString());
        assertTrue(cmd.invoked, "Command must be executed");
        assertEquals(1, fakeOps.activateCalls.size(), "(c) Foregrounding must be called exactly once per command");
    }

    @Test
    void testD_foregroundFailureAbortsExecutionAndSpeaksMessage() throws Exception {
        fakeOps.activateResult = false; // Activation fails
        fakeOps.onActivate = () -> {};  // Never becomes foreground

        TestInputCommand cmd = new TestInputCommand("open_galaxy_map");
        VegaExecutionGateway gateway = new VegaExecutionGateway(
                Map.of(cmd.id(), cmd), Map.of(), Map.of(), SYNC, SYNC);

        ExecutionRequest request = new ExecutionRequest(
                "req-2", cmd.id(), new JsonObject(), "map", 1L);

        JsonObject result = gateway.submit(request).get();

        assertEquals("aborted", result.get(SystemFunctionResultFields.STATUS).getAsString());
        assertFalse(cmd.invoked, "(d) Command handler must NOT be executed when foreground fails");
        assertTrue(narrator.narratedLines.stream().anyMatch(msg ->
                msg.contains("ゲームのウィンドウを前面にできないため") || msg.contains("Could not bring the game window")),
                "(d) Abort message must be voiced when foreground fails");
    }

    @Test
    void testF_queryDoesNotEnsureForeground() throws Exception {
        TestQuery query = new TestQuery("query_trade_candidates");
        VegaExecutionGateway gateway = new VegaExecutionGateway(
                Map.of(), Map.of(query.id(), query), Map.of(), SYNC, SYNC);

        ExecutionRequest request = new ExecutionRequest(
                "req-3", query.id(), new JsonObject(), "find trade", 1L);

        gateway.submit(request).get();

        assertTrue(query.invoked, "Query must be executed");
        assertEquals(0, fakeOps.activateCalls.size(), "(f) Query must never attempt to foreground the game");
    }

    @Test
    void testF_commandWithoutGameInputDoesNotEnsureForeground() throws Exception {
        NoInputCommand noInputCmd = new NoInputCommand("sleep_ignore");
        VegaExecutionGateway gateway = new VegaExecutionGateway(
                Map.of(noInputCmd.id(), noInputCmd), Map.of(), Map.of(), SYNC, SYNC);

        ExecutionRequest request = new ExecutionRequest(
                "req-4", noInputCmd.id(), new JsonObject(), "sleep", 1L);

        gateway.submit(request).get();

        assertTrue(noInputCmd.invoked, "No-input command must be executed");
        assertEquals(0, fakeOps.activateCalls.size(), "(f) sendsGameInput=false command must never attempt to foreground");
    }

    // -------------------------------------------------------------------------
    // Helpers & Fakes
    // -------------------------------------------------------------------------

    private static final class TestInputCommand implements IntelCommand {
        private final String id;
        boolean invoked;

        TestInputCommand(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String execute(JsonObject params, String responseText) {
            invoked = true;
            return null;
        }
    }

    private static final class NoInputCommand implements IntelCommand {
        private final String id;
        boolean invoked;

        NoInputCommand(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean sendsGameInput() {
            return false;
        }

        @Override
        public String execute(JsonObject params, String responseText) {
            invoked = true;
            return null;
        }
    }

    private static final class TestQuery implements IntelQuery {
        private final String id;
        boolean invoked;

        TestQuery(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public JsonObject handle(String action, JsonObject params, String text) {
            invoked = true;
            JsonObject payload = new JsonObject();
            payload.addProperty("status", "ok");
            return payload;
        }
    }

    private static final class RecordingNarrator implements VegaNarrator {
        final List<String> narratedLines = new ArrayList<>();

        @Override
        public void narrate(String line, String instructions) {
            narratedLines.add(line);
        }

        @Override
        public void filler(String spokenText, boolean autoProceed) {
        }

        @Override
        public void announce(String phrase, boolean urgent) {
            narratedLines.add(phrase);
        }
    }

    private static final class FakeWindowOps implements GameWindowActivator.NativeWindowOps {
        boolean isWindows = true;
        WinDef.HWND foundGameWindow;
        WinDef.HWND foregroundWindow;
        boolean activateResult = true;
        Runnable onActivate = () -> {};
        final List<WinDef.HWND> activateCalls = new ArrayList<>();

        @Override
        public boolean isWindows() {
            return isWindows;
        }

        @Override
        public WinDef.HWND findGameWindow() {
            return foundGameWindow;
        }

        @Override
        public boolean isWindowValid(WinDef.HWND hwnd) {
            return hwnd != null && hwnd.equals(foundGameWindow);
        }

        @Override
        public WinDef.HWND getForegroundWindow() {
            return foregroundWindow;
        }

        @Override
        public String getWindowTitle(WinDef.HWND hwnd) {
            return "";
        }

        @Override
        public boolean activateWindow(WinDef.HWND hwnd) {
            activateCalls.add(hwnd);
            onActivate.run();
            return activateResult;
        }

        long currentTime = 1000L;

        @Override
        public long currentTimeMillis() {
            return currentTime;
        }

        @Override
        public void sleep(long ms) {
            currentTime += ms;
        }
    }
}
