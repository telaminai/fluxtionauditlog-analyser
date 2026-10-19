package telamin.fluxtion.audit.analyser.analyser.assistant.setup;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ChildEnvironment;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ChildPath;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeResolver;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ProcessTree;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * M77 (spec-ai-setup-wizard.md §8.4, §8.6, §9.1): the two read-only checks of <i>Set up AI</i>, as adapters that report
 * facts — {@code claude --version} (5 s) and {@code claude auth status --json} (10 s). The ONLY processes the wizard
 * starts before a registration button (S2); neither sends a prompt.
 *
 * <ul>
 *   <li><b>The same environment as Start, for the selected posture</b> (review R1-15): the child of the safe posture
 *       gets the safe environment; Full's gets {@code PATH} extended as {@link ChildPath#extend} does for Full, so a
 *       {@code claude} that is a Node script answers here exactly as it would at Start.</li>
 *   <li><b>A fresh EMPTY working folder per probe</b> (review R1-6, S10, ★ T22): {@code auth status} reads the working
 *       folder's {@code .claude/settings.json} (verified 2026-10-18), and a folder's settings are what folder trust is
 *       for. So each probe runs in a new, empty, owner-only directory under this process's private probe root — never
 *       the project root and never the process's own working directory — deleted afterwards.</li>
 *   <li><b>The parse boundary</b> is {@link ClaudeAuthParse}: nothing but enums leaves; raw output is never kept.</li>
 * </ul>
 *
 * <p>It decides nothing: {@code aiSetup} asks for a probe and decides what its result means.
 */
public final class ClaudeProbes {

    public static final Duration VERSION_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    /** Bounded output: a version line or a small JSON object. More is not read. */
    static final int OUTPUT_LIMIT = 64 * 1024;

    /** What one run of a command did. Its output is read by the parse boundary and then dropped. */
    public record Ran(int exit, String stdout, boolean timedOut) {
        @Override
        public String toString() {             // never the output
            return "ClaudeProbes.Ran[exit=" + exit + ", timedOut=" + timedOut + "]";
        }
    }

    /** Starts {@code command} in {@code dir} with the child environment; a seam for the tests (★ T22). */
    @FunctionalInterface
    public interface Runner {
        Ran run(List<String> command, Map<String, String> extraEnvironment, Set<String> removedEnvironment, Path dir,
                Duration timeout) throws IOException;
    }

    /** {@code claude --version}'s fact, before an id is put on it. */
    public record Version(SessionEvents.ProbeOutcome outcome, String version, Path executable) {
    }

    private final Runner runner;
    private final Supplier<Path> probeRoot;
    private final ClaudeCodeProvider provider = new ClaudeCodeProvider();

    public ClaudeProbes(Runner runner, Supplier<Path> probeRoot) {
        this.runner = runner;
        this.probeRoot = probeRoot;
    }

    /** The real runner: a process, its stdout bounded, its stderr drained and dropped, killed with its tree on time-out. */
    public static ClaudeProbes system(Supplier<Path> probeRoot) {
        return new ClaudeProbes(ClaudeProbes::runProcess, probeRoot);
    }

    /** Resolve {@code claude} (PATH, then the standard install places — the one resolver), then ask its version. */
    public Version version(Map<String, String> environment, Path home, boolean full) {
        ClaudeResolver.Resolution r = ClaudeResolver.resolve(environment, home);
        if (!r.found()) return new Version(SessionEvents.ProbeOutcome.NOT_FOUND, "", null);
        Ran ran = run(r.executable(), List.of("--version"), environment, home, full, VERSION_TIMEOUT);
        if (ran == null) return new Version(SessionEvents.ProbeOutcome.DID_NOT_RUN, "", r.executable());
        if (ran.timedOut()) return new Version(SessionEvents.ProbeOutcome.TIMED_OUT, "", r.executable());
        String v = ClaudeAuthParse.version(ran.exit(), ran.stdout());
        return v.isEmpty() ? new Version(SessionEvents.ProbeOutcome.DID_NOT_RUN, "", r.executable())
                : new Version(SessionEvents.ProbeOutcome.FOUND, v, r.executable());
    }

    /** {@code claude auth status --json} — {@code --json} always explicit, since a default can change (§9). */
    public ClaudeAuthParse.Auth auth(Map<String, String> environment, Path home, boolean full) {
        ClaudeResolver.Resolution r = ClaudeResolver.resolve(environment, home);
        if (!r.found()) {
            return new ClaudeAuthParse.Auth(SessionEvents.AuthOutcome.DID_NOT_RUN, false,
                    telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Method.NONE,
                    telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Plan.NONE,
                    telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.KeySource.NONE);
        }
        Ran ran = run(r.executable(), List.of("auth", "status", "--json"), environment, home, full, AUTH_TIMEOUT);
        if (ran == null) {
            return new ClaudeAuthParse.Auth(SessionEvents.AuthOutcome.DID_NOT_RUN, false,
                    telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Method.NONE,
                    telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Plan.NONE,
                    telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.KeySource.NONE);
        }
        return ClaudeAuthParse.auth(ran.exit(), ran.stdout(), ran.timedOut());
    }

    /** One probe: its own fresh empty folder, the posture's environment, the time-out; the folder removed after. */
    private Ran run(Path executable, List<String> args, Map<String, String> environment, Path home, boolean full,
                    Duration timeout) {
        LocalAgentPosture posture = full ? LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT)
                : LocalAgentPosture.ANALYSER_ASSISTANT;
        ChildPath.Extension path = full ? ChildPath.extend(environment, home) : null;
        Map<String, String> extra = provider.environment(posture, path);
        Set<String> removed = ChildEnvironment.removed(environment, posture);
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(executable.toString());
        command.addAll(args);
        Path dir = null;
        try {
            dir = freshEmptyDirectory(probeRoot.get());
            return runner.run(List.copyOf(command), extra, removed, dir, timeout);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (dir != null) deleteQuietly(dir);
        }
    }

    /**
     * S10: a new directory under {@code root}, owner-only where the file system allows, holding nothing — so none of
     * the three trust files (a project's {@code .claude/settings.json}, {@code .claude/settings.local.json},
     * {@code .mcp.json}) can be read by the probe.
     */
    static Path freshEmptyDirectory(Path root) throws IOException {
        Files.createDirectories(root);
        Path dir;
        try {
            dir = Files.createTempDirectory(root, "probe-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException notPosix) {
            dir = Files.createTempDirectory(root, "probe-");
        }
        try (var listing = Files.list(dir)) {
            if (listing.findAny().isPresent()) throw new IOException("the probe folder is not empty");
        }
        return dir;
    }

    private static void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    // a probe folder left behind is empty and private; nothing depends on its removal
                }
            });
        } catch (IOException ignore) {
            // as above
        }
    }

    /** The real process: environment composed as for Start ({@link ChildEnvironment#compose}). */
    static Ran runProcess(List<String> command, Map<String, String> extra, Set<String> removed, Path dir,
                          Duration timeout) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        Map<String, String> env = pb.environment();
        Map<String, String> composed = ChildEnvironment.compose(env, removed, extra);
        env.clear();
        env.putAll(composed);
        pb.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
        Process p = pb.start();
        Thread drain = new Thread(() -> discard(p.getErrorStream()), "analyser-setup-probe-stderr");
        drain.setDaemon(true);
        drain.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread read = new Thread(() -> bounded(p.getInputStream(), out), "analyser-setup-probe-stdout");
        read.setDaemon(true);
        read.start();
        boolean done;
        try {
            done = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            done = false;
        }
        if (!done) {
            ProcessTree.terminate(p.toHandle(), Duration.ofSeconds(1));
            return new Ran(-1, "", true);
        }
        try {
            read.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String text;
        synchronized (out) {
            text = out.toString(StandardCharsets.UTF_8);
        }
        return new Ran(p.exitValue(), text, false);
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "NUL" : "/dev/null");
    }

    private static void bounded(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[4096];
        try (in) {
            int n;
            while ((n = in.read(buf)) > 0) {
                synchronized (out) {
                    if (out.size() < OUTPUT_LIMIT) out.write(buf, 0, Math.min(n, OUTPUT_LIMIT - out.size()));
                }
            }
        } catch (IOException ignore) {
            // the process ended; what was read is the answer
        }
    }

    private static void discard(InputStream in) {
        byte[] buf = new byte[4096];
        try (in) {
            while (in.read(buf) > 0) {
                // stderr is never read: it is not the answer, and it may carry what the parse boundary drops
            }
        } catch (IOException ignore) {
            // ended
        }
    }
}
