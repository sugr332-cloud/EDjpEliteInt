package elite.intel.ui.support;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameWindowActivatorTest {

    private FakeWindowOps fakeOps;
    private final WinDef.HWND gameHwnd1 = new WinDef.HWND(new Pointer(0x1000));
    private final WinDef.HWND gameHwnd2 = new WinDef.HWND(new Pointer(0x2000));
    private final WinDef.HWND otherHwnd = new WinDef.HWND(new Pointer(0x9999));

    @BeforeEach
    void setUp() {
        fakeOps = new FakeWindowOps();
        fakeOps.isWindows = true;
        fakeOps.foundGameWindow = gameHwnd1;
        GameWindowActivator.setNativeWindowOpsForTesting(fakeOps);
        GameWindowActivator.resetForTesting();
        GameWindowActivator.setNativeWindowOpsForTesting(fakeOps);
    }

    @AfterEach
    void tearDown() {
        GameWindowActivator.resetForTesting();
    }

    @Test
    void nonWindowsPlatformAlwaysReturnsTrueWithoutCheckingOrActivating() {
        fakeOps.isWindows = false;
        fakeOps.foregroundWindow = otherHwnd;

        assertTrue(GameWindowActivator.isEliteDangerousForeground());
        assertTrue(GameWindowActivator.ensureGameForeground());
        assertEquals(0, fakeOps.activateCalls.size());
    }

    @Test
    void testA_alreadyForegroundDoesNotActivate() {
        GameWindowActivator.findAndCacheGameWindow();
        fakeOps.foregroundWindow = gameHwnd1;

        assertTrue(GameWindowActivator.isEliteDangerousForeground());
        assertTrue(GameWindowActivator.ensureGameForeground());
        assertEquals(0, fakeOps.activateCalls.size(), "(a) already in foreground: must not call activateWindow");
        assertEquals(0, fakeOps.sleepCalls.size(), "(a) already in foreground: must not wait");
    }

    @Test
    void testB_notForegroundActivatesAndSleepsSettleDelay() {
        GameWindowActivator.findAndCacheGameWindow();
        fakeOps.foregroundWindow = otherHwnd;
        fakeOps.onActivate = () -> fakeOps.foregroundWindow = gameHwnd1;

        assertTrue(GameWindowActivator.ensureGameForeground(), "(b) should activate and return true");
        assertEquals(List.of(gameHwnd1), fakeOps.activateCalls, "(b) must activate game window");
        assertTrue(fakeOps.sleepCalls.contains((long) GameWindowActivator.SETTLE_DELAY_MS),
                "(b) must pause settle delay after foregrounding");
    }

    @Test
    void testD_gameNotFoundReturnsFalseWithoutActivating() {
        fakeOps.foundGameWindow = null;
        fakeOps.foregroundWindow = otherHwnd;

        assertFalse(GameWindowActivator.ensureGameForeground(), "(d) game window not found: must return false");
        assertEquals(0, fakeOps.activateCalls.size(), "(d) must not attempt activation when window not found");
    }

    @Test
    void testD_activationFailureReturnsFalseAfterTimeout() {
        GameWindowActivator.findAndCacheGameWindow();
        fakeOps.foregroundWindow = otherHwnd;
        // activate does not change foreground window (e.g. permission denied)
        fakeOps.activateResult = true;

        assertFalse(GameWindowActivator.ensureGameForeground(), "(d) activation timed out: must return false");
        assertEquals(1, fakeOps.activateCalls.size());
    }

    @Test
    void testE_invalidCachedHwndTriggersRediscovery() {
        GameWindowActivator.findAndCacheGameWindow();
        assertEquals(gameHwnd1, GameWindowActivator.getCachedGameWindowForTesting());

        // Game restarts: gameHwnd1 becomes invalid, new window is gameHwnd2
        fakeOps.validWindows.remove(gameHwnd1);
        fakeOps.foundGameWindow = gameHwnd2;
        fakeOps.validWindows.add(gameHwnd2);
        fakeOps.foregroundWindow = otherHwnd;
        fakeOps.onActivate = () -> fakeOps.foregroundWindow = gameHwnd2;

        assertTrue(GameWindowActivator.ensureGameForeground(), "(e) rediscovery after invalidate");
        assertEquals(gameHwnd2, GameWindowActivator.getCachedGameWindowForTesting(),
                "(e) cached HWND must be updated to new window");
        assertEquals(List.of(gameHwnd2), fakeOps.activateCalls);
    }

    @Test
    void foregroundTitleMatchUpdatesCachedHwndWhenMismatched() {
        GameWindowActivator.findAndCacheGameWindow();
        assertEquals(gameHwnd1, GameWindowActivator.getCachedGameWindowForTesting());

        // Foreground window is gameHwnd2 (different HWND), but title matches Elite Dangerous
        fakeOps.foregroundWindow = gameHwnd2;
        fakeOps.titles.put(gameHwnd2, "Elite - Dangerous (CLIENT)");

        assertTrue(GameWindowActivator.isEliteDangerousForeground(),
                "Title match must recognize game even if HWND didn't match initial cache");
        assertEquals(gameHwnd2, GameWindowActivator.getCachedGameWindowForTesting(),
                "Cached HWND must be updated to current foreground window");
    }

    private static final class FakeWindowOps implements GameWindowActivator.NativeWindowOps {
        boolean isWindows = true;
        WinDef.HWND foundGameWindow;
        WinDef.HWND foregroundWindow;
        boolean activateResult = true;
        Runnable onActivate = () -> {};
        final List<WinDef.HWND> activateCalls = new ArrayList<>();
        final List<Long> sleepCalls = new ArrayList<>();
        final List<WinDef.HWND> validWindows = new ArrayList<>();
        final java.util.Map<WinDef.HWND, String> titles = new java.util.HashMap<>();

        FakeWindowOps() {
            validWindows.add(new WinDef.HWND(new Pointer(0x1000)));
            validWindows.add(new WinDef.HWND(new Pointer(0x2000)));
        }

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
            return hwnd != null && validWindows.contains(hwnd);
        }

        @Override
        public WinDef.HWND getForegroundWindow() {
            return foregroundWindow;
        }

        @Override
        public String getWindowTitle(WinDef.HWND hwnd) {
            return titles.getOrDefault(hwnd, "");
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
            sleepCalls.add(ms);
        }
    }
}
