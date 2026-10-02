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
 * Guards routing between the new "nearest space station" command and the neighbours that share its words:
 * query_stations (answers, plots nothing) and find_fuel_station (a fuel errand).
 */
@Tag("embedding-manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NearestOrbitalStationRoutingTest {

    private static final Set<IntelActionCategory> ALL = EnumSet.allOf(IntelActionCategory.class);
    private static final double SEM_MARGIN = 0.04;

    private static final String NAVIGATE_NEAREST = "navigate_to_nearest_orbital_station";
    private static final String QUERY_STATIONS = "query_stations";
    private static final String FIND_FUEL_STATION = "find_fuel_station";

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

    @ParameterizedTest(name = "JA \"{0}\" routes to navigate_to_nearest_orbital_station above query_stations")
    @ValueSource(strings = {
            "最寄りの宇宙ステーションへ",
            "一番近い宇宙ステーションに行って",
            "近くのスターポートへ向かって",
            "最寄りの宇宙ステーションに案内",
            "最寄りの宇宙ステーションへ向かって"
    })
    void japaneseGuidancePhrasesRouteToTheNewCommand(String utterance) {
        assertNewCommandAbove(Language.JA, utterance, QUERY_STATIONS);
    }

    @ParameterizedTest(name = "EN \"{0}\" routes to navigate_to_nearest_orbital_station above query_stations")
    @ValueSource(strings = {
            "nearest station",
            "nearest starport",
            "take me to the nearest space station"
    })
    void englishGuidancePhrasesRouteToTheNewCommand(String utterance) {
        assertNewCommandAbove(Language.EN, utterance, QUERY_STATIONS);
    }

    @ParameterizedTest(name = "JA \"{0}\" stays on query_stations above the new command")
    @ValueSource(strings = {
            "この星系のステーションは",
            "どんなステーションがある"
    })
    void japaneseQuestionPhrasesStayOnQueryStations(String utterance) {
        assertFirstAboveSecond(Language.JA, utterance, QUERY_STATIONS, NAVIGATE_NEAREST);
    }

    @ParameterizedTest(name = "JA \"{0}\" stays on find_fuel_station above the new command")
    @ValueSource(strings = {
            "最寄りの燃料ステーションを探して"
    })
    void japaneseFuelPhrasesStayOnFindFuelStation(String utterance) {
        assertFirstAboveSecond(Language.JA, utterance, FIND_FUEL_STATION, NAVIGATE_NEAREST);
    }

    @ParameterizedTest(name = "EN \"{0}\" stays on find_fuel_station above the new command")
    @ValueSource(strings = {
            "find nearest fuel station"
    })
    void englishFuelPhrasesStayOnFindFuelStation(String utterance) {
        assertFirstAboveSecond(Language.EN, utterance, FIND_FUEL_STATION, NAVIGATE_NEAREST);
    }

    private void assertNewCommandAbove(Language language, String utterance, String other) {
        Ranked ranked = rank(language, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int newIdx = ranked.ids.indexOf(NAVIGATE_NEAREST);
        int otherIdx = ranked.ids.indexOf(other);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(newIdx >= 0 && ranked.scores.get(newIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, NAVIGATE_NEAREST, newIdx < 0 ? -1.0 : ranked.scores.get(newIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
        assertTrue(otherIdx < 0 || newIdx < otherIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked %s (#%d, %.3f) above %s (#%d, %.3f)",
                utterance, other, otherIdx, otherIdx >= 0 ? ranked.scores.get(otherIdx) : -1.0,
                NAVIGATE_NEAREST, newIdx, newIdx >= 0 ? ranked.scores.get(newIdx) : -1.0));
    }

    private void assertFirstAboveSecond(Language language, String utterance, String first, String second) {
        Ranked ranked = rank(language, PlayerSituation.IN_SHIP_DEEP_SPACE, utterance);

        int firstIdx = ranked.ids.indexOf(first);
        int secondIdx = ranked.ids.indexOf(second);
        double cutoff = ranked.scores.get(0) - SEM_MARGIN;
        assertTrue(firstIdx >= 0 && ranked.scores.get(firstIdx) >= cutoff, () -> String.format(Locale.ROOT,
                "\"%s\" left %s out of shortlist: score %.3f < cutoff %.3f (top: %s %.3f)",
                utterance, first, firstIdx < 0 ? -1.0 : ranked.scores.get(firstIdx), cutoff,
                ranked.ids.get(0), ranked.scores.get(0)));
        assertTrue(secondIdx < 0 || firstIdx < secondIdx, () -> String.format(Locale.ROOT,
                "\"%s\" ranked %s (#%d, %.3f) above %s (#%d, %.3f)",
                utterance, second, secondIdx, secondIdx >= 0 ? ranked.scores.get(secondIdx) : -1.0,
                first, firstIdx, firstIdx >= 0 ? ranked.scores.get(firstIdx) : -1.0));
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
