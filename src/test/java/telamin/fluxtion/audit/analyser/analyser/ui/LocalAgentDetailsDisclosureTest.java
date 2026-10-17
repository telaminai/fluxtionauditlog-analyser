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
     * ★ The panel renders. Witness: show {@code detailsBody} whatever the attention (the old always-visible lines) — the
     * "collapsed by default" assertion fails; or ignore the attention — the "opens by itself" assertion fails.
     */
    @Test
    @DisplayName("★ collapsed by default; opens by itself on attention; the person's choice is kept for the run")
    void collapsedUntilSomethingNeedsAttention() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            long ref = store.putHeader(HEADER);
            LocalAgentPanel p = new LocalAgentPanel(store);
            p.render(state(ref, "OK", LocalAgentState.Attention.NONE, 0));
            assertTrue(p.detailsToggle().isVisible(), "there are details to show");
            assertFalse(p.detailsBody().isVisible(), "collapsed by default");
            assertFalse(p.detailsOpen());
            assertEquals("▸ Details", p.detailsToggle().getText());
            assertTrue(p.detailsToggle().isFocusable(), "keyboard-reachable: Tab reaches it, Space opens it");
            assertEquals("Details", p.detailsToggle().getAccessibleContext().getAccessibleName());
            assertEquals("collapsed", p.detailsToggle().getAccessibleContext().getAccessibleDescription());
            List<String> body = texts(p.detailsBody());
            assertTrue(body.contains("Claude Code CLI 2.1.282"), "the CLI version is a detail: " + body);
            assertTrue(body.stream().anyMatch(t -> t.contains("requests go to https://proxy.example:8443")), body.toString());
            for (String t : texts(p)) assertFalse(t.startsWith("CLI "), "the CLI version is no longer a chip: " + t);

            p.detailsToggle().doClick();                         // the person opens it
            assertTrue(p.detailsBody().isVisible());
            assertEquals("▾ Details", p.detailsToggle().getText());
            assertEquals("expanded", p.detailsToggle().getAccessibleContext().getAccessibleDescription());
            p.render(state(2, ref, "OK", LocalAgentState.Attention.NONE, 0));
            assertTrue(p.detailsBody().isVisible(), "kept open across renders in this run");
            p.detailsToggle().doClick();                         // and shuts it
            assertFalse(p.detailsBody().isVisible());

            p.render(state(ref, "OK", LocalAgentState.Attention.WARNING, 2));   // the node: something unexpected
            assertTrue(p.detailsBody().isVisible(), "opens by itself when the node says it needs attention");
            assertTrue(p.detailsToggle().getText().startsWith("▾ Details — needs attention"), p.detailsToggle().getText());
            assertTrue(texts(p.detailsBody()).stream().anyMatch(t -> t.startsWith("Diagnostics: 2 unknown stream events")));
            p.detailsToggle().doClick();                         // the person may still close it
            assertFalse(p.detailsBody().isVisible());
            p.render(state(ref, "OK", LocalAgentState.Attention.WARNING, 3));
            assertFalse(p.detailsBody().isVisible(), "the same attention does not reopen what the person closed");
            p.render(state(ref, "TOOLS_MISMATCH", LocalAgentState.Attention.MISMATCH, 3));
            assertTrue(p.detailsBody().isVisible(), "a new attention does");
            assertTrue(p.detailsToggle().getText().contains("did not match"), p.detailsToggle().getText());
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
