package elite.intel.gameapi.journal.subscribers;

import com.google.common.eventbus.Subscribe;
import elite.intel.db.managers.PistonModeManager;
import elite.intel.gameapi.journal.events.DockedEvent;
import elite.intel.gameapi.journal.events.UndockedEvent;

/**
 * Dispatches Docked and Undocked events to {@link PistonModeManager}.
 * Registered automatically by {@link elite.intel.gameapi.SubscriberRegistration}.
 */
public class PistonModeSubscriber {

    public PistonModeSubscriber() {
    }

    @Subscribe
    public void onDocked(DockedEvent event) {
        PistonModeManager.getInstance().onDocked(event);
    }

    @Subscribe
    public void onUndocked(UndockedEvent event) {
        PistonModeManager.getInstance().onUndocked(event);
    }
}
