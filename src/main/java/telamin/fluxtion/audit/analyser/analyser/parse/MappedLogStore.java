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
    private final Path path;
    private final Map<Integer, LogRecord> cache = new LinkedHashMap<>(CACHE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, LogRecord> e) {
            return size() > CACHE;
        }
    };

    public MappedLogStore(Path path) throws IOException {
        this.path = path;
        this.index = buildIndex(path);
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
    }

    @Override
    public String localFile() {
        return path == null ? null : path.toString();
    }

    private static LogIndex buildIndex(Path path) throws IOException {
        LogIndex idx = new LogIndex();
        ByteRecordFramer.frame(path, (offset, length, text) -> idx.add(RecordParser.parse(text, offset, length)));
        return idx;
    }

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
