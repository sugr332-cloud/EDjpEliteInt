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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards routing to {@code navigate_to_engineer} (EG-4):
 * - Engineer navigation phrases in JA and EN offer navigate_to_engineer in shortlist.
 * - Unseen engineer names generalize to navigate_to_engineer.
 * - Question phrases still route to query_engineer.
 * - Existing commands (navigate_to_fleet_carrier, navigate_to_home_system,
 *   navigate_to_previous_station, navigate_to_search_result) are not preempted.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavigateToEngineerRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String NAVIGATE_TO_ENGINEER = "navigate_to_engineer";
    private static final String QUERY_ENGINEER = "query_engineer";

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

    @ParameterizedTest(name = "JA \"{0}\" offers navigate_to_engineer in shortlist")
    @ValueSource(strings = {
            "フェリシティ・ファーシーアのところへ向かって",
            "パリン教授のステーションへ",
            "FSD のエンジニアへ向かって",
            "1 番目のエンジニアへ",
            "エルビラ・マルトゥークのところへ向かって", // Unseen engineer name to test generalization
            "エンジニアのステーションへ"
    })
    void japanesePhrasesOfferNavigateToEngineer(String utterance) {
        assertOffered(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance, NAVIGATE_TO_ENGINEER);
    }

    @ParameterizedTest(name = "EN \"{0}\" offers navigate_to_engineer in shortlist")
    @ValueSource(strings = {
            "navigate to Felicity Farseer",
            "route to Professor Palin",
            "route to the engineer for FSD",
            "go to engineer 1",
            "route to the engineer"
    })
    void englishPhrasesOfferNavigateToEngineer(String utterance) {
        assertOffered(Language.EN, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance, NAVIGATE_TO_ENGINEER);
    }

    @ParameterizedTest(name = "JA question \"{0}\" still offers query_engineer")
    @ValueSource(strings = {
            "フェリシティ・ファーシーアの開放条件は",
            "エンジニアはどこ",
            "FSD を上げるエンジニアは",
            "エンジニアの進み具合は"
    })
    void engineerQuestionsStillRouteToQueryEngineer(String utterance) {
        assertOffered(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance, QUERY_ENGINEER);
    }

    @ParameterizedTest(name = "Utterance \"{0}\" routes to \"{1}\" not preempted by navigate_to_engineer")
    @CsvSource({
            "キャリアへ向かって, navigate_to_fleet_carrier",
            "自宅星系へ向かって, navigate_to_home_system",
            "前のステーションへ, navigate_to_previous_station",
            "ランク 1 の購入先へ航路, navigate_to_search_result"
    })
    void existingNavigationCommandsNotPreempted(String utterance, String expectedCommandId) {
        assertOffered(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance, expectedCommandId);
    }

    private void assertOffered(Language language, PlayerSituation situation, String utterance, String expectedId) {
        Ranked ranked = rank(language, situation, utterance);
        int at = ranked.ids.indexOf(expectedId);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(at >= 0 && ranked.scores.get(at) >= cutoff, () -> String.format(Locale.ROOT,
                "%s \"%s\" (situation=%s) left %s out of the shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                language, utterance, situation, expectedId, at < 0 ? -1.0 : ranked.scores.get(at), cutoff,
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
