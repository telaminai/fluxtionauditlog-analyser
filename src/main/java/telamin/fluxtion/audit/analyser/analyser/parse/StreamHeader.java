package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The audit stream header as it appears in a log: the document a Fluxtion runtime (1.1.4 and later) writes
 * before the first record of every stream, and again whenever it re-heads one (a new sink, record format or
 * host context). The runtime's own reference is {@code AuditStreamHeader} and FLXA §3.4; the text form is
 * <pre>
 * auditStreamHeader:
 *     schema: 1
 *     processor: com.acme.TradeProcessor
 *     sourceFingerprint: 5f3c...
 *     toolchainVersion: 1.0.84
 *     runtimeVersion: 1.1.4
 *     startedAt: 2026-11-03T09:15:02.118Z
 *     host: {serverId: pricing-1, runId: 2c41..., group: pricing, name: quoter, generation: '1'}
 * </pre>
 * and a binary (FLXA version 2) file carries the same text in a HEADER frame, which the binary reader hands
 * over as a document of its own. One parser, both formats.
 *
 * <p><b>Stream metadata, never a record.</b> Like the stream-end marker it is recognised at the one place
 * documents enter a store ({@link StreamEndTracker#accept}), so it never reaches the index, a count, a filter,
 * a walk, coverage or an export of records, and no downstream surface has to remember to leave it out.
 *
 * <p><b>Recognised by position, validated by shape.</b> A document is a header when its first content line
 * (after blank lines, comments and a plain leading separator) is exactly {@code auditStreamHeader:}. Every
 * such document is taken out of the records, well formed or not. A well-formed one has {@code schema: 1} and
 * all seven keys the schema-1 writer always writes, each on an indented {@code key: value} line, with
 * {@code host} a flow map. Anything else is kept in order with a {@link #problem()} and stated as a
 * diagnostic: it is neither read as a record nor trusted as a header. Requiring every key is what catches a
 * header cut short by a crash, whose last key ({@code host}) is the one that goes missing first. Keys beyond
 * the seven are ignored: the runtime makes new keys additive within a schema.
 *
 * <p><b>What it proves</b> is the runtime's statement, repeated so no surface overstates it: equal
 * {@code sourceFingerprint}s mean the same graph SHAPE (node names, classes, callbacks and edges), never the
 * same build. {@code null} means the producer had no fingerprint to state.
 *
 * @param firstRecord       the row index of the first record this header precedes, which is the number of
 *                          records read before it; equal to the log's size when no record follows it
 * @param schema            the declared schema, or null when absent or not an integer
 * @param processor         the processor's name, or null
 * @param sourceFingerprint the graph-shape digest, or null when the producer had none (no descriptor, or an
 *                          interpreted processor)
 * @param toolchainVersion  information only, never part of the key; or null
 * @param runtimeVersion    information only, never part of the key; or null
 * @param startedAt         when the stream started, as written (an ISO-8601 instant); or null
 * @param host              the host's run identity, insertion-ordered and unmodifiable; empty outside a host
 * @param problem           why this header is not well formed, in words; null when it is
 * @param text              the document as the log holds it, from its first content line (a leading separator and
 *                          blank lines removed) — so an excerpt of the log can carry the header in force, byte for byte
 */
public record StreamHeader(int firstRecord, Integer schema, String processor, String sourceFingerprint,
                           String toolchainVersion, String runtimeVersion, String startedAt,
                           Map<String, String> host, String problem, String text) {

    /** The document key, as the runtime's {@code AuditStreamHeader.DOCUMENT_KEY} spells it. */
    public static final String DOCUMENT_KEY = "auditStreamHeader";
    /** The only schema this analyser reads. */
    public static final int SCHEMA = 1;

    private static final String KEY_LINE = DOCUMENT_KEY + ":";
    private static final List<String> KEYS = List.of("schema", "processor", "sourceFingerprint",
            "toolchainVersion", "runtimeVersion", "startedAt", "host");

    public StreamHeader {
        host = host == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(host));
    }

    /** True when this header is well formed and its fields may be used. */
    public boolean valid() {
        return problem == null;
    }

    /** The same header, placed at a different row — a rolled set re-bases each member's rows. */
    public StreamHeader atRecord(int row) {
        return new StreamHeader(row, schema, processor, sourceFingerprint, toolchainVersion, runtimeVersion,
                startedAt, host, problem, text);
    }

    /**
     * Whether this document is a stream header: its first content line is exactly {@code auditStreamHeader:}.
     * Cheap for the overwhelming majority of documents, which are records.
     */
    public static boolean isHeader(String documentText) {
        if (documentText == null || documentText.indexOf(KEY_LINE) < 0) return false;
        String[] lines = documentText.split("\n", -1);
        int at = firstContentLine(lines);
        return at >= 0 && AuditText.strip(lines[at]).equals(KEY_LINE);
    }

    /**
     * Where a header document that ran straight into the next document ends — the 0-based line of the first
     * column-0 content line after its body — or -1 when this is not a header or nothing follows it.
     *
     * <p>A sink frames the header exactly as it frames records. With {@code ---} between documents the header
     * arrives alone. A sink that separates nothing runs it into the record after it, and the framers split it
     * there, so the record keeps its own text and the header is still not a record. The header's body is
     * indented and the writer never puts a line break in a value, so its first unindented line is the next
     * document — a comment header such as {@code #10:57:37.407 [main] INFO …} included.
     */
    static int documentAfter(String documentText) {
        if (!isHeader(documentText)) return -1;
        String[] lines = documentText.split("\n", -1);
        int key = firstContentLine(lines);
        for (int i = key + 1; i < lines.length; i++) {
            String raw = lines[i];
            if (AuditText.asciiStrip(raw).isEmpty()) continue;
            char c = raw.charAt(0);
            if (c != ' ' && c != '\t') return i;
        }
        return -1;
    }

    /**
     * Parses a document {@link #isHeader} accepted. Never throws: whatever is wrong is the {@link #problem()}.
     *
     * @param firstRecord the number of records read before this document
     */
    public static StreamHeader parse(String documentText, int firstRecord) {
        String[] lines = documentText.split("\n", -1);
        int key = firstContentLine(lines);
        Map<String, String> values = new LinkedHashMap<>();
        String problem = null;
        for (int i = key + 1; i < lines.length && problem == null; i++) {
            String raw = stripCr(lines[i]);
            String t = AuditText.asciiStrip(raw);
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (raw.charAt(0) != ' ' && raw.charAt(0) != '\t') {
                problem = "line " + (i + 1) + " is not indented under '" + KEY_LINE + "'";
                break;
            }
            int colon = t.indexOf(':');
            String name = colon <= 0 ? null : t.substring(0, colon);
            if (name == null || !isIdentifier(name)) {
                problem = "line " + (i + 1) + " is not a 'key: value' line";
                break;
            }
            if (values.containsKey(name)) {
                problem = "the key '" + name + "' appears twice";
                break;
            }
            values.put(name, t.substring(colon + 1));
        }
        Integer schema = null;
        String schemaText = values.containsKey("schema") ? scalar(values.get("schema")) : null;
        if (schemaText != null && schemaText.matches("[0-9]{1,9}")) schema = Integer.parseInt(schemaText);
        Map<String, String> host = Map.of();
        if (problem == null) {
            if (!values.containsKey("schema")) {
                problem = "it has no 'schema'";
            } else if (schema == null) {
                problem = "its schema " + quoted(schemaText) + " is not a number";
            } else if (schema != SCHEMA) {
                problem = "it declares schema " + schema + ", and this analyser reads schema " + SCHEMA;
            } else {
                List<String> missing = KEYS.stream().filter(k -> !values.containsKey(k)).toList();
                if (!missing.isEmpty()) {
                    problem = "it is missing " + String.join(", ", missing)
                            + " — a header cut short, or one this writer did not finish";
                }
            }
        }
        if (problem == null) {
            Map<String, String> parsed = flowMap(values.get("host"));
            if (parsed == null) problem = "its host is not a {key: value} map";
            else host = parsed;
        }
        return new StreamHeader(firstRecord, schema,
                scalar(values.get("processor")), scalar(values.get("sourceFingerprint")),
                scalar(values.get("toolchainVersion")), scalar(values.get("runtimeVersion")),
                scalar(values.get("startedAt")), host, problem, documentText(lines));
    }

    /**
     * The sentence a malformed header contributes to a store's source diagnostics, or null for a valid one.
     *
     * @param records how many records the log holds, so a header after the last one can say so
     */
    public String diagnostic(int records) {
        if (valid()) return null;
        String where = firstRecord >= records
                ? (records == 0 ? "The stream header in this log" : "The stream header after the last record")
                : "The stream header before record " + (firstRecord + 1);
        return where + " is not well formed: " + problem + ". It is not read as a record, and it says "
                + "nothing about which processor or graph wrote the records after it.";
    }

    /** Every malformed header's sentence, in order. */
    public static List<String> diagnostics(List<StreamHeader> headers, int records) {
        return headers.stream().map(h -> h.diagnostic(records)).filter(java.util.Objects::nonNull).toList();
    }

    // ---- the small YAML subset the writer produces --------------------------------------------------------

    /** The document from its first non-blank, non-separator line, trailing line breaks removed. */
    private static String documentText(String[] lines) {
        int from = 0;
        while (from < lines.length && (AuditText.strip(lines[from]).isEmpty() || AuditText.strip(lines[from]).equals("---"))) {
            from++;
        }
        String t = String.join("\n", java.util.Arrays.asList(lines).subList(from, lines.length));
        int end = t.length();
        while (end > 0 && (t.charAt(end - 1) == '\n' || t.charAt(end - 1) == '\r')) end--;
        return t.substring(0, end);
    }

    /** A record's string form names the header without repeating its document. */
    @Override
    public String toString() {
        return "StreamHeader[firstRecord=" + firstRecord + ", processor=" + processor + ", sourceFingerprint="
                + sourceFingerprint + ", host=" + host + (problem == null ? "" : ", problem=" + problem) + "]";
    }

    /** The first line that is not blank, a comment, or a plain separator; -1 when there is none. */
    private static int firstContentLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String t = AuditText.strip(lines[i]);
            if (t.isEmpty() || t.startsWith("#") || t.equals("---")) continue;
            return i;
        }
        return -1;
    }

    private static boolean isIdentifier(String s) {
        if (!Character.isLetter(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    private static String stripCr(String raw) {
        return !raw.isEmpty() && raw.charAt(raw.length() - 1) == '\r' ? raw.substring(0, raw.length() - 1) : raw;
    }

    /**
     * One scalar as the writer spells it: {@code null}, a plain value, or a single-quoted one with {@code ''}
     * for a quote. A double-quoted value is accepted for a hand-written header. A plain value's {@code  #}
     * comment is dropped. Returns null for {@code null}, {@code ~}, an empty value, or an absent key.
     */
    static String scalar(String rest) {
        if (rest == null) return null;
        String v = AuditText.asciiStrip(rest);
        if (v.isEmpty()) return null;
        char q = v.charAt(0);
        if (q == '\'' || q == '"') {
            String inner = unquote(v, 0);
            if (inner != null) return inner;
        }
        int hash = v.indexOf(" #");
        if (hash >= 0) v = AuditText.asciiStrip(v.substring(0, hash));
        if (v.equals("null") || v.equals("~") || v.isEmpty()) return null;
        return v;
    }

    /** The quoted scalar starting at {@code from}, or null when it never closes. */
    private static String unquote(String v, int from) {
        int end = quotedEnd(v, from);
        if (end < 0) return null;
        String inner = v.substring(from + 1, end);
        return v.charAt(from) == '\'' ? inner.replace("''", "'") : inner.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** The index of the closing quote of the scalar opened at {@code from}, or -1. */
    private static int quotedEnd(String v, int from) {
        char q = v.charAt(from);
        for (int i = from + 1; i < v.length(); i++) {
            char c = v.charAt(i);
            if (q == '\'' && c == '\'') {
                if (i + 1 < v.length() && v.charAt(i + 1) == '\'') { i++; continue; }
                return i;
            }
            if (q == '"' && c == '\\') { i++; continue; }
            if (q == '"' && c == '"') return i;
        }
        return -1;
    }

    /**
     * A one-line flow map, {@code {k: v, k2: 'a, b'}}, with scalars as {@link #scalar} reads them. Null when the
     * text is not one. A key spelled twice is not a map this writer produces, so it is refused too.
     */
    static Map<String, String> flowMap(String rest) {
        if (rest == null) return null;
        String v = AuditText.asciiStrip(rest);
        if (v.length() < 2 || v.charAt(0) != '{' || v.charAt(v.length() - 1) != '}') return null;
        String body = AuditText.asciiStrip(v.substring(1, v.length() - 1));
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i < body.length()) {
            int[] keyEnd = new int[1];
            String k = token(body, i, ':', keyEnd);
            if (k == null || keyEnd[0] >= body.length() || body.charAt(keyEnd[0]) != ':') return null;
            int[] valueEnd = new int[1];
            String val = token(body, keyEnd[0] + 1, ',', valueEnd);
            if (val == null || out.containsKey(scalarOrEmpty(k))) return null;
            out.put(scalarOrEmpty(k), scalar(val));
            i = valueEnd[0] < body.length() ? valueEnd[0] + 1 : body.length();
            if (valueEnd[0] < body.length() && AuditText.asciiStrip(body.substring(i)).isEmpty()) return null;
        }
        return out;
    }

    private static String scalarOrEmpty(String token) {
        String s = scalar(token);
        return s == null ? "null" : s;
    }

    /**
     * The token from {@code from} up to {@code stop} (or the end), honouring quotes. Writes where it stopped to
     * {@code end[0]}. Null when a quote does not close or the token is empty.
     */
    private static String token(String s, int from, char stop, int[] end) {
        int i = from;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        if (i < s.length() && (s.charAt(i) == '\'' || s.charAt(i) == '"')) {
            int close = quotedEnd(s, i);
            if (close < 0) return null;
            int j = close + 1;
            while (j < s.length() && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j++;
            if (j < s.length() && s.charAt(j) != stop) return null;
            end[0] = j;
            return s.substring(i, close + 1);
        }
        int j = s.indexOf(stop, i);   // a plain token never holds its stop: the writer quotes any that would
        if (j < 0) j = s.length();
        String t = AuditText.asciiStrip(s.substring(i, j));
        if (t.isEmpty()) return null;
        end[0] = j;
        return t;
    }

    private static String quoted(String s) {
        return s == null ? "(empty)" : "'" + (s.length() > 40 ? s.substring(0, 40) + "…" : s) + "'";
    }
}
