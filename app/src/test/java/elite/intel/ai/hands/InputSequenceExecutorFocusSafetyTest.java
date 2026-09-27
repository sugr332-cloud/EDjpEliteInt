package elite.intel.ai.hands;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import elite.intel.ai.hands.events.GameInputSequenceEvent;
import elite.intel.ai.hands.events.GameInputStep;
import elite.intel.ui.support.GameWindowActivator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InputSequenceExecutorFocusSafetyTest {

    private FakeWindowOps fakeOps;
    private final WinDef.HWND gameHwnd = new WinDef.HWND(new Pointer(0x1000));
    private final WinDef.HWND otherHwnd = new WinDef.HWND(new Pointer(0x9999));

    @BeforeEach
    void setUp() {
        fakeOps = new FakeWindowOps();
        fakeOps.isWindows = true;
        fakeOps.foundGameWindow = gameHwnd;
        fakeOps.foregroundWindow = otherHwnd;

        GameWindowActivator.resetForTesting();
        GameWindowActivator.setNativeWindowOpsForTesting(fakeOps);
    }

    @AfterEach
    void tearDown() {
        GameWindowActivator.resetForTesting();
    }

    @Test
    void testG_inputProducingStepIsDroppedWhenGameIsNotInForeground() {
        // Foreground is otherHwnd (browser / chat / etc.)
        fakeOps.foregroundWindow = otherHwnd;

        GameInputStep tapStep = GameInputStep.rawKey(KeyProcessor.KEY_SPACE, 0, 0);
        assertTrue(tapStep.isInputProducing(), "RAW_KEY must be input-producing");

        // Safety check indicates step should be dropped
        assertTrue(InputSequenceExecutor.shouldDropStep(tapStep),
                "(g) Input-producing step must be dropped when game is not foreground");

        // The fallback insurance only drops inputs; it does NOT trigger foreground activation
        assertEquals(0, fakeOps.activateCalls, "(g) Insurance check must not attempt foreground activation");
    }

    @Test
    void testG_delayStepExecutesEvenWhenGameIsNotInForeground() {
        fakeOps.foregroundWindow = otherHwnd;

        AtomicBoolean conditionEvaluated = new AtomicBoolean(false);
        GameInputStep waitStep = GameInputStep.waitUntil("test condition", () -> {
            conditionEvaluated.set(true);
            return true;
        }, 1000);

        assertFalse(waitStep.isInputProducing(), "WAIT_UNTIL must NOT be input-producing");

        assertFalse(InputSequenceExecutor.shouldDropStep(waitStep),
                "Non-input-producing steps must not be dropped even when game is not foreground");
    }

    @Test
    void testG_inputProducingStepExecutesWhenGameIsForeground() {
        fakeOps.foregroundWindow = gameHwnd;

        GameInputStep tapStep = GameInputStep.rawKey(KeyProcessor.KEY_F12, 0, 0);
        assertTrue(tapStep.isInputProducing(), "RAW_KEY must be input-producing");

        assertFalse(InputSequenceExecutor.shouldDropStep(tapStep),
                "(g) Input-producing step must NOT be dropped when game is in foreground");
    }

    private static final class FakeWindowOps implements GameWindowActivator.NativeWindowOps {
        boolean isWindows = true;
        WinDef.HWND foundGameWindow;
        WinDef.HWND foregroundWindow;
        int activateCalls = 0;

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
            activateCalls++;
            return true;
        }

        @Override
        public void sleep(long ms) {
        }
    }
}
