package elite.intel.ai.brain.actions.handlers.queries;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.handlers.queries.struct.AiDataStruct;
import elite.intel.ai.hands.BindingChordSpeech;
import elite.intel.ai.hands.BindingConflictScanner;
import elite.intel.ai.hands.BindingDisplayNames;
import elite.intel.ai.hands.BindingsMonitor;
import elite.intel.ai.hands.KeyBindingsParser;
import elite.intel.ai.hands.KeyBindingsParser.KeyBinding;
import elite.intel.ai.hands.KeyBindingsParser.ReadOnlyBindingSlot;
import elite.intel.ai.hands.KeyBindingsParser.ReadOnlyBindingSlots;
import elite.intel.util.yaml.ToYamlConvertable;
import elite.intel.util.yaml.YamlFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in query to report key bindings, key lookups, and binding conflicts (LF-2).
 * Does not press keys; answers only.
 */
@RegisterQuery
public class KeyBindingQuery extends BaseQueryAnalyzer implements IntelQuery {

    private static final Logger log = LogManager.getLogger(KeyBindingQuery.class);

    public static final String ID = "query_key_binding";

    @FunctionalInterface
    public interface BindsFileResolver {
        File resolve() throws Exception;
    }

    private final BindsFileResolver bindsFileResolver;

    private static final Pattern F_KEY_PATTERN = Pattern.compile("(?i)(?:^|.*?\\b)f([1-9]|1[0-2])(?:\\s*(?:キー|key))?.*");
    private static final Pattern SINGLE_CHAR_KEY_PATTERN = Pattern.compile("(?i)(?:^|.*?\\b)([a-z0-9])\\s*(?:キー|key)(?:.*)?");
    private static final Pattern BARE_SINGLE_CHAR_PATTERN = Pattern.compile("(?i)^[a-z0-9]$");

    private static final Map<String, String> KEY_ALIASES = new LinkedHashMap<>();
    static {
        // 修飾キー
        KEY_ALIASES.put("左コントロール", "Key_LeftControl");
        KEY_ALIASES.put("左control", "Key_LeftControl");
        KEY_ALIASES.put("左ctrl", "Key_LeftControl");
        KEY_ALIASES.put("left control", "Key_LeftControl");
        KEY_ALIASES.put("left ctrl", "Key_LeftControl");
        KEY_ALIASES.put("右コントロール", "Key_RightControl");
        KEY_ALIASES.put("右control", "Key_RightControl");
        KEY_ALIASES.put("右ctrl", "Key_RightControl");
        KEY_ALIASES.put("right control", "Key_RightControl");
        KEY_ALIASES.put("right ctrl", "Key_RightControl");
        KEY_ALIASES.put("左シフト", "Key_LeftShift");
        KEY_ALIASES.put("左shift", "Key_LeftShift");
        KEY_ALIASES.put("left shift", "Key_LeftShift");
        KEY_ALIASES.put("右シフト", "Key_RightShift");
        KEY_ALIASES.put("右shift", "Key_RightShift");
        KEY_ALIASES.put("right shift", "Key_RightShift");
        KEY_ALIASES.put("左オルト", "Key_LeftAlt");
        KEY_ALIASES.put("左alt", "Key_LeftAlt");
        KEY_ALIASES.put("left alt", "Key_LeftAlt");
        KEY_ALIASES.put("右オルト", "Key_RightAlt");
        KEY_ALIASES.put("右alt", "Key_RightAlt");
        KEY_ALIASES.put("right alt", "Key_RightAlt");

        // 特殊キー
        KEY_ALIASES.put("エンター", "Key_Enter");
        KEY_ALIASES.put("enter", "Key_Enter");
        KEY_ALIASES.put("return", "Key_Enter");
        KEY_ALIASES.put("スペース", "Key_Space");
        KEY_ALIASES.put("space", "Key_Space");
        KEY_ALIASES.put("バックスペース", "Key_Backspace");
        KEY_ALIASES.put("backspace", "Key_Backspace");
        KEY_ALIASES.put("タブ", "Key_Tab");
        KEY_ALIASES.put("tab", "Key_Tab");
        KEY_ALIASES.put("エスケープ", "Key_Escape");
        KEY_ALIASES.put("escape", "Key_Escape");
        KEY_ALIASES.put("esc", "Key_Escape");

        // 矢印キー
        KEY_ALIASES.put("上矢印", "Key_UpArrow");
        KEY_ALIASES.put("上キー", "Key_UpArrow");
        KEY_ALIASES.put("up arrow", "Key_UpArrow");
        KEY_ALIASES.put("下矢印", "Key_DownArrow");
        KEY_ALIASES.put("下キー", "Key_DownArrow");
        KEY_ALIASES.put("down arrow", "Key_DownArrow");
        KEY_ALIASES.put("左矢印", "Key_LeftArrow");
        KEY_ALIASES.put("左キー", "Key_LeftArrow");
        KEY_ALIASES.put("left arrow", "Key_LeftArrow");
        KEY_ALIASES.put("右矢印", "Key_RightArrow");
        KEY_ALIASES.put("右キー", "Key_RightArrow");
        KEY_ALIASES.put("right arrow", "Key_RightArrow");

        // テンキー
        KEY_ALIASES.put("テンキー", "Key_Numpad_");
        KEY_ALIASES.put("numpad", "Key_Numpad_");

        // その他
        KEY_ALIASES.put("ホーム", "Key_Home");
        KEY_ALIASES.put("home", "Key_Home");
        KEY_ALIASES.put("エンド", "Key_End");
        KEY_ALIASES.put("end", "Key_End");
        KEY_ALIASES.put("インサート", "Key_Insert");
        KEY_ALIASES.put("insert", "Key_Insert");
        KEY_ALIASES.put("デリート", "Key_Delete");
        KEY_ALIASES.put("delete", "Key_Delete");
        KEY_ALIASES.put("ページアップ", "Key_PageUp");
        KEY_ALIASES.put("page up", "Key_PageUp");
        KEY_ALIASES.put("ページダウン", "Key_PageDown");
        KEY_ALIASES.put("page down", "Key_PageDown");
    }

    private static final Map<String, List<String>> JAPANESE_OPERATION_SYNONYMS = new LinkedHashMap<>();
    static {
        JAPANESE_OPERATION_SYNONYMS.put("サイレントランニング", List.of("ToggleSilentRunning"));
        JAPANESE_OPERATION_SYNONYMS.put("ジャンプ", List.of("HyperSuperCombination", "Hyperspace", "Supercruise"));
        JAPANESE_OPERATION_SYNONYMS.put("ハイパースペース", List.of("Hyperspace", "HyperSuperCombination"));
        JAPANESE_OPERATION_SYNONYMS.put("スーパークルーズ", List.of("Supercruise", "HyperSuperCombination"));
        JAPANESE_OPERATION_SYNONYMS.put("着陸装置", List.of("LandingGearToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("ランディングギア", List.of("LandingGearToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("ギア", List.of("LandingGearToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("カーゴスクープ", List.of("ToggleCargoScoop"));
        JAPANESE_OPERATION_SYNONYMS.put("貨物スクープ", List.of("ToggleCargoScoop"));
        JAPANESE_OPERATION_SYNONYMS.put("ライト", List.of("HeadlightsToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("ヘッドライト", List.of("HeadlightsToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("ナイトビジョン", List.of("NightVisionToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("暗視", List.of("NightVisionToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("ハードポイント", List.of("DeployHardpointToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("武器展開", List.of("DeployHardpointToggle"));
        JAPANESE_OPERATION_SYNONYMS.put("シールドセル", List.of("UseShieldCell"));
        JAPANESE_OPERATION_SYNONYMS.put("チャフ", List.of("FireChaffLauncher"));
        JAPANESE_OPERATION_SYNONYMS.put("ヒートシンク", List.of("DeployHeatSink"));
        JAPANESE_OPERATION_SYNONYMS.put("停止", List.of("SetSpeedZero"));
        JAPANESE_OPERATION_SYNONYMS.put("速度ゼロ", List.of("SetSpeedZero"));
        JAPANESE_OPERATION_SYNONYMS.put("ブースト", List.of("UseBoostJuice"));
        JAPANESE_OPERATION_SYNONYMS.put("ターゲット", List.of("SelectTarget", "SelectTargetsTarget"));
        JAPANESE_OPERATION_SYNONYMS.put("出航", List.of("Undock"));
        JAPANESE_OPERATION_SYNONYMS.put("発進", List.of("Undock"));
    }

    public KeyBindingQuery() {
        this(() -> BindingsMonitor.getInstance().resolveActiveBindsFile());
    }

    public KeyBindingQuery(BindsFileResolver bindsFileResolver) {
        this.bindsFileResolver = Objects.requireNonNull(bindsFileResolver);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String llmDescription() {
        return "Report Elite Dangerous key bindings, key lookups, and binding conflicts.";
    }

    @Override
    public JsonObject handle(String action, JsonObject params, String originalUserInput) throws Exception {
        DataDto data = buildData(params, originalUserInput);
        String instructions = """
                Answer the user's question about Elite Dangerous control bindings, key assignments, or binding conflicts.
                
                Data structure:
                - type: 'conflict', 'key_lookup', 'action_lookup', or 'error'
                - query: original user query
                - message: summary message
                - conflicts: list of detected binding conflicts (actionA, actionAName, actionB, actionBName, chord, description, blocking)
                - singleModifierConflicts: list of bare modifier conflicts (bareAction, bareActionName, modifierKey, modifierSpoken, chordAction, chordActionName, chordSpoken, description)
                - results: list of matched actions with primary and secondary slots (actionId, label, section, group, primary, secondary)
                
                Rules:
                - Answer concisely and accurately based on the provided data.
                - When describing keys, use clear spoken names.
                - Never attempt to trigger or press keys; this is a query only.
                """;
        return process(new AiDataStruct(instructions, data), originalUserInput);
    }

    public DataDto buildData(JsonObject params, String originalUserInput) {
        String rawQuery = extractQuery(params, originalUserInput);
        File file;
        try {
            file = bindsFileResolver.resolve();
        } catch (Exception e) {
            log.error("Failed to resolve active binds file", e);
            file = null;
        }

        if (file == null || !file.exists()) {
            return new DataDto("error", rawQuery, "キー設定ファイルが見つかりません。", List.of(), List.of());
        }

        Map<String, ReadOnlyBindingSlots> readOnlySlots;
        Map<String, KeyBinding> executableBindings;
        try {
            readOnlySlots = KeyBindingsParser.getInstance().parseReadOnlyBindingSlots(file);
            executableBindings = KeyBindingsParser.getInstance().parseBindings(file);
        } catch (Exception e) {
            log.error("Failed to parse binds file {}", file.getAbsolutePath(), e);
            return new DataDto("error", rawQuery, "キー設定ファイルの解析に失敗しました。", List.of(), List.of());
        }

        String normalized = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);

        // 照合優先順 (1): 「重なり」「コンフリクト」を含む → BindingConflictScanner の結果
        if (isConflictQuery(normalized)) {
            List<BindingConflictScanner.Conflict> conflicts = BindingConflictScanner.scan(executableBindings);
            List<ConflictItemDto> conflictItems = new ArrayList<>();
            for (BindingConflictScanner.Conflict c : conflicts) {
                String nameA = BindingDisplayNames.label(c.actionA());
                String nameB = BindingDisplayNames.label(c.actionB());
                String spokenChord = BindingChordSpeech.describe(c.chord());
                conflictItems.add(new ConflictItemDto(c.actionA(), nameA, c.actionB(), nameB, spokenChord, c.description(), c.blocking()));
            }

            List<BindingConflictScanner.SingleModifierConflict> singleModifierConflicts =
                    BindingConflictScanner.scanSingleModifierConflicts(executableBindings, BindingsMonitor.appDrivenActions());
            List<SingleModifierConflictItemDto> singleModifierItems = new ArrayList<>();
            for (BindingConflictScanner.SingleModifierConflict sc : singleModifierConflicts) {
                String bareName = BindingDisplayNames.label(sc.bareAction());
                String chordName = BindingDisplayNames.label(sc.chordAction());
                String modifierSpoken = BindingChordSpeech.describe(Set.of(sc.modifierKey()));
                String chordSpoken = BindingChordSpeech.describe(sc.chord());
                singleModifierItems.add(new SingleModifierConflictItemDto(
                        sc.bareAction(), bareName, sc.modifierKey(), modifierSpoken,
                        sc.chordAction(), chordName, chordSpoken, sc.description()));
            }

            StringBuilder sb = new StringBuilder();
            if (conflictItems.isEmpty() && singleModifierItems.isEmpty()) {
                sb.append("キーの重なり（コンフリクト）は検出されませんでした。");
            } else {
                if (!conflictItems.isEmpty()) {
                    sb.append(conflictItems.size()).append(" 件のキーの重なり");
                }
                if (!singleModifierItems.isEmpty()) {
                    if (!conflictItems.isEmpty()) {
                        sb.append("、および ");
                    }
                    sb.append(singleModifierItems.size()).append(" 件の単独修飾キーの干渉");
                }
                sb.append("が検出されました。");
            }

            return new DataDto("conflict", rawQuery, sb.toString(), conflictItems, singleModifierItems, List.of());
        }

        // 照合優先順 (2): キー名から逆引き
        String resolvedKey = resolveKeyToken(normalized);
        if (resolvedKey != null && !resolvedKey.isBlank()) {
            List<BindingSlotResultDto> matched = findActionsByKey(readOnlySlots, resolvedKey);
            String keyDisplayName = formatKeyDisplayName(resolvedKey);
            String message = matched.isEmpty()
                    ? keyDisplayName + " に割り当てられている操作はありません。"
                    : keyDisplayName + " に割り当てられている操作（上位 " + matched.size() + " 件）です。";
            return new DataDto("key_lookup", rawQuery, message, List.of(), matched);
        }

        // 照合優先順 (3): 操作名から検索
        List<BindingSlotResultDto> matchedActions = findActionsByOperationName(readOnlySlots, rawQuery, normalized);
        String message = matchedActions.isEmpty()
                ? "該当する操作のキー割り当てが見つかりませんでした。"
                : "該当する操作のキー割り当て（上位 " + matchedActions.size() + " 件）です。";
        return new DataDto("action_lookup", rawQuery, message, List.of(), matchedActions);
    }

    private String extractQuery(JsonObject params, String originalUserInput) {
        if (params != null) {
            if (params.has("query") && !params.get("query").isJsonNull()) {
                String q = params.get("query").getAsString();
                if (!q.isBlank()) return q;
            }
            if (params.has("key") && !params.get("key").isJsonNull()) {
                String k = params.get("key").getAsString();
                if (!k.isBlank()) return k;
            }
        }
        if (originalUserInput != null && !originalUserInput.isBlank()) {
            return originalUserInput;
        }
        return "";
    }

    private boolean isConflictQuery(String normalized) {
        return normalized.contains("重なり")
                || normalized.contains("コンフリクト")
                || normalized.contains("衝突")
                || normalized.contains("重複")
                || normalized.contains("conflict")
                || normalized.contains("overlap");
    }

    private String resolveKeyToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        for (Map.Entry<String, String> entry : KEY_ALIASES.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        Matcher fMatcher = F_KEY_PATTERN.matcher(text);
        if (fMatcher.matches()) {
            return "Key_F" + fMatcher.group(1);
        }

        Matcher singleMatcher = SINGLE_CHAR_KEY_PATTERN.matcher(text);
        if (singleMatcher.matches()) {
            return "Key_" + singleMatcher.group(1).toUpperCase(Locale.ROOT);
        }

        Matcher bareMatcher = BARE_SINGLE_CHAR_PATTERN.matcher(text);
        if (bareMatcher.matches()) {
            return "Key_" + text.toUpperCase(Locale.ROOT);
        }

        return null;
    }

    private List<BindingSlotResultDto> findActionsByKey(Map<String, ReadOnlyBindingSlots> slotsMap, String keyToken) {
        List<BindingSlotResultDto> results = new ArrayList<>();
        boolean prefixMatch = keyToken.endsWith("_"); // e.g. Key_Numpad_

        for (Map.Entry<String, ReadOnlyBindingSlots> entry : slotsMap.entrySet()) {
            String actionId = entry.getKey();
            ReadOnlyBindingSlots slots = entry.getValue();

            boolean primaryMatch = matchesSlot(slots.primary(), keyToken, prefixMatch);
            boolean secondaryMatch = matchesSlot(slots.secondary(), keyToken, prefixMatch);

            if (primaryMatch || secondaryMatch) {
                results.add(toResultDto(actionId, slots));
                if (results.size() >= 5) {
                    break;
                }
            }
        }
        return results;
    }

    private boolean matchesSlot(ReadOnlyBindingSlot slot, String keyToken, boolean prefixMatch) {
        if (slot == null || slot.key() == null || slot.key().isBlank()) {
            return false;
        }
        if (prefixMatch) {
            if (slot.key().startsWith(keyToken)) return true;
            for (String mod : slot.modifiers()) {
                if (mod != null && mod.startsWith(keyToken)) return true;
            }
        } else {
            if (slot.key().equalsIgnoreCase(keyToken)) return true;
            for (String mod : slot.modifiers()) {
                if (mod != null && mod.equalsIgnoreCase(keyToken)) return true;
            }
        }
        return false;
    }

    private List<BindingSlotResultDto> findActionsByOperationName(
            Map<String, ReadOnlyBindingSlots> slotsMap,
            String rawQuery,
            String normalized) {

        String cleaned = cleanQuery(normalized);
        Set<String> targetActionIds = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> synonym : JAPANESE_OPERATION_SYNONYMS.entrySet()) {
            if (normalized.contains(synonym.getKey()) || (cleaned != null && cleaned.contains(synonym.getKey()))) {
                targetActionIds.addAll(synonym.getValue());
            }
        }

        List<ScoredAction> scored = new ArrayList<>();
        for (String actionId : slotsMap.keySet()) {
            BindingDisplayNames.ControlName controlName = BindingDisplayNames.lookup(actionId);
            int score = 0;

            if (targetActionIds.contains(actionId)) {
                score += 100;
            }

            if (!cleaned.isBlank()) {
                String idLower = actionId.toLowerCase(Locale.ROOT);
                String labelLower = controlName.label().toLowerCase(Locale.ROOT);
                String nameLower = controlName.name().toLowerCase(Locale.ROOT);

                if (idLower.equals(cleaned) || nameLower.equals(cleaned)) {
                    score += 80;
                } else if (idLower.startsWith(cleaned) || nameLower.startsWith(cleaned)) {
                    score += 50;
                } else if (idLower.contains(cleaned) || nameLower.contains(cleaned) || labelLower.contains(cleaned)) {
                    score += 30;
                }
            }

            if (score > 0) {
                scored.add(new ScoredAction(actionId, score, controlName.order()));
            }
        }

        scored.sort(Comparator.comparingInt(ScoredAction::score).reversed()
                .thenComparingInt(ScoredAction::order));

        List<BindingSlotResultDto> results = new ArrayList<>();
        for (ScoredAction sa : scored) {
            ReadOnlyBindingSlots slots = slotsMap.get(sa.actionId);
            if (slots != null) {
                results.add(toResultDto(sa.actionId, slots));
                if (results.size() >= 5) {
                    break;
                }
            }
        }
        return results;
    }

    private String cleanQuery(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)(のキーは何|は何のキー|のキーは|のキー|キーは何|は何の割り当て|の割り当て|キー|key)", "")
                .trim();
    }

    private BindingSlotResultDto toResultDto(String actionId, ReadOnlyBindingSlots slots) {
        String label = BindingDisplayNames.label(actionId);
        BindingDisplayNames.ControlName control = BindingDisplayNames.lookup(actionId);
        SlotDetailDto primary = toSlotDetail(slots.primary());
        SlotDetailDto secondary = toSlotDetail(slots.secondary());
        return new BindingSlotResultDto(actionId, label, control.section().name(), control.group(), primary, secondary);
    }

    private SlotDetailDto toSlotDetail(ReadOnlyBindingSlot slot) {
        if (slot == null || "{NoDevice}".equalsIgnoreCase(slot.device()) || slot.key() == null || slot.key().isBlank()) {
            return null;
        }
        String spoken;
        if ("Keyboard".equalsIgnoreCase(slot.device())) {
            Set<String> chord = new LinkedHashSet<>();
            if (slot.modifiers() != null) {
                for (String m : slot.modifiers()) {
                    if (m != null && !m.isBlank()) chord.add(m);
                }
            }
            chord.add(slot.key());
            spoken = BindingChordSpeech.describe(chord);
        } else {
            spoken = slot.device() + " " + slot.key();
        }
        return new SlotDetailDto(slot.device(), slot.key(), List.of(slot.modifiers()), spoken, slot.hold());
    }

    private String formatKeyDisplayName(String keyToken) {
        if (keyToken.startsWith("Key_")) {
            String bare = keyToken.substring(4);
            return bare + " キー";
        }
        return keyToken;
    }

    private record ScoredAction(String actionId, int score, int order) {
    }

    public static class DataDto implements ToYamlConvertable {
        private final String type;
        private final String query;
        private final String message;
        private final List<ConflictItemDto> conflicts;
        private final List<SingleModifierConflictItemDto> singleModifierConflicts;
        private final List<BindingSlotResultDto> results;

        public DataDto(String type, String query, String message,
                       List<ConflictItemDto> conflicts, List<BindingSlotResultDto> results) {
            this(type, query, message, conflicts, List.of(), results);
        }

        public DataDto(String type, String query, String message,
                       List<ConflictItemDto> conflicts,
                       List<SingleModifierConflictItemDto> singleModifierConflicts,
                       List<BindingSlotResultDto> results) {
            this.type = type;
            this.query = query;
            this.message = message;
            this.conflicts = conflicts != null ? conflicts : List.of();
            this.singleModifierConflicts = singleModifierConflicts != null ? singleModifierConflicts : List.of();
            this.results = results != null ? results : List.of();
        }

        public String type() { return type; }
        public String query() { return query; }
        public String message() { return message; }
        public List<ConflictItemDto> conflicts() { return conflicts; }
        public List<SingleModifierConflictItemDto> singleModifierConflicts() { return singleModifierConflicts; }
        public List<BindingSlotResultDto> results() { return results; }

        @Override
        public String toYaml() {
            return YamlFactory.toYaml(this);
        }
    }

    public record ConflictItemDto(
            String actionA,
            String actionAName,
            String actionB,
            String actionBName,
            String chord,
            String description,
            boolean blocking
    ) {
    }

    public record SingleModifierConflictItemDto(
            String bareAction,
            String bareActionName,
            String modifierKey,
            String modifierSpoken,
            String chordAction,
            String chordActionName,
            String chordSpoken,
            String description
    ) {
    }

    public record BindingSlotResultDto(
            String actionId,
            String label,
            String section,
            String group,
            SlotDetailDto primary,
            SlotDetailDto secondary
    ) {
    }

    public record SlotDetailDto(
            String device,
            String key,
            List<String> modifiers,
            String spoken,
            boolean hold
    ) {
    }
}
