package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.IntelActionContext;
import elite.intel.ai.brain.actions.handlers.commands.IntelCommand;
import elite.intel.ai.brain.actions.handlers.commands.RegisterCommand;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.RankedModuleEngineer;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.LocationManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.db.managers.ReminderManager;
import elite.intel.eventbus.GameEventBus;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.engineers.EngineerDirectory;
import elite.intel.gameapi.engineers.EngineerDirectory.EngineerInfo;
import elite.intel.gameapi.engineers.EngineerModuleMatcher;
import elite.intel.gameapi.inputs.RoutePlotter;
import elite.intel.i18n.Language;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.session.SystemSession;
import elite.intel.util.StringUtls;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Built-in command to plot a course to an engineer's base (EG-4).
 */
@RegisterCommand
public final class NavigateToEngineerCommand implements IntelCommand {

    public static final String ID = "navigate_to_engineer";

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

    private final BooleanSupplier inMainShipChecker;
    private final Supplier<String> currentStarSystemSupplier;
    private final Supplier<LocationDao.Coordinates> currentCoordinatesSupplier;
    private final Supplier<Optional<LatestDisplay>> latestDisplaySupplier;
    private final EngineerDirectory engineerDirectory;
    private final ReminderSetter reminderSetter;
    private final RoutePlotterFunction routePlotterFunction;
    private final VoicePublisher voicePublisher;

    public NavigateToEngineerCommand() {
        this(
                () -> Status.getInstance().isInMainShip(),
                () -> PlayerSession.getInstance().getPrimaryStarName(),
                () -> LocationManager.getInstance().getGalacticCoordinates(),
                () -> QueryResultDisplayManager.getInstance().getLatest(),
                EngineerDirectory.getInstance(),
                (text, system, station, contact) -> ReminderManager.getInstance().setReminder(text, system, station, contact),
                (answer, dest) -> new RoutePlotter().plotRouteAnd(answer, dest),
                GameEventBus::publish
        );
    }

    NavigateToEngineerCommand(
            BooleanSupplier inMainShipChecker,
            Supplier<String> currentStarSystemSupplier,
            Supplier<LocationDao.Coordinates> currentCoordinatesSupplier,
            Supplier<Optional<LatestDisplay>> latestDisplaySupplier,
            EngineerDirectory engineerDirectory,
            ReminderSetter reminderSetter,
            RoutePlotterFunction routePlotterFunction,
            VoicePublisher voicePublisher) {
        this.inMainShipChecker = Objects.requireNonNull(inMainShipChecker, "inMainShipChecker");
        this.currentStarSystemSupplier = Objects.requireNonNull(currentStarSystemSupplier, "currentStarSystemSupplier");
        this.currentCoordinatesSupplier = Objects.requireNonNull(currentCoordinatesSupplier, "currentCoordinatesSupplier");
        this.latestDisplaySupplier = Objects.requireNonNull(latestDisplaySupplier, "latestDisplaySupplier");
        this.engineerDirectory = Objects.requireNonNull(engineerDirectory, "engineerDirectory");
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
        return "Plot a course to an engineer's station or base.";
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
                        "name",
                        "string",
                        false,
                        "Engineer name to navigate to.",
                        null,
                        "Engineer name"
                ),
                new ActionParameterSpec(
                        "rank",
                        "number",
                        false,
                        "Candidate rank (1-based index from displayed engineer cards).",
                        List.of("1", "2", "3"),
                        "Candidate rank"
                ),
                new ActionParameterSpec(
                        "module",
                        "string",
                        false,
                        "Module name to find the best engineer for.",
                        null,
                        "Module name"
                )
        );
    }

    @Override
    public String execute(JsonObject params, String responseText) {
        boolean isGui = params != null && params.has("source")
                && "gui".equalsIgnoreCase(params.get("source").getAsString());

        // 1. Ship check
        if (!inMainShipChecker.getAsBoolean()) {
            String answer = StringUtls.localizedResponse("handler.navigateToEngineer.notInMainShip");
            return returnOrVoice(answer, isGui);
        }

        // 2. Resolve destination engineer
        DestinationResult resolved = resolveTargetEngineer(params, responseText);
        if (resolved.errorResponseKey() != null) {
            String answer = resolved.errorArg() != null
                    ? StringUtls.localizedResponse(resolved.errorResponseKey(), resolved.errorArg())
                    : StringUtls.localizedResponse(resolved.errorResponseKey());
            return returnOrVoice(answer, isGui);
        }

        EngineerInfo target = resolved.engineer();
        if (target == null) {
            String answer = StringUtls.localizedResponse("handler.navigateToEngineer.unknownEngineer");
            return returnOrVoice(answer, isGui);
        }

        String targetSystem = target.system();
        String stationName = target.base();
        String bodyName = target.body();

        // 3. Same system check
        String currentSystem = currentStarSystemSupplier.get();
        boolean sameSystem = currentSystem != null && targetSystem != null
                && currentSystem.trim().equalsIgnoreCase(targetSystem.trim());

        Language lang = SystemSession.getInstance().getLanguage();
        String spokenTargetName = engineerDirectory.spokenName(target, lang);
        String displayTargetName = engineerDirectory.displayName(target, lang);

        // 4. Set reminder (always set, even if in the same system)
        String reminderMessage = StringUtls.localizedResponse("handler.navigateToEngineer.reminder", displayTargetName, stationName, targetSystem);
        reminderSetter.setReminder(reminderMessage, targetSystem, stationName, null);

        if (sameSystem) {
            String answer;
            if (bodyName != null && !bodyName.isBlank()) {
                answer = StringUtls.localizedResponse("handler.navigateToEngineer.sameSystem", spokenTargetName, stationName, bodyName);
            } else {
                answer = StringUtls.localizedResponse("handler.navigateToEngineer.sameSystemNoBody", spokenTargetName, stationName);
            }
            return returnOrVoice(answer, isGui);
        }

        // 5. Different system -> route plotting
        String message;
        if (target.permitRequired()) {
            message = StringUtls.localizedResponse("handler.navigateToEngineer.successPermitRequired", spokenTargetName, targetSystem, stationName);
        } else {
            message = StringUtls.localizedResponse("handler.navigateToEngineer.success", spokenTargetName, targetSystem, stationName);
        }

        String plottedAnswer = routePlotterFunction.plotRouteAnd(message, targetSystem);
        String finalAnswer = (plottedAnswer != null && !plottedAnswer.isBlank()) ? plottedAnswer : message;
        return returnOrVoice(finalAnswer, isGui);
    }

    private record DestinationResult(EngineerInfo engineer, String errorResponseKey, Object errorArg) {
        static DestinationResult ok(EngineerInfo eng) {
            return new DestinationResult(eng, null, null);
        }
        static DestinationResult error(String errorResponseKey) {
            return new DestinationResult(null, errorResponseKey, null);
        }
        static DestinationResult error(String errorResponseKey, Object arg) {
            return new DestinationResult(null, errorResponseKey, arg);
        }
    }

    private DestinationResult resolveTargetEngineer(JsonObject params, String utterance) {
        // Priority 1: params.name or engineer name mentioned in utterance
        if (params != null && params.has("name") && !params.get("name").isJsonNull()) {
            String name = params.get("name").getAsString().trim();
            if (!name.isBlank()) {
                Optional<EngineerInfo> engOpt = engineerDirectory.findByName(name);
                if (engOpt.isPresent()) {
                    return DestinationResult.ok(engOpt.get());
                }
            }
        }
        if (utterance != null && !utterance.isBlank()) {
            Optional<EngineerInfo> mentionedOpt = engineerDirectory.findMentionedIn(utterance);
            if (mentionedOpt.isPresent()) {
                return DestinationResult.ok(mentionedOpt.get());
            }
        }

        // Priority 2: params.rank present -> rank-th engineer from currently displayed cards
        if (params != null && params.has("rank") && !params.get("rank").isJsonNull()) {
            JsonElement rankElem = params.get("rank");
            int rank;
            try {
                double val = rankElem.getAsDouble();
                if (val < 1 || val != Math.floor(val)) {
                    return DestinationResult.error("handler.navigateToEngineer.invalidRank", rankElem.getAsString());
                }
                rank = (int) val;
            } catch (Exception e) {
                return DestinationResult.error("handler.navigateToEngineer.invalidRank", rankElem.getAsString());
            }

            Optional<EngineersDisplayDto> displayedOpt = getActiveEngineersDisplay();
            if (displayedOpt.isEmpty() || displayedOpt.get().engineerNames() == null || displayedOpt.get().engineerNames().isEmpty()) {
                return DestinationResult.error("handler.navigateToEngineer.noDisplayedEngineers");
            }

            List<String> names = displayedOpt.get().engineerNames();
            if (rank < 1 || rank > names.size()) {
                return DestinationResult.error("handler.navigateToEngineer.invalidRank", rank);
            }

            String targetName = names.get(rank - 1);
            Optional<EngineerInfo> engOpt = engineerDirectory.findByName(targetName);
            if (engOpt.isPresent()) {
                return DestinationResult.ok(engOpt.get());
            }
        }

        // Priority 3: module name in utterance or params.module -> 1st in EG-2 order
        String paramModule = null;
        if (params != null && params.has("module") && !params.get("module").isJsonNull()) {
            paramModule = params.get("module").getAsString().trim();
        }
        Optional<String> matchedModule = Optional.empty();
        if (paramModule != null && !paramModule.isBlank()) {
            matchedModule = EngineerModuleMatcher.getInstance().matchModule(paramModule);
            if (matchedModule.isEmpty() && utterance != null && !utterance.isBlank()) {
                matchedModule = EngineerModuleMatcher.getInstance().findMentionedModule(utterance);
            }
        } else if (utterance != null && !utterance.isBlank()) {
            matchedModule = EngineerModuleMatcher.getInstance().findMentionedModule(utterance);
        }

        if (matchedModule.isPresent()) {
            LocationDao.Coordinates here = currentCoordinatesSupplier.get();
            List<RankedModuleEngineer> ranked = EngineerQuery.findEngineersForModule(matchedModule.get(), here, engineerDirectory);
            if (!ranked.isEmpty()) {
                return DestinationResult.ok(ranked.get(0).engineer());
            }
        }

        // Priority 4: currently displayed card is exactly 1 engineer
        Optional<EngineersDisplayDto> displayedOpt = getActiveEngineersDisplay();
        if (displayedOpt.isPresent() && displayedOpt.get().engineerNames() != null && displayedOpt.get().engineerNames().size() == 1) {
            String singleName = displayedOpt.get().engineerNames().get(0);
            Optional<EngineerInfo> engOpt = engineerDirectory.findByName(singleName);
            if (engOpt.isPresent()) {
                return DestinationResult.ok(engOpt.get());
            }
        }

        // Priority 5: otherwise unknown
        return DestinationResult.error("handler.navigateToEngineer.unknownEngineer");
    }

    private Optional<EngineersDisplayDto> getActiveEngineersDisplay() {
        Optional<LatestDisplay> latestOpt = latestDisplaySupplier.get();
        if (latestOpt.isEmpty()) {
            return Optional.empty();
        }
        LatestDisplay latest = latestOpt.get();
        Instant now = Instant.now();
        if (latest.savedAt() == null || Duration.between(latest.savedAt(), now).getSeconds() > 36000) {
            return Optional.empty();
        }
        if (!latest.isEngineers() || latest.engineers() == null) {
            return Optional.empty();
        }
        return Optional.of(latest.engineers());
    }

    private String returnOrVoice(String answer, boolean isGui) {
        if (isGui) {
            voicePublisher.publish(new AiVoxResponseEvent(answer));
            return null;
        }
        return answer;
    }
}
