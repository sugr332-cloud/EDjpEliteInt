package elite.intel.gameapi.search.spansh;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SpanshTimestampsTest {

    @Test
    void testParseSpanshOffsetFormats() {
        // "2026-09-25 12:00:00+00" (single 2-digit offset with space)
        Instant t1 = SpanshTimestamps.parse("2026-09-25 12:00:00+00");
        assertNotNull(t1);
        assertEquals(Instant.parse("2026-09-25T12:00:00Z"), t1);

        // "+00:00" (colon-separated offset)
        Instant t2 = SpanshTimestamps.parse("2026-09-25 12:00:00+00:00");
        assertNotNull(t2);
        assertEquals(Instant.parse("2026-09-25T12:00:00Z"), t2);

        // Fractional seconds with offset
        Instant t3 = SpanshTimestamps.parse("2026-09-25 12:00:00.123+00");
        assertNotNull(t3);
        assertEquals(Instant.parse("2026-09-25T12:00:00.123Z"), t3);

        Instant t4 = SpanshTimestamps.parse("2026-09-25 12:00:00.123456+00:00");
        assertNotNull(t4);
        assertEquals(Instant.parse("2026-09-25T12:00:00.123456Z"), t4);

        // ISO format with "Z"
        Instant tIso = SpanshTimestamps.parse("2026-09-25T12:00:00Z");
        assertNotNull(tIso);
        assertEquals(Instant.parse("2026-09-25T12:00:00Z"), tIso);

        // Invalid / null / empty inputs
        assertNull(SpanshTimestamps.parse(null));
        assertNull(SpanshTimestamps.parse(""));
        assertNull(SpanshTimestamps.parse("   "));
        assertNull(SpanshTimestamps.parse("invalid-date"));
        assertNull(SpanshTimestamps.parse("rubbish"));
    }
}
