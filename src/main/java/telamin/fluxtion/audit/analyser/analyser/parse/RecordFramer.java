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
     * True when the line [start,end) is exactly {@code ---} (ignoring surrounding space, tab and CR).
     *
     * <p><b>A byte-order mark is skipped ONLY at the file's first character.</b> Round 3 skipped leading
     * marks on every line, so that two BOM'd files concatenated would still separate at the join. The
     * independent review measured what that cost (F1): the released 1.0.45 exporter escapes every line
     * that trims to {@code ---} by space, tab or CR — the language this predicate spoke when #39 was
     * written — and not a line that begins with U+FEFF. A node value of {@code "\n\uFEFF---\n"} plus
     * marker lines therefore passed through the shipped exporter unescaped and was split here, and a real
     * runtime record read as <b>COMPLETE with no marker written</b>. Widening the reader's separator
     * language silently invalidated the writer's escape.
     *
     * <p>At offset 0 no payload can reach: the container writes the first bytes of a file, never a
     * record's value. So a BOM'd file whose first line is a separator still separates, and that is the only
     * case of the original round-3 fix that is safe. A BOM at a concatenation point no longer separates;
     * the join then runs two records together and {@code UNSEPARATED} says so, which is loud rather than
     * wrong, and is what base did. <b>Before widening this again, widen the exporter's escape first, and
     * test the two together</b> ({@code ExporterFramingAgreementTest}).
     */
    private static boolean isSeparator(String s, int start, int end) {
        int a = start, b = end;
        // Repeated marks are real at the head of a file (`cat bom-only.yaml run.yaml`), so all of them
        // are skipped there — and only there. See above for why never mid-file.
        if (start == 0) while (a < b && AuditText.isBom(s.charAt(a))) a++;
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
