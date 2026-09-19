package elite.intel.ai.brain.vega.llm;

import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Routes LLM requests based on turn type (§R.6.2):
 * - Tool-calling turns (!request.tools().isEmpty()) route to the command gateway (LM Studio / cloud API).
 * - Chat and summarization turns (request.tools().isEmpty()) route to the Antigravity CLI (agy) gateway.
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
        if (!request.tools().isEmpty()) {
            return toolGateway.submit(request);
        }
        return chatGateway.submit(request);
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
