package elite.intel.gameapi.journal.subscribers;

import com.google.common.eventbus.Subscribe;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.journal.events.*;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Tracks whether docking permission is currently granted and valid.
 * Becomes valid on DockingGranted, and invalidates on Docked, DockingDenied,
 * DockingTimeout, DockingCancelled, SupercruiseEntry, FSDJump, Undocked,
 * or after 10 minutes.
 */
public class DockingStateTracker {

    private static final Duration EXPIRATION = Duration.ofMinutes(10);
    private static final DockingStateTracker INSTANCE = new DockingStateTracker();

    public static Supplier<Instant> clock = Instant::now;

    private volatile boolean granted = false;
    private volatile String stationName;
    private volatile Instant grantedTime;

    public static DockingStateTracker getInstance() {
        return INSTANCE;
    }

    public DockingStateTracker() {
        GameEventBus.register(this);
    }

    @Subscribe
    public synchronized void onDockingGranted(DockingGrantedEvent event) {
        if (event == null) return;
        this.granted = true;
        this.stationName = event.getStationName();
        this.grantedTime = clock.get();
    }

    @Subscribe
    public synchronized void onDocked(DockedEvent event) {
        invalidate();
    }

    @Subscribe
    public synchronized void onDockingDenied(DockingDeniedEvent event) {
        invalidate();
    }

    @Subscribe
    public synchronized void onDockingTimeout(DockingTimeoutEvent event) {
        invalidate();
    }

    @Subscribe
    public synchronized void onDockingCancelled(DockingCancelledEvent event) {
        invalidate();
    }

    @Subscribe
    public synchronized void onSupercruiseEntry(SupercruiseEntryEvent event) {
        invalidate();
    }

    @Subscribe
    public synchronized void onFsdJump(FSDJumpEvent event) {
        invalidate();
    }

    @Subscribe
    public synchronized void onUndocked(UndockedEvent event) {
        invalidate();
    }

    public synchronized boolean isDockingGranted() {
        if (!granted) {
            return false;
        }
        if (grantedTime == null || Duration.between(grantedTime, clock.get()).compareTo(EXPIRATION) > 0) {
            invalidate();
            return false;
        }
        return true;
    }

    public synchronized String getGrantedStationName() {
        return isDockingGranted() ? stationName : null;
    }

    public synchronized void invalidate() {
        this.granted = false;
        this.stationName = null;
        this.grantedTime = null;
    }

    public synchronized void resetForTesting() {
        invalidate();
        clock = Instant::now;
    }
}
