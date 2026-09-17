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
    private Path source;                  // set when built from a file, so follow can re-read it

    public HeapLogStore(String file) {
        this.file = (file == null) ? "" : file;
        this.index = buildIndex(this.file);
    }

    public static HeapLogStore fromFile(Path path) throws IOException {
        HeapLogStore s = new HeapLogStore(Files.readString(path, StandardCharsets.UTF_8));
        s.source = path;
        return s;
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
        this.file = full;
        // require a terminator so a record still being written isn't indexed until complete; the
        // first `before` records are byte-identical (append-only) so we skip them and add the rest
        RecordFramer.frame(full, raw -> {
            if (seen[0]++ < before) return;
            index.add(RecordParser.parse(raw.text(), raw.offset()));
        }, true);
        return index.size() - before;
    }

    private static LogIndex buildIndex(String file) {
        LogIndex idx = new LogIndex();
        RecordFramer.frame(file, raw -> idx.add(RecordParser.parse(raw.text(), raw.offset())));
        return idx;
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
