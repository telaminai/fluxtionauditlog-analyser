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
     */
    public static void frame(String file, Consumer<RawRecord> sink, boolean requireTerminator) {
        if (file == null || file.isEmpty()) return;
        int n = file.length();
        int i = 0;
        int recStart = -1;
        while (i < n) {
            int lineStart = i;
            int j = i;
            while (j < n && file.charAt(j) != '\n') j++;
            int lineEnd = j;                      // exclusive of '\n'
            boolean isSep = isSeparator(file, lineStart, lineEnd);
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

    /** True when the line [start,end) is exactly {@code ---} (ignoring surrounding whitespace/CR). */
    private static boolean isSeparator(String s, int start, int end) {
        int a = start, b = end;
        while (a < b && isWs(s.charAt(a))) a++;
        while (b > a && isWs(s.charAt(b - 1))) b--;
        return (b - a) == 3 && s.charAt(a) == '-' && s.charAt(a + 1) == '-' && s.charAt(a + 2) == '-';
    }

    private static boolean isBlank(String s, int start, int end) {
        for (int k = start; k < end; k++) {
            if (!isWs(s.charAt(k))) return false;
        }
        return true;
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\r';
    }
}
