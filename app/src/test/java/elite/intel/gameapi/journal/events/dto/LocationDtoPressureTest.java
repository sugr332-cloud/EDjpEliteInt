package elite.intel.gameapi.journal.events.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Surface pressure was carried by {@link elite.intel.gameapi.journal.events.ScanEvent} but never copied
 * into {@link LocationDto} - every C-CORE rule keyed on pressure could only ever see it as missing.
 */
class LocationDtoPressureTest {

    @Test
    void aScanRecordsTheBodysSurfacePressure() {
        LocationDto body = new LocationDto(2L, 1L);

        body.setSurfacePressure(101325.0);

        assertEquals(101325.0, body.getSurfacePressure());
    }

    /**
     * Mirrors {@code setSurfaceTemperature}/{@code setGravity}: a later scan reporting 0 (the game's
     * placeholder for "not measured this pass", e.g. a NavBeaconDetail bulk scan) must not overwrite an
     * already-recorded real value.
     */
    @Test
    void aLaterZeroDoesNotOverwriteAnAlreadyRecordedPressure() {
        LocationDto body = new LocationDto(2L, 1L);
        body.setSurfacePressure(101325.0);

        body.setSurfacePressure(0);

        assertEquals(101325.0, body.getSurfacePressure());
    }

    @Test
    void aBodyStartsWithNoRecordedPressure() {
        assertEquals(0.0, new LocationDto(2L, 1L).getSurfacePressure());
    }
}
