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
 * Guards routing to auto_dock and request_docking (PT-5):
 * Verifies that auto-docking utterances ("自動ドッキング", "ドッキングして", "ステーションにドッキング")
 * route to auto_dock as offered candidate and ranked above request_docking,
 * while "ドッキングを要求" ranks request_docking above auto_dock.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoDockRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String AUTO_DOCK = "auto_dock";
    private static final String REQUEST_DOCKING = "request_docking";

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

    @ParameterizedTest(name = "JA \"{0}\" offers auto_dock and ranks it above request_docking")
    @ValueSource(strings = {
            "自動ドッキング",
            "ドッキングして",
            "ステーションにドッキング"
    })
    void autoDockPhrasesOfferAutoDockAboveRequestDocking(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        // 1. auto_dock must be in offered shortlist
        int autoDockIdx = ranked.ids.indexOf(AUTO_DOCK);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(autoDockIdx >= 0 && ranked.scores.get(autoDockIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, AUTO_DOCK, autoDockIdx < 0 ? -1.0 : ranked.scores.get(autoDockIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));

        // 2. auto_dock must be ranked above request_docking
        int requestDockingIdx = ranked.ids.indexOf(REQUEST_DOCKING);
        assertTrue(requestDockingIdx < 0 || autoDockIdx < requestDockingIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked request_docking (#%d, %.3f) above auto_dock (#%d, %.3f)",
                utterance, requestDockingIdx, requestDockingIdx >= 0 ? ranked.scores.get(requestDockingIdx) : -1.0,
                autoDockIdx, autoDockIdx >= 0 ? ranked.scores.get(autoDockIdx) : -1.0));
    }

    @Test
    void requestDockingPhraseRanksRequestDockingAboveAutoDock() {
        String utterance = "ドッキングを要求";
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int requestDockingIdx = ranked.ids.indexOf(REQUEST_DOCKING);
        int autoDockIdx = ranked.ids.indexOf(AUTO_DOCK);

        assertTrue(requestDockingIdx >= 0, "Request docking should be present in ranked candidates");
        assertTrue(autoDockIdx < 0 || requestDockingIdx < autoDockIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked auto_dock (#%d, %.3f) above request_docking (#%d, %.3f)",
                utterance, autoDockIdx, autoDockIdx >= 0 ? ranked.scores.get(autoDockIdx) : -1.0,
                requestDockingIdx, requestDockingIdx >= 0 ? ranked.scores.get(requestDockingIdx) : -1.0));
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
