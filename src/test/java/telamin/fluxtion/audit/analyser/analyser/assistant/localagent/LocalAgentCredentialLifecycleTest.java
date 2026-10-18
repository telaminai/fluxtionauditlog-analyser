package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review of PR #145 (R1, R2): when a hosted credential lives and dies, and what a Stop that overtakes its own spawn may
 * still do. The real adapter and generated session processor; a FAKE process ({@link FixtureAgent}). Each ★ test names
 * the mutation it was confirmed red against.
 */
class LocalAgentCredentialLifecycleTest {

    @TempDir
    Path home;

    /** A worker that runs nothing until the test says so, in the order the test chooses: the app's is a thread pool. */
    private static final class DeferredWorker implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable r) {
            tasks.add(r);
        }

        Runnable take(int i) {
            return tasks.remove(i);
        }
    }

    private Harness bound() throws Exception {
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        Harness h = new Harness(agent, home);
        h.start();
        assertEquals("READY", h.state().phase(), "bound: " + h.state().reason());
        return h;
    }

    private long endpointFiles() throws Exception {
        Path dir = LocalAgentLock.directory(home);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("endpoint-")).count();
        }
    }

    private long lockFiles() throws Exception {
        Path dir = LocalAgentLock.directory(home);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".lock")).count();
        }
    }

    // ---- R1: a Stop that overtakes its spawn ------------------------------------------------------------------------

    /**
     * ★ Witness: remove the {@code run.killed} check before the lock — a lock file appears (lock files are never deleted,
     * so one existing proves the lock was taken); remove both checks — a process starts as well.
     */
    @Test
    @DisplayName("★ R1: a Stop whose terminate runs before its spawn starts nothing: no lock, no endpoint file, no process")
    void aStopThatOvertakesItsSpawnStartsNothing() throws Exception {
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        DeferredWorker worker = new DeferredWorker();
        Harness h = new Harness(agent, home, () -> Runnable::run, worker);
        h.start();
        assertEquals("SPAWNING", h.state().phase());
        assertEquals(1, worker.tasks.size(), "the spawn is queued");
        h.driver.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        assertEquals("OFF", h.state().phase());
        assertEquals(2, worker.tasks.size(), "the terminate is queued behind the spawn");
        worker.take(1).run();                       // the pool runs the terminate first
        worker.take(0).run();                       // then the spawn carries on
        h.drain();
        assertTrue(agent.processes.isEmpty(), "no process was launched for the stopped generation");
        assertEquals(0, endpointFiles(), "no endpoint file was written");
        assertEquals(0, lockFiles(), "the lock was never taken");
        assertTrue(worker.tasks.isEmpty());
    }

    /** ★ Witness: remove the {@code run.killed} check just before {@code launcher.start} — a process starts. */
    @Test
    @DisplayName("★ R1: a Stop that lands after the credential is minted but before the launch launches nothing and revokes it")
    void aStopJustBeforeTheLaunchLaunchesNothing() throws Exception {
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        DeferredWorker worker = new DeferredWorker();
        Harness h = new Harness(agent, home, () -> Runnable::run, worker);
        h.start();
        assertEquals("SPAWNING", h.state().phase());
        String[] credential = new String[1];
        // the spawn reads the clock once, after the lock, the credential and the endpoint file, just before the launch
        h.clock.onNextRead = () -> {
            try (Stream<Path> s = Files.list(LocalAgentLock.directory(home))) {
                Path file = s.filter(p -> p.getFileName().toString().startsWith("endpoint-")).findFirst().orElseThrow();
                credential[0] = new RestEndpointFile(file).read().hosted();
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            assertEquals(1, h.credentials.generationOf(credential[0]), "the credential is live at this point");
            h.driver.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        };
        worker.take(0).run();
        h.drain();
        assertNotNull(credential[0], "the hook ran inside the spawn");
        assertTrue(agent.processes.isEmpty(), "no process was launched for the stopped generation");
        assertEquals(-1, h.credentials.generationOf(credential[0]), "the credential is revoked");
        assertEquals(0, endpointFiles(), "the endpoint file is removed");
        while (!worker.tasks.isEmpty()) worker.take(0).run();       // the queued terminate finds nothing to stop
        assertTrue(agent.processes.isEmpty());
    }

    /** A re-Start after an overtaken spawn is not refused as LOCKED: the stopped spawn released (never took) the lock. */
    @Test
    @DisplayName("R1: after an overtaken spawn, a re-Start of the same conversation starts normally")
    void aReStartAfterAnOvertakenSpawnIsNotLocked() throws Exception {
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        DeferredWorker worker = new DeferredWorker();
        Harness h = new Harness(agent, home, () -> Runnable::run, worker);
        h.start();
        h.driver.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        worker.take(1).run();
        worker.take(0).run();
        h.drain();
        h.start();
        while (!worker.tasks.isEmpty()) worker.take(0).run();
        h.drain();
        assertEquals("READY", h.state().phase(), h.state().reason());
        assertEquals(1, agent.processes.size(), "only the re-Start launched");
    }

    // ---- R2: the revokes and the tool-count comparison --------------------------------------------------------------

    /** ★ Witness: remove {@code credentials.revoke} from the adapter's KILL path — the credential lives through the grace. */
    @Test
    @DisplayName("★ R2: the credential is revoked at Stop, while the tree is still ending")
    void revokedAtStopBeforeTheTreeEnds() throws Exception {
        Harness h = bound();
        h.agent.exitOnKill = false;                 // the tree takes its SIGTERM grace to end
        FixtureAgent.Proc p = h.agent.last();
        String hosted = new RestEndpointFile(p.endpointFile).read().hosted();
        assertEquals(1, h.credentials.generationOf(hosted));
        h.driver.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertEquals(1, p.terminated, "the tree was signalled");
        assertFalse(p.onExit().isDone(), "and has not ended yet");
        assertEquals(-1, h.credentials.generationOf(hosted), "revoked before the tree ended");
    }

    /** ★ Witness: remove {@code credentials.revoke} from the adapter's {@code cleanup} — a crashed process's credential lives on. */
    @Test
    @DisplayName("★ R2: the credential is revoked when the process exits by itself")
    void revokedWhenTheProcessExitsByItself() throws Exception {
        Harness h = bound();
        FixtureAgent.Proc p = h.agent.last();
        String hosted = new RestEndpointFile(p.endpointFile).read().hosted();
        assertEquals(1, h.credentials.generationOf(hosted));
        p.exit(1);                                  // a crash: nobody pressed Stop
        h.drain();
        assertEquals(0, p.terminated, "nothing signalled it");
        assertEquals(-1, h.credentials.generationOf(hosted), "revoked on the process's own exit");
    }

    /** ★ Witness: remove {@code tools.size() != expected.size()} from {@code LocalAgentPosture.check} — a duplicate passes. */
    @Test
    @DisplayName("★ R2: a tool reported twice is a tools mismatch, not the expected set")
    void aRepeatedToolIsAToolsMismatch() {
        List<String> tools = new ArrayList<>(LocalAgentPosture.expectedTools());
        List<String> servers = List.of(LocalAgentPosture.HOSTED_SERVER + ":connected");
        assertEquals(LocalAgentPosture.Verdict.OK, LocalAgentPosture.check("s", LocalAgentPosture.PERMISSION_MODE,
                tools, servers, LocalAgentPosture.REQUIRED_CAPABILITIES, LocalAgentPosture.digest("s")), "the control");
        tools.add(tools.get(0));
        assertEquals(LocalAgentPosture.Verdict.TOOLS_MISMATCH, LocalAgentPosture.check("s",
                LocalAgentPosture.PERMISSION_MODE, tools, servers, LocalAgentPosture.REQUIRED_CAPABILITIES,
                LocalAgentPosture.digest("s")));
    }
}
