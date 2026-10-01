package elite.intel.ui.widget;

import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
import elite.intel.ui.screen.AiTabPanel;
import elite.intel.ui.screen.AiUiState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTabPanelChatNamesTest {

    private Language originalLanguage;
    private AiTabPanel panel;

    @BeforeEach
    void setUp() {
        originalLanguage = SystemSession.getInstance().getLanguage();
    }

    @AfterEach
    void tearDown() {
        SystemSession.getInstance().setLanguage(originalLanguage);
        if (panel != null) {
            panel.dispose();
        }
    }

    @Test
    void chatMessagesDecorateEngineerNamesInJapanese() throws Exception {
        SystemSession.getInstance().setLanguage(Language.JA);

        panel = new AiTabPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), new AiUiState());
        List<HudLogArea> logAreas = findComponentsOfType(panel, HudLogArea.class);
        assertFalse(logAreas.isEmpty());
        // chatPanel is the selectable HudLogArea with Align support
        HudLogArea chatArea = logAreas.get(0);

        panel.addUserMessage("フェリシティ・ファーシーアが良いです");
        panel.addAiMessage("パリン教授とデッカー大佐に確認しました");

        // Wait for Swing invokeLater to run
        SwingUtilities.invokeAndWait(() -> {});

        String exported = chatArea.exportText();
        assertTrue(exported.contains("フェリシティ・ファーシーア（Felicity Farseer）が良いです"),
                "User message must decorate engineer name with displayName. Exported: " + exported);

        assertTrue(exported.contains("パリン教授（Professor Palin）") && exported.contains("ブリス・デッカー（Colonel Bris Dekker）"),
                "AI message must decorate both engineer names with displayNames. Exported: " + exported);
    }

    @Test
    void chatMessagesDoNotDecorateInEnglish() throws Exception {
        SystemSession.getInstance().setLanguage(Language.EN);

        panel = new AiTabPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), new AiUiState());
        List<HudLogArea> logAreas = findComponentsOfType(panel, HudLogArea.class);
        HudLogArea chatArea = logAreas.get(0);

        panel.addUserMessage("フェリシティ・ファーシーアが良いです");

        SwingUtilities.invokeAndWait(() -> {});

        String exported = chatArea.exportText();
        assertTrue(exported.contains("フェリシティ・ファーシーアが良いです") && !exported.contains("（Felicity Farseer）"),
                "English mode must not decorate Katakana names. Exported: " + exported);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> List<T> findComponentsOfType(Container container, Class<T> type) {
        List<T> list = new ArrayList<>();
        for (Component comp : container.getComponents()) {
            if (type.isInstance(comp)) {
                list.add((T) comp);
            }
            if (comp instanceof Container) {
                list.addAll(findComponentsOfType((Container) comp, type));
            }
        }
        return list;
    }
}
