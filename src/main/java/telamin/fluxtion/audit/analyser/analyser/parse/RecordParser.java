package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

import telamin.fluxtion.audit.analyser.analyser.model.EventKind;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.util.Set;

/**
 * Parses one record slice into a {@link LogRecord}: header comment + {@code eventLogRecord} scalar
 * fields eagerly; the {@code nodeLogs} block is captured as text and parsed lazily (spec §4.1).
 * Never throws — an unrecognisable slice yields a {@link EventKind#PARSE_ERROR} record that still
 * carries its raw text.
 */
public final class RecordParser {

    private static final Set<String> SCALAR_KEYS = Set.of(
            "eventTime", "logTime", "endTime", "groupingId", "event", "eventType", "eventToString", "thread",
            "nodeLogs");

    private RecordParser() {
    }

    public static LogRecord parse(String text, long offset) {
        return parse(text, offset, text.length());
    }

    /**
     * Parse with an explicit stored length. Heap store uses the char length; the memory-mapped store
     * passes the record's <b>byte</b> length so it can re-slice the file (spec §7).
     */
    public static LogRecord parse(String text, long offset, int storedLength) {
        return parse(text, offset, storedLength, AuditLogReader.TextEncoding.LEGACY);
    }

    /** @see #parse(String, long, int, AuditLogReader.TextEncoding) */
    public static LogRecord parse(String text, long offset, AuditLogReader.TextEncoding encoding) {
        return parse(text, offset, text.length(), encoding);
    }

    /**
     * @param encoding the {@code nodeLogs} grammar, as DECLARED by the reader that produced this text
     *                 ({@link AuditLogReader#textEncoding()}). Never inferred from the text: a
     *                 declaration-shaped line inside a multiline legacy value is value data, and
     *                 promoting it to a control field deleted the entry after it (review, round 6).
     */
    public static LogRecord parse(String text, long offset, int storedLength,
                                  AuditLogReader.TextEncoding encoding) {
        RecordHeader header = RecordHeader.EMPTY;
        Long eventTime = null, logTime = null, endTime = null;
        String groupingId = null, event = null, eventType = null, eventToString = null, thread = null;
        StringBuilder nodeLogs = new StringBuilder();
        boolean inNodeLogs = false;
        boolean sawFields = false;
        int nodeLogsCount = 0;
        boolean hasNaN = false;
        boolean hasBreach = false;

        for (String raw : text.split("\n", -1)) {
            String line = stripCr(raw);
            String t = line.strip();
            if (t.isEmpty()) {
                if (inNodeLogs) nodeLogs.append('\n');
                continue;
            }
            if (t.charAt(0) == '#') {
                if (header == RecordHeader.EMPTY) header = HeaderParser.parse(t);
                continue;
            }
            if (inNodeLogs) {
                // a non-item line that is a known top-level scalar (e.g. endTime) ends the block —
                // independent of indentation (node-log items always start with "- ")
                boolean isItem = t.startsWith("- ") || t.equals("-");
                if (!isItem && isTopScalarLine(t)) {
                    inNodeLogs = false;   // fall through to scalar handling
                } else {
                    nodeLogs.append(line).append('\n');
                    if (isItem) nodeLogsCount++;
                    if (!hasNaN && t.contains("NaN")) hasNaN = true;
                    if (!hasBreach && t.contains("Breach: true")) hasBreach = true;
                    continue;
                }
            }
            if (t.equals("eventLogRecord:")) {
                sawFields = true;
                continue;
            }
            String[] kv = splitScalar(t);
            if (kv == null) continue;
            String key = kv[0], val = kv[1];
            switch (key) {
                case "nodeLogs":     inNodeLogs = true; sawFields = true; break;
                case "eventTime":    eventTime = parseTime(val, true);  sawFields = true; break;
                case "logTime":      logTime = parseTime(val, false);   sawFields = true; break;
                case "endTime":      endTime = parseTime(val, false);   sawFields = true; break;
                case "groupingId":   groupingId = nullLiteral(val);     sawFields = true; break;
                case "event":        event = emptyToNull(val);          sawFields = true; break;
                // The fully-qualified identity, when the source carries it. The text record never
                // did - it has always written the simple name - so this is null for text logs and
                // set for binary ones, whose wire records Class.getName(). Kept separate from
                // `event` so nothing that matches the simple name literally changes behaviour.
                case "eventType":    eventType = emptyToNull(val);      sawFields = true; break;
                case "eventToString":eventToString = emptyToNull(val);  sawFields = true; break;
                case "thread":       thread = emptyToNull(val);         sawFields = true; break;
                default: /* unknown top-level scalar: ignore, keep in rawText */
            }
        }

        EventDimension dim = EventDimension.derive(event, eventToString);
        String resolvedThread = thread != null ? thread : header.thread();
        final String block = nodeLogs.toString();
        final boolean quotedScalarsFinal = encoding == AuditLogReader.TextEncoding.QUOTED_SCALARS;

        return LogRecord.builder()
                .fileOffset(offset)
                .byteLength(storedLength)
                .eventTime(eventTime)
                .logTime(logTime)
                .endTime(endTime)
                .groupingId(groupingId)
                .event(event)
                .eventType(eventType)
                .textEncoding(encoding)
                .eventToString(eventToString)
                .thread(resolvedThread)
                .logger(header.logger())
                .level(header.level())
                .headerTime(header.time())
                .kind(sawFields ? EventKind.OK : EventKind.PARSE_ERROR)
                .callback(dim.callback())
                .declaringType(dim.declaringType())
                .eventDimension(dim.value())
                .nodeLogsCount(nodeLogsCount)
                .hasNaN(hasNaN)
                .hasBreach(hasBreach)
                .rawText(text)
                .nodeLogsSupplier(() -> NodeLogTokenizer.parseBlock(block, quotedScalarsFinal))
                .build();
    }

    private static boolean isTopScalarLine(String trimmed) {
        String[] kv = splitScalar(trimmed);
        return kv != null && SCALAR_KEYS.contains(kv[0]);
    }

    /** Splits a trimmed scalar line into [key, value] on the first {@code :}; null if not a scalar. */
    private static String[] splitScalar(String t) {
        int idx = t.indexOf(':');
        if (idx <= 0) return null;
        String key = t.substring(0, idx);
        if (!isIdentifier(key)) return null;
        String val = (idx + 1 < t.length() ? t.substring(idx + 1) : "").strip();
        return new String[]{key, val};
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    private static Long parseTime(String val, boolean eventTime) {
        if (val == null || val.isEmpty()) return null;
        try {
            long v = Long.parseLong(val.trim());
            return (eventTime && v == -1L) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullLiteral(String v) {
        return (v == null || v.equals("null") || v.isEmpty()) ? null : v;
    }

    private static String emptyToNull(String v) {
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String stripCr(String s) {
        return (!s.isEmpty() && s.charAt(s.length() - 1) == '\r') ? s.substring(0, s.length() - 1) : s;
    }
}
