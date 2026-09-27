package telamin.fluxtion.audit.analyser.analyser.config;

import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import java.util.List;

/**
 * Deleting a report is recoverable (PR #33; owner decision 2026-09-27).
 *
 * <p>A delete — from the Reports tab or from {@code report {name, delete: true}} — moves the report into a
 * "recently deleted" list instead of destroying it. The rules live here, apart from the frame, so they are tested
 * without a display.
 *
 * <h2>Machine-local, never in a profile</h2>
 *
 * <p>The list is kept in the analyser's OWN settings ({@link AppConfig#deletedReports}, a machine-tier field),
 * never in the project profile or a settings export. Project profiles are committed to repositories, and a deleted
 * report written into one would be committed and shared — the opposite of deleting it. Each entry remembers the
 * project it came from, and is restored only into that project.
 *
 * <h2>Bounded</h2>
 *
 * <p>At most {@link #CAPACITY} entries, newest first; the oldest go first.
 */
public final class ReportBin {

    /** How many deleted reports are kept, across all projects. */
    public static final int CAPACITY = 20;

    private ReportBin() {
    }

    /** The key a report is filed under: the active profile's path, or "" with no project open. */
    public static String projectKey(AppConfig c) {
        return c == null || c.activeProjectPath == null ? "" : c.activeProjectPath;
    }

    /**
     * Move the named report into the bin.
     *
     * @return the report that was deleted, or null when none has that name
     */
    public static ReportSpec delete(AppConfig c, String name, String now) {
        ReportSpec doomed = find(c.reports, name);
        if (doomed == null) return null;
        c.deletedReports.add(0, new DeletedReport(projectKey(c), now, doomed));
        while (c.deletedReports.size() > CAPACITY) c.deletedReports.remove(c.deletedReports.size() - 1);
        c.reports.removeIf(r -> r.name().equals(name));
        return doomed;
    }

    /** Names that can be restored into the active project, newest first. */
    public static List<String> restorable(AppConfig c) {
        String key = projectKey(c);
        return c.deletedReports.stream().filter(d -> d.project().equals(key)).map(d -> d.report().name()).toList();
    }

    /**
     * Restore the most recently deleted report of that name into the active project.
     *
     * @return null on success, otherwise why it was refused — nothing changes on a refusal
     */
    public static String restore(AppConfig c, String name) {
        String key = projectKey(c);
        DeletedReport found = null;
        for (DeletedReport d : c.deletedReports) {
            if (d.project().equals(key) && d.report().name().equals(name)) { found = d; break; }
        }
        if (found == null) {
            return "no deleted report called \"" + name + "\" in this project — restorable: " + restorable(c);
        }
        if (find(c.reports, name) != null) {
            return "a report called \"" + name + "\" already exists — rename it first, so restoring does not replace it";
        }
        c.deletedReports.remove(found);
        c.reports.add(found.report());
        return null;
    }

    private static ReportSpec find(List<ReportSpec> reports, String name) {
        if (name == null) return null;
        for (ReportSpec r : reports) if (r.name().equals(name)) return r;
        return null;
    }
}
