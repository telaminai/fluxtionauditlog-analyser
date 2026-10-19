package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final review of the M74 usability pass (2026-10-17) #12, an owner decision: a drafted question no longer says "(log
 * generation N)" to the person; a small muted chip by the composer says which log the drafts are about — "this log ·
 * opened 14:05" — rendering the snapshot's open log against each draft's binding (the node's, on its prime effect).
 * Headless.
 */
class BindingChipTest {

    static final long AT_1405 = LocalDateTime.of(2026, 10, 17, 14, 5).atZone(ZoneId.systemDefault()).toInstant()
            .toEpochMilli();

    /**
     * ★ M78 D1 (OD-Q.2, TQ5): the chip shows ONLY on a mismatch — it is the warning. Witnesses: show it on a match (put
     * back "this log · opened 14:05" in {@code chipText}) — the "nothing on a match" assertion fails; say the generation
     * on it — the "no generation words" assertion fails; render it for every draft (ignore the binding) — the unbound
     * draft shows it.
     */
    @Test
    @DisplayName("★ the chip only on a mismatch: nothing for the log open now; 'an earlier log' once it moved; never the generation")
    void theChipSaysWhichLogInWords() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssistantSurface s = new AssistantSurface();
            s.renderOpenLog(true, 3, AT_1405);
            s.appendDraft(AssistantMode.LOCAL_AGENT, 11, "an unbound question", null, 0);
            assertFalse(s.bindingChip.isVisible(), "a draft that names no log has no chip");

            s.appendDraft(AssistantMode.LOCAL_AGENT, 12, "What happened in record #2?", null, 3);
            assertFalse(s.bindingChip.isVisible(), "★ nothing on a match: the draft is about the log open now");

            s.renderOpenLog(true, 4, AT_1405 + 60_000);                      // the log was reloaded under the draft
            assertTrue(s.bindingChip.isVisible(), "★ the mismatch is the warning");
            assertEquals("an earlier log · Send will refuse it — ask again", s.bindingChip.getText());
            String name = s.bindingChip.getAccessibleContext().getAccessibleName();
            for (String words : new String[]{s.bindingChip.getText(), name}) {
                assertFalse(words.toLowerCase(java.util.Locale.ROOT).contains("generation"), words);
            }
            assertEquals(UiTheme.noteForeground(), s.bindingChip.getForeground());
            s.renderOpenLog(false, 0, 0);
            assertTrue(s.bindingChip.getText().startsWith("an earlier log"), "no log open: the draft's log is gone too");

            s.accepted();
            assertFalse(s.bindingChip.isVisible(), "sent: the composer holds no draft, and no chip");
        });
    }

    @Test
    @DisplayName("the chip's words, pure: nothing on a match, the warning on a mismatch")
    void chipWords() {
        assertNull(AssistantSurface.chipText(0, true, 3, AT_1405));
        assertNull(AssistantSurface.chipText(3, true, 3, 0), "M78 D1: a match shows nothing");
        assertNull(AssistantSurface.chipText(3, true, 3, AT_1405));
        assertNotNull(AssistantSurface.chipText(3, true, 4, AT_1405), "a mismatch is said");
        assertEquals("14:05", AssistantSurface.hhmm(AT_1405));
    }

    /** ★ Rule 9: the chip renders the snapshot. Witness: render it from the frame's own state instead of {@code next}. */
    @Test
    @DisplayName("★ rule 9: the frame renders the chip from the session snapshot, and places a draft with the node's binding")
    void theChipRendersTheSnapshot() throws Exception {
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertTrue(frame.contains("assistantPanel.renderOpenLog(next.logOpen(), next.logGeneration(), next.logOpenedAt())"),
                "the open log, from the snapshot");
        String prime = OneDispatchModelTest.bodyOf(frame,
                "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result primeLocalAgentDraft(");
        assertTrue(prime.contains("e.logGeneration()"), "the draft's binding, from the node's prime effect");
        assertTrue(OneDispatchModelTest.bodyOf(frame,
                "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result primeAssistantDraft(")
                .contains("e.logGeneration()"));
    }
}
