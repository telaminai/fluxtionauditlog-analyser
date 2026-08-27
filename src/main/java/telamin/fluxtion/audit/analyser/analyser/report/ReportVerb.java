package telamin.fluxtion.audit.analyser.analyser.report;

import telamin.fluxtion.audit.analyser.analyser.graph.Evaluator;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.llm.ReadService;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The headless half of {@code report {sections}} (spec-investigation-reports M33.3): parameter
 * parsing into a {@link ReportSpec}, and TABLE assembly — running the section's declared call and
 * evaluating its highlight rule. Pure so the echo contract is pinned by test; the app layer owns
 * storage, images and file writes.
 *
 * <p>The echo contract is M26.4's: invalid sections are SKIPPED AND NAMED in warnings, never
 * silently dropped and never fatal to their siblings — and text supplied on a non-narrative section
 * is called out by name, because the model drops it (D-I1) and a silent drop would read as a bug
 * rather than a rule.
 */
public final class ReportVerb {

    private ReportVerb() {
    }

    public record Parsed(ReportSpec spec, List<String> warnings) {
    }

    @SuppressWarnings("unchecked")
    public static Parsed parse(Map<String, Object> params, LogFingerprint fingerprint,
                               FilterSnapshot filter) {
        List<String> warnings = new ArrayList<>();
        List<ReportSpec.SectionSpec> sections = new ArrayList<>();
        Object raw = params.get("sections");
        if (raw instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?, ?> m)) {
                    warnings.add("section " + i + ": not an object — skipped");
                    continue;
                }
                ReportSpec.SectionSpec s = parseSection(i, (Map<String, Object>) m, warnings);
                if (s != null) sections.add(s);
            }
        }
        ReportSpec spec = new ReportSpec(
                str(params.get("name")), str(params.get("title")),
                java.time.Instant.now().toString(), str(params.get("notes")),
                fingerprint, filter, sections);
        return new Parsed(spec, warnings);
    }

    private static ReportSpec.SectionSpec parseSection(int i, Map<String, Object> m,
                                                       List<String> warnings) {
        String kindStr = str(m.get("kind"));
        ReportSpec.Kind kind;
        try {
            kind = ReportSpec.Kind.valueOf(kindStr == null ? "" : kindStr.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnings.add("section " + i + ": unknown kind '" + kindStr + "' — skipped (kinds: "
                    + "finding, record, chart, topology, series, table, narrative)");
            return null;
        }
        String text = str(m.get("text"));
        if (text != null && kind != ReportSpec.Kind.NARRATIVE) {
            // D-I1, said where the caller can hear it: the model has nowhere to put this text
            warnings.add("section " + i + ": 'text' on a '" + kindStr.toLowerCase(java.util.Locale.ROOT)
                    + "' section is ignored — a report includes findings, it never authors them; "
                    + "'flag' is the one write site");
        }
        Integer rec = asInt(m.get("recordIndex"));
        switch (kind) {
            case FINDING, RECORD -> {
                if (rec == null) {
                    warnings.add("section " + i + ": a " + kindStr + " section needs 'recordIndex' — skipped");
                    return null;
                }
                return kind == ReportSpec.Kind.FINDING
                        ? ReportSpec.SectionSpec.finding(rec)
                        : ReportSpec.SectionSpec.record(rec, str(m.get("file")));
            }
            case CHART -> {
                String g = str(m.get("graph"));
                if (g == null) {
                    warnings.add("section " + i + ": a chart section needs 'graph' — skipped");
                    return null;
                }
                return ReportSpec.SectionSpec.chart(g);
            }
            case TOPOLOGY -> {
                String f = str(m.get("focus"));
                if (f == null) {
                    warnings.add("section " + i + ": a topology section needs 'focus' — skipped");
                    return null;
                }
                return ReportSpec.SectionSpec.topology(f);
            }
            case SERIES -> {
                Map<String, Object> call = callMap(m.get("call"));
                if (call.isEmpty()) {
                    warnings.add("section " + i + ": a series section needs 'call' — skipped");
                    return null;
                }
                return ReportSpec.SectionSpec.series(call);
            }
            case TABLE -> {
                Map<String, Object> call = callMap(m.get("call"));
                if (call.isEmpty()) {
                    warnings.add("section " + i + ": a table's rows are derived — it needs 'call' "
                            + "naming a verb (read/series/aggregate/coverage) — skipped");
                    return null;
                }
                return ReportSpec.SectionSpec.table(call, columns(m.get("columns")),
                        str(m.get("rowWhen")), str(m.get("rowWhenLabel")));
            }
            case NARRATIVE -> {
                if (text == null || text.isBlank()) {
                    warnings.add("section " + i + ": a narrative section needs 'text' — skipped");
                    return null;
                }
                return ReportSpec.SectionSpec.narrative(text);
            }
        }
        return null;
    }

    private static List<ReportSpec.ColumnSpec> columns(Object raw) {
        List<ReportSpec.ColumnSpec> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Integer w = asInt(m.get("width"));
                    out.add(new ReportSpec.ColumnSpec(str(m.get("key")), str(m.get("heading")),
                            str(m.get("format")), str(m.get("align")), str(m.get("emphasis")),
                            w == null ? 0 : w));
                }
            }
        }
        return out;
    }

    /**
     * Preserve a report call in the form the action received. Nested maps and arrays must remain
     * structured: {@code aggregate {filter: {...}}} and {@code series {crossings: {...}}} are reissued
     * verbatim after a restart. Top-level scalars retain their historical string form, including the
     * integral-number spelling that makes a JSON {@code recordIndex: 0} reissue as {@code "0"}, not
     * {@code "0.0"}.
     */
    private static Map<String, Object> callMap(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(e.getKey().toString(), e.getValue() instanceof Map<?, ?> || e.getValue() instanceof List<?>
                            ? structured(e.getValue()) : scalar(e.getValue()));
                }
            }
        }
        return out;
    }

    private static Object structured(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                if (e.getKey() != null) copy.put(e.getKey().toString(), structured(e.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(structured(item));
            return copy;
        }
        return value;
    }

    private static String scalar(Object v) {
        if (v instanceof Number n && !(v instanceof Integer || v instanceof Long)) {
            double d = n.doubleValue();
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e15) {
                return Long.toString((long) d);
            }
        }
        return v.toString();
    }

    // ---- table assembly (D-I7: rows are DERIVED) --------------------------------------------------

    public record AssembledTable(ReportRenderer.TableData table, List<String> notes) {
    }

    /**
     * Runs a TABLE section's call and evaluates its highlight rule. {@code read {fields}} is the v1
     * source; the other declared verbs resolve (M33.1) but assemble in a later slice — the note says
     * so rather than returning an empty table that looks like an answer.
     *
     * <p>D-I8: {@code rowWhen} is evaluated STRICTLY against each row's own record — a fresh
     * evaluator per row, refs looked up in that record's nodeLogs only, no LOCF carry. A ref the
     * record did not log is NaN, the rule is not truthy, the row is not highlighted: a rule that
     * cannot be checked against its own row does not fire on it.
     */
    public static AssembledTable assembleTable(ReportSpec.SectionSpec s, LogStore store) {
        List<String> notes = new ArrayList<>();
        String verb = text(s.call().get("verb"));
        if (!"read".equals(verb)) {
            notes.add("table source '" + verb + "' is not assembled yet — v1 derives rows from "
                    + "read {fields}; the section resolves and the gap is stated rather than hidden");
            return new AssembledTable(new ReportRenderer.TableData(s.columns(), List.of(),
                    new boolean[0], s.rowWhen(), s.rowWhenLabel()), notes);
        }
        Map<String, Object> callParams = new LinkedHashMap<>();
        for (var e : s.call().entrySet()) {
            if (e.getKey().equals("verb")) continue;
            if (e.getKey().equals("fields")) {
                callParams.put("fields", fields(e.getValue()));
            } else {
                callParams.put(e.getKey(), e.getValue());   // ReadService parses numerics itself
            }
        }
        if (!callParams.containsKey("fields")) {
            notes.add("the call names no 'fields' — a table needs projected columns; rows are empty");
            return new AssembledTable(new ReportRenderer.TableData(s.columns(), List.of(),
                    new boolean[0], s.rowWhen(), s.rowWhenLabel()), notes);
        }
        Map<String, Object> result = ReadService.read(store.index().snapshot(), callParams, store::rawText);
        Object note = result.get("note");
        if (note != null) notes.add(note.toString());       // the 25-record cap rides into the echo

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");

        List<ReportSpec.ColumnSpec> cols = s.columns().isEmpty()
                ? defaultColumns(s.call().get("fields")) : s.columns();
        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowRecords = new ArrayList<>();
        for (Map<String, Object> r : records) {
            List<String> row = new ArrayList<>(cols.size());
            for (ReportSpec.ColumnSpec col : cols) {
                row.add(cell(r, col.key()));
            }
            rows.add(row);
            rowRecords.add((Integer) r.get("recordIndex"));
        }

        boolean[] hot = new boolean[rows.size()];
        if (s.rowWhen() != null) {
            try {
                Expr rule = Expr.parse(s.rowWhen());
                // A rule needing history cannot be applied one row at a time, and applying it anyway
                // is worse than refusing: the windowed forms that DON'T return NaN quietly collapse to
                // their bare argument and highlight, under a label still claiming the window. Refuse,
                // and say so on the page — resolution carries the same words into the echo.
                String problem = ReportResolver.rowWhenProblem(rule, s.rowWhen());
                if (problem != null) {
                    notes.add(problem);
                } else {
                    Set<GraphKey> refs = rule.refs();
                    for (int i = 0; i < rowRecords.size(); i++) {
                        hot[i] = firesOn(rule, refs, store, rowRecords.get(i));
                    }
                }
            } catch (RuntimeException e) {
                // resolution already carries this as a warning (acceptance 7); render un-highlighted
            }
        }
        return new AssembledTable(new ReportRenderer.TableData(cols, rows, hot,
                s.rowWhen(), s.rowWhenLabel()), notes);
    }

    /** Rows per marker series the PDF table shows before capping (D-M3: the cap names the rest). */
    public static final int MARKER_TABLE_CAP = 200;

    /**
     * The markers riding a chart as a TABLE — label, glyph, time, payload, record — so a printed
     * report carries the events as data, not only as glyphs in a picture (M32.7). Capped with the
     * cap NAMED (D-M3's rule in table form); the record column keeps every row a signpost.
     */
    public static AssembledTable markersTable(
            List<telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries> series) {
        List<ReportSpec.ColumnSpec> cols = List.of(
                new ReportSpec.ColumnSpec("series", "series", "", "left", "", 0),
                new ReportSpec.ColumnSpec("glyph", "glyph", "", "left", "", 0),
                new ReportSpec.ColumnSpec("time", "time (UTC)", "time", "", "", 0),
                new ReportSpec.ColumnSpec("payload", "payload", "", "left", "", 0),
                new ReportSpec.ColumnSpec("record", "record", "", "right", "", 0));
        List<List<String>> rows = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int total = 0;
        for (var ms : series) total += ms.points().size();
        outer:
        for (var ms : series) {
            for (var pt : ms.points()) {
                if (rows.size() >= MARKER_TABLE_CAP) {
                    notes.add("showing the first " + MARKER_TABLE_CAP + " of " + total
                            + " marker(s) — the chart's ×N badges carry the density");
                    break outer;
                }
                rows.add(List.of(ms.label(), ms.glyph(), Long.toString(pt.time()),
                        pt.payload() == null ? "" : pt.payload(),
                        pt.recordIndex() < 0 ? "" : Integer.toString(pt.recordIndex())));
            }
        }
        return new AssembledTable(new ReportRenderer.TableData(cols, rows, new boolean[0], null, null),
                notes);
    }

    private static boolean firesOn(Expr rule, Set<GraphKey> refs, LogStore store, int recordIndex) {
        var nodeLogs = store.record(recordIndex).nodeLogs();
        Map<GraphKey, Double> values = new LinkedHashMap<>();
        for (GraphKey k : refs) {
            KV kv = SeriesExtractor.lastMatching(nodeLogs, k);
            if (kv != null) {
                var d = kv.graphValue();
                if (d.isPresent()) values.put(k, d.getAsDouble());
            }
        }
        Long lt = store.index().logTime(recordIndex);
        Evaluator eval = rule.newEvaluator();               // fresh per row: STRICT, no carry
        double v = eval.eval(lt == null ? 0L : lt, values);
        return Double.isFinite(v) && v != 0.0;
    }

    private static List<String> fields(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList();
        }
        String fields = text(value);
        return fields == null || fields.isBlank() ? List.of() : List.of(fields.split("\\s*,\\s*"));
    }

    private static List<ReportSpec.ColumnSpec> defaultColumns(Object value) {
        List<ReportSpec.ColumnSpec> cols = new ArrayList<>();
        cols.add(new ReportSpec.ColumnSpec("recordIndex", "record", "", "right", "", 0));
        cols.add(new ReportSpec.ColumnSpec("logTime", "time (UTC)", "time", "", "", 0));
        cols.add(new ReportSpec.ColumnSpec("event", "event", "", "left", "", 0));
        for (String f : fields(value)) {
            if (!f.isBlank()) cols.add(new ReportSpec.ColumnSpec(f, f, "", "", "", 0));
        }
        return cols;
    }

    @SuppressWarnings("unchecked")
    private static String cell(Map<String, Object> record, String key) {
        Object direct = record.get(key);
        if (direct != null) return direct.toString();
        Object values = record.get("values");
        if (values instanceof Map<?, ?> v) {
            Object hit = ((Map<String, Object>) v).get(key);
            if (hit != null) return hit.toString();
        }
        return "";
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }

    private static String text(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
