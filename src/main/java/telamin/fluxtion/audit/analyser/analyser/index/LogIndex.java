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
    private byte[] fileId = new byte[1024];   // 0 for single-file stores; set by a rolled composite (M30)
    private int size = 0;

    /** Registered member files of a rolled set, in load order; empty for a single-file index. */
    private final java.util.List<String> files = new java.util.ArrayList<>();

    /** False when offsets are SYNTHETIC (an SPI container without byte anchors, M31 D-P4). */
    private boolean byteAnchors = true;

    public void setByteAnchors(boolean byteAnchors) {
        this.byteAnchors = byteAnchors;
    }

    public boolean byteAnchors() {
        return byteAnchors;
    }

    /**
     * False when the source could not supply a dispatch order within a cycle (M34 D-A1a) — the
     * position of a node in {@code nodeLogs} is then ARRIVAL order, not causality. True for every
     * text container and for Fluxtion, whose order the AOT compiler derives; consumers that read
     * position as meaning (step-through, the topology's dispatch badges, route escalation) must
     * qualify loudly when this is false rather than presenting an invented order as an observed one.
     */
    private boolean totalOrder = true;

    public void setTotalOrder(boolean totalOrder) {
        this.totalOrder = totalOrder;
    }

    public boolean totalOrder() {
        return totalOrder;
    }

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
        fileId = Arrays.copyOf(fileId, cap);
    }

    // ---- rolled sets (M30) --------------------------------------------------------------------------

    /** Register a member file; returns its id. Only a rolled composite calls this. */
    public synchronized int registerFile(String displayName) {
        files.add(displayName);
        return files.size() - 1;
    }

    /** Member-file display names, load order; empty for a single-file index. */
    public java.util.List<String> files() {
        return java.util.List.copyOf(files);
    }

    public int fileCount() {
        return files.isEmpty() ? 1 : files.size();
    }

    /** The member file this record came from (0 for single-file stores). */
    public int fileId(int i) {
        return fileId[i];
    }

    /**
     * Copy one row from a member file's own index into this merged index (M30.2) — column copy with
     * re-interning, so the merge never re-parses a record. Offsets stay FILE-LOCAL (D-R2: a byte
     * offset must remain a real offset into a real file); {@code fid} says which file.
     */
    public synchronized void addFrom(LogIndex src, int row, int fid) {
        ensure(size + 1);
        offset[size] = src.offset(row);
        length[size] = src.length(row);
        Long lt = src.logTime(row);
        logTime[size] = lt == null ? NO_TIME : lt;
        Long et = src.eventTime(row);
        eventTime[size] = et == null ? NO_TIME : et;
        Long en = src.endTime(row);
        endTime[size] = en == null ? NO_TIME : en;
        int d = dimensions.intern(src.dimension(row));
        dimId[size] = d;
        if (d >= dimCount.length) dimCount = Arrays.copyOf(dimCount, Math.max(d + 1, dimCount.length * 2));
        dimCount[d]++;
        loggerId[size] = loggers.intern(src.logger(row));
        threadId[size] = threads.intern(src.thread(row));
        eventId[size] = events.intern(src.event(row));
        eventStrId[size] = eventStrings.intern(src.eventToString(row));
        groupingId[size] = groupings.intern(src.groupingId(row));
        callbackId[size] = callbacks.intern(src.callback(row));
        declaringTypeId[size] = declaringTypes.intern(src.declaringType(row));
        nodeLogsCount[size] = src.nodeLogsCount(row);
        if (nodeLogsCount[size] > maxNodeLogs) maxNodeLogs = nodeLogsCount[size];
        flags[size] = src.flags(row);
        fileId[size] = (byte) fid;
        if (lt != null) {
            if (lt < minLog) minLog = lt;
            if (lt > maxLog) maxLog = lt;
        }
        size++;
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
        return new Snapshot(size, offset, logTime, dimId, threadId, flags, fileId,
                files.toArray(new String[0]), byteAnchors,
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
        private final byte[] fileId;
        private final String[] fileNames;      // empty for single-file stores
        private final boolean byteAnchors;
        private final String[] dimValues;      // captured copies — no live Dictionary reference
        private final String[] threadValues;
        private final long minLog, maxLog;

        private Snapshot(int size, long[] offset, long[] logTime, int[] dimId, int[] threadId, byte[] flags,
                         byte[] fileId, String[] fileNames, boolean byteAnchors,
                         String[] dimValues, String[] threadValues, long minLog, long maxLog) {
            this.byteAnchors = byteAnchors;
            this.size = size;
            this.offset = offset;
            this.logTime = logTime;
            this.dimId = dimId;
            this.threadId = threadId;
            this.flags = flags;
            this.fileId = fileId;
            this.fileNames = fileNames;
            this.dimValues = dimValues;
            this.threadValues = threadValues;
            this.minLog = minLog;
            this.maxLog = maxLog;
        }

        public boolean byteAnchors() { return byteAnchors; }

        public int fileCount() { return fileNames.length == 0 ? 1 : fileNames.length; }

        public String[] fileNames() { return fileNames.clone(); }

        public int fileId(int i) { return fileId[i]; }

        /**
         * The record whose byte range contains {@code byteOffset} WITHIN member file {@code fid}
         * (M30 D-R2: offsets are file-local, so an offset search must be file-scoped). Linear over the
         * file's rows — member row ranges are contiguous but this stays correct even if they were not.
         */
        public int rowForOffsetInFile(long byteOffset, int fid) {
            int ans = -1;
            for (int i = 0; i < size; i++) {
                if (fileId[i] == fid && offset[i] <= byteOffset) ans = i;
                else if (fileId[i] == fid && offset[i] > byteOffset && ans >= 0) break;
            }
            return ans;
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
