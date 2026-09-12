package elite.intel.bio.ccore;

import com.google.gson.annotations.SerializedName;

/**
 * One species' verdict for a {@link BodyContext}, already aggregated across that species' rulesets by
 * C-CORE (see EDpjKinsaku's {@code aggregate_species_evaluations()}) - one entry per species C-CORE has
 * a rule for in the requested genus, not one per ruleset.
 *
 * @param speciesCode the FDev codex symbol, e.g. {@code "$Codex_Ent_Aleoids_01_Name;"}
 * @param speciesName the human-readable species name, e.g. {@code "Aleoida Arcus"}
 */
public record RuleEvaluation(
        @SerializedName("species_code") String speciesCode,
        @SerializedName("species_name") String speciesName,
        RuleStatus status,
        String reason
) {
}
