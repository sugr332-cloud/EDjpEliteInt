package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.db.dao.EngineerProgressDao.EngineerProgressRecord;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.LocationManager;
import elite.intel.gameapi.engineers.EngineerDirectory;
import elite.intel.gameapi.engineers.EngineerDirectory.EngineerInfo;
import elite.intel.gameapi.engineers.EngineerDirectory.Specialty;
import elite.intel.gameapi.engineers.EngineerModuleMatcher;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.util.NavigationUtils;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Built-in query to answer questions about Elite Dangerous engineers by voice (EG-2).
 * Answers only; does not display UI cards, press keys, or plot routes.
 */
@RegisterQuery
public class EngineerQuery extends BaseQueryAnalyzer implements IntelQuery {

    private static final Logger log = LogManager.getLogger(EngineerQuery.class);

    public static final String ID = "query_engineer";

    @FunctionalInterface
    public interface CoordinatesResolver {
        LocationDao.Coordinates resolve();
    }

    private final CoordinatesResolver coordinatesResolver;

    public EngineerQuery() {
        this(() -> LocationManager.getInstance().getGalacticCoordinates());
    }

    public EngineerQuery(CoordinatesResolver coordinatesResolver) {
        this.coordinatesResolver = Objects.requireNonNull(coordinatesResolver);
    }

    public record ModuleEngineerDto(
            String name,
            String system,
            String base,
            Double distanceLy,
            int maxGrade,
            String progress,
            Integer rank,
            boolean permitRequired
    ) {}

    public record ProgressEngineerDto(
            String name,
            String progress,
            Integer rank,
            Integer rankProgress
    ) {}

    public record SpecialtyDto(
            String moduleOrDescription,
            int maxGrade
    ) {}

    public record DataDto(
            String type,
            String query,
            // For type='module'
            String moduleName,
            List<ModuleEngineerDto> moduleEngineers,
            // For type='engineer'
            String name,
            String engineerType,
            String system,
            String base,
            String body,
            Double distanceLy,
            String invite,
            String unlock,
            String referral,
            List<SpecialtyDto> specialties,
            String progress,
            Integer rank,
            Integer rankProgress,
            boolean permitRequired,
            String notes,
            // For type='progress'
            boolean onlyUnlocked,
            Map<String, List<ProgressEngineerDto>> progressGroups,
            // For type='unknown_module'
            String rawModule,
            String message
    ) implements ToYamlConvertable {
        @Override
        public String toYaml() {
            return YamlFactory.toYaml(this);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Answers questions about Elite Dangerous engineers, module upgrades, and player unlock progress.";
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        DataDto data = buildData(params, originalUserInput);
        String instructions = """
                Answer the user's question about Elite Dangerous engineers concisely and accurately based on the provided data.
                
                Data structure:
                - type: 'engineer', 'module', 'progress', or 'unknown_module'
                
                Rules:
                - Answer in the user's language concisely.
                - For type 'module': mention up to the top 3 engineers, including their maximum grade and progress status. For any engineer with permitRequired=true, explicitly mention that a system permit is required.
                - For type 'engineer': summarize the engineer's details (system, base, requirements, specialties, progress status).
                - For type 'progress': summarize the player's engineering progress by status group.
                - For type 'unknown_module': state that the requested module could not be identified.
                - Never attempt to set route, press keys, or display cards; this is a voice query response only.
                - Do not guess or extrapolate beyond the provided data.
                """;
        saveDisplayResult(data);
        return process(new AiDataStruct(instructions, data), originalUserInput);
    }

    public DataDto buildData(JsonObject params, String originalUserInput) {
        LocationDao.Coordinates here = null;
        try {
            here = coordinatesResolver.resolve();
        } catch (Exception e) {
            log.warn("Could not retrieve current galactic coordinates", e);
        }

        // 1. Check if an engineer is explicitly mentioned in the utterance
        Optional<EngineerInfo> mentionedEngineer = EngineerDirectory.getInstance().findMentionedIn(originalUserInput);
        if (mentionedEngineer.isPresent()) {
            return buildEngineerDetailData(mentionedEngineer.get(), here, originalUserInput);
        }

        // 2. Check if a module is found (from params.module or utterance)
        String paramModule = null;
        if (params != null && params.has("module") && !params.get("module").isJsonNull()) {
            paramModule = params.get("module").getAsString().trim();
        }

        Optional<String> matchedModule = Optional.empty();
        if (paramModule != null && !paramModule.isBlank()) {
            matchedModule = EngineerModuleMatcher.getInstance().matchModule(paramModule);
            if (matchedModule.isEmpty()) {
                matchedModule = EngineerModuleMatcher.getInstance().findMentionedModule(originalUserInput);
            }
        } else {
            matchedModule = EngineerModuleMatcher.getInstance().findMentionedModule(originalUserInput);
        }

        if (matchedModule.isPresent()) {
            return buildModuleEngineersData(matchedModule.get(), here, originalUserInput);
        }

        // 3. params.module is present but cannot be resolved -> unknown_module
        if (paramModule != null && !paramModule.isBlank()) {
            return new DataDto(
                    "unknown_module", originalUserInput,
                    null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, false, null,
                    false, null,
                    paramModule, "指定されたモジュールは判別できませんでした。"
            );
        }

        // 4. Progress list / general progress query
        return buildProgressData(originalUserInput);
    }

    private DataDto buildEngineerDetailData(EngineerInfo eng, LocationDao.Coordinates here, String query) {
        Double distance = calculateDistanceLy(here, eng.coords());

        Optional<EngineerProgressRecord> progOpt = EngineerProgressManager.getInstance().findByName(eng.name());
        String progress = progOpt.map(EngineerProgressRecord::progress).orElse("記録なし");
        Integer rank = progOpt.map(EngineerProgressRecord::rank).orElse(null);
        Integer rankProgress = progOpt.map(EngineerProgressRecord::rankProgress).orElse(null);

        List<SpecialtyDto> specs = new ArrayList<>();
        if (eng.specialties() != null) {
            for (Specialty sp : eng.specialties()) {
                specs.add(new SpecialtyDto(sp.module(), sp.maxGrade()));
            }
        }

        return new DataDto(
                "engineer", query,
                null, null,
                eng.name(), eng.type(), eng.system(), eng.base(), eng.body(),
                distance, eng.invite(), eng.unlock(), eng.referral(),
                specs, progress, rank, rankProgress, eng.permitRequired(), eng.notes(),
                false, null,
                null, null
        );
    }

    public record RankedModuleEngineer(
            EngineerInfo engineer,
            int maxGrade,
            Double distanceLy
    ) {}

    public static List<RankedModuleEngineer> findEngineersForModule(
            String moduleName,
            LocationDao.Coordinates here,
            EngineerDirectory dir) {
        if (moduleName == null || dir == null) {
            return Collections.emptyList();
        }
        List<RankedModuleEngineer> candidates = new ArrayList<>();
        for (EngineerInfo eng : dir.getShipEngineers()) {
            if (eng.specialties() == null) continue;
            for (Specialty sp : eng.specialties()) {
                if (moduleName.equalsIgnoreCase(sp.module())) {
                    Double dist = calculateDistanceLy(here, eng.coords());
                    candidates.add(new RankedModuleEngineer(eng, sp.maxGrade(), dist));
                    break;
                }
            }
        }

        // Sort: maxGrade desc, distanceLy asc (null distance goes to the end)
        candidates.sort((a, b) -> {
            if (b.maxGrade() != a.maxGrade()) {
                return Integer.compare(b.maxGrade(), a.maxGrade());
            }
            if (a.distanceLy() == null && b.distanceLy() == null) return 0;
            if (a.distanceLy() == null) return 1;
            if (b.distanceLy() == null) return -1;
            return Double.compare(a.distanceLy(), b.distanceLy());
        });

        return candidates;
    }

    private DataDto buildModuleEngineersData(String moduleName, LocationDao.Coordinates here, String query) {
        EngineerDirectory dir = EngineerDirectory.getInstance();
        EngineerProgressManager pm = EngineerProgressManager.getInstance();

        List<RankedModuleEngineer> ranked = findEngineersForModule(moduleName, here, dir);

        List<ModuleEngineerDto> candidates = new ArrayList<>();
        for (RankedModuleEngineer item : ranked) {
            EngineerInfo eng = item.engineer();
            Optional<EngineerProgressRecord> prog = pm.findByName(eng.name());
            String progressStr = prog.map(EngineerProgressRecord::progress).orElse("記録なし");
            Integer rank = prog.map(EngineerProgressRecord::rank).orElse(null);

            candidates.add(new ModuleEngineerDto(
                    eng.name(),
                    eng.system(),
                    eng.base(),
                    item.distanceLy(),
                    item.maxGrade(),
                    progressStr,
                    rank,
                    eng.permitRequired()
            ));
        }

        // Limit to 5
        List<ModuleEngineerDto> limited = candidates.stream().limit(5).toList();

        return new DataDto(
                "module", query,
                moduleName, limited,
                null, null, null, null, null, null, null, null, null, null, null, null, null, false, null,
                false, null,
                null, null
        );
    }

    private DataDto buildProgressData(String query) {
        String norm = query != null ? Normalizer.normalize(query, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT) : "";
        boolean onlyUnlocked = norm.contains("開放済み") || norm.contains("アンロック") || norm.contains("unlocked");

        List<EngineerProgressRecord> allRecords = EngineerProgressManager.getInstance().getAll();
        Map<String, List<ProgressEngineerDto>> groups = new LinkedHashMap<>();

        for (EngineerProgressRecord rec : allRecords) {
            if (onlyUnlocked && !"Unlocked".equalsIgnoreCase(rec.progress())) {
                continue;
            }
            String groupKey = rec.progress() != null ? rec.progress() : "Unknown";
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(
                    new ProgressEngineerDto(rec.displayName(), rec.progress(), rec.rank(), rec.rankProgress())
            );
        }

        return new DataDto(
                "progress", query,
                null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, false, null,
                onlyUnlocked, groups,
                null, null
        );
    }

    public static Double calculateDistanceLy(LocationDao.Coordinates here, EngineerDirectory.Coords coords) {
        if (here == null || coords == null) {
            return null;
        }
        double dist = NavigationUtils.calculateGalacticDistance(
                here.x(), here.y(), here.z(),
                coords.x(), coords.y(), coords.z()
        );
        return Math.round(dist * 10.0) / 10.0;
    }

    private void saveDisplayResult(DataDto data) {
        if (data == null || "unknown_module".equals(data.type())) {
            return;
        }
        QueryResultDisplayManager displayMgr = QueryResultDisplayManager.getInstance();
        if ("module".equals(data.type())) {
            if (data.moduleEngineers() != null && !data.moduleEngineers().isEmpty()) {
                List<String> names = data.moduleEngineers().stream()
                        .map(ModuleEngineerDto::name)
                        .toList();
                displayMgr.saveEngineers(new EngineersDisplayDto("module", data.moduleName(), names));
            }
        } else if ("engineer".equals(data.type())) {
            if (data.name() != null && !data.name().isBlank()) {
                displayMgr.saveEngineers(new EngineersDisplayDto("engineer", null, List.of(data.name())));
            }
        } else if ("progress".equals(data.type())) {
            List<String> sortedNames = extractSortedProgressEngineers(data.onlyUnlocked());
            if (!sortedNames.isEmpty()) {
                displayMgr.saveEngineers(new EngineersDisplayDto("progress", null, sortedNames));
            } else {
                displayMgr.clearEngineers();
            }
        }
    }

    private List<String> extractSortedProgressEngineers(boolean onlyUnlocked) {
        List<EngineerProgressRecord> allRecords = EngineerProgressManager.getInstance().getAll();
        if (allRecords == null || allRecords.isEmpty()) {
            return Collections.emptyList();
        }
        return allRecords.stream()
                .filter(rec -> !onlyUnlocked || "Unlocked".equalsIgnoreCase(rec.progress()))
                .sorted(Comparator.comparingInt((EngineerProgressRecord r) -> progressPriority(r.progress()))
                        .thenComparing(EngineerProgressRecord::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(EngineerProgressRecord::displayName)
                .toList();
    }

    private int progressPriority(String progress) {
        if (progress == null) return 6;
        return switch (progress.toLowerCase(Locale.ROOT)) {
            case "unlocked" -> 1;
            case "acquainted" -> 2;
            case "invited" -> 3;
            case "known" -> 4;
            case "barred" -> 5;
            default -> 6;
        };
    }
}
