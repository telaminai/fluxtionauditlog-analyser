package telamin.fluxtion.audit.analyser.analyser.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The exact local command an MCP client launches for the analyser bridge (M42.1).
 *
 * <p>This is an argument vector, deliberately not a shell string: it is shared by client registration,
 * generic configuration and the analyser's own loopback probe, all of which must launch precisely the
 * same program. The per-run REST endpoint and token are discovered by the bridge and never belong here.
 */
public final class McpLaunchCommand {

    /** The stable flag that turns the application launch into its headless stdio MCP bridge. */
    public static final String MCP_FLAG = "--mcp";

    private final List<String> launcher;

    private McpLaunchCommand(List<String> launcher) {
        if (launcher == null || launcher.isEmpty()) {
            throw new IllegalArgumentException("an MCP launcher needs a command");
        }
        List<String> checked = new ArrayList<>(launcher.size());
        for (String arg : launcher) {
            if (arg == null || arg.isBlank()) throw new IllegalArgumentException("launcher arguments must be non-blank");
            if (MCP_FLAG.equals(arg)) throw new IllegalArgumentException("pass the app launcher, not its --mcp flag");
            checked.add(arg);
        }
        this.launcher = List.copyOf(checked);
    }

    /** Build a bridge command from a resolved executable and any launcher arguments it requires. */
    public static McpLaunchCommand of(List<String> launcher) {
        return new McpLaunchCommand(launcher);
    }

    /**
     * The documented JBang app install supplies this stable launcher. It is an optional discovery
     * result: an absent JBang app is not an error, because a selected fatjar is the honest fallback.
     */
    public static Optional<McpLaunchCommand> installedJbang(Path userHome) {
        Objects.requireNonNull(userHome, "userHome");
        Path bin = userHome.resolve(".jbang").resolve("bin");
        for (String name : List.of("analyser", "analyser.cmd", "analyser.bat")) {
            Path candidate = bin.resolve(name);
            if (Files.isRegularFile(candidate)) return Optional.of(of(List.of(candidate.toAbsolutePath().toString())));
        }
        return Optional.empty();
    }

    /**
     * A safe form-2 resolver for an app launched directly from one shaded jar. A classpath with more
     * than one entry is deliberately refused: reconstructing an IDE or arbitrary Java launch would be a
     * plausible-looking guess, not the exact bridge command a client should inherit. JBang wins whenever
     * its documented launcher exists; this is the useful fallback for {@code java -jar} and release smoke
     * tests.
     */
    public static Optional<McpLaunchCommand> runningJar() {
        String classPath = System.getProperty("java.class.path", "");
        String home = System.getProperty("java.home", "");
        if (home.isBlank()) return Optional.empty();
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return fromRunningJar(Path.of(home, "bin", executable), classPath, File.pathSeparator);
    }

    static Optional<McpLaunchCommand> fromRunningJar(Path javaExecutable, String classPath, String pathSeparator) {
        if (javaExecutable == null || classPath == null || classPath.isBlank()
                || pathSeparator == null || classPath.contains(pathSeparator)) {
            return Optional.empty();
        }
        try {
            Path jar = Path.of(classPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(javaExecutable) || !Files.isRegularFile(jar)
                    || !jar.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return Optional.empty();
            }
            return Optional.of(jar(javaExecutable, jar));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** A selected shaded jar, launched by the selected absolute Java executable. */
    public static McpLaunchCommand jar(Path javaExecutable, Path jar) {
        Objects.requireNonNull(javaExecutable, "javaExecutable");
        Objects.requireNonNull(jar, "jar");
        return of(List.of(javaExecutable.toAbsolutePath().toString(), "-jar", jar.toAbsolutePath().toString()));
    }

    /** The immutable argument vector actually given to {@link ProcessBuilder}. */
    public List<String> command() {
        List<String> command = new ArrayList<>(launcher);
        command.add(MCP_FLAG);
        return List.copyOf(command);
    }

    /** The command before the MCP bridge flag, for client-specific configuration renderers. */
    public List<String> launcher() {
        return launcher;
    }
}
