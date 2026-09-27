package elite.intel.ui.support;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Optional;

/**
 * Manages detection, caching, and foreground activation for the Elite Dangerous game window.
 * Protects against input dispatch to other applications by ensuring the game is in the foreground
 * before commands execute.
 */
public final class GameWindowActivator {

    private static final Logger log = LogManager.getLogger(GameWindowActivator.class);

    public static final int FOREGROUND_CONFIRM_TIMEOUT_MS = 2000;
    public static final int SETTLE_DELAY_MS = 300;

    private static final String[] ELITE_WINDOW_TITLE_MARKERS = {
            "elite - dangerous",
            "elite dangerous"
    };

    private static volatile WinDef.HWND cachedGameWindow = null;
    private static NativeWindowOps ops = new DefaultNativeWindowOps();

    private GameWindowActivator() {
    }

    /**
     * Finds and caches the Elite Dangerous window identifier if the game is currently running.
     * Called once at application startup.
     */
    public static void findAndCacheGameWindow() {
        if (!ops.isWindows()) {
            return;
        }
        cachedGameWindow = ops.findGameWindow();
        if (cachedGameWindow != null) {
            log.info("Cached Elite Dangerous window HWND: {}", cachedGameWindow);
        } else {
            log.debug("Elite Dangerous window not found during startup check");
        }
    }

    /**
     * Checks if the foreground window is Elite Dangerous.
     * <p>
     * Returns true if:
     * <ol>
     *   <li>Platform is not Windows (Linux etc. - no focus enforcement)</li>
     *   <li>Foreground window matches the cached HWND</li>
     *   <li>Foreground window title matches Elite Dangerous title rules (in which case cached HWND is updated)</li>
     * </ol>
     */
    public static boolean isEliteDangerousForeground() {
        if (!ops.isWindows()) {
            return true;
        }
        WinDef.HWND fg = ops.getForegroundWindow();
        if (fg == null) {
            return false;
        }
        WinDef.HWND cached = getOrRefreshGameWindow();
        if (cached != null && fg.equals(cached)) {
            return true;
        }
        String title = ops.getWindowTitle(fg);
        if (isEliteDangerousTitle(title)) {
            log.info("Foreground window title matched Elite Dangerous; updating cached HWND to {}", fg);
            cachedGameWindow = fg;
            return true;
        }
        return false;
    }

    /**
     * Ensures Elite Dangerous is in the foreground before sending command inputs.
     * <p>
     * If already in foreground, returns {@code true} immediately without delay.
     * Otherwise requests foreground activation, polls up to {@value #FOREGROUND_CONFIRM_TIMEOUT_MS} ms
     * for confirmation, and pauses {@value #SETTLE_DELAY_MS} ms before returning.
     *
     * @return {@code true} if game is verified in foreground, {@code false} if not found or activation failed
     */
    public static boolean ensureGameForeground() {
        if (!ops.isWindows()) {
            return true;
        }
        if (isEliteDangerousForeground()) {
            return true;
        }
        boolean requested = activateEliteDangerousWindow();
        if (!requested) {
            return false;
        }
        long deadline = ops.currentTimeMillis() + FOREGROUND_CONFIRM_TIMEOUT_MS;
        while (ops.currentTimeMillis() < deadline) {
            if (isEliteDangerousForeground()) {
                ops.sleep(SETTLE_DELAY_MS);
                return true;
            }
            ops.sleep(50);
        }
        log.warn("Timed out after {} ms waiting for Elite Dangerous window to become foreground", FOREGROUND_CONFIRM_TIMEOUT_MS);
        return false;
    }

    /**
     * Attempts to restore and foreground the game window.
     * Used directly by {@link GuiCommandRunner} to preserve existing GUI dispatch behavior.
     *
     * @return {@code true} when an Elite Dangerous window was found and a foreground request was accepted
     */
    public static boolean activateEliteDangerousWindow() {
        if (!ops.isWindows()) {
            return false;
        }
        WinDef.HWND hwnd = getOrRefreshGameWindow();
        if (hwnd == null) {
            log.debug("Elite Dangerous window not found for game window activation");
            return false;
        }
        boolean foregroundSet = ops.activateWindow(hwnd);
        log.debug("Elite Dangerous foreground request accepted={}", foregroundSet);
        return foregroundSet;
    }

    /**
     * Returns the cached window HWND, refreshing it if the cached window has become invalid.
     */
    private static WinDef.HWND getOrRefreshGameWindow() {
        if (ops.isWindowValid(cachedGameWindow)) {
            return cachedGameWindow;
        }
        findAndCacheGameWindow();
        return cachedGameWindow;
    }

    public static boolean isEliteDangerousTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String normalized = title.toLowerCase(Locale.ROOT);
        for (String marker : ELITE_WINDOW_TITLE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Test seams
    // -------------------------------------------------------------------------

    public interface NativeWindowOps {
        boolean isWindows();
        WinDef.HWND findGameWindow();
        boolean isWindowValid(WinDef.HWND hwnd);
        WinDef.HWND getForegroundWindow();
        String getWindowTitle(WinDef.HWND hwnd);
        boolean activateWindow(WinDef.HWND hwnd);
        void sleep(long ms);
        default long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    public static void setNativeWindowOpsForTesting(NativeWindowOps testOps) {
        ops = testOps != null ? testOps : new DefaultNativeWindowOps();
    }

    public static void resetForTesting() {
        cachedGameWindow = null;
        ops = new DefaultNativeWindowOps();
    }

    public static WinDef.HWND getCachedGameWindowForTesting() {
        return cachedGameWindow;
    }

    public static void setCachedGameWindowForTesting(WinDef.HWND hwnd) {
        cachedGameWindow = hwnd;
    }

    // -------------------------------------------------------------------------
    // Default Win32/JNA implementation
    // -------------------------------------------------------------------------

    private static final class DefaultNativeWindowOps implements NativeWindowOps {
        @Override
        public boolean isWindows() {
            return Platform.isWindows();
        }

        @Override
        public WinDef.HWND findGameWindow() {
            WindowSearch search = new WindowSearch();
            User32.INSTANCE.EnumWindows(search, null);
            return search.match;
        }

        @Override
        public boolean isWindowValid(WinDef.HWND hwnd) {
            return hwnd != null
                    && User32.INSTANCE.IsWindow(hwnd)
                    && User32.INSTANCE.IsWindowVisible(hwnd);
        }

        @Override
        public WinDef.HWND getForegroundWindow() {
            return User32.INSTANCE.GetForegroundWindow();
        }

        @Override
        public String getWindowTitle(WinDef.HWND hwnd) {
            return windowTitle(hwnd);
        }

        @Override
        public boolean activateWindow(WinDef.HWND hwnd) {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
            User32.INSTANCE.BringWindowToTop(hwnd);
            return User32.INSTANCE.SetForegroundWindow(hwnd);
        }

        @Override
        public void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String windowTitle(WinDef.HWND hwnd) {
        if (hwnd == null) return "";
        char[] buffer = new char[512];
        int length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        return length <= 0 ? "" : new String(buffer, 0, length);
    }

    private static final class WindowSearch implements WinUser.WNDENUMPROC {
        private WinDef.HWND match;

        @Override
        public boolean callback(WinDef.HWND hwnd, Pointer data) {
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                return true;
            }
            String title = windowTitle(hwnd);
            if (isEliteDangerousTitle(title)) {
                match = hwnd;
                return false;
            }
            return true;
        }
    }
}
