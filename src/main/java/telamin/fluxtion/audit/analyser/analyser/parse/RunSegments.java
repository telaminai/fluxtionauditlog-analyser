package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Evidence-set key: the open log's records divided into RUNS by the run key its stream headers state
 * ({@code docs/specs/spec-evidence-set-key.md} §3, §4.5).
 *
 * <p><b>The run key</b> is {@code serverId + runId + group/name#generation}, read from a header's {@code host} map.
 * {@code generation} alone is not a run: a static Mongoose deployment restarts as {@code group/name#1} every time, which
 * is why {@code serverId} and {@code runId} are in the key (D5).
 *
 * <p><b>The rules</b> (owner, 2026-11-03, and the independent review of slice 5):
 * <ul>
 *   <li>A new segment starts only when the run key changes. A re-sent header with an equal key (a sink or context
 *       change) continues the run.</li>
 *   <li>An EMPTY key (a header with no host map) continues the run in force and never starts or names one (F6). A log
 *       whose headers are all empty is one segment, labelled "no run identity stated", until a header names a key.</li>
 *   <li>A STREAM BREAK — a stream-end marker's boundary ({@link LogStore#runBoundaries()}) or the first row of a member of a
 *       rolled set — ends the stream. What follows is a stretch of its own unless a header immediately precedes it; one
 *       whose run key equals the run in force (a Mongoose capture re-heading at a daily roll) continues that run, so a
 *       capture rolled across files is one run (acceptance 7).</li>
 *   <li>Records no header covers — before the first header, or after a break with none following it — are a segment of
 *       their own, {@link Kind#UNHEADED} (F1).</li>
 *   <li>A header the analyser cannot read starts a segment labelled "run identity unreadable", {@link Kind#UNREADABLE}:
 *       a future schema from a new process is never merged into the previous run (F12).</li>
 * </ul>
 * Pure, so the session node, discovery, the bundle writer and {@code --verify} compute the same thing.
 */
public final class RunSegments {

    private RunSegments() {
    }

    /** What stated a segment's run. */
    public enum Kind {
        /** A readable stream header, with or without a host map. */
        HEADED,
        /** No header covers these records. */
        UNHEADED,
        /** A header the analyser could not read. */
        UNREADABLE
    }

    /** The parts of a run key; any may be null. Empty when the header named no host. */
    public record RunKey(String serverId, String runId, String group, String name, String generation) {

        public static final RunKey EMPTY = new RunKey(null, null, null, null, null);

        public static RunKey of(StreamHeader header) {
            if (header == null || !header.valid()) return EMPTY;
            Map<String, String> h = header.host();
            return new RunKey(blank(h.get("serverId")), blank(h.get("runId")), blank(h.get("group")),
                    blank(h.get("name")), blank(h.get("generation")));
        }

        public boolean isEmpty() {
            return serverId == null && runId == null && group == null && name == null && generation == null;
        }

        /** The key as one comparable string: {@code serverId|runId|group/name#generation}. Empty for no host. */
        public String key() {
            if (isEmpty()) return "";
            return nz(serverId) + "|" + nz(runId) + "|" + nz(group) + "/" + nz(name) + "#" + nz(generation);
        }

        /** {@code serverId · runId(first 8) · group/name#generation}, leaving out what the header did not state. */
        public String label() {
            if (isEmpty()) return "no run identity stated";
            List<String> parts = new ArrayList<>();
            if (serverId != null) parts.add(serverId);
            if (runId != null) parts.add(runId.length() > 8 ? runId.substring(0, 8) : runId);
            if (group != null || name != null || generation != null) {
                parts.add(nz(group) + "/" + nz(name) + (generation == null ? "" : "#" + generation));
            }
            return String.join(" · ", parts);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("serverId", serverId);
            m.put("runId", runId);
            m.put("group", group);
            m.put("name", name);
            m.put("generation", generation);
            return m;
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }

    /**
     * One run.
     *
     * @param ordinal      1-based
     * @param firstRecord  the row of its first record
     * @param endRecord    one past the row of its last record; equal to {@code firstRecord} for a run with no records
     * @param runKey       its run key; {@link RunKey#EMPTY} unless {@link Kind#HEADED} with a host
     * @param kind         what stated it
     * @param fingerprints the distinct {@code sourceFingerprint}s its headers state, in order; a null entry is a header
     *                     that stated none, or one that could not be read
     */
    public record Segment(int ordinal, int firstRecord, int endRecord, RunKey runKey, Kind kind,
                          List<String> fingerprints) {

        public Segment {
            fingerprints = java.util.Collections.unmodifiableList(new ArrayList<>(fingerprints));
        }

        public boolean headed() {
            return kind == Kind.HEADED;
        }

        public int records() {
            return endRecord - firstRecord;
        }

        public String label() {
            return switch (kind) {
                case HEADED -> runKey.label();
                case UNREADABLE -> "run identity unreadable";
                case UNHEADED -> firstRecord == 0 ? "before the first stream header" : "no stream header";
            };
        }

        /** Records as a person counts them: {@code records 1–40}, or {@code no records}. */
        public String span() {
            return records() == 0 ? "no records" : records() == 1 ? "record " + (firstRecord + 1)
                    : "records " + (firstRecord + 1) + "–" + endRecord;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("segment", ordinal);
            m.put("label", label());
            m.put("firstRecord", firstRecord);
            m.put("lastRecord", records() == 0 ? null : endRecord - 1);
            m.put("records", records());
            m.put("runKey", headed() ? runKey.toMap() : null);
            m.put("sourceFingerprints", fingerprints);
            return m;
        }
    }

    /** As {@link #of(List, int, Collection)} for a single stream with no breaks. */
    public static List<Segment> of(List<StreamHeader> headers, int records) {
        return of(headers, records, List.of());
    }

    /**
     * The runs of a log.
     *
     * @param headers its stream headers in file order, each at its first record ({@link LogStore#streamHeaders()})
     * @param records how many records it holds
     * @param breaks  rows at which a stream ended and another began ({@link LogStore#streamBreaks()})
     * @return no segments for a log with no headers: nothing states a run, so there is nothing to divide by
     */
    public static List<Segment> of(List<StreamHeader> headers, int records, Collection<Integer> breaks) {
        if (headers == null || headers.isEmpty()) return List.of();
        TreeMap<Integer, List<StreamHeader>> at = new TreeMap<>();
        for (StreamHeader h : headers) {
            at.computeIfAbsent(Math.max(0, Math.min(h.firstRecord(), records)), k -> new ArrayList<>()).add(h);
        }
        TreeSet<Integer> stops = new TreeSet<>();
        if (breaks != null) for (int b : breaks) if (b > 0 && b < records) stops.add(b);
        TreeSet<Integer> rows = new TreeSet<>(at.keySet());
        rows.addAll(stops);
        Builder b = new Builder();
        for (int r : rows) {
            List<StreamHeader> here = at.getOrDefault(r, List.of());
            int from = 0;
            if (stops.contains(r)) {
                StreamHeader first = here.isEmpty() ? null : here.get(0);
                RunKey k = first == null ? null : RunKey.of(first);
                boolean sameRun = first != null && first.valid() && !k.isEmpty()
                        && b.kind == Kind.HEADED && k.key().equals(b.key.key());
                if (sameRun) {
                    b.print(first);
                    from = 1;
                } else {
                    b.start(r, Kind.UNHEADED, RunKey.EMPTY);
                }
            }
            for (int i = from; i < here.size(); i++) b.header(r, here.get(i));
        }
        b.close(records);
        return b.out;
    }

    /** The segment being built, and those finished. */
    private static final class Builder {
        final List<Segment> out = new ArrayList<>();
        int start = 0;
        Kind kind = Kind.UNHEADED;
        RunKey key = RunKey.EMPTY;
        LinkedHashSet<String> prints = new LinkedHashSet<>();
        boolean sawNull;

        void header(int row, StreamHeader h) {
            if (!h.valid()) {
                start(row, Kind.UNREADABLE, RunKey.EMPTY);   // F12: never merged into the run before it
                sawNull = true;
                return;
            }
            RunKey k = RunKey.of(h);
            if (kind == Kind.HEADED && (k.isEmpty() || k.key().equals(key.key()))) {
                print(h);                                    // the same run, or an empty key that continues it (F6)
                return;
            }
            start(row, Kind.HEADED, k);
            print(h);
        }

        void print(StreamHeader h) {
            if (h.valid() && h.sourceFingerprint() != null) prints.add(h.sourceFingerprint());
            else sawNull = true;
        }

        /** End the segment in force at {@code row} and begin another of {@code kind} there. */
        void start(int row, Kind next, RunKey nextKey) {
            close(row);
            start = row;
            kind = next;
            key = nextKey;
            prints = new LinkedHashSet<>();
            sawNull = false;
        }

        /** Emit the segment in force, ending at {@code row}, unless it is an empty stretch no header covers. */
        void close(int row) {
            if (row > start || kind != Kind.UNHEADED) {
                List<String> p = new ArrayList<>(prints);
                if (sawNull) p.add(null);
                out.add(new Segment(out.size() + 1, start, row, key, kind, p));
            }
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
