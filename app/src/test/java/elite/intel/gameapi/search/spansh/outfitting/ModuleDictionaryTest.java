package elite.intel.gameapi.search.spansh.outfitting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleDictionaryTest {

    private static ModuleDictionary dictionary;

    @BeforeAll
    public static void setUp() {
        dictionary = ModuleDictionary.getInstance();
    }

    @Test
    void testStandardModuleWithClassAndRating() {
        // "5A FSD"
        Optional<MatchedModule> m1 = dictionary.match("5A FSD");
        assertTrue(m1.isPresent());
        assertEquals("Frame Shift Drive", m1.get().canonicalName());
        assertEquals(5, m1.get().moduleClass());
        assertEquals("A", m1.get().rating());

        // "5a フレームシフトドライブ"
        Optional<MatchedModule> m2 = dictionary.match("5a フレームシフトドライブ");
        assertTrue(m2.isPresent());
        assertEquals("Frame Shift Drive", m2.get().canonicalName());
        assertEquals(5, m2.get().moduleClass());
        assertEquals("A", m2.get().rating());

        // "Frame Shift Drive 5A"
        Optional<MatchedModule> m3 = dictionary.match("Frame Shift Drive 5A");
        assertTrue(m3.isPresent());
        assertEquals("Frame Shift Drive", m3.get().canonicalName());
        assertEquals(5, m3.get().moduleClass());
        assertEquals("A", m3.get().rating());
    }

    @Test
    void testModuleWithoutClassAndRating() {
        // "燃料スクープ" -> name only
        Optional<MatchedModule> m = dictionary.match("燃料スクープ");
        assertTrue(m.isPresent());
        assertEquals("Fuel Scoop", m.get().canonicalName());
        assertNull(m.get().moduleClass());
        assertNull(m.get().rating());

        // "Fuel Scoop" -> name only
        Optional<MatchedModule> m2 = dictionary.match("Fuel Scoop");
        assertTrue(m2.isPresent());
        assertEquals("Fuel Scoop", m2.get().canonicalName());
        assertNull(m2.get().moduleClass());
        assertNull(m2.get().rating());
    }

    @Test
    void testClassRange0To8AndRatingRangeAToI() {
        // Class 0, Rating I
        Optional<MatchedModule> m0i = dictionary.match("0I FSD");
        assertTrue(m0i.isPresent());
        assertEquals(0, m0i.get().moduleClass());
        assertEquals("I", m0i.get().rating());

        // Class 8, Rating A
        Optional<MatchedModule> m8a = dictionary.match("8A FSD");
        assertTrue(m8a.isPresent());
        assertEquals(8, m8a.get().moduleClass());
        assertEquals("A", m8a.get().rating());

        // Class 3, Rating C
        Optional<MatchedModule> m3c = dictionary.match("3C スラスター");
        assertTrue(m3c.isPresent());
        assertEquals("Thrusters", m3c.get().canonicalName());
        assertEquals(3, m3c.get().moduleClass());
        assertEquals("C", m3c.get().rating());
    }

    @Test
    void testOutOfRangeIsUnknown() {
        // 9A is out of class range (0-8)
        Optional<MatchedModule> m9a = dictionary.match("9A FSD");
        assertTrue(m9a.isEmpty(), "9A FSD should be UNKNOWN");

        // 5Z is out of rating range (A-I)
        Optional<MatchedModule> m5z = dictionary.match("5Z FSD");
        assertTrue(m5z.isEmpty(), "5Z FSD should be UNKNOWN");

        // 10A
        Optional<MatchedModule> m10a = dictionary.match("10A FSD");
        assertTrue(m10a.isEmpty(), "10A FSD should be UNKNOWN");
    }

    @Test
    void testShieldAloneIsUnknown() {
        // "シールド" alone must NOT match Shield Generator (rule 3)
        Optional<MatchedModule> shield = dictionary.match("シールド");
        assertTrue(shield.isEmpty(), "シールド alone must be UNKNOWN");

        Optional<MatchedModule> shield5a = dictionary.match("5A シールド");
        assertTrue(shield5a.isEmpty(), "5A シールド must be UNKNOWN");

        // "シールドジェネレーター" must match Shield Generator
        Optional<MatchedModule> gen = dictionary.match("シールドジェネレーター");
        assertTrue(gen.isPresent());
        assertEquals("Shield Generator", gen.get().canonicalName());

        Optional<MatchedModule> gen5a = dictionary.match("5A シールドジェネレーター");
        assertTrue(gen5a.isPresent());
        assertEquals("Shield Generator", gen5a.get().canonicalName());
        assertEquals(5, gen5a.get().moduleClass());
        assertEquals("A", gen5a.get().rating());
    }

    @Test
    void testUnknownModuleName() {
        Optional<MatchedModule> unk = dictionary.match("5A 未知のモジュール");
        assertTrue(unk.isEmpty(), "Unknown module should be UNKNOWN");

        Optional<MatchedModule> empty = dictionary.match("");
        assertTrue(empty.isEmpty());

        Optional<MatchedModule> nullVal = dictionary.match(null);
        assertTrue(nullVal.isEmpty());
    }

    @Test
    void testAliasParserRequirements() throws Exception {
        Map<String, String> canonMap = Map.of(
                "frame shift drive", "Frame Shift Drive",
                "fuel scoop", "Fuel Scoop"
        );

        // 1. Valid input with comments and blank lines
        String valid = """
                # Comment line
                
                   # Another comment with spaces
                fsd = Frame Shift Drive
                燃料スクープ = Fuel Scoop
                """;
        Map<String, String> parsed = ModuleDictionary.parseAliases(new java.io.StringReader(valid), canonMap);
        assertEquals(2, parsed.size());
        assertEquals("Frame Shift Drive", parsed.get("fsd"));
        assertEquals("Fuel Scoop", parsed.get("燃料スクープ"));

        // 2. Line missing '=' must throw IllegalStateException
        String missingEq = """
                # Valid line
                fsd = Frame Shift Drive
                invalid line without equals sign
                """;
        assertThrows(IllegalStateException.class, () ->
                ModuleDictionary.parseAliases(new java.io.StringReader(missingEq), canonMap));

        // 3. Duplicate key must throw IllegalStateException
        String duplicateKey = """
                fsd = Frame Shift Drive
                # duplicate
                fsd = Frame Shift Drive
                """;
        assertThrows(IllegalStateException.class, () ->
                ModuleDictionary.parseAliases(new java.io.StringReader(duplicateKey), canonMap));

        // 4. Empty key must throw IllegalStateException
        String emptyKey = """
                = Frame Shift Drive
                """;
        assertThrows(IllegalStateException.class, () ->
                ModuleDictionary.parseAliases(new java.io.StringReader(emptyKey), canonMap));
    }

    @Test
    void testAllAliasCanonicalTargetsExistInCanonicalModulesJson() throws Exception {
        java.util.Set<String> canonicalNames = new java.util.HashSet<>();
        try (var is = getClass().getResourceAsStream("/outfitting/spansh_canonical_modules.json")) {
            assertNotNull(is, "spansh_canonical_modules.json must exist");
            var root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var arr = root.getAsJsonArray("modules");
            for (var el : arr) {
                canonicalNames.add(el.getAsString().trim());
            }
        }
        assertFalse(canonicalNames.isEmpty(), "Canonical modules must not be empty");

        java.util.List<String> errors = new java.util.ArrayList<>();
        try (var is = getClass().getResourceAsStream("/outfitting/module_aliases_ja.properties")) {
            assertNotNull(is, "module_aliases_ja.properties must exist");
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = trimmed.indexOf('=');
                    assertTrue(eqIdx > 0, "Line " + lineNo + " must have '='");
                    String alias = trimmed.substring(0, eqIdx).trim();
                    String canonicalVal = trimmed.substring(eqIdx + 1).trim();
                    if (!canonicalNames.contains(canonicalVal)) {
                        errors.add("Line " + lineNo + " (" + alias + "): canonical module '" + canonicalVal + "' does not exist");
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), "Found non-existent canonical module mappings:\n" + String.join("\n", errors));
    }
}
