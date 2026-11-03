package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evidence-set key: the open log's records divided into RUNS by the run key its stream headers state
 * ({@code docs/specs/spec-evidence-set-key.md} §3, §4.5).
 *
 * <p><b>The run key</b> is {@code serverId + runId + group/name#generation}, read from a header's {@code host} map.
 * {@code generation} alone is not a run: a static Mongoose deployment restarts as {@code group/name#1} every time, which
 * is why {@code serverId} and {@code runId} are in the key (D5).
 *
 * <p><b>A new segment starts only when the run key changes.</b> The runtime re-heads a stream after a sink, record-format
 * or host-context change, and a Mongoose capture repeats the header at each daily roll; a re-sent header with an equal
 * key is the same run and does not split it — so a rolled set whose files carry one run key is one segment. A header
 * with an empty host map has an empty run key: the log is one segment unless a later header names a different key.
 * Records before the first header belong to no stated run and form a leading segment of their own.
 *
 * <p>Pure, so the session node that publishes it and the bundle writer that records it compute the same thing.
 */
public final class RunSegments {

    private RunSegments() {
    }

    /** The four parts of a run key; any may be null. Empty when the header named no host. */
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
     * @param runKey       its run key; {@link RunKey#EMPTY} for records before the first header
     * @param headed       false only for records before the first header
     * @param fingerprints the distinct {@code sourceFingerprint}s its headers state, in order; a null entry is a header
     *                     that stated none
     */
    public record Segment(int ordinal, int firstRecord, int endRecord, RunKey runKey, boolean headed,
                          List<String> fingerprints) {

        public Segment {
            fingerprints = java.util.Collections.unmodifiableList(new ArrayList<>(fingerprints));
        }

        public int records() {
            return endRecord - firstRecord;
        }

        public String label() {
            return headed ? runKey.label() : "before the first stream header";
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
            m.put("runKey", headed ? runKey.toMap() : null);
            m.put("sourceFingerprints", fingerprints);
            return m;
        }
    }

    /**
     * The runs of a log.
     *
     * @param headers its stream headers in file order, each at its first record ({@link LogStore#streamHeaders()})
     * @param records how many records it holds
     * @return no segments for a log with no headers: nothing states a run, so there is nothing to divide by
     */
    public static List<Segment> of(List<StreamHeader> headers, int records) {
        List<Segment> out = new ArrayList<>();
        if (headers == null || headers.isEmpty()) return out;
        int first = Math.min(headers.get(0).firstRecord(), records);
        if (first > 0) out.add(new Segment(1, 0, first, RunKey.EMPTY, false, List.of()));
        RunKey current = null;
        int start = first;
        LinkedHashSet<String> prints = new LinkedHashSet<>();
        boolean sawNull = false;
        for (StreamHeader h : headers) {
            // a malformed header states no run: it neither starts nor names one, and keeps the run in force
            RunKey key = h.valid() ? RunKey.of(h) : current == null ? RunKey.EMPTY : current;
            int at = Math.min(Math.max(h.firstRecord(), start), records);
            if (current != null && !Objects.equals(current.key(), key.key())) {
                out.add(new Segment(out.size() + 1, start, at, current, true, prints(prints, sawNull)));
                start = at;
                prints = new LinkedHashSet<>();
                sawNull = false;
            }
            if (current == null || !Objects.equals(current.key(), key.key())) current = key;
            if (h.valid() && h.sourceFingerprint() != null) prints.add(h.sourceFingerprint());
            else sawNull = true;
        }
        out.add(new Segment(out.size() + 1, start, records, current, true, prints(prints, sawNull)));
        return out;
    }

    private static List<String> prints(LinkedHashSet<String> known, boolean sawNull) {
        List<String> out = new ArrayList<>(known);
        if (sawNull) out.add(null);
        return out;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
