package elite.intel.gameapi.engineers;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EngineerModuleMatcherTest {

    private final EngineerModuleMatcher matcher = EngineerModuleMatcher.getInstance();

    @Test
    void all47ShipModulesCanBeMatchedByEnglishName() {
        Set<String> modules = matcher.getAllShipModules();
        assertEquals(47, modules.size(), "Ship module count in engineers.json must be exactly 47");

        for (String mod : modules) {
            Optional<String> matched = matcher.matchModule(mod);
            assertTrue(matched.isPresent(), "Must match English name: " + mod);
            assertEquals(mod, matched.get(), "Matched name must match canonical engineers.json name");

            // Also match lowercase
            Optional<String> matchedLower = matcher.matchModule(mod.toLowerCase());
            assertTrue(matchedLower.isPresent(), "Must match lowercase: " + mod.toLowerCase());
            assertEquals(mod, matchedLower.get());
        }
    }

    @Test
    void matchesJapanesePhrasesAndColloquials() {
        // 「FSD を上げるエンジニア」→ Frame Shift Drive
        Optional<String> fsd = matcher.findMentionedModule("FSD を上げるエンジニア");
        assertTrue(fsd.isPresent());
        assertEquals("Frame Shift Drive", fsd.get());

        // 「シールドブースター」→ Shield Booster
        Optional<String> sb = matcher.findMentionedModule("シールドブースターの改造");
        assertTrue(sb.isPresent());
        assertEquals("Shield Booster", sb.get());

        // 「シールド」→ Shield Generator
        Optional<String> sg = matcher.findMentionedModule("シールドを強化したい");
        assertTrue(sg.isPresent());
        assertEquals("Shield Generator", sg.get());

        // 「マルチキャノン」→ Multi-cannon
        Optional<String> mc = matcher.findMentionedModule("マルチキャノンを作れる人");
        assertTrue(mc.isPresent());
        assertEquals("Multi-cannon", mc.get());

        // 「装甲」→ Armour
        Optional<String> armour = matcher.findMentionedModule("装甲を厚くする");
        assertTrue(armour.isPresent());
        assertEquals("Armour", armour.get());

        // 「カーゴスキャナー」→ Manifest Scanner
        Optional<String> cs = matcher.findMentionedModule("カーゴスキャナーのエンジニア");
        assertTrue(cs.isPresent());
        assertEquals("Manifest Scanner", cs.get());
    }

    @Test
    void longestMatchIsPrioritized() {
        // "シールドブースターのエンジニア" contains both "シールド" (Shield Generator) and "シールドブースター" (Shield Booster)
        // Must match Shield Booster
        Optional<String> match = matcher.findMentionedModule("シールドブースターのエンジニアは");
        assertTrue(match.isPresent());
        assertEquals("Shield Booster", match.get());
    }
}
