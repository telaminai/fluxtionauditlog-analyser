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

    int size();

    LogIndex index();

    /** Full parse of one record (scalars + lazy node-logs). */
    LogRecord record(int row);

    /** Cheap raw text of a row (no parsing) — used for full-text search incl. node-logs. */
    String rawText(int row);

    Long minLogTime();

    Long maxLogTime();

    /** The local file this store reads (a real path even for S3, which is fetched to a temp file); null if none. */
    default String localFile() {
        return null;
    }

    /** True if this store can incrementally append newly-written records (follow/tail mode). */
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
