package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import elite.intel.ai.hands.Bindings;
import elite.intel.ai.hands.events.GameInputSequenceEvent;
import elite.intel.ai.hands.events.GameInputStep;
import elite.intel.eventbus.GameControllerBus;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.session.StatusFlags;
import elite.intel.session.ui.LeftPanel;
import elite.intel.session.ui.PanelTab;
import elite.intel.session.ui.UINavigator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestDockingCommandTest {

    private final InputCapture capture = new InputCapture();
    private final RequestDockingCommand command = new RequestDockingCommand();

    @BeforeEach
    void setUp() {
        GameControllerBus.register(capture);
    }

    @AfterEach
    void tearDown() {
        GameControllerBus.unregister(capture);
    }

    @Test
    void idIsRequestDocking() {
        assertEquals("request_docking", command.id());
    }

    @Test
    void isVisibleForLLMOnlyInMainShipFighterOrSrv() {
        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE)));
        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_FIGHTER)));
        assertTrue(command.isVisibleForLLM(Status.detached(PlayerSituation.IN_SRV)));
        assertFalse(command.isVisibleForLLM(Status.detached(PlayerSituation.ON_FOOT_STATION)));
    }

    @Test
    void sendDockingRequestInMainShipNavigatesContactsAndSubmitsKeystrokes() {
        RecordingNavigator navigator = new RecordingNavigator();
        Status status = Status.detached(PlayerSituation.IN_SHIP_DEEP_SPACE);

        RequestDockingCommand.sendDockingRequest(navigator, status);

        // Verify navigator calls
        assertEquals(StatusFlags.GuiFocus.EXTERNAL_PANEL, navigator.assumedFocus);
        assertEquals(StatusFlags.GuiFocus.EXTERNAL_PANEL, navigator.openedPanel);
        assertEquals(LeftPanel.CONTACTS, navigator.navigatedTab);
        assertEquals(StatusFlags.GuiFocus.EXTERNAL_PANEL, navigator.closedPanel);

        // Verify key sequence
        assertEquals(1, capture.events.size());
        List<GameInputStep> steps = capture.events.get(0).getSteps();
        assertEquals(5, steps.size());

        // Step 0: UI_DOWN tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(0).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_DOWN.getGameBinding(), steps.get(0).getBindingId());

        // Step 1: UI_UP hold 700ms
        assertEquals(GameInputStep.Type.BINDING_HOLD, steps.get(1).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_UP.getGameBinding(), steps.get(1).getBindingId());
        assertEquals(700, steps.get(1).getDurationMs());

        // Step 2: UI_RIGHT tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(2).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_RIGHT.getGameBinding(), steps.get(2).getBindingId());

        // Step 3: delay 700ms
        assertEquals(GameInputStep.Type.DELAY, steps.get(3).getType());
        assertEquals(700, steps.get(3).getDurationMs());

        // Step 4: UI_SELECT hold 120ms
        assertEquals(GameInputStep.Type.BINDING_HOLD, steps.get(4).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_SELECT.getGameBinding(), steps.get(4).getBindingId());
        assertEquals(120, steps.get(4).getDurationMs());
    }

    @Test
    void sendDockingRequestOutsideMainShipSendsRolePanelSequence() {
        RecordingNavigator navigator = new RecordingNavigator();
        Status status = Status.detached(PlayerSituation.IN_FIGHTER);

        RequestDockingCommand.sendDockingRequest(navigator, status);

        // Navigator should not be touched
        assertNull(navigator.openedPanel);

        // Verify key sequence
        assertEquals(1, capture.events.size());
        List<GameInputStep> steps = capture.events.get(0).getSteps();
        assertEquals(6, steps.size());

        // Step 0: FOCUS_ROLE_PANEL tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(0).getType());
        assertEquals(Bindings.GameCommand.BINDING_FOCUS_ROLE_PANEL.getGameBinding(), steps.get(0).getBindingId());

        // Step 1: UI_LEFT tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(1).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_LEFT.getGameBinding(), steps.get(1).getBindingId());

        // Step 2: UI_DOWN tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(2).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_DOWN.getGameBinding(), steps.get(2).getBindingId());

        // Step 3: UI_RIGHT tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(3).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_RIGHT.getGameBinding(), steps.get(3).getBindingId());

        // Step 4: UI_SELECT hold 120ms
        assertEquals(GameInputStep.Type.BINDING_HOLD, steps.get(4).getType());
        assertEquals(Bindings.GameCommand.BINDING_UI_SELECT.getGameBinding(), steps.get(4).getBindingId());
        assertEquals(120, steps.get(4).getDurationMs());

        // Step 5: FOCUS_ROLE_PANEL tap
        assertEquals(GameInputStep.Type.BINDING_TAP, steps.get(5).getType());
        assertEquals(Bindings.GameCommand.BINDING_FOCUS_ROLE_PANEL.getGameBinding(), steps.get(5).getBindingId());
    }

    @Test
    void executeReturnsNull() {
        String result = command.execute(new JsonObject(), null);
        assertNull(result);
    }

    private static final class RecordingNavigator extends UINavigator {
        StatusFlags.GuiFocus assumedFocus;
        StatusFlags.GuiFocus openedPanel;
        PanelTab navigatedTab;
        StatusFlags.GuiFocus closedPanel;

        @Override
        public void assumeDefaultState(StatusFlags.GuiFocus panel) {
            this.assumedFocus = panel;
        }

        @Override
        public void openAndNavigate(StatusFlags.GuiFocus panel, PanelTab target) {
            this.openedPanel = panel;
            this.navigatedTab = target;
        }

        @Override
        public void closeAndRestore(StatusFlags.GuiFocus panel) {
            this.closedPanel = panel;
        }
    }

    private static final class InputCapture {
        private final List<GameInputSequenceEvent> events = new ArrayList<>();

        @Subscribe
        public void on(GameInputSequenceEvent event) {
            events.add(event);
        }
    }
}
