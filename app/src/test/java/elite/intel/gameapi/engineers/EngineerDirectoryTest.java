package elite.intel.gameapi.engineers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import elite.intel.i18n.Language;

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

    @Test
    void allEngineersHaveNonEmptySpecialties() {
        List<EngineerDirectory.EngineerInfo> all = directory.getAllEngineers();
        assertEquals(38, all.size());
        for (EngineerDirectory.EngineerInfo eng : all) {
            assertNotNull(eng.specialties(), "Specialties list must not be null for " + eng.name());
            assertFalse(eng.specialties().isEmpty(), "Specialties must not be empty for " + eng.name());
        }
    }

    @Test
    void onFootEngineerSpecialtiesParsedWithGradeZero() {
        Optional<EngineerDirectory.EngineerInfo> dominoOpt = directory.findByName("Domino Green");
        assertTrue(dominoOpt.isPresent());
        EngineerDirectory.EngineerInfo domino = dominoOpt.get();
        assertTrue(domino.isOnFoot());
        assertFalse(domino.specialties().isEmpty());

        boolean hasGreaterRange = domino.specialties().stream().anyMatch(
                sp -> "Greater range".equals(sp.module()) && sp.maxGrade() == 0
        );
        assertTrue(hasGreaterRange, "Domino Green specialties must contain module 'Greater range' with maxGrade 0");
    }

    @Test
    void shipEngineerSpecialtiesParsedWithPositiveGrade() {
        Optional<EngineerDirectory.EngineerInfo> felicityOpt = directory.findByName("Felicity Farseer");
        assertTrue(felicityOpt.isPresent());
        EngineerDirectory.EngineerInfo felicity = felicityOpt.get();
        assertTrue(felicity.isShip());
        assertFalse(felicity.specialties().isEmpty());

        for (EngineerDirectory.Specialty sp : felicity.specialties()) {
            assertTrue(sp.maxGrade() >= 1, "Ship engineer specialty grade must be 1 or higher for " + sp.module());
        }
    }

    @Test
    void allEngineersHaveNonEmptyNamesJa() {
        List<EngineerDirectory.EngineerInfo> all = directory.getAllEngineers();
        assertEquals(38, all.size());
        for (EngineerDirectory.EngineerInfo eng : all) {
            assertNotNull(eng.namesJa(), "namesJa must not be null for " + eng.name());
            assertFalse(eng.namesJa().isEmpty(), "namesJa must not be empty for " + eng.name());
        }
    }

    @Test
    void findMentionedInMatchesVariousJapaneseAndEnglishUtterances() {
        // "フェリシティ・ファーシーアの開放条件は", "フェリシティファーシーアはどこ" -> Felicity Farseer
        Optional<EngineerDirectory.EngineerInfo> felicity1 = directory.findMentionedIn("フェリシティ・ファーシーアの開放条件は");
        assertTrue(felicity1.isPresent());
        assertEquals("Felicity Farseer", felicity1.get().name());

        Optional<EngineerDirectory.EngineerInfo> felicity2 = directory.findMentionedIn("フェリシティファーシーアはどこ");
        assertTrue(felicity2.isPresent());
        assertEquals("Felicity Farseer", felicity2.get().name());

        // "パリン教授" -> Professor Palin
        Optional<EngineerDirectory.EngineerInfo> palin = directory.findMentionedIn("パリン教授に会いたい");
        assertTrue(palin.isPresent());
        assertEquals("Professor Palin", palin.get().name());

        // "Tod McQuinn", "The Blaster", "ブラスター" -> Tod 'The Blaster' McQuinn
        Optional<EngineerDirectory.EngineerInfo> tod1 = directory.findMentionedIn("where is Tod McQuinn");
        assertTrue(tod1.isPresent());
        assertEquals("Tod 'The Blaster' McQuinn", tod1.get().name());

        Optional<EngineerDirectory.EngineerInfo> tod2 = directory.findMentionedIn("tell me about The Blaster");
        assertTrue(tod2.isPresent());
        assertEquals("Tod 'The Blaster' McQuinn", tod2.get().name());

        Optional<EngineerDirectory.EngineerInfo> tod3 = directory.findMentionedIn("ブラスターの開放条件");
        assertTrue(tod3.isPresent());
        assertEquals("Tod 'The Blaster' McQuinn", tod3.get().name());
    }

    @Test
    void normalizeForSpeechStripsQuotesMiddleDotsAndWhitespace() {
        assertEquals("felicityfarseer", EngineerDirectory.normalizeForSpeech("Felicity Farseer"));
        assertEquals("フェリシティファーシーア", EngineerDirectory.normalizeForSpeech("フェリシティ・ファーシーア"));
        assertEquals("フェリシティファーシーア", EngineerDirectory.normalizeForSpeech("フェリシティ･ファーシーア"));
        assertEquals("todtheblastermcquinn", EngineerDirectory.normalizeForSpeech("Tod 'The Blaster' McQuinn"));
        assertEquals("todtheblastermcquinn", EngineerDirectory.normalizeForSpeech("Tod “The Blaster” McQuinn"));
        assertEquals("", EngineerDirectory.normalizeForSpeech(null));
        assertEquals("", EngineerDirectory.normalizeForSpeech("   "));
    }

    @Test
    void specialtyNamesJaLoadedCorrectly() {
        assertEquals(72, directory.getSpecialtyNamesJa().size(), "specialtyNamesJa must contain exactly 72 mappings");
        assertEquals("フレームシフトドライブ（FSD）", directory.localizedSpecialtyName("Frame Shift Drive", Language.JA));
        assertEquals("Frame Shift Drive", directory.localizedSpecialtyName("Frame Shift Drive", Language.EN));
        assertEquals("射程の延長", directory.localizedSpecialtyName("Greater range", Language.JA));
        assertEquals("Greater range", directory.localizedSpecialtyName("Greater range", Language.EN));
        // Fallback for unknown
        assertEquals("Unknown Specialty", directory.localizedSpecialtyName("Unknown Specialty", Language.JA));
    }

    @Test
    void engineerLocalizedNamesAndConditions() {
        EngineerDirectory.EngineerInfo felicity = directory.findByName("Felicity Farseer").orElseThrow();
        assertEquals("フェリシティ・ファーシーア（Felicity Farseer）", directory.displayName(felicity, Language.JA));
        assertEquals("Felicity Farseer", directory.displayName(felicity, Language.EN));
        assertEquals("フェリシティ・ファーシーア", directory.spokenName(felicity, Language.JA));
        assertEquals("Felicity Farseer", directory.spokenName(felicity, Language.EN));

        // Conditions
        assertNotNull(felicity.inviteJa());
        assertFalse(felicity.inviteJa().isBlank());
        assertNotNull(felicity.unlockJa());
        assertFalse(felicity.unlockJa().isBlank());

        assertEquals(felicity.inviteJa(), directory.localizedInvite(felicity, Language.JA));
        assertEquals(felicity.invite(), directory.localizedInvite(felicity, Language.EN));
        assertEquals(felicity.unlockJa(), directory.localizedUnlock(felicity, Language.JA));
        assertEquals(felicity.unlock(), directory.localizedUnlock(felicity, Language.EN));
    }

    @Test
    void engineerReferralFormatting() {
        // Terra Velasquez referral is Jude Navarro
        EngineerDirectory.EngineerInfo terra = directory.findByName("Terra Velasquez").orElseThrow();
        assertEquals("Jude Navarro", terra.referral());

        // Display format: ジュード・ナバロ（Jude Navarro）
        assertEquals("ジュード・ナバロ（Jude Navarro）", directory.formatReferral(terra.referral(), Language.JA));
        assertEquals("Jude Navarro", directory.formatReferral(terra.referral(), Language.EN));

        // Spoken format: ジュード・ナバロ
        assertEquals("ジュード・ナバロ", directory.formatReferralSpoken(terra.referral(), Language.JA));
        assertEquals("Jude Navarro", directory.formatReferralSpoken(terra.referral(), Language.EN));

        // Null / blank referral
        assertEquals("", directory.formatReferral(null, Language.JA));
        assertEquals("", directory.formatReferral("", Language.JA));
        assertEquals("", directory.formatReferralSpoken(null, Language.JA));
        assertEquals("", directory.formatReferralSpoken("", Language.JA));
    }
}
