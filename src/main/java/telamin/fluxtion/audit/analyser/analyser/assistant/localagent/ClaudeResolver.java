package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds {@code claude} (spec §4.3, [P0-38]). An analyser started from the Finder or the Dock sees macOS's minimal GUI
 * {@code PATH} ({@code /usr/bin:/bin:/usr/sbin:/sbin}), where the native installer's {@code ~/.local/bin/claude} is not.
 * So the inherited {@code PATH} is searched first, then the standard install locations, and a failure lists EVERY place
 * searched, so the person can see why.
 */
public final class ClaudeResolver {

    private ClaudeResolver() {
    }

    /** The executable found, or null; and every directory searched, in order. */
    public record Resolution(Path executable, List<String> searched) {
        public Resolution {
            searched = List.copyOf(searched);
        }

        public boolean found() {
            return executable != null;
        }

        /** The words of a not-found failure: the places searched, one per line. */
        public String searchedText() {
            return "Searched for claude in:\n  " + String.join("\n  ", searched);
        }
    }

    /** The directories searched after {@code PATH}, in order. */
    static List<Path> standardLocations(Path home, Map<String, String> env, boolean windows) {
        List<Path> out = new ArrayList<>();
        if (home != null) out.add(home.resolve(".local").resolve("bin"));
        if (!windows) {
            out.add(Path.of("/opt/homebrew/bin"));
            out.add(Path.of("/usr/local/bin"));
        }
        String npmPrefix = env == null ? null : env.get("NPM_CONFIG_PREFIX");
        if (npmPrefix != null && !npmPrefix.isBlank()) {
            out.add(windows ? Path.of(npmPrefix) : Path.of(npmPrefix).resolve("bin"));
        }
        if (home != null) out.add(home.resolve(".npm-global").resolve("bin"));
        if (windows && env != null && env.get("APPDATA") != null) out.add(Path.of(env.get("APPDATA")).resolve("npm"));
        return out;
    }

    public static Resolution resolve(Map<String, String> env, Path home) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        // review finding 7: Windows spells it "Path"; the key is found without regard to case
        return resolve(env == null ? null : env.get(ChildPath.pathKey(env)), java.io.File.pathSeparator, windows, home, env);
    }

    static Resolution resolve(String pathValue, String separator, boolean windows, Path home, Map<String, String> env) {
        Set<String> searched = new LinkedHashSet<>();
        List<String> names = windows ? List.of("claude.exe", "claude.cmd", "claude.bat", "claude") : List.of("claude");
        List<Path> dirs = new ArrayList<>();
        if (pathValue != null && !pathValue.isBlank() && separator != null && !separator.isEmpty()) {
            for (String entry : pathValue.split(Pattern.quote(separator))) {
                if (entry.isBlank()) continue;
                try {
                    dirs.add(Path.of(entry));
                } catch (RuntimeException ignore) {
                    // a malformed PATH entry is skipped; the others are still searched
                }
            }
        }
        dirs.addAll(standardLocations(home, env, windows));
        for (Path dir : dirs) {
            if (!searched.add(dir.toString())) continue;
            for (String name : names) {
                try {
                    Path candidate = dir.resolve(name);
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                        return new Resolution(candidate.toAbsolutePath().normalize(), new ArrayList<>(searched));
                    }
                } catch (RuntimeException ignore) {
                    // unreadable entry: keep looking
                }
            }
        }
        return new Resolution(null, new ArrayList<>(searched));
    }
}
