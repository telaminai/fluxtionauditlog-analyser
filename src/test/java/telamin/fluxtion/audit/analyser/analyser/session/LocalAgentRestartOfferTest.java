package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.*;

/**
 * Owner, 2026-10-18 ("update the assistant now? yes / no"), on the REAL generated processor: a settings change that may
 * wait is OFFERED as a restart, and the running session keeps its old settings until the person answers; a change that
 * takes a power away ends the session at once and is never offered.
 *
 * <p>Offerable: Safe analysis → Full Claude Code; a full mode that asks less; the folder; the model; the spend limit.
 * Mandatory: consent withdrawn; Full switched off; a full mode that asks more; the folder no longer confirmed for the
 * posture running (a Settings change). Witnesses for the ★ rows are named on each.
 */
class LocalAgentRestartOfferTest {

    /** {@code c} with the model and the folder digest observed, as the frame reports them. */
    static SessionEvents.LocalAgentConsent observed(SessionEvents.LocalAgentConsent c, String model, String folder) {
        return new SessionEvents.LocalAgentConsent(c.allowed(), c.folderConfirmed(), c.bundleWorkingCopy(), c.postureRef(),
                c.budgetMicros(), c.folder(), c.full(), c.fullMode(), c.fullFolderConfirmed(), model, folder);
    }

    static SessionEvents.LocalAgentConsent safe(String model, long budget) {
        return observed(consent(true, true, false, "posture-a", budget), model, "folder-a");
    }

    /** Full Claude Code in the same folder, confirmed for it. */
    static SessionEvents.LocalAgentConsent fullHere(FullMode mode) {
        return new SessionEvents.LocalAgentConsent(true, true, false, "posture-full-" + mode.cli(), BUDGET,
                SessionEvents.FolderVerdict.OK, true, mode, true, "sonnet", "folder-a");
    }

    /** A ready Safe analysis session started under {@code safe("sonnet", BUDGET)}. */
    static SessionEffects.StartLocalAgentEffect ready(SessionDriver d, FakeSessionAdapter a) {
        d.post(new SessionEvents.LocalAgentStartRequested(1, safe("sonnet", BUDGET)));
        var start = a.agentStarts.get(a.agentStarts.size() - 1);
        bind(d, start);
        assertEquals("READY", state(d).phase());
        return start;
    }

    static LocalAgentState.Restart restart(SessionDriver d) {
        return state(d).extra().restart();
    }

    /**
     * ★ An offerable change shows the offer and stops nothing. Witness (the behaviour before the owner's request): make
     * {@code consentWhileRunning} kill for an offerable change, as {@code endForConsent} did — the kill counts fail.
     */
    @Test
    @DisplayName("★ the model, the spend limit and Safe → Full are each offered; the session keeps running")
    void anOfferableChangeIsOfferedAndStopsNothing() {
        for (SessionEvents.LocalAgentConsent change : java.util.List.of(safe("opus", BUDGET), safe("sonnet", 3_000_000),
                fullHere(FullMode.DEFAULT))) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = driver(a);
            ready(d, a);
            d.post(new SessionEvents.LocalAgentConsentChanged(change));
            assertEquals(0, kills(a).size(), "★ nothing is stopped: " + restart(d).changed());
            assertEquals("READY", state(d).phase());
            assertTrue(restart(d).offered(), "the restart is offered");
            assertEquals(1, a.restartOffers.size(), "the node asked for the offer to be shown");
            assertEquals(restart(d).offer(), a.restartOffers.get(0).offer());
            assertEquals(LocalAgentSession.RESTART_STATUS, a.restartOffers.get(0).status());
            // the same change reported again (a second Settings save) is not offered twice
            d.post(new SessionEvents.LocalAgentConsentChanged(change));
            assertEquals(1, a.restartOffers.size());
        }
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        ready(d, a);
        d.post(new SessionEvents.LocalAgentConsentChanged(safe("opus", 3_000_000)));
        assertEquals("the model (sonnet → opus), the spend limit", restart(d).changed(), "what changed, in words");
        assertFalse(restart(d).newChat(), "the same posture: the conversation would continue");
    }

    /** ★ Yes restarts, resuming the conversation. Witness: drop {@code start(request, c)} in restartNow — nothing starts. */
    @Test
    @DisplayName("★ Yes stops the process and starts it again; the same posture resumes the conversation")
    void yesRestartsAndResumes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = ready(d, a);
        var t = send(d, a, 10, safe("sonnet", BUDGET));
        d.post(new SessionEvents.LocalAgentTurnEnded(first.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.LocalAgentConsentChanged(safe("opus", BUDGET)));
        long offer = restart(d).offer();
        d.post(new SessionEvents.LocalAgentRestartAnswered(20, offer, true, safe("opus", BUDGET)));
        assertEquals(1, kills(a).size(), "the old process is stopped");
        assertEquals(2, a.agentStarts.size(), "★ and a new one started");
        var second = a.agentStarts.get(1);
        assertTrue(second.resume(), "the same posture: resumed");
        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(1, state(d).conversation(), "the same conversation");
        assertFalse(restart(d).any(), "the offer is answered");
        assertTrue(state(d).entries().stream().anyMatch(e -> LocalAgentSession.RESTARTED_TEXT.equals(e.detail())),
                "the transcript says it was restarted");
        assertTrue(state(d).answer().accepted());
    }

    /** ★ Yes under another posture begins a new chat. Witness: drop {@code beginConversation()} — Start refuses POSTURE_CHANGED. */
    @Test
    @DisplayName("★ Yes for Safe → Full begins a new chat (a conversation is never resumed under other settings)")
    void yesUnderAnotherPostureBeginsANewChat() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = ready(d, a);
        var t = send(d, a, 10, safe("sonnet", BUDGET));
        d.post(new SessionEvents.LocalAgentTurnEnded(first.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.LocalAgentConsentChanged(fullHere(FullMode.ACCEPT_EDITS)));
        assertTrue(restart(d).newChat(), "the offer says Yes begins a new chat");
        d.post(new SessionEvents.LocalAgentRestartAnswered(21, restart(d).offer(), true, fullHere(FullMode.ACCEPT_EDITS)));
        assertEquals(2, a.agentStarts.size(), "★ started again: " + state(d).reason());
        var second = a.agentStarts.get(1);
        assertFalse(second.resume());
        assertNotEquals(first.sessionId(), second.sessionId());
        assertTrue(second.posture().isFull());
        assertEquals(2, state(d).conversation(), "a new conversation");
        assertTrue(state(d).entries().stream().anyMatch(e -> LocalAgentSession.RESTARTED_NEW_CHAT_TEXT.equals(e.detail())),
                "the new chat says why it began");
    }

    /** ★ Later keeps the session on its old settings, with the chip. Witness: kill on Later — the kill count fails. */
    @Test
    @DisplayName("★ Later keeps it running with the chip; a Send goes to it; the chip offers again; a put-back change withdraws it")
    void laterKeepsRunningWithTheChip() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        ready(d, a);
        d.post(new SessionEvents.LocalAgentConsentChanged(safe("opus", BUDGET)));
        d.post(new SessionEvents.LocalAgentRestartAnswered(22, restart(d).offer(), false, safe("opus", BUDGET)));
        assertEquals(0, kills(a).size(), "★ Later stops nothing");
        assertTrue(restart(d).deferred(), "the chip shows");
        assertEquals(1, a.agentStarts.size());
        // a Send goes to the running session — no refusal, no second offer
        send(d, a, 11, safe("opus", BUDGET));
        assertEquals(1, a.restartOffers.size());
        assertTrue(restart(d).deferred());
        // a stale answer (the offer was already answered) changes nothing
        d.post(new SessionEvents.LocalAgentRestartAnswered(23, restart(d).offer(), true, safe("opus", BUDGET)));
        assertEquals(0, kills(a).size(), "an answer to an answered offer is stale");
        // the chip offers again
        d.post(new SessionEvents.LocalAgentRestartOfferRequested("test"));
        assertTrue(restart(d).offered());
        // the person puts the model back: the offer is withdrawn
        d.post(new SessionEvents.LocalAgentConsentChanged(safe("sonnet", BUDGET)));
        assertFalse(restart(d).any(), "nothing to apply any more");
        assertEquals(0, kills(a).size());
    }

    /**
     * ★ Mandatory changes stop at once with no offer, idle and mid-turn. Witnesses: drop the {@code FULL_OFF} or the
     * {@code TRUST_CHANGED} check in {@code mandatoryStop} — that change is offered instead and the kill count fails.
     */
    @Test
    @DisplayName("★ withdrawn, Full off, a stricter mode and a lost folder trust each stop at once — mid-turn too — and are never offered")
    void mandatoryChangesStopAtOnce() {
        record Case(String verdict, SessionEvents.LocalAgentConsent start, SessionEvents.LocalAgentConsent change) { }
        var withdrawn = observed(consent(false, true, false, "posture-a", BUDGET), "sonnet", "folder-a");
        var untrusted = observed(consent(true, false, false, "posture-a", BUDGET), "sonnet", "folder-a");
        java.util.List<Case> cases = java.util.List.of(
                new Case("SWITCHED_OFF", safe("sonnet", BUDGET), withdrawn),
                new Case("FULL_OFF", fullHere(FullMode.DEFAULT), safe("sonnet", BUDGET)),
                new Case("MODE_STRICTER", fullHere(FullMode.AUTO), fullHere(FullMode.DEFAULT)),
                new Case("TRUST_CHANGED", safe("sonnet", BUDGET), untrusted));
        for (Case k : cases) {
            for (boolean midTurn : new boolean[]{false, true}) {
                FakeSessionAdapter a = new FakeSessionAdapter();
                SessionDriver d = driver(a);
                d.post(new SessionEvents.LocalAgentStartRequested(1, k.start()));
                var start = a.agentStarts.get(0);
                if (k.start().full()) {
                    LocalAgentFullPostureTest.bindFull(d, start, k.start().fullMode());
                } else {
                    bind(d, start);
                }
                if (midTurn) {
                    d.post(new SessionEvents.LocalAgentSendRequested(10, 10, SessionEvents.SlashClass.TEXT, null, k.start()));
                    assertTrue(state(d).busy(), k.verdict());
                }
                d.post(new SessionEvents.LocalAgentConsentChanged(k.change()));
                String what = k.verdict() + (midTurn ? " mid-turn" : " idle");
                assertEquals(1, kills(a).size(), "★ stopped at once: " + what);
                assertEquals("OFF", state(d).phase(), what);
                assertEquals(k.verdict(), state(d).verdict(), what);
                assertEquals(0, a.restartOffers.size(), "★ never offered: " + what);
                assertFalse(restart(d).any(), what);
            }
        }
    }

    /** ★ Yes while Claude answers waits for the answer. Witness: drop the AFTER_TURN branch in endTurn — nothing restarts. */
    @Test
    @DisplayName("★ Yes while Claude is answering restarts when the answer ends, never in the middle of it")
    void yesMidTurnRestartsAfterTheTurn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = ready(d, a);
        var t = send(d, a, 10, safe("sonnet", BUDGET));
        d.post(new SessionEvents.LocalAgentConsentChanged(safe("sonnet", 2_000_000)));
        assertTrue(restart(d).offered());
        d.post(new SessionEvents.LocalAgentRestartAnswered(24, restart(d).offer(), true, safe("sonnet", 2_000_000)));
        assertTrue(restart(d).afterTurn(), "it waits for the answer");
        assertEquals(0, kills(a).size(), "the turn is never cut short by a change that may wait");
        assertTrue(state(d).busy());
        d.post(new SessionEvents.LocalAgentTurnEnded(first.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(1, kills(a).size(), "★ then the old process is stopped");
        assertEquals(2, a.agentStarts.size(), "and a new one started");
        assertEquals(2_000_000, a.agentStarts.get(1).maxBudgetMicros(), "under the new spend limit");
        assertFalse(restart(d).any());
    }

    /** ★ Probe 2026-10-18: switched back on, the panel said "switched off" until Start. Witness: drop the clearing branch. */
    @Test
    @DisplayName("★ allowed again after it was switched off: the stale 'switched off' is cleared")
    void allowedAgainClearsTheStaleVerdict() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        ready(d, a);
        d.post(new SessionEvents.LocalAgentConsentChanged(observed(consent(false, true, false, "posture-a", BUDGET),
                "sonnet", "folder-a")));
        assertEquals("SWITCHED_OFF", state(d).verdict());
        d.post(new SessionEvents.LocalAgentConsentChanged(safe("sonnet", BUDGET)));
        assertEquals("", state(d).verdict(), "★ no longer said to be switched off");
        assertEquals(LocalAgentSession.ALLOWED_AGAIN_TEXT, state(d).reason());
    }
}
