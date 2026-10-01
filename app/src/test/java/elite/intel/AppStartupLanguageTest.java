package elite.intel;

import elite.intel.diagnostics.DiagnosticsMode;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import elite.intel.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppStartupLanguageTest {

    private Language originalLanguage;
    private Path diagnosticsLangFile;

    @BeforeEach
    void setUp() throws IOException {
        originalLanguage = SystemSession.getInstance().getLanguage();
        diagnosticsLangFile = AppPaths.getDiagnosticsLanguageFile();
        Files.deleteIfExists(diagnosticsLangFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(diagnosticsLangFile);
        SystemSession.getInstance().setLanguage(originalLanguage);
    }

    @Test
    void startupLanguageInitializesToJapaneseEvenIfStoredAsEnglish() {
        SystemSession.getInstance().setLanguage(Language.EN);
        assertEquals(Language.EN, SystemSession.getInstance().getLanguage());

        App.initStartupLanguage();
        assertEquals(Language.JA, SystemSession.getInstance().getLanguage());
    }

    @Test
    void diagnosticsModeOverridesStartupLanguageWhenFilePresent() throws IOException {
        Files.createDirectories(diagnosticsLangFile.getParent());
        Files.writeString(diagnosticsLangFile, "EN");

        App.initStartupLanguage();
        assertEquals(Language.JA, SystemSession.getInstance().getLanguage());

        DiagnosticsMode.applyBootLanguage();
        assertEquals(Language.EN, SystemSession.getInstance().getLanguage());
    }
}
