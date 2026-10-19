package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.*;

class LocalAgentRecoveryDraftTest {
    static final String FIXED_LINE = "This was interrupted part-way. Check what has already changed before continuing.";

    @Test
    void changedFolderStartsANewConversationWithAnUnsentRecoveryDraft() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        var first = startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentExited(1, 143, 0));
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent(true, true, false, "posture-b", BUDGET)));
        assertEquals(2, a.agentStarts.size(), "the changed folder starts a fresh conversation after disclosing why");
        var second = a.agentStarts.getLast();
        assertFalse(second.resume());
        assertNotEquals(first.sessionId(), second.sessionId());
        assertEquals("posture-b", second.postureRef());
        assertEquals(1, a.recoveryPlaced.size());
        assertEquals(10, a.recoveryPlaced.getFirst().draftRef());
        assertEquals(FIXED_LINE, a.recoveryPlaced.getFirst().fixedLine());
        assertTrue(state(d).entries().stream().anyMatch(e -> e.detail().contains("can't be resumed here")));
        bind(d, second);
        assertEquals(1, a.agentSends.size(), "the recovery draft is never sent when the new process becomes ready");
    }

    @Test
    void unknownResumedIdFallsBackOnceAndPlacesTheInterruptedRequest() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentExited(1, 143, 0));
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
        assertTrue(a.agentStarts.getLast().resume());
        d.post(new SessionEvents.LocalAgentExited(2, 1, 0, true));
        assertEquals(3, a.agentStarts.size(), "an explicitly unknown resumed id falls back once");
        assertFalse(a.agentStarts.getLast().resume());
        assertEquals(1, a.recoveryPlaced.size());
        assertEquals(10, a.recoveryPlaced.getFirst().draftRef());
        d.post(new SessionEvents.LocalAgentExited(3, 1, 0, true));
        assertEquals(3, a.agentStarts.size(), "a failed new chat never retries itself");
    }

    @Test
    void successfulResumePlacesNothingAndAGenericFailureDoesNotRetry() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        var first = startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentExited(1, 143, 0));
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
        var resumed = a.agentStarts.getLast();
        bind(d, resumed);
        assertEquals(first.sessionId(), resumed.sessionId());
        assertTrue(a.recoveryPlaced.isEmpty());
        d.post(new SessionEvents.LocalAgentExited(2, 1, 0));
        assertEquals(2, a.agentStarts.size(), "an unspecified crash/auth failure does not start a new chat");
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertTrue(a.recoveryPlaced.isEmpty(), "successful resume consumes the old interrupted request");
    }

    @Test
    void stopDeadlinePreservesTheRequestForTheNextNewChatOnlyOnce() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        var sent = send(d, a, 27);
        d.post(new SessionEvents.LocalAgentStopRequested("test"));
        d.post(new SessionEvents.LocalAgentStopDeadline(1, sent.ticket(), SessionEvents.StopDeadline.ACK));
        assertTrue(a.recoveryPlaced.isEmpty());
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertEquals(1, a.recoveryPlaced.size());
        assertEquals(27, a.recoveryPlaced.getFirst().draftRef());
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertEquals(1, a.recoveryPlaced.size(), "the interrupted request is consumed once");
        assertEquals(1, a.agentSends.size());
    }

    @Test
    void theStartClockCoversEveryPhaseAndRejectsOldGenerationTicks() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        assertEquals(1, a.activityClocks.size(), "the node requests one clock for the start window");
        d.post(new SessionEvents.LocalAgentActivityTick(1, 0, 1_000));
        assertEquals("Starting Claude Code…", state(d).extra().guide().currentStep());
        d.post(new SessionEvents.LocalAgentActivityTick(1, 0, 4_000));
        assertEquals("Starting Claude Code… 0:03", state(d).extra().guide().currentStep());
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        assertEquals("Checking Claude Code is ready…", state(d).extra().guide().currentStep());
        d.post(new SessionEvents.LocalAgentInitialized(1, true, "default", 7));
        assertEquals("Connecting to the analyser's tools…", state(d).extra().guide().currentStep());
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 0, 90_000));
        assertEquals(0, state(d).extra().guide().secondsSinceLastEvent(), "a replaced clock cannot age its successor");
        bind(d, a.agentStarts.getLast());
        assertEquals("", state(d).extra().guide().currentStep());
    }

    /** ★ C5 witness: replace the TIMEOUT sentence with the generic exited sentence. */
    @Test
    void theStartDeadlineRendersItsOwnTimeoutBanner() throws Exception {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentStartDeadline(1));
        assertEquals("TIMEOUT", state(d).verdict());
        String banner = "Claude Code did not finish starting within 35 s, so it was stopped";
        assertEquals(banner, state(d).extra().guide().banner(), "TIMEOUT has its own banner, not EXITED or ALREADY_RUNNING");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var panel = new telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel(
                    new telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript());
            panel.render(state(d));
            assertTrue(visibleText(panel, banner), "the panel renders the node's timeout banner");
        });
    }

    private static boolean visibleText(java.awt.Container root, String expected) {
        for (java.awt.Component c : root.getComponents()) {
            if (!c.isVisible()) continue;
            if (c instanceof javax.swing.text.JTextComponent text && expected.equals(text.getText())) return true;
            if (c instanceof java.awt.Container child && visibleText(child, expected)) return true;
        }
        return false;
    }
}
