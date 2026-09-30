package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.db.managers.DockingHistoryManager;
import elite.intel.session.DockedMarket;
import elite.intel.session.Status;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Built-in query to report the previous station from docking history without plotting a course.
 */
@RegisterQuery
public class PreviousStationQuery extends BaseQueryAnalyzer implements IntelQuery {

    public static final String ID = "query_previous_station";

    @FunctionalInterface
    public interface PreviousStationResolver {
        Optional<DockingHistoryEntry> resolve(int back, boolean isDocked, long currentDockedMarketId);
    }

    private final PreviousStationResolver previousStationResolver;
    private final BooleanSupplier isDockedSupplier;
    private final LongSupplier dockedMarketIdSupplier;

    public PreviousStationQuery() {
        this(
                (back, isDocked, marketId) -> DockingHistoryManager.getInstance().getPreviousStation(back, isDocked, marketId),
                () -> Status.getInstance().isDocked(),
                () -> DockedMarket.getInstance().marketId()
        );
    }

    PreviousStationQuery(
            PreviousStationResolver previousStationResolver,
            BooleanSupplier isDockedSupplier,
            LongSupplier dockedMarketIdSupplier) {
        this.previousStationResolver = Objects.requireNonNull(previousStationResolver, "previousStationResolver");
        this.isDockedSupplier = Objects.requireNonNull(isDockedSupplier, "isDockedSupplier");
        this.dockedMarketIdSupplier = Objects.requireNonNull(dockedMarketIdSupplier, "dockedMarketIdSupplier");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Report the previous station the commander was docked at from docking history (station name, star system, station type, and distance from arrival star).";
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        boolean isDocked = isDockedSupplier.getAsBoolean();
        long marketId = isDocked ? dockedMarketIdSupplier.getAsLong() : 0L;
        Optional<DockingHistoryEntry> entryOpt = previousStationResolver.resolve(1, isDocked, marketId);

        String instructions = """
                Answer the user's question about the previous station from docking history.
                
                Status values:
                - ok: previous station details are available
                - no_record: no docking record found in history
                
                Data fields:
                - stationName: station name
                - starSystem: star system
                - stationType: station type
                - distFromStarLS: distance from arrival star in light seconds
                
                Rules:
                - If status is 'no_record', tell the user that there is no docking record available.
                - If status is 'ok', report the station name, star system, station type, and distance from arrival star in light seconds.
                - Never modify route or plot navigation.
                """;

        DataDto data;
        if (entryOpt.isPresent()) {
            DockingHistoryEntry entry = entryOpt.get();
            data = new DataDto("ok", entry.stationName(), entry.starSystem(), entry.stationType(), entry.distFromStarLS());
        } else {
            data = new DataDto("no_record", null, null, null, null);
        }

        return process(new AiDataStruct(instructions, data), originalUserInput);
    }

    record DataDto(
            String status,
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
