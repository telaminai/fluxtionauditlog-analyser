package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * External utility review (owner-approved 2026-10-17): the Claude Code operator lines — CLI version, endpoint, PATH,
 * diagnostics, the last error — sit behind a <i>Details</i> disclosure, collapsed by default and keyboard-reachable. It
 * opens by itself only when the {@code localAgentSession} node says something needs attention (rule 9: the node decides,
 * the panel renders); the person's own choice is kept for the run, never saved.
 */
class LocalAgentDetailsDisclosureTest {

    static final LocalAgentTranscript.Header HEADER = new LocalAgentTranscript.Header("claude-sonnet-5", "2.1.282",
            "<subscription>", "firstParty", List.of(), "/work/proj", null, List.of("~/.local/bin"), false,
            "https://proxy.example:8443");

    static LocalAgentState state(long headerRef, String verdict, LocalAgentState.Attention a, int unknownEvents) {
        return state(1, headerRef, verdict, a, unknownEvents);
    }

    static LocalAgentState state(long conversation, long headerRef, String verdict, LocalAgentState.Attention a,
                                 int unknownEvents) {
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, conversation, 1, "READY", verdict, "", 0, 1, headerRef, 1, 0, 0, 0,
                0, 0, false, false, false, 0, List.of(), LocalAgentState.Answer.NONE,
                LocalAgentPosture.ANALYSER_ASSISTANT, 0, SessionEvents.Billing.SUBSCRIPTION, null, null,
                new LocalAgentState.Extra(0, null, unknownEvents, 0, 0, Map.of(), 0, a));
    }

    static List<String> texts(Container root) {
        List<String> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && l.getText() != null) out.add(l.getText());
            if (c instanceof javax.swing.text.JTextComponent t && t.isVisible() && t.getText() != null) out.add(t.getText());
            if (c instanceof Container k) out.addAll(texts(k));
        }
        return out;
    }

    /**
     * ★ The node's decision. Witness: return NONE for a {@code *_MISMATCH} verdict — the first assertion fails, and the
     * disclosure would stay shut over the reason the session was stopped.
     */
    @Test
    @DisplayName("★ the node decides: a mismatch, a refusal or a warning needs attention; a version or an endpoint never does")
    void theNodeDecidesWhatNeedsAttention() {
        assertEquals(LocalAgentState.Attention.MISMATCH, LocalAgentSession.attention("TOOLS_MISMATCH", 0, 0, 0));
        assertEquals(LocalAgentState.Attention.MISMATCH, LocalAgentSession.attention("OLD_BRIDGE", 0, 0, 0));
        assertEquals(LocalAgentState.Attention.REFUSAL, LocalAgentSession.attention("NOT_LOGGED_IN", 0, 0, 0));
        assertEquals(LocalAgentState.Attention.REFUSAL, LocalAgentSession.attention("", 7, 0, 0),
                "a spawn failure's words (where claude was searched for)");
        assertEquals(LocalAgentState.Attention.WARNING, LocalAgentSession.attention("EXITED", 0, 3, 0));
        assertEquals(LocalAgentState.Attention.WARNING, LocalAgentSession.attention("OK", 0, 0, 2));
        for (String quiet : List.of("", "OK", "BOUND", "SWITCHED_OFF", "SETUP_DIRECTORY", "BUSY", "SLASH_BLOCKED")) {
            assertEquals(LocalAgentState.Attention.NONE, LocalAgentSession.attention(quiet, 0, 0, 0), quiet);
        }
    }

    /**
     * ★ The panel renders. M78 D1 (spec-quiet-assistant-panel.md §4.1 items 17–18, §4.2, TQ5): the disclosure is
     * CONDITIONAL — the banner's Details button exists only while the node says something needs attention, and opens
     * when the person asks; otherwise the same lines are in About this session…. Witnesses: show the button whatever the
     * attention (the old always-there "▸ Details") — the "not without attention" assertion fails; or ignore the
     * attention — the "button is there" assertion fails.
     */
    @Test
    @DisplayName("★ no Details without attention (the lines are in About); the banner's Details on attention, opened when asked")
    void collapsedUntilSomethingNeedsAttention() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            long ref = store.putHeader(HEADER);
            LocalAgentPanel p = new LocalAgentPanel(store);
            p.render(state(ref, "OK", LocalAgentState.Attention.NONE, 0));
            assertFalse(p.detailsToggle().isVisible(), "★ no Details while nothing needs attention");
            assertFalse(p.detailsBody().isVisible());
            assertFalse(p.detailsOpen());
            String about = String.join("\n", p.aboutLines());
            assertTrue(about.contains("CLI 2.1.282"), "the CLI version is in About: " + about);
            assertTrue(about.contains("requests go to https://proxy.example:8443"), about);
            for (String t : texts(p)) assertFalse(t.startsWith("CLI "), "the CLI version is no longer a chip: " + t);

            p.render(state(ref, "OK", LocalAgentState.Attention.WARNING, 2));   // the node: something unexpected
            assertTrue(p.detailsToggle().isVisible(), "the banner's Details");
            assertTrue(p.detailsToggle().isFocusable(), "keyboard-reachable: Tab reaches it, Space opens it");
            assertEquals("Details", p.detailsToggle().getAccessibleContext().getAccessibleName());
            assertFalse(p.detailsBody().isVisible(), "M78 D1: the banner's Details opens when asked for");
            assertEquals("▸ Details", p.detailsToggle().getText());
            assertEquals("collapsed; needs attention: Claude Code reported something unexpected",
                    p.detailsToggle().getAccessibleContext().getAccessibleDescription(), "what needs attention, said");
            p.detailsToggle().doClick();                         // the person opens it
            assertTrue(p.detailsBody().isVisible());
            assertEquals("▾ Details", p.detailsToggle().getText());
            List<String> body = texts(p.detailsBody());
            assertTrue(body.contains("Claude Code CLI 2.1.282"), "the CLI version is a detail: " + body);
            assertTrue(body.stream().anyMatch(t -> t.startsWith("Diagnostics: 2 unknown stream events")), body.toString());
            p.render(state(2, ref, "OK", LocalAgentState.Attention.WARNING, 3));
            assertTrue(p.detailsBody().isVisible(), "kept open across renders in this run");
            p.detailsToggle().doClick();                         // and shuts it
            assertFalse(p.detailsBody().isVisible());
            p.render(state(ref, "TOOLS_MISMATCH", LocalAgentState.Attention.MISMATCH, 3));
            assertTrue(p.detailsToggle().isVisible(), "a new attention: the button is there");
            assertTrue(p.detailsToggle().getAccessibleContext().getAccessibleDescription().contains("did not match"),
                    p.detailsToggle().getAccessibleContext().getAccessibleDescription());
            p.render(state(ref, "OK", LocalAgentState.Attention.NONE, 0));
            assertFalse(p.detailsToggle().isVisible(), "★ attention cleared: Details goes with it");
            return null;
        });
    }

    /** A panel with nothing to detail shows no disclosure at all. */
    @Test
    void noDetailsNoDisclosure() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
            p.render(state(0, "", LocalAgentState.Attention.NONE, 0));
            assertFalse(p.detailsToggle().isVisible());
            assertFalse(p.detailsBody().isVisible());
            return null;
        });
    }
}
