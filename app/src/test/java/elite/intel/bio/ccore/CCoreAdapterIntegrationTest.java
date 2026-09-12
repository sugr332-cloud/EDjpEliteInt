package elite.intel.bio.ccore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link CCoreAdapter} against the real {@code python -m app.cli bio evaluate} process, not a
 * fake one - this is the one place that would catch the request/response shape actually drifting from
 * EDpjKinsaku's CLI, which {@link CCoreAdapterJsonTest} cannot (it never starts a process).
 * <p>
 * Requires a Python on PATH with EDpjKinsaku pip-installed (editable install confirmed to work from any
 * working directory - see {@link CCoreAdapter}'s class comment). Both were true on the machine and
 * checkout layout this was built against; there is no fake/skip path here yet because this adapter has
 * no other caller to protect from that dependency (see docs/ELITEINTEL_INTEGRATION_PLAN.md Phase 4).
 */
class CCoreAdapterIntegrationTest {

    private final CCoreAdapter adapter = new CCoreAdapter();

    /**
     * The exact fixture already verified by hand (README-quoted pipe invocation) and by
     * EDpjKinsaku's own {@code test_aleoida_arcus_matches_boundary_values_inclusive}: Aleoida Arcus'
     * boundary values, which must come back MATCH end to end through the real subprocess.
     */
    @Test
    void aleoidaArcusBoundaryBodyMatchesThroughTheRealCli() {
        BodyContext body = new BodyContext(
                "CarbonDioxide", 0.04, 180.0, 0.0161, "Rocky body", "None", null);

        List<RuleEvaluation> evaluations = adapter.evaluate("Aleoida", body);

        assertFalse(evaluations.isEmpty(), "Aleoida has five species converted; none came back");
        RuleEvaluation arcus = evaluations.stream()
                .filter(e -> e.speciesName().equals("Aleoida Arcus"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aleoida Arcus missing from: " + evaluations));
        assertEquals(RuleStatus.MATCH, arcus.status());
    }

    @Test
    void genusNameMatchingIsCaseInsensitive() {
        BodyContext body = new BodyContext(
                "CarbonDioxide", 0.04, 180.0, 0.0161, "Rocky body", "None", null);

        assertEquals(adapter.evaluate("Aleoida", body), adapter.evaluate("ALEOIDA", body));
    }

    @Test
    void aBodyWithNoDataComesBackInsufficientForEveryone() {
        BodyContext body = new BodyContext(null, null, null, null, null, null, null);

        List<RuleEvaluation> evaluations = adapter.evaluate("Fumerola", body);

        assertEquals(4, evaluations.size(), "Fumerola has four species converted");
        assertTrue(evaluations.stream().allMatch(e -> e.status() == RuleStatus.INSUFFICIENT_DATA));
    }

    /**
     * 13 of C-CORE's 19 genera have no evaluator yet (see {@code evaluate_genus()}'s KeyError on the
     * EDpjKinsaku side) - the adapter must surface that as a clear failure, not an empty "no
     * candidates" result that looks identical to a real negative.
     */
    @Test
    void anUnconvertedGenusFailsWithACleanErrorRatherThanAnEmptyList() {
        BodyContext body = new BodyContext(null, null, null, null, null, null, null);

        CCoreAdapterException error = assertThrows(CCoreAdapterException.class,
                () -> adapter.evaluate("Tussock", body));
        assertTrue(error.getMessage().toLowerCase().contains("tussock"), error.getMessage());
    }
}
