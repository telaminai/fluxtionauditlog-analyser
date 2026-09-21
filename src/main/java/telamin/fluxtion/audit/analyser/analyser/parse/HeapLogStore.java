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
    private volatile boolean trailingPending;
    private final boolean includesEofRecord;
    /** D-E3: whether this file says it is whole. Never null; UNKNOWN for every producer that is silent. */
    private volatile StreamEnd streamEnd = StreamEnd.unknown(0);
    /**
     * Kept across appends so follow uses the same rule the initial load did. Review found the marker
     * leaking into the index in follow mode and the state never moving off its load-time value, because
     * {@link #appendFrom} framed without a tracker at all.
     */
    private final StreamEndTracker tracker = new StreamEndTracker();
    private FileReadIdentity readIdentity;
    private Path source;                  // set when built from a file, so follow can re-read it

    public HeapLogStore(String file) {
        this(file, false);
    }

    /**
     * Indexes the file and, on the same pass, works out whether it says it is whole
     * ({@code spec-audit-stream-end.md} D-E3).
     *
     * <p>The marker is dropped rather than indexed (D-E4): it is a container fact wearing a record's
     * clothes, and it must not reach the table, a count, a series or the timeline. Dropping it HERE, at
     * the one place records enter the index, is why no downstream surface needs to remember to filter it.
     *
     * <p><b>Two interactions with TA-6, both found by the integration rehearsal rather than by me.</b>
     * {@code includesEofRecord} must also require that the last framed item WAS a record: a file whose
     * final item is a marker has an unterminated tail that is not a record, and treating it as an EOF
     * record would make {@link #appendFrom} refuse to follow a file that is simply finished. And a file
     * with a trailing PENDING record cannot be COMPLETE whatever an earlier marker declared — the
     * records after that marker are exactly the case D-E3 calls unknown.
     */
    private HeapLogStore(String file, boolean requireTerminator) {
        this.file = (file == null) ? "" : file;
        this.index = new LogIndex();
        // ONE-ITEM LOOKAHEAD. §1a requires a marker to be followed by its `---`, so an unterminated
        // final item is never a marker — and whether the final item was terminated is only known when the
        // framer returns. So each item is held back until the next one arrives, and the last is decided
        // with `eof` in hand. See the class comment for why that rule exists.
        java.util.ArrayDeque<RawRecord> held = new java.util.ArrayDeque<>(1);
        boolean eof = RecordFramer.frameWithPending(this.file, raw -> {
            if (!held.isEmpty()) offer(held.poll(), false);
            held.add(raw);
        }, requireTerminator);
        boolean lastIndexed = held.isEmpty() || offer(held.poll(), eof && !requireTerminator);
        this.trailingPending = eof && requireTerminator;
        // A held-back marker is not a trailing RECORD, so this snapshot has no EOF record to reload for.
        this.includesEofRecord = eof && !requireTerminator && lastIndexed;
        this.streamEnd = tracker.resolve();
        if (trailingPending) streamEnd = pendingOverride(streamEnd, index.size());
    }

    /**
     * Offer one framed item to the tracker and, if it is a record, to the index.
     *
     * @param unterminated true when this item had no closing {@code ---}. §1a: such an item is NEVER a
     *                     marker, because at the byte level a marker a writer has finished and one it is
     *                     halfway through writing are the same bytes. Round five measured both sides of
     *                     that: a half-written {@code streamEndRecords: 1} of an intended 12 read as
     *                     "the marker is wrong, 12 were read", and a finished-but-unterminated marker
     *                     made follow and a fresh load of identical bytes disagree for ever.
     */
    private boolean offer(RawRecord raw, boolean unterminated) {
        if (unterminated) {
            // §1a rule 1: an unterminated final item is never a claim. If it LOOKS like a marker it is
            // held back and explained rather than indexed — round six asked for that, because otherwise
            // the first real export with a marker shows an unexplained empty row and says nothing.
            if (StreamEndMarker.of(raw.text()).isPresent()) {
                tracker.unterminatedMarker();
                return false;
            }
            tracker.acceptRecord();                          // an ordinary record, but never a marker
        } else if (!tracker.accept(raw.text())) {
            return false;                                    // the marker itself: not a record
        }
        index.add(RecordParser.parse(raw.text(), raw.offset()));
        return true;
    }

    /**
     * A live read holding a record still being written cannot report the file's own verdict.
     *
     * <p>That pending record sits AFTER the last marker, which §1a already calls unknown — but the
     * tracker never saw it, because follow withholds an unterminated item, so the tracker believed the
     * marker was the last thing in the file. Round five's V-4: a live read said "declares 5 records and
     * 3 were read" where a fresh read of the same bytes said unknown. The state is forced to unknown and
     * <b>the failing runs travel with it</b>, so the proof of an earlier loss is not lost in the process.
     */
    private static StreamEnd pendingOverride(StreamEnd end, int records) {
        return StreamEnd.unknown(records).withRuns(end.runs());
    }

    public static HeapLogStore fromFile(Path path) throws IOException {
        var capture = FileReadIdentity.begin(path);
        // Files.readString rejects malformed UTF-8. Re-encoding its immutable text gives exactly
        // the bytes read (including separators/CRLF/BOM), without another traversal of the file.
        String text = Files.readString(path, StandardCharsets.UTF_8);
        capture.accept(text.getBytes(StandardCharsets.UTF_8));
        var identity = capture.finish();
        HeapLogStore s = new HeapLogStore(text, false);
        s.readIdentity = identity;
        s.source = path;
        return s;
    }

    /** New live-read view, never mutate the ordinary snapshot or its outstanding walkers. */
    public HeapLogStore forFollow() {
        if (!includesEofRecord) return this;
        HeapLogStore live = new HeapLogStore(file, true);
        live.source = source;
        live.readIdentity = readIdentity;
        return live;
    }

    @Override public int trailingRecordsIncluded() { return includesEofRecord ? 1 : 0; }

    @Override public java.util.List<FileReadIdentity> readIdentities() {
        return readIdentity == null ? java.util.List.of() : java.util.List.of(readIdentity);
    }

    @Override
    public String localFile() {
        return source == null ? null : source.toString();
    }

    @Override
    public boolean supportsFollow() {
        return source != null;
    }

    @Override
    public int appendFrom(Path path) throws IOException {
        Path p = path != null ? path : source;
        if (p == null) return -1;
        // A snapshot may include an EOF record. It cannot safely become an append-only index:
        // later fields would change an existing row. The adapter reloads it as an explicit live read.
        if (includesEofRecord) return -1;
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
        tracker.reset();
        // Follow withholds an unterminated final item, so everything the sink sees here IS terminated and
        // no lookahead is needed: the §1a rule is satisfied by requireTerminator itself.
        trailingPending = RecordFramer.frameWithPending(full, raw -> {
            if (!tracker.accept(raw.text())) return;      // a marker is never a record, in follow either
            if (seen[0]++ < before) return;               // already indexed, and byte-identical
            index.add(RecordParser.parse(raw.text(), raw.offset()));
        }, true);
        this.streamEnd = tracker.resolve();
        if (trailingPending) streamEnd = pendingOverride(streamEnd, index.size());
        return index.size() - before;
    }

    @Override public int trailingRecordsPending() { return trailingPending ? 1 : 0; }

    @Override
    public StreamEnd streamEnd() {
        return streamEnd;
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
