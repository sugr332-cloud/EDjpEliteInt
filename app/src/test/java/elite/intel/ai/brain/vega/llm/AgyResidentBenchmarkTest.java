package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.AiTransportResult;
import elite.intel.ai.brain.vega.model.ThoughtSource;
import elite.intel.ai.brain.vega.model.llm.LlmMessage;
import elite.intel.ai.brain.vega.model.llm.LlmMessageRole;
import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;
import elite.intel.ai.brain.vega.model.llm.LlmToolDefinition;
import elite.intel.ai.brain.vega.model.llm.PromptCacheProfile;
import elite.intel.ai.brain.vega.prompt.VegaSystemPrompt;
import elite.intel.ai.brain.vega.tools.SpeakFunction;
import elite.intel.util.json.GsonFactory;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dedicated benchmark and routing verification suite for Antigravity CLI (agy) transport.
 * <p>
 * Contains two completely independent tests:
 * <ol>
 *   <li>{@link #test1_RoutingSmokeTest()}: Smoke test independently verifying {@link TurnRoutingLlmGateway}
 *       routing correctness (chatGateway vs toolGateway) based on non-speak tool presence.</li>
 *   <li>{@link #test2_ABBenchmarkComparison50()}: Official A/B benchmark comparing production persistent
 *       {@link AgyResidentProcessManager} against legacy one-shot process execution across 50 identical fixed prompts.</li>
 * </ol>
 */
@Tag("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AgyResidentBenchmarkTest {

    private static final String SYSTEM_PROMPT = new VegaSystemPrompt().staticRules(ThoughtSource.COMMANDER);

    /**
     * 50 fixed, independent short Japanese conversation prompts.
     * Evaluated in identical order with identical wrapping for both Resident and One-shot modes.
     */
    private static final List<String> BENCHMARK_PROMPTS_50 = List.of(
            "こんにちは、司令官のステータスを確認したい",
            "現在のシールドは何パーセント？",
            "燃料残量はどう？",
            "目的地はどこになっている？",
            "今何時？",
            "貨物庫に何が入っている？",
            "エンジンの状態は？",
            "異常はあるか？",
            "レーダー範囲内の脅威は？",
            "了解、ありがとう。スタンバイして",
            "右舷側の船体強度は大丈夫か？",
            "残りのジャンプ回数はいくつだ？",
            "コクピットの照明を暗くして",
            "最寄りのステーションの名前を教えて",
            "通信ログに新しいメッセージはある？",
            "艦内気圧は正常に保たれているか？",
            "パワープラントの稼働率は？",
            "センサーの感度設定はどうなっている？",
            "本日の航行距離はどのくらい？",
            "リザーブタンクの燃料は足りているか？",
            "スラスターの温度上昇は見られるか？",
            "船体塗装の損耗具合はどうだ？",
            "周辺宙域の警報レベルを教えて",
            "ナビゲーションビーコンを捕捉しているか？",
            "次の補給ポイントまでどのくらい？",
            "ドッキング許可の申請状況はどう？",
            "カーゴスクープの開閉状態を確認して",
            "生命維持装置の残り稼働時間は？",
            "主砲の電力チャージ状況を教えて",
            "電子対抗策の残弾数は？",
            "熱排出システムの効率は良好か？",
            "FSDの冷却は完了したか？",
            "惑星地表までの高度はどのくらい？",
            "現在の巡航速度を教えて",
            "スキャン完了まであと何秒？",
            "機体各部の気密チェック結果は？",
            "近くに採掘可能な小惑星はあるか？",
            "シールドセルバンクの残数は？",
            "自動修復システムの診断結果はどう？",
            "星系警察のパトロール船は見当たるか？",
            "現在の機体質量はどのくらい？",
            "重力アシストの進入角は適正か？",
            "アレイアンテナの受信強度は？",
            "ブラックボックスの記録は正常か？",
            "緊急脱出ポッドの状態を確認して",
            "船内空調の温度を一度下げて",
            "燃料消費効率のログを見せて",
            "外部装甲の放射線レベルは？",
            "ステーションへの進入速度は制限内か？",
            "お疲れさま、本日の航海記録を保存して"
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
        obj.addProperty("json_schema", AgyResidentProcessManager.buildFixedSpeakSchema());
        return GsonFactory.getGson().toJson(obj);
    }

    /**
     * Verifies that the JSON response structurally conforms to the speak tool output envelope.
     * <p>
     * Note: This method only verifies the presence of {@code tool_calls}, that the first tool call
     * name is {@code "speak"}, and the existence of an {@code arguments} field (or top-level speak name).
     * It does not perform comprehensive schema constraint validation (e.g. types, required properties).
     */
    private static boolean verifyStrictSchema(JsonObject json) {
        if (json == null) return false;
        if (json.has("structured_output") && json.get("structured_output").isJsonObject()) {
            json = json.getAsJsonObject("structured_output");
        }
        if (json.has("tool_calls") && json.get("tool_calls").isJsonArray()) {
            JsonArray tcs = json.getAsJsonArray("tool_calls");
            if (!tcs.isEmpty()) {
                JsonObject first = tcs.get(0).getAsJsonObject();
                return first.has("name") && "speak".equals(first.get("name").getAsString()) && first.has("arguments");
            }
        }
        return json.has("name") && "speak".equals(json.get("name").getAsString());
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

    record Stats(double mean, long median, long p95, long min, long max) {}

    private static Stats calculateStats(List<Long> latencies) {
        if (latencies.isEmpty()) return new Stats(0, 0, 0, 0, 0);
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long median = sorted.get(sorted.size() / 2);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        long p95 = sorted.get((int) Math.min(sorted.size() - 1, Math.floor(sorted.size() * 0.95)));
        return new Stats(mean, median, p95, min, max);
    }

    // ==========================================================================================
    // 1. TurnRoutingLlmGateway 独立ルーティング Smoke Test
    // ==========================================================================================
    private static final class CountingGateway implements LlmGateway {
        final AtomicInteger submitCalls = new AtomicInteger(0);
        final CompletableFuture<LlmResult> submitResult = new CompletableFuture<>();

        @Override
        public CompletableFuture<LlmResult> submit(LlmRequest request) {
            submitCalls.incrementAndGet();
            return submitResult;
        }

        @Override
        public CompletableFuture<String> completePlainText(LlmRequest request) {
            return CompletableFuture.completedFuture("plain text");
        }

        @Override
        public void close() {}
    }

    @Test
    @Order(1)
    void test1_RoutingSmokeTest() {
        System.out.println("==========================================================================================");
        System.out.println("TEST 1: TURN-ROUTING GATEWAY INDEPENDENT SMOKE TEST");
        System.out.println("==========================================================================================");

        CountingGateway toolGateway = new CountingGateway();
        CountingGateway chatGateway = new CountingGateway();
        TurnRoutingLlmGateway routingGateway = new TurnRoutingLlmGateway(toolGateway, chatGateway);

        LlmToolDefinition speakTool = new LlmToolDefinition("speak", "Speak to commander", "{}", List.of());
        LlmToolDefinition internalTool = new LlmToolDefinition("docking_request", "Request docking", "{}", List.of());

        // Case A: SpeakFunction のみの通常会話リクエスト -> chatGateway (agy) へ到達すること
        LlmRequest chatReq = new LlmRequest(
                "req-chat",
                List.of(LlmMessage.of(LlmMessageRole.USER, "こんにちは")),
                List.of(speakTool),
                PromptCacheProfile.COMMANDER
        );
        CompletableFuture<LlmResult> futA = routingGateway.submit(chatReq);
        assertSame(chatGateway.submitResult, futA);
        assertEquals(1, chatGateway.submitCalls.get(), "Chat request must reach chatGateway");
        assertEquals(0, toolGateway.submitCalls.get(), "Chat request must not reach toolGateway");

        // Case B: SpeakFunction 以外の内部ツールを含むリクエスト -> toolGateway (LM Studio/Cloud) へ到達すること
        LlmRequest toolReq = new LlmRequest(
                "req-tool",
                List.of(LlmMessage.of(LlmMessageRole.USER, "ドッキングを申請して")),
                List.of(speakTool, internalTool),
                PromptCacheProfile.COMMANDER
        );
        CompletableFuture<LlmResult> futB = routingGateway.submit(toolReq);
        assertSame(toolGateway.submitResult, futB);
        assertEquals(1, chatGateway.submitCalls.get(), "Tool request must not increment chatGateway");
        assertEquals(1, toolGateway.submitCalls.get(), "Tool request must reach toolGateway");

        System.out.println("[Routing Smoke Test Result] PASS: Case A (speak-only) -> chatGateway (calls=1), Case B (with non-speak tool) -> toolGateway (calls=1)");
    }

    // ==========================================================================================
    // 2. A/B ベンチマーク比較 (50ターン: Resident vs One-shot)
    // ==========================================================================================
    @Test
    @Order(2)
    void test2_ABBenchmarkComparison50() throws Exception {
        System.out.println("\n==========================================================================================");
        System.out.println("TEST 2: A/B BENCHMARK COMPARISON (50 TURNS: RESIDENT vs ONE-SHOT)");
        System.out.println("==========================================================================================");

        int totalTurns = BENCHMARK_PROMPTS_50.size();
        assertEquals(50, totalTurns, "Must have exactly 50 benchmark prompts");

        // --------------------------------------------------------------------------------------
        // Phase 1: Resident 常駐測定 (50 ターン)
        // --------------------------------------------------------------------------------------
        System.out.println("\n--- [Phase 1: Resident Mode (50 Turns)] ---");
        List<Long> residentLatencies = new ArrayList<>();
        List<Long> residentPids = new ArrayList<>();
        int residentSuccessCount = 0;
        int residentStrictOk = 0;
        int residentInternalToolCount = 0;
        int residentTimeoutCount = 0;
        int residentErrorCount = 0;
        long residentInitialPid = 0;

        try (AgyCliTransport transport = new AgyCliTransport()) {
            assertNotNull(transport.residentManager(), "Resident manager must be initialized");

            for (int i = 0; i < totalTurns; i++) {
                int turnNum = i + 1;
                String input = BENCHMARK_PROMPTS_50.get(i);
                String wrappedPrompt = wrapPrompt(input);

                long tStart = System.nanoTime();
                AiTransportResult result = transport.sendOutcome(wrappedPrompt);
                long elapsedMs = (System.nanoTime() - tStart) / 1_000_000;
                residentLatencies.add(elapsedMs);

                long currentPid = getProcessPid(transport);
                residentPids.add(currentPid);

                if (turnNum == 1) {
                    residentInitialPid = currentPid;
                    assertTrue(residentInitialPid > 0, "Initial PID must be valid (>0)");
                } else {
                    assertEquals(residentInitialPid, currentPid,
                            "Resident PID must remain identical across all turns (no restarts). Turn " + turnNum + " PID: " + currentPid);
                }

                boolean isStrict = false;
                if (result instanceof AiTransportResult.Success success) {
                    residentSuccessCount++;
                    JsonObject json = success.response();
                    isStrict = verifyStrictSchema(json);
                    if (json.has("name") && !"speak".equals(json.get("name").getAsString())) {
                        residentInternalToolCount++;
                    }
                } else if (result instanceof AiTransportResult.Failure failure) {
                    residentErrorCount++;
                    if (failure.diagnostic().contains("timed out") || failure.diagnostic().contains("timeout")) {
                        residentTimeoutCount++;
                    }
                }

                if (isStrict) residentStrictOk++;

                if (turnNum == 1 || turnNum % 10 == 0 || turnNum == totalTurns) {
                    System.out.printf("[Resident Turn %2d/%d] Elapsed=%5dms | PID=%d | StrictOK=%-3s | State=%s%n",
                            turnNum, totalTurns, elapsedMs, currentPid, isStrict ? "YES" : "NO", transport.residentManager().getState());
                }
            }
        }

        // --------------------------------------------------------------------------------------
        // Phase 2: One-shot 測定 (50 ターン)
        // --------------------------------------------------------------------------------------
        System.out.println("\n--- [Phase 2: One-shot Mode (50 Turns)] ---");
        List<Long> oneshotLatencies = new ArrayList<>();
        int oneshotSuccessCount = 0;
        int oneshotStrictOk = 0;
        int oneshotInternalToolCount = 0;
        int oneshotTimeoutCount = 0;
        int oneshotErrorCount = 0;

        AtomicInteger launchCounter = new AtomicInteger(0);
        AgyCliTransport.ProcessStarter processStarter = pb -> {
            launchCounter.incrementAndGet();
            return pb.start();
        };

        try (AgyCliTransport oneShotTransport = new AgyCliTransport("agy", Duration.ofSeconds(15), processStarter)) {
            assertNull(oneShotTransport.residentManager(), "One-shot transport must not have resident manager");

            for (int i = 0; i < totalTurns; i++) {
                int turnNum = i + 1;
                String input = BENCHMARK_PROMPTS_50.get(i);
                String wrappedPrompt = wrapPrompt(input);

                long tStart = System.nanoTime();
                AiTransportResult result = oneShotTransport.sendOutcome(wrappedPrompt);
                long elapsedMs = (System.nanoTime() - tStart) / 1_000_000;
                oneshotLatencies.add(elapsedMs);

                boolean isStrict = false;
                if (result instanceof AiTransportResult.Success success) {
                    oneshotSuccessCount++;
                    JsonObject json = success.response();
                    isStrict = verifyStrictSchema(json);
                    if (json.has("name") && !"speak".equals(json.get("name").getAsString())) {
                        oneshotInternalToolCount++;
                    }
                } else if (result instanceof AiTransportResult.Failure failure) {
                    oneshotErrorCount++;
                    if (failure.diagnostic().contains("timed out") || failure.diagnostic().contains("timeout")) {
                        oneshotTimeoutCount++;
                    }
                }

                if (isStrict) oneshotStrictOk++;

                if (turnNum == 1 || turnNum % 10 == 0 || turnNum == totalTurns) {
                    System.out.printf("[One-shot Turn %2d/%d] Elapsed=%5dms | LaunchCount=%d | StrictOK=%-3s%n",
                            turnNum, totalTurns, elapsedMs, launchCounter.get(), isStrict ? "YES" : "NO");
                }
            }
        }

        int actualLaunches = launchCounter.get();
        System.out.printf("[One-shot Process Launch Verification] Expected: %d, Actual: %d%n", totalTurns, actualLaunches);

        // --------------------------------------------------------------------------------------
        // Phase 3: 統計算出 & 全件レポート出力
        // --------------------------------------------------------------------------------------
        System.out.println("\n==========================================================================================");
        System.out.println("A/B BENCHMARK SUMMARY REPORT");
        System.out.println("==========================================================================================");

        System.out.println("\n[All Elapsed Milliseconds]");
        System.out.println("Resident (Turn 1..50): " + residentLatencies);
        System.out.println("One-shot (Turn 1..50): " + oneshotLatencies);

        long residentCold = residentLatencies.get(0);
        List<Long> residentWarmList = residentLatencies.subList(1, residentLatencies.size());
        Stats residentWarmStats = calculateStats(residentWarmList);
        Stats residentOverallStats = calculateStats(residentLatencies);
        Stats oneshotStats = calculateStats(oneshotLatencies);

        System.out.println("\n[Summary Metrics]");
        System.out.printf("%-25s | %-12s | %-12s | %-12s | %-12s | %-12s%n",
                "Mode", "Mean (ms)", "Median (ms)", "P95 (ms)", "Min (ms)", "Max (ms)");
        System.out.println("----------------------------------------------------------------------------------------------");
        System.out.printf("%-25s | %12.1f | %12d | %12d | %12d | %12d%n",
                "Resident Warm (2..50)", residentWarmStats.mean(), residentWarmStats.median(), residentWarmStats.p95(), residentWarmStats.min(), residentWarmStats.max());
        System.out.printf("%-25s | %12.1f | %12d | %12d | %12d | %12d%n",
                "Resident Overall (1..50)", residentOverallStats.mean(), residentOverallStats.median(), residentOverallStats.p95(), residentOverallStats.min(), residentOverallStats.max());
        System.out.printf("%-25s | %12.1f | %12d | %12d | %12d | %12d%n",
                "One-shot Overall (1..50)", oneshotStats.mean(), oneshotStats.median(), oneshotStats.p95(), oneshotStats.min(), oneshotStats.max());
        System.out.printf("Resident Cold (Turn 1): %d ms%n", residentCold);

        System.out.println("\n[Lifecycle & Stability Metrics]");
        System.out.printf("- Resident Initial PID: %d, Final PID: %d, Restarts: %d%n",
                residentInitialPid, residentPids.get(residentPids.size() - 1),
                residentPids.stream().distinct().count() - 1);
        System.out.printf("- Resident Success: %d/%d, Strict Schema (speak tool envelope): %d/%d (%.1f%%)%n",
                residentSuccessCount, totalTurns, residentStrictOk, totalTurns, (double) residentStrictOk / totalTurns * 100.0);
        System.out.printf("- Resident Timeouts: %d, Errors: %d, InternalTool: %d%n",
                residentTimeoutCount, residentErrorCount, residentInternalToolCount);

        System.out.printf("- One-shot Actual Launches: %d / %d (match: %b)%n",
                actualLaunches, totalTurns, actualLaunches == totalTurns);
        System.out.printf("- One-shot Success: %d/%d, Strict Schema (speak tool envelope): %d/%d (%.1f%%)%n",
                oneshotSuccessCount, totalTurns, oneshotStrictOk, totalTurns, (double) oneshotStrictOk / totalTurns * 100.0);
        System.out.printf("- One-shot Timeouts: %d, Errors: %d, InternalTool: %d%n",
                oneshotTimeoutCount, oneshotErrorCount, oneshotInternalToolCount);

        // Verification assertions
        assertEquals(totalTurns, residentLatencies.size());
        assertEquals(totalTurns, oneshotLatencies.size());
        assertEquals(1, residentPids.stream().distinct().count(), "Resident PID must be constant across all 50 turns");
    }
}
