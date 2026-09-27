package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.ai.brain.AiTransportResult;
import elite.intel.ai.brain.AiTransportResult.FailureKind;
import elite.intel.ai.brain.vega.tools.SpeakFunction;
import elite.intel.util.json.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of a persistent (resident) Antigravity CLI ({@code agy}) process
 * running in {@code --input-format stream-json --output-format stream-json} mode.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Strict sandbox environment isolation with deny-all agent tools permissions.</li>
 *   <li>Fixed JSON Schema enforcement for {@link SpeakFunction}.</li>
 *   <li>Format B NDJSON protocol communication: {@code {"event":"user","message":{"content":"..."}}}.</li>
 *   <li>State machine management: UNINITIALIZED, STARTING, READY, BUSY, TERMINATING, DEAD.</li>
 *   <li>Dual timeout handling: 15s CLI print-timeout and 20s Java watchdog with tree termination.</li>
 *   <li>Transparent restart on failure / termination (transient recovery path).</li>
 *   <li>Graceful cleanup via {@link #close()} and JVM shutdown hook safeguard.</li>
 * </ul>
 */
public class AgyResidentProcessManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgyResidentProcessManager.class);

    static final String DEFAULT_EXECUTABLE = "agy";
    static final Duration DEFAULT_CLI_TIMEOUT = Duration.ofSeconds(15);
    static final Duration DEFAULT_WATCHDOG_TIMEOUT = Duration.ofSeconds(20);

    private static final String ISOLATED_SETTINGS_JSON =
            "{\"permissions\":{\"deny\":[\"command(*)\",\"write_file(*)\",\"read_file(*)\"]}}";

    public enum State {
        UNINITIALIZED,
        STARTING,
        READY,
        BUSY,
        TERMINATING,
        DEAD
    }

    @FunctionalInterface
    public interface ProcessStarter {
        Process start(ProcessBuilder pb) throws IOException;
    }

    private final String executable;
    private final Duration cliTimeout;
    private final Duration watchdogTimeout;
    private final ProcessStarter processStarter;
    private final Thread shutdownHook;

    private final AtomicReference<State> state = new AtomicReference<>(State.UNINITIALIZED);

    private Path tempDir;
    private Process process;
    private BufferedWriter stdinWriter;
    private BlockingQueue<String> stdoutQueue;
    private BlockingQueue<String> stderrQueue;
    private Thread stdoutThread;
    private Thread stderrThread;

    public AgyResidentProcessManager() {
        this(DEFAULT_EXECUTABLE, DEFAULT_CLI_TIMEOUT, DEFAULT_WATCHDOG_TIMEOUT, ProcessBuilder::start);
    }

    public AgyResidentProcessManager(String executable, Duration cliTimeout) {
        this(executable, cliTimeout, DEFAULT_WATCHDOG_TIMEOUT, ProcessBuilder::start);
    }

    AgyResidentProcessManager(String executable, Duration cliTimeout, Duration watchdogTimeout, ProcessStarter processStarter) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.cliTimeout = Objects.requireNonNull(cliTimeout, "cliTimeout");
        this.watchdogTimeout = Objects.requireNonNull(watchdogTimeout, "watchdogTimeout");
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");

        this.shutdownHook = new Thread(this::closeSilently, "agy-resident-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
        }
    }

    public State getState() {
        return state.get();
    }

    /**
     * Ensures the resident process is started and in {@link State#READY}.
     * If currently {@link State#DEAD} or {@link State#UNINITIALIZED}, spins up a new process.
     */
    public synchronized void ensureReady() throws IOException {
        if (state.get() == State.READY && process != null && process.isAlive()) {
            return;
        }

        cleanupExistingProcess();

        state.set(State.STARTING);
        try {
            // 1. Setup isolated temp directory
            tempDir = Files.createTempDirectory("eliteintel-agy-resident-");
            setupIsolatedSettings(tempDir);

            // 2. Write fixed JSON schema for SpeakFunction
            Path schemaFile = tempDir.resolve("schema.json");
            Files.writeString(schemaFile, buildFixedSpeakSchema(), StandardCharsets.UTF_8);

            // 3. Build command
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add("--sandbox");
            command.add("--disable-slash-commands");
            command.add("--input-format");
            command.add("stream-json");
            command.add("--output-format");
            command.add("stream-json");
            command.add("--effort");
            command.add("low");
            command.add("--json-schema");
            command.add(schemaFile.toAbsolutePath().toString());
            command.add("--print-timeout");
            command.add(cliTimeout.toSeconds() + "s");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(false);
            pb.environment().put("USERPROFILE", tempDir.toAbsolutePath().toString());

            // 4. Start process
            process = processStarter.start(pb);
            stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

            stdoutQueue = new LinkedBlockingQueue<>();
            stderrQueue = new LinkedBlockingQueue<>();

            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            stdoutThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        stdoutQueue.put(line);
                    }
                } catch (Exception ignored) {}
            }, "agy-resident-stdout");
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            stderrThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        stderrQueue.put(line);
                    }
                } catch (Exception ignored) {}
            }, "agy-resident-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // 5. Wait for "init" event (up to 10 seconds)
            String initLine = stdoutQueue.poll(10, TimeUnit.SECONDS);
            if (initLine == null || !initLine.contains("\"init\"")) {
                state.set(State.DEAD);
                throw new IOException("Failed to receive init event from resident agy process: " + initLine);
            }

            state.set(State.READY);
            log.info("Resident agy process started successfully and in READY state (PID: {})", process.pid());

        } catch (Throwable t) {
            state.set(State.DEAD);
            cleanupExistingProcess();
            throw (t instanceof IOException ioe) ? ioe : new IOException("Failed to start resident agy: " + t.getMessage(), t);
        }
    }

    /**
     * Executes one turn against the resident process using Format B JSON message.
     */
    public synchronized AiTransportResult send(String promptText) {
        if (state.get() == State.TERMINATING) {
            return AiTransportResult.failure(FailureKind.CANCELLED, null, "Resident manager is terminating");
        }

        try {
            ensureReady();
        } catch (IOException e) {
            log.warn("Failed to ensure resident agy is ready: {}", e.getMessage());
            return AiTransportResult.failure(FailureKind.TRANSIENT, null, "Could not start resident agy: " + e.getMessage());
        }

        state.set(State.BUSY);
        long tStart = System.currentTimeMillis();

        // 1. Build Format B NDJSON line
        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("content", promptText);
        JsonObject req = new JsonObject();
        req.addProperty("event", "user");
        req.add("message", msgObj);
        String lineToSend = GsonFactory.getGson().toJson(req);

        // 2. Write to stdin
        try {
            stdinWriter.write(lineToSend);
            stdinWriter.newLine();
            stdinWriter.flush();
        } catch (IOException e) {
            log.warn("Broken pipe writing to resident agy stdin: {}", e.getMessage());
            terminateProcessTreeSilently(process);
            state.set(State.DEAD);
            return AiTransportResult.failure(FailureKind.TRANSIENT, null, "Broken pipe: " + e.getMessage());
        }

        // 3. Wait for result with Java watchdog timeout
        long deadline = System.currentTimeMillis() + watchdogTimeout.toMillis();
        boolean hasResult = false;
        boolean isTimeout = false;
        String responseText = "";
        String errorMsg = null;
        JsonObject structuredOutput = null;
        JsonObject resultObject = null;
        double resultDurationSeconds = -1;

        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                isTimeout = true;
                break;
            }

            // Drain stderr
            String errLine;
            while (stderrQueue != null && (errLine = stderrQueue.poll()) != null) {
                if (errorMsg == null) errorMsg = errLine;
                else errorMsg += "; " + errLine;
            }

            String line;
            try {
                line = stdoutQueue.poll(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                state.set(State.DEAD);
                terminateProcessTreeSilently(process);
                return AiTransportResult.failure(FailureKind.CANCELLED, null, "Interrupted while waiting for resident output");
            }

            if (line == null) {
                isTimeout = true;
                break;
            }

            try {
                JsonElement el = JsonParser.parseString(line);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("event") && "result".equals(obj.get("event").getAsString())) {
                        hasResult = true;
                        if (obj.has("result") && obj.get("result").isJsonObject()) {
                            JsonObject res = obj.getAsJsonObject("result");
                            resultObject = res;
                            if (res.has("error") && !res.get("error").isJsonNull()) {
                                errorMsg = res.get("error").getAsString();
                            }
                            if (res.has("structured_output") && !res.get("structured_output").isJsonNull() && res.get("structured_output").isJsonObject()) {
                                structuredOutput = res.getAsJsonObject("structured_output");
                                responseText = GsonFactory.getGson().toJson(structuredOutput);
                            } else if (res.has("response") && !res.get("response").isJsonNull()) {
                                responseText = res.get("response").getAsString().strip();
                            }
                            if (res.has("duration_seconds") && !res.get("duration_seconds").isJsonNull()) {
                                resultDurationSeconds = res.get("duration_seconds").getAsDouble();
                            }
                        }
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Handle Watchdog Timeout (20s)
        if (isTimeout && !hasResult) {
            log.warn("Resident agy turn did not complete within Java watchdog of {}; terminating process tree", watchdogTimeout);
            terminateProcessTreeSilently(process);
            state.set(State.DEAD);
            return AiTransportResult.failure(FailureKind.TRANSIENT, null,
                    "Resident agy execution timed out after " + watchdogTimeout);
        }

        // 5. Handle CLI print-timeout / error / response evaluation
        // Two independent signals, both tied to the configured cliTimeout rather than a fixed "15s":
        // (a) agy's own stderr message, when it sends one; (b) a "result" event that arrived with no error and
        // no usable output, whose own duration_seconds is a degenerate value (<=0, agy's sentinel for an
        // incomplete turn in stream-json mode - confirmed on real hardware: a genuine successful turn always
        // reports a positive multi-second duration) or has already reached cliTimeout (in case a future/older
        // agy instead reports the real partial elapsed time). This is agy's way of reporting the print-timeout
        // over stdout when stderr is empty. A short/empty result whose duration_seconds is a plausible positive
        // value under cliTimeout is left to the ordinary MALFORMED_RESPONSE path below, so an empty answer is
        // not blanket-treated as a timeout.
        boolean isPrintTimeout = (errorMsg != null && errorMsg.contains("print timeout after " + cliTimeout.toSeconds() + "s"))
                || (errorMsg == null && responseText.isBlank()
                        && (resultDurationSeconds == 0 || resultDurationSeconds >= cliTimeout.toSeconds()));
        boolean processAlive = process != null && process.isAlive();

        if (isPrintTimeout || (errorMsg != null && responseText.isBlank())) {
            // 15s print timeout reached: do NOT immediately kill. Check process / stream health.
            if (processAlive) {
                state.set(State.READY);
                log.info("Resident process survived print timeout and restored to READY (PID: {})", process.pid());
            } else {
                state.set(State.DEAD);
                log.warn("Resident process died during print timeout; set to DEAD");
            }
            return AiTransportResult.failure(FailureKind.TRANSIENT, null,
                    "agy print timeout: " + (errorMsg != null ? errorMsg : "partial/empty response"));
        }

        if (!processAlive) {
            state.set(State.DEAD);
            return AiTransportResult.failure(FailureKind.TRANSIENT, null, "Resident process died unexpectedly during turn");
        }

        state.set(State.READY);

        // 6. Return parsed response
        if (structuredOutput != null) {
            return AiTransportResult.success(resultObject);
        }

        if (responseText.isBlank()) {
            log.warn("Resident agy returned empty output (stderr: {})", errorMsg != null ? errorMsg : "none");
            return AiTransportResult.failure(FailureKind.MALFORMED_RESPONSE, null, "Resident agy returned empty output");
        }

        try {
            JsonObject json = JsonParser.parseString(responseText).getAsJsonObject();
            return AiTransportResult.success(json);
        } catch (Exception e) {
            log.warn("Resident agy response was not valid JSON: {}", e.getMessage());
            return AiTransportResult.failure(FailureKind.MALFORMED_RESPONSE, null,
                    "Resident agy stdout did not parse as JSON: " + e.getMessage() + "; raw: " + responseText);
        }
    }

    @Override
    public synchronized void close() {
        if (state.get() == State.TERMINATING || state.get() == State.DEAD) {
            return;
        }
        state.set(State.TERMINATING);
        log.info("Closing resident agy process manager...");

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (Throwable ignored) {}

        cleanupExistingProcess();
        state.set(State.DEAD);
    }

    private void closeSilently() {
        try {
            close();
        } catch (Throwable ignored) {}
    }

    private void cleanupExistingProcess() {
        if (process != null) {
            terminateProcessTreeSilently(process);
            process = null;
        }
        if (tempDir != null) {
            deleteTempDirSilently(tempDir);
            tempDir = null;
        }
    }

    private void terminateProcessTreeSilently(Process proc) {
        if (proc == null) return;
        try {
            if (proc.isAlive()) {
                try {
                    proc.toHandle().descendants().forEach(ph -> {
                        try { ph.destroyForcibly(); } catch (Throwable ignored) {}
                    });
                } catch (Throwable t) {
                    log.debug("ProcessHandle.descendants() failed: {}", t.getMessage());
                }
                proc.destroyForcibly();
            }
        } catch (Throwable t) {
            log.debug("Failed to destroy process: {}", t.getMessage());
        }
    }

    private void deleteTempDirSilently(Path dir) {
        try {
            if (dir != null && Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (Throwable t) {
            log.warn("Failed to cleanup isolated temp directory {}: {}", dir, t.getMessage());
        }
    }

    private void setupIsolatedSettings(Path dir) throws IOException {
        Path configDir = dir.resolve(".gemini").resolve("antigravity-cli");
        Files.createDirectories(configDir);
        Path settingsPath = configDir.resolve("settings.json");
        Files.writeString(settingsPath, ISOLATED_SETTINGS_JSON, StandardCharsets.UTF_8);
    }

    /**
     * Fixed JSON Schema enforcing SpeakFunction tool call format.
     */
    static String buildFixedSpeakSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject toolCalls = new JsonObject();
        toolCalls.addProperty("type", "array");
        toolCalls.addProperty("description", "The list of tool calls to execute.");

        JsonObject items = new JsonObject();
        items.addProperty("type", "object");
        JsonObject itemProps = new JsonObject();

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        JsonArray enumVals = new JsonArray();
        enumVals.add(SpeakFunction.ID);
        nameProp.add("enum", enumVals);
        itemProps.add("name", nameProp);

        JsonObject argsProp = new JsonObject();
        argsProp.addProperty("type", "string");
        itemProps.add("arguments", argsProp);

        items.add("properties", itemProps);
        JsonArray req = new JsonArray();
        req.add("name");
        req.add("arguments");
        items.add("required", req);

        toolCalls.add("items", items);
        properties.add("tool_calls", toolCalls);

        schema.add("properties", properties);
        JsonArray rootReq = new JsonArray();
        rootReq.add("tool_calls");
        schema.add("required", rootReq);

        return GsonFactory.getGson().toJson(schema);
    }
}
