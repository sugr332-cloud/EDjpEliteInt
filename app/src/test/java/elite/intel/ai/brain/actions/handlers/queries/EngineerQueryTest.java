package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.DataDto;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.ModuleEngineerDto;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
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
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            progressManager.recordProgress("Felicity Farseer", 300000L, "Unlocked", 5, 0, "2026-10-01T10:00:00Z");
            EngineerQuery query = new EngineerQuery(() -> mockSol);

            // 1. English
            session.setLanguage(Language.EN);
            DataDto dataEn = query.buildData(new JsonObject(), "フェリシティ・ファーシーアの開放条件を教えて");
            assertEquals("engineer", dataEn.type());
            assertEquals("Felicity Farseer", dataEn.name());
            assertEquals("ship", dataEn.engineerType());
            assertEquals("Deciat", dataEn.system());
            assertEquals("Farseer Inc", dataEn.base());
            assertNotNull(dataEn.distanceLy());
            assertTrue(dataEn.distanceLy() > 0);
            assertEquals("Unlocked", dataEn.progress());
            assertEquals(5, dataEn.rank());
            assertEquals(0, dataEn.rankProgress());
            assertFalse(dataEn.permitRequired());
            assertNotNull(dataEn.specialties());
            assertFalse(dataEn.specialties().isEmpty());
            assertTrue(dataEn.specialties().stream().anyMatch(sp -> "Frame Shift Drive".equals(sp.moduleOrDescription())));

            // 2. Japanese
            session.setLanguage(Language.JA);
            DataDto dataJa = query.buildData(new JsonObject(), "フェリシティ・ファーシーアの開放条件を教えて");
            assertEquals("engineer", dataJa.type());
            assertEquals("フェリシティ・ファーシーア", dataJa.name(), "In Japanese mode, LLM data name must be Katakana name");
            assertEquals("ship", dataJa.engineerType());
            assertEquals("Deciat", dataJa.system());
            assertEquals("Farseer Inc", dataJa.base());
            assertNotNull(dataJa.distanceLy());
            assertTrue(dataJa.distanceLy() > 0);
            assertEquals("Unlocked", dataJa.progress());
            assertEquals(5, dataJa.rank());
            assertEquals(0, dataJa.rankProgress());
            assertFalse(dataJa.permitRequired());
            assertNotNull(dataJa.invite());
            assertTrue(dataJa.invite().contains("スカウト"));
            assertNotNull(dataJa.unlock());
            assertTrue(dataJa.unlock().contains("メタアロイ"));
            assertNotNull(dataJa.specialties());
            assertFalse(dataJa.specialties().isEmpty());
            assertTrue(dataJa.specialties().stream().anyMatch(sp -> "フレームシフトドライブ（FSD）".equals(sp.moduleOrDescription())),
                    "Specialties must use Japanese localized name");
        } finally {
            session.setLanguage(orig);
        }
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
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            EngineerQuery query = new EngineerQuery(() -> null);

            // 1. English
            session.setLanguage(Language.EN);
            DataDto dataEn = query.buildData(new JsonObject(), "フェリシティ・ファーシーアはどこ？");
            assertEquals("engineer", dataEn.type());
            assertEquals("Felicity Farseer", dataEn.name());
            assertNull(dataEn.distanceLy(), "Distance must be null when coordinates unavailable");

            // 2. Japanese
            session.setLanguage(Language.JA);
            DataDto dataJa = query.buildData(new JsonObject(), "フェリシティ・ファーシーアはどこ？");
            assertEquals("engineer", dataJa.type());
            assertEquals("フェリシティ・ファーシーア", dataJa.name());
            assertNull(dataJa.distanceLy(), "Distance must be null when coordinates unavailable");
        } finally {
            session.setLanguage(orig);
        }
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
        assertEquals(6, dto.engineerNames().size(), "FSD module card must contain all 6 engineers");
        assertTrue(dto.engineerNames().contains("Felicity Farseer"));
        assertTrue(dto.engineerNames().contains("Elvira Martuuk"));
    }

    @Test
    void moduleCardDisplaysAllEngineersWhileVoiceDataLimitsToFive() throws Exception {
        EngineerQuery query = createTestQuery();
        JsonObject params = new JsonObject();
        params.addProperty("module", "Sensors");

        DataDto data = query.buildData(params, "センサーを改造できるエンジニア");
        assertEquals("module", data.type());
        assertNotNull(data.moduleEngineers());
        assertEquals(5, data.moduleEngineers().size(), "Voice/LLM candidates must remain capped at 5");

        query.handle("query_engineer", params, "センサーを改造できるエンジニア");
        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("module", dto.queryKind());
        assertEquals("Sensors", dto.moduleName());
        assertEquals(8, dto.engineerNames().size(), "Sensors module card must contain all 8 engineers");
    }

    @Test
    void directoryQueryReturnsAll38EngineersSortedShipThenOnFootAlphabetical() throws Exception {
        EngineerQuery query = createTestQuery();
        DataDto data = query.buildData(new JsonObject(), "エンジニアの効能と得意分野を教えて");

        assertEquals("directory", data.type());
        assertEquals("all", data.directoryFilter());
        assertEquals(38, data.totalCount());
        assertEquals(25, data.shipCount());
        assertEquals(13, data.onFootCount());
        assertNull(data.specialties());
        assertNull(data.name());
        assertNull(data.moduleEngineers());

        // Spoken YAML must contain counts but no names or specialties
        String yaml = data.toYaml();
        assertTrue(yaml.contains("totalCount: 38"));
        assertTrue(yaml.contains("shipCount: 25"));
        assertTrue(yaml.contains("onFootCount: 13"));
        assertFalse(yaml.contains("Felicity Farseer"));
        assertFalse(yaml.contains("cardEngineerNames"));

        // Saved card display must contain all 38
        query.handle("query_engineer", new JsonObject(), "エンジニアの効能と得意分野を教えて");
        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("directory", dto.queryKind());
        assertNull(dto.moduleName());
        assertEquals(38, dto.engineerNames().size());

        // First 25 are ship engineers in alphabetical order
        List<String> names = dto.engineerNames();
        for (int i = 0; i < 25; i++) {
            var info = elite.intel.gameapi.engineers.EngineerDirectory.getInstance().findByName(names.get(i)).orElseThrow();
            assertTrue(info.isShip(), "First 25 must be ship engineers: " + names.get(i));
        }
        for (int i = 0; i < 24; i++) {
            assertTrue(names.get(i).compareToIgnoreCase(names.get(i + 1)) <= 0,
                    "Ship engineers must be sorted alphabetically: " + names.get(i) + " vs " + names.get(i + 1));
        }

        // Remaining 13 are on-foot engineers in alphabetical order
        for (int i = 25; i < 38; i++) {
            var info = elite.intel.gameapi.engineers.EngineerDirectory.getInstance().findByName(names.get(i)).orElseThrow();
            assertTrue(info.isOnFoot(), "Remaining 13 must be on-foot engineers: " + names.get(i));
        }
        for (int i = 25; i < 37; i++) {
            assertTrue(names.get(i).compareToIgnoreCase(names.get(i + 1)) <= 0,
                    "On-foot engineers must be sorted alphabetically: " + names.get(i) + " vs " + names.get(i + 1));
        }
    }

    @Test
    void directoryQueryShipFilterFilters25Engineers() throws Exception {
        EngineerQuery query = createTestQuery();
        DataDto data = query.buildData(new JsonObject(), "船のエンジニアの得意分野");

        assertEquals("directory", data.type());
        assertEquals("ship", data.directoryFilter());
        assertEquals(25, data.totalCount());
        assertEquals(25, data.shipCount());
        assertEquals(0, data.onFootCount());

        query.handle("query_engineer", new JsonObject(), "船のエンジニアの得意分野");
        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals(25, dto.engineerNames().size());
        for (String name : dto.engineerNames()) {
            var info = elite.intel.gameapi.engineers.EngineerDirectory.getInstance().findByName(name).orElseThrow();
            assertTrue(info.isShip());
        }
    }

    @Test
    void directoryQueryOnFootFilterFilters13Engineers() throws Exception {
        EngineerQuery query = createTestQuery();
        DataDto data = query.buildData(new JsonObject(), "徒歩のエンジニアは何ができる？");

        assertEquals("directory", data.type());
        assertEquals("onfoot", data.directoryFilter());
        assertEquals(13, data.totalCount());
        assertEquals(0, data.shipCount());
        assertEquals(13, data.onFootCount());

        query.handle("query_engineer", new JsonObject(), "徒歩のエンジニアは何ができる？");
        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals(13, dto.engineerNames().size());
        for (String name : dto.engineerNames()) {
            var info = elite.intel.gameapi.engineers.EngineerDirectory.getInstance().findByName(name).orElseThrow();
            assertTrue(info.isOnFoot());
        }
    }

    @Test
    void progressQueryMaintainedWhenNoDirectoryKeywords() {
        EngineerQuery query = createTestQuery();

        DataDto data1 = query.buildData(new JsonObject(), "エンジニアの一覧");
        assertEquals("progress", data1.type(), "一覧 alone without directory keywords must be progress");

        DataDto data2 = query.buildData(new JsonObject(), "エンジニアの進み具合は");
        assertEquals("progress", data2.type());
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

    @Test
    void tpPhraseEngineersListAndEfficacyReturnsAll38DirectoryCards() throws Exception {
        EngineerQuery query = createTestQuery();
        JsonObject params = new JsonObject();

        DataDto data = query.buildData(params, "エンジニアの一覧と効能を教えて");
        assertEquals("directory", data.type());
        assertEquals("all", data.directoryFilter());
        assertEquals(38, data.totalCount());

        query.handle("query_engineer", params, "エンジニアの一覧と効能を教えて");
        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("directory", dto.queryKind());
        assertNotNull(dto.engineerNames());
        assertEquals(38, dto.engineerNames().size(), "Must contain all 38 engineers on directory card");
    }

    @Test
    void tpPhraseFsdRelatedEngineersResolvesModuleFromUtteranceAndDisplaysSixCards() throws Exception {
        EngineerQuery query = createTestQuery();
        JsonObject params = new JsonObject();

        DataDto data = query.buildData(params, "FSD に関係するエンジニアは誰が居る？");
        assertEquals("module", data.type());
        assertEquals("Frame Shift Drive", data.moduleName());
        assertNotNull(data.moduleEngineers());
        assertTrue(data.moduleEngineers().size() <= 5, "Voice data must be capped at 5 or fewer");

        query.handle("query_engineer", params, "FSD に関係するエンジニアは誰が居る？");
        Optional<LatestDisplay> latestOpt = displayManager.getLatest();
        assertTrue(latestOpt.isPresent());
        EngineersDisplayDto dto = latestOpt.get().engineers();
        assertEquals("module", dto.queryKind());
        assertEquals("Frame Shift Drive", dto.moduleName());
        assertNotNull(dto.engineerNames());
        assertEquals(6, dto.engineerNames().size(), "FSD module card must contain all 6 engineers");
        assertTrue(dto.engineerNames().containsAll(List.of(
                "Felicity Farseer", "Elvira Martuuk", "Colonel Bris Dekker",
                "Professor Palin", "Chloe Sedesi", "Mel Brandon"
        )));
    }
}

