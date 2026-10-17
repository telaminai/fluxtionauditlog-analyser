package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 §4.7, §4.8, §4.3 — process hygiene with REAL child processes (a shell, a JVM), never a {@code claude}: the tree
 * kill, the stderr drain and redaction, the session lock across two JVMs, and the resolver with the GUI's PATH.
 */
@DisabledOnOs(OS.WINDOWS)   // Windows is open P1a acceptance (§11): no SIGTERM, no /bin/sh
class LocalAgentProcessTest {

    @TempDir
    Path tmp;

    /** A child that ignores SIGTERM, with a grandchild that does too: only the snapshot-then-kill removes both. */
    @Test
    @DisplayName("the process tree is gone after a stop, with a real child that ignores SIGTERM (snapshot before kill)")
    void theTreeIsGone() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        AgentProcess p = new OsAgentLauncher().start(List.of("/bin/sh", "-c",
                        "trap '' TERM; (trap '' TERM; sleep 60) & echo started; wait"),
                Map.of(), tmp, lines::add);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (lines.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20);
        ProcessHandle root = ProcessHandle.of(p.pid()).orElseThrow();
        List<ProcessHandle> before = root.descendants().toList();
        assertFalse(before.isEmpty(), "the tree has descendants");
        p.terminateTree(Duration.ofMillis(300));
        p.onExit().get(10, TimeUnit.SECONDS);
        for (ProcessHandle h : before) {
            h.onExit().get(10, TimeUnit.SECONDS);
            assertFalse(h.isAlive(), "a descendant survived (an orphan): " + h.pid());
        }
        assertFalse(root.isAlive());
    }

    @Test
    @DisplayName("stderr above 64 KB does not deadlock the child; what is kept is a bounded, redacted tail")
    void stderrIsDrainedBoundedAndRedacted() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        AgentProcess p = new OsAgentLauncher().start(List.of("/bin/sh", "-c",
                        "i=0; while [ $i -lt 3000 ]; do echo 'noise noise noise noise noise noise noise noise' >&2; "
                                + "i=$((i+1)); done; echo 'token=sk-ant-SECRETSECRET1234 failed' >&2; echo done"),
                Map.of(), tmp, lines::add);
        assertEquals(0, p.onExit().get(20, TimeUnit.SECONDS), "the child finished: the pipe never filled");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!lines.contains("done") && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(lines.contains("done"));
        Thread.sleep(100);
        String tail = p.stderrTail();
        assertTrue(tail.length() <= OsAgentLauncher.STDERR_TAIL, "bounded: " + tail.length());
        assertFalse(tail.contains("SECRETSECRET"), "redacted: " + LocalAgentAdapter.lastLine(tail));
        assertTrue(LocalAgentAdapter.lastLine(tail).startsWith("token="), LocalAgentAdapter.lastLine(tail));
    }

    @Test
    @DisplayName("the child gets the analyser's environment plus the CLAUDE.md flag, in the confirmed folder")
    void environmentAndFolder() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        AgentProcess p = new OsAgentLauncher().start(List.of("/bin/sh", "-c",
                "echo \"$CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD\"; pwd"), new ClaudeCodeProvider().environment(),
                tmp, lines::add);
        p.onExit().get(10, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (lines.size() < 2 && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals("1", lines.get(0));
        assertEquals(tmp.toRealPath().toString(), Path.of(lines.get(1)).toRealPath().toString());
    }

    /**
     * ★ Witness: make {@code tryAcquire} ignore {@code tryLock()} returning null (treat it as acquired) — the second
     * JVM's lock is "taken" twice and the first assertion fails.
     */
    @Test
    @DisplayName("★ the lock: a second analyser JVM cannot take a held session; it is released when the holder dies")
    void theLockAcrossTwoJvms() throws Exception {
        Path dir = tmp.resolve("local-agent");
        String session = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                LockHolder.class.getName(), dir.toString(), session).redirectErrorStream(true).start();
        try {
            var out = new java.io.BufferedReader(new java.io.InputStreamReader(holder.getInputStream()));
            assertEquals("held", out.readLine(), "the other analyser holds it");
            assertTrue(LocalAgentLock.tryAcquire(dir, session).isEmpty(),
                    "This conversation is open in another analyser window.");
        } finally {
            holder.destroyForcibly();          // a crash: no clean release
            holder.waitFor(10, TimeUnit.SECONDS);
        }
        var after = LocalAgentLock.tryAcquire(dir, session);
        assertTrue(after.isPresent(), "an operating-system lock is released when its holder dies");
        after.get().close();
        var again = LocalAgentLock.tryAcquire(dir, session);
        assertTrue(again.isPresent(), "and on stop (close)");
        again.get().close();
    }

    /** The other analyser instance: takes the lock and holds it until killed. */
    public static final class LockHolder {
        public static void main(String[] args) throws Exception {
            var lock = LocalAgentLock.tryAcquire(Path.of(args[0]), args[1]);
            System.out.println(lock.isPresent() ? "held" : "busy");
            System.out.flush();
            Thread.sleep(60_000);
        }
    }

    @Test
    @DisplayName("the resolver finds ~/.local/bin/claude with the minimal GUI PATH, and lists every place it searched")
    void theResolverWithTheGuiPath() throws Exception {
        Path bin = Files.createDirectories(tmp.resolve(".local/bin"));
        Path claude = bin.resolve("claude");
        Files.writeString(claude, "#!/bin/sh\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        var found = ClaudeResolver.resolve("/usr/bin:/bin:/usr/sbin:/sbin", ":", false, tmp, Map.of());
        assertEquals(claude.toAbsolutePath().normalize(), found.executable());

        org.junit.jupiter.api.Assumptions.assumeFalse(Files.exists(Path.of("/opt/homebrew/bin/claude"))
                || Files.exists(Path.of("/usr/local/bin/claude")), "a claude in a standard location would be found");
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        var missing = ClaudeResolver.resolve("/nonexistent-a:/nonexistent-b", ":", false, empty,
                Map.of("NPM_CONFIG_PREFIX", "/nonexistent-npm"));
        assertFalse(missing.found());
        assertEquals(List.of("/nonexistent-a", "/nonexistent-b", empty.resolve(".local/bin").toString(),
                "/opt/homebrew/bin", "/usr/local/bin", "/nonexistent-npm/bin", empty.resolve(".npm-global/bin").toString()),
                missing.searched(), "every place, in order");
        assertTrue(missing.searchedText().contains("/opt/homebrew/bin"));
    }
}
