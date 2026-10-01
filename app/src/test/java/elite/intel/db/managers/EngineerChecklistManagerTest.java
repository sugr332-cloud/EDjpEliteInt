package elite.intel.db.managers;

import elite.intel.ui.screen.QueryResultCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngineerChecklistManagerTest {

    private final EngineerChecklistManager manager = EngineerChecklistManager.getInstance();

    @BeforeEach
    void setUp() {
        manager.clear();
    }

    @Test
    void normalizeNameUsedForLookup() {
        // "Tod 'The Blaster' McQuinn" has quotes and mixed case
        String engName = "Tod 'The Blaster' McQuinn";
        manager.setChecked(engName, EngineerChecklistManager.ITEM_INVITE, true);

        // Can find with normalized variations
        assertTrue(manager.isChecked("tod the blaster mcquinn", EngineerChecklistManager.ITEM_INVITE));
        assertTrue(manager.isChecked("  TOD 'THE BLASTER' MCQUINN  ", EngineerChecklistManager.ITEM_INVITE));
        assertFalse(manager.isChecked(engName, EngineerChecklistManager.ITEM_UNLOCK));

        Map<String, Boolean> map = manager.getChecklistForEngineer(engName);
        assertEquals(1, map.size());
        assertTrue(map.get(EngineerChecklistManager.ITEM_INVITE));
    }

    @Test
    void setSelectedDoesNotTriggerDbSave() {
        String engName = "Felicity Farseer";
        manager.setChecked(engName, EngineerChecklistManager.ITEM_INVITE, true);

        String initialUpdatedAt = manager.getUpdatedAt(engName, EngineerChecklistManager.ITEM_INVITE);
        assertNotNull(initialUpdatedAt);

        // Create UI checkbox which wires an ActionListener
        JCheckBox cb = QueryResultCard.createChecklistCheckBox(
                engName,
                EngineerChecklistManager.ITEM_INVITE,
                "Invite text",
                true
        );

        // Calling setSelected in UI code should NOT fire ActionListener, so DB updated_at must NOT change
        cb.setSelected(false);
        String afterSetSelectedFalse = manager.getUpdatedAt(engName, EngineerChecklistManager.ITEM_INVITE);
        assertEquals(initialUpdatedAt, afterSetSelectedFalse, "setSelected must not update DB");
        assertTrue(manager.isChecked(engName, EngineerChecklistManager.ITEM_INVITE), "DB status must remain true");

        cb.setSelected(true);
        String afterSetSelectedTrue = manager.getUpdatedAt(engName, EngineerChecklistManager.ITEM_INVITE);
        assertEquals(initialUpdatedAt, afterSetSelectedTrue, "setSelected must not update DB");

        // Simulating a real user click (doClick) DOES trigger ActionListener
        cb.doClick();
        String afterDoClick = manager.getUpdatedAt(engName, EngineerChecklistManager.ITEM_INVITE);
        assertNotNull(afterDoClick);
        assertFalse(manager.isChecked(engName, EngineerChecklistManager.ITEM_INVITE), "User click must update DB");
    }
}
