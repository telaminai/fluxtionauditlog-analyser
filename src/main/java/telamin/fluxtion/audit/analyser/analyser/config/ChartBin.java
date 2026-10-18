package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Owner decision 2026-10-18: a saved chart is never destroyed irrecoverably — by a person, an assistant or an external
 * client. Deleting a chart, or replacing its stored definition (a re-sent notes, series, guides or bands set,
 * {@code clearNotes}), keeps the previous definition here, the way {@link ReportBin} keeps reports and {@link WalkBin}
 * keeps walks. The rules live here, apart from the frame, so they are tested without a display.
 *
 * <ul>
 *   <li><b>Machine-local, never in a profile</b> ({@link AppConfig#deletedCharts}, a machine-tier field, like the other
 *       two bins): a profile is committed to a repository, and a deleted chart written into one would be shared.</li>
 *   <li><b>Per project</b>: each entry remembers the project it came from and is restored only into that project.</li>
 *   <li><b>Bounded the same way as the other bins</b>: at most {@link #CAPACITY} entries, newest first, the oldest
 *       going first. (The report and walk bins bound by count only; {@code deletedAt} is display-only there too.)</li>
 *   <li><b>Restoring never replaces</b>: a chart whose name is taken — open or saved — is refused, as a report or a
 *       walk is; replace-then-restore is therefore: delete the current one (it enters the bin too), then restore the
 *       earlier version by its id.</li>
 * </ul>
 */
public final class ChartBin {

    /** How many binned chart definitions are kept, across all projects — the report and walk bins' bound. */
    public static final int CAPACITY = ReportBin.CAPACITY;

    /** Why a definition is in the bin. */
    public enum Reason {
        /** The chart was deleted. */
        DELETED,
        /** A later call replaced its stored definition; the chart of that name still exists. */
        REPLACED;

        public String word() {
            return name().toLowerCase();
        }

        public static Reason parse(String s) {
            return "replaced".equalsIgnoreCase(s == null ? "" : s.trim()) ? REPLACED : DELETED;
        }
    }

    private ChartBin() {
    }

    /** Whether {@code g} defines nothing — no series, formula, note, explanation, caption, annotation or pin. */
    public static boolean isEmptyDefinition(GraphSpec g) {
        return g == null || (g.series() == null || g.series().isEmpty()) && g.exprs().isEmpty() && g.notes().isEmpty()
                && (g.explanation() == null || g.explanation().isBlank()) && (g.note() == null || g.note().isBlank())
                && g.rightAxis().isEmpty() && g.guides().isEmpty() && g.bands().isEmpty() && g.external().isEmpty()
                && g.markers().isEmpty() && g.from() == null && g.to() == null;
    }

    /** Keep {@code previous} in the bin of the active project, newest first, within the bound. */
    public static void keep(AppConfig c, GraphSpec previous, Reason reason, String now) {
        if (c == null || previous == null || previous.name() == null) return;
        c.deletedCharts.add(0, new DeletedChart(ReportBin.projectKey(c), now, reason, previous.withOpen(false)));
        while (c.deletedCharts.size() > CAPACITY) c.deletedCharts.remove(c.deletedCharts.size() - 1);
    }

    /** Move the named saved chart into the bin. Returns it, or null when no saved chart has that name. */
    public static GraphSpec delete(AppConfig c, String name, String now) {
        GraphSpec doomed = find(c.savedGraphs, name);
        if (doomed == null) return null;
        keep(c, doomed, Reason.DELETED, now);
        c.savedGraphs.removeIf(g -> name.equals(g.name()));
        return doomed;
    }

    /** The entries restorable into the active project, newest first. */
    public static List<DeletedChart> restorable(AppConfig c) {
        String key = ReportBin.projectKey(c);
        return c.deletedCharts.stream().filter(d -> d.project().equals(key)).toList();
    }

    /** The names restorable into the active project, newest first (a name binned twice is listed twice). */
    public static List<String> restorableNames(AppConfig c) {
        return restorable(c).stream().map(d -> d.chart().name()).toList();
    }

    /** What {@code graph {restore: true}} returns: one row per restorable entry, newest first. */
    public static List<Map<String, Object>> listing(AppConfig c) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DeletedChart d : restorable(c)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id(d));
            row.put("name", d.chart().name());
            row.put("reason", d.reason().word());
            row.put("at", d.deletedAt());
            out.add(row);
        }
        return out;
    }

    /**
     * An entry's id: stable across a save and reload (it is derived from the entry's stored project, time, reason and
     * name), distinct for two versions of one name, and short enough to type. {@code restore} takes it where a name is ambiguous.
     */
    public static String id(DeletedChart d) {
        String digest = telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity.sha256(d.project() + "|" + d.deletedAt()
                + "|" + d.reason().word() + "|" + d.chart().name());
        String hex = digest.substring(digest.indexOf(':') + 1);
        return "chart-" + hex.substring(0, 10);
    }

    /** A restore's outcome: the definition to put back, or why nothing changed. */
    public record Restored(GraphSpec chart, String refusal) {
        public boolean ok() {
            return refusal == null;
        }
    }

    /**
     * Take an entry out of the bin by its id, or by name (the newest entry of that name). Nothing changes on a refusal.
     *
     * @param taken whether a chart of a name exists now, open or saved — restoring never replaces one
     */
    public static Restored restore(AppConfig c, String idOrName, Predicate<String> taken) {
        String want = idOrName == null ? "" : idOrName.trim();
        DeletedChart found = null;
        for (DeletedChart d : restorable(c)) {
            if (id(d).equals(want)) { found = d; break; }
        }
        if (found == null) {
            for (DeletedChart d : restorable(c)) {
                if (d.chart().name().equals(want)) { found = d; break; }
            }
        }
        if (found == null) {
            return new Restored(null, "no deleted or replaced chart called \"" + want + "\" in this project — restorable: "
                    + listing(c));
        }
        String name = found.chart().name();
        if (taken != null && taken.test(name)) {
            return new Restored(null, "a chart called \"" + name + "\" exists — restoring never replaces one: rename it, "
                    + "or delete it (it can be restored too), then restore " + id(found));
        }
        c.deletedCharts.remove(found);
        return new Restored(found.chart().withOpen(true), null);
    }

    public static GraphSpec find(List<GraphSpec> charts, String name) {
        if (name == null || charts == null) return null;
        for (GraphSpec g : charts) if (name.equals(g.name())) return g;
        return null;
    }
}
