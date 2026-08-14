package telamin.fluxtion.audit.analyser.analyser.diff;

import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.DiffRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure formatters for a record diff (R13.12): CSV, JSON, and a monospaced text layout for PDF. No Swing,
 * no external libraries — the PDF is written by {@link TextPdf}.
 */
public final class DiffExport {
    private DiffExport() { }

    /** RFC-4180-ish CSV: {@code key,<labelA>,<labelB>,change}. */
    public static String toCsv(List<DiffRow> rows, String labelA, String labelB) {
        StringBuilder sb = new StringBuilder();
        sb.append(csv("key")).append(',').append(csv(labelA)).append(',').append(csv(labelB)).append(",change\r\n");
        for (DiffRow r : rows) {
            sb.append(csv(r.key())).append(',').append(csv(r.a())).append(',')
              .append(csv(r.b())).append(',').append(r.change().name()).append("\r\n");
        }
        return sb.toString();
    }

    /** A JSON object {@code {a, b, differences, rows:[{key,a,b,change}]}}. */
    public static String toJson(List<DiffRow> rows, String labelA, String labelB) {
        long diffs = rows.stream().filter(DiffRow::isDifference).count();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"a\": ").append(jstr(labelA))
          .append(",\n  \"b\": ").append(jstr(labelB))
          .append(",\n  \"differences\": ").append(diffs)
          .append(",\n  \"rows\": [\n");
        for (int i = 0; i < rows.size(); i++) {
            DiffRow r = rows.get(i);
            sb.append("    {\"key\": ").append(jstr(r.key()))
              .append(", \"a\": ").append(jstr(r.a()))
              .append(", \"b\": ").append(jstr(r.b()))
              .append(", \"change\": ").append(jstr(r.change().name())).append('}')
              .append(i < rows.size() - 1 ? "," : "").append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    /**
     * Aligned monospaced lines (header + a row per key) for the PDF layout. Column widths are capped so
     * a line fits a US-Letter page at Courier 9pt (~98 chars) — long keys/values are clipped, not wrapped.
     */
    public static List<String> toTextLines(List<DiffRow> rows, String labelA, String labelB) {
        int keyW = width(rows.stream().map(DiffRow::key), "key", 34);
        int aW = width(rows.stream().map(DiffRow::a), labelA, 22);
        int bW = width(rows.stream().map(DiffRow::b), labelB, 22);
        List<String> out = new ArrayList<>();
        out.add(pad(clip("key", keyW), keyW) + "  " + pad(clip(labelA, aW), aW) + "  "
                + pad(clip(labelB, bW), bW) + "  change");
        out.add(rule(keyW) + "  " + rule(aW) + "  " + rule(bW) + "  " + rule(9));
        for (DiffRow r : rows) {
            String mark = switch (r.change()) {
                case CHANGED -> "~";
                case ONLY_A -> "-";
                case ONLY_B -> "+";
                case SAME -> " ";
            };
            out.add(pad(clip(r.key(), keyW), keyW) + "  " + pad(clip(r.a(), aW), aW) + "  "
                    + pad(clip(r.b(), bW), bW) + "  " + mark + " " + r.change().name());
        }
        return out;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static int width(java.util.stream.Stream<String> values, String header, int cap) {
        int w = header == null ? 3 : header.length();
        for (String v : (Iterable<String>) values::iterator) if (v != null) w = Math.max(w, v.length());
        return Math.min(w, cap);
    }

    private static String pad(String s, int w) {
        String v = s == null ? "" : s;
        return v.length() >= w ? v : v + " ".repeat(w - v.length());
    }

    private static String clip(String s, int w) {
        String v = s == null ? "" : s;
        return v.length() <= w ? v : v.substring(0, Math.max(0, w - 1)) + "…";
    }

    private static String rule(int w) {
        return "-".repeat(Math.max(1, w));
    }

    private static String csv(String s) {
        String v = s == null ? "" : s;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
