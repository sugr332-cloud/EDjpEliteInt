package elite.intel.gameapi.search.spansh;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

/**
 * Shared utility for parsing timestamp strings returned by Spansh APIs.
 * Supports standard ISO-8601 (e.g. {@code 2026-09-25T12:00:00Z}) as well as
 * Spansh's space-separated timestamp format with timezone offset (e.g. {@code 2026-09-25 12:00:00+00}).
 */
public final class SpanshTimestamps {

    private static final DateTimeFormatter SPANSH_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd").appendLiteral(' ').appendPattern("HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .appendPattern("[XXX][XX][X]")
            .toFormatter();

    private SpanshTimestamps() {}

    /**
     * Parses a timestamp from Spansh into an {@link Instant}.
     * Returns {@code null} if the timestamp is null, blank, or malformed.
     *
     * @param timestamp timestamp string from Spansh or ISO-8601
     * @return parsed {@link Instant}, or {@code null} if parsing fails
     */
    public static Instant parse(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            try {
                return Instant.from(SPANSH_TIMESTAMP.parse(timestamp));
            } catch (RuntimeException other) {
                return null;
            }
        }
    }
}
