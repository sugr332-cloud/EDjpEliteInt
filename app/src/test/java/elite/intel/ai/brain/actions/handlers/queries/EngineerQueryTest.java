package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.DataDto;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.ModuleEngineerDto;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerProgressManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineerQueryTest {

    private final EngineerProgressManager progressManager = EngineerProgressManager.getInstance();

    // Mock coordinates near Sol (0, 0, 0)
    private final LocationDao.Coordinates mockSol = new LocationDao.Coordinates("Sol", 0.0, 0.0, 0.0);

    @BeforeEach
    void setUp() {
        progressManager.clear();
    }

    @AfterEach
    void tearDown() {
        progressManager.clear();
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
}
