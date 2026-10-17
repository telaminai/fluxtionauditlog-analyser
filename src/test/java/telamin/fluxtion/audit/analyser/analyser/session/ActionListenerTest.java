package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;
import telamin.fluxtion.audit.analyser.analyser.session.node.ActionListener;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Holistic review B5 and B2 (2026-10-17) on the REAL processor: the action listener's lifecycle is actionListener's —
 * the REST switch, a session needing the hosted bridge and releasing it, and the person's choice to close external
 * access while a full session runs. Facts in; the listener effects and the status words out.
 */
class ActionListenerTest {

    static List<String> statuses(FakeSessionAdapter a) {
        return a.performed.stream().filter(e -> e instanceof SessionEffects.ShowStatusEffect)
                .map(e -> ((SessionEffects.ShowStatusEffect) e).text()).toList();
    }

    /**
     * ★ B5. Witness: release the listener whatever the switch (keepListening false in {@code released}) — the person's
     * REST server is stopped when the session ends.
     */
    @Test
    @DisplayName("★ the switch, a session's need and its release decide the listener; the frame decides nothing")
    void theLifecycleIsTheNodes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.RestSwitchObserved(false));
        assertTrue(a.listenerConfigs.isEmpty(), "off and nothing needs it: nothing to change");
        var start = LocalAgentSessionTest.startToReady(d, a);
        assertFalse(a.agentServes.get(0).external(), "REST off: a hosted-only listener for the session");
        assertTrue(statuses(a).contains(ActionListener.HOSTED_ONLY));
        assertEquals(List.of("serveBridge", "start"), a.agentOrder.subList(0, 2), "the listener before the process");

        d.post(new SessionEvents.RestSwitchObserved(true));
        var on = a.listenerConfigs.get(a.listenerConfigs.size() - 1);
        assertTrue(on.listen() && on.external(), "REST on while the session runs: opened to external clients");
        d.post(new SessionEvents.RestSwitchObserved(false));
        var off = a.listenerConfigs.get(a.listenerConfigs.size() - 1);
        assertTrue(off.listen() && !off.external(), "REST off while the session runs: the session keeps its listener");
        assertTrue(statuses(a).contains(ActionListener.EXTERNAL_CLOSED), statuses(a).toString());

        d.post(new SessionEvents.RestSwitchObserved(true));
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        d.post(new SessionEvents.LocalAgentExited(start.generation(), 0, 0));
        var release = a.agentReleases.get(a.agentReleases.size() - 1);
        assertTrue(release.keepListening() && release.external(), "the person's REST server stays when the session ends");
        assertEquals(0, d.snapshot().listener().bridgeFor());

        d.post(new SessionEvents.RestSwitchObserved(false));
        var stop = a.listenerConfigs.get(a.listenerConfigs.size() - 1);
        assertFalse(stop.listen(), "off and nothing needs it: stopped");
        assertTrue(statuses(a).contains(ActionListener.STOPPED));
    }

    /**
     * ★ B2: a FULL session started with external access open can be driven by its own tools as an external client; the
     * panel is offered "Close external access while this session runs", the person's choice closes it for that session
     * only, and it opens again when the session ends. Witness: compute offerClose without fullSession — the default
     * posture is offered it too; or ignore ExternalAccessChosen — nothing closes.
     */
    @Test
    @DisplayName("★ B2: full + external REST → the offer; choosing it closes external access for that session only")
    void aFullSessionIsOfferedToCloseExternalAccess() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.RestSwitchObserved(true));
        var start = LocalAgentFullPostureTest.startFull(d, a, FullMode.DEFAULT);
        ListenerState s = d.snapshot().listener();
        assertTrue(s.external() && s.fullSession() && s.offerClose(), String.valueOf(s));
        String limits = telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.limitsText(d.snapshot().localAgent(), s);
        assertTrue(limits.endsWith(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.EXTERNAL_OPEN_TO_TOOLS),
                "the panel's limits line says external REST is open to the session's tools: " + limits);

        d.post(new SessionEvents.ExternalAccessChosen(start.generation() + 1, true));
        assertTrue(d.snapshot().listener().offerClose(), "an answer for another session is stale");
        d.post(new SessionEvents.ExternalAccessChosen(start.generation(), true));
        var closed = a.listenerConfigs.get(a.listenerConfigs.size() - 1);
        assertTrue(closed.listen() && !closed.external(), "closed for external clients; the session keeps its listener");
        s = d.snapshot().listener();
        assertTrue(s.closedForSession() && !s.offerClose() && !s.external(), String.valueOf(s));
        assertTrue(statuses(a).contains(ActionListener.CLOSED_FOR_SESSION));
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.limitsText(d.snapshot().localAgent(), s)
                .endsWith(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.EXTERNAL_CLOSED_FOR_SESSION));

        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        d.post(new SessionEvents.LocalAgentExited(start.generation(), 0, 0));
        var release = a.agentReleases.get(a.agentReleases.size() - 1);
        assertTrue(release.keepListening() && release.external(), "the session ended: external access opens again");
        assertFalse(d.snapshot().listener().closedForSession());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = LocalAgentSessionTest.driver(b);
        e.post(new SessionEvents.RestSwitchObserved(true));
        LocalAgentSessionTest.startToReady(e, b);
        assertFalse(e.snapshot().listener().offerClose(), "the default posture's tools cannot reach the endpoint file");
    }
}
