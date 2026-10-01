package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * UPS-1: where one record's text stops being that record's own structure — the trace of a value written unquoted with a
 * line break in it.
 *
 * <p><b>Why it exists.</b> An audit writer that prints {@code eventToString} unquoted lets a line break inside the event's
 * text end the line early; whatever follows is read as if the producer had written it. Measured on mongoose 1.0.32 with
 * fluxtion 1.1.0: an operator-typed admin command argument holding a line break and {@code nodeLogs:} made the analyser
 * report a node that does not exist and blame the graph for not declaring it; one holding {@code event: …} replaced the
 * record's event type. The text cannot say which lines are the producer's, so the parser reads a broken record only up to
 * its break and reads none of its node logs, and {@link ProducerDiagnostics} names it.
 *
 * <p><b>The three breaks</b>, each impossible in a record the producer wrote whole:
 * <ul>
 *   <li>a line <b>less indented than the record's fields</b> — a value's continuation, since nothing of a record sits
 *       outside its own fields;</li>
 *   <li>a <b>second {@code eventLogRecord:} line</b> — a record holds one record key (a separator inside it is a framing
 *       fault the framer has already acted on, MA-7);</li>
 *   <li>a <b>repeated top-level field</b> at the fields' indentation. Which copy is the producer's cannot be told — the
 *       forged one can come first — so the record is read only up to the field's FIRST occurrence.</li>
 * </ul>
 * Lines before the first field (comments, a leading separator a reader plugin renders, a headerless run-together line)
 * are never a break: they are the subject of other findings. A line that begins inside a quoted scalar is that scalar's
 * continuation, never a break — quotes are tracked across lines exactly as {@link FramingScan} tracks them, so the two
 * agree about what is inside a value.
 *
 * @param line       the 1-based line, within the record's text, where the break was found
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

    /** The break is a second record key — the observation {@link FramingScan} names as suspected collapsed framing. */
    public boolean secondRecordKey() {
        return SECOND_RECORD_KEY.equals(reason);
    }

    /** The break in {@code text}, or null when the record's structure is whole. */
    public static RecordBreak find(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] lines = text.split("\n", -1);
        int fieldIndent = -1;
        Map<String, Integer> firstAt = new HashMap<>();
        boolean quotesCount = quotesClose(lines);
        char open = 0;   // the quote open at the start of this line, as FramingScan tracks it
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (!raw.isEmpty() && raw.charAt(raw.length() - 1) == '\r') raw = raw.substring(0, raw.length() - 1);
            boolean insideQuote = quotesCount && open != 0;
            open = FramingScan.quoteStateAfter(raw, 0, raw.length(), open);
            if (insideQuote) continue;   // a quoted scalar's continuation, whatever its indentation
            String t = AuditText.strip(raw);
            if (t.isEmpty() || t.charAt(0) == '#') continue;
            int indent = indentation(raw, i == 0);
            if (fieldIndent < 0) {
                // before the first field: the record key, a leading separator, or text that is not a field at all
                String key = keyOf(t);
                if (key != null) {
                    fieldIndent = indent;
                    if (FIELD_KEYS.contains(key)) firstAt.put(key, i);
                }
                continue;
            }
            // a separator the framer did not split on (a BOM in front of it, mid-file) is framing's subject: the record
            // key after it is the break, which UNSEPARATED names. An ESCAPED separator ("\---") is a value's text.
            if (t.equals("---")) continue;
            if (t.equals(RECORD_KEY)) return new RecordBreak(i + 1, i, SECOND_RECORD_KEY);
            if (indent < fieldIndent) {
                return new RecordBreak(i + 1, i, "a line less indented than the record's fields");
            }
            if (indent == fieldIndent) {
                String key = keyOf(t);
                if (key != null && FIELD_KEYS.contains(key)) {
                    Integer first = firstAt.putIfAbsent(key, i);
                    if (first != null) return new RecordBreak(i + 1, first, "the field '" + key + "' a second time");
                }
            }
        }
        return null;
    }

    /**
     * Whether every quote opened in the record closes within it. Only then are quotes trusted to say what is inside a
     * value: an unquoted value that merely STARTS with a quote character (an event whose text begins with one) never
     * closes, and trusting it would hide every line after it — forged ones included — behind a quote that is not one.
     */
    static boolean quotesClose(String[] lines) {
        char open = 0;
        for (String raw : lines) {
            String line = !raw.isEmpty() && raw.charAt(raw.length() - 1) == '\r' ? raw.substring(0, raw.length() - 1) : raw;
            open = FramingScan.quoteStateAfter(line, 0, line.length(), open);
        }
        return open == 0;
    }

    /** Leading spaces and tabs; a byte-order mark at the very start of the text is the file's, not indentation. */
    private static int indentation(String line, boolean first) {
        int k = first && !line.isEmpty() && AuditText.isBom(line.charAt(0)) ? 1 : 0;
        int n = 0;
        while (k < line.length() && (line.charAt(k) == ' ' || line.charAt(k) == '\t')) { k++; n++; }
        return n;
    }

    /** The key of a {@code key:} line, or null — the record key itself is not a field. */
    private static String keyOf(String trimmed) {
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
