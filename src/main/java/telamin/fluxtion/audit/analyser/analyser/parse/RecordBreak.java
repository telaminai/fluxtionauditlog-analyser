package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * UPS-1: what each line of one record is, and where the record's own structure stops — the trace of a value written
 * unquoted with a line break in it.
 *
 * <p><b>Why it exists.</b> An audit writer that prints {@code eventToString} unquoted lets a line break inside the event's
 * text end the line early; whatever follows is read as if the producer had written it. Measured on mongoose 1.0.32 with
 * fluxtion 1.1.0: an operator-typed admin command argument holding a line break and {@code nodeLogs:} made the analyser
 * report a node that does not exist and blame the graph for not declaring it; one holding {@code event: …} replaced the
 * record's event type. The text cannot say which lines are the producer's, so the parser reads a broken record only up to
 * its break and reads none of its node logs, and {@link ProducerDiagnostics} names it.
 *
 * <p><b>One rule for the detector and the parser</b> (review of 9474c687, findings 1 and 2). {@link #analyse} gives every
 * line a {@link Role}, and {@link RecordParser} reads a field only from a {@link Role#FIELD} line and a node log only from a
 * node-block line. The parser used to accept a field at any indentation and let any field-shaped line end the node block,
 * so a payload indented deeper than this class looked, or quoted where this class did not look, still became evidence.
 * <ul>
 *   <li>A <b>field</b> is a key line at the fields' indentation (the first field's), outside a quoted scalar and outside
 *       the node-log block.</li>
 *   <li>The <b>node-log block</b> follows {@code nodeLogs:} and holds every line more indented than the fields, and the
 *       {@code - } items at the fields' indentation. A line at the fields' indentation that is not an item ends it.</li>
 *   <li>A line that begins <b>inside a closed quoted scalar</b> continues that value, wherever it is: never a field, never
 *       the end of the block. Quotes are tracked across lines exactly as {@link FramingScan} tracks them, and trusted only
 *       when every quote opened in the record closes in it; a quote that never closes is text.</li>
 * </ul>
 *
 * <p><b>The breaks</b>, each impossible in a record the producer wrote whole (fluxtion-runtime's {@code LogRecord} writes
 * its fields at one indentation, items deeper, and {@code nodeLogs:} for every record):
 * <ul>
 *   <li>a line <b>less indented</b> than the fields — except one exported-service signature, below;</li>
 *   <li>a line <b>more indented</b> than the fields outside the node-log block, unless it continues a block scalar
 *       ({@code |}, {@code >}) or nests under an unknown key with no value, which the format ignores (§2);</li>
 *   <li>a non-blank line at the fields' indentation that is <b>not a field</b>;</li>
 *   <li>a <b>second {@code eventLogRecord:} line</b> (a separator inside the record is a framing fault the framer has
 *       already acted on, MA-7);</li>
 *   <li>a <b>repeated field</b>. A value's text can begin a forged copy only after the first field that carries an
 *       event's or a node's own text — {@code eventToString}, {@code nodeLogs}, or a field the format does not know. A
 *       first copy before that line is the producer's (fluxtion-runtime writes {@code eventTime}, {@code logTime},
 *       {@code groupingId} and {@code event} from its own state, never from an event's text), so the record is read up
 *       to the SECOND copy. After it, which copy is the producer's cannot be told — the forged one can come first — so
 *       the record is read only up to the FIRST.</li>
 * </ul>
 * Every break in the record is collected, and the record is read only up to the <b>earliest</b> line any of them withholds
 * from. And because a value's text can begin forged lines only after the first field that carries it, a broken record
 * is never read past that field's own line: a forged field before the visible break — {@code eventType}, which a text
 * producer never writes, so never repeats — is withheld with everything after it.
 *
 * <p><b>The exported-service signature</b> (finding 4). An exported service call's {@code eventToString} is the generator's
 * description: {@code ExportFunctionAuditEvent.toString()} returns what the generated processor passes to
 * {@code beforeServiceCall}, a string literal the builder documents as {@code "@Override\npublic boolean myMethod(int arg0)"}
 * ({@code ExportFunctionDataDto}). Its one line break comes from the generator, never from runtime data, so exactly that
 * shape is whole: the record's first {@code event} is {@code ExportFunctionAuditEvent}, {@code eventToString} is exactly
 * {@code @Override}, and the next line is one column-0 {@code public …(…)} signature. The value reads as {@code @Override},
 * as it did before UPS-1. A value's text cannot select this rule: the producer writes {@code event} before
 * {@code eventToString}.
 *
 * <p>Lines before the first field (comments, a leading separator a reader plugin renders, a headerless run-together line)
 * are never a break: they are the subject of other findings.
 *
 * @param line       the 1-based line, within the record's text, where the structure first breaks
 * @param keepBefore the 0-based line index the parser stops before: fields on lines before it are read, nothing after
 * @param reason     what the break is, in words
 */
public record RecordBreak(int line, int keepBefore, String reason) {

    /** The fields every reader of Format 1 knows; only these can be "repeated" (anything else is ignored, §2). */
    static final Set<String> FIELD_KEYS = Set.of(
            "eventTime", "logTime", "endTime", "groupingId", "event", "eventType", "eventToString", "thread",
            "nodeLogs");

    private static final String RECORD_KEY = "eventLogRecord:";
    private static final String SECOND_RECORD_KEY = "a second '" + RECORD_KEY + "' line";
    private static final String EXPORT_EVENT = "ExportFunctionAuditEvent";
    /** The generator's exported-service signature: public, a return type, a name, a parameter list; no key, no quote. */
    private static final Pattern EXPORT_SIGNATURE =
            Pattern.compile("public [^:#'\"{}()]+\\([^:#'\"{}()]*\\)( throws [\\w$., ]+)?");
    private static final Pattern BLOCK_SCALAR = Pattern.compile("[|>][+-]?[1-9]?[+-]?");

    /** What one line of a record is; the parser reads by this and nothing else. */
    public enum Role {
        /** Not read: blank, a separator, text before the fields, a value's continuation, an ignored nesting. */
        OUTSIDE,
        /** A comment before the first field: the record's header. */
        HEADER,
        /** The {@code eventLogRecord:} line. */
        RECORD_KEY,
        /** A field at the fields' indentation. */
        FIELD,
        /** A {@code - } item of the node-log block. */
        NODE_ITEM,
        /** Any other line of the node-log block, a quoted value's continuation included. */
        NODE_LINE,
        /** A blank line inside the node-log block, kept so the block's text keeps its shape. */
        NODE_BLANK
    }

    /** Every line's role, and the break, or null when the record is whole. */
    public record Structure(Role[] roles, RecordBreak broken) {
    }

    /** The break is a second record key — the observation {@link FramingScan} names as suspected collapsed framing. */
    public boolean secondRecordKey() {
        return SECOND_RECORD_KEY.equals(reason);
    }

    /** The break in {@code text}, or null when the record's structure is whole. */
    public static RecordBreak find(String text) {
        return analyse(text).broken();
    }

    /** Every line's role, by the one rule above; {@code text} is split on {@code \n}, a trailing {@code \r} ignored. */
    public static Structure analyse(String text) {
        if (text == null || text.isEmpty()) return new Structure(new Role[0], null);
        String[] lines = text.split("\n", -1);
        Role[] roles = new Role[lines.length];
        java.util.Arrays.fill(roles, Role.OUTSIDE);
        Breaks breaks = new Breaks();
        int fieldIndent = -1;
        Map<String, Integer> firstAt = new HashMap<>();
        boolean quotesCount = quotesClose(lines);
        char open = 0;                  // the quote open at the start of this line, as FramingScan tracks it
        boolean inNode = false;         // inside the node-log block
        boolean nestedIgnored = false;  // under an unknown key with no value: its nesting is ignored, not read
        boolean blockScalar = false;    // under a field whose value is a block scalar indicator
        String firstEvent = null;
        String previousKey = null, previousValue = null;   // the last field line, for the exported-service shape
        boolean signatureTaken = false;
        int textFrom = Integer.MAX_VALUE;   // the first field that can carry an event's or a node's own text
        for (int i = 0; i < lines.length; i++) {
            String raw = stripCr(lines[i]);
            boolean insideQuote = quotesCount && open != 0;
            open = FramingScan.quoteStateAfter(raw, 0, raw.length(), open);
            if (insideQuote) {          // a quoted scalar's continuation, whatever its indentation (finding 2)
                if (inNode) roles[i] = Role.NODE_LINE;
                continue;
            }
            String t = AuditText.strip(raw);
            if (t.isEmpty()) {
                if (inNode) roles[i] = Role.NODE_BLANK;
                continue;
            }
            if (t.charAt(0) == '#') {
                if (fieldIndent < 0) roles[i] = Role.HEADER;   // only before the fields can a comment be the header
                continue;
            }
            int indent = indentation(raw, i == 0);
            if (fieldIndent < 0) {
                // before the first field: the record key, a leading separator, or text that is not a field at all
                if (t.equals(RECORD_KEY)) {
                    roles[i] = Role.RECORD_KEY;
                    continue;
                }
                String key = keyOf(t);
                if (key == null) continue;
                fieldIndent = indent;
                // falls through: this is the first field
            } else {
                // a separator the framer did not split on (a BOM in front of it, mid-file) is framing's subject: the record
                // key after it is the break, which UNSEPARATED names. An ESCAPED separator ("\---") is a value's text.
                if (t.equals("---")) continue;
                if (t.equals(RECORD_KEY)) {
                    breaks.add(i, i, SECOND_RECORD_KEY);
                    continue;
                }
                if (indent < fieldIndent) {
                    if (!signatureTaken && indent == 0 && EXPORT_EVENT.equals(firstEvent)
                            && "eventToString".equals(previousKey) && "@Override".equals(previousValue)
                            && keyOf(t) == null && EXPORT_SIGNATURE.matcher(t).matches()) {
                        signatureTaken = true;          // the generator's own line break, finding 4
                        previousKey = null;
                        continue;
                    }
                    breaks.add(i, i, "a line less indented than the record's fields");
                    continue;
                }
                if (inNode) {
                    boolean item = t.startsWith("- ") || t.equals("-");
                    if (indent > fieldIndent || item) {
                        roles[i] = item ? Role.NODE_ITEM : Role.NODE_LINE;
                        continue;
                    }
                    inNode = false;             // a line at the fields' indentation ends the block
                }
                if (indent > fieldIndent) {
                    if (nestedIgnored || blockScalar) continue;   // an ignored nesting, or a block scalar's text
                    breaks.add(i, i, "a line more indented than the record's fields, outside its node logs");
                    continue;
                }
            }
            // at the fields' indentation
            nestedIgnored = false;
            blockScalar = false;
            String key = keyOf(t);
            if (key == null) {
                breaks.add(i, i, "a line at the fields' indentation that is not a field");
                continue;
            }
            roles[i] = Role.FIELD;
            String value = AuditText.strip(t.substring(key.length() + 1));
            previousKey = key;
            previousValue = value;
            boolean carriesText = !FIELD_KEYS.contains(key) || key.equals("eventToString") || key.equals("nodeLogs");
            if (carriesText && textFrom == Integer.MAX_VALUE) textFrom = i;
            if (FIELD_KEYS.contains(key)) {
                Integer first = firstAt.putIfAbsent(key, i);
                if (first != null) breaks.add(i, first <= textFrom ? i : first, "the field '" + key + "' a second time");
                if (key.equals("nodeLogs")) inNode = true;
                else if (BLOCK_SCALAR.matcher(value).matches()) blockScalar = true;
                if (key.equals("event") && first == null) firstEvent = value;
            } else if (value.isEmpty()) {
                nestedIgnored = true;
            }
        }
        return new Structure(roles, breaks.result(textFrom));
    }

    /** The earliest structural break, and the earliest line any break withholds from. */
    private static final class Breaks {
        private int line = -1, keepBefore = Integer.MAX_VALUE;
        private String reason;

        void add(int index, int withholdFrom, String why) {
            if (line < 0 || index < line) {
                line = index;
                reason = why;
            }
            keepBefore = Math.min(keepBefore, withholdFrom);
        }

        /** @param textFrom the first field that can carry a value's text: nothing after its line is read once broken */
        RecordBreak result(int textFrom) {
            if (line < 0) return null;
            int keep = textFrom == Integer.MAX_VALUE ? keepBefore : Math.min(keepBefore, textFrom + 1);
            return new RecordBreak(line + 1, keep, reason);
        }
    }

    /**
     * Whether every quote opened in the record closes within it. Only then are quotes trusted to say what is inside a
     * value: an unquoted value that merely STARTS with a quote character (an event whose text begins with one) never
     * closes, and trusting it would hide every line after it — forged ones included — behind a quote that is not one.
     */
    static boolean quotesClose(String[] lines) {
        char open = 0;
        for (String raw : lines) {
            String line = stripCr(raw);
            open = FramingScan.quoteStateAfter(line, 0, line.length(), open);
        }
        return open == 0;
    }

    private static String stripCr(String raw) {
        return !raw.isEmpty() && raw.charAt(raw.length() - 1) == '\r' ? raw.substring(0, raw.length() - 1) : raw;
    }

    /** Leading spaces and tabs; a byte-order mark at the very start of the text is the file's, not indentation. */
    private static int indentation(String line, boolean first) {
        int k = first && !line.isEmpty() && AuditText.isBom(line.charAt(0)) ? 1 : 0;
        int n = 0;
        while (k < line.length() && (line.charAt(k) == ' ' || line.charAt(k) == '\t')) { k++; n++; }
        return n;
    }

    /** The key of a {@code key:} line, or null — the record key itself is not a field. */
    static String keyOf(String trimmed) {
        if (trimmed.equals(RECORD_KEY)) return null;
        int idx = trimmed.indexOf(':');
        if (idx <= 0) return null;
        String key = trimmed.substring(0, idx);
        if (!Character.isLetter(key.charAt(0))) return null;
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return null;
        }
        return key;
    }
}
