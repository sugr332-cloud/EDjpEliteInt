package elite.intel.gameapi.engineers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EngineerDirectoryTest {

    private final EngineerDirectory directory = EngineerDirectory.getInstance();

    @Test
    void directoryLoadsTotalAndCategoryCounts() {
        assertNotNull(directory);
        List<EngineerDirectory.EngineerInfo> all = directory.getAllEngineers();
        assertEquals(38, all.size(), "Total engineers count must be exactly 38");

        List<EngineerDirectory.EngineerInfo> ship = directory.getShipEngineers();
        assertEquals(25, ship.size(), "Ship engineers count must be 25");

        List<EngineerDirectory.EngineerInfo> onFoot = directory.getOnFootEngineers();
        assertEquals(13, onFoot.size(), "On-foot engineers count must be 13");
    }

    @Test
    void directoryReadsMeta() {
        EngineerDirectory.MetaInfo meta = directory.getMeta();
        assertNotNull(meta);
        assertNotNull(meta.description());
        assertFalse(meta.description().isBlank());
        assertNotNull(meta.checkedAt());
        assertFalse(meta.sources().isEmpty());
    }

    @Test
    void permitRequiredFlagIsParsedCorrectly() {
        // Marco Qwent requires Sirius permit -> true
        Optional<EngineerDirectory.EngineerInfo> marco = directory.findByName("Marco Qwent");
        assertTrue(marco.isPresent());
        assertTrue(marco.get().permitRequired(), "Marco Qwent must require permit");
        assertEquals("Sirius", marco.get().system());

        // Felicity Farseer does not require permit -> false
        Optional<EngineerDirectory.EngineerInfo> felicity = directory.findByName("Felicity Farseer");
        assertTrue(felicity.isPresent());
        assertFalse(felicity.get().permitRequired(), "Felicity Farseer must not require permit");
    }

    @Test
    void nameMatchingHandlesCasingAndQuoteVariations() {
        // Exact match
        Optional<EngineerDirectory.EngineerInfo> opt1 = directory.findByName("Tod 'The Blaster' McQuinn");
        assertTrue(opt1.isPresent());
        assertEquals("Tod 'The Blaster' McQuinn", opt1.get().name());

        // Lowercase
        Optional<EngineerDirectory.EngineerInfo> opt2 = directory.findByName("tod 'the blaster' mcquinn");
        assertTrue(opt2.isPresent());

        // Curly single quotes (right single quote ’ / left single quote ‘)
        Optional<EngineerDirectory.EngineerInfo> opt3 = directory.findByName("Tod ’The Blaster’ McQuinn");
        assertTrue(opt3.isPresent());

        // Double quotes
        Optional<EngineerDirectory.EngineerInfo> opt4 = directory.findByName("Tod \"The Blaster\" McQuinn");
        assertTrue(opt4.isPresent());

        // Curly double quotes
        Optional<EngineerDirectory.EngineerInfo> opt5 = directory.findByName("Tod “The Blaster” McQuinn");
        assertTrue(opt5.isPresent());

        // No quotes
        Optional<EngineerDirectory.EngineerInfo> opt6 = directory.findByName("Tod The Blaster McQuinn");
        assertTrue(opt6.isPresent());

        // Leading/trailing and extra whitespace
        Optional<EngineerDirectory.EngineerInfo> opt7 = directory.findByName("   felicity    farseer   ");
        assertTrue(opt7.isPresent());
        assertEquals("Felicity Farseer", opt7.get().name());
    }

    @Test
    void normalizeNameWorksConsistently() {
        assertEquals("tod the blaster mcquinn", EngineerDirectory.normalizeName("Tod 'The Blaster' McQuinn"));
        assertEquals("tod the blaster mcquinn", EngineerDirectory.normalizeName("Tod ’The Blaster’ McQuinn"));
        assertEquals("tod the blaster mcquinn", EngineerDirectory.normalizeName("Tod \"The Blaster\" McQuinn"));
        assertEquals("tod the blaster mcquinn", EngineerDirectory.normalizeName("Tod “The Blaster” McQuinn"));
        assertEquals("felicity farseer", EngineerDirectory.normalizeName("   Felicity   Farseer  "));
        assertEquals("", EngineerDirectory.normalizeName(null));
        assertEquals("", EngineerDirectory.normalizeName("   "));
    }
}
