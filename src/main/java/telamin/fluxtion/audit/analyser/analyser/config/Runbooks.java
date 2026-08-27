package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * M38.1 (spec-portable-context D-C2) — a runbook is stored as a POINTER: <i>"the deploy runbook for this
 * system is {@code ops/deploy.md}"</i>, relative to the project root. Never the commands.
 *
 * <p>The rule, which outlives the spec: <b>anything in a profile that an agent will act on must be inert,
 * or a pointer to something under version control — never an instruction the profile itself carries.</b>
 * Profiles move by email and in repositories; a profile that could carry instructions would make opening
 * a colleague's project with an agent attached execute text written by whoever sent the file.
 *
 * <p>So this class is a gate, and it is deliberately narrow: a value is accepted only if it can be nothing
 * but a relative path inside the project. Everything a command line needs — spaces, shell metacharacters,
 * line breaks, an absolute root, a {@code ..} escape, a URL scheme — is refused <i>with a reason</i>, at
 * write time (the verb) and at read time (import, and the config loader), so a hand-edited or hostile file
 * degrades to "that entry was dropped, here is why" rather than to a pointer someone acts on.
 */
public final class Runbooks {

    private Runbooks() {
    }

    public static final int MAX_PATH = 200;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,39}");
    /** Path segments: letters, digits, dot, dash, underscore — nothing a shell or a URL would read. */
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    /** The reason this (name, path) pair may NOT be stored, or empty if it is an acceptable pointer. */
    public static Optional<String> refuse(String name, String path) {
        if (name == null || !NAME.matcher(name).matches()) {
            return Optional.of("runbook name must be 1–40 letters, digits, '-' or '_' (got "
                    + (name == null ? "nothing" : "'" + abbreviate(name) + "'") + ")");
        }
        if (path == null || path.isBlank()) return Optional.of("runbook '" + name + "': no path given");
        if (path.length() > MAX_PATH) {
            return Optional.of("runbook '" + name + "': path longer than " + MAX_PATH + " characters — that is not a path");
        }
        if (path.chars().anyMatch(ch -> ch == '\n' || ch == '\r' || ch == '\t')) {
            return Optional.of("runbook '" + name + "': contains a line break or tab — a runbook is a LOCATION, never its contents");
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.matches("^[A-Za-z]:.*")) {
            return Optional.of("runbook '" + name + "': '" + abbreviate(path) + "' is absolute — it must be relative to the project root");
        }
        if (path.contains("://") || path.startsWith("~")) {
            return Optional.of("runbook '" + name + "': '" + abbreviate(path) + "' is a URL or a home-relative path — it must be a file in this repository");
        }
        String[] segments = path.split("[/\\\\]");
        for (String seg : segments) {
            if (seg.equals("..")) {
                return Optional.of("runbook '" + name + "': '" + abbreviate(path) + "' escapes the project root ('..')");
            }
            if (seg.isEmpty() || !SEGMENT.matcher(seg).matches()) {
                return Optional.of("runbook '" + name + "': '" + abbreviate(path) + "' is not a plain relative path — spaces, "
                        + "quotes, '$', ';', '|', '&', '<', '>', '*', '?' and backticks are refused because a "
                        + "runbook entry is a location, never a command");
            }
        }
        return Optional.empty();
    }

    /** Where the pointer lands on this machine, or null when there is no project root to resolve against. */
    public static Path resolve(Path projectRoot, String relative) {
        if (projectRoot == null || relative == null) return null;
        return projectRoot.resolve(relative.replace('\\', '/')).normalize();
    }

    public static boolean exists(Path projectRoot, String relative) {
        Path p = resolve(projectRoot, relative);
        return p != null && Files.isRegularFile(p);
    }

    private static String abbreviate(String s) {
        return s.length() <= 60 ? s : s.substring(0, 57) + "…";
    }
}
