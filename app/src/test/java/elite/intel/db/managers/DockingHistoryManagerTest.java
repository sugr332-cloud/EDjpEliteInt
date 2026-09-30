package elite.intel.db.managers;

import com.google.gson.JsonObject;
import elite.intel.db.dao.DockingHistoryDao.DockingHistoryEntry;
import elite.intel.gameapi.journal.events.DockedEvent;
import elite.intel.gameapi.journal.events.LocationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DockingHistoryManagerTest {

    private final DockingHistoryManager manager = DockingHistoryManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.clear();
    }

    @AfterEach
    void tearDown() {
        manager.clear();
    }

    private static DockedEvent dockedEvent(String stationName, String starSystem, long systemAddress,
                                           long marketId, String stationType, double distFromStarLS,
                                           String timestamp) {
        JsonObject j = new JsonObject();
        j.addProperty("timestamp", timestamp != null ? timestamp : "2026-09-28T12:00:00Z");
        j.addProperty("event", "Docked");
        j.addProperty("StationName", stationName);
        j.addProperty("StationType", stationType);
        j.addProperty("StarSystem", starSystem);
        j.addProperty("SystemAddress", systemAddress);
        j.addProperty("MarketID", marketId);
        j.addProperty("DistFromStarLS", distFromStarLS);
        return new DockedEvent(j);
    }

    private static LocationEvent locationEvent(boolean docked, String stationName, String starSystem,
                                               long systemAddress, long marketId, String stationType,
                                               double distFromStarLS, String timestamp) {
        JsonObject j = new JsonObject();
        j.addProperty("timestamp", timestamp != null ? timestamp : "2026-09-28T12:00:00Z");
        j.addProperty("event", "Location");
        j.addProperty("Docked", docked);
        if (stationName != null) {
            j.addProperty("StationName", stationName);
        }
        if (stationType != null) {
            j.addProperty("StationType", stationType);
        }
        j.addProperty("StarSystem", starSystem != null ? starSystem : "Sol");
        j.addProperty("SystemAddress", systemAddress);
        j.addProperty("MarketID", marketId);
        j.addProperty("DistFromStarLS", distFromStarLS);
        return new LocationEvent(j);
    }

    /**
     * TEST GATE 1: Docked で 1 件追加され、全項目が保存されること
     */
    @Test
    void test1_dockedAddsEntryAndSavesAllFields() {
        DockedEvent event = dockedEvent("Jameson Memorial", "Shinrarta Dezhra", 123456L,
                999999L, "Orbis", 123.4, "2026-09-28T10:00:00Z");

        manager.recordDocked(event);

        List<DockingHistoryEntry> history = manager.getHistory(10);
        assertEquals(1, history.size(), "Should have exactly 1 entry");

        DockingHistoryEntry entry = history.get(0);
        assertTrue(entry.id() > 0, "ID should be positive");
        assertEquals("Jameson Memorial", entry.stationName());
        assertEquals("Shinrarta Dezhra", entry.starSystem());
        assertEquals(123456L, entry.systemAddress());
        assertEquals(999999L, entry.marketId());
        assertEquals("Orbis", entry.stationType());
        assertEquals(123.4, entry.distFromStarLS(), 0.001);
        assertEquals("2026-09-28T10:00:00Z", entry.dockedAt());
    }

    /**
     * TEST GATE 2: 同じ MarketID の Docked が続いたとき、件数は増えず全項目が新しい値になること
     */
    @Test
    void test2_consecutiveDockedSameMarketIdUpdatesAllFieldsWithoutAddingRow() {
        DockedEvent ev1 = dockedEvent("Jameson Memorial", "Shinrarta Dezhra", 123456L,
                999999L, "Orbis", 123.4, "2026-09-28T10:00:00Z");
        manager.recordDocked(ev1);

        long originalId = manager.getHistory(10).get(0).id();

        DockedEvent ev2 = dockedEvent("Jameson Memorial Updated", "Shinrarta Dezhra 2", 654321L,
                999999L, "Coriolis", 456.7, "2026-09-28T11:00:00Z");
        manager.recordDocked(ev2);

        List<DockingHistoryEntry> history = manager.getHistory(10);
        assertEquals(1, history.size(), "Count should remain 1");

        DockingHistoryEntry updated = history.get(0);
        assertEquals(originalId, updated.id(), "Row ID should remain the same");
        assertEquals("Jameson Memorial Updated", updated.stationName());
        assertEquals("Shinrarta Dezhra 2", updated.starSystem());
        assertEquals(654321L, updated.systemAddress());
        assertEquals(999999L, updated.marketId());
        assertEquals("Coriolis", updated.stationType());
        assertEquals(456.7, updated.distFromStarLS(), 0.001);
        assertEquals("2026-09-28T11:00:00Z", updated.dockedAt());
    }

    /**
     * TEST GATE 3: 同じ MarketID のまま starSystem／systemAddress が変わった場合（キャリアの移動）、その行の星系が更新されること
     */
    @Test
    void test3_sameMarketIdSystemChangeUpdatesSystemFields() {
        DockedEvent ev1 = dockedEvent("N.A.S. Fleet", "Sol", 1000L,
                777777L, "FleetCarrier", 50.0, "2026-09-28T10:00:00Z");
        manager.recordDocked(ev1);

        DockedEvent ev2 = dockedEvent("N.A.S. Fleet", "Alpha Centauri", 2000L,
                777777L, "FleetCarrier", 15.0, "2026-09-28T11:00:00Z");
        manager.recordDocked(ev2);

        List<DockingHistoryEntry> history = manager.getHistory(10);
        assertEquals(1, history.size(), "Count should remain 1");

        DockingHistoryEntry updated = history.get(0);
        assertEquals("N.A.S. Fleet", updated.stationName());
        assertEquals("Alpha Centauri", updated.starSystem());
        assertEquals(2000L, updated.systemAddress());
        assertEquals(777777L, updated.marketId());
        assertEquals("FleetCarrier", updated.stationType());
        assertEquals(15.0, updated.distFromStarLS(), 0.001);
        assertEquals("2026-09-28T11:00:00Z", updated.dockedAt());
    }

    /**
     * TEST GATE 4: 51 件追加で 50 件に保たれ、最も古い 1 件が消えていること
     */
    @Test
    void test4_maintains50EntriesMaxAndDeletesOldest() {
        for (int i = 1; i <= 51; i++) {
            DockedEvent ev = dockedEvent("Station " + i, "System " + i, 1000L + i,
                    10000L + i, "Coriolis", (double) i, "2026-09-28T10:00:" + String.format("%02d", i % 60) + "Z");
            manager.recordDocked(ev);
        }

        List<DockingHistoryEntry> history = manager.getHistory(100);
        assertEquals(50, history.size(), "History should be capped at 50 entries");

        // Oldest entry (MarketID 10001L) should have been deleted
        boolean hasOldest = history.stream().anyMatch(e -> e.marketId() == 10001L);
        assertFalse(hasOldest, "Oldest entry (MarketID 10001) should have been pruned");

        // Newest entry (MarketID 10051L) should be at index 0 (latest)
        assertEquals(10051L, history.get(0).marketId(), "Latest entry should be MarketID 10051");

        // The oldest remaining entry should be MarketID 10002L
        assertEquals(10002L, history.get(history.size() - 1).marketId(), "Oldest surviving entry should be MarketID 10002");
    }

    /**
     * TEST GATE 5: getNthPreviousStation: back=0／1／2 が正しいこと。負や範囲外は空
     */
    @Test
    void test5_getNthPreviousStation() {
        DockedEvent evA = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T01:00:00Z");
        DockedEvent evB = dockedEvent("Station B", "System B", 200L, 20L, "Orbis", 2.0, "2026-09-28T02:00:00Z");
        DockedEvent evC = dockedEvent("Station C", "System C", 300L, 30L, "Ocellus", 3.0, "2026-09-28T03:00:00Z");

        manager.recordDocked(evA);
        manager.recordDocked(evB);
        manager.recordDocked(evC);

        // back = 0 (latest = C)
        Optional<DockingHistoryEntry> station0 = manager.getNthPreviousStation(0);
        assertTrue(station0.isPresent());
        assertEquals("Station C", station0.get().stationName());

        // back = 1 (1 previous = B)
        Optional<DockingHistoryEntry> station1 = manager.getNthPreviousStation(1);
        assertTrue(station1.isPresent());
        assertEquals("Station B", station1.get().stationName());

        // back = 2 (2 previous = A)
        Optional<DockingHistoryEntry> station2 = manager.getNthPreviousStation(2);
        assertTrue(station2.isPresent());
        assertEquals("Station A", station2.get().stationName());

        // Out of range (back = 3)
        Optional<DockingHistoryEntry> station3 = manager.getNthPreviousStation(3);
        assertTrue(station3.isEmpty(), "back=3 should be empty when only 3 entries exist");

        // Negative (back = -1)
        Optional<DockingHistoryEntry> stationNeg = manager.getNthPreviousStation(-1);
        assertTrue(stationNeg.isEmpty(), "back=-1 should be empty");

        // Large out of range
        Optional<DockingHistoryEntry> stationLarge = manager.getNthPreviousStation(999);
        assertTrue(stationLarge.isEmpty(), "back=999 should be empty");
    }

    /**
     * TEST GATE 6: getRecentDistinctStations: A→B で (B, A)、A→A→B で (B, A)、A のみ・同じ MarketID のみは空
     */
    @Test
    void test6_getRecentDistinctStations() {
        DockedEvent evA = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T01:00:00Z");
        DockedEvent evB = dockedEvent("Station B", "System B", 200L, 20L, "Orbis", 2.0, "2026-09-28T02:00:00Z");

        // 1. A -> B => (B, A)
        manager.recordDocked(evA);
        manager.recordDocked(evB);
        Optional<DockingHistoryManager.DistinctStations> pairAB = manager.getRecentDistinctStations();
        assertTrue(pairAB.isPresent());
        assertEquals("Station B", pairAB.get().current().stationName());
        assertEquals("Station A", pairAB.get().previous().stationName());

        // 2. A -> A -> B => (B, A)
        manager.clear();
        DockedEvent evA2 = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.5, "2026-09-28T01:30:00Z");
        manager.recordDocked(evA);
        manager.recordDocked(evA2);
        manager.recordDocked(evB);
        Optional<DockingHistoryManager.DistinctStations> pairAAB = manager.getRecentDistinctStations();
        assertTrue(pairAAB.isPresent());
        assertEquals("Station B", pairAAB.get().current().stationName());
        assertEquals("Station A", pairAAB.get().previous().stationName());

        // 3. A のみ => 空
        manager.clear();
        manager.recordDocked(evA);
        Optional<DockingHistoryManager.DistinctStations> pairOnlyA = manager.getRecentDistinctStations();
        assertTrue(pairOnlyA.isEmpty(), "Single station should return empty");

        // 4. 同じ MarketID のみ => 空
        manager.clear();
        manager.recordDocked(evA);
        manager.recordDocked(evA2);
        Optional<DockingHistoryManager.DistinctStations> pairSameMarket = manager.getRecentDistinctStations();
        assertTrue(pairSameMarket.isEmpty(), "Consecutive same station only should return empty");
    }

    /**
     * TEST GATE 7: Location（ドッキング中）で直前と同じ MarketID は追加されないこと
     */
    @Test
    void test7_locationDockedSameMarketIdNotAdded() {
        DockedEvent evA = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T01:00:00Z");
        manager.recordDocked(evA);

        LocationEvent locA = locationEvent(true, "Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T02:00:00Z");
        manager.recordLocation(locA);

        List<DockingHistoryEntry> history = manager.getHistory(10);
        assertEquals(1, history.size(), "Location with same MarketID while docked should not add an entry");
        assertEquals("2026-09-28T01:00:00Z", history.get(0).dockedAt(), "Docked timestamp should not be overwritten by Location");
    }

    /**
     * TEST GATE 8: Location（ドッキング中）で直前と違う MarketID は 1 件追加されること
     */
    @Test
    void test8_locationDockedDifferentMarketIdAddsEntry() {
        DockedEvent evA = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T01:00:00Z");
        manager.recordDocked(evA);

        LocationEvent locB = locationEvent(true, "Station B", "System B", 200L, 20L, "Orbis", 2.0, "2026-09-28T02:00:00Z");
        manager.recordLocation(locB);

        List<DockingHistoryEntry> history = manager.getHistory(10);
        assertEquals(2, history.size(), "Location with different MarketID while docked should add an entry");
        assertEquals("Station B", history.get(0).stationName(), "Latest should be Station B");
        assertEquals("Station A", history.get(1).stationName(), "Previous should be Station A");
    }

    /**
     * TEST GATE 9: isDocked() が false の Location は記録されないこと
     */
    @Test
    void test9_locationNotDockedNotRecorded() {
        // First verify empty state stays empty
        LocationEvent locUndocked1 = locationEvent(false, null, "System X", 500L, 0L, null, 0.0, "2026-09-28T01:00:00Z");
        manager.recordLocation(locUndocked1);
        assertTrue(manager.getHistory(10).isEmpty(), "Undocked location should not be recorded into empty history");

        // Verify with existing entry
        DockedEvent evA = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T01:00:00Z");
        manager.recordDocked(evA);
        assertEquals(1, manager.getHistory(10).size());

        LocationEvent locUndocked2 = locationEvent(false, "Station B", "System B", 200L, 20L, "Orbis", 2.0, "2026-09-28T02:00:00Z");
        manager.recordLocation(locUndocked2);

        List<DockingHistoryEntry> history = manager.getHistory(10);
        assertEquals(1, history.size(), "Undocked location must not add an entry");
        assertEquals("Station A", history.get(0).stationName());
    }

    /**
     * TEST GATE 10: MarketID が 0 のとき、stationName と systemAddress の組で同じと判定されること（同じ組は更新、違う組は追加）
     */
    @Test
    void test10_marketIdZeroUsesStationNameAndSystemAddress() {
        // Initial entry with MarketID = 0
        DockedEvent ev1 = dockedEvent("Outpost Alpha", "Sol", 12345L, 0L, "Outpost", 10.0, "2026-09-28T10:00:00Z");
        manager.recordDocked(ev1);
        assertEquals(1, manager.getHistory(10).size());

        // Same stationName & systemAddress with MarketID = 0 -> update
        DockedEvent evSame = dockedEvent("Outpost Alpha", "Sol", 12345L, 0L, "Outpost", 25.0, "2026-09-28T11:00:00Z");
        manager.recordDocked(evSame);
        List<DockingHistoryEntry> historyAfterSame = manager.getHistory(10);
        assertEquals(1, historyAfterSame.size(), "Same stationName & systemAddress with marketId=0 should update in place");
        assertEquals(25.0, historyAfterSame.get(0).distFromStarLS(), 0.001);
        assertEquals("2026-09-28T11:00:00Z", historyAfterSame.get(0).dockedAt());

        // Different stationName with same systemAddress and MarketID = 0 -> add
        DockedEvent evDiffName = dockedEvent("Outpost Beta", "Sol", 12345L, 0L, "Outpost", 30.0, "2026-09-28T12:00:00Z");
        manager.recordDocked(evDiffName);
        List<DockingHistoryEntry> historyAfterDiffName = manager.getHistory(10);
        assertEquals(2, historyAfterDiffName.size(), "Different stationName with marketId=0 should add an entry");
        assertEquals("Outpost Beta", historyAfterDiffName.get(0).stationName());

        // Same stationName with different systemAddress and MarketID = 0 -> add
        DockedEvent evDiffAddr = dockedEvent("Outpost Beta", "Proxima", 99999L, 0L, "Outpost", 35.0, "2026-09-28T13:00:00Z");
        manager.recordDocked(evDiffAddr);
        List<DockingHistoryEntry> historyAfterDiffAddr = manager.getHistory(10);
        assertEquals(3, historyAfterDiffAddr.size(), "Different systemAddress with marketId=0 should add an entry");
        assertEquals("Outpost Beta", historyAfterDiffAddr.get(0).stationName());
        assertEquals("Proxima", historyAfterDiffAddr.get(0).starSystem());
        assertEquals(99999L, historyAfterDiffAddr.get(0).systemAddress());
    }

    /**
     * TEST GATE 11: isFleetCarrier(): FleetCarrier は true、それ以外は false
     */
    @Test
    void test11_isFleetCarrier() {
        DockingHistoryEntry carrier = new DockingHistoryEntry(1, "Carrier 1", "Sol", 1L, 1L, "FleetCarrier", 0.0, "ts");
        assertTrue(carrier.isFleetCarrier(), "FleetCarrier exact match should return true");

        DockingHistoryEntry carrierLower = new DockingHistoryEntry(2, "Carrier 2", "Sol", 1L, 1L, "fleetcarrier", 0.0, "ts");
        assertTrue(carrierLower.isFleetCarrier(), "fleetcarrier case-insensitive match should return true");

        DockingHistoryEntry carrierUpper = new DockingHistoryEntry(3, "Carrier 3", "Sol", 1L, 1L, "FLEETCARRIER", 0.0, "ts");
        assertTrue(carrierUpper.isFleetCarrier(), "FLEETCARRIER upper match should return true");

        DockingHistoryEntry coriolis = new DockingHistoryEntry(4, "Station 1", "Sol", 1L, 1L, "Coriolis", 0.0, "ts");
        assertFalse(coriolis.isFleetCarrier(), "Coriolis should return false");

        DockingHistoryEntry outpost = new DockingHistoryEntry(5, "Station 2", "Sol", 1L, 1L, "Outpost", 0.0, "ts");
        assertFalse(outpost.isFleetCarrier(), "Outpost should return false");

        DockingHistoryEntry nullType = new DockingHistoryEntry(6, "Station 3", "Sol", 1L, 1L, null, 0.0, "ts");
        assertFalse(nullType.isFleetCarrier(), "Null stationType should return false");

        DockingHistoryEntry emptyType = new DockingHistoryEntry(7, "Station 4", "Sol", 1L, 1L, "", 0.0, "ts");
        assertFalse(emptyType.isFleetCarrier(), "Empty stationType should return false");
    }

    /**
     * TEST GATE LF-1: getPreviousStation(back, isDocked, currentDockedMarketId)
     * - ドッキング中・一致 → back 件目
     * - ドッキング中・不一致 → back - 1 件目
     * - ドッキング中・MarketID 0 → back 件目
     * - 未ドッキング → back - 1 件目
     * - 範囲外 → 空
     */
    @Test
    void test12_getPreviousStation() {
        // History: 0=Station C (marketId=30), 1=Station B (marketId=20), 2=Station A (marketId=10)
        DockedEvent evA = dockedEvent("Station A", "System A", 100L, 10L, "Coriolis", 1.0, "2026-09-28T01:00:00Z");
        DockedEvent evB = dockedEvent("Station B", "System B", 200L, 20L, "Orbis", 2.0, "2026-09-28T02:00:00Z");
        DockedEvent evC = dockedEvent("Station C", "System C", 300L, 30L, "Ocellus", 3.0, "2026-09-28T03:00:00Z");

        manager.recordDocked(evA);
        manager.recordDocked(evB);
        manager.recordDocked(evC);

        // 1. ドッキング中・一致 (currentDockedMarketId = 30L matches 0th entry Station C)
        // back=1 -> index 1 (Station B)
        Optional<DockingHistoryEntry> dockedMatchBack1 = manager.getPreviousStation(1, true, 30L);
        assertTrue(dockedMatchBack1.isPresent());
        assertEquals("Station B", dockedMatchBack1.get().stationName());

        // back=2 -> index 2 (Station A)
        Optional<DockingHistoryEntry> dockedMatchBack2 = manager.getPreviousStation(2, true, 30L);
        assertTrue(dockedMatchBack2.isPresent());
        assertEquals("Station A", dockedMatchBack2.get().stationName());

        // 2. ドッキング中・不一致 (currentDockedMarketId = 99L != 30L, unrecorded docked station)
        // back=1 -> index 0 (Station C, the previous station before current unrecorded one)
        Optional<DockingHistoryEntry> dockedMismatchBack1 = manager.getPreviousStation(1, true, 99L);
        assertTrue(dockedMismatchBack1.isPresent());
        assertEquals("Station C", dockedMismatchBack1.get().stationName());

        // back=2 -> index 1 (Station B)
        Optional<DockingHistoryEntry> dockedMismatchBack2 = manager.getPreviousStation(2, true, 99L);
        assertTrue(dockedMismatchBack2.isPresent());
        assertEquals("Station B", dockedMismatchBack2.get().stationName());

        // 3. ドッキング中・MarketID 0 (currentDockedMarketId = 0L)
        // back=1 -> index 1 (Station B)
        Optional<DockingHistoryEntry> dockedZeroBack1 = manager.getPreviousStation(1, true, 0L);
        assertTrue(dockedZeroBack1.isPresent());
        assertEquals("Station B", dockedZeroBack1.get().stationName());

        // 4. 未ドッキング (isDocked = false)
        // back=1 -> index 0 (Station C, last docked station)
        Optional<DockingHistoryEntry> undockedBack1 = manager.getPreviousStation(1, false, 0L);
        assertTrue(undockedBack1.isPresent());
        assertEquals("Station C", undockedBack1.get().stationName());

        // back=2 -> index 1 (Station B, station before last docked station)
        Optional<DockingHistoryEntry> undockedBack2 = manager.getPreviousStation(2, false, 0L);
        assertTrue(undockedBack2.isPresent());
        assertEquals("Station B", undockedBack2.get().stationName());

        // 5. 範囲外 -> 空
        // back=0 -> empty (< 1)
        assertTrue(manager.getPreviousStation(0, false, 0L).isEmpty());
        assertTrue(manager.getPreviousStation(-1, true, 30L).isEmpty());

        // back=3 on docked match -> index 3 (out of range, only 3 entries)
        assertTrue(manager.getPreviousStation(3, true, 30L).isEmpty());

        // back=4 on undocked -> index 3 (out of range)
        assertTrue(manager.getPreviousStation(4, false, 0L).isEmpty());
    }
}

