package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-Schema-style parameter shapes for every action verb (AV.3). Published by {@code GET /manifest}
 * so a foreign agent learns each verb's params without reading source or trial-and-error against the
 * structured errors. The single source of truth — the same schemas back the MCP bridge (M13).
 */
public final class VerbSchemas {
    private VerbSchemas() { }

    /** verb → JSON Schema (draft-07 style object) for its {@code params}. */
    public static Map<String, Object> all() {
        Map<String, Object> s = new LinkedHashMap<>();

        s.put("aggregate", schema("Read-only counts/rates over the log; never mutates the UI.",
                props(
                        p("metric", enumStr("count", "rate_per_min", "nan_count", "breach_count"), "what to compute"),
                        p("groupBy", enumStr("dimension", "thread", "hour", "minute", "day", "none"), "bucketing"),
                        p("filter", filterObject(), "optional scope for the aggregation"),
                        p("limit", integer(), "max buckets returned (default 500)")),
                req("metric")));

        s.put("series", schema("Read-only: stats and threshold crossings over any key or formula, "
                        + "computed in the analyser — ask 'where does X exceed Y' in ONE call instead of "
                        + "paging records. Crossings are edge events with recordIndex/byteOffset anchors "
                        + "for a targeted 'read'; capped with an explicit truncated flag.",
                props(
                        p("expr", string(), "a key (\"instanceId.key\") or a formula over keys, e.g. "
                                + "\"ask.price - bid.price\""),
                        p("resolve", enumStr("STRICT", "LOCF"), "STRICT (default): all refs co-occur in "
                                + "one record; LOCF: carry each ref's last value"),
                        p("filter", filterObject(), "optional scope (from/to/dimensions; text is refused "
                                + "here — narrow with the 'filter' verb instead)"),
                        p("crossings", crossingsObject(), "report where the value ENTERS a region: "
                                + "{above} and/or {below}"),
                        p("limit", integer(), "max crossing events per direction (default and cap "
                                + telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan.MAX_CROSSINGS + ")"),
                        p("buckets", enumStr("minute", "hour"), "per-bucket count/min/max/mean instead of "
                                + "one whole-window summary")),
                req("expr")));

        s.put("read", schema("Read-only: the raw text of N records around an anchor, so you can seek the "
                        + "log through this socket without filesystem access. Max " + ReadService.MAX_COUNT
                        + " records/call.",
                props(
                        p("recordIndex", integer(), "anchor by record index (0-based)"),
                        p("byteOffset", integer(), "anchor by byte offset (resolves to the containing record)"),
                        p("at", integer(), "anchor by time (epoch millis) — resolves to the record "
                                + "at-or-before that moment; no need to estimate record indexes from times"),
                        p("count", integer(), "total records, centred on the anchor (default " + ReadService.DEFAULT_COUNT + ")"),
                        p("before", integer(), "records before the anchor (overrides count)"),
                        p("after", integer(), "records after the anchor (overrides count)"),
                        p("fields", arr(string()), "project just these \"instanceId.key\"s (or "
                                + "\"instanceId.*\") per record instead of raw text — 10-50x fewer tokens "
                                + "when you only need specific values; last occurrence per record, same "
                                + "as graphing. Omit for the full raw record (quoting evidence)")),
                List.of() /* one of recordIndex|byteOffset|at — enforced at runtime */));

        s.put("filter", schema("Narrow every view. A missing field is unchanged; null clears it.",
                props(
                        p("from", integer(), "window start (epoch millis)"),
                        p("to", integer(), "window end (epoch millis)"),
                        p("dimensions", arr(string()), "event dimensions to keep (OR); omit/null = all"),
                        p("text", string(), "free-text match (SLOW raw byte scan)")),
                List.of()));

        s.put("graph", schema("Create/append a named time-series graph, or rename one.",
                props(
                        p("name", string(), "target graph name (null = current tab)"),
                        p("series", arr(string()), "raw keys, each \"instanceId.key\""),
                        p("exprs", arr(exprObject()), "formula series over keys"),
                        p("style", enumStr("step", "line", "points"), "plot style"),
                        p("explanation", string(), "multi-line write-up drawn ON the plot — what this "
                                + "chart shows and why it matters. Survives an exported PNG."),
                        p("notes", arr(noteObject()), "notes pinned to moments in time, numbered on the "
                                + "plot and listed beneath it"),
                        p("clearNotes", bool(), "drop existing pins (keeps the explanation)"),
                        p("rightAxis", arr(string()), "series to measure against a SECOND vertical scale; "
                                + "use when magnitudes differ enough that one scale flattens the smaller "
                                + "series into the axis"),
                        p("from", integer(), "pin window start (epoch millis) — survives filter changes"),
                        p("to", integer(), "pin window end (epoch millis)"),
                        p("newTab", bool(), "open a new graph tab"),
                        p("guides", arr(guideObject()), "labelled horizontal threshold rules — REPLACES "
                                + "the set; draw the 0.004 the reader would otherwise interpolate"),
                        p("bands", arr(bandObject()), "shade the time intervals where a condition held "
                                + "(e.g. {expr: \"ask.price - bid.price > 0.004\", label: \"in breach\"}) "
                                + "— REPLACES the set; intervals recompute with the data like any series"),
                        p("rationale", string(), "why you built this graph — captions the plot (provenance)"),
                        p("rename", string(), "with {name}, rename that graph to this")),
                List.of()));

        s.put("goto", schema("Select the record containing an anchor in the table.",
                props(
                        p("byteOffset", integer(), "anchor by byte offset"),
                        p("recordIndex", integer(), "anchor by record index"),
                        p("at", integer(), "anchor by time (epoch millis) — the record at-or-before that moment"),
                        p("reveal", bool(), "if the record is filtered out, relax the filter to show it")),
                List.of()));

        s.put("flag", schema("Bookmark records so your findings are reviewable in the UI. This is the ONE "
                        + "place a finding is written: the note and fix appear in the records table, as a "
                        + "callout painted on the Topology graph for that record, and in an exported "
                        + "report. Supplying only one of note/fix keeps the other.",
                props(
                        p("byteOffsets", arr(integer()), "anchors by byte offset"),
                        p("recordIndexes", arr(integer()), "anchors by record index"),
                        p("note", string(), "what is wrong with this cycle, and why it matters"),
                        p("fix", string(), "the likely cause or suggested fix — where to look")),
                List.of()));

        s.put("coverage", schema("Which of the processor's nodes never wrote audit output in this run — "
                        + "coverage, for a graph. Needs a log AND a graphml. Answers the question a human "
                        + "cannot answer by looking at a 300-node estate: what did this run never "
                        + "exercise? A gap means 'never logged', not proven 'never ran' — see the note in "
                        + "the result.",
                props(
                        p("filtered", bool(), "score only the records the current filter shows "
                                + "(default false: the whole log)"),
                        p("limit", integer(), "how many never-logged nodes to list (default 100)")),
                List.of()));

        s.put("report", schema("Export one record's finding as a PDF: the explanation and suggested fix, "
                        + "the event, the node log, a picture of the topology as currently focused, and "
                        + "optionally a plot. Write the finding with 'flag' and set the view up with "
                        + "'goto' + 'topology' first — the report captures what is on screen. Requires "
                        + "'Allow file exports' (Settings > Assistant); the path resolves INSIDE the "
                        + "configured export directory and existing files are never overwritten.",
                props(
                        p("path", string(), "where to write the .pdf"),
                        p("recordIndex", integer(), "which record; defaults to the current selection"),
                        p("title", string(), "the headline; defaults to the event and record index"),
                        p("graph", string(), "name of an open graph to include, when the problem is a trend"),
                        p("topology", bool(), "include the graph picture (default true)")),
                req("path")));

        s.put("context", schema("Read-only: what the user is currently looking at — the active filter, "
                        + "their selection and flags, the topology cursor, the open graphs, and the source "
                        + "configuration. Returns POINTERS (record indexes and byte offsets), not record "
                        + "text; fetch what you need with 'read'. The returned 'filter' is in the exact "
                        + "shape 'aggregate' accepts, so you can scope a query to the user's own filter by "
                        + "passing it straight back.",
                props(),
                List.of()));

        s.put("topology", schema("Drive the Topology tab: what is shown, what is selected, and where the "
                        + "step cursor is. All reversible view state — nothing is loaded or written.",
                props(
                        p("select", string(), "instanceId to select (null clears the selection)"),
                        p("scope", enumStr("node", "neighbours", "routes", "all"),
                                "how far around the selection to reach; default keeps the current scope"),
                        pAny("focus", "boolean or string. TRUE filters the view to the selection's "
                                + "scope — that context becomes the whole graph and later calls operate "
                                + "inside it (contexts nest); FALSE exits every context. A STRING recalls "
                                + "a saved named focus by name (replaces the context stack)"),
                        pAny("pop", "true steps out one context level; \"all\" returns to the full graph"),
                        p("saveFocusAs", string(), "save the current context as a named focus with this "
                                + "name (replace-by-name; project-tier, shared with saved setups)"),
                        p("rationale", string(), "with saveFocusAs: why this view exists — shown in the "
                                + "picker so the focus is a finding, not an unexplained view"),
                        p("scaffolding", bool(), "show the framework nodes Fluxtion adds to every graph"),
                        p("step", integer(), "advance the cursor by N rows (negative steps back)"),
                        p("recordIndex", integer(), "move the cursor to this record in the filtered view"),
                        p("source", bool(), "show the source pane beside the graph"),
                        p("sync", bool(), "does the source pane follow what is selected, or stay put?"),
                        p("orientation", enumStr("top_down", "left_right"), "layout direction"),
                        p("fit", bool(), "frame the whole graph"),
                        p("callout", bool(), "show the current record's finding painted over the graph "
                                + "(default on). The TEXT comes from that record's flag — write it with "
                                + "'flag', not here"),
                        p("showAll", bool(), "exit every focus context and clear selection and cycle "
                                + "shading — the plain full graph")),
                List.of()));

        s.put("screenshot", schema("Write a PNG of the app's own window to a path. Painted by the app, "
                        + "so it needs no screen-recording permission — and captures exactly the state the "
                        + "other verbs just set up. Requires 'Allow file exports' (Settings > Assistant); "
                        + "the path resolves INSIDE the configured export directory (pass a relative name) "
                        + "and existing files are never overwritten.",
                props(
                        p("path", string(), "where to write the .png"),
                        p("scope", string(), "\"window\" (default), \"topology\", \"records\", or "
                                + "\"menu:File\" to open a top-level menu and leave it open so a native "
                                + "screen capture includes the popup — \"menu:close\" puts it back. The "
                                + "painted PNG never contains a popup; a Swing menu is a separate layer.")),
                req("path")));

        s.put("open", schema("Open an audit log and/or a processor .graphml. Reaches the FILESYSTEM: it "
                        + "points the app at any readable path.",
                props(
                        p("log", string(), "path to an audit log, or an s3:// URI"),
                        p("graphml", string(), "path to a processor .graphml"),
                        p("processor", string(), "fully-qualified EventProcessor class to resolve nodes "
                                + "against; needed before source navigation works")),
                List.of()));

        s.put("source_root", schema("Inspect or change the configured Java source roots. Reaches the "
                        + "FILESYSTEM: a root grants source reading of every .java file beneath it.",
                props(
                        p("add", arr(string()), "roots to add"),
                        p("remove", arr(string()), "roots to remove")),
                List.of()));

        return s;
    }

    // ---- tiny JSON-Schema builders --------------------------------------------------------------

    private static Map<String, Object> schema(String desc, Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("description", desc);
        m.put("properties", properties);
        if (!required.isEmpty()) m.put("required", required);
        return m;
    }

    @SafeVarargs
    private static Map<String, Object> props(Map.Entry<String, Object>... entries) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : entries) m.put(e.getKey(), e.getValue());
        return m;
    }

    private static Map.Entry<String, Object> p(String name, Map<String, Object> type, String desc) {
        type.put("description", desc);
        return Map.entry(name, type);
    }

    /** A property that accepts more than one JSON type — described in words, unconstrained in type. */
    private static Map.Entry<String, Object> pAny(String name, String desc) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("description", desc);
        return Map.entry(name, spec);
    }

    private static List<String> req(String... names) {
        return List.of(names);
    }

    private static Map<String, Object> string() {
        return type("string");
    }

    private static Map<String, Object> integer() {
        return type("integer");
    }

    private static Map<String, Object> bool() {
        return type("boolean");
    }

    private static Map<String, Object> enumStr(String... values) {
        Map<String, Object> m = type("string");
        m.put("enum", List.of(values));
        return m;
    }

    private static Map<String, Object> arr(Map<String, Object> items) {
        Map<String, Object> m = type("array");
        m.put("items", items);
        return m;
    }

    private static Map<String, Object> type(String t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", t);
        return m;
    }

    private static Map<String, Object> crossingsObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("above", number(), "report entries into value > above"),
                p("below", number(), "report entries into value < below")));
        return m;
    }

    private static Map<String, Object> number() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "number");
        return m;
    }

    private static Map<String, Object> filterObject() {
        return schema("filter scope", props(
                p("dimensions", arr(string()), "event dimensions"),
                p("from", integer(), "window start (epoch millis)"),
                p("to", integer(), "window end (epoch millis)"),
                p("text", string(), "free-text match")), List.of());
    }

    /** A note pinned to a moment: when, what it says, and optionally which series it is about. */
    private static Map<String, Object> guideObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("value", number(), "the y value the rule sits at"),
                p("label", string(), "what this threshold means (e.g. \"4bp limit\")"),
                p("rightAxis", bool(), "read the value against the right-hand scale")));
        m.put("required", List.of("value"));
        return m;
    }

    private static Map<String, Object> bandObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("expr", string(), "the condition — any formula; truthy = shaded"),
                p("label", string(), "what the shaded region means")));
        m.put("required", List.of("expr"));
        return m;
    }

    private static Map<String, Object> noteObject() {
        Map<String, Object> m = type("object");
        m.put("properties", props(
                p("at", integer(), "epoch millis the note is about"),
                p("recordIndex", integer(), "…or the record whose logTime to pin it to"),
                p("text", string(), "what to say"),
                p("series", string(), "the series it refers to; omit for a note about the chart")));
        m.put("required", List.of("text"));
        return m;
    }

    private static Map<String, Object> exprObject() {
        return schema("a formula series", props(
                p("label", string(), "display label"),
                p("expr", string(), "formula over keys, e.g. ask.price - bid.price"),
                p("resolve", enumStr("LOCF", "STRICT"), "carry last value (LOCF) or same-record only (STRICT)")),
                req("expr"));
    }
}
