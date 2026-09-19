package elite.intel.ai.brain.vega.llm;

import elite.intel.ai.brain.vega.model.llm.LlmMessage;
import elite.intel.ai.brain.vega.model.llm.LlmMessageRole;
import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;
import elite.intel.ai.brain.vega.model.llm.LlmToolDefinition;
import elite.intel.ai.brain.vega.model.llm.PromptCacheProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TurnRoutingLlmGatewayTest {

    private static class RecordingGateway implements LlmGateway {
        final AtomicReference<LlmRequest> lastSubmit = new AtomicReference<>();
        final AtomicReference<LlmRequest> lastCompletePlainText = new AtomicReference<>();
        final AtomicBoolean closed = new AtomicBoolean(false);
        final CompletableFuture<LlmResult> submitResult = new CompletableFuture<>();
        final CompletableFuture<String> plainTextResult = new CompletableFuture<>();

        @Override
        public CompletableFuture<LlmResult> submit(LlmRequest request) {
            lastSubmit.set(request);
            return submitResult;
        }

        @Override
        public CompletableFuture<String> completePlainText(LlmRequest request) {
            lastCompletePlainText.set(request);
            return plainTextResult;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static LlmRequest requestWithTools() {
        return new LlmRequest(
                "req-tool",
                List.of(LlmMessage.of(LlmMessageRole.USER, "deploy cargo scoop")),
                List.of(new LlmToolDefinition("deploy_cargo_scoop", "deploys scoop", "", List.of())),
                PromptCacheProfile.COMMANDER
        );
    }

    private static LlmRequest requestWithoutTools() {
        return new LlmRequest(
                "req-chat",
                List.of(LlmMessage.of(LlmMessageRole.USER, "hello")),
                List.of(),
                PromptCacheProfile.COMMANDER
        );
    }

    @Test
    void testSubmitWithToolsRoutesToToolGateway() {
        RecordingGateway toolGateway = new RecordingGateway();
        RecordingGateway chatGateway = new RecordingGateway();
        TurnRoutingLlmGateway router = new TurnRoutingLlmGateway(toolGateway, chatGateway);

        LlmRequest request = requestWithTools();
        CompletableFuture<LlmResult> future = router.submit(request);

        assertSame(toolGateway.submitResult, future);
        assertSame(request, toolGateway.lastSubmit.get());
        assertNull(chatGateway.lastSubmit.get(), "chatGateway must NOT be invoked when tools are present");
    }

    @Test
    void testSubmitWithoutToolsRoutesToChatGateway() {
        RecordingGateway toolGateway = new RecordingGateway();
        RecordingGateway chatGateway = new RecordingGateway();
        TurnRoutingLlmGateway router = new TurnRoutingLlmGateway(toolGateway, chatGateway);

        LlmRequest request = requestWithoutTools();
        CompletableFuture<LlmResult> future = router.submit(request);

        assertSame(chatGateway.submitResult, future);
        assertSame(request, chatGateway.lastSubmit.get());
        assertNull(toolGateway.lastSubmit.get(), "toolGateway must NOT be invoked when tools are empty");
    }

    @Test
    void testCompletePlainTextRoutesToChatGateway() {
        RecordingGateway toolGateway = new RecordingGateway();
        RecordingGateway chatGateway = new RecordingGateway();
        TurnRoutingLlmGateway router = new TurnRoutingLlmGateway(toolGateway, chatGateway);

        LlmRequest request = requestWithoutTools();
        CompletableFuture<String> future = router.completePlainText(request);

        assertSame(chatGateway.plainTextResult, future);
        assertSame(request, chatGateway.lastCompletePlainText.get());
        assertNull(toolGateway.lastCompletePlainText.get(), "toolGateway must NOT be invoked for completePlainText");
    }

    @Test
    void testCloseClosesBothGatewaysAndPropagatesException() {
        AtomicBoolean chatClosed = new AtomicBoolean(false);
        LlmGateway failingToolGateway = new RecordingGateway() {
            @Override
            public void close() {
                throw new RuntimeException("toolGateway close failure");
            }
        };
        LlmGateway trackingChatGateway = new RecordingGateway() {
            @Override
            public void close() {
                chatClosed.set(true);
            }
        };

        TurnRoutingLlmGateway router = new TurnRoutingLlmGateway(failingToolGateway, trackingChatGateway);

        RuntimeException ex = assertThrows(RuntimeException.class, router::close);
        assertEquals("toolGateway close failure", ex.getMessage());
        assertTrue(chatClosed.get(), "chatGateway must be closed even when toolGateway throws");
    }

    @Test
    void testConstructorRejectsNullGateways() {
        RecordingGateway valid = new RecordingGateway();
        assertThrows(NullPointerException.class, () -> new TurnRoutingLlmGateway(null, valid));
        assertThrows(NullPointerException.class, () -> new TurnRoutingLlmGateway(valid, null));
    }
}
