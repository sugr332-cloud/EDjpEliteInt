package elite.intel.gameapi;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuxiliaryFilesMonitorTest {

    @TempDir
    Path tempDir;

    private AuxiliaryFilesMonitor monitor;
    private Logger coreLogger;
    private AbstractAppender testAppender;
    private final List<LogEvent> capturedLogs = new ArrayList<>();
    private long virtualTime;

    private static final String STATUS_JSON_1 = """
            { "timestamp":"2026-09-27T10:00:00Z", "event":"Status", "Flags":16777216, "Flags2":0 }
            """;

    private static final String STATUS_JSON_2 = """
            { "timestamp":"2026-09-27T10:05:00Z", "event":"Status", "Flags":1, "Flags2":0 }
            """;

    @BeforeEach
    void setUp() {
        monitor = new AuxiliaryFilesMonitor(tempDir);
        monitor.resetStatusDiagnosticsForTesting();

        virtualTime = 100_000L;
        AuxiliaryFilesMonitor.timeSupplier = () -> virtualTime;

        coreLogger = (Logger) LogManager.getLogger(AuxiliaryFilesMonitor.class);
        testAppender = new AbstractAppender("TestAuxMonitorAppender", null, null, false, Property.EMPTY_ARRAY) {
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
        AuxiliaryFilesMonitor.timeSupplier = System::currentTimeMillis;
    }

    private void writeStatus(String content) throws IOException {
        Files.writeString(tempDir.resolve("Status.json"), content, StandardCharsets.UTF_8);
    }

    @Test
    void startupDiagnosticsLogsDirectoryAndStatusJsonState() throws IOException {
        // Case 1: Status.json does not exist
        monitor.logStartupDiagnostics();
        assertEquals(1, capturedLogs.size());
        LogEvent log1 = capturedLogs.get(0);
        assertEquals(Level.INFO, log1.getLevel());
        String msg1 = log1.getMessage().getFormattedMessage();
        assertTrue(msg1.contains("Auxiliary files monitor started, watching directory: " + tempDir));
        assertTrue(msg1.contains("Status.json exists=false"));

        capturedLogs.clear();

        // Case 2: Status.json exists
        writeStatus(STATUS_JSON_1);
        monitor.logStartupDiagnostics();
        assertEquals(1, capturedLogs.size());
        LogEvent log2 = capturedLogs.get(0);
        assertEquals(Level.INFO, log2.getLevel());
        String msg2 = log2.getMessage().getFormattedMessage();
        assertTrue(msg2.contains("Status.json exists=true"));
        assertTrue(msg2.contains("modified="));
    }

    @Test
    void statusFileUnreadableFor30SecondsLogsOnceWhenFileMissing() {
        // Initial check at t=100_000 (Status.json missing)
        monitor.checkStatusFile();
        assertEquals(0, capturedLogs.size(), "No log immediately on first check");

        // Advance 29 seconds (t=129_000) -> still no log
        virtualTime += 29_000L;
        monitor.checkStatusFile();
        assertEquals(0, capturedLogs.size(), "No log before 30 seconds threshold");

        // Advance past 30 seconds (t=130_000) -> 1 log
        virtualTime += 1_000L;
        monitor.checkStatusFile();
        assertEquals(1, capturedLogs.size(), "Should log once when Status.json has not been read for 30s");
        LogEvent log = capturedLogs.get(0);
        assertEquals(Level.INFO, log.getLevel());
        String msg = log.getMessage().getFormattedMessage();
        assertTrue(msg.contains("Status.json has not been read for"), msg);
        assertTrue(msg.contains("exists=false"), msg);

        // Advance further -> should not log again
        virtualTime += 10_000L;
        monitor.checkStatusFile();
        assertEquals(1, capturedLogs.size(), "Should not log repeatedly while stale");
    }

    @Test
    void statusFileStaleWhenNotUpdatedAndResetsWhenUpdated() throws IOException {
        // Write Status.json and perform initial read at t=100_000
        writeStatus(STATUS_JSON_1);
        monitor.checkStatusFile();
        assertEquals(0, capturedLogs.size(), "Normal initial read produces no warning");

        // File is unchanged for 30 seconds -> logs stale warning
        virtualTime += 30_000L;
        monitor.checkStatusFile();
        assertEquals(1, capturedLogs.size(), "Should log warning when file has not been updated for 30s");
        assertTrue(capturedLogs.get(0).getMessage().getFormattedMessage().contains("Status.json has not been read for"));

        // File is updated at t=135_000 -> resets stale state
        virtualTime += 5_000L;
        writeStatus(STATUS_JSON_2);
        monitor.checkStatusFile();
        assertEquals(1, capturedLogs.size(), "Reading updated file should not produce a stale log");

        // Another 29 seconds without update -> no log
        virtualTime += 29_000L;
        monitor.checkStatusFile();
        assertEquals(1, capturedLogs.size());

        // 30 seconds reached again -> produces second stale log
        virtualTime += 1_000L;
        monitor.checkStatusFile();
        assertEquals(2, capturedLogs.size(), "Should log stale warning again after 30s of no further updates");
    }
}
