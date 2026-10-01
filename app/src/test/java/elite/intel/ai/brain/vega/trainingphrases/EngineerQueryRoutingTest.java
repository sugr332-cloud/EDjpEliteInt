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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards routing to {@code query_engineer} (EG-2):
 * Verifies that engineer-related questions in Japanese and English route to query_engineer in shortlist.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EngineerQueryRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

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

    @ParameterizedTest(name = "JA \"{0}\" offers query_engineer in shortlist")
    @ValueSource(strings = {
            "FSD を上げるエンジニアは",
            "エンジニアの進み具合は",
            "開放済みのエンジニアは",
            "フェリシティ・ファーシーアの開放条件は",
            "エンジニアの一覧",
            "エンジニアはどこ",
            "エンジニアの効能",
            "エンジニアの得意分野",
            "エンジニアは何ができる？",
            "船のエンジニアの得意分野",
            "徒歩のエンジニアは何ができる？",
            "エンジニアの一覧と効能を教えて",
            "FSD に関係するエンジニアは誰が居る？",
            "シールドに関係するエンジニアは誰が居る"
    })
    void japanesePhrasesOfferQueryEngineer(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int queryIdx = ranked.ids.indexOf(QUERY_ENGINEER);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(queryIdx >= 0 && ranked.scores.get(queryIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, QUERY_ENGINEER, queryIdx < 0 ? -1.0 : ranked.scores.get(queryIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
    }

    @ParameterizedTest(name = "EN \"{0}\" offers query_engineer in shortlist")
    @ValueSource(strings = {
            "which engineer upgrades FSD",
            "engineer for thrusters",
            "unlocked engineers",
            "where is the engineer",
            "engineer unlock requirements",
            "what can engineers do",
            "engineer specialties",
            "ship engineer specialties",
            "on-foot engineer specialties",
            "what can on-foot engineers do",
            "list engineers and their specialties",
            "which engineers work on the frame shift drive"
    })
    void englishPhrasesOfferQueryEngineer(String utterance) {
        Ranked ranked = rank(Language.EN, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int queryIdx = ranked.ids.indexOf(QUERY_ENGINEER);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(queryIdx >= 0 && ranked.scores.get(queryIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, QUERY_ENGINEER, queryIdx < 0 ? -1.0 : ranked.scores.get(queryIdx), cutoff,
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
