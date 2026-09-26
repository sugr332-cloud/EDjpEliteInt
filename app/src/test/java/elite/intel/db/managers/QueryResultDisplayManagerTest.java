package elite.intel.db.managers;

import com.google.common.eventbus.Subscribe;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.MatchedModuleDto;
import elite.intel.ai.brain.actions.handlers.queries.NearestOutfittingQuery.OutfittingDataDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidateDto;
import elite.intel.ai.brain.actions.handlers.queries.TradeCandidatesQuery.TradeCandidatesDataDto;
import elite.intel.db.dao.QueryResultDisplayDao;
import elite.intel.db.util.Database;
import elite.intel.eventbus.UiBus;
import elite.intel.ui.event.QueryResultDisplayUpdatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QueryResultDisplayManagerTest {

    private final QueryResultDisplayManager manager = QueryResultDisplayManager.getInstance();
    private final EventCaptor eventCaptor = new EventCaptor();

    private static class EventCaptor {
        final List<QueryResultDisplayUpdatedEvent> events = new ArrayList<>();

        @Subscribe
        public void onEvent(QueryResultDisplayUpdatedEvent event) {
            events.add(event);
        }
    }

    @BeforeEach
    void setUp() {
        manager.clearAll();
        eventCaptor.events.clear();
        UiBus.register(eventCaptor);
    }

    @AfterEach
    void tearDown() {
        UiBus.unregister(eventCaptor);
        manager.clearAll();
    }

    @Test
    void tradeCandidatesDataDtoRoundTripWithNullsMatchesAllFields() {
        // TradeCandidateDto with all fields set including nulls where allowable
        TradeCandidateDto candidate1 = new TradeCandidateDto(
                1,
                "Gold",
                "Sol",
                "Galileo",
                50000,
                "50,000",
                12000L,
                "12,000",
                500.2,
                "500",
                "2026-09-26 10:00:00+00",
                "Alpha Centauri",
                "Columbus",
                60000,
                "60,000",
                5000L,
                "5,000",
                1200.0,
                "1,200",
                "2026-09-26 11:00:00+00",
                10000,
                "10,000",
                700,
                "700",
                7000000L,
                "7,000,000",
                4.37,
                "4.37",
                4.37,
                "4.37"
        );

        // candidate2 with null optional fields
        TradeCandidateDto candidate2 = new TradeCandidateDto(
                2,
                "Silver",
                "Sol",
                "Daedalus",
                0,
                null,
                0L,
                null,
                null,
                null,
                null,
                "Barnard's Star",
                "Boston Base",
                0,
                null,
                0L,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                0L,
                null,
                null,
                null,
                null,
                null
        );

        TradeCandidatesDataDto dto = new TradeCandidatesDataDto(
                "ok",
                "Sol",
                null, // searchedFromSystem null
                null, // referenceSource null
                null, // freshStationCount null
                "profit",
                30,
                "30",
                List.of(candidate1, candidate2)
        );

        manager.saveTradeCandidates(dto);

        Optional<QueryResultDisplayManager.DisplayRecord<TradeCandidatesDataDto>> record = manager.getTradeCandidates();
        assertTrue(record.isPresent(), "Record should be present after save");
        assertEquals(QueryResultDisplayManager.TYPE_TRADE_CANDIDATES, record.get().queryType());
        assertNotNull(record.get().savedAt());

        TradeCandidatesDataDto read = record.get().data();
        assertEquals(dto.status(), read.status());
        assertEquals(dto.currentSystem(), read.currentSystem());
        assertNull(read.searchedFromSystem());
        assertNull(read.referenceSource());
        assertNull(read.freshStationCount());
        assertEquals(dto.priority(), read.priority());
        assertEquals(dto.searchRadiusLy(), read.searchRadiusLy());
        assertEquals(dto.searchRadiusLyDisplay(), read.searchRadiusLyDisplay());
        assertEquals(2, read.candidates().size());

        TradeCandidateDto readCand1 = read.candidates().get(0);
        assertEquals(candidate1, readCand1);

        TradeCandidateDto readCand2 = read.candidates().get(1);
        assertEquals(candidate2, readCand2);

        // Verify event was published
        assertEquals(1, eventCaptor.events.size());
        assertEquals(QueryResultDisplayManager.TYPE_TRADE_CANDIDATES, eventCaptor.events.get(0).queryType());
    }

    @Test
    void outfittingDataDtoRoundTripWithNullsMatchesAllFields() {
        MatchedModuleDto moduleDto = new MatchedModuleDto(
                "6A Frame Shift Drive",
                6,
                "A"
        );

        OutfittingDataDto dto = new OutfittingDataDto(
                "found",
                "6A FSD",
                moduleDto,
                "Shinrarta Dezhra",
                "Jameson Memorial",
                "Orbis",
                12.34,
                "12.34",
                null, // distanceToArrivalLs null
                null, // distanceToArrivalLsDisplay null
                16000000L,
                "16,000,000",
                "2026-09-26 12:00:00+00",
                2L,
                "2",
                false
        );

        manager.saveOutfitting(dto);

        Optional<QueryResultDisplayManager.DisplayRecord<OutfittingDataDto>> record = manager.getOutfitting();
        assertTrue(record.isPresent());
        assertEquals(QueryResultDisplayManager.TYPE_OUTFITTING, record.get().queryType());
        assertNotNull(record.get().savedAt());

        OutfittingDataDto read = record.get().data();
        assertEquals(dto.status(), read.status());
        assertEquals(dto.rawModuleInput(), read.rawModuleInput());
        assertEquals(dto.module(), read.module());
        assertEquals(dto.starSystem(), read.starSystem());
        assertEquals(dto.stationName(), read.stationName());
        assertEquals(dto.stationType(), read.stationType());
        assertEquals(dto.distanceLy(), read.distanceLy());
        assertEquals(dto.distanceLyDisplay(), read.distanceLyDisplay());
        assertNull(read.distanceToArrivalLs());
        assertNull(read.distanceToArrivalLsDisplay());
        assertEquals(dto.price(), read.price());
        assertEquals(dto.priceDisplay(), read.priceDisplay());
        assertEquals(dto.outfittingUpdatedAt(), read.outfittingUpdatedAt());
        assertEquals(dto.dataAgeHours(), read.dataAgeHours());
        assertEquals(dto.dataAgeHoursDisplay(), read.dataAgeHoursDisplay());
        assertEquals(dto.stale(), read.stale());

        // Verify event was published
        assertEquals(1, eventCaptor.events.size());
        assertEquals(QueryResultDisplayManager.TYPE_OUTFITTING, eventCaptor.events.get(0).queryType());
    }

    @Test
    void nonSavingStatusesClearPersistedData() {
        // First save a valid trade candidate
        TradeCandidateDto c = new TradeCandidateDto(
                1, "Gold", "Sol", "Galileo", 50000, "50,000", 1000L, "1,000", 100.0, "100",
                "2026-09-26 10:00:00+00", "Sol", "Columbus", 60000, "60,000", 2000L, "2,000",
                200.0, "200", "2026-09-26 10:00:00+00", 10000, "10,000", 100, "100",
                1000000L, "1,000,000", 0.0, "0.0", 0.0, "0.0"
        );
        manager.saveTradeCandidates(new TradeCandidatesDataDto("ok", "Sol", "profit", 30, List.of(c)));
        assertTrue(manager.getTradeCandidates().isPresent());

        // Saving no_result or too_few_stations clears it
        manager.saveTradeCandidates(TradeCandidatesDataDto.tooFewStations("Sol", "Sol", "current", "profit", 30, 1));
        assertFalse(manager.getTradeCandidates().isPresent(), "too_few_stations should clear previous results");

        // First save outfitting
        manager.saveOutfitting(new OutfittingDataDto("found", "FSD", null, "Sol", "Galileo", "Coriolis", 0.0, 100.0, 1000L, "2026-09-26 10:00:00+00", 1L, false));
        assertTrue(manager.getOutfitting().isPresent());

        // Saving no_result clears it
        manager.saveOutfitting(OutfittingDataDto.noResult("FSD", null));
        assertFalse(manager.getOutfitting().isPresent(), "noResult should clear previous outfitting result");
    }

    @Test
    void checkConstraintRejectsInvalidQueryTypeAtSqlLevel() {
        assertThrows(Exception.class, () -> Database.withDao(QueryResultDisplayDao.class, dao -> {
            dao.save("invalid_type", "2026-09-26T00:00:00Z", "{}");
            return null;
        }));
    }
}
