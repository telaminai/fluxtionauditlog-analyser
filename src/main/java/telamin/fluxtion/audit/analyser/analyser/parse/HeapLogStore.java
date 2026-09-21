package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * In-heap store for files up to the configured threshold (default 500 MB, spec §7). The whole file
 * is held as a {@code String}; the index is built in one streaming pass; individual records are
 * re-sliced and parsed on demand (their node-logs stay lazy). Table columns come from the index, so
 * scrolling never re-parses.
 */
public final class HeapLogStore implements LogStore {

    private volatile String file;        // grows in follow/tail mode (append-only); volatile: read off-EDT by readView()
    private final LogIndex index;
    /** D-E3: whether this file says it is whole. Never null; UNKNOWN for every producer that is silent. */
    private volatile StreamEnd streamEnd = StreamEnd.unknown(0);
    /**
     * Kept across appends so follow uses the same rule the initial load did. Review found the marker
     * leaking into the index in follow mode and the state never moving off its load-time value, because
     * {@link #appendFrom} framed without a tracker at all.
     */
    private final StreamEndTracker tracker = new StreamEndTracker();
    /**
     * AF-3a: the last indexed row came from text that had not closed with a separator, so if the file
     * grows it must be re-read rather than skipped. Says nothing about completeness — see
     * {@link #buildIndex}.
     */
    private boolean staleLastRow;
    private FileReadIdentity readIdentity;
    private Path source;                  // set when built from a file, so follow can re-read it

    public HeapLogStore(String file) {
        this.file = (file == null) ? "" : file;
        this.index = buildIndex(this.file);
    }

    public static HeapLogStore fromFile(Path path) throws IOException {
        var capture = FileReadIdentity.begin(path);
        // Files.readString rejects malformed UTF-8. Re-encoding its immutable text gives exactly
        // the bytes read (including separators/CRLF/BOM), without another traversal of the file.
        String text = Files.readString(path, StandardCharsets.UTF_8);
        capture.accept(text.getBytes(StandardCharsets.UTF_8));
        var identity = capture.finish();
        HeapLogStore s = new HeapLogStore(text);
        s.readIdentity = identity;
        s.source = path;
        return s;
    }

    @Override public java.util.List<FileReadIdentity> readIdentities() {
        return readIdentity == null ? java.util.List.of() : java.util.List.of(readIdentity);
    }

    @Override
    public String localFile() {
        return source == null ? null : source.toString();
    }

    @Override
    public boolean supportsFollow() {
        return true;
    }

    @Override
    public int appendFrom(Path path) throws IOException {
        Path p = path != null ? path : source;
        if (p == null) return -1;
        String full = Files.readString(p, StandardCharsets.UTF_8);
        if (full.length() < file.length()) return -1;    // truncated / rotated → caller reloads
        if (full.length() == file.length()) return 0;    // no growth
        final int before = index.size();
        final int[] seen = {0};
        // M65 D-F0 part 1: publish the TEXT before the rows that point into it. The file is append-only, so
        // every row already indexed keeps valid offsets in the longer string, and a reader that sees a new row
        // under the index lock therefore sees a `file` that contains it. The old order (rows first, text last)
        // left a window in which a walker could read a row whose span lay past the end of the old string.
        // If the framing below throws midway, `file` is new and the index partially extended: every indexed
        // row still has a valid span (no reader can throw), and the unindexed tail is picked up the next time
        // the file GROWS — a same-length re-read returns 0 above. Before, the retry was immediate but readers
        // could throw meanwhile (impl review F2).
        this.readIdentity = null; // follow changes the indexed view; no stale opening digest may describe it
        this.file = full;
        // Require a terminator so a record still being written isn't indexed until complete; the first
        // `before` records are byte-identical (append-only) so we skip them and add the rest.
        //
        // The tracker is RESET and re-run over the whole file rather than continued from where the load
        // left it. This pass already walks every record — the skip below is what makes it cheap, not the
        // framing — so re-running costs one marker test per record and keeps the follow state derived
        // from the same rule, in the same order, as a fresh load of the same bytes. Review found both
        // halves of this broken: an appended marker was indexed as a record, and streamEnd kept its
        // load-time value for ever.
        //
        // AF-3a: the last indexed row may have been half-written when it was indexed. A load emits an
        // unterminated trailing record — correctly, since §1 lets a whole file end without a separator —
        // but if the file GROWS, that record was being written after all, and skipping it as
        // already-indexed freezes its truncated text in the index for ever. Measured before the fix: a row
        // kept `event: Ti` and no node logs while the file held `event: Tick` and one.
        //
        // So that row is RE-READ rather than skipped. It is dropped only once its replacement is in hand,
        // which is why the new records are collected before any of them is added: if the record is STILL
        // half-written, this pass withholds it, `tail` comes back empty, and the stale row is left exactly
        // where it is. The index never shrinks, and no row ever vanishes from under a reader.
        final int keep = staleLastRow && before > 0 ? before - 1 : before;
        final java.util.List<LogRecord> tail = new java.util.ArrayList<>();
        tracker.reset();
        RecordFramer.frame(full, raw -> {
            if (!tracker.accept(raw.text())) return;      // a marker is never a record, in follow either
            if (seen[0]++ < keep) return;                 // already indexed, and byte-identical
            tail.add(RecordParser.parse(raw.text(), raw.offset()));
        }, true);
        if (keep < before && !tail.isEmpty()) {
            index.dropLast();                             // replaced, not removed: `tail` holds it whole
            staleLastRow = false;
        }
        for (LogRecord r : tail) index.add(r);
        this.streamEnd = tracker.resolve();
        return index.size() - before;
    }

    /**
     * Indexes the file and, on the same pass, works out whether it says it is whole
     * ({@code spec-audit-stream-end.md} D-E3).
     *
     * <p>The marker is dropped rather than indexed (D-E4): it is a container fact wearing a record's
     * clothes, and it must not reach the table, a count, a series or the timeline. Dropping it HERE,
     * at the one place records enter the index, is why no downstream surface needs to remember to
     * filter it.
     */
    private LogIndex buildIndex(String file) {
        LogIndex idx = new LogIndex();
        RecordFramer.frame(file, raw -> {
            if (tracker.accept(raw.text())) idx.add(RecordParser.parse(raw.text(), raw.offset()));
        }, false);
        this.streamEnd = tracker.resolve();
        // AF-3a: was the last row indexed from text that had not closed yet? Answered from the file
        // itself rather than by asking the framer, deliberately. The framer used to report an unclosed
        // tail and that report was read as a completeness verdict, which made every real export look
        // damaged; the signal is gone and should stay gone. THIS question is a different one, local to
        // follow, and it has nothing to say about whether the file is whole: a closed file that ends
        // without a separator is whole, and simply never grows.
        this.staleLastRow = idx.size() > 0 && !endsWithSeparator(file);
        return idx;
    }

    /** True when the text's last non-blank line is a {@code ---} separator, so nothing is left open. */
    private static boolean endsWithSeparator(String text) {
        int end = text.length();
        while (end > 0) {
            int lineStart = text.lastIndexOf('\n', end - 1) + 1;
            String line = text.substring(lineStart, end).strip();
            if (!line.isEmpty()) return line.equals("---");
            end = lineStart == 0 ? 0 : lineStart - 1;
        }
        return false;
    }

    @Override
    public StreamEnd streamEnd() {
        return streamEnd;
    }

    @Override
    public java.util.List<String> sourceDiagnostics() {
        String d = streamEnd.diagnostic("this log");
        return d == null ? java.util.List.of() : java.util.List.of(d);
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public LogIndex index() {
        return index;
    }

    @Override
    public LogRecord record(int row) {
        return RecordParser.parse(rawText(row), index.offset(row));
    }

    @Override
    public String rawText(int row) {
        int start = (int) index.offset(row);
        return file.substring(start, start + index.length(row));
    }

    /**
     * M65 D-F0 part 2 — the view a walker takes while follow may append. ORDER MATTERS: {@code size} and the
     * span arrays are captured under the index lock, and the text is read AFTER the lock is released. The
     * writer's order is {@code file = full} (volatile) then {@code index.add} (locked), so a reader that saw row
     * {@code k} under the lock sees a text that contains it. Reading the text BEFORE the lock would pair an old
     * string with a new size — the very race part 1 closes. Rows are served from the captured arrays and
     * string, never from the live index, which is read unsynchronised.
     */
    @Override
    public ReadView readView() {
        final LogIndex.RowSpans spans = index.rowSpans();   // size + arrays, UNDER the lock …
        final String text = file;                            // … then the text, AFTER it (volatile read)
        return new ReadView() {
            @Override public int size() { return spans.size(); }
            @Override public LogIndex index() { return index; }
            @Override public String rawText(int row) {
                int start = (int) spans.offset(row);
                return text.substring(start, start + spans.length(row));
            }
            @Override public LogRecord record(int row) {
                return RecordParser.parse(rawText(row), spans.offset(row));
            }
        };
    }

    @Override
    public Long minLogTime() {
        return index.minLogTime();
    }

    @Override
    public Long maxLogTime() {
        return index.maxLogTime();
    }
}
