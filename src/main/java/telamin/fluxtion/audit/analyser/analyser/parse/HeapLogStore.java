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
    /**
     * Bytes of the file this store has SEEN, decoded or not. -1 when not built from a file.
     *
     * <p>Follow compared DECODED lengths, so bytes the decoder held back — the start of a character not yet
     * finished — were invisible: a poll that added one byte after a marker returned "no growth", kept the
     * opening identity, and kept COMPLETE (independent review, F2). Growth is a fact about bytes.
     */
    private long byteLength = -1;
    /** Trailing bytes held back as the valid start of an unfinished character. They are past every item. */
    private volatile int pendingBytes;
    private volatile java.util.List<Integer> runBoundaries = java.util.List.of();
    /**
     * Follow saw bytes it could not decode (re-review RR-1). The rows read before them stand; the FILE's
     * claim does not, and nothing more is read until it is reopened. That holds by three different routes,
     * stated separately because the first account of it named only one (second re-review O3): a poll that
     * finds MORE bytes re-decodes the whole file and meets the same bad byte again; a QUIET poll never decodes
     * at all, returning at the byte-length check — which is also the route a SAME-LENGTH replacement takes, so
     * it is not detected and the fault stays (third re-review O-E); and a poll whose decode SUCCEEDS returns
     * {@code -1} so the caller reloads, because the bad byte cannot vanish by appending — the file was replaced
     * (S1). A reload that meets a replacement caught mid-character is the carried cold-open limitation.
     */
    private volatile boolean liveReadFailed;
    /** M68.5: the file key of the content read, or null when the filesystem has none — never compared as equal. */
    private Object fileKey;
    private volatile FollowIdentity followIdentity;
    /** M68.5: the file's metadata when the content in memory was read. */
    private ReadThroughIdentity.Meta atOpen;

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
        this.runBoundaries = tracker.runBoundaries();
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
        s.byteLength = text.getBytes(StandardCharsets.UTF_8).length;
        s.fileKey = keyOf(path);
        s.atOpen = ReadThroughIdentity.metaOf(path);
        return s;
    }

    /** New live-read view, never mutate the ordinary snapshot or its outstanding walkers. */
    public HeapLogStore forFollow() {
        if (!includesEofRecord) return this;
        HeapLogStore live = new HeapLogStore(file, true);
        live.source = source;
        live.readIdentity = readIdentity;
        live.byteLength = byteLength;
        live.fileKey = fileKey;
        live.atOpen = atOpen;
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
        // Integration of M68.5 (D-E6) with MA's byte-level Follow: the bytes are read once, bracketed by attributes, and
        // decided in this order — (1) bytes that shrank are a replacement; (2) a failed live read stays failed on a
        // quiet poll, and a decode that now SUCCEEDS means the file was replaced (S1); (3) the decoded complete prefix is
        // classified against every character already read (FollowIdentity) BEFORE anything is indexed, so a same-length
        // rewrite is announced rather than read as "no growth"; (4) held-back bytes that grew with no new character still
        // re-frame, so the pending state and the stream-end claim move with them (F2).
        java.nio.file.attribute.BasicFileAttributes atStart = attributes(p);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(p);
        } catch (IOException e) {
            bytes = null;
        }
        java.nio.file.attribute.BasicFileAttributes after = attributes(p);
        boolean changedDuringRead = atStart == null || after == null || atStart.size() != after.size()
                || !atStart.lastModifiedTime().equals(after.lastModifiedTime())
                || !java.util.Objects.equals(atStart.fileKey(), after.fileKey());
        Object keyNow = after == null ? null : after.fileKey();
        if (bytes == null) {
            this.followIdentity = FollowIdentity.classify(fileKey, file, keyNow, null, changedDuringRead);
            return 0;                                          // not established: index nothing
        }
        boolean bytesMoved = bytes.length != byteLength;
        // The bytes changed, so no opening digest describes this file any more and no earlier verdict covers it —
        // whether or not they decode (re-review RR-1): retire it before anything else can return.
        if (bytesMoved) this.readIdentity = null;
        if (byteLength >= 0 && bytes.length < byteLength) {
            this.followIdentity = new FollowIdentity(FollowIdentity.Verdict.REPLACEMENT,
                    "the file is shorter than what was already read");
            return -1;                                         // truncated / rotated → the caller reopens, and says why
        }
        if (liveReadFailed && !bytesMoved) return 0;          // a quiet poll after a failed read decodes nothing more
        Utf8Prefix decoded;
        try {
            decoded = decodeCompletePrefix(bytes);
        } catch (java.nio.charset.CharacterCodingException unreadable) {
            // A same-length replacement can fail here too: an earlier successful poll proves nothing about these bytes.
            this.readIdentity = null;
            this.followIdentity = new FollowIdentity(FollowIdentity.Verdict.UNVERIFIED,
                    "the current file could not be decoded as UTF-8");
            this.byteLength = bytes.length;
            this.liveReadFailed = true;
            this.pendingBytes = 0;                        // not a character on its way: never presented as one
            this.streamEnd = StreamEnd.unknown(index.size()).withRuns(streamEnd.runs());
            throw unreadable;
        }
        // A read has already failed on bytes that can never be UTF-8, and those bytes do not go away by appending.
        // So a decode that now SUCCEEDS means the file was replaced underneath the store (second re-review S1).
        if (liveReadFailed) return -1;
        String full = decoded.text();
        FollowIdentity identity = FollowIdentity.classify(fileKey, file, keyNow, full, changedDuringRead);
        this.followIdentity = identity;
        if (identity.verdict() == FollowIdentity.Verdict.REPLACEMENT) return -1;   // the caller reopens, and says why
        boolean onlyPendingBytesMoved = identity.verdict() == FollowIdentity.Verdict.UNCHANGED && bytesMoved;
        if (!identity.mayIndexGrowth() && !onlyPendingBytesMoved) return 0;   // unchanged, or not established
        this.byteLength = bytes.length;
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
        this.file = full;
        this.atOpen = ReadThroughIdentity.metaOf(p);   // M68.5: the content in memory is now what was just read
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
        this.pendingBytes = decoded.pendingBytes();
        this.streamEnd = tracker.resolve();
        this.runBoundaries = tracker.runBoundaries();
        // Held-back bytes are content past the last item, exactly like a record still being written: the
        // file cannot vouch for itself while they are there, whatever an earlier marker declared (F2).
        if (trailingPending || pendingBytes > 0) streamEnd = pendingOverride(streamEnd, index.size());
        return index.size() - before;
    }

    /**
     * Decode only up to the last COMPLETE UTF-8 character (phase 1 round 4, F2).
     *
     * <p>Follow polls a file while a writer appends to it, so a poll can land between the bytes of one
     * multi-byte character — a split BOM, or any non-ASCII text. {@code Files.readString} threw
     * {@code MalformedInputException} for that tick and the status bar showed "Follow read failed". A
     * half-written character is simply not there yet: it is left for the next poll, like any other
     * pending tail. Bytes malformed anywhere else still throw, as before.
     */
    static String completeUtf8(byte[] b) throws java.nio.charset.CharacterCodingException {
        return decodeCompletePrefix(b).text();
    }

    /** What decoded, and how many trailing bytes were held back as an unfinished character. */
    record Utf8Prefix(String text, int pendingBytes) {
    }

    /**
     * Decode everything but a trailing VALID PREFIX of a character, and say how long that prefix is.
     *
     * <p><b>Only a prefix that can still become a character is waited for</b> (independent review, F2). The
     * earlier version held back any trailing lead byte, so {@code C0} — which can never begin valid UTF-8 —
     * would have waited for ever, silently. Now the lead byte and, where UTF-8 restricts it, the second byte
     * are checked against RFC 3629's table; anything that can never complete throws, as malformed bytes
     * anywhere else always did.
     */
    static Utf8Prefix decodeCompletePrefix(byte[] b) throws java.nio.charset.CharacterCodingException {
        int n = b.length, lead = n - 1;
        while (lead >= 0 && lead >= n - 3 && (b[lead] & 0xC0) == 0x80) lead--;   // back over continuation bytes
        int keep = n;
        if (lead >= 0 && lead >= n - 3) {
            int v = b[lead] & 0xFF;
            int need = v < 0x80 ? 1 : (v & 0xE0) == 0xC0 ? 2 : (v & 0xF0) == 0xE0 ? 3 : (v & 0xF8) == 0xF0 ? 4 : 1;
            if (lead + need > n) {
                if (!canBegin(v) || (lead + 1 < n && !secondByteAllowed(v, b[lead + 1] & 0xFF))) {
                    throw new java.nio.charset.MalformedInputException(n - lead);
                }
                keep = lead;                                                     // a valid prefix: wait for it
            }
        }
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b, 0, keep)).toString();
        return new Utf8Prefix(text, n - keep);
    }

    /** RFC 3629: C0, C1 and F5–FF never begin a character; neither does a continuation byte. */
    private static boolean canBegin(int v) {
        return (v >= 0xC2 && v <= 0xDF) || (v >= 0xE0 && v <= 0xEF) || (v >= 0xF0 && v <= 0xF4);
    }

    /** The second-byte ranges RFC 3629 narrows, which is what rules out overlongs and surrogates. */
    private static boolean secondByteAllowed(int lead, int second) {
        return switch (lead) {
            case 0xE0 -> second >= 0xA0 && second <= 0xBF;
            case 0xED -> second >= 0x80 && second <= 0x9F;
            case 0xF0 -> second >= 0x90 && second <= 0xBF;
            case 0xF4 -> second >= 0x80 && second <= 0x8F;
            default -> second >= 0x80 && second <= 0xBF;
        };
    }

    /** A record still being written, or the bytes of a character not yet finished: either way, not done. */
    @Override public int trailingRecordsPending() { return trailingPending || pendingBytes > 0 ? 1 : 0; }

    /**
     * M68.3 (acceptance 10): the frame still being written under Follow — the text after the last {@code ---} — or
     * null when there is none. OBSERVABLE, not accepted: nothing here indexes it, and it is not a record until its
     * separator arrives whole ("--" then "-\n" on a later poll stays pending until the line is complete).
     */
    @Override public String pendingFrameText() {
        if (!trailingPending) return null;
        String text = file;
        int from = 0;
        int i = 0;
        while (i < text.length()) {
            int end = text.indexOf('\n', i);
            if (end < 0) break;                                   // the last line has no newline: part of the frame
            String line = text.substring(i, end).strip();
            if (line.equals("---")) from = end + 1;
            i = end + 1;
        }
        String frame = text.substring(from);
        return frame.isBlank() ? null : frame;
    }

    /**
     * M68.5: a change on disk since the content was read. The text is in memory, so nothing on disk alters what this
     * store serves: a change is labelled superseded, never suspended. Under Follow, {@link #followIdentity} decides.
     */
    @Override public ReadThroughIdentity readThroughIdentity() {
        return source == null || atOpen == null ? null
                : ReadThroughIdentity.classify(atOpen, ReadThroughIdentity.metaOf(source), true);
    }

    /** Independent review R3: assessed when this store was read from a file it can look at again. */
    @Override public boolean readThroughAssessed() {
        return source != null && atOpen != null;
    }

    /** M68.5: what the last Follow poll established about the file's identity, or null before the first poll. */
    @Override public FollowIdentity followIdentity() { return followIdentity; }

    private static Object keyOf(Path p) {
        var a = attributes(p);
        return a == null ? null : a.fileKey();
    }

    private static java.nio.file.attribute.BasicFileAttributes attributes(Path p) {
        try {
            return Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    @Override
    public StreamEnd streamEnd() {
        return streamEnd;
    }

    @Override
    public java.util.List<Integer> runBoundaries() {
        return runBoundaries;
    }

    /**
     * A failed live read is SOURCE DAMAGE: the reader could not read part of the source (second re-review O1).
     * It was first stated as a completeness gap, whose meaning is "the container's own claim did not check
     * out" — but no claim was checked; bytes could not be decoded at all. As damage it is listed FIRST by
     * {@code ProducerDiagnostics}, ahead of anything said about the records that were read. The stream-end
     * state is UNKNOWN either way.
     */
    @Override
    public java.util.List<String> sourceDiagnostics() {
        if (!liveReadFailed) return java.util.List.of();
        return java.util.List.of("Follow could not read this log: its current bytes are not valid UTF-8. The "
                + index.size() + " previously read records are shown from the retained snapshot; whether the current "
                + "file is complete is unknown until it is reopened.");
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
