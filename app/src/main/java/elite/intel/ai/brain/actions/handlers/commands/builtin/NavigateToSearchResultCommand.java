package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.db.managers.ReminderManager;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.inputs.RoutePlotter;
import elite.intel.session.Status;
import elite.intel.util.StringUtls;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Built-in command to plot a course to the selected destination from the latest trade candidates
 * or nearest outfitting search result.
 */
@RegisterCommand
public final class NavigateToSearchResultCommand implements IntelCommand {

    public static final String ID = "navigate_to_search_result";

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

    private final Supplier<Optional<LatestDisplay>> latestDisplaySupplier;
    private final BooleanSupplier inMainShipChecker;
    private final ReminderSetter reminderSetter;
    private final RoutePlotterFunction routePlotterFunction;
    private final VoicePublisher voicePublisher;

    public NavigateToSearchResultCommand() {
        this(
                () -> QueryResultDisplayManager.getInstance().getLatest(),
                () -> Status.getInstance().isInMainShip(),
                (text, system, station, contact) -> ReminderManager.getInstance().setReminder(text, system, station, contact),
                (answer, dest) -> new RoutePlotter().plotRouteAnd(answer, dest),
                GameEventBus::publish
        );
    }

    NavigateToSearchResultCommand(
            Supplier<Optional<LatestDisplay>> latestDisplaySupplier,
            BooleanSupplier inMainShipChecker,
            ReminderSetter reminderSetter,
            RoutePlotterFunction routePlotterFunction,
            VoicePublisher voicePublisher) {
        this.latestDisplaySupplier = Objects.requireNonNull(latestDisplaySupplier, "latestDisplaySupplier");
        this.inMainShipChecker = Objects.requireNonNull(inMainShipChecker, "inMainShipChecker");
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
        return "Plot a route to the star system of the selected trade candidate or outfitting search result (rank and buy/sell leg).";
    }

    @Override
    public boolean isVisibleForLLM(Status status) {
        return status.isInMainShip();
    }

    @Override
    public boolean isAvailableIn(IntelActionContext context) {
        return true;
    }

    @Override
    public List<ActionParameterSpec> parameters() {
        return List.of(
                new ActionParameterSpec(
                        "rank",
                        "number",
                        false,
                        "Candidate rank (1 to 3, default: 1)",
                        List.of("1", "2", "3"),
                        "Candidate rank 1, 2, or 3"
                ),
                new ActionParameterSpec(
                        "leg",
                        "string",
                        false,
                        "Destination leg: 'buy' for purchase location, 'sell' for sale location (default: 'buy')",
                        List.of("buy", "sell"),
                        null,
                        List.of("buy", "sell")
                )
        );
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        boolean isGui = params != null && params.has("source")
                && "gui".equalsIgnoreCase(params.get("source").getAsString());

        // 1. Ship check
        if (!inMainShipChecker.getAsBoolean()) {
            String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.notInMainShip");
            return returnOrVoice(answer, isGui);
        }

        // 2. Latest search result check (within 10 hours)
        Optional<LatestDisplay> latestOpt = latestDisplaySupplier.get();
        if (latestOpt.isEmpty()) {
            String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.noRecentResult");
            return returnOrVoice(answer, isGui);
        }

        LatestDisplay latest = latestOpt.get();
        Instant now = Instant.now();
        if (latest.savedAt() == null || Duration.between(latest.savedAt(), now).getSeconds() > 36000) {
            String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.noRecentResult");
            return returnOrVoice(answer, isGui);
        }

        // 3. Parse and validate rank
        int rank = 1;
        if (params != null && params.has("rank") && !params.get("rank").isJsonNull()) {
            JsonElement rankElem = params.get("rank");
            try {
                double val = rankElem.getAsDouble();
                if (val != Math.floor(val)) {
                    String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.invalidRank", rankElem.getAsString());
                    return returnOrVoice(answer, isGui);
                }
                rank = (int) val;
            } catch (Exception e) {
                String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.invalidRank", rankElem.getAsString());
                return returnOrVoice(answer, isGui);
            }
        }

        // 4. Parse and validate leg
        String leg = "buy";
        if (params != null && params.has("leg") && !params.get("leg").isJsonNull()) {
            leg = params.get("leg").getAsString().trim().toLowerCase(Locale.ROOT);
            if (!"buy".equals(leg) && !"sell".equals(leg)) {
                String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.invalidLeg");
                return returnOrVoice(answer, isGui);
            }
        }

        // 5. Resolve target system and station based on query type
        String targetSystem;
        String targetStation;
        String message;

        if (latest.isOutfitting()) {
            if (rank != 1) {
                String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.invalidRank", rank);
                return returnOrVoice(answer, isGui);
            }
            OutfittingDataDto dto = latest.outfitting();
            if (dto == null || dto.starSystem() == null || dto.starSystem().isBlank()) {
                String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.noRecentResult");
                return returnOrVoice(answer, isGui);
            }
            targetSystem = dto.starSystem();
            targetStation = dto.stationName();
            message = StringUtls.localizedResponse("handler.navigateToSearchResult.outfitting", targetStation, targetSystem);
        } else if (latest.isTradeCandidates()) {
            TradeCandidatesDataDto dto = latest.tradeCandidates();
            if (dto == null || dto.candidates() == null || dto.candidates().isEmpty()) {
                String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.noRecentResult");
                return returnOrVoice(answer, isGui);
            }
            if (rank < 1 || rank > dto.candidates().size()) {
                String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.invalidRank", rank);
                return returnOrVoice(answer, isGui);
            }
            TradeCandidateDto candidate = dto.candidates().get(rank - 1);
            if ("buy".equals(leg)) {
                targetSystem = candidate.buySystem();
                targetStation = candidate.buyStation();
                message = StringUtls.localizedResponse("handler.navigateToSearchResult.tradeBuy", rank, targetStation, targetSystem);
            } else {
                targetSystem = candidate.sellSystem();
                targetStation = candidate.sellStation();
                message = StringUtls.localizedResponse("handler.navigateToSearchResult.tradeSell", rank, targetStation, targetSystem);
            }
        } else {
            String answer = StringUtls.localizedResponse("handler.navigateToSearchResult.noRecentResult");
            return returnOrVoice(answer, isGui);
        }

        // 6. Set reminder and plot route
        reminderSetter.setReminder(message, targetSystem, targetStation, null);
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
