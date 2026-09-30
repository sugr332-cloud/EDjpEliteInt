package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.ai.mouth.subscribers.events.AiVoxResponseEvent;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.gameapi.ReminderContact;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import elite.intel.util.StringUtls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NavigateToPreviousStationCommandTest {

    private record ReminderRecord(String text, String starSystem, String stationName, ReminderContact contact) {
    }

    private record RoutePlotRecord(String answer, String destination) {
    }

    private final List<ReminderRecord> reminders = new ArrayList<>();
    private final List<RoutePlotRecord> plottedRoutes = new ArrayList<>();
    private final List<Object> publishedEvents = new ArrayList<>();

    private final List<DockingHistoryEntry> historyEntries = new ArrayList<>();
    private final AtomicBoolean inMainShip = new AtomicBoolean(true);
    private final AtomicBoolean isDocked = new AtomicBoolean(false);
    private final AtomicLong dockedMarketId = new AtomicLong(0);
    private final AtomicReference<Long> currentSystemAddress = new AtomicReference<>(null);
    private final AtomicReference<String> currentStarSystem = new AtomicReference<>(null);
    private final AtomicReference<String> ownCarrierCallSign = new AtomicReference<>(null);
    private final AtomicReference<String> currentFleetCarrierSystem = new AtomicReference<>(null);

    private NavigateToPreviousStationCommand command;
    private Language originalLanguage;

    @BeforeEach
    void setUp() {
        originalLanguage = SystemSession.getInstance().getLanguage();
        SystemSession.getInstance().setLanguage(Language.JA);

        reminders.clear();
        plottedRoutes.clear();
        publishedEvents.clear();
        historyEntries.clear();

        inMainShip.set(true);
        isDocked.set(false);
        dockedMarketId.set(0);
        currentSystemAddress.set(null);
        currentStarSystem.set(null);
        ownCarrierCallSign.set(null);
        currentFleetCarrierSystem.set(null);

        command = new NavigateToPreviousStationCommand(
                (back, docked, marketId) -> {
                    if (back < 1) return Optional.empty();
                    int targetIndex;
                    if (docked) {
                        if (marketId > 0) {
                            if (!historyEntries.isEmpty() && historyEntries.get(0).marketId() != marketId) {
                                targetIndex = back - 1;
                            } else {
                                targetIndex = back;
                            }
                        } else {
                            targetIndex = back;
                        }
                    } else {
                        targetIndex = back - 1;
                    }
                    if (targetIndex < 0 || targetIndex >= historyEntries.size()) {
                        return Optional.empty();
                    }
                    return Optional.of(historyEntries.get(targetIndex));
                },
                inMainShip::get,
                isDocked::get,
                dockedMarketId::get,
                currentSystemAddress::get,
                currentStarSystem::get,
                ownCarrierCallSign::get,
                currentFleetCarrierSystem::get,
                (text, system, station, contact) -> reminders.add(new ReminderRecord(text, system, station, contact)),
                (answer, dest) -> {
                    plottedRoutes.add(new RoutePlotRecord(answer, dest));
                    return answer + " [PLOTTED]";
                },
                publishedEvents::add
        );
    }

    @AfterEach
    void tearDown() {
        SystemSession.getInstance().setLanguage(originalLanguage);
    }

    private static DockingHistoryEntry createEntry(long id, String stationName, String starSystem, long systemAddress,
                                                   long marketId, String stationType) {
        return new DockingHistoryEntry(id, stationName, starSystem, systemAddress, marketId, stationType, 100.0, "2026-09-28T10:00:00Z");
    }

    @Test
    void testGate1_differentSystem_plotsRouteAndSetsReminder() {
        // 0: StationB in SysB, 1: StationA in SysA
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());

        assertEquals(1, reminders.size());
        assertEquals("SysA", reminders.get(0).starSystem());
        assertEquals("StationA", reminders.get(0).stationName());
        assertEquals("前のステーション: StationA（SysA 星系）", reminders.get(0).text());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.success", "StationA", "SysA");
        assertEquals(expectedMessage + " [PLOTTED]", result);
    }

    @Test
    void testGate2_sameSystem_systemAddressMatch_noRoutePlotReminderRegistered() {
        // 0: StationB in SysA (1000L), 1: StationA in SysA (1000L)
        historyEntries.add(createEntry(2, "StationB", "SysA", 1000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        currentSystemAddress.set(1000L);
        currentStarSystem.set("SysA");

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertTrue(plottedRoutes.isEmpty(), "Route should NOT be plotted for same system");

        assertEquals(1, reminders.size());
        assertEquals("SysA", reminders.get(0).starSystem());
        assertEquals("StationA", reminders.get(0).stationName());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.sameSystem", "StationA");
        assertEquals(expectedMessage, result);
    }

    @Test
    void testGate2b_sameSystem_systemNameFallbackMatch() {
        // systemAddress is 0 or unavailable, but system names match case-insensitively
        historyEntries.add(createEntry(2, "StationB", "SysA", 0L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 0L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        currentSystemAddress.set(null);
        currentStarSystem.set("sysa");

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertTrue(plottedRoutes.isEmpty(), "Route should NOT be plotted for same system");
        assertEquals(1, reminders.size());
        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.sameSystem", "StationA");
        assertEquals(expectedMessage, result);
    }

    @Test
    void testGate3_insufficientHistory() {
        // Empty history
        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.insufficientHistory"), result);

        // History with 1 entry, but back=2 requested
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));
        isDocked.set(true);
        dockedMarketId.set(10L);
        params.addProperty("back", 2);
        result = command.execute(params, "");

        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.insufficientHistory"), result);
    }


    @Test
    void testGate4_notInMainShip() {
        inMainShip.set(false);

        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.notInMainShip"), result);
    }

    @Test
    void testGate5_backEqualsTwo_targetsSecondPreviousStation() {
        // 0: StationC (marketId=30), 1: StationB (marketId=20), 2: StationA (marketId=10)
        historyEntries.add(createEntry(3, "StationC", "SysC", 3000L, 30L, "Coriolis"));
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(30L);
        currentSystemAddress.set(3000L);
        currentStarSystem.set("SysC");

        JsonObject params = new JsonObject();
        params.addProperty("back", 2);
        String result = command.execute(params, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());
        assertEquals("StationA", reminders.get(0).stationName());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.success", "StationA", "SysA");
        assertEquals(expectedMessage + " [PLOTTED]", result);
    }

    @Test
    void testGate6_invalidBack_zeroNegativeDecimal() {
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        // back = 0
        JsonObject params0 = new JsonObject();
        params0.addProperty("back", 0);
        String res0 = command.execute(params0, "");
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.invalidBack", "0"), res0);

        // back = -1
        JsonObject paramsNeg = new JsonObject();
        paramsNeg.addProperty("back", -1);
        String resNeg = command.execute(paramsNeg, "");
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.invalidBack", "-1"), resNeg);

        // back = 1.5
        JsonObject paramsDec = new JsonObject();
        paramsDec.addProperty("back", 1.5);
        String resDec = command.execute(paramsDec, "");
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.invalidBack", "1.5"), resDec);

        // back = non-number string
        JsonObject paramsStr = new JsonObject();
        paramsStr.addProperty("back", "abc");
        String resStr = command.execute(paramsStr, "");
        assertEquals(StringUtls.localizedResponse("handler.navigateToPreviousStation.invalidBack", "abc"), resStr);

        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }

    @Test
    void testGate7_dockedAndLatestHistoryMismatch_backOneTargetsZero() {
        // History only has StationA (marketId=10L)
        // Player is currently docked at StationB (marketId=20L, unrecorded)
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(20L); // different from 10L
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        params.addProperty("back", 1);
        String result = command.execute(params, "");

        // Exception rule: targetIndex = back - 1 = 0 (StationA)
        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());
        assertEquals("StationA", reminders.get(0).stationName());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.success", "StationA", "SysA");
        assertEquals(expectedMessage + " [PLOTTED]", result);
    }

    @Test
    void testGate8_dockedAndLatestHistoryMatches_backOneTargetsOne() {
        // History has 0: StationB (marketId=20L), 1: StationA (marketId=10L)
        // Player is docked at StationB (marketId=20L)
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(20L); // matches history 0
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        params.addProperty("back", 1);
        String result = command.execute(params, "");

        // Standard: targetIndex = back = 1 (StationA)
        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());
        assertEquals("StationA", reminders.get(0).stationName());
    }

    @Test
    void testGate9_undocked_backOneTargetsZero_backTwoTargetsOne() {
        // Undocked (isDocked = false)
        // History has 0: StationB (marketId=20L), 1: StationA (marketId=10L)
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(false);
        dockedMarketId.set(0L);
        currentSystemAddress.set(3000L);
        currentStarSystem.set("SysC");

        // back = 1 -> targets 0th entry (StationB, last visited station)
        JsonObject params1 = new JsonObject();
        params1.addProperty("back", 1);
        String result1 = command.execute(params1, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysB", plottedRoutes.get(0).destination());
        assertEquals("StationB", reminders.get(0).stationName());

        plottedRoutes.clear();
        reminders.clear();

        // back = 2 -> targets 1st entry (StationA, station before last visited station)
        JsonObject params2 = new JsonObject();
        params2.addProperty("back", 2);
        String result2 = command.execute(params2, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());
        assertEquals("StationA", reminders.get(0).stationName());
    }

    @Test
    void testGate10_otherCarrier_plotsRouteToRecordedSystemWithCarrierMessage() {
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "Carrier-XYZ", "SysA", 1000L, 10L, "FleetCarrier"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        ownCarrierCallSign.set("MY-CARRIER");
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.carrier", "Carrier-XYZ", "SysA");
        assertEquals(expectedMessage + " [PLOTTED]", result);
    }

    @Test
    void testGate11_ownCarrierMoved_plotsRouteToCurrentSystemWithOwnCarrierMovedMessage() {
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        // Carrier recorded at SysA
        historyEntries.add(createEntry(1, "Q7B-89X", "SysA", 1000L, 10L, "FleetCarrier"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        ownCarrierCallSign.set("Q7B-89X");
        // But carrier has moved to SysNew!
        currentFleetCarrierSystem.set("SysNew");
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysNew", plottedRoutes.get(0).destination());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.ownCarrierMoved", "Q7B-89X", "SysNew");
        assertEquals(expectedMessage + " [PLOTTED]", result);

        assertEquals(1, reminders.size());
        assertEquals("SysNew", reminders.get(0).starSystem());
        assertEquals("Q7B-89X", reminders.get(0).stationName());
    }

    @Test
    void testGate11b_ownCarrierUnknownLocation_fallbackToRecordedSystem() {
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "Q7B-89X", "SysA", 1000L, 10L, "FleetCarrier"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        ownCarrierCallSign.set("Q7B-89X");
        currentFleetCarrierSystem.set(null); // Unknown
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        String result = command.execute(params, "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("SysA", plottedRoutes.get(0).destination());

        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.carrier", "Q7B-89X", "SysA");
        assertEquals(expectedMessage + " [PLOTTED]", result);
    }

    @Test
    void testGate12_guiSource_publishesAiVoxResponseEventAndReturnsNull() {
        historyEntries.add(createEntry(2, "StationB", "SysB", 2000L, 20L, "Coriolis"));
        historyEntries.add(createEntry(1, "StationA", "SysA", 1000L, 10L, "Coriolis"));

        isDocked.set(true);
        dockedMarketId.set(20L);
        currentSystemAddress.set(2000L);
        currentStarSystem.set("SysB");

        JsonObject params = new JsonObject();
        params.addProperty("source", "gui");
        String result = command.execute(params, "");

        assertNull(result, "Command executed from GUI should return null");
        assertEquals(1, publishedEvents.size());
        assertInstanceOf(AiVoxResponseEvent.class, publishedEvents.get(0));

        AiVoxResponseEvent vox = (AiVoxResponseEvent) publishedEvents.get(0);
        String expectedMessage = StringUtls.localizedResponse("handler.navigateToPreviousStation.success", "StationA", "SysA");
        assertEquals(expectedMessage + " [PLOTTED]", vox.getText());
    }


    @Test
    void testCommandMetadata() {
        assertEquals("navigate_to_previous_station", command.id());
        assertNotNull(command.llmDescription());
        assertTrue(command.isVisibleForLLM(null));
        assertTrue(command.isAvailableIn(null));
        assertEquals(1, command.parameters().size());
        assertEquals("back", command.parameters().get(0).getName());
    }
}
