package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.AssistantProfileChoice;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robot walkthrough of a newcomer's first run (2026-10-18), problems #1 and #3, on the REAL generated processor.
 *
 * <p>#1: a question asked from a record before any assistant was chosen used to be composed for the in-app assistant
 * (the mode's silent default), so after Apply — Safe analysis Claude Code refused it as unknown, and asking again
 * appended a second refused copy. Now {@code assistantProfile} holds it unbound and places it for the assistant applied;
 * an unknown draft has one action, Ask again for Claude Code. #3: one primary action and one instruction line.
 */
class FirstRunGuideTest {

    static SessionDriver unchosen(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        d.post(new SessionEvents.StartPageAvailability(false, false, false, false));
        return d;
    }

    /**
     * ★ The question waits for the choice and is placed for the assistant applied, after the mode. Witness: drop the
     * {@code PlacePendingDraftEffect} request in {@code onProfileApplied} — nothing is placed.
     */
    @Test
    @DisplayName("★ asked before any assistant is chosen: held unbound, the cards open, and Apply places it for Claude Code")
    void aQuestionWaitsForTheChoice() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(new SessionEvents.AssistantDraftPending(7, "Q1", "ask Q1 from records"));
        assertTrue(d.snapshot().profiles().open(), "the choices open");
        assertTrue(d.snapshot().profiles().questionWaits());
        assertEquals(AssistantProfileChoice.QUESTION_WAITS, d.snapshot().profiles().status(), "the one line says it waits");
        assertTrue(a.pendingPlaced.isEmpty(), "nothing is placed for the mode's default");
        assertTrue(a.assistantPrimes.isEmpty() && a.agentPrimes.isEmpty(), "nor bound to anyone");
        // a second question replaces the first
        d.post(new SessionEvents.AssistantDraftPending(8, "Q1", "ask Q1 from records"));
        d.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.SAFE));
        assertEquals(1, a.pendingPlaced.size(), "★ placed once a profile is applied");
        assertEquals(8, a.pendingPlaced.get(0).draftRef(), "the newest question");
        assertEquals(AssistantMode.LOCAL_AGENT, a.pendingPlaced.get(0).mode(), "for Claude Code");
        assertTrue(a.agentOrder.indexOf("chooseMode") < a.agentOrder.indexOf("placePending"),
                "after the mode, so Claude Code's node binds it: " + a.agentOrder);
        assertFalse(d.snapshot().profiles().questionWaits());
    }

    /** With an in-app key stored before profiles existed, the question goes to the in-app assistant at once. */
    @Test
    @DisplayName("an assistant that can already answer gets the question at once")
    void anAssistantThatCanAnswerGetsItAtOnce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        d.post(new SessionEvents.StartPageAvailability(false, false, true, false));
        d.post(new SessionEvents.AssistantDraftPending(9, "Q1", "ask"));
        assertEquals(1, a.pendingPlaced.size());
        assertEquals(AssistantMode.IN_APP, a.pendingPlaced.get(0).mode());
        assertFalse(d.snapshot().profiles().open(), "nothing to choose");
    }

    /**
     * ★ Ask again for Claude Code: the unknown draft's verdict carries the one action, and the node asks for exactly the
     * unknown drafts again. Witness: keep STALE_DRAFT for an unknown binding — the action is never offered.
     */
    @Test
    @DisplayName("★ a question drafted for the other assistant: one refusal line, one action, and Ask again composes it for Claude Code")
    void askAgainForClaudeCode() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.LocalAgentDraftComposed(40, "Q1", null, 0, LocalAgentSessionTest.consent(), "t"));
        d.post(new SessionEvents.LocalAgentSendRequested(2, 41, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent(), List.of(40L, 77L)));      // 77: drafted for the in-app assistant
        LocalAgentState s = d.snapshot().localAgent();
        assertEquals("UNKNOWN_DRAFT", s.verdict(), "★ its own verdict, with its own action");
        assertEquals(LocalAgentState.Guide.ASK_AGAIN, s.extra().guide().action(), "★ the one action");
        assertTrue(s.extra().guide().refusal());
        assertTrue(s.extra().guide().instruction().startsWith("This message holds a drafted question"),
                "★ the refusal IS the line, never a suffix: " + s.extra().guide().instruction());
        d.post(new SessionEvents.LocalAgentAskAgainRequested(3, List.of(40L, 77L)));
        assertEquals(1, a.recomposed.size());
        assertEquals(List.of(77L), a.recomposed.get(0).draftRefs(), "only the draft Claude Code does not know");
        assertEquals("", d.snapshot().localAgent().verdict(), "the refusal is answered");
    }

    /**
     * ★ #3: one primary action, one instruction line. Witness: make {@code guide} return STOP whatever the phase — the
     * not-running assertion fails; or put the phase before a refusal — the refusal-alone assertion fails.
     */
    @Test
    @DisplayName("★ one primary action (Send, or Stop while something can be stopped) and one instruction line")
    void onePrimaryActionOneLine() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentState off = d.snapshot().localAgent();
        assertEquals(LocalAgentState.Guide.SEND, off.extra().guide().primary(), "★ not running: Send (it starts Claude Code)");
        assertEquals(LocalAgentSession.NOT_RUNNING_TEXT, off.extra().guide().instruction());
        assertFalse(off.extra().guide().instruction().contains("Start, then Send"), "never two steps");
        LocalAgentSessionTest.startToReady(d, a);
        assertEquals("Ready", d.snapshot().localAgent().extra().guide().instruction());
        assertEquals(LocalAgentState.Guide.SEND, d.snapshot().localAgent().extra().guide().primary());
        LocalAgentSessionTest.send(d, a, 10);
        assertEquals(LocalAgentState.Guide.STOP, d.snapshot().localAgent().extra().guide().primary(), "answering: Stop");
        assertEquals("Claude is answering…", d.snapshot().localAgent().extra().guide().instruction());
        // a refusal while nothing runs is the line on its own
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = LocalAgentSessionTest.driver(b);
        e.post(new SessionEvents.LocalAgentStartRequested(1, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        String line = e.snapshot().localAgent().extra().guide().instruction();
        assertTrue(line.startsWith("Starting Claude Code is switched off"), "★ the refusal alone: " + line);
        assertFalse(line.contains("Not running."), line);
    }
}
