package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.ai.brain.actions.ActionParameterSpec;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery;
import elite.intel.ai.brain.actions.handlers.queries.EngineerQuery.RankedModuleEngineer;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.db.managers.QueryResultDisplayManager.LatestDisplay;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.engineers.EngineerDirectory;
import elite.intel.gameapi.engineers.EngineerDirectory.EngineerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NavigateToEngineerCommandTest {

    private record ReminderRecord(String text, String starSystem, String stationName, ReminderContact contact) {
    }

    private record RoutePlotRecord(String answer, String destination) {
    }

    private final List<ReminderRecord> reminders = new ArrayList<>();
    private final List<RoutePlotRecord> plottedRoutes = new ArrayList<>();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final AtomicBoolean inMainShip = new AtomicBoolean(true);
    private final AtomicReference<String> currentStarSystem = new AtomicReference<>("Sol");
    private final AtomicReference<LocationDao.Coordinates> currentCoords =
            new AtomicReference<>(new LocationDao.Coordinates("Sol", 0.0, 0.0, 0.0));
    private final AtomicReference<Optional<LatestDisplay>> latestDisplay =
            new AtomicReference<>(Optional.empty());

    private final EngineerDirectory engineerDirectory = EngineerDirectory.getInstance();
    private NavigateToEngineerCommand command;

    @BeforeEach
    void setUp() {
        reminders.clear();
        plottedRoutes.clear();
        publishedEvents.clear();
        inMainShip.set(true);
        currentStarSystem.set("Sol");
        currentCoords.set(new LocationDao.Coordinates("Sol", 0.0, 0.0, 0.0));
        latestDisplay.set(Optional.empty());

        command = new NavigateToEngineerCommand(
                inMainShip::get,
                currentStarSystem::get,
                currentCoords::get,
                latestDisplay::get,
                engineerDirectory,
                (text, system, station, contact) -> reminders.add(new ReminderRecord(text, system, station, contact)),
                (answer, dest) -> {
                    plottedRoutes.add(new RoutePlotRecord(answer, dest));
                    return answer + " [PLOTTED]";
                },
                publishedEvents::add
        );
    }

    @Test
    void testParameters_DefinesNameRankModuleWithoutDefault() {
        List<ActionParameterSpec> specs = command.parameters();
        assertNotNull(specs);
        assertEquals(3, specs.size());

        ActionParameterSpec nameSpec = specs.stream().filter(s -> "name".equals(s.getName())).findFirst().orElseThrow();
        assertFalse(nameSpec.isRequired());

        ActionParameterSpec rankSpec = specs.stream().filter(s -> "rank".equals(s.getName())).findFirst().orElseThrow();
        assertFalse(rankSpec.isRequired());
        assertEquals("number", rankSpec.getType());
        assertFalse(rankSpec.getDescription().toLowerCase().contains("default"), "rank spec must not specify a default value");

        ActionParameterSpec moduleSpec = specs.stream().filter(s -> "module".equals(s.getName())).findFirst().orElseThrow();
        assertFalse(moduleSpec.isRequired());
    }

    @Test
    void testNotInMainShip_Refuses() {
        inMainShip.set(false);
        JsonObject params = new JsonObject();
        params.addProperty("name", "Felicity Farseer");

        String result = command.execute(params, "フェリシティ・ファーシーアのところへ向かって");
        assertNotNull(result);
        assertTrue(result.contains("本船に搭乗している必要") || result.contains("main ship"), "Must refuse when not in main ship: " + result);
        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when not in main ship");
        assertTrue(reminders.isEmpty(), "Must not set reminder when not in main ship");
    }

    @Test
    void testDifferentSystem_PlotsRouteAndSetsReminder() {
        currentStarSystem.set("Sol");
        JsonObject params = new JsonObject();
        params.addProperty("name", "Felicity Farseer");

        String result = command.execute(params, "フェリシティ・ファーシーアのステーションへ");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Deciat", plottedRoutes.get(0).destination());
        assertTrue(result.contains("Deciat"), "Result must contain system name: " + result);

        assertEquals(1, reminders.size());
        assertEquals("Deciat", reminders.get(0).starSystem());
        assertEquals("Farseer Inc", reminders.get(0).stationName());
        assertTrue(reminders.get(0).text().contains("Felicity Farseer"));
    }

    @Test
    void testSameSystem_NoRoutePlot_AnswersBaseAndBody_SetsReminder() {
        currentStarSystem.set("Deciat");
        JsonObject params = new JsonObject();
        params.addProperty("name", "Felicity Farseer");

        String result = command.execute(params, "フェリシティ・ファーシーアの基地へ");
        assertNotNull(result);
        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when in same system");
        assertTrue(result.contains("同じ星系") || result.contains("in this system"), "Result must mention same system: " + result);
        assertTrue(result.contains("Farseer Inc"), "Result must mention base: " + result);
        assertTrue(result.contains("Deciat 6 A") || result.contains("Deciat 6 a"), "Result must mention body: " + result);

        assertEquals(1, reminders.size());
        assertEquals("Deciat", reminders.get(0).starSystem());
        assertEquals("Farseer Inc", reminders.get(0).stationName());
    }

    @Test
    void testPermitRequired_AppendsPermitWarning() {
        currentStarSystem.set("Sol");
        JsonObject params = new JsonObject();
        params.addProperty("name", "Marco Qwent");

        String result = command.execute(params, "マルコ・クウェントのところへ向かって");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Sirius", plottedRoutes.get(0).destination());
        assertTrue(result.contains("許可証が必要") || result.contains("permit is required"), "Result must mention permit requirement: " + result);
    }

    @Test
    void testDestinationResolution_Order1_ParamsName() {
        JsonObject params = new JsonObject();
        params.addProperty("name", "Professor Palin");

        String result = command.execute(params, "エンジニアへ航路");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Arque", plottedRoutes.get(0).destination());
    }

    @Test
    void testDestinationResolution_Order1_MentionedInUtterance() {
        JsonObject params = new JsonObject();
        String result = command.execute(params, "フェリシティ・ファーシーアのところへ向かって");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Deciat", plottedRoutes.get(0).destination());
    }

    @Test
    void testDestinationResolution_Order2_ParamsRank_Success() {
        List<String> names = List.of("Felicity Farseer", "Professor Palin", "Elvira Martuuk");
        EngineersDisplayDto dto = new EngineersDisplayDto("module", "Frame Shift Drive", names);
        latestDisplay.set(Optional.of(new LatestDisplay("engineers", Instant.now(), null, null, dto)));

        JsonObject params = new JsonObject();
        params.addProperty("rank", 2);

        String result = command.execute(params, "2番目のエンジニアへ");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Arque", plottedRoutes.get(0).destination());
    }

    @Test
    void testDestinationResolution_Order2_ParamsRank_NoCards_Refuses() {
        latestDisplay.set(Optional.empty());

        JsonObject params = new JsonObject();
        params.addProperty("rank", 1);

        String result = command.execute(params, "1番目のエンジニアへ");
        assertNotNull(result);
        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when no cards displayed");
        assertTrue(result.contains("表示中") || result.contains("No engineer cards"), "Result: " + result);
    }

    @Test
    void testDestinationResolution_Order2_ParamsRank_OutOfRange_Refuses() {
        List<String> names = List.of("Felicity Farseer", "Professor Palin");
        EngineersDisplayDto dto = new EngineersDisplayDto("module", "Frame Shift Drive", names);
        latestDisplay.set(Optional.of(new LatestDisplay("engineers", Instant.now(), null, null, dto)));

        JsonObject params = new JsonObject();
        params.addProperty("rank", 5);

        String result = command.execute(params, "5番目のエンジニアへ");
        assertNotNull(result);
        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when rank is out of range");
        assertTrue(result.contains("5") && (result.contains("表示されていません") || result.contains("out of range")), "Result: " + result);
    }

    @Test
    void testDestinationResolution_Order3_MentionedModule_ChoosesTopEngineer() {
        JsonObject params = new JsonObject();
        String result = command.execute(params, "FSD のエンジニアへ向かって");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        // FSD G5 engineer top is Felicity Farseer or Elvira Martuuk depending on distance
        assertTrue("Deciat".equals(plottedRoutes.get(0).destination()) || "Khun".equals(plottedRoutes.get(0).destination()));
    }

    @Test
    void testDestinationResolution_Order4_SingleDisplayedEngineer() {
        List<String> names = List.of("Professor Palin");
        EngineersDisplayDto dto = new EngineersDisplayDto("engineer", null, names);
        latestDisplay.set(Optional.of(new LatestDisplay("engineers", Instant.now(), null, null, dto)));

        JsonObject params = new JsonObject();
        String result = command.execute(params, "エンジニアのところへ向かって");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Arque", plottedRoutes.get(0).destination());
    }

    @Test
    void testDestinationResolution_Order5_Ambiguous_CannotResolve() {
        latestDisplay.set(Optional.empty());

        JsonObject params = new JsonObject();
        String result = command.execute(params, "エンジニアへ航路");
        assertNotNull(result);
        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when ambiguous");
        assertTrue(reminders.isEmpty(), "Must not set reminder when ambiguous");
        assertTrue(result.contains("特定できませんでした") || result.contains("Could not identify"), "Result: " + result);
    }

    @Test
    void testRankAbsent_MultipleCardsDisplayed_NoNameNoModule_Refuses() {
        // Requirement from user feedback: rank is absent, 3 cards displayed, utterance has neither name nor module
        // -> must answer "cannot identify which engineer" and set no route or reminder.
        List<String> names = List.of("Felicity Farseer", "Professor Palin", "Elvira Martuuk");
        EngineersDisplayDto dto = new EngineersDisplayDto("module", "Frame Shift Drive", names);
        latestDisplay.set(Optional.of(new LatestDisplay("engineers", Instant.now(), null, null, dto)));

        JsonObject params = new JsonObject();
        String result = command.execute(params, "エンジニアのステーションへ向かって");
        assertNotNull(result);
        assertTrue(plottedRoutes.isEmpty(), "Must not plot route when rank is absent and multiple cards displayed without name/module");
        assertTrue(reminders.isEmpty(), "Must not set reminder when rank is absent and multiple cards displayed without name/module");
        assertTrue(result.contains("特定できませんでした") || result.contains("Could not identify"), "Result: " + result);
    }

    @Test
    void testRankAbsent_MultipleCardsDisplayed_ModulePresent_ChoosesEG2FirstNotCardFirst() {
        // Requirement from user feedback: rank is absent, utterance has FSD -> not card #1, but EG-2 module #1
        // To verify this: set displayed cards to something NOT #1 in FSD (e.g. Liz Ryder, Hera Tani, Todd)
        List<String> nonFsdNames = List.of("Liz Ryder", "Hera Tani", "Tod 'The Blaster' McQuinn");
        EngineersDisplayDto dto = new EngineersDisplayDto("progress", null, nonFsdNames);
        latestDisplay.set(Optional.of(new LatestDisplay("engineers", Instant.now(), null, null, dto)));

        // Expectation: EG-2 FSD top engineer (Felicity Farseer / Elvira Martuuk)
        List<RankedModuleEngineer> expectedFsd = EngineerQuery.findEngineersForModule("Frame Shift Drive", currentCoords.get(), engineerDirectory);
        assertFalse(expectedFsd.isEmpty());
        String expectedFsdTop = expectedFsd.get(0).engineer().name();
        String expectedFsdSystem = expectedFsd.get(0).engineer().system();

        JsonObject params = new JsonObject();
        String result = command.execute(params, "FSD のエンジニアへ航路");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals(expectedFsdSystem, plottedRoutes.get(0).destination(), "Must choose EG-2 order #1, not displayed card #1");
        assertTrue(result.contains(expectedFsdTop));
    }

    @Test
    void testOnFootEngineer_Supported() {
        // Domino Green is an on-foot engineer in Orishis
        currentStarSystem.set("Sol");
        JsonObject params = new JsonObject();
        params.addProperty("name", "Domino Green");

        String result = command.execute(params, "ドミノ・グリーンのところへ向かって");
        assertNotNull(result);
        assertEquals(1, plottedRoutes.size());
        assertEquals("Orishis", plottedRoutes.get(0).destination());
        assertEquals(1, reminders.size());
        assertEquals("Orishis", reminders.get(0).starSystem());
        assertEquals("The Jackrabbit", reminders.get(0).stationName());
    }

    @Test
    void testGuiSource_PublishesAiVoxAndReturnsNull() {
        JsonObject params = new JsonObject();
        params.addProperty("name", "Felicity Farseer");
        params.addProperty("source", "gui");

        String result = command.execute(params, null);
        assertNull(result, "When source=gui, execute() must return null");
        assertEquals(1, plottedRoutes.size());
        assertEquals("Deciat", plottedRoutes.get(0).destination());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof AiVoxResponseEvent);
        AiVoxResponseEvent event = (AiVoxResponseEvent) publishedEvents.get(0);
        assertTrue(event.getText().contains("Deciat"));
    }
}
