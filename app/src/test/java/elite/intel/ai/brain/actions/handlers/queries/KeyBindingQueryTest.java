package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.KeyBindingQuery.BindingSlotResultDto;
import elite.intel.ai.brain.actions.handlers.queries.KeyBindingQuery.ConflictItemDto;
import elite.intel.ai.brain.actions.handlers.queries.KeyBindingQuery.DataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeyBindingQuery (LF-2), testing against lf2-sample.binds.
 */
class KeyBindingQueryTest {

    private KeyBindingQuery query;
    private File sampleBindsFile;

    @BeforeEach
    void setUp() throws Exception {
        URL url = getClass().getResource("/bindings/lf2-sample.binds");
        assertNotNull(url, "Fixture /bindings/lf2-sample.binds must exist on test classpath");
        sampleBindsFile = new File(url.toURI());
        query = new KeyBindingQuery(() -> sampleBindsFile);
    }

    private DataDto runQuery(String queryText) {
        JsonObject json = new JsonObject();
        json.addProperty("query", queryText);
        return query.buildData(json, queryText);
    }

    @Test
    void test1_searchByOperationName_singleResult() {
        // "サイレントランニングは何のキー" -> ToggleSilentRunning (Right Shift)
        DataDto data = runQuery("サイレントランニングは何のキー");
        assertEquals("action_lookup", data.type());
        assertFalse(data.results().isEmpty(), "Should find ToggleSilentRunning");

        BindingSlotResultDto first = data.results().get(0);
        assertEquals("ToggleSilentRunning", first.actionId());
        assertNotNull(first.primary());
        assertEquals("Keyboard", first.primary().device());
        assertEquals("Key_RightShift", first.primary().key());
        assertEquals("Right Shift", first.primary().spoken());
    }

    @Test
    void test2_searchByKeyName_japanesePhrasing() {
        // "J キーは何" -> HyperSuperCombination (Key_J)
        DataDto dataJ = runQuery("J キーは何");
        assertEquals("key_lookup", dataJ.type());
        assertFalse(dataJ.results().isEmpty(), "Should find action assigned to J key");
        assertEquals("HyperSuperCombination", dataJ.results().get(0).actionId());
        assertEquals("Key_J", dataJ.results().get(0).primary().key());

        // "左コントロールに何が割り当てられている" -> OrderDefensiveBehaviour / OrderAggressiveBehaviour
        DataDto dataCtrl = runQuery("左コントロールに何が割り当てられている");
        assertEquals("key_lookup", dataCtrl.type());
        assertTrue(dataCtrl.results().size() >= 2, "Should find both defensive and aggressive orders for LeftControl");
        boolean hasDefend = dataCtrl.results().stream().anyMatch(r -> "OrderDefensiveBehaviour".equals(r.actionId()));
        boolean hasAttack = dataCtrl.results().stream().anyMatch(r -> "OrderAggressiveBehaviour".equals(r.actionId()));
        assertTrue(hasDefend && hasAttack, "Both orders should be found for LeftControl");
    }

    @Test
    void test3_searchConflicts_detectsOverlaps() {
        // "キーの重なりを教えて" -> should detect conflict between OrderDefensiveBehaviour and OrderAggressiveBehaviour
        DataDto data = runQuery("キーの重なりを教えて");
        assertEquals("conflict", data.type());
        assertFalse(data.conflicts().isEmpty(), "Should detect at least 1 conflict in fixture");

        ConflictItemDto conflict = data.conflicts().stream()
                .filter(c -> ("OrderAggressiveBehaviour".equals(c.actionA()) && "OrderDefensiveBehaviour".equals(c.actionB()))
                        || ("OrderDefensiveBehaviour".equals(c.actionA()) && "OrderAggressiveBehaviour".equals(c.actionB())))
                .findFirst()
                .orElse(null);

        assertNotNull(conflict, "Conflict between defensive and aggressive behaviour should be detected");
        assertEquals("Left Control", conflict.chord());
    }

    @Test
    void test4_searchByOperationName_multipleCandidates() {
        // "ジャンプのキーは" -> matches HyperSuperCombination and Hyperspace
        DataDto data = runQuery("ジャンプのキーは");
        assertEquals("action_lookup", data.type());
        assertTrue(data.results().size() >= 2, "Should return multiple jump-related actions");
        assertTrue(data.results().size() <= 5, "Should cap candidates at maximum 5");

        boolean hasCombo = data.results().stream().anyMatch(r -> "HyperSuperCombination".equals(r.actionId()));
        boolean hasHyper = data.results().stream().anyMatch(r -> "Hyperspace".equals(r.actionId()));
        assertTrue(hasCombo && hasHyper, "Both HyperSuperCombination and Hyperspace should be returned");
    }

    @Test
    void test5_noMatchFound() {
        // Unmapped key: "F12 キーは何"
        DataDto dataKey = runQuery("F12 キーは何");
        assertEquals("key_lookup", dataKey.type());
        assertTrue(dataKey.results().isEmpty(), "No action should match F12 in fixture");
        assertTrue(dataKey.message().contains("割り当てられている操作はありません"));

        // Unmapped operation: "未知の存在しない機能のキー"
        DataDto dataOp = runQuery("未知の存在しない機能のキー");
        assertEquals("action_lookup", dataOp.type());
        assertTrue(dataOp.results().isEmpty(), "No action should match unknown operation");
        assertTrue(dataOp.message().contains("見つかりませんでした"));
    }

    @Test
    void test6_nonKeyboardDeviceReturned() {
        // "AutoBreakBuggyButton" -> Primary device 044F0422 (Joy_1), Secondary Mouse (Mouse_3)
        DataDto data = runQuery("AutoBreakBuggyButton");
        assertEquals("action_lookup", data.type());
        assertFalse(data.results().isEmpty(), "Should find AutoBreakBuggyButton");

        BindingSlotResultDto result = data.results().get(0);
        assertEquals("AutoBreakBuggyButton", result.actionId());

        assertNotNull(result.primary(), "Primary slot should be populated for non-keyboard device");
        assertEquals("044F0422", result.primary().device());
        assertEquals("Joy_1", result.primary().key());

        assertNotNull(result.secondary(), "Secondary slot should be populated for mouse device");
        assertEquals("Mouse", result.secondary().device());
        assertEquals("Mouse_3", result.secondary().key());
    }

    @Test
    void test7_searchConflicts_includesSingleModifierConflicts() {
        // "キーの重なりを教えて" -> should include bare modifier conflict for SetSpeed50 (Key_LeftShift) and Hyperspace (Key_LeftShift + Key_F4)
        DataDto data = runQuery("キーの重なりを教えて");
        assertEquals("conflict", data.type());
        assertFalse(data.singleModifierConflicts().isEmpty(), "Should detect single modifier conflict");

        KeyBindingQuery.SingleModifierConflictItemDto item = data.singleModifierConflicts().stream()
                .filter(sc -> "SetSpeed50".equals(sc.bareAction()) && "Hyperspace".equals(sc.chordAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(item, "Should find single modifier conflict between SetSpeed50 and Hyperspace");
        assertEquals("Key_LeftShift", item.modifierKey());
        assertEquals("Left Shift", item.modifierSpoken());
        assertEquals("Left Shift plus F 4", item.chordSpoken());
        assertTrue(data.message().contains("単独修飾キーの干渉"));
    }
}
