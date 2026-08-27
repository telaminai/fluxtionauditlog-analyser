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
 * The deliberately small, argument-vector-only front door to Codex's local MCP configuration (M42.3).
 *
 * <p>The analyser never reads or edits {@code config.toml} itself. It asks the installed Codex CLI about
 * exactly {@value #SERVER_NAME}, and only runs add, replace, or remove after the person confirms the
 * exact command. Output is consumed so a noisy CLI cannot hang the UI, bounded before it is retained,
 * and redacted before any diagnostic can escape this class.
 */
public final class CodexMcpClient {

    /** The client-side registration label; the bridge's serverInfo name intentionally remains separate. */
    public static final String SERVER_NAME = "fluxtion-analyser";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int OUTPUT_LIMIT = 8 * 1024;
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)\\b(token|api[_-]?key|password|secret|authorization)\\b(\\s*[:=]\\s*|\\s+bearer\\s+)([^\\s,;]+)");

    public enum RegistrationStatus { PRESENT, ABSENT, INDETERMINATE }

    public enum Status { SUCCEEDED, FAILED, TIMED_OUT, LAUNCH_FAILED }

    /** A safe status only: raw CLI output is deliberately not exposed to the UI or logs. */
    public record Result(Status status, String detail) {
        public boolean successful() {
            return status == Status.SUCCEEDED;
        }
    }

    public record Registration(RegistrationStatus status, String detail) {
        public boolean present() {
            return status == RegistrationStatus.PRESENT;
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private final Path executable;
    private final ProcessStarter starter;
    private final Duration timeout;

    private CodexMcpClient(Path executable) {
        this(executable, command -> new ProcessBuilder(command).redirectErrorStream(true).start(), DEFAULT_TIMEOUT);
    }

    CodexMcpClient(Path executable, ProcessStarter starter, Duration timeout) {
        this.executable = executable.toAbsolutePath().normalize();
        this.starter = starter;
        this.timeout = timeout;
    }

    /** Locate a real {@code codex} executable from PATH without starting a third-party process. */
    public static Optional<CodexMcpClient> detect() {
        return findExecutable(System.getenv("PATH"), System.getProperty("path.separator"), isWindows())
                .map(CodexMcpClient::new);
    }

    static Optional<Path> findExecutable(String pathValue, String pathSeparator, boolean windows) {
        if (pathValue == null || pathValue.isBlank() || pathSeparator == null || pathSeparator.isBlank()) {
            return Optional.empty();
        }
        List<String> names = windows ? List.of("codex.exe", "codex.cmd", "codex.bat", "codex") : List.of("codex");
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

    public Path executable() {
        return executable;
    }

    /** Query only the named server; unlike a config-file parser this remains Codex's supported interface. */
    public Registration registration() {
        Outcome outcome = invoke(getCommand());
        if (outcome.status == Status.SUCCEEDED) {
            return new Registration(RegistrationStatus.PRESENT, "Codex lists the named registration.");
        }
        if (outcome.status == Status.FAILED && saysMissing(outcome.output)) {
            return new Registration(RegistrationStatus.ABSENT, "Codex has no registration with this name.");
        }
        return new Registration(RegistrationStatus.INDETERMINATE,
                "Codex could not confirm the named registration. Its configuration was not changed.");
    }

    /** Add the named stdio server. A duplicate is deliberately not guessed to be replaceable. */
    public Result add(McpLaunchCommand bridge) {
        return result(invoke(addCommand(bridge)), "Codex registered the analyser.",
                "Codex did not add the registration; the analyser settings were not changed.");
    }

    /** Replace only the exact named registration: remove it through Codex, then add the new command through Codex. */
    public Result replace(McpLaunchCommand bridge) {
        Outcome removed = invoke(removeCommand());
        if (removed.status != Status.SUCCEEDED) {
            return result(removed, "", "Codex did not remove the named registration; nothing was added.");
        }
        return result(invoke(addCommand(bridge)), "Codex replaced the analyser registration.",
                "Codex removed the named registration but did not add the new command.");
    }

    /** Remove only {@value #SERVER_NAME}; the confirmation UI names this exact target. */
    public Result remove() {
        return result(invoke(removeCommand()), "Codex removed the named registration.",
                "Codex did not remove the named registration; the analyser settings were not changed.");
    }

    public List<String> getCommand() {
        return command("mcp", "get", SERVER_NAME, "--json");
    }

    public List<String> addCommand(McpLaunchCommand bridge) {
        return addCommand(executable.toString(), bridge);
    }

    /** The same current CLI form, with its normal {@code codex} name, for a copy-only fallback. */
    public static List<String> addCommandForCopy(McpLaunchCommand bridge) {
        return addCommand("codex", bridge);
    }

    public List<String> removeCommand() {
        return command("mcp", "remove", SERVER_NAME);
    }

    /** A terminal-friendly fallback only; process execution always uses the unquoted argument vector. */
    public static String shellDisplay(List<String> command) {
        return command.stream().map(CodexMcpClient::quoteForShell).reduce((left, right) -> left + " " + right).orElse("");
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

    private static List<String> addCommand(String executable, McpLaunchCommand bridge) {
        List<String> command = new ArrayList<>(List.of(executable, "mcp", "add", SERVER_NAME, "--"));
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
            Thread t = new Thread(r, "analyser-codex-mcp");
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
