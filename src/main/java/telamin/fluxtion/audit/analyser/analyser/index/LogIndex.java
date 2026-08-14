package telamin.fluxtion.audit.analyser.analyser.index;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact, columnar index over all records — the browse/filter/summarise substrate that never needs
 * node-log parsing (spec §7). Scalar fields live in parallel primitive arrays; dimension/logger/
 * thread strings are interned. This scales to very large files: only the index (not the records)
 * is held in heap.
 *
 * <p>Absent times are stored as {@link #NO_TIME} and surfaced as {@code null}.
 */
public final class LogIndex {

    public static final long NO_TIME = Long.MIN_VALUE;

    private long[] offset = new long[1024];
    private int[] length = new int[1024];
    private long[] logTime = new long[1024];
    private long[] eventTime = new long[1024];
    private long[] endTime = new long[1024];
    private int[] dimId = new int[1024];
    private int[] loggerId = new int[1024];
    private int[] threadId = new int[1024];
    private int[] eventId = new int[1024];
    private int[] eventStrId = new int[1024];
    private int[] groupingId = new int[1024];
    private int[] callbackId = new int[1024];
    private int[] declaringTypeId = new int[1024];
    private int[] nodeLogsCount = new int[1024];
    private byte[] flags = new byte[1024];
    private int size = 0;

    /** flags bits */
    public static final int FLAG_PARSE_ERROR = 1;
    public static final int FLAG_NAN = 2;
    public static final int FLAG_BREACH = 4;

    private final Dictionary dimensions = new Dictionary();
    private final Dictionary loggers = new Dictionary();
    private final Dictionary threads = new Dictionary();
    private final Dictionary events = new Dictionary();
    private final Dictionary eventStrings = new Dictionary();
    private final Dictionary groupings = new Dictionary();
    private final Dictionary callbacks = new Dictionary();
    private final Dictionary declaringTypes = new Dictionary();
    private int[] dimCount = new int[16];

    private long minLog = Long.MAX_VALUE;
    private long maxLog = Long.MIN_VALUE;
    private int maxNodeLogs = 0;

    public synchronized void add(LogRecord r) {
        ensure(size + 1);
        offset[size] = r.fileOffset();
        length[size] = r.byteLength();
        long lt = r.logTime() != null ? r.logTime() : NO_TIME;
        logTime[size] = lt;
        eventTime[size] = r.eventTime() != null ? r.eventTime() : NO_TIME;
        endTime[size] = r.endTime() != null ? r.endTime() : NO_TIME;

        int d = dimensions.intern(r.eventDimension());
        dimId[size] = d;
        if (d >= dimCount.length) dimCount = Arrays.copyOf(dimCount, Math.max(d + 1, dimCount.length * 2));
        dimCount[d]++;
        loggerId[size] = loggers.intern(r.logger());
        threadId[size] = threads.intern(r.thread());
        eventId[size] = events.intern(r.event());
        eventStrId[size] = eventStrings.intern(r.eventToString());
        groupingId[size] = groupings.intern(r.groupingId());
        callbackId[size] = callbacks.intern(r.callback());
        declaringTypeId[size] = declaringTypes.intern(r.declaringType());
        nodeLogsCount[size] = r.nodeLogsCount();
        if (r.nodeLogsCount() > maxNodeLogs) maxNodeLogs = r.nodeLogsCount();
        byte f = 0;
        if (r.kind() == telamin.fluxtion.audit.analyser.analyser.model.EventKind.PARSE_ERROR) f |= FLAG_PARSE_ERROR;
        if (r.hasNaN()) f |= FLAG_NAN;
        if (r.hasBreach()) f |= FLAG_BREACH;
        flags[size] = f;

        if (lt != NO_TIME) {
            if (lt < minLog) minLog = lt;
            if (lt > maxLog) maxLog = lt;
        }
        size++;
    }

    private void ensure(int n) {
        if (n <= offset.length) return;
        int cap = Math.max(n, offset.length * 2);
        offset = Arrays.copyOf(offset, cap);
        length = Arrays.copyOf(length, cap);
        logTime = Arrays.copyOf(logTime, cap);
        eventTime = Arrays.copyOf(eventTime, cap);
        endTime = Arrays.copyOf(endTime, cap);
        dimId = Arrays.copyOf(dimId, cap);
        loggerId = Arrays.copyOf(loggerId, cap);
        threadId = Arrays.copyOf(threadId, cap);
        eventId = Arrays.copyOf(eventId, cap);
        eventStrId = Arrays.copyOf(eventStrId, cap);
        groupingId = Arrays.copyOf(groupingId, cap);
        callbackId = Arrays.copyOf(callbackId, cap);
        declaringTypeId = Arrays.copyOf(declaringTypeId, cap);
        nodeLogsCount = Arrays.copyOf(nodeLogsCount, cap);
        flags = Arrays.copyOf(flags, cap);
    }

    public int size() { return size; }

    public long offset(int i) { return offset[i]; }
    public int length(int i) { return length[i]; }

    public Long logTime(int i)   { return logTime[i] == NO_TIME ? null : logTime[i]; }
    public Long eventTime(int i) { return eventTime[i] == NO_TIME ? null : eventTime[i]; }
    public Long endTime(int i)   { return endTime[i] == NO_TIME ? null : endTime[i]; }

    public String dimension(int i) { return dimensions.get(dimId[i]); }
    public String logger(int i)    { return loggers.get(loggerId[i]); }
    public String thread(int i)    { return threads.get(threadId[i]); }
    public String event(int i)     { return blankToNull(events.get(eventId[i])); }
    public String eventToString(int i) { return blankToNull(eventStrings.get(eventStrId[i])); }
    public String groupingId(int i){ return blankToNull(groupings.get(groupingId[i])); }
    public String callback(int i)  { return blankToNull(callbacks.get(callbackId[i])); }
    public String declaringType(int i) { return blankToNull(declaringTypes.get(declaringTypeId[i])); }
    public int nodeLogsCount(int i){ return nodeLogsCount[i]; }
    public byte flags(int i)       { return flags[i]; }
    public boolean parseError(int i){ return (flags[i] & FLAG_PARSE_ERROR) != 0; }
    public boolean hasNaN(int i)   { return (flags[i] & FLAG_NAN) != 0; }
    public boolean hasBreach(int i){ return (flags[i] & FLAG_BREACH) != 0; }

    private static String blankToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    public Long minLogTime() { return size == 0 || minLog == Long.MAX_VALUE ? null : minLog; }
    public Long maxLogTime() { return size == 0 || maxLog == Long.MIN_VALUE ? null : maxLog; }
    public int maxNodeLogsCount() { return maxNodeLogs; }

    /**
     * An immutable, point-in-time view of the index for off-EDT readers (e.g. the aggregate query,
     * spec-assistant-actions §6). Captures {@code size} and the current column-array references under
     * the index lock; because arrays only grow via {@code copyOf}, a captured reference still holds
     * valid data for {@code [0, size)}. Follow-mode appends after the snapshot simply aren't seen.
     */
    public synchronized Snapshot snapshot() {
        // capture column-array refs AND defensive copies of the dictionaries under the lock, so an
        // off-EDT reader never touches the live resizable Dictionary lists that follow-mode grows
        return new Snapshot(size, offset, logTime, dimId, threadId, flags,
                dimensions.copyValues(), threads.copyValues(), minLog, maxLog);
    }

    /** Read-only bound view returned by {@link #snapshot()}; safe to read from any thread. */
    public static final class Snapshot {
        private final int size;
        private final long[] offset;
        private final long[] logTime;
        private final int[] dimId;
        private final int[] threadId;
        private final byte[] flags;
        private final String[] dimValues;      // captured copies — no live Dictionary reference
        private final String[] threadValues;
        private final long minLog, maxLog;

        private Snapshot(int size, long[] offset, long[] logTime, int[] dimId, int[] threadId, byte[] flags,
                         String[] dimValues, String[] threadValues, long minLog, long maxLog) {
            this.size = size;
            this.offset = offset;
            this.logTime = logTime;
            this.dimId = dimId;
            this.threadId = threadId;
            this.flags = flags;
            this.dimValues = dimValues;
            this.threadValues = threadValues;
            this.minLog = minLog;
            this.maxLog = maxLog;
        }

        public int size() { return size; }

        public long offset(int i) { return offset[i]; }

        /** The record index whose byte range contains {@code byteOffset} (floor), clamped to [0, size). */
        public int rowForOffset(long byteOffset) {
            int lo = 0, hi = size - 1, ans = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (offset[mid] <= byteOffset) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
            }
            return ans;
        }

        public Long logTime(int i) { return logTime[i] == NO_TIME ? null : logTime[i]; }

        public String dimension(int i) {
            int id = dimId[i];
            return (id >= 0 && id < dimValues.length) ? dimValues[id] : null;
        }

        public String thread(int i) {
            int id = threadId[i];
            return (id >= 0 && id < threadValues.length) ? threadValues[id] : null;
        }

        public boolean parseError(int i) { return (flags[i] & FLAG_PARSE_ERROR) != 0; }
        public boolean hasNaN(int i)     { return (flags[i] & FLAG_NAN) != 0; }
        public boolean hasBreach(int i)  { return (flags[i] & FLAG_BREACH) != 0; }

        public Long minLogTime() { return size == 0 || minLog == Long.MAX_VALUE ? null : minLog; }
        public Long maxLogTime() { return size == 0 || maxLog == Long.MIN_VALUE ? null : maxLog; }
    }

    /** Per-dimension record counts, in first-seen order. */
    public Map<String, Integer> dimensionCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int d = 0; d < dimensions.size(); d++) {
            out.put(dimensions.get(d), d < dimCount.length ? dimCount[d] : 0);
        }
        return out;
    }
}
