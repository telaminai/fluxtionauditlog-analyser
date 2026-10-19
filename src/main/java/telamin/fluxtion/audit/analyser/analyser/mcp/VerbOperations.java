package telamin.fluxtion.audit.analyser.analyser.mcp;

import java.util.Map;

/**
 * Final review of the M74 usability pass (2026-10-17) #2: which sub-action a {@code report} or {@code open} call
 * performs, decided ONCE. The executor ({@code ActionExecutor}, {@code MainFrame.reportVerb}) runs the operation this
 * chooses, the approval dialog ({@code PermissionText}) describes the operation this chooses, and the hosted-call
 * classification ({@link McpTools#hostedCallKind}) judges it — so the sentence the person approves is the call that
 * runs. Before this each read the input with its own predicates: the dialog took a {@code delete} key that was merely
 * present ({@code delete: false}) as a delete, while the executor, testing {@code Boolean.TRUE}, built and replaced the
 * report. ({@code walk} has its chooser already, {@code WalkVerb.choose}, used the same way.)
 *
 * <p>The order of the tests is the executor's order, and each one is the executor's own predicate.
 */
public final class VerbOperations {

    private VerbOperations() {
    }

    /** A {@code report} call's one operation. */
    public enum ReportOp {
        /** {@code bundle: {path, …}} alone: an evidence bundle is written. */
        BUNDLE,
        /** {@code restore: true} alone: the deleted reports are listed. */
        LIST_DELETED,
        /** {@code restore: "name"} alone: that report comes back. */
        RESTORE,
        /** {@code delete: true} with a name: the report moves to the bin. */
        DELETE,
        /** {@code rename} (non-null) with a name. */
        RENAME,
        /** {@code csv} (non-null): one table is written as CSV. */
        CSV,
        /** {@code sections} or {@code name} (or a {@code csv}/{@code rename} key that is null): build or replace. */
        BUILD,
        /** None of those: the single-record finding PDF. */
        FINDING,
        /** A call the executor refuses before anything changes; {@link Chosen#error()} says why. */
        INVALID
    }

    /** The operation, or why the call is refused. */
    public record Chosen<T extends Enum<T>>(T op, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** The operation {@code report} performs for {@code p} — the executor's own order and predicates. */
    public static Chosen<ReportOp> report(Map<?, ?> p) {
        if (p == null) p = Map.of();
        if (p.containsKey("bundle")) {
            if (p.size() != 1) return invalid("report 'bundle' must be used alone");
            if (!(p.get("bundle") instanceof Map<?, ?>)) {
                return invalid("report 'bundle' is an object: {path, notes?, from?, to?, replay?}");
            }
            return new Chosen<>(ReportOp.BUNDLE, null);
        }
        if (p.containsKey("restore")) {
            if (p.size() != 1) return invalid("report 'restore' must be used alone");
            Object restore = p.get("restore");
            if (Boolean.TRUE.equals(restore)) return new Chosen<>(ReportOp.LIST_DELETED, null);
            if (restore instanceof String name && !name.isBlank()) return new Chosen<>(ReportOp.RESTORE, null);
            return invalid("report 'restore' needs true to list or a nonblank report name");
        }
        if (p.containsKey("sections") || p.containsKey("name") || p.containsKey("csv")) {
            if (Boolean.TRUE.equals(p.get("delete"))) return new Chosen<>(ReportOp.DELETE, null);
            if (p.get("rename") != null) return new Chosen<>(ReportOp.RENAME, null);
            if (p.get("csv") != null) return new Chosen<>(ReportOp.CSV, null);
            return new Chosen<>(ReportOp.BUILD, null);
        }
        return new Chosen<>(ReportOp.FINDING, null);
    }

    private static Chosen<ReportOp> invalid(String why) {
        return new Chosen<>(ReportOp.INVALID, why);
    }

    /** A {@code graph} call's one operation (owner decision 2026-10-18), in the executor's order. */
    public enum GraphOp {
        /** {@code restore: true} alone: the deleted and replaced chart definitions are listed. */
        LIST_DELETED,
        /** {@code restore: "name or id"} alone: that definition comes back. */
        RESTORE,
        /** {@code close} (true) with a name: the tab closes and the definition is kept. */
        CLOSE,
        /** {@code delete} (true) with a name: the chart moves to the bin. */
        DELETE,
        /** {@code rename} present: the chart is renamed. */
        RENAME,
        /** Only {@code name}, {@code refresh} or {@code newTab}: a chart is shown, reopened, re-extracted or created empty. */
        SHOW,
        /** Anything else: the call sets part of a chart's definition — of an existing chart, or of one it creates. */
        EDIT,
        /** A call the executor refuses before anything changes; {@link Chosen#error()} says why. */
        INVALID
    }

    /** The keys of a {@code graph} call that change no stored definition by themselves. */
    static final java.util.Set<String> GRAPH_SHOW_KEYS = java.util.Set.of("name", "refresh", "newTab");

    /** The operation {@code graph} performs for {@code p} — the executor's own order and predicates. */
    public static Chosen<GraphOp> graph(Map<?, ?> p) {
        if (p == null) p = Map.of();
        if (p.containsKey("restore")) {
            if (p.size() != 1) return new Chosen<>(GraphOp.INVALID, "graph 'restore' must be used alone");
            Object restore = p.get("restore");
            if (Boolean.TRUE.equals(restore)) return new Chosen<>(GraphOp.LIST_DELETED, null);
            if (restore instanceof String key && !key.isBlank()) return new Chosen<>(GraphOp.RESTORE, null);
            return new Chosen<>(GraphOp.INVALID, "graph 'restore' needs true to list, or a chart's name or id");
        }
        if (flag(p.get("close"))) return new Chosen<>(GraphOp.CLOSE, null);
        if (flag(p.get("delete"))) return new Chosen<>(GraphOp.DELETE, null);
        if (p.containsKey("rename")) return new Chosen<>(GraphOp.RENAME, null);
        for (Object k : p.keySet()) {
            if (!GRAPH_SHOW_KEYS.contains(String.valueOf(k))) return new Chosen<>(GraphOp.EDIT, null);
        }
        return new Chosen<>(GraphOp.SHOW, null);
    }

    /** The executor's boolean reading: a Boolean, or a string that reads {@code true}. */
    public static boolean flag(Object o) {
        return o instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(o));
    }

    /** An {@code open} call's first act — the one the executor dispatches on. */
    public enum OpenOp {
        /** {@code create} (any value): a project is created or previewed. */
        CREATE,
        /** {@code follow}: Follow turns on or off. */
        FOLLOW,
        /** {@code posture}, {@code record} or {@code close: "handoff"}: the shared canvas. */
        CANVAS,
        /** {@code analysis} (non-null): a saved analysis is run. */
        ANALYSIS,
        /** {@code restore}: the last session is restored or its offer dismissed. */
        RESTORE_SESSION,
        /** {@code bundle} (non-null): an evidence bundle is opened. */
        BUNDLE,
        /** {@code project} (non-null): the project switches. */
        PROJECT,
        /** {@code discover} (non-null): candidates are listed, nothing opened. */
        DISCOVER,
        /** {@code close} (non-null). */
        CLOSE,
        /** Anything else: the files named ({@code log}, {@code logs}, {@code graphml}, …), or a refusal if none. */
        FILES
    }

    /** The operation {@code open} dispatches on for {@code p} — the executor's own order and predicates. */
    public static OpenOp open(Map<?, ?> p) {
        if (p == null) return OpenOp.FILES;
        if (p.containsKey("create")) return OpenOp.CREATE;
        if (p.containsKey("follow")) return OpenOp.FOLLOW;
        if (p.get("posture") != null || p.get("record") != null
                || "handoff".equalsIgnoreCase(p.get("close") == null ? null : String.valueOf(p.get("close")))) {
            return OpenOp.CANVAS;
        }
        if (p.get("analysis") != null) return OpenOp.ANALYSIS;
        if (p.containsKey("restore")) return OpenOp.RESTORE_SESSION;
        if (p.get("bundle") != null) return OpenOp.BUNDLE;
        if (p.get("project") != null) return OpenOp.PROJECT;
        boolean alsoOpens = p.get("graphml") != null || p.get("processor") != null || p.get("design") != null
                || p.get("diagnostics") != null;
        if (p.get("log") != null && p.get("format") != null && !alsoOpens) return OpenOp.FILES;
        if (p.get("discover") != null) return OpenOp.DISCOVER;
        if (p.get("close") != null) return OpenOp.CLOSE;
        return OpenOp.FILES;
    }
}
