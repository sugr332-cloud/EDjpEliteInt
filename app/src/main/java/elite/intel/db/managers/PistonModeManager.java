package elite.intel.db.managers;

import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.db.dao.PistonModeDao;
import elite.intel.db.dao.PistonModeDao.PistonModeEntry;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.db.util.Database;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.inputs.RoutePlotter;
import elite.intel.gameapi.journal.events.DockedEvent;
import elite.intel.gameapi.journal.events.UndockedEvent;
import elite.intel.session.DockedMarket;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.ui.support.GameWindowActivator;
import elite.intel.util.StringUtls;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Manages the persistent piston trading mode (PT-4):
 * Alternates navigation between endpoints A and B upon undocking.
 */
public final class PistonModeManager {

    private static final Logger log = LogManager.getLogger(PistonModeManager.class);
    private static final PistonModeManager INSTANCE = new PistonModeManager();

    public static final int UNDOCK_ROUTE_DELAY_SECONDS = 15;
    public static final String START_TYPE_HISTORY = "HISTORY";
    public static final String START_TYPE_TRADE_CANDIDATE = "TRADE_CANDIDATE";

    @FunctionalInterface
    public interface RoutePlotterFunction {
        String plotRouteAnd(String answer, String destination);
    }

    @FunctionalInterface
    public interface ReminderSetter {
        void setReminder(String text, String starSystem, String stationName, ReminderContact contact);
    }

    @FunctionalInterface
    public interface VoicePublisher {
        void publish(Object event);
    }

    @FunctionalInterface
    public interface ArrivalTargetFunction {
        void target(String starSystem, String stationName);
    }

    private final RoutePlotterFunction routePlotterFunction;
    private final ReminderSetter reminderSetter;
    private final VoicePublisher voicePublisher;
    private final BooleanSupplier gameWindowForegroundChecker;
    private final IntSupplier undockDelaySeconds;
    private final ArrivalTargetFunction arrivalTargetFunction;
    private final Executor delayExecutor;
    private final Supplier<Status> statusSupplier;
    private final Supplier<DockingHistoryManager> dockingHistorySupplier;
    private final Supplier<QueryResultDisplayManager> queryResultDisplaySupplier;
    private final Supplier<String> currentStarSystemSupplier;
    private final Supplier<Long> currentSystemAddressSupplier;
    private final Supplier<Long> dockedMarketIdSupplier;

    private PistonModeManager() {
        this(
                (answer, dest) -> new RoutePlotter().plotRouteAnd(answer, dest),
                (text, sys, stn, contact) -> ReminderManager.getInstance().setReminder(text, sys, stn, contact),
                GameEventBus::publish,
                GameWindowActivator::isEliteDangerousForeground,
                () -> UNDOCK_ROUTE_DELAY_SECONDS,
                (sys, stn) -> {}, // PT-3 placeholder
                Executors.newVirtualThreadPerTaskExecutor(),
                Status::getInstance,
                DockingHistoryManager::getInstance,
                QueryResultDisplayManager::getInstance,
                () -> PlayerSession.getInstance().getPrimaryStarName(),
                () -> {
                    var data = PlayerSession.getInstance().getLocationData();
                    return data != null ? data.getSystemAddress() : null;
                },
                () -> DockedMarket.getInstance().marketId()
        );
    }

    PistonModeManager(
            RoutePlotterFunction routePlotterFunction,
            ReminderSetter reminderSetter,
            VoicePublisher voicePublisher,
            BooleanSupplier gameWindowForegroundChecker,
            IntSupplier undockDelaySeconds,
            ArrivalTargetFunction arrivalTargetFunction,
            Executor delayExecutor,
            Supplier<Status> statusSupplier,
            Supplier<DockingHistoryManager> dockingHistorySupplier,
            Supplier<QueryResultDisplayManager> queryResultDisplaySupplier,
            Supplier<String> currentStarSystemSupplier,
            Supplier<Long> currentSystemAddressSupplier,
            Supplier<Long> dockedMarketIdSupplier) {
        this.routePlotterFunction = Objects.requireNonNull(routePlotterFunction, "routePlotterFunction");
        this.reminderSetter = Objects.requireNonNull(reminderSetter, "reminderSetter");
        this.voicePublisher = Objects.requireNonNull(voicePublisher, "voicePublisher");
        this.gameWindowForegroundChecker = Objects.requireNonNull(gameWindowForegroundChecker, "gameWindowForegroundChecker");
        this.undockDelaySeconds = Objects.requireNonNull(undockDelaySeconds, "undockDelaySeconds");
        this.arrivalTargetFunction = Objects.requireNonNull(arrivalTargetFunction, "arrivalTargetFunction");
        this.delayExecutor = Objects.requireNonNull(delayExecutor, "delayExecutor");
        this.statusSupplier = Objects.requireNonNull(statusSupplier, "statusSupplier");
        this.dockingHistorySupplier = Objects.requireNonNull(dockingHistorySupplier, "dockingHistorySupplier");
        this.queryResultDisplaySupplier = Objects.requireNonNull(queryResultDisplaySupplier, "queryResultDisplaySupplier");
        this.currentStarSystemSupplier = Objects.requireNonNull(currentStarSystemSupplier, "currentStarSystemSupplier");
        this.currentSystemAddressSupplier = Objects.requireNonNull(currentSystemAddressSupplier, "currentSystemAddressSupplier");
        this.dockedMarketIdSupplier = Objects.requireNonNull(dockedMarketIdSupplier, "dockedMarketIdSupplier");
    }

    public static PistonModeManager getInstance() {
        return INSTANCE;
    }

    public record StartResult(boolean success, String message) {
        public static StartResult success(String message) {
            return new StartResult(true, message);
        }

        public static StartResult failure(String message) {
            return new StartResult(false, message);
        }
    }

    public synchronized PistonModeEntry getState() {
        try {
            return Database.withDao(PistonModeDao.class, PistonModeDao::getState);
        } catch (Exception e) {
            log.error("Failed to read piston_mode state: {}", e.getMessage(), e);
            return null;
        }
    }

    public synchronized boolean isActive() {
        PistonModeEntry state = getState();
        return state != null && state.active();
    }

    /**
     * Start piston mode from docking history.
     */
    public synchronized StartResult startFromHistory() {
        Optional<DockingHistoryManager.DistinctStations> distinctOpt = dockingHistorySupplier.get().getRecentDistinctStations();
        if (distinctOpt.isEmpty()) {
            return StartResult.failure(StringUtls.localizedResponse("handler.pistonMode.noHistory"));
        }

        DockingHistoryManager.DistinctStations distinct = distinctOpt.get();
        DockingHistoryEntry x = distinct.current();
        DockingHistoryEntry y = distinct.previous();

        String nowIso = Instant.now().toString();
        saveState(true, START_TYPE_HISTORY,
                x.stationName(), x.starSystem(), x.systemAddress(), x.marketId(),
                y.stationName(), y.starSystem(), y.systemAddress(), y.marketId(),
                null, nowIso);

        Status status = statusSupplier.get();
        boolean dockedAtX = status.isDocked() && isDockedAtStation(x.marketId(), x.stationName(), x.starSystem());

        if (dockedAtX) {
            // Already docked at X: wait for player departure without setting route
            return StartResult.success(StringUtls.localizedResponse(
                    "handler.pistonMode.startHistoryWait",
                    x.stationName(), x.starSystem(),
                    y.stationName(), y.starSystem()
            ));
        } else {
            // Not docked at X (in space or docked elsewhere): route to Y
            String currentSystem = currentStarSystemSupplier.get();
            Long currentSystemAddress = currentSystemAddressSupplier.get();
            boolean sameSystem = isSameSystem(currentSystem, y.starSystem(), currentSystemAddress, y.systemAddress());

            String reminderText = StringUtls.localizedResponse("handler.pistonMode.reminder", y.stationName(), y.starSystem());
            reminderSetter.setReminder(reminderText, y.starSystem(), y.stationName(), null);

            if (sameSystem) {
                String waitPart = StringUtls.localizedResponse("handler.pistonMode.startHistoryWait", x.stationName(), x.starSystem(), y.stationName(), y.starSystem());
                String samePart = StringUtls.localizedResponse("handler.pistonMode.sameSystem", y.stationName());
                return StartResult.success(waitPart + " " + samePart);
            } else {
                String routeMessage = StringUtls.localizedResponse(
                        "handler.pistonMode.startHistoryRoute",
                        x.stationName(), x.starSystem(),
                        y.stationName(), y.starSystem()
                );
                String plotted = routePlotterFunction.plotRouteAnd(routeMessage, y.starSystem());
                return StartResult.success(plotted != null && !plotted.isBlank() ? plotted : routeMessage);
            }
        }
    }

    /**
     * Start piston mode from trade candidates search results.
     */
    public synchronized StartResult startFromTradeCandidates(int rank) {
        Optional<LatestDisplay> latestOpt = queryResultDisplaySupplier.get().getLatest();
        if (latestOpt.isEmpty()) {
            return StartResult.failure(StringUtls.localizedResponse("handler.pistonMode.noRecentResult"));
        }

        LatestDisplay latest = latestOpt.get();
        Instant now = Instant.now();
        if (latest.savedAt() == null || Duration.between(latest.savedAt(), now).getSeconds() > 36000) {
            return StartResult.failure(StringUtls.localizedResponse("handler.pistonMode.noRecentResult"));
        }

        if (!latest.isTradeCandidates()) {
            return StartResult.failure(StringUtls.localizedResponse("handler.pistonMode.noRecentResult"));
        }

        TradeCandidatesDataDto dto = latest.tradeCandidates();
        if (dto == null || dto.candidates() == null || dto.candidates().isEmpty()) {
            return StartResult.failure(StringUtls.localizedResponse("handler.pistonMode.noRecentResult"));
        }

        if (rank < 1 || rank > dto.candidates().size()) {
            return StartResult.failure(StringUtls.localizedResponse("handler.pistonMode.invalidRank", rank));
        }

        TradeCandidateDto candidate = dto.candidates().get(rank - 1);
        String buyStation = candidate.buyStation();
        String buySystem = candidate.buySystem();
        String sellStation = candidate.sellStation();
        String sellSystem = candidate.sellSystem();
        String commodity = candidate.commodity();

        String nowIso = Instant.now().toString();
        saveState(true, START_TYPE_TRADE_CANDIDATE,
                buyStation, buySystem, 0L, 0L,
                sellStation, sellSystem, 0L, 0L,
                commodity, nowIso);

        Status status = statusSupplier.get();
        boolean dockedAtA = status.isDocked() && isDockedAtStation(0L, buyStation, buySystem);

        if (dockedAtA) {
            // Already docked at A (buy station): capture market ID if available and wait
            long curMarketId = dockedMarketIdSupplier.get();
            Long curSystemAddress = currentSystemAddressSupplier.get();
            if (curMarketId != 0) {
                updateStationAMarketId(curMarketId, curSystemAddress != null ? curSystemAddress : 0L, nowIso);
            }

            return StartResult.success(StringUtls.localizedResponse(
                    "handler.pistonMode.startTradeWait",
                    buyStation, buySystem,
                    sellStation, sellSystem
            ));
        } else {
            // Not docked at A: route to A (purchase location)
            String currentSystem = currentStarSystemSupplier.get();
            Long currentSystemAddress = currentSystemAddressSupplier.get();
            boolean sameSystem = isSameSystem(currentSystem, buySystem, currentSystemAddress, null);

            String reminderText = StringUtls.localizedResponse("handler.pistonMode.reminder", buyStation, buySystem);
            reminderSetter.setReminder(reminderText, buySystem, buyStation, null);

            if (sameSystem) {
                String waitPart = StringUtls.localizedResponse("handler.pistonMode.startTradeWait", buyStation, buySystem, sellStation, sellSystem);
                String samePart = StringUtls.localizedResponse("handler.pistonMode.sameSystem", buyStation);
                return StartResult.success(waitPart + " " + samePart);
            } else {
                String routeMessage = StringUtls.localizedResponse(
                        "handler.pistonMode.startTradeRoute",
                        buyStation, buySystem,
                        sellStation, sellSystem
                );
                String plotted = routePlotterFunction.plotRouteAnd(routeMessage, buySystem);
                return StartResult.success(plotted != null && !plotted.isBlank() ? plotted : routeMessage);
            }
        }
    }

    /**
     * Stop piston mode.
     */
    public synchronized boolean stop() {
        if (!isActive()) {
            return false;
        }
        try {
            Database.withDao(PistonModeDao.class, dao -> {
                dao.deactivate(Instant.now().toString());
                return null;
            });
            return true;
        } catch (Exception e) {
            log.error("Failed to deactivate piston_mode: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Journal Docked event handler.
     */
    public synchronized void onDocked(DockedEvent event) {
        if (event == null || !isActive()) {
            return;
        }

        PistonModeEntry state = getState();
        if (state == null || !state.active()) {
            return;
        }

        String dockedStation = event.getStationName();
        String dockedSystem = event.getStarSystem();
        long marketId = event.getMarketID();
        long systemAddress = event.getSystemAddress();

        // 1. If starting from trade candidates, capture MarketID upon first docking at A or B
        if (START_TYPE_TRADE_CANDIDATE.equalsIgnoreCase(state.startType())) {
            if (state.stationAMarketId() == 0 && isStationMatch(dockedStation, dockedSystem, state.stationAName(), state.stationASystem())) {
                updateStationAMarketId(marketId, systemAddress, Instant.now().toString());
                state = getState();
            } else if (state.stationBMarketId() == 0 && isStationMatch(dockedStation, dockedSystem, state.stationBName(), state.stationBSystem())) {
                updateStationBMarketId(marketId, systemAddress, Instant.now().toString());
                state = getState();
            }
        }

        if (state == null) {
            return;
        }

        // 2. Check if docked station is A or B
        boolean isA = isStationA(event, state);
        boolean isB = isStationB(event, state);

        if (!isA && !isB) {
            // Docked at a third station: auto-terminate piston mode
            stop();
            voicePublisher.publish(new AiVoxResponseEvent(
                    StringUtls.localizedResponse("handler.pistonMode.autoStoppedForeignDock", dockedStation)
            ));
        }
    }

    /**
     * Journal Undocked event handler.
     */
    public synchronized void onUndocked(UndockedEvent event) {
        if (event == null || !isActive()) {
            return;
        }

        PistonModeEntry state = getState();
        if (state == null || !state.active()) {
            return;
        }

        // Determine destination endpoint
        boolean fromA = isUndockedFromA(event, state);
        boolean fromB = isUndockedFromB(event, state);

        final String targetStation;
        final String targetSystem;
        final Long targetSystemAddress;

        if (fromA) {
            targetStation = state.stationBName();
            targetSystem = state.stationBSystem();
            targetSystemAddress = state.stationBSystemAddress();
        } else if (fromB) {
            targetStation = state.stationAName();
            targetSystem = state.stationASystem();
            targetSystemAddress = state.stationASystemAddress();
        } else {
            // Undocked from unknown / non-piston station: do nothing
            return;
        }

        // Schedule delayed route plotting on virtual thread
        delayExecutor.execute(() -> handleDelayedUndockRoute(targetStation, targetSystem, targetSystemAddress));
    }

    private void handleDelayedUndockRoute(String targetStation, String targetSystem, Long targetSystemAddress) {
        int delaySec = undockDelaySeconds.getAsInt();
        if (delaySec > 0) {
            try {
                Thread.sleep(delaySec * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Verify mode is still active
        if (!isActive()) {
            return;
        }

        Status status = statusSupplier.get();
        boolean inMainShip = status.isInMainShip();
        boolean flying = !status.isDocked() && !status.isLanded();
        boolean noFocus = status.isNoFocus();
        boolean isForeground = gameWindowForegroundChecker.getAsBoolean();

        if (inMainShip && flying && noFocus && isForeground) {
            // All conditions met: plot route and set reminder
            String currentSystem = currentStarSystemSupplier.get();
            Long currentSystemAddress = currentSystemAddressSupplier.get();
            boolean sameSystem = isSameSystem(currentSystem, targetSystem, currentSystemAddress, targetSystemAddress);

            String reminderText = StringUtls.localizedResponse("handler.pistonMode.reminder", targetStation, targetSystem);
            reminderSetter.setReminder(reminderText, targetSystem, targetStation, null);

            if (sameSystem) {
                voicePublisher.publish(new AiVoxResponseEvent(
                        StringUtls.localizedResponse("handler.pistonMode.sameSystem", targetStation)
                ));
            } else {
                String prompt = StringUtls.localizedResponse("handler.pistonMode.undockedRoute", targetStation, targetSystem);
                String plotted = routePlotterFunction.plotRouteAnd(prompt, targetSystem);
                voicePublisher.publish(new AiVoxResponseEvent(plotted != null && !plotted.isBlank() ? plotted : prompt));
            }

            // PT-3 arrival target placeholder
            arrivalTargetFunction.target(targetSystem, targetStation);
        } else {
            // Conditions not met: do not plot route, guide user via voice
            voicePublisher.publish(new AiVoxResponseEvent(
                    StringUtls.localizedResponse("handler.pistonMode.routeSkipped", targetStation, targetSystem)
            ));
        }
    }

    private boolean isStationA(DockedEvent event, PistonModeEntry state) {
        if (state.stationAMarketId() != 0 && event.getMarketID() != 0) {
            return event.getMarketID() == state.stationAMarketId();
        }
        return isStationMatch(event.getStationName(), event.getStarSystem(), state.stationAName(), state.stationASystem());
    }

    private boolean isStationB(DockedEvent event, PistonModeEntry state) {
        if (state.stationBMarketId() != 0 && event.getMarketID() != 0) {
            return event.getMarketID() == state.stationBMarketId();
        }
        return isStationMatch(event.getStationName(), event.getStarSystem(), state.stationBName(), state.stationBSystem());
    }

    private boolean isUndockedFromA(UndockedEvent event, PistonModeEntry state) {
        if (state.stationAMarketId() != 0 && event.getMarketID() != 0) {
            return event.getMarketID() == state.stationAMarketId();
        }
        String currentSystem = currentStarSystemSupplier.get();
        return isStationMatch(event.getStationName(), currentSystem, state.stationAName(), state.stationASystem());
    }

    private boolean isUndockedFromB(UndockedEvent event, PistonModeEntry state) {
        if (state.stationBMarketId() != 0 && event.getMarketID() != 0) {
            return event.getMarketID() == state.stationBMarketId();
        }
        String currentSystem = currentStarSystemSupplier.get();
        return isStationMatch(event.getStationName(), currentSystem, state.stationBName(), state.stationBSystem());
    }

    private boolean isDockedAtStation(long expectedMarketId, String expectedStation, String expectedSystem) {
        long curMarketId = dockedMarketIdSupplier.get();
        if (expectedMarketId != 0 && curMarketId != 0) {
            return curMarketId == expectedMarketId;
        }
        String currentSystem = currentStarSystemSupplier.get();
        return isStationMatch(expectedStation, currentSystem, expectedStation, expectedSystem);
    }

    private boolean isStationMatch(String stn1, String sys1, String stn2, String sys2) {
        if (stn1 == null || stn2 == null) {
            return false;
        }
        if (!stn1.trim().equalsIgnoreCase(stn2.trim())) {
            return false;
        }
        if (sys1 != null && sys2 != null && !sys1.trim().equalsIgnoreCase(sys2.trim())) {
            return false;
        }
        return true;
    }

    private boolean isSameSystem(String currentSystem, String targetSystem, Long currentSystemAddress, Long targetSystemAddress) {
        if (currentSystemAddress != null && targetSystemAddress != null && currentSystemAddress != 0 && targetSystemAddress != 0) {
            return currentSystemAddress.equals(targetSystemAddress);
        }
        if (currentSystem != null && targetSystem != null) {
            return currentSystem.trim().equalsIgnoreCase(targetSystem.trim());
        }
        return false;
    }

    private void saveState(
            boolean active, String startType,
            String stnAName, String stnASystem, long stnASystemAddress, long stnAMarketId,
            String stnBName, String stnBSystem, long stnBSystemAddress, long stnBMarketId,
            String commodity, String updatedAt) {
        try {
            Database.withDao(PistonModeDao.class, dao -> {
                dao.saveState(active, startType,
                        stnAName, stnASystem, stnASystemAddress, stnAMarketId,
                        stnBName, stnBSystem, stnBSystemAddress, stnBMarketId,
                        commodity, updatedAt);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to save piston_mode state: {}", e.getMessage(), e);
        }
    }

    private void updateStationAMarketId(long marketId, long systemAddress, String updatedAt) {
        try {
            Database.withDao(PistonModeDao.class, dao -> {
                dao.updateStationAMarketId(marketId, systemAddress, updatedAt);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to update station A marketId: {}", e.getMessage(), e);
        }
    }

    private void updateStationBMarketId(long marketId, long systemAddress, String updatedAt) {
        try {
            Database.withDao(PistonModeDao.class, dao -> {
                dao.updateStationBMarketId(marketId, systemAddress, updatedAt);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to update station B marketId: {}", e.getMessage(), e);
        }
    }
}
