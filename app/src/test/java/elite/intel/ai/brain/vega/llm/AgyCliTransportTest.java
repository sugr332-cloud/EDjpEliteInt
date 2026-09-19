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
        private final AtomicBoolean destroyedForcibly = new AtomicBoolean(false);

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
            destroyedForcibly.set(true);
            return this;
        }

        boolean wasDestroyedForcibly() {
            return destroyedForcibly.get();
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

    @Test
    void testCommandLineAndSchemaFileCaptureRequestExactly() {
        AtomicReference<List<String>> capturedCommand = new AtomicReference<>();
        AtomicReference<Path> capturedSchemaFile = new AtomicReference<>();
        AtomicReference<String> capturedSchemaContent = new AtomicReference<>();

        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5), pb -> {
            capturedCommand.set(new ArrayList<>(pb.command()));
            int schemaIndex = pb.command().indexOf("--json-schema");
            if (schemaIndex != -1 && schemaIndex + 1 < pb.command().size()) {
                Path p = Path.of(pb.command().get(schemaIndex + 1));
                capturedSchemaFile.set(p);
                try {
                    capturedSchemaContent.set(Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return new FakeProcess(0, "{\"status\":\"SUCCESS\"}", "", false);
        });

        String requestWithSchema = "{\"prompt\":\"find nearest station\",\"json_schema\":\"{\\\"type\\\":\\\"object\\\"}\"}";
        transport.sendOutcome(requestWithSchema);

        assertNotNull(capturedCommand.get());
        assertTrue(capturedCommand.get().contains("--print"));
        int printIndex = capturedCommand.get().indexOf("--print");
        assertEquals("find nearest station", capturedCommand.get().get(printIndex + 1));
        assertTrue(capturedCommand.get().contains("--json-schema"));
        assertNotNull(capturedSchemaFile.get());
        assertEquals("{\"type\":\"object\"}", capturedSchemaContent.get());

        // Test without schema: --json-schema must not be present
        AtomicReference<List<String>> capturedCommandNoSchema = new AtomicReference<>();
        AgyCliTransport noSchemaTransport = new AgyCliTransport("agy", Duration.ofSeconds(5), pb -> {
            capturedCommandNoSchema.set(new ArrayList<>(pb.command()));
            return new FakeProcess(0, "{\"status\":\"SUCCESS\"}", "", false);
        });

        noSchemaTransport.sendOutcome("{\"prompt\":\"summarize text\"}");
        assertNotNull(capturedCommandNoSchema.get());
        assertTrue(capturedCommandNoSchema.get().contains("--print"));
        int noSchemaPrintIndex = capturedCommandNoSchema.get().indexOf("--print");
        assertEquals("summarize text", capturedCommandNoSchema.get().get(noSchemaPrintIndex + 1));
        assertFalse(capturedCommandNoSchema.get().contains("--json-schema"));
    }

    @Test
    void testIsolatedSettingsJsonIsConfiguredCorrectly() {
        AtomicReference<String> settingsContent = new AtomicReference<>();

        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5), pb -> {
            Path settingsPath = pb.directory().toPath().resolve(".gemini").resolve("antigravity-cli").resolve("settings.json");
            try {
                if (Files.exists(settingsPath)) {
                    settingsContent.set(Files.readString(settingsPath, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new FakeProcess(0, "{\"status\":\"SUCCESS\"}", "", false);
        });

        transport.sendOutcome(validRequest("security check"));

        assertNotNull(settingsContent.get(), "settings.json must exist in isolated dir");
        assertTrue(settingsContent.get().contains("\"command(*)\""));
        assertTrue(settingsContent.get().contains("\"write_file(*)\""));
        assertTrue(settingsContent.get().contains("\"read_file(*)\""));
    }

    @Test
    void testNonZeroExitCodeWithMultilineStderrDiagnostic() {
        String multilineStderr = "Error: invalid flag --unknown\nUsage: agy [flags]\nstack trace line 1\nstack trace line 2";
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(2, "", multilineStderr, false));

        AiTransportResult outcome = transport.sendOutcome(validRequest("syntax error test"));

        assertInstanceOf(AiTransportResult.Failure.class, outcome);
        AiTransportResult.Failure failure = (AiTransportResult.Failure) outcome;
        assertEquals(AiTransportResult.FailureKind.PERMANENT, failure.kind());
        assertEquals(2, failure.statusCode());
        assertTrue(failure.diagnostic().contains("invalid flag --unknown"));
        assertTrue(failure.diagnostic().contains("Usage: agy [flags]"));
        assertTrue(failure.diagnostic().contains("stack trace line 2"));
    }

    @Test
    void testTimeoutTriggersProcessDestroyForcibly() {
        AtomicReference<FakeProcess> processRef = new AtomicReference<>();
        AgyCliTransport transport = new AgyCliTransport("agy", Duration.ofMillis(100), pb -> {
            FakeProcess p = new FakeProcess(0, "{}", "", true);
            processRef.set(p);
            return p;
        });

        AiTransportResult outcome = transport.sendOutcome(validRequest("timeout process kill"));

        assertInstanceOf(AiTransportResult.Failure.class, outcome);
        assertEquals(AiTransportResult.FailureKind.TRANSIENT, ((AiTransportResult.Failure) outcome).kind());
        assertNotNull(processRef.get());
        assertTrue(processRef.get().wasDestroyedForcibly(), "Process must be forcibly destroyed on timeout");
    }

    @Test
    void testProcessStartIOExceptionCleansUpTempDirectory() {
        AtomicReference<Path> capturedDir = new AtomicReference<>();
        AgyCliTransport transport = new AgyCliTransport("nonexistent-agy", Duration.ofSeconds(5), pb -> {
            capturedDir.set(pb.directory().toPath());
            assertTrue(Files.exists(capturedDir.get()), "Temp dir must exist before process start fails");
            throw new IOException("Cannot run program nonexistent-agy: CreateProcess error=2, The system cannot find the file specified");
        });

        AiTransportResult outcome = transport.sendOutcome(validRequest("missing binary"));

        assertInstanceOf(AiTransportResult.Failure.class, outcome);
        assertEquals(AiTransportResult.FailureKind.PERMANENT, ((AiTransportResult.Failure) outcome).kind());
        assertNotNull(capturedDir.get());
        assertFalse(Files.exists(capturedDir.get()), "Temp dir must be cleaned up even when ProcessBuilder.start() throws IOException");
    }

    @Test
    void testNonObjectJsonOutputReturnsMalformedResponseFailure() {
        // Case 1: JSON array instead of JSON object
        AgyCliTransport arrayTransport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(0, "[1, 2, 3]", "", false));
        AiTransportResult arrayOutcome = arrayTransport.sendOutcome(validRequest("array"));
        assertInstanceOf(AiTransportResult.Failure.class, arrayOutcome);
        assertEquals(AiTransportResult.FailureKind.MALFORMED_RESPONSE, ((AiTransportResult.Failure) arrayOutcome).kind());

        // Case 2: JSON primitive string instead of JSON object
        AgyCliTransport stringTransport = new AgyCliTransport("agy", Duration.ofSeconds(5),
                pb -> new FakeProcess(0, "\"plain string\"", "", false));
        AiTransportResult stringOutcome = stringTransport.sendOutcome(validRequest("string"));
        assertInstanceOf(AiTransportResult.Failure.class, stringOutcome);
        assertEquals(AiTransportResult.FailureKind.MALFORMED_RESPONSE, ((AiTransportResult.Failure) stringOutcome).kind());
    }
}
