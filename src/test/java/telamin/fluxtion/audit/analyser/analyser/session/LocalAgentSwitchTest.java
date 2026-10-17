package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (the simplified Move, owner 2026-10-17; spec §5.3 adjusted): a PERSON's project switch while Claude Code runs is
 * held and the person asked — Stop and get Start ready / Keep it running / Cancel. No handoff report (deferred). The node
 * decides; the real processor; a recording adapter.
 */
class LocalAgentSwitchTest {

    static final String TARGET = "/work/other/.analyser/project.fluxtion-settings";
    static final SessionEvents.ProjectAudience PERSON =
            new SessionEvents.ProjectAudience(SessionEvents.OperationOrigin.PERSON, true);

    static void personSwitch(SessionDriver d) {
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), TARGET, TransitionKind.EXPLICIT_SWITCH, "menu",
                null, PERSON));
    }

    static long loads(FakeSessionAdapter a) {
        return a.performed.stream().filter(e -> e instanceof SessionEffects.LoadProfileEffect).count();
    }

    /** ★ Witness: never hold (holds() returns false) — the switch applies while Claude Code runs and no offer is made. */
    @Test
    @DisplayName("★ a person's switch is held while a process runs, and the offer is asked for; nothing loads yet")
    void aSwitchIsHeldWhileClaudeCodeRuns() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        personSwitch(d);
        assertEquals(1, a.switchOffers.size());
        assertEquals("other", a.switchOffers.get(0).targetName());
        assertEquals(0, loads(a), "held: nothing loads until the person answers");
        assertTrue(d.snapshot().localAgent().running());
    }

    @Test
    @DisplayName("with no process running the switch applies as today, with no offer")
    void withNoProcessTheSwitchApplies() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        personSwitch(d);
        assertTrue(a.switchOffers.isEmpty());
        assertEquals(1, loads(a));
        assertEquals(TARGET, a.appliedProfile);
    }

    /** ★ Witness: hold every switch (drop the person-origin check in holds) — the assistant's open is held. */
    @Test
    @DisplayName("★ an assistant's open {project} is never held")
    void anAssistantsOpenIsNeverHeld() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), TARGET, TransitionKind.EXPLICIT_SWITCH, "socket",
                new SessionEvents.AssistantActionOrigin(1, 1), new SessionEvents.ProjectAudience(
                SessionEvents.OperationOrigin.MACHINE, false)));
        assertTrue(a.switchOffers.isEmpty());
        assertEquals(1, loads(a));
    }

    /**
     * ★ Witness: release the switch without stopping (drop the kill in onSwitchChosen) — the process still runs; or
     * skip arming — the armed assertion fails.
     */
    @Test
    @DisplayName("★ Stop: the process stops, the switch applies, and Start is armed in the new project; nothing starts")
    void stopAndArm() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        personSwitch(d);
        int starts = a.agentStarts.size();
        d.post(new SessionEvents.LocalAgentSwitchChosen(a.switchOffers.get(0).request(),
                SessionEvents.SwitchChoice.STOP_AND_ARM));
        assertFalse(d.snapshot().localAgent().running(), "stopped");
        assertEquals(1, LocalAgentSessionTest.kills(a).size());
        assertEquals(1, loads(a), "the switch applies");
        assertEquals(TARGET, a.appliedProfile);
        assertEquals("armed.SWITCH", d.snapshot().localAgent().armedWhy());
        assertEquals(starts, a.agentStarts.size(), "an armed Start starts nothing");
    }

    @Test
    @DisplayName("Keep: the switch applies and the session keeps running in its own folder")
    void keep() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        personSwitch(d);
        d.post(new SessionEvents.LocalAgentSwitchChosen(a.switchOffers.get(0).request(), SessionEvents.SwitchChoice.KEEP));
        assertTrue(d.snapshot().localAgent().running());
        assertTrue(LocalAgentSessionTest.kills(a).isEmpty());
        assertEquals(1, loads(a));
        assertTrue(d.snapshot().localAgent().reason().contains("its own folder"));
    }

    /** ★ Witness: release on Cancel too — the cancelled switch loads. */
    @Test
    @DisplayName("★ Cancel drops the switch: nothing loads and the session runs on; a stale answer is refused")
    void cancel() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        personSwitch(d);
        long request = a.switchOffers.get(0).request();
        d.post(new SessionEvents.LocalAgentSwitchChosen(request, SessionEvents.SwitchChoice.CANCEL));
        assertEquals(0, loads(a));
        assertTrue(d.snapshot().localAgent().running());
        d.post(new SessionEvents.LocalAgentSwitchChosen(request, SessionEvents.SwitchChoice.KEEP));
        assertEquals(0, loads(a), "the offer was answered: a second answer releases nothing");
    }

    @Test
    @DisplayName("a newer switch while one is held replaces the target; the offer is made again")
    void aNewerSwitchReplacesTheHeldOne() {
        String other = "/work/third/.analyser/project.fluxtion-settings";
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET).withProfile(other);
        SessionDriver d = LocalAgentSessionTest.driver(a);
        LocalAgentSessionTest.startToReady(d, a);
        personSwitch(d);
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), other, TransitionKind.EXPLICIT_SWITCH, "menu", null,
                PERSON));
        assertEquals(2, a.switchOffers.size());
        d.post(new SessionEvents.LocalAgentSwitchChosen(a.switchOffers.get(0).request(), SessionEvents.SwitchChoice.KEEP));
        assertEquals(0, loads(a), "the first offer's answer is stale");
        d.post(new SessionEvents.LocalAgentSwitchChosen(a.switchOffers.get(1).request(), SessionEvents.SwitchChoice.KEEP));
        assertEquals(other, a.appliedProfile);
    }
}
