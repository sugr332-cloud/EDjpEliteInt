package elite.intel.ui.screen;

import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerChecklistManager;
import elite.intel.db.managers.EngineerProgressManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineerCardTest {

    @BeforeEach
    void setUp() {
        EngineerChecklistManager.getInstance().clear();
        EngineerProgressManager.getInstance().clear();
    }

    @Test
    void cardGeneratesForShipEngineerWithoutReferral() {
        // Felicity Farseer is a ship engineer (no referral)
        QueryResultCard card = QueryResultCard.forEngineer("Felicity Farseer", "Frame Shift Drive", new LocationDao.Coordinates("Sol", 0.0, 0.0, 0.0));
        assertNotNull(card);
        assertTrue(card.getCardTitle().contains("Felicity Farseer"));

        List<JCheckBox> checkBoxes = findComponentsOfType(card, JCheckBox.class);
        // Should have invite and unlock, but NEVER referral_task
        assertFalse(checkBoxes.isEmpty());
        for (JCheckBox cb : checkBoxes) {
            assertFalse(cb.getText().contains("紹介作業") || cb.getText().contains("Referral Task"),
                    "Ship engineer must not have referral_task checkbox");
        }
    }

    @Test
    void cardGeneratesReferralTaskOnlyForOnfootWithReferral() {
        // Terra Velasquez is onfoot and has referral ("Jude Navarro")
        QueryResultCard cardWithRef = QueryResultCard.forEngineer("Terra Velasquez", null, null);
        assertNotNull(cardWithRef);

        List<JCheckBox> checkBoxes = findComponentsOfType(cardWithRef, JCheckBox.class);
        boolean hasReferral = checkBoxes.stream()
                .anyMatch(cb -> cb.getText().contains("紹介作業") || cb.getText().contains("Referral Task"));
        assertTrue(hasReferral, "Onfoot engineer with referral must have referral_task checkbox");

        // Domino Green is onfoot but referral is empty/null in engineers.json
        QueryResultCard cardWithoutRef = QueryResultCard.forEngineer("Domino Green", null, null);
        List<JCheckBox> cbDomino = findComponentsOfType(cardWithoutRef, JCheckBox.class);
        boolean hasRefDomino = cbDomino.stream()
                .anyMatch(cb -> cb.getText().contains("紹介作業") || cb.getText().contains("Referral Task"));
        assertFalse(hasRefDomino, "Onfoot engineer without referral must not have referral_task checkbox");
    }

    @Test
    void autoProgressStageDisplay() {
        // 1. No record
        QueryResultCard cardNoRec = QueryResultCard.forEngineer("Elvira Martuuk", null, null);
        List<JLabel> labelsNoRec = findComponentsOfType(cardNoRec, JLabel.class);
        assertTrue(labelsNoRec.stream().anyMatch(l -> l.getText().contains("記録なし") || l.getText().contains("No Record")));

        // 2. Barred
        EngineerProgressManager.getInstance().recordProgress("Elvira Martuuk", null, "Barred", null, null, "2026-10-01T10:00:00Z");
        QueryResultCard cardBarred = QueryResultCard.forEngineer("Elvira Martuuk", null, null);
        List<JLabel> labelsBarred = findComponentsOfType(cardBarred, JLabel.class);
        assertTrue(labelsBarred.stream().anyMatch(l -> l.getText().contains("Barred") || l.getText().contains("出入り禁止")));

        // 3. Unlocked with Rank 5
        EngineerProgressManager.getInstance().recordProgress("Elvira Martuuk", null, "Unlocked", 5, 45, "2026-10-01T11:00:00Z");
        QueryResultCard cardUnlocked = QueryResultCard.forEngineer("Elvira Martuuk", null, null);
        List<JLabel> labelsUnlocked = findComponentsOfType(cardUnlocked, JLabel.class);
        // Should have checked markers for all stages
        assertTrue(labelsUnlocked.stream().anyMatch(l -> l.getText().startsWith("☑") && (l.getText().contains("知っている") || l.getText().contains("Known"))));
        assertTrue(labelsUnlocked.stream().anyMatch(l -> l.getText().startsWith("☑") && (l.getText().contains("招待済み") || l.getText().contains("Invited"))));
        assertTrue(labelsUnlocked.stream().anyMatch(l -> l.getText().startsWith("☑") && (l.getText().contains("面識あり") || l.getText().contains("Acquainted"))));
        assertTrue(labelsUnlocked.stream().anyMatch(l -> l.getText().startsWith("☑") && (l.getText().contains("開放済み") || l.getText().contains("Unlocked"))));
        assertTrue(labelsUnlocked.stream().anyMatch(l -> l.getText().startsWith("☑") && l.getText().contains("5") && l.getText().contains("45%")));
    }

    @Test
    void manualChecklistToggleUpdatesStyleAndDb() {
        String engName = "Felicity Farseer";
        QueryResultCard card = QueryResultCard.forEngineer(engName, null, null);
        List<JCheckBox> checkBoxes = findComponentsOfType(card, JCheckBox.class);
        assertFalse(checkBoxes.isEmpty());

        JCheckBox firstCb = checkBoxes.get(0);
        assertFalse(firstCb.isSelected());

        // Click to toggle
        firstCb.doClick();
        assertTrue(firstCb.isSelected());
        assertTrue(firstCb.getText().contains("<strike"), "Checked box must have strike-through HTML style");
        assertTrue(EngineerChecklistManager.getInstance().isChecked(engName, EngineerChecklistManager.ITEM_INVITE));

        // Click again to uncheck
        firstCb.doClick();
        assertFalse(firstCb.isSelected());
        assertFalse(firstCb.getText().contains("<strike"));
        assertFalse(EngineerChecklistManager.getInstance().isChecked(engName, EngineerChecklistManager.ITEM_INVITE));
    }

    @Test
    void cardGeneratesStationButtonAndDisablesTemporarilyOnClick() {
        QueryResultCard card = QueryResultCard.forEngineer("Felicity Farseer", null, null);
        assertNotNull(card);
        List<JButton> buttons = card.getActionButtons();
        assertNotNull(buttons);
        assertEquals(1, buttons.size(), "Engineer card must have exactly one action button");

        JButton btn = buttons.get(0);
        String txt = btn.getText().toLowerCase();
        assertTrue(btn.getText().contains("ステーションへ発進") || txt.contains("plot to station"),
                "Button text was: " + btn.getText());
        assertTrue(btn.isEnabled());

        // Click disables button
        btn.doClick();
        assertFalse(btn.isEnabled(), "Button must be temporarily disabled after click");
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
