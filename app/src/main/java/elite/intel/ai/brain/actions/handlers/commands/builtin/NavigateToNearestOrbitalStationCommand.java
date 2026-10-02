package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.LocationManager;
import elite.intel.db.managers.ReminderManager;
import elite.intel.db.managers.ShipManager;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.inputs.RoutePlotter;
import elite.intel.gameapi.journal.events.dto.shiploadout.ShipLoadOutDto;
import elite.intel.gameapi.search.spansh.station.refuel.OrbitalStationSearch;
import elite.intel.gameapi.search.spansh.station.refuel.OrbitalStationSearch.OrbitalStation;
import elite.intel.gameapi.search.spansh.station.refuel.RefuelStationSearch;
import elite.intel.session.DockedMarket;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * "Take me to the nearest space station" - finds the closest orbital station this ship can land on and
 * plots a route to it.
 * <p>
 * Unlike {@link FindFuelStationCommand}, which weighs every station that stays put and only prefers an
 * orbital one, the search here is restricted to orbital types, so a surface port or a settlement is never the
 * answer. See {@link OrbitalStationSearch}.
 * <p>
 * Answered like {@link NavigateToPreviousStationCommand}: a station in the system the commander is already in
 * is named and no route is plotted; any other gets a route. Either way a reminder is left. The station the
 * commander is docked at is never the answer - asking from a dock means "the next one".
 */
@RegisterCommand
public final class NavigateToNearestOrbitalStationCommand implements IntelCommand {

    public static final String ID = "navigate_to_nearest_orbital_station";

    /**
     * The radius to search while the ship's jump range is not known yet; the same figure as the refuel search.
     */
    private static final int DEFAULT_RANGE_LY = 50;

    @FunctionalInterface
    public interface StationFinder {
        OrbitalStationSearch.Result find(double x, double y, double z, int radiusLy, String padSize,
                                         long excludeMarketId);
    }

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

    private final StationFinder stationFinder;
    private final BooleanSupplier inMainShipChecker;
    private final Supplier<LocationDao.Coordinates> coordinatesSupplier;
    private final Supplier<String> currentStarSystemSupplier;
    private final Supplier<String> padSizeSupplier;
    private final IntSupplier jumpRangeSupplier;
    private final BooleanSupplier isDockedChecker;
    private final LongSupplier dockedMarketIdSupplier;
    private final ReminderSetter reminderSetter;
    private final RoutePlotterFunction routePlotterFunction;
    private final VoicePublisher voicePublisher;

    public NavigateToNearestOrbitalStationCommand() {
        this(
                OrbitalStationSearch::nearest,
                () -> Status.getInstance().isInMainShip(),
                () -> LocationManager.getInstance().getGalacticCoordinates(),
                () -> PlayerSession.getInstance().getPrimaryStarName(),
                () -> ShipManager.getInstance().requiredPadSize(),
                () -> {
                    ShipLoadOutDto loadout = PlayerSession.getInstance().getShipLoadout();
                    return loadout == null ? 0 : (int) loadout.getMaxJumpRange();
                },
                () -> Status.getInstance().isDocked(),
                () -> DockedMarket.getInstance().marketId(),
                (text, system, station, contact) -> ReminderManager.getInstance().setReminder(text, system, station, contact),
                (answer, dest) -> new RoutePlotter().plotRouteAnd(answer, dest),
                GameEventBus::publish
        );
    }

    NavigateToNearestOrbitalStationCommand(
            StationFinder stationFinder,
            BooleanSupplier inMainShipChecker,
            Supplier<LocationDao.Coordinates> coordinatesSupplier,
            Supplier<String> currentStarSystemSupplier,
            Supplier<String> padSizeSupplier,
            IntSupplier jumpRangeSupplier,
            BooleanSupplier isDockedChecker,
            LongSupplier dockedMarketIdSupplier,
            ReminderSetter reminderSetter,
            RoutePlotterFunction routePlotterFunction,
            VoicePublisher voicePublisher) {
        this.stationFinder = Objects.requireNonNull(stationFinder, "stationFinder");
        this.inMainShipChecker = Objects.requireNonNull(inMainShipChecker, "inMainShipChecker");
        this.coordinatesSupplier = Objects.requireNonNull(coordinatesSupplier, "coordinatesSupplier");
        this.currentStarSystemSupplier = Objects.requireNonNull(currentStarSystemSupplier, "currentStarSystemSupplier");
        this.padSizeSupplier = Objects.requireNonNull(padSizeSupplier, "padSizeSupplier");
        this.jumpRangeSupplier = Objects.requireNonNull(jumpRangeSupplier, "jumpRangeSupplier");
        this.isDockedChecker = Objects.requireNonNull(isDockedChecker, "isDockedChecker");
        this.dockedMarketIdSupplier = Objects.requireNonNull(dockedMarketIdSupplier, "dockedMarketIdSupplier");
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
        return "Plot a course to the nearest space station (orbital starport, not a surface port, settlement "
                + "or fleet carrier) that this ship can land on.";
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
        return List.of();
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        boolean isGui = params != null && params.has("source")
                && "gui".equalsIgnoreCase(params.get("source").getAsString());

        // 1. Ship check
        if (!inMainShipChecker.getAsBoolean()) {
            return returnOrVoice(StringUtls.localizedResponse(
                    "handler.navigateToNearestOrbitalStation.notInMainShip"), isGui);
        }

        // 2. Position
        LocationDao.Coordinates coordinates = coordinatesSupplier.get();
        if (!isKnownPosition(coordinates)) {
            return returnOrVoice(StringUtls.localizedResponse(
                    "handler.navigateToNearestOrbitalStation.noCoords"), isGui);
        }

        // 3. Search. The docked station is the one the commander is already at, so it is never the answer.
        int jumpRange = jumpRangeSupplier.getAsInt();
        int radius = jumpRange < 1 ? DEFAULT_RANGE_LY : jumpRange;
        long excludeMarketId = isDockedChecker.getAsBoolean() ? dockedMarketIdSupplier.getAsLong() : 0L;

        OrbitalStationSearch.Result result = stationFinder.find(
                coordinates.x(), coordinates.y(), coordinates.z(), radius, padSizeSupplier.get(), excludeMarketId);
        if (result == null || result.stations().isEmpty()) {
            if (result == null || result.failed()) {
                return returnOrVoice(StringUtls.localizedResponse(
                        "handler.navigateToNearestOrbitalStation.searchFailed"), isGui);
            }
            // How far it actually looked, not what was asked for: the search widens on its own.
            return returnOrVoice(StringUtls.localizedResponse(
                    "handler.navigateToNearestOrbitalStation.notFound",
                    RefuelStationSearch.radiiToTry(radius).getLast()), isGui);
        }

        OrbitalStation station = result.stations().getFirst();

        // 4. Reminder (always set, even in the same system)
        String reminder = StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.reminder",
                station.stationName(), station.starSystem());
        reminderSetter.setReminder(reminder, station.starSystem(), station.stationName(), null);

        // 5. Same system: say so, plot nothing
        if (isHere(station)) {
            return returnOrVoice(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.here",
                    station.stationName(), Math.round(station.arrivalLs())), isGui);
        }

        // 6. Route
        String message = StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.plotted",
                station.stationName(), station.starSystem(), Math.round(station.distanceLy()));
        String plotted = routePlotterFunction.plotRouteAnd(message, station.starSystem());
        return returnOrVoice((plotted != null && !plotted.isBlank()) ? plotted : message, isGui);
    }

    /**
     * Sol really does sit at 0,0,0 - and so does a position nothing was recorded for. Only the star's name
     * tells them apart, as in the refuel command.
     */
    private boolean isKnownPosition(LocationDao.Coordinates coordinates) {
        if (coordinates == null) return false;
        boolean atOrigin = coordinates.x() == 0 && coordinates.y() == 0 && coordinates.z() == 0;
        return !atOrigin || "Sol".equalsIgnoreCase(currentStarSystemSupplier.get());
    }

    /**
     * Whether the station is in the system the commander is in: a reported distance of zero, or the same
     * name, because either input can be the stale one.
     */
    private boolean isHere(OrbitalStation station) {
        String current = currentStarSystemSupplier.get();
        return station.distanceLy() <= 0
                || (current != null && current.strip().equalsIgnoreCase(station.starSystem().strip()));
    }

    private String returnOrVoice(String answer, boolean isGui) {
        if (isGui) {
            voicePublisher.publish(new AiVoxResponseEvent(answer));
            return null;
        }
        return answer;
    }
}
