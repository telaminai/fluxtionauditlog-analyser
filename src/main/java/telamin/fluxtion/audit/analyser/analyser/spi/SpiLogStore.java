package telamin.fluxtion.audit.analyser.analyser.spi;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The generic store the CORE builds over any {@link AuditLogReader}'s record stream (D-P1: plugins
 * hand over records, never stores). Offsets are synthetic (running positions over the canonical
 * texts) and are <b>not advertised</b> — a reader without {@code byteAnchors} anchors by
 * {@code recordIndex} only, exactly as M30's D-R2 already generalised.
 */
public final class SpiLogStore implements LogStore {

    private final List<String> texts = new ArrayList<>();
    private final LogIndex index = new LogIndex();
    private final AuditLogReader reader;

    private SpiLogStore(AuditLogReader reader) {
        this.reader = reader;
    }

    public static SpiLogStore open(AuditLogReader reader, Path source) throws IOException {
        SpiLogStore store = new SpiLogStore(reader);
        store.index.setByteAnchors(reader.capabilities().byteAnchors());   // synthetic offsets refuse anchoring
        long[] offset = {0};
        reader.read(source, text -> {
            LogRecord rec = RecordParser.parse(text, offset[0]);
            store.index.add(rec);
            store.texts.add(text);
            offset[0] += text.length();   // synthetic — never handed out as a real file offset
        });
        return store;
    }

    /** The reader that produced this store — the capability flags live on it (D-P4). */
    public AuditLogReader reader() {
        return reader;
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
        return RecordParser.parse(texts.get(row), index.offset(row));
    }

    @Override
    public String rawText(int row) {
        return texts.get(row);
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
