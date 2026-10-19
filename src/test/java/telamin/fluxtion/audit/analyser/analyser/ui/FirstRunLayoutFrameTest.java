package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * Robot re-walk #6 (2026-10-18), in the REAL frame with a FAKE Claude Code and an isolated home: the In-app bullets
 * overlapped, the Full bullets were cut, the restart note was cut and read "Stopped:" for a restart the person asked for,
 * and the Safe status paragraph said "Send starts Claude Code" twice. Every check runs at the default 1200×800 with the
 * assistant docked, and again popped out at its own width ({@link #POP_OUT_WIDTH}); what "no clipped text" means is
 * {@link TextFit}'s.
 */
class FirstRunLayoutFrameTest {

    /** The assistant window's width when it first pops out (the robot's 520×720). */
    static final int POP_OUT_WIDTH = 520;

    static void select(MainFrame f, String tab) throws Exception {
        onEdt(() -> {
            JTabbedPane tabs = (JTabbedPane) field(f, "sideTabs");
            tabs.setSelectedIndex(tabs.indexOfTab(tab));
        });
        settle();
    }

    /** Two passes of the event queue: a layout queued by a layout has run. */
    static void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            onEdt(() -> { });
            Thread.sleep(60);
        }
    }

    /** Pop the assistant out, at the pop-out width, and wait until its window shows it laid out. */
    static JFrame popOut(MainFrame f) throws Exception {
        AssistantPanel a = (AssistantPanel) field(f, "assistantPanel");
        onEdt(() -> a.hostButton().doClick());
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        JFrame w = null;
        while (w == null && System.nanoTime() < until) {
            for (Window x : Window.getWindows()) {
                if (x instanceof JFrame j && j != f && j.isShowing() && AssistantPanel.WINDOW_TITLE.equals(j.getTitle())) w = j;
            }
            Thread.sleep(40);
        }
        assertNotNull(w, "the assistant popped out");
        JFrame shown = w;
        onEdt(() -> { shown.setSize(POP_OUT_WIDTH, 720); shown.validate(); });
        settle();
        return shown;
    }

    static void assertFits(JComponent root, String where) throws Exception {
        List<String>[] found = new List[1];
        onEdt(() -> found[0] = TextFit.problems(root));
        assertTrue(found[0].isEmpty(), "★ clipped text " + where + ":\n  " + String.join("\n  ", found[0]));
    }

    /**
     * ★ The Safe status paragraph after Apply with a question placed: "Send starts Claude Code" once, and the whole panel
     * shows its text whole, docked and popped out. Witness: put back {@code "…; Send starts Claude Code and asks it. " +
     * CONTACTS_ANTHROPIC} — the count is 2.
     */
    @Test
    @DisplayName("★ Safe analysis with a question placed: one \"Send starts Claude Code\", and no clipped text docked or popped out")
    void safeStatusSaysItOnceAndFits(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (AssistantFirstRunFrameTest.Fresh f = new AssistantFirstRunFrameTest.Fresh(tmp, 1200, 800)) {
            f.openSampleLog(tmp);
            f.askFromTheRecord();
            f.applyProfile(AssistantProfile.SAFE);
            select(f.frame, "Analyser assistant");
            String line = ClaudeCodeTabFrameTest.state(f.frame).extra().guide().instruction();
            assertEquals(LocalAgentSession.PLACED_NOT_RUNNING_TEXT, line, "the question is placed and nothing runs");
            assertEquals(1, AssistantFirstRunFrameTest.occurrences(line, "Send starts Claude Code"),
                    "★ said once: " + line);
            assertFits(f.assistant(), "in the docked assistant at 1200×800");
            popOut(f.frame);
            assertFits(f.assistant(), "in the assistant popped out at " + POP_OUT_WIDTH);
        }
    }

    /**
     * ★ The restart the person asked for: its note reads "Restarted with the new settings", with no "Stopped:" label,
     * and is shown whole docked and popped out. Witness: give the restart's {@code kill} the STOPPED status — the note
     * starts "Stopped:".
     */
    @Test
    @DisplayName("★ after Yes, restart: the note says \"Restarted with the new settings\" (no \"Stopped:\") and is not cut")
    void restartNoteIsCalmAndWhole(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (AssistantSettingsTogglesFrameTest.Session s = new AssistantSettingsTogglesFrameTest.Session(tmp, false)) {
            onEdt(() -> { s.frame.setSize(1200, 800); s.frame.validate(); });
            s.settings(c -> c.claudeCodeModel = "opus");
            onEdt(() -> s.panel.restartYesButton().doClick());
            ClaudeCodeTabFrameTest.awaitState(s.frame, x -> "READY".equals(x.phase()) && x.generation() == 2,
                    "restarted and ready");
            LocalAgentState st = s.state();
            LocalAgentState.Entry note = st.entries().stream()
                    .filter(e -> LocalAgentSession.RESTARTED_TEXT.equals(e.detail())).findFirst().orElseThrow();
            assertEquals(LocalAgentSession.RESTARTED, note.status(), "★ a restart, not a stop");
            select(s.frame, "Analyser assistant");
            AssistantPanel a = (AssistantPanel) field(s.frame, "assistantPanel");
            String[] shown = new String[1];
            onEdt(() -> shown[0] = a.conversationView().getText());
            assertTrue(shown[0].contains(LocalAgentSession.RESTARTED_TEXT), shown[0]);
            assertFalse(shown[0].contains("Stopped: " + LocalAgentSession.RESTARTED_TEXT), "★ no alarm label: " + shown[0]);
            assertFits(a, "in the docked assistant at 1200×800");
            popOut(s.frame);
            assertFits(a, "in the assistant popped out at " + POP_OUT_WIDTH);
        }
    }
}
