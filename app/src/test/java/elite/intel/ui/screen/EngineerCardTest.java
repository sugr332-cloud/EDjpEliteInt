package elite.intel.ui.screen;

import elite.intel.db.dao.LocationDao;
import elite.intel.db.managers.EngineerChecklistManager;
import elite.intel.db.managers.EngineerProgressManager;
import elite.intel.db.managers.QueryResultDisplayManager;
import elite.intel.db.managers.QueryResultDisplayManager.EngineersDisplayDto;
import elite.intel.i18n.Language;
import elite.intel.session.SystemSession;
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

    @Test
    void cardGeneratesHeaderInJapaneseAndEnglish() {
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            // Japanese: カタカナ（英字）（宇宙船）
            session.setLanguage(Language.JA);
            QueryResultCard cardJa = QueryResultCard.forEngineer("Felicity Farseer", null, null);
            assertEquals("フェリシティ・ファーシーア（Felicity Farseer）（宇宙船）", cardJa.getCardTitle());

            QueryResultCard cardOnFootJa = QueryResultCard.forEngineer("Domino Green", null, null);
            assertEquals("ドミノ・グリーン（Domino Green）（徒歩）", cardOnFootJa.getCardTitle());

            // English: Felicity Farseer (Ship)
            session.setLanguage(Language.EN);
            QueryResultCard cardEn = QueryResultCard.forEngineer("Felicity Farseer", null, null);
            assertEquals("Felicity Farseer (Ship)", cardEn.getCardTitle());

            QueryResultCard cardOnFootEn = QueryResultCard.forEngineer("Domino Green", null, null);
            assertEquals("Domino Green (On-foot)", cardOnFootEn.getCardTitle());
        } finally {
            session.setLanguage(orig);
        }
    }

    @Test
    void cardDisplaysJapaneseConditionsAndSpecialtiesInJapaneseMode() {
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            session.setLanguage(Language.JA);

            // Felicity Farseer
            QueryResultCard cardFelicity = QueryResultCard.forEngineer("Felicity Farseer", "Frame Shift Drive", null);
            List<JLabel> labels = findComponentsOfType(cardFelicity, JLabel.class);
            List<JCheckBox> checkBoxes = findComponentsOfType(cardFelicity, JCheckBox.class);

            // Specialties: フレームシフトドライブ（FSD）
            boolean hasFsdJa = labels.stream().anyMatch(l -> l.getText().contains("フレームシフトドライブ（FSD）"));
            assertTrue(hasFsdJa, "Specialties must display Japanese name: フレームシフトドライブ（FSD）");

            // Invite condition
            boolean hasInviteJa = checkBoxes.stream().anyMatch(cb -> cb.getText().contains("スカウト（Scout）"));
            assertTrue(hasInviteJa, "Invite condition must be in Japanese");

            // Unlock condition
            boolean hasUnlockJa = checkBoxes.stream().anyMatch(cb -> cb.getText().contains("メタアロイ（Meta-Alloys）"));
            assertTrue(hasUnlockJa, "Unlock condition must be in Japanese");

            // Terra Velasquez (On-foot with referral)
            QueryResultCard cardTerra = QueryResultCard.forEngineer("Terra Velasquez", null, null);
            List<JCheckBox> cbTerra = findComponentsOfType(cardTerra, JCheckBox.class);
            boolean hasRefJa = cbTerra.stream().anyMatch(cb -> cb.getText().contains("ジュード・ナバロ（Jude Navarro）"));
            assertTrue(hasRefJa, "Referral must display Japanese format with katakana and English name");
        } finally {
            session.setLanguage(orig);
        }
    }

    @Test
    void cardFilterButtonAppearsWhenShowFilterButtonTrueAndSavesSingleEngineer() {
        SystemSession session = SystemSession.getInstance();
        Language orig = session.getLanguage();
        try {
            session.setLanguage(Language.JA);

            // When showFilterButton = false (single engineer card or default)
            QueryResultCard singleCard = QueryResultCard.forEngineer("Felicity Farseer", null, null, false);
            assertEquals(1, singleCard.getActionButtons().size(), "Single engineer card must have only 1 action button");

            // When showFilterButton = true (multiple engineers displayed)
            QueryResultCard multiCard = QueryResultCard.forEngineer("Felicity Farseer", "Frame Shift Drive", null, true);
            List<JButton> buttons = multiCard.getActionButtons();
            assertEquals(2, buttons.size(), "Multi-engineer card must have 2 action buttons");

            JButton isolateBtn = buttons.get(0);
            assertEquals("この人に絞る", isolateBtn.getText());

            // Click the isolate button
            isolateBtn.doClick();

            // Verify QueryResultDisplayManager was updated with single engineer
            QueryResultDisplayManager mgr = QueryResultDisplayManager.getInstance();
            assertTrue(mgr.getLatest().isPresent());
            var latest = mgr.getLatest().get();
            assertEquals("engineers", latest.queryType());
            assertNotNull(latest.engineers());
            EngineersDisplayDto savedDto = latest.engineers();
            assertEquals("engineer", savedDto.queryKind());
            assertEquals(List.of("Felicity Farseer"), savedDto.engineerNames());
        } finally {
            session.setLanguage(orig);
        }
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
