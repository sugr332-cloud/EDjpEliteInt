package elite.intel.ai.brain.commons;

import elite.intel.ai.brain.VegaIdentity;
import elite.intel.ai.mouth.TtsProvider;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the identity of the analysis prompt - the one every {@code Analyze*Query} handler speaks through.
 */
class PromptFactoryTest {

    private final PromptFactory factory = PromptFactory.getInstance();
    private Language originalLanguage;
    private TtsProvider originalProvider;

    @BeforeEach
    void setUp() {
        SystemSession session = SystemSession.getInstance();
        originalLanguage = session.getLanguage();
        originalProvider = session.getTtsProvider();
    }

    @AfterEach
    void tearDown() {
        SystemSession session = SystemSession.getInstance();
        session.setTtsProvider(originalProvider);
        session.setLanguage(originalLanguage);
    }

    /**
     * The analysis path is spoken, so it carries the same identity as VEGA prompts.
     *
     * <p>WHY: it used to open "You are {shipName}, a ship in Elite Dangerous - space sim game", which made the
     * model answer as the hull while VEGA prompt had it answering as an AI named Vega. Same session,
     * two speakers, depending only on whether the answer came from a query handler or from dialogue.
     */
    @Test
    void analysisPromptSpeaksAsVegaNotAsTheShip() {
        String prompt = factory.generateAnalysisPrompt();

        assertTrue(prompt.contains(VegaIdentity.identityClause()),
                "the spoken analysis prompt must open with the shared identity clause");
        assertFalse(prompt.contains("a ship in Elite Dangerous"),
                "the model must never be told it is the ship");
    }

    /**
     * Identity travels with personality: a style clause on its own ("respond as a close friend", "full chaos
     * mode") leaves the model to infer who is speaking, and inferring a person is exactly the failure mode.
     */
    @Test
    void personalityBlockRestatesWhoIsSpeaking() {
        String prompt = factory.generateAnalysisPrompt();
        int personality = prompt.indexOf("Personality: ");

        assertTrue(personality >= 0, "the analysis prompt must carry a personality block");
        assertTrue(prompt.indexOf(VegaIdentity.identityClause(), personality) > personality,
                "the personality block must be preceded by the identity clause, not stand alone");
    }

    @Test
    void japaneseResponseLanguageInstructsArabicNumeralsInsteadOfSpellOut() {
        SystemSession session = SystemSession.getInstance();
        session.setLanguage(Language.JA);

        String prompt = factory.generateAnalysisPrompt();

        assertTrue(prompt.contains("Write numbers as Arabic numerals with thousands separators (e.g., 17,070,320). Do not write numbers in kanji or kana."),
                "Japanese analysis prompt must instruct Arabic numerals with thousands separators and no kanji/kana");
        assertFalse(prompt.contains("Spell out numerals"),
                "Japanese analysis prompt must not instruct spelling out numerals");
    }

    @Test
    void englishResponseLanguageInstructsSpellingOutNumerals() {
        SystemSession session = SystemSession.getInstance();
        session.setLanguage(Language.EN);

        String prompt = factory.generateAnalysisPrompt();

        assertTrue(prompt.contains("Spell out numerals (e.g., twenty-three, not 23)."),
                "English analysis prompt must keep the classic spell out numerals rule");
        assertFalse(prompt.contains("Write numbers as Arabic numerals with thousands separators"),
                "English analysis prompt must not include Arabic numerals instruction");
    }
}
