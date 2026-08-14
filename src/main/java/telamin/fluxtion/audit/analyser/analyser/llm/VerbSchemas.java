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
                        p("filter", filterObject(), "optional scope for the aggregation")),
                req("metric")));

        s.put("read", schema("Read-only: the raw text of N records around an anchor, so you can seek the "
                        + "log through this socket without filesystem access. Max " + ReadService.MAX_COUNT
                        + " records/call.",
                props(
                        p("recordIndex", integer(), "anchor by record index (0-based)"),
                        p("byteOffset", integer(), "anchor by byte offset (resolves to the containing record)"),
                        p("count", integer(), "total records, centred on the anchor (default " + ReadService.DEFAULT_COUNT + ")"),
                        p("before", integer(), "records before the anchor (overrides count)"),
                        p("after", integer(), "records after the anchor (overrides count)")),
                List.of() /* one of recordIndex|byteOffset — enforced at runtime */));

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
                        p("from", integer(), "pin window start (epoch millis) — survives filter changes"),
                        p("to", integer(), "pin window end (epoch millis)"),
                        p("newTab", bool(), "open a new graph tab"),
                        p("rationale", string(), "why you built this graph — captions the plot (provenance)"),
                        p("rename", string(), "with {name}, rename that graph to this")),
                List.of()));

        s.put("goto", schema("Select the record containing an anchor in the table.",
                props(
                        p("byteOffset", integer(), "anchor by byte offset"),
                        p("recordIndex", integer(), "anchor by record index"),
                        p("reveal", bool(), "if the record is filtered out, relax the filter to show it")),
                List.of()));

        s.put("flag", schema("Bookmark records so your findings are reviewable in the UI.",
                props(
                        p("byteOffsets", arr(integer()), "anchors by byte offset"),
                        p("recordIndexes", arr(integer()), "anchors by record index"),
                        p("note", string(), "annotation stored with the flag (your finding)")),
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

    private static Map<String, Object> filterObject() {
        return schema("filter scope", props(
                p("dimensions", arr(string()), "event dimensions"),
                p("from", integer(), "window start (epoch millis)"),
                p("to", integer(), "window end (epoch millis)"),
                p("text", string(), "free-text match")), List.of());
    }

    private static Map<String, Object> exprObject() {
        return schema("a formula series", props(
                p("label", string(), "display label"),
                p("expr", string(), "formula over keys, e.g. ask.price - bid.price"),
                p("resolve", enumStr("LOCF", "STRICT"), "carry last value (LOCF) or same-record only (STRICT)")),
                req("expr"));
    }
}
