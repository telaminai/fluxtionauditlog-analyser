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

    /** Asked once at open (M34.1) — a reader that scans a registry must not be re-invoked per query. */
    private AuditLogReader.SourceGraph sourceGraph;

    /** Why the reader's graph is absent, when it tried and failed. Null when it simply had none. */
    private String graphNote;

    public String graphNote() {
        return graphNote;
    }

    private SpiLogStore(AuditLogReader reader) {
        this.reader = reader;
    }

    @Override
    public java.util.Optional<AuditLogReader.SourceGraph> sourceGraph() {
        return java.util.Optional.ofNullable(sourceGraph);
    }

    public static SpiLogStore open(AuditLogReader reader, Path source) throws IOException {
        SpiLogStore store = new SpiLogStore(reader);
        store.index.setByteAnchors(reader.capabilities().byteAnchors());   // synthetic offsets refuse anchoring
        store.index.setTotalOrder(reader.capabilities().ordering()          // D-A1a: an invented order
                == AuditLogReader.Ordering.TOTAL);                          // must never read as causality
        // a reader that cannot supply a graph returns empty; one that FAILS must not take the log
        // down with it — an unreadable graph is a missing graph, and the log is still evidence
        try {
            store.sourceGraph = reader.graph(source).orElse(null);
        } catch (RuntimeException | IOException e) {
            store.graphNote = reader.formatId() + " could not supply a graph: " + e.getMessage();
        }
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
