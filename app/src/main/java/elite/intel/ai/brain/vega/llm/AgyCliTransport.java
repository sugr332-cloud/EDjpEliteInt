package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import elite.intel.ai.brain.AiTransportResult;
import elite.intel.ai.brain.AiTransportResult.FailureKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Executes the Antigravity CLI ({@code agy}) as an LLM transport.
 * <p>
 * In production runtime (§R.6, §R.6.1), delegates to {@link AgyResidentProcessManager} to maintain a persistent,
 * resident {@code agy} process running in {@code stream-json} mode with low reasoning effort, fixed SpeakFunction schema,
 * 15s print-timeout, 20s Java watchdog, and transparent recovery.
 * <p>
 * Strict runtime security and process isolation boundaries are preserved:
 * <ul>
 *   <li>Runs in an isolated temporary working directory.</li>
 *   <li>Configures strict {@code permissions.deny} rules ({@code command(*)}, {@code write_file(*)}, {@code read_file(*)}).</li>
 *   <li>Redirects {@code USERPROFILE} to the temporary directory.</li>
 *   <li><b>NEVER</b> passes {@code --dangerously-skip-permissions}.</li>
 * </ul>
 */
public class AgyCliTransport implements LlmTransport, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgyCliTransport.class);

    static final String DEFAULT_EXECUTABLE = "agy";
    static final Duration DEFAULT_CLI_TIMEOUT = Duration.ofSeconds(15);
    static final Duration PROCESS_WAIT_MARGIN = Duration.ofSeconds(5);

    private static final String ISOLATED_SETTINGS_JSON =
            "{\"permissions\":{\"deny\":[\"command(*)\",\"write_file(*)\",\"read_file(*)\"]}}";

    @FunctionalInterface
    public interface ProcessStarter {
        Process start(ProcessBuilder pb) throws IOException;
    }

    private final String executable;
    private final Duration cliTimeout;
    private final ProcessStarter processStarter;
    private final AgyResidentProcessManager residentManager;

    /**
     * Production constructor using default {@code agy} executable on PATH and resident process manager.
     */
    public AgyCliTransport() {
        this(new AgyResidentProcessManager(DEFAULT_EXECUTABLE, DEFAULT_CLI_TIMEOUT));
    }

    /**
     * Configurable constructor for custom binary path and timeouts using resident process manager.
     */
    public AgyCliTransport(String executable, Duration cliTimeout) {
        this(new AgyResidentProcessManager(executable, cliTimeout));
    }

    /**
     * Direct injection of resident process manager.
     */
    public AgyCliTransport(AgyResidentProcessManager residentManager) {
        this.residentManager = Objects.requireNonNull(residentManager, "residentManager");
        this.executable = DEFAULT_EXECUTABLE;
        this.cliTimeout = DEFAULT_CLI_TIMEOUT;
        this.processStarter = null;
    }

    /**
     * Test seam constructor allowing injection of mock/fake process starter (legacy one-shot mode for tests).
     */
    AgyCliTransport(String executable, Duration cliTimeout, ProcessStarter processStarter) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.cliTimeout = Objects.requireNonNull(cliTimeout, "cliTimeout");
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        this.residentManager = null;
    }

    @Override
    public JsonObject send(String requestBody) {
        AiTransportResult outcome = sendOutcome(requestBody);
        if (outcome instanceof AiTransportResult.Success success) {
            return success.response();
        }
        AiTransportResult.Failure failure = (AiTransportResult.Failure) outcome;
        throw new IllegalStateException("VEGA transport failed: " + failure.diagnostic());
    }

    @Override
    public AiTransportResult sendOutcome(String requestBody) {
        if (residentManager != null) {
            return sendResidentOutcome(requestBody);
        }
        return sendOneShotOutcome(requestBody);
    }

    private AiTransportResult sendResidentOutcome(String requestBody) {
        String prompt;
        try {
            prompt = parsePrompt(requestBody);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid agy transport request: {}", e.getMessage());
            return AiTransportResult.failure(FailureKind.PERMANENT, null, "Invalid request: " + e.getMessage());
        } catch (Throwable t) {
            log.error("Unexpected failure parsing agy transport request: {}", t.getMessage(), t);
            return AiTransportResult.failure(FailureKind.PERMANENT, null, "Agy transport error: " + t.getMessage());
        }

        return residentManager.send(prompt);
    }

    private String parsePrompt(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("requestBody must not be null or blank");
        }
        try {
            JsonElement element = JsonParser.parseString(requestBody);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("prompt") || obj.get("prompt").isJsonNull()) {
                    throw new IllegalArgumentException("requestBody JSON must contain a non-null 'prompt' property");
                }
                return obj.get("prompt").getAsString();
            }
        } catch (JsonSyntaxException ignored) {
            return requestBody;
        }
        throw new IllegalArgumentException("Unsupported requestBody format: " + requestBody);
    }

    @Override
    public void close() {
        if (residentManager != null) {
            residentManager.close();
        }
    }

    AgyResidentProcessManager residentManager() {
        return residentManager;
    }

    // --- Legacy one-shot execution (retained for testing seam compatibility) ---
    private AiTransportResult sendOneShotOutcome(String requestBody) {
        Path tempDir = null;
        Process process = null;
        try {
            // 1. Create ephemeral isolated directory
            tempDir = Files.createTempDirectory("eliteintel-agy-runtime-");
            setupIsolatedSettings(tempDir);

            // 2. Parse request parameters (prompt, schema, etc.)
            ParsedRequest parsed = parseRequest(requestBody, tempDir);

            // 3. Build command line and ProcessBuilder
            ProcessBuilder pb = buildProcess(parsed, tempDir);

            // 4. Start the process
            try {
                process = processStarter.start(pb);
            } catch (IOException e) {
                log.warn("Failed to start agy binary ({}): {}", executable, e.getMessage());
                return AiTransportResult.failure(FailureKind.PERMANENT, null,
                        "Could not start agy binary (" + executable + "): " + e.getMessage());
            }

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(parsed.prompt().getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException e) {
                log.warn("Failed to write prompt to agy stdin: {}", e.getMessage());
                return AiTransportResult.failure(FailureKind.TRANSIENT, null,
                        "Failed to write prompt to agy stdin: " + e.getMessage());
            }

            // 5. Execute and collect output with dual timeout
            return executeProcess(process);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid agy transport request: {}", e.getMessage());
            return AiTransportResult.failure(FailureKind.PERMANENT, null, "Invalid request: " + e.getMessage());
        } catch (Throwable t) {
            log.error("Unexpected failure during agy transport execution: {}", t.getMessage(), t);
            return AiTransportResult.failure(FailureKind.PERMANENT, null, "Agy transport error: " + t.getMessage());
        } finally {
            // Multi-layered cleanup: process termination and temp directory deletion are fully isolated
            if (process != null) {
                terminateProcessTreeSilently(process);
            }
            if (tempDir != null) {
                deleteTempDirSilently(tempDir);
            }
        }
    }

    private void setupIsolatedSettings(Path tempDir) throws IOException {
        Path configDir = tempDir.resolve(".gemini").resolve("antigravity-cli");
        Files.createDirectories(configDir);
        Path settingsPath = configDir.resolve("settings.json");
        Files.writeString(settingsPath, ISOLATED_SETTINGS_JSON, StandardCharsets.UTF_8);
    }

    private record ParsedRequest(String prompt, Path schemaFile) {
    }

    private ParsedRequest parseRequest(String requestBody, Path tempDir) throws IOException {
        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException("requestBody must not be null or blank");
        }

        try {
            JsonElement element = JsonParser.parseString(requestBody);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("prompt") || obj.get("prompt").isJsonNull()) {
                    throw new IllegalArgumentException("requestBody JSON must contain a non-null 'prompt' property");
                }
                String prompt = obj.get("prompt").getAsString();
                Path schemaFile = null;
                if (obj.has("json_schema") && !obj.get("json_schema").isJsonNull()) {
                    String schemaContent = obj.get("json_schema").getAsString();
                    schemaFile = tempDir.resolve("schema.json");
                    Files.writeString(schemaFile, schemaContent, StandardCharsets.UTF_8);
                }
                return new ParsedRequest(prompt, schemaFile);
            }
        } catch (JsonSyntaxException ignored) {
            // Plain text payload passed directly (e.g. simple test or direct prompt)
            return new ParsedRequest(requestBody, null);
        }

        throw new IllegalArgumentException("Unsupported requestBody format: " + requestBody);
    }

    private ProcessBuilder buildProcess(ParsedRequest parsed, Path tempDir) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--sandbox");
        command.add("--disable-slash-commands");
        command.add("--output-format");
        command.add("json");
        command.add("--effort");
        command.add("low");

        if (parsed.schemaFile() != null) {
            command.add("--json-schema");
            command.add(parsed.schemaFile().toAbsolutePath().toString());
        }

        command.add("--print-timeout");
        command.add(cliTimeout.toSeconds() + "s");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(false);
        pb.environment().put("USERPROFILE", tempDir.toAbsolutePath().toString());
        return pb;
    }

    private AiTransportResult executeProcess(Process process) {
        StreamCollector stdout = StreamCollector.start(process.getInputStream(), "agy-stdout");
        StreamCollector stderr = StreamCollector.start(process.getErrorStream(), "agy-stderr");

        Duration watchdogTimeout = cliTimeout.plus(PROCESS_WAIT_MARGIN);
        boolean finished = waitFor(process, watchdogTimeout);

        if (!finished) {
            terminateProcessTreeSilently(process);
            stdout.join();
            stderr.join();
            log.warn("agy CLI process did not complete within watchdog deadline of {}", watchdogTimeout);
            return AiTransportResult.failure(FailureKind.TRANSIENT, null,
                    "agy execution timed out after " + watchdogTimeout);
        }

        String out = stdout.join();
        String err = stderr.join();
        int exitCode = process.exitValue();

        if (exitCode != 0) {
            log.warn("agy CLI exited with non-zero code {}: {}", exitCode, err.strip());
            return AiTransportResult.failure(FailureKind.PERMANENT, exitCode,
                    "agy exited with code " + exitCode + ": " + err.strip());
        }

        if (out.isBlank()) {
            log.warn("agy CLI returned empty stdout (stderr: {})", err.strip());
            return AiTransportResult.failure(FailureKind.MALFORMED_RESPONSE, exitCode,
                    "agy returned empty output. stderr: " + err.strip());
        }

        try {
            JsonObject json = JsonParser.parseString(out).getAsJsonObject();
            return AiTransportResult.success(json);
        } catch (Exception e) {
            log.warn("agy CLI response was not valid JSON: {}", e.getMessage());
            return AiTransportResult.failure(FailureKind.MALFORMED_RESPONSE, exitCode,
                    "agy stdout did not parse as JSON: " + e.getMessage() + "; raw: " + out);
        }
    }

    private boolean waitFor(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void terminateProcessTreeSilently(Process process) {
        try {
            if (process.isAlive()) {
                try {
                    process.toHandle().descendants().forEach(ph -> {
                        try {
                            ph.destroyForcibly();
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (Throwable t) {
                    log.debug("ProcessHandle.descendants() failed: {}", t.getMessage());
                }
                process.destroyForcibly();
            }
        } catch (Throwable t) {
            log.debug("Failed to destroy process: {}", t.getMessage());
        }
    }

    private void deleteTempDirSilently(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                try (var stream = Files.walk(tempDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (Throwable t) {
            log.warn("Failed to cleanup isolated temp directory {}: {}", tempDir, t.getMessage());
        }
    }

    /**
     * Thread-based stream collector ensuring pipe buffers do not overflow and deadlock.
     */
    static final class StreamCollector {
        private final Thread thread;
        private final StringBuilder text = new StringBuilder();

        private StreamCollector(InputStream in, String threadName) {
            this.thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        text.append(buffer, 0, read);
                    }
                } catch (IOException ignored) {
                }
            }, threadName);
            this.thread.start();
        }

        static StreamCollector start(InputStream in, String threadName) {
            return new StreamCollector(in, threadName);
        }

        String join() {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return text.toString();
        }
    }
}
