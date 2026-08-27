package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * M38.3 (spec-portable-context D-C4) — an environment the project declares, and the §E provenance string a
 * log from it should carry. Two environments running the same build emit logs identical in shape and
 * usually in filename; only a declared value separates them, and today that value is typed by whoever
 * wrote the export script, per site. This is the analyser-side default for estates that do not yet have
 * UP-MNG-03 (the server supplying it) — and where both exist, the DECLARED value wins ({@link #match}
 * is only consulted when the opener declared nothing).
 *
 * @param name       short handle: {@code prod}, {@code uat}, {@code dev-a}
 * @param provenance the §E string a log from this environment carries — free text, one line
 * @param logDir     optional, project-relative: a log under this directory is from this environment.
 *                   Same gate as every pointer (D-C2): relative, no {@code ..}, no command shapes.
 */
public record Environment(String name, String provenance, String logDir) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,39}");
    public static final int MAX_PROVENANCE = 200;

    public Environment {
        name = name == null ? "" : name.trim();
        provenance = provenance == null ? "" : provenance.trim();
        logDir = logDir == null || logDir.isBlank() ? null : logDir.trim();
    }

    /** Why this declaration may NOT be stored, or empty when it is sound. */
    public static Optional<String> refuse(Environment e) {
        if (e == null) return Optional.of("no environment");
        if (!NAME.matcher(e.name()).matches()) {
            return Optional.of("environment name must be 1–40 letters, digits, '-' or '_' (got '" + e.name() + "')");
        }
        if (e.provenance().isBlank()) return Optional.of("environment '" + e.name() + "': no provenance string");
        if (e.provenance().length() > MAX_PROVENANCE) {
            return Optional.of("environment '" + e.name() + "': provenance longer than " + MAX_PROVENANCE + " characters");
        }
        if (e.provenance().chars().anyMatch(ch -> ch == '\n' || ch == '\r' || ch == '\t')) {
            return Optional.of("environment '" + e.name() + "': provenance must be one line");
        }
        if (e.logDir() != null) {
            Optional<String> dir = Runbooks.refusePointer("environment '" + e.name() + "' logDir", e.logDir());
            if (dir.isPresent()) return dir;
        }
        return Optional.empty();
    }

    /** The outcome of {@link #match}: which environment applies and WHY — the reason is what `context` reports. */
    public record Match(Environment environment, String reason) { }

    /**
     * The environment a log falls under when nobody declared one: the first whose {@code logDir} contains
     * the log, else the project's default. Pure, so it is tested without a frame.
     *
     * <p><b>Declaration order decides nested directories</b> (review N1): with {@code logs/} and
     * {@code logs/prod/} both declared, a log under {@code logs/prod/} takes whichever is declared FIRST.
     * Declare the more specific directory first. <b>A remote open with no local copy takes only the
     * default</b> (review N2): that is a confident answer derived from nothing about the log, so the
     * default is reported as exactly that — "project default environment" — everywhere it appears, and a
     * project that does not want S3 logs stamped declares no default.
     *
     * @param projectRoot the project's directory (logDirs resolve against it); null when no project is open
     * @param logFile     the local file, or null (a remote open with no local copy matches only the default)
     */
    public static Optional<Match> match(List<Environment> environments, String defaultName, Path projectRoot, Path logFile) {
        if (environments == null || environments.isEmpty()) return Optional.empty();
        if (projectRoot != null && logFile != null) {
            Path log = logFile.toAbsolutePath().normalize();
            for (Environment e : environments) {
                if (e.logDir() == null) continue;
                Path dir = Runbooks.resolve(projectRoot, e.logDir());
                if (dir != null && log.startsWith(dir.toAbsolutePath().normalize())) {
                    return Optional.of(new Match(e, "project environment '" + e.name() + "' — the log is under " + e.logDir()));
                }
            }
        }
        if (defaultName != null && !defaultName.isBlank()) {
            for (Environment e : environments) {
                if (e.name().equals(defaultName)) {
                    return Optional.of(new Match(e, "project default environment '" + e.name() + "'"));
                }
            }
        }
        return Optional.empty();
    }
}
