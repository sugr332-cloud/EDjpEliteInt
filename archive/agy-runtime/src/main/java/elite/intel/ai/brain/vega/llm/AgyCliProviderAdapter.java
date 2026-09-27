package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import elite.intel.ai.brain.vega.model.llm.LlmMessage;
import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;
import elite.intel.ai.brain.vega.model.llm.LlmToolDefinition;
import elite.intel.ai.brain.vega.model.llm.LlmToolInvocation;
import elite.intel.util.json.GsonFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter bridging VEGA's {@link LlmRequest} / {@link LlmResult} protocol to the Antigravity CLI ({@code agy}).
 * <p>
 * Operational contracts (§R.6, §R.6.1):
 * <ul>
 *   <li><b>Tool-Calling Turn ({@code !request.tools().isEmpty()}):</b> Dynamically generates a strict JSON Schema
 *       constraining {@code name} to offered tools and enforcing stringified arguments. Encapsulates this in
 *       {@code {"prompt": ..., "json_schema": ...}} for {@link AgyCliTransport}.</li>
 *   <li><b>Compression / Plain-Text Turn ({@code request.tools().isEmpty()}):</b> Omits the schema constraint,
 *       emitting {@code {"prompt": ...}} to let {@code agy} respond in plain text.</li>
 *   <li><b>Response Parsing:</b> Prioritizes {@code structured_output.tool_calls}, with fallback to parsing
 *       the {@code response} string. Unparseable, missing, or malformed tool calls immediately yield
 *       {@link LlmResult.Status#INVALID_RESPONSE} for protocol repair.</li>
 * </ul>
 */
public final class AgyCliProviderAdapter implements LlmProviderAdapter {

    @Override
    public String buildRequestBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("prompt", renderPrompt(request));

        if (!request.tools().isEmpty()) {
            String schema = buildJsonSchema(request.tools());
            if (schema != null) {
                body.addProperty("json_schema", schema);
            }
        }

        return GsonFactory.getGson().toJson(body);
    }

    private String renderPrompt(LlmRequest request) {
        StringBuilder sb = new StringBuilder();
        for (LlmMessage m : request.messages()) {
            if (m.content() != null && !m.content().isBlank()) {
                switch (m.role()) {
                    case SYSTEM -> sb.append("[System Instructions]\n").append(m.content()).append("\n\n");
                    case USER -> sb.append("[Commander]\n").append(m.content()).append("\n\n");
                    case ASSISTANT -> sb.append("[Assistant]\n").append(m.content()).append("\n\n");
                    case TOOL -> sb.append("[Tool Result]\n").append(m.content()).append("\n\n");
                }
            }
        }

        if (!request.tools().isEmpty()) {
            sb.append("[Instruction]\n")
              .append("Select the appropriate tool from the offered tools to respond to the commander's request, ")
              .append("and output valid JSON conforming to the requested schema.");
        }

        return sb.toString().strip();
    }

    private String buildJsonSchema(List<LlmToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        JsonArray enumArray = new JsonArray();
        for (LlmToolDefinition tool : tools) {
            enumArray.add(tool.name());
        }

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.add("enum", enumArray);

        JsonObject argsProp = new JsonObject();
        argsProp.addProperty("type", "string");

        JsonObject itemsProps = new JsonObject();
        itemsProps.add("name", nameProp);
        itemsProps.add("arguments", argsProp);

        JsonArray requiredItemProps = new JsonArray();
        requiredItemProps.add("name");
        requiredItemProps.add("arguments");

        JsonObject items = new JsonObject();
        items.addProperty("type", "object");
        items.add("properties", itemsProps);
        items.add("required", requiredItemProps);

        JsonObject toolCallsProp = new JsonObject();
        toolCallsProp.addProperty("type", "array");
        toolCallsProp.add("items", items);

        JsonObject rootProps = new JsonObject();
        rootProps.add("tool_calls", toolCallsProp);

        JsonArray requiredRoot = new JsonArray();
        requiredRoot.add("tool_calls");

        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.add("properties", rootProps);
        root.add("required", requiredRoot);

        return GsonFactory.getGson().toJson(root);
    }

    @Override
    public LlmResult parse(JsonObject response) {
        try {
            JsonArray toolCalls = extractToolCalls(response);
            if (toolCalls == null || toolCalls.isEmpty()) {
                return invalid(extractPlainTextIfNonJson(response));
            }

            List<LlmToolInvocation> invocations = new ArrayList<>();
            for (JsonElement element : toolCalls) {
                if (!element.isJsonObject()) {
                    return invalid(null);
                }
                JsonObject call = element.getAsJsonObject();
                if (!call.has("name") || call.get("name").isJsonNull()) {
                    return invalid(null);
                }
                String name = call.get("name").getAsString();
                if (name == null || name.isBlank()) {
                    return invalid(null);
                }

                String id = call.has("id") && !call.get("id").isJsonNull() ? call.get("id").getAsString() : null;
                JsonObject arguments = parseArguments(call.get("arguments"));
                invocations.add(new LlmToolInvocation(id, name, arguments));
            }

            // Successfully parsed tool calls: droppedText is null because the model responded with tools
            return new LlmResult(LlmResult.Status.OK, invocations, "stop", null);
        } catch (RuntimeException malformed) {
            return invalid(extractPlainTextIfNonJson(response));
        }
    }

    private JsonArray extractToolCalls(JsonObject response) {
        if (response == null) {
            return null;
        }

        // 1. Primary path: structured_output.tool_calls
        if (response.has("structured_output") && response.get("structured_output").isJsonObject()) {
            JsonObject structured = response.getAsJsonObject("structured_output");
            if (structured.has("tool_calls") && structured.get("tool_calls").isJsonArray()) {
                return structured.getAsJsonArray("tool_calls");
            }
        }

        // 2. Fallback path: response property containing JSON string
        if (response.has("response") && response.get("response").isJsonPrimitive()) {
            try {
                JsonElement parsed = JsonParser.parseString(response.get("response").getAsString());
                if (parsed.isJsonObject()) {
                    JsonObject obj = parsed.getAsJsonObject();
                    if (obj.has("tool_calls") && obj.get("tool_calls").isJsonArray()) {
                        return obj.getAsJsonArray("tool_calls");
                    }
                }
            } catch (JsonSyntaxException ignored) {
            }
        }

        return null;
    }

    private JsonObject parseArguments(JsonElement arguments) {
        if (arguments == null || arguments.isJsonNull()) {
            return new JsonObject();
        }
        if (arguments.isJsonObject()) {
            return arguments.getAsJsonObject();
        }
        if (arguments.isJsonPrimitive() && arguments.getAsJsonPrimitive().isString()) {
            String str = arguments.getAsString().strip();
            if (str.isEmpty()) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(str);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            throw new IllegalArgumentException("Parsed arguments was not a JSON object: " + str);
        }
        throw new IllegalArgumentException("Tool arguments must be a JSON object or string, got: " + arguments);
    }

    @Override
    public String parseText(JsonObject response) {
        if (response == null) {
            return null;
        }
        try {
            if (response.has("response") && !response.get("response").isJsonNull()) {
                String text = response.get("response").getAsString();
                return text == null || text.isBlank() ? null : text.strip();
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Extracts droppedText only when the response is free-form plain text and NOT a JSON structure.
     */
    private String extractPlainTextIfNonJson(JsonObject response) {
        String text = parseText(response);
        if (text == null) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed.isJsonObject() || parsed.isJsonArray()) {
                // If it was a JSON structure that failed tool-call validation, it is not plain text
                return null;
            }
        } catch (JsonSyntaxException ignored) {
            // It is indeed plain text (e.g. model answered directly in Japanese instead of tool-calling)
        }
        return text;
    }

    private static LlmResult invalid(String droppedText) {
        return new LlmResult(LlmResult.Status.INVALID_RESPONSE, List.of(), null, droppedText);
    }
}
