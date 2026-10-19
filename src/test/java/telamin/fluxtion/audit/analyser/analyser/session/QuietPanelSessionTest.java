package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M78 D1, the quiet assistant panel (spec-quiet-assistant-panel.md §5), node side, on the REAL generated processor: the
 * header's chip word, the once-per-session PLAN note and the dropped Approved note are decided by the session's nodes
 * (rule 9) — the panel only renders them. Headless.
 */
class QuietPanelSessionTest {

    static List<LocalAgentState.Entry> notes(SessionDriver d, String status) {
        return d.snapshot().localAgent().entries().stream()
                .filter(e -> LocalAgentState.NOTE.equals(e.kind()) && status.equals(e.status())).toList();
    }

    static String chip(SessionDriver d) {
        return d.snapshot().localAgent().extra().guide().chipWord();
    }

    /**
     * ★ TQ4: the PLAN note appears ONCE per session — a turn's return to READY never repeats it — and AGAIN after a
     * restart, which is a new session. Witnesses: append it on every READY (note it, unlatched, where a turn's end
     * returns the phase to READY) — the after-a-turn count is 2; latch it once per process (a static flag, or never reset by a new
     * generation) — the after-a-restart count stays 1.
     */
    @Test
    @DisplayName("★ TQ4: the PLAN note once per session, never again after a turn, and again after a restart")
    void thePlanNoteOncePerSession() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        assertEquals(0, notes(d, LocalAgentSession.PLAN).size(), "nothing before the session is READY");
        var start = LocalAgentSessionTest.startToReady(d, a);
        assertEquals(1, notes(d, LocalAgentSession.PLAN).size(), "★ on the session's first READY");
        assertEquals(LocalAgentSession.planLine(LocalAgentPosture.ANALYSER_ASSISTANT, d.snapshot().localAgent().billing()),
                notes(d, LocalAgentSession.PLAN).get(0).detail(), "the node's own line");
        assertTrue(notes(d, LocalAgentSession.PLAN).get(0).detail().startsWith("Claude Code · Safe"));
        var t = LocalAgentSessionTest.send(d, a, 10);
        d.post(LocalAgentSessionTest.goodInit(start, t.ticket()));
        d.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals("READY", d.snapshot().localAgent().phase(), "back to READY after the turn");
        assertEquals(1, notes(d, LocalAgentSession.PLAN).size(), "★ a turn's READY never repeats it");

        d.post(new SessionEvents.LocalAgentExited(start.generation(), 1, 0));           // the session ends
        d.post(new SessionEvents.LocalAgentStartRequested(2, LocalAgentSessionTest.consent()));
        var again = a.agentStarts.get(a.agentStarts.size() - 1);
        assertNotEquals(start.generation(), again.generation(), "a new session");
        LocalAgentSessionTest.bind(d, again);
        assertEquals("READY", d.snapshot().localAgent().phase());
        assertEquals(2, notes(d, LocalAgentSession.PLAN).size(), "★ again after a restart");
    }

    @Test
    @DisplayName("the PLAN line: the posture and how it is paid for — never a dollar figure")
    void thePlanLine() {
        assertEquals("Claude Code · Safe · uses your Claude plan",
                LocalAgentSession.planLine(LocalAgentPosture.ANALYSER_ASSISTANT, SessionEvents.Billing.SUBSCRIPTION));
        assertEquals("Claude Code · Full (default) · your tools · uses your API key",
                LocalAgentSession.planLine(LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT),
                        SessionEvents.Billing.API_KEY));
        assertEquals("Claude Code · Safe", LocalAgentSession.planLine(LocalAgentPosture.ANALYSER_ASSISTANT,
                SessionEvents.Billing.UNKNOWN));
        for (SessionEvents.Billing b : SessionEvents.Billing.values()) {
            assertFalse(LocalAgentSession.planLine(LocalAgentPosture.ANALYSER_ASSISTANT, b).contains("US$"), b.name());
        }
    }

    /**
     * ★ TQ5 (node half): an Approved decision leaves NO note — the tool line's "approved by you" says it — and a Denied
     * one still does. Witnesses: put back {@code permissionNoted(ticket, "APPROVED", …)} — the Approved count is 1; drop
     * the Denied note with it (no {@code permissionNoted} in {@code deny}) — the Denied count is 0.
     */
    @Test
    @DisplayName("★ TQ5: the Approved note is gone, the Denied note is kept — dropped by the node, never filtered by the panel")
    void theApprovedNoteIsDroppedTheDeniedKept() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        long g = start.generation();
        long ticket = d.snapshot().localAgent().openTicket();
        d.post(LocalAgentPermissionTest.report(g, ticket, 51));
        assertTrue(d.snapshot().permission().waiting());
        d.post(new SessionEvents.LocalAgentPermissionChosen(51, true));
        assertEquals(0, notes(d, "APPROVED").size(), "★ no Approved note");
        d.post(LocalAgentPermissionTest.report(g, ticket, 52));
        d.post(new SessionEvents.LocalAgentPermissionChosen(52, false));
        assertEquals(1, notes(d, "DENIED").size(), "★ the Denied note is kept");
    }

    /**
     * ★ TQ6 (node half): the chip word is the node's, from a closed set — "needs you" exactly while localAgentPermission
     * holds a dialog open, "answering…" while a turn runs, "stopped" after Claude Code exits, nothing when READY.
     * Witness: map the permission node's waiting state to nothing (drop {@code permissionWaiting} in its
     * {@code changed()}) — the "needs you" assertion fails.
     */
    @Test
    @DisplayName("★ the chip word: nothing when READY, answering… in a turn, needs you while a permission waits, stopped after an exit")
    void theChipWordIsTheNodes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        assertEquals(LocalAgentState.Guide.CHIP_READY, chip(d), "not started: nothing (Send starts it)");
        var start = LocalAgentSessionTest.startToReady(d, a);
        assertEquals(LocalAgentState.Guide.CHIP_READY, chip(d), "READY says nothing");
        assertEquals("", d.snapshot().localAgent().extra().guide().banner(), "and has no banner");
        var t = LocalAgentSessionTest.send(d, a, 10);
        d.post(LocalAgentSessionTest.goodInit(start, t.ticket()));
        assertEquals(LocalAgentState.Guide.CHIP_ANSWERING, chip(d));
        d.post(LocalAgentPermissionTest.report(start.generation(), t.ticket(), 61));
        assertEquals(LocalAgentState.Guide.CHIP_NEEDS_YOU, chip(d), "★ the permission node's waiting state");
        d.post(new SessionEvents.LocalAgentPermissionChosen(61, false));
        assertEquals(LocalAgentState.Guide.CHIP_ANSWERING, chip(d), "answered: back to answering…");
        d.post(new SessionEvents.LocalAgentExited(start.generation(), 1, 0));
        assertEquals(LocalAgentState.Guide.CHIP_STOPPED, chip(d), "exited: stopped");
        assertTrue(d.snapshot().localAgent().extra().guide().banner().startsWith("Claude Code exited (code 1)"),
                "the exit is the one banner: " + d.snapshot().localAgent().extra().guide().banner());
        for (String w : List.of(LocalAgentState.Guide.CHIP_READY, LocalAgentState.Guide.CHIP_STARTING,
                LocalAgentState.Guide.CHIP_ANSWERING, LocalAgentState.Guide.CHIP_NEEDS_YOU, LocalAgentState.Guide.CHIP_STOPPED)) {
            assertTrue(LocalAgentState.Guide.CHIP_WORDS.contains(w));
            assertTrue(w.split(" ").length <= 2, "one or two words: " + w);
        }
    }
}
