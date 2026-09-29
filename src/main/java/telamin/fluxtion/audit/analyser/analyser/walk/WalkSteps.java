package telamin.fluxtion.audit.analyser.analyser.walk;

import telamin.fluxtion.audit.analyser.analyser.config.ChartNames;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M69 — reads and validates walk steps against the fixed allow-list ({@code spec-spotlight-walks.md} §3.3, W-A7).
 * Pure: no store, no frame. Anything outside the allow-list is refused BY NAME, before anything is saved.
 *
 * <p>Steps parsed here carry no bases yet: those are computed by the analyser from what is open at save
 * ({@link WalkSpec.Basis}). A client cannot supply a digest; it can only point.
 */
public final class WalkSteps {

    private WalkSteps() {
    }

    /** The fields a step may carry. */
    static final Set<String> STEP_FIELDS = Set.of("caption", "view", "targets", "id", "conversationThrough");
    /** The fields a step's view may carry (§3.3). Not a chart window, not an open, not anything that writes. */
    static final Set<String> VIEW_FIELDS = Set.of("tab", "filter", "record", "graph", "focus");
    /** A filter is always complete; these are its fields. */
    static final Set<String> FILTER_FIELDS = Set.of("from", "to", "groupMode", "dimensions", "text");
    static final Set<String> TARGET_FIELDS = Set.of("target", "caption");
    static final Set<String> GROUP_MODES = Set.of("DIMENSION", "RAW_EVENT");

    /**
     * The spotlight families a walk step may point at in v1 — those whose basis §3.5 can state. Source (design and
     * Java), toolbar and menu targets are refused: their identity would be a revision of a document or a UI layout,
     * which a walk has no basis for yet.
     */
    static final Set<SpotlightTarget.Family> ALLOWED = Set.of(
            SpotlightTarget.Family.TAB, SpotlightTarget.Family.RECORDS, SpotlightTarget.Family.RECORDS_ROW,
            SpotlightTarget.Family.DETAIL, SpotlightTarget.Family.DETAIL_NODE, SpotlightTarget.Family.TOPOLOGY,
            SpotlightTarget.Family.TOPOLOGY_NODE, SpotlightTarget.Family.TOPOLOGY_VERDICT, SpotlightTarget.Family.GRAPH,
            SpotlightTarget.Family.GRAPH_NOTE, SpotlightTarget.Family.GRAPH_SERIES, SpotlightTarget.Family.PROJECT,
            SpotlightTarget.Family.PROJECT_ROW, SpotlightTarget.Family.STATUS);

    /** A parse: the steps, or the first refusal with its path ("step 2: view.window is not a walk view field …"). */
    public record Parsed(List<WalkSpec.Step> steps, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** Parse the verb's {@code steps} array. */
    public static Parsed parse(Object raw) {
        if (!(raw instanceof List<?> list)) return refuse("'steps' is a list of steps: [{caption?, view?, targets}]");
        if (list.isEmpty()) return refuse("a walk needs at least one step");
        if (list.size() > WalkSpec.MAX_STEPS) {
            return refuse("at most " + WalkSpec.MAX_STEPS + " steps — " + list.size() + " were given");
        }
        List<WalkSpec.Step> steps = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String at = "step " + (i + 1) + ": ";
            if (!(list.get(i) instanceof Map<?, ?> m)) return refuse(at + "a step is an object {caption?, view?, targets}");
            String unknown = unknownField(m, STEP_FIELDS);
            if (unknown != null) return refuse(at + "'" + unknown + "' is not a step field — a step has caption, view, targets, and (with "
                    + "a conversation) id and conversationThrough");
            WalkSpec.View view;
            try {
                view = view(m.get("view"));
            } catch (IllegalArgumentException e) {
                return refuse(at + e.getMessage());
            }
            List<WalkSpec.Target> targets = new ArrayList<>();
            Object t = m.get("targets");
            if (t != null) {
                if (!(t instanceof List<?> tl)) return refuse(at + "'targets' is a list of {target, caption?}");
                for (Object o : tl) {
                    if (o instanceof String s) {
                        targets.add(new WalkSpec.Target(s, "", null));
                    } else if (o instanceof Map<?, ?> tm) {
                        String bad = unknownField(tm, TARGET_FIELDS);
                        if (bad != null) return refuse(at + "'" + bad + "' is not a target field — a target has target, caption");
                        targets.add(new WalkSpec.Target(str(tm.get("target")), str(tm.get("caption")), null));
                    } else {
                        return refuse(at + "a target is \"records:row:12\" or {target, caption}");
                    }
                }
            }
            WalkSpec.Step step = new WalkSpec.Step(str(m.get("caption")), view, targets, str(m.get("id")),
                    str(m.get("conversationThrough")));
            String problem = problem(step);
            if (problem != null) return refuse(at + problem);
            steps.add(step);
        }
        return new Parsed(List.copyOf(steps), null);
    }

    /** What is wrong with one step, or null (used for parsed and captured steps alike). */
    public static String problem(WalkSpec.Step step) {
        if (step.targets().isEmpty() && step.caption().isBlank() && step.view().isEmpty()) {
            return "an empty step — give it a caption, a view or at least one target";
        }
        if (step.targets().size() > SpotlightTarget.MAX_LIT) {
            return "at most " + SpotlightTarget.MAX_LIT + " targets in a step — " + step.targets().size() + " were given";
        }
        if (step.caption().length() > 500 || step.caption().chars().anyMatch(ch -> ch == '\n' || ch == '\r')) {
            return "a step's caption is one line of at most 500 characters";
        }
        WalkSpec.View v = step.view();
        if (v.tab() != null && !SpotlightTarget.TABS.contains(v.tab())) {
            return "view.tab '" + v.tab() + "' is not a tab — one of " + new java.util.TreeSet<>(SpotlightTarget.TABS);
        }
        if (v.record() != null && v.record() < 0) return "view.record is a record index, 0 or more";
        if (v.graph() != null) {
            String p = ChartNames.problem(v.graph());
            if (p != null) return "view.graph: " + p;
        }
        if (v.focus() != null && v.focus().name().isBlank()) return "view.focus names a saved topology focus";
        WalkSpec.Filter f = v.filter();
        if (f != null) {
            if (!GROUP_MODES.contains(f.groupMode())) {
                return "view.filter.groupMode is DIMENSION or RAW_EVENT — '" + f.groupMode() + "' is neither";
            }
            if (f.from() != null && f.to() != null && f.from() > f.to()) {
                return "view.filter.from is after view.filter.to";
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (WalkSpec.Target t : step.targets()) {
            if (t.target().isBlank()) return "a target needs its address, e.g. \"records:row:12\"";
            SpotlightTarget.Parsed parsed = SpotlightTarget.parse(t.target());
            if (!parsed.ok()) return "target '" + t.target() + "': " + parsed.error();
            SpotlightTarget.Family family = parsed.target().family();
            if (!ALLOWED.contains(family)) {
                return "target '" + t.target() + "' (" + family.form() + ") cannot be a walk step in this version — "
                        + "a walk points at records, detail, topology, charts, tabs, the project panel and the status "
                        + "line; source, toolbar and menu targets have no basis a walk can check";
            }
            if ((family == SpotlightTarget.Family.DETAIL_NODE || family == SpotlightTarget.Family.DETAIL)
                    && v.record() == null) {
                return "target '" + t.target() + "' shows the SELECTED record's detail, so the step must select one "
                        + "(view.record)";
            }
            if (isChart(family) && parsed.target().graph() == null && v.graph() == null) {
                return "target '" + t.target() + "' means the SELECTED chart, so the step must select one (view.graph) "
                        + "or name it (graph:<name>…)";
            }
            String captionError = SpotlightTarget.captionError(t.caption().isBlank() ? null : t.caption());
            if (captionError != null) return "target '" + t.target() + "': " + captionError;
            if (!seen.add(t.target().toLowerCase(java.util.Locale.ROOT))) {
                return "target '" + t.target() + "' appears twice in one step";
            }
        }
        return null;
    }

    static boolean isChart(SpotlightTarget.Family f) {
        return f == SpotlightTarget.Family.GRAPH || f == SpotlightTarget.Family.GRAPH_NOTE
                || f == SpotlightTarget.Family.GRAPH_SERIES;
    }

    /** The basis kind a target needs (§3.5). */
    public static String basisKind(String target) {
        SpotlightTarget.Parsed p = SpotlightTarget.parse(target);
        if (!p.ok()) return "none";
        return switch (p.target().family()) {
            case RECORDS_ROW, DETAIL, DETAIL_NODE -> "record";
            case GRAPH, GRAPH_NOTE, GRAPH_SERIES -> "chart";
            case TOPOLOGY_NODE -> "graph";
            default -> "none";
        };
    }

    private static WalkSpec.View view(Object raw) {
        if (raw == null) return WalkSpec.View.NONE;
        if (!(raw instanceof Map<?, ?> m)) throw new IllegalArgumentException("'view' is an object {tab?, filter?, record?, graph?, focus?}");
        String unknown = unknownField(m, VIEW_FIELDS);
        if (unknown != null) {
            throw new IllegalArgumentException("view." + unknown + " is not a walk view field — a view may set tab, "
                    + "filter, record, graph and focus only" + ("window".equals(unknown) || "from".equals(unknown)
                    || "pin".equals(unknown) ? " (a chart's window is not restored by a walk in this version)" : ""));
        }
        WalkSpec.Filter filter = null;
        Object fr = m.get("filter");
        if (fr != null) {
            if (!(fr instanceof Map<?, ?> fm)) throw new IllegalArgumentException("view.filter is an object");
            String bad = unknownField(fm, FILTER_FIELDS);
            if (bad != null) {
                throw new IllegalArgumentException("view.filter." + bad + " is not a filter field — from, to, groupMode, "
                        + "dimensions, text");
            }
            List<String> dims = null;
            Object d = fm.get("dimensions");
            if (d instanceof List<?> dl) {
                dims = new ArrayList<>();
                for (Object o : dl) dims.add(String.valueOf(o));
            } else if (d != null && !"all".equals(d)) {
                throw new IllegalArgumentException("view.filter.dimensions is a list of names, or \"all\"");
            }
            filter = new WalkSpec.Filter(lng(fm.get("from"), "view.filter.from"), lng(fm.get("to"), "view.filter.to"),
                    str(fm.get("groupMode")), dims, fm.get("text") == null ? "" : String.valueOf(fm.get("text")));
        }
        Integer record = null;
        Object r = m.get("record");
        if (r != null) {
            // review PR57 R9: range-checked BEFORE narrowing — intValue() turned 4294967296 into record 0
            Long index = integral(r, 0, Integer.MAX_VALUE);
            if (index == null) {
                throw new IllegalArgumentException("view.record is a record index: a whole number from 0 to "
                        + Integer.MAX_VALUE);
            }
            record = index.intValue();
        }
        WalkSpec.FocusRef focus = null;
        Object fo = m.get("focus");
        if (fo != null) {
            if (!(fo instanceof String fs)) throw new IllegalArgumentException("view.focus is the name of a saved topology focus");
            focus = new WalkSpec.FocusRef(fs, "");
        }
        return new WalkSpec.View(str(m.get("tab")), filter, record, str(m.get("graph")), focus);
    }

    private static Long lng(Object o, String field) {
        if (o == null) return null;
        Long millis = integral(o, Long.MIN_VALUE, Long.MAX_VALUE);
        if (millis == null) throw new IllegalArgumentException(field + " is epoch milliseconds, a whole number");
        return millis;
    }

    /**
     * Review PR57 R9: {@code o} as a whole number in {@code [min, max]}, or null. It is checked EXACTLY before any
     * narrowing: finite, integral, and in range. A JSON number may arrive as any {@link Number}, including a
     * {@link java.math.BigInteger} beyond {@code long}.
     */
    public static Long integral(Object o, long min, long max) {
        if (!(o instanceof Number n)) return null;
        java.math.BigDecimal v;
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) return null;
            v = java.math.BigDecimal.valueOf(d);
        } else {
            try {
                v = new java.math.BigDecimal(n.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (v.signum() != 0 && v.stripTrailingZeros().scale() > 0) return null;
        if (v.compareTo(java.math.BigDecimal.valueOf(min)) < 0 || v.compareTo(java.math.BigDecimal.valueOf(max)) > 0) {
            return null;
        }
        return v.longValueExact();
    }

    private static String unknownField(Map<?, ?> m, Set<String> allowed) {
        for (Object k : m.keySet()) {
            if (!allowed.contains(String.valueOf(k))) return String.valueOf(k);
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Parsed refuse(String why) {
        return new Parsed(List.of(), why);
    }
}
