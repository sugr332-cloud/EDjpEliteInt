package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.db.managers.DockingHistoryManager;
import elite.intel.db.managers.ReminderManager;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.inputs.RoutePlotter;
import elite.intel.session.DockedMarket;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Built-in command to plot a course to the previous station from docking history.
 */
@RegisterCommand
public final class NavigateToPreviousStationCommand implements IntelCommand {

    public static final String ID = "navigate_to_previous_station";

    @FunctionalInterface
    public interface ReminderSetter {
        void setReminder(String text, String starSystem, String stationName, ReminderContact contact);
    }

    @FunctionalInterface
    public interface RoutePlotterFunction {
        String plotRouteAnd(String answer, String destination);
    }

    @FunctionalInterface
    public interface VoicePublisher {
        void publish(Object event);
    }

    private final Function<Integer, Optional<DockingHistoryEntry>> nthStationLookup;
    private final BooleanSupplier inMainShipChecker;
    private final BooleanSupplier isDockedChecker;
    private final LongSupplier dockedMarketIdSupplier;
    private final Supplier<Long> currentSystemAddressSupplier;
    private final Supplier<String> currentStarSystemSupplier;
    private final Supplier<String> ownCarrierCallSignSupplier;
    private final Supplier<String> currentFleetCarrierSystemSupplier;
    private final ReminderSetter reminderSetter;
    private final RoutePlotterFunction routePlotterFunction;
    private final VoicePublisher voicePublisher;

    public NavigateToPreviousStationCommand() {
        this(
                back -> DockingHistoryManager.getInstance().getNthPreviousStation(back),
                () -> Status.getInstance().isInMainShip(),
                () -> Status.getInstance().isDocked(),
                () -> DockedMarket.getInstance().marketId(),
                () -> {
                    var data = PlayerSession.getInstance().getLocationData();
                    return data != null ? data.getSystemAddress() : null;
                },
                () -> PlayerSession.getInstance().getPrimaryStarName(),
                () -> {
                    var carrier = PlayerSession.getInstance().getFleetCarrierData();
                    return carrier != null ? carrier.getCallSign() : null;
                },
                () -> PlayerSession.getInstance().getCurrentFleetCarrierSystem(),
                (text, system, station, contact) -> ReminderManager.getInstance().setReminder(text, system, station, contact),
                (answer, dest) -> new RoutePlotter().plotRouteAnd(answer, dest),
                GameEventBus::publish
        );
    }

    NavigateToPreviousStationCommand(
            Function<Integer, Optional<DockingHistoryEntry>> nthStationLookup,
            BooleanSupplier inMainShipChecker,
            BooleanSupplier isDockedChecker,
            LongSupplier dockedMarketIdSupplier,
            Supplier<Long> currentSystemAddressSupplier,
            Supplier<String> currentStarSystemSupplier,
            Supplier<String> ownCarrierCallSignSupplier,
            Supplier<String> currentFleetCarrierSystemSupplier,
            ReminderSetter reminderSetter,
            RoutePlotterFunction routePlotterFunction,
            VoicePublisher voicePublisher) {
        this.nthStationLookup = Objects.requireNonNull(nthStationLookup, "nthStationLookup");
        this.inMainShipChecker = Objects.requireNonNull(inMainShipChecker, "inMainShipChecker");
        this.isDockedChecker = Objects.requireNonNull(isDockedChecker, "isDockedChecker");
        this.dockedMarketIdSupplier = Objects.requireNonNull(dockedMarketIdSupplier, "dockedMarketIdSupplier");
        this.currentSystemAddressSupplier = Objects.requireNonNull(currentSystemAddressSupplier, "currentSystemAddressSupplier");
        this.currentStarSystemSupplier = Objects.requireNonNull(currentStarSystemSupplier, "currentStarSystemSupplier");
        this.ownCarrierCallSignSupplier = Objects.requireNonNull(ownCarrierCallSignSupplier, "ownCarrierCallSignSupplier");
        this.currentFleetCarrierSystemSupplier = Objects.requireNonNull(currentFleetCarrierSystemSupplier, "currentFleetCarrierSystemSupplier");
        this.reminderSetter = Objects.requireNonNull(reminderSetter, "reminderSetter");
        this.routePlotterFunction = Objects.requireNonNull(routePlotterFunction, "routePlotterFunction");
        this.voicePublisher = Objects.requireNonNull(voicePublisher, "voicePublisher");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Plot a course to the star system of the previous station from docking history.";
    }

    @Override
    public boolean isVisibleForLLM(Status status) {
        return true;
    }

    @Override
    public boolean isAvailableIn(IntelActionContext context) {
        return true;
    }

    @Override
    public List<ActionParameterSpec> parameters() {
        return List.of(
                new ActionParameterSpec(
                        "back",
                        "number",
                        false,
                        "Number of stations to go back in docking history (1 for previous, 2 for two stations ago, default: 1).",
                        List.of("1", "2"),
                        "Stations back (default: 1)"
                )
        );
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        boolean isGui = params != null && params.has("source")
                && "gui".equalsIgnoreCase(params.get("source").getAsString());

        // 1. Ship check
        if (!inMainShipChecker.getAsBoolean()) {
            String answer = StringUtls.localizedResponse("handler.navigateToPreviousStation.notInMainShip");
            return returnOrVoice(answer, isGui);
        }

        // 2. Parse and validate back parameter
        int back = 1;
        if (params != null && params.has("back") && !params.get("back").isJsonNull()) {
            JsonElement backElem = params.get("back");
            try {
                double val = backElem.getAsDouble();
                if (val < 1 || val != Math.floor(val)) {
                    String answer = StringUtls.localizedResponse("handler.navigateToPreviousStation.invalidBack", backElem.getAsString());
                    return returnOrVoice(answer, isGui);
                }
                back = (int) val;
            } catch (Exception e) {
                String answer = StringUtls.localizedResponse("handler.navigateToPreviousStation.invalidBack", backElem.getAsString());
                return returnOrVoice(answer, isGui);
            }
        }

        // 3. Exception rule for counting:
        // If docked and the latest history entry does not match the currently docked station marketId,
        // treat the currently docked station as unrecorded, so target back - 1.
        int targetIndex = back;
        if (isDockedChecker.getAsBoolean()) {
            long currentDockedMarketId = dockedMarketIdSupplier.getAsLong();
            if (currentDockedMarketId > 0) {
                Optional<DockingHistoryEntry> latestEntryOpt = nthStationLookup.apply(0);
                if (latestEntryOpt.isPresent() && latestEntryOpt.get().marketId() != currentDockedMarketId) {
                    targetIndex = back - 1;
                }
            }
        }

        if (targetIndex < 0) {
            String answer = StringUtls.localizedResponse("handler.navigateToPreviousStation.insufficientHistory");
            return returnOrVoice(answer, isGui);
        }

        Optional<DockingHistoryEntry> targetOpt = nthStationLookup.apply(targetIndex);
        if (targetOpt.isEmpty()) {
            String answer = StringUtls.localizedResponse("handler.navigateToPreviousStation.insufficientHistory");
            return returnOrVoice(answer, isGui);
        }

        DockingHistoryEntry target = targetOpt.get();
        String stationName = target.stationName();
        String targetSystem = target.starSystem();

        // 4. Fleet carrier check
        boolean isFleetCarrier = target.isFleetCarrier();
        String ownCallSign = ownCarrierCallSignSupplier.get();
        boolean isOwnCarrier = ownCallSign != null && !ownCallSign.isBlank()
                && stationName != null && ownCallSign.equalsIgnoreCase(stationName.trim());

        String message;
        if (isOwnCarrier) {
            String currentCarrierSys = currentFleetCarrierSystemSupplier.get();
            if (currentCarrierSys != null && !currentCarrierSys.isBlank()) {
                boolean moved = targetSystem == null || !currentCarrierSys.trim().equalsIgnoreCase(targetSystem.trim());
                targetSystem = currentCarrierSys.trim();
                if (moved) {
                    message = StringUtls.localizedResponse("handler.navigateToPreviousStation.ownCarrierMoved", stationName, targetSystem);
                } else {
                    message = StringUtls.localizedResponse("handler.navigateToPreviousStation.success", stationName, targetSystem);
                }
            } else {
                message = StringUtls.localizedResponse("handler.navigateToPreviousStation.carrier", stationName, targetSystem);
            }
        } else if (isFleetCarrier) {
            message = StringUtls.localizedResponse("handler.navigateToPreviousStation.carrier", stationName, targetSystem);
        } else {
            message = StringUtls.localizedResponse("handler.navigateToPreviousStation.success", stationName, targetSystem);
        }

        // 5. Same system check (compare systemAddress first, fallback to system name)
        Long currentSysAddr = currentSystemAddressSupplier.get();
        boolean sameSystem = false;
        if (currentSysAddr != null && currentSysAddr > 0 && target.systemAddress() > 0) {
            if (isOwnCarrier && targetSystem != null && !targetSystem.equalsIgnoreCase(target.starSystem())) {
                String currentSysName = currentStarSystemSupplier.get();
                if (currentSysName != null && !currentSysName.isBlank()) {
                    sameSystem = currentSysName.trim().equalsIgnoreCase(targetSystem.trim());
                }
            } else {
                sameSystem = (currentSysAddr.longValue() == target.systemAddress());
            }
        } else {
            String currentSysName = currentStarSystemSupplier.get();
            if (currentSysName != null && !currentSysName.isBlank() && targetSystem != null) {
                sameSystem = currentSysName.trim().equalsIgnoreCase(targetSystem.trim());
            }
        }

        // 6. Set reminder (always set, even if in the same system)
        String reminderMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.reminder", stationName, targetSystem);
        reminderSetter.setReminder(reminderMessage, targetSystem, stationName, null);

        // 7. Route plotting
        if (sameSystem) {
            String answer = StringUtls.localizedResponse("handler.navigateToPreviousStation.sameSystem", stationName);
            return returnOrVoice(answer, isGui);
        }

        String plottedAnswer = routePlotterFunction.plotRouteAnd(message, targetSystem);
        String finalAnswer = (plottedAnswer != null && !plottedAnswer.isBlank()) ? plottedAnswer : message;
        return returnOrVoice(finalAnswer, isGui);
    }

    private String returnOrVoice(String answer, boolean isGui) {
        if (isGui) {
            voicePublisher.publish(new AiVoxResponseEvent(answer));
            return null;
        }
        return answer;
    }
}
