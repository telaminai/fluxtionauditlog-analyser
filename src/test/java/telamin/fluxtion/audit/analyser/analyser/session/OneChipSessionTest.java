package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentFullPostureTest.full;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.kills;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.state;

/**
 * M78 D2, one chip (spec-quiet-assistant-panel.md §5.1, §5.4, OD-Q.6, OD-Q.12), node side, on the REAL generated processor:
 * the in-app assistant's chip word is {@code assistantLoop}'s, and a quick switch with no turn running restarts Claude
 * Code at once — decided by {@code localAgentSession}, never by the menu (rule 9). Headless.
 */
class OneChipSessionTest {

    /**
     * ★ OD-Q.12 (§5.1): the in-app assistant publishes the same chip word as Claude Code — "answering…" while a turn is
     * in progress, nothing when it ends — so the panel joins it and never picks it. Witness: drop
     * {@code RUNNING_ACTION} (or {@code REQUESTING}) from {@code AssistantLoop.chipWord} — the requesting assertion fails.
     */
    @Test
    @DisplayName("★ the in-app chip word is assistantLoop's: answering… during a turn, nothing before and after it")
    void theInAppChipWordIsTheLoops() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        assertEquals(LocalAgentState.Guide.CHIP_READY, d.snapshot().assistant().chipWord(), "idle: nothing");
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(d.snapshot().assistant().busy(), d.snapshot().assistant().phase());
        assertEquals(LocalAgentState.Guide.CHIP_ANSWERING, d.snapshot().assistant().chipWord(), "★ preparing: answering…");
        var ctx = a.assistantContexts.get(a.assistantContexts.size() - 1);
        d.post(new SessionEvents.AssistantContextPrepared(ctx.ticket(), 2));
        assertEquals("REQUESTING", d.snapshot().assistant().phase());
        assertEquals(LocalAgentState.Guide.CHIP_ANSWERING, d.snapshot().assistant().chipWord(), "★ requesting: answering…");
        var r1 = a.assistantRequests.get(a.assistantRequests.size() - 1);
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of()));
        assertEquals("COMPLETE", d.snapshot().assistant().phase());
        assertEquals(LocalAgentState.Guide.CHIP_READY, d.snapshot().assistant().chipWord(), "★ answered: nothing");
        for (String phase : List.of("IDLE", "PREPARING", "REQUESTING", "RUNNING_ACTION", "COMPLETE", "CANCELLED",
                "SUPERSEDED", "LIMIT_REACHED", "FAILED")) {
            assertTrue(LocalAgentState.Guide.CHIP_WORDS.contains(
                    telamin.fluxtion.audit.analyser.analyser.session.node.AssistantLoop.chipWord(phase)), "a closed set");
        }
    }

    private static SessionEvents.AssistantProfileApplied applied(AssistantProfile p, SessionEvents.ProfileOrigin origin) {
        return new SessionEvents.AssistantProfileApplied(900, 77, p, true, "", origin);
    }

    /**
     * ★ OD-Q.6 (§5.4): Safe analysis running, nothing being answered; the quick switch applies Full Claude Code — its
     * performer reports the consent (the offer is made) and then the Applied fact from the MENU — and Claude Code restarts
     * AT ONCE on the new settings: one stop, a new start, no offer left showing. The same change from Settings is offered
     * (Restart / Later), and a switch while a turn is running is offered too. Witnesses: drop the origin check in
     * {@code LocalAgentSession.onProfileApplied} — the Settings case restarts and its "offered" assertion fails; drop the
     * open-ticket check — the mid-turn case restarts and its "the turn goes on" assertion fails; return false at once —
     * the menu case keeps the offer and its "restarted at once" assertion fails.
     */
    @Test
    @DisplayName("★ OD-Q.6: a quick switch with no turn running restarts at once; from Settings, or mid-turn, it is offered")
    void aQuickSwitchRestartsAtOnce() {
        // the quick switch, idle: at once
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        assertEquals("READY", state(d).phase());
        d.post(new SessionEvents.LocalAgentConsentChanged(full(FullMode.DEFAULT)));
        assertEquals(1, a.restartOffers.size(), "the consent report offers, as for any deferrable change");
        assertTrue(state(d).extra().restart().offered());
        d.post(applied(AssistantProfile.FULL, SessionEvents.ProfileOrigin.MENU));
        assertEquals(1, kills(a).size(), "★ restarted at once: the old process stopped");
        assertEquals(2, a.agentStarts.size(), "★ and a new one started on the new settings");
        assertFalse(state(d).extra().restart().any(), "★ no banner left: nothing to answer");

        // the same change from Settings: offered, never restarted by itself
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = LocalAgentSessionTest.driver(b);
        LocalAgentSessionTest.startToReady(e, b);
        e.post(new SessionEvents.LocalAgentConsentChanged(full(FullMode.DEFAULT)));
        e.post(applied(AssistantProfile.FULL, SessionEvents.ProfileOrigin.SETTINGS));
        assertEquals(0, kills(b).size(), "★ a Settings change is offered: nothing stopped");
        assertTrue(state(e).extra().restart().offered(), "★ the banner asks");

        // the quick switch while a turn runs: offered, the turn goes on
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = LocalAgentSessionTest.driver(c);
        var start = LocalAgentSessionTest.startToReady(f, c);
        var t = LocalAgentSessionTest.send(f, c, 10);
        f.post(LocalAgentSessionTest.goodInit(start, t.ticket()));
        assertTrue(state(f).busy());
        f.post(new SessionEvents.LocalAgentConsentChanged(full(FullMode.DEFAULT)));
        f.post(applied(AssistantProfile.FULL, SessionEvents.ProfileOrigin.MENU));
        assertEquals(0, kills(c).size(), "★ the turn goes on");
        assertTrue(state(f).busy());
        assertTrue(state(f).extra().restart().offered(), "★ and the banner asks (OD-Q.6: a turn running)");

        // a refused or failed apply changes nothing
        FakeSessionAdapter g = new FakeSessionAdapter();
        SessionDriver h = LocalAgentSessionTest.driver(g);
        LocalAgentSessionTest.startToReady(h, g);
        h.post(new SessionEvents.LocalAgentConsentChanged(full(FullMode.DEFAULT)));
        h.post(new SessionEvents.AssistantProfileApplied(900, 77, AssistantProfile.FULL, false, "not saved",
                SessionEvents.ProfileOrigin.MENU));
        assertEquals(0, kills(g).size(), "a failed save restarts nothing");
    }
}
