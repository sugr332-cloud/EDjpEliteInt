package elite.intel.gameapi.gamestate.subscribers;

import com.google.common.eventbus.Subscribe;
import elite.intel.ai.mouth.EventNarrator;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.gamestate.dtos.GameEvents;
import elite.intel.gameapi.gamestate.status_events.BeingInterdictedEvent;
import elite.intel.gameapi.gamestate.status_events.InGlideEvent;
import elite.intel.gameapi.gamestate.status_events.PlayerMovedEvent;
import elite.intel.session.PlayerSession;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.session.StatusFlags.GuiFocus;
import elite.intel.session.ui.PanelStateTracker;
import elite.intel.util.Ranks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Supplier;

import static elite.intel.util.StringUtls.localizedEvent;

public class StatusEventSubscriber {

    private static final Logger log = LogManager.getLogger(StatusEventSubscriber.class);

    static Supplier<Instant> clockSupplier = Instant::now;
    static Supplier<Path> statusPathSupplier = () -> {
        Path p = PlayerSession.getInstance().getJournalPath();
        return p != null ? p.resolve("Status.json") : null;
    };

    private boolean lowFuelAnnounced = false;
    private boolean lowOxygenAnnounced = false;
    private boolean lowHealthAnnounced = false;
    private boolean glideAnnounced = false;
    private String lastAnnouncedLegalState = null;

    // Track previous GuiFocus to detect transitions, not just current state
    private GuiFocus previousGuiFocus = GuiFocus.NO_FOCUS;

    // Track previous PlayerSituation to log UNKNOWN transitions only once (null != UNKNOWN on startup)
    private PlayerSituation previousSituation = null;

    void resetPreviousSituationForTesting() {
        previousSituation = null;
    }

    @Subscribe
    public void onStatusChangedEvent(GameEvents.StatusEvent event) {
        Status status = Status.getInstance();

        // --------------------------------------------------------------------------------------
        // Situation transition diagnostic (D-2) - log only on transition into UNKNOWN
        PlayerSituation currentSituation = status.getSituation(event.getFlags(), event.getFlags2(), null);
        if (currentSituation == PlayerSituation.UNKNOWN && previousSituation != PlayerSituation.UNKNOWN) {
            Instant lastRead = clockSupplier.get();
            Path statusPath = statusPathSupplier.get();
            String modifiedStr = "null";
            if (statusPath != null && Files.exists(statusPath)) {
                try {
                    modifiedStr = Files.getLastModifiedTime(statusPath).toInstant().toString();
                } catch (IOException ignored) {
                }
            }
            log.info("Game situation changed to UNKNOWN — flags=0x{} flags2=0x{} path={} modified={} lastRead={}",
                    Long.toHexString(event.getFlags()), Long.toHexString(event.getFlags2()),
                    statusPath, modifiedStr, lastRead);
        }
        previousSituation = currentSituation;

        // --------------------------------------------------------------------------------------
        // GuiFocus transition - must run before setStatus() so we compare against the previous state.
        GuiFocus currentGuiFocus = GuiFocus.fromValue(event.getGuiFocus());
        if (currentGuiFocus != previousGuiFocus) {
            PanelStateTracker.getInstance().onGuiFocusChanged(currentGuiFocus);
            previousGuiFocus = currentGuiFocus;
        }

        // --------------------------------------------------------------------------------------
        // Legal state change alert - suppress Speeding (transient) and deduplicate against
        // the last state we actually announced so rapid oscillation doesn't repeat the alert.
        String legalState = event.getLegalState();
        if (legalState != null
                && !"Speeding".equalsIgnoreCase(legalState)
                && !"Clean".equalsIgnoreCase(legalState)
                && !legalState.equalsIgnoreCase(lastAnnouncedLegalState)) {
            // The template is localized; the state itself is a raw English journal value and needs translating too.
            EventNarrator.critical(localizedEvent("event.status.legalStatus",
                    Ranks.getLocalizedLegalStatus(legalState)));
            lastAnnouncedLegalState = legalState;
        }

        status.setStatus(event);
        GameEventBus.publish(new PlayerMovedEvent(event.getLatitude(), event.getLongitude(), event.getPlanetRadius(), event.getAltitude()));

        // --------------------------------------------------------------------------------------
        //TODO: Can throw custom events. like BeingInterdictedEvent if(status.isBeingInterdicted()){ publish event...}

        GameEventBus.publish(new InGlideEvent(status.isGlideMode()));

        if (status.isBeingInterdicted()) {
            GameEventBus.publish(new BeingInterdictedEvent());
        }

        // --------------------------------------------------------------------------------------
        // Mission-critical alerts
        if (status.isLowFuel() && !lowFuelAnnounced) {
            EventNarrator.critical(localizedEvent("event.status.lowFuel"));
            lowFuelAnnounced = true;
        }

        if (status.isLowOxygen() && !lowOxygenAnnounced) {
            EventNarrator.critical(localizedEvent("event.status.lowOxygen"));
            lowOxygenAnnounced = true;
        }

        if (status.isLowHealth() && !lowHealthAnnounced) {
            EventNarrator.critical(localizedEvent("event.status.lowHealth"));
            lowHealthAnnounced = true;
        }

        if (status.isGlideMode() && !glideAnnounced) {
            EventNarrator.critical(localizedEvent("event.status.glideEngaged"));
            glideAnnounced = true;
        } else if (!status.isGlideMode() && glideAnnounced) {
            glideAnnounced = false;
        }
    }
}