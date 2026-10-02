package elite.intel.ai.brain.actions.handlers.commands.builtin;

import com.google.gson.JsonObject;
import elite.intel.db.dao.LocationDao;
import elite.intel.gameapi.ReminderContact;
import elite.intel.gameapi.search.spansh.station.refuel.OrbitalStationSearch;
import elite.intel.gameapi.search.spansh.station.refuel.OrbitalStationSearch.OrbitalStation;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import elite.intel.util.StringUtls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NavigateToNearestOrbitalStationCommandTest {

    private record ReminderRecord(String text, String starSystem, String stationName, ReminderContact contact) {
    }

    private record RoutePlotRecord(String answer, String destination) {
    }

    private record SearchCall(int radius, String padSize, long excludeMarketId) {
    }

    private final List<ReminderRecord> reminders = new ArrayList<>();
    private final List<RoutePlotRecord> plottedRoutes = new ArrayList<>();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final List<SearchCall> searchCalls = new ArrayList<>();

    private final AtomicBoolean inMainShip = new AtomicBoolean(true);
    private final AtomicBoolean isDocked = new AtomicBoolean(false);
    private final AtomicLong dockedMarketId = new AtomicLong(0);
    private final AtomicReference<String> currentStarSystem = new AtomicReference<>("Sol");
    private final AtomicReference<LocationDao.Coordinates> coordinates =
            new AtomicReference<>(new LocationDao.Coordinates("Sol", 1.0, 2.0, 3.0));
    private final AtomicReference<String> padSize = new AtomicReference<>("M");
    private final AtomicInteger jumpRange = new AtomicInteger(30);
    private final AtomicReference<OrbitalStationSearch.Result> searchResult = new AtomicReference<>();

    private NavigateToNearestOrbitalStationCommand command;
    private Language originalLanguage;

    @BeforeEach
    void setUp() {
        originalLanguage = SystemSession.getInstance().getLanguage();
        SystemSession.getInstance().setLanguage(Language.JA);

        reminders.clear();
        plottedRoutes.clear();
        publishedEvents.clear();
        searchCalls.clear();
        inMainShip.set(true);
        isDocked.set(false);
        dockedMarketId.set(0);
        currentStarSystem.set("Sol");
        coordinates.set(new LocationDao.Coordinates("Sol", 1.0, 2.0, 3.0));
        padSize.set("M");
        jumpRange.set(30);
        searchResult.set(null);

        command = new NavigateToNearestOrbitalStationCommand(
                (x, y, z, radius, pad, exclude) -> {
                    searchCalls.add(new SearchCall(radius, pad, exclude));
                    return searchResult.get();
                },
                inMainShip::get,
                coordinates::get,
                currentStarSystem::get,
                padSize::get,
                jumpRange::get,
                isDocked::get,
                dockedMarketId::get,
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

    private static OrbitalStation station(String system, String name, double ly, double ls, String marketId) {
        return new OrbitalStation(system, name, "Coriolis Starport", ly, ls, marketId);
    }

    private static OrbitalStationSearch.Result found(OrbitalStation... stations) {
        return new OrbitalStationSearch.Result(List.of(stations), false);
    }

    @Test
    void stationInThisSystem_noRouteButReminderIsKept() {
        searchResult.set(found(station("Sol", "Daedalus", 0.0, 321.4, "1")));

        String result = command.execute(new JsonObject(), "");

        assertTrue(plottedRoutes.isEmpty());
        assertEquals(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.here", "Daedalus", 321L),
                result);
        assertEquals(1, reminders.size());
        assertEquals("Sol", reminders.get(0).starSystem());
        assertEquals("Daedalus", reminders.get(0).stationName());
        assertNull(reminders.get(0).contact());
    }

    @Test
    void stationInAnotherSystem_plotsRouteAndSetsReminder() {
        searchResult.set(found(station("Alpha Centauri", "Hutton Orbital", 4.4, 2000.0, "2")));

        String result = command.execute(new JsonObject(), "");

        assertEquals(1, plottedRoutes.size());
        assertEquals("Alpha Centauri", plottedRoutes.get(0).destination());
        String expected = StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.plotted",
                "Hutton Orbital", "Alpha Centauri", 4L);
        assertEquals(expected + " [PLOTTED]", result);

        assertEquals(1, reminders.size());
        assertEquals("Alpha Centauri", reminders.get(0).starSystem());
        assertEquals("Hutton Orbital", reminders.get(0).stationName());
        assertEquals("最寄りの宇宙ステーション: Hutton Orbital（Alpha Centauri 星系）", reminders.get(0).text());
    }

    @Test
    void searchUsesJumpRangeAndShipPadSize() {
        searchResult.set(found(station("Alpha Centauri", "Hutton Orbital", 4.4, 2000.0, "2")));
        jumpRange.set(37);
        padSize.set("L");

        command.execute(new JsonObject(), "");

        assertEquals(List.of(new SearchCall(37, "L", 0L)), searchCalls);
    }

    @Test
    void unknownJumpRangeFallsBackToFiftyLightYears() {
        searchResult.set(found(station("Alpha Centauri", "Hutton Orbital", 4.4, 2000.0, "2")));
        jumpRange.set(0);

        command.execute(new JsonObject(), "");

        assertEquals(50, searchCalls.get(0).radius());
    }

    @Test
    void dockedAtAStation_itsMarketIdIsHandedToTheSearchSoItIsNotChosen() {
        isDocked.set(true);
        dockedMarketId.set(111L);
        searchResult.set(found(station("Sol", "Haberlandt Survey", 0.0, 2623.0, "222")));

        command.execute(new JsonObject(), "");

        assertEquals(111L, searchCalls.get(0).excludeMarketId());
    }

    @Test
    void notDocked_nothingIsExcluded() {
        isDocked.set(false);
        dockedMarketId.set(111L);
        searchResult.set(found(station("Sol", "Daedalus", 0.0, 100.0, "1")));

        command.execute(new JsonObject(), "");

        assertEquals(0L, searchCalls.get(0).excludeMarketId());
    }

    @Test
    void nothingFoundEvenWhenWidened_refusesAndSaysHowFarItLooked() {
        searchResult.set(new OrbitalStationSearch.Result(List.of(), false));
        jumpRange.set(40);

        String result = command.execute(new JsonObject(), "");

        assertEquals(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.notFound", 1000), result);
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }

    @Test
    void searchThatGotNoAnswer_saysSoInsteadOfClaimingThereIsNone() {
        searchResult.set(new OrbitalStationSearch.Result(List.of(), true));

        String result = command.execute(new JsonObject(), "");

        assertEquals(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.searchFailed"), result);
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }

    @Test
    void nullSearchResult_isTreatedAsAFailedSearch() {
        searchResult.set(null);

        String result = command.execute(new JsonObject(), "");

        assertEquals(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.searchFailed"), result);
    }

    @Test
    void notInMainShip_refusesWithoutSearching() {
        inMainShip.set(false);
        searchResult.set(found(station("Alpha Centauri", "Hutton Orbital", 4.4, 2000.0, "2")));

        String result = command.execute(new JsonObject(), "");

        assertEquals(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.notInMainShip"), result);
        assertTrue(searchCalls.isEmpty());
        assertTrue(plottedRoutes.isEmpty());
        assertTrue(reminders.isEmpty());
    }

    @Test
    void noCoordinates_refusesWithoutSearching() {
        coordinates.set(new LocationDao.Coordinates("Unknown", 0.0, 0.0, 0.0));
        currentStarSystem.set("Barnard's Star");

        String result = command.execute(new JsonObject(), "");

        assertEquals(StringUtls.localizedResponse("handler.navigateToNearestOrbitalStation.noCoords"), result);
        assertTrue(searchCalls.isEmpty());
    }

    @Test
    void solAtTheOriginIsAKnownPosition() {
        coordinates.set(new LocationDao.Coordinates("Sol", 0.0, 0.0, 0.0));
        currentStarSystem.set("Sol");
        searchResult.set(found(station("Sol", "Daedalus", 0.0, 100.0, "1")));

        command.execute(new JsonObject(), "");

        assertEquals(1, searchCalls.size());
    }

    @Test
    void guiSource_speaksTheAnswerAndReturnsNull() {
        searchResult.set(found(station("Sol", "Daedalus", 0.0, 321.0, "1")));
        JsonObject params = new JsonObject();
        params.addProperty("source", "gui");

        assertNull(command.execute(params, ""));
        assertEquals(1, publishedEvents.size());
    }

    @Test
    void idAndSendsGameInputDefault() {
        assertEquals("navigate_to_nearest_orbital_station", command.id());
        assertTrue(command.sendsGameInput());
        assertTrue(command.parameters().isEmpty());
    }
}
