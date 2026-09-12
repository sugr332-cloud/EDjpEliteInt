package elite.intel.ai.brain.vega.memory.facts.sources;

import elite.intel.ai.brain.vega.memory.facts.LocalizedFactRelevance;
import elite.intel.ai.brain.vega.memory.facts.MemoryFactContext;
import elite.intel.ai.brain.vega.memory.facts.MemoryFactSource;
import elite.intel.ai.brain.vega.memory.facts.RegisterMemoryFactSource;
import elite.intel.bio.ccore.RuleEvaluation;
import elite.intel.bio.ccore.RuleStatus;
import elite.intel.db.managers.LocationManager;
import elite.intel.gameapi.journal.events.dto.LocationDto;
import elite.intel.session.PlayerSession;
import elite.intel.session.PlayerSituation;
import elite.intel.session.Status;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Context-gated fact source for C-CORE's exobiology species candidates on the body the commander is at
 * (docs/ELITEINTEL_INTEGRATION_PLAN.md Phase 7): it contributes only C-CORE's {@code MATCH} verdicts
 * from {@link LocationDto#getSpeciesEvaluations()}, already computed and stored by
 * {@code SAASignalsFoundSubscriber} (Phase 5) - this source only reads that state, it never calls
 * {@code CCoreAdapter} or the C-CORE CLI itself. {@code NO_MATCH}/{@code INSUFFICIENT_DATA} are
 * C-CORE's internal reasoning, not something the commander asked to hear, so they stay off the fact
 * line (they remain on the DTO for {@link elite.intel.ui.overlay.ExobiologyObjectiveSource}, the HUD's
 * own independent reader of the same field).
 * <p>
 * Scoped to whatever genus Phase 5 evaluates (Aleoida only, for now) - this source has no genus
 * knowledge of its own; it simply reports whatever C-CORE already decided and stored.
 */
@RegisterMemoryFactSource
public final class ExobiologyCandidateFactSource implements MemoryFactSource {

    /** Provenance label for the {@code <fact source="...">} attribute. */
    private static final String ID = "exobiology_candidates";

    /** Existing alias groups (ai_action_aliases.properties) that already mean "what can I find here". */
    private static final List<String> RELEVANCE_ALIAS_KEYS = List.of(
            "query_exobiology_samples",
            "query_biome_analysis");

    /** Same gate as {@link CurrentBodyFactSource}: a candidate list only makes sense at or near a body. */
    private static final Set<PlayerSituation> AT_BODY = EnumSet.of(
            PlayerSituation.IN_SHIP_LANDED,
            PlayerSituation.IN_SHIP_GLIDE,
            PlayerSituation.IN_SHIP_ORBIT,
            PlayerSituation.IN_SHIP_RING,
            PlayerSituation.IN_SRV,
            PlayerSituation.ON_FOOT_PLANET);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isRelevant(MemoryFactContext context) {
        return LocalizedFactRelevance.matches(context, 2, RELEVANCE_ALIAS_KEYS);
    }

    @Override
    public List<String> factsFor(MemoryFactContext context) {
        // Situation gate first, without the body record, exactly like CurrentBodyFactSource: keeps the
        // DB read off the common not-at-body turn.
        if (!AT_BODY.contains(Status.getInstance().getSituation(null))) {
            return List.of();
        }
        LocationDto body = LocationManager.getInstance().findByLocationData(PlayerSession.getInstance().getLocationData());
        String fact = format(body == null ? null : body.getSpeciesEvaluations());
        return fact.isBlank() ? List.of() : List.of(fact);
    }

    /**
     * Builds the single compact candidates line from stored C-CORE evaluations, keeping only
     * {@code MATCH} species in the order C-CORE returned them. Empty when there are none - a body never
     * evaluated, one with only {@code NO_MATCH}/{@code INSUFFICIENT_DATA}, or a null/empty list all
     * produce the same silent "nothing to say" result. Pure and package-visible for testing.
     */
    static String format(List<RuleEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return "";
        }
        List<String> matches = new ArrayList<>();
        for (RuleEvaluation evaluation : evaluations) {
            if (evaluation.status() == RuleStatus.MATCH) {
                matches.add(evaluation.speciesName());
            }
        }
        if (matches.isEmpty()) {
            return "";
        }
        return FactLine.capped("C-CORE exobiology candidates", matches);
    }
}
