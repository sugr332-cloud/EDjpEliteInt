package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.ai.brain.AiTransportResult;
import elite.intel.ai.brain.vega.VegaRuntimeGraph;
import elite.intel.ai.brain.vega.VegaRuntimeGraphFactory;
import elite.intel.ai.brain.vega.model.llm.LlmMessage;
import elite.intel.ai.brain.vega.model.llm.LlmMessageRole;
import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;
import elite.intel.ai.brain.vega.model.llm.LlmToolDefinition;
import elite.intel.ai.brain.vega.model.ThoughtSource;
import elite.intel.ai.brain.vega.prompt.VegaSystemPrompt;
import elite.intel.ai.brain.vega.tools.SpeakFunction;
import elite.intel.util.json.GsonFactory;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("local-integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AgyResidentProductionVerificationTest {

    private static final String SYSTEM_PROMPT = new VegaSystemPrompt().staticRules(ThoughtSource.COMMANDER);

    private static final List<String> BASE_INPUTS = List.of(
            "こんにちは、司令官のステータスを確認したい",
            "現在のシールドは何パーセント？",
            "燃料残量はどう？",
            "目的地はどこになっている？",
            "今何時？",
            "貨物庫に何が入っている？",
            "エンジンの状態は？",
            "異常はあるか？",
            "レーダー範囲内の脅威は？",
            "了解、ありがとう。スタンバイして"
    );

    private static String wrapPrompt(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("[System Instructions]\n").append(SYSTEM_PROMPT).append("\n\n");
        sb.append("[Commander]\n").append(text).append("\n\n");
        sb.append("[Instruction]\n")
                .append("Select the appropriate tool from the offered tools to respond to the commander's request, ")
                .append("and output valid JSON conforming to the requested schema.");

        JsonObject obj = new JsonObject();
        obj.addProperty("prompt", sb.toString().strip());
        return GsonFactory.getGson().toJson(obj);
    }

    // --------------------------------------------------------------------------------------------------
    // 1. 連続ターン耐久試験 (50ターン)
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(1)
    void test1_ContinuousTurnsEndurance50() throws Exception {
        System.out.println("==========================================================================================");
        System.out.println("TEST 1: CONTINUOUS 50-TURN ENDURANCE VERIFICATION (C-5.2 Production Code)");
        System.out.println("==========================================================================================");

        int targetTurns = 50;
        List<Long> latencies = new ArrayList<>();
        int strictOkCount = 0;
        int timeoutCount = 0;
        int internalToolCount = 0;

        try (AgyCliTransport transport = new AgyCliTransport()) {
            assertNotNull(transport.residentManager(), "residentManager must be initialized");

            for (int i = 0; i < targetTurns; i++) {
                int turnNum = i + 1;
                String input = BASE_INPUTS.get(i % BASE_INPUTS.size());

                long tStart = System.currentTimeMillis();
                AiTransportResult result = transport.sendOutcome(wrapPrompt(input));
                long elapsed = System.currentTimeMillis() - tStart;
                latencies.add(elapsed);

                boolean isStrictOk = false;
                boolean isInternalTool = false;

                if (result instanceof AiTransportResult.Success success) {
                    JsonObject json = success.response();
                    System.out.println("[DEBUG Turn " + turnNum + " JSON] " + json);
                    isStrictOk = verifyStrictSchema(json);
                    if (json.has("name") && !"speak".equals(json.get("name").getAsString())) {
                        isInternalTool = true;
                    }
                } else if (result instanceof AiTransportResult.Failure failure) {
                    System.out.println("[DEBUG Turn " + turnNum + " Failure] " + failure.diagnostic());
                    if (failure.diagnostic().contains("timed out") || failure.diagnostic().contains("timeout")) {
                        timeoutCount++;
                    }
                }

                if (isStrictOk) strictOkCount++;
                if (isInternalTool) internalToolCount++;

                if (turnNum <= 5 || turnNum % 10 == 0 || turnNum == targetTurns) {
                    System.out.printf("[Endurance Turn %2d/%d] Elapsed=%5dms | StrictOK=%-3s | State=%s%n",
                            turnNum, targetTurns, elapsed, isStrictOk ? "YES" : "NO", transport.residentManager().getState());
                }

                assertEquals(AgyResidentProcessManager.State.READY, transport.residentManager().getState(),
                        "State must return to READY after each turn");
            }

            // Statistics
            Collections.sort(latencies);
            double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long median = latencies.get(latencies.size() / 2);
            long max = latencies.get(latencies.size() - 1);
            long p95 = latencies.get((int) (latencies.size() * 0.95));

            System.out.println("\n[Test 1 Endurance Summary]");
            System.out.printf("- 実行ターン数: %d / %d%n", latencies.size(), targetTurns);
            System.out.printf("- Strict Schema維持率: %d/%d (%.1f%%)%n", strictOkCount, targetTurns, (double) strictOkCount / targetTurns * 100.0);
            System.out.printf("- 内部Tool誤爆数: %d%n", internalToolCount);
            System.out.printf("- タイムアウト数: %d%n", timeoutCount);
            System.out.printf("- レイテンシ分布: 平均=%.1fms, 中央値=%dms, P95=%dms, 最大=%dms%n", avg, median, p95, max);

            assertEquals(targetTurns, strictOkCount, "All 50 turns must produce strict schema output");
            assertEquals(0, internalToolCount, "Internal tool invocations must be zero");
            assertEquals(0, timeoutCount, "Timeouts must be zero");
        }
    }

    // --------------------------------------------------------------------------------------------------
    // 2. --print-timeout 15s 到達時の挙動検証
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(2)
    void test2_PrintTimeoutBehavior() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 2: PRINT-TIMEOUT HANDLING (Health check & READY/DEAD determination)");
        System.out.println("==========================================================================================");

        // cliTimeout = 1s で意図的に agy 側 print-timeout を発火させる
        try (AgyResidentProcessManager manager = new AgyResidentProcessManager("agy", Duration.ofSeconds(1))) {
            manager.ensureReady();
            assertEquals(AgyResidentProcessManager.State.READY, manager.getState());

            System.out.println("[Test 2] Sending turn with 1s print-timeout configured...");
            AiTransportResult outcome = manager.send("長文の詳細なシステム診断レポートを作成してください");

            System.out.printf("[Test 2 Result] Outcome type: %s%n", outcome.getClass().getSimpleName());
            if (outcome instanceof AiTransportResult.Failure failure) {
                System.out.printf("[Test 2 Result] Failure Kind: %s | Diagnostic: [%s]%n", failure.kind(), failure.diagnostic());
                assertEquals(AiTransportResult.FailureKind.TRANSIENT, failure.kind());
                assertTrue(failure.diagnostic().contains("print timeout"), "Diagnostic must mention print timeout");
            }

            // 健全性確認後の状態
            AgyResidentProcessManager.State stateAfterTimeout = manager.getState();
            System.out.printf("[Test 2 Result] Process State after print-timeout: %s%n", stateAfterTimeout);

            if (stateAfterTimeout == AgyResidentProcessManager.State.READY) {
                System.out.println("[Test 2 Next Turn] Process survived and is READY. Sending normal follow-up turn...");
                AiTransportResult nextOutcome = manager.send("こんにちは");
                System.out.printf("[Test 2 Next Turn Result] Outcome type: %s%n", nextOutcome.getClass().getSimpleName());
                if (nextOutcome instanceof AiTransportResult.Success success) {
                    System.out.printf("[Test 2 Next Turn Result] Strict OK: %s%n", verifyStrictSchema(success.response()));
                    assertTrue(verifyStrictSchema(success.response()), "Follow-up turn must produce strict schema");
                }
            } else {
                System.out.printf("[Test 2] Process was evaluated as DEAD. State: %s%n", stateAfterTimeout);
            }
        }
    }

    // --------------------------------------------------------------------------------------------------
    // 3. 20s Java watchdog 到達時の挙動検証
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(3)
    void test3_JavaWatchdogTimeoutAndRestart() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 3: JAVA WATCHDOG TIMEOUT & RESTART (200ms watchdog simulation)");
        System.out.println("==========================================================================================");

        // watchdogTimeout = 100ms で Java 側タイムアウトを発火させる
        try (AgyResidentProcessManager manager = new AgyResidentProcessManager("agy", Duration.ofSeconds(15), Duration.ofMillis(100), ProcessBuilder::start)) {
            manager.ensureReady();
            assertEquals(AgyResidentProcessManager.State.READY, manager.getState());

            System.out.println("[Test 3] Triggering Java watchdog timeout...");
            AiTransportResult outcome = manager.send("テストプロンプト");

            assertInstanceOf(AiTransportResult.Failure.class, outcome);
            AiTransportResult.Failure failure = (AiTransportResult.Failure) outcome;
            assertEquals(AiTransportResult.FailureKind.TRANSIENT, failure.kind());
            assertTrue(failure.diagnostic().contains("timed out"), "Diagnostic must indicate timeout: " + failure.diagnostic());

            assertEquals(AgyResidentProcessManager.State.DEAD, manager.getState(), "State must transition to DEAD on watchdog timeout");
            System.out.printf("[Test 3 Result] State after watchdog: %s%n", manager.getState());

            System.out.println("[Test 3 Recovery] Sending follow-up turn to trigger transparent restart...");
            // watchdog を十分な長さに戻した新しい manager または ensureReady
        }

        // 本番 Transport (20s watchdog) での再起動確認
        try (AgyCliTransport transport = new AgyCliTransport()) {
            transport.residentManager().ensureReady();
            // 意図的にプロセスを kill して DEAD にする
            System.out.println("[Test 3 Restart] Manually terminating process to test ensureReady() restart...");
            transport.residentManager().close();
            assertEquals(AgyResidentProcessManager.State.DEAD, transport.residentManager().getState());

            // 次の要求で自動再起動
            AiTransportResult restartOutcome = transport.sendOutcome(wrapPrompt("再起動後のステータス確認"));
            assertInstanceOf(AiTransportResult.Success.class, restartOutcome, "Turn after restart must succeed");
            assertEquals(AgyResidentProcessManager.State.READY, transport.residentManager().getState());
            JsonObject json = ((AiTransportResult.Success) restartOutcome).response();
            assertTrue(verifyStrictSchema(json), "Response after restart must conform to strict schema");
            System.out.printf("[Test 3 Restart Result] Response conforms to Strict Schema: YES%n");
        }
    }

    // --------------------------------------------------------------------------------------------------
    // 4. agyプロセスの異常死 (taskkillでの強制終了) 検証
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(4)
    void test4_ExternalTaskkillRecovery() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 4: EXTERNAL TASKKILL & AUTOMATIC RECOVERY");
        System.out.println("==========================================================================================");

        try (AgyCliTransport transport = new AgyCliTransport()) {
            transport.residentManager().ensureReady();
            long pid = getProcessPid(transport);
            System.out.printf("[Test 4] Resident process active with PID: %d%n", pid);

            System.out.printf("[Test 4] Executing external taskkill /F /T /PID %d ...%n", pid);
            new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start().waitFor();
            Thread.sleep(300);

            assertFalse(isProcessAlive(pid), "Process must be terminated by taskkill");
            System.out.println("[Test 4] Process confirmed dead on OS. Sending next turn to test automatic recovery...");

            AiTransportResult outcome = transport.sendOutcome(wrapPrompt("強制終了後の復旧確認"));
            assertInstanceOf(AiTransportResult.Success.class, outcome, "Recovery turn must succeed");
            assertEquals(AgyResidentProcessManager.State.READY, transport.residentManager().getState());

            long newPid = getProcessPid(transport);
            System.out.printf("[Test 4 Recovery] New resident process running with new PID: %d (StrictOK: %s)%n",
                    newPid, verifyStrictSchema(((AiTransportResult.Success) outcome).response()));
            assertNotEquals(pid, newPid, "New process PID must differ from killed PID");
        }
    }

    // --------------------------------------------------------------------------------------------------
    // 5. Broken pipe / stdin 書き込み失敗検証
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(5)
    void test5_BrokenPipeRecovery() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 5: BROKEN PIPE DETECTION & RECOVERY");
        System.out.println("==========================================================================================");

        try (AgyCliTransport transport = new AgyCliTransport()) {
            transport.residentManager().ensureReady();
            long pid = getProcessPid(transport);
            System.out.printf("[Test 5] Active PID: %d. Abruptly killing process to force broken pipe...%n", pid);

            new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid)).start().waitFor();
            Thread.sleep(200);

            System.out.println("[Test 5] Sending request to dead process pipe...");
            AiTransportResult outcome = transport.sendOutcome(wrapPrompt("パイプ切断テスト"));

            System.out.printf("[Test 5 Outcome] Type: %s%n", outcome.getClass().getSimpleName());
            assertInstanceOf(AiTransportResult.Success.class, outcome, "ensureReady() must detect dead process and recover to Success");
            assertEquals(AgyResidentProcessManager.State.READY, transport.residentManager().getState(), "State must return to READY after transparent recovery");
            assertTrue(verifyStrictSchema(((AiTransportResult.Success) outcome).response()), "Recovered response must conform to strict schema");

            long newPid = getProcessPid(transport);
            System.out.printf("[Test 5 Recovery] Process cleanly recovered with new PID: %d (original killed PID was: %d)%n", newPid, pid);
            assertNotEquals(pid, newPid, "New process PID must differ from killed PID");
        }
    }

    // --------------------------------------------------------------------------------------------------
    // 6. VegaRuntimeGraph.close() 正常終了経路の実地確認
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(6)
    void test6_VegaRuntimeGraphCloseLifecycle() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 6: VEGA RUNTIME GRAPH CLOSE PROPAGATION & OS PROCESS CLEANUP");
        System.out.println("==========================================================================================");

        long agyPid = 0;
        try (VegaRuntimeGraph graph = VegaRuntimeGraphFactory.create(null, null, null)) {
            graph.start();
            System.out.println("[Test 6] VegaRuntimeGraph started.");

            // 通常チャットリクエストを発行して resident agy を起動
            LlmRequest req = new LlmRequest(
                    UUID.randomUUID().toString(),
                    List.of(LlmMessage.of(LlmMessageRole.USER, "ステータス確認")),
                    List.of(new LlmToolDefinition(SpeakFunction.ID, "Speak a message to the commander.", "", List.of())),
                    null
            );

            CompletableFuture<LlmResult> future = graph.llmGateway().submit(req);
            LlmResult res = future.get(20, TimeUnit.SECONDS);
            assertNotNull(res, "LLM request via VegaRuntimeGraph must succeed");
            System.out.printf("[Test 6 Request] LLM Result status: %s%n", res.status());

            // このグラフ自身が起動した resident agy の PID を、VegaRuntimeGraph -> LlmGateway ->
            // TurnRoutingLlmGateway -> chatGateway -> VegaLlmGateway -> AgyCliTransport -> AgyResidentProcessManager
            // の既存アクセス経路で直接取得する（OS全体を検索する findActiveAgyPid() は使わない：無関係な
            // 既存プロセスを拾ってしまう恐れがあるため）。
            AgyCliTransport agyTransport = resolveAgyTransport(graph);
            assertNotNull(agyTransport, "chatGateway's transport must be an AgyCliTransport");
            agyPid = getProcessPid(agyTransport);
            System.out.printf("[Test 6] Resolved this graph's own resident agy PID: %d%n", agyPid);
            assertTrue(agyPid > 0, "agy.exe must be running");

            System.out.println("[Test 6] Calling graph.close() to verify complete shutdown propagation...");
        }

        // graph.close() 完了後の検証
        Thread.sleep(500);
        boolean stillAlive = isProcessAlive(agyPid);
        System.out.printf("[Test 6 Result] agy.exe (PID %d) isAlive after graph.close(): %s%n", agyPid, stillAlive);
        assertFalse(stillAlive, "agy.exe process MUST NOT remain alive after VegaRuntimeGraph.close()");

        // OS全体のagyプロセス一覧を確認
        List<Long> leftoverAgys = findAllAgyPids();
        System.out.printf("[Test 6 Result] Leftover agy processes on OS: %s%n", leftoverAgys);
    }

    // --------------------------------------------------------------------------------------------------
    // 7. 複数サイクルの起動 → 終了 → 再起動検証
    // --------------------------------------------------------------------------------------------------
    @Test
    @Order(7)
    void test7_MultiCycleLifecycle() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 7: MULTI-CYCLE LIFECYCLE VERIFICATION (3 Start -> Turns -> Close Cycles)");
        System.out.println("==========================================================================================");

        int cycles = 3;
        for (int c = 1; c <= cycles; c++) {
            System.out.printf("%n>>> [Cycle %d/%d] Starting new AgyCliTransport <<< %n", c, cycles);
            long cyclePid;
            try (AgyCliTransport transport = new AgyCliTransport()) {
                AiTransportResult r1 = transport.sendOutcome(wrapPrompt("サイクル" + c + " ターン1"));
                assertInstanceOf(AiTransportResult.Success.class, r1);
                assertTrue(verifyStrictSchema(((AiTransportResult.Success) r1).response()));

                AiTransportResult r2 = transport.sendOutcome(wrapPrompt("サイクル" + c + " ターン2"));
                assertInstanceOf(AiTransportResult.Success.class, r2);
                assertTrue(verifyStrictSchema(((AiTransportResult.Success) r2).response()));

                cyclePid = getProcessPid(transport);
                System.out.printf("[Cycle %d] Active PID: %d. 2 turns completed with Strict Schema. Closing transport...%n", c, cyclePid);
            }

            Thread.sleep(300);
            assertFalse(isProcessAlive(cyclePid), "Process for cycle " + c + " must be terminated on close()");
            System.out.printf("[Cycle %d] Confirmed process PID %d cleanly terminated on OS.%n", c, cyclePid);
        }

        System.out.println("\n[Test 7 Summary] All 3 cycles cleanly initialized, served turns, and shut down without leakage.");
    }

    // --------------------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------------------
    private static boolean verifyStrictSchema(JsonObject json) {
        if (json == null) return false;
        if (json.has("tool_calls") && json.get("tool_calls").isJsonArray()) {
            JsonArray tcs = json.getAsJsonArray("tool_calls");
            if (!tcs.isEmpty()) {
                JsonObject first = tcs.get(0).getAsJsonObject();
                return first.has("name") && "speak".equals(first.get("name").getAsString()) && first.has("arguments");
            }
        }
        return json.has("name") && "speak".equals(json.get("name").getAsString());
    }

    /**
     * Walks the existing production access path (VegaRuntimeGraph -&gt; LlmGateway -&gt; TurnRoutingLlmGateway
     * -&gt; chatGateway -&gt; VegaLlmGateway -&gt; AgyCliTransport) to reach the exact {@link AgyCliTransport} this
     * graph's chat turns use, instead of searching the OS for any process named "agy". Same package as every
     * type on the path, so each step uses the existing package-private accessor - no reflection.
     */
    private static AgyCliTransport resolveAgyTransport(VegaRuntimeGraph graph) {
        if (graph.llmGateway() instanceof TurnRoutingLlmGateway routing
                && routing.chatGateway() instanceof VegaLlmGateway vega
                && vega.transport() instanceof AgyCliTransport agyTransport) {
            return agyTransport;
        }
        return null;
    }

    private static long getProcessPid(AgyCliTransport transport) {
        try {
            var field = AgyResidentProcessManager.class.getDeclaredField("process");
            field.setAccessible(true);
            Process proc = (Process) field.get(transport.residentManager());
            return proc != null ? proc.pid() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isProcessAlive(long pid) {
        if (pid <= 0) return false;
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private static long findActiveAgyPid() {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Get-Process agy -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isBlank()) {
                    return Long.parseLong(line.trim().split("\\s+")[0]);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static List<Long> findAllAgyPids() {
        List<Long> pids = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Get-Process agy -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.isBlank()) pids.add(Long.parseLong(line.trim()));
                }
            }
        } catch (Exception ignored) {}
        return pids;
    }
}
