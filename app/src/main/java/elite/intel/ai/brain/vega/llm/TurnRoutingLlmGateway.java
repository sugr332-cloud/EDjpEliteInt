package elite.intel.ai.brain.vega.llm;

import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;
import elite.intel.ai.brain.vega.tools.SpeakFunction;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Routes LLM requests based on turn type (§R.6.2):
 * - Tool-calling turns (tools() offers something beyond {@link SpeakFunction}) route to the command gateway
 *   (LM Studio / cloud API).
 * - Chat and summarization turns (tools() is empty, or offers {@link SpeakFunction} only - the one system
 *   function every COMMANDER turn carries regardless of content) route to the Antigravity CLI (agy) gateway.
 *   {@code request.tools().isEmpty()} alone cannot detect this: {@code ComposedPrompt.tools()} always unions in
 *   system functions, so a plain "hello" still carries {@code speak} even though {@code SemanticActionReducer}
 *   already reduced game tools to none.
 */
public final class TurnRoutingLlmGateway implements LlmGateway {

    private final LlmGateway toolGateway;
    private final LlmGateway chatGateway;

    public TurnRoutingLlmGateway(LlmGateway toolGateway, LlmGateway chatGateway) {
        this.toolGateway = Objects.requireNonNull(toolGateway, "toolGateway");
        this.chatGateway = Objects.requireNonNull(chatGateway, "chatGateway");
    }

    @Override
    public CompletableFuture<LlmResult> submit(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        if (isToolCallingTurn(request)) {
            return toolGateway.submit(request);
        }
        return chatGateway.submit(request);
    }

    /** True when the request offers any tool other than {@link SpeakFunction}. */
    private static boolean isToolCallingTurn(LlmRequest request) {
        return request.tools().stream().anyMatch(tool -> !SpeakFunction.ID.equals(tool.name()));
    }

    @Override
    public CompletableFuture<String> completePlainText(LlmRequest request) {
        return chatGateway.completePlainText(request);
    }

    @Override
    public void close() {
        try {
            toolGateway.close();
        } finally {
            chatGateway.close();
        }
    }

    LlmGateway toolGateway() {
        return toolGateway;
    }

    LlmGateway chatGateway() {
        return chatGateway;
    }
}
