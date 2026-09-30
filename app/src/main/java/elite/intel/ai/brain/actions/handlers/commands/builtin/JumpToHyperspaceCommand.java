package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.ai.brain.vega.VegaRuntime;
import elite.intel.ai.hands.Bindings;
import elite.intel.ai.hands.BindingsMonitor;
import elite.intel.ai.hands.KeyBindingsParser;
import elite.intel.ai.hands.events.GameInputSequenceEvent;
import elite.intel.ai.hands.events.GameInputStep;
import elite.intel.db.managers.ShipRouteManager;
import elite.intel.eventbus.GameControllerBus;
import elite.intel.gameapi.FuelScoop;
import elite.intel.gameapi.data.FsdTarget;
import elite.intel.gameapi.inputs.PreFtlChecks;
import elite.intel.gameapi.inputs.UiNavCommon;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static elite.intel.ai.hands.Bindings.GameCommand.BINDING_HYPER_SUPER_COMBINATION;
import static elite.intel.ai.hands.Bindings.GameCommand.BINDING_JUMP_TO_HYPERSPACE;
import static elite.intel.ai.hands.Bindings.GameCommand.BINDING_TARGET_NEXT_ROUTE_SYSTEM;

/**
 * Self-describing "jump to hyperspace" command.
 * Supports primary "Hyperspace" binding with fallback to "HyperSuperCombination"
 * when in normal space with a plotted route ahead.
 */
@RegisterCommand
public final class JumpToHyperspaceCommand implements IntelCommand {
    public static final String ID = "jump_to_hyperspace";

    @Override
    public String llmDescription() {
        return "Engage the frame shift drive to jump to the next system on the plotted route (hyperspace jump).";
    }

    private final PlayerSession playerSession;
    private final Status status;
    private final BooleanSupplier hasHyperspaceBinding;
    private final BooleanSupplier hasHyperSuperCombinationBinding;
    private final BooleanSupplier hasRoute;
    private final Consumer<GameInputSequenceEvent> gameInputPublisher;
    private final Runnable uiCloseAction;
    private final Consumer<String> fillerConsumer;

    public JumpToHyperspaceCommand() {
        this(
                PlayerSession.getInstance(),
                Status.getInstance(),
                () -> isBindingBound(BINDING_JUMP_TO_HYPERSPACE.getGameBinding()),
                () -> isBindingBound(BINDING_HYPER_SUPER_COMBINATION.getGameBinding()),
                () -> hasPlottedRoute(),
                GameControllerBus::publish,
                UiNavCommon::close,
                message -> {
                    if (VegaRuntime.narrator() != null) {
                        VegaRuntime.narrator().filler(message, false);
                    }
                }
        );
    }

    JumpToHyperspaceCommand(
            PlayerSession playerSession,
            Status status,
            BooleanSupplier hasHyperspaceBinding,
            BooleanSupplier hasHyperSuperCombinationBinding,
            BooleanSupplier hasRoute,
            Consumer<GameInputSequenceEvent> gameInputPublisher,
            Runnable uiCloseAction,
            Consumer<String> fillerConsumer) {
        this.playerSession = Objects.requireNonNull(playerSession, "playerSession");
        this.status = Objects.requireNonNull(status, "status");
        this.hasHyperspaceBinding = Objects.requireNonNull(hasHyperspaceBinding, "hasHyperspaceBinding");
        this.hasHyperSuperCombinationBinding = Objects.requireNonNull(hasHyperSuperCombinationBinding, "hasHyperSuperCombinationBinding");
        this.hasRoute = Objects.requireNonNull(hasRoute, "hasRoute");
        this.gameInputPublisher = Objects.requireNonNull(gameInputPublisher, "gameInputPublisher");
        this.uiCloseAction = Objects.requireNonNull(uiCloseAction, "uiCloseAction");
        this.fillerConsumer = Objects.requireNonNull(fillerConsumer, "fillerConsumer");
    }

    private static boolean isBindingBound(String bindingName) {
        BindingsMonitor monitor = BindingsMonitor.getInstance();
        if (monitor.getBindings() == null) {
            monitor.ensureBindingsLoaded();
        }
        Map<String, KeyBindingsParser.KeyBinding> bindings = monitor.getBindings();
        return bindings != null && bindings.containsKey(bindingName);
    }

    private static boolean hasPlottedRoute() {
        return !ShipRouteManager.getInstance().getOrderedRoute().isEmpty();
    }

    @Override
    public String id() {
        return ID;
    }

    /** A hyperspace jump needs open space: not while docked or landed. */
    @Override
    public boolean isVisibleForLLM(Status status) {
        return status.isInMainShip() && !status.isDocked() && !status.isLanded();
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        boolean hasHyperspace = hasHyperspaceBinding.getAsBoolean();
        boolean hasCombination = hasHyperSuperCombinationBinding.getAsBoolean();

        // 1. If neither jump binding is available, return error without sending keys
        if (!hasHyperspace && !hasCombination) {
            return StringUtls.localizedResponse("handler.fsd.noJumpBinding");
        }

        // 2. If Hyperspace is not bound, fallback to HyperSuperCombination only in normal space with a plotted route
        String jumpBinding;
        if (hasHyperspace) {
            jumpBinding = BINDING_JUMP_TO_HYPERSPACE.getGameBinding();
        } else {
            boolean inNormalSpace = status.isInMainShip() && !status.isDocked() && !status.isLanded() && !status.isInSupercruise();
            if (!inNormalSpace || !hasRoute.getAsBoolean()) {
                return StringUtls.localizedResponse("handler.fsd.noRouteForJump");
            }
            jumpBinding = BINDING_HYPER_SUPER_COMBINATION.getGameBinding();
        }

        // 3. Select next route system and close UI
        gameInputPublisher.accept(GameInputSequenceEvent.single(GameInputStep.bindingTap(BINDING_TARGET_NEXT_ROUTE_SYSTEM.getGameBinding())));
        uiCloseAction.run();
        gameInputPublisher.accept(GameInputSequenceEvent.single(GameInputStep.delay(150)));

        FsdTarget fsdTarget = playerSession.getFsdTarget();
        if (fsdTarget != null) {
            String starName = fsdTarget.getName() == null ? "unknown" : fsdTarget.getName();
            String starClass = fsdTarget.getStarClass() == null ? "unknown" : fsdTarget.getStarClass();
            String message;
            // Both halves matter: the commander asked to hear about fuel, and this ship can actually
            // scoop it. Without a scoop "refuel possible" names a supply they cannot reach. See FuelScoop.
            if (FuelScoop.announceFuelStars()) {
                String fuelStatus = fsdTarget.getFuelStarStatus() == null ? "unknown" : fsdTarget.getFuelStarStatus();
                message = StringUtls.localizedResponse("handler.fsd.jumping", starName, starClass, fuelStatus);
            } else {
                message = StringUtls.localizedResponse("handler.fsd.jumpingNoFuel", starName, starClass);
            }
            fillerConsumer.accept(message);
        }

        if (status.isFsdCharging()) return null;

        if (status.isFsdMassLocked()) {
            return StringUtls.localizedResponse("handler.supercruise.massLocked");
        } else if (status.isFsdCooldown()) {
            return StringUtls.localizedResponse("handler.supercruise.cooldown");
        } else if (status.isInMainShip()) {
            PreFtlChecks.preJumpCheck(status, StringUtls.localizedResponse("handler.supercruise.preparingFtl"));
            gameInputPublisher.accept(GameInputSequenceEvent.single(GameInputStep.bindingTap(jumpBinding)));
        } else {
            return StringUtls.localizedResponse("handler.supercruise.notInShip");
        }
        return null;
    }
}
