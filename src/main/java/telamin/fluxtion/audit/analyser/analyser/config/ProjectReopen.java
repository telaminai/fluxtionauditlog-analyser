package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What a project can offer to reopen: the logs and topologies this machine has opened inside it.
 *
 * <p><b>Why this exists, and why it is an offer.</b> A project profile carries settings, never session
 * state — {@link ProjectProfile} records that decision and the open question behind it (O3: "should
 * switching projects re-open that project's last log?", deferred to avoid coupling the two). The
 * consequence in use was that opening a project restored the source and the processors and left you
 * with no log and no topology, with nothing on screen to say which ones belonged to this project or
 * where they were. Found in use, 2026-09-30.
 *
 * <p>This settles O3 without moving the tier boundary. The profile is untouched and stays portable:
 * the candidates come from the MACHINE's recent lists, narrowed to the ones that live inside this
 * project. Nothing is reopened on its own — half your workspace reappearing was the surprise O3 was
 * avoiding — so the answer is a list you choose from, and choosing nothing is an ordinary answer.
 *
 * <p>Both parts are independent. A topology with no log is a perfectly good thing to open, and so is
 * a log with no topology, so neither list constrains the other.
 */
public record ProjectReopen(List<String> logs, List<String> topologies) {

    /** At most this many of each, most-recent first: an offer, not a file manager. */
    public static final int MOST = 12;

    public ProjectReopen {
        logs = List.copyOf(logs);
        topologies = List.copyOf(topologies);
    }

    /**
     * The machine's recent logs and topologies, wherever they live — the answer when a project has
     * nothing of its own, and what the on-demand menu entry offers.
     *
     * <p>Working copies are excluded here as they are everywhere else: an unpacked bundle is a
     * throwaway, the bundle itself is the durable thing, and it has its own list. On a machine that
     * had mostly been opening bundles this was not a detail — EVERY recent entry was a working copy,
     * so an offer narrowed to a project found nothing and said nothing (found in use, 2026-09-30).
     */
    public static ProjectReopen recent(List<String> recentLogs, List<String> recentGraphml,
                                       Predicate<String> exists) {
        return new ProjectReopen(usable(recentLogs, exists), usable(recentGraphml, exists));
    }

    /** Nothing to offer — this machine has opened neither a log nor a topology inside this project. */
    public boolean isEmpty() {
        return logs.isEmpty() && topologies.isEmpty();
    }

    /**
     * The candidates for a project, in the machine's recency order.
     *
     * @param projectRoot  the project's directory; a null root offers nothing, because "recent files
     *                     everywhere" is the global list and answers a different question
     * @param recentLogs   {@link AppConfig#recentFiles}
     * @param recentGraphml {@link AppConfig#recentGraphml}
     * @param exists       whether a path is still readable — injected so the decision is testable
     *                     without a filesystem, and so a deleted file is never offered
     */
    public static ProjectReopen forProject(Path projectRoot, List<String> recentLogs,
                                           List<String> recentGraphml, Predicate<String> exists) {
        return forScopes(projectRoot == null ? List.of() : List.of(projectRoot),
                recentLogs, recentGraphml, exists);
    }

    /**
     * As above, for a project that reaches beyond its own directory.
     *
     * <p>"Inside this project" cannot mean "under the project directory" alone. A project declares
     * where its code lives, and here those are eleven sibling checkouts -- so its TOPOLOGIES sat in a
     * sibling repository while its logs sat under the project, and an offer narrowed to the directory
     * listed the logs and silently found no topology at all (reported in use, 2026-09-30). The scope
     * is the project's directory AND the source roots it declares: the places the project itself says
     * belong to it.
     */
    public static ProjectReopen forScopes(List<Path> scopes, List<String> recentLogs,
                                           List<String> recentGraphml, Predicate<String> exists) {
        return new ProjectReopen(within(scopes, recentLogs, exists),
                within(scopes, recentGraphml, exists));
    }

    private static List<String> within(List<Path> scopes, List<String> paths, Predicate<String> exists) {
        if (scopes == null || scopes.isEmpty()) return List.of();
        List<Path> roots = new ArrayList<>();
        for (Path scope : scopes) {
            if (scope != null) roots.add(scope.toAbsolutePath().normalize());
        }
        return keep(paths, exists, file -> roots.stream().anyMatch(file::startsWith));
    }

    private static List<String> usable(List<String> paths, Predicate<String> exists) {
        return keep(paths, exists, file -> true);
    }

    private static List<String> keep(List<String> paths, Predicate<String> exists,
                                     Predicate<Path> where) {
        if (paths == null) return List.of();
        List<String> kept = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isBlank() || kept.size() >= MOST) continue;
            // A remote location (s3://, http://) is not "inside" a directory and Path.of would mangle it.
            if (path.contains("://")) continue;
            Path file;
            try {
                file = Path.of(path).toAbsolutePath().normalize();
            } catch (RuntimeException invalid) {
                continue;
            }
            // An unpacked bundle is a throwaway. Offering to reopen one as if it were your own file
            // invites work inside a directory the next open may replace.
            if (telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.isWorkingCopy(file)) continue;
            if (where.test(file) && exists.test(path) && !kept.contains(path)) kept.add(path);
        }
        return List.copyOf(kept);
    }
}
