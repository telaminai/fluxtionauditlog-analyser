package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

/**
 * Backend abstraction over a loaded log. The {@link LogIndex} serves all browse/filter/summary
 * columns; {@link #record(int)} materialises a full record (with lazy node-logs) on demand for the
 * detail view / graphing. Implementations: {@code HeapLogStore} (≤ threshold) and {@code
 * MappedLogStore} (memory-mapped, M7).
 */
public interface LogStore extends AutoCloseable {

    /** Identities of the raw local bytes consumed during opening, in log-set order; empty if unsupported. */
    default java.util.List<FileReadIdentity> readIdentities() { return java.util.List.of(); }

    int size();

    LogIndex index();

    /** Full parse of one record (scalars + lazy node-logs). */
    LogRecord record(int row);

    /** Cheap raw text of a row (no parsing) — used for full-text search incl. node-logs. */
    String rawText(int row);

    /**
     * The graph this source declared, when it had one (M34.1). Default empty: a text container is a
     * stream of records and knows nothing about structure, which is why the GraphML has always been
     * a separate file. Only an SPI reader can answer differently.
     */
    default java.util.Optional<telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.SourceGraph>
            sourceGraph() {
        return java.util.Optional.empty();
    }

    /**
     * Why {@link #sourceGraph} is empty when the source TRIED and failed — null when it simply had
     * none (review M34 F2). Without this on the store surface the reason SpiLogStore records has no
     * reader: a source whose registry was unreachable looked exactly like one with no graph.
     */
    default String sourceGraphNote() {
        return null;
    }

    /** The {@code nodeLogs} grammar every record of this store is parsed with — the reader's declaration. */
    default telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding textEncoding() {
        return telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding.LEGACY;
    }

    /**
     * What the reader could NOT read of the source — a cut tail, names the file never defined — as
     * plain statements. Empty for a whole source. These are about the SOURCE, shown beside the
     * evidence, never inside it.
     */
    default java.util.List<String> sourceDiagnostics() {
        return java.util.List.of();
    }

    /**
     * What this source says about its own COMPLETENESS, as sentences — {@code spec-audit-stream-end.md}.
     *
     * <p>Separate from {@link #sourceDiagnostics()}, which is what the reader could not read. Round five
     * found a rolled set of whole files labelled "source damage" on the status bar, because every store
     * diagnostic was wrapped as damage. These are a different kind of statement and now say so.
     *
     * <p>Derived HERE from {@link #streamEnd()} rather than overridden per store: three stores each had
     * their own copy of the same two lines, and the SPI store's copy was simply missing, so a plugin-read
     * log told an agent records were missing and told the person nothing.
     */
    default java.util.List<String> completenessDiagnostics() {
        String d = StreamEndReport.sentence(streamEnd(), "this log");
        return d == null ? java.util.List.of() : java.util.List.of(d);
    }

    /** True when {@link #completenessDiagnostics()} states a limit rather than reporting a fault. */
    default boolean completenessIsNote() {
        // Round six S-1: this was state == UNKNOWN alone, so a proven loss followed by one more record
        // became a NOTE and lost its warning, while the same loss without that record warned. §1a says
        // the failed run MUST still be reported; a run that is reported without a warning is not.
        return streamEnd().state() == StreamEnd.State.UNKNOWN && streamEnd().runs().isEmpty();
    }

    /**
     * Whether this source says it is whole — {@code spec-audit-stream-end.md} D-E3.
     *
     * <p>The default is UNKNOWN, and that is the honest answer for every store that cannot tell: a
     * silent producer, a reader plugin over someone else's container, a rolled set. UNKNOWN is not a
     * defect and must never be rendered as "complete" (D-T8).
     */
    default StreamEnd streamEnd() {
        return StreamEnd.unknown(size());
    }

    /**
     * A bounded view for a walk that may overlap a follow append (M65 D-F0). {@link #size()} is fixed when the
     * view is taken; {@link #record}/{@link #rawText} serve rows below it from data captured with that size, so a
     * walker never reads a row the store is still writing. Take one per walk; do not hold it across walks.
     */
    interface ReadView {
        int size();

        LogIndex index();

        LogRecord record(int row);

        String rawText(int row);
    }

    /**
     * The default is a live view with its size fixed at creation — correct for every store that never grows.
     * A store that supports follow overrides it with a locked capture ({@code HeapLogStore}).
     */
    default ReadView readView() {
        final LogStore self = this;
        final int n = size();
        return new ReadView() {
            @Override public int size() { return n; }
            @Override public LogIndex index() { return self.index(); }
            @Override public LogRecord record(int row) { java.util.Objects.checkIndex(row, n); return self.record(row); }
            @Override public String rawText(int row) { java.util.Objects.checkIndex(row, n); return self.rawText(row); }
        };
    }

    Long minLogTime();

    Long maxLogTime();

    /** The local file this store reads (a real path even for S3, which is fetched to a temp file); null if none. */
    default String localFile() {
        return null;
    }

    /** EOF records included by an ordinary snapshot, or -1 if framing is not exposed.
     * A missing final separator is legal Format 1, not evidence of truncation or completeness. */
    default int trailingRecordsIncluded() { return -1; }

    /** Pending trailing records, or -1 when this reader does not expose framing status. */
    default int trailingRecordsPending() { return -1; }

    /** True if this store can incrementally append newly-written records (follow/tail mode). */
    /**
     * M68.5 (D-E6): what the last Follow poll established about the file's identity, or null for a store that does not
     * follow or has not polled yet.
     */
    default FollowIdentity followIdentity() {
        return null;
    }

    default boolean supportsFollow() {
        return false;
    }

    /**
     * Re-read a growing local file and append any newly-<b>completed</b> records to the index
     * (follow/tail mode). Returns the number of records appended, {@code 0} if unchanged, or
     * {@code -1} if the file shrank / was rotated (the caller should reload from scratch).
     */
    default int appendFrom(java.nio.file.Path path) throws java.io.IOException {
        return -1;
    }

    /** Release any resources (e.g. a mapped file channel). No-op by default. */
    default void close() {
    }
}
