package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * Review M74 usability #7 (2026-10-17), on real dialogs: the approval dialog, full Claude Code's folder trust and Safe
 * analysis's folder confirmation share one button order and one default. In each, the safe choice (Deny, Don't trust)
 * is the default button and the right-most; the risky one (Allow once, Trust…) the left-most, with room between; Escape
 * chooses the safe one. Before: Trust sat where Deny sits in the approval dialog, and the Safe question's default was Yes.
 */
class SafeChoiceFrameTest {

    static List<AbstractButton> buttons(Container root) {
        List<AbstractButton> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && b.isShowing()) out.add(b);
            if (c instanceof Container k) out.addAll(buttons(k));
        }
        return out;
    }

    /** The rule, measured on the screen: safe right-most and default, risky left-most, not adjacent; Escape is safe. */
    static void assertTheRule(JDialog d, String safe, String risky) {
        List<AbstractButton> row = buttons(d.getContentPane());
        row.sort(Comparator.comparingInt(b -> SwingUtilities.convertPoint(b, new Point(0, 0), d.getContentPane()).x));
        AbstractButton first = row.get(0), last = row.get(row.size() - 1);
        assertEquals(safe, last.getText(), d.getTitle() + ": the safe choice is the right-most: " + texts(row));
        assertEquals(risky, first.getText(), d.getTitle() + ": the risky choice is the left-most");
        assertSame(last, d.getRootPane().getDefaultButton(), d.getTitle() + ": and the safe choice is the default");
        assertFalse(((JButton) first).isDefaultCapable(), d.getTitle() + ": the risky choice can never be the default");
        int riskyRight = SwingUtilities.convertPoint(first, new Point(first.getWidth(), 0), d.getContentPane()).x;
        int nextLeft = SwingUtilities.convertPoint(row.get(1), new Point(0, 0), d.getContentPane()).x;
        assertTrue(nextLeft - riskyRight >= UiTheme.SPACE_LG, d.getTitle() + ": the risky choice is never adjacent ("
                + (nextLeft - riskyRight) + " px)");
        Object esc = d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertNotNull(esc, d.getTitle() + ": Escape is bound");
        d.getRootPane().getActionMap().get(esc).actionPerformed(new ActionEvent(d.getRootPane(), 0, "esc"));
    }

    static List<String> texts(List<AbstractButton> row) {
        return row.stream().map(AbstractButton::getText).toList();
    }

    /**
     * ★ Witness: build the full-folder dialog with JOptionPane options {TRUST, DONT} as before — Trust is left of Don't
     * trust but the default is chosen by the look and feel; or the Safe dialog as a stock YES_NO — its default is Yes and
     * the first assertion on it fails.
     */
    @Test
    @DisplayName("★ approval, full folder trust and Safe folder confirmation: the same order, the same safe default")
    void oneOrderOneDefault(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        AtomicReference<Boolean> denied = new AtomicReference<>();
        onEdt(() -> {
            var content = PermissionText.of(new LocalAgentTranscript.Permission("mcp__x__analyser_walk", "x", "walk",
                    "{\"delete\":true,\"name\":\"DEMO first breach\"}"), ToolClass.HOSTED_DESTRUCTIVE, "walk");
            LocalAgentPermissionDialog p = new LocalAgentPermissionDialog(null, content, 120_000,
                    new LocalAgentPermissionDialog.Answers() {
                        @Override public void chosen(boolean allow) { denied.set(!allow); }
                        @Override public void stop() { }
                    }, false);
            p.setVisible(true);
            p.allowDelayElapsed();
            assertTheRule(p, LocalAgentPermissionDialog.DENY, LocalAgentPermissionDialog.ALLOW);
            p.close();

            SafeChoice.Asked full = LocalAgentFolderDialog.fullDialog(null, tmp, FullFolderTrust.scan(tmp));
            full.dialog().setVisible(true);
            assertTheRule(full.dialog(), LocalAgentFolderDialog.DONT, LocalAgentFolderDialog.TRUST);
            assertFalse(full.chosenRisky(), "Escape chose Don't trust");
            full.dialog().dispose();

            SafeChoice.Asked safe = LocalAgentFolderDialog.safeDialog(null, tmp, true);
            safe.dialog().setVisible(true);
            assertTheRule(safe.dialog(), LocalAgentFolderDialog.DONT, LocalAgentFolderDialog.SAFE_TRUST);
            assertFalse(safe.chosenRisky(), "Escape chose Don't trust");
            assertTrue(LocalAgentFolderDialog.safeText(tmp, true).startsWith("This is the analyser's own private working "
                    + "folder"), "an analyser-owned folder is said in words before its path");
            safe.dialog().dispose();
        });
        assertEquals(Boolean.TRUE, denied.get(), "Escape in the approval dialog denied");
    }
}
