package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import elite.intel.ai.hands.Bindings;
import elite.intel.ai.hands.events.GameInputSequenceEvent;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.eventbus.GameControllerBus;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.gamestate.dtos.GameEvents;
import elite.intel.gameapi.journal.events.*;
import elite.intel.gameapi.journal.events.dto.shiploadout.ModuleDto;
import elite.intel.gameapi.journal.events.dto.shiploadout.ShipLoadOutDto;
import elite.intel.gameapi.journal.subscribers.DockingStateTracker;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.session.StatusFlags;
import elite.intel.session.ui.UINavigator;
import elite.intel.util.StringUtls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AutoDockCommandTest {

    private static final long IN_MAIN_SHIP = 16_777_216L;
    private static final long SUPERCRUISE = 16L;

    private final List<Object> gameInputs = new ArrayList<>();
    private final List<AiVoxResponseEvent> voxResponses = new ArrayList<>();
    private final AtomicReference<Runnable> backgroundTask = new AtomicReference<>();
    private DockingStateTracker dockingStateTracker;
    private final StatusCapture statusCapture = new StatusCapture();

    private long savedLiveFlags;
    private long savedLiveFlags2;

    @BeforeEach
    void setUp() {
        gameInputs.clear();
        voxResponses.clear();
        backgroundTask.set(null);
        dockingStateTracker = new DockingStateTracker();
        dockingStateTracker.resetForTesting();
        GameControllerBus.register(statusCapture);

        savedLiveFlags = Status.getInstance().getStatus().getFlags();
        savedLiveFlags2 = Status.getInstance().getStatus().getFlags2();
    }

    @AfterEach
    void tearDown() {
        GameControllerBus.unregister(statusCapture);
        dockingStateTracker.resetForTesting();
        setLiveStatus(savedLiveFlags, savedLiveFlags2);
    }

    private AutoDockCommand createCommand(
            Status status,
            UINavigator navigator,
            ShipLoadOutDto loadout,
            int timeoutSeconds) {
        return new AutoDockCommand(
                status,
                navigator != null ? navigator : new UINavigator(),
                dockingStateTracker,
                () -> loadout,
                gameInputs::add,
                event -> voxResponses.add((AiVoxResponseEvent) event),
                task -> backgroundTask.set(task),
                timeoutSeconds
        );
    }

    // 1. Basic command metadata
    @Test
    void metadataAndVisibility() {
        AutoDockCommand command = createCommand(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE), null, withDockingComputer(true), 20);
        assertEquals("auto_dock", command.id());
        assertTrue(command.sendsGameInput());

        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE)));
        assertFalse(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_SUPERCRUISE)));
        assertFalse(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_DOCKED)));
        assertFalse(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_LANDED)));
        assertFalse(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_FIGHTER)));
        assertFalse(command.isVisibleForLLM(Status.detached(PlayerSituation.ON_FOOT_STATION)));
    }

    // 2. Normal flow: docking granted -> zero throttle + vox message
    @Test
    void executeRequestsDockingAndSetsThrottleZeroWhenGranted() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, withDockingComputer(true), 20);

        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.requested"), result);
        assertNotNull(backgroundTask.get(), "Background task must be submitted");

        // Fire DockingGrantedEvent
        JsonObject json = createEventJson("DockingGranted");
        json.addProperty("StationName", "Jameson Memorial");
        GameEventBus.publish(new DockingGrantedEvent(json));

        // Run background task
        backgroundTask.get().run();

        // Throttle zero sent
        assertEquals(1, gameInputs.size());
        assertTrue(gameInputs.get(0) instanceof GameInputSequenceEvent);
        GameInputSequenceEvent seq = (GameInputSequenceEvent) gameInputs.get(0);
        assertEquals(Bindings.GameCommand.BINDING_SET_SPEED_ZERO.getGameBinding(), seq.getSteps().get(0).getBindingId());

        // Vox notification sent
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse("handler.autoDock.throttleZero"), voxResponses.get(0).getText());
    }

    // 3. Docking denied with various reasons -> no throttle zero, appropriate vox notification
    @ParameterizedTest(name = "Denial reason \"{0}\" produces \"{1}\"")
    @CsvSource({
            "Distance, handler.autoDock.denied.distance",
            "NoSpace, handler.autoDock.denied.noSpace",
            "Hostile, handler.autoDock.denied.hostile",
            "Offences, handler.autoDock.denied.offences",
            "TooLarge, handler.autoDock.denied.tooLarge",
            "ActiveFighter, handler.autoDock.denied.activeFighter",
            "UnknownReason, handler.autoDock.denied.other",
            ", handler.autoDock.denied.other"
    })
    void executeHandlesDockingDenied(String reason, String expectedMessageKey) {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, withDockingComputer(true), 20);

        command.execute(new JsonObject(), null);
        assertNotNull(backgroundTask.get());

        JsonObject json = createEventJson("DockingDenied");
        if (reason != null) {
            json.addProperty("Reason", reason);
        }
        GameEventBus.publish(new DockingDeniedEvent(json));

        backgroundTask.get().run();

        // No throttle zero
        assertTrue(gameInputs.isEmpty());
        // Reason-specific notification
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse(expectedMessageKey), voxResponses.get(0).getText());
    }

    // 4. Timeout -> no throttle zero, timeout notification
    @Test
    void executeHandlesTimeoutWithoutEvent() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, withDockingComputer(true), 0);

        command.execute(new JsonObject(), null);
        assertNotNull(backgroundTask.get());

        // Run background task without publishing event -> times out immediately (timeoutSeconds = 0)
        backgroundTask.get().run();

        assertTrue(gameInputs.isEmpty());
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse("handler.autoDock.timeout"), voxResponses.get(0).getText());
    }

    @Test
    void executeHandlesDockingTimeoutEvent() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, withDockingComputer(true), 20);

        command.execute(new JsonObject(), null);
        assertNotNull(backgroundTask.get());

        JsonObject json = createEventJson("DockingTimeout");
        GameEventBus.publish(new DockingTimeoutEvent(json));

        backgroundTask.get().run();

        assertTrue(gameInputs.isEmpty());
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse("handler.autoDock.timeout"), voxResponses.get(0).getText());
    }

    // 5. Cancelled -> no throttle zero, cancelled notification
    @Test
    void executeHandlesDockingCancelledEvent() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, withDockingComputer(true), 20);

        command.execute(new JsonObject(), null);
        assertNotNull(backgroundTask.get());

        JsonObject json = createEventJson("DockingCancelled");
        GameEventBus.publish(new DockingCancelledEvent(json));

        backgroundTask.get().run();

        assertTrue(gameInputs.isEmpty());
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse("handler.autoDock.cancelled"), voxResponses.get(0).getText());
    }

    // 6. Already granted -> no request keys, sets throttle zero immediately, returns alreadyGranted
    @Test
    void executeWhenAlreadyGrantedSendsThrottleZeroImmediately() {
        // Pre-grant docking
        JsonObject json = createEventJson("DockingGranted");
        json.addProperty("StationName", "Jameson Memorial");
        dockingStateTracker.onDockingGranted(new DockingGrantedEvent(json));

        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, withDockingComputer(true), 20);

        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.alreadyGranted"), result);

        // Immediate throttle zero
        assertEquals(1, gameInputs.size());
        GameInputSequenceEvent seq = (GameInputSequenceEvent) gameInputs.get(0);
        assertEquals(Bindings.GameCommand.BINDING_SET_SPEED_ZERO.getGameBinding(), seq.getSteps().get(0).getBindingId());

        // No background task
        assertNull(backgroundTask.get());
        assertTrue(voxResponses.isEmpty());
        // No request docking keystrokes published to GameControllerBus
        assertTrue(statusCapture.events.isEmpty());
    }

    // 7. No docking computer equipped -> request sent, returns noDockingComputer, no wait / no throttle zero
    @Test
    void executeWithoutDockingComputerRequestsOnly() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        // Loadout with only shield generator (no docking computer)
        ShipLoadOutDto loadout = new ShipLoadOutDto();
        ModuleDto mod = new ModuleDto();
        mod.setItem("Int_ShieldGenerator_Size3_Class1");
        mod.setOn(true);
        loadout.setModules(List.of(mod));

        AutoDockCommand command = createCommand(status, null, loadout, 20);

        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.noDockingComputer"), result);

        // Docking request sequence was sent to GameControllerBus
        assertFalse(statusCapture.events.isEmpty());
        // No throttle zero sent
        assertTrue(gameInputs.isEmpty());
        // No background waiter
        assertNull(backgroundTask.get());
    }

    @Test
    void executeWithDockingComputerTurnedOffRequestsOnly() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        // Docking computer present but powered OFF (on = false)
        ShipLoadOutDto loadout = withDockingComputer(false);

        AutoDockCommand command = createCommand(status, null, loadout, 20);

        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.noDockingComputer"), result);

        assertFalse(statusCapture.events.isEmpty());
        assertTrue(gameInputs.isEmpty());
        assertNull(backgroundTask.get());
    }

    // 8. Loadout unknown (null) -> assumes normal flow (request, wait, throttle zero on grant)
    @Test
    void executeWithUnknownLoadoutProceedsWithNormalFlow() {
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, null, null, 20);

        String result = command.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.requested"), result);
        assertNotNull(backgroundTask.get());

        JsonObject json = createEventJson("DockingGranted");
        GameEventBus.publish(new DockingGrantedEvent(json));

        backgroundTask.get().run();

        assertEquals(1, gameInputs.size());
        assertEquals(1, voxResponses.size());
    }

    // 9. Invalid situations -> refused immediately, no keys sent
    @Test
    void executeRefusesInInvalidSituations() {
        // Not in main ship
        AutoDockCommand cmdNotInShip = createCommand(Status.detached(PlayerSituation.IN_FIGHTER), null, withDockingComputer(true), 20);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.notInMainShip"), cmdNotInShip.execute(new JsonObject(), null));
        assertTrue(gameInputs.isEmpty());
        assertNull(backgroundTask.get());

        // Already docked
        AutoDockCommand cmdDocked = createCommand(Status.detached(PlayerSituation.IN_SHIP_DOCKED), null, withDockingComputer(true), 20);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.alreadyDocked"), cmdDocked.execute(new JsonObject(), null));
        assertTrue(gameInputs.isEmpty());
        assertNull(backgroundTask.get());

        // Landed on surface
        AutoDockCommand cmdLanded = createCommand(Status.detached(PlayerSituation.IN_SHIP_LANDED), null, withDockingComputer(true), 20);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.alreadyDocked"), cmdLanded.execute(new JsonObject(), null));
        assertTrue(gameInputs.isEmpty());
        assertNull(backgroundTask.get());

        // In supercruise
        AutoDockCommand cmdSupercruise = createCommand(Status.detached(PlayerSituation.IN_SHIP_SUPERCRUISE), null, withDockingComputer(true), 20);
        assertEquals(StringUtls.localizedResponse("handler.autoDock.inSupercruise"), cmdSupercruise.execute(new JsonObject(), null));
        assertTrue(gameInputs.isEmpty());
        assertNull(backgroundTask.get());
    }

    // 10. Situation changed during wait -> cancelled notification, no throttle zero
    @Test
    void executeAbortsThrottleZeroIfSituationChangesWhileWaiting() {
        // Set initial live status: in main ship, not in supercruise
        setLiveStatus(IN_MAIN_SHIP, 0L);

        AutoDockCommand command = createCommand(Status.getInstance(), null, withDockingComputer(true), 20);
        command.execute(new JsonObject(), null);
        assertNotNull(backgroundTask.get());

        // Docking granted arrives, but ship jumped to supercruise in the meantime
        setLiveStatus(IN_MAIN_SHIP | SUPERCRUISE, 0L);
        JsonObject json = createEventJson("DockingGranted");
        GameEventBus.publish(new DockingGrantedEvent(json));

        backgroundTask.get().run();

        // Throttle zero must NOT be sent
        assertTrue(gameInputs.isEmpty());
        // Cancelled notification
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse("handler.autoDock.cancelled"), voxResponses.get(0).getText());
    }

    // 11. Early waiter registration: event published during sendDockingRequest is captured
    @Test
    void waiterIsRegisteredBeforeSendingDockingRequest() {
        AtomicBoolean eventDeliveredDuringRequest = new AtomicBoolean(false);

        // Navigator that fires DockingGrantedEvent in the middle of request execution
        UINavigator interceptingNavigator = new UINavigator() {
            @Override
            public void assumeDefaultState(StatusFlags.GuiFocus panel) {
                // Fire event right when request sequence starts
                JsonObject json = createEventJson("DockingGranted");
                json.addProperty("StationName", "Jameson Memorial");
                GameEventBus.publish(new DockingGrantedEvent(json));
                eventDeliveredDuringRequest.set(true);
            }
        };

        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);
        AutoDockCommand command = createCommand(status, interceptingNavigator, withDockingComputer(true), 20);

        command.execute(new JsonObject(), null);
        assertTrue(eventDeliveredDuringRequest.get());
        assertNotNull(backgroundTask.get());

        // Background task should see the event already completed without timeout
        backgroundTask.get().run();

        assertEquals(1, gameInputs.size());
        assertEquals(1, voxResponses.size());
        assertEquals(StringUtls.localizedResponse("handler.autoDock.throttleZero"), voxResponses.get(0).getText());
    }

    private static void setLiveStatus(long flags, long flags2) {
        GameEvents.StatusEvent snapshot = Status.getInstance().getStatus();
        snapshot.setFlags(flags);
        snapshot.setFlags2(flags2);
        Status.getInstance().setStatus(snapshot);
    }

    private ShipLoadOutDto withDockingComputer(boolean on) {
        ShipLoadOutDto loadout = new ShipLoadOutDto();
        ModuleDto mod = new ModuleDto();
        mod.setItem("Int_DockingComputer_Advanced");
        mod.setOn(on);
        loadout.setModules(List.of(mod));
        return loadout;
    }

    private JsonObject createEventJson(String eventName) {
        JsonObject json = new JsonObject();
        json.addProperty("event", eventName);
        json.addProperty("timestamp", "2026-09-28T12:00:00Z");
        return json;
    }

    private static final class StatusCapture {
        private final List<GameInputSequenceEvent> events = new ArrayList<>();

        @Subscribe
        public void on(GameInputSequenceEvent event) {
            events.add(event);
        }
    }
}
