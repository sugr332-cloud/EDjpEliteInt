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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards routing to {@code navigate_to_search_result} (P8-9):
 * Short and natural Japanese commander phrasings like "ランク一の購入地" or "2位の売却地へ航路"
 * must be offered to the LLM even when phrasing is concise, contains kanji numerals, or situation is Unknown.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NavigateToSearchResultRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private SemanticPhraseMatcher matcher;

    @BeforeAll
    void boot() throws Exception {
        Cypher.initializeKey();
        Database.init().close();
        CommandRegistry.getInstance().load();
        QueryRegistry.getInstance().load();
        matcher = SemanticSearchProvider.matcher();
    }

    @ParameterizedTest(name = "JA \"{0}\" offers navigate_to_search_result")
    @ValueSource(strings = {
            "ランク一の購入地",
            "ランク1の購入地に案内して",
            "ランク二の売却地",
            "2位の売却地へ航路",
            "ランク3の購入先へ"
    })
    void japanesePhrasesOfferNavigateToSearchResult(String utterance) {
        assertOffered(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance, "navigate_to_search_result");
    }

    @Test
    void offersNavigateToSearchResultEvenWhenSituationIsUnknown() {
        // P8-9 (4): When situation is Unknown, isInMainShip() was false and hid the command in real machine.
        // isVisibleForLLM is now true unconditionally so the command is offered and execute refuses with explanation.
        assertOffered(Language.JA, PlayerSituation.UNKNOWN, "ランク一の購入地", "navigate_to_search_result");
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
