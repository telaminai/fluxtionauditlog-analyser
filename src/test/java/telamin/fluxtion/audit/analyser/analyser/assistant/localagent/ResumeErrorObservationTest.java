package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResumeErrorObservationTest {
    @Test
    void onlyThePublishedUnknownSessionErrorQualifies() {
        assertTrue(LocalAgentAdapter.resumeSessionUnknown("No conversation found with session ID: DEMO-session"));
        assertTrue(LocalAgentAdapter.resumeSessionUnknown("warning\n No conversation found with session ID: DEMO-session\n"));
        assertFalse(LocalAgentAdapter.resumeSessionUnknown("authentication failed"));
        assertFalse(LocalAgentAdapter.resumeSessionUnknown("session failed to start"));
        assertFalse(LocalAgentAdapter.resumeSessionUnknown("example: No conversation found with session ID: DEMO-session"));
        assertFalse(LocalAgentAdapter.resumeSessionUnknown(null));
    }

    @Test
    void theAdapterClockTicksOnlyWhileItsRequestedStartIsCurrent(@TempDir Path home) throws Exception {
        var agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        agent.rewrite = line -> line.contains("control_response") ? null : line;
        var h = new Harness(agent, home);
        h.start();
        assertEquals("HANDSHAKE", h.state().phase());
        h.clock.advance(3_000);
        assertEquals(3, h.facts(SessionEvents.LocalAgentActivityTick.class).size());
        assertEquals("Checking Claude Code is ready… 0:03", h.state().extra().guide().currentStep());
        h.driver.post(new SessionEvents.LocalAgentStopRequested("test"));
        h.clock.advance(5_000);
        assertEquals(3, h.facts(SessionEvents.LocalAgentActivityTick.class).size(), "the old clock stops by its scope");
    }

    @Test
    void anUnknownResumeExitIsObservedByTheRealAdapter(@TempDir Path home) throws Exception {
        var agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        agent.rewrite = line -> line.contains("command_lifecycle") && line.contains("completed")
                || line.contains("\"type\": \"result\"") ? null : line;
        var h = new Harness(agent, home);
        h.start();
        h.send("Check the DEMO validation.");
        agent.last().exit(143);
        agent.rewrite = line -> line.contains("control_response") ? null : line;
        h.start();
        assertEquals("HANDSHAKE", h.state().phase());
        agent.last().stderr = "No conversation found with session ID: DEMO-session";
        agent.last().exit(1);
        assertTrue(h.facts(SessionEvents.LocalAgentExited.class).getLast().resumeSessionUnknown());
        assertEquals(3, agent.processes.size(), "one fresh-chat fallback, not a generic reconnect loop");
        assertTrue(h.others.stream().anyMatch(e -> e instanceof
                telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.PlaceRecoveryDraftEffect));
    }
}
