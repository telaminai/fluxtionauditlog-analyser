package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #7 (2026-10-17), headless: the one button rule every consent dialog shares — the risky choice
 * alone at the left, the safe choice last at the right (the default), anything else beside the safe one. Where the three
 * dialogs are built and shown is checked on a real frame by {@code SafeChoiceFrameTest}.
 */
class SafeChoiceTest {

    /** ★ Witness: add the buttons in call order (risky, then safe, side by side) — risky is adjacent and this fails. */
    @Test
    @DisplayName("★ the row: risky alone at the left, never default-capable and with no mnemonic; the safe one last")
    void theRow() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            JButton risky = new JButton("Allow once");
            risky.setMnemonic('A');
            JButton safe = new JButton("Deny");
            JButton stop = new JButton("Stop Claude Code");
            JPanel row = SafeChoice.buttonRow(risky, safe, stop);
            JPanel left = (JPanel) risky.getParent();
            JPanel right = (JPanel) safe.getParent();
            assertNotSame(left, right, "the risky choice is never beside the safe one");
            assertEquals(1, left.getComponentCount(), "it stands alone");
            Component[] r = right.getComponents();
            assertSame(safe, r[r.length - 1], "the safe choice is the last, at the right end");
            assertSame(stop, r[0], "anything else sits beside the safe one");
            assertFalse(risky.isDefaultCapable(), "Enter can never mean the risky choice");
            assertEquals(0, risky.getMnemonic(), "nor a mnemonic");
            assertFalse(stop.isDefaultCapable());
            assertSame(row, left.getParent());
            assertEquals("safe", safe.getClientProperty(SafeChoice.ROLE));
            return null;
        });
    }

    /** Every consent dialog of the assistant goes through the rule: no stock Yes/No is left for a folder or an approval. */
    @Test
    @DisplayName("the approval, full-folder-trust and Safe folder dialogs are built by the rule, not a stock Yes/No")
    void theThreeDialogsUseTheRule() throws Exception {
        String folder = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/LocalAgentFolderDialog.java"));
        assertTrue(folder.contains("SafeChoice.ask(parent, FULL_TITLE"));
        assertTrue(folder.contains("SafeChoice.ask(parent, SAFE_TITLE"));
        assertFalse(folder.contains("JOptionPane"), "no stock dialog: its default and its order are the look and feel's");
        String perm = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/LocalAgentPermissionDialog.java"));
        assertTrue(perm.contains("SafeChoice.buttonRow(allow, deny, stop)"));
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertFalse(frame.contains("\"Claude Code — confirm this folder\", JOptionPane.YES_NO_OPTION"),
                "the Safe folder question is no longer a Yes/No whose default is Yes");
        assertTrue(frame.contains("LocalAgentFolderDialog.confirmSafe("));
        assertEquals("Don't trust", LocalAgentFolderDialog.DONT);
        assertEquals("Trust this folder", LocalAgentFolderDialog.SAFE_TRUST);
    }
}
