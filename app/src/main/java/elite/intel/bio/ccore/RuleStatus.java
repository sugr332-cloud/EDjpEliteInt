package elite.intel.bio.ccore;

/**
 * Mirrors EDpjKinsaku's {@code app.bio.c_core.RuleStatus} exactly (same constant names, so Gson's
 * default enum handling maps the CLI's JSON string straight across with no adapter needed).
 */
public enum RuleStatus {
    MATCH,
    NO_MATCH,
    INSUFFICIENT_DATA,
    RULE_DEFINITION_ERROR,
    RULESET_INCONSISTENCY
}
