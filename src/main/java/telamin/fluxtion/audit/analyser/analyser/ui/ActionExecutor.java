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
        void flag(int[] modelRows, String note, String fix, String kind);
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

    /**
     * The session's decided state (M44.4b/c): what may be asserted about coverage (M44.2), and the identity of the pair
     * a comparison is made against. Read from the socket thread — the snapshot is immutable and published after each
     * completed operation. Optional: an unwired executor scores as before, because a missing opinion must not become a
     * refusal.
     */
    private java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot> sessionSnapshot;

    /** The pair identity a coverage comparison carries to the session (M44.4c). */
    public static final String PAIR_LOG_GENERATION = "pairLogGeneration";
    public static final String PAIR_GRAPH_REVISION = "pairGraphRevision";

    private java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity> readIdentity;

    /** M68.5: what changed about the open log's file, observed at each record-reading request. */
    public void bindReadIdentity(
            java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity> identity) {
        this.readIdentity = identity;
    }

    @Override
    public telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity readIdentity() {
        return readIdentity == null ? null : readIdentity.get();
    }

    public void bindSessionSnapshot(
            java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot> snapshot) {
        this.sessionSnapshot = snapshot;
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
    private java.util.function.Supplier<java.util.Set<java.nio.file.Path>> readGrants = java.util.Set::of;
    private java.util.function.Supplier<String> timeOrderNote = () -> null;

    /** Non-null while the loaded log has time-order violations — appended to time-anchored echoes (D-R4). */
    public void setTimeOrderNote(java.util.function.Supplier<String> note) {
        this.timeOrderNote = note == null ? () -> null : note;
    }

    /** Files the user picked in a chooser this session — the D-F4 read allowlist's second half. */
    public void setReadGrants(java.util.function.Supplier<java.util.Set<java.nio.file.Path>> readGrants) {
        this.readGrants = readGrants == null ? java.util.Set::of : readGrants;
    }

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
        // M64 D-SP3: a verb that changes the view puts the spotlight out. A spotlight that outlives its context points
        // at the wrong thing, which is worse than none. The list is SpotlightTarget's, so what ends a spotlight is
        // stated once; `screenshot` and `context` are deliberately not on it — they are how the tutor checks what it
        // lit. …except `open`'s canvas form (M48.7): posture and the selector's record change no view.
        //
        // M68.4 (D-E3, "what a refusal preserves"): put out when the verb SUCCEEDED, not before it was validated. It
        // used to go out first, so a refused call — no log loaded, a bad anchor, a malformed follow — had already
        // destroyed the context the caller was pointing at, and the view it refused to change was left unlit.
        ActionResult result = renderVerb(action, params);
        if (app != null && putsOutSpotlight(action, params, result)) {
            onEdt(() -> {
                app.clearSpotlight();
                return null;
            });
        }
        return result;
    }

    /** The policy, stated once and tested per verb: a view-changing verb that SUCCEEDED puts the spotlight out. */
    static boolean putsOutSpotlight(String action, Map<String, Object> params, ActionResult result) {
        boolean changesTheView = SpotlightTarget.VIEW_CHANGING_VERBS.contains(action)
                && !("open".equals(action) && params != null && isCanvasWrite(params));
        return changesTheView && result != null && result.ok();
    }

    private ActionResult renderVerb(String action, Map<String, Object> params) {
        // these three do not read the records table, and two of them exist precisely to get a log open —
        // requiring one first would make them useless
        switch (action) {
            case "open" -> {
                if (params.containsKey("follow")) {
                    if (params.size() != 1 || !(params.get("follow") instanceof Boolean))
                        return ActionResult.error("use open {follow: true|false} alone, after the log has finished opening");
                    return app == null ? ActionResult.error("Follow is not enabled here; not following")
                            : onEdt(() -> app.follow((Boolean) params.get("follow")));
                }
                if (isCanvasWrite(params)) return onEdt(() -> doOpenCanvas(params));   // M48.7: needs no log, opens no file
                if (params.get("analysis") != null) return doOpenAnalysis(params);   // M38.4: off the EDT, see method
                return doOpen(params);
            }
            case "source_root" -> {
                return onEdt(() -> doSourceRoot(params));
            }
            case "source" -> {
                return app == null ? ActionResult.error("source navigation is not enabled here") : app.source(params);
            }
            case "spotlight" -> {
                // M64: needs no log — a tab, the toolbar, the status line and the Project panel are all
                // there on a fresh start, which is when a tutor first says "look here"
                if (app == null) return ActionResult.error("'spotlight' is not enabled here");
                if (SpotlightTarget.hasJava(params)) return doJavaSpotlight(params);
                return onEdt(() -> doSpotlight(params));
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
                    Map<String, Object> result =
                            telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan.scan(sStore, params);
                    String caveat = timeOrderNote.get();
                    if (caveat != null) {
                        result = new LinkedHashMap<>(result);
                        result.put("timeOrderNote", caveat);   // D-R4: loud degradation, never silence
                    }
                    return ActionResult.ok("series", "result", result);
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
                // M33.3: the sections form builds/replaces a NAMED report (path optional — render or
                // CSV when given); the shipped single-record form stays below as sugar
                if (params.containsKey("sections") || params.containsKey("name")
                        || params.containsKey("csv")) {
                    String resolved = null;
                    if (params.get("path") != null) {
                        var out = guardedPath(params.get("path"));   // B1: same guard, same directory
                        if (!out.ok()) return ActionResult.error(out.error());
                        resolved = out.path().toString();
                    }
                    final String rp = resolved;
                    return onEdt(() -> app == null
                            ? ActionResult.error("'report' is not enabled here")
                            : app.report(params, rp));
                }
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

    /** Whether an open reply says its load is still running — under {@code log} (openLog) or at the top (openLogs). */
    private static boolean isLoading(ActionResult r) {
        if (r.payload() == null) return false;
        if (Boolean.TRUE.equals(r.payload().get("loading"))) return true;
        return r.toMap().get("log") instanceof Map<?, ?> lm && Boolean.TRUE.equals(lm.get("loading"));
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

        // M44.2 — the DECISION is the session processor's (CoverageClaim), not this method's. It used
        // to check one of the four ways coverage stops meaning anything: an INFERRED graph. The other
        // three — a processor that cannot log, a graph that does not describe this log, and a capture
        // level below TRACE — went unchecked here, and two of them had no home at all.
        // M44.4c: captured BEFORE the scan. The scan runs here, off the EDT; if another log or graph opens while it runs,
        // the comparison names the pair it was actually made against and the session refuses it as stale — it used to
        // qualify whatever pairing was published when the scan finished.
        var snap = sessionSnapshot == null ? null : sessionSnapshot.get();
        var claim = snap == null ? null : snap.claim();
        if (claim != null && !claim.allowed()) {
            return ActionResult.error(claim.reason() + ".");
        }

        // Take the graph facts on the EDT, then score the whole log off it. The same pure service feeds
        // report tables, so an exported denominator cannot diverge from this action's echo.
        boolean filtered = Boolean.TRUE.equals(p.get("filtered"));
        var coverageInput = onEdt(() -> new telamin.fluxtion.audit.analyser.analyser.topology.CoverageService.Input(
                topology.fullTopology(), topology.authoredNodeIds(), topology.sourceResolver()));
        var assessed = telamin.fluxtion.audit.analyser.analyser.topology.CoverageService.assess(
                s, filtered, filter.get(), coverageInput);
        Map<String, Object> out = new LinkedHashMap<>(assessed.echo());
        // M68.1 re-review R2 (acceptance 3): this comparison covers the whole scope against every declared
        // node; the pairing published on open covered a sample. The broader one qualifies the narrower one
        // wherever it is shown, and this reply says that it did.
        if (app != null) {
            // round 4, Q5b: a filtered comparison carries the identity of the filter it was made under
            Map<String, Object> forQualification = new LinkedHashMap<>(assessed.echo());
            if (snap != null) {
                forQualification.put(PAIR_LOG_GENERATION, snap.logGeneration());
                forQualification.put(PAIR_GRAPH_REVISION, snap.graphRevision());
            }
            if (filtered && filter.get() != null) {
                var snapshot = telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot.of(filter.get());
                forQualification.put("filterKey", snapshot.toString());
                forQualification.put("filterLabel", snapshot.describe());
            }
            String qualified = onEdt(() -> app.qualifyPublishedPairing(forQualification));
            if (qualified != null) out.put("qualifiedPublishedPairing", qualified);
        }
        // A QUALIFIED number is computable and must carry what it hides — refusing it would be as much
        // a failure as printing it bare.
        if (claim != null && claim.claim()
                != telamin.fluxtion.audit.analyser.analyser.session.CoveragePolicy.Claim.FULL) {
            out.put("claim", claim.claim().name().toLowerCase(java.util.Locale.ROOT));
            out.put("claimNote", claim.reason());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allNever = (List<Map<String, Object>>) out.get("neverLogged");
        Integer askedLimit = intOrNull(p.get("limit"));
        int limit = askedLimit == null ? 100 : Math.max(1, askedLimit);
        List<Map<String, Object>> never = allNever.stream().limit(limit).toList();
        out.put("neverLogged", never);
        if (allNever.size() > never.size()) out.put("neverLoggedTruncated", allNever.size() - never.size());
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
        // R13-4: the owner decision allows a NEW chart while definitions are withheld, and the UI does. The
        // verb refused everything, so the assistant was held to a stricter rule than the person beside it.
        // A chart that IS withheld is still refused — that is what the ambiguity protects.
        String refusal = onEdt(graphTabs::definitionRefusal);
        if (refusal != null) {
            String target = asText(p.get("name"));
            boolean withheld = target == null || onEdt(() -> graphTabs.isWithheldDefinition(target));
            if (withheld) {
                return ActionResult.error(target == null
                        ? refusal + " Name the new chart to create one while this is unresolved."
                        : refusal);
            }
        }
        String seriesRefusal = seriesShapeRefusal(p.get("series"));
        if (seriesRefusal != null) return ActionResult.error(seriesRefusal);
        // reveal what you changed: `topology` brings its tab forward, and a plot the caller cannot see is
        // indistinguishable from one that was never drawn
        if (app != null) app.showTab("Graph");
        // rename requires an explicit target {name, rename} — never selection-dependent
        if (p.containsKey("rename")) {
            String from = asText(p.get("name")), to = asText(p.get("rename"));
            if (from == null) return ActionResult.error("graph rename needs the target 'name'");
            // M68.4 (D-E3): a rename renames and does nothing else, so anything else in the call is refused with it
            // rather than dropped — it used to reply "renamed" and silently skip series, style and the rest
            List<String> alsoAsked = new java.util.ArrayList<>(p.keySet());
            alsoAsked.removeAll(List.of("name", "rename"));
            if (!alsoAsked.isEmpty()) {
                return ActionResult.error("a rename does only the rename — send " + alsoAsked
                        + " as a separate graph call; nothing was changed");
            }
            // M68.6 (D-E5, Q2 = refuse): a name no spotlight address can carry is refused where it is given
            String problem = SpotlightTarget.chartNameProblem(to);
            if (problem != null) return ActionResult.error("rename refused, nothing changed: " + problem);
            return onEdt(() -> {
                if (graphTabs.graphNamed(from) == null) return ActionResult.error("no open graph named '" + from + "'");
                if (to == null || to.isBlank()) return ActionResult.error("graph rename needs a non-blank new name");
                return graphTabs.renameNamed(from, to)
                        ? ActionResult.ok("graph", "applied", Map.of("renamed", from + " → " + to))
                        : ActionResult.error("a chart named '" + to.trim() + "' already exists, including saved closed charts");
            });
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
        Set<String> found = SeriesExtractor.resolveExisting(s, new java.util.LinkedHashSet<>(requested));
        Set<String> expressionFound = SeriesExtractor.resolveExisting(s, wanted, true);
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
            List<String> unseen = e.refs().stream().map(GraphKey::display).filter(d -> !expressionFound.contains(d)).toList();
            Map<String, Object> echo = new LinkedHashMap<>();
            echo.put("label", pe[0]);
            echo.put("ok", true);
            if (!unseen.isEmpty()) echo.put("unseenRefs", unseen);
            exprEcho.add(echo);
        }

        // M29.3: external CSV series — guard (D-F4), then LOAD here, off the EDT, so the echo can
        // carry the loader's honesty report and the panel apply is a plain EDT state change.
        List<telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.ExternalSpec> externalSpecs = null;
        java.util.Map<String, telamin.fluxtion.audit.analyser.analyser.graph.Series> externalLoaded = null;
        List<String> externalNotes = null;
        List<Map<String, Object>> externalEcho = null;
        List<String> externalWarnings = new ArrayList<>();
        if (p.containsKey("external")) {
            externalSpecs = new ArrayList<>();
            externalLoaded = new java.util.LinkedHashMap<>();
            externalNotes = new ArrayList<>();
            externalEcho = new ArrayList<>();
            java.util.Map<String, String> noteByLabel = new java.util.LinkedHashMap<>();   // one note per exact label, in first-seen order
            var cfg = exportConfig == null ? null : exportConfig.get();
            for (Object o : asList(p.get("external"))) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String path = asText(m.get("path"));
                String label = asText(m.get("label"));
                if (label == null || label.isBlank()) {
                    externalWarnings.add("external entry has no 'label' — skipped");
                    continue;
                }
                var resolved = telamin.fluxtion.audit.analyser.analyser.llm.ExportGuard.resolveRead(
                        path, cfg != null && cfg.assistantExports,
                        cfg == null ? "" : cfg.assistantExportDir, readGrants.get());
                if (resolved.error() != null) {
                    externalWarnings.add("external '" + label + "': " + resolved.error());
                    continue;
                }
                Long off = asLong(m.get("offsetMillis"));
                var spec = new telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.ExternalSpec(
                        resolved.path().toString(), label, asText(m.get("time")),
                        asText(m.get("timeFormat")), asText(m.get("zone")), asText(m.get("value")),
                        off == null ? 0 : off);
                try {
                    var r = telamin.fluxtion.audit.analyser.analyser.graph.ExternalCsvLoader.load(
                            resolved.path(), new telamin.fluxtion.audit.analyser.analyser.graph.ExternalCsvLoader.Spec(
                                    label, spec.time(), spec.timeFormat(), spec.zone(), spec.value(),
                                    spec.offsetMillis()));
                    Map<String, Object> e = new LinkedHashMap<>();
                    String note = label + ": " + r.rowsLoaded() + " rows";
                    int earlier = -1;
                    for (int i = 0; i < externalSpecs.size(); i++) if (label.equals(externalSpecs.get(i).label())) earlier = i;
                    if (earlier >= 0) {
                        // ledger review F2: external is replace-by-label, and one call carrying the same label twice
                        // drew TWO identical legend rows. The later entry applies — IN THE EARLIER ENTRY'S PLACE
                        // (main review 2026-09-18 F1: remove-and-append reordered the specs while the loaded map
                        // kept its slot, so with x, y, x the legend's swatches named the other line's colour).
                        externalSpecs.set(earlier, spec);
                        externalLoaded.put(label, r.series());                   // a re-put keeps the map's slot
                        for (int i = 0; i < externalEcho.size(); i++) if (label.equals(externalEcho.get(i).get("label"))) externalEcho.set(i, e);
                        noteByLabel.put(label, note);                             // keyed by the EXACT label (re-review F5:
                                                                                // "x: y" is not a note about "x")
                        externalWarnings.add("external label '" + label + "' given twice in one call — a label names ONE "
                                + "series, so the later entry replaced the earlier");
                    } else {
                        externalSpecs.add(spec);
                        externalLoaded.put(label, r.series());
                        externalEcho.add(e);
                        noteByLabel.put(label, note);
                    }
                    e.put("label", label);
                    e.put("rows", r.rowsLoaded());
                    if (r.rowsSkipped() > 0) e.put("skipped", r.rowsSkipped());
                    if (r.rowsReordered() > 0) e.put("reordered", r.rowsReordered());
                    if (r.fromMillis() != null) { e.put("from", r.fromMillis()); e.put("to", r.toMillis()); }
                    if (spec.offsetMillis() != 0) e.put("offsetMillis", spec.offsetMillis());
                    if (!r.diagnostics().isEmpty()) e.put("diagnostics", r.diagnostics());
                } catch (Exception ex) {
                    externalWarnings.add("external '" + label + "' failed to load: " + ex.getMessage());
                }
            }
            externalNotes.addAll(noteByLabel.values());
        }

        String name = asText(p.get("name"));
        String style = asText(p.get("style"));
        String rationale = asText(p.get("rationale"));   // provenance: why the agent built this graph
        boolean newTab = Boolean.TRUE.equals(p.get("newTab"));
        boolean refresh = Boolean.TRUE.equals(p.get("refresh"));   // M65 D-F4: the one word for "re-extract anyway"
        boolean pinRequested = p.containsKey("from") || p.containsKey("to");
        Long from = asLong(p.get("from"));
        Long to = asLong(p.get("to"));

        final var extSpecs = externalSpecs;
        final var extLoaded = externalLoaded;
        final var extNotes = externalNotes;
        final var extEcho = externalEcho;
        return onEdt(() -> {
            // M68.6 (D-E5, Q2 = refuse): refused BEFORE anything is created or changed. An EXISTING chart is reached by
            // its name as saved — that is the compatibility promise — so only a name this call would create is judged.
            if (name != null && (newTab || graphTabs.graphNamed(name) == null)) {
                String problem = SpotlightTarget.chartNameProblem(name);
                if (problem != null) return ActionResult.error("graph refused, nothing created: " + problem);
            }
            GraphPanel panel = graphTabs.graphForAction(name, newTab);
            if (panel == null && graphTabs.definitionRefusal() != null)
                return ActionResult.error(graphTabs.definitionRefusal());
            if (panel == null) return ActionResult.error(graphTabs.hasDefinition(name)
                    ? "a chart named '" + name + "' already exists; omit newTab to edit or reopen it"
                    : "could not open a graph (no log loaded)");
            int requestsBefore = panel.extractionRequests();
            if (extSpecs != null) panel.setExternalPreloaded(extSpecs, extLoaded, extNotes);   // REPLACE
            panel.addKeys(toAdd);
            for (Object[] pe : parsedExprs) panel.addExpr((String) pe[0], (String) pe[1], (SeriesExtractor.Resolve) pe[2]);
            if (style != null) panel.setStyleByName(style);
            if (rationale != null) panel.setCaption(rationale);   // caption the plot with the agent's reason
            applyNotesAndAxes(panel, p, s);
            List<String> annotationIssues = applyGuidesAndBands(panel, p);
            if (pinRequested) panel.pin(from, to);   // explicit range → pin (evidence artifact); else follows
            if (refresh) panel.onRecordsAppended();
            Map<String, Object> applied = new LinkedHashMap<>();
            // "scheduled", not true: the walk lands after this call returns (debounce + off-EDT); `series` is
            // the way to READ a fresh value, the chart is what lags. false = nothing in this call re-extracts.
            applied.put("refreshed", panel.extractionRequests() > requestsBefore ? "scheduled" : Boolean.FALSE);
            applied.put("scope", panel.scopeFacts());
            if (p.containsKey("guides")) applied.put("guides", panel.guides().size());
            if (p.containsKey("markers")) {
                applied.put("markers", panel.markerSpecs().size());
                applied.put("markerResolution", panel.markerSpecs().stream().map(m -> Map.of("label", m.label(), "resolve", m.isExternal() ? "external" : m.resolve())).toList());
                // extraction is async in the reExtract pipeline; notes surface on the panel and in the
                // NEXT call's echo — the counts here confirm what was ACCEPTED
            }
            if (p.containsKey("bands")) applied.put("bands", panel.bandSpecs().size());
            if (extEcho != null) applied.put("external", extEcho);
            applied.put("name", name == null ? "(current)" : name);
            // the style the chart now HAS, so a caller can confirm a {style} it sent (or learn the one it did not)
            applied.put("style", panel.styleName());
            applied.put("resolved", requested.stream().filter(found::contains).toList());
            applied.put("unresolved", unresolved);
            if (!exprEcho.isEmpty()) applied.put("exprs", exprEcho);
            if (!unresolved.isEmpty() || exprEcho.stream().anyMatch(e -> e.containsKey("unseenRefs"))) {
                applied.put("availableKeysSample", availableSample.stream().limit(40).toList());
                applied.put("note", "graphable keys are TOP-LEVEL numeric/boolean nodeLog values "
                        + "(instanceId.key); a value nested inside a toString (e.g. MutableOrder(price=..)) "
                        + "is not itself a key. A well-formed but unresolved key is still added (it plots if the "
                        + "key ever fires); one without an instanceId.key dot is not added.");
            }
            if (panel.isPinned()) {
                Map<String, Object> pinned = new LinkedHashMap<>();
                pinned.put("from", panel.pinnedFrom());
                pinned.put("to", panel.pinnedTo());
                applied.put("pinned", pinned);
            }
            List<String> warnings = new ArrayList<>(externalWarnings);
            warnings.addAll(annotationIssues);
            warnings.addAll(annotationWarnings(panel, p));
            if (!warnings.isEmpty()) applied.put("warnings", warnings);
            return ActionResult.ok("graph", "applied", applied);
        });
    }

    /**
     * Echo hardening (M26.4): {@code rightAxis} or a note's {@code series} naming a series that is not on
     * the graph — after this call's additions — is applied anyway (the series may arrive next call) but
     * WARNED about, because before this it was a silent no-op only visible by inspecting the plot.
     */
    private static List<String> annotationWarnings(GraphPanel panel, Map<String, Object> p) {
        List<String> warnings = new ArrayList<>();
        Set<String> onGraph = panel.plottedLabels();
        for (String ax : strList(p.get("rightAxis"))) {
            if (!onGraph.contains(ax)) {
                warnings.add("rightAxis names '" + ax + "' but no series with that label is on this "
                        + "graph — it takes effect only when/if that series is added");
            }
        }
        if (p.get("notes") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                String series = str(m.get("series"));
                if (series != null && !onGraph.contains(series)) {
                    warnings.add("a note names series '" + series + "' but no series with that label is "
                            + "on this graph — the pin shows unattributed");
                }
            }
        }
        return warnings;
    }

    private static Map<String, Object> errEcho(String label, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("error", error);
        return m;
    }

    // ---- goto ------------------------------------------------------------------------------------

    private ActionResult doGoto(LogStore s, Map<String, Object> p) {
        // M68.4 (set 10, P53): clampRow on an EMPTY log gives max(0, -1) = 0, so goto replied ok for record 0 of a log
        // that has no records. Found by the refused-call spotlight test, which expected this call to be refused.
        if (s.index().size() == 0) return ActionResult.error("this log has no records to go to");
        int row = targetRow(s.index(), p, "byteOffset", "recordIndex");
        if (row == -3) return ActionResult.error("this source has no byte anchors — anchor by "
                + "recordIndex or at instead");
        if (row == -2) return ActionResult.error("this log is a rolled set — a byteOffset needs 'file' "
                + "(one of " + s.index().files() + "), or anchor by recordIndex/at");
        if (row < 0) return ActionResult.error(p.get("at") instanceof Number
                ? "'at' cannot resolve — no record carries a log time"
                : "goto needs a byteOffset, recordIndex or at (epoch millis)");
        boolean reveal = Boolean.TRUE.equals(p.get("reveal"));
        // M68.4 (D-E3): with several anchors, byteOffset beats recordIndex beats at — and the losers are now NAMED
        List<String> anchors = new java.util.ArrayList<>();
        for (String k : List.of("byteOffset", "recordIndex", "at")) if (p.get(k) != null) anchors.add(k);
        List<String> unusedAnchors = anchors.size() > 1 ? anchors.subList(1, anchors.size()) : List.of();
        return onEdt(() -> {
            FilterState f = filter.get();
            boolean visible = tablePanel.selectModelRow(row);
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("recordIndex", row);
            // M68.4 (D-E3): goto keeps its documented clamp — it only moves the view, and the echo says where — but
            // a clamped index is now NAMED rather than left for the caller to spot in a different number
            if (p.get("recordIndex") instanceof Number asked && anchors.get(0).equals("recordIndex")
                    && asked.longValue() != row) {
                applied.put("clamped", Map.of("asked", asked, "used", row));
            }
            if (!unusedAnchors.isEmpty()) {
                applied.put("ignored", unusedAnchors);
                applied.put("ignoredWhy", "one anchor chooses the record; " + anchors.get(0) + " was used");
            }
            applied.put("byteOffset", s.index().offset(row));
            if (s.index().fileCount() > 1) applied.put("file", s.index().files().get(s.index().fileId(row)));
            if (p.get("at") instanceof Number && timeOrderNote.get() != null) {
                applied.put("timeOrderNote", timeOrderNote.get());   // D-R4: 'at' may be approximate here
            }
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
    static void revealRecord(FilterState f, LogStore s, int row) {
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
        if (idx.fileCount() > 1 && !asList(p.get("byteOffsets")).isEmpty()) {
            return ActionResult.error("this log is a rolled set — offsets are file-local; flag by "
                    + "recordIndexes instead");
        }
        // M68.4 (D-E3): a flag attaches a FINDING to a record, so it never lands on a record nobody named. An index or
        // offset outside the log used to be clamped to the first or last record, and an entry that was not a number
        // was skipped: `flag {recordIndexes: [1000]}` on a ten-record log flagged record 9. Now the call is refused,
        // naming them, and nothing is flagged — the rule spotlight already applies to rows (SpotlightSetTest).
        List<Object> notInThisLog = new ArrayList<>();
        long lastByte = idx.size() == 0 ? -1 : idx.offset(idx.size() - 1) + idx.length(idx.size() - 1);
        for (Object o : asList(p.get("byteOffsets"))) {
            Long off = asLong(o);
            if (off == null || off < 0 || off >= lastByte) notInThisLog.add(o);
            else rows.add(floorRow(idx, off));
        }
        for (Object o : asList(p.get("recordIndexes"))) {
            Long ri = asLong(o);
            if (ri == null || ri < 0 || ri >= idx.size()) notInThisLog.add(o);
            else rows.add(ri.intValue());
        }
        if (!notInThisLog.isEmpty()) {
            return ActionResult.error("not in this log: " + notInThisLog + " (it has " + idx.size()
                    + " records, numbered 0 to " + (idx.size() - 1) + "); nothing was flagged");
        }
        if (rows.isEmpty()) return ActionResult.error("flag needs byteOffsets[] or recordIndexes[]");
        String note = asText(p.get("note"));
        String fix = asText(p.get("fix"));
        String kind = asText(p.get("kind"));
        if (p.containsKey("kind") && !Set.of("fault", "confirmation").contains(kind == null ? "" : kind))
            return ActionResult.error("flag kind must be fault or confirmation");
        int[] rowArr = rows.stream().mapToInt(Integer::intValue).toArray();
        return onEdt(() -> {
            flagRows.flag(rowArr, note, fix, kind);
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("flagged", rowArr.length);
            applied.put("recordIndexes", rows.stream().sorted().toList());
            if (note != null) applied.put("note", note);
            if (fix != null) applied.put("fix", fix);
            if (kind != null) applied.put("kind", kind);
            return ActionResult.ok("flag", "applied", applied);
        });
    }

    // ---- offset resolution (pure; unit-tested by GotoResolveTest) --------------------------------

    /**
     * Pick a target model row from {@code byteOffset} (floor), {@code recordIndex} (clamp) or {@code at}
     * (epoch millis, at-or-before — M26.2 time anchors); -1 if none given or {@code at} can't resolve
     * (no record carries a log time).
     */
    static int targetRow(LogIndex idx, Map<String, Object> p, String offsetKey, String indexKey) {
        if (p.get(offsetKey) instanceof Number n) {
            if (!idx.byteAnchors()) return -3;   // no byte anchors in this container (M31 D-P4)
            if (idx.fileCount() > 1) {
                Integer fid = fileIdOf(p.get("file"), idx);
                if (fid == null) return -2;   // ambiguous: rolled set needs 'file' — caller reports it
                return floorRowInFile(idx, n.longValue(), fid);
            }
            return floorRow(idx, n.longValue());
        }
        if (p.get(indexKey) instanceof Number n) return clampRow(idx, n.intValue());
        if (p.get("at") instanceof Number n) {
            return telamin.fluxtion.audit.analyser.analyser.llm.ReadService
                    .rowAtOrBefore(idx.size(), idx::logTime, n.longValue());
        }
        return -1;
    }

    /** {@code 'file'} (member name or id) → file id, or null. Rolled sets have file-local offsets (D-R2). */
    static Integer fileIdOf(Object file, LogIndex idx) {
        if (file instanceof Number n) {
            int id = n.intValue();
            return id >= 0 && id < idx.fileCount() ? id : null;
        }
        if (file != null) {
            java.util.List<String> names = idx.files();
            for (int i = 0; i < names.size(); i++) if (names.get(i).equals(file.toString())) return i;
        }
        return null;
    }

    /** Floor-resolve within ONE member file of a rolled set (offsets are file-local — D-R2). */
    static int floorRowInFile(LogIndex idx, long byteOffset, int fid) {
        int ans = -1;
        for (int i = 0; i < idx.size(); i++) {
            if (idx.fileId(i) != fid) continue;
            if (idx.offset(i) <= byteOffset) ans = i;
            else if (ans >= 0) break;
        }
        return ans;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
    }

    /**
     * M44.2 — which act a combined 'open' means, and what it therefore did not honour, is decided by
     * the session processor ({@code IgnoredParameters}). This asks it.
     *
     * <p>The precedence order and the sentence explaining each one used to live in three
     * near-identical blocks below, one per act. Three copies of a precedence order is three places
     * for it to drift, and the sentence is the part a caller actually reads.
     *
     * <p><b>Unwired, it still decides — it just is not audited.</b> The first version returned null
     * here and let the caller report nothing, which a test caught immediately: for coverage a missing
     * opinion must not become a refusal, but here the harmful direction is the opposite. A parameter
     * silently dropped reads to the caller exactly like one that was honoured, so falling back to
     * silence would have reintroduced the defect this rule exists to prevent. The fallback is the same
     * pure decision without the audit record, never a different answer.
     */
    private telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters.Decision
            openDecision(Map<String, Object> params) {
        java.util.Set<String> supplied = new java.util.LinkedHashSet<>();
        params.forEach((k, v) -> { if (v != null) supplied.add(k); });
        return ignoredParameters == null
                ? new telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters()
                        .apply(supplied)
                : ignoredParameters.apply(supplied);
    }

    private java.util.function.Function<java.util.Set<String>,
            telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters.Decision> ignoredParameters;

    public void bindIgnoredParameters(java.util.function.Function<java.util.Set<String>,
            telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters.Decision> f) {
        this.ignoredParameters = f;
    }

    /**
     * M38.4 — recall a saved analysis. NOT under onEdt(): the run waits for each asynchronous open to settle,
     * and a wait on the EDT blocks the completion it waits for (the first live run timed out that way while
     * the log sat loaded). Each step hops to the EDT on its own through render().
     */
    /**
     * M64 — light the spotlight. A row target is REVEALED through the {@code goto} path first (D-SP2:
     * "a target that is not on screen is first revealed with the same moves the existing verbs use"), so a
     * filtered-out record is brought into the table the way {@code goto {reveal: true}} brings it, and is
     * selected — which also puts it in the record detail for a following {@code detail:node} spotlight.
     * It calls {@link #doGoto} directly rather than {@code render("goto")}: the public verb would put out
     * the very spotlight this is about to light.
     */
    /** Cancellation is request-scoped, including interruption before the EDT has captured anything. */
    private ActionResult doJavaSpotlight(Map<String, Object> params) {
        if (SwingUtilities.isEventDispatchThread())
            return ActionResult.error("Java source spotlight requires asynchronous preparation; invoke off the EDT");
        var answer = new java.util.concurrent.CompletableFuture<ActionResult>();
        SwingUtilities.invokeLater(() -> {
            if (answer.isDone()) return;
            try {
                var pending = app.prepareJavaSpotlight(params, () -> revealSpotlightRows(params));
                answer.whenComplete((value, failure) -> { if (answer.isCancelled()) pending.cancel(false); });
                pending.whenComplete((value, failure) -> {
                    if (failure == null) answer.complete(value); else answer.completeExceptionally(failure);
                });
            } catch (RuntimeException ex) { answer.completeExceptionally(ex); }
        });
        try { return answer.get(); }
        catch (InterruptedException ex) {
            answer.cancel(false);
            Thread.currentThread().interrupt();
            return ActionResult.error("Java source spotlight interrupted; preparation cancelled");
        } catch (java.util.concurrent.ExecutionException ex) {
            return ActionResult.error("Java source spotlight failed: " + ex.getCause().getMessage());
        }
    }

    private ActionResult doSpotlight(Map<String, Object> params) {
        LogStore s = store.get();
        if (Boolean.TRUE.equals(params.get("clear"))) return app.spotlight(params);
        SpotlightTarget.Requests asked = SpotlightTarget.requests(params);
        // Judge the WHOLE call before the first reveal (review of M64.6, F2). goto's reveal relaxes the filter and
        // changes the selection, so a call refused for a misspelt member — or for being a seventh — must be
        // refused HERE, not after a row in the same call has already erased the person's investigation scope.
        // …and a row this log does not have (re-review R5): goto CLAMPS an index, so an out-of-range row used to
        // relax the filter and select the LAST record before the spotlight refused it.
        String wrong = SpotlightTarget.precheck(asked, app.spotlightLit(), s == null ? -1 : s.index().size());
        if (wrong != null) return ActionResult.error(wrong);
        revealSpotlightRows(params);
        return app.spotlight(params);
    }

    private void revealSpotlightRows(Map<String, Object> params) {
        LogStore s = store.get();
        var asked = SpotlightTarget.requests(params);
        if (s != null) {
            for (SpotlightTarget.Request one : asked.requests()) {       // several rows: each revealed, the last left selected
                SpotlightTarget.Parsed parsed = SpotlightTarget.parse(one.target());
                if (!parsed.ok() || parsed.target().family() != SpotlightTarget.Family.RECORDS_ROW) continue;
                Map<String, Object> reveal = new LinkedHashMap<>();
                reveal.put("recordIndex", parsed.target().number());
                reveal.put("reveal", true);
                doGoto(s, reveal);
            }
        }
    }

    private ActionResult doOpenAnalysis(Map<String, Object> params) {
        if (app == null) return ActionResult.error("'open' is not enabled here");
        // M38.4: recall a saved analysis — its steps run through render(), so each keeps its own guards.
        // On `open` because recalling is a lifecycle act like the others here, and the verb surface is a
        // compatibility surface that does not grow for a concept an existing verb already names.
        Map<String, String> bind = new LinkedHashMap<>();
        if (params.get("bind") instanceof Map<?, ?> m) m.forEach((k, v) -> { if (v != null) bind.put(String.valueOf(k), String.valueOf(v)); });
        ActionResult r = app.runAnalysis(str(params.get("analysis")), bind);
        // This method is deliberately OFF the EDT (see above), and the decision is an event submitted to
        // the session processor, whose driver is confined to the EDT (M44.3 D-A1). Deciding here on the
        // caller's thread made every `open {analysis}` run its steps and then FAIL with a protocol
        // violation — released in 1.13.x, found when the conversation harness was next re-run.
        var decision = onEdt(() -> openDecision(params));
        if (r.ok() && decision != null && decision.anythingIgnored()) {
            Map<String, Object> echo = new LinkedHashMap<>(asMap(r.toMap().get("analysis")));
            echo.put("ignored", decision.ignored());
            echo.put("ignoredWhy", decision.why());
            return ActionResult.ok("open", "analysis", echo);
        }
        return r;
    }

    // ---- M48.7: the shared canvas, on the verb that already means "put this in force" ------------------

    /** The two things `open` puts on the canvas, and the one way it takes them off. */
    private static final Set<String> CANVAS_PARAMS = Set.of("posture", "record");

    private static boolean isCanvasWrite(Map<String, Object> params) {
        return params.get("posture") != null || params.get("record") != null
                || "handoff".equalsIgnoreCase(str(params.get("close")));
    }

    /**
     * {@code open {posture}}, {@code open {record}} and {@code open {close: "handoff"}} — the session's
     * posture and the authoring mode selector's record (M48.7). This was its own verb, {@code handoff}, for
     * a day; it was folded in before it shipped (second reader, A6) because {@code open} already means "put
     * this in force" and already has the close idiom {@code open {close: "project"}} — one verb, one way
     * to undo, fifteen tools instead of sixteen. Everything of substance is unchanged and lives in
     * {@link telamin.fluxtion.audit.analyser.analyser.llm.CanvasHandoff}.
     *
     * <p><b>A canvas write goes alone.</b> The rest of {@code open} names what it ignored; a write to shared
     * state is refused instead, whole, because "half of what you asked was applied" is the one answer a
     * typed, fail-closed canvas may not give.
     */
    private ActionResult doOpenCanvas(Map<String, Object> params) {
        if (app == null) return ActionResult.error("'open' is not enabled here");
        boolean closing = "handoff".equalsIgnoreCase(str(params.get("close")));
        List<String> others = new ArrayList<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() == null) continue;
            boolean mine = closing ? e.getKey().equals("close") : CANVAS_PARAMS.contains(e.getKey());
            if (!mine) others.add(e.getKey());
        }
        if (!others.isEmpty()) {
            return ActionResult.error((closing ? "open {close: \"handoff\"}" : "a canvas write (posture / record)")
                    + " goes ALONE — this call also carried " + others + ". Nothing was applied: send "
                    + (closing && (params.get("posture") != null || params.get("record") != null)
                    ? "the close and the write separately — which half of \"set it and remove it\" was meant is not "
                    + "something to guess" : "them as separate calls"));
        }
        Map<String, Object> write = new LinkedHashMap<>();
        if (closing) {
            write.put("clear", "all");
        } else {
            if (params.get("posture") != null) write.put("posture", params.get("posture"));
            if (params.get("record") != null) write.put("record", params.get("record"));
        }
        ActionResult r = app.handoff(write);
        if (!r.ok() || !closing) return r;
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("closed", "handoff");
        echo.put("handoff", r.toMap().get("handoff"));
        return ActionResult.ok("open", "applied", echo);
    }

    private ActionResult doOpen(Map<String, Object> params) {
        if (app == null) return ActionResult.error("'open' is not enabled here");
        if (params.containsKey("restore")) {
            if (params.size() != 1 || !("last".equals(params.get("restore")) || "dismiss".equals(params.get("restore"))))
                return ActionResult.error("use open {restore: last|dismiss} alone to accept or decline the current session offer");
            return "dismiss".equals(params.get("restore")) ? onEdt(app::dismissSessionRestore) : onEdt(app::restoreSession);
        }
        if (params.get("project") != null) {
            // M35.8: the largest act on this verb goes first. A project switch is a session boundary
            // (M35.5) — the log and graph close with it — so "open a project and a log" in one call
            // has no coherent reading: whatever the log arrived into would be swept away by the switch.
            // Sequence the calls instead; the ignored params are NAMED (M26.4, review R2).
            ActionResult r = onEdt(() -> app.openProject(str(params.get("project"))));
            var decision = onEdt(() -> openDecision(params));
            if (r.ok() && decision != null && decision.anythingIgnored()) {
                Map<String, Object> echo = new LinkedHashMap<>(asMap(r.toMap().get("opened")));
                echo.put("ignored", decision.ignored());
                echo.put("ignoredWhy", decision.why());
                return ActionResult.ok("open", "opened", echo);
            }
            return r;
        }
        // M68.4 (D-E3): what ELSE this call opens alongside a log. A log with an explicit format, and a rolled set, used
        // to return early here, and a graphml, processor, design or diagnostics in the same call was dropped without a
        // word (DX-03). They now go through the same sequence as log + graphml below, so both halves are honoured.
        boolean alsoOpens = params.get("graphml") != null || params.get("processor") != null
                || params.get("design") != null || params.get("diagnostics") != null;
        if (params.get("log") != null && params.get("format") != null && !alsoOpens) {
            // §E + M35.9: the declaration travels WITH the open — one call, nothing set beforehand
            return onEdt(() -> app.openLog(str(params.get("log")), str(params.get("format")), str(params.get("provenance"))));
        }
        if (params.get("discover") != null) {
            ActionResult found = "diagnostics".equals(str(params.get("discover")))
                    ? onEdt(() -> app.discoverDiagnostics())
                    : onEdt(() -> app.discoverGraphs());   // lists, never opens — M35.4
            // M68.4 (D-E3): discover lists and opens nothing, so anything else this call asked to open is NAMED as not
            // done — it used to be dropped silently, the same defect the project and close branches already avoid
            List<String> notDone = new ArrayList<>();
            for (String k : List.of("log", "logs", "graphml", "processor", "design", "diagnostics")) {
                if (params.get(k) != null) notDone.add(k);
            }
            if (!found.ok() || notDone.isEmpty() || found.payload() == null) return found;
            Map<String, Object> echo = new LinkedHashMap<>(found.payload());
            echo.put("ignored", notDone);
            echo.put("ignoredWhy", "discover lists candidates and opens nothing; send the open as its own call");
            return ActionResult.ok(found.action(), found.payloadKey(), echo);
        }
        if (params.get("close") != null) {
            // the counterpart of open, on the same verb: closing is a lifecycle act, not a new concept
            ActionResult r = onEdt(() -> app.close(str(params.get("close"))));
            // review R2 / M26.4: "open and close at once" is incoherent and the useful reading is the
            // close — but a param that was silently dropped reads to the caller as one that was
            // honoured, so name them. Every verb in this surface owes the caller that.
            var decision = onEdt(() -> openDecision(params));
            if (r.ok() && decision != null && decision.anythingIgnored()) {
                Map<String, Object> echo = new LinkedHashMap<>(asMap(r.toMap().get("applied")));
                echo.put("ignored", decision.ignored());
                echo.put("ignoredWhy", decision.why());
                return ActionResult.ok("open", "applied", echo);
            }
            return r;
        }
        List<String> rolledSet = null;
        if (params.get("logs") instanceof List<?> list && !list.isEmpty()) {
            List<String> paths = new ArrayList<>();
            for (Object o : list) if (o != null) paths.add(o.toString());
            if (!alsoOpens) {
                return onEdt(() -> app.openLogs(paths, str(params.get("provenance"))));   // M30: an explicit set — content orders it
            }
            rolledSet = paths;                           // M68.4: opened first, below, then the rest of this call
        }
        String log = str(params.get("log"));
        String graphml = str(params.get("graphml"));
        String processor = str(params.get("processor"));
        String design = str(params.get("design"));
        String diagnostics = str(params.get("diagnostics"));
        String format = str(params.get("format"));
        if (log == null && rolledSet == null && graphml == null && processor == null && design == null && diagnostics == null) {
            return ActionResult.error(
                    "'open' needs 'design', 'diagnostics', 'log', 'graphml', 'processor', 'project', 'analysis', 'posture', 'record', "
                            + "'close' or 'discover'");
        }
        Map<String, Object> echo = new java.util.LinkedHashMap<>();
        boolean logLoading = false;
        if (rolledSet != null) {
            // M68.4 (acceptance 4): the rolled set opens first, exactly as on its own; the graph below then lands
            // mid-load, and the session keeps a graph opened FOR the arriving log (LogArrival, M68.4)
            final List<String> set = rolledSet;
            ActionResult r = onEdt(() -> app.openLogs(set, str(params.get("provenance"))));
            if (!r.ok()) return r;
            echo.put("logs", set);
            logLoading = isLoading(r);
            if (logLoading) echo.put("logLoading", true);
        } else if (log != null) {
            // §E + M35.9: provenance rides the same call as the path — and, since M68.4, so does an explicit format
            ActionResult r = onEdt(() -> app.openLog(log, format, str(params.get("provenance"))));
            if (!r.ok()) return r;
            echo.put("log", log);
            // the frame loads a log in the background and says so; anything judged later in THIS call
            // was judged before that log existed
            logLoading = isLoading(r);
            if (logLoading) echo.put("logLoading", true);
        }
        if (graphml != null) {
            ActionResult r = onEdt(() -> app.openGraphml(graphml));
            if (!r.ok() && (echo.containsKey("log") || echo.containsKey("logs"))) {
                // M68.4 (D-E3, "an early success followed by a later failure"): the log half has already started, and a
                // bare error would read as "nothing happened". Say which half did.
                return ActionResult.error(r.toMap().get("error") + " — the log in this call "
                        + (logLoading ? "is loading" : "was opened") + " and stays open; only the graphml was refused");
            }
            if (!r.ok()) return r;
            // carry the inner echo up rather than replacing it with the path we already knew:
            // openGraphml answers "does this graph fit the open log?" (M35.3) and that verdict is
            // the useful half — an agent switching processors must not have to call context to get it
            Object payload = r.toMap().get("graphml");
            if (payload instanceof Map<?, ?> m && !m.isEmpty()) {
                Map<String, Object> g = new java.util.LinkedHashMap<>(asMap(payload));
                if (logLoading) {
                    // 2026-09-16 session report: `open {log, graphml}` echoed "no log is open" on a
                    // first open and the PREVIOUS log's node counts on a re-open, while context said
                    // otherwise a moment later. The verdict was real, but about the wrong log. Replace
                    // it rather than pass it on; the frame re-judges when the load lands.
                    g.keySet().removeAll(PAIRING_KEYS);
                    g.put("pairing", PAIRING_PENDING);
                }
                echo.put("graphml", g);
            } else {
                echo.put("graphml", graphml);
            }
        }
        if (processor != null) {
            ActionResult r = onEdt(() -> app.selectProcessor(processor));
            if (!r.ok()) return r;
            echo.put("processor", processor);
        }
        if (design != null) {
            ActionResult r = app.openDesign(design);
            if (!r.ok()) return r;
            echo.put("design", r.payload());
        }
        if (diagnostics != null) {
            ActionResult r = app.openDiagnostics(diagnostics);
            if (!r.ok()) return r;
            echo.put("diagnostics", r.payload());
        }
        return ActionResult.ok("open", "opened", echo);
    }

    /** The pairing echo when the log it would be judged against has not finished loading. */
    public static final String PAIRING_PENDING = "pending — the log is still loading; the graph is judged "
            + "against it when the load lands. Read context.graphPairing for the verdict.";

    /** The keys openGraphml's echo uses for a verdict; removed when that verdict was about another log. */
    private static final java.util.List<String> PAIRING_KEYS = java.util.List.of(
            "pairing", "appliesToOpenLog", "loggedNodes", "declaredByGraph", "verdict");

    private ActionResult doSourceRoot(Map<String, Object> params) {
        if (app == null) return ActionResult.error("'source_root' is not enabled here");
        List<String> added = new java.util.ArrayList<>();
        List<String> rejected = new java.util.ArrayList<>();
        for (String path : strList(params.get("add"))) {
            if (app.addSourceRoot(path)) added.add(path);
            else rejected.add(path);
        }
        List<String> removed = new java.util.ArrayList<>();
        List<String> notRemoved = new java.util.ArrayList<>();
        for (String path : strList(params.get("remove"))) {
            if (app.removeSourceRoot(path)) removed.add(path);
            else notRemoved.add(path);          // M68.4 (D-E3): it used to be left out without a word
        }
        Map<String, Object> echo = new java.util.LinkedHashMap<>();
        echo.put("roots", app.sourceRoots());
        if (!added.isEmpty()) echo.put("added", added);
        if (!removed.isEmpty()) echo.put("removed", removed);
        // a path that is not a source root is reported rather than silently ignored: the caller would
        // otherwise go on to wonder why source navigation still finds nothing
        if (!rejected.isEmpty()) echo.put("notASourceRoot", rejected);
        if (!notRemoved.isEmpty()) echo.put("notRemoved", notRemoved);
        return ActionResult.ok("source_root", "sourceRoots", echo);
    }

    private ActionResult doTopology(Map<String, Object> params) {
        if (topology == null) return ActionResult.error("'topology' is not enabled here");
        if (!topology.hasTopology()) {
            return ActionResult.error("no topology is loaded — use 'open' with a graphml first");
        }
        // M68.4 (D-E3): VALIDATE EVERY FIELD FIRST, then apply. Each check below used to run after the fields before it
        // had been applied, so a bad scope, an unknown node, a missing focus or a bad routeBound was refused after
        // scaffolding, sync and the selection had already changed — a refusal that had half-happened. The tab switch
        // waits too: a refused call leaves the view where it was.
        String problem = topologyProblem(params);
        if (problem != null) return ActionResult.error(problem + " — nothing was changed");
        if (app != null) app.showTab("Topology");

        if (params.containsKey("scaffolding")) topology.setScaffoldingVisible(bool(params.get("scaffolding")));
        if (params.containsKey("showAll") && bool(params.get("showAll"))) topology.showAll();

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
        // review P1: the routes hop bound was reachable only from a Swing checkbox, so an agent was
        // told in the echo that the unbounded answer was "one untick away" and had no way to untick it.
        // Read BEFORE scope, so a single call can set both and get the answer it asked for.
        Object routeBound = params.get("routeBound");
        if (routeBound instanceof Boolean b) {
            topology.setRouteBound(b);
        } else if (routeBound != null) {
            return ActionResult.error("routeBound must be true or false, got '" + routeBound + "'");
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
        if (record != null) {
            // M68.4 (D-E4, acceptance 5): the record parameter ESTABLISHES the state it needs. With no record bound the
            // step cursor ignored it, and the reply stated the cursor's default of 0 whatever was asked (DX-04). A
            // record is bound the way goto binds one — by selecting its row — and the cursor then moves to it.
            if (!topology.hasBoundRecord()) tablePanel.selectModelRow(record);
            topology.moveToRecord(record);
        }
        Integer step = intOrNull(params.get("step"));
        if (step != null && step != 0) topology.step(step);
        if (params.containsKey("fit") && bool(params.get("fit"))) topology.fit();

        java.util.Map<String, Object> echo = topology.cursorState();
        if (record != null && !Integer.valueOf(record).equals(echo.get("recordIndex"))) {
            // D-E4: the echo and the visible result agree, or the call says it did not do what it was asked
            return ActionResult.error("recordIndex " + record + " could not be shown in the topology (the cursor is at "
                    + echo.get("recordIndex") + ")");
        }
        if (!topology.lastRecallNote().isEmpty()) echo.put("recallNote", topology.lastRecallNote());
        return ActionResult.ok("topology", "topology", echo);
    }

    /** M68.4 (D-E3): why this topology call must be refused whole, or null — checked before anything is applied. */
    private String topologyProblem(Map<String, Object> params) {
        if (params.containsKey("select")) {
            String id = str(params.get("select"));
            if (id != null && !topology.hasNode(id)) return "no node '" + id + "' in this topology";
        }
        Object routeBound = params.get("routeBound");
        if (routeBound != null && !(routeBound instanceof Boolean)) {
            return "routeBound must be true or false, got '" + routeBound + "'";
        }
        String scope = str(params.get("scope"));
        if (scope != null) {
            try {
                telamin.fluxtion.audit.analyser.analyser.topology.TopologyFocus.Scope.valueOf(scope.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return "unknown scope '" + scope + "'";
            }
        }
        if (params.get("focus") instanceof String namedFocus) {
            String why = topology.recallFocusProblem(namedFocus);
            if (why != null) return why;
        }
        String orientation = str(params.get("orientation"));
        if (orientation != null && !orientation.equalsIgnoreCase("left_right") && !orientation.equalsIgnoreCase("top_down")) {
            // it used to become top-down silently: a declared parameter that did something other than it said
            return "orientation must be 'left_right' or 'top_down', got '" + orientation + "'";
        }
        if (params.containsKey("recordIndex")) {
            Integer record = intOrNull(params.get("recordIndex"));
            LogStore s = store.get();
            if (record == null) return "recordIndex must be a number";
            if (s == null) return "recordIndex needs an open log — nothing is loaded";
            if (record < 0 || record >= s.size()) {
                return "recordIndex " + record + " is not a record of this log (it has " + s.size() + ", numbered 0 to "
                        + (s.size() - 1) + ")";
            }
            if (!tablePanel.isModelRowVisible(record)) {
                return "record " + record + " is hidden by the current filter — goto {recordIndex: " + record
                        + ", reveal: true} first, or widen the filter";
            }
        }
        return null;
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

    /**
     * Apply {@code guides} / {@code bands} (M28.5/.6) — present = REPLACE the set, like rightAxis.
     * Malformed items are skipped and NAMED in the returned warnings (never silently dropped); a band
     * whose expression does not parse is rejected here, at set time, where the caller can still react.
     */
    private List<String> applyGuidesAndBands(GraphPanel panel, Map<String, Object> p) {
        var cfg = exportConfig == null ? null : exportConfig.get();
        List<String> warnings = new ArrayList<>();
        if (p.containsKey("guides")) {
            List<telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.GuideSpec> guides = new ArrayList<>();
            for (Object o : asList(p.get("guides"))) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object v = m.get("value");
                if (v instanceof Number n) {
                    guides.add(new telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.GuideSpec(
                            n.doubleValue(), asText(m.get("label")), bool(m.get("rightAxis"))));
                } else {
                    warnings.add("guide " + (m.get("label") != null ? "'" + m.get("label") + "' " : "")
                            + "has no numeric 'value' — skipped");
                }
            }
            panel.setGuides(guides);
        }
        if (p.containsKey("markers")) {
            List<telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.MarkerSpec> specs = new ArrayList<>();
            for (Object o : asList(p.get("markers"))) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String label = asText(m.get("label"));
                String glyph = asText(m.get("glyph"));
                if (glyph != null && !telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries.GLYPHS.contains(glyph)) {
                    warnings.add("marker '" + label + "': unknown glyph '" + glyph + "' — using circle (one of "
                            + telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries.GLYPHS + ")");
                    glyph = "circle";
                }
                // M32.8: an external CSV source — the M29 loader + a payload column. Reads ride the
                // SAME confinement as graph {external}: exchange directory or a chooser grant.
                if (m.get("external") instanceof Map<?, ?> ext) {
                    if (label == null || label.isBlank()) {
                        warnings.add("marker entry needs 'label' — skipped");
                        continue;
                    }
                    String path = asText(ext.get("path"));
                    var resolved = telamin.fluxtion.audit.analyser.analyser.llm.ExportGuard.resolveRead(
                            path, cfg != null && cfg.assistantExports,
                            cfg == null ? "" : cfg.assistantExportDir, readGrants.get());
                    if (resolved.error() != null) {
                        warnings.add("marker '" + label + "': " + resolved.error());
                        continue;
                    }
                    Long off = asLong(ext.get("offsetMillis"));
                    specs.add(new telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.MarkerSpec(
                            label, glyph == null ? "circle" : glyph, null, null, null,
                            resolved.path().toString(), asText(ext.get("time")),
                            asText(ext.get("timeFormat")), asText(ext.get("zone")),
                            asText(ext.get("value")), asText(ext.get("payload")),
                            off == null ? 0 : off));
                    continue;
                }
                String when = asText(m.get("when"));
                if (label == null || label.isBlank() || when == null || when.isBlank()) {
                    warnings.add("marker entry needs 'label' and 'when' (or 'external') — skipped");
                    continue;
                }
                try {
                    telamin.fluxtion.audit.analyser.analyser.graph.Expr.parse(when);
                } catch (RuntimeException ex) {
                    warnings.add("marker '" + label + "' when '" + when + "' does not parse: " + ex.getMessage());
                    continue;
                }
                String resolve = asText(m.get("resolve"));
                resolve = resolve == null ? "STRICT" : resolve.toUpperCase(java.util.Locale.ROOT);
                if (!resolve.equals("STRICT") && !resolve.equals("LOCF")) {
                    warnings.add("marker '" + label + "': resolve must be STRICT or LOCF — skipped");
                    continue;
                }
                specs.add(new telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.MarkerSpec(
                        label, glyph == null ? "circle" : glyph, when,
                        asText(m.get("y")), asText(m.get("payload")), resolve));
            }
            panel.setMarkers(specs);
        }
        if (p.containsKey("bands")) {
            List<telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.BandSpec> bands = new ArrayList<>();
            for (Object o : asList(p.get("bands"))) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String expr = asText(m.get("expr"));
                if (expr == null || expr.isBlank()) {
                    warnings.add("band has no 'expr' — skipped");
                    continue;
                }
                try {
                    telamin.fluxtion.audit.analyser.analyser.graph.Expr.parse(expr);
                    bands.add(new telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.BandSpec(
                            expr, asText(m.get("label"))));
                } catch (RuntimeException ex) {
                    warnings.add("band expr '" + expr + "' does not parse: " + ex.getMessage());
                }
            }
            panel.setBands(bands);
        }
        return warnings;
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
    <T> T onEdt(Callable<T> body) {
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

    /**
     * Why a {@code series} argument cannot be applied, or null when it can. Each entry is an
     * {@code "instanceId.key"} string. An object used to be stringified — {@code {expr=a.b, label=x}} has a
     * dot, so it parsed as a key that could never fire — then added, persisted, and echoed as {@code ok}
     * with "unresolved series are still added (they plot when/if the key fires)". Two models in four virgin
     * runs sent the {@code exprs} shape here and one reported the empty chart as "resolved but no data".
     * Refused whole, before anything changes: unlike a malformed guide, a wrong series leaves a chart that
     * looks built and plots nothing.
     */
    static String seriesShapeRefusal(Object series) {
        if (series == null) return null;
        if (!(series instanceof List<?> list)) {
            return "series is a list of \"instanceId.key\" strings, e.g. [\"quotePublisher.spread\"]; got a "
                    + series.getClass().getSimpleName() + ". Nothing was changed";
        }
        for (Object o : list) {
            if (o instanceof String) continue;
            // a null entry was dropped by asStringList and echoed ok — the same empty chart (PR #14 review, F2)
            String got = o == null ? "null"
                    : o instanceof Map<?, ?> m && m.containsKey("expr")
                    ? "an object with 'expr' — for a labelled or computed series use exprs: [{expr, label}]"
                    : "a " + o.getClass().getSimpleName();
            return "series entries are \"instanceId.key\" strings, e.g. \"quotePublisher.spread\"; got " + got
                    + ". Nothing was changed";
        }
        return null;
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
