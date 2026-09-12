package elite.intel.ai.brain.commons;

import elite.intel.ai.mouth.TtsProvider;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiResponseLanguagePolicyTest {
    /**
     * The end of the Cyrillic English-fallback in practice: Kokoro is withdrawn from a Cyrillic commander at
     * the session read, so even a database that still stores it answers them in their own language rather than
     * in English.
     */
    @Test
    void aStoredKokoroNoLongerForcesEnglishOnACyrillicCommander() {
        SystemSession session = SystemSession.getInstance();
        TtsProvider previousProvider = session.getTtsProvider();
        Language previousLanguage = session.getLanguage();
        try {
            session.setTtsProvider(TtsProvider.KOKORO);
            session.setLanguage(Language.RU);

            assertEquals(TtsProvider.EDGE, session.getTtsProvider(), "Kokoro cannot voice Cyrillic");
            assertEquals(Language.RU, AiResponseLanguagePolicy.resolveEffectiveAiResponseLanguage(session));
        } finally {
            session.setTtsProvider(previousProvider);
            session.setLanguage(previousLanguage);
        }
    }

    @Test
    void edgeCloudTtsKeepsTheConfiguredCyrillicLanguage() {
        SystemSession session = SystemSession.getInstance();
        TtsProvider previousProvider = session.getTtsProvider();
        Language previousLanguage = session.getLanguage();
        try {
            session.setTtsProvider(TtsProvider.EDGE);
            session.setLanguage(Language.UK);

            assertEquals(Language.UK, AiResponseLanguagePolicy.resolveEffectiveAiResponseLanguage(session));
        } finally {
            session.setTtsProvider(previousProvider);
            session.setLanguage(previousLanguage);
        }
    }

    /**
     * The Japanese counterpart of {@link #aStoredKokoroNoLongerForcesEnglishOnACyrillicCommander}: Kokoro is
     * withdrawn from a Japanese commander at the session read for the same reason (see
     * {@link elite.intel.ai.mouth.TtsProvider#canVoice}), so this policy answers them in Japanese rather than
     * falling back to English - the fallback this test exists to catch would have silently reappeared if
     * {@code resolveEffectiveAiResponseLanguage} still carried its own separate copy of "which languages
     * Kokoro cannot voice" instead of asking {@code TtsProvider} directly.
     */
    @Test
    void aStoredKokoroNoLongerForcesEnglishOnAJapaneseCommander() {
        SystemSession session = SystemSession.getInstance();
        TtsProvider previousProvider = session.getTtsProvider();
        Language previousLanguage = session.getLanguage();
        try {
            session.setTtsProvider(TtsProvider.KOKORO);
            session.setLanguage(Language.JA);

            assertEquals(TtsProvider.EDGE, session.getTtsProvider(), "Kokoro cannot voice Japanese");
            assertEquals(Language.JA, AiResponseLanguagePolicy.resolveEffectiveAiResponseLanguage(session));
        } finally {
            session.setTtsProvider(previousProvider);
            session.setLanguage(previousLanguage);
        }
    }

    @Test
    void edgeCloudTtsKeepsTheConfiguredJapaneseLanguage() {
        SystemSession session = SystemSession.getInstance();
        TtsProvider previousProvider = session.getTtsProvider();
        Language previousLanguage = session.getLanguage();
        try {
            session.setTtsProvider(TtsProvider.EDGE);
            session.setLanguage(Language.JA);

            assertEquals(Language.JA, AiResponseLanguagePolicy.resolveEffectiveAiResponseLanguage(session));
        } finally {
            session.setTtsProvider(previousProvider);
            session.setLanguage(previousLanguage);
        }
    }
}
