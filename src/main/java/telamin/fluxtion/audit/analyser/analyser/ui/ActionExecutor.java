package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.RenderExecutor;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Applies the <b>render</b> verbs (filter / graph / goto / flag) for the assistant action interface
 * (spec-assistant-actions §4.2–4.5). Bridges to the shared {@link FilterState}, {@link GraphTabs}, the
 * records table and the flag store; every UI mutation is marshalled to the EDT. Offset‑addressed verbs
 * (goto/flag) <b>floor‑resolve and clamp</b> a byte offset to the record that contains it, and echo the
 * resolution back so the model knows exactly where it landed.
 */
public final class ActionExecutor implements RenderExecutor {

    private static final int DISCOVER_LIMIT = 20_000;   // records scanned to enumerate graphable keys

    private final Supplier<LogStore> store;
    private final Supplier<FilterState> filter;
    private final GraphTabs graphTabs;
    private final LogTablePanel tablePanel;
    private final BiConsumer<int[], String> flagRows;   // (model rows, optional note)

    public ActionExecutor(Supplier<LogStore> store, Supplier<FilterState> filter, GraphTabs graphTabs,
                          LogTablePanel tablePanel, BiConsumer<int[], String> flagRows) {
        this.store = store;
        this.filter = filter;
        this.graphTabs = graphTabs;
        this.tablePanel = tablePanel;
        this.flagRows = flagRows;
    }

    @Override
    public ActionResult render(String action, Map<String, Object> params) {
        LogStore s = store.get();
        if (s == null) return ActionResult.error("no log is loaded");
        return switch (action) {
            case "filter" -> doFilter(params);
            case "graph" -> doGraph(s, params);
            case "goto" -> doGoto(s, params);
            case "flag" -> doFlag(s, params);
            default -> ActionResult.error("unknown render verb '" + action + "'");
        };
    }

    // ---- filter ----------------------------------------------------------------------------------

    private ActionResult doFilter(Map<String, Object> p) {
        FilterState f = filter.get();
        if (f == null) return ActionResult.error("no log is loaded");
        return onEdt(() -> {
            // "missing = unchanged, null = cleared" — distinguished via containsKey
            Long from = p.containsKey("from") ? asLong(p.get("from")) : f.fromMillis();
            Long to = p.containsKey("to") ? asLong(p.get("to")) : f.toMillis();
            f.setTimeRange(from, to);
            // null = cleared (all); [] = none; [..] = those dimensions (OR)
            if (p.containsKey("dimensions")) {
                Object dv = p.get("dimensions");
                f.setDimensions(dv == null ? null : asStringSet(dv));
            }
            if (p.containsKey("text")) f.setText(asText(p.get("text")));

            Map<String, Object> applied = new LinkedHashMap<>();   // echo the FULL resulting state
            applied.put("from", f.fromMillis());
            applied.put("to", f.toMillis());
            // null = all (no constraint); [] = none; [..] = the selected dimensions — mirror the contract
            applied.put("dimensions", f.dimensions() == null ? null : new ArrayList<>(f.dimensions()));
            applied.put("text", f.text());
            return ActionResult.ok("filter", "applied", applied);
        });
    }

    // ---- graph -----------------------------------------------------------------------------------

    private ActionResult doGraph(LogStore s, Map<String, Object> p) {
        // rename requires an explicit target {name, rename} — never selection-dependent
        if (p.containsKey("rename")) {
            String from = asText(p.get("name")), to = asText(p.get("rename"));
            if (from == null) return ActionResult.error("graph rename needs the target 'name'");
            return onEdt(() -> graphTabs.renameNamed(from, to)
                    ? ActionResult.ok("graph", "applied", Map.of("renamed", from + " → " + to))
                    : ActionResult.error("no graph named '" + from + "'"));
        }

        List<String> requested = asStringList(p.get("series"));

        // Parse formulas by STRUCTURE (refs split on the first dot — no discovery needed); collect their
        // refs so we can tell the agent which exist. Only syntax errors block a formula.
        List<Object[]> parsedExprs = new ArrayList<>();   // {label, exprText, Resolve, Expr}
        List<Map<String, Object>> exprEcho = new ArrayList<>();
        Set<String> wanted = new java.util.LinkedHashSet<>(requested);
        for (Object o : asList(p.get("exprs"))) {
            if (!(o instanceof Map<?, ?> em)) continue;
            String exprText = asText(em.get("expr"));
            String label = asText(em.get("label"));
            String lbl = label == null ? exprText : label;
            if (exprText == null) {
                exprEcho.add(errEcho(lbl, "missing 'expr'"));
                continue;
            }
            try {
                Expr e = Expr.parse(exprText);
                for (GraphKey k : e.refs()) wanted.add(k.display());
                parsedExprs.add(new Object[]{lbl, exprText, resolveOf(asText(em.get("resolve"))), e});
            } catch (RuntimeException ex) {
                exprEcho.add(errEcho(lbl, ex.getMessage()));
            }
        }

        // Accurate resolution over the WHOLE log (targeted, early-exit) — a key that only fires late still
        // resolves. Plus a sample of real keys to suggest when something is unresolved.
        Set<String> found = SeriesExtractor.resolveExisting(s, wanted);
        List<String> availableSample = SeriesExtractor.discover(s, new FilterState(), DISCOVER_LIMIT)
                .stream().map(GraphKey::display).toList();

        // Raw series: add EVERY well-formed key regardless of resolution — extraction scans the whole log,
        // so a real-but-rare key plots; a genuinely-absent one is flagged (not silently dropped).
        List<GraphKey> toAdd = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String req : requested) {
            GraphKey k = GraphKey.fromDisplay(req);
            if (k == null) { unresolved.add(req); continue; }   // malformed — no instanceId.key dot
            toAdd.add(k);
            if (!found.contains(req)) unresolved.add(req);
        }
        for (Object[] pe : parsedExprs) {
            Expr e = (Expr) pe[3];
            List<String> unseen = e.refs().stream().map(GraphKey::display).filter(d -> !found.contains(d)).toList();
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("label", pe[0]);
            echo.put("ok", true);
            if (!unseen.isEmpty()) echo.put("unseenRefs", unseen);
            exprEcho.add(echo);
        }

        String name = asText(p.get("name"));
        String style = asText(p.get("style"));
        String rationale = asText(p.get("rationale"));   // provenance: why the agent built this graph
        boolean newTab = Boolean.TRUE.equals(p.get("newTab"));
        boolean pinRequested = p.containsKey("from") || p.containsKey("to");
        Long from = asLong(p.get("from"));
        Long to = asLong(p.get("to"));

        return onEdt(() -> {
            GraphPanel panel = graphTabs.graphForAction(name, newTab);
            if (panel == null) return ActionResult.error("could not open a graph (no log loaded)");
            panel.addKeys(toAdd);
            for (Object[] pe : parsedExprs) panel.addExpr((String) pe[0], (String) pe[1], (SeriesExtractor.Resolve) pe[2]);
            if (style != null) panel.setStyleByName(style);
            if (rationale != null) panel.setCaption(rationale);   // caption the plot with the agent's reason
            if (pinRequested) panel.pin(from, to);   // explicit range → pin (evidence artifact); else follows
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("name", name == null ? "(current)" : name);
            applied.put("resolved", requested.stream().filter(found::contains).toList());
            applied.put("unresolved", unresolved);
            if (!exprEcho.isEmpty()) applied.put("exprs", exprEcho);
            if (!unresolved.isEmpty() || exprEcho.stream().anyMatch(e -> e.containsKey("unseenRefs"))) {
                applied.put("availableKeysSample", availableSample.stream().limit(40).toList());
                applied.put("note", "graphable keys are TOP-LEVEL numeric/boolean nodeLog values "
                        + "(instanceId.key); a value nested inside a toString (e.g. MutableOrder(price=..)) "
                        + "is not itself a key. Unresolved series are still added (they plot when/if the key fires).");
            }
            if (panel.isPinned()) {
                Map<String, Object> pinned = new LinkedHashMap<>();
                pinned.put("from", panel.pinnedFrom());
                pinned.put("to", panel.pinnedTo());
                applied.put("pinned", pinned);
            }
            return ActionResult.ok("graph", "applied", applied);
        });
    }

    private static Map<String, Object> errEcho(String label, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("error", error);
        return m;
    }

    // ---- goto ------------------------------------------------------------------------------------

    private ActionResult doGoto(LogStore s, Map<String, Object> p) {
        int row = targetRow(s.index(), p, "byteOffset", "recordIndex");
        if (row < 0) return ActionResult.error("goto needs a byteOffset or recordIndex");
        boolean reveal = Boolean.TRUE.equals(p.get("reveal"));
        return onEdt(() -> {
            FilterState f = filter.get();
            boolean visible = tablePanel.selectModelRow(row);
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("recordIndex", row);
            applied.put("byteOffset", s.index().offset(row));
            if (!visible) {
                if (reveal && f != null) {
                    revealRecord(f, s, row);
                    boolean nowVisible = tablePanel.selectModelRow(row);
                    applied.put("revealed", nowVisible);
                    if (!nowVisible) applied.put("note", "relaxed the filter, but the record is still hidden "
                            + "(likely 'Records ▸ Show flagged only')");
                } else {
                    applied.put("visible", false);
                    applied.put("note", "the record is filtered out of the table view — pass reveal:true to show it"
                            + hidingReason(f, s, row));
                }
            }
            return ActionResult.ok("goto", "applied", applied);
        });
    }

    /** Relax the filter minimally so record {@code row} passes: widen the window, add its dimension, drop text. */
    private void revealRecord(FilterState f, LogStore s, int row) {
        LogIndex idx = s.index();
        Long lt = idx.logTime(row);
        if (lt != null) {
            Long from = f.fromMillis();
            Long to = f.toMillis();
            Long nf = (from != null && lt < from) ? lt : from;
            Long nt = (to != null && lt > to) ? lt : to;
            f.setTimeRange(nf, nt);
        }
        Set<String> dims = f.dimensions();
        if (dims != null) {
            String key = f.groupKey(idx, row);
            if (!dims.contains(key)) {
                Set<String> nd = new HashSet<>(dims);
                nd.add(key);
                f.setDimensions(nd);
            }
        }
        if (!f.test(idx, row) && f.text() != null && !f.text().isBlank()) {
            f.setText(null);   // a text filter still hides it → clear it (reveal is opt-in)
        }
    }

    /** A human hint naming which filter constraint hides record {@code row}, or "" if none is obvious. */
    private String hidingReason(FilterState f, LogStore s, int row) {
        if (f == null) return "";
        LogIndex idx = s.index();
        List<String> why = new ArrayList<>();
        Long lt = idx.logTime(row);
        if (lt != null && ((f.fromMillis() != null && lt < f.fromMillis())
                || (f.toMillis() != null && lt > f.toMillis()))) {
            why.add("time range");
        }
        Set<String> dims = f.dimensions();
        String key = f.groupKey(idx, row);
        if (dims != null && !dims.contains(key)) why.add("dimension '" + key + "'");
        if (f.text() != null && !f.text().isBlank() && !f.test(idx, row) && why.isEmpty()) why.add("text filter");
        return why.isEmpty() ? "" : " (hidden by: " + String.join(", ", why) + ")";
    }

    // ---- flag ------------------------------------------------------------------------------------

    private ActionResult doFlag(LogStore s, Map<String, Object> p) {
        LogIndex idx = s.index();
        Set<Integer> rows = new HashSet<>();
        for (Object o : asList(p.get("byteOffsets"))) {
            Long off = asLong(o);
            if (off != null) rows.add(floorRow(idx, off));
        }
        for (Object o : asList(p.get("recordIndexes"))) {
            Long ri = asLong(o);
            if (ri != null) rows.add(clampRow(idx, ri.intValue()));
        }
        if (rows.isEmpty()) return ActionResult.error("flag needs byteOffsets[] or recordIndexes[]");
        String note = asText(p.get("note"));
        int[] rowArr = rows.stream().mapToInt(Integer::intValue).toArray();
        return onEdt(() -> {
            flagRows.accept(rowArr, note);
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("flagged", rowArr.length);
            applied.put("recordIndexes", rows.stream().sorted().toList());
            if (note != null) applied.put("note", note);
            return ActionResult.ok("flag", "applied", applied);
        });
    }

    // ---- offset resolution (pure; unit-tested by GotoResolveTest) --------------------------------

    /** Pick a target model row from {@code byteOffset} (floor) or {@code recordIndex} (clamp); -1 if neither. */
    static int targetRow(LogIndex idx, Map<String, Object> p, String offsetKey, String indexKey) {
        if (p.get(offsetKey) instanceof Number n) return floorRow(idx, n.longValue());
        if (p.get(indexKey) instanceof Number n) return clampRow(idx, n.intValue());
        return -1;
    }

    /** The record whose {@code [offset, offset+len)} contains {@code byteOffset}; clamps to first/last. */
    static int floorRow(LogIndex idx, long byteOffset) {
        int n = idx.size();
        if (n == 0) return -1;
        if (byteOffset <= idx.offset(0)) return 0;
        if (byteOffset >= idx.offset(n - 1)) return n - 1;   // within-last or past-EOF → clamp to last
        int lo = 0, hi = n - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (idx.offset(mid) <= byteOffset) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    static int clampRow(LogIndex idx, int recordIndex) {
        return Math.max(0, Math.min(recordIndex, idx.size() - 1));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Run {@code body} on the EDT and return its result (render verbs mutate Swing state). */
    private <T> T onEdt(Callable<T> body) {
        if (SwingUtilities.isEventDispatchThread()) return call(body);
        @SuppressWarnings("unchecked") final T[] out = (T[]) new Object[1];
        final RuntimeException[] err = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    out[0] = body.call();
                } catch (Exception e) {
                    err[0] = e instanceof RuntimeException re ? re : new RuntimeException(e);
                }
            });
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        if (err[0] != null) throw err[0];
        return out[0];
    }

    private static <T> T call(Callable<T> body) {
        try {
            return body.call();
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private static SeriesExtractor.Resolve resolveOf(String s) {
        try {
            return s == null ? SeriesExtractor.Resolve.LOCF : SeriesExtractor.Resolve.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SeriesExtractor.Resolve.LOCF;
        }
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static String asText(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static List<String> asStringList(Object o) {
        List<String> out = new ArrayList<>();
        for (Object e : asList(o)) if (e != null) out.add(e.toString());
        return out;
    }

    private static Set<String> asStringSet(Object o) {
        return new HashSet<>(asStringList(o));
    }
}
