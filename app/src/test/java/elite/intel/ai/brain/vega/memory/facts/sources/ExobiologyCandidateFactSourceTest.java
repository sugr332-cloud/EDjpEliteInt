package elite.intel.ai.brain.vega.memory.facts.sources;

import elite.intel.ai.brain.vega.memory.facts.MemoryFactContext;
import elite.intel.bio.ccore.RuleEvaluation;
import elite.intel.bio.ccore.RuleStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExobiologyCandidateFactSourceTest {

    private static RuleEvaluation eval(String name, RuleStatus status) {
        return new RuleEvaluation("$Codex_Ent_Aleoids_Test_Name;", name, status, "test reason");
    }

    @Test
    void buildsALineFromASingleMatch() {
        assertEquals("C-CORE exobiology candidates: Aleoida Arcus",
                ExobiologyCandidateFactSource.format(List.of(eval("Aleoida Arcus", RuleStatus.MATCH))));
    }

    @Test
    void buildsALineFromMultipleMatchesPreservingOrder() {
        assertEquals("C-CORE exobiology candidates: Aleoida Arcus, Aleoida Gravis",
                ExobiologyCandidateFactSource.format(List.of(
                        eval("Aleoida Arcus", RuleStatus.MATCH),
                        eval("Aleoida Coronamus", RuleStatus.NO_MATCH),
                        eval("Aleoida Gravis", RuleStatus.MATCH))));
    }

    @Test
    void emptyWhenOnlyNoMatch() {
        assertTrue(ExobiologyCandidateFactSource.format(List.of(
                eval("Aleoida Arcus", RuleStatus.NO_MATCH),
                eval("Aleoida Gravis", RuleStatus.NO_MATCH))).isEmpty());
    }

    @Test
    void emptyWhenOnlyInsufficientData() {
        assertTrue(ExobiologyCandidateFactSource.format(List.of(
                eval("Aleoida Arcus", RuleStatus.INSUFFICIENT_DATA))).isEmpty());
    }

    @Test
    void emptyWhenListIsEmpty() {
        assertTrue(ExobiologyCandidateFactSource.format(List.of()).isEmpty());
    }

    @Test
    void emptyWhenListIsNull() {
        assertTrue(ExobiologyCandidateFactSource.format(null).isEmpty());
    }

    @Test
    void isRelevantForExobiologySamplesQuery() {
        ExobiologyCandidateFactSource source = new ExobiologyCandidateFactSource();
        assertTrue(source.isRelevant(MemoryFactContext.forCommanderInput("what organisms are on this planet")));
    }

    @Test
    void isRelevantForBiomeAnalysisQuery() {
        ExobiologyCandidateFactSource source = new ExobiologyCandidateFactSource();
        assertTrue(source.isRelevant(MemoryFactContext.forCommanderInput("what life is here")));
    }

    @Test
    void isNotRelevantForUnrelatedQuery() {
        ExobiologyCandidateFactSource source = new ExobiologyCandidateFactSource();
        assertFalse(source.isRelevant(MemoryFactContext.forCommanderInput("what is my ship's fuel level")));
    }
}
