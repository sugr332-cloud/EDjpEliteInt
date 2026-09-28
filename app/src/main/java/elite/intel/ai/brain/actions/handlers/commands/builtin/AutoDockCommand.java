package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.ai.hands.Bindings;
import elite.intel.ai.hands.events.GameInputSequenceEvent;
import elite.intel.ai.hands.events.GameInputStep;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.managers.ShipLoadoutManager;
import elite.intel.eventbus.GameControllerBus;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.journal.events.DockingCancelledEvent;
import elite.intel.gameapi.journal.events.DockingDeniedEvent;
import elite.intel.gameapi.journal.events.DockingGrantedEvent;
import elite.intel.gameapi.journal.events.DockingTimeoutEvent;
import elite.intel.gameapi.journal.events.dto.shiploadout.ModuleDto;
import elite.intel.gameapi.journal.events.dto.shiploadout.ShipLoadOutDto;
import elite.intel.gameapi.journal.subscribers.DockingStateTracker;
import elite.intel.session.Status;
import elite.intel.session.ui.UINavigator;
import elite.intel.util.StringUtls;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Built-in command for voice auto-docking (PT-5):
 * Requests docking permission, waits asynchronously for grant/denial/timeout,
 * and sets throttle to zero once granted if a docking computer is equipped.
 */
@RegisterCommand
public final class AutoDockCommand implements IntelCommand {

    public static final String ID = "auto_dock";
    public static final int DOCKING_TIMEOUT_SECONDS = 20;

    private static final Logger log = LogManager.getLogger(AutoDockCommand.class);

    private final Status status;
    private final UINavigator navigator;
    private final DockingStateTracker dockingStateTracker;
    private final Supplier<ShipLoadOutDto> shipLoadoutSupplier;
    private final Consumer<Object> gameInputPublisher;
    private final Consumer<Object> voicePublisher;
    private final Executor backgroundExecutor;
    private final int timeoutSeconds;

    public AutoDockCommand() {
        this(
                Status.getInstance(),
                new UINavigator(),
                DockingStateTracker.getInstance(),
                () -> ShipLoadoutManager.getInstance().get(),
                GameControllerBus::publish,
                GameEventBus::publish,
                Executors.newVirtualThreadPerTaskExecutor(),
                DOCKING_TIMEOUT_SECONDS
        );
    }

    AutoDockCommand(
            Status status,
            UINavigator navigator,
            DockingStateTracker dockingStateTracker,
            Supplier<ShipLoadOutDto> shipLoadoutSupplier,
            Consumer<Object> gameInputPublisher,
            Consumer<Object> voicePublisher,
            Executor backgroundExecutor,
            int timeoutSeconds) {
        this.status = Objects.requireNonNull(status, "status");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.dockingStateTracker = Objects.requireNonNull(dockingStateTracker, "dockingStateTracker");
        this.shipLoadoutSupplier = Objects.requireNonNull(shipLoadoutSupplier, "shipLoadoutSupplier");
        this.gameInputPublisher = Objects.requireNonNull(gameInputPublisher, "gameInputPublisher");
        this.voicePublisher = Objects.requireNonNull(voicePublisher, "voicePublisher");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Request docking permission at nearby station and automatically throttle to zero for auto-docking when granted.";
    }

    @Override
    public boolean isVisibleForLLM(Status status) {
        return status.isInMainShip()
                && !status.isDocked()
                && !status.isLanded()
                && !status.isInSupercruise();
    }

    @Override
    public boolean sendsGameInput() {
        return true;
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        // 1. Situation checks
        if (!status.isInMainShip()) {
            return StringUtls.localizedResponse("handler.autoDock.notInMainShip");
        }
        if (status.isDocked() || status.isLanded()) {
            return StringUtls.localizedResponse("handler.autoDock.alreadyDocked");
        }
        if (status.isInSupercruise()) {
            return StringUtls.localizedResponse("handler.autoDock.inSupercruise");
        }

        // 2. Already granted check
        if (dockingStateTracker.isDockingGranted()) {
            sendThrottleZero();
            return StringUtls.localizedResponse("handler.autoDock.alreadyGranted");
        }

        // 3. Docking computer check
        Boolean hasComputer = checkDockingComputerInstalled();
        if (Boolean.FALSE.equals(hasComputer)) {
            // Definitely no docking computer or turned off: request only, do not wait, do not zero throttle
            RequestDockingCommand.sendDockingRequest(navigator, status);
            return StringUtls.localizedResponse("handler.autoDock.noDockingComputer");
        }

        // 4. Register event listener BEFORE sending docking request to avoid race condition
        DockingResultWaiter waiter = new DockingResultWaiter();
        GameEventBus.register(waiter);

        try {
            RequestDockingCommand.sendDockingRequest(navigator, status);
        } catch (Throwable t) {
            GameEventBus.unregister(waiter);
            log.error("Failed to send docking request key sequence", t);
            throw t;
        }

        // 5. Asynchronously await outcome on virtual thread
        backgroundExecutor.execute(() -> awaitDockingOutcome(waiter));

        // Return immediately so VEGA turn does not block
        return StringUtls.localizedResponse("handler.autoDock.requested");
    }

    private void awaitDockingOutcome(DockingResultWaiter waiter) {
        try {
            Object event = waiter.future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (event instanceof DockingGrantedEvent) {
                // Re-verify situation before sending throttle zero
                if (!status.isInMainShip() || status.isDocked() || status.isLanded() || status.isInSupercruise()) {
                    speak(StringUtls.localizedResponse("handler.autoDock.cancelled"));
                    return;
                }
                sendThrottleZero();
                speak(StringUtls.localizedResponse("handler.autoDock.throttleZero"));
            } else if (event instanceof DockingDeniedEvent denied) {
                speak(deniedMessage(denied.getReason()));
            } else if (event instanceof DockingCancelledEvent) {
                speak(StringUtls.localizedResponse("handler.autoDock.cancelled"));
            } else if (event instanceof DockingTimeoutEvent) {
                speak(StringUtls.localizedResponse("handler.autoDock.timeout"));
            }
        } catch (TimeoutException e) {
            speak(StringUtls.localizedResponse("handler.autoDock.timeout"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.warn("Error during auto-dock waiting", e);
            speak(StringUtls.localizedResponse("handler.autoDock.timeout"));
        } finally {
            GameEventBus.unregister(waiter);
        }
    }

    private void sendThrottleZero() {
        gameInputPublisher.accept(GameInputSequenceEvent.of(
                GameInputStep.bindingTap(Bindings.GameCommand.BINDING_SET_SPEED_ZERO.getGameBinding())
        ));
    }

    private void speak(String phrase) {
        if (phrase != null && !phrase.isBlank()) {
            voicePublisher.accept(new AiVoxResponseEvent(phrase));
        }
    }

    private String deniedMessage(String reason) {
        if (reason == null) {
            return StringUtls.localizedResponse("handler.autoDock.denied.other");
        }
        return switch (reason.trim().toLowerCase()) {
            case "distance" -> StringUtls.localizedResponse("handler.autoDock.denied.distance");
            case "nospace" -> StringUtls.localizedResponse("handler.autoDock.denied.noSpace");
            case "hostile" -> StringUtls.localizedResponse("handler.autoDock.denied.hostile");
            case "offences" -> StringUtls.localizedResponse("handler.autoDock.denied.offences");
            case "toolarge" -> StringUtls.localizedResponse("handler.autoDock.denied.tooLarge");
            case "activefighter" -> StringUtls.localizedResponse("handler.autoDock.denied.activeFighter");
            default -> StringUtls.localizedResponse("handler.autoDock.denied.other");
        };
    }

    private Boolean checkDockingComputerInstalled() {
        ShipLoadOutDto loadout = shipLoadoutSupplier.get();
        if (loadout == null || loadout.getModules() == null) {
            return null; // Unknown
        }
        for (ModuleDto m : loadout.getModules()) {
            if (m.getItem() != null && m.getItem().toLowerCase().contains("dockingcomputer")) {
                if (m.isOn()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final class DockingResultWaiter {
        final CompletableFuture<Object> future = new CompletableFuture<>();

        @Subscribe
        public void onGranted(DockingGrantedEvent event) {
            future.complete(event);
        }

        @Subscribe
        public void onDenied(DockingDeniedEvent event) {
            future.complete(event);
        }

        @Subscribe
        public void onCancelled(DockingCancelledEvent event) {
            future.complete(event);
        }

        @Subscribe
        public void onTimeout(DockingTimeoutEvent event) {
            future.complete(event);
        }
    }
}
