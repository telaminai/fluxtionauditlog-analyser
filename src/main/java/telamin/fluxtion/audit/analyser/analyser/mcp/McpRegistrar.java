package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * M77-3 (spec-ai-setup-wizard.md §9.3): Set up AI's registration of the analyser with Claude Code or Codex — the
 * adapter that performs {@code CheckMcpRegistrationEffect}, {@code ConfirmRegisterEffect} (the vectors it shows) and
 * {@code RegisterMcpEffect} (the same vectors, run). It decides nothing: the {@code aiSetup} node asks.
 *
 * <ul>
 *   <li><b>One resolver</b> (review R1-9, ★ T24): the client is the one {@link ClaudeResolver} finds — PATH, then the
 *       standard install places — never {@code ClaudeMcpClient.detect()}, which searches only the inherited PATH, so a
 *       Finder-launched analyser that says "Claude Code found" can also register.</li>
 *   <li><b>An empty folder</b> (S10, ★ T22): every {@code mcp get} and {@code mcp add} runs in a fresh, empty directory
 *       under the analyser's private scratch area — never the project root or this process's working directory, whose
 *       {@code .claude/settings.json} the CLI would read.</li>
 *   <li><b>The vector shown is the vector run</b> (S5, ★ T10): {@link #run} executes the lists {@link #vectors}
 *       returned, unchanged; {@code ClaudeMcpClient.run} refuses a list that is not its own executable's.</li>
 * </ul>
 * Raw CLI output never leaves the client classes ({@code ClaudeMcpClient.Result}'s rule).
 */
public class McpRegistrar {

    /** The client's registration, as its own CLI says. */
    public enum Found { PRESENT, ABSENT, INDETERMINATE }

    /** How a client process is started in its folder; a test can replace it. */
    @FunctionalInterface
    public interface Starter {
        Process start(List<String> command, Path folder) throws IOException;
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, String> env;
    private final Path home;
    private final Optional<McpLaunchCommand> launcher;
    private final Supplier<Path> scratchRoot;
    private final Starter starter;

    public McpRegistrar(Map<String, String> env, Path home, Optional<McpLaunchCommand> launcher,
                        Supplier<Path> scratchRoot) {
        this(env, home, launcher, scratchRoot, (command, folder) -> new ProcessBuilder(command)
                .directory(folder.toFile()).redirectErrorStream(true).start());
    }

    public McpRegistrar(Map<String, String> env, Path home, Optional<McpLaunchCommand> launcher,
                        Supplier<Path> scratchRoot, Starter starter) {
        this.env = env == null ? Map.of() : Map.copyOf(env);
        this.home = home;
        this.launcher = launcher == null ? Optional.empty() : launcher;
        this.scratchRoot = scratchRoot;
        this.starter = starter;
    }

    /** Whether this copy can be registered at all (a supported launcher: the running jar or the JBang install). */
    public boolean launcherAvailable() {
        return launcher.isPresent();
    }

    /** Where the one resolver finds the client — a file check; nothing is run. */
    public Optional<Path> executable(boolean codex) {
        ClaudeResolver.Resolution r = codex ? ClaudeResolver.resolveCodex(env, home) : ClaudeResolver.resolve(env, home);
        return Optional.ofNullable(r.executable());
    }

    /** {@code mcp get}: explicit only (Claude Code's may health-check the configured server). */
    public Found check(boolean codex) {
        Optional<Path> exe = executable(codex);
        if (exe.isEmpty()) return Found.INDETERMINATE;
        Path folder = freshFolder();
        if (codex) {
            return switch (new CodexMcpClient(exe.get(), c -> starter.start(c, folder), TIMEOUT).registration().status()) {
                case PRESENT -> Found.PRESENT;
                case ABSENT -> Found.ABSENT;
                case INDETERMINATE -> Found.INDETERMINATE;
            };
        }
        return switch (new ClaudeMcpClient(exe.get(), c -> starter.start(c, folder), TIMEOUT).registration().status()) {
            case PRESENT -> Found.PRESENT;
            case ABSENT -> Found.ABSENT;
            case INDETERMINATE -> Found.INDETERMINATE;
        };
    }

    /**
     * The exact argument vectors a registration runs, in order — the add, or for a replace the remove and then the add.
     * Empty when it cannot run (no launcher, or the client is not on this machine).
     */
    public List<List<String>> vectors(boolean codex, boolean replace) {
        Optional<Path> exe = executable(codex);
        if (exe.isEmpty() || launcher.isEmpty()) return List.of();
        List<List<String>> out = new ArrayList<>();
        if (codex) {
            CodexMcpClient c = new CodexMcpClient(exe.get(), x -> null, TIMEOUT);
            if (replace) out.add(c.removeCommand());
            out.add(c.addCommand(launcher.get()));
        } else {
            ClaudeMcpClient c = new ClaudeMcpClient(exe.get(), x -> null, TIMEOUT);
            if (replace) out.add(c.removeCommand());
            out.add(c.addCommand(launcher.get()));
        }
        return List.copyOf(out);
    }

    /** Run {@code vectors} as given (each in a fresh empty folder); true only when every one succeeded. */
    public boolean run(boolean codex, List<List<String>> vectors) {
        if (vectors == null || vectors.isEmpty()) return false;
        Optional<Path> exe = executable(codex);
        if (exe.isEmpty()) return false;
        for (List<String> vector : vectors) {
            Path folder = freshFolder();
            boolean ok = codex
                    ? new CodexMcpClient(exe.get(), c -> starter.start(c, folder), TIMEOUT).run(vector).successful()
                    : new ClaudeMcpClient(exe.get(), c -> starter.start(c, folder), TIMEOUT).run(vector).successful();
            if (!ok) return false;
        }
        return true;
    }

    /** The command a person runs themselves ({@code I'll run it myself}): the plain name, as M42 copies it. */
    public String copyCommand(boolean codex) {
        if (launcher.isEmpty()) return "";
        return codex ? CodexMcpClient.shellDisplay(CodexMcpClient.addCommandForCopy(launcher.get()))
                : ClaudeMcpClient.shellDisplay(ClaudeMcpClient.userCommandForCopy(launcher.get()));
    }

    /** Another MCP client's configuration ({@code GenericMcpConfiguration}, unchanged). */
    public String genericConfiguration() {
        return launcher.map(GenericMcpConfiguration::render).orElse("");
    }

    /** One vector as a person reads it. */
    public static String display(boolean codex, List<String> vector) {
        return codex ? CodexMcpClient.shellDisplay(vector) : ClaudeMcpClient.shellDisplay(vector);
    }

    /** A fresh, empty directory under the private scratch area (S10): created per process, holding nothing. */
    Path freshFolder() {
        try {
            Path root = scratchRoot.get();
            Files.createDirectories(root);
            return Files.createTempDirectory(root, "mcp-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
