package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.8, §10) — the start page's button on the REAL generated processor: the route by what can answer, the
 * demo with zero processes, the person's own flow before any draft, the pop-out host with nothing open, the no-AI
 * fallback. The list is the built-in S1–S6 (the configurable list is deferred by the owner until after P3).
 */
class StartPagePromptTest {

    static SessionEvents.LocalAgentConsent consent(boolean allowed) {
        return LocalAgentSessionTest.consent(allowed, true, false, true, "p", 0);
    }

    static SessionEvents.StartPagePromptSent press(long request, String entry, long textRef, boolean claudeAllowed,
                                                   boolean claudeFound, boolean inApp, boolean workspace) {
        return new SessionEvents.StartPagePromptSent(request, entry, textRef, consent(claudeAllowed), claudeFound,
                inApp, true, workspace);
    }

    static StartPageState state(SessionDriver d) {
        return d.snapshot().startPage();
    }

    // ---- ★ routing by what can answer (r8 F8) ------------------------------------------------------------------------

    /** ★ Witness: route on the mode alone (return CLAUDE_CODE whenever the mode is LOCAL_AGENT) — rows 2 and 4 fail. */
    @Test
    @DisplayName("★ the route per mode × switch × resolver found × key")
    void routeTable() {
        record Row(boolean localAgent, boolean allowed, boolean found, boolean key, StartPageState.Route want) { }
        for (Row r : List.of(
                new Row(true, true, true, false, StartPageState.Route.CLAUDE_CODE),
                new Row(true, true, false, false, StartPageState.Route.NO_AI),          // switch on, no claude found
                new Row(true, true, false, true, StartPageState.Route.SWITCH_TO_IN_APP),
                new Row(true, false, true, false, StartPageState.Route.NO_AI),          // found, switch off
                new Row(false, false, false, true, StartPageState.Route.IN_APP),
                new Row(false, true, true, false, StartPageState.Route.SWITCH_TO_CLAUDE_CODE),
                new Row(false, true, true, true, StartPageState.Route.IN_APP),         // the mode in force wins
                new Row(false, false, false, false, StartPageState.Route.NO_AI))) {
            assertEquals(r.want(), StartPagePrompt.route(r.localAgent(), r.allowed(), r.found(), r.key()), r.toString());
        }
    }

    @Test
    @DisplayName("the published route follows the availability observed and the mode; switch on with no claude names it")
    void publishedRoute() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        assertEquals(StartPageState.Route.NO_AI, state(d).route());
        d.post(new SessionEvents.StartPageAvailability(true, false, false, true));
        assertEquals(StartPageState.Route.NO_AI, state(d).route());
        assertTrue(state(d).claudeCodeNotFound(), "the no-AI column shows the resolver's line");
        d.post(new SessionEvents.StartPageAvailability(true, true, false, true));
        assertEquals(StartPageState.Route.SWITCH_TO_CLAUDE_CODE, state(d).route(), "the button reads Ask with Claude Code");
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
        assertEquals(StartPageState.Route.CLAUDE_CODE, state(d).route(), "the remembered mode answers");
    }

    // ---- ★ the button never starts Claude Code; the demo has zero processes ------------------------------------------

    /** ★ Witness: request a StartLocalAgentEffect from onSent — the zero-starts assertion fails. */
    @Test
    @DisplayName("★ the demo plays in every column with zero processes; the button never starts Claude Code")
    void theDemoAndNoStart() {
        for (boolean[] col : new boolean[][]{{true, true, false}, {false, false, true}, {false, false, false}}) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = new SessionDriver(a);
            d.post(new SessionEvents.AssistantModeChosen(col[0] ? AssistantMode.LOCAL_AGENT : AssistantMode.IN_APP, "t"));
            d.post(press(1, "S1", 0, col[0], col[1], col[2], false));
            assertEquals(1, a.demos.size(), "the demo plays");
            assertEquals(0, a.agentStarts.size(), "no process");
            assertEquals(0, a.agentSends.size());
            assertEquals(0, a.assistantContexts.size(), "and no provider call");
            assertEquals(0, a.startComposes.size(), "no draft: the demo's words are its own, labelled");

            d.post(press(2, "S6", 77, col[0], col[1], col[2], false));
            assertEquals(0, a.agentStarts.size(), "a prompt entry starts nothing either");
        }
    }

    /** ★ r8 F1. Witness: popOut = false whatever the workspace — the nothing-open assertion fails. */
    @Test
    @DisplayName("★ with nothing open the draft goes to the popped-out assistant; over a workspace, to the docked tab")
    void popOutWithNothingOpen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
        d.post(press(1, "S6", 0, true, true, false, false));
        assertEquals(1, a.startComposes.size());
        var c = a.startComposes.get(0);
        assertEquals("S6", c.entryId());
        assertEquals(AssistantMode.LOCAL_AGENT, c.target());
        assertFalse(c.chooseMode());
        assertTrue(c.popOut(), "nothing is open: the assistant pops out (OD-74.21)");
        d.post(press(2, "S6", 0, true, true, false, true));
        assertFalse(a.startComposes.get(1).popOut(), "a workspace is behind the page: its docked tab");
    }

    @Test
    @DisplayName("the mode in force cannot answer and the other can: the button chooses that mode (the person's act)")
    void switchingMode() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);                // in-app, no key
        d.post(press(1, "S6", 0, true, true, false, false));
        var c = a.startComposes.get(0);
        assertEquals(AssistantMode.LOCAL_AGENT, c.target());
        assertTrue(c.chooseMode(), "OD-74.23: Ask with Claude Code chooses Claude Code");
    }

    // ---- ★ the person's flow first; no dead end ----------------------------------------------------------------------

    /** ★ r8 F9/F10. Witness: compose S3's draft on the press (before the flow) — the no-compose-yet assertion fails. */
    @Test
    @DisplayName("★ S3, S4 and S5 run the person's own flow before any draft; the draft is composed after it")
    void flowBeforeDraft() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
        for (String id : List.of("S3", "S4", "S5")) {
            int before = a.startFlows.size();
            d.post(press(10 + before, id, 0, true, true, false, false));
            assertEquals(before + 1, a.startFlows.size(), id + " runs its flow");
            assertEquals(id, a.startFlows.get(before).entryId());
        }
        assertEquals(0, a.startComposes.size(), "nothing is composed before a flow ends");
        d.post(new SessionEvents.StartPageFlowDone(10, SessionEvents.FlowOutcome.REFUSED, 0, 0, 0));
        assertEquals(0, a.startComposes.size(), "a bundle that did not verify drafts nothing (r8 F3)");
        d.post(new SessionEvents.StartPageFlowDone(11, SessionEvents.FlowOutcome.DONE, 0, 0, 0));
        assertEquals(1, a.startComposes.size(), "S4's fetch done (or unreachable): composed");
        assertEquals("S4", a.startComposes.get(0).entryId());
        d.post(new SessionEvents.StartPageFlowDone(12, SessionEvents.FlowOutcome.DONE, 3, 0, 0));
        assertEquals("S5", a.startComposes.get(1).entryId());
        assertEquals(3, a.startComposes.get(1).logGeneration(), "bound to the generation the flow opened");
        d.post(new SessionEvents.StartPageFlowDone(12, SessionEvents.FlowOutcome.DONE, 3, 0, 0));
        assertEquals(2, a.startComposes.size(), "a second end for the same press is stale");
    }

    @Test
    @DisplayName("S5 with a log already open drafts at once, bound to that log's generation")
    void s5WithALogOpen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
        long gen = AskDraftSessionTest.open(d, "/logs/x.yaml");
        d.post(press(1, "S5", 0, true, true, false, true));
        assertEquals(0, a.startFlows.size());
        assertEquals(gen, a.startComposes.get(0).logGeneration());
    }

    /**
     * ★ r8 F2, F21 — no dead end in any column: every entry ends in a draft for an assistant that can answer, a flow, a
     * demo, or a fallback. Witness: return without an effect for the no-AI column's prompt entries — S6's row fails.
     */
    @Test
    @DisplayName("★ no dead end: every built-in entry × {Claude Code, in-app, no AI} ends in an effect")
    void noDeadEnd() {
        boolean[][] columns = {{true, true, false}, {false, false, true}, {false, false, false}};
        for (boolean[] col : columns) {
            for (AskCatalogue.Entry e : AskCatalogue.startPageBuiltIns()) {
                FakeSessionAdapter a = new FakeSessionAdapter();
                SessionDriver d = new SessionDriver(a);
                if (col[0]) d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
                d.post(press(1, e.id(), 0, col[0], col[1], col[2], false));
                int effects = a.demos.size() + a.startFlows.size() + a.startFallbacks.size() + a.startComposes.size();
                assertEquals(1, effects, e.id() + " in column " + java.util.Arrays.toString(col) + " ends somewhere");
                assertEquals(0, a.agentStarts.size());
                if (!col[0] && !col[2] && (e.id().equals("S3") || e.id().equals("S5"))) {
                    d.post(new SessionEvents.StartPageFlowDone(1, SessionEvents.FlowOutcome.DONE, 1, 0, 0));
                    assertEquals(1, a.startFallbacks.size(), e.id() + ": the flow ran, then its fallback");
                    assertEquals(e.id(), a.startFallbacks.get(0).entryId());
                }
            }
        }
    }

    @Test
    @DisplayName("free text alone is an ordinary question; an empty box with no entry sends nothing")
    void freeText() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(press(1, "NONE", 0, false, false, true, false));
        assertEquals(0, a.startComposes.size());
        assertFalse(state(d).note().isBlank());
        d.post(press(2, "NONE", 9, false, false, true, false));
        assertEquals(1, a.startComposes.size());
        assertEquals(9, a.startComposes.get(0).textRef());
        assertEquals(AssistantMode.IN_APP, a.startComposes.get(0).target());
    }

    // ---- a dropped prompt pack (owner request 2026-10-17) ------------------------------------------------------------

    /** ★ Witness: ignore {@code ReturnToMainOptions} in the node — the pack stays in force and this fails. */
    @Test
    @DisplayName("★ a valid pack replaces the list until Return; a refused one keeps the list and names its cause")
    void thePackInForce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        assertEquals(0, state(d).packRef());
        d.post(new SessionEvents.StartPromptPackDropped(7, null, 0));
        assertEquals(7, state(d).packRef(), "the pack is in force");
        d.post(new SessionEvents.StartPromptPackDropped(0,
                telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Cause.HTML, 3));
        assertEquals(7, state(d).packRef(), "a refused drop keeps the list in force");
        assertEquals("line 3 — HTML is not allowed", state(d).packRefusalWords());
        d.post(new SessionEvents.ReturnToMainOptions("test"));
        assertEquals(0, state(d).packRef(), "Return restores the built-in list");
        assertEquals("", state(d).packRefusalWords());
        String record = String.join("\n", d.auditSink().records());
        assertTrue(record.contains("StartPromptPackDropped"));
        assertFalse(record.contains("HTML is not allowed") && record.contains("words"), "codes, never a file's words");
    }
}
