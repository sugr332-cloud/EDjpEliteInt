package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import elite.intel.ai.hands.Bindings;
import elite.intel.ai.hands.events.GameInputSequenceEvent;
import elite.intel.eventbus.GameControllerBus;
import elite.intel.gameapi.data.FsdTarget;
import elite.intel.gameapi.gamestate.dtos.GameEvents;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard: a plotted jump must reach the game, even when the commander's current system is not
 * a known location row (an unvisited/unscanned system leaves the looked-up location without a star name).
 */
class JumpToHyperspaceCommandTest {

    private static final long IN_MAIN_SHIP = 16_777_216L;
    private static final long SUPERCRUISE = 16L;
    private static final long FSD_MASS_LOCKED = 65536L;
    private static final long FSD_COOLDOWN = 262144L;

    private final long savedFlags = Status.getInstance().getStatus().getFlags();
    private final long savedFlags2 = Status.getInstance().getStatus().getFlags2();
    private final JumpToHyperspaceCommand command = new JumpToHyperspaceCommand();
    private final InputCapture inputCapture = new InputCapture();
    private FsdTarget savedFsdTarget;

    @BeforeEach
    void registerInputCapture() {
        savedFsdTarget = PlayerSession.getInstance().getFsdTarget();
        GameControllerBus.register(inputCapture);
    }

    @AfterEach
    void cleanUp() {
        GameControllerBus.unregister(inputCapture);
        if (savedFsdTarget != null) {
            PlayerSession.getInstance().setFsdTarget(savedFsdTarget);
        }
        setStatus(savedFlags, savedFlags2);
    }

    @Test
    void jumpsWhenCurrentLocationIsUnknown() {
        setStatus(IN_MAIN_SHIP, 0L);
        PlayerSession.getInstance().setFsdTarget(
                new FsdTarget("Los", "K", null, null, null, null, "Fuel star"));

        String outcome = command.execute(new JsonObject(), null);

        assertNull(outcome, "an executed jump reports no blocking outcome");
        assertFalse(inputCapture.events.isEmpty(), "the jump must reach the game as input");
    }

    private JumpToHyperspaceCommand createTestCommand(
            BooleanSupplier hasHyperspace,
            BooleanSupplier hasCombination,
            BooleanSupplier hasRoute,
            List<GameInputSequenceEvent> capturedInputs) {
        return new JumpToHyperspaceCommand(
                PlayerSession.getInstance(),
                Status.getInstance(),
                hasHyperspace,
                hasCombination,
                hasRoute,
                capturedInputs::add,
                () -> {},
                msg -> {}
        );
    }

    // TEST GATE 1: Hyperspace あり → 従来どおり Hyperspace を押す（キーの順番も従来どおり）
    @Test
    void hyperspaceBoundPressesHyperspace() {
        setStatus(IN_MAIN_SHIP, 0L);
        List<GameInputSequenceEvent> capturedInputs = new ArrayList<>();
        JumpToHyperspaceCommand cmd = createTestCommand(() -> true, () -> false, () -> false, capturedInputs);

        String result = cmd.execute(new JsonObject(), null);

        assertNull(result, "successful jump returns null");
        assertEquals(3, capturedInputs.size());
        assertEquals(Bindings.GameCommand.BINDING_TARGET_NEXT_ROUTE_SYSTEM.getGameBinding(),
                capturedInputs.get(0).getSteps().get(0).getBindingId());
        assertEquals(150, capturedInputs.get(1).getSteps().get(0).getDurationMs());
        assertEquals(Bindings.GameCommand.BINDING_JUMP_TO_HYPERSPACE.getGameBinding(),
                capturedInputs.get(2).getSteps().get(0).getBindingId());
    }

    // TEST GATE 2: Hyperspace なし＋HyperSuperCombination あり＋通常空間＋航路あり → HyperSuperCombination を押す
    @Test
    void hyperspaceUnboundCombinationBoundInNormalSpaceWithRoutePressesCombination() {
        setStatus(IN_MAIN_SHIP, 0L);
        List<GameInputSequenceEvent> capturedInputs = new ArrayList<>();
        JumpToHyperspaceCommand cmd = createTestCommand(() -> false, () -> true, () -> true, capturedInputs);

        String result = cmd.execute(new JsonObject(), null);

        assertNull(result, "successful jump returns null");
        assertEquals(3, capturedInputs.size());
        assertEquals(Bindings.GameCommand.BINDING_TARGET_NEXT_ROUTE_SYSTEM.getGameBinding(),
                capturedInputs.get(0).getSteps().get(0).getBindingId());
        assertEquals(150, capturedInputs.get(1).getSteps().get(0).getDurationMs());
        assertEquals(Bindings.GameCommand.BINDING_HYPER_SUPER_COMBINATION.getGameBinding(),
                capturedInputs.get(2).getSteps().get(0).getBindingId());
    }

    // TEST GATE 3: Hyperspace なし＋HyperSuperCombination あり＋航路なし → 押さず noRouteForJump
    @Test
    void hyperspaceUnboundCombinationBoundWithoutRouteReturnsNoRouteForJump() {
        setStatus(IN_MAIN_SHIP, 0L);
        List<GameInputSequenceEvent> capturedInputs = new ArrayList<>();
        JumpToHyperspaceCommand cmd = createTestCommand(() -> false, () -> true, () -> false, capturedInputs);

        String result = cmd.execute(new JsonObject(), null);

        assertEquals(StringUtls.localizedResponse("handler.fsd.noRouteForJump"), result);
        assertTrue(capturedInputs.isEmpty(), "no keys must be sent when route is missing");
    }

    // TEST GATE 4: Hyperspace なし＋HyperSuperCombination あり＋スーパークルーズ中 → 押さず noRouteForJump
    @Test
    void hyperspaceUnboundCombinationBoundInSupercruiseReturnsNoRouteForJump() {
        setStatus(IN_MAIN_SHIP | SUPERCRUISE, 0L);
        List<GameInputSequenceEvent> capturedInputs = new ArrayList<>();
        JumpToHyperspaceCommand cmd = createTestCommand(() -> false, () -> true, () -> true, capturedInputs);

        String result = cmd.execute(new JsonObject(), null);

        assertEquals(StringUtls.localizedResponse("handler.fsd.noRouteForJump"), result);
        assertTrue(capturedInputs.isEmpty(), "no keys must be sent while in supercruise");
    }

    // TEST GATE 5: どちらも無し → 押さず noJumpBinding
    @Test
    void neitherBindingAvailableReturnsNoJumpBinding() {
        setStatus(IN_MAIN_SHIP, 0L);
        List<GameInputSequenceEvent> capturedInputs = new ArrayList<>();
        JumpToHyperspaceCommand cmd = createTestCommand(() -> false, () -> false, () -> true, capturedInputs);

        String result = cmd.execute(new JsonObject(), null);

        assertEquals(StringUtls.localizedResponse("handler.fsd.noJumpBinding"), result);
        assertTrue(capturedInputs.isEmpty(), "no keys must be sent when neither binding is available");
    }

    // TEST GATE 6: マスロック中・クールダウン中の既存の答えが変わらない
    @Test
    void massLockedAndCooldownAnswersUnchanged() {
        List<GameInputSequenceEvent> capturedInputs = new ArrayList<>();
        JumpToHyperspaceCommand cmd = createTestCommand(() -> true, () -> false, () -> true, capturedInputs);

        // Mass locked
        setStatus(IN_MAIN_SHIP | FSD_MASS_LOCKED, 0L);
        String massLockedResult = cmd.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.supercruise.massLocked"), massLockedResult);

        // Cooldown
        setStatus(IN_MAIN_SHIP | FSD_COOLDOWN, 0L);
        String cooldownResult = cmd.execute(new JsonObject(), null);
        assertEquals(StringUtls.localizedResponse("handler.supercruise.cooldown"), cooldownResult);
    }

    private static void setStatus(long flags, long flags2) {
        GameEvents.StatusEvent snapshot = Status.getInstance().getStatus();
        snapshot.setFlags(flags);
        snapshot.setFlags2(flags2);
        Status.getInstance().setStatus(snapshot);
    }

    private static final class InputCapture {
        private final List<GameInputSequenceEvent> events = new ArrayList<>();

        @Subscribe
        public void on(GameInputSequenceEvent event) {
            events.add(event);
        }
    }
}
