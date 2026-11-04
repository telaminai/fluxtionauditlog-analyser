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
     *                 Under {@code LEGACY} only, the record's own declaration - {@code textEncoding:
     *                 QUOTED_SCALARS} as the first line of its mapping ({@link #declaration(String)}) -
     *                 selects the quoted grammar for this record; the result says so
     *                 ({@link LogRecord#recordDeclaredEncoding()}).
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
        // §3a: a record may declare its own grammar, but only as the FIRST line of its mapping, and only where the
        // reader declares none. Anywhere else the same spelling is content. A reader's declaration is never overridden.
        boolean recordDeclared = false;
        if (encoding == AuditLogReader.TextEncoding.LEGACY) {
            Declaration declaration = declaration(lines, roles, readLines);
            if (declaration != null && declaration.honoured()) {
                encoding = AuditLogReader.TextEncoding.QUOTED_SCALARS;
                recordDeclared = true;
            }
        }
        final boolean decodeHeader = recordDeclared;
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
                        case "groupingId":   groupingId = header(val, decodeHeader, true); sawFields = true; break;
                        case "event":        event = header(val, decodeHeader, false); sawFields = true; break;
                        // The fully-qualified identity, when the source carries it. The text record never
                        // did - it has always written the simple name - so this is null for text logs and
                        // set for binary ones, whose wire records Class.getName(). Kept separate from
                        // `event` so nothing that matches the simple name literally changes behaviour.
                        case "eventType":    eventType = header(val, decodeHeader, false); sawFields = true; break;
                        case "eventToString":eventToString = header(val, decodeHeader, false); sawFields = true; break;
                        case "thread":       thread = header(val, decodeHeader, false); sawFields = true; break;
                        default: /* unknown top-level scalar: ignore, keep in rawText */
                    }
                }
                case OUTSIDE -> { }
            }
        }

        // A broken record reads none of its node logs: RecordBreak never reads one past its first text-carrying field
        // (nodeLogs is one), so no node line precedes readLines.
        EventDimension dim = EventDimension.derive(event, eventToString);
        String resolvedThread = thread != null ? thread : header.thread();
        final String block = nodeLogs.toString();
        final boolean quotedScalarsFinal = encoding == AuditLogReader.TextEncoding.QUOTED_SCALARS;
        // reserved @-keys are the binary reader's vocabulary; the text runtime that declares per record has none
        final boolean reservedKeysFinal = quotedScalarsFinal && !recordDeclared;

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
                .recordDeclaredEncoding(recordDeclared)
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
                    NodeLogData data = NodeLogTokenizer.parseBlockData(block, quotedScalarsFinal, reservedKeysFinal);
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

    /** The key of the per-record grammar declaration (format-spec §3a). */
    public static final String TEXT_ENCODING_KEY = "textEncoding";

    /**
     * A record's own grammar declaration: {@code textEncoding: <value>} as the first non-empty line after
     * {@code eventLogRecord:}. Only {@code QUOTED_SCALARS} is honoured; any other value leaves the record legacy
     * and is reported ({@link ProducerDiagnostics.Kind#UNKNOWN_TEXT_ENCODING}).
     *
     * @param line  the 1-based line within the record's text
     * @param value the declared value, as written
     */
    public record Declaration(int line, String value) {
        public boolean honoured() {
            return AuditLogReader.TextEncoding.QUOTED_SCALARS.name().equals(value);
        }
    }

    /**
     * The declaration {@code text} makes about its own grammar, or null when it makes none. Recognised by POSITION,
     * as the §1a stream-end marker is: the line that immediately follows the record key (blank lines aside, the same
     * whitespace and byte-order-mark stripping), at the fields' indentation and outside any quoted value. A line spelled
     * the same anywhere else - inside a multi-line {@code eventToString}, a node value, after {@code endTime} - is content.
     */
    public static Declaration declaration(String text) {
        if (text == null || text.indexOf(TEXT_ENCODING_KEY) < 0) return null;   // the common case: no analysis
        String[] lines = text.split("\n", -1);
        RecordBreak.Structure structure = RecordBreak.analyse(text);
        RecordBreak broken = structure.broken();
        return declaration(lines, structure.roles(), broken == null ? lines.length : broken.keepBefore());
    }

    private static Declaration declaration(String[] lines, RecordBreak.Role[] roles, int readLines) {
        int key = -1;
        for (int i = 0; i < readLines; i++) {
            if (roles[i] == RecordBreak.Role.RECORD_KEY) { key = i; break; }
            if (roles[i] == RecordBreak.Role.FIELD) return null;   // fields with no record key before them
        }
        if (key < 0) return null;
        for (int i = key + 1; i < readLines; i++) {
            String t = AuditText.strip(lines[i]);
            if (t.isEmpty()) continue;
            if (roles[i] != RecordBreak.Role.FIELD) return null;
            String[] kv = splitScalar(t);
            if (kv == null || !TEXT_ENCODING_KEY.equals(kv[0])) return null;
            return new Declaration(i + 1, kv[1]);
        }
        return null;
    }

    /**
     * A header scalar's value. Under a record's own declaration a value that is ENTIRELY one quoted scalar decodes
     * (§3a) and is text whatever it spells - a quoted {@code "null"} grouping id is the string {@code null}. Otherwise,
     * and always for a reader's declaration, it is the raw text it has always been.
     */
    private static String header(String val, boolean decode, boolean nullLiteral) {
        if (decode) {
            NodeLogTokenizer.Scalar scalar = NodeLogTokenizer.unquote(val);
            if (scalar.quoted()) return emptyToNull(scalar.text());
        }
        return nullLiteral ? nullLiteral(val) : emptyToNull(val);
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
