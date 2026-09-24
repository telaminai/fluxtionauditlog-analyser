package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Splits a log into record slices on {@code ---} separator lines (spec §4.1). A record is the text
 * between two separator lines, starting at its first non-blank line (the {@code #} header). Blank
 * leading/trailing segments (the file typically starts and ends with {@code ---}) are skipped.
 *
 * <p>Operates on the whole file as a {@code String} (HeapLogStore path); offsets are character
 * offsets. A byte-based streaming variant will be added for the memory-mapped path (M7).
 */
public final class RecordFramer {

    private RecordFramer() {
    }

    public static List<RawRecord> frame(String file) {
        List<RawRecord> out = new ArrayList<>();
        frame(file, out::add);
        return out;
    }

    /** Streams record slices to {@code sink} without materialising a list. */
    public static void frame(String file, Consumer<RawRecord> sink) {
        frame(file, sink, false);
    }

    /**
     * Streams record slices to {@code sink}. When {@code requireTerminator} is true, a trailing record
     * that has no closing {@code ---} yet is <b>not</b> emitted — used by follow/tail mode so a record
     * still being written isn't indexed until it is complete.
     *
     * <p><b>A missing trailing separator is not a defect.</b> §1 makes {@code ---} a SEPARATOR: a whole
     * file may end with its last record and no separator after it, and Mongoose's audit export does,
     * writing {@code \n---\n} only between records. An earlier version of the stream-end work reported
     * such a file as stopped mid-write, which made every real export look damaged. {@code
     * requireTerminator} is a LIVE-READ heuristic — on the next poll the rest of the record will be
     * there — and never a completeness verdict. See {@link StreamEnd}.
     */
    public static void frame(String file, Consumer<RawRecord> sink, boolean requireTerminator) {
        frameWithPending(file, sink, requireTerminator);
    }

    /** Returns whether EOF follows non-blank record text. Emission is controlled separately by requireTerminator. */
    static boolean frameWithPending(String file, Consumer<RawRecord> sink, boolean requireTerminator) {
        if (file == null || file.isEmpty()) return false;
        int n = file.length();
        int i = 0;
        int recStart = -1;
        while (i < n) {
            int lineStart = i;
            int j = i;
            while (j < n && file.charAt(j) != '\n') j++;
            int lineEnd = j;                      // exclusive of '\n'
            boolean isSep = (!requireTerminator || j < n) && isSeparator(file, lineStart, lineEnd);
            boolean isBlank = isBlank(file, lineStart, lineEnd);
            if (isSep) {
                if (recStart >= 0) {
                    emit(file, recStart, lineStart, sink);
                    recStart = -1;
                }
            } else if (recStart < 0 && !isBlank) {
                recStart = lineStart;
            }
            i = (j < n) ? j + 1 : n;              // advance past '\n'
        }
        if (recStart >= 0 && !requireTerminator) emit(file, recStart, n, sink);
        return recStart >= 0;
    }

    /**
     * Frame a text container for a reader PLUGIN — everything except an unterminated final marker.
     *
     * <p>§1a rule 1 says a stream-end marker only counts once its {@code ---} is there, and applying
     * that is the plugin's job: a plugin yields items one at a time, so nothing above it can see where
     * the container ended. Round six found the reference reader handing an unterminated marker over as
     * an ordinary item, and the SPI path then called six files complete where the built-in reader said
     * the claim was unfinished.
     *
     * <p>The rule is narrow on purpose. An unterminated final item that is NOT marker-shaped is an
     * ordinary record and is emitted — the commonest real export ends exactly that way, and withholding
     * it would lose a record. Only a final item that would otherwise be read as a completeness claim is
     * held back. Plugin authors should call this rather than re-deriving it.
     */
    public static void frameForPlugin(String file, Consumer<RawRecord> sink) {
        java.util.ArrayDeque<RawRecord> held = new java.util.ArrayDeque<>(1);
        boolean eof = frameWithPending(file, raw -> {
            if (!held.isEmpty()) sink.accept(held.poll());
            held.add(raw);
        }, false);
        if (held.isEmpty()) return;
        RawRecord last = held.poll();
        if (!(eof && StreamEndMarker.of(last.text()).isPresent())) sink.accept(last);
    }

    private static void emit(String file, int start, int end, Consumer<RawRecord> sink) {
        // trim a single trailing newline/whitespace run but keep the record's own content intact
        int e = end;
        while (e > start) {
            char c = file.charAt(e - 1);
            if (c == '\n' || c == '\r') e--; else break;
        }
        if (e <= start) return;
        sink.accept(new RawRecord(start, e - start, file.substring(start, e)));
    }

    /**
     * True when the line [start,end) is exactly {@code ---} (ignoring surrounding whitespace/CR).
     *
     * <p><b>A leading byte-order mark is skipped too.</b> U+FEFF is a character, not whitespace, so a
     * UTF-8 BOM before the file's first {@code ---} stopped it separating: a healthy BOM'd file whose
     * first line was a separator reported the whole head as one record and raised
     * {@code NO_RECORD_KEY}, and a BOM-only file opened as ONE record with {@code NO_NODE_LOGS} rather
     * than reading as empty. Same treatment as {@code StreamEndMarker.strip}, which already did this.
     */
    private static boolean isSeparator(String s, int start, int end) {
        int a = start, b = end;
        // Repeated marks are real: two BOM'd files concatenated, or a tool adding one to a file that
        // already had it. Skipping only the first left the second as content, so a healthy leading
        // separator stopped separating and the head of the file ran together.
        while (a < b && AuditText.isBom(s.charAt(a))) a++;
        while (a < b && isWs(s.charAt(a))) a++;
        while (b > a && isWs(s.charAt(b - 1))) b--;
        return (b - a) == 3 && s.charAt(a) == '-' && s.charAt(a + 1) == '-' && s.charAt(a + 2) == '-';
    }

    /** A lone byte-order mark is not content: a BOM-only file is empty, not a one-record file. */
    private static boolean isBlank(String s, int start, int end) {
        while (start < end && AuditText.isBom(s.charAt(start))) start++;
        for (int k = start; k < end; k++) {
            if (!isWs(s.charAt(k))) return false;
        }
        return true;
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\r';
    }
}
