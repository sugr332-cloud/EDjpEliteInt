package elite.intel.ai.mouth;

import elite.intel.i18n.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kokoro is the only engine that can fail to voice a language outright, and Cyrillic and Japanese are the
 * only languages it fails on - Cyrillic because its phonemizer has no front end for the script at all,
 * Japanese because the bundled model's Japanese speakers are held out and its phonemizer language table has
 * no Japanese entry (see {@link TtsProvider#canVoice}). The rule is pinned here because three places lean on
 * it - the stored setting the session hands back, the mouth the factory builds, and the segment the settings
 * panel withdraws - and all three would degrade into silence, or into reading the wrong script with the wrong
 * pronunciation rules, if it drifted.
 */
class TtsProviderLanguageTest {

    @Test
    void onlyKokoroIsBeatenByCyrillicOrJapanese() {
        for (Language language : Language.values()) {
            assertTrue(TtsProvider.EDGE.canVoice(language), "Edge carries every language: " + language);
            assertTrue(TtsProvider.GOOGLE.canVoice(language), "Google carries every language: " + language);
            boolean beatsKokoro = language.isCyrillicScript() || language == Language.JA;
            assertEquals(!beatsKokoro, TtsProvider.KOKORO.canVoice(language), "Kokoro against " + language);
        }
    }

    @Test
    void kokoroCannotVoiceJapanese() {
        assertFalse(TtsProvider.KOKORO.canVoice(Language.JA),
                "Japanese speakers are held out of the cast and the phonemizer has no Japanese entry");
    }

    @Test
    void googleAndEdgeVoiceJapanese() {
        assertTrue(TtsProvider.GOOGLE.canVoice(Language.JA));
        assertTrue(TtsProvider.EDGE.canVoice(Language.JA));
    }

    @Test
    void anEngineThatCannotSpeakTheLanguageIsReplacedByTheKeylessOne() {
        for (Language language : Language.values()) {
            for (TtsProvider selected : TtsProvider.values()) {
                TtsProvider resolved = TtsProvider.forLanguage(selected, language);
                assertTrue(resolved.canVoice(language), selected + " under " + language + " resolved to " + resolved);
                if (selected.canVoice(language)) {
                    assertEquals(selected, resolved, "a usable selection is never second-guessed: " + selected);
                } else {
                    assertEquals(TtsProvider.EDGE, resolved,
                            "the stand-in must be keyless, not a paid account: " + language);
                }
            }
        }
    }

    /**
     * Google speaks Cyrillic, so a Russian commander who pays for it keeps it: this rule withdraws Kokoro, not
     * the cloud.
     */
    @Test
    void googleSurvivesACyrillicLanguage() {
        assertEquals(TtsProvider.GOOGLE, TtsProvider.forLanguage(TtsProvider.GOOGLE, Language.RU));
        assertEquals(TtsProvider.GOOGLE, TtsProvider.forLanguage(TtsProvider.GOOGLE, Language.UK));
        assertFalse(TtsProvider.KOKORO.canVoice(Language.RU));
    }
}
