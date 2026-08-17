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
    /**
     * Where a flag lands: the model rows, and what was concluded about them. Both text fields are
     * optional and a null means "leave whatever is already recorded" — flagging a row to add a suggested
     * fix must not wipe the note that says what the fix is for.
     */
    @FunctionalInterface
    public interface FlagSink {
        void flag(int[] modelRows, String note, String fix);
    }

    private final FlagSink flagRows;
    private TopologyPanel topology;
    private telamin.fluxtion.audit.analyser.analyser.llm.AppControl app;

    public ActionExecutor(Supplier<LogStore> store, Supplier<FilterState> filter, GraphTabs graphTabs,
                          LogTablePanel tablePanel, FlagSink flagRows) {
        this.store = store;
        this.filter = filter;
        this.graphTabs = graphTabs;
        this.tablePanel = tablePanel;
        this.flagRows = flagRows;
    }

    /** Wire the verbs that reach beyond the records table. Optional: unwired verbs report unavailable. */
    public void bind(TopologyPanel topology, telamin.fluxtion.audit.analyser.analyser.llm.AppControl app) {
        this.topology = topology;
        this.app = app;
    }

    /**
     * The export policy for file-writing verbs (screenshot / report): opt-in + directory-confined
     * (B1, review_handoff_16_aug_2026). Unwired → those verbs are refused, which is the safe default.
     */
    public void bindExportPolicy(java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.config.AppConfig> config) {
        this.exportConfig = config;
    }

    private java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.config.AppConfig> exportConfig;

    /** Resolve a verb-supplied write path against the policy; error string when refused. */
    private telamin.fluxtion.audit.analyser.analyser.llm.ExportGuard.Resolved guardedPath(Object requested) {
        var cfg = exportConfig == null ? null : exportConfig.get();
        return telamin.fluxtion.audit.analyser.analyser.llm.ExportGuard.resolve(
                requested == null ? null : requested.toString(),
                cfg != null && cfg.assistantExports,
                cfg == null ? "" : cfg.assistantExportDir);
    }

    @Override
    public ActionResult render(String action, Map<String, Object> params) {
        // these three do not read the records table, and two of them exist precisely to get a log open —
        // requiring one first would make them useless
        switch (action) {
            case "open" -> {
                return onEdt(() -> doOpen(params));
            }
            case "source_root" -> {
                return onEdt(() -> doSourceRoot(params));
            }
            case "topology" -> {
                return onEdt(() -> doTopology(params));
            }
            case "coverage" -> {
                return doCoverage(params);
            }
            case "series" -> {
                // M26.1 — computed HERE, off the EDT, so a token-metered agent never pages raw records
                // to do arithmetic the index can do in milliseconds
                LogStore sStore = store.get();
                if (sStore == null) return ActionResult.error("no log is loaded");
                try {
                    return ActionResult.ok("series", "result",
                            telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan.scan(sStore, params));
                } catch (RuntimeException e) {
                    return ActionResult.error("series failed: " + e.getMessage());
                }
            }
            case "context" -> {
                return onEdt(() -> app == null
                        ? ActionResult.error("'context' is not enabled here")
                        : app.context());
            }
            case "screenshot" -> {
                var out = guardedPath(params.get("path"));   // B1: opt-in + confined; verbs never write elsewhere
                if (!out.ok()) return ActionResult.error(out.error());
                return onEdt(() -> app == null
                        ? ActionResult.error("'screenshot' is not enabled here")
                        : app.screenshot(out.path().toString(), str(params.get("scope"))));
            }
            case "report" -> {
                var out = guardedPath(params.get("path"));   // B1: opt-in + confined; verbs never write elsewhere
                if (!out.ok()) return ActionResult.error(out.error());
                return onEdt(() -> app == null
                        ? ActionResult.error("'report' is not enabled here")
                        // the topology goes in unless asked otherwise: a diagnosis of one cycle that does
                        // not show the cycle is the half of the evidence a reader cannot reconstruct
                        : app.exportFinding(out.path().toString(), intOrNull(params.get("recordIndex")),
                                str(params.get("title")), str(params.get("graph")),
                                !params.containsKey("topology") || bool(params.get("topology"))));
            }
            default -> { }
        }
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

    // ---- coverage --------------------------------------------------------------------------------

    /**
     * Which declared nodes never logged in this run.
     *
     * <p>Deliberately not marshalled to the EDT for the scan: it walks the whole log, and holding the UI
     * thread for a 300-node/20k-record pass is exactly the kind of freeze that makes a tool feel broken.
     * Only the topology read needs the EDT, and that is a set copy.
     */
    private ActionResult doCoverage(Map<String, Object> p) {
        if (topology == null || !topology.hasTopology()) {
            return ActionResult.error("no topology is loaded — 'coverage' compares the graph against "
                    + "the log, so it needs a graphml. Use 'open' with a graphml first.");
        }
        LogStore s = store.get();
        if (s == null) return ActionResult.error("no log is loaded");

        Set<String> declared = onEdt(() -> Set.copyOf(topology.authoredNodeIds()));

        // one pass, honouring the active filter only if asked: coverage over "the records I am looking
        // at" and coverage over "the whole run" are different questions and the caller must pick
        boolean filtered = Boolean.TRUE.equals(p.get("filtered"));
        FilterState f = filtered ? filter.get() : null;
        Set<String> logged = new java.util.LinkedHashSet<>();
        int scanned = 0;
        for (int row = 0; row < s.size(); row++) {
            if (f != null && !f.test(s.index(), row)) continue;
            scanned++;
            for (var nodeLog : s.record(row).nodeLogs()) {
                logged.add(nodeLog.instanceId());
            }
        }

        telamin.fluxtion.audit.analyser.analyser.topology.NodeCoverage cov =
                telamin.fluxtion.audit.analyser.analyser.topology.NodeCoverage.of(
                        declared, logged, Set.of());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("declared", cov.declaredCount());
        out.put("covered", cov.covered().size());
        out.put("uncovered", cov.uncovered().size());
        out.put("ratio", Math.round(cov.ratio() * 1000) / 1000.0);
        out.put("recordsScanned", scanned);
        out.put("scope", filtered ? "current filter" : "whole log");

        int limit = p.get("limit") instanceof Number n ? Math.max(1, n.intValue()) : 100;
        List<Map<String, Object>> never = new ArrayList<>();
        for (String id : cov.uncovered()) {
            if (never.size() >= limit) break;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("instanceId", id);
            var info = onEdt(() -> topology.nodeInfo(id));
            if (info != null && info.className() != null) entry.put("class", info.className());
            never.add(entry);
        }
        out.put("neverLogged", never);
        if (cov.uncovered().size() > never.size()) {
            out.put("neverLoggedTruncated", cov.uncovered().size() - never.size());
        }
        // a node absent from the log may have run silently; say so once rather than let the caller read
        // this as a list of dead code
        out.put("note", "a node appears here if it never wrote audit output. That is 'never logged', "
                + "not proven 'never ran' — a node with no auditLog call, or one whose dirty contract "
                + "stops it early, is silent by design. Build with addEventAudit(LogLevel.TRACE) to make "
                + "absence conclusive.");
        if (cov.buildMismatch()) {
            out.put("loggedButNotInTopology", cov.loggedButNotInTopology().stream().limit(20).toList());
            out.put("warning", "instanceIds in the log are absent from the topology — the graphml is "
                    + "probably from a different build, which makes every other figure here suspect");
        }
        return ActionResult.ok("coverage", "coverage", out);
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
        // reveal what you changed: `topology` brings its tab forward, and a plot the caller cannot see is
        // indistinguishable from one that was never drawn
        if (app != null) app.showTab("Graph");
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
            applyNotesAndAxes(panel, p, s);
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
        String fix = asText(p.get("fix"));
        int[] rowArr = rows.stream().mapToInt(Integer::intValue).toArray();
        return onEdt(() -> {
            flagRows.flag(rowArr, note, fix);
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("flagged", rowArr.length);
            applied.put("recordIndexes", rows.stream().sorted().toList());
            if (note != null) applied.put("note", note);
            if (fix != null) applied.put("fix", fix);
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

    // ---- environment and topology verbs -----------------------------------------------------------

    private ActionResult doOpen(Map<String, Object> params) {
        if (app == null) return ActionResult.error("'open' is not enabled here");
        String log = str(params.get("log"));
        String graphml = str(params.get("graphml"));
        String processor = str(params.get("processor"));
        if (log == null && graphml == null && processor == null) {
            return ActionResult.error("'open' needs 'log', 'graphml' and/or 'processor'");
        }
        Map<String, Object> echo = new java.util.LinkedHashMap<>();
        if (log != null) {
            ActionResult r = app.openLog(log);
            if (!r.ok()) return r;
            echo.put("log", log);
        }
        if (graphml != null) {
            ActionResult r = app.openGraphml(graphml);
            if (!r.ok()) return r;
            echo.put("graphml", graphml);
        }
        if (processor != null) {
            ActionResult r = app.selectProcessor(processor);
            if (!r.ok()) return r;
            echo.put("processor", processor);
        }
        return ActionResult.ok("open", "opened", echo);
    }

    private ActionResult doSourceRoot(Map<String, Object> params) {
        if (app == null) return ActionResult.error("'source_root' is not enabled here");
        List<String> added = new java.util.ArrayList<>();
        List<String> rejected = new java.util.ArrayList<>();
        for (String path : strList(params.get("add"))) {
            if (app.addSourceRoot(path)) added.add(path);
            else rejected.add(path);
        }
        List<String> removed = new java.util.ArrayList<>();
        for (String path : strList(params.get("remove"))) {
            if (app.removeSourceRoot(path)) removed.add(path);
        }
        Map<String, Object> echo = new java.util.LinkedHashMap<>();
        echo.put("roots", app.sourceRoots());
        if (!added.isEmpty()) echo.put("added", added);
        if (!removed.isEmpty()) echo.put("removed", removed);
        // a path that is not a source root is reported rather than silently ignored: the caller would
        // otherwise go on to wonder why source navigation still finds nothing
        if (!rejected.isEmpty()) echo.put("notASourceRoot", rejected);
        return ActionResult.ok("source_root", "sourceRoots", echo);
    }

    private ActionResult doTopology(Map<String, Object> params) {
        if (topology == null) return ActionResult.error("'topology' is not enabled here");
        if (!topology.hasTopology()) {
            return ActionResult.error("no topology is loaded — use 'open' with a graphml first");
        }
        if (app != null) app.showTab("Topology");

        if (params.containsKey("scaffolding")) topology.setScaffoldingVisible(bool(params.get("scaffolding")));
        if (params.containsKey("showAll") && bool(params.get("showAll"))) topology.clearView();

        // Tracking is set BEFORE anything that could follow, so one call can turn it off AND select
        // without the selection dragging the source pane along on its way out. Ordering is the whole
        // meaning of the flag here.
        if (params.containsKey("sync")) topology.setSourceSync(bool(params.get("sync")));

        if (params.containsKey("select")) {
            String id = str(params.get("select"));
            if (id != null && !topology.hasNode(id)) {
                return ActionResult.error("no node '" + id + "' in this topology");
            }
            topology.selectNode(id);
        }
        String scope = str(params.get("scope"));
        if (scope != null) {
            try {
                topology.setScope(telamin.fluxtion.audit.analyser.analyser.topology.TopologyFocus.Scope
                        .valueOf(scope.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return ActionResult.error("unknown scope '" + scope + "'");
            }
        }
        // M27: pop leaves contexts ("all" = back to the full graph); focus accepts a BOOLEAN
        // (true pushes the selection's scope as a context, false exits the filter) or a STRING
        // (recall a named focus); saveFocusAs names the current context, with an optional rationale.
        Object pop = params.get("pop");
        if (pop != null) {
            topology.popFocus("all".equalsIgnoreCase(String.valueOf(pop)));
        }
        Object focus = params.get("focus");
        if (focus instanceof String namedFocus) {
            String err = topology.recallFocus(namedFocus);
            if (err != null) return ActionResult.error(err);
        } else if (focus != null) {
            topology.setFocus(bool(focus));
        }
        String saveFocusAs = str(params.get("saveFocusAs"));
        if (saveFocusAs != null) {
            String err = topology.saveFocusAs(saveFocusAs, str(params.get("rationale")));
            if (err != null) return ActionResult.error(err);
        }
        if (params.containsKey("source")) topology.setSourcePaneVisible(bool(params.get("source")));
        // deliberately a visibility switch and nothing more: the callout's TEXT is the record's flag, so
        // there is exactly one place to write a diagnosis and this is not it
        if (params.containsKey("callout")) topology.setCalloutVisible(bool(params.get("callout")));

        String orientation = str(params.get("orientation"));
        if (orientation != null) {
            topology.setOrientation("left_right".equalsIgnoreCase(orientation)
                    ? telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout.Orientation.LEFT_RIGHT
                    : telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout.Orientation.TOP_DOWN);
        }
        Integer record = intOrNull(params.get("recordIndex"));
        if (record != null) topology.moveToRecord(record);
        Integer step = intOrNull(params.get("step"));
        if (step != null && step != 0) topology.step(step);
        if (params.containsKey("fit") && bool(params.get("fit"))) topology.fit();

        java.util.Map<String, Object> echo = topology.cursorState();
        if (!topology.lastRecallNote().isEmpty()) echo.put("recallNote", topology.lastRecallNote());
        return ActionResult.ok("topology", "topology", echo);
    }

    /**
     * Apply the reader-facing annotations: the explanation block, notes pinned to moments, and which
     * series belong on a second scale.
     *
     * <p>A note may be anchored by {@code at} (epoch millis) or by {@code recordIndex}, because a caller
     * that has just found something with {@code read} or {@code aggregate} has the index to hand and
     * should not have to convert it. An index that does not resolve is dropped rather than pinned to
     * zero — a note at the wrong moment is worse than a missing one.
     */
    private void applyNotesAndAxes(telamin.fluxtion.audit.analyser.analyser.ui.GraphPanel panel,
                                   Map<String, Object> p, LogStore store) {
        var notes = panel.notes();
        if (bool(p.get("clearNotes"))) {
            notes = notes.withoutNotes();
        }
        String explanation = str(p.get("explanation"));
        if (explanation != null) {
            notes = notes.withExplanation(explanation);
        }
        if (p.get("notes") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                String text = str(m.get("text"));
                if (text == null || text.isBlank()) continue;
                Long at = anchorMillis(m, store);
                if (at == null) continue;
                notes = notes.plus(new telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.Note(
                        at, text, str(m.get("series"))));
            }
        }
        panel.setNotes(notes);

        if (p.containsKey("rightAxis")) {
            panel.setAxes(new telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment(
                    strList(p.get("rightAxis"))));
        }
    }

    /** A note's moment, from an explicit time or from the record it refers to. */
    private static Long anchorMillis(Map<?, ?> note, LogStore store) {
        // epoch millis do NOT fit in an int — parsing this as one silently wraps to a negative and pins
        // the note somewhere in 1969
        Long at = longOrNull(note.get("at"));
        if (at != null && at > 0) {
            return at;
        }
        Long index = longOrNull(note.get("recordIndex"));
        if (index == null || store == null || index < 0 || index >= store.size()) {
            return null;
        }
        return store.record(index.intValue()).logTime();
    }

    private static Long longOrNull(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? null : Long.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(o));
    }

    private static Integer intOrNull(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return o == null ? null : Integer.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> strList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new java.util.ArrayList<>(list.size());
        for (Object item : list) if (item != null) out.add(item.toString());
        return out;
    }

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
