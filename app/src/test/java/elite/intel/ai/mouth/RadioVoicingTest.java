package elite.intel.ai.mouth;

import elite.intel.i18n.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins which engine voices radio. Kokoro's phonemizer has no Cyrillic front end, so a Russian or Ukrainian
 * commander would hear either silence or gibberish from it; Kokoro's Japanese speakers are also held out and
 * its phonemizer has no Japanese entry (see {@link TtsProvider#canVoice}), so Japanese fails the same way for
 * an unrelated reason. Edge is keyless and speaks all three, so it takes the channel there and nowhere else.
 */
class RadioVoicingTest {

    @Test
    void cyrillicOrJapaneseIsVoicedByEdgeAndEveryOtherLanguageByKokoro() {
        for (Language language : Language.values()) {
            boolean beatsKokoro = language.isCyrillicScript() || language == Language.JA;
            assertEquals(
                    beatsKokoro ? TtsProvider.EDGE : TtsProvider.KOKORO,
                    RadioVoicing.engineFor(language),
                    "radio engine for " + language);
        }
    }

    @Test
    void googleIsNeverARadioEngine() {
        for (Language language : Language.values()) {
            assertNotEquals(TtsProvider.GOOGLE, RadioVoicing.engineFor(language),
                    "radio is chatter, not narration - it must never bill a Google key: " + language);
        }
    }
}
