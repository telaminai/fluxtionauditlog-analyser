package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * The {@code read} query verb (AV.1): return N records around an anchor (a {@code recordIndex} or a
 * {@code byteOffset}), so an agent can seek the log <b>through the socket</b> without its own filesystem
 * access — the case for sandboxed / remote agents and S3-temp-file loads where the agent doesn't know
 * the path. Read-only over a {@link LogIndex.Snapshot} + a raw-text accessor; rate-limited by
 * {@link #MAX_COUNT} records per call.
 */
public final class ReadService {
    private ReadService() { }

    /** Default number of records returned when neither count nor before/after is given (centred on the anchor). */
    public static final int DEFAULT_COUNT = 5;
    /** Hard cap on records returned per call (rate limit). */
    public static final int MAX_COUNT = 25;

    public static Map<String, Object> read(LogIndex.Snapshot snap, Map<String, Object> params,
                                           IntFunction<String> rawText) {
        int size = snap.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", size);
        if (size == 0) {
            out.put("records", List.of());
            return out;
        }

        Integer recordIndex = asInt(params.get("recordIndex"));
        Long byteOffset = asLong(params.get("byteOffset"));
        Long at = asLong(params.get("at"));
        int anchor;
        String anchorNote = null;
        if (recordIndex != null) {
            anchor = clamp(recordIndex, 0, size - 1);
        } else if (byteOffset != null) {
            if (!snap.byteAnchors()) {
                throw new IllegalArgumentException("this source has no byte anchors (its container is "
                        + "not a byte-addressed file) — anchor by recordIndex or at instead");
            }
            if (snap.fileCount() > 1) {
                // D-R2: offsets are FILE-LOCAL in a rolled set — a bare offset is ambiguous and is
                // refused with the file list rather than guessed
                Integer fid = fileIdOf(params.get("file"), snap);
                if (fid == null) {
                    throw new IllegalArgumentException("this log is a rolled set — a byteOffset needs "
                            + "'file' (one of " + java.util.Arrays.toString(snap.fileNames())
                            + "), or anchor by recordIndex/at");
                }
                anchor = snap.rowForOffsetInFile(byteOffset, fid);
                if (anchor < 0) throw new IllegalArgumentException("no record at that offset in '"
                        + snap.fileNames()[fid] + "'");
            } else {
                anchor = snap.rowForOffset(byteOffset);
            }
        } else if (at != null) {
            anchor = rowAtOrBefore(size, snap::logTime, at);
            if (anchor < 0) throw new IllegalArgumentException("no record carries a log time — 'at' cannot resolve");
            Long lt = snap.logTime(anchor);
            if (lt != null && lt > at) anchorNote = "every timed record is after 'at' — anchored to the first";
        } else {
            throw new IllegalArgumentException("read needs a recordIndex, byteOffset or at (epoch millis) anchor");
        }

        Integer count = asInt(params.get("count"));
        Integer before = asInt(params.get("before"));
        Integer after = asInt(params.get("after"));
        int b;
        int a;
        if (before != null || after != null) {
            b = before != null ? Math.max(0, before) : 0;
            a = after != null ? Math.max(0, after) : 0;
        } else {
            int n = count != null ? Math.max(1, count) : DEFAULT_COUNT;
            b = (n - 1) / 2;      // centre the anchor
            a = n - 1 - b;
        }

        // rate limit: at most MAX_COUNT records, ALWAYS including the anchor — trim before first, then after
        boolean capped = false;
        if (b + 1 + a > MAX_COUNT) {
            capped = true;
            b = Math.min(b, MAX_COUNT - 1);
            a = Math.min(a, MAX_COUNT - 1 - b);
        }

        int start = clamp(anchor - b, 0, size - 1);
        int end = clamp(anchor + a, 0, size - 1);

        List<String> fields = fieldList(params.get("fields"));
        Set<String> seenFields = fields == null ? null : new java.util.LinkedHashSet<>();
        List<Map<String, Object>> records = new ArrayList<>();
        for (int row = start; row <= end; row++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordIndex", row);
            m.put("byteOffset", snap.offset(row));
            if (snap.fileCount() > 1) m.put("file", snap.fileNames()[snap.fileId(row)]);   // offsets are file-local
            Long lt = snap.logTime(row);
            if (lt != null) m.put("logTime", lt);
            if (fields == null) {
                m.put("text", rawText.apply(row));
            } else {
                LogRecord rec = RecordParser.parse(rawText.apply(row), snap.offset(row));
                if (rec.event() != null) m.put("event", rec.event());
                m.put("values", project(rec.nodeLogs(), fields, seenFields));
            }
            records.add(m);
        }

        out.put("anchor", anchor);
        out.put("from", start);
        out.put("to", end);
        out.put("records", records);
        List<String> notes = new ArrayList<>();
        if (capped) notes.add("capped at " + MAX_COUNT + " records per read; page with recordIndex");
        else if (anchorNote != null) notes.add(anchorNote);
        if (fields != null) {
            List<String> missed = fields.stream()
                    .filter(f -> !f.endsWith(".*") && !seenFields.contains(f)).toList();
            if (!missed.isEmpty()) notes.add("fields never seen in these records: " + missed);
        }
        if (!notes.isEmpty()) out.put("note", String.join("; ", notes));
        return out;
    }

    /** The {@code fields} param as a non-empty list of {@code instanceId.key} / {@code instanceId.*}, or null. */
    private static List<String> fieldList(Object o) {
        if (!(o instanceof List<?> l) || l.isEmpty()) return null;
        List<String> out = new ArrayList<>(l.size());
        for (Object f : l) if (f != null && !f.toString().isBlank()) out.add(f.toString().trim());
        return out.isEmpty() ? null : out;
    }

    /**
     * The projected {@code values} for one record (M26.3): each requested {@code instanceId.key} that the
     * record logged, with <b>last-occurrence-per-record</b> semantics — the same rule graphing uses, so a
     * projected value always matches the plotted one. {@code instanceId.*} takes every key that instance
     * logged. Values stay the raw logged text: projection is a token economy, not a retype.
     */
    private static Map<String, String> project(List<NodeLog> nodeLogs, List<String> fields, Set<String> seen) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String f : fields) {
            int dot = f.lastIndexOf('.');
            if (dot <= 0 || dot == f.length() - 1) continue;   // not instanceId.key shaped — noted as unseen
            String id = f.substring(0, dot);
            String key = f.substring(dot + 1);
            if ("*".equals(key)) {
                for (NodeLog nl : nodeLogs) {
                    if (!nl.instanceId().equals(id)) continue;
                    for (KV kv : nl.entries()) {
                        if (kv.key() != null) values.put(id + "." + kv.key(), kv.rawValue());   // last wins
                    }
                }
            } else {
                String v = null;
                boolean present = false;
                for (NodeLog nl : nodeLogs) {
                    if (!nl.instanceId().equals(id)) continue;
                    KV kv = nl.last(key);
                    if (kv != null) { v = kv.rawValue(); present = true; }   // last occurrence wins
                }
                if (present) values.put(f, v);
            }
        }
        seen.addAll(values.keySet());
        return values;
    }

    /**
     * The latest row whose log time is <b>at-or-before</b> {@code at} (M26.2 time anchors) — so an agent
     * translates "what was happening at 09:14:03.250" into a record in one call instead of estimating
     * records-per-minute from a sample. Untimed rows (null log time) are skipped during comparison; if
     * every timed row is after {@code at}, the <b>first timed</b> row is returned (clamp, like the byte
     * anchors — callers note it). Returns -1 only when no row carries a time at all. Binary search —
     * log times are non-decreasing in file order for a single-logger file, the shipped read model.
     */
    public static int rowAtOrBefore(int size, IntFunction<Long> logTime, long at) {
        int lo = 0, hi = size - 1, ans = -1, firstTimed = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            // an untimed row can't be compared — walk left to the nearest timed row in [lo, mid]
            int probe = mid;
            Long lt = logTime.apply(probe);
            while (lt == null && probe > lo) lt = logTime.apply(--probe);
            if (lt == null) {          // [lo, mid] is entirely untimed → the answer lies right of mid
                lo = mid + 1;
                continue;
            }
            if (firstTimed < 0 || probe < firstTimed) firstTimed = probe;
            if (lt <= at) {
                ans = probe;
                lo = mid + 1;
            } else {
                hi = probe - 1;
            }
        }
        if (ans >= 0) return ans;
        if (firstTimed >= 0) return firstTimed;
        // never met a timed row on the search path — scan for one (rare: mostly-untimed file)
        for (int i = 0; i < size; i++) if (logTime.apply(i) != null) return i;
        return -1;
    }

    /** Resolve a 'file' param — a member index or a member name — to a file id; null when absent/unknown. */
    private static Integer fileIdOf(Object file, LogIndex.Snapshot snap) {
        if (file instanceof Number n) {
            int id = n.intValue();
            return id >= 0 && id < snap.fileCount() ? id : null;
        }
        if (file != null) {
            String name = file.toString();
            String[] names = snap.fileNames();
            for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
        }
        return null;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
