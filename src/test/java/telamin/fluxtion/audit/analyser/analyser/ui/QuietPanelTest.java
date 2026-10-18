package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import javax.swing.JLabel;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M78 D1, the quiet assistant panel (spec-quiet-assistant-panel.md §3, §4, §7), headless: what the panel shows on every
 * use is a few words, what it shows once is where it is read once, and every word comes from the snapshot.
 */
class QuietPanelTest {

    static LocalAgentState state(String phase, long ticket, List<LocalAgentState.Entry> entries,
                                 Map<Long, LocalAgentState.Turn> turns, long headerRef) {
        LocalAgentState s = new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, phase, "", "", ticket, 1, headerRef, 1, 0, 0,
                0, 0, 0, false, false, true, 0, entries, LocalAgentState.Answer.NONE, LocalAgentPosture.ANALYSER_ASSISTANT, 0,
                SessionEvents.Billing.SUBSCRIPTION, null, null,
                new LocalAgentState.Extra(0, null, 0, 0, 0, turns, 0, LocalAgentState.Attention.NONE));
        return LocalAgentPanelCueTest.published(s);
    }

    /**
     * ★ TQ3: "Claude · unverified" on EVERY answer, the whole {@code NOT_VERIFIED} sentence as the word's accessible
     * description and tooltip. Witnesses: draw the mark only for the first answer (or only when the answer is the first
     * of its turn) — the second answer has none and the count is 1; drop {@code setAccessibleDescription} and the
     * tooltip (a JLabel's description falls back to its tooltip) — the description assertion fails.
     */
    @Test
    @DisplayName("★ TQ3: \"Claude · unverified\" on every answer, with NOT_VERIFIED as its accessible description")
    void everyAnswerIsMarkedUnverified() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(List.of(
                    new TranscriptView.Item("y1", TranscriptStyle.Role.YOU, "What happened?", "", 0, 0, false),
                    new TranscriptView.Item("a1", TranscriptStyle.Role.CLAUDE, "First part.", "", 0, 0, false),
                    new TranscriptView.Item("a2", TranscriptStyle.Role.CLAUDE, "Second answer of the same turn.", "", 0, 0,
                            false)));
            List<JLabel> marks = v.unverifiedMarks();
            assertEquals(2, marks.size(), "★ one mark per answer");
            for (JLabel l : marks) {
                assertEquals(TranscriptStyle.UNVERIFIED, l.getText());
                assertEquals(TranscriptStyle.NOT_VERIFIED, l.getAccessibleContext().getAccessibleDescription(),
                        "★ the whole sentence is its description");
                assertTrue(l.getToolTipText().contains(TranscriptStyle.NOT_VERIFIED), "and its tooltip");
            }
            assertTrue(v.shownText(1).startsWith("Claude · "), "the answer is headed \"Claude · unverified\": "
                    + v.shownText(1));
            assertFalse(v.getText().contains(TranscriptStyle.NOT_VERIFIED), "the sentence is not repeated in the line");
            return null;
        });
    }

    /**
     * ★ Copy on hover or focus (OD-Q.11), and the turn time as the answer's tooltip (OD-Q.8). Witness: paint Copy always
     * (return true from {@code shown()}) — the "not shown until hovered" assertion fails.
     */
    @Test
    @DisplayName("★ an answer's Copy shows only under the pointer; its turn time is a tooltip, not a line")
    void copyOnHoverAndTheTurnTimeIsATooltip() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            long words = store.add(LocalAgentTranscript.Kind.ANSWER, "The finding is written.");
            long question = store.add(LocalAgentTranscript.Kind.USER, "Write it up");
            LocalAgentPanel p = new LocalAgentPanel(store);
            p.render(state("READY", 0, List.of(new LocalAgentState.Entry(question, LocalAgentState.USER, 1, "", "", 0),
                    new LocalAgentState.Entry(words, LocalAgentState.ANSWER, 1, "DONE", "", 1)),
                    Map.of(1L, new LocalAgentState.Turn(-1, 6_100)), 0));
            TranscriptView v = p.conversationView();
            assertFalse(v.getText().contains("Turn:"), "★ no turn line: " + v.getText());
            List<TranscriptView.Item> items = p.items(p.lastState());
            assertEquals("Turn: 6.1 s", items.get(1).tip(), "the time is the answer's tooltip");
            String key = items.get(1).key();
            assertEquals(1, v.buttons("Copy").size(), "the answer has its Copy");
            assertFalse(v.copyShown(key), "★ not shown until hovered or focused");
            v.hoverForTest(1);
            assertTrue(v.copyShown(key), "shown under the pointer");
            v.hoverForTest(-1);
            assertFalse(v.copyShown(key));
            return null;
        });
    }

    /**
     * ★ TQ5 (panel half): Stop REPLACES Send in the same slot only while the node's primary action is Stop; the status
     * line is the node's banner only; the composer's hint is "Draft — not sent". Witness: show Stop whatever the primary
     * (the old always-there disabled Stop) — the READY assertion fails.
     */
    @Test
    @DisplayName("★ TQ5: Stop in Send's slot only while busy; no status line without a banner; the PLAN note stands alone")
    void theConditionalsRenderTheNode() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            LocalAgentPanel p = new LocalAgentPanel(store, a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            LocalAgentState.Entry plan = new LocalAgentState.Entry(0, LocalAgentState.NOTE, 0, LocalAgentSession.PLAN,
                    "Claude Code · Safe · uses your Claude plan", 0);
            p.render(state("READY", 0, List.of(plan), Map.of(), 0));
            assertTrue(a.sendButton().isVisible() && !a.cancelButton().isVisible(), "★ READY: Send, no Stop");
            assertFalse(p.instructionLine().isVisible(), "no \"Ready\" line: the chip word says nothing");
            assertFalse(a.surface().start.isVisible(), "Start / Resume is in ⋯");
            assertTrue(a.conversationView().getText().contains("Claude Code · Safe · uses your Claude plan"));
            assertFalse(a.conversationView().getText().contains(": Claude Code · Safe"), "the PLAN note has no label");
            long q = store.add(LocalAgentTranscript.Kind.USER, "q");
            p.render(state("BUSY", 2, List.of(plan, new LocalAgentState.Entry(q, LocalAgentState.USER, 2, "", "", 0)),
                    Map.of(), 0));
            assertTrue(a.cancelButton().isVisible() && !a.sendButton().isVisible(), "★ busy: Stop in Send's slot");
            assertSame(a.sendButton().getParent(), a.cancelButton().getParent(), "the same slot");
            assertEquals(LocalAgentState.Guide.CHIP_ANSWERING, p.lastState().extra().guide().chipWord());
            assertTrue(a.profileButton().getText().contains(LocalAgentState.Guide.CHIP_ANSWERING),
                    "the control joins the node's word: " + a.profileButton().getText());
            assertEquals("Draft — not sent", telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue
                    .text("composer.hint.CLAUDE_CODE"));
            return null;
        });
    }

    /**
     * TQ7: About this session… holds what the header used to repeat — the limits, the not-local-only sentence, the folder,
     * the model and the plan — through the same pinned constants.
     */
    @Test
    @DisplayName("TQ7: About this session… shows the limits, the external-access sentence, the folder, the model and the plan")
    void aboutHoldsWhatTheHeaderRepeated() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            long ref = store.putHeader(LocalAgentDetailsDisclosureTest.HEADER);
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            LocalAgentPanel p = new LocalAgentPanel(store, a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            p.render(state("READY", 0, List.of(), Map.of(), ref));
            String about = a.aboutText();
            assertTrue(about.contains(LocalAgentPanel.SAFE_LIMITS), "the limits: " + about);
            assertTrue(about.contains(AssistantProfiles.CLAUDE_CODE_SENDS), "where questions go (not local-only)");
            assertTrue(about.contains("/work/proj"), "the folder");
            assertTrue(about.contains("claude-sonnet-5"), "the model");
            assertTrue(about.contains(LocalAgentPanel.PLAN_USAGE), "the plan");
            assertTrue(about.contains(LocalAgentPanel.LOCK_WARNING), "the fork warning, when a conversation exists");
            assertTrue(java.util.Arrays.stream(a.moreMenu().getComponents()).anyMatch(c -> c instanceof javax.swing.JMenuItem m
                    && AssistantPanel.ABOUT.equals(m.getText())), "opened from ⋯");
            return null;
        });
    }
}
