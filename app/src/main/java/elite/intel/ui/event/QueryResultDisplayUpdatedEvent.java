package elite.intel.ui.event;

/**
 * Published when a query result (trade candidates or nearest outfitting) is saved or cleared in the database.
 * Display panels observe this event to refresh their state from the database.
 *
 * @param queryType the query type that was updated ("trade_candidates" or "outfitting")
 */
public record QueryResultDisplayUpdatedEvent(String queryType) {
}
