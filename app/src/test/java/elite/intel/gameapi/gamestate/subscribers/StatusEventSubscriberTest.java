package elite.intel.gameapi.gamestate.subscribers;

import elite.intel.gameapi.gamestate.dtos.GameEvents;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEventSubscriberTest {

    @TempDir
    Path tempDir;

    private StatusEventSubscriber subscriber;
    private Logger coreLogger;
    private AbstractAppender testAppender;
    private final List<LogEvent> capturedLogs = new ArrayList<>();

    private Path mockStatusFile;
    private final Instant fixedTime = Instant.parse("2026-09-27T10:15:30Z");

    @BeforeEach
    void setUp() throws IOException {
        subscriber = new StatusEventSubscriber();
        subscriber.resetPreviousSituationForTesting();

        mockStatusFile = tempDir.resolve("Status.json");
        Files.writeString(mockStatusFile, "{\"Flags\":0,\"Flags2\":0}");

        StatusEventSubscriber.clockSupplier = () -> fixedTime;
        StatusEventSubscriber.statusPathSupplier = () -> mockStatusFile;

        coreLogger = (Logger) LogManager.getLogger(StatusEventSubscriber.class);
        testAppender = new AbstractAppender("TestStatusEventAppender", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                capturedLogs.add(event.toImmutable());
            }
        };
        testAppender.start();
        coreLogger.addAppender(testAppender);
    }

    @AfterEach
    void tearDown() {
        if (coreLogger != null && testAppender != null) {
            coreLogger.removeAppender(testAppender);
            testAppender.stop();
        }
        StatusEventSubscriber.clockSupplier = Instant::now;
        StatusEventSubscriber.statusPathSupplier = () -> null;
    }

    private GameEvents.StatusEvent createStatusEvent(long flags, long flags2) {
        GameEvents.StatusEvent event = new GameEvents.StatusEvent();
        event.setFlags(flags);
        event.setFlags2(flags2);
        event.setGuiFocus(0); // NO_FOCUS
        return event;
    }

    @Test
    void initialStatusEventUnknownLogsOnce() {
        // Startup initial event is UNKNOWN (flags=0)
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));

        assertEquals(1, capturedLogs.size(), "Should log once on startup when situation is UNKNOWN");
        LogEvent log = capturedLogs.get(0);
        assertEquals(Level.INFO, log.getLevel());
        String msg = log.getMessage().getFormattedMessage();
        assertTrue(msg.contains("Game situation changed to UNKNOWN"), msg);
        assertTrue(msg.contains("flags=0x0"), msg);
        assertTrue(msg.contains("flags2=0x0"), msg);
        assertTrue(msg.contains("path=" + mockStatusFile), msg);
        assertTrue(msg.contains("lastRead=" + fixedTime), msg);
    }

    @Test
    void consecutiveUnknownDoesNotLogAgain() {
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));
        assertEquals(1, capturedLogs.size());

        // Subsequent UNKNOWN events must not log again
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));
        assertEquals(1, capturedLogs.size(), "Subsequent UNKNOWN events should not produce redundant logs");
    }

    @Test
    void knownSituationThenUnknownLogsOnce() {
        // Start with known situation (flags=1 is DOCKED -> IN_SHIP_DOCKED)
        subscriber.onStatusChangedEvent(createStatusEvent(1L, 0L));
        assertEquals(0, capturedLogs.size(), "Known situation should not produce UNKNOWN log");

        // Transition to UNKNOWN
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));
        assertEquals(1, capturedLogs.size(), "Transition into UNKNOWN should produce 1 log");
        assertTrue(capturedLogs.get(0).getMessage().getFormattedMessage().contains("Game situation changed to UNKNOWN"));
    }

    @Test
    void transitionToKnownAndBackToUnknownLogsAgain() {
        // First transition to UNKNOWN
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));
        assertEquals(1, capturedLogs.size());

        // Back to known (DOCKED)
        subscriber.onStatusChangedEvent(createStatusEvent(1L, 0L));
        assertEquals(1, capturedLogs.size());

        // Back to UNKNOWN
        subscriber.onStatusChangedEvent(createStatusEvent(0L, 0L));
        assertEquals(2, capturedLogs.size(), "Transitioning back to UNKNOWN should produce another log");
    }
}
