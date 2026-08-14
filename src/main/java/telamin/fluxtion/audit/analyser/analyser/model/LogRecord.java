package telamin.fluxtion.audit.analyser.analyser.model;

import java.util.List;
import java.util.function.Supplier;

/**
 * A single parsed {@code eventLogRecord}. Scalar fields are parsed eagerly; {@link #nodeLogs()} is
 * parsed lazily on first access (from the record's raw text) and memoised, so browsing/filtering the
 * table never pays for node-log parsing.
 *
 * <p>Times are epoch millis or {@code null} when absent; {@code eventTime == -1} is normalised to
 * {@code null} (non event-driven cycle).
 */
public final class LogRecord {

    private final long fileOffset;
    private final int byteLength;

    private final Long eventTime;
    private final Long logTime;
    private final Long endTime;
    private final String groupingId;
    private final String event;
    private final String eventToString;
    private final String thread;
    private final String logger;
    private final String level;
    private final String headerTime;

    private final EventKind kind;
    private final String callback;         // method name when eventToString is a signature, else null
    private final String declaringType;    // FQN of the declaring class of the callback, else null
    private final String eventDimension;   // callback != null ? callback : event
    private final int nodeLogsCount;       // number of node-log items (cheap, no value parsing)
    private final boolean hasNaN;          // node-logs contain a NaN value (anomaly cue)
    private final boolean hasBreach;       // node-logs contain a "...Breach: true" (anomaly cue)

    private final String rawText;
    private final Supplier<List<NodeLog>> nodeLogsSupplier;
    private volatile List<NodeLog> nodeLogs;   // memoised

    private LogRecord(Builder b) {
        this.fileOffset = b.fileOffset;
        this.byteLength = b.byteLength;
        this.eventTime = b.eventTime;
        this.logTime = b.logTime;
        this.endTime = b.endTime;
        this.groupingId = b.groupingId;
        this.event = b.event;
        this.eventToString = b.eventToString;
        this.thread = b.thread;
        this.logger = b.logger;
        this.level = b.level;
        this.headerTime = b.headerTime;
        this.kind = b.kind;
        this.callback = b.callback;
        this.declaringType = b.declaringType;
        this.eventDimension = b.eventDimension;
        this.nodeLogsCount = b.nodeLogsCount;
        this.hasNaN = b.hasNaN;
        this.hasBreach = b.hasBreach;
        this.rawText = b.rawText;
        this.nodeLogsSupplier = b.nodeLogsSupplier;
    }

    /** Lazily parses (once) and returns the node-logs for this record. Never {@code null}. */
    public List<NodeLog> nodeLogs() {
        List<NodeLog> local = nodeLogs;
        if (local == null) {
            synchronized (this) {
                local = nodeLogs;
                if (local == null) {
                    local = nodeLogsSupplier == null ? List.of() : nodeLogsSupplier.get();
                    if (local == null) local = List.of();
                    nodeLogs = local;
                }
            }
        }
        return local;
    }

    public long fileOffset()      { return fileOffset; }
    public int  byteLength()      { return byteLength; }
    public Long eventTime()       { return eventTime; }
    public Long logTime()         { return logTime; }
    public Long endTime()         { return endTime; }
    public String groupingId()    { return groupingId; }
    public String event()         { return event; }
    public String eventToString() { return eventToString; }
    public String thread()        { return thread; }
    public String logger()        { return logger; }
    public String level()         { return level; }
    public String headerTime()    { return headerTime; }
    public EventKind kind()       { return kind; }
    public String callback()      { return callback; }
    public String declaringType() { return declaringType; }
    public String eventDimension(){ return eventDimension; }
    public int nodeLogsCount()    { return nodeLogsCount; }
    public boolean hasNaN()       { return hasNaN; }
    public boolean hasBreach()    { return hasBreach; }
    public String rawText()       { return rawText; }

    public static Builder builder() { return new Builder(); }

    /** Mutable builder used by the parser (and tests). */
    public static final class Builder {
        private long fileOffset;
        private int byteLength;
        private Long eventTime, logTime, endTime;
        private String groupingId, event, eventToString, thread, logger, level, headerTime;
        private EventKind kind = EventKind.OK;
        private String callback, declaringType, eventDimension;
        private int nodeLogsCount;
        private boolean hasNaN, hasBreach;
        private String rawText;
        private Supplier<List<NodeLog>> nodeLogsSupplier;

        public Builder fileOffset(long v) { this.fileOffset = v; return this; }
        public Builder byteLength(int v) { this.byteLength = v; return this; }
        public Builder eventTime(Long v) { this.eventTime = v; return this; }
        public Builder logTime(Long v) { this.logTime = v; return this; }
        public Builder endTime(Long v) { this.endTime = v; return this; }
        public Builder groupingId(String v) { this.groupingId = v; return this; }
        public Builder event(String v) { this.event = v; return this; }
        public Builder eventToString(String v) { this.eventToString = v; return this; }
        public Builder thread(String v) { this.thread = v; return this; }
        public Builder logger(String v) { this.logger = v; return this; }
        public Builder level(String v) { this.level = v; return this; }
        public Builder headerTime(String v) { this.headerTime = v; return this; }
        public Builder kind(EventKind v) { this.kind = v; return this; }
        public Builder callback(String v) { this.callback = v; return this; }
        public Builder declaringType(String v) { this.declaringType = v; return this; }
        public Builder eventDimension(String v) { this.eventDimension = v; return this; }
        public Builder nodeLogsCount(int v) { this.nodeLogsCount = v; return this; }
        public Builder hasNaN(boolean v) { this.hasNaN = v; return this; }
        public Builder hasBreach(boolean v) { this.hasBreach = v; return this; }
        public Builder rawText(String v) { this.rawText = v; return this; }
        public Builder nodeLogsSupplier(Supplier<List<NodeLog>> v) { this.nodeLogsSupplier = v; return this; }

        public LogRecord build() { return new LogRecord(this); }
    }
}
