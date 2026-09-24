package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Large-file backend (spec §7). The whole file is never held in heap: a single streaming pass builds
 * the {@link LogIndex} (byte offsets + scalar fields), and individual records are read on demand from
 * the file channel and parsed lazily, with a small LRU cache. Scales past 2 GB (64-bit byte offsets).
 */
public final class MappedLogStore implements LogStore {

    private static final int CACHE = 256;

    private final FileChannel channel;
    private final LogIndex index;
    private final StreamEnd streamEnd;
    private final java.util.List<Integer> runBoundaries;
    private final Path path;
    private final FileReadIdentity readIdentity;
    private final boolean includesEofRecord;
    private final Map<Integer, LogRecord> cache = new LinkedHashMap<>(CACHE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, LogRecord> e) {
            return size() > CACHE;
        }
    };

    public MappedLogStore(Path path) throws IOException {
        this.path = path;
        this.index = new LogIndex();
        var capture = FileReadIdentity.begin(path);
        StreamEndTracker tracker = new StreamEndTracker();
        try (var in = capture.open()) {
            // Same one-item lookahead as the heap store, and for the same §1a reason: an unterminated
            // final item is never a marker, and whether it was terminated is only known at the end.
            Object[] held = {null, null, null};
            boolean eof = ByteRecordFramer.frameWithEof(in, (offset, length, text) -> {
                if (held[2] != null) offer(tracker, index, (long) held[0], (int) held[1], (String) held[2], false);
                held[0] = offset; held[1] = length; held[2] = text;
            });
            boolean lastIndexed = held[2] == null
                    || offer(tracker, index, (long) held[0], (int) held[1], (String) held[2], eof);
            includesEofRecord = eof && lastIndexed;
        }
        this.streamEnd = tracker.resolve();
        this.runBoundaries = tracker.runBoundaries();
        this.readIdentity = capture.finish();
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
    }

    /** @see HeapLogStore#offer — an unterminated final item is never a marker (§1a). */
    private static boolean offer(StreamEndTracker tracker, LogIndex index, long offset, int length,
                                 String text, boolean unterminated) {
        if (unterminated) {
            if (StreamEndMarker.of(text).isPresent()) {      // §1a rule 1, see HeapLogStore#offer
                tracker.unterminatedMarker();
                return false;
            }
            tracker.acceptRecord();
        } else if (!tracker.accept(text)) {
            return false;
        }
        index.add(RecordParser.parse(text, offset, length));
        return true;
    }

    @Override
    public StreamEnd streamEnd() {
        return streamEnd;
    }

    @Override
    public java.util.List<Integer> runBoundaries() {
        return runBoundaries;
    }

    @Override
    public String localFile() {
        return path == null ? null : path.toString();
    }

    @Override public java.util.List<FileReadIdentity> readIdentities() { return java.util.List.of(readIdentity); }

    @Override public int trailingRecordsIncluded() { return includesEofRecord ? 1 : 0; }
    @Override public int trailingRecordsPending() { return 0; }

    @Override public int size() { return index.size(); }
    @Override public LogIndex index() { return index; }
    @Override public Long minLogTime() { return index.minLogTime(); }
    @Override public Long maxLogTime() { return index.maxLogTime(); }

    @Override
    public synchronized LogRecord record(int row) {
        LogRecord cached = cache.get(row);
        if (cached != null) return cached;
        LogRecord parsed = RecordParser.parse(rawText(row), index.offset(row), index.length(row));
        cache.put(row, parsed);
        return parsed;
    }

    @Override
    public String rawText(int row) {
        long offset = index.offset(row);
        int length = index.length(row);
        ByteBuffer bb = ByteBuffer.allocate(length);
        try {
            int read = 0;
            while (read < length) {
                int r = channel.read(bb, offset + read);
                if (r < 0) break;
                read += r;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new String(bb.array(), 0, bb.position(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException ignore) {
            // best-effort
        }
    }
}
