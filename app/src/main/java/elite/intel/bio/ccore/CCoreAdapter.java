package elite.intel.bio.ccore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import elite.intel.util.json.GsonFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Calls EDpjKinsaku's C-CORE species-evaluation CLI ({@code edpj bio evaluate}) as a one-shot external
 * process per {@link #evaluate(String, BodyContext)} call: writes one request as JSON to its stdin,
 * reads its stdout as the response, and never keeps the process running past that call.
 * <p>
 * {@code edpj.exe} (the pip-installed console script) is deliberately not used - on the machine this
 * was built on it lives in a Scripts directory that is not on PATH, while
 * {@code python -m app.cli bio evaluate} was confirmed to work from any working directory (EDpjKinsaku
 * is pip-installed in editable mode, so {@code app.cli} is importable regardless of this JVM's CWD).
 * See docs/ELITEINTEL_INTEGRATION_PLAN.md's Phase 4 section for the investigation this is built on.
 * <p>
 * Not yet called from anywhere in the real pipeline - wiring this into
 * {@code ScanEventSubscriber}/the HUD is Phase 5. This class exists to be exercised on its own first.
 */
public class CCoreAdapter {

    /**
     * Assumes a working Python on PATH. Not user-configurable yet: adding a Settings-backed path
     * (mirroring {@code PlayerSession.setJournalPath()}) is deliberately deferred until this adapter
     * itself is proven, to keep this change scoped to the CLI boundary alone.
     */
    private static final String PYTHON_COMMAND = "python";

    /**
     * Measured cold latency is ~450-480ms (Python interpreter start plus the CLI's eagerly-imported
     * subcommand modules); this leaves a wide margin for a slower machine before treating the process
     * as hung.
     */
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * Evaluates every species C-CORE has a rule for in {@code genus} against {@code body}, already
     * aggregated per species (one entry per species, not per ruleset - see
     * {@code aggregate_species_evaluations()} on the EDpjKinsaku side).
     *
     * @throws CCoreAdapterException the CLI could not be started, timed out, exited non-zero (including
     *                                for a genus C-CORE has no evaluator for yet), or its stdout did not
     *                                parse as the expected response shape
     */
    public List<RuleEvaluation> evaluate(String genus, BodyContext body) {
        String requestJson = toRequestJson(genus, body);
        Process process = start();
        try {
            return runRequest(process, requestJson);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static String toRequestJson(String genus, BodyContext body) {
        JsonObject request = new JsonObject();
        request.addProperty("genus", genus);
        request.add("body", GsonFactory.toJsonObject(body));
        return GsonFactory.getGson().toJson(request);
    }

    private Process start() {
        try {
            return new ProcessBuilder(PYTHON_COMMAND, "-m", "app.cli", "bio", "evaluate")
                    .redirectErrorStream(false)
                    .start();
        } catch (IOException e) {
            throw new CCoreAdapterException(
                    "Could not start the C-CORE CLI (" + PYTHON_COMMAND + " -m app.cli bio evaluate): "
                            + e.getMessage(), e);
        }
    }

    private List<RuleEvaluation> runRequest(Process process, String requestJson) {
        // Started before writing stdin and before waiting: the child may begin producing output (or
        // erroring) before it has consumed the whole request, and an unread, full pipe buffer on either
        // side is a deadlock waiting to happen.
        StreamCollector stdout = StreamCollector.start(process.getInputStream(), "ccore-stdout");
        StreamCollector stderr = StreamCollector.start(process.getErrorStream(), "ccore-stderr");

        writeRequest(process, requestJson);

        if (!waitFor(process)) {
            // destroyForcibly() closes both pipes, which is what lets join() below return instead of
            // blocking exactly as long as the hang this timeout exists to bound.
            process.destroyForcibly();
            stdout.join();
            stderr.join();
            throw new CCoreAdapterException(
                    "C-CORE CLI did not respond within " + TIMEOUT + " (request abandoned)");
        }

        String out = stdout.join();
        String err = stderr.join();
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new CCoreAdapterException("C-CORE CLI exited with code " + exitCode + ": " + err.strip());
        }
        return parseResponse(out);
    }

    private void writeRequest(Process process, String requestJson) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(requestJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CCoreAdapterException(
                    "Could not write the request to the C-CORE CLI: " + e.getMessage(), e);
        }
    }

    private boolean waitFor(Process process) {
        try {
            return process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static List<RuleEvaluation> parseResponse(String stdout) {
        JsonArray array;
        try {
            array = GsonFactory.getGson().fromJson(stdout, JsonArray.class);
        } catch (JsonSyntaxException e) {
            throw new CCoreAdapterException("C-CORE CLI response was not valid JSON: " + e.getMessage(), e);
        }
        if (array == null) {
            throw new CCoreAdapterException("C-CORE CLI returned no response body");
        }
        List<RuleEvaluation> evaluations = new ArrayList<>(array.size());
        for (var element : array) {
            evaluations.add(GsonFactory.getGson().fromJson(element, RuleEvaluation.class));
        }
        return evaluations;
    }
}
