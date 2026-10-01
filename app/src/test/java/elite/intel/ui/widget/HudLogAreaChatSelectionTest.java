package elite.intel.ui.widget;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudLogAreaChatSelectionTest {

    @Test
    void chatModeAllowsMouseDragSelectionAndCopying() throws Exception {
        HudLogArea area = HudLogArea.chat(0);
        area.setSize(new Dimension(800, 600));

        area.addMessage("こんにちは", HudLogArea.Style.USER_INPUT, HudLogArea.Align.LEFT);
        area.addMessage("了解しました。何かご用ですか？", HudLogArea.Style.AI_RESPONSE, HudLogArea.Align.RIGHT);

        List<HudLogArea.RenderedLine> lines = area.refreshLines();
        assertFalse(lines.isEmpty(), "Lines must not be empty");
        assertTrue(lines.size() >= 4, "Should have at least 2 timestamp lines and 2 body lines");

        // First message body line is line 1
        HudLogArea.RenderedLine firstBody = lines.stream()
                .filter(l -> l.messageIndex() == 0 && l.lineIndex() == 1)
                .findFirst().orElseThrow();
        assertEquals("こんにちは", firstBody.text());

        // Second message body line is line 1 of message 1
        HudLogArea.RenderedLine secondBody = lines.stream()
                .filter(l -> l.messageIndex() == 1 && l.lineIndex() == 1)
                .findFirst().orElseThrow();
        assertTrue(secondBody.text().contains("了解しました"));

        // Mouse press at firstBody start
        int pressX = firstBody.x() + 2;
        int pressY = firstBody.topY() + 4;
        MouseEvent press = new MouseEvent(area, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, pressX, pressY, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(press);

        // Mouse drag to secondBody end
        int dragX = secondBody.x() + 300;
        int dragY = secondBody.topY() + 4;
        MouseEvent drag = new MouseEvent(area, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, dragX, dragY, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(drag);

        MouseEvent release = new MouseEvent(area, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, dragX, dragY, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(release);

        assertTrue(area.hasSelectedText(), "Should have selected text after drag");

        String selected = area.getSelectedText();
        assertNotNull(selected);
        assertTrue(selected.contains("こんにちは"), "Selected text must contain first message: " + selected);
        assertTrue(selected.contains("了解しました"), "Selected text must contain second message: " + selected);

        // Test copySelectedText
        area.copySelectedText();
        try {
            String clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (clip != null) {
                assertEquals(selected, clip);
            }
        } catch (Exception ignored) {
            // Headless / clipboard unavailable
        }
    }

    @Test
    void promptRowNotIncludedInCopy() {
        HudLogArea area = HudLogArea.chat(0);
        area.setSize(new Dimension(800, 600));

        area.addMessage("司令官の発言", HudLogArea.Style.USER_INPUT, HudLogArea.Align.LEFT);

        List<HudLogArea.RenderedLine> lines = area.refreshLines();
        for (HudLogArea.RenderedLine line : lines) {
            assertFalse(line.text().contains("Prompt") || line.text().equals(">"),
                    "Bottom prompt must not be in refreshLines: " + line.text());
        }
    }

    @Test
    void selectionPreservedWhenNewMessageAppendedUnderLimit() {
        HudLogArea area = HudLogArea.chat(0);
        area.setSize(new Dimension(800, 600));

        area.addMessage("メッセージ1", HudLogArea.Style.USER_INPUT, HudLogArea.Align.LEFT);
        List<HudLogArea.RenderedLine> lines = area.refreshLines();
        HudLogArea.RenderedLine body = lines.get(1);

        // Select message 1
        MouseEvent press = new MouseEvent(area, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, body.x(), body.topY() + 2, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(press);
        MouseEvent drag = new MouseEvent(area, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, body.x() + 100, body.topY() + 2, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(drag);

        assertTrue(area.hasSelectedText());

        // Append new message (total 2 <= MAX_MESSAGES = 40)
        area.addMessage("メッセージ2", HudLogArea.Style.AI_RESPONSE, HudLogArea.Align.RIGHT);

        // Selection should remain preserved
        assertTrue(area.hasSelectedText(), "Selection must be preserved when under MAX_MESSAGES limit");
        assertTrue(area.getSelectedText().contains("メッセージ1"));
    }

    @Test
    void selectionClearedWhenMessagesTrimmedOverLimit() {
        HudLogArea area = HudLogArea.chat(0);
        area.setSize(new Dimension(800, 600));

        // Add 40 messages (up to MAX_MESSAGES)
        for (int i = 0; i < 40; i++) {
            area.addMessage("メッセージ " + i, HudLogArea.Style.USER_INPUT, HudLogArea.Align.LEFT);
        }

        List<HudLogArea.RenderedLine> lines = area.refreshLines();
        HudLogArea.RenderedLine line = lines.get(1);

        // Select something
        MouseEvent press = new MouseEvent(area, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, line.x(), line.topY() + 2, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(press);
        MouseEvent drag = new MouseEvent(area, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, line.x() + 50, line.topY() + 2, 1, false, MouseEvent.BUTTON1);
        area.dispatchEvent(drag);

        assertTrue(area.hasSelectedText());

        // Adding 41st message causes oldest to be trimmed
        area.addMessage("メッセージ 40", HudLogArea.Style.USER_INPUT, HudLogArea.Align.LEFT);

        // Selection must be cleared because message indices shifted
        assertFalse(area.hasSelectedText(), "Selection must be cleared when oldest message is trimmed");
    }

    @Test
    void typewriterAnimationDoesNotThrowDuringSelection() {
        // Delay > 0 simulates ongoing typewriter animation
        HudLogArea area = HudLogArea.chat(100);
        area.setSize(new Dimension(800, 600));

        assertDoesNotThrow(() -> {
            area.addMessage("アニメーション中の長いテキストテストです。", HudLogArea.Style.AI_RESPONSE, HudLogArea.Align.RIGHT);
            List<HudLogArea.RenderedLine> lines = area.refreshLines();
            if (!lines.isEmpty()) {
                HudLogArea.RenderedLine l = lines.get(lines.size() - 1);
                MouseEvent press = new MouseEvent(area, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        MouseEvent.BUTTON1_DOWN_MASK, l.x(), l.topY() + 2, 1, false, MouseEvent.BUTTON1);
                area.dispatchEvent(press);
                MouseEvent drag = new MouseEvent(area, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                        MouseEvent.BUTTON1_DOWN_MASK, l.x() + 200, l.topY() + 2, 1, false, MouseEvent.BUTTON1);
                area.dispatchEvent(drag);
                area.getSelectedText();
                area.copySelectedText();
            }
        });
    }
}
