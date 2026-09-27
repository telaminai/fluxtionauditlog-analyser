package telamin.fluxtion.audit.analyser.analyser.config;

import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.List;

/**
 * M69 — delete, list and restore walks, the way {@link ReportBin} does for reports: a delete is recoverable, the bin
 * is machine-local and per project, and restoring never replaces a walk of the same name.
 */
public final class WalkBin {

    public static final int CAPACITY = ReportBin.CAPACITY;

    private WalkBin() {
    }

    /** Move the named walk to the bin. Returns it, or null when no walk has that name. */
    public static WalkSpec delete(AppConfig c, String name, String now) {
        WalkSpec doomed = find(c.walks, name);
        if (doomed == null) return null;
        c.deletedWalks.add(0, new DeletedWalk(ReportBin.projectKey(c), now, doomed));
        while (c.deletedWalks.size() > CAPACITY) c.deletedWalks.remove(c.deletedWalks.size() - 1);
        c.walks.removeIf(w -> w.name().equals(name));
        return doomed;
    }

    /** The names restorable into the active project. */
    public static List<String> restorable(AppConfig c) {
        String key = ReportBin.projectKey(c);
        return c.deletedWalks.stream().filter(d -> d.project().equals(key)).map(d -> d.walk().name()).toList();
    }

    /** Restore by name. Returns null on success, or why it was refused. */
    public static String restore(AppConfig c, String name) {
        String key = ReportBin.projectKey(c);
        DeletedWalk found = null;
        for (DeletedWalk d : c.deletedWalks) {
            if (d.project().equals(key) && d.walk().name().equals(name)) { found = d; break; }
        }
        if (found == null) return "no deleted walk called \"" + name + "\" in this project — restorable: " + restorable(c);
        if (find(c.walks, name) != null) {
            return "a walk called \"" + name + "\" already exists — rename it first, so restoring does not replace it";
        }
        c.deletedWalks.remove(found);
        c.walks.add(found.walk());
        return null;
    }

    /** Rename by name. Returns null on success, or why it was refused. */
    public static String rename(AppConfig c, String from, String to) {
        WalkSpec w = find(c.walks, from);
        if (w == null) return "no walk called \"" + from + "\" — walks: " + c.walks.stream().map(WalkSpec::name).toList();
        String problem = ChartNames.problem(to);
        if (to == null || to.isBlank()) return "rename needs the new name";
        if (problem != null) return problem.replace("a chart name", "a walk name");
        if (!from.equals(to) && find(c.walks, to) != null) return "a walk called \"" + to + "\" already exists";
        c.walks.replaceAll(x -> x.name().equals(from) ? x.renamed(to.trim()) : x);
        return null;
    }

    public static WalkSpec find(List<WalkSpec> walks, String name) {
        if (name == null) return null;
        for (WalkSpec w : walks) if (w.name().equals(name)) return w;
        return null;
    }
}
