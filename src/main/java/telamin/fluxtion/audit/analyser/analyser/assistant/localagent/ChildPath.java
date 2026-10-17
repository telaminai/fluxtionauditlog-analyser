package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * OD-74.11: the full posture's child {@code PATH}. Hooks, Bash and the person's stdio MCP servers ({@code npx},
 * {@code uvx}) inherit the analyser's environment, and an analyser started from the Finder or the Dock has the minimal
 * GUI {@code PATH} [P0-38], where they fail (PB-2: both an {@code npx} server and a {@code #!/usr/bin/env node} server
 * {@code failed}, and a hook found neither). So the standard install locations that EXIST and are not already present
 * are APPENDED, in a fixed order, so the person's own order always wins.
 *
 * <p><b>Computed, never discovered.</b> No login shell is run, and nothing is started: running the person's shell
 * start-up files would execute code outside any consent the analyser asked for. Anything else (an {@code nvm} node, a
 * project venv) is the person's to set in Claude Code's own settings {@code env}. Pure apart from the directory check,
 * which the tests replace.
 */
public final class ChildPath {

    private ChildPath() {
    }

    /** The extended value and exactly what was appended, in order (the header's environment line names them). */
    public record Extension(String path, List<String> appended) {
        public Extension {
            appended = List.copyOf(appended);
        }
    }

    public static Extension extend(Map<String, String> inherited, Path home) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return extend(inherited, home, windows, File.pathSeparator, Files::isDirectory);
    }

    /**
     * @param isDirectory the existence check: the only contact with the machine
     */
    static Extension extend(Map<String, String> inherited, Path home, boolean windows, String separator,
                            Predicate<Path> isDirectory) {
        String path = inherited == null ? null : inherited.get("PATH");
        Set<String> present = new LinkedHashSet<>();
        if (path != null && !path.isEmpty()) {
            for (String entry : path.split(Pattern.quote(separator))) {
                if (!entry.isEmpty()) present.add(normal(entry));
            }
        }
        List<String> appended = new ArrayList<>();
        for (Path candidate : candidates(inherited, home, windows)) {
            String c = candidate.toString();
            if (present.contains(normal(c))) continue;
            boolean exists;
            try {
                exists = isDirectory.test(candidate);
            } catch (RuntimeException e) {
                exists = false;
            }
            if (!exists) continue;
            present.add(normal(c));
            appended.add(c);
        }
        StringBuilder out = new StringBuilder(path == null ? "" : path);
        for (String a : appended) {
            if (!out.isEmpty()) out.append(separator);
            out.append(a);
        }
        return new Extension(out.toString(), appended);
    }

    /**
     * The OD-74.11 locations, in order: {@code ~/.local/bin}, {@code /opt/homebrew/bin}, {@code /usr/local/bin},
     * {@code $NPM_CONFIG_PREFIX/bin}, {@code ~/.npm-global/bin}; on Windows {@code %APPDATA%\npm} only (§11).
     */
    static List<Path> candidates(Map<String, String> env, Path home, boolean windows) {
        List<Path> out = new ArrayList<>();
        if (windows) {
            String appData = env == null ? null : env.get("APPDATA");
            if (appData != null && !appData.isBlank()) out.add(Path.of(appData).resolve("npm"));
            return out;
        }
        if (home != null) out.add(home.resolve(".local").resolve("bin"));
        out.add(Path.of("/opt/homebrew/bin"));
        out.add(Path.of("/usr/local/bin"));
        String npmPrefix = env == null ? null : env.get("NPM_CONFIG_PREFIX");
        if (npmPrefix != null && !npmPrefix.isBlank()) out.add(Path.of(npmPrefix).resolve("bin"));
        if (home != null) out.add(home.resolve(".npm-global").resolve("bin"));
        return out;
    }

    /** {@code /a/b/} and {@code /a/b} are one entry. */
    private static String normal(String entry) {
        String e = entry.trim();
        while (e.length() > 1 && (e.endsWith("/") || e.endsWith("\\"))) e = e.substring(0, e.length() - 1);
        return e;
    }

    /** How the header names an appended directory: under the home folder as {@code ~/…}. */
    public static String shown(String dir, Path home) {
        if (home == null || dir == null) return dir;
        String h = home.toString();
        if (dir.equals(h)) return "~";
        if (dir.startsWith(h + File.separator)) return "~" + dir.substring(h.length());
        return dir;
    }
}
