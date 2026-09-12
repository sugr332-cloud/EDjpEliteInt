package elite.intel.bio.ccore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link CCoreAdapter} against the real bundled {@code bio_entry} binary, not a fake one -
 * this is the one place that would catch the request/response shape actually drifting from
 * EDpjKinsaku's CLI, which {@link CCoreAdapterJsonTest} cannot (it never starts a process).
 * <p>
 * Requires {@code distribution/ccore/windows/bio_entry.exe} to exist at the path
 * {@link elite.intel.util.AppPaths#getCCoreBinary()} resolves (see docs/ELITEINTEL_INTEGRATION_PLAN.md
 * Phase 8) - true on the checkout this was built against; there is no fake/skip path here yet because
 * this adapter has no other caller to protect from that dependency.
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
