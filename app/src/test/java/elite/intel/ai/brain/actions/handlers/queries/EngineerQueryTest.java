package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.DataDto;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.ModuleEngineerDto;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EngineerQueryTest {

    private final EngineerProgressManager progressManager = EngineerProgressManager.getInstance();
    private final QueryResultDisplayManager displayManager = QueryResultDisplayManager.getInstance();

    // Mock coordinates near Sol (0, 0, 0)
    private final LocationDao.Coordinates mockSol = new LocationDao.Coordinates("Sol", 0.0, 0.0, 0.0);

    @BeforeEach
    void setUp() {
        progressManager.clear();
        displayManager.clearAll();
    }

    @AfterEach
    void tearDown() {
        progressManager.clear();
        displayManager.clearAll();
    }

    @Test
    void moduleQueryOrdersByMaxGradeDescDistanceAscAndLimitsToFive() {
        // Set some progress in DB
        progressManager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Elvira Martuuk", 300001L, "Unlocked", 3, 20, "2026-10-01T10:00:00Z");

        EngineerQuery query = new EngineerQuery(() -> mockSol);

        JsonObject params = new JsonObject();
        params.addProperty("module", "Frame Shift Drive");

        DataDto data = query.buildData(params, "FSDのエンジニアは誰？");
        assertEquals("module", data.type());
        assertEquals("Frame Shift Drive", data.moduleName());
        assertNotNull(data.moduleEngineers());
        assertFalse(data.moduleEngineers().isEmpty());
        assertTrue(data.moduleEngineers().size() <= 5, "Must limit to 5 engineers");

        List<ModuleEngineerDto> list = data.moduleEngineers();
        for (int i = 0; i < list.size() - 1; i++) {
            ModuleEngineerDto curr = list.get(i);
            ModuleEngineerDto next = list.get(i + 1);

            if (curr.maxGrade() == next.maxGrade()) {
                if (curr.distanceLy() != null && next.distanceLy() != null) {
                    assertTrue(curr.distanceLy() <= next.distanceLy(),
                            "Same grade must be sorted by distance ascending: " + curr.distanceLy() + " vs " + next.distanceLy());
                }
            } else {
                assertTrue(curr.maxGrade() > next.maxGrade(),
                        "Must be sorted by maxGrade descending: " + curr.maxGrade() + " vs " + next.maxGrade());
            }
        }
    }

    @Test
    void engineerQueryReturnsFullEngineerDetails() {
        progressManager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");

        EngineerQuery query = new EngineerQuery(() -> mockSol);

        DataDto data = query.buildData(new JsonObject(), "フェリシティ・ファーシーアの開放条件を教えて");
        assertEquals("engineer", data.type());
        assertEquals("Felicity Farseer", data.name());
        assertEquals("ship", data.engineerType());
        assertEquals("Deciat", data.system());
        assertEquals("Farseer Inc", data.base());
        assertNotNull(data.distanceLy());
        assertTrue(data.distanceLy() > 0);
        assertEquals("Unlocked", data.progress());
        assertEquals(5, data.rank());
        assertEquals(0, data.rankProgress());
        assertFalse(data.permitRequired());
        assertNotNull(data.specialties());
        assertFalse(data.specialties().isEmpty());
    }

    @Test
    void progressQueryReturnsAllProgressGroupsAndFiltersOnlyUnlocked() {
        progressManager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Elvira Martuuk", 300001L, "Invited", null, null, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Marco Qwent", 300015L, "Known", null, null, "2026-10-01T10:00:00Z");

        EngineerQuery query = new EngineerQuery(() -> mockSol);

        // General progress: all groups
        DataDto generalData = query.buildData(new JsonObject(), "エンジニアの進捗を教えて");
        assertEquals("progress", generalData.type());
        assertFalse(generalData.onlyUnlocked());
        assertNotNull(generalData.progressGroups());
        assertTrue(generalData.progressGroups().containsKey("Unlocked"));
        assertTrue(generalData.progressGroups().containsKey("Invited"));
        assertTrue(generalData.progressGroups().containsKey("Known"));

        // Only unlocked
        DataDto unlockedData = query.buildData(new JsonObject(), "開放済みのエンジニアは？");
        assertEquals("progress", unlockedData.type());
        assertTrue(unlockedData.onlyUnlocked());
        assertNotNull(unlockedData.progressGroups());
        assertTrue(unlockedData.progressGroups().containsKey("Unlocked"));
        assertFalse(unlockedData.progressGroups().containsKey("Invited"));
        assertFalse(unlockedData.progressGroups().containsKey("Known"));
    }

    @Test
    void distanceIsNullWhenCoordinatesUnavailable() {
        EngineerQuery query = new EngineerQuery(() -> null);

        DataDto data = query.buildData(new JsonObject(), "フェリシティ・ファーシーアはどこ？");
        assertEquals("engineer", data.type());
        assertEquals("Felicity Farseer", data.name());
        assertNull(data.distanceLy(), "Distance must be null when coordinates unavailable");
    }

    @Test
    void unknownModuleWhenParamModuleCannotBeResolvedEvenIfProgressKeywordsAbsent() {
        EngineerQuery query = new EngineerQuery(() -> mockSol);

        JsonObject params = new JsonObject();
        params.addProperty("module", "NonExistentSuperWeapon");

        DataDto data = query.buildData(params, "エンジニアを教えて");
        assertEquals("unknown_module", data.type());
        assertEquals("NonExistentSuperWeapon", data.rawModule());
        assertNotNull(data.message());
    }

    @Test
    void unknownModuleWhenParamModuleCannotBeResolvedWithProgressKeywords() {
        EngineerQuery query = new EngineerQuery(() -> mockSol);

        JsonObject params = new JsonObject();
        params.addProperty("module", "NonExistentSuperWeapon");

        DataDto data = query.buildData(params, "エンジニアの一覧を教えて");
        assertEquals("unknown_module", data.type(), "unknown_module must take precedence over progress listing");
        assertEquals("NonExistentSuperWeapon", data.rawModule());
    }

    private EngineerQuery createTestQuery() {
        return new EngineerQuery(() -> mockSol) {
            @Override
            protected JsonObject process(elite.intel.ai.brain.actions.handlers.queries.struct.AiData struct, String userInput) {
                JsonObject res = new JsonObject();
                res.addProperty("text_to_speech_response", "mock_response");
                return res;
            }
        };
    }

    @Test
    void cardEngineersDisplaySavedForModule() throws Exception {
        EngineerQuery query = createTestQuery();
        JsonObject params = new JsonObject();
        params.addProperty("module", "Frame Shift Drive");

        query.handle("query_engineer", params, "FSDのエンジニアは？");

        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        assertTrue(latestOpt.get().isEngineers());

        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("module", dto.queryKind());
        assertEquals("Frame Shift Drive", dto.moduleName());
        assertNotNull(dto.engineerNames());
        assertTrue(dto.engineerNames().size() <= 5);
        assertTrue(dto.engineerNames().contains("Felicity Farseer"));
        assertTrue(dto.engineerNames().contains("Elvira Martuuk"));
    }

    @Test
    void cardEngineersDisplaySavedForSingleEngineer() throws Exception {
        EngineerQuery query = createTestQuery();
        JsonObject params = new JsonObject();

        query.handle("query_engineer", params, "フェリシティ・ファーシーアはどこ？");

        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        assertTrue(latestOpt.get().isEngineers());

        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("engineer", dto.queryKind());
        assertNull(dto.moduleName());
        assertEquals(List.of("Felicity Farseer"), dto.engineerNames());
    }

    @Test
    void cardEngineersDisplaySavedForProgressWithCorrectOrdering() throws Exception {
        // Register engineers with various progress statuses
        progressManager.recordProgress("Zacariah Nemo", null, "Barred", null, null, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Bill Turner", null, "Known", null, null, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Chloe Sedesi", null, "Invited", null, null, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Didi Vatermann", null, "Acquainted", null, null, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Felicity Farseer", null, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");
        progressManager.recordProgress("Elvira Martuuk", null, "Unlocked", 3, 0, "2026-10-01T10:00:00Z");

        EngineerQuery query = createTestQuery();
        query.handle("query_engineer", new JsonObject(), "エンジニアの進捗は？");

        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("progress", dto.queryKind());

        List<String> names = dto.engineerNames();
        // Ordering: Unlocked (Elvira, Felicity) -> Acquainted (Didi) -> Invited (Chloe) -> Known (Bill) -> Barred (Zacariah)
        assertEquals("Elvira Martuuk", names.get(0));
        assertEquals("Felicity Farseer", names.get(1));
        assertEquals("Didi Vatermann", names.get(2));
        assertEquals("Chloe Sedesi", names.get(3));
        assertEquals("Bill Turner", names.get(4));
        assertEquals("Zacariah Nemo", names.get(5));
    }

    @Test
    void unknownModuleDoesNotOverwriteExistingDisplay() throws Exception {
        // 1. First display Felicity Farseer
        EngineerQuery query = createTestQuery();
        query.handle("query_engineer", new JsonObject(), "フェリシティ・ファーシーアはどこ？");

        Optional<LatestDisplay> beforeOpt = displayManager.getLatest();
        assertTrue(beforeOpt.isPresent());
        assertEquals("engineer", beforeOpt.get().engineers().queryKind());

        // 2. Query unknown module
        JsonObject params = new JsonObject();
        params.addProperty("module", "NonExistentUnknownModuleXYZ");
        query.handle("query_engineer", params, "未知のモジュール");

        // 3. Display must NOT be overwritten
        Optional<LatestDisplay> afterOpt = displayManager.getLatest();
        assertTrue(afterOpt.isPresent());
        assertEquals("engineer", afterOpt.get().engineers().queryKind());
        assertEquals(List.of("Felicity Farseer"), afterOpt.get().engineers().engineerNames());
    }
}
