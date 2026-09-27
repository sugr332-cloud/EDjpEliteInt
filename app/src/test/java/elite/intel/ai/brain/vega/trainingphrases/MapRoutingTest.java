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
 * Guards routing to galaxy map and system map (J-20):
 * Verifies that generic map utterances ("マップ", "マップ開いて", "マップを開いて")
 * route to display_open_galaxy_map as offered candidate and ranked above display_open_system_map,
 * while "システムマップを開いて" ranks display_open_system_map above galaxy map.
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MapRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String GALAXY_MAP = "display_open_galaxy_map";
    private static final String SYSTEM_MAP = "display_open_system_map";

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

    @ParameterizedTest(name = "JA \"{0}\" offers galaxy map and ranks it above system map")
    @ValueSource(strings = {
            "マップ開いて",
            "マップを開いて",
            "マップ"
    })
    void genericMapPhrasesOfferGalaxyMapAboveSystemMap(String utterance) {
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        // 1. galaxy map must be in offered shortlist
        int galaxyIdx = ranked.ids.indexOf(GALAXY_MAP);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(galaxyIdx >= 0 && ranked.scores.get(galaxyIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, GALAXY_MAP, galaxyIdx < 0 ? -1.0 : ranked.scores.get(galaxyIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));

        // 2. galaxy map must be ranked above system map
        int systemIdx = ranked.ids.indexOf(SYSTEM_MAP);
        assertTrue(systemIdx < 0 || galaxyIdx < systemIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked system map (#%d, %.3f) above galaxy map (#%d, %.3f)",
                utterance, systemIdx, systemIdx >= 0 ? ranked.scores.get(systemIdx) : -1.0,
                galaxyIdx, galaxyIdx >= 0 ? ranked.scores.get(galaxyIdx) : -1.0));
    }

    @Test
    void systemMapPhraseRanksSystemMapAboveGalaxyMap() {
        String utterance = "システムマップを開いて";
        Ranked ranked = rank(Language.JA, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int systemIdx = ranked.ids.indexOf(SYSTEM_MAP);
        int galaxyIdx = ranked.ids.indexOf(GALAXY_MAP);

        assertTrue(systemIdx >= 0, "System map should be present in ranked candidates");
        assertTrue(galaxyIdx < 0 || systemIdx < galaxyIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked galaxy map (#%d, %.3f) above system map (#%d, %.3f)",
                utterance, galaxyIdx, galaxyIdx >= 0 ? ranked.scores.get(galaxyIdx) : -1.0,
                systemIdx, systemIdx >= 0 ? ranked.scores.get(systemIdx) : -1.0));
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
