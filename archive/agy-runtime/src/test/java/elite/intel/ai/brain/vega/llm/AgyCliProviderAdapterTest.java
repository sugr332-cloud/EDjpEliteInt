package elite.intel.ai.brain.vega.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elite.intel.ai.brain.vega.model.llm.LlmMessage;
import elite.intel.ai.brain.vega.model.llm.LlmMessageRole;
import elite.intel.ai.brain.vega.model.llm.LlmRequest;
import elite.intel.ai.brain.vega.model.llm.LlmResult;
import elite.intel.ai.brain.vega.model.llm.LlmToolDefinition;
import elite.intel.ai.brain.vega.model.llm.LlmToolInvocation;
import elite.intel.ai.brain.vega.model.llm.PromptCacheProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgyCliProviderAdapterTest {

    private final AgyCliProviderAdapter adapter = new AgyCliProviderAdapter();

    private static LlmToolDefinition tool(String name) {
        return new LlmToolDefinition(name, "description for " + name, "", List.of());
    }

    private static LlmRequest requestWithTools(List<LlmToolDefinition> tools) {
        return new LlmRequest(
                "req-1",
                List.of(
                        LlmMessage.of(LlmMessageRole.SYSTEM, "You are an assistant."),
                        LlmMessage.of(LlmMessageRole.USER, "Show me status.")
                ),
                tools,
                PromptCacheProfile.COMMANDER
        );
    }

    private static LlmRequest requestWithoutTools() {
        return new LlmRequest(
                "req-2",
                List.of(
                        LlmMessage.of(LlmMessageRole.SYSTEM, "Summarize the history."),
                        LlmMessage.of(LlmMessageRole.USER, "Do it now.")
                ),
                List.of(),
                PromptCacheProfile.COMPRESSION
        );
    }

    @Test
    void testBuildRequestBodyWithToolsIncludesPromptAndJsonSchemaWithEnum() {
        List<LlmToolDefinition> tools = List.of(
                tool("query_local_outfitting"),
                tool("find_commodity"),
                tool("speak")
        );
        LlmRequest request = requestWithTools(tools);

        String bodyString = adapter.buildRequestBody(request);
        JsonObject body = JsonParser.parseString(bodyString).getAsJsonObject();

        assertTrue(body.has("prompt"), "body must contain prompt");
        String prompt = body.get("prompt").getAsString();
        assertTrue(prompt.contains("You are an assistant."));
        assertTrue(prompt.contains("Show me status."));
        assertTrue(prompt.contains("[Instruction]"));

        assertTrue(body.has("json_schema"), "body must contain json_schema when tools are present");
        String schemaString = body.get("json_schema").getAsString();
        JsonObject schema = JsonParser.parseString(schemaString).getAsJsonObject();

        assertEquals("object", schema.get("type").getAsString());
        JsonObject properties = schema.getAsJsonObject("properties");
        assertTrue(properties.has("tool_calls"));

        JsonObject toolCalls = properties.getAsJsonObject("tool_calls");
        assertEquals("array", toolCalls.get("type").getAsString());
        JsonObject items = toolCalls.getAsJsonObject("items");
        JsonObject itemProperties = items.getAsJsonObject("properties");
        JsonObject nameProp = itemProperties.getAsJsonObject("name");

        assertTrue(nameProp.has("enum"));
        JsonArray enumArray = nameProp.getAsJsonArray("enum");
        List<String> enumValues = new ArrayList<>();
        enumArray.forEach(e -> enumValues.add(e.getAsString()));

        assertEquals(3, enumValues.size());
        assertTrue(enumValues.contains("query_local_outfitting"));
        assertTrue(enumValues.contains("find_commodity"));
        assertTrue(enumValues.contains("speak"));

        JsonObject argsProp = itemProperties.getAsJsonObject("arguments");
        assertEquals("string", argsProp.get("type").getAsString());
    }

    @Test
    void testBuildRequestBodyWithoutToolsOmitsJsonSchema() {
        LlmRequest request = requestWithoutTools();

        String bodyString = adapter.buildRequestBody(request);
        JsonObject body = JsonParser.parseString(bodyString).getAsJsonObject();

        assertTrue(body.has("prompt"), "body must contain prompt");
        assertFalse(body.has("json_schema"), "body must NOT contain json_schema when tools are empty");
    }

    @Test
    void testParseStructuredOutputToolCallsReturnsOk() {
        String jsonStr = """
                {
                  "status": "SUCCESS",
                  "structured_output": {
                    "tool_calls": [
                      {
                        "name": "find_commodity",
                        "arguments": "{\\"commodity\\":\\"Gold\\",\\"radius\\":10}"
                      }
                    ]
                  },
                  "response": "something"
                }
                """;
        JsonObject response = JsonParser.parseString(jsonStr).getAsJsonObject();

        LlmResult result = adapter.parse(response);

        assertEquals(LlmResult.Status.OK, result.status());
        assertEquals(1, result.toolInvocations().size());
        LlmToolInvocation invocation = result.toolInvocations().get(0);
        assertEquals("find_commodity", invocation.name());
        assertEquals("Gold", invocation.arguments().get("commodity").getAsString());
        assertEquals(10, invocation.arguments().get("radius").getAsInt());
        assertNull(result.droppedText(), "droppedText should be null on successful tool call parse");
    }

    @Test
    void testParseFallbackToResponseStringWhenStructuredOutputAbsent() {
        String jsonStr = """
                {
                  "status": "SUCCESS",
                  "response": "{\\"toolAction\\":\\"Checking\\",\\"tool_calls\\":[{\\"name\\":\\"speak\\",\\"arguments\\":\\"{\\\\\\"text\\\\\\":\\\\\\"Hello Commander\\\\\\"}\\"}]}"
                }
                """;
        JsonObject response = JsonParser.parseString(jsonStr).getAsJsonObject();

        LlmResult result = adapter.parse(response);

        assertEquals(LlmResult.Status.OK, result.status());
        assertEquals(1, result.toolInvocations().size());
        LlmToolInvocation invocation = result.toolInvocations().get(0);
        assertEquals("speak", invocation.name());
        assertEquals("Hello Commander", invocation.arguments().get("text").getAsString());
    }

    @Test
    void testParseReturnsInvalidResponseWhenToolCallsMissingOrEmpty() {
        // Case 1: Missing tool_calls entirely
        JsonObject missing = JsonParser.parseString("{\"status\":\"SUCCESS\"}").getAsJsonObject();
        assertEquals(LlmResult.Status.INVALID_RESPONSE, adapter.parse(missing).status());

        // Case 2: Empty tool_calls array
        String emptyStr = "{\"status\":\"SUCCESS\",\"structured_output\":{\"tool_calls\":[]}}";
        JsonObject empty = JsonParser.parseString(emptyStr).getAsJsonObject();
        assertEquals(LlmResult.Status.INVALID_RESPONSE, adapter.parse(empty).status());
    }

    @Test
    void testParseArgumentsConvertsStringifiedJsonToProperJsonObject() {
        // Case 1: Empty object string "{}"
        String emptyObjStr = """
                {
                  "structured_output": {
                    "tool_calls": [
                      { "name": "speak", "arguments": "{}" }
                    ]
                  }
                }
                """;
        LlmResult emptyObjResult = adapter.parse(JsonParser.parseString(emptyObjStr).getAsJsonObject());
        assertEquals(LlmResult.Status.OK, emptyObjResult.status());
        assertEquals(0, emptyObjResult.toolInvocations().get(0).arguments().size());

        // Case 2: Blank string ""
        String blankStr = """
                {
                  "structured_output": {
                    "tool_calls": [
                      { "name": "speak", "arguments": "" }
                    ]
                  }
                }
                """;
        LlmResult blankResult = adapter.parse(JsonParser.parseString(blankStr).getAsJsonObject());
        assertEquals(LlmResult.Status.OK, blankResult.status());
        assertEquals(0, blankResult.toolInvocations().get(0).arguments().size());

        // Case 3: Already-parsed JsonObject in arguments
        String directObjStr = """
                {
                  "structured_output": {
                    "tool_calls": [
                      { "name": "speak", "arguments": { "key": "value" } }
                    ]
                  }
                }
                """;
        LlmResult directResult = adapter.parse(JsonParser.parseString(directObjStr).getAsJsonObject());
        assertEquals(LlmResult.Status.OK, directResult.status());
        assertEquals("value", directResult.toolInvocations().get(0).arguments().get("key").getAsString());
    }

    @Test
    void testParseReturnsInvalidResponseWhenArgumentsAreMalformedJson() {
        String malformedStr = """
                {
                  "structured_output": {
                    "tool_calls": [
                      { "name": "speak", "arguments": "{not-valid-json}" }
                    ]
                  }
                }
                """;
        JsonObject response = JsonParser.parseString(malformedStr).getAsJsonObject();

        LlmResult result = adapter.parse(response);

        assertEquals(LlmResult.Status.INVALID_RESPONSE, result.status());
        assertTrue(result.toolInvocations().isEmpty());
    }

    @Test
    void testDroppedTextDifferentiationBetweenPlainTextAndFailedJson() {
        // Case 1: Model responded with pure plain text (e.g. Japanese sentence)
        String plainTextStr = """
                {
                  "status": "SUCCESS",
                  "response": "了解しました。現在地を確認します。"
                }
                """;
        JsonObject plainTextResponse = JsonParser.parseString(plainTextStr).getAsJsonObject();
        LlmResult plainTextResult = adapter.parse(plainTextResponse);

        assertEquals(LlmResult.Status.INVALID_RESPONSE, plainTextResult.status());
        assertEquals("了解しました。現在地を確認します。", plainTextResult.droppedText(),
                "Pure plain text must be preserved in droppedText for diagnostics");

        // Case 2: Model emitted a JSON structure that failed tool_call validation
        String failedJsonStr = """
                {
                  "status": "SUCCESS",
                  "response": "{\\"tool_calls\\":[]}"
                }
                """;
        JsonObject failedJsonResponse = JsonParser.parseString(failedJsonStr).getAsJsonObject();
        LlmResult failedJsonResult = adapter.parse(failedJsonResponse);

        assertEquals(LlmResult.Status.INVALID_RESPONSE, failedJsonResult.status());
        assertNull(failedJsonResult.droppedText(),
                "Failed JSON structure must NOT be treated as plain text in droppedText");
    }

    @Test
    void testParseTextExtractsStrippedTextFromResponseProperty() {
        JsonObject response = JsonParser.parseString("{\"response\":\"  This is a summary.  \"}").getAsJsonObject();
        assertEquals("This is a summary.", adapter.parseText(response));

        JsonObject emptyResponse = JsonParser.parseString("{\"response\":\"   \"}").getAsJsonObject();
        assertNull(adapter.parseText(emptyResponse));

        JsonObject nullResponse = JsonParser.parseString("{\"response\":null}").getAsJsonObject();
        assertNull(adapter.parseText(nullResponse));

        assertNull(adapter.parseText(null));
    }

    @Test
    void testParseRealWorldFixtureMultipleToolCalls() {
        String realWorldFixture = """
                {
                  "conversation_id": "c7a8b9c0-1234-5678-9abc-def012345678",
                  "status": "SUCCESS",
                  "structured_output": {
                    "tool_calls": [
                      {
                        "name": "find_commodity",
                        "arguments": "{\\"commodity\\":\\"Painite\\",\\"radius\\":50}"
                      },
                      {
                        "name": "speak",
                        "arguments": "{\\"text\\":\\"パイナイトを検索しています\\"}"
                      }
                    ]
                  },
                  "response": "something"
                }
                """;
        JsonObject response = JsonParser.parseString(realWorldFixture).getAsJsonObject();

        LlmResult result = adapter.parse(response);

        assertEquals(LlmResult.Status.OK, result.status());
        assertEquals(2, result.toolInvocations().size());

        LlmToolInvocation first = result.toolInvocations().get(0);
        assertEquals("find_commodity", first.name());
        assertEquals("Painite", first.arguments().get("commodity").getAsString());
        assertEquals(50, first.arguments().get("radius").getAsInt());

        LlmToolInvocation second = result.toolInvocations().get(1);
        assertEquals("speak", second.name());
        assertEquals("パイナイトを検索しています", second.arguments().get("text").getAsString());
        assertNull(result.droppedText());
    }

    @Test
    void testParseNonObjectStructuredOutputReturnsInvalidResponse() {
        // Case 1: structured_output is a string
        String stringStructured = "{\"status\":\"SUCCESS\",\"structured_output\":\"not an object\"}";
        JsonObject stringResponse = JsonParser.parseString(stringStructured).getAsJsonObject();
        assertEquals(LlmResult.Status.INVALID_RESPONSE, adapter.parse(stringResponse).status());

        // Case 2: structured_output is an array
        String arrayStructured = "{\"status\":\"SUCCESS\",\"structured_output\":[1, 2, 3]}";
        JsonObject arrayResponse = JsonParser.parseString(arrayStructured).getAsJsonObject();
        assertEquals(LlmResult.Status.INVALID_RESPONSE, adapter.parse(arrayResponse).status());
    }

    @Test
    void testParseToolCallWithMissingNameReturnsInvalidResponse() {
        // Case 1: missing name property
        String missingName = """
                {
                  "status": "SUCCESS",
                  "structured_output": {
                    "tool_calls": [
                      { "arguments": "{\\"key\\":\\"value\\"}" }
                    ]
                  }
                }
                """;
        JsonObject missingResponse = JsonParser.parseString(missingName).getAsJsonObject();
        assertEquals(LlmResult.Status.INVALID_RESPONSE, adapter.parse(missingResponse).status());

        // Case 2: empty name property
        String emptyName = """
                {
                  "status": "SUCCESS",
                  "structured_output": {
                    "tool_calls": [
                      { "name": "   ", "arguments": "{\\"key\\":\\"value\\"}" }
                    ]
                  }
                }
                """;
        JsonObject emptyResponse = JsonParser.parseString(emptyName).getAsJsonObject();
        assertEquals(LlmResult.Status.INVALID_RESPONSE, adapter.parse(emptyResponse).status());
    }

    @Test
    void testParseMalformedJsonInResponseStringFallbackReturnsInvalidResponse() {
        // response property has malformed JSON where tool_calls cannot be parsed
        String malformedResponseStr = """
                {
                  "status": "SUCCESS",
                  "response": "Here is the tool call: {\\"tool_calls\\": not valid json"
                }
                """;
        JsonObject response = JsonParser.parseString(malformedResponseStr).getAsJsonObject();
        LlmResult result = adapter.parse(response);
        assertEquals(LlmResult.Status.INVALID_RESPONSE, result.status());
    }

    @Test
    void testFullFlowFromTransportOutputToAdapterResult() {
        // Simulates the end-to-end json structure emitted by AgyCliTransport.sendOutcome()
        String transportSuccessJson = """
                {
                  "conversation_id": "conv-999",
                  "status": "SUCCESS",
                  "structured_output": {
                    "tool_calls": [
                      {
                        "name": "query_local_outfitting",
                        "arguments": "{}"
                      }
                    ]
                  },
                  "response": "{}"
                }
                """;
        JsonObject transportOutput = JsonParser.parseString(transportSuccessJson).getAsJsonObject();

        LlmResult adapterResult = adapter.parse(transportOutput);

        assertEquals(LlmResult.Status.OK, adapterResult.status());
        assertEquals(1, adapterResult.toolInvocations().size());
        assertEquals("query_local_outfitting", adapterResult.toolInvocations().get(0).name());
        assertEquals(0, adapterResult.toolInvocations().get(0).arguments().size());
    }
}
