package elite.intel.junit.gameapi.journal.subscribers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import elite.intel.bio.ccore.RuleEvaluation;
import elite.intel.bio.ccore.RuleStatus;
import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.LocationManager;
import elite.intel.db.util.Database;
import elite.intel.gameapi.journal.events.SAASignalsFoundEvent;
import elite.intel.gameapi.journal.events.dto.LocationDto;
import elite.intel.gameapi.journal.subscribers.SAASignalsFoundSubscriber;
import elite.intel.util.json.GsonFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5 of the C-CORE integration plan (docs/ELITEINTEL_INTEGRATION_PLAN.md): the real
 * SAASignalsFound → CCoreAdapter → LocationDto.speciesEvaluations path, through the actual
 * {@code python -m app.cli bio evaluate} subprocess - not a fake one, since {@link CCoreAdapter} has no
 * seam for one yet and this is the one place that would catch the whole chain (including the
 * "Aleoids" journal stem → "Aleoida" C-CORE genus name conversion via BioForms) drifting apart.
 */
class SAASignalsFoundCCoreSliceTest {

    private final SAASignalsFoundSubscriber subscriber = new SAASignalsFoundSubscriber();
    private final LocationManager locationManager = LocationManager.getInstance();

    @Test
    void aleoidaArcusBoundaryBodyGetsAMatchStoredOnTheLocation() throws InterruptedException {
        long sysAddr = 111_222_333_444L;
        long bodyId = 7L;
        String starSystem = "CCoreSliceTestSystem";
        String planetName = starSystem + " 1";

        seedPrimaryStar(sysAddr, starSystem);
        // Stands in for a Scan event already having run: the exact BodyContext EDpjKinsaku's own test
        // fixture uses for Aleoida Arcus' boundary values (test_aleoida_arcus_matches_boundary_values_inclusive).
        locationManager.updateBody(sysAddr, bodyId, loc -> {
            loc.setPlanetName(planetName);
            loc.setStarName(starSystem);
            loc.setAtmosphere("CarbonDioxide");
            loc.setGravity(0.04);
            loc.setSurfaceTemperature(180.0);
            loc.setSurfacePressure(0.0161);
            loc.setPlanetClass("Rocky body");
            loc.setVolcanism("None");
        });

        subscriber.onSAASignalsFound(aleoidaSignalsEvent(planetName, sysAddr, bodyId));

        awaitTrue(() -> !locationManager.findBySystemAddress(sysAddr, bodyId).getSpeciesEvaluations().isEmpty());

        List<RuleEvaluation> evaluations = locationManager.findBySystemAddress(sysAddr, bodyId).getSpeciesEvaluations();
        RuleEvaluation arcus = evaluations.stream()
                .filter(e -> "Aleoida Arcus".equals(e.speciesName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aleoida Arcus missing from: " + evaluations));
        assertEquals(RuleStatus.MATCH, arcus.status());
    }

    @Test
    void aGenusOutsideTheSliceIsNeverSentToCCore() throws InterruptedException {
        long sysAddr = 555_666_777_888L;
        long bodyId = 3L;
        String starSystem = "CCoreSliceNonAleoidaSystem";
        String planetName = starSystem + " 1";

        seedPrimaryStar(sysAddr, starSystem);
        locationManager.updateBody(sysAddr, bodyId, loc -> loc.setPlanetName(planetName));

        subscriber.onSAASignalsFound(tussockSignalsEvent(planetName, sysAddr, bodyId));

        // No async C-CORE call is made for Tussocks, so there is nothing to await; give the virtual
        // thread a moment to have finished the (genus-only) processing before asserting the negative.
        awaitTrue(() -> !locationManager.findBySystemAddress(sysAddr, bodyId).getGenus().isEmpty());
        assertTrue(locationManager.findBySystemAddress(sysAddr, bodyId).getSpeciesEvaluations().isEmpty());
    }

    private void seedPrimaryStar(long sysAddr, String starSystem) {
        LocationDto star = new LocationDto(0L, sysAddr);
        star.setStarName(starSystem);
        star.setLocationType(LocationDto.LocationType.PRIMARY_STAR);
        Database.withDao(LocationDao.class, dao -> {
            dao.upsert(0L, "ccore_slice_test_primary_star_" + sysAddr, starSystem, sysAddr,
                    GsonFactory.getGson().toJson(star));
            return null;
        });
    }

    private static SAASignalsFoundEvent aleoidaSignalsEvent(String bodyName, long systemAddress, long bodyId) {
        return signalsEvent(bodyName, systemAddress, bodyId, "$Codex_Ent_Aleoids_Genus_Name;", "Aleoida");
    }

    private static SAASignalsFoundEvent tussockSignalsEvent(String bodyName, long systemAddress, long bodyId) {
        return signalsEvent(bodyName, systemAddress, bodyId, "$Codex_Ent_Tussocks_Genus_Name;", "Tussock");
    }

    private static SAASignalsFoundEvent signalsEvent(
            String bodyName, long systemAddress, long bodyId, String genusSymbol, String genusLocalised) {
        JsonObject j = new JsonObject();
        j.addProperty("timestamp", Instant.now().toString());
        j.addProperty("event", "SAASignalsFound");
        j.addProperty("BodyName", bodyName);
        j.addProperty("SystemAddress", systemAddress);
        j.addProperty("BodyID", bodyId);
        JsonArray signals = new JsonArray();
        JsonObject bioSignal = new JsonObject();
        bioSignal.addProperty("Type", "$SAA_SignalType_Biological;");
        bioSignal.addProperty("Count", 1);
        signals.add(bioSignal);
        j.add("Signals", signals);
        JsonArray genuses = new JsonArray();
        JsonObject genus = new JsonObject();
        genus.addProperty("Genus", genusSymbol);
        genus.addProperty("Genus_Localised", genusLocalised);
        genuses.add(genus);
        j.add("Genuses", genuses);
        return new SAASignalsFoundEvent(j);
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000; // the real CLI call needs headroom over its own 5s timeout
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Condition not met within 8 seconds");
            Thread.sleep(20);
        }
    }
}
