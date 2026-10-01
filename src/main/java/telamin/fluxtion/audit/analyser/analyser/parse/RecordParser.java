package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

import telamin.fluxtion.audit.analyser.analyser.model.EventKind;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLogData;

/**
 * Parses one record slice into a {@link LogRecord}: header comment + {@code eventLogRecord} scalar
 * fields eagerly; the {@code nodeLogs} block is captured as text and parsed lazily (spec §4.1).
 * Never throws — an unrecognisable slice yields a {@link EventKind#PARSE_ERROR} record that still
 * carries its raw text.
 */
public final class RecordParser {

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
        // Block extraction strips CRs and skips comments; retain the original line positions so the
        // tokenizer's spans address rawText, not its normalised intermediate block.
        java.util.List<int[]> nodeLinePositions = new java.util.ArrayList<>();
        int rawPosition = 0;
        boolean sawFields = false;
        int nodeLogsCount = 0;
        boolean hasNaN = false;
        boolean hasBreach = false;

        // UPS-1: ONE rule says what each line is, and the parser reads by it (review of 9474c687, findings 1 and 2): a field
        // only at the fields' indentation, outside a quoted scalar and outside the node-log block; a node log only inside
        // that block. A record whose structure breaks (a value written unquoted with a line break in it) is read only up to
        // the break, and none of its node logs are read — the text cannot say which lines after it are the producer's.
        RecordBreak.Structure structure = RecordBreak.analyse(text);
        RecordBreak broken = structure.broken();
        RecordBreak.Role[] roles = structure.roles();
        String[] lines = text.split("\n", -1);
        int readLines = broken == null ? lines.length : broken.keepBefore();
        for (int lineIndex = 0; lineIndex < readLines; lineIndex++) {
            String raw = lines[lineIndex];
            int linePosition = rawPosition;
            rawPosition += raw.length() + 1;
            String line = stripCr(raw);
            // AuditText.strip, not String.strip(): strip() keeps U+FEFF, so a record behind a
            // byte-order mark never matched the '#' below, lost its header, and with it its thread,
            // level and logger — which moved auditLevelFinest from DEBUG to INFO and made coverage
            // say debug calls might be missing. A BOM changed a verdict.
            String t = AuditText.strip(line);
            switch (roles[lineIndex]) {
                case HEADER -> {
                    if (header == RecordHeader.EMPTY) header = HeaderParser.parse(t);
                }
                case RECORD_KEY -> sawFields = true;
                case NODE_BLANK -> nodeLogs.append('\n');
                case NODE_ITEM, NODE_LINE -> {
                    nodeLinePositions.add(new int[]{nodeLogs.length(), linePosition});
                    nodeLogs.append(line).append('\n');
                    if (roles[lineIndex] == RecordBreak.Role.NODE_ITEM) nodeLogsCount++;
                    if (!hasNaN && t.contains("NaN")) hasNaN = true;
                    if (!hasBreach && t.contains("Breach: true")) hasBreach = true;
                }
                case FIELD -> {
                    String[] kv = splitScalar(t);
                    if (kv == null) break;
                    String key = kv[0], val = kv[1];
                    switch (key) {
                        case "nodeLogs":     sawFields = true; break;
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
                case OUTSIDE -> { }
            }
        }

        if (broken != null) {
            nodeLogs.setLength(0);
            nodeLinePositions.clear();
            nodeLogsCount = 0;
            hasNaN = false;
            hasBreach = false;
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
                .brokenAtLine(broken == null ? 0 : broken.line())
                .nodeLogDataSupplier(() -> {
                    NodeLogData data = NodeLogTokenizer.parseBlockData(block, quotedScalarsFinal);
                    java.util.List<NodeLogData.KeySpan> spans = new java.util.ArrayList<>();
                    int line = 0;
                    for (var span : data.keySpans()) {
                        while (line + 1 < nodeLinePositions.size()
                                && nodeLinePositions.get(line + 1)[0] <= span.start()) line++;
                        int[] position = nodeLinePositions.get(line);
                        int shift = position[1] - position[0];
                        spans.add(new NodeLogData.KeySpan(span.start() + shift, span.end() + shift,
                                span.instanceId(), span.key()));
                    }
                    return new NodeLogData(data.nodes(), spans);
                })
                .build();
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
