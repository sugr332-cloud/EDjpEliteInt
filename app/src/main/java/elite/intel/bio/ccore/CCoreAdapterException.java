package elite.intel.bio.ccore;

/**
 * The C-CORE CLI could not be reached or did not answer usably: it failed to start, timed out, exited
 * non-zero (including for an unconverted genus - see EDpjKinsaku's {@code evaluate_genus()} KeyError),
 * or returned something that did not parse as the response shape it promises.
 * <p>
 * Unchecked, and deliberately undecided about UI/logging: {@link CCoreAdapter} has no caller yet (see
 * docs/ELITEINTEL_INTEGRATION_PLAN.md Phase 4/5), so it is not this class's place to guess whether a
 * failure should be shown to the commander, logged and swallowed, or retried.
 */
public class CCoreAdapterException extends RuntimeException {
    public CCoreAdapterException(String message) {
        super(message);
    }

    public CCoreAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
