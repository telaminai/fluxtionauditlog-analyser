package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        int anchor;
        if (recordIndex != null) {
            anchor = clamp(recordIndex, 0, size - 1);
        } else if (byteOffset != null) {
            anchor = snap.rowForOffset(byteOffset);
        } else {
            throw new IllegalArgumentException("read needs a recordIndex or byteOffset anchor");
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

        List<Map<String, Object>> records = new ArrayList<>();
        for (int row = start; row <= end; row++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordIndex", row);
            m.put("byteOffset", snap.offset(row));
            Long lt = snap.logTime(row);
            if (lt != null) m.put("logTime", lt);
            m.put("text", rawText.apply(row));
            records.add(m);
        }

        out.put("anchor", anchor);
        out.put("from", start);
        out.put("to", end);
        out.put("records", records);
        if (capped) out.put("note", "capped at " + MAX_COUNT + " records per read; page with recordIndex");
        return out;
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
