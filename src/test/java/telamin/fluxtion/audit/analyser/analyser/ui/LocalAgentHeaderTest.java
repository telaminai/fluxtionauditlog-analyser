package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M74 review S5 — the Claude Code header: the session's own launch folder, and nothing before the session is bound. */
class LocalAgentHeaderTest {

    static LocalAgentState state(String phase, long headerRef) {
        return state(phase, headerRef, "", "", 0, false);
    }

    static LocalAgentState state(String phase, long headerRef, String verdict, String reason, long openTicket,
                                 boolean resumable) {
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, phase, verdict, reason, openTicket, 0, headerRef, 1, 0,
                0, 0, 0, 0, false, false, resumable, 0, List.of(), LocalAgentState.Answer.NONE);
    }

    /** ★ Witness: put the folder back on the header's first line — the "its own line" assertion fails. */
    @Test
    @DisplayName("★ the folder has its own line; before the first turn the configured model is shown as such")
    void folderLineAndConfiguredModel() {
        var h = new LocalAgentTranscript.Header(null, null, "<subscription>", "firstParty", List.of(), "/work/proj", "haiku");
        String line = LocalAgentPanel.headerLine(h, state("READY", 1));
        String[] lines = line.split("\n");
        assertEquals(2, lines.length, line);
        assertEquals("Folder: /work/proj", lines[1], "the folder on its own line");
        assertTrue(lines[0].contains("model: haiku (confirmed after the first turn)"), line);
        var confirmed = h.withInit("claude-haiku-4-5", "2.1.282");
        String after = LocalAgentPanel.headerLine(confirmed, state("READY", 1));
        assertTrue(after.contains("claude-haiku-4-5") && !after.contains("confirmed after"), after);
    }

    /** ★ Witness: label Start "(resume)" whatever the phase — the running and starting assertions fail. */
    @Test
    @DisplayName("★ Start says what it would do: Start, Resume, Starting…, Running…")
    void startLabels() {
        assertEquals("Start Claude Code", LocalAgentPanel.startLabel(state("OFF", 0)));
        assertEquals("Resume Claude Code", LocalAgentPanel.startLabel(state("OFF", 0, "", "", 0, true)));
        assertEquals("Starting…", LocalAgentPanel.startLabel(state("BINDING", 0, "", "", 0, true)));
        assertEquals("Running…", LocalAgentPanel.startLabel(state("READY", 1, "", "", 0, true)));
        assertEquals("Running…", LocalAgentPanel.startLabel(state("BUSY", 1, "", "", 3, true)));
    }

    /** ★ Witness: drop the single-sentence case in {@code phaseText} — the phase and the refusal both appear. */
    @Test
    @DisplayName("★ a busy refusal is one sentence on the status line")
    void busyIsOneSentence() {
        String line = LocalAgentPanel.phaseText(state("BUSY", 1, "BUSY", "Claude is still answering — Stop or wait.", 3, true));
        assertEquals("Claude is still answering — Stop or wait.", line);
        assertEquals("Claude is answering…", LocalAgentPanel.phaseText(state("BUSY", 1, "", "", 3, true)));
    }

    /** ★ Witness: make {@code WrapLine} a plain one-line label (no wrapping) — the narrow height does not grow. */
    @Test
    @DisplayName("★ the header, posture, lock and status lines wrap to the tab's width instead of ending in …")
    void linesWrap() throws Exception {
        javax.swing.JPanel parent = new javax.swing.JPanel(new java.awt.BorderLayout());
        WrapLine w = new WrapLine(LocalAgentPanel.LOCK_WARNING + " " + LocalAgentPanel.DEFAULT_POSTURE_NOTE);
        parent.add(w);
        parent.setSize(2_000, 400);
        int wide = w.getPreferredSize().height;
        parent.setSize(220, 400);
        int narrow = w.getPreferredSize().height;
        assertTrue(narrow >= 3 * wide, "wraps at a narrow width: " + wide + " → " + narrow);
        assertEquals(LocalAgentPanel.LOCK_WARNING + " " + LocalAgentPanel.DEFAULT_POSTURE_NOTE, w.getText(), "nothing cut");
        for (String name : java.util.List.of("header", "posture", "lockWarning", "status")) {
            var f = LocalAgentPanel.class.getDeclaredField(name);
            assertEquals(WrapLine.class, f.getType(), name + " wraps");
        }
    }

    /** ★ Witness: force 110 px on every wrapped component again — the row's natural width is lost. */
    @Test
    @DisplayName("★ Settings: a row wrapped by leftWrap keeps its natural width (the Max actions row was clipped)")
    void leftWrapKeepsARowsWidth() {
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        row.add(new javax.swing.JSpinner());
        row.add(new javax.swing.JLabel("per turn, across its rounds:"));
        row.add(new javax.swing.JSpinner());
        int natural = row.getPreferredSize().width;
        var wrapped = ConfigPanel.leftWrap(row);
        assertTrue(wrapped.getPreferredSize().width >= natural, natural + " vs " + wrapped.getPreferredSize().width);
        javax.swing.JSpinner single = new javax.swing.JSpinner();
        ConfigPanel.leftWrap(single);
        assertEquals(110, single.getPreferredSize().width, "a single field keeps its fixed width");
    }

    /** ★ Witness: return the header in {@code shownHeader} whatever the phase — the BINDING assertion fails. */
    @Test
    @DisplayName("★ S5: no header details before BOUND; once bound, the launch folder from the session's own record")
    void theHeaderIsTheSessionsOwn() {
        LocalAgentTranscript store = new LocalAgentTranscript();
        long ref = store.putHeader(new LocalAgentTranscript.Header("claude-sonnet-5", "2.1.282", "<subscription>",
                "firstParty", List.of(), "/work/launched-here"));
        for (String phase : List.of("SPAWNING", "HANDSHAKE", "BINDING")) {
            assertNull(LocalAgentPanel.shownHeader(state(phase, ref), store), phase);
            assertFalse(LocalAgentPanel.headerLine(null, state(phase, ref)).contains("launched-here"));
        }
        var shown = LocalAgentPanel.shownHeader(state("READY", ref), store);
        String line = LocalAgentPanel.headerLine(shown, state("READY", ref));
        assertTrue(line.contains("/work/launched-here"), line);
        assertTrue(line.contains("CLI 2.1.282") && line.contains("<subscription>") && line.contains("firstParty"), line);
    }
}
