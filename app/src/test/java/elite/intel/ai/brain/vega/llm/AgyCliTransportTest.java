package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.AiTransportResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgyCliTransportTest {

    private static class FakeProcess extends Process {
        private final int exitCode;
        private final byte[] stdout;
        private final byte[] stderr;
        private final boolean simulateTimeout;
        private final AtomicBoolean alive = new AtomicBoolean(true);

        FakeProcess(int exitCode, String stdout, String stderr, boolean simulateTimeout) {
            this.exitCode = exitCode;
            this.stdout = (stdout != null ? stdout : "").getBytes(StandardCharsets.UTF_8);
            this.stderr = (stderr != null ? stderr : "").getBytes(StandardCharsets.UTF_8);
            this.simulateTimeout = simulateTimeout;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(stdout);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(stderr);
        }

        @Override
        public int waitFor() {
            alive.set(false);
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (simulateTimeout) {
                return false;
            }
            alive.set(false);
            return true;
        }

        @Override
        public int exitValue() {
            if (simulateTimeout && alive.get()) {
                throw new IllegalThreadStateException("Process still running");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            alive.set(false);
        }

        @Override
        public Process destroyForcibly() {
            alive.set(false);
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }
    }

    private static String validRequest(String prompt) {
        return "{\"prompt\":\"" + prompt + "\"}";
    }

    @Test
    void testSuccessOutcomeReturnsParsedJson() {
        String jsonOutput = "{\"status\":\"SUCCESS\",\"response\":\"ok\"}";
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(0, jsonOutput, "", false));

        AiTransportResult outcome = transport.sendOutcome(validRequest("hello"));

        assertInstanceOf(AiTransportResult.Success.class, outcome);
        AiTransportResult.Success success = (AiTransportResult.Success) outcome;
        assertEquals("SUCCESS", success.response().get("status").getAsString());
        assertEquals("ok", success.response().get("response").getAsString());
    }

    @Test
    void testNonZeroExitCodeReturnsPermanentFailureWithStderrDiagnostic() {
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(1, "", "permission denied for tool", false));

        AiTransportResult outcome = transport.sendOutcome(validRequest("list files"));

        assertInstanceOf(AiTransportResult.Failure.class, outcome);
        AiTransportResult.Failure failure = (AiTransportResult.Failure) outcome;
        assertEquals(AiTransportResult.FailureKind.PERMANENT, failure.kind());
        assertEquals(1, failure.statusCode());
        assertTrue(failure.diagnostic().contains("permission denied for tool"),
                "Diagnostic should contain stderr content: " + failure.diagnostic());
    }

    @Test
    void testTimeoutReturnsTransientFailure() {
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofMillis(100),
                pb -> new FakeProcess(0, "{}", "", true));

        AiTransportResult outcome = transport.sendOutcome(validRequest("slow prompt"));

        assertInstanceOf(AiTransportResult.Failure.class, outcome);
        AiTransportResult.Failure failure = (AiTransportResult.Failure) outcome;
        assertEquals(AiTransportResult.FailureKind.TRANSIENT, failure.kind());
        assertTrue(failure.diagnostic().contains("timed out"),
                "Diagnostic should indicate timeout: " + failure.diagnostic());
    }

    @Test
    void testEmptyAndMalformedJsonOutputReturnsMalformedResponseFailure() {
        // Case 1: Empty output
        AgyCliTransport emptyTransport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(0, "", "notice", false));
        AiTransportResult emptyOutcome = emptyTransport.sendOutcome(validRequest("empty"));
        assertInstanceOf(AiTransportResult.Failure.class, emptyOutcome);
        assertEquals(AiTransportResult.FailureKind.MALFORMED_RESPONSE, ((AiTransportResult.Failure) emptyOutcome).kind());

        // Case 2: Malformed JSON
        AgyCliTransport malformedTransport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(0, "Not a JSON", "", false));
        AiTransportResult malformedOutcome = malformedTransport.sendOutcome(validRequest("malformed"));
        assertInstanceOf(AiTransportResult.Failure.class, malformedOutcome);
        assertEquals(AiTransportResult.FailureKind.MALFORMED_RESPONSE, ((AiTransportResult.Failure) malformedOutcome).kind());
        assertTrue(((AiTransportResult.Failure) malformedOutcome).diagnostic().contains("did not parse as JSON"));
    }

    @Test
    void testProcessStartIOExceptionReturnsPermanentFailure() {
        AgyCliTransport transport = new AgyCliTransport("nonexistent-agy", Duration.ofSeconds(5),
                pb -> {
                    throw new IOException("Cannot run program nonexistent-agy: CreateProcess error=2");
                });

        AiTransportResult outcome = transport.sendOutcome(validRequest("run"));

        assertInstanceOf(AiTransportResult.Failure.class, outcome);
        AiTransportResult.Failure failure = (AiTransportResult.Failure) outcome;
        assertEquals(AiTransportResult.FailureKind.PERMANENT, failure.kind());
        assertTrue(failure.diagnostic().contains("Could not start agy binary"));
    }

    @Test
    void testTempDirectoryIsCleanedUpOnSuccessAndFailure() {
        AtomicReference<Path> capturedDir = new AtomicReference<>();

        // Test Success cleanup
        AgyCliTransport successTransport = new AgyCliTransport("agy", Duration.ofSeconds(5), pb -> {
            capturedDir.set(pb.directory().toPath());
            assertTrue(Files.exists(capturedDir.get()), "tempDir must exist while process runs");
            return new FakeProcess(0, "{\"ok\":true}", "", false);
        });
        successTransport.sendOutcome(validRequest("success test"));
        assertNotNull(capturedDir.get());
        assertFalse(Files.exists(capturedDir.get()), "tempDir must be deleted after success");

        // Test Failure cleanup
        AtomicReference<Path> failureCapturedDir = new AtomicReference<>();
        AgyCliTransport failureTransport = new AgyCliTransport("agy", Duration.ofSeconds(5), pb -> {
            failureCapturedDir.set(pb.directory().toPath());
            assertTrue(Files.exists(failureCapturedDir.get()), "tempDir must exist while process runs");
            return new FakeProcess(1, "", "error", false);
        });
        failureTransport.sendOutcome(validRequest("failure test"));
        assertNotNull(failureCapturedDir.get());
        assertFalse(Files.exists(failureCapturedDir.get()), "tempDir must be deleted after failure");

        // Test Timeout cleanup
        AtomicReference<Path> timeoutCapturedDir = new AtomicReference<>();
        AgyCliTransport timeoutTransport = new AgyCliTransport("agy", Duration.ofMillis(100), pb -> {
            timeoutCapturedDir.set(pb.directory().toPath());
            assertTrue(Files.exists(timeoutCapturedDir.get()), "tempDir must exist while process runs");
            return new FakeProcess(0, "{}", "", true);
        });
        timeoutTransport.sendOutcome(validRequest("timeout test"));
        assertNotNull(timeoutCapturedDir.get());
        assertFalse(Files.exists(timeoutCapturedDir.get()), "tempDir must be deleted after timeout");
    }

    @Test
    void testSendThrowsIllegalStateExceptionOnFailure() {
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(1, "", "fatal failure", false));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> transport.send(validRequest("fail")));
        assertTrue(ex.getMessage().contains("VEGA transport failed:"));
        assertTrue(ex.getMessage().contains("fatal failure"));
    }

    @Test
    void testCommandLineNeverIncludesDangerouslySkipPermissions() {
        List<String> capturedCommand = new ArrayList<>();
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5), pb -> {
            capturedCommand.addAll(pb.command());
            return new FakeProcess(0, "{\"status\":\"SUCCESS\"}", "", false);
        });

        transport.sendOutcome(validRequest("security test"));

        assertFalse(capturedCommand.isEmpty(), "Command line must not be empty");
        assertFalse(capturedCommand.contains("--dangerously-skip-permissions"),
                "Command line must NEVER contain --dangerously-skip-permissions");
        assertTrue(capturedCommand.contains("--sandbox"), "Command line must include --sandbox");
        assertTrue(capturedCommand.contains("--disable-slash-commands"), "Command line must include --disable-slash-commands");
    }
}
