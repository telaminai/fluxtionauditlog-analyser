package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.*;

/** M81 C1: Restart recovers the process without silently discarding its conversation. */
class LocalAgentRestartRecoveryTest {

    /** ★ C1 witness: the old running() no-op leaves HANDSHAKE unchanged and never kills or spawns. */
    @Test
    void restartKillsAStuckHandshakeBeforeSpawning() {
        for (String phase : List.of("HANDSHAKE", "SPAWNING", "BINDING", "STOPPING")) {
            var a = new FakeSessionAdapter();
            var d = driver(a);
            d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
            if (!phase.equals("SPAWNING"))
                d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
            if (phase.equals("BINDING"))
                d.post(new SessionEvents.LocalAgentInitialized(1, true, "default", 7));
            if (phase.equals("STOPPING")) {
                bind(d, a.agentStarts.getFirst());
                send(d, a, 10);
                d.post(new SessionEvents.LocalAgentStopRequested("test"));
            }
            assertEquals(phase, state(d).phase());
            a.performed.clear();
            d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
            assertEquals(1, kills(a).size(), "Restart kills the stuck " + phase + " generation");
            assertEquals(2, a.agentStarts.size(), "Restart spawns after " + phase);
            var second = a.agentStarts.getLast();
            assertEquals(2, second.generation());
            assertEquals("SPAWNING", state(d).phase());
            assertTrue(a.performed.indexOf(kills(a).getFirst()) < a.performed.indexOf(second),
                    "the old generation is stopped before the new spawn");
            assertTrue(state(d).answer().accepted());
            d.post(new SessionEvents.LocalAgentExited(1, 143, 0));
            assertEquals("SPAWNING", state(d).phase(), "the old exit cannot close the replacement");
        }
    }

    /** ★ C2 witness: beginConversation() on external exit fails the retained-resumable assertion. */
    @Test
    void externalSigtermRestartResumesTheSameConversation() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        var first = startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentExited(first.generation(), 143, 0));
        assertEquals("Claude Code was stopped from outside the analyser (signal 15).", state(d).reason());
        assertTrue(state(d).resumable(), "an external SIGTERM does not discard the conversation");
        assertTrue(kills(a).isEmpty(), "the analyser did not request the external exit");
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
        var resumed = a.agentStarts.getLast();
        assertTrue(resumed.resume());
        assertEquals(first.sessionId(), resumed.sessionId(), "Restart resumes the same session id");
        assertNotEquals(first.nonce(), resumed.nonce());
        assertEquals(1, state(d).conversation());
    }

    @Test
    void signalWordsAreNarrowAndOnlyForUnrequestedExits() {
        for (int code : List.of(137, 1, 130)) {
            var a = new FakeSessionAdapter();
            var d = driver(a);
            startToReady(d, a);
            d.post(new SessionEvents.LocalAgentExited(1, code, 0));
            assertEquals(code == 137 ? "Claude Code was stopped from outside the analyser (signal 9)."
                    : "Claude Code exited (code " + code + ")", state(d).reason());
        }
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        String before = state(d).reason();
        d.post(new SessionEvents.LocalAgentExited(1, 143, 0));
        assertEquals(before, state(d).reason(), "a requested kill is never called external");
    }

    @Test
    void healthyStartIsVisibleAndDoesNotReplaceTheProcess() {
        for (boolean busy : List.of(false, true)) {
            var a = new FakeSessionAdapter();
            var d = driver(a);
            startToReady(d, a);
            if (busy) send(d, a, 10);
            long ticket = state(d).openTicket();
            int entries = state(d).entries().size();
            d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
            assertEquals(1, a.agentStarts.size());
            assertTrue(kills(a).isEmpty());
            assertEquals(ticket, state(d).openTicket());
            assertEquals(busy ? "BUSY" : "READY", state(d).phase());
            assertEquals(entries + 1, state(d).entries().size());
            var note = state(d).entries().getLast();
            assertEquals("ALREADY_RUNNING", note.status());
            assertEquals("Claude Code is already running.", note.detail());
        }
    }

    @Test
    void rateLimitIsExplicitlyAdvisoryAndDoesNotPreventRestartOrNewChat() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        d.post(new SessionEvents.LocalAgentNoted(1, 0, SessionEvents.AgentNote.RATE_LIMITED, 1_800_000_000));
        assertTrue(state(d).entries().getLast().detail().contains("This does not block Start or Restart."));
        d.post(new SessionEvents.LocalAgentExited(1, 1, 0));
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent()));
        assertEquals(2, a.agentStarts.size());
        d.post(new SessionEvents.LocalAgentNoted(2, 0, SessionEvents.AgentNote.RATE_LIMITED, 0));
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        d.post(new SessionEvents.LocalAgentStartRequested(3, consent()));
        assertEquals(3, a.agentStarts.size());
        assertTrue(state(d).answer().accepted());
    }
}
