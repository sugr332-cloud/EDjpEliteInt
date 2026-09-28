package elite.intel.ai.brain.vega.trainingphrases;

import elite.intel.ai.brain.actions.handlers.commands.CommandRegistry;
import elite.intel.ai.brain.actions.handlers.queries.QueryRegistry;
import elite.intel.ai.brain.vega.model.IntelActionCategory;
import elite.intel.ai.brain.vega.prompt.AliasEmbeddingText;
import elite.intel.ai.brain.vega.prompt.GameToolCandidates;
import elite.intel.ai.embed.SemanticPhraseMatcher;
import elite.intel.ai.embed.SemanticSearchProvider;
import elite.intel.db.util.Database;
import elite.intel.i18n.Language;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;
import elite.intel.session.SystemSession;
import elite.intel.util.Cypher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards routing to start_piston_mode and stop_piston_mode (PT-4):
 * Verifies that piston mode utterances ("ピストン輸送開始", "交易候補 1 でピストン輸送")
 * route to start_piston_mode as offered candidate, "ピストン輸送終了" routes to stop_piston_mode,
 * and "ピストン先へ" remains ranked with navigate_to_previous_station.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PistonModeRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String START_PISTON_MODE = "start_piston_mode";
    private static final String STOP_PISTON_MODE = "stop_piston_mode";
    private static final String NAVIGATE_TO_PREVIOUS_STATION = "navigate_to_previous_station";

    private SemanticPhraseMatcher matcher;
    private Language originalLanguage;

    @BeforeAll
    void boot() throws Exception {
        originalLanguage = SystemSession.getInstance().getLanguage();
        Cypher.initializeKey();
        Database.init().close();
        CommandRegistry.getInstance().load();
        QueryRegistry.getInstance().load();
        matcher = SemanticSearchProvider.matcher();
    }

    @AfterAll
    void tearDown() {
        SystemSession.getInstance().setLanguage(originalLanguage);
    }

    @ParameterizedTest(name = "JA \"{0}\" offers start_piston_mode in shortlist")
    @ValueSource(strings = {
            "ピストン輸送開始",
            "交易候補 1 でピストン輸送"
    })
    void startPistonModePhrasesOfferStartPistonMode(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int startIdx = ranked.ids.indexOf(START_PISTON_MODE);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(startIdx >= 0 && ranked.scores.get(startIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, START_PISTON_MODE, startIdx < 0 ? -1.0 : ranked.scores.get(startIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
    }

    @Test
    void stopPistonModePhraseOffersStopPistonMode() {
        String utterance = "ピストン輸送終了";
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int stopIdx = ranked.ids.indexOf(STOP_PISTON_MODE);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(stopIdx >= 0 && ranked.scores.get(stopIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, STOP_PISTON_MODE, stopIdx < 0 ? -1.0 : ranked.scores.get(stopIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
    }

    @Test
    void pistonDestinationPhraseRanksNavigateToPreviousStationAboveStartPistonMode() {
        String utterance = "ピストン先へ";
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int prevStationIdx = ranked.ids.indexOf(NAVIGATE_TO_PREVIOUS_STATION);
        int startPistonIdx = ranked.ids.indexOf(START_PISTON_MODE);

        assertTrue(prevStationIdx >= 0, "navigate_to_previous_station should be present in ranked candidates");
        assertTrue(startPistonIdx < 0 || prevStationIdx < startPistonIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked %s (#%d, %.3f) above %s (#%d, %.3f)",
                utterance, START_PISTON_MODE, startPistonIdx, startPistonIdx >= 0 ? ranked.scores.get(startPistonIdx) : -1.0,
                NAVIGATE_TO_PREVIOUS_STATION, prevStationIdx, prevStationIdx >= 0 ? ranked.scores.get(prevStationIdx) : -1.0));
    }

    private Ranked rank(Language language, PlayerSituation situation, String utterance) {
        SystemSession.getInstance().setLanguage(language);
        List<GameToolCandidates.Candidate> catalog = new GameToolCandidates(Status.detached(situation)).collect(ALL);
        float[] query = matcher.embedQuery(utterance);
        double[] scores = new double[catalog.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            GameToolCandidates.Candidate candidate = catalog.get(i);
            scores[i] = matcher.bestSimilarity(query,
                    AliasEmbeddingText.phrases(candidate.localizedAliasGroup(), candidate.tool().parameters()));
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(scores[b], scores[a]));
        Ranked ranked = new Ranked();
        for (int i : order) {
            ranked.ids.add(catalog.get(i).id());
            ranked.scores.add(scores[i]);
        }
        return ranked;
    }

    private static final class Ranked {
        final List<String> ids = new ArrayList<>();
        final List<Double> scores = new ArrayList<>();
    }
}
