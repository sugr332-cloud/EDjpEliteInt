package elite.intel.ai.brain.vega.diag;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import elite.intel.ai.brain.vega.model.llm.LlmToolDefinition;
import elite.intel.ai.brain.vega.model.llm.LlmToolInvocation;
import elite.intel.ai.brain.vega.prompt.Fact;
import elite.intel.eventbus.UiBus;
import elite.intel.ui.event.AppLogDebugEvent;
import elite.intel.ui.event.AppLogEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pure rendering helpers of {@link VegaDiagnostics} and its dual output to
 * {@link UiBus} and the dedicated Log4j2 logger (D-1).
 */
class VegaDiagnosticsTest {

    private static final int MAX_TEXT = 400; // mirrors VegaDiagnostics.MAX_TEXT
    private static final int MAX_ARGS = 300; // mirrors VegaDiagnostics.MAX_ARGS

    private static LlmToolDefinition tool(String name) {
        return new LlmToolDefinition(name, "", "", List.of());
    }

    private static JsonObject argsOf(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    @Test
    void namesEmptyGivesEmptyBrackets() {
        assertEquals("[]", VegaDiagnostics.names(List.of()));
    }

    @Test
    void namesJoinsToolNames() {
        assertEquals("[find_action, set_reminder]",
                VegaDiagnostics.names(List.of(tool("find_action"), tool("set_reminder"))));
    }

    @Test
    void callsEmptyGivesNone() {
        assertEquals("none", VegaDiagnostics.calls(List.of()));
    }

    @Test
    void callsRendersNameThenArgs() {
        LlmToolInvocation speak = new LlmToolInvocation("id", "speak", argsOf("text", "hi"));
        assertEquals("speak{\"text\":\"hi\"}", VegaDiagnostics.calls(List.of(speak)));
    }

    @Test
    void argsEmptyOrNullGivesBraces() {
        assertEquals("{}", VegaDiagnostics.args(null));
        assertEquals("{}", VegaDiagnostics.args(new JsonObject()));
    }

    @Test
    void argsRendersCompactJson() {
        assertEquals("{\"text\":\"hi\"}", VegaDiagnostics.args(argsOf("text", "hi")));
    }

    @Test
    void argsElidesJsonPastLimit() {
        String longValue = "x".repeat(400);
        String rendered = VegaDiagnostics.args(argsOf("k", longValue));
        assertEquals(MAX_ARGS, rendered.length());
        assertTrue(rendered.endsWith("…"));
    }

    @Test
    void factRendersProvenanceTaggedText() {
        assertEquals("[system] current system Sol",
                VegaDiagnostics.fact(new Fact("current system Sol", "system")));
    }

    @Test
    void truncateNullGivesEmpty() {
        assertEquals("", VegaDiagnostics.truncate(null));
    }

    @Test
    void truncateShortTextUnchanged() {
        assertEquals("all stop", VegaDiagnostics.truncate("all stop"));
    }

    @Test
    void truncateFlattensNewlines() {
        assertEquals("line1 line2", VegaDiagnostics.truncate("line1\nline2"));
    }

    @Test
    void truncateKeepsTextAtLimit() {
        String atLimit = "a".repeat(MAX_TEXT);
        assertEquals(atLimit, VegaDiagnostics.truncate(atLimit));
    }

    @Test
    void truncateElidesTextPastLimit() {
        String rendered = VegaDiagnostics.truncate("a".repeat(MAX_TEXT + 40));
        assertEquals(MAX_TEXT, rendered.length());
        assertTrue(rendered.endsWith("…"));
    }

    private final TestUiBusListener uiListener = new TestUiBusListener();
    private final List<LogEvent> capturedLogEvents = new ArrayList<>();
    private AbstractAppender testAppender;
    private org.apache.logging.log4j.core.Logger coreLogger;

    @BeforeEach
    void setUpLogging() {
        UiBus.register(uiListener);
        coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(VegaDiagnostics.LOGGER_NAME);
        testAppender = new AbstractAppender("TestDiagAppender", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                capturedLogEvents.add(event.toImmutable());
            }
        };
        testAppender.start();
        coreLogger.addAppender(testAppender);
    }

    @AfterEach
    void tearDownLogging() {
        UiBus.unregister(uiListener);
        if (coreLogger != null && testAppender != null) {
            coreLogger.removeAppender(testAppender);
            testAppender.stop();
        }
        VegaDiagnostics.exitThought();
    }

    @Test
    void infoPublishesToUiBusAndLogsAtInfoLevel() {
        VegaDiagnostics.info("c1", "intake", "deploy landing gear");

        String expected = "VEGA c1 intake: deploy landing gear";
        assertEquals(1, uiListener.appLogEvents.size());
        assertEquals(expected, uiListener.appLogEvents.get(0));

        assertEquals(1, capturedLogEvents.size());
        assertEquals(Level.INFO, capturedLogEvents.get(0).getLevel());
        assertEquals(expected, capturedLogEvents.get(0).getMessage().getFormattedMessage());
    }

    @Test
    void debugPublishesToUiBusAndLogsAtDebugLevel() {
        VegaDiagnostics.debug("c2", "exec", "deploy_landing_gear{}");

        String expected = "VEGA c2 exec: deploy_landing_gear{}";
        int totalUiEvents = uiListener.appLogEvents.size() + uiListener.appLogDebugEvents.size();
        assertEquals(1, totalUiEvents);
        String uiEventData = !uiListener.appLogEvents.isEmpty()
                ? uiListener.appLogEvents.get(0)
                : uiListener.appLogDebugEvents.get(0);
        assertEquals(expected, uiEventData);

        assertEquals(1, capturedLogEvents.size());
        assertEquals(Level.DEBUG, capturedLogEvents.get(0).getLevel());
        assertEquals(expected, capturedLogEvents.get(0).getMessage().getFormattedMessage());
    }

    @Test
    void debugAmbientUsesBoundTraceAndLogsAtDebugLevel() {
        VegaDiagnostics.enterThought("trace-99");
        VegaDiagnostics.debugAmbient("reduce", "shortlist 2");

        String expected = "VEGA trace-99 reduce: shortlist 2";
        int totalUiEvents = uiListener.appLogEvents.size() + uiListener.appLogDebugEvents.size();
        assertEquals(1, totalUiEvents);

        assertEquals(1, capturedLogEvents.size());
        assertEquals(Level.DEBUG, capturedLogEvents.get(0).getLevel());
        assertEquals(expected, capturedLogEvents.get(0).getMessage().getFormattedMessage());
    }

    private static class TestUiBusListener {
        final List<String> appLogEvents = new ArrayList<>();
        final List<String> appLogDebugEvents = new ArrayList<>();

        @Subscribe
        public void onAppLog(AppLogEvent e) {
            appLogEvents.add(e.getData());
        }

        @Subscribe
        public void onAppLogDebug(AppLogDebugEvent e) {
            appLogDebugEvents.add(e.getData());
        }
    }
}
