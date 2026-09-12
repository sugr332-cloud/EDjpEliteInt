package elite.intel.ai.brain.commons;

import elite.intel.ai.mouth.TtsProvider;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;

public final class AiResponseLanguagePolicy {

    private AiResponseLanguagePolicy() {
    }


    /**
     * Resolves the effective AI response language based on the system session configuration
     * and available Text-to-Speech (TTS) settings.
     * <p>
     * Delegates the actual capability question to {@link TtsProvider#canVoice}, the single place that decides
     * whether an engine can voice a language, rather than repeating that decision here: this method used to
     * carry its own copy of the Kokoro/Cyrillic rule, and a second copy is exactly how a case {@code canVoice}
     * already knows about (Japanese - see its Javadoc) went unhandled here for a time. Google and Edge voice
     * every language we ship, so they never fall through; Kokoro's few gaps (Cyrillic, Japanese) fall back to
     * English.
     * <p>
     * In practice a commander in one of Kokoro's gap languages no longer reaches that fallback for long:
     * {@code SystemSession.getTtsProvider()} withdraws Kokoro from them entirely (see
     * {@link TtsProvider#forLanguage}), so they are on Edge or Google and are answered in their own language.
     * The English branch stays as the guard that makes this method true of any engine and any stored setting,
     * not only of today's three providers.
     *
     * @param systemSession the session containing system language and TTS configuration details
     * @return the session's language, except when the configured TTS cannot voice it, in which case English
     */
    public static Language resolveEffectiveAiResponseLanguage(SystemSession systemSession) {
        Language sessionLanguage = systemSession.getLanguage();
        TtsProvider provider = systemSession.getTtsProvider();
        return provider.canVoice(sessionLanguage) ? sessionLanguage : Language.EN;
    }

    public static boolean isGoogleTtsConfiguredAndUsable(SystemSession systemSession) {
        return systemSession.getTtsProvider() == TtsProvider.GOOGLE;
    }
}
