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
                        p("byteOffset", integer(), "anchor by byte offset (resolves to the containing "
                                + "record). On a rolled set offsets are file-local — pass 'file' too"),
                        p("file", string(), "rolled sets only: which member file a byteOffset is into "
                                + "(name or index)"),
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
                        p("external", arr(externalObject()), "external CSV series (M29) — REPLACES the "
                                + "set. The clock is DECLARED, never sniffed; reads are confined to the "
                                + "exchange directory (Settings ▸ Assistant) or user-chosen files"),
                        p("markers", arr(markerObject()), "marker series (M32) — discrete events drawn "
                                + "as glyphs: buys/sells on a price line with per-point payloads (order "
                                + "ids) on hover; click a marker to select its record. REPLACES the set"),
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
                        p("byteOffset", integer(), "anchor by byte offset (rolled sets: pass 'file' too)"),
                        p("file", string(), "rolled sets only: the member file a byteOffset is into"),
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

        s.put("report", schema("Two forms. SINGLE RECORD (sugar): pass 'path' + 'recordIndex' to export "
                        + "one record's finding as a PDF — write the finding with 'flag' first. "
                        + "INVESTIGATION (M33): pass 'name' + 'sections' to build or REPLACE a named "
                        + "report — an ordered list of REFERENCES with connective prose, never a "
                        + "free-form document. A finding section renders what 'flag' wrote and this "
                        + "verb CANNOT set or change that text. Evidence re-renders live against the "
                        + "loaded log; the report stores the authoring context (log fingerprint + "
                        + "filter) and announces when either differs. Add 'path' to also render the "
                        + "PDF, or 'csv' (a table section index) + 'path' to export that table's rows. "
                        + "Any 'path' requires 'Allow assistant file exchange' and resolves INSIDE the "
                        + "exchange directory; existing files are never overwritten.",
                props(
                        p("name", string(), "the report's identity — building again with the same name REPLACES it"),
                        p("title", string(), "the headline"),
                        p("notes", string(), "prose about the report — rendered visibly as narrative"),
                        p("sections", arr(reportSectionObject()), "the ordered sections; invalid ones "
                                + "are skipped and named in warnings[]"),
                        p("csv", integer(), "export this table section's rows as CSV (needs 'name' of "
                                + "an existing report + 'path')"),
                        p("path", string(), "where to write the .pdf (or .csv with 'csv')"),
                        p("recordIndex", integer(), "single-record form: which record; defaults to the "
                                + "current selection"),
                        p("graph", string(), "single-record form: an open graph to include"),
                        p("topology", bool(), "single-record form: include the graph picture (default true)")),
                List.of()));

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
                        + "other verbs just set up. Requires 'Allow assistant file exchange' (Settings > Assistant); "
                        + "the path resolves INSIDE the configured exchange directory (pass a relative name) "
                        + "and existing files are never overwritten.",
                props(
                        p("path", string(), "where to write the .png"),
                        p("scope", string(), "\"window\" (default), \"topology\", \"records\", or "
                                + "\"menu:File\" to open a top-level menu and leave it open so a native "
                                + "screen capture includes the popup — \"menu:close\" puts it back. The "
                                + "painted PNG never contains a popup; a Swing menu is a separate layer.")),
                req("path")));

        s.put("open", schema("Open an audit log and/or a processor .graphml — or CLOSE what is open. "
                        + "Reaches the FILESYSTEM: it points the app at any readable path.",
                props(
                        p("log", string(), "path to an audit log, or an s3:// URI"),
                        p("logs", arr(string()), "an explicit ROLLED SET (M30): the member files, any "
                                + "order — content decides the load order (each file's first timed "
                                + "logTime); the echo reports the order and the time-order report"),
                        p("graphml", string(), "path to a processor .graphml"),
                        p("processor", string(), "fully-qualified EventProcessor class to resolve nodes "
                                + "against; needed before source navigation works"),
                        p("format", string(), "force a specific installed reader (M31 plugins) — e.g. "
                                + "\"yaml\"; omit to let readers claim the file by content"),
                        p("discover", enumStr("graphml"), "list the .graphml files under the "
                                + "configured source roots, RANKED against the open log (M35.4) — "
                                + "each with its node count and how many of the log's nodes it "
                                + "declares. Opens NOTHING: pick one and pass it as 'graphml'. "
                                + "Auto-selecting would be the convenience that reintroduces the "
                                + "defect M35 exists to prevent"),
                        p("provenance", string(), "WHERE this log came from — free text, declared "
                                + "alongside 'log' (e.g. \"risk-engine · localhost:8081 · ~/dev/risk "
                                + "· exported 09:14Z\"). A file name is not a system: exporting three "
                                + "servers' logs to /tmp gives three artefacts nobody can tell apart. "
                                + "It rides the status bar, context, report headers and PDFs, and lets "
                                + "the mismatch banner name a SYSTEM rather than a temp file. Never "
                                + "inferred — omit it and the analyser says nothing rather than "
                                + "guessing"),
                        p("close", enumStr("log", "graph", "all", "project"), "close what is open "
                                + "(M35.1) — the counterpart of opening, and the way to switch cleanly "
                                + "between systems. Log-DERIVED state clears (records, filter, shading, "
                                + "step cursor, flags); named graphs, focuses, source roots and reports "
                                + "are PROFILE state and survive, each saying why it cannot resolve "
                                + "rather than vanishing. 'project' (M35.8) leaves the active project "
                                + "and restores YOUR OWN settings — the ones in force before any project "
                                + "was opened — which is a session boundary too: the log and graph "
                                + "close with it, and the echo says so. Ignored when combined with "
                                + "log/graphml/processor"),
                        p("project", string(), "path to a project's .analyser/project.fluxtion-settings, "
                                + "or the project directory (M35.8). APPLIES the project — it does not "
                                + "ask: source roots, Maven repos, event processors, named graphs, "
                                + "focuses, reports and hidden columns are REPLACED by the project's, "
                                + "and the open log and graph are CLOSED, because a project is a "
                                + "session boundary. The echo names everything it replaced with "
                                + "before/after counts, what it closed, the previously active project, "
                                + "and the one call that puts it back — so the mutation is reversible "
                                + "from the answer you were given. This is how to ACCEPT the "
                                + "projectOffer that context reports. Any other param in the same call "
                                + "is ignored and named")),
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
    private static Map<String, Object> externalObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("path", string(), "the CSV file — inside the exchange directory, or a file the user "
                        + "opened by hand this session"),
                p("label", string(), "legend name; replace-by-label"),
                p("time", string(), "the timestamp column name"),
                p("timeFormat", string(), "epochMillis | epochSeconds | iso8601 | a DateTimeFormatter "
                        + "pattern — declared, never inferred"),
                p("zone", string(), "IANA zone (UTC, Europe/London) — required unless the format "
                        + "carries an offset"),
                p("value", string(), "the value column name"),
                p("offsetMillis", integer(), "deliberate clock correction, default 0 — always displayed")));
        m.put("required", List.of("path", "label", "time", "timeFormat", "value"));
        return m;
    }

    private static Map<String, Object> markerObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("label", string(), "legend name — one meaning, one series, one glyph"),
                p("glyph", enumStr("triangleUp", "triangleDown", "circle", "square", "diamond", "x"),
                        "the point shape (default circle)"),
                p("when", string(), "a bare \"instanceId.key\" fires wherever that key was logged; "
                        + "anything else is a condition formula, truthy fires"),
                p("y", string(), "\"instanceId.key\" or a formula for the marker's height; "
                        + "\"series:<label>\" rides a plotted series' value; \"axis\" (default) "
                        + "draws ticks in a lane under the plot"),
                p("payload", string(), "an \"instanceId.key\" whose logged text rides each point — "
                        + "shown on hover and in exports, NEVER computable (the record is the "
                        + "queryable form)"),
                p("external", markerExternalObject(), "M32.8: source the markers from a CSV instead "
                        + "of the log — the M29 loader plus a payload column. Points are NOT records "
                        + "(no click-through), the clock is declared, the chart is stamped, and reads "
                        + "are confined to the exchange directory or a chooser grant")));
        m.put("required", List.of("label", "when"));
        return m;
    }

    private static Map<String, Object> markerExternalObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("path", string(), "the CSV file (inside the exchange directory, or chooser-granted)"),
                p("time", string(), "the timestamp column"),
                p("timeFormat", string(), "epochMillis | epochSeconds | iso8601 | a DateTimeFormatter "
                        + "pattern — declared, never inferred"),
                p("zone", string(), "IANA zone, required unless the format carries an offset"),
                p("value", string(), "optional numeric column for the marker's height; omit for the "
                        + "axis lane"),
                p("payload", string(), "optional column whose text rides each point (an order id)"),
                p("offsetMillis", integer(), "deliberate clock correction, always shown on the stamp")));
        m.put("required", List.of("path", "time", "timeFormat"));
        return m;
    }

    private static Map<String, Object> reportSectionObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("kind", string(), "finding | record | chart | topology | series | table | narrative"),
                p("recordIndex", integer(), "finding/record: the anchor. A finding renders what "
                        + "'flag' wrote for that record — there is no way to supply finding text here"),
                p("file", string(), "record on a rolled set: the member file"),
                p("graph", string(), "chart: the open graph's name"),
                p("focus", string(), "topology: the named focus"),
                p("call", type("object"), "series/table: the parameters that DERIVE the data — for a "
                        + "table: {verb: \"read\", fields: \"a.x, b.y\", recordIndex, count, …}"),
                p("text", string(), "narrative ONLY: the prose — rendered visibly as narrative, never "
                        + "styled as evidence"),
                p("columns", arr(reportColumnObject()), "table: the declared presentation"),
                p("rowWhen", string(), "table: highlight rows where this condition is truthy — "
                        + "evaluated STRICTLY against each row's own record, no carry; the rule is "
                        + "printed with the table. POINT-WISE ONLY: rolling-window functions "
                        + "(mean/sum/rollingMin/rollingMax/lag/delta/rate) need history a single row "
                        + "does not have, so they are refused and named rather than applied to a "
                        + "one-sample window — compute the window with 'series' and compare here"),
                p("rowWhenLabel", string(), "table: what the highlight MEANS (e.g. \"in breach\")")));
        m.put("required", List.of("kind"));
        return m;
    }

    private static Map<String, Object> reportColumnObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props(
                p("key", string(), "recordIndex | logTime | event | an instanceId.key from the call's fields"),
                p("heading", string(), "printed heading; defaults to the key"),
                p("format", string(), "\"0\"/\"0.00\" decimals · percent · duration · time (epoch→UTC)"),
                p("align", string(), "left | right (default: numbers right, text left)"),
                p("emphasis", string(), "bold | muted"),
                p("width", integer(), "declared width in points; omit to size from content")));
        m.put("required", List.of("key"));
        return m;
    }

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
