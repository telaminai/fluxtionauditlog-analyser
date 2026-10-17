package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

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

    static LocalAgentState billed(SessionEvents.Billing billing, long spent, long budget, boolean under,
                                  LocalAgentPosture posture, int servers) {
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, "READY", "", "", 0, 1, 1, 1, 0, 0, 0, spent, budget,
                under, false, true, 0, List.of(), LocalAgentState.Answer.NONE, posture, servers, billing);
    }

    /**
     * ★ owner, 2026-10-17: a cost only where it means something. Witness: show the dollar figure whatever the billing
     * (P1a) — the subscription line carries "US$" and the first assertion fails; and "of US$0.00" with no limit.
     */
    @Test
    @DisplayName("★ the header's cost by billing: a plan shows no dollars; an API key shows used, of the limit when one is set")
    void costWordingPerBilling() {
        var a = LocalAgentPosture.ANALYSER_ASSISTANT;
        String plan = LocalAgentPanel.costText(billed(SessionEvents.Billing.SUBSCRIPTION, 1_230_000, 5_000_000, false, a, 0));
        assertEquals(LocalAgentPanel.PLAN_USAGE, plan, "on a Claude plan: no dollar figure, even with a limit set");
        assertFalse(plan.contains("US$"));
        assertEquals("US$1.23 used", LocalAgentPanel.costText(billed(SessionEvents.Billing.API_KEY, 1_230_000, 0, false, a, 0)));
        assertEquals("at least US$1.23 of US$5.00",
                LocalAgentPanel.costText(billed(SessionEvents.Billing.API_KEY, 1_230_000, 5_000_000, true, a, 0)));
        assertEquals("", LocalAgentPanel.costText(billed(SessionEvents.Billing.UNKNOWN, 1_230_000, 0, false, a, 0)),
                "billing unknown and no limit: nothing");
        var h = new LocalAgentTranscript.Header("claude-sonnet-5", "2.1.282", "<subscription>", "firstParty", List.of(),
                "/w");
        String line = LocalAgentPanel.headerLine(h, billed(SessionEvents.Billing.SUBSCRIPTION, 1_230_000, 0, false, a, 0));
        assertTrue(line.contains("usage counts against your Claude plan") && !line.contains("US$"), line);
    }

    /** ★ §5.2: the full posture's lines. Witness: render the default posture's note for both — the full assertion fails. */
    @Test
    @DisplayName("★ the posture line: the mode, n MCP servers loaded, and exactly what was appended to PATH")
    void theFullPostureLines() {
        var full = LocalAgentPosture.full(LocalAgentPosture.FullMode.ACCEPT_EDITS);
        var h = new LocalAgentTranscript.Header("claude-sonnet-5", "2.1.282", "<subscription>", "firstParty", List.of(),
                "/w", "sonnet", List.of("~/.local/bin", "/opt/homebrew/bin"));
        String line = LocalAgentPanel.postureLine(h, billed(SessionEvents.Billing.SUBSCRIPTION, 0, 0, false, full, 3));
        assertEquals("full\u00A0Claude\u00A0Code\u00A0(acceptEdits) — your settings, skills, hooks and 3 MCP servers are loaded\n"
                + "PATH: inherited, plus ~/.local/bin, /opt/homebrew/bin", line);
        assertTrue(LocalAgentPanel.headerLine(h, billed(SessionEvents.Billing.SUBSCRIPTION, 0, 0, false, full, 3))
                .contains("full\u00A0Claude\u00A0Code\u00A0(acceptEdits)"));
        assertEquals(LocalAgentPanel.DEFAULT_POSTURE_NOTE, LocalAgentPanel.postureLine(h,
                billed(SessionEvents.Billing.SUBSCRIPTION, 0, 0, false, LocalAgentPosture.ANALYSER_ASSISTANT, 0)));
        String none = LocalAgentPanel.postureLine(new LocalAgentTranscript.Header(null, null, "", null, List.of(), "/w"),
                billed(SessionEvents.Billing.SUBSCRIPTION, 0, 0, false, full, 1));
        assertTrue(none.endsWith("hooks and 1 MCP server are loaded\nPATH: inherited"), none);
        String zero = LocalAgentPanel.postureLine(new LocalAgentTranscript.Header(null, null, "", null, List.of(), "/w"),
                billed(SessionEvents.Billing.SUBSCRIPTION, 0, 0, false, full, 0));
        assertTrue(zero.contains("your settings, skills and hooks are loaded, and none of your MCP servers"), zero);
        assertFalse(zero.contains("0 MCP"), zero);
    }

    /** M74 visual check. Witness: the plain label (no no-break spaces) — "(default)" wraps onto the next row. */
    @Test
    @DisplayName("the header never breaks inside the posture's name")
    void thePostureNameStaysWhole() throws Exception {
        var full = LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT);
        var h = new LocalAgentTranscript.Header(null, null, "<plan>", "firstParty", List.of(), "/w", "haiku");
        String header = LocalAgentPanel.headerLine(h, billed(SessionEvents.Billing.SUBSCRIPTION, 0, 0, false, full, 0));
        javax.swing.JPanel parent = new javax.swing.JPanel(new java.awt.BorderLayout());
        WrapLine w = new WrapLine(header);
        parent.add(w);
        String label = "full\u00A0Claude\u00A0Code\u00A0(default)";
        int at = header.indexOf(label);
        assertTrue(at > 0, header);
        for (int width = 200; width <= 520; width += 10) {
            parent.setSize(width, 400);
            w.setSize(width, w.getPreferredSize().height);
            w.doLayout();
            int startRow = javax.swing.text.Utilities.getRowStart(w, at);
            int endRow = javax.swing.text.Utilities.getRowStart(w, at + label.length() - 1);
            assertEquals(startRow, endRow, "the posture's name split across rows at width " + width);
        }
    }

    /** M74 visual check. Witness: no updateUI override — the note keeps the light colour after the switch. */
    @Test
    @DisplayName("a theme switch re-renders the notes in the new theme's note colour")
    void notesFollowTheTheme() throws Exception {
        LocalAgentTranscript store = new LocalAgentTranscript();
        LocalAgentPanel panel = new LocalAgentPanel(store);
        var note = new LocalAgentState.Entry(0, LocalAgentState.NOTE, 0, "HOOK", "SessionStart: 1 ran", 0);
        var s = new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, "READY", "", "", 0, 1, 0, 1, 0, 0, 0, 0, 0, false,
                false, false, 0, List.of(note), LocalAgentState.Answer.NONE);
        String before = javax.swing.UIManager.getLookAndFeel().getClass().getName();
        java.awt.Color[] seen = new java.awt.Color[2];
        try {
            // every Swing step on the EDT, as the application does it
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                ThemeManager.apply("Light");
                panel.render(s);
                seen[0] = noteColour(panel);
                ThemeManager.apply("Dark");
                javax.swing.SwingUtilities.updateComponentTreeUI(panel);   // queues the re-render
            });
            javax.swing.SwingUtilities.invokeAndWait(() -> seen[1] = noteColour(panel));
            assertEquals(new java.awt.Color(0xB0, 0x5A, 0x00), seen[0], "light: the light note colour");
            assertEquals(new java.awt.Color(0xF0, 0xB4, 0x5A), seen[1], "re-rendered in the dark note colour");
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(before);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static java.awt.Color noteColour(LocalAgentPanel p) {
        var doc = p.conversationView().getStyledDocument();
        return javax.swing.text.StyleConstants.getForeground(doc.getCharacterElement(1).getAttributes());
    }

    /**
     * M74 visual check: Settings ▸ Assistant scrolls instead of clipping, and its fields never collapse. Witness:
     * always track the viewport's width — at a narrower viewport the field gets less than its preferred width.
     */
    @Test
    @DisplayName("the Assistant tab scrolls; a viewport narrower than the content scrolls sideways rather than collapsing fields")
    void theAssistantTabScrollsWithoutCollapsing() {
        javax.swing.JPanel content = new javax.swing.JPanel(new java.awt.GridBagLayout());
        javax.swing.JTextField field = new javax.swing.JTextField(12);
        content.add(new javax.swing.JLabel("Claude Code model:"));
        content.add(field);
        javax.swing.JScrollPane sp = ConfigPanel.scrollingTab(content);
        int need = content.getPreferredSize().width;
        sp.setSize(need + 200, 40);
        sp.doLayout();
        sp.getViewport().doLayout();
        var holder = (javax.swing.JComponent) sp.getViewport().getView();
        assertTrue(((javax.swing.Scrollable) holder).getScrollableTracksViewportWidth(), "room to spare: wraps to width");
        sp.setSize(need / 2, 40);
        sp.doLayout();
        sp.getViewport().doLayout();
        assertFalse(((javax.swing.Scrollable) holder).getScrollableTracksViewportWidth(), "too narrow: scroll, don't squeeze");
        holder.setSize(holder.getPreferredSize());
        holder.doLayout();
        content.doLayout();
        assertTrue(field.getWidth() >= field.getPreferredSize().width, "the field keeps its width: " + field.getWidth());
    }
}
