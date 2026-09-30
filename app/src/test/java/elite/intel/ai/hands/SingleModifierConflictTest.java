package elite.intel.ai.hands;

import elite.intel.ai.hands.BindingConflictScanner.Conflict;
import elite.intel.ai.hands.BindingConflictScanner.SingleModifierConflict;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for bare modifier key conflict detection (LF-3).
 * Verifies that bare Ctrl/Shift/Alt assignments in ship flight that clash with app-driven
 * combination shortcuts are detected, while honoring vehicle contexts and modifier sides.
 */
class SingleModifierConflictTest {

    private static KeyBindingsParser.KeyBinding binding(String key, String... modifiers) {
        return KeyBindingsParser.getInstance().new KeyBinding(key, modifiers, false);
    }

    @Test
    void test1_shipActionWithBareLeftControl_detectedAgainstAppDrivenChord() {
        // 宇宙船の操作で左 Ctrl 単独 ＋ アプリ操作（例: 次の星系）で左 Ctrl＋何か → 検出される
        Map<String, KeyBindingsParser.KeyBinding> bindings = new HashMap<>();
        bindings.put("SetSpeedMinus25", binding("Key_LeftControl"));
        bindings.put("TargetNextRouteSystem", binding("Key_M", "Key_LeftControl"));

        Set<String> appDrivenActions = Set.of("TargetNextRouteSystem");

        List<SingleModifierConflict> conflicts =
                BindingConflictScanner.scanSingleModifierConflicts(bindings, appDrivenActions);

        assertEquals(1, conflicts.size());
        SingleModifierConflict c = conflicts.get(0);
        assertEquals("SetSpeedMinus25", c.bareAction());
        assertEquals("Key_LeftControl", c.modifierKey());
        assertEquals("TargetNextRouteSystem", c.chordAction());
        assertEquals(Set.of("Key_LeftControl", "Key_M"), c.chord());
        assertNotNull(c.description());
    }

    @Test
    void test2_shipActionWithBareRightShift_detectedAgainstAppDrivenChord() {
        // 宇宙船の操作で右 Shift 単独（サイレントランニング） ＋ アプリ操作で右 Shift＋何か → 検出される
        Map<String, KeyBindingsParser.KeyBinding> bindings = new HashMap<>();
        bindings.put("ToggleSilentRunning", binding("Key_RightShift"));
        bindings.put("DeployHardpointToggle", binding("Key_U", "Key_RightShift"));

        Set<String> appDrivenActions = Set.of("DeployHardpointToggle");

        List<SingleModifierConflict> conflicts =
                BindingConflictScanner.scanSingleModifierConflicts(bindings, appDrivenActions);

        assertEquals(1, conflicts.size());
        SingleModifierConflict c = conflicts.get(0);
        assertEquals("ToggleSilentRunning", c.bareAction());
        assertEquals("Key_RightShift", c.modifierKey());
        assertEquals("DeployHardpointToggle", c.chordAction());
        assertEquals(Set.of("Key_RightShift", "Key_U"), c.chord());
    }

    @Test
    void test3_buggyOrHumanoidActionWithBareModifier_notDetectedDueToDifferentContext() {
        // 車両や徒歩の操作で修飾キー単独 ＋ アプリ操作（宇宙船）で同じ修飾キーの組み合わせ → 検出されない（場面が違う）
        Map<String, KeyBindingsParser.KeyBinding> bindings = new HashMap<>();
        // Buggy (SRV) action
        bindings.put("BuggyPrimaryFireButton", binding("Key_LeftControl"));
        // Humanoid (On-foot) action
        bindings.put("HumanoidForwardButton", binding("Key_LeftControl"));
        // App-driven ship action
        bindings.put("TargetNextRouteSystem", binding("Key_M", "Key_LeftControl"));

        Set<String> appDrivenActions = Set.of("TargetNextRouteSystem");

        List<SingleModifierConflict> conflicts =
                BindingConflictScanner.scanSingleModifierConflicts(bindings, appDrivenActions);

        assertTrue(conflicts.isEmpty(), "SRV and on-foot controls should not conflict with ship shortcuts");
    }

    @Test
    void test4_shipActionWithBareModifier_notDetectedWhenNoAppShortcutUsesModifier() {
        // 宇宙船の操作で修飾キー単独だが、アプリ操作にその修飾キーを含む組み合わせが無い → 検出されない
        Map<String, KeyBindingsParser.KeyBinding> bindings = new HashMap<>();
        bindings.put("SetSpeedMinus25", binding("Key_LeftControl"));
        // App shortcut uses Key_LeftShift, not Key_LeftControl
        bindings.put("TargetNextRouteSystem", binding("Key_M", "Key_LeftShift"));

        Set<String> appDrivenActions = Set.of("TargetNextRouteSystem");

        List<SingleModifierConflict> conflicts =
                BindingConflictScanner.scanSingleModifierConflicts(bindings, appDrivenActions);

        assertTrue(conflicts.isEmpty(), "Should not detect conflict when app-driven action does not use LeftControl");
    }

    @Test
    void test5_leftControlVersusRightControl_distinguishedBySide() {
        // 左 Ctrl 単独 ＋ アプリ操作で右 Ctrl の組み合わせ → 検出されない（左右が違う）
        Map<String, KeyBindingsParser.KeyBinding> bindings = new HashMap<>();
        bindings.put("SetSpeedMinus25", binding("Key_LeftControl"));
        bindings.put("TargetNextRouteSystem", binding("Key_M", "Key_RightControl"));

        Set<String> appDrivenActions = Set.of("TargetNextRouteSystem");

        List<SingleModifierConflict> conflicts =
                BindingConflictScanner.scanSingleModifierConflicts(bindings, appDrivenActions);

        assertTrue(conflicts.isEmpty(), "LeftControl and RightControl must not collide");
    }

    @Test
    void test6_existingScanResultsRemainUnchanged() {
        // 既存の BindingConflictScannerTest（scan の結果）が変わらないこと:
        // scan() は exact chord のみ検出するため、単独修飾キーと修飾キー組み合わせでは衝突しない
        Map<String, KeyBindingsParser.KeyBinding> bindings = new HashMap<>();
        bindings.put("SetSpeedMinus25", binding("Key_LeftControl"));
        bindings.put("TargetNextRouteSystem", binding("Key_M", "Key_LeftControl"));

        List<Conflict> conflicts = BindingConflictScanner.scan(bindings);
        assertTrue(conflicts.isEmpty(), "Existing scan() must not flag different exact chords as conflicts");
    }
}
