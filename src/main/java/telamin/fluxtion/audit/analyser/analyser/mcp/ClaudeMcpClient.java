package telamin.fluxtion.audit.analyser.analyser.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * The confirmed, user-scoped Claude Code MCP route (M42.4).
 *
 * <p>Claude Code owns {@code ~/.claude.json}; this class never parses or edits it. Every operation is
 * an exact current CLI argument vector. The one status check is deliberately explicit because current
 * Claude Code may health-check a configured server while answering {@code mcp get}.
 */
public final class ClaudeMcpClient {

    public static final String SERVER_NAME = "fluxtion-analyser";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int OUTPUT_LIMIT = 8 * 1024;
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)\\b(token|api[_-]?key|password|secret|authorization)\\b(\\s*[:=]\\s*|\\s+bearer\\s+)([^\\s,;]+)");

    public enum RegistrationStatus { PRESENT, ABSENT, INDETERMINATE }

    public enum Status { SUCCEEDED, FAILED, TIMED_OUT, LAUNCH_FAILED }

    /** A safe result only; raw Claude Code output is not sent to the UI or logs. */
    public record Result(Status status, String detail) {
        public boolean successful() { return status == Status.SUCCEEDED; }
    }

    public record Registration(RegistrationStatus status, String detail) {
        public boolean present() { return status == RegistrationStatus.PRESENT; }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private final Path executable;
    private final ProcessStarter starter;
    private final Duration timeout;

    private ClaudeMcpClient(Path executable) {
        this(executable, command -> new ProcessBuilder(command).redirectErrorStream(true).start(), DEFAULT_TIMEOUT);
    }

    ClaudeMcpClient(Path executable, ProcessStarter starter, Duration timeout) {
        this.executable = executable.toAbsolutePath().normalize();
        this.starter = starter;
        this.timeout = timeout;
    }

    /** Locate a real {@code claude} executable from PATH without starting it. */
    public static Optional<ClaudeMcpClient> detect() {
        return findExecutable(System.getenv("PATH"), System.getProperty("path.separator"), isWindows())
                .map(ClaudeMcpClient::new);
    }

    static Optional<Path> findExecutable(String pathValue, String pathSeparator, boolean windows) {
        if (pathValue == null || pathValue.isBlank() || pathSeparator == null || pathSeparator.isBlank()) {
            return Optional.empty();
        }
        List<String> names = windows ? List.of("claude.exe", "claude.cmd", "claude.bat", "claude") : List.of("claude");
        for (String entry : pathValue.split(Pattern.quote(pathSeparator))) {
            if (entry.isBlank()) continue;
            for (String name : names) {
                try {
                    Path candidate = Path.of(entry).resolve(name);
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                        return Optional.of(candidate.toAbsolutePath().normalize());
                    }
                } catch (RuntimeException ignore) {
                    // A malformed PATH entry is not a reason to treat another valid entry as unavailable.
                }
            }
        }
        return Optional.empty();
    }

    public Path executable() { return executable; }

    /** Explicit only: current Claude Code may start a configured bridge as part of this supported status check. */
    public Registration registration() {
        Outcome outcome = invoke(getCommand());
        if (outcome.status == Status.SUCCEEDED) {
            return new Registration(RegistrationStatus.PRESENT, "Claude Code lists the named registration.");
        }
        if (outcome.status == Status.FAILED && saysMissing(outcome.output)) {
            return new Registration(RegistrationStatus.ABSENT, "Claude Code has no registration with this name.");
        }
        return new Registration(RegistrationStatus.INDETERMINATE,
                "Claude Code could not confirm the named registration. Its configuration was not changed.");
    }

    /** Add the named STDIO server to the user's Claude Code scope. */
    public Result add(McpLaunchCommand bridge) {
        return result(invoke(addCommand(bridge)), "Claude Code registered the analyser for this user.",
                "Claude Code did not add the user registration; the analyser settings were not changed.");
    }

    /** Replace only this name in user scope: a confirmed user-scope remove, then a user-scope add. */
    public Result replace(McpLaunchCommand bridge) {
        Outcome removed = invoke(removeCommand());
        if (removed.status != Status.SUCCEEDED) {
            return result(removed, "", "Claude Code did not remove the user registration; nothing was added.");
        }
        return result(invoke(addCommand(bridge)), "Claude Code replaced the user registration.",
                "Claude Code removed the user registration but did not add the new command.");
    }

    /** Remove only the named registration from user scope; local/project scopes are never inferred or touched. */
    public Result remove() {
        return result(invoke(removeCommand()), "Claude Code removed the user registration.",
                "Claude Code did not remove the user registration; the analyser settings were not changed.");
    }

    public List<String> getCommand() { return command("mcp", "get", SERVER_NAME); }

    public List<String> addCommand(McpLaunchCommand bridge) { return addCommand(executable.toString(), "user", bridge); }

    /** The project's command is copy-only: executing it is the person's intentional project-policy decision. */
    public static List<String> projectCommandForCopy(McpLaunchCommand bridge) {
        return addCommand("claude", "project", bridge);
    }

    /** The same current user-scope form, with normal {@code claude}, for a copy-only fallback. */
    public static List<String> userCommandForCopy(McpLaunchCommand bridge) {
        return addCommand("claude", "user", bridge);
    }

    public List<String> removeCommand() { return command("mcp", "remove", "--scope", "user", SERVER_NAME); }

    public static String shellDisplay(List<String> command) {
        return command.stream().map(ClaudeMcpClient::quoteForShell).reduce((left, right) -> left + " " + right).orElse("");
    }

    static String redactForDisplay(String output) {
        if (output == null || output.isBlank()) return "";
        return SECRET_VALUE.matcher(output).replaceAll("$1$2…");
    }

    private List<String> command(String... parts) {
        List<String> command = new ArrayList<>(parts.length + 1);
        command.add(executable.toString());
        command.addAll(List.of(parts));
        return List.copyOf(command);
    }

    private static List<String> addCommand(String executable, String scope, McpLaunchCommand bridge) {
        List<String> command = new ArrayList<>(List.of(executable, "mcp", "add", "--scope", scope,
                "--transport", "stdio", SERVER_NAME, "--"));
        command.addAll(bridge.command());
        return List.copyOf(command);
    }

    private Outcome invoke(List<String> command) {
        Process process;
        try {
            process = starter.start(command);
        } catch (IOException | RuntimeException e) {
            return new Outcome(Status.LAUNCH_FAILED, "");
        }
        ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analyser-claude-mcp");
            t.setDaemon(true);
            return t;
        });
        Future<String> output = reader.submit(() -> drain(process.getInputStream()));
        try {
            if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(100, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                return new Outcome(Status.TIMED_OUT, "");
            }
            String text = output.get(200, TimeUnit.MILLISECONDS);
            return new Outcome(process.exitValue() == 0 ? Status.SUCCEEDED : Status.FAILED, redactForDisplay(text));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Outcome(Status.TIMED_OUT, "");
        } catch (ExecutionException | TimeoutException e) {
            process.destroyForcibly();
            return new Outcome(Status.FAILED, "");
        } finally {
            reader.shutdownNow();
        }
    }

    private static Result result(Outcome outcome, String success, String failure) {
        return new Result(outcome.status, outcome.status == Status.SUCCEEDED ? success : failure);
    }

    private static boolean saysMissing(String output) {
        String text = output.toLowerCase(Locale.ROOT);
        return text.contains("not found") || text.contains("no mcp server") || text.contains("unknown mcp server");
    }

    private static String quoteForShell(String argument) {
        if (argument.matches("[A-Za-z0-9_@%+=:,./-]+")) return argument;
        if (isWindows()) return "\"" + argument.replace("\"", "\\\"") + "\"";
        return "'" + argument.replace("'", "'\"'\"'") + "'";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String drain(InputStream stream) throws IOException {
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int count; (count = stream.read(buffer)) >= 0; ) {
            if (kept.size() < OUTPUT_LIMIT) kept.write(buffer, 0, Math.min(count, OUTPUT_LIMIT - kept.size()));
        }
        return kept.toString(StandardCharsets.UTF_8);
    }

    private record Outcome(Status status, String output) { }
}
