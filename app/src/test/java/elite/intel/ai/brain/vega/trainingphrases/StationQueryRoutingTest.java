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
 * Guards routing separation between queries (query_previous_station, query_current_station)
 * and action commands (navigate_to_previous_station, start_piston_mode) (LF-1).
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StationQueryRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String QUERY_PREVIOUS_STATION = "query_previous_station";
    private static final String QUERY_CURRENT_STATION = "query_current_station";
    private static final String NAVIGATE_TO_PREVIOUS_STATION = "navigate_to_previous_station";
    private static final String START_PISTON_MODE = "start_piston_mode";

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

    @ParameterizedTest(name = "JA \"{0}\" routes to query_previous_station above action commands")
    @ValueSource(strings = {
            "前のステーションはどこ",
            "前のステーションを教えて",
            "前回どこに着艦した"
    })
    void previousStationQueryPhrasesRankAboveActions(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int queryIdx = ranked.ids.indexOf(QUERY_PREVIOUS_STATION);
        int navIdx = ranked.ids.indexOf(NAVIGATE_TO_PREVIOUS_STATION);
        int pistonIdx = ranked.ids.indexOf(START_PISTON_MODE);

        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(queryIdx >= 0 && ranked.scores.get(queryIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, QUERY_PREVIOUS_STATION, queryIdx < 0 ? -1.0 : ranked.scores.get(queryIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));

        assertTrue(navIdx < 0 || queryIdx < navIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked %s (#%d, %.3f) above %s (#%d, %.3f)",
                utterance, NAVIGATE_TO_PREVIOUS_STATION, navIdx, navIdx >= 0 ? ranked.scores.get(navIdx) : -1.0,
                QUERY_PREVIOUS_STATION, queryIdx, queryIdx >= 0 ? ranked.scores.get(queryIdx) : -1.0));

        assertTrue(pistonIdx < 0 || queryIdx < pistonIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked %s (#%d, %.3f) above %s (#%d, %.3f)",
                utterance, START_PISTON_MODE, pistonIdx, pistonIdx >= 0 ? ranked.scores.get(pistonIdx) : -1.0,
                QUERY_PREVIOUS_STATION, queryIdx, queryIdx >= 0 ? ranked.scores.get(queryIdx) : -1.0));
    }

    @ParameterizedTest(name = "JA \"{0}\" offers query_current_station in shortlist")
    @ValueSource(strings = {
            "今いるステーションは",
            "今どこのステーションにいる"
    })
    void currentStationQueryPhrasesOfferQueryCurrentStation(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int queryIdx = ranked.ids.indexOf(QUERY_CURRENT_STATION);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(queryIdx >= 0 && ranked.scores.get(queryIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, QUERY_CURRENT_STATION, queryIdx < 0 ? -1.0 : ranked.scores.get(queryIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
    }

    @ParameterizedTest(name = "JA \"{0}\" offers navigate_to_previous_station in shortlist")
    @ValueSource(strings = {
            "前のステーションへ",
            "前のステーションに戻って"
    })
    void navigationPhrasesOfferNavigateToPreviousStation(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int navIdx = ranked.ids.indexOf(NAVIGATE_TO_PREVIOUS_STATION);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(navIdx >= 0 && ranked.scores.get(navIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, NAVIGATE_TO_PREVIOUS_STATION, navIdx < 0 ? -1.0 : ranked.scores.get(navIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
    }

    @Test
    void startPistonModePhraseOffersStartPistonMode() {
        String utterance = "ピストン輸送開始";
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int pistonIdx = ranked.ids.indexOf(START_PISTON_MODE);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(pistonIdx >= 0 && ranked.scores.get(pistonIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, START_PISTON_MODE, pistonIdx < 0 ? -1.0 : ranked.scores.get(pistonIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
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
