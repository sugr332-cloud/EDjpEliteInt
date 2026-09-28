package elite.intel.gameapi.journal.subscribers;

import com.google.gson.JsonObject;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.journal.events.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DockingStateTrackerTest {

    private DockingStateTracker tracker;
    private AtomicReference<Instant> now;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(Instant.parse("2026-09-28T12:00:00Z"));
        DockingStateTracker.clock = now::get;
        tracker = new DockingStateTracker();
        tracker.resetForTesting();
        DockingStateTracker.clock = now::get;
    }

    @AfterEach
    void tearDown() {
        tracker.resetForTesting();
    }

    @Test
    void initialStatusIsInvalid() {
        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void dockingGrantedEventActivatesTracker() {
        JsonObject json = createEventJson("DockingGranted");
        json.addProperty("StationName", "Jameson Memorial");
        json.addProperty("LandingPad", 3);
        json.addProperty("MarketID", 123456L);
        json.addProperty("StationType", "Coriolis");

        DockingGrantedEvent event = new DockingGrantedEvent(json);
        tracker.onDockingGranted(event);

        assertTrue(tracker.isDockingGranted());
        assertEquals("Jameson Memorial", tracker.getGrantedStationName());
    }

    @Test
    void dockingPermissionExpiresAfterTenMinutes() {
        JsonObject json = createEventJson("DockingGranted");
        json.addProperty("StationName", "Jameson Memorial");
        DockingGrantedEvent event = new DockingGrantedEvent(json);
        tracker.onDockingGranted(event);

        assertTrue(tracker.isDockingGranted());

        // Advance 9 minutes 59 seconds: still valid
        now.set(now.get().plus(Duration.ofSeconds(599)));
        assertTrue(tracker.isDockingGranted());
        assertEquals("Jameson Memorial", tracker.getGrantedStationName());

        // Advance past 10 minutes: expired
        now.set(now.get().plus(Duration.ofSeconds(2)));
        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void dockedEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("Docked");
        json.addProperty("StationName", "Jameson Memorial");
        tracker.onDocked(new DockedEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void dockingDeniedEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("DockingDenied");
        json.addProperty("Reason", "Distance");
        tracker.onDockingDenied(new DockingDeniedEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void dockingTimeoutEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("DockingTimeout");
        tracker.onDockingTimeout(new DockingTimeoutEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void dockingCancelledEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("DockingCancelled");
        tracker.onDockingCancelled(new DockingCancelledEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void supercruiseEntryEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("SupercruiseEntry");
        json.addProperty("Starsystem", "Sol");
        tracker.onSupercruiseEntry(new SupercruiseEntryEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void fsdJumpEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("FSDJump");
        json.addProperty("StarSystem", "Shinrarta Dezhra");
        tracker.onFsdJump(new FSDJumpEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void undockedEventInvalidatesPermission() {
        grantPermission();
        JsonObject json = createEventJson("Undocked");
        json.addProperty("StationName", "Jameson Memorial");
        tracker.onUndocked(new UndockedEvent(json));

        assertFalse(tracker.isDockingGranted());
        assertNull(tracker.getGrantedStationName());
    }

    @Test
    void deliversThroughGameEventBus() {
        JsonObject json = createEventJson("DockingGranted");
        json.addProperty("StationName", "Explorer's Anchorage");
        GameEventBus.publish(new DockingGrantedEvent(json));

        // DockingStateTracker constructor registered itself to GameEventBus
        assertTrue(tracker.isDockingGranted());
        assertEquals("Explorer's Anchorage", tracker.getGrantedStationName());

        JsonObject deniedJson = createEventJson("DockingDenied");
        deniedJson.addProperty("Reason", "Hostile");
        GameEventBus.publish(new DockingDeniedEvent(deniedJson));

        assertFalse(tracker.isDockingGranted());
    }

    private void grantPermission() {
        JsonObject json = createEventJson("DockingGranted");
        json.addProperty("StationName", "Jameson Memorial");
        tracker.onDockingGranted(new DockingGrantedEvent(json));
        assertTrue(tracker.isDockingGranted());
    }

    private JsonObject createEventJson(String eventName) {
        JsonObject json = new JsonObject();
        json.addProperty("event", eventName);
        json.addProperty("timestamp", now.get().toString());
        return json;
    }
}
