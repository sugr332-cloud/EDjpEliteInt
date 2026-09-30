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
 * Guards routing to {@code query_key_binding} (LF-2):
 * Verifies that key binding question phrases route to query_key_binding in shortlist,
 * and command phrases like "ジャンプして" route to jump_to_hyperspace above query_key_binding.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeyBindingQueryRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String QUERY_KEY_BINDING = "query_key_binding";
    private static final String JUMP_TO_HYPERSPACE = "jump_to_hyperspace";

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

    @ParameterizedTest(name = "JA \"{0}\" offers query_key_binding in shortlist")
    @ValueSource(strings = {
            "ジャンプのキーは",
            "サイレントランニングは何のキー",
            "J キーは何",
            "左コントロールに何が割り当てられている",
            "キーの重なりを教えて",
            "キー設定を教えて"
    })
    void keyBindingPhrasesOfferQueryKeyBinding(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int queryIdx = ranked.ids.indexOf(QUERY_KEY_BINDING);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(queryIdx >= 0 && ranked.scores.get(queryIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, QUERY_KEY_BINDING, queryIdx < 0 ? -1.0 : ranked.scores.get(queryIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
    }

    @Test
    void jumpCommandPhraseRanksJumpToHyperspaceAboveKeyBindingQuery() {
        String utterance = "ジャンプして";
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int jumpIdx = ranked.ids.indexOf(JUMP_TO_HYPERSPACE);
        int queryIdx = ranked.ids.indexOf(QUERY_KEY_BINDING);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;

        assertTrue(jumpIdx >= 0 && ranked.scores.get(jumpIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, JUMP_TO_HYPERSPACE, jumpIdx < 0 ? -1.0 : ranked.scores.get(jumpIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));

        assertTrue(queryIdx < 0 || jumpIdx < queryIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked %s (#%d, %.3f) above %s (#%d, %.3f)",
                utterance, QUERY_KEY_BINDING, queryIdx, queryIdx >= 0 ? ranked.scores.get(queryIdx) : -1.0,
                JUMP_TO_HYPERSPACE, jumpIdx, jumpIdx >= 0 ? ranked.scores.get(jumpIdx) : -1.0));
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
