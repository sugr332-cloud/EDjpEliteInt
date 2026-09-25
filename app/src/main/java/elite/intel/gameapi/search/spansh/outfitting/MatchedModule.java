package elite.intel.gameapi.search.spansh.outfitting;

import java.util.Objects;

/**
 * Represents a matched ship outfitting module with canonical Spansh name and optional class/rating.
 */
public record MatchedModule(
        String canonicalName,
        Integer moduleClass,
        String rating
) {
    public MatchedModule {
        Objects.requireNonNull(canonicalName, "canonicalName must not be null");
        if (moduleClass != null && (moduleClass < 0 || moduleClass > 8)) {
            throw new IllegalArgumentException("moduleClass must be between 0 and 8: " + moduleClass);
        }
        if (rating != null) {
            rating = rating.toUpperCase();
            if (rating.length() != 1 || rating.charAt(0) < 'A' || rating.charAt(0) > 'I') {
                throw new IllegalArgumentException("rating must be between A and I: " + rating);
            }
        }
    }

    public String displayName() {
        StringBuilder sb = new StringBuilder();
        if (moduleClass != null) {
            sb.append(moduleClass);
        }
        if (rating != null) {
            sb.append(rating);
        }
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(canonicalName);
        return sb.toString();
    }
}
