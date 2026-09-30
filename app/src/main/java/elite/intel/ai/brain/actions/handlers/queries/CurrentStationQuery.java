package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.db.managers.DockingHistoryManager;
import elite.intel.session.DockedMarket;
import elite.intel.session.PlayerSession;
import elite.intel.session.Status;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Built-in query to report the currently docked station or last visited station without plotting a course.
 */
@RegisterQuery
public class CurrentStationQuery extends BaseQueryAnalyzer implements IntelQuery {

    public static final String ID = "query_current_station";

    private final BooleanSupplier isDockedSupplier;
    private final LongSupplier dockedMarketIdSupplier;
    private final Supplier<String> dockedStationNameSupplier;
    private final Supplier<String> currentStarSystemSupplier;
    private final Function<Integer, Optional<DockingHistoryEntry>> historyLookup;

    public CurrentStationQuery() {
        this(
                () -> Status.getInstance().isDocked(),
                () -> DockedMarket.getInstance().marketId(),
                () -> DockedMarket.getInstance().stationName(),
                () -> PlayerSession.getInstance().getPrimaryStarName(),
                back -> DockingHistoryManager.getInstance().getNthPreviousStation(back)
        );
    }

    CurrentStationQuery(
            BooleanSupplier isDockedSupplier,
            LongSupplier dockedMarketIdSupplier,
            Supplier<String> dockedStationNameSupplier,
            Supplier<String> currentStarSystemSupplier,
            Function<Integer, Optional<DockingHistoryEntry>> historyLookup) {
        this.isDockedSupplier = Objects.requireNonNull(isDockedSupplier, "isDockedSupplier");
        this.dockedMarketIdSupplier = Objects.requireNonNull(dockedMarketIdSupplier, "dockedMarketIdSupplier");
        this.dockedStationNameSupplier = Objects.requireNonNull(dockedStationNameSupplier, "dockedStationNameSupplier");
        this.currentStarSystemSupplier = Objects.requireNonNull(currentStarSystemSupplier, "currentStarSystemSupplier");
        this.historyLookup = Objects.requireNonNull(historyLookup, "historyLookup");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Report the station currently docked at, or the last station docked at if currently undocked.";
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        boolean isDocked = isDockedSupplier.getAsBoolean();
        DataDto data;

        if (isDocked) {
            long marketId = dockedMarketIdSupplier.getAsLong();
            String name = dockedStationNameSupplier.get();
            Optional<DockingHistoryEntry> latestOpt = historyLookup.apply(0);
            if (latestOpt.isPresent() && marketId > 0 && latestOpt.get().marketId() == marketId) {
                DockingHistoryEntry h = latestOpt.get();
                data = new DataDto("docked", true, name != null ? name : h.stationName(), h.starSystem(), h.stationType(), h.distFromStarLS());
            } else {
                String sys = currentStarSystemSupplier.get();
                data = new DataDto("docked", true, name, sys, null, null);
            }
        } else {
            Optional<DockingHistoryEntry> latestOpt = historyLookup.apply(0);
            if (latestOpt.isPresent()) {
                DockingHistoryEntry h = latestOpt.get();
                data = new DataDto("undocked", false, h.stationName(), h.starSystem(), h.stationType(), h.distFromStarLS());
            } else {
                data = new DataDto("no_record", false, null, null, null, null);
            }
        }

        String instructions = """
                Answer the user's question about their current station or last docked station.
                
                Status values:
                - docked: currently docked at a station
                - undocked: not docked; last docked station details are provided
                - no_record: not docked and no past docking history exists
                
                Data fields:
                - isDocked: true if currently docked, false otherwise
                - stationName: name of the station
                - starSystem: star system of the station
                - stationType: type of the station (may be null)
                - distFromStarLS: distance from the arrival star in light seconds (may be null)
                
                Rules:
                - If status is 'docked', inform the user that they are docked at stationName (in starSystem system). Include stationType and distFromStarLS if present.
                - If status is 'undocked', inform the user that they are not docked, but were last docked at stationName (in starSystem system).
                - If status is 'no_record', tell the user that they are not docked and have no docking history recorded.
                - Never modify route or plot navigation.
                """;

        return process(new AiDataStruct(instructions, data), originalUserInput);
    }

    record DataDto(
            String status,
            boolean isDocked,
            String stationName,
            String starSystem,
            String stationType,
            Double distFromStarLS
    ) implements ToYamlConvertable {
        @Override
        public String toYaml() {
            return YamlFactory.toYaml(this);
        }
    }
}
