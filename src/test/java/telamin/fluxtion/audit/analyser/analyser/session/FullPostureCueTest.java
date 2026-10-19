package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner request, 2026-10-17 (spec §5.9 "The full-posture cue"): the composer, the start-page box, Send, the tab and the
 * pop-out window show that the person's own full Claude Code is in force — and the cue FOLLOWS THE SESSION: it appears
 * and disappears with the posture {@code localAgentSession} reports, on the real processor, never with the config file.
 */
class FullPostureCueTest {

    static SessionEvents.LocalAgentConsent full(LocalAgentPosture.FullMode mode) {
        return new SessionEvents.LocalAgentConsent(true, true, false, "p-" + mode, 0,
                SessionEvents.FolderVerdict.OK, true, mode, true);
    }

    static LocalAgentState state(SessionDriver d) {
        return d.snapshot().localAgent();
    }

    /**
     * ★ Witness: key the cue on the running posture alone ({@code fullCue()} returning NONE whenever no process runs) — the
     * switched-on-but-not-running assertion fails; key it on nothing (always NONE) and the running one fails too.
     */
    @Test
    @DisplayName("★ the cue is present iff the session's posture is full: ON (switched on, not running), RUNNING, NONE")
    void theCueFollowsTheSession() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        assertEquals(LocalAgentState.FullCue.NONE, state(d).fullCue(), "nothing observed: no cue");
        d.post(new SessionEvents.LocalAgentConsentChanged(LocalAgentSessionTest.consent()));
        assertEquals(LocalAgentState.FullCue.NONE, state(d).fullCue(), "the default posture: no cue");

        d.post(new SessionEvents.LocalAgentConsentChanged(full(LocalAgentPosture.FullMode.DEFAULT)));
        assertEquals(LocalAgentState.FullCue.ON, state(d).fullCue(), "switched on, not running");
        assertFalse(state(d).strongCue());

        d.post(new SessionEvents.LocalAgentStartRequested(1, full(LocalAgentPosture.FullMode.AUTO)));
        var start = a.agentStarts.get(0);
        assertTrue(start.posture().isFull());
        assertEquals(LocalAgentState.FullCue.RUNNING, state(d).fullCue(), "running in the full posture");
        assertTrue(state(d).strongCue(), "auto asks least: the strong cue");
        assertEquals(LocalAgentPosture.FullMode.AUTO, state(d).cuePosture().fullMode());

        d.post(new SessionEvents.LocalAgentConsentChanged(new SessionEvents.LocalAgentConsent(false, true, false,
                "p", 0)));
        assertEquals(LocalAgentState.FullCue.NONE, state(d).fullCue(), "switched off: the session stops and the cue goes");
    }

    @Test
    @DisplayName("acceptEdits is strong; default and plan are standard")
    void strength() {
        for (var m : List.of(LocalAgentPosture.FullMode.values())) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = LocalAgentSessionTest.driver(a);
            d.post(new SessionEvents.LocalAgentConsentChanged(full(m)));
            boolean strong = m == LocalAgentPosture.FullMode.AUTO || m == LocalAgentPosture.FullMode.ACCEPT_EDITS;
            assertEquals(strong, state(d).strongCue(), m.name());
        }
    }
}
